package com.geihou.module.system.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geihou.module.system.dal.dataobject.auth.AuthUserDO;
import com.geihou.module.system.dal.mysql.auth.AuthUserRepository;
import com.geihou.module.system.framework.jwt.GeihouAccessTokenUserRole;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GeihouAuthUserLoginLookupAdapterTest {

    private final AuthUserRepository repository = mock(AuthUserRepository.class);
    private final GeihouAuthUserLoginLookupAdapter adapter = new GeihouAuthUserLoginLookupAdapter(repository);

    @Test
    void shouldLookupTenantUserByRequestedTenantIdAndUsername() {
        when(repository.selectByTenantIdAndUsername(2L, "owner")).thenReturn(user(
                1001L, 2L, GeihouAccessTokenUserRole.OWNER.code(), "owner", false, 1));

        Optional<GeihouAdminLoginAuthenticationService.LoginUser> result =
                adapter.findByUsername(" owner ", Optional.of(2L));

        assertThat(result).isPresent();
        assertThat(result.get().userId()).isEqualTo(1001L);
        assertThat(result.get().tenantId()).isEqualTo(2L);
        assertThat(result.get().userRole()).isEqualTo(GeihouAccessTokenUserRole.OWNER.code());
        assertThat(result.get().loginFailCount()).isEqualTo(1);
        assertThat(result.get().lockUntil()).isNull();
        verify(repository).selectByTenantIdAndUsername(2L, "owner");
    }

    @Test
    void shouldLookupPlatformUserWithTenantZeroWhenTenantCodeIsBlank() {
        when(repository.selectByTenantIdAndUsername(0L, "consultant")).thenReturn(user(
                2001L, 0L, GeihouAccessTokenUserRole.CONSULTANT.code(), "consultant", true, 0));

        Optional<GeihouAdminLoginAuthenticationService.LoginUser> result =
                adapter.findByUsername("consultant", Optional.empty());

        assertThat(result).isPresent();
        assertThat(result.get().tenantId()).isZero();
        assertThat(result.get().twoFactorEnabled()).isTrue();
        assertThat(result.get().lockUntil()).isEqualTo(LocalDateTime.parse("2026-06-20T02:30:00"));
        verify(repository).selectByTenantIdAndUsername(0L, "consultant");
    }

    @Test
    void shouldReturnEmptyForBlankUsernameOrMissingUser() {
        assertThat(adapter.findByUsername(" ", Optional.of(2L))).isEmpty();
        when(repository.selectByTenantIdAndUsername(2L, "missing")).thenReturn(null);

        assertThat(adapter.findByUsername("missing", Optional.of(2L))).isEmpty();
    }

    @Test
    void shouldFailClosedForNegativeTenantId() {
        assertThat(adapter.findByUsername("owner", Optional.of(-1L))).isEmpty();
    }

    private static AuthUserDO user(long userId,
                                   long tenantId,
                                   String userRole,
                                   String username,
                                   boolean twoFactorEnabled,
                                   int loginFailCount) {
        AuthUserDO user = new AuthUserDO();
        user.setId(userId);
        user.setTenantId(tenantId);
        user.setUserRole(userRole);
        user.setUsername(username);
        user.setPasswordHash("$2a$10$placeholderplaceholderplaceholderplaceholderplaceholder");
        user.setPasswordSalt("legacy-salt");
        user.setTwoFactorEnabled(twoFactorEnabled);
        user.setStatus("ACTIVE");
        user.setLoginFailCount(loginFailCount);
        if (userId == 2001L) {
            user.setLockUntil(LocalDateTime.parse("2026-06-20T02:30:00"));
        }
        return user;
    }
}
