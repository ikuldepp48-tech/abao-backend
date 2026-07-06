package com.geihou.module.system.service.auth;

import com.geihou.module.system.framework.jwt.GeihouAccessTokenAudience;
import com.geihou.module.system.framework.jwt.GeihouAccessTokenAudienceMapper;
import com.geihou.module.system.framework.jwt.GeihouAccessTokenIssuer;
import com.geihou.module.system.framework.jwt.GeihouAccessTokenRequest;
import com.geihou.module.system.framework.jwt.GeihouAccessTokenUserRole;

import java.util.Objects;
import java.util.Optional;

/**
 * Access-token-only assembler for already-authenticated flows.
 */
public class GeihouAccessTokenIssueService {

    private final GeihouAccessTokenIssuer accessTokenIssuer;

    public GeihouAccessTokenIssueService(GeihouAccessTokenIssuer accessTokenIssuer) {
        this.accessTokenIssuer = Objects.requireNonNull(accessTokenIssuer, "accessTokenIssuer must not be null");
    }

    public Optional<GeihouAccessTokenIssueResult> issue(GeihouAccessTokenIssueCommand command) {
        if (command == null) {
            return Optional.empty();
        }

        try {
            GeihouAccessTokenAudience audience = GeihouAccessTokenAudienceMapper
                    .audienceForUserRole(command.userRole())
                    .orElseThrow(() -> new IllegalArgumentException("userRole must map to an audience"));
            if (requiresStepUp(command.userRole()) && !command.stepUpVerified()) {
                return Optional.empty();
            }
            GeihouAccessTokenRequest request = new GeihouAccessTokenRequest(
                    command.userId(),
                    command.tenantId(),
                    audience,
                    command.userRole(),
                    command.roles());
            return accessTokenIssuer.issue(request)
                    .map(accessToken -> new GeihouAccessTokenIssueResult(
                            accessToken,
                            audience.ttl().toSeconds(),
                            request.userId(),
                            request.tenantId(),
                            request.userRole(),
                            request.roles()));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private static boolean requiresStepUp(String userRole) {
        return GeihouAccessTokenUserRole.CONSULTANT.code().equals(userRole)
                || GeihouAccessTokenUserRole.PLATFORM_ADMIN.code().equals(userRole);
    }
}
