package com.geihou.module.supplychain.stock.service;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.supplychain.api.stock.dto.StockEventReqDTO;
import com.geihou.module.supplychain.api.stock.enums.StockDirectionEnum;
import com.geihou.module.supplychain.api.stock.enums.StockEventTypeEnum;
import com.geihou.module.supplychain.stock.SupplychainTestConfig;
import com.geihou.module.supplychain.stock.SupplychainTestSchemaInitializer;
import com.geihou.module.supplychain.stock.dal.dataobject.StockEventDO;
import com.geihou.module.supplychain.stock.dal.mapper.StockEventMapper;
import com.geihou.module.supplychain.stock.framework.StockBusinessException;
import com.geihou.module.supplychain.stock.mq.StockEventPublisher;
import com.geihou.module.supplychain.stock.mq.event.StockLowWarningEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link StockEventService}.
 *
 * <p>Covers: AC-1 (INSERT-only), AC-2 (balance not raw-changed), AC-5 (idempotent),
 * AC-11 (transactional event+balance).
 */
@SpringBootTest(
        classes = SupplychainTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:stock_event_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class StockEventServiceTest {

    @Autowired
    private StockEventService stockEventService;
    @Autowired
    private StockEventMapper stockEventMapper;
    @Autowired
    private StockBalanceService stockBalanceService;
    @Autowired
    private DataSource dataSource;
    @MockBean
    private StockEventPublisher stockEventPublisher;

    @BeforeEach
    void setUp() throws Exception {
        SupplychainTestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void recordEvent_inPurchasesIncreasesBalance() {
        StockEventReqDTO req = buildReq(StockEventTypeEnum.PURCHASE_IN, StockDirectionEnum.IN,
                new BigDecimal("100"), "req-" + UUID.randomUUID());
        Long eventId = stockEventService.recordEvent(req);

        assertThat(eventId).isNotNull();

        StockEventDO event = stockEventMapper.selectById(eventId);
        assertThat(event).isNotNull();
        assertThat(event.getEventType()).isEqualTo("PURCHASE_IN");
        assertThat(event.getDirection()).isEqualTo("IN");
        assertThat(event.getQuantity()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(event.getBalanceAfter()).isEqualByComparingTo(new BigDecimal("100"));

        BigDecimal available = stockBalanceService.getAvailableQty(1L, 1001L, 10L);
        assertThat(available).isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    void recordEvent_outConsumeDecreasesBalance() {
        // First, purchase in 100
        StockEventReqDTO inReq = buildReq(StockEventTypeEnum.PURCHASE_IN, StockDirectionEnum.IN,
                new BigDecimal("100"), "req-in-" + UUID.randomUUID());
        stockEventService.recordEvent(inReq);

        // Then, consume out 30
        StockEventReqDTO outReq = buildReq(StockEventTypeEnum.CONSUME_OUT, StockDirectionEnum.OUT,
                new BigDecimal("30"), "req-out-" + UUID.randomUUID());
        Long eventId = stockEventService.recordEvent(outReq);

        assertThat(eventId).isNotNull();

        BigDecimal available = stockBalanceService.getAvailableQty(1L, 1001L, 10L);
        assertThat(available).isEqualByComparingTo(new BigDecimal("70"));
    }

    @Test
    void recordEvent_outBelowMinThresholdPublishesLowWarningOnce() {
        stockEventService.recordEvent(buildReq(StockEventTypeEnum.PURCHASE_IN, StockDirectionEnum.IN,
                new BigDecimal("100"), "req-in-" + UUID.randomUUID()));

        updateMinThreshold(1L, 1001L, 10L, new BigDecimal("80"));

        StockEventReqDTO outReq = buildReq(StockEventTypeEnum.CONSUME_OUT, StockDirectionEnum.OUT,
                new BigDecimal("30"), "req-low-" + UUID.randomUUID());
        Long eventId = stockEventService.recordEvent(outReq);
        assertThat(eventId).isNotNull();

        verify(stockEventPublisher, times(1)).publishStockLowWarning(argThat((StockLowWarningEvent event) ->
                event.getTenantId().equals(1L)
                        && event.getStockItemId().equals(1001L)
                        && event.getLocationId().equals(10L)
                        && event.getQuantity().compareTo(new BigDecimal("70")) == 0
                        && event.getThresholdQuantity().compareTo(new BigDecimal("80")) == 0
                        && event.getWarningLevel().equals("LOW")));

        Long retryId = stockEventService.recordEvent(outReq);
        assertThat(retryId).isEqualTo(eventId);
        verify(stockEventPublisher, times(1)).publishStockLowWarning(argThat((StockLowWarningEvent event) ->
                event.getTenantId().equals(1L)
                        && event.getStockItemId().equals(1001L)
                        && event.getLocationId().equals(10L)));
    }

    @Test
    void recordEvent_idempotentSameClientRequestId() {
        String clientRequestId = "idempotent-" + UUID.randomUUID();
        StockEventReqDTO req = buildReq(StockEventTypeEnum.PURCHASE_IN, StockDirectionEnum.IN,
                new BigDecimal("50"), clientRequestId);

        Long eventId1 = stockEventService.recordEvent(req);
        Long eventId2 = stockEventService.recordEvent(req);

        assertThat(eventId1).isEqualTo(eventId2);

        // Only one event should exist
        StockEventDO event = stockEventMapper.selectByClientRequestId(1L, clientRequestId);
        assertThat(event).isNotNull();
        assertThat(event.getId()).isEqualTo(eventId1);
        verify(stockEventPublisher, never()).publishStockLowWarning(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void recordEvent_outInsufficientStockThrows() {
        StockEventReqDTO req = buildReq(StockEventTypeEnum.CONSUME_OUT, StockDirectionEnum.OUT,
                new BigDecimal("10"), "req-" + UUID.randomUUID());

        assertThatThrownBy(() -> stockEventService.recordEvent(req))
                .isInstanceOf(StockBusinessException.class);
    }

    @Test
    void recordEvent_invalidEventTypeThrows() {
        StockEventReqDTO req = buildReq(StockEventTypeEnum.PURCHASE_IN, StockDirectionEnum.IN,
                new BigDecimal("10"), "req-" + UUID.randomUUID());
        req.setEventType("INVALID_TYPE");

        assertThatThrownBy(() -> stockEventService.recordEvent(req))
                .isInstanceOf(StockBusinessException.class);
    }

    @Test
    void recordEvent_directionMismatchThrows() {
        StockEventReqDTO req = buildReq(StockEventTypeEnum.PURCHASE_IN, StockDirectionEnum.IN,
                new BigDecimal("10"), "req-" + UUID.randomUUID());
        req.setDirection("OUT"); // Mismatch: PURCHASE_IN should be IN

        assertThatThrownBy(() -> stockEventService.recordEvent(req))
                .isInstanceOf(StockBusinessException.class);
    }

    @Test
    void recordEvent_quantityMustBePositive() {
        StockEventReqDTO req = buildReq(StockEventTypeEnum.PURCHASE_IN, StockDirectionEnum.IN,
                new BigDecimal("-5"), "req-" + UUID.randomUUID());

        assertThatThrownBy(() -> stockEventService.recordEvent(req))
                .isInstanceOf(StockBusinessException.class);
    }

    @Test
    void recordEvent_nullClientRequestIdAllowed() {
        StockEventReqDTO req = buildReq(StockEventTypeEnum.PURCHASE_IN, StockDirectionEnum.IN,
                new BigDecimal("10"), null);

        Long eventId = stockEventService.recordEvent(req);
        assertThat(eventId).isNotNull();
    }

    @Test
    void recordEvent_multipleInEventsAccumulate() {
        stockEventService.recordEvent(buildReq(StockEventTypeEnum.PURCHASE_IN, StockDirectionEnum.IN,
                new BigDecimal("50"), "req-1-" + UUID.randomUUID()));
        stockEventService.recordEvent(buildReq(StockEventTypeEnum.PRODUCTION_IN, StockDirectionEnum.IN,
                new BigDecimal("30"), "req-2-" + UUID.randomUUID()));

        BigDecimal available = stockBalanceService.getAvailableQty(1L, 1001L, 10L);
        assertThat(available).isEqualByComparingTo(new BigDecimal("80"));
    }

    // ===== G2-02I-2A: COUNT_ADJUST / INTERNAL support tests =====

    @Test
    void recordEvent_countAdjustPositiveIncreasesBalance() {
        // First, purchase in 100 to have a baseline
        stockEventService.recordEvent(buildReq(StockEventTypeEnum.PURCHASE_IN, StockDirectionEnum.IN,
                new BigDecimal("100"), "req-pre-" + UUID.randomUUID()));

        // COUNT_ADJUST positive (盘盈) +20
        StockEventReqDTO req = buildReq(StockEventTypeEnum.COUNT_ADJUST, StockDirectionEnum.INTERNAL,
                new BigDecimal("20"), "req-" + UUID.randomUUID());
        req.setAdjustmentSign(1);

        Long eventId = stockEventService.recordEvent(req);
        assertThat(eventId).isNotNull();

        StockEventDO event = stockEventMapper.selectById(eventId);
        assertThat(event).isNotNull();
        assertThat(event.getEventType()).isEqualTo("COUNT_ADJUST");
        assertThat(event.getDirection()).isEqualTo("INTERNAL");
        assertThat(event.getQuantity()).isEqualByComparingTo(new BigDecimal("20"));
        assertThat(event.getBalanceAfter()).isEqualByComparingTo(new BigDecimal("120"));

        BigDecimal available = stockBalanceService.getAvailableQty(1L, 1001L, 10L);
        assertThat(available).isEqualByComparingTo(new BigDecimal("120"));
    }

    @Test
    void recordEvent_countAdjustPositiveDefaultSignIncreasesBalance() {
        // adjustmentSign null defaults to +1 (positive)
        stockEventService.recordEvent(buildReq(StockEventTypeEnum.PURCHASE_IN, StockDirectionEnum.IN,
                new BigDecimal("50"), "req-pre-" + UUID.randomUUID()));

        StockEventReqDTO req = buildReq(StockEventTypeEnum.COUNT_ADJUST, StockDirectionEnum.INTERNAL,
                new BigDecimal("10"), "req-" + UUID.randomUUID());
        // adjustmentSign intentionally left null

        Long eventId = stockEventService.recordEvent(req);
        assertThat(eventId).isNotNull();

        StockEventDO event = stockEventMapper.selectById(eventId);
        assertThat(event.getBalanceAfter()).isEqualByComparingTo(new BigDecimal("60"));

        assertThat(stockBalanceService.getAvailableQty(1L, 1001L, 10L))
                .isEqualByComparingTo(new BigDecimal("60"));
    }

    @Test
    void recordEvent_countAdjustNegativeDecreasesBalance() {
        // Baseline: 100
        stockEventService.recordEvent(buildReq(StockEventTypeEnum.PURCHASE_IN, StockDirectionEnum.IN,
                new BigDecimal("100"), "req-pre-" + UUID.randomUUID()));

        // COUNT_ADJUST negative (盘亏) -30
        StockEventReqDTO req = buildReq(StockEventTypeEnum.COUNT_ADJUST, StockDirectionEnum.INTERNAL,
                new BigDecimal("30"), "req-" + UUID.randomUUID());
        req.setAdjustmentSign(-1);

        Long eventId = stockEventService.recordEvent(req);
        assertThat(eventId).isNotNull();

        StockEventDO event = stockEventMapper.selectById(eventId);
        assertThat(event).isNotNull();
        assertThat(event.getEventType()).isEqualTo("COUNT_ADJUST");
        assertThat(event.getDirection()).isEqualTo("INTERNAL");
        assertThat(event.getQuantity()).isEqualByComparingTo(new BigDecimal("30"));
        assertThat(event.getBalanceAfter()).isEqualByComparingTo(new BigDecimal("70"));

        BigDecimal available = stockBalanceService.getAvailableQty(1L, 1001L, 10L);
        assertThat(available).isEqualByComparingTo(new BigDecimal("70"));
    }

    @Test
    void recordEvent_countAdjustNegativeInsufficientStockThrows() {
        // Baseline: 10
        stockEventService.recordEvent(buildReq(StockEventTypeEnum.PURCHASE_IN, StockDirectionEnum.IN,
                new BigDecimal("10"), "req-pre-" + UUID.randomUUID()));

        // Try to adjust -50 (more than available)
        StockEventReqDTO req = buildReq(StockEventTypeEnum.COUNT_ADJUST, StockDirectionEnum.INTERNAL,
                new BigDecimal("50"), "req-" + UUID.randomUUID());
        req.setAdjustmentSign(-1);

        assertThatThrownBy(() -> stockEventService.recordEvent(req))
                .isInstanceOf(StockBusinessException.class);

        // Balance unchanged
        assertThat(stockBalanceService.getAvailableQty(1L, 1001L, 10L))
                .isEqualByComparingTo(new BigDecimal("10"));

        // No event written
        StockEventDO event = stockEventMapper.selectByClientRequestId(1L, req.getClientRequestId());
        assertThat(event).isNull();
    }

    @Test
    void recordEvent_countAdjustIdempotentSameClientRequestId() {
        String clientRequestId = "count-adjust-idempotent-" + UUID.randomUUID();

        // Baseline: 100
        stockEventService.recordEvent(buildReq(StockEventTypeEnum.PURCHASE_IN, StockDirectionEnum.IN,
                new BigDecimal("100"), "req-pre-" + UUID.randomUUID()));

        StockEventReqDTO req = buildReq(StockEventTypeEnum.COUNT_ADJUST, StockDirectionEnum.INTERNAL,
                new BigDecimal("15"), clientRequestId);
        req.setAdjustmentSign(1);

        Long eventId1 = stockEventService.recordEvent(req);
        Long eventId2 = stockEventService.recordEvent(req);

        assertThat(eventId1).isEqualTo(eventId2);

        StockEventDO event = stockEventMapper.selectByClientRequestId(1L, clientRequestId);
        assertThat(event).isNotNull();
        assertThat(event.getId()).isEqualTo(eventId1);

        // Balance only increased once (100 + 15 = 115)
        assertThat(stockBalanceService.getAvailableQty(1L, 1001L, 10L))
                .isEqualByComparingTo(new BigDecimal("115"));
    }

    @Test
    void recordEvent_countAdjustTenantIsolation() {
        // Tenant 1: baseline 100, then +20 adjust
        TenantContextHolder.setTenantId(1L);
        stockEventService.recordEvent(buildReq(StockEventTypeEnum.PURCHASE_IN, StockDirectionEnum.IN,
                new BigDecimal("100"), "pre-t1-" + UUID.randomUUID()));
        StockEventReqDTO req1 = buildReq(StockEventTypeEnum.COUNT_ADJUST, StockDirectionEnum.INTERNAL,
                new BigDecimal("20"), "adjust-t1-" + UUID.randomUUID());
        req1.setAdjustmentSign(1);
        stockEventService.recordEvent(req1);

        // Tenant 2: baseline 200, then -50 adjust
        TenantContextHolder.setTenantId(2L);
        StockEventReqDTO pre2 = buildReq(StockEventTypeEnum.PURCHASE_IN, StockDirectionEnum.IN,
                new BigDecimal("200"), "pre-t2-" + UUID.randomUUID());
        pre2.setTenantId(2L);
        stockEventService.recordEvent(pre2);
        StockEventReqDTO req2 = buildReq(StockEventTypeEnum.COUNT_ADJUST, StockDirectionEnum.INTERNAL,
                new BigDecimal("50"), "adjust-t2-" + UUID.randomUUID());
        req2.setTenantId(2L);
        req2.setAdjustmentSign(-1);
        stockEventService.recordEvent(req2);

        // Verify independent balances
        TenantContextHolder.setTenantId(1L);
        assertThat(stockBalanceService.getAvailableQty(1L, 1001L, 10L))
                .isEqualByComparingTo(new BigDecimal("120"));
        TenantContextHolder.setTenantId(2L);
        assertThat(stockBalanceService.getAvailableQty(2L, 1001L, 10L))
                .isEqualByComparingTo(new BigDecimal("150"));
    }

    @Test
    void recordEvent_countAdjustInvalidSignThrows() {
        StockEventReqDTO req = buildReq(StockEventTypeEnum.COUNT_ADJUST, StockDirectionEnum.INTERNAL,
                new BigDecimal("10"), "req-" + UUID.randomUUID());
        req.setAdjustmentSign(0); // invalid

        assertThatThrownBy(() -> stockEventService.recordEvent(req))
                .isInstanceOf(StockBusinessException.class);
    }

    // ===== G2-02J-1: adjustment_sign persistence tests =====

    @Test
    void recordEvent_count_adjust_persists_positive_sign() {
        stockEventService.recordEvent(buildReq(StockEventTypeEnum.PURCHASE_IN, StockDirectionEnum.IN,
                new BigDecimal("100"), "req-pre-" + UUID.randomUUID()));

        StockEventReqDTO req = buildReq(StockEventTypeEnum.COUNT_ADJUST, StockDirectionEnum.INTERNAL,
                new BigDecimal("20"), "req-" + UUID.randomUUID());
        req.setAdjustmentSign(1);

        Long eventId = stockEventService.recordEvent(req);
        assertThat(eventId).isNotNull();

        StockEventDO event = stockEventMapper.selectById(eventId);
        assertThat(event.getAdjustmentSign()).isEqualTo(1);
    }

    @Test
    void recordEvent_count_adjust_persists_negative_sign() {
        stockEventService.recordEvent(buildReq(StockEventTypeEnum.PURCHASE_IN, StockDirectionEnum.IN,
                new BigDecimal("100"), "req-pre-" + UUID.randomUUID()));

        StockEventReqDTO req = buildReq(StockEventTypeEnum.COUNT_ADJUST, StockDirectionEnum.INTERNAL,
                new BigDecimal("30"), "req-" + UUID.randomUUID());
        req.setAdjustmentSign(-1);

        Long eventId = stockEventService.recordEvent(req);
        assertThat(eventId).isNotNull();

        StockEventDO event = stockEventMapper.selectById(eventId);
        assertThat(event.getAdjustmentSign()).isEqualTo(-1);
    }

    @Test
    void recordEvent_count_adjust_default_sign_persisted() {
        stockEventService.recordEvent(buildReq(StockEventTypeEnum.PURCHASE_IN, StockDirectionEnum.IN,
                new BigDecimal("50"), "req-pre-" + UUID.randomUUID()));

        StockEventReqDTO req = buildReq(StockEventTypeEnum.COUNT_ADJUST, StockDirectionEnum.INTERNAL,
                new BigDecimal("10"), "req-" + UUID.randomUUID());
        // adjustmentSign intentionally left null, defaults to +1 in recordEvent

        Long eventId = stockEventService.recordEvent(req);
        assertThat(eventId).isNotNull();

        StockEventDO event = stockEventMapper.selectById(eventId);
        // After recordEvent defaults null → +1, the persisted value should be 1
        assertThat(event.getAdjustmentSign()).isEqualTo(1);
    }

    @Test
    void recordEvent_in_event_sign_is_null() {
        StockEventReqDTO req = buildReq(StockEventTypeEnum.PURCHASE_IN, StockDirectionEnum.IN,
                new BigDecimal("100"), "req-" + UUID.randomUUID());

        Long eventId = stockEventService.recordEvent(req);
        assertThat(eventId).isNotNull();

        StockEventDO event = stockEventMapper.selectById(eventId);
        assertThat(event.getAdjustmentSign()).isNull();
    }

    @Test
    void recordEvent_out_event_sign_is_null() {
        // First purchase in to have stock
        stockEventService.recordEvent(buildReq(StockEventTypeEnum.PURCHASE_IN, StockDirectionEnum.IN,
                new BigDecimal("100"), "req-pre-" + UUID.randomUUID()));

        StockEventReqDTO req = buildReq(StockEventTypeEnum.CONSUME_OUT, StockDirectionEnum.OUT,
                new BigDecimal("30"), "req-" + UUID.randomUUID());

        Long eventId = stockEventService.recordEvent(req);
        assertThat(eventId).isNotNull();

        StockEventDO event = stockEventMapper.selectById(eventId);
        assertThat(event.getAdjustmentSign()).isNull();
    }

    // ===== G2-02I-2: adjustment_reason validation tests =====

    @Test
    void recordEvent_countAdjustReasonMissing_throws() {
        // Baseline
        stockEventService.recordEvent(buildReq(StockEventTypeEnum.PURCHASE_IN, StockDirectionEnum.IN,
                new BigDecimal("100"), "req-pre-" + UUID.randomUUID()));

        StockEventReqDTO req = buildReq(StockEventTypeEnum.COUNT_ADJUST, StockDirectionEnum.INTERNAL,
                new BigDecimal("10"), "req-" + UUID.randomUUID());
        req.setAdjustmentSign(1);
        req.setAdjustmentReason(null); // missing

        assertThatThrownBy(() -> stockEventService.recordEvent(req))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("Adjustment reason");
    }

    @Test
    void recordEvent_countAdjustReasonBlank_throws() {
        stockEventService.recordEvent(buildReq(StockEventTypeEnum.PURCHASE_IN, StockDirectionEnum.IN,
                new BigDecimal("100"), "req-pre-" + UUID.randomUUID()));

        StockEventReqDTO req = buildReq(StockEventTypeEnum.COUNT_ADJUST, StockDirectionEnum.INTERNAL,
                new BigDecimal("10"), "req-" + UUID.randomUUID());
        req.setAdjustmentSign(1);
        req.setAdjustmentReason("   "); // blank

        assertThatThrownBy(() -> stockEventService.recordEvent(req))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("Adjustment reason");
    }

    @Test
    void recordEvent_countAdjustReasonTooShort_throws() {
        stockEventService.recordEvent(buildReq(StockEventTypeEnum.PURCHASE_IN, StockDirectionEnum.IN,
                new BigDecimal("100"), "req-pre-" + UUID.randomUUID()));

        StockEventReqDTO req = buildReq(StockEventTypeEnum.COUNT_ADJUST, StockDirectionEnum.INTERNAL,
                new BigDecimal("10"), "req-" + UUID.randomUUID());
        req.setAdjustmentSign(1);
        req.setAdjustmentReason("太短了"); // only 3 chars, < 30

        assertThatThrownBy(() -> stockEventService.recordEvent(req))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("Adjustment reason");
    }

    @Test
    void recordEvent_countAdjustReasonPersisted() {
        stockEventService.recordEvent(buildReq(StockEventTypeEnum.PURCHASE_IN, StockDirectionEnum.IN,
                new BigDecimal("100"), "req-pre-" + UUID.randomUUID()));

        String reason = "这是一条测试用的盘点调整原因说明，长度超过三十个字符以满足校验。";
        StockEventReqDTO req = buildReq(StockEventTypeEnum.COUNT_ADJUST, StockDirectionEnum.INTERNAL,
                new BigDecimal("10"), "req-" + UUID.randomUUID());
        req.setAdjustmentSign(1);
        req.setAdjustmentReason(reason);

        Long eventId = stockEventService.recordEvent(req);
        assertThat(eventId).isNotNull();

        StockEventDO event = stockEventMapper.selectById(eventId);
        assertThat(event.getAdjustmentReason()).isEqualTo(reason);
    }

    /**
     * G2-01A FIX: Two tenants using the SAME client_request_id should each
     * generate their own independent event — no cross-tenant idempotency
     * interference.
     */
    @Test
    void recordEvent_crossTenantSameClientRequestIdNoInterference() {
        String sharedClientRequestId = "shared-cross-tenant-" + UUID.randomUUID();

        // Tenant 1 purchases 100
        TenantContextHolder.setTenantId(1L);
        StockEventReqDTO req1 = buildReq(StockEventTypeEnum.PURCHASE_IN, StockDirectionEnum.IN,
                new BigDecimal("100"), sharedClientRequestId);
        Long eventId1 = stockEventService.recordEvent(req1);

        // Tenant 2 uses the SAME client_request_id but purchases 50
        TenantContextHolder.setTenantId(2L);
        StockEventReqDTO req2 = buildReq(StockEventTypeEnum.PURCHASE_IN, StockDirectionEnum.IN,
                new BigDecimal("50"), sharedClientRequestId);
        req2.setTenantId(2L);
        Long eventId2 = stockEventService.recordEvent(req2);

        // Two DISTINCT events should be created
        assertThat(eventId1).isNotEqualTo(eventId2);

        // Each tenant's event is retrievable independently
        TenantContextHolder.setTenantId(1L);
        StockEventDO event1 = stockEventMapper.selectByClientRequestId(1L, sharedClientRequestId);
        assertThat(event1).isNotNull();
        assertThat(event1.getTenantId()).isEqualTo(1L);
        assertThat(event1.getBalanceAfter()).isEqualByComparingTo(new BigDecimal("100"));

        TenantContextHolder.setTenantId(2L);
        StockEventDO event2 = stockEventMapper.selectByClientRequestId(2L, sharedClientRequestId);
        assertThat(event2).isNotNull();
        assertThat(event2.getTenantId()).isEqualTo(2L);
        assertThat(event2.getBalanceAfter()).isEqualByComparingTo(new BigDecimal("50"));

        // Idempotency still works per-tenant: re-sending for tenant 1 returns the same event
        TenantContextHolder.setTenantId(1L);
        Long eventId1Retry = stockEventService.recordEvent(req1);
        assertThat(eventId1Retry).isEqualTo(eventId1);

        // Balances are independent
        assertThat(stockBalanceService.getAvailableQty(1L, 1001L, 10L))
                .isEqualByComparingTo(new BigDecimal("100"));
        TenantContextHolder.setTenantId(2L);
        assertThat(stockBalanceService.getAvailableQty(2L, 1001L, 10L))
                .isEqualByComparingTo(new BigDecimal("50"));
    }

    /**
     * G2-01A FIX: Sequential events' balance_after must be consistent with
     * the running balance, proving that no stale snapshot is used.
     */
    @Test
    void recordEvent_balanceAfterReflectsCorrectRunningBalance() {
        // Event 1: purchase in 100 → balance_after should be 100
        Long id1 = stockEventService.recordEvent(buildReq(StockEventTypeEnum.PURCHASE_IN,
                StockDirectionEnum.IN, new BigDecimal("100"), "seq-1-" + UUID.randomUUID()));
        StockEventDO e1 = stockEventMapper.selectById(id1);
        assertThat(e1.getBalanceAfter()).isEqualByComparingTo(new BigDecimal("100"));

        // Event 2: purchase in 30 → balance_after should be 130
        Long id2 = stockEventService.recordEvent(buildReq(StockEventTypeEnum.PRODUCTION_IN,
                StockDirectionEnum.IN, new BigDecimal("30"), "seq-2-" + UUID.randomUUID()));
        StockEventDO e2 = stockEventMapper.selectById(id2);
        assertThat(e2.getBalanceAfter()).isEqualByComparingTo(new BigDecimal("130"));

        // Event 3: consume out 50 → balance_after should be 80
        Long id3 = stockEventService.recordEvent(buildReq(StockEventTypeEnum.CONSUME_OUT,
                StockDirectionEnum.OUT, new BigDecimal("50"), "seq-3-" + UUID.randomUUID()));
        StockEventDO e3 = stockEventMapper.selectById(id3);
        assertThat(e3.getBalanceAfter()).isEqualByComparingTo(new BigDecimal("80"));

        // Event 4: consume out 20 → balance_after should be 60
        Long id4 = stockEventService.recordEvent(buildReq(StockEventTypeEnum.RETURN_OUT,
                StockDirectionEnum.OUT, new BigDecimal("20"), "seq-4-" + UUID.randomUUID()));
        StockEventDO e4 = stockEventMapper.selectById(id4);
        assertThat(e4.getBalanceAfter()).isEqualByComparingTo(new BigDecimal("60"));

        // Final balance matches last event's balance_after
        assertThat(stockBalanceService.getAvailableQty(1L, 1001L, 10L))
                .isEqualByComparingTo(new BigDecimal("60"));
    }

    private StockEventReqDTO buildReq(StockEventTypeEnum eventType, StockDirectionEnum direction,
                                       BigDecimal quantity, String clientRequestId) {
        StockEventReqDTO req = new StockEventReqDTO();
        req.setTenantId(1L);
        req.setEventTime(LocalDateTime.now());
        req.setBusinessDate(LocalDate.now());
        req.setEventType(eventType.getCode());
        req.setDirection(direction.getCode());
        req.setStockItemId(1001L);
        req.setSkuCode("SKU_TEST");
        req.setLocationId(10L);
        req.setQuantity(quantity);
        req.setUnit("个");
        req.setOperatorUserId(999L);
        req.setClientRequestId(clientRequestId);
        // G2-02I-2: COUNT_ADJUST events require adjustmentReason >=30 chars
        if (eventType == StockEventTypeEnum.COUNT_ADJUST) {
            req.setAdjustmentReason("这是一条测试用的盘点调整原因说明，长度超过三十个字符以满足校验要求。");
        }
        return req;
    }

    private void updateMinThreshold(Long tenantId, Long stockItemId, Long locationId, BigDecimal minThreshold) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE stock_balance SET min_threshold = ? " +
                             "WHERE tenant_id = ? AND stock_item_id = ? AND location_id = ? AND deleted = false")) {
            ps.setBigDecimal(1, minThreshold);
            ps.setLong(2, tenantId);
            ps.setLong(3, stockItemId);
            ps.setLong(4, locationId);
            assertThat(ps.executeUpdate()).isEqualTo(1);
        } catch (Exception e) {
            throw new IllegalStateException("failed to update min_threshold for test", e);
        }
    }
}
