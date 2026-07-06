package com.geihou.module.finance.cart.enums;

import com.geihou.module.finance.cart.dal.dataobject.CartDO;
import com.geihou.module.finance.cart.dal.dataobject.CartItemDO;
import com.geihou.module.finance.cart.dal.dataobject.CartEventLogDO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BigDecimal money test for cart module (AC-2).
 *
 * <p>Verifies that all money fields in CartDO, CartItemDO, and CartEventLogDO
 * are BigDecimal type. Verifies that no double/float fields are used for money.
 */
class BigDecimalMoneyTest {

    @Test
    void cartDOHasNoDoubleOrFloatMoneyFields() {
        Field[] fields = CartDO.class.getDeclaredFields();
        for (Field field : fields) {
            String name = field.getName();
            if (name.contains("amount") || name.contains("Amount")) {
                assertThat(field.getType())
                        .as("Field %s in CartDO must be BigDecimal, not %s", name, field.getType().getSimpleName())
                        .isEqualTo(BigDecimal.class);
            }
        }
    }

    @Test
    void cartItemDOHasNoDoubleOrFloatMoneyFields() {
        Field[] fields = CartItemDO.class.getDeclaredFields();
        for (Field field : fields) {
            String name = field.getName();
            if (name.contains("price") || name.contains("Price") ||
                name.contains("subtotal") || name.contains("Subtotal") ||
                name.contains("discount") || name.contains("Discount") ||
                name.contains("total") || name.contains("Total")) {
                assertThat(field.getType())
                        .as("Field %s in CartItemDO must be BigDecimal, not %s", name, field.getType().getSimpleName())
                        .isEqualTo(BigDecimal.class);
            }
        }
    }

    @Test
    void cartEventLogDOHasNoDoubleOrFloatMoneyFields() {
        Field[] fields = CartEventLogDO.class.getDeclaredFields();
        for (Field field : fields) {
            String name = field.getName();
            if (name.contains("amount") || name.contains("Amount")) {
                assertThat(field.getType())
                        .as("Field %s in CartEventLogDO must be BigDecimal, not %s", name, field.getType().getSimpleName())
                        .isEqualTo(BigDecimal.class);
            }
        }
    }

    @Test
    void noDoubleOrFloatInAnyCartDO() {
        // Check all cart DOs for any double/float fields
        checkNoDoubleFloat(CartDO.class);
        checkNoDoubleFloat(CartItemDO.class);
        checkNoDoubleFloat(CartEventLogDO.class);
    }

    private void checkNoDoubleFloat(Class<?> clazz) {
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            assertThat(field.getType())
                    .as("Field %s in %s must not be double or float", field.getName(), clazz.getSimpleName())
                    .isNotIn(double.class, Double.class, float.class, Float.class);
        }
    }
}
