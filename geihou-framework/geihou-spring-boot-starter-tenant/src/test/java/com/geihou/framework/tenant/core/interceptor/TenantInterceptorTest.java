package com.geihou.framework.tenant.core.interceptor;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class TenantInterceptorTest {

    private final TenantInterceptor interceptor = new TenantInterceptor();

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldReturnTenantIdAsExpression() {
        TenantContextHolder.setTenantId(123L);

        assertThat(interceptor.getTenantId().toString()).isEqualTo("123");
    }

    @Test
    void shouldThrowWhenTenantContextMissing() {
        assertThatIllegalStateException()
                .isThrownBy(interceptor::getTenantId)
                .withMessageContaining("Tenant context is required");
    }

    @Test
    void shouldReturnTenantIdColumn() {
        assertThat(interceptor.getTenantIdColumn()).isEqualTo("tenant_id");
    }

    @Test
    void shouldNotIgnoreAnyTableByDefault() {
        assertThat(interceptor.ignoreTable("any_table")).isFalse();
        assertThat(interceptor.ignoreTable("sys_user")).isFalse();
        assertThat(interceptor.ignoreTable("")).isFalse();
    }

    @Test
    void shouldIgnoreTableWhenIgnoreFlagIsTrue() {
        TenantContextHolder.setIgnore(true);

        assertThat(interceptor.ignoreTable("any_table")).isTrue();
    }

    @Test
    void shouldNotIgnoreTableWhenIgnoreFlagIsFalse() {
        TenantContextHolder.setIgnore(false);

        assertThat(interceptor.ignoreTable("any_table")).isFalse();
    }
}
