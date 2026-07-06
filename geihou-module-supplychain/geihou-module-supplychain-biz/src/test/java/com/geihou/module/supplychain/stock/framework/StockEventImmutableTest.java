package com.geihou.module.supplychain.stock.framework;

import com.geihou.module.supplychain.stock.dal.dataobject.StockEventDO;
import com.geihou.module.supplychain.stock.dal.mapper.StockEventMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test that stock_event table has no UPDATE/DELETE path.
 *
 * <p>Covers: AC-1 (stock_event INSERT-only).
 */
class StockEventImmutableTest {

    @Test
    void stockEventMapperDeclaresNoUpdateMethods() {
        Method[] declaredMethods = StockEventMapper.class.getDeclaredMethods();
        for (Method method : declaredMethods) {
            String methodName = method.getName().toLowerCase();
            assertThat(methodName).doesNotContain("update");
        }
    }

    @Test
    void stockEventMapperDeclaresNoDeleteMethods() {
        Method[] declaredMethods = StockEventMapper.class.getDeclaredMethods();
        for (Method method : declaredMethods) {
            String methodName = method.getName().toLowerCase();
            assertThat(methodName).doesNotContain("delete");
        }
    }

    @Test
    void stockEventDOHasNoUpdateTimeField() {
        // stock_event is INSERT-only — no update_time / updater field
        boolean hasUpdateTime = false;
        boolean hasUpdater = false;
        for (java.lang.reflect.Field field : StockEventDO.class.getDeclaredFields()) {
            String name = field.getName().toLowerCase();
            if (name.equals("updatetime")) hasUpdateTime = true;
            if (name.equals("updater")) hasUpdater = true;
        }
        assertThat(hasUpdateTime).as("StockEventDO must not have updateTime field (INSERT-only)").isFalse();
        assertThat(hasUpdater).as("StockEventDO must not have updater field (INSERT-only)").isFalse();
    }

    @Test
    void stockEventDOHasNoDeletedField() {
        // stock_event is INSERT-only — no soft delete
        boolean hasDeleted = false;
        for (java.lang.reflect.Field field : StockEventDO.class.getDeclaredFields()) {
            if (field.getName().toLowerCase().equals("deleted")) {
                hasDeleted = true;
                break;
            }
        }
        assertThat(hasDeleted).as("StockEventDO must not have deleted field (INSERT-only, no soft delete)").isFalse();
    }
}
