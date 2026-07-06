package com.geihou.module.finance.product.enums;

import com.geihou.module.finance.product.framework.ProductErrorCodeConstants;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Enum consistency test.
 *
 * <p>Verifies that enum values match the global enum table (02-全局枚举表-V2.md)
 * and PRD-G1-02 Section 2.2 DDL / Section 4.1 state machine.
 *
 * <p>AC-4: Status enum uses Java enum class, values match PRD and global enum table.
 */
class ProductEnumConsistencyTest {

    @Test
    void spuStatusEnumShouldHaveExactlyFourValues() {
        assertThat(SpuStatusEnum.values()).hasSize(4);
    }

    @Test
    void spuStatusEnumCodesShouldMatchGlobalEnumTable() {
        // ENUM_SPU_STATUS: NEW/ACTIVE/PAUSED/DEPRECATED
        assertThat(SpuStatusEnum.NEW.getCode()).isEqualTo("NEW");
        assertThat(SpuStatusEnum.ACTIVE.getCode()).isEqualTo("ACTIVE");
        assertThat(SpuStatusEnum.PAUSED.getCode()).isEqualTo("PAUSED");
        assertThat(SpuStatusEnum.DEPRECATED.getCode()).isEqualTo("DEPRECATED");
    }

    @Test
    void spuStatusFromCodeShouldRoundTrip() {
        for (SpuStatusEnum status : SpuStatusEnum.values()) {
            assertThat(SpuStatusEnum.fromCode(status.getCode())).isEqualTo(status);
        }
    }

