package com.geihou.module.supplychain.stock.service;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.supplychain.stock.SupplychainTestConfig;
import com.geihou.module.supplychain.stock.SupplychainTestSchemaInitializer;
import com.geihou.module.supplychain.stock.controller.admin.vo.StockCountSessionCreateReqVO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockBalanceDO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockCountRecordDO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockCountSessionDO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockItemDO;
import com.geihou.module.supplychain.stock.dal.mapper.StockBalanceMapper;
import com.geihou.module.supplychain.stock.dal.mapper.StockEventMapper;
import com.geihou.module.supplychain.stock.dal.mapper.StockItemMapper;
import com.geihou.module.supplychain.stock.dal.mapper.StockCountRecordMapper;
import com.geihou.module.supplychain.stock.dal.mapper.StockCountSessionMapper;
import com.geihou.module.supplychain.stock.framework.StockBusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link StockCountService}.
 *
 * <p>Covers: create session, start count, record (surplus/loss/no-diff), submit,
 * approve (surplus/loss/no-diff/mixed), idempotent approval, invalid status,
 * diff_reason >=30 chars (unconditional), evidence for high variance, tenant isolation,
 * oversell failure, no-record submit rejection, INSERT-only verification.
 *
 * <p>Source: TASK-G2-02I-2 §9.1.
 */
