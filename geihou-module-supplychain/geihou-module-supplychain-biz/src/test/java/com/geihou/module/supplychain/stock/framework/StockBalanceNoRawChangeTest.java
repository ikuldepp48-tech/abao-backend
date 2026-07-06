package com.geihou.module.supplychain.stock.framework;

import com.geihou.module.supplychain.stock.service.StockBalanceService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test that stock_balance balance cannot be raw-changed via public API.
 *
 * <p>Covers: AC-2 (stock_balance 不可裸改 — no setAvailableQty/setTotalQty in service interface).
 */
class StockBalanceNoRawChangeTest {

    @Test
    void stockBalanceServiceHasNoSetAvailableQtyMethod() {
        Method[] methods = StockBalanceService.class.getDeclaredMethods();
        for (Method method : methods) {
            String methodName = method.getName().toLowerCase();
            assertThat(methodName).doesNotContain("setavailableqty");
            assertThat(methodName).doesNotContain("settotalqty");
        }
    }

    @Test
    void stockBalanceServiceHasNoUpdateBalanceMethod() {
        Method[] methods = StockBalanceService.class.getDeclaredMethods();
        for (Method method : methods) {
            String methodName = method.getName().toLowerCase();
            assertThat(methodName).doesNotContain("updatebalance");
            assertThat(methodName).doesNotContain("setbalance");
        }
    }

    @Test
    void stockBalanceServiceOnlyHasReadOnlyMethods() {
        Method[] methods = StockBalanceService.class.getDeclaredMethods();
        // Should only have: getAvailableQty, checkAvailable, getBalance, getReservedQty
        assertThat(methods).hasSize(4);
        for (Method method : methods) {
            String methodName = method.getName().toLowerCase();
            assertThat(methodName.startsWith("get") || methodName.startsWith("check"))
                    .as("Method " + method.getName() + " should be read-only (get/check prefix)")
                    .isTrue();
        }
    }

    @Test
    void stockBalanceServiceHasNoSetReservedQtyMethod() {
        Method[] methods = StockBalanceService.class.getDeclaredMethods();
        for (Method method : methods) {
            String methodName = method.getName().toLowerCase();
            assertThat(methodName).doesNotContain("setreservedqty");
        }
    }
}
