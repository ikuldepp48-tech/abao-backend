package com.geihou.framework.tenant.core.interceptor;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.geihou.framework.tenant.core.context.TenantContextHolder;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;

/**
 * MyBatis-Plus tenant line handler for SQL-level tenant isolation.
 */
public class TenantInterceptor implements TenantLineHandler {

    static final String TENANT_ID_COLUMN = "tenant_id";

    @Override
    public Expression getTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context is required for tenant SQL isolation");
        }
        return new LongValue(tenantId);
    }

    @Override
    public String getTenantIdColumn() {
        return TENANT_ID_COLUMN;
    }

    @Override
    public boolean ignoreTable(String tableName) {
        return TenantContextHolder.isIgnore();
    }
}
