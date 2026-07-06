package com.geihou.module.system.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.geihou.module.system.dal.dataobject.auth.AuthCaptchaChallengeDO;
import com.geihou.module.system.dal.mysql.auth.AuthCaptchaChallengeRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class GeihouCaptchaChallengeServiceTest {

    private final AuthCaptchaChallengeRepository repository = mock(AuthCaptchaChallengeRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-20T01:00:00Z"), ZoneId.of("UTC"));
    private final GeihouCaptchaChallengeService service = new GeihouCaptchaChallengeService(repository, clock);

    @Test
    void shouldRequireCaptchaAfterThreeFailedAttempts() {
        assertThat(service.captchaRequired(1L, 2)).isFalse();
        assertThat(service.captchaRequired(1L, 3)).isTrue();
        assertThat(service.captchaRequired(1L, 5)).isTrue();
    }

    @Test
    void shouldCreateOpaqueChallengeWithoutPersistingRawAnswer() {
        GeihouCaptchaChallengeService.CaptchaChallenge challenge =
                service.createChallenge(" aB12 ", Duration.ofMinutes(2));

        ArgumentCaptor<AuthCaptchaChallengeDO> captor = ArgumentCaptor.forClass(AuthCaptchaChallengeDO.class);
        Mockito.verify(repository).insert(captor.capture());
        AuthCaptchaChallengeDO entity = captor.getValue();

        assertThat(challenge.captchaKey()).isEqualTo(entity.getCaptchaKey());
        assertThat(entity.getCaptchaKey()).isNotBlank();
        assertThat(entity.getAnswerHash()).hasSize(64);
        assertThat(entity.getAnswerHash()).doesNotContain("AB12").doesNotContain("aB12");
        assertThat(entity.getExpireTime()).isEqualTo(LocalDateTime.parse("2026-06-20T01:02:00"));
        assertThat(entity.getCreateTime()).isEqualTo(LocalDateTime.parse("2026-06-20T01:00:00"));
    }

    @Test
    void shouldRejectBlankAnswerOrNonPositiveTtl() {
        assertThatThrownBy(() -> service.createChallenge(" ", Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createChallenge("1234", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldVerifyAndConsumeMatchingChallengeOnce() {
        GeihouCaptchaChallengeService.CaptchaChallenge challenge =
                service.createChallenge("AB12", Duration.ofMinutes(5));
        AuthCaptchaChallengeDO persisted = capturedChallenge();
        when(repository.selectUsableByKey(challenge.captchaKey(), LocalDateTime.parse("2026-06-20T01:00:00")))
                .thenReturn(persisted);
        when(repository.consume(challenge.captchaKey(), LocalDateTime.parse("2026-06-20T01:00:00")))
                .thenReturn(true)
                .thenReturn(false);

        assertThat(service.verify(challenge.captchaKey(), " ab12 ")).isTrue();
        assertThat(service.verify(challenge.captchaKey(), " ab12 ")).isFalse();
    }

    @Test
    void shouldFailClosedForMissingExpiredOrMismatchedCaptcha() {
        assertThat(service.verify(null, "1234")).isFalse();
        assertThat(service.verify("key", " ")).isFalse();

        when(repository.selectUsableByKey("missing", LocalDateTime.parse("2026-06-20T01:00:00")))
                .thenReturn(null);
        assertThat(service.verify("missing", "1234")).isFalse();

        GeihouCaptchaChallengeService.CaptchaChallenge challenge =
                service.createChallenge("AB12", Duration.ofMinutes(5));
        AuthCaptchaChallengeDO persisted = capturedChallenge();
        when(repository.selectUsableByKey(challenge.captchaKey(), LocalDateTime.parse("2026-06-20T01:00:00")))
                .thenReturn(persisted);

        assertThat(service.verify(challenge.captchaKey(), "WRONG")).isFalse();
        Mockito.verify(repository, Mockito.never()).consume("missing", LocalDateTime.parse("2026-06-20T01:00:00"));
    }

    private AuthCaptchaChallengeDO capturedChallenge() {
        ArgumentCaptor<AuthCaptchaChallengeDO> captor = ArgumentCaptor.forClass(AuthCaptchaChallengeDO.class);
        Mockito.verify(repository, Mockito.atLeastOnce()).insert(captor.capture());
        return captor.getValue();
    }
}
