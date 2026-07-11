package com.geihou.module.system.controller.admin.auth;

import com.geihou.common.pojo.CommonResult;
import com.geihou.framework.security.core.constant.GeihouAuthErrorCodes;
import com.geihou.module.system.framework.security.GeihouAuthUnavailableReasonEnum;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fallback controller active only when {@code geihou.security.auth-disabled=true}.
 *
 * <p>Maps the same two endpoints as {@link GeihouAdminAuthController} and
 * returns HTTP 503 with {@code CommonResult} code=1008,
 * msg=AUTH_SERVICE_UNAVAILABLE, data=null. The two controllers are mutually
 * exclusive via {@link ConditionalOnProperty}; no dual-controller conflict.
 *
 * <p>Must use {@link ResponseEntity} to set the real HTTP 503 status.
 * {@code CommonResult.error(...)} alone leaves HTTP 200 and violates the
 * G0-04H185-SYS-503-CONTRACT.
 *
 * <p>Source: G0-04H185-SYS-503-CONTRACT.
 */
@RestController
@RequestMapping("/admin-api/auth")
@ConditionalOnProperty(
        prefix = "geihou.security",
        name = "auth-disabled",
        havingValue = "true")
public class GeihouAdminAuthUnavailableController {

    @PostMapping("/login")
    public ResponseEntity<CommonResult<Void>> login() {
        return unavailable();
    }

    @PostMapping("/two-factor-verify")
    public ResponseEntity<CommonResult<Void>> twoFactorVerify() {
        return unavailable();
    }

    private static ResponseEntity<CommonResult<Void>> unavailable() {
        CommonResult<Void> body = CommonResult.error(
                GeihouAuthErrorCodes.AUTH_SERVICE_UNAVAILABLE,
                GeihouAuthUnavailableReasonEnum.AUTH_SERVICE_UNAVAILABLE.name());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
