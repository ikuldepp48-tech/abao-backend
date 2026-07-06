package com.geihou.module.supplychain.stock.service;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.supplychain.api.stock.dto.ReconcileItemDTO;
import com.geihou.module.supplychain.api.stock.dto.ReconcileReportRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockEventReqDTO;
import com.geihou.module.supplychain.api.stock.enums.StockDirectionEnum;
import com.geihou.module.supplychain.api.stock.enums.StockEventTypeEnum;
import com.geihou.module.supplychain.stock.SupplychainTestConfig;
import com.geihou.module.supplychain.stock.SupplychainTestSchemaInitializer;
import com.geihou.module.supplychain.stock.dal.dataobject.StockBalanceDO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockEventDO;
import com.geihou.module.supplychain.stock.dal.mapper.StockBalanceMapper;
import com.geihou.module.supplychain.stock.dal.mapper.StockEventMapper;
import com.geihou.module.supplychain.stock.framework.StockBusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link BalanceReconcileService} (G2-02J).
 *
 * <p>Uses Spring + H2 in MySQL mode. Each test seeds stock_event and
 * stock_balance rows directly via mappers, then invokes the reconciliation
 * service and asserts the report.
 *
 * <p>Covers task package §10.1 T-01 through T-18.
 */
@SpringBootTest(
        classes = SupplychainTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:reconcile_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class BalanceReconcileServiceTest {

    @Autowired
    private BalanceReconcileService balanceReconcileService;
    @Autowired
    private StockEventMapper stockEventMapper;
    @Autowired
    private StockBalanceMapper stockBalanceMapper;
    @Autowired
    private DataSource dataSource;

    private static final Long TENANT_A = 1L;
    private static final Long TENANT_B = 2L;
    private static final Long ITEM_1 = 1001L;
    private static final Long ITEM_2 = 1002L;
    private static final Long LOC_1 = 10L;
    private static final Long LOC_2 = 20L;
    private static final String SKU_1 = "SKU_001";
    private static final String SKU_2 = "SKU_002";

    @BeforeEach
    void setUp() throws Exception {
        SupplychainTestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(TENANT_A);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // ================================================================
    // T-01: matched balance
    // ================================================================

    @Test
    void reconcile_matched_balance() {
        // 2 IN events (100 + 50) + 1 OUT event (70) → expected = 80
        insertEvent(TENANT_A, ITEM_1, SKU_1, LOC_1, "PURCHASE_IN", "IN",
                new BigDecimal("100"), new BigDecimal("100"), 1);
        insertEvent(TENANT_A, ITEM_1, SKU_1, LOC_1, "PURCHASE_IN", "IN",
                new BigDecimal("50"), new BigDecimal("150"), 2);
        insertEvent(TENANT_A, ITEM_1, SKU_1, LOC_1, "CONSUME_OUT", "OUT",
                new BigDecimal("70"), new BigDecimal("80"), 3);
        insertBalance(TENANT_A, ITEM_1, LOC_1, new BigDecimal("80"), new BigDecimal("80"));

        ReconcileReportRespDTO report = balanceReconcileService.reconcileAll(TENANT_A);

        assertThat(report.getTotalDimensions()).isEqualTo(1);
        assertThat(report.getMatchedCount()).isEqualTo(1);
        assertThat(report.getMismatchedCount()).isEqualTo(0);
        ReconcileItemDTO item = report.getItems().get(0);
        assertThat(item.getStatus()).isEqualTo("MATCH");
        assertThat(item.getExpectedTotalQty()).isEqualByComparingTo(new BigDecimal("80"));
        assertThat(item.getActualTotalQty()).isEqualByComparingTo(new BigDecimal("80"));
        assertThat(item.getEventCount()).isEqualTo(3);
    }

    // ================================================================
    // T-02: mismatched balance
    // ================================================================

    @Test
    void reconcile_mismatched_balance() {
        // Events: 100 IN → expected = 100; balance = 80
        insertEvent(TENANT_A, ITEM_1, SKU_1, LOC_1, "PURCHASE_IN", "IN",
                new BigDecimal("100"), new BigDecimal("100"), 1);
        insertBalance(TENANT_A, ITEM_1, LOC_1, new BigDecimal("80"), new BigDecimal("80"));

        ReconcileReportRespDTO report = balanceReconcileService.reconcileAll(TENANT_A);

        assertThat(report.getMismatchedCount()).isEqualTo(1);
        ReconcileItemDTO item = report.getItems().get(0);
        assertThat(item.getStatus()).isEqualTo("MISMATCH");
        assertThat(item.getExpectedTotalQty()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(item.getActualTotalQty()).isEqualByComparingTo(new BigDecimal("80"));
        // totalQtyDiff = expected - actual = 100 - 80 = 20
        assertThat(item.getTotalQtyDiff()).isEqualByComparingTo(new BigDecimal("20"));
    }

    // ================================================================
    // T-03: tenant isolation
    // ================================================================

    @Test
    void reconcile_tenant_isolation() {
        // Tenant A: event + balance
        insertEvent(TENANT_A, ITEM_1, SKU_1, LOC_1, "PURCHASE_IN", "IN",
                new BigDecimal("100"), new BigDecimal("100"), 1);
        insertBalance(TENANT_A, ITEM_1, LOC_1, new BigDecimal("100"), new BigDecimal("100"));

        // Tenant B: event + balance
        insertEvent(TENANT_B, ITEM_2, SKU_2, LOC_2, "PURCHASE_IN", "IN",
                new BigDecimal("50"), new BigDecimal("50"), 10);
        insertBalance(TENANT_B, ITEM_2, LOC_2, new BigDecimal("50"), new BigDecimal("50"));

        ReconcileReportRespDTO report = balanceReconcileService.reconcileAll(TENANT_A);

        assertThat(report.getTenantId()).isEqualTo(TENANT_A);
        assertThat(report.getTotalDimensions()).isEqualTo(1);
        ReconcileItemDTO item = report.getItems().get(0);
        assertThat(item.getStockItemId()).isEqualTo(ITEM_1);
        assertThat(item.getLocationId()).isEqualTo(LOC_1);
        // Tenant B data must not appear
        assertThat(report.getItems()).allSatisfy(i -> {
            assertThat(i.getStockItemId()).isNotEqualTo(ITEM_2);
        });
    }

    // ================================================================
    // T-04: empty — no events, no balance
    // ================================================================

    @Test
    void reconcile_empty_no_events_no_balance() {
        ReconcileReportRespDTO report = balanceReconcileService.reconcileAll(TENANT_A);

        assertThat(report.getTotalDimensions()).isEqualTo(0);
        assertThat(report.getMatchedCount()).isEqualTo(0);
        assertThat(report.getItems()).isEmpty();
    }

    // ================================================================
    // T-05: events without balance
    // ================================================================

    @Test
    void reconcile_events_without_balance() {
        insertEvent(TENANT_A, ITEM_1, SKU_1, LOC_1, "PURCHASE_IN", "IN",
                new BigDecimal("50"), new BigDecimal("50"), 1);

        ReconcileReportRespDTO report = balanceReconcileService.reconcileAll(TENANT_A);

        assertThat(report.getEventsWithoutBalanceCount()).isEqualTo(1);
        ReconcileItemDTO item = report.getItems().get(0);
        assertThat(item.getStatus()).isEqualTo("EVENT_WITHOUT_BALANCE");
        assertThat(item.getExpectedTotalQty()).isEqualByComparingTo(new BigDecimal("50"));
        assertThat(item.getActualTotalQty()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ================================================================
    // T-06: balance without events
    // ================================================================

    @Test
    void reconcile_balance_without_events() {
        insertBalance(TENANT_A, ITEM_1, LOC_1, new BigDecimal("100"), new BigDecimal("100"));

        ReconcileReportRespDTO report = balanceReconcileService.reconcileAll(TENANT_A);

        assertThat(report.getBalancesWithoutEventsCount()).isEqualTo(1);
        ReconcileItemDTO item = report.getItems().get(0);
        assertThat(item.getStatus()).isEqualTo("BALANCE_WITHOUT_EVENT");
        assertThat(item.getExpectedTotalQty()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(item.getActualTotalQty()).isEqualByComparingTo(new BigDecimal("100"));
    }

    // ================================================================
    // T-07: COUNT_ADJUST positive (surplus)
    // ================================================================

    @Test
    void reconcile_count_adjust_positive() {
        // First: PURCHASE_IN 100, balance_after = 100
        insertEvent(TENANT_A, ITEM_1, SKU_1, LOC_1, "PURCHASE_IN", "IN",
                new BigDecimal("100"), new BigDecimal("100"), 1);
        // COUNT_ADJUST: balance_after = 120 → diff > 0 → inferred sign = +1
        insertEvent(TENANT_A, ITEM_1, SKU_1, LOC_1, "COUNT_ADJUST", "INTERNAL",
                new BigDecimal("20"), new BigDecimal("120"), 2);
        // Balance matches: 100 + 20 = 120
        insertBalance(TENANT_A, ITEM_1, LOC_1, new BigDecimal("120"), new BigDecimal("120"));

        ReconcileReportRespDTO report = balanceReconcileService.reconcileAll(TENANT_A);

        ReconcileItemDTO item = report.getItems().get(0);
        assertThat(item.getStatus()).isEqualTo("MATCH");
        assertThat(item.getExpectedTotalQty()).isEqualByComparingTo(new BigDecimal("120"));
        assertThat(item.getInferredAdjustmentSign()).isEqualTo(1);
        assertThat(item.isHasAmbiguousSign()).isFalse();
    }

    // ================================================================
    // T-08: COUNT_ADJUST negative (loss)
    // ================================================================

    @Test
    void reconcile_count_adjust_negative() {
        // First: PURCHASE_IN 100, balance_after = 100
        insertEvent(TENANT_A, ITEM_1, SKU_1, LOC_1, "PURCHASE_IN", "IN",
                new BigDecimal("100"), new BigDecimal("100"), 1);
        // COUNT_ADJUST: balance_after = 80 → diff < 0 → inferred sign = -1
        insertEvent(TENANT_A, ITEM_1, SKU_1, LOC_1, "COUNT_ADJUST", "INTERNAL",
                new BigDecimal("20"), new BigDecimal("80"), 2);
        // Balance matches: 100 - 20 = 80
        insertBalance(TENANT_A, ITEM_1, LOC_1, new BigDecimal("80"), new BigDecimal("80"));

        ReconcileReportRespDTO report = balanceReconcileService.reconcileAll(TENANT_A);

        ReconcileItemDTO item = report.getItems().get(0);
        assertThat(item.getStatus()).isEqualTo("MATCH");
        assertThat(item.getExpectedTotalQty()).isEqualByComparingTo(new BigDecimal("80"));
        assertThat(item.getInferredAdjustmentSign()).isEqualTo(-1);
        assertThat(item.isHasAmbiguousSign()).isFalse();
    }

    // ================================================================
    // T-09: COUNT_ADJUST ambiguous (diff = 0)
    // ================================================================

    @Test
    void reconcile_count_adjust_ambiguous() {
        // PURCHASE_IN 100, balance_after = 100
        insertEvent(TENANT_A, ITEM_1, SKU_1, LOC_1, "PURCHASE_IN", "IN",
                new BigDecimal("100"), new BigDecimal("100"), 1);
        // COUNT_ADJUST: balance_after = 100 → diff = 0 → ambiguous
        insertEvent(TENANT_A, ITEM_1, SKU_1, LOC_1, "COUNT_ADJUST", "INTERNAL",
                new BigDecimal("10"), new BigDecimal("100"), 2);
        // Expected: only IN contributes (100), ambiguous event skipped
        insertBalance(TENANT_A, ITEM_1, LOC_1, new BigDecimal("100"), new BigDecimal("100"));

        ReconcileReportRespDTO report = balanceReconcileService.reconcileAll(TENANT_A);

        ReconcileItemDTO item = report.getItems().get(0);
        assertThat(item.isHasAmbiguousSign()).isTrue();
        assertThat(item.getInferredAdjustmentSign()).isEqualTo(0);
        assertThat(item.getInferenceWarnings()).isNotEmpty();
        assertThat(report.getAmbiguousSignCount()).isEqualTo(1);
        // Expected = 100 (ambiguous event skipped), actual = 100 → MATCH
        assertThat(item.getStatus()).isEqualTo("MATCH");
    }

    // ================================================================
    // T-10: mixed events (IN + OUT + INTERNAL)
    // ================================================================

    @Test
    void reconcile_mixed_events() {
        // PURCHASE_IN 200 → balance_after = 200
        insertEvent(TENANT_A, ITEM_1, SKU_1, LOC_1, "PURCHASE_IN", "IN",
                new BigDecimal("200"), new BigDecimal("200"), 1);
        // CONSUME_OUT 50 → balance_after = 150
        insertEvent(TENANT_A, ITEM_1, SKU_1, LOC_1, "CONSUME_OUT", "OUT",
                new BigDecimal("50"), new BigDecimal("150"), 2);
        // COUNT_ADJUST surplus 30 → balance_after = 180 → inferred +1
        insertEvent(TENANT_A, ITEM_1, SKU_1, LOC_1, "COUNT_ADJUST", "INTERNAL",
                new BigDecimal("30"), new BigDecimal("180"), 3);
        // Expected: 200 - 50 + 30 = 180
        insertBalance(TENANT_A, ITEM_1, LOC_1, new BigDecimal("180"), new BigDecimal("180"));

        ReconcileReportRespDTO report = balanceReconcileService.reconcileAll(TENANT_A);

        ReconcileItemDTO item = report.getItems().get(0);
        assertThat(item.getStatus()).isEqualTo("MATCH");
        assertThat(item.getExpectedTotalQty()).isEqualByComparingTo(new BigDecimal("180"));
        assertThat(item.getInferredAdjustmentSign()).isEqualTo(1);
    }

    // ================================================================
    // T-11: reconcile by item
    // ================================================================

    @Test
    void reconcile_by_item() {
        // Item 1 at location 1
        insertEvent(TENANT_A, ITEM_1, SKU_1, LOC_1, "PURCHASE_IN", "IN",
                new BigDecimal("100"), new BigDecimal("100"), 1);
        insertBalance(TENANT_A, ITEM_1, LOC_1, new BigDecimal("100"), new BigDecimal("100"));
        // Item 2 at location 2
        insertEvent(TENANT_A, ITEM_2, SKU_2, LOC_2, "PURCHASE_IN", "IN",
                new BigDecimal("50"), new BigDecimal("50"), 2);
        insertBalance(TENANT_A, ITEM_2, LOC_2, new BigDecimal("50"), new BigDecimal("50"));

        ReconcileReportRespDTO report = balanceReconcileService.reconcileByItem(TENANT_A, ITEM_1);

        assertThat(report.getTotalDimensions()).isEqualTo(1);
        assertThat(report.getItems().get(0).getStockItemId()).isEqualTo(ITEM_1);
    }

    // ================================================================
    // T-12: reconcile by item + location
    // ================================================================

    @Test
    void reconcile_by_item_location() {
        insertEvent(TENANT_A, ITEM_1, SKU_1, LOC_1, "PURCHASE_IN", "IN",
                new BigDecimal("100"), new BigDecimal("100"), 1);
        insertBalance(TENANT_A, ITEM_1, LOC_1, new BigDecimal("100"), new BigDecimal("100"));

        ReconcileReportRespDTO report = balanceReconcileService.reconcileByItemLocation(TENANT_A, ITEM_1, LOC_1);

        assertThat(report.getTotalDimensions()).isEqualTo(1);
        ReconcileItemDTO item = report.getItems().get(0);
        assertThat(item.getStockItemId()).isEqualTo(ITEM_1);
        assertThat(item.getLocationId()).isEqualTo(LOC_1);
        assertThat(item.getStatus()).isEqualTo("MATCH");
    }

    // ================================================================
    // T-13: read-only — no writes
    // ================================================================

    @Test
    void reconcile_read_only_no_writes() {
        insertEvent(TENANT_A, ITEM_1, SKU_1, LOC_1, "PURCHASE_IN", "IN",
                new BigDecimal("100"), new BigDecimal("100"), 1);
        insertBalance(TENANT_A, ITEM_1, LOC_1, new BigDecimal("100"), new BigDecimal("100"));

        long eventsBefore = stockEventMapper.selectAllByTenant(TENANT_A).size();
        long balancesBefore = stockBalanceMapper.selectAllByTenant(TENANT_A).size();

        balanceReconcileService.reconcileAll(TENANT_A);

        long eventsAfter = stockEventMapper.selectAllByTenant(TENANT_A).size();
        long balancesAfter = stockBalanceMapper.selectAllByTenant(TENANT_A).size();

        assertThat(eventsAfter).isEqualTo(eventsBefore);
        assertThat(balancesAfter).isEqualTo(balancesBefore);
        // Verify balance values unchanged
        StockBalanceDO bal = stockBalanceMapper.selectByTenantItemLocation(TENANT_A, ITEM_1, LOC_1);
        assertThat(bal.getTotalQty()).isEqualByComparingTo(new BigDecimal("100"));
    }

    // ================================================================
    // T-14: multi-dimension (partial match, partial mismatch)
    // ================================================================

    @Test
    void reconcile_multi_dimension() {
        // Dim 1: match
        insertEvent(TENANT_A, ITEM_1, SKU_1, LOC_1, "PURCHASE_IN", "IN",
                new BigDecimal("100"), new BigDecimal("100"), 1);
        insertBalance(TENANT_A, ITEM_1, LOC_1, new BigDecimal("100"), new BigDecimal("100"));
        // Dim 2: mismatch
        insertEvent(TENANT_A, ITEM_2, SKU_2, LOC_2, "PURCHASE_IN", "IN",
                new BigDecimal("50"), new BigDecimal("50"), 2);
        insertBalance(TENANT_A, ITEM_2, LOC_2, new BigDecimal("40"), new BigDecimal("40"));

        ReconcileReportRespDTO report = balanceReconcileService.reconcileAll(TENANT_A);

        assertThat(report.getTotalDimensions()).isEqualTo(2);
        assertThat(report.getMatchedCount()).isEqualTo(1);
        assertThat(report.getMismatchedCount()).isEqualTo(1);
    }

    // ================================================================
    // T-15: reserved_qty info-only
    // ================================================================

    @Test
    void reconcile_reserved_qty_info_only() {
        insertEvent(TENANT_A, ITEM_1, SKU_1, LOC_1, "PURCHASE_IN", "IN",
                new BigDecimal("100"), new BigDecimal("100"), 1);
        // Balance with reserved_qty = 20, available = 80, total = 100.
        // reserved_qty reduces available_qty but does not write stock_event,
        // so expectedAvailableQty (100, from events) != actualAvailableQty (80).
        // This is a known limitation: reserved_qty is info-only, not recomputed.
        // total_qty should still match (expected=100, actual=100).
        insertBalanceWithReserved(TENANT_A, ITEM_1, LOC_1,
                new BigDecimal("80"), new BigDecimal("100"), new BigDecimal("20"));

        ReconcileReportRespDTO report = balanceReconcileService.reconcileAll(TENANT_A);

        ReconcileItemDTO item = report.getItems().get(0);
        // Total qty matches (events only affect total)
        assertThat(item.getExpectedTotalQty()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(item.getActualTotalQty()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(item.getTotalQtyDiff()).isEqualByComparingTo(BigDecimal.ZERO);
        // Available qty mismatches because reserved_qty reduces actual available
        // but does not write events (known limitation — reserved is info-only)
        assertThat(item.getExpectedAvailableQty()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(item.getActualAvailableQty()).isEqualByComparingTo(new BigDecimal("80"));
        // reserved_qty is surfaced as info-only, not recomputed
        assertThat(item.getActualReservedQty()).isEqualByComparingTo(new BigDecimal("20"));
        // Status is MISMATCH because available_qty differs
        assertThat(item.getStatus()).isEqualTo("MISMATCH");
    }

    // ================================================================
    // T-16: event ordering by time then id
    // ================================================================

    @Test
    void reconcile_event_ordering_by_time_then_id() {
        LocalDateTime now = LocalDateTime.now();
        // Two events with same event_time, different ids — id ASC is tiebreaker
        insertEventWithTime(TENANT_A, ITEM_1, SKU_1, LOC_1, "PURCHASE_IN", "IN",
                new BigDecimal("60"), new BigDecimal("60"), 1, now);
        insertEventWithTime(TENANT_A, ITEM_1, SKU_1, LOC_1, "CONSUME_OUT", "OUT",
                new BigDecimal("30"), new BigDecimal("30"), 2, now);
        // Expected: 60 - 30 = 30
        insertBalance(TENANT_A, ITEM_1, LOC_1, new BigDecimal("30"), new BigDecimal("30"));

        ReconcileReportRespDTO report = balanceReconcileService.reconcileAll(TENANT_A);

        ReconcileItemDTO item = report.getItems().get(0);
        assertThat(item.getStatus()).isEqualTo("MATCH");
        assertThat(item.getExpectedTotalQty()).isEqualByComparingTo(new BigDecimal("30"));
    }

    // ================================================================
    // T-17: tenantId required
    // ================================================================

    @Test
    void reconcile_tenant_id_required() {
        assertThatThrownBy(() -> balanceReconcileService.reconcileAll(null))
                .isInstanceOf(StockBusinessException.class);
    }

    // ================================================================
    // T-18: COUNT_ADJUST as first event
    // ================================================================

    @Test
    void reconcile_count_adjust_first_event() {
        // First event is COUNT_ADJUST, prevBalance = 0
        // balance_after = 50 → diff = 50 > 0 → inferred +1
        insertEvent(TENANT_A, ITEM_1, SKU_1, LOC_1, "COUNT_ADJUST", "INTERNAL",
                new BigDecimal("50"), new BigDecimal("50"), 1);
        // Expected: +50
        insertBalance(TENANT_A, ITEM_1, LOC_1, new BigDecimal("50"), new BigDecimal("50"));

        ReconcileReportRespDTO report = balanceReconcileService.reconcileAll(TENANT_A);

        ReconcileItemDTO item = report.getItems().get(0);
        assertThat(item.getExpectedTotalQty()).isEqualByComparingTo(new BigDecimal("50"));
        assertThat(item.getInferredAdjustmentSign()).isEqualTo(1);
        assertThat(item.getStatus()).isEqualTo("MATCH");
    }

    // ================================================================
    // G2-02J-1: persisted adjustment_sign tests
    // ================================================================

    @Test
    void reconcile_prefers_persisted_sign_positive() {
        // PURCHASE_IN 100, balance_after = 100
        insertEvent(TENANT_A, ITEM_1, SKU_1, LOC_1, "PURCHASE_IN", "IN",
                new BigDecimal("100"), new BigDecimal("100"), 1);
        // COUNT_ADJUST with persisted sign = +1, balance_after = 120
        insertEventWithSign(TENANT_A, ITEM_1, SKU_1, LOC_1, "COUNT_ADJUST", "INTERNAL",
                new BigDecimal("20"), new BigDecimal("120"), 2, 1);
        insertBalance(TENANT_A, ITEM_1, LOC_1, new BigDecimal("120"), new BigDecimal("120"));

        ReconcileReportRespDTO report = balanceReconcileService.reconcileAll(TENANT_A);

        ReconcileItemDTO item = report.getItems().get(0);
        assertThat(item.getStatus()).isEqualTo("MATCH");
        assertThat(item.getExpectedTotalQty()).isEqualByComparingTo(new BigDecimal("120"));
        assertThat(item.getPersistedAdjustmentSign()).isEqualTo(1);
        assertThat(item.getInferredAdjustmentSign()).isEqualTo(1);
        assertThat(item.isHasAmbiguousSign()).isFalse();
    }

    @Test
    void reconcile_prefers_persisted_sign_negative() {
        // PURCHASE_IN 100, balance_after = 100
        insertEvent(TENANT_A, ITEM_1, SKU_1, LOC_1, "PURCHASE_IN", "IN",
                new BigDecimal("100"), new BigDecimal("100"), 1);
        // COUNT_ADJUST with persisted sign = -1, balance_after = 80
        insertEventWithSign(TENANT_A, ITEM_1, SKU_1, LOC_1, "COUNT_ADJUST", "INTERNAL",
                new BigDecimal("20"), new BigDecimal("80"), 2, -1);
        insertBalance(TENANT_A, ITEM_1, LOC_1, new BigDecimal("80"), new BigDecimal("80"));

        ReconcileReportRespDTO report = balanceReconcileService.reconcileAll(TENANT_A);

        ReconcileItemDTO item = report.getItems().get(0);
        assertThat(item.getStatus()).isEqualTo("MATCH");
        assertThat(item.getExpectedTotalQty()).isEqualByComparingTo(new BigDecimal("80"));
        assertThat(item.getPersistedAdjustmentSign()).isEqualTo(-1);
        assertThat(item.getInferredAdjustmentSign()).isEqualTo(-1);
        assertThat(item.isHasAmbiguousSign()).isFalse();
    }

    @Test
    void reconcile_fallback_legacy_null_sign() {
        // PURCHASE_IN 100, balance_after = 100
        insertEvent(TENANT_A, ITEM_1, SKU_1, LOC_1, "PURCHASE_IN", "IN",
                new BigDecimal("100"), new BigDecimal("100"), 1);
        // COUNT_ADJUST with NULL sign (legacy), balance_after = 120 → diff > 0 → inferred +1
        insertEvent(TENANT_A, ITEM_1, SKU_1, LOC_1, "COUNT_ADJUST", "INTERNAL",
                new BigDecimal("20"), new BigDecimal("120"), 2);
        insertBalance(TENANT_A, ITEM_1, LOC_1, new BigDecimal("120"), new BigDecimal("120"));

        ReconcileReportRespDTO report = balanceReconcileService.reconcileAll(TENANT_A);

        ReconcileItemDTO item = report.getItems().get(0);
        assertThat(item.getStatus()).isEqualTo("MATCH");
        assertThat(item.getExpectedTotalQty()).isEqualByComparingTo(new BigDecimal("120"));
        assertThat(item.getPersistedAdjustmentSign()).isNull();
        assertThat(item.getInferredAdjustmentSign()).isEqualTo(1);
        assertThat(item.isHasAmbiguousSign()).isFalse();
    }

    @Test
    void reconcile_fallback_legacy_null_sign_negative() {
        // PURCHASE_IN 100, balance_after = 100
        insertEvent(TENANT_A, ITEM_1, SKU_1, LOC_1, "PURCHASE_IN", "IN",
                new BigDecimal("100"), new BigDecimal("100"), 1);
        // COUNT_ADJUST with NULL sign (legacy), balance_after = 80 → diff < 0 → inferred -1
        insertEvent(TENANT_A, ITEM_1, SKU_1, LOC_1, "COUNT_ADJUST", "INTERNAL",
                new BigDecimal("20"), new BigDecimal("80"), 2);
        insertBalance(TENANT_A, ITEM_1, LOC_1, new BigDecimal("80"), new BigDecimal("80"));

        ReconcileReportRespDTO report = balanceReconcileService.reconcileAll(TENANT_A);

        ReconcileItemDTO item = report.getItems().get(0);
        assertThat(item.getStatus()).isEqualTo("MATCH");
        assertThat(item.getExpectedTotalQty()).isEqualByComparingTo(new BigDecimal("80"));
        assertThat(item.getPersistedAdjustmentSign()).isNull();
        assertThat(item.getInferredAdjustmentSign()).isEqualTo(-1);
        assertThat(item.isHasAmbiguousSign()).isFalse();
    }

    @Test
    void reconcile_ambiguous_only_legacy() {
        // PURCHASE_IN 100, balance_after = 100
        insertEvent(TENANT_A, ITEM_1, SKU_1, LOC_1, "PURCHASE_IN", "IN",
                new BigDecimal("100"), new BigDecimal("100"), 1);
        // COUNT_ADJUST with NULL sign (legacy), balance_after = 100 → diff = 0 → ambiguous
        insertEvent(TENANT_A, ITEM_1, SKU_1, LOC_1, "COUNT_ADJUST", "INTERNAL",
                new BigDecimal("10"), new BigDecimal("100"), 2);
        insertBalance(TENANT_A, ITEM_1, LOC_1, new BigDecimal("100"), new BigDecimal("100"));

        ReconcileReportRespDTO report = balanceReconcileService.reconcileAll(TENANT_A);

        ReconcileItemDTO item = report.getItems().get(0);
        assertThat(item.isHasAmbiguousSign()).isTrue();
        assertThat(item.getPersistedAdjustmentSign()).isNull();
        assertThat(item.getInferredAdjustmentSign()).isEqualTo(0);
    }

    @Test
    void reconcile_persisted_sign_no_ambiguity() {
        // PURCHASE_IN 100, balance_after = 100
        insertEvent(TENANT_A, ITEM_1, SKU_1, LOC_1, "PURCHASE_IN", "IN",
                new BigDecimal("100"), new BigDecimal("100"), 1);
        // COUNT_ADJUST with persisted sign = +1, but balance_after = 100 (diff = 0)
        // Persisted sign takes priority — NOT ambiguous
        insertEventWithSign(TENANT_A, ITEM_1, SKU_1, LOC_1, "COUNT_ADJUST", "INTERNAL",
                new BigDecimal("10"), new BigDecimal("100"), 2, 1);
        // Expected: 100 + 10 = 110 (persisted +1 used, not diff inference)
        insertBalance(TENANT_A, ITEM_1, LOC_1, new BigDecimal("110"), new BigDecimal("110"));

        ReconcileReportRespDTO report = balanceReconcileService.reconcileAll(TENANT_A);

        ReconcileItemDTO item = report.getItems().get(0);
        assertThat(item.getStatus()).isEqualTo("MATCH");
        assertThat(item.getExpectedTotalQty()).isEqualByComparingTo(new BigDecimal("110"));
        assertThat(item.getPersistedAdjustmentSign()).isEqualTo(1);
        assertThat(item.getInferredAdjustmentSign()).isEqualTo(1);
        assertThat(item.isHasAmbiguousSign()).isFalse();
    }

    @Test
    void reconcile_mixed_persisted_and_legacy() {
        // PURCHASE_IN 100, balance_after = 100
        insertEvent(TENANT_A, ITEM_1, SKU_1, LOC_1, "PURCHASE_IN", "IN",
                new BigDecimal("100"), new BigDecimal("100"), 1);
        // 1st COUNT_ADJUST: legacy NULL, balance_after = 120 → diff > 0 → inferred +1
        insertEvent(TENANT_A, ITEM_1, SKU_1, LOC_1, "COUNT_ADJUST", "INTERNAL",
                new BigDecimal("20"), new BigDecimal("120"), 2);
        // 2nd COUNT_ADJUST: persisted -1, balance_after = 100
        insertEventWithSign(TENANT_A, ITEM_1, SKU_1, LOC_1, "COUNT_ADJUST", "INTERNAL",
                new BigDecimal("20"), new BigDecimal("100"), 3, -1);
        // Expected: 100 + 20 - 20 = 100
        insertBalance(TENANT_A, ITEM_1, LOC_1, new BigDecimal("100"), new BigDecimal("100"));

        ReconcileReportRespDTO report = balanceReconcileService.reconcileAll(TENANT_A);

        ReconcileItemDTO item = report.getItems().get(0);
        assertThat(item.getStatus()).isEqualTo("MATCH");
        assertThat(item.getExpectedTotalQty()).isEqualByComparingTo(new BigDecimal("100"));
        // Last event is persisted -1, so persistedAdjustmentSign = -1
        assertThat(item.getPersistedAdjustmentSign()).isEqualTo(-1);
        assertThat(item.getInferredAdjustmentSign()).isEqualTo(-1);
        assertThat(item.isHasAmbiguousSign()).isFalse();
    }

    // ================================================================
    // BF-01: Backfill logic test (H2 Java-side equivalent)
    // ================================================================

    @Test
    void backfill_migration_logic_correct() throws Exception {
        // Insert 3 legacy COUNT_ADJUST events (adjustment_sign = NULL):
        // Event 1: balance_after = 120, prevBalance = 0 → diff > 0 → +1
        // Event 2: balance_after = 100, prevBalance = 120 → diff < 0 → -1
        // Event 3: balance_after = 100, prevBalance = 100 → diff = 0 → NULL (ambiguous)

        insertEvent(TENANT_A, ITEM_1, SKU_1, LOC_1, "COUNT_ADJUST", "INTERNAL",
                new BigDecimal("120"), new BigDecimal("120"), 1);
        insertEvent(TENANT_A, ITEM_1, SKU_1, LOC_1, "COUNT_ADJUST", "INTERNAL",
                new BigDecimal("20"), new BigDecimal("100"), 2);
        insertEvent(TENANT_A, ITEM_1, SKU_1, LOC_1, "COUNT_ADJUST", "INTERNAL",
                new BigDecimal("10"), new BigDecimal("100"), 3);

        // Verify all 3 have NULL adjustment_sign before backfill
        List<StockEventDO> eventsBefore = stockEventMapper.selectByTenantItemLocation(TENANT_A, ITEM_1, LOC_1);
        assertThat(eventsBefore).hasSize(3);
        for (StockEventDO e : eventsBefore) {
            assertThat(e.getAdjustmentSign()).isNull();
        }

        // Java-side equivalent backfill logic (same diff inference rule as migration SQL)
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             PreparedStatement updateStmt = conn.prepareStatement(
                     "UPDATE stock_event SET adjustment_sign = ? WHERE id = ?")) {

            // Query all COUNT_ADJUST events for this dimension, ordered by event_time ASC, id ASC
            ResultSet rs = stmt.executeQuery(
                    "SELECT id, balance_after FROM stock_event " +
                    "WHERE tenant_id = " + TENANT_A + " AND stock_item_id = " + ITEM_1 +
                    " AND location_id = " + LOC_1 +
                    " AND event_type = 'COUNT_ADJUST' AND direction = 'INTERNAL' " +
                    "ORDER BY event_time ASC, id ASC");

            List<long[]> eventList = new ArrayList<>();
            List<BigDecimal> balanceAfters = new ArrayList<>();
            while (rs.next()) {
                eventList.add(new long[]{rs.getLong("id")});
                balanceAfters.add(rs.getBigDecimal("balance_after"));
            }
            rs.close();

            BigDecimal prevBalance = BigDecimal.ZERO;
            for (int i = 0; i < eventList.size(); i++) {
                long eventId = eventList.get(i)[0];
                BigDecimal balanceAfter = balanceAfters.get(i);
                BigDecimal diff = balanceAfter.subtract(prevBalance);
                int cmp = diff.compareTo(BigDecimal.ZERO);
                Integer inferredSign = null;
                if (cmp > 0) {
                    inferredSign = 1;
                } else if (cmp < 0) {
                    inferredSign = -1;
                }
                // cmp == 0 → leave NULL (ambiguous)

                if (inferredSign != null) {
                    updateStmt.setInt(1, inferredSign);
                    updateStmt.setLong(2, eventId);
                    updateStmt.executeUpdate();
                }

                prevBalance = balanceAfter;
            }
        }

        // Verify backfill results
        List<StockEventDO> eventsAfter = stockEventMapper.selectByTenantItemLocation(TENANT_A, ITEM_1, LOC_1);
        assertThat(eventsAfter).hasSize(3);
        // Event 1: diff = 120 - 0 = 120 > 0 → +1
        assertThat(eventsAfter.get(0).getAdjustmentSign()).isEqualTo(1);
        // Event 2: diff = 100 - 120 = -20 < 0 → -1
        assertThat(eventsAfter.get(1).getAdjustmentSign()).isEqualTo(-1);
        // Event 3: diff = 100 - 100 = 0 → NULL (ambiguous, not backfilled)
        assertThat(eventsAfter.get(2).getAdjustmentSign()).isNull();
    }

    // ================================================================
    // G2-02W-30: source_order_item_id line trace audit for BOM restore
    // ================================================================

    @Test
    void reconcile_bomLineTraceGap_keepsBalanceMatchAndSurfacesAuditWarning() {
        insertBomEvent(TENANT_A, ITEM_1, SKU_1, LOC_1, "CONSUME_OUT", "OUT",
                new BigDecimal("10"), new BigDecimal("90"), 1, 9001L, null);
        insertBomEvent(TENANT_A, ITEM_1, SKU_1, LOC_1, "RETURN_IN", "IN",
                new BigDecimal("10"), new BigDecimal("100"), 2, 9001L, 80001L);
        insertBalance(TENANT_A, ITEM_1, LOC_1, new BigDecimal("0"), BigDecimal.ZERO);

        ReconcileReportRespDTO report = balanceReconcileService.reconcileAll(TENANT_A);

        assertThat(report.getLineTraceGapCount()).isEqualTo(1);
        ReconcileItemDTO item = report.getItems().get(0);
        assertThat(item.getStatus()).isEqualTo("MATCH");
        assertThat(item.isHasLineTraceGap()).isTrue();
        assertThat(item.getLineTraceableEventCount()).isEqualTo(1);
        assertThat(item.getLineTraceMissingEventCount()).isEqualTo(1);
        assertThat(item.getLineRestoreEventCount()).isEqualTo(1);
        assertThat(item.getHistoricalNullRestoreEventCount()).isEqualTo(0);
        assertThat(item.getLineTraceWarnings())
                .anySatisfy(warning -> assertThat(warning).contains("source_order_item_id"));
    }

    @Test
    void reconcile_historicalNullRestoreEvent_surfacesAuditWarning() {
        insertBomEvent(TENANT_A, ITEM_1, SKU_1, LOC_1, "CONSUME_OUT", "OUT",
                new BigDecimal("10"), new BigDecimal("-10"), 1, 9002L, 80002L);
        insertBomEvent(TENANT_A, ITEM_1, SKU_1, LOC_1, "PURCHASE_IN", "IN",
                new BigDecimal("10"), BigDecimal.ZERO, 2, 9002L, null);
        insertBalance(TENANT_A, ITEM_1, LOC_1, BigDecimal.ZERO, BigDecimal.ZERO);

        ReconcileReportRespDTO report = balanceReconcileService.reconcileAll(TENANT_A);

        assertThat(report.getLineTraceGapCount()).isEqualTo(1);
        ReconcileItemDTO item = report.getItems().get(0);
        assertThat(item.getStatus()).isEqualTo("MATCH");
        assertThat(item.isHasLineTraceGap()).isTrue();
        assertThat(item.getLineTraceableEventCount()).isEqualTo(1);
        assertThat(item.getLineTraceMissingEventCount()).isEqualTo(1);
        assertThat(item.getLineRestoreEventCount()).isEqualTo(0);
        assertThat(item.getHistoricalNullRestoreEventCount()).isEqualTo(1);
        assertThat(item.getLineTraceWarnings())
                .anySatisfy(warning -> assertThat(warning)
                        .contains("historical", "source_order_item_id", "PURCHASE_IN"));
    }

    // ================================================================
    // Helper methods
    // ================================================================

    private void insertEvent(Long tenantId, Long itemId, String sku, Long locId,
                             String eventType, String direction,
                             BigDecimal qty, BigDecimal balanceAfter, long idOffset) {
        insertEventWithTime(tenantId, itemId, sku, locId, eventType, direction,
                qty, balanceAfter, idOffset, LocalDateTime.now().plusNanos(idOffset));
    }

    private void insertEventWithTime(Long tenantId, Long itemId, String sku, Long locId,
                                     String eventType, String direction,
                                     BigDecimal qty, BigDecimal balanceAfter,
                                     long idOffset, LocalDateTime eventTime) {
        insertEventWithSign(tenantId, itemId, sku, locId, eventType, direction,
                qty, balanceAfter, idOffset, eventTime, null);
    }

    private void insertEventWithSign(Long tenantId, Long itemId, String sku, Long locId,
                                     String eventType, String direction,
                                     BigDecimal qty, BigDecimal balanceAfter,
                                     long idOffset, Integer adjustmentSign) {
        insertEventWithSign(tenantId, itemId, sku, locId, eventType, direction,
                qty, balanceAfter, idOffset, LocalDateTime.now().plusNanos(idOffset), adjustmentSign);
    }

    private void insertEventWithSign(Long tenantId, Long itemId, String sku, Long locId,
                                     String eventType, String direction,
                                     BigDecimal qty, BigDecimal balanceAfter,
                                     long idOffset, LocalDateTime eventTime,
                                     Integer adjustmentSign) {
        StockEventDO event = new StockEventDO();
        event.setTenantId(tenantId);
        event.setEventTime(eventTime);
        event.setBusinessDate(LocalDate.now());
        event.setEventType(eventType);
        event.setDirection(direction);
        event.setStockItemId(itemId);
        event.setSkuCode(sku);
        event.setLocationId(locId);
        event.setQuantity(qty);
        event.setUnit("个");
        event.setOperatorUserId(999L);
        event.setBalanceAfter(balanceAfter);
        event.setCreateTime(LocalDateTime.now());
        event.setAdjustmentSign(adjustmentSign);
        if ("COUNT_ADJUST".equals(eventType)) {
            event.setAdjustmentReason("This is a test adjustment reason that exceeds 30 chars");
        }
        stockEventMapper.insert(event);
    }

    private void insertBomEvent(Long tenantId, Long itemId, String sku, Long locId,
                                String eventType, String direction,
                                BigDecimal qty, BigDecimal balanceAfter, long idOffset,
                                Long sourceRecordId, Long sourceOrderItemId) {
        StockEventDO event = new StockEventDO();
        event.setTenantId(tenantId);
        event.setEventTime(LocalDateTime.now().plusNanos(idOffset));
        event.setBusinessDate(LocalDate.now());
        event.setEventType(eventType);
        event.setDirection(direction);
        event.setStockItemId(itemId);
        event.setSkuCode(sku);
        event.setLocationId(locId);
        event.setQuantity(qty);
        event.setUnit("个");
        event.setSourceModule("checkout");
        event.setSourceRecordId(sourceRecordId);
        event.setSourceOrderItemId(sourceOrderItemId);
        event.setReferenceNo("ORDER-" + sourceRecordId);
        event.setClientRequestId("bom-line-audit-" + idOffset);
        event.setOperatorUserId(999L);
        event.setBalanceAfter(balanceAfter);
        event.setCreateTime(LocalDateTime.now());
        event.setParentEventId(7000L);
        event.setRecipeId(6000L);
        event.setRecipeVersion(1);
        stockEventMapper.insert(event);
    }

    private void insertBalance(Long tenantId, Long itemId, Long locId,
                               BigDecimal totalQty, BigDecimal availableQty) {
        insertBalanceWithReserved(tenantId, itemId, locId, availableQty, totalQty, BigDecimal.ZERO);
    }

    private void insertBalanceWithReserved(Long tenantId, Long itemId, Long locId,
                                            BigDecimal availableQty, BigDecimal totalQty,
                                            BigDecimal reservedQty) {
        StockBalanceDO balance = new StockBalanceDO();
        balance.setTenantId(tenantId);
        balance.setStockItemId(itemId);
        balance.setLocationId(locId);
        balance.setAvailableQty(availableQty);
        balance.setTotalQty(totalQty);
        balance.setReservedQty(reservedQty);
        balance.setAvgUnitCost(BigDecimal.ZERO);
        balance.setVersion(0);
        balance.setCreator("test");
        balance.setCreateTime(LocalDateTime.now());
        balance.setUpdater("test");
        balance.setUpdateTime(LocalDateTime.now());
        balance.setDeleted(false);
        stockBalanceMapper.insert(balance);
    }
}
