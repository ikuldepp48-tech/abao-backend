package com.geihou.module.supplychain.api.stock.enums;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SupplychainCommandOperationEnum}.
 *
 * <p>Verifies exactly 6 values, codes match frozen operation strings,
 * label equals code, and fromCode round-trip works.
 */
class SupplychainCommandOperationEnumTest {

    @Test
    void exactlySixValues() {
        assertThat(SupplychainCommandOperationEnum.values()).hasSize(6);
    }

    @Test
    void codesMatchFrozenOperations() {
        List<String> codes = Arrays.stream(SupplychainCommandOperationEnum.values())
                .map(SupplychainCommandOperationEnum::getCode)
                .collect(Collectors.toList());
        assertThat(codes).containsExactly(
                "RESERVE",
                "RELEASE",
                "COMMIT",
                "SALES_OUT_BOM_REVERSE",
                "SALES_REVERSE_RESTORE",
                "OBSERVE_MISSING_MAPPING"
        );
    }

    @Test
    void labelEqualsCode() {
        for (SupplychainCommandOperationEnum op : SupplychainCommandOperationEnum.values()) {
            assertThat(op.getLabel()).isEqualTo(op.getCode());
        }
    }

    @Test
    void fromCode_roundTrip() {
        for (SupplychainCommandOperationEnum op : SupplychainCommandOperationEnum.values()) {
            assertThat(SupplychainCommandOperationEnum.fromCode(op.getCode())).isEqualTo(op);
        }
    }

    @Test
    void fromCode_unknownThrows() {
        assertThatThrownBy(() -> SupplychainCommandOperationEnum.fromCode("RESERVE_SHORT"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
