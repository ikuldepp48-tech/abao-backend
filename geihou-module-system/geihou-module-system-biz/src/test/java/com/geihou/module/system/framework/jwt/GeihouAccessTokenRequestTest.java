package com.geihou.module.system.framework.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeihouAccessTokenRequestTest {

    @Test
    void shouldMapCanonicalUserRolesToAudiences() {
        assertThat(GeihouAccessTokenAudienceMapper.audienceForUserRole("CUSTOMER"))
                .hasValue(GeihouAccessTokenAudience.CUSTOMER);
        assertThat(GeihouAccessTokenAudienceMapper.audienceForUserRole("STORE_STAFF"))
                .hasValue(GeihouAccessTokenAudience.STAFF);
        assertThat(GeihouAccessTokenAudienceMapper.audienceForUserRole("CK_WORKER"))
                .hasValue(GeihouAccessTokenAudience.STAFF);
        assertThat(GeihouAccessTokenAudienceMapper.audienceForUserRole("STORE_MANAGER"))
                .hasValue(GeihouAccessTokenAudience.ADMIN);
        assertThat(GeihouAccessTokenAudienceMapper.audienceForUserRole("CK_MANAGER"))
                .hasValue(GeihouAccessTokenAudience.ADMIN);
        assertThat(GeihouAccessTokenAudienceMapper.audienceForUserRole("OWNER"))
                .hasValue(GeihouAccessTokenAudience.ADMIN);
        assertThat(GeihouAccessTokenAudienceMapper.audienceForUserRole("CONSULTANT"))
                .hasValue(GeihouAccessTokenAudience.CONSULTANT);
        assertThat(GeihouAccessTokenAudienceMapper.audienceForUserRole("PLATFORM_ADMIN"))
                .hasValue(GeihouAccessTokenAudience.PLATFORM);
    }

    @Test
    void shouldRejectUnknownAndOldLocalRoleCodes() {
        assertThat(GeihouAccessTokenAudienceMapper.audienceForUserRole("STAFF")).isEmpty();
        assertThat(GeihouAccessTokenAudienceMapper.audienceForUserRole("PLATFORM_OPERATOR")).isEmpty();
        assertThat(GeihouAccessTokenAudienceMapper.audienceForUserRole("PLATFORM_DEVELOPER")).isEmpty();
        assertThat(GeihouAccessTokenAudienceMapper.audienceForUserRole("SHOP_MANAGER")).isEmpty();
        assertThat(GeihouAccessTokenAudienceMapper.audienceForUserRole(null)).isEmpty();
        assertThat(GeihouAccessTokenAudienceMapper.audienceForUserRole(" ")).isEmpty();

        assertThatThrownBy(() -> newRequest(200L, GeihouAccessTokenAudience.STAFF, "STAFF"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("canonical ENUM_USER_ROLE");
        assertThatThrownBy(() -> newRequest(0L, GeihouAccessTokenAudience.PLATFORM, "PLATFORM_OPERATOR"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("canonical ENUM_USER_ROLE");
    }

    @Test
    void shouldAllowTenantScopedRolesWithPositiveTenantId() {
        assertThat(newRequest(200L, GeihouAccessTokenAudience.CUSTOMER, "CUSTOMER").tenantId())
                .isEqualTo(200L);
        assertThat(newRequest(200L, GeihouAccessTokenAudience.STAFF, "STORE_STAFF").tenantId())
                .isEqualTo(200L);
        assertThat(newRequest(200L, GeihouAccessTokenAudience.STAFF, "CK_WORKER").tenantId())
                .isEqualTo(200L);
        assertThat(newRequest(200L, GeihouAccessTokenAudience.ADMIN, "STORE_MANAGER").tenantId())
                .isEqualTo(200L);
        assertThat(newRequest(200L, GeihouAccessTokenAudience.ADMIN, "CK_MANAGER").tenantId())
                .isEqualTo(200L);
        assertThat(newRequest(200L, GeihouAccessTokenAudience.ADMIN, "OWNER").tenantId())
                .isEqualTo(200L);
    }

    @Test
    void shouldAllowZeroTenantOnlyForConsultantAndPlatformIdentityTokens() {
        assertThat(newRequest(0L, GeihouAccessTokenAudience.CONSULTANT, "CONSULTANT").tenantId())
                .isZero();
        assertThat(newRequest(0L, GeihouAccessTokenAudience.PLATFORM, "PLATFORM_ADMIN").tenantId())
                .isZero();
    }

    @Test
    void shouldRejectZeroTenantForTenantScopedRoles() {
        assertThatThrownBy(() -> newRequest(0L, GeihouAccessTokenAudience.CUSTOMER, "CUSTOMER"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId does not match");
        assertThatThrownBy(() -> newRequest(0L, GeihouAccessTokenAudience.STAFF, "STORE_STAFF"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId does not match");
        assertThatThrownBy(() -> newRequest(0L, GeihouAccessTokenAudience.STAFF, "CK_WORKER"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId does not match");
        assertThatThrownBy(() -> newRequest(0L, GeihouAccessTokenAudience.ADMIN, "OWNER"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId does not match");
    }

    @Test
    void shouldRejectNonZeroTenantForPlatformScopedRoles() {
        assertThatThrownBy(() -> newRequest(200L, GeihouAccessTokenAudience.CONSULTANT, "CONSULTANT"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId does not match");
        assertThatThrownBy(() -> newRequest(200L, GeihouAccessTokenAudience.PLATFORM, "PLATFORM_ADMIN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId does not match");
    }

    @Test
    void shouldRejectMismatchedRoleAndAudiencePairs() {
        assertThat(GeihouAccessTokenAudienceMapper.supports("OWNER", GeihouAccessTokenAudience.ADMIN))
                .isTrue();
        assertThat(GeihouAccessTokenAudienceMapper.supports("OWNER", GeihouAccessTokenAudience.PLATFORM))
                .isFalse();

        assertThatThrownBy(() -> newRequest(200L, GeihouAccessTokenAudience.ADMIN, "PLATFORM_ADMIN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userRole and audience must match");
        assertThatThrownBy(() -> newRequest(200L, GeihouAccessTokenAudience.STAFF, "CUSTOMER"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userRole and audience must match");
    }

    @Test
    void shouldKeepRolesClaimStructurallyValidatedWithoutDefaulting() {
        assertThat(new GeihouAccessTokenRequest(
                100L,
                200L,
                GeihouAccessTokenAudience.ADMIN,
                "OWNER",
                List.of("OWNER")).roles())
                .containsExactly("OWNER");

        assertThatThrownBy(() -> new GeihouAccessTokenRequest(
                100L,
                200L,
                GeihouAccessTokenAudience.ADMIN,
                "OWNER",
                List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeihouAccessTokenRequest(
                100L,
                200L,
                GeihouAccessTokenAudience.ADMIN,
                "OWNER",
                List.of("OWNER", " ")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectRolePrefixedMalformedLowercasePermissionLikeAndDuplicateRoleCodes() {
        // ROLE_ prefixed codes must be rejected.
        assertThatThrownBy(() -> new GeihouAccessTokenRequest(
                100L, 200L, GeihouAccessTokenAudience.ADMIN, "OWNER", List.of("ROLE_OWNER")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ROLE_");
        // Lowercase / mixed case must be rejected.
        assertThatThrownBy(() -> new GeihouAccessTokenRequest(
                100L, 200L, GeihouAccessTokenAudience.ADMIN, "OWNER", List.of("owner")))
                .isInstanceOf(IllegalArgumentException.class);
        // Colon permission-like values must be rejected.
        assertThatThrownBy(() -> new GeihouAccessTokenRequest(
                100L, 200L, GeihouAccessTokenAudience.ADMIN, "OWNER", List.of("ORDER:READ")))
                .isInstanceOf(IllegalArgumentException.class);
        // Hyphenated codes must be rejected.
        assertThatThrownBy(() -> new GeihouAccessTokenRequest(
                100L, 200L, GeihouAccessTokenAudience.ADMIN, "OWNER", List.of("SHOP-MANAGER")))
                .isInstanceOf(IllegalArgumentException.class);
        // Duplicates must be rejected.
        assertThatThrownBy(() -> new GeihouAccessTokenRequest(
                100L, 200L, GeihouAccessTokenAudience.ADMIN, "OWNER", List.of("OWNER", "OWNER")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicates");
        // Null entries must be rejected.
        assertThatThrownBy(() -> new GeihouAccessTokenRequest(
                100L, 200L, GeihouAccessTokenAudience.ADMIN, "OWNER", Arrays.asList("OWNER", null)))
                .isInstanceOf(NullPointerException.class);
        // Null list must be rejected.
        assertThatThrownBy(() -> new GeihouAccessTokenRequest(
                100L, 200L, GeihouAccessTokenAudience.ADMIN, "OWNER", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldNormalizeRawRoleCodesIntoStableSortedImmutableList() {
        List<String> source = new ArrayList<>(List.of("SHOP_MANAGER", "OWNER", "CASHIER"));

        GeihouAccessTokenRequest request = new GeihouAccessTokenRequest(
                100L, 200L, GeihouAccessTokenAudience.ADMIN, "OWNER", source);
        source.add("WAITER");

        assertThat(request.roles()).containsExactly("CASHIER", "OWNER", "SHOP_MANAGER");
        assertThatThrownBy(() -> request.roles().add("WAITER"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldMapCanonicalIdentityRolesToRepresentativeRbacRoleCodes() {
        // CUSTOMER -> CUSTOMER
        assertThat(new GeihouAccessTokenRequest(
                100L, 200L, GeihouAccessTokenAudience.CUSTOMER, "CUSTOMER", List.of("CUSTOMER")).roles())
                .containsExactly("CUSTOMER");
        // STORE_STAFF -> CASHIER
        assertThat(new GeihouAccessTokenRequest(
                100L, 200L, GeihouAccessTokenAudience.STAFF, "STORE_STAFF", List.of("CASHIER")).roles())
                .containsExactly("CASHIER");
        // CK_WORKER -> CK_WORKER
        assertThat(new GeihouAccessTokenRequest(
                100L, 200L, GeihouAccessTokenAudience.STAFF, "CK_WORKER", List.of("CK_WORKER")).roles())
                .containsExactly("CK_WORKER");
        // STORE_MANAGER -> SHOP_MANAGER
        assertThat(new GeihouAccessTokenRequest(
                100L, 200L, GeihouAccessTokenAudience.ADMIN, "STORE_MANAGER", List.of("SHOP_MANAGER")).roles())
                .containsExactly("SHOP_MANAGER");
        // CK_MANAGER -> CK_MANAGER
        assertThat(new GeihouAccessTokenRequest(
                100L, 200L, GeihouAccessTokenAudience.ADMIN, "CK_MANAGER", List.of("CK_MANAGER")).roles())
                .containsExactly("CK_MANAGER");
        // OWNER -> OWNER
        assertThat(new GeihouAccessTokenRequest(
                100L, 200L, GeihouAccessTokenAudience.ADMIN, "OWNER", List.of("OWNER")).roles())
                .containsExactly("OWNER");
        // CONSULTANT -> CONSULTANT
        assertThat(new GeihouAccessTokenRequest(
                100L, 0L, GeihouAccessTokenAudience.CONSULTANT, "CONSULTANT", List.of("CONSULTANT")).roles())
                .containsExactly("CONSULTANT");
        // PLATFORM_ADMIN -> PLATFORM_OPERATOR
        assertThat(new GeihouAccessTokenRequest(
                100L, 0L, GeihouAccessTokenAudience.PLATFORM, "PLATFORM_ADMIN", List.of("PLATFORM_OPERATOR")).roles())
                .containsExactly("PLATFORM_OPERATOR");
    }

    private static GeihouAccessTokenRequest newRequest(
            long tenantId, GeihouAccessTokenAudience audience, String userRole) {
        return new GeihouAccessTokenRequest(
                100L,
                tenantId,
                audience,
                userRole,
                List.of("OWNER"));
    }
}
