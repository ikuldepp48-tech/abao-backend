package com.geihou.module.system.service.auth;

import com.geihou.module.system.dal.dataobject.auth.AuthUserDO;
import com.geihou.module.system.dal.mysql.auth.AuthUserRepository;
import java.util.Objects;
import java.util.Optional;

/**
 * Repository-backed user lookup port for PRD 0-05 backstage password login.
 */
public class GeihouAuthUserLoginLookupAdapter implements GeihouAdminLoginAuthenticationService.UserLookupPort {

    private static final long PLATFORM_TENANT_ID = 0L;

    private final AuthUserRepository authUserRepository;

    public GeihouAuthUserLoginLookupAdapter(AuthUserRepository authUserRepository) {
        this.authUserRepository = Objects.requireNonNull(authUserRepository, "authUserRepository must not be null");
    }

    @Override
    public Optional<GeihouAdminLoginAuthenticationService.LoginUser> findByUsername(
            String username,
            Optional<Long> requestedTenantId) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        long tenantId = requestedTenantId.orElse(PLATFORM_TENANT_ID);
        if (tenantId < 0L) {
            return Optional.empty();
        }
        AuthUserDO user = authUserRepository.selectByTenantIdAndUsername(tenantId, username.trim());
        if (user == null) {
            return Optional.empty();
        }
        return Optional.of(new GeihouAdminLoginAuthenticationService.LoginUser(
                user.getId() == null ? 0L : user.getId(),
                user.getTenantId() == null ? -1L : user.getTenantId(),
                user.getUserRole(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getPasswordSalt(),
                Boolean.TRUE.equals(user.getTwoFactorEnabled()),
                user.getStatus(),
                user.getLoginFailCount() == null ? 0 : user.getLoginFailCount(),
                user.getLockUntil()));
    }
}
