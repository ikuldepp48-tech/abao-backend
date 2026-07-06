package com.geihou.module.finance.cart.dal;

import com.geihou.module.finance.cart.dal.mapper.CartEventLogMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cart event log immutability test (AC-4).
 *
 * <p>Verifies that CartEventLogMapper has no update or delete methods
 * by checking that BaseMapperX's update/delete methods are not overridden
 * and the mapper interface declares no such methods.
 *
 * <p>This is a static analysis test — it checks the interface declaration
 * rather than runtime behavior.
 */
class CartEventLogImmutableTest {

    @Test
    void cartEventLogMapperDeclaresNoCustomMethods() {
        // CartEventLogMapper should only have the inherited BaseMapperX methods
        // It should not declare any update/delete methods of its own
        Method[] declaredMethods = CartEventLogMapper.class.getDeclaredMethods();
        assertThat(declaredMethods).isEmpty();
    }

    @Test
    void cartEventLogMapperHasNoUpdateByIdOverride() {
        Method[] declaredMethods = CartEventLogMapper.class.getDeclaredMethods();
        for (Method method : declaredMethods) {
            String name = method.getName().toLowerCase();
            assertThat(name).doesNotContain("update");
            assertThat(name).doesNotContain("delete");
        }
    }

    @Test
    void cartEventLogDOHasNoUpdaterOrDeletedField() {
        // The DO should not have updater/update_time/deleted fields
        // since it's INSERT-only
        java.lang.reflect.Field[] fields = com.geihou.module.finance.cart.dal.dataobject.CartEventLogDO.class.getDeclaredFields();
        for (java.lang.reflect.Field field : fields) {
            String name = field.getName();
            assertThat(name).isNotIn("updater", "updateTime", "deleted");
        }
    }
}
