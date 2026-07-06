package com.geihou.framework.tenant.core.context;

import com.alibaba.ttl.TtlRunnable;
import org.springframework.core.task.TaskDecorator;

/**
 * Captures the tenant context snapshot when a task is submitted to an executor.
 */
public class TenantTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        return TtlRunnable.get(runnable);
    }
}
