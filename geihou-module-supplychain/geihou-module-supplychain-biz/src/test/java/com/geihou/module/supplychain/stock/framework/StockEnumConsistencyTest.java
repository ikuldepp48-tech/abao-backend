package com.geihou.module.supplychain.stock.framework;

import com.geihou.module.supplychain.api.stock.enums.StockDirectionEnum;
import com.geihou.module.supplychain.api.stock.enums.StockEventTypeEnum;
import com.geihou.module.supplychain.api.stock.enums.StockReserveStatusEnum;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enum consistency test for stock event types.
 *
 * <p>Covers: AC-9 (event type uses global enum table 12 values, not PRD 7 values,
 * no RESERVE_OUT/RESERVE_RELEASE/RESERVE_COMMIT).
 */
class StockEnumConsistencyTest {

    @Test
    void eventTypeEnumHasExactly12Values() {
        assertThat(StockEventTypeEnum.values()).hasSize(12);
    }

    @Test
    void eventTypeEnumMatchesGlobalEnumTable() {
        // Global enum table 12 values (CG-12-A)
        Set<String> expectedCodes = Set.of(
                "PURCHASE_IN", "PRODUCTION_IN", "TRANSFER_IN", "RETURN_IN",
                "CONSUME_OUT", "PRODUCTION_OUT", "TRANSFER_OUT",
                "COUNT_ADJUST", "LOSS_OUT", "SCRAP_OUT",
                "EXPIRY_OUT", "RETURN_OUT"
        );

        Set<String> actualCodes = Arrays.stream(StockEventTypeEnum.values())
                .map(StockEventTypeEnum::getCode)
                .collect(Collectors.toSet());

        assertThat(actualCodes).isEqualTo(expectedCodes);
    }

    @Test
    void noReserveOutEnumValue() {
        List<String> codes = Arrays.stream(StockEventTypeEnum.values())
                .map(StockEventTypeEnum::getCode)
                .toList();

        assertThat(codes).doesNotContain("RESERVE_OUT");
        assertThat(codes).doesNotContain("RESERVE_RELEASE");
        assertThat(codes).doesNotContain("RESERVE_COMMIT");
    }

    @Test
    void directionEnumHas3Values() {
        assertThat(StockDirectionEnum.values()).hasSize(3);
    }

    @Test
    void inTypesHave4Values() {
        assertThat(StockEventTypeEnum.IN_TYPES).hasSize(4);
    }

    @Test
    void outTypesHave7Values() {
        assertThat(StockEventTypeEnum.OUT_TYPES).hasSize(7);
    }

    @Test
    void internalTypesHave1Value() {
        assertThat(StockEventTypeEnum.INTERNAL_TYPES).hasSize(1);
        assertThat(StockEventTypeEnum.INTERNAL_TYPES).contains(StockEventTypeEnum.COUNT_ADJUST);
    }

    @Test
    void fromCodeThrowsForUnknownType() {
        try {
            StockEventTypeEnum.fromCode("UNKNOWN_TYPE");
            assertThat(false).as("Should have thrown").isTrue();
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    void fromCodeThrowsForReserveOut() {
        try {
            StockEventTypeEnum.fromCode("RESERVE_OUT");
            assertThat(false).as("Should have thrown").isTrue();
        } catch (IllegalArgumentException e) {
            // Expected — RESERVE_OUT is not a valid enum value
        }
    }

    @Test
    void fromCodeReturnsReturnInForValidCode() {
        // Explicit positive assertion: fromCode("RETURN_IN") must resolve to RETURN_IN
        StockEventTypeEnum result = StockEventTypeEnum.fromCode("RETURN_IN");
        assertThat(result).isEqualTo(StockEventTypeEnum.RETURN_IN);
        assertThat(result.getCode()).isEqualTo("RETURN_IN");
        assertThat(result.getDirection()).isEqualTo("IN");
    }

    // === G2-01B1: StockReserveStatusEnum tests ===

    @Test
    void stockReserveStatusEnumHas3Values() {
        assertThat(StockReserveStatusEnum.values()).hasSize(3);
    }

    @Test
    void stockReserveStatusEnumHasReservedReleasedCommitted() {
        Set<String> expectedCodes = Set.of("RESERVED", "RELEASED", "COMMITTED");
        Set<String> actualCodes = Arrays.stream(StockReserveStatusEnum.values())
                .map(StockReserveStatusEnum::getCode)
                .collect(Collectors.toSet());
        assertThat(actualCodes).isEqualTo(expectedCodes);
    }

    @Test
    void reserveStatusIsNotInEventTypeEnum() {
        // RESERVED, RELEASED, COMMITTED must NOT be in StockEventTypeEnum
        List<String> eventTypeCodes = Arrays.stream(StockEventTypeEnum.values())
                .map(StockEventTypeEnum::getCode)
                .toList();
        assertThat(eventTypeCodes).doesNotContain("RESERVED");
        assertThat(eventTypeCodes).doesNotContain("RELEASED");
        assertThat(eventTypeCodes).doesNotContain("COMMITTED");
    }
}
