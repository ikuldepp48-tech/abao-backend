package com.geihou.module.supplychain.stock.framework;

import com.geihou.module.supplychain.api.stock.dto.StockBalanceRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockEventReqDTO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockBalanceDO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockEventDO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BigDecimal verification test for quantity/amount fields.
 *
 * <p>Covers: AC-6 (all quantity/amount fields use BigDecimal, no double/float).
 */
class BigDecimalQuantityTest {

    @Test
    void stockEventDOQuantityFieldsAreBigDecimal() {
        List<String> nonBigDecimalFields = new ArrayList<>();
        for (Field field : StockEventDO.class.getDeclaredFields()) {
            String name = field.getName().toLowerCase();
            if (isQuantityOrAmountField(name)) {
                if (field.getType() != BigDecimal.class) {
                    nonBigDecimalFields.add(field.getName() + " (" + field.getType().getSimpleName() + ")");
                }
            }
        }
        assertThat(nonBigDecimalFields).as("Non-BigDecimal quantity/amount fields in StockEventDO").isEmpty();
    }

    @Test
    void stockBalanceDOQuantityFieldsAreBigDecimal() {
        List<String> nonBigDecimalFields = new ArrayList<>();
        for (Field field : StockBalanceDO.class.getDeclaredFields()) {
            String name = field.getName().toLowerCase();
            if (isQuantityOrAmountField(name)) {
                if (field.getType() != BigDecimal.class) {
                    nonBigDecimalFields.add(field.getName() + " (" + field.getType().getSimpleName() + ")");
                }
            }
        }
        assertThat(nonBigDecimalFields).as("Non-BigDecimal quantity/amount fields in StockBalanceDO").isEmpty();
    }

    @Test
    void stockEventReqDTOQuantityFieldsAreBigDecimal() {
        List<String> nonBigDecimalFields = new ArrayList<>();
        for (Field field : StockEventReqDTO.class.getDeclaredFields()) {
            String name = field.getName().toLowerCase();
            if (isQuantityOrAmountField(name)) {
                if (field.getType() != BigDecimal.class) {
                    nonBigDecimalFields.add(field.getName() + " (" + field.getType().getSimpleName() + ")");
                }
            }
        }
        assertThat(nonBigDecimalFields).as("Non-BigDecimal quantity/amount fields in StockEventReqDTO").isEmpty();
    }

    @Test
    void stockBalanceRespDTOQuantityFieldsAreBigDecimal() {
        List<String> nonBigDecimalFields = new ArrayList<>();
        for (Field field : StockBalanceRespDTO.class.getDeclaredFields()) {
            String name = field.getName().toLowerCase();
            if (isQuantityOrAmountField(name)) {
                if (field.getType() != BigDecimal.class) {
                    nonBigDecimalFields.add(field.getName() + " (" + field.getType().getSimpleName() + ")");
                }
            }
        }
        assertThat(nonBigDecimalFields).as("Non-BigDecimal quantity/amount fields in StockBalanceRespDTO").isEmpty();
    }

    private boolean isQuantityOrAmountField(String fieldName) {
        return fieldName.contains("qty") || fieldName.contains("quantity")
                || fieldName.contains("cost") || fieldName.contains("amount")
                || fieldName.contains("price") || fieldName.contains("balance")
                || fieldName.contains("threshold");
    }
}
