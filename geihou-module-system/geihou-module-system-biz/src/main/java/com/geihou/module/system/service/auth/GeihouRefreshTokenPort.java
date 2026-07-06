package com.geihou.module.system.service.auth;

import java.time.LocalDateTime;

public interface GeihouRefreshTokenPort {

    RefreshTokenIssueResult issue(long userId, long tenantId, String userRole);

    RefreshTokenVerifyResult verify(String refreshToken);

    boolean revoke(String refreshToken, String reason);

    record RefreshTokenIssueResult(String refreshToken, LocalDateTime expireTime) {
    }

    record RefreshTokenVerifyResult(long userId, long tenantId, String userRole) {
    }
}
