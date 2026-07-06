package com.geihou.bootstrap.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.geihou.framework.security.core.constant.GeihouAuthErrorCodes;
import com.geihou.framework.security.core.context.GeihouPrincipal;
import com.geihou.framework.security.core.spi.TokenValidationResult;
import com.geihou.framework.security.core.spi.TokenValidator;
import com.geihou.module.system.controller.admin.auth.GeihouAdminAuthController;
import com.geihou.module.system.service.auth.GeihouAdminLoginOrchestratorService;
import com.geihou.module.system.service.auth.GeihouAdminLoginOrchestratorService.LoginOrchestrationResult;
import com.geihou.module.system.service.auth.GeihouTwoFactorVerifyOrchestrationService;
import com.geihou.module.system.service.auth.GeihouTwoFactorVerifyOrchestrationService.TwoFactorVerifyResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = GeihouAdminRouteGuardRuntimeSmokeTest.SmokeApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.web-application-type=servlet")
class GeihouAdminRouteGuardRuntimeSmokeTest {

    private static final String VALID_TOKEN = "smoke-valid-token";

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldRejectMissingAuthorizationOnProtectedPath() {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                "/admin-api/runtime-smoke/protected", JsonNode.class);

        assertAuthError(response, GeihouAuthErrorCodes.UNAUTHORIZED);
    }

    @Test
    void shouldRejectNonBearerAuthorizationOnProtectedPath() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth("user", "password");

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                "/admin-api/runtime-smoke/protected",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                JsonNode.class);

        assertAuthError(response, GeihouAuthErrorCodes.UNAUTHORIZED);
    }

    @Test
    void shouldLetContextFilterRejectInvalidBearerToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("bad-token");

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                "/admin-api/runtime-smoke/protected",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                JsonNode.class);

        assertAuthError(response, GeihouAuthErrorCodes.INVALID_TOKEN);
    }

    @Test
    void shouldPassProtectedPathWithValidBearerToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(VALID_TOKEN);

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                "/admin-api/runtime-smoke/protected",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                JsonNode.class);

        assertOk(response);
    }

    @Test
    void shouldPassAuthPermitPathWithoutToken() {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                "/admin-api/auth/runtime-smoke-login", JsonNode.class);

        assertOk(response);
    }

    @Test
    void shouldPassRealLoginEndpointWithoutToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "/admin-api/auth/login",
                new HttpEntity<>("""
                        {
                          "username": "owner",
                          "password": "secret",
                          "tenantCode": "abao"
                        }
                        """, headers),
                JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("code").asInt()).isEqualTo(0);
        assertThat(response.getBody().get("data").get("accessToken").asText()).isEqualTo("runtime-access-token");
    }

    @Test
    void shouldPassRealTwoFactorVerifyEndpointWithoutToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "/admin-api/auth/two-factor-verify",
                new HttpEntity<>("""
                        {
                          "tempToken": "runtime-temp-token",
                          "code": "123456"
                        }
                        """, headers),
                JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("code").asInt()).isEqualTo(0);
        assertThat(response.getBody().get("data").get("accessToken").asText()).isEqualTo("runtime-access-token");
    }

    @Test
    void shouldPassPublicPathWithoutToken() {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                "/public/runtime-smoke", JsonNode.class);

        assertOk(response);
    }

    private void assertAuthError(ResponseEntity<JsonNode> response, int expectedCode) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("code").asInt()).isEqualTo(expectedCode);
    }

    private void assertOk(ResponseEntity<JsonNode> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("ok").asBoolean()).isTrue();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            FlywayAutoConfiguration.class
    })
    @Import(SmokeBeans.class)
    static class SmokeApplication {
    }

    static class SmokeBeans {

        @Bean
        TokenValidator smokeTokenValidator() {
            return token -> {
                if (VALID_TOKEN.equals(token)) {
                    GeihouPrincipal principal = new GeihouPrincipal(
                            1L,
                            "smoke-user",
                            1L,
                            Set.of("runtime-smoke"),
                            Set.of(),
                            "smoke-token-id");
                    return TokenValidationResult.valid(principal);
                }
                return TokenValidationResult.invalid(GeihouAuthErrorCodes.INVALID_TOKEN, "Invalid token");
            };
        }

        @Bean
        RuntimeSmokeController runtimeSmokeController() {
            return new RuntimeSmokeController();
        }

        @Bean
        GeihouAdminLoginOrchestratorService geihouAdminLoginOrchestratorService() {
            GeihouAdminLoginOrchestratorService service = mock(GeihouAdminLoginOrchestratorService.class);
            when(service.orchestrate(any())).thenReturn(LoginOrchestrationResult.success(
                    "runtime-access-token",
                    3600L,
                    "runtime-refresh-token",
                    2001L,
                    1L,
                    "OWNER"));
            return service;
        }

        @Bean
        GeihouTwoFactorVerifyOrchestrationService geihouTwoFactorVerifyOrchestrationService() {
            GeihouTwoFactorVerifyOrchestrationService service =
                    mock(GeihouTwoFactorVerifyOrchestrationService.class);
            when(service.verify(any(), any(), any(), any())).thenReturn(TwoFactorVerifyResult.success(
                    "runtime-access-token",
                    3600L,
                    "runtime-refresh-token",
                    2001L,
                    1L,
                    "OWNER"));
            return service;
        }

        @Bean
        GeihouAdminAuthController geihouAdminAuthController(
                GeihouAdminLoginOrchestratorService orchestratorService,
                GeihouTwoFactorVerifyOrchestrationService twoFactorVerifyService) {
            return new GeihouAdminAuthController(orchestratorService, twoFactorVerifyService);
        }
    }

    @RestController
    static class RuntimeSmokeController {

        @GetMapping({
                "/admin-api/runtime-smoke/protected",
                "/admin-api/auth/runtime-smoke-login",
                "/public/runtime-smoke"
        })
        Map<String, Boolean> ok() {
            return Map.of("ok", true);
        }
    }
}
