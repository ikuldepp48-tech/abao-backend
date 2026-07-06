package com.geihou.module.system.framework.jwt;

import java.util.Optional;

/**
 * Maps canonical ENUM_USER_ROLE codes to PRD 0-05 access-token audiences.
 */
public final class GeihouAccessTokenAudienceMapper {

    private GeihouAccessTokenAudienceMapper() {
    }

    public static Optional<GeihouAccessTokenAudience> audienceForUserRole(String userRole) {
        return GeihouAccessTokenUserRole.fromCode(userRole)
                .map(GeihouAccessTokenUserRole::audience);
    }

    public static boolean supports(String userRole, GeihouAccessTokenAudience audience) {
        return audienceForUserRole(userRole)
                .filter(mappedAudience -> mappedAudience == audience)
                .isPresent();
    }
}
