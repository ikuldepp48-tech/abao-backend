package com.geihou.module.system.controller.admin.auth.vo;

public record GeihouAdminLoginRespVO(
        String accessToken,
        String refreshToken,
        long expiresIn,
        GeihouAdminLoginUserInfoVO userInfo) {
}
