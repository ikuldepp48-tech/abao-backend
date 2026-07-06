package com.geihou.module.supplychain.stock.job;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.supplychain.api.stock.dto.ReconcileReportRespDTO;
import com.geihou.module.supplychain.stock.dal.mapper.StockEventMapper;
import com.geihou.module.supplychain.stock.service.BalanceReconcileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduled job that triggers balance reconciliation across all tenants.
 *
 * <p>Default disabled. To enable, set {@code stock.reconcile.enabled=true} and
 * ensure {@code @EnableScheduling} is present on a configuration class.
 *
 * <p>Cron expression is configurable via {@code stock.reconcile.cron},
 * defaulting to daily at 04:00 ({@code 0 0 4 * * ?}).
 *
 * <p>The job delegates entirely to {@link BalanceReconcileService#reconcileAll},
 * iterating over all tenants with stock_event data. Each tenant is reconciled
 * independently; failure on one tenant does not abort the batch.
 *
 * <p>The job is strictly read-only: it does not call recordEvent, does not
 * update stock_balance, and does not auto-fix any discrepancies. Reconciliation
 * results are logged as summaries only.
 *
 * <p>Tenant enumeration: uses {@link StockEventMapper#selectDistinctTenantIds()}
 * to obtain all tenant IDs that have stock_event data. No hardcoded tenant IDs.
 *
 * <p>Source: TASK-G2-02J-2, PRD-组2-02 §10.1.
 */
@Component
public class BalanceReconcileJob {

    private static final Logger log = LoggerFactory.getLogger(BalanceReconcileJob.class);

    @Autowired
    private BalanceReconcileService reconcileService;

    @Autowired
    private StockEventMapper stockEventMapper;

    @Value("${stock.reconcile.enabled:false}")
    private boolean enabled;

    /**
     * Execute balance reconciliation on schedule.
     *
     * <p>Iterates over all tenants with stock_event data, calling
     * {@link BalanceReconcileService#reconcileAll} for each. Single-tenant
     * exceptions are caught and logged; the batch continues.
     */
    @Scheduled(cron = "${stock.reconcile.cron:0 0 4 * * ?}")
    public void execute() {
        if (!enabled) {
            return;
        }

        log.info("BalanceReconcileJob triggered");

        List<Long> tenantIds = stockEventMapper.selectDistinctTenantIds();

        int totalTenants = tenantIds.size();
        int successCount = 0;
        int failedCount = 0;
        int totalMismatches = 0;
        int totalLineTraceGaps = 0;

        for (Long tenantId : tenantIds) {
            try {
                // Set tenant context for the interceptor (service queries are
                // tenant-scoped via both explicit parameter and interceptor)
                TenantContextHolder.setTenantId(tenantId);
                try {
                    ReconcileReportRespDTO report = reconcileService.reconcileAll(tenantId);
                    log.info("BalanceReconcileJob tenant={} reconciled: totalDimensions={}, matched={}, mismatched={}, eventsWithoutBalance={}, balancesWithoutEvents={}, ambiguousSign={}, lineTraceGap={}",
                            tenantId, report.getTotalDimensions(), report.getMatchedCount(),
                            report.getMismatchedCount(), report.getEventsWithoutBalanceCount(),
                            report.getBalancesWithoutEventsCount(), report.getAmbiguousSignCount(),
                            report.getLineTraceGapCount());
                    successCount++;
                    totalMismatches += report.getMismatchedCount();
                    totalLineTraceGaps += report.getLineTraceGapCount();
                } finally {
                    TenantContextHolder.clear();
                }
            } catch (Exception e) {
                log.error("BalanceReconcileJob tenant={} failed", tenantId, e);
                failedCount++;
            }
        }

        log.info("BalanceReconcileJob completed: tenants={}, success={}, failed={}, totalMismatches={}, totalLineTraceGaps={}",
                totalTenants, successCount, failedCount, totalMismatches, totalLineTraceGaps);
    }
}
