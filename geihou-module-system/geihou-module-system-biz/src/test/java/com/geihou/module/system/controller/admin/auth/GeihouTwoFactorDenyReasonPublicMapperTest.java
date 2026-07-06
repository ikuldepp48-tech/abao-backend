package com.geihou.module.system.controller.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.geihou.module.system.service.auth.GeihouTwoFactorVerifyOrchestrationService.DenyReason;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class GeihouTwoFactorDenyReasonPublicMapperTest {

    @Test
    void shouldMapTotpMismatchToTwoFactorCodeInvalid() {
        assertThat(GeihouTwoFactorDenyReasonPublicMapper.toPublicReason(DenyReason.TOTP_MISMATCH))
                .isEqualTo(GeihouTwoFactorDenyReasonPublicMapper.TWO_FACTOR_CODE_INVALID);
    }

    @Test
    void shouldMapTempTokenAttemptsExceededToPublicAttemptsExceeded() {
        assertThat(GeihouTwoFactorDenyReasonPublicMapper.toPublicReason(DenyReason.TEMP_TOKEN_ATTEMPTS_EXCEEDED))
                .isEqualTo(GeihouTwoFactorDenyReasonPublicMapper.TWO_FACTOR_ATTEMPTS_EXCEEDED);
    }

    @Test
    void shouldMapTempTokenInvalidToTwoFactorTokenInvalid() {
        assertThat(GeihouTwoFactorDenyReasonPublicMapper.toPublicReason(DenyReason.TEMP_TOKEN_INVALID))
                .isEqualTo(GeihouTwoFactorDenyReasonPublicMapper.TWO_FACTOR_TOKEN_INVALID);
    }

    @Test
    void shouldMapTempTokenAlreadyConsumedToTwoFactorTokenInvalid() {
        assertThat(GeihouTwoFactorDenyReasonPublicMapper.toPublicReason(DenyReason.TEMP_TOKEN_ALREADY_CONSUMED))
                .isEqualTo(GeihouTwoFactorDenyReasonPublicMapper.TWO_FACTOR_TOKEN_INVALID);
    }

    @Test
    void shouldMapUserNotFoundToGenericDenied() {
        assertThat(GeihouTwoFactorDenyReasonPublicMapper.toPublicReason(DenyReason.USER_NOT_FOUND))
                .isEqualTo(GeihouTwoFactorDenyReasonPublicMapper.TWO_FACTOR_DENIED);
    }

    @Test
    void shouldMapUserSoftDeletedToGenericDenied() {
        assertThat(GeihouTwoFactorDenyReasonPublicMapper.toPublicReason(DenyReason.USER_SOFT_DELETED))
                .isEqualTo(GeihouTwoFactorDenyReasonPublicMapper.TWO_FACTOR_DENIED);
    }

    @Test
    void shouldMapUserInactiveToGenericDenied() {
        assertThat(GeihouTwoFactorDenyReasonPublicMapper.toPublicReason(DenyReason.USER_INACTIVE))
                .isEqualTo(GeihouTwoFactorDenyReasonPublicMapper.TWO_FACTOR_DENIED);
    }

    @Test
    void shouldMapCrossContextMismatchToGenericDenied() {
        assertThat(GeihouTwoFactorDenyReasonPublicMapper.toPublicReason(DenyReason.CROSS_CONTEXT_MISMATCH))
                .isEqualTo(GeihouTwoFactorDenyReasonPublicMapper.TWO_FACTOR_DENIED);
    }

    @Test
    void shouldMapTwoFactorConfigIncompleteToGenericDenied() {
        assertThat(GeihouTwoFactorDenyReasonPublicMapper.toPublicReason(DenyReason.TWO_FACTOR_CONFIG_INCOMPLETE))
                .isEqualTo(GeihouTwoFactorDenyReasonPublicMapper.TWO_FACTOR_DENIED);
    }

    @Test
    void shouldMapInvalidRoleScopeToGenericDenied() {
        assertThat(GeihouTwoFactorDenyReasonPublicMapper.toPublicReason(DenyReason.INVALID_ROLE_SCOPE))
                .isEqualTo(GeihouTwoFactorDenyReasonPublicMapper.TWO_FACTOR_DENIED);
    }

    @Test
    void shouldMapRefreshTokenIssueFailedToGenericDenied() {
        assertThat(GeihouTwoFactorDenyReasonPublicMapper.toPublicReason(DenyReason.REFRESH_TOKEN_ISSUE_FAILED))
                .isEqualTo(GeihouTwoFactorDenyReasonPublicMapper.TWO_FACTOR_DENIED);
    }

    @Test
    void shouldMapTokenIssuanceFailedToGenericDenied() {
        assertThat(GeihouTwoFactorDenyReasonPublicMapper.toPublicReason(DenyReason.TOKEN_ISSUANCE_FAILED))
                .isEqualTo(GeihouTwoFactorDenyReasonPublicMapper.TWO_FACTOR_DENIED);
    }

    @Test
    void shouldMapNullDenyReasonToTwoFactorTokenInvalid() {
        assertThat(GeihouTwoFactorDenyReasonPublicMapper.toPublicReason(null))
                .isEqualTo(GeihouTwoFactorDenyReasonPublicMapper.TWO_FACTOR_TOKEN_INVALID);
    }

    @Test
    void shouldNeverLeakInternalEnumNameForAnyDenyReason() {
        EnumSet.allOf(DenyReason.class).forEach(reason -> {
            String publicReason = GeihouTwoFactorDenyReasonPublicMapper.toPublicReason(reason);
            assertThat(publicReason)
                    .as("public reason for %s must not equal internal name", reason.name())
                    .isNotEqualTo(reason.name());
            assertThat(publicReason)
                    .as("public reason for %s must be one of the stable constants", reason.name())
                    .isIn(
                            GeihouTwoFactorDenyReasonPublicMapper.TWO_FACTOR_TOKEN_INVALID,
                            GeihouTwoFactorDenyReasonPublicMapper.TWO_FACTOR_CODE_INVALID,
                            GeihouTwoFactorDenyReasonPublicMapper.TWO_FACTOR_ATTEMPTS_EXCEEDED,
                            GeihouTwoFactorDenyReasonPublicMapper.TWO_FACTOR_DENIED);
        });
    }

    @Test
    void shouldNotExposeSensitiveSubstringForInternalReasons() {
        // Ensure none of the sensitive internal enum names leak as substrings of the public reason
        // for the internal-only DenyReason values.
        EnumSet.of(
                DenyReason.USER_NOT_FOUND,
                DenyReason.USER_SOFT_DELETED,
                DenyReason.USER_INACTIVE,
                DenyReason.CROSS_CONTEXT_MISMATCH,
                DenyReason.TWO_FACTOR_CONFIG_INCOMPLETE,
                DenyReason.INVALID_ROLE_SCOPE,
                DenyReason.REFRESH_TOKEN_ISSUE_FAILED,
                DenyReason.TOKEN_ISSUANCE_FAILED)
                .forEach(reason -> {
                    String publicReason = GeihouTwoFactorDenyReasonPublicMapper.toPublicReason(reason);
                    assertThat(publicReason)
                            .as("public reason for %s must not contain internal enum name", reason.name())
                            .doesNotContain(reason.name());
                });
    }
}
