package com.geihou.module.system.framework.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class GeihouRbacRoleCodesTest {

    @Test
    void shouldNormalizeRawRoleCodesAsStableImmutableSet() {
        List<String> source = new ArrayList<>(List.of("SHOP_MANAGER", "OWNER", "CASHIER"));

        List<String> normalized = GeihouRbacRoleCodes.normalize(source);
        source.add("WAITER");

        assertThat(normalized).containsExactly("CASHIER", "OWNER", "SHOP_MANAGER");
        assertThatThrownBy(() -> normalized.add("WAITER"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "",
            " ",
            "ROLE_OWNER",
            "owner",
            "Owner",
            "ORDER:READ",
            "SHOP-MANAGER",
            "_OWNER",
            "OWNER_",
            "OWNER__ADMIN"
    })
    void shouldRejectInvalidRoleCode(String roleCode) {
        assertThatThrownBy(() -> GeihouRbacRoleCodes.normalize(Arrays.asList("OWNER", roleCode)))
                .isInstanceOfAny(NullPointerException.class, IllegalArgumentException.class);
    }

    @Test
    void shouldRejectEmptyDuplicateAndTooLongRoleCodes() {
        assertThatThrownBy(() -> GeihouRbacRoleCodes.normalize(List.of()))
                .hasMessageContaining("empty");
        assertThatThrownBy(() -> GeihouRbacRoleCodes.normalize(List.of("OWNER", "OWNER")))
                .hasMessageContaining("duplicates");
        assertThatThrownBy(() -> GeihouRbacRoleCodes.normalize(List.of("A".repeat(65))))
                .hasMessageContaining("64");
    }
}
