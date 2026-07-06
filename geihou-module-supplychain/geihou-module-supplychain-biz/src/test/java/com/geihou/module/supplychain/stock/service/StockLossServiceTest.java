package com.geihou.module.supplychain.stock.service;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.supplychain.api.stock.dto.StockEventReqDTO;
import com.geihou.module.supplychain.api.stock.enums.StockDirectionEnum;
import com.geihou.module.supplychain.api.stock.enums.StockEventTypeEnum;
import com.geihou.module.supplychain.stock.SupplychainTestConfig;
import com.geihou.module.supplychain.stock.SupplychainTestSchemaInitializer;
import com.geihou.module.supplychain.stock.controller.admin.vo.StockLossCreateReqVO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockBalanceDO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockLossDO;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link StockLossService}.
 *
 * <p>Covers: low amount auto-approve + stock deduction, high amount pending approve,
 * approve with stock deduction, reject without deduction, idempotent re-approve,
 * invalid status transitions, cancel, tenant isolation, OTHER reason validation,
 * invalid loss reason.
 *
 * <p>Source: TASK-G2-02I-3 Section 7.1.
 */
@SpringBootTest(
        classes = SupplychainTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:stock_loss_service_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class StockLossServiceTest {

    @Autowired
    private StockLossService stockLossService;
    @Autowired
    private StockEventService stockEventService;
    @Autowired
    private StockBalanceMapper stockBalanceMapper;
    @Autowired
    private StockEventMapper stockEventMapper;
    @Autowired
    private DataSource dataSource;

    private static final Long TENANT_A = 1L;
    private static final Long TENANT_B = 2L;
    private static final Long STOCK_ITEM_ID = 1001L;
    private static final Long LOCATION_ID = 10L;
    private static final String SKU_CODE = "SKU_TEST";
    private static final Long OPERATOR_ID = 100L;
    private static final Long APPROVER_ID = 200L;

    @BeforeEach
    void setUp() throws Exception {
        SupplychainTestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(TENANT_A);
        // Seed stock balance with cost for amount calculation
        seedBalance(TENANT_A, STOCK_ITEM_ID, LOCATION_ID, new BigDecimal("100"), new BigDecimal("10"));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createLoss_lowAmount_autoApprovedAndDeductsStock() {
        // quantity=5, unitCost=10 → totalAmount=50 ≤ 1000 → auto APPROVED
        StockLossCreateReqVO req = buildCreateReq("LOSS", new BigDecimal("5"), "EXPIRY");
        StockLossDO loss = stockLossService.createLoss(req);

        assertThat(loss.getStatus()).isEqualTo("APPROVED");
        assertThat(loss.getApproverUserId()).isEqualTo(OPERATOR_ID);
        assertThat(loss.getApproveTime()).isNotNull();
        assertThat(loss.getStockEventId()).isNotNull();
        assertThat(loss.getStockEventId()).isGreaterThan(0L);

        // Verify stock was deducted: 100 - 5 = 95
        StockBalanceDO balance = stockBalanceMapper.selectByTenantItemLocation(TENANT_A, STOCK_ITEM_ID, LOCATION_ID);
        assertThat(balance.getAvailableQty()).isEqualByComparingTo(new BigDecimal("95"));
    }

    @Test
    void createLoss_highAmount_entersPendingApprove() {
        // quantity=200, unitCost=10 → totalAmount=2000 > 1000 → PENDING_APPROVE
        StockLossCreateReqVO req = buildCreateReq("LOSS", new BigDecimal("200"), "SPOILAGE");
        StockLossDO loss = stockLossService.createLoss(req);

        assertThat(loss.getStatus()).isEqualTo("PENDING_APPROVE");
        assertThat(loss.getStockEventId()).isNull();
        assertThat(loss.getApproverUserId()).isNull();

        // Verify stock was NOT deducted: still 100
        StockBalanceDO balance = stockBalanceMapper.selectByTenantItemLocation(TENANT_A, STOCK_ITEM_ID, LOCATION_ID);
        assertThat(balance.getAvailableQty()).isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    void approveLoss_pendingApprove_deductsStock() {
        // Seed enough stock: 500 available, cost=10
        // quantity=200, unitCost=10 → totalAmount=2000 > 1000 → PENDING_APPROVE
        seedBalance(TENANT_A, STOCK_ITEM_ID, LOCATION_ID, new BigDecimal("500"), new BigDecimal("10"));

        StockLossCreateReqVO req = buildCreateReq("SCRAP", new BigDecimal("200"), "EQUIPMENT_FAILURE");
        StockLossDO loss = stockLossService.createLoss(req);
        assertThat(loss.getStatus()).isEqualTo("PENDING_APPROVE");

        // Approve
        StockLossDO approved = stockLossService.approveLoss(loss.getId(), TENANT_A, APPROVER_ID);

        assertThat(approved.getStatus()).isEqualTo("APPROVED");
        assertThat(approved.getApproverUserId()).isEqualTo(APPROVER_ID);
        assertThat(approved.getApproveTime()).isNotNull();
        assertThat(approved.getStockEventId()).isNotNull();
        assertThat(approved.getStockEventId()).isGreaterThan(0L);

        // Verify stock was deducted: 500 - 200 = 300
        StockBalanceDO balance = stockBalanceMapper.selectByTenantItemLocation(TENANT_A, STOCK_ITEM_ID, LOCATION_ID);
        assertThat(balance.getAvailableQty()).isEqualByComparingTo(new BigDecimal("300"));
    }

    @Test
    void approveLoss_sufficientStock_deductsStock() {
        // Use quantity=50, unitCost=10 → totalAmount=500 ≤ 1000 → auto-approved
        // Let's use quantity=150, unitCost=10 → totalAmount=1500 > 1000 → PENDING_APPROVE
        // But available is only 100, so let's seed more
        seedBalance(TENANT_A, STOCK_ITEM_ID, LOCATION_ID, new BigDecimal("500"), new BigDecimal("10"));

        StockLossCreateReqVO req = buildCreateReq("LOSS", new BigDecimal("150"), "ACCIDENTAL_DAMAGE");
        StockLossDO loss = stockLossService.createLoss(req);
        assertThat(loss.getStatus()).isEqualTo("PENDING_APPROVE");

        StockLossDO approved = stockLossService.approveLoss(loss.getId(), TENANT_A, APPROVER_ID);
        assertThat(approved.getStatus()).isEqualTo("APPROVED");
        assertThat(approved.getStockEventId()).isNotNull();

        // Verify stock was deducted: 500 - 150 = 350
        StockBalanceDO balance = stockBalanceMapper.selectByTenantItemLocation(TENANT_A, STOCK_ITEM_ID, LOCATION_ID);
        assertThat(balance.getAvailableQty()).isEqualByComparingTo(new BigDecimal("350"));
    }

    @Test
    void rejectLoss_pendingApprove_noStockDeduction() {
        seedBalance(TENANT_A, STOCK_ITEM_ID, LOCATION_ID, new BigDecimal("500"), new BigDecimal("10"));

        StockLossCreateReqVO req = buildCreateReq("LOSS", new BigDecimal("150"), "THEFT");
        StockLossDO loss = stockLossService.createLoss(req);
        assertThat(loss.getStatus()).isEqualTo("PENDING_APPROVE");

        StockLossDO rejected = stockLossService.rejectLoss(loss.getId(), TENANT_A, APPROVER_ID, "Not enough evidence");

        assertThat(rejected.getStatus()).isEqualTo("REJECTED");
        assertThat(rejected.getApproverUserId()).isEqualTo(APPROVER_ID);
        assertThat(rejected.getRejectReason()).isEqualTo("Not enough evidence");
        assertThat(rejected.getStockEventId()).isNull();

        // Verify stock was NOT deducted: still 500
        StockBalanceDO balance = stockBalanceMapper.selectByTenantItemLocation(TENANT_A, STOCK_ITEM_ID, LOCATION_ID);
        assertThat(balance.getAvailableQty()).isEqualByComparingTo(new BigDecimal("500"));
    }

    @Test
    void approveLoss_alreadyApproved_returnsExistingResultWithoutReDeduction() {
        seedBalance(TENANT_A, STOCK_ITEM_ID, LOCATION_ID, new BigDecimal("500"), new BigDecimal("10"));

        StockLossCreateReqVO req = buildCreateReq("LOSS", new BigDecimal("150"), "PROCESS_LOSS");
        StockLossDO loss = stockLossService.createLoss(req);

        // First approval
        StockLossDO firstApprove = stockLossService.approveLoss(loss.getId(), TENANT_A, APPROVER_ID);
        assertThat(firstApprove.getStatus()).isEqualTo("APPROVED");
        Long firstEventId = firstApprove.getStockEventId();
        assertThat(firstEventId).isNotNull();

        // Balance after first approval: 500 - 150 = 350
        StockBalanceDO balanceAfterFirst = stockBalanceMapper.selectByTenantItemLocation(
                TENANT_A, STOCK_ITEM_ID, LOCATION_ID);
        assertThat(balanceAfterFirst.getAvailableQty()).isEqualByComparingTo(new BigDecimal("350"));

        // Second approval → returns APPROVED record (idempotent, no exception)
        StockLossDO secondApprove = stockLossService.approveLoss(loss.getId(), TENANT_A, APPROVER_ID);
        assertThat(secondApprove.getStatus()).isEqualTo("APPROVED");
        assertThat(secondApprove.getStockEventId()).isEqualTo(firstEventId);

        // Balance unchanged: still 350 (no re-deduction)
        StockBalanceDO balanceAfterSecond = stockBalanceMapper.selectByTenantItemLocation(
                TENANT_A, STOCK_ITEM_ID, LOCATION_ID);
        assertThat(balanceAfterSecond.getAvailableQty()).isEqualByComparingTo(new BigDecimal("350"));
    }

    @Test
    void approveLoss_cancelledStatus_throwsInvalidStatus() {
        // Create high amount loss → PENDING_APPROVE, then cancel it
        seedBalance(TENANT_A, STOCK_ITEM_ID, LOCATION_ID, new BigDecimal("500"), new BigDecimal("10"));

        StockLossCreateReqVO req = buildCreateReq("LOSS", new BigDecimal("150"), "EXPIRY");
        StockLossDO loss = stockLossService.createLoss(req);
        assertThat(loss.getStatus()).isEqualTo("PENDING_APPROVE");

        stockLossService.cancelLoss(loss.getId(), TENANT_A, OPERATOR_ID);

        // Try to approve a CANCELLED loss → should throw LOSS_INVALID_STATUS
        assertThatThrownBy(() -> stockLossService.approveLoss(loss.getId(), TENANT_A, APPROVER_ID))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("Invalid stock loss status");
    }

    @Test
    void rejectLoss_alreadyRejected_rejectsReject() {
        seedBalance(TENANT_A, STOCK_ITEM_ID, LOCATION_ID, new BigDecimal("500"), new BigDecimal("10"));

        StockLossCreateReqVO req = buildCreateReq("LOSS", new BigDecimal("150"), "EMPLOYEE_ERROR");
        StockLossDO loss = stockLossService.createLoss(req);
        stockLossService.rejectLoss(loss.getId(), TENANT_A, APPROVER_ID, "test");

        assertThatThrownBy(() -> stockLossService.rejectLoss(loss.getId(), TENANT_A, APPROVER_ID, "test2"))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("already rejected");
    }

    @Test
    void cancelLoss_pendingApprove_noStockDeduction() {
        seedBalance(TENANT_A, STOCK_ITEM_ID, LOCATION_ID, new BigDecimal("500"), new BigDecimal("10"));

        StockLossCreateReqVO req = buildCreateReq("LOSS", new BigDecimal("150"), "PROCESS_LOSS");
        StockLossDO loss = stockLossService.createLoss(req);
        assertThat(loss.getStatus()).isEqualTo("PENDING_APPROVE");

        StockLossDO cancelled = stockLossService.cancelLoss(loss.getId(), TENANT_A, OPERATOR_ID);
        assertThat(cancelled.getStatus()).isEqualTo("CANCELLED");

        // Verify stock was NOT deducted
        StockBalanceDO balance = stockBalanceMapper.selectByTenantItemLocation(TENANT_A, STOCK_ITEM_ID, LOCATION_ID);
        assertThat(balance.getAvailableQty()).isEqualByComparingTo(new BigDecimal("500"));
    }

    @Test
    void cancelLoss_alreadyApproved_rejectsCancel() {
        StockLossCreateReqVO req = buildCreateReq("LOSS", new BigDecimal("5"), "EXPIRY");
        StockLossDO loss = stockLossService.createLoss(req);
        assertThat(loss.getStatus()).isEqualTo("APPROVED");

        assertThatThrownBy(() -> stockLossService.cancelLoss(loss.getId(), TENANT_A, OPERATOR_ID))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("Invalid stock loss status");
    }

    @Test
    void queryLoss_tenantIsolation_returnsNull() {
        StockLossCreateReqVO req = buildCreateReq("LOSS", new BigDecimal("5"), "EXPIRY");
        StockLossDO loss = stockLossService.createLoss(req);

        // Switch to tenant B and try to query tenant A's loss
        TenantContextHolder.setTenantId(TENANT_B);
        StockLossDO found = stockLossService.getLoss(loss.getId(), TENANT_B);
        assertThat(found).isNull();
    }

    @Test
    void approveLoss_tenantIsolation_throwsNotFound() {
        StockLossCreateReqVO req = buildCreateReq("LOSS", new BigDecimal("5"), "EXPIRY");
        StockLossDO loss = stockLossService.createLoss(req);

        // Switch to tenant B and try to approve tenant A's loss
        TenantContextHolder.setTenantId(TENANT_B);
        assertThatThrownBy(() -> stockLossService.approveLoss(loss.getId(), TENANT_B, APPROVER_ID))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void createLoss_otherReasonWithoutRemark_throwsException() {
        StockLossCreateReqVO req = buildCreateReq("LOSS", new BigDecimal("5"), "OTHER");
        req.setRemark(null);

        assertThatThrownBy(() -> stockLossService.createLoss(req))
                .isInstanceOf(StockBusinessException.class)
                .hasMessageContaining("Remark required for OTHER");
    }

    @Test
    void createLoss_invalidLossReason_throwsException() {
        StockLossCreateReqVO req = buildCreateReq("LOSS", new BigDecimal("5"), "INVALID_REASON");

        assertThatThrownBy(() -> stockLossService.createLoss(req))
                .isInstanceOf(StockBusinessException.class);
    }

    @Test
    void createLoss_scrapType_usesScrapOutEvent() {
        // Low amount SCRAP → auto APPROVED, should use SCRAP_OUT event type
        StockLossCreateReqVO req = buildCreateReq("SCRAP", new BigDecimal("5"), "EXPIRY");
        StockLossDO loss = stockLossService.createLoss(req);

        assertThat(loss.getStatus()).isEqualTo("APPROVED");
        assertThat(loss.getStockEventId()).isNotNull();

        // Verify the event type is SCRAP_OUT
        var event = stockEventMapper.selectById(loss.getStockEventId());
        assertThat(event).isNotNull();
        assertThat(event.getEventType()).isEqualTo("SCRAP_OUT");
        assertThat(event.getDirection()).isEqualTo("OUT");
        assertThat(event.getSourceModule()).isEqualTo("stock_loss");
    }

    @Test
    void createLoss_lossType_usesLossOutEvent() {
        // Low amount LOSS → auto APPROVED, should use LOSS_OUT event type
        StockLossCreateReqVO req = buildCreateReq("LOSS", new BigDecimal("5"), "EXPIRY");
        StockLossDO loss = stockLossService.createLoss(req);

        assertThat(loss.getStatus()).isEqualTo("APPROVED");

        var event = stockEventMapper.selectById(loss.getStockEventId());
        assertThat(event).isNotNull();
        assertThat(event.getEventType()).isEqualTo("LOSS_OUT");
        assertThat(event.getDirection()).isEqualTo("OUT");
        assertThat(event.getSourceModule()).isEqualTo("stock_loss");
        assertThat(event.getSourceRecordId()).isEqualTo(loss.getId());
        assertThat(event.getReferenceNo()).isEqualTo(loss.getLossNo());
        assertThat(event.getClientRequestId()).isEqualTo("stock-loss-create-" + loss.getId());
    }

    // --- Helpers ---

    private StockLossCreateReqVO buildCreateReq(String lossType, BigDecimal quantity, String lossReason) {
        StockLossCreateReqVO req = new StockLossCreateReqVO();
        req.setTenantId(TENANT_A);
        req.setLossType(lossType);
        req.setStockItemId(STOCK_ITEM_ID);
        req.setSkuCode(SKU_CODE);
        req.setLocationId(LOCATION_ID);
        req.setQuantity(quantity);
        req.setUnit("KG");
        req.setLossReason(lossReason);
        req.setRemark("test remark");
        req.setOperatorUserId(OPERATOR_ID);
        return req;
    }

    private void seedBalance(Long tenantId, Long stockItemId, Long locationId,
                             BigDecimal availableQty, BigDecimal avgUnitCost) {
        // Delete existing balance if any (avoid duplicate key on selectByTenantItemLocation)
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
}