    @Test
    void spuStatusFromCodeShouldRejectUnknownCode() {
        assertThatThrownBy(() -> SpuStatusEnum.fromCode("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void spuStatusNewCanTransitionToActive() {
        assertThat(SpuStatusEnum.NEW.canTransitionTo(SpuStatusEnum.ACTIVE)).isTrue();
    }

    @Test
    void spuStatusActiveCanTransitionToPausedAndDeprecated() {
        assertThat(SpuStatusEnum.ACTIVE.canTransitionTo(SpuStatusEnum.PAUSED)).isTrue();
        assertThat(SpuStatusEnum.ACTIVE.canTransitionTo(SpuStatusEnum.DEPRECATED)).isTrue();
        assertThat(SpuStatusEnum.ACTIVE.canTransitionTo(SpuStatusEnum.NEW)).isFalse();
    }

    @Test
    void spuStatusDeprecatedIsTerminal() {
        assertThat(SpuStatusEnum.DEPRECATED.canTransitionTo(SpuStatusEnum.ACTIVE)).isFalse();
        assertThat(SpuStatusEnum.DEPRECATED.canTransitionTo(SpuStatusEnum.PAUSED)).isFalse();
        assertThat(SpuStatusEnum.DEPRECATED.canTransitionTo(SpuStatusEnum.NEW)).isFalse();
    }

    @Test
    void skuStatusEnumShouldHaveExactlyFiveValues() {
        assertThat(SkuStatusEnum.values()).hasSize(5);
    }

    @Test
    void skuStatusEnumCodesShouldMatchGlobalEnumTable() {
        // ENUM_SKU_STATUS: NEW/ACTIVE/SOLD_OUT/PAUSED/DEPRECATED
        assertThat(SkuStatusEnum.NEW.getCode()).isEqualTo("NEW");
        assertThat(SkuStatusEnum.ACTIVE.getCode()).isEqualTo("ACTIVE");
        assertThat(SkuStatusEnum.SOLD_OUT.getCode()).isEqualTo("SOLD_OUT");
        assertThat(SkuStatusEnum.PAUSED.getCode()).isEqualTo("PAUSED");
        assertThat(SkuStatusEnum.DEPRECATED.getCode()).isEqualTo("DEPRECATED");
    }

    @Test
    void skuStatusFromCodeShouldRoundTrip() {
        for (SkuStatusEnum status : SkuStatusEnum.values()) {
            assertThat(SkuStatusEnum.fromCode(status.getCode())).isEqualTo(status);
        }
    }

    @Test
    void skuStatusActiveCanTransitionToSoldOutPausedDeprecated() {
        assertThat(SkuStatusEnum.ACTIVE.canTransitionTo(SkuStatusEnum.SOLD_OUT)).isTrue();
        assertThat(SkuStatusEnum.ACTIVE.canTransitionTo(SkuStatusEnum.PAUSED)).isTrue();
        assertThat(SkuStatusEnum.ACTIVE.canTransitionTo(SkuStatusEnum.DEPRECATED)).isTrue();
    }

    @Test
    void skuStatusDeprecatedIsTerminal() {
        assertThat(SkuStatusEnum.DEPRECATED.canTransitionTo(SkuStatusEnum.ACTIVE)).isFalse();
        assertThat(SkuStatusEnum.DEPRECATED.canTransitionTo(SkuStatusEnum.SOLD_OUT)).isFalse();
    }

    @Test
    void spuTypeEnumShouldUseFinishedNotNormal() {
        // G1-02C Codex ruling: use FINISHED, not NORMAL
        assertThat(SpuTypeEnum.FINISHED.getCode()).isEqualTo("FINISHED");
        assertThat(SpuTypeEnum.SEMI_FINISHED.getCode()).isEqualTo("SEMI_FINISHED");
        assertThat(SpuTypeEnum.RAW_MATERIAL.getCode()).isEqualTo("RAW_MATERIAL");
        // COMBO and SERVICE are future slice values
        assertThat(SpuTypeEnum.COMBO.getCode()).isEqualTo("COMBO");
        assertThat(SpuTypeEnum.SERVICE.getCode()).isEqualTo("SERVICE");
    }

    @Test
    void spuTypeEnumMustNotContainNormal() {
        for (SpuTypeEnum type : SpuTypeEnum.values()) {
            assertThat(type.getCode()).isNotEqualTo("NORMAL");
        }
    }

    @Test
    void stockStrategyEnumShouldHaveTwoValues() {
        assertThat(StockStrategyEnum.values()).hasSize(2);
        assertThat(StockStrategyEnum.TRACK_STOCK.getCode()).isEqualTo("TRACK_STOCK");
        assertThat(StockStrategyEnum.UNLIMITED.getCode()).isEqualTo("UNLIMITED");
    }

    @Test
    void priceChangeTypeEnumShouldHaveFourValues() {
        assertThat(PriceChangeTypeEnum.values()).hasSize(4);
        assertThat(PriceChangeTypeEnum.MANUAL.getCode()).isEqualTo("MANUAL");
        assertThat(PriceChangeTypeEnum.PROMOTION.getCode()).isEqualTo("PROMOTION");
        assertThat(PriceChangeTypeEnum.COST_BASED.getCode()).isEqualTo("COST_BASED");
        assertThat(PriceChangeTypeEnum.MARKET_BASED.getCode()).isEqualTo("MARKET_BASED");
    }

    @Test
    void productErrorCodesShouldBeUnique() throws IllegalAccessException {
        // G1-02F MF-1 fix: ensure no duplicate numeric error code values
        Field[] fields = ProductErrorCodeConstants.class.getDeclaredFields();
        List<Integer> codes = new ArrayList<>();
        for (Field field : fields) {
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof com.geihou.common.error.ErrorCode) {
                codes.add(((com.geihou.common.error.ErrorCode) value).getCode());
            }
        }
        Set<Integer> seen = new HashSet<>();
        for (Integer code : codes) {
            assertThat(seen.add(code))
                    .as("duplicate error code value: %d", code)
                    .isTrue();
        }
    }

    @Test
    void prdRegisteredComboAddonCodesShouldKeep1002006And1002007() {
        // PRD-G1-02 Section 7.3: COMBO_PRICE_UNREASONABLE=1002006, ADDON_GROUP_INVALID=1002007
        assertThat(ProductErrorCodeConstants.COMBO_PRICE_UNREASONABLE.getCode()).isEqualTo(1002006);
        assertThat(ProductErrorCodeConstants.ADDON_GROUP_INVALID.getCode()).isEqualTo(1002007);
    }

    @Test
    void renumberedSpuAndStatusCodesShouldNotCollideWithPrdCodes() {
        // G1-02A codes renumbered to 1002009/1002010 to avoid collision with PRD-registered 1002006/1002007
        assertThat(ProductErrorCodeConstants.SPU_MUST_BE_DEPRECATED_BEFORE_DELETE.getCode()).isEqualTo(1002009);
        assertThat(ProductErrorCodeConstants.STATUS_CHANGE_REASON_REQUIRED.getCode()).isEqualTo(1002010);
    }
}
