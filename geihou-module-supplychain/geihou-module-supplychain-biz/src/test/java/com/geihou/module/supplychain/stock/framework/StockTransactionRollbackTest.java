package com.geihou.module.supplychain.stock.framework;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.supplychain.api.stock.dto.StockEventReqDTO;
import com.geihou.module.supplychain.api.stock.dto.StockCommitReqDTO;
import com.geihou.module.supplychain.api.stock.dto.StockReserveReqDTO;
import com.geihou.module.supplychain.api.stock.enums.StockDirectionEnum;
import com.geihou.module.supplychain.api.stock.enums.StockEventTypeEnum;
import com.geihou.module.supplychain.stock.SupplychainTestConfig;
import com.geihou.module.supplychain.stock.SupplychainTestSchemaInitializer;
import com.geihou.module.supplychain.stock.dal.dataobject.StockEventDO;
import com.geihou.module.supplychain.stock.dal.mapper.StockEventMapper;
import com.geihou.module.supplychain.stock.service.StockBalanceService;
import com.geihou.module.supplychain.stock.service.StockEventService;
import com.geihou.module.supplychain.stock.service.StockReserveService;
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
 * Transaction rollback test: when balance update fails, event should also roll back.
 *
 * <p>Covers: AC-11 (event + balance update in same transaction, atomic).
 */
@SpringBootTest(
        classes = SupplychainTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:stock_rollback_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class StockTransactionRollbackTest {

    @Autowired
    private StockEventService stockEventService;
    @Autowired
    private StockBalanceService stockBalanceService;
    @Autowired
    private StockReserveService stockReserveService;
    @Autowired
    private StockEventMapper stockEventMapper;
    @Autowired
    private DataSource dataSource;

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
    void insufficientStockRollsBackEvent() {
        // Try to consume more than available (0) — should fail
        StockEventReqDTO req = buildReq(StockEventTypeEnum.CONSUME_OUT, StockDirectionEnum.OUT,
                new BigDecimal("10"), "rollback-" + UUID.randomUUID());

        assertThatThrownBy(() -> stockEventService.recordEvent(req))
                .isInstanceOf(StockBusinessException.class);

        // Event should NOT exist (rolled back)
        StockEventDO event = stockEventMapper.selectByClientRequestId(1L, req.getClientRequestId());
        assertThat(event).isNull();

        // Balance should still be 0
        BigDecimal available = stockBalanceService.getAvailableQty(1L, 1001L, 10L);
        assertThat(available).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void successfulEventCommitsBothEventAndBalance() {
        StockEventReqDTO req = buildReq(StockEventTypeEnum.PURCHASE_IN, StockDirectionEnum.IN,
                new BigDecimal("50"), "commit-" + UUID.randomUUID());

        Long eventId = stockEventService.recordEvent(req);

        // Event should exist
        StockEventDO event = stockEventMapper.selectById(eventId);
        assertThat(event).isNotNull();

        // Balance should be updated
        BigDecimal available = stockBalanceService.getAvailableQty(1L, 1001L, 10L);
        assertThat(available).isEqualByComparingTo(new BigDecimal("50"));
    }

    // === G2-01B1: Reserve/commit rollback tests ===

    @Test
    void reserve_insufficientStockRollsBack() {
        // No stock available (0 balance)
        StockReserveReqDTO req = new StockReserveReqDTO();
        req.setTenantId(1L);
        req.setStockItemId(1001L);
        req.setLocationId(10L);
        req.setSkuCode("SKU_TEST");
        req.setQuantity(new BigDecimal("10"));
        req.setUnit("个");
        req.setSourceModule("checkout");
        req.setSourceRecordId(100L);
        req.setIdempotentKey("rollback-reserve-" + UUID.randomUUID());
        req.setOperatorUserId(999L);

        assertThatThrownBy(() -> stockReserveService.reserveStock(req))
                .isInstanceOf(StockBusinessException.class);

        // Balance should still be 0
        assertThat(stockBalanceService.getAvailableQty(1L, 1001L, 10L))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(stockBalanceService.getReservedQty(1L, 1001L, 10L))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void reserveAndCommit_successfulFlow() {
        // Purchase 100
        stockEventService.recordEvent(buildReq(StockEventTypeEnum.PURCHASE_IN, StockDirectionEnum.IN,
                new BigDecimal("100"), "purchase-" + UUID.randomUUID()));

        // Reserve 30
        String idemKey = "reserve-flow-" + UUID.randomUUID();
        StockReserveReqDTO reserveReq = new StockReserveReqDTO();
        reserveReq.setTenantId(1L);
        reserveReq.setStockItemId(1001L);
        reserveReq.setLocationId(10L);
        reserveReq.setSkuCode("SKU_TEST");
        reserveReq.setQuantity(new BigDecimal("30"));
        reserveReq.setUnit("个");
        reserveReq.setSourceModule("checkout");
        reserveReq.setSourceRecordId(100L);
        reserveReq.setIdempotentKey(idemKey);
        reserveReq.setOperatorUserId(999L);
        stockReserveService.reserveStock(reserveReq);

        // Available should be 70, reserved 30, total 100
        assertThat(stockBalanceService.getAvailableQty(1L, 1001L, 10L))
                .isEqualByComparingTo(new BigDecimal("70"));
        assertThat(stockBalanceService.getReservedQty(1L, 1001L, 10L))
                .isEqualByComparingTo(new BigDecimal("30"));

        // Commit
        StockCommitReqDTO commitReq = new StockCommitReqDTO();
        commitReq.setTenantId(1L);
        commitReq.setIdempotentKey(idemKey);
        commitReq.setOperatorUserId(999L);
        commitReq.setEventTime(LocalDateTime.now());
        commitReq.setBusinessDate(LocalDate.now());
        Long eventId = stockReserveService.commitStock(commitReq);

        // After commit: available 70 (unchanged), reserved 0, total 70
        assertThat(stockBalanceService.getAvailableQty(1L, 1001L, 10L))
                .isEqualByComparingTo(new BigDecimal("70"));
        assertThat(stockBalanceService.getReservedQty(1L, 1001L, 10L))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(eventId).isNotNull();
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
        return req;
    }
}
