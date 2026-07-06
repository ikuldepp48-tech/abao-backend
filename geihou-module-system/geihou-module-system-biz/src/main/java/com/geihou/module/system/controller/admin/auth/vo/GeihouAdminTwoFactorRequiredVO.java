package com.geihou.module.system.controller.admin.auth.vo;

public record GeihouAdminTwoFactorRequiredVO(
        boolean twoFactorRequired,
        String tempToken,
        String method) {
}
