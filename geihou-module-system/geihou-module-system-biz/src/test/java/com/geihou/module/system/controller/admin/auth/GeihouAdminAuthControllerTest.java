package com.geihou.module.system.controller.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.geihou.common.pojo.CommonResult;
import com.geihou.module.system.controller.admin.auth.vo.GeihouAdminLoginReqVO;
import com.geihou.module.system.controller.admin.auth.vo.GeihouAdminLoginRespVO;
import com.geihou.module.system.controller.admin.auth.vo.GeihouAdminTwoFactorRequiredVO;
import com.geihou.module.system.controller.admin.auth.vo.GeihouAdminTwoFactorVerifyReqVO;
import com.geihou.module.system.service.auth.GeihouAdminLoginAuthenticationCommand;
import com.geihou.module.system.service.auth.GeihouAdminLoginOrchestratorService;
import com.geihou.module.system.service.auth.GeihouAdminLoginOrchestratorService.LoginOrchestrationResult;
import com.geihou.module.system.service.auth.GeihouAdminLoginOrchestratorService.OrchestrationDenyReason;
import com.geihou.module.system.service.auth.GeihouTwoFactorVerifyOrchestrationService;
import com.geihou.module.system.service.auth.GeihouTwoFactorVerifyOrchestrationService.DenyReason;
import com.geihou.module.system.service.auth.GeihouTwoFactorVerifyOrchestrationService.TwoFactorVerifyResult;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class GeihouAdminAuthControllerTest {

    private final GeihouAdminLoginOrchestratorService orchestratorService =
            mock(GeihouAdminLoginOrchestratorService.class);
    private final GeihouTwoFactorVerifyOrchestrationService twoFactorVerifyService =
            mock(GeihouTwoFactorVerifyOrchestrationService.class);
    private final GeihouAdminAuthController controller =
            new GeihouAdminAuthController(orchestratorService, twoFactorVerifyService);

    @Test
    void shouldMapSuccessResultToLoginRespVo() {
        GeihouAdminLoginReqVO request = request();
        when(orchestratorService.orchestrate(Mockito.any()))
                .thenReturn(LoginOrchestrationResult.success(
                        "access-token", 3600L, "refresh-token", 2001L, 1L, "OWNER"));

        CommonResult<Object> response = controller.login(request, requestContext());

        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getData()).isInstanceOf(GeihouAdminLoginRespVO.class);
        GeihouAdminLoginRespVO data = (GeihouAdminLoginRespVO) response.getData();
        assertThat(data.accessToken()).isEqualTo("access-token");
        assertThat(data.refreshToken()).isEqualTo("refresh-token");
        assertThat(data.expiresIn()).isEqualTo(3600L);
        assertThat(data.userInfo().userId()).isEqualTo(2001L);
        assertThat(data.userInfo().tenantId()).isEqualTo(1L);
        assertThat(data.userInfo().userRole()).isEqualTo("OWNER");

        ArgumentCaptor<GeihouAdminLoginAuthenticationCommand> captor =
                ArgumentCaptor.forClass(GeihouAdminLoginAuthenticationCommand.class);
        Mockito.verify(orchestratorService).orchestrate(captor.capture());
        assertThat(captor.getValue().username()).isEqualTo("owner");
        assertThat(captor.getValue().password()).isEqualTo("secret");
        assertThat(captor.getValue().tenantCode()).isEqualTo("abao");
        assertThat(captor.getValue().captcha()).isEqualTo("1234");
        assertThat(captor.getValue().captchaKey()).isEqualTo("captcha-key");
        assertThat(captor.getValue().clientIp()).isEqualTo("1.2.3.4");
        assertThat(captor.getValue().userAgent()).isEqualTo("Geihou-Test-Agent");
    }

    @Test
    void shouldMapTwoFactorRequiredResultToTwoFactorVo() {
        when(orchestratorService.orchestrate(Mockito.any()))
                .thenReturn(LoginOrchestrationResult.twoFactorRequired(
                        "temp-token", LocalDateTime.parse("2026-06-20T03:05:00"), "TOTP"));

        CommonResult<Object> response = controller.login(request(), requestContext());

        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getData()).isInstanceOf(GeihouAdminTwoFactorRequiredVO.class);
        GeihouAdminTwoFactorRequiredVO data = (GeihouAdminTwoFactorRequiredVO) response.getData();
        assertThat(data.twoFactorRequired()).isTrue();
        assertThat(data.tempToken()).isEqualTo("temp-token");
        assertThat(data.method()).isEqualTo("TOTP");
    }

    @Test
    void shouldMapDeniedResultToPublicSafeReasonForInvalidPassword() {
        when(orchestratorService.orchestrate(Mockito.any()))
                .thenReturn(LoginOrchestrationResult.denied(OrchestrationDenyReason.INVALID_PASSWORD));

        CommonResult<Object> response = controller.login(request(), requestContext());

        assertThat(response.getCode()).isEqualTo(401000);
        assertThat(response.getMsg()).isEqualTo("LOGIN_CREDENTIALS_INVALID");
        assertThat(response.getMsg()).doesNotContain("INVALID_PASSWORD");
        assertThat(response.getData()).isNull();
    }

    @Test
    void shouldMapDeniedResultToPublicSafeReasonForUnknownUser() {
        when(orchestratorService.orchestrate(Mockito.any()))
                .thenReturn(LoginOrchestrationResult.denied(OrchestrationDenyReason.UNKNOWN_USER));

        CommonResult<Object> response = controller.login(request(), requestContext());

        assertThat(response.getCode()).isEqualTo(401000);
        assertThat(response.getMsg()).isEqualTo("LOGIN_CREDENTIALS_INVALID");
        assertThat(response.getMsg()).doesNotContain("UNKNOWN_USER");
        assertThat(response.getData()).isNull();
    }

    @Test
    void shouldMapDeniedResultToPublicSafeReasonForUnknownTenant() {
        when(orchestratorService.orchestrate(Mockito.any()))
                .thenReturn(LoginOrchestrationResult.denied(OrchestrationDenyReason.UNKNOWN_TENANT));

        CommonResult<Object> response = controller.login(request(), requestContext());

        assertThat(response.getCode()).isEqualTo(401000);
        assertThat(response.getMsg()).isEqualTo("LOGIN_CREDENTIALS_INVALID");
        assertThat(response.getMsg()).doesNotContain("UNKNOWN_TENANT");
        assertThat(response.getData()).isNull();
    }

    @Test
    void shouldMapDeniedResultToPublicSafeReasonForAccountLocked() {
        when(orchestratorService.orchestrate(Mockito.any()))
                .thenReturn(LoginOrchestrationResult.denied(OrchestrationDenyReason.ACCOUNT_LOCKED));

        CommonResult<Object> response = controller.login(request(), requestContext());

        assertThat(response.getCode()).isEqualTo(401000);
        assertThat(response.getMsg()).isEqualTo("LOGIN_ACCOUNT_LOCKED");
        assertThat(response.getMsg()).isNotEqualTo("ACCOUNT_LOCKED");
        assertThat(response.getData()).isNull();
    }

    @Test
    void shouldMapDeniedResultToPublicSafeReasonForCaptchaInvalid() {
        when(orchestratorService.orchestrate(Mockito.any()))
                .thenReturn(LoginOrchestrationResult.denied(OrchestrationDenyReason.CAPTCHA_INVALID));

        CommonResult<Object> response = controller.login(request(), requestContext());

        assertThat(response.getCode()).isEqualTo(401000);
        assertThat(response.getMsg()).isEqualTo("LOGIN_CAPTCHA_INVALID");
        assertThat(response.getMsg()).isNotEqualTo("CAPTCHA_INVALID");
        assertThat(response.getData()).isNull();
    }

    @Test
    void shouldMapDeniedResultToPublicSafeReasonForInvalidRequest() {
        when(orchestratorService.orchestrate(Mockito.any()))
                .thenReturn(LoginOrchestrationResult.denied(OrchestrationDenyReason.INVALID_REQUEST));

        CommonResult<Object> response = controller.login(request(), requestContext());

        assertThat(response.getCode()).isEqualTo(401000);
        assertThat(response.getMsg()).isEqualTo("LOGIN_BAD_REQUEST");
        assertThat(response.getMsg()).doesNotContain("INVALID_REQUEST");
        assertThat(response.getData()).isNull();
    }

    @Test
    void shouldMapDeniedResultToPublicSafeReasonForUserNotActive() {
        when(orchestratorService.orchestrate(Mockito.any()))
                .thenReturn(LoginOrchestrationResult.denied(OrchestrationDenyReason.USER_NOT_ACTIVE));

        CommonResult<Object> response = controller.login(request(), requestContext());

        assertThat(response.getCode()).isEqualTo(401000);
        assertThat(response.getMsg()).isEqualTo("LOGIN_DENIED");
        assertThat(response.getMsg()).doesNotContain("USER_NOT_ACTIVE");
        assertThat(response.getData()).isNull();
    }

    @Test
    void shouldMapDeniedResultToPublicSafeReasonForInvalidRoleScope() {
        when(orchestratorService.orchestrate(Mockito.any()))
                .thenReturn(LoginOrchestrationResult.denied(OrchestrationDenyReason.INVALID_ROLE_SCOPE));

        CommonResult<Object> response = controller.login(request(), requestContext());

        assertThat(response.getCode()).isEqualTo(401000);
        assertThat(response.getMsg()).isEqualTo("LOGIN_DENIED");
        assertThat(response.getMsg()).doesNotContain("INVALID_ROLE_SCOPE");
        assertThat(response.getData()).isNull();
    }

    @Test
    void shouldMapDeniedResultToPublicSafeReasonForRoleResolutionFailed() {
        when(orchestratorService.orchestrate(Mockito.any()))
                .thenReturn(LoginOrchestrationResult.denied(OrchestrationDenyReason.ROLE_RESOLUTION_FAILED));

        CommonResult<Object> response = controller.login(request(), requestContext());

        assertThat(response.getCode()).isEqualTo(401000);
        assertThat(response.getMsg()).isEqualTo("LOGIN_DENIED");
        assertThat(response.getMsg()).doesNotContain("ROLE_RESOLUTION_FAILED");
        assertThat(response.getData()).isNull();
    }

    @Test
    void shouldMapDeniedResultToPublicSafeReasonForTempTokenIssueFailed() {
        when(orchestratorService.orchestrate(Mockito.any()))
                .thenReturn(LoginOrchestrationResult.denied(OrchestrationDenyReason.TEMP_TOKEN_ISSUE_FAILED));

        CommonResult<Object> response = controller.login(request(), requestContext());

        assertThat(response.getCode()).isEqualTo(401000);
        assertThat(response.getMsg()).isEqualTo("LOGIN_DENIED");
        assertThat(response.getMsg()).doesNotContain("TEMP_TOKEN_ISSUE_FAILED");
        assertThat(response.getData()).isNull();
    }

    @Test
    void shouldMapDeniedResultToPublicSafeReasonForRefreshTokenIssueFailed() {
        when(orchestratorService.orchestrate(Mockito.any()))
                .thenReturn(LoginOrchestrationResult.denied(OrchestrationDenyReason.REFRESH_TOKEN_ISSUE_FAILED));

        CommonResult<Object> response = controller.login(request(), requestContext());

        assertThat(response.getCode()).isEqualTo(401000);
        assertThat(response.getMsg()).isEqualTo("LOGIN_DENIED");
        assertThat(response.getMsg()).doesNotContain("REFRESH_TOKEN_ISSUE_FAILED");
        assertThat(response.getData()).isNull();
    }

    @Test
    void shouldMapDeniedResultToPublicSafeReasonForInternalError() {
        when(orchestratorService.orchestrate(Mockito.any()))
                .thenReturn(LoginOrchestrationResult.denied(OrchestrationDenyReason.INTERNAL_ERROR));

        CommonResult<Object> response = controller.login(request(), requestContext());

        assertThat(response.getCode()).isEqualTo(401000);
        assertThat(response.getMsg()).isEqualTo("LOGIN_DENIED");
        assertThat(response.getMsg()).doesNotContain("INTERNAL_ERROR");
        assertThat(response.getData()).isNull();
    }

    @Test
    void shouldHandleNullDenyReasonAsLoginDenied() {
        // Construct a DENIED result with null denyReason directly via the record constructor,
        // because LoginOrchestrationResult.denied() rejects null. This exercises the controller's
        // defensive null fallback.
        LoginOrchestrationResult resultWithNullDenyReason = new LoginOrchestrationResult(
                com.geihou.module.system.service.auth.GeihouAdminLoginOrchestratorService.OrchestrationStatus.DENIED,
                null, 0L, null, null, null, null, null, null, null, null);
        when(orchestratorService.orchestrate(Mockito.any()))
                .thenReturn(resultWithNullDenyReason);

        CommonResult<Object> response = controller.login(request(), requestContext());

        assertThat(response.getCode()).isEqualTo(401000);
        assertThat(response.getMsg()).isEqualTo("LOGIN_DENIED");
        assertThat(response.getData()).isNull();
    }

    @Test
    void shouldMapTwoFactorVerifySuccessToLoginRespVo() {
        GeihouAdminTwoFactorVerifyReqVO request = twoFactorVerifyRequest();
        when(twoFactorVerifyService.verify("temp-token", "123456", "1.2.3.4", "Geihou-Test-Agent"))
                .thenReturn(TwoFactorVerifyResult.success(
                        "access-token", 3600L, "refresh-token", 2001L, 1L, "OWNER"));

        CommonResult<Object> response = controller.twoFactorVerify(request, requestContext());

        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getData()).isInstanceOf(GeihouAdminLoginRespVO.class);
        GeihouAdminLoginRespVO data = (GeihouAdminLoginRespVO) response.getData();
        assertThat(data.accessToken()).isEqualTo("access-token");
        assertThat(data.refreshToken()).isEqualTo("refresh-token");
        assertThat(data.expiresIn()).isEqualTo(3600L);
        assertThat(data.userInfo().userId()).isEqualTo(2001L);
        assertThat(data.userInfo().tenantId()).isEqualTo(1L);
        assertThat(data.userInfo().userRole()).isEqualTo("OWNER");
    }

    @Test
    void shouldMapTwoFactorVerifyDeniedToPublicSafeReasonForTotpMismatch() {
        when(twoFactorVerifyService.verify("temp-token", "000000", "1.2.3.4", "Geihou-Test-Agent"))
                .thenReturn(TwoFactorVerifyResult.denied(DenyReason.TOTP_MISMATCH));

        CommonResult<Object> response = controller.twoFactorVerify(twoFactorVerifyRequest("000000"), requestContext());

        assertThat(response.getCode()).isEqualTo(401000);
        assertThat(response.getMsg()).isEqualTo("TWO_FACTOR_CODE_INVALID");
        assertThat(response.getData()).isNull();
    }

    @Test
    void shouldMapTwoFactorVerifyDeniedToPublicSafeReasonForTempTokenAttemptsExceeded() {
        when(twoFactorVerifyService.verify("temp-token", "000000", "1.2.3.4", "Geihou-Test-Agent"))
                .thenReturn(TwoFactorVerifyResult.denied(DenyReason.TEMP_TOKEN_ATTEMPTS_EXCEEDED));

        CommonResult<Object> response = controller.twoFactorVerify(twoFactorVerifyRequest("000000"), requestContext());

        assertThat(response.getCode()).isEqualTo(401000);
        assertThat(response.getMsg()).isEqualTo("TWO_FACTOR_ATTEMPTS_EXCEEDED");
        assertThat(response.getData()).isNull();
    }

    @Test
    void shouldMapTwoFactorVerifyDeniedToPublicSafeReasonForTempTokenInvalid() {
        when(twoFactorVerifyService.verify("temp-token", "000000", "1.2.3.4", "Geihou-Test-Agent"))
                .thenReturn(TwoFactorVerifyResult.denied(DenyReason.TEMP_TOKEN_INVALID));

        CommonResult<Object> response = controller.twoFactorVerify(twoFactorVerifyRequest("000000"), requestContext());

        assertThat(response.getCode()).isEqualTo(401000);
        assertThat(response.getMsg()).isEqualTo("TWO_FACTOR_TOKEN_INVALID");
        assertThat(response.getData()).isNull();
    }

    @Test
    void shouldMapTwoFactorVerifyDeniedToPublicSafeReasonForTempTokenAlreadyConsumed() {
        when(twoFactorVerifyService.verify("temp-token", "000000", "1.2.3.4", "Geihou-Test-Agent"))
                .thenReturn(TwoFactorVerifyResult.denied(DenyReason.TEMP_TOKEN_ALREADY_CONSUMED));

        CommonResult<Object> response = controller.twoFactorVerify(twoFactorVerifyRequest("000000"), requestContext());

        assertThat(response.getCode()).isEqualTo(401000);
        assertThat(response.getMsg()).isEqualTo("TWO_FACTOR_TOKEN_INVALID");
        assertThat(response.getData()).isNull();
    }

    @Test
    void shouldMapInternalUserNotFoundToGenericTwoFactorDenied() {
        when(twoFactorVerifyService.verify("temp-token", "123456", "1.2.3.4", "Geihou-Test-Agent"))
                .thenReturn(TwoFactorVerifyResult.denied(DenyReason.USER_NOT_FOUND));

        CommonResult<Object> response = controller.twoFactorVerify(twoFactorVerifyRequest(), requestContext());

        assertThat(response.getCode()).isEqualTo(401000);
        assertThat(response.getMsg()).isEqualTo("TWO_FACTOR_DENIED");
        assertThat(response.getMsg()).doesNotContain("USER_NOT_FOUND");
        assertThat(response.getData()).isNull();
    }

    @Test
    void shouldMapInternalUserInactiveToGenericTwoFactorDenied() {
        when(twoFactorVerifyService.verify("temp-token", "123456", "1.2.3.4", "Geihou-Test-Agent"))
                .thenReturn(TwoFactorVerifyResult.denied(DenyReason.USER_INACTIVE));

        CommonResult<Object> response = controller.twoFactorVerify(twoFactorVerifyRequest(), requestContext());

        assertThat(response.getCode()).isEqualTo(401000);
        assertThat(response.getMsg()).isEqualTo("TWO_FACTOR_DENIED");
        assertThat(response.getMsg()).doesNotContain("USER_INACTIVE");
        assertThat(response.getData()).isNull();
    }

    @Test
    void shouldMapInternalTokenIssuanceFailedToGenericTwoFactorDenied() {
        when(twoFactorVerifyService.verify("temp-token", "123456", "1.2.3.4", "Geihou-Test-Agent"))
                .thenReturn(TwoFactorVerifyResult.denied(DenyReason.TOKEN_ISSUANCE_FAILED));

        CommonResult<Object> response = controller.twoFactorVerify(twoFactorVerifyRequest(), requestContext());

        assertThat(response.getCode()).isEqualTo(401000);
        assertThat(response.getMsg()).isEqualTo("TWO_FACTOR_DENIED");
        assertThat(response.getMsg()).doesNotContain("TOKEN_ISSUANCE_FAILED");
        assertThat(response.getData()).isNull();
    }

    @Test
    void shouldMapInternalCrossContextMismatchToGenericTwoFactorDenied() {
        when(twoFactorVerifyService.verify("temp-token", "123456", "1.2.3.4", "Geihou-Test-Agent"))
                .thenReturn(TwoFactorVerifyResult.denied(DenyReason.CROSS_CONTEXT_MISMATCH));

        CommonResult<Object> response = controller.twoFactorVerify(twoFactorVerifyRequest(), requestContext());

        assertThat(response.getCode()).isEqualTo(401000);
        assertThat(response.getMsg()).isEqualTo("TWO_FACTOR_DENIED");
        assertThat(response.getMsg()).doesNotContain("CROSS_CONTEXT_MISMATCH");
        assertThat(response.getData()).isNull();
    }

    @Test
    void shouldHandleNullTwoFactorVerifyRequestGracefully() {
        when(twoFactorVerifyService.verify(null, null, "1.2.3.4", "Geihou-Test-Agent"))
                .thenReturn(TwoFactorVerifyResult.denied(DenyReason.TEMP_TOKEN_INVALID));

        CommonResult<Object> response = controller.twoFactorVerify(null, requestContext());

        assertThat(response.getCode()).isEqualTo(401000);
        assertThat(response.getMsg()).isEqualTo("TWO_FACTOR_TOKEN_INVALID");
        assertThat(response.getData()).isNull();
    }

    private static GeihouAdminLoginReqVO request() {
        GeihouAdminLoginReqVO request = new GeihouAdminLoginReqVO();
        request.setUsername("owner");
        request.setPassword("secret");
        request.setTenantCode("abao");
        request.setCaptcha("1234");
        request.setCaptchaKey("captcha-key");
        return request;
    }

    private static GeihouAdminTwoFactorVerifyReqVO twoFactorVerifyRequest() {
        return twoFactorVerifyRequest("123456");
    }

    private static GeihouAdminTwoFactorVerifyReqVO twoFactorVerifyRequest(String code) {
        GeihouAdminTwoFactorVerifyReqVO request = new GeihouAdminTwoFactorVerifyReqVO();
        request.setTempToken("temp-token");
        request.setCode(code);
        return request;
    }

    private static HttpServletRequest requestContext() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4, 5.6.7.8");
        when(request.getHeader("User-Agent")).thenReturn("Geihou-Test-Agent");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        return request;
    }
}
