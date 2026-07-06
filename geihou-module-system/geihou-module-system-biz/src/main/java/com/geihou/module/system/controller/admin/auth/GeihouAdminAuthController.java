package com.geihou.module.system.controller.admin.auth;

import com.geihou.common.pojo.CommonResult;
import com.geihou.module.system.controller.admin.auth.vo.GeihouAdminLoginReqVO;
import com.geihou.module.system.controller.admin.auth.vo.GeihouAdminLoginRespVO;
import com.geihou.module.system.controller.admin.auth.vo.GeihouAdminLoginUserInfoVO;
import com.geihou.module.system.controller.admin.auth.vo.GeihouAdminTwoFactorRequiredVO;
import com.geihou.module.system.controller.admin.auth.vo.GeihouAdminTwoFactorVerifyReqVO;
import com.geihou.module.system.service.auth.GeihouAdminLoginAuthenticationCommand;
import com.geihou.module.system.service.auth.GeihouAdminLoginOrchestratorService;
import com.geihou.module.system.service.auth.GeihouAdminLoginOrchestratorService.LoginOrchestrationResult;
import com.geihou.module.system.service.auth.GeihouAdminLoginOrchestratorService.OrchestrationDenyReason;
import com.geihou.module.system.service.auth.GeihouAdminLoginOrchestratorService.OrchestrationStatus;
import com.geihou.module.system.service.auth.GeihouTwoFactorVerifyOrchestrationService;
import com.geihou.module.system.service.auth.GeihouTwoFactorVerifyOrchestrationService.DenyReason;
import com.geihou.module.system.service.auth.GeihouTwoFactorVerifyOrchestrationService.Status;
import com.geihou.module.system.service.auth.GeihouTwoFactorVerifyOrchestrationService.TwoFactorVerifyResult;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin-api/auth")
public class GeihouAdminAuthController {

    private static final int LOGIN_DENIED_CODE = 401000;

    private final GeihouAdminLoginOrchestratorService orchestratorService;
    private final GeihouTwoFactorVerifyOrchestrationService twoFactorVerifyService;

    public GeihouAdminAuthController(
            GeihouAdminLoginOrchestratorService orchestratorService,
            GeihouTwoFactorVerifyOrchestrationService twoFactorVerifyService) {
        this.orchestratorService = Objects.requireNonNull(
                orchestratorService, "orchestratorService must not be null");
        this.twoFactorVerifyService = Objects.requireNonNull(
                twoFactorVerifyService, "twoFactorVerifyService must not be null");
    }

    @PostMapping("/login")
    public CommonResult<Object> login(
            @RequestBody GeihouAdminLoginReqVO reqVO,
            HttpServletRequest httpRequest) {
        LoginOrchestrationResult result = orchestratorService.orchestrate(toCommand(reqVO, httpRequest));
        if (result.status() == OrchestrationStatus.SUCCESS) {
            return CommonResult.success(new GeihouAdminLoginRespVO(
                    result.accessToken(),
                    result.refreshToken(),
                    result.expiresInSeconds(),
                    new GeihouAdminLoginUserInfoVO(
                            result.userId(), result.userRole(), result.tenantId(), null, null)));
        }
        if (result.status() == OrchestrationStatus.TWO_FACTOR_REQUIRED) {
            return CommonResult.success(new GeihouAdminTwoFactorRequiredVO(
                    true, result.tempToken(), result.twoFactorMethod()));
        }
        OrchestrationDenyReason reason = result.denyReason() == null
                ? OrchestrationDenyReason.INTERNAL_ERROR
                : result.denyReason();
        return CommonResult.error(LOGIN_DENIED_CODE, GeihouLoginDenyReasonPublicMapper.toPublicReason(reason));
    }

    @PostMapping("/two-factor-verify")
    public CommonResult<Object> twoFactorVerify(
            @RequestBody GeihouAdminTwoFactorVerifyReqVO reqVO,
            HttpServletRequest httpRequest) {
        TwoFactorVerifyResult result = twoFactorVerifyService.verify(
                reqVO == null ? null : reqVO.getTempToken(),
                reqVO == null ? null : reqVO.getCode(),
                clientIp(httpRequest),
                userAgent(httpRequest));
        if (result.status() == Status.SUCCESS) {
            return CommonResult.success(new GeihouAdminLoginRespVO(
                    result.accessToken(),
                    result.refreshToken(),
                    result.expiresInSeconds(),
                    new GeihouAdminLoginUserInfoVO(
                            result.userId(), result.userRole(), result.tenantId(), null, null)));
        }
        DenyReason reason = result.denyReason() == null ? DenyReason.TEMP_TOKEN_INVALID : result.denyReason();
        return CommonResult.error(LOGIN_DENIED_CODE, GeihouTwoFactorDenyReasonPublicMapper.toPublicReason(reason));
    }

    private static GeihouAdminLoginAuthenticationCommand toCommand(
            GeihouAdminLoginReqVO reqVO,
            HttpServletRequest httpRequest) {
        String clientIp = clientIp(httpRequest);
        String userAgent = userAgent(httpRequest);
        if (reqVO == null) {
            return new GeihouAdminLoginAuthenticationCommand(
                    null, null, null, null, null, clientIp, userAgent, "admin-login");
        }
        return new GeihouAdminLoginAuthenticationCommand(
                reqVO.getUsername(),
                reqVO.getPassword(),
                reqVO.getTenantCode(),
                reqVO.getCaptcha(),
                reqVO.getCaptchaKey(),
                clientIp,
                userAgent,
                "admin-login");
    }

    private static String clientIp(HttpServletRequest httpRequest) {
        if (httpRequest == null) {
            return null;
        }
        String forwardedFor = httpRequest.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",", 2)[0].trim();
        }
        return httpRequest.getRemoteAddr();
    }

    private static String userAgent(HttpServletRequest httpRequest) {
        if (httpRequest == null) {
            return null;
        }
        return httpRequest.getHeader("User-Agent");
    }
}
