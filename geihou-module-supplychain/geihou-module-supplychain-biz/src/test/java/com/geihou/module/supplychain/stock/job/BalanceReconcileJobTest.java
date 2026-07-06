package com.geihou.module.supplychain.stock.job;

import com.geihou.module.supplychain.api.stock.dto.ReconcileReportRespDTO;
import com.geihou.module.supplychain.stock.dal.mapper.StockEventMapper;
import com.geihou.module.supplychain.stock.service.BalanceReconcileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BalanceReconcileJob} (G2-02J-2).
 *
 * <p>Uses Mockito mocks — no Spring context, no DB.
 *
 * <p>Covers task package §8.1 T-01 through T-08.
 */
@ExtendWith(MockitoExtension.class)
class BalanceReconcileJobTest {

    @Mock
    private BalanceReconcileService reconcileService;

    @Mock
    private StockEventMapper stockEventMapper;

    @InjectMocks
    private BalanceReconcileJob job;

    @BeforeEach
    void setUp() {
        // Default to disabled; individual tests override
        ReflectionTestUtils.setField(job, "enabled", false);
    }

    // ================================================================
    // T-01: disabled_no_op
    // ================================================================

    @Test
    void disabled_no_op() {
        ReflectionTestUtils.setField(job, "enabled", false);

        job.execute();

        verifyNoInteractions(reconcileService);
        verify(stockEventMapper, never()).selectDistinctTenantIds();
    }

    // ================================================================
    // T-02: enabled_invokes_tenants
    // ================================================================

    @Test
    void enabled_invokes_tenants() {
        ReflectionTestUtils.setField(job, "enabled", true);
        when(stockEventMapper.selectDistinctTenantIds()).thenReturn(List.of(1L, 2L));
        when(reconcileService.reconcileAll(eq(1L))).thenReturn(buildReport(1L, 2, 2, 0));
        when(reconcileService.reconcileAll(eq(2L))).thenReturn(buildReport(2L, 1, 1, 0));

        job.execute();

        verify(reconcileService).reconcileAll(eq(1L));
        verify(reconcileService).reconcileAll(eq(2L));
        verify(reconcileService, times(2)).reconcileAll(anyLong());
    }

    // ================================================================
    // T-03: exception_isolation
    // ================================================================

    @Test
    void exception_isolation() {
        ReflectionTestUtils.setField(job, "enabled", true);
        when(stockEventMapper.selectDistinctTenantIds()).thenReturn(List.of(1L, 2L, 3L));
        when(reconcileService.reconcileAll(eq(1L))).thenReturn(buildReport(1L, 1, 1, 0));
        when(reconcileService.reconcileAll(eq(2L))).thenThrow(new RuntimeException("DB connection lost"));
        when(reconcileService.reconcileAll(eq(3L))).thenReturn(buildReport(3L, 1, 1, 0));

        job.execute();

        // Tenant 1 and 3 should still be invoked despite tenant 2 failure
        verify(reconcileService).reconcileAll(eq(1L));
        verify(reconcileService).reconcileAll(eq(2L));
        verify(reconcileService).reconcileAll(eq(3L));
    }

    // ================================================================
    // T-04: empty_tenants_no_crash
    // ================================================================

    @Test
    void empty_tenants_no_crash() {
        ReflectionTestUtils.setField(job, "enabled", true);
        when(stockEventMapper.selectDistinctTenantIds()).thenReturn(List.of());

        job.execute();

        verify(reconcileService, never()).reconcileAll(anyLong());
    }

    // ================================================================
    // T-05: read_only_no_record_event
    // ================================================================

    @Test
    void read_only_no_record_event() {
        // The Job class does not inject or reference StockEventService at all.
        // Verify that the Job class has no field of type StockEventService
        // by checking declared fields.
        boolean hasStockEventServiceField = List.of(BalanceReconcileJob.class.getDeclaredFields())
                .stream()
                .noneMatch(f -> f.getType().getName().contains("StockEventService"));
        assertThat(hasStockEventServiceField).isTrue();

        // Also verify no method on Job references recordEvent
        boolean hasRecordEventCall = List.of(BalanceReconcileJob.class.getDeclaredMethods())
                .stream()
                .map(Method::getName)
                .noneMatch(name -> name.contains("recordEvent"));
        assertThat(hasRecordEventCall).isTrue();
    }

    // ================================================================
    // T-06: read_only_no_mapper_write
    // ================================================================

    @Test
    void read_only_no_mapper_write() {
        // The Job class does not inject StockBalanceMapper at all.
        // Verify that the Job class has no field of type StockBalanceMapper.
        boolean hasStockBalanceMapperField = List.of(BalanceReconcileJob.class.getDeclaredFields())
                .stream()
                .noneMatch(f -> f.getType().getName().contains("StockBalanceMapper"));
        assertThat(hasStockBalanceMapperField).isTrue();
    }

    // ================================================================
    // T-07: cron_default_value
    // ================================================================

    @Test
    void cron_default_value() throws NoSuchMethodException {
        Method executeMethod = BalanceReconcileJob.class.getDeclaredMethod("execute");
        Scheduled scheduled = executeMethod.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        // The default cron is specified as a placeholder in @Value
        assertThat(scheduled.cron()).isEqualTo("${stock.reconcile.cron:0 0 4 * * ?}");
    }

    // ================================================================
    // T-08: single_tenant_summary
    // ================================================================

    @Test
    void single_tenant_summary() {
        ReflectionTestUtils.setField(job, "enabled", true);
        when(stockEventMapper.selectDistinctTenantIds()).thenReturn(List.of(1L));
        // Report with 3 dimensions: 2 MATCH + 1 MISMATCH
        when(reconcileService.reconcileAll(eq(1L))).thenReturn(buildReport(1L, 3, 2, 1));

        job.execute();

        verify(reconcileService).reconcileAll(eq(1L));
        // The job should complete without error; mismatch count is logged
    }

    // ================================================================
    // Additional: catch_Exception_not_Throwable
    // ================================================================

    @Test
    void catch_exception_not_throwable() {
        // Verify that the execute method's try-catch catches Exception, not Throwable.
        // We test this by ensuring an Error (e.g., OutOfMemoryError) propagates.
        ReflectionTestUtils.setField(job, "enabled", true);
        when(stockEventMapper.selectDistinctTenantIds()).thenReturn(List.of(1L));
        when(reconcileService.reconcileAll(eq(1L))).thenThrow(new Error("Simulated JVM error"));

        // Error should propagate, not be caught by catch(Exception)
        try {
            job.execute();
            // If we reach here, the Error was caught — that's wrong
            throw new AssertionError("Error should not be caught by catch(Exception)");
        } catch (Error e) {
            assertThat(e.getMessage()).isEqualTo("Simulated JVM error");
        }

        verify(reconcileService).reconcileAll(eq(1L));
    }

    // ================================================================
    // Helpers
    // ================================================================

    private ReconcileReportRespDTO buildReport(Long tenantId, int totalDimensions,
                                                int matchedCount, int mismatchedCount) {
        ReconcileReportRespDTO report = new ReconcileReportRespDTO();
        report.setTenantId(tenantId);
        report.setReconcileTime(LocalDateTime.now());
        report.setTotalDimensions(totalDimensions);
        report.setMatchedCount(matchedCount);
        report.setMismatchedCount(mismatchedCount);
        report.setEventsWithoutBalanceCount(0);
        report.setBalancesWithoutEventsCount(0);
        report.setAmbiguousSignCount(0);
        report.setItems(List.of());
        return report;
    }
}
