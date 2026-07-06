package com.geihou.module.system.controller.admin.auth.vo;

public record GeihouAdminLoginUserInfoVO(
        Long userId,
        String userRole,
        Long tenantId,
        String nickname,
        String avatar) {
}