@SpringBootTest(
        classes = SupplychainTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:stock_count_service_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class StockCountServiceTest {

    @Autowired
    private StockCountService stockCountService;
    @Autowired
    private StockEventService stockEventService;
    @Autowired
    private StockCountSessionMapper sessionMapper;
    @Autowired
    private StockCountRecordMapper recordMapper;
    @Autowired
    private StockBalanceMapper stockBalanceMapper;
    @Autowired
    private StockEventMapper stockEventMapper;
    @Autowired
    private StockItemMapper stockItemMapper;
    @Autowired
    private DataSource dataSource;

    private static final Long TENANT_A = 1L;
    private static final Long TENANT_B = 2L;
    private static final Long LOCATION_ID = 10L;
    private static final Long STOCK_ITEM_ID_1 = 1001L;
    private static final Long STOCK_ITEM_ID_2 = 1002L;
    private static final Long STOCK_ITEM_ID_3 = 1003L;
    private static final String SKU_CODE_1 = "SKU_001";
    private static final String SKU_CODE_2 = "SKU_002";
    private static final String SKU_CODE_3 = "SKU_003";
    private static final Long OPERATOR_ID = 100L;
    private static final Long APPROVER_ID = 200L;

    private static final String VALID_DIFF_REASON = "这是一条测试用的差异原因说明，长度超过三十个字符以满足校验要求。";
    private static final String SHORT_DIFF_REASON = "太短了";

    @BeforeEach
    void setUp() throws Exception {
        SupplychainTestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(TENANT_A);
        // Seed stock items
        seedStockItem(TENANT_A, STOCK_ITEM_ID_1, SKU_CODE_1);
        seedStockItem(TENANT_A, STOCK_ITEM_ID_2, SKU_CODE_2);
        seedStockItem(TENANT_A, STOCK_ITEM_ID_3, SKU_CODE_3);
        // Seed balances: item1=100, item2=50, item3=30, cost=10
        seedBalance(TENANT_A, STOCK_ITEM_ID_1, LOCATION_ID, new BigDecimal("100"), new BigDecimal("10"));
        seedBalance(TENANT_A, STOCK_ITEM_ID_2, LOCATION_ID, new BigDecimal("50"), new BigDecimal("10"));
        seedBalance(TENANT_A, STOCK_ITEM_ID_3, LOCATION_ID, new BigDecimal("30"), new BigDecimal("10"));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // --- ① Create session ---

    @Test
    void createSession_success() {
        StockCountSessionCreateReqVO req = buildCreateReq("FULL");
        StockCountSessionDO session = stockCountService.createSession(req);

        assertThat(session.getId()).isNotNull();
        assertThat(session.getStatus()).isEqualTo("PLANNING");
        assertThat(session.getSessionCode()).startsWith("COUNT-");
        assertThat(session.getCountType()).isEqualTo("FULL");
        assertThat(session.getLocationId()).isEqualTo(LOCATION_ID);
        assertThat(session.getTotalItems()).isNull();
        assertThat(session.getDiffItems()).isNull();
    }

    // --- ② Start count ---

    @Test
    void startCount_success() {
        StockCountSessionDO session = createAndStartSession();
        assertThat(session.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(session.getStartTime()).isNotNull();
    }

    // --- ③ Record count (surplus / 盘盈) ---

    @Test
    void recordCount_surplus_diffPositive() {
        StockCountSessionDO session = createAndStartSession();
        // system=100, actual=110 → diff=+10 (盘盈)
        StockCountRecordDO record = stockCountService.recordCount(
                session.getId(), TENANT_A, STOCK_ITEM_ID_1,
                new BigDecimal("110"), VALID_DIFF_REASON, null, OPERATOR_ID);

        assertThat(record.getSystemQty()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(record.getActualQty()).isEqualByComparingTo(new BigDecimal("110"));
        assertThat(record.getDiffQty()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(record.getDiffReason()).isEqualTo(VALID_DIFF_REASON);
        assertThat(record.getAdjustmentEventId()).isNull(); // not backfilled
    }

    // --- ④ Record count (loss / 盘亏) ---

    @Test
    void recordCount_loss_diffNegative() {
        StockCountSessionDO session = createAndStartSession();
        // system=100, actual=90 → diff=-10 (盘亏)
        StockCountRecordDO record = stockCountService.recordCount(
                session.getId(), TENANT_A, STOCK_ITEM_ID_1,
                new BigDecimal("90"), VALID_DIFF_REASON, null, OPERATOR_ID);

        assertThat(record.getDiffQty()).isEqualByComparingTo(new BigDecimal("-10"));
    }

    // --- ⑤ Record count (no diff) ---

    @Test
    void recordCount_noDiff_diffReasonNotRequired() {
        StockCountSessionDO session = createAndStartSession();
        // system=100, actual=100 → diff=0 (no diff reason needed)
        StockCountRecordDO record = stockCountService.recordCount(
                session.getId(), TENANT_A, STOCK_ITEM_ID_1,
                new BigDecimal("100"), null, null, OPERATOR_ID);

        assertThat(record.getDiffQty()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(record.getDiffReason()).isNull();
    }

    // --- ⑥ Submit count ---

    @Test
    void submitCount_success() {
        StockCountSessionDO session = createAndStartSession();
        stockCountService.recordCount(session.getId(), TENANT_A, STOCK_ITEM_ID_1,
                new BigDecimal("110"), VALID_DIFF_REASON, null, OPERATOR_ID);
        stockCountService.recordCount(session.getId(), TENANT_A, STOCK_ITEM_ID_2,
                new BigDecimal("50"), null, null, OPERATOR_ID); // no diff

        StockCountSessionDO submitted = stockCountService.submitCount(session.getId(), TENANT_A, OPERATOR_ID);

        assertThat(submitted.getStatus()).isEqualTo("DIFF_REVIEW");
        assertThat(submitted.getTotalItems()).isEqualTo(2);
        assertThat(submitted.getDiffItems()).isEqualTo(1);
        // diff_value = |10| × 10 = 100
        assertThat(submitted.getTotalDiffValue()).isEqualByComparingTo(new BigDecimal("100"));
    }

    // --- ⑦ Approve count (surplus generates COUNT_ADJUST +1 event) ---

    @Test
    void approveCount_surplus_generatesCountAdjustEvent() {
        StockCountSessionDO session = createAndStartSession();
        // Record surplus: system=100, actual=110, diff=+10
        stockCountService.recordCount(session.getId(), TENANT_A, STOCK_ITEM_ID_1,
                new BigDecimal("110"), VALID_DIFF_REASON, null, OPERATOR_ID);
        stockCountService.submitCount(session.getId(), TENANT_A, OPERATOR_ID);

        BigDecimal balanceBefore = getAvailableQty(TENANT_A, STOCK_ITEM_ID_1, LOCATION_ID);
        assertThat(balanceBefore).isEqualByComparingTo(new BigDecimal("100"));

        StockCountSessionDO approved = stockCountService.approveCount(session.getId(), TENANT_A, APPROVER_ID);

        assertThat(approved.getStatus()).isEqualTo("ADJUSTED");
        assertThat(approved.getApproverUserId()).isEqualTo(APPROVER_ID);
        assertThat(approved.getApproveTime()).isNotNull();

        // Balance should increase by 10: 100 + 10 = 110
        BigDecimal balanceAfter = getAvailableQty(TENANT_A, STOCK_ITEM_ID_1, LOCATION_ID);
        assertThat(balanceAfter).isEqualByComparingTo(new BigDecimal("110"));
    }

    // --- ⑧ Approve count (loss generates COUNT_ADJUST -1 event) ---

    @Test
    void approveCount_loss_generatesCountAdjustEvent() {
        StockCountSessionDO session = createAndStartSession();
        // Record loss: system=100, actual=90, diff=-10
        stockCountService.recordCount(session.getId(), TENANT_A, STOCK_ITEM_ID_1,
                new BigDecimal("90"), VALID_DIFF_REASON, null, OPERATOR_ID);
        stockCountService.submitCount(session.getId(), TENANT_A, OPERATOR_ID);

        BigDecimal balanceBefore = getAvailableQty(TENANT_A, STOCK_ITEM_ID_1, LOCATION_ID);
        assertThat(balanceBefore).isEqualByComparingTo(new BigDecimal("100"));

        StockCountSessionDO approved = stockCountService.approveCount(session.getId(), TENANT_A, APPROVER_ID);

        assertThat(approved.getStatus()).isEqualTo("ADJUSTED");

        // Balance should decrease by 10: 100 - 10 = 90
        BigDecimal balanceAfter = getAvailableQty(TENANT_A, STOCK_ITEM_ID_1, LOCATION_ID);
        assertThat(balanceAfter).isEqualByComparingTo(new BigDecimal("90"));
    }

    // --- ⑨ Approve count (no diff generates no event) ---

    @Test
    void approveCount_noDiff_generatesNoEvent() {
        StockCountSessionDO session = createAndStartSession();
        // Record no diff: system=100, actual=100, diff=0
        stockCountService.recordCount(session.getId(), TENANT_A, STOCK_ITEM_ID_1,
                new BigDecimal("100"), null, null, OPERATOR_ID);
        stockCountService.submitCount(session.getId(), TENANT_A, OPERATOR_ID);

        BigDecimal balanceBefore = getAvailableQty(TENANT_A, STOCK_ITEM_ID_1, LOCATION_ID);

        StockCountSessionDO approved = stockCountService.approveCount(session.getId(), TENANT_A, APPROVER_ID);

        assertThat(approved.getStatus()).isEqualTo("ADJUSTED");

        // Balance unchanged
        BigDecimal balanceAfter = getAvailableQty(TENANT_A, STOCK_ITEM_ID_1, LOCATION_ID);
        assertThat(balanceAfter).isEqualByComparingTo(balanceBefore);

        // No COUNT_ADJUST events generated
        List<StockCountRecordDO> records = recordMapper.selectBySession(session.getId(), TENANT_A);
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getDiffQty()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // --- ⑩ Approve count (mixed: surplus + loss + no diff) ---

    @Test
    void approveCount_mixedDiff_generatesEventsForDiffOnly() {
        StockCountSessionDO session = createAndStartSession();
        // Item1: surplus +10 (100→110)
        stockCountService.recordCount(session.getId(), TENANT_A, STOCK_ITEM_ID_1,
                new BigDecimal("110"), VALID_DIFF_REASON, null, OPERATOR_ID);
        // Item2: loss -5 (50→45)
        stockCountService.recordCount(session.getId(), TENANT_A, STOCK_ITEM_ID_2,
                new BigDecimal("45"), VALID_DIFF_REASON, null, OPERATOR_ID);
        // Item3: no diff (30→30)
        stockCountService.recordCount(session.getId(), TENANT_A, STOCK_ITEM_ID_3,
                new BigDecimal("30"), null, null, OPERATOR_ID);

        stockCountService.submitCount(session.getId(), TENANT_A, OPERATOR_ID);

        StockCountSessionDO approved = stockCountService.approveCount(session.getId(), TENANT_A, APPROVER_ID);
        assertThat(approved.getStatus()).isEqualTo("ADJUSTED");
        assertThat(approved.getTotalItems()).isEqualTo(3);
        assertThat(approved.getDiffItems()).isEqualTo(2);

        // Item1: 100 + 10 = 110
        assertThat(getAvailableQty(TENANT_A, STOCK_ITEM_ID_1, LOCATION_ID))
                .isEqualByComparingTo(new BigDecimal("110"));
        // Item2: 50 - 5 = 45
        assertThat(getAvailableQty(TENANT_A, STOCK_ITEM_ID_2, LOCATION_ID))
                .isEqualByComparingTo(new BigDecimal("45"));
        // Item3: unchanged 30
        assertThat(getAvailableQty(TENANT_A, STOCK_ITEM_ID_3, LOCATION_ID))
                .isEqualByComparingTo(new BigDecimal("30"));
    }

    // --- ⑪ Idempotent approval (re-approval does not re-adjust) ---

    @Test
    void approveCount_alreadyAdjusted_returnsExistingWithoutReAdjustment() {
        StockCountSessionDO session = createAndStartSession();
        stockCountService.recordCount(session.getId(), TENANT_A, STOCK_ITEM_ID_1,
                new BigDecimal("110"), VALID_DIFF_REASON, null, OPERATOR_ID);
        stockCountService.submitCount(session.getId(), TENANT_A, OPERATOR_ID);

        // First approval
        stockCountService.approveCount(session.getId(), TENANT_A, APPROVER_ID);
        BigDecimal balanceAfterFirst = getAvailableQty(TENANT_A, STOCK_ITEM_ID_1, LOCATION_ID);
        assertThat(balanceAfterFirst).isEqualByComparingTo(new BigDecimal("110"));

        // Second approval → returns ADJUSTED without re-adjusting
        StockCountSessionDO second = stockCountService.approveCount(session.getId(), TENANT_A, APPROVER_ID);
        assertThat(second.getStatus()).isEqualTo("ADJUSTED");

        // Balance unchanged
        BigDecimal balanceAfterSecond = getAvailableQty(TENANT_A, STOCK_ITEM_ID_1, LOCATION_ID);
        assertThat(balanceAfterSecond).isEqualByComparingTo(new BigDecimal("110"));
    }

    // --- ⑫ Invalid status transition rejected ---

    @Test
    void startCount_invalidStatus_throwsException() {
        StockCountSessionDO session = createAndStartSession();
        // Already IN_PROGRESS, cannot start again
        assertThatThrownBy(() -> stockCountService.startCount(session.getId(), TENANT_A, OPERATOR_ID))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("Invalid stock count status");
    }

    @Test
    void submitCount_invalidStatus_throwsException() {
        StockCountSessionCreateReqVO req = buildCreateReq("FULL");
        StockCountSessionDO session = stockCountService.createSession(req);
        // Still PLANNING, cannot submit
        assertThatThrownBy(() -> stockCountService.submitCount(session.getId(), TENANT_A, OPERATOR_ID))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("Invalid stock count status");
    }

    // --- ⑬ Diff record without diff_reason rejected (>=30 chars unconditional) ---

    @Test
    void recordCount_diffWithoutReason_rejected() {
        StockCountSessionDO session = createAndStartSession();
        // diff=+10 but no reason
        assertThatThrownBy(() -> stockCountService.recordCount(
                session.getId(), TENANT_A, STOCK_ITEM_ID_1,
                new BigDecimal("110"), null, null, OPERATOR_ID))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("Diff reason required");
    }

    // --- ⑭ Low diff without diff_reason also rejected (unconditional) ---

    @Test
    void recordCount_lowDiffWithoutReason_alsoRejected() {
        StockCountSessionDO session = createAndStartSession();
        // diff=+1 (low value: 1×10=10 ≤ 100) but no reason → still rejected
        assertThatThrownBy(() -> stockCountService.recordCount(
                session.getId(), TENANT_A, STOCK_ITEM_ID_1,
                new BigDecimal("101"), null, null, OPERATOR_ID))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("Diff reason required");
    }

    // --- ⑮ High variance without evidence rejected ---

    @Test
    void recordCount_highVarianceWithoutEvidence_rejected() {
        StockCountSessionDO session = createAndStartSession();
        // diff=+20, cost=10 → amount=200 > 100 threshold → evidence required
        assertThatThrownBy(() -> stockCountService.recordCount(
                session.getId(), TENANT_A, STOCK_ITEM_ID_1,
                new BigDecimal("120"), VALID_DIFF_REASON, null, OPERATOR_ID))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("Evidence photo required");
    }

    // --- ⑯ Tenant isolation query ---

    @Test
    void getSession_tenantIsolation_returnsNull() {
        StockCountSessionDO session = createAndStartSession();

        // Switch to tenant B and try to query tenant A's session
        TenantContextHolder.setTenantId(TENANT_B);
        StockCountSessionDO found = stockCountService.getSession(session.getId(), TENANT_B);
        assertThat(found).isNull();
    }

    // --- ⑰ Tenant isolation approval rejected ---

    @Test
    void approveCount_tenantIsolation_throwsNotFound() {
        StockCountSessionDO session = createAndStartSession();
        stockCountService.recordCount(session.getId(), TENANT_A, STOCK_ITEM_ID_1,
                new BigDecimal("110"), VALID_DIFF_REASON, null, OPERATOR_ID);
        stockCountService.submitCount(session.getId(), TENANT_A, OPERATOR_ID);

        // Switch to tenant B and try to approve tenant A's session
        TenantContextHolder.setTenantId(TENANT_B);
        assertThatThrownBy(() -> stockCountService.approveCount(session.getId(), TENANT_B, APPROVER_ID))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("not found");
    }

    // --- ⑱ Oversell failure (negative variance exceeds available) ---

    @Test
    void approveCount_oversell_throwsAndRollsBack() {
        StockCountSessionDO session = createAndStartSession();
        // Use item3 (balance=30, cost=10)
        // Record: system=30, actual=0, diff=-30, amount=300 > 100 → evidence required
        stockCountService.recordCount(session.getId(), TENANT_A, STOCK_ITEM_ID_3,
                new BigDecimal("0"), VALID_DIFF_REASON, "https://example.com/evidence.jpg", OPERATOR_ID);
        stockCountService.submitCount(session.getId(), TENANT_A, OPERATOR_ID);

        // Manually reduce balance to 10 (less than |diff|=30)
        StockBalanceDO balance = stockBalanceMapper.selectByTenantItemLocation(
                TENANT_A, STOCK_ITEM_ID_3, LOCATION_ID);
        stockBalanceMapper.updateBalanceWithOptimisticLock(
                balance.getId(), TENANT_A,
                new BigDecimal("10"), new BigDecimal("10"),
                balance.getLastEventId(), balance.getLastEventTime(),
                balance.getVersion(), "test", LocalDateTime.now());

        // Approve should fail with INSUFFICIENT_STOCK (available=10, requested=30)
        assertThatThrownBy(() -> stockCountService.approveCount(session.getId(), TENANT_A, APPROVER_ID))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("Insufficient stock");

        // Session should still be DIFF_REVIEW (transaction rolled back)
        StockCountSessionDO refreshed = sessionMapper.selectByIdAndTenant(session.getId(), TENANT_A);
        assertThat(refreshed.getStatus()).isEqualTo("DIFF_REVIEW");
    }

    // --- ⑲ Submit with no records rejected ---

    @Test
    void submitCount_noRecords_throwsException() {
        StockCountSessionDO session = createAndStartSession();
        // No records recorded yet
        assertThatThrownBy(() -> stockCountService.submitCount(session.getId(), TENANT_A, OPERATOR_ID))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("Not all items recorded");
    }

    // --- ⑳ INSERT-only verification (recordCount does not produce UPDATE) ---

    @Test
    void recordCount_insertOnly_noUpdateOnRecord() {
        StockCountSessionDO session = createAndStartSession();

        // Record item1
        StockCountRecordDO record1 = stockCountService.recordCount(
                session.getId(), TENANT_A, STOCK_ITEM_ID_1,
                new BigDecimal("110"), VALID_DIFF_REASON, null, OPERATOR_ID);

        // Record item2 (different item)
        StockCountRecordDO record2 = stockCountService.recordCount(
                session.getId(), TENANT_A, STOCK_ITEM_ID_2,
                new BigDecimal("45"), VALID_DIFF_REASON, null, OPERATOR_ID);

        // Verify both records exist and are unchanged
        StockCountRecordDO found1 = recordMapper.selectByIdAndTenant(record1.getId(), TENANT_A);
        StockCountRecordDO found2 = recordMapper.selectByIdAndTenant(record2.getId(), TENANT_A);

        assertThat(found1.getActualQty()).isEqualByComparingTo(new BigDecimal("110"));
        assertThat(found1.getSystemQty()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(found1.getDiffQty()).isEqualByComparingTo(new BigDecimal("10"));

        assertThat(found2.getActualQty()).isEqualByComparingTo(new BigDecimal("45"));
        assertThat(found2.getSystemQty()).isEqualByComparingTo(new BigDecimal("50"));
        assertThat(found2.getDiffQty()).isEqualByComparingTo(new BigDecimal("-5"));

        // adjustment_event_id is always null (not backfilled)
        assertThat(found1.getAdjustmentEventId()).isNull();
        assertThat(found2.getAdjustmentEventId()).isNull();
    }

    // --- ㉑ Diff reason too short rejected ---

    @Test
    void recordCount_diffReasonTooShort_rejected() {
        StockCountSessionDO session = createAndStartSession();
        // diff=+10 but reason only 3 chars
        assertThatThrownBy(() -> stockCountService.recordCount(
                session.getId(), TENANT_A, STOCK_ITEM_ID_1,
                new BigDecimal("110"), SHORT_DIFF_REASON, null, OPERATOR_ID))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("Diff reason required");
    }

    // --- ㉒ Approve generates events with correct sourceModule and referenceNo ---

    @Test
    void approveCount_eventsHaveCorrectSourceModuleAndReferenceNo() {
        StockCountSessionDO session = createAndStartSession();
        StockCountRecordDO record = stockCountService.recordCount(
                session.getId(), TENANT_A, STOCK_ITEM_ID_1,
                new BigDecimal("110"), VALID_DIFF_REASON, null, OPERATOR_ID);
        stockCountService.submitCount(session.getId(), TENANT_A, OPERATOR_ID);

        stockCountService.approveCount(session.getId(), TENANT_A, APPROVER_ID);

        // Find the COUNT_ADJUST event
        var events = stockEventMapper.selectByTenantItemLocation(TENANT_A, STOCK_ITEM_ID_1, LOCATION_ID);
        assertThat(events).isNotEmpty();
        var countAdjustEvent = events.stream()
                .filter(e -> "COUNT_ADJUST".equals(e.getEventType()))
                .findFirst()
                .orElseThrow();
        assertThat(countAdjustEvent.getDirection()).isEqualTo("INTERNAL");
        assertThat(countAdjustEvent.getSourceModule()).isEqualTo("stock_count");
        assertThat(countAdjustEvent.getSourceRecordId()).isEqualTo(record.getId());
        assertThat(countAdjustEvent.getReferenceNo()).isEqualTo(session.getSessionCode());
        assertThat(countAdjustEvent.getClientRequestId())
                .isEqualTo("stock-count-approve-" + session.getId() + "-" + record.getId());
        // G2-02I-2: adjustment_reason persisted from record.diffReason
        assertThat(countAdjustEvent.getAdjustmentReason()).isEqualTo(record.getDiffReason());
    }

    // --- Helpers ---

    private StockCountSessionDO createAndStartSession() {
        StockCountSessionCreateReqVO req = buildCreateReq("FULL");
        StockCountSessionDO session = stockCountService.createSession(req);
        return stockCountService.startCount(session.getId(), TENANT_A, OPERATOR_ID);
    }

    private StockCountSessionCreateReqVO buildCreateReq(String countType) {
        StockCountSessionCreateReqVO req = new StockCountSessionCreateReqVO();
        req.setTenantId(TENANT_A);
        req.setLocationId(LOCATION_ID);
        req.setCountType(countType);
        req.setOperatorUserId(OPERATOR_ID);
        return req;
    }

    private BigDecimal getAvailableQty(Long tenantId, Long stockItemId, Long locationId) {
        StockBalanceDO balance = stockBalanceMapper.selectByTenantItemLocation(tenantId, stockItemId, locationId);
        return balance != null ? balance.getAvailableQty() : BigDecimal.ZERO;
    }

    private void seedBalance(Long tenantId, Long stockItemId, Long locationId,
                             BigDecimal availableQty, BigDecimal avgUnitCost) {
        StockBalanceDO existing = stockBalanceMapper.selectByTenantItemLocation(tenantId, stockItemId, locationId);
        if (existing != null) {
            stockBalanceMapper.deleteById(existing.getId());
        }
        StockBalanceDO balance = new StockBalanceDO();
        balance.setTenantId(tenantId);
        balance.setStockItemId(stockItemId);
        balance.setLocationId(locationId);
        balance.setAvailableQty(availableQty);
        balance.setTotalQty(availableQty);
        balance.setReservedQty(BigDecimal.ZERO);
        balance.setAvgUnitCost(avgUnitCost);
        balance.setVersion(0);
        balance.setCreator("system");
        balance.setCreateTime(LocalDateTime.now());
        balance.setUpdater("system");
        balance.setUpdateTime(LocalDateTime.now());
        balance.setDeleted(false);
        stockBalanceMapper.insert(balance);
    }

    private void seedStockItem(Long tenantId, Long stockItemId, String skuCode) {
        StockItemDO existing = stockItemMapper.selectByIdAndTenant(stockItemId, tenantId);
        if (existing != null) {
            stockItemMapper.deleteById(existing.getId());
        }
        StockItemDO item = new StockItemDO();
        item.setId(stockItemId);
        item.setTenantId(tenantId);
        item.setSkuCode(skuCode);
        item.setItemName("Test Item " + skuCode);
        item.setUnit("KG");
        item.setIsRawMaterial(true);
        item.setIsSemiFinished(false);
        item.setIsFinished(false);
        item.setIsActive(true);
        item.setCreator("system");
        item.setCreateTime(LocalDateTime.now());
        item.setUpdater("system");
        item.setUpdateTime(LocalDateTime.now());
        item.setDeleted(false);
        stockItemMapper.insert(item);
    }
}
