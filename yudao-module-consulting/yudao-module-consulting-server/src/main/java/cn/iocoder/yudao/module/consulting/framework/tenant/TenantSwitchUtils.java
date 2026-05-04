package cn.iocoder.yudao.module.consulting.framework.tenant;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;

/**
 * 咨询师跨租户访问工具
 * <p>
 * 咨询师属于独立租户，通过此类方法临时切换到客户租户执行查询。
 * 每次跨租户访问都会记录审计日志。
 */
@Slf4j(topic = "cross-tenant-audit")
public class TenantSwitchUtils {

    /**
     * 以指定租户身份执行查询（跨租户读）
     *
     * @param tenantId 目标客户租户 ID
     * @param callable 查询逻辑
     * @return 查询结果
     */
    public static <V> V executeAsTenant(Long tenantId, Callable<V> callable) {
        Long currentTenant = TenantContextHolder.getTenantId();
        log.info("[跨租户访问] 从租户({}) 切换到租户({}) 执行查询", currentTenant, tenantId);
        try {
            return TenantUtils.execute(tenantId, callable);
        } catch (Exception e) {
            log.error("[跨租户访问] 查询失败 tenantId({})", tenantId, e);
            throw new RuntimeException("跨租户查询失败", e);
        } finally {
            log.info("[跨租户访问] 查询完成，返回租户({})", currentTenant);
        }
    }

    /**
     * 跨多个租户聚合查询
     *
     * @param tenantIds 目标客户租户 ID 列表
     * @param action    对每个租户执行的查询逻辑
     * @return 聚合结果列表
     */
    public static <V> List<V> executeAcrossTenants(List<Long> tenantIds, Function<Long, V> action) {
        log.info("[跨租户聚合] 对 {} 个租户执行聚合查询", tenantIds.size());
        List<V> results = new ArrayList<>();
        for (Long tenantId : tenantIds) {
            V result = executeAsTenant(tenantId, () -> action.apply(tenantId));
            if (result != null) {
                results.add(result);
            }
        }
        return results;
    }

    /**
     * 以指定租户身份执行操作（跨租户写，慎用）
     *
     * @param tenantId 目标客户租户 ID
     * @param runnable 写操作逻辑
     */
    public static void executeAsTenantWrite(Long tenantId, Runnable runnable) {
        Long currentTenant = TenantContextHolder.getTenantId();
        log.warn("[跨租户写入] 从租户({}) 切换到租户({}) 执行写入", currentTenant, tenantId);
        try {
            TenantUtils.execute(tenantId, runnable);
        } finally {
            log.warn("[跨租户写入] 写入完成，返回租户({})", currentTenant);
        }
    }
}
