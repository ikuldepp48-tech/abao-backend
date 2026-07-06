package com.geihou.module.system.controller.admin.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.geihou.module.system.service.auth.GeihouAdminLoginOrchestratorService;
import com.geihou.module.system.service.auth.GeihouAdminLoginOrchestratorService.LoginOrchestrationResult;
import com.geihou.module.system.service.auth.GeihouAdminLoginOrchestratorService.OrchestrationDenyReason;
import com.geihou.module.system.service.auth.GeihouAdminLoginAuthenticationCommand;
import com.geihou.module.system.service.auth.GeihouTwoFactorVerifyOrchestrationService;
import com.geihou.module.system.service.auth.GeihouTwoFactorVerifyOrchestrationService.DenyReason;
import com.geihou.module.system.service.auth.GeihouTwoFactorVerifyOrchestrationService.TwoFactorVerifyResult;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GeihouAdminAuthControllerWebMvcTest {

    private GeihouAdminLoginOrchestratorService orchestratorService;
    private GeihouTwoFactorVerifyOrchestrationService twoFactorVerifyService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        orchestratorService = mock(GeihouAdminLoginOrchestratorService.class);
        twoFactorVerifyService = mock(GeihouTwoFactorVerifyOrchestrationService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new GeihouAdminAuthController(orchestratorService, twoFactorVerifyService))
                .build();
    }

    @Test
    void shouldReturnLoginRespJsonShape() throws Exception {
        when(orchestratorService.orchestrate(any()))
                .thenReturn(LoginOrchestrationResult.success(
                        "access-token", 3600L, "refresh-token", 2001L, 1L, "OWNER"));

        mockMvc.perform(post("/admin-api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "1.2.3.4, 5.6.7.8")
                        .header("User-Agent", "Geihou-WebMvc-Agent")
                        .content(loginRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("success"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.data.expiresIn").value(3600))
                .andExpect(jsonPath("$.data.userInfo.userId").value(2001))
                .andExpect(jsonPath("$.data.userInfo.tenantId").value(1))
                .andExpect(jsonPath("$.data.userInfo.userRole").value("OWNER"));

        ArgumentCaptor<GeihouAdminLoginAuthenticationCommand> captor =
                ArgumentCaptor.forClass(GeihouAdminLoginAuthenticationCommand.class);
        verify(orchestratorService).orchestrate(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().clientIp()).isEqualTo("1.2.3.4");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().userAgent()).isEqualTo("Geihou-WebMvc-Agent");
    }

    @Test
    void shouldReturnTwoFactorRequiredJsonShape() throws Exception {
        when(orchestratorService.orchestrate(any()))
                .thenReturn(LoginOrchestrationResult.twoFactorRequired(
                        "temp-token", LocalDateTime.parse("2026-06-20T03:05:00"), "TOTP"));

        mockMvc.perform(post("/admin-api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("success"))
                .andExpect(jsonPath("$.data.twoFactorRequired").value(true))
                .andExpect(jsonPath("$.data.tempToken").value("temp-token"))
                .andExpect(jsonPath("$.data.method").value("TOTP"));
    }

    @Test
    void shouldReturnDeniedJsonShapeForInvalidPassword() throws Exception {
        when(orchestratorService.orchestrate(any()))
                .thenReturn(LoginOrchestrationResult.denied(OrchestrationDenyReason.INVALID_PASSWORD));

        mockMvc.perform(post("/admin-api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401000))
                .andExpect(jsonPath("$.msg").value("LOGIN_CREDENTIALS_INVALID"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldReturnDeniedJsonShapeForUnknownUser() throws Exception {
        when(orchestratorService.orchestrate(any()))
                .thenReturn(LoginOrchestrationResult.denied(OrchestrationDenyReason.UNKNOWN_USER));

        mockMvc.perform(post("/admin-api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401000))
                .andExpect(jsonPath("$.msg").value("LOGIN_CREDENTIALS_INVALID"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldReturnDeniedJsonShapeForUnknownTenant() throws Exception {
        when(orchestratorService.orchestrate(any()))
                .thenReturn(LoginOrchestrationResult.denied(OrchestrationDenyReason.UNKNOWN_TENANT));

        mockMvc.perform(post("/admin-api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401000))
                .andExpect(jsonPath("$.msg").value("LOGIN_CREDENTIALS_INVALID"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldReturnDeniedJsonShapeForAccountLocked() throws Exception {
        when(orchestratorService.orchestrate(any()))
                .thenReturn(LoginOrchestrationResult.denied(OrchestrationDenyReason.ACCOUNT_LOCKED));

        mockMvc.perform(post("/admin-api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401000))
                .andExpect(jsonPath("$.msg").value("LOGIN_ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldReturnDeniedJsonShapeForCaptchaInvalid() throws Exception {
        when(orchestratorService.orchestrate(any()))
                .thenReturn(LoginOrchestrationResult.denied(OrchestrationDenyReason.CAPTCHA_INVALID));

        mockMvc.perform(post("/admin-api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401000))
                .andExpect(jsonPath("$.msg").value("LOGIN_CAPTCHA_INVALID"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldReturnDeniedJsonShapeForRoleResolutionFailed() throws Exception {
        when(orchestratorService.orchestrate(any()))
                .thenReturn(LoginOrchestrationResult.denied(OrchestrationDenyReason.ROLE_RESOLUTION_FAILED));

        mockMvc.perform(post("/admin-api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401000))
                .andExpect(jsonPath("$.msg").value("LOGIN_DENIED"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldReturnDeniedJsonShapeForInternalError() throws Exception {
        when(orchestratorService.orchestrate(any()))
                .thenReturn(LoginOrchestrationResult.denied(OrchestrationDenyReason.INTERNAL_ERROR));

        mockMvc.perform(post("/admin-api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401000))
                .andExpect(jsonPath("$.msg").value("LOGIN_DENIED"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldReturnTwoFactorVerifySuccessJsonShape() throws Exception {
        when(twoFactorVerifyService.verify("temp-token", "123456", "1.2.3.4", "Geihou-WebMvc-Agent"))
                .thenReturn(TwoFactorVerifyResult.success(
                        "access-token", 3600L, "refresh-token", 2001L, 1L, "OWNER"));

        mockMvc.perform(post("/admin-api/auth/two-factor-verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "1.2.3.4, 5.6.7.8")
                        .header("User-Agent", "Geihou-WebMvc-Agent")
                        .content(twoFactorVerifyRequestJson("123456")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("success"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.data.expiresIn").value(3600))
                .andExpect(jsonPath("$.data.userInfo.userId").value(2001))
                .andExpect(jsonPath("$.data.userInfo.tenantId").value(1))
                .andExpect(jsonPath("$.data.userInfo.userRole").value("OWNER"));
    }

    @Test
    void shouldReturnTwoFactorVerifyDeniedJsonShape() throws Exception {
        when(twoFactorVerifyService.verify("temp-token", "000000", "1.2.3.4", "Geihou-WebMvc-Agent"))
                .thenReturn(TwoFactorVerifyResult.denied(DenyReason.TOTP_MISMATCH));

        mockMvc.perform(post("/admin-api/auth/two-factor-verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "1.2.3.4, 5.6.7.8")
                        .header("User-Agent", "Geihou-WebMvc-Agent")
                        .content(twoFactorVerifyRequestJson("000000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401000))
                .andExpect(jsonPath("$.msg").value("TWO_FACTOR_CODE_INVALID"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldReturnTwoFactorVerifyDeniedJsonShapeForTempTokenAttemptsExceeded() throws Exception {
        when(twoFactorVerifyService.verify("temp-token", "000000", "1.2.3.4", "Geihou-WebMvc-Agent"))
                .thenReturn(TwoFactorVerifyResult.denied(DenyReason.TEMP_TOKEN_ATTEMPTS_EXCEEDED));

        mockMvc.perform(post("/admin-api/auth/two-factor-verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "1.2.3.4, 5.6.7.8")
                        .header("User-Agent", "Geihou-WebMvc-Agent")
                        .content(twoFactorVerifyRequestJson("000000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401000))
                .andExpect(jsonPath("$.msg").value("TWO_FACTOR_ATTEMPTS_EXCEEDED"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldReturnTwoFactorVerifyDeniedJsonShapeForTempTokenInvalid() throws Exception {
        when(twoFactorVerifyService.verify("temp-token", "000000", "1.2.3.4", "Geihou-WebMvc-Agent"))
                .thenReturn(TwoFactorVerifyResult.denied(DenyReason.TEMP_TOKEN_INVALID));

        mockMvc.perform(post("/admin-api/auth/two-factor-verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "1.2.3.4, 5.6.7.8")
                        .header("User-Agent", "Geihou-WebMvc-Agent")
                        .content(twoFactorVerifyRequestJson("000000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401000))
                .andExpect(jsonPath("$.msg").value("TWO_FACTOR_TOKEN_INVALID"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldNotExposeInternalUserNotFoundInTwoFactorVerifyDeniedResponse() throws Exception {
        when(twoFactorVerifyService.verify("temp-token", "123456", "1.2.3.4", "Geihou-WebMvc-Agent"))
                .thenReturn(TwoFactorVerifyResult.denied(DenyReason.USER_NOT_FOUND));

        mockMvc.perform(post("/admin-api/auth/two-factor-verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "1.2.3.4, 5.6.7.8")
                        .header("User-Agent", "Geihou-WebMvc-Agent")
                        .content(twoFactorVerifyRequestJson("123456")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401000))
                .andExpect(jsonPath("$.msg").value("TWO_FACTOR_DENIED"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldNotExposeInternalTokenIssuanceFailedInTwoFactorVerifyDeniedResponse() throws Exception {
        when(twoFactorVerifyService.verify("temp-token", "123456", "1.2.3.4", "Geihou-WebMvc-Agent"))
                .thenReturn(TwoFactorVerifyResult.denied(DenyReason.TOKEN_ISSUANCE_FAILED));

        mockMvc.perform(post("/admin-api/auth/two-factor-verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "1.2.3.4, 5.6.7.8")
                        .header("User-Agent", "Geihou-WebMvc-Agent")
                        .content(twoFactorVerifyRequestJson("123456")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401000))
                .andExpect(jsonPath("$.msg").value("TWO_FACTOR_DENIED"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    private static String loginRequestJson() {
        return """
                {
                  "username": "owner",
                  "password": "secret",
                  "tenantCode": "abao",
                  "captcha": "1234",
                  "captchaKey": "captcha-key"
                }
                """;
    }

    private static String twoFactorVerifyRequestJson(String code) {
        return """
                {
                  "tempToken": "temp-token",
                  "code": "%s"
                }
                """.formatted(code);
    }
}
