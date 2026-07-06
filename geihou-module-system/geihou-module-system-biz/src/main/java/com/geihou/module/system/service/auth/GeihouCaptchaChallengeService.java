package com.geihou.module.system.service.auth;

import com.geihou.module.system.dal.dataobject.auth.AuthCaptchaChallengeDO;
import com.geihou.module.system.dal.mysql.auth.AuthCaptchaChallengeRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

public class GeihouCaptchaChallengeService
        implements GeihouAdminLoginAuthenticationService.CaptchaPort {

    private static final int CAPTCHA_THRESHOLD = 3;
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private final AuthCaptchaChallengeRepository repository;
    private final Clock clock;

    public GeihouCaptchaChallengeService(AuthCaptchaChallengeRepository repository) {
        this(repository, Clock.systemDefaultZone());
    }

    GeihouCaptchaChallengeService(AuthCaptchaChallengeRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public CaptchaChallenge createChallenge(String answer) {
        return createChallenge(answer, DEFAULT_TTL);
    }

    CaptchaChallenge createChallenge(String answer, Duration ttl) {
        if (isBlank(answer) || ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("captcha answer and positive ttl are required");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        String captchaKey = UUID.randomUUID().toString();
        AuthCaptchaChallengeDO entity = new AuthCaptchaChallengeDO();
        entity.setCaptchaKey(captchaKey);
        entity.setAnswerHash(hash(captchaKey, answer));
        entity.setExpireTime(now.plus(ttl));
        entity.setCreateTime(now);
        repository.insert(entity);
        return new CaptchaChallenge(captchaKey, entity.getExpireTime());
    }

    @Override
    public boolean captchaRequired(long userId, int loginFailCount) {
        return loginFailCount >= CAPTCHA_THRESHOLD;
    }

    @Override
    public boolean verify(String captchaKey, String captcha) {
        if (isBlank(captchaKey) || isBlank(captcha)) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        AuthCaptchaChallengeDO challenge = repository.selectUsableByKey(captchaKey.trim(), now);
        if (challenge == null || !hash(captchaKey.trim(), captcha).equals(challenge.getAnswerHash())) {
            return false;
        }
        return repository.consume(captchaKey.trim(), now);
    }

    private static String hash(String captchaKey, String answer) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((captchaKey + ":" + normalizeAnswer(answer)).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required", ex);
        }
    }

    private static String normalizeAnswer(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record CaptchaChallenge(String captchaKey, LocalDateTime expireTime) {
    }
}
