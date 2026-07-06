package com.geihou.module.system.controller.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.geihou.module.system.service.auth.GeihouAdminLoginOrchestratorService.OrchestrationDenyReason;
import com.geihou.module.system.service.auth.GeihouTwoFactorVerifyOrchestrationService.DenyReason;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class GeihouLoginDenyReasonPublicMapperTest {

    @Test
    void shouldMapInvalidRequestToLoginBadRequest() {
        assertThat(GeihouLoginDenyReasonPublicMapper.toPublicReason(OrchestrationDenyReason.INVALID_REQUEST))
                .isEqualTo(GeihouLoginDenyReasonPublicMapper.LOGIN_BAD_REQUEST);
    }

    @Test
    void shouldMapUnknownTenantToLoginCredentialsInvalid() {
        assertThat(GeihouLoginDenyReasonPublicMapper.toPublicReason(OrchestrationDenyReason.UNKNOWN_TENANT))
                .isEqualTo(GeihouLoginDenyReasonPublicMapper.LOGIN_CREDENTIALS_INVALID);
    }

    @Test
    void shouldMapUnknownUserToLoginCredentialsInvalid() {
        assertThat(GeihouLoginDenyReasonPublicMapper.toPublicReason(OrchestrationDenyReason.UNKNOWN_USER))
                .isEqualTo(GeihouLoginDenyReasonPublicMapper.LOGIN_CREDENTIALS_INVALID);
    }

    @Test
    void shouldMapUserNotActiveToLoginDenied() {
        assertThat(GeihouLoginDenyReasonPublicMapper.toPublicReason(OrchestrationDenyReason.USER_NOT_ACTIVE))
                .isEqualTo(GeihouLoginDenyReasonPublicMapper.LOGIN_DENIED);
    }

    @Test
    void shouldMapAccountLockedToLoginAccountLocked() {
        assertThat(GeihouLoginDenyReasonPublicMapper.toPublicReason(OrchestrationDenyReason.ACCOUNT_LOCKED))
                .isEqualTo(GeihouLoginDenyReasonPublicMapper.LOGIN_ACCOUNT_LOCKED);
    }

    @Test
    void shouldMapCaptchaInvalidToLoginCaptchaInvalid() {
        assertThat(GeihouLoginDenyReasonPublicMapper.toPublicReason(OrchestrationDenyReason.CAPTCHA_INVALID))
                .isEqualTo(GeihouLoginDenyReasonPublicMapper.LOGIN_CAPTCHA_INVALID);
    }

    @Test
    void shouldMapInvalidPasswordToLoginCredentialsInvalid() {
        assertThat(GeihouLoginDenyReasonPublicMapper.toPublicReason(OrchestrationDenyReason.INVALID_PASSWORD))
                .isEqualTo(GeihouLoginDenyReasonPublicMapper.LOGIN_CREDENTIALS_INVALID);
    }

    @Test
    void shouldMapInvalidRoleScopeToLoginDenied() {
        assertThat(GeihouLoginDenyReasonPublicMapper.toPublicReason(OrchestrationDenyReason.INVALID_ROLE_SCOPE))
                .isEqualTo(GeihouLoginDenyReasonPublicMapper.LOGIN_DENIED);
    }

    @Test
    void shouldMapRoleResolutionFailedToLoginDenied() {
        assertThat(GeihouLoginDenyReasonPublicMapper.toPublicReason(OrchestrationDenyReason.ROLE_RESOLUTION_FAILED))
                .isEqualTo(GeihouLoginDenyReasonPublicMapper.LOGIN_DENIED);
    }

    @Test
    void shouldMapTempTokenIssueFailedToLoginDenied() {
        assertThat(GeihouLoginDenyReasonPublicMapper.toPublicReason(OrchestrationDenyReason.TEMP_TOKEN_ISSUE_FAILED))
                .isEqualTo(GeihouLoginDenyReasonPublicMapper.LOGIN_DENIED);
    }

    @Test
    void shouldMapRefreshTokenIssueFailedToLoginDenied() {
        assertThat(GeihouLoginDenyReasonPublicMapper.toPublicReason(OrchestrationDenyReason.REFRESH_TOKEN_ISSUE_FAILED))
                .isEqualTo(GeihouLoginDenyReasonPublicMapper.LOGIN_DENIED);
    }

    @Test
    void shouldMapInternalErrorToLoginDenied() {
        assertThat(GeihouLoginDenyReasonPublicMapper.toPublicReason(OrchestrationDenyReason.INTERNAL_ERROR))
                .isEqualTo(GeihouLoginDenyReasonPublicMapper.LOGIN_DENIED);
    }

    @Test
    void shouldMapNullDenyReasonToLoginDenied() {
        assertThat(GeihouLoginDenyReasonPublicMapper.toPublicReason(null))
                .isEqualTo(GeihouLoginDenyReasonPublicMapper.LOGIN_DENIED);
    }

    @Test
    void shouldNeverLeakInternalEnumNameForAnyOrchestrationDenyReason() {
        EnumSet.allOf(OrchestrationDenyReason.class).forEach(reason -> {
            String publicReason = GeihouLoginDenyReasonPublicMapper.toPublicReason(reason);
            assertThat(publicReason)
                    .as("public reason for %s must not equal internal name", reason.name())
                    .isNotEqualTo(reason.name());
            assertThat(publicReason)
                    .as("public reason for %s must be one of the stable constants", reason.name())
                    .isIn(
                            GeihouLoginDenyReasonPublicMapper.LOGIN_BAD_REQUEST,
                            GeihouLoginDenyReasonPublicMapper.LOGIN_CREDENTIALS_INVALID,
                            GeihouLoginDenyReasonPublicMapper.LOGIN_CAPTCHA_INVALID,
                            GeihouLoginDenyReasonPublicMapper.LOGIN_ACCOUNT_LOCKED,
                            GeihouLoginDenyReasonPublicMapper.LOGIN_DENIED);
        });
    }

    @Test
    void shouldNotExposeSensitiveSubstringForInternalReasons() {
        // Ensure none of the sensitive internal enum names leak as substrings of the public reason
        // for the internal-only OrchestrationDenyReason values. ACTIONABLE public signals
        // (ACCOUNT_LOCKED, CAPTCHA_INVALID, INVALID_REQUEST) intentionally carry a recognizable
        // name with a LOGIN_ prefix and are excluded from this substring check; they are still
        // asserted to be not equal to the internal name in the exhaustive test above.
        EnumSet.of(
                OrchestrationDenyReason.UNKNOWN_TENANT,
                OrchestrationDenyReason.UNKNOWN_USER,
                OrchestrationDenyReason.USER_NOT_ACTIVE,
                OrchestrationDenyReason.INVALID_PASSWORD,
                OrchestrationDenyReason.INVALID_ROLE_SCOPE,
                OrchestrationDenyReason.ROLE_RESOLUTION_FAILED,
                OrchestrationDenyReason.TEMP_TOKEN_ISSUE_FAILED,
                OrchestrationDenyReason.REFRESH_TOKEN_ISSUE_FAILED,
                OrchestrationDenyReason.INTERNAL_ERROR)
                .forEach(reason -> {
                    String publicReason = GeihouLoginDenyReasonPublicMapper.toPublicReason(reason);
                    assertThat(publicReason)
                            .as("public reason for %s must not contain internal enum name", reason.name())
                            .doesNotContain(reason.name());
                });
    }

    @Test
    void shouldNotCollideWithTwoFactorDenyReasonEnumNames() {
        // The login public-safe strings must also not collide with any 2FA DenyReason enum name
        // to avoid cross-endpoint wire-contract confusion.
        EnumSet.allOf(DenyReason.class).forEach(twoFactorReason -> {
            EnumSet.allOf(OrchestrationDenyReason.class).forEach(loginReason -> {
                String publicReason = GeihouLoginDenyReasonPublicMapper.toPublicReason(loginReason);
                assertThat(publicReason)
                        .as("login public reason must not equal 2FA DenyReason name %s",
                                twoFactorReason.name())
                        .isNotEqualTo(twoFactorReason.name());
            });
        });
    }
}
