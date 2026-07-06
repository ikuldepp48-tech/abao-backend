package com.geihou.module.system.service.auth;

/**
 * Password-login proof request for PRD 0-05 backstage login.
 */
public record GeihouAdminLoginAuthenticationCommand(
        String username,
        String password,
        String tenantCode,
        String captcha,
        String captchaKey,
        String clientIp,
        String userAgent,
        String operator) {
}
