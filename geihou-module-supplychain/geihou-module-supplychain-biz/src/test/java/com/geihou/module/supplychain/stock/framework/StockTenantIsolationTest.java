package com.geihou.module.supplychain.stock.framework;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.supplychain.api.stock.dto.StockEventReqDTO;
import com.geihou.module.supplychain.api.stock.dto.StockReleaseReqDTO;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tenant isolation test for stock module.
 *
 * <p>Covers: AC-3 (tenant isolation — all queries filter by tenant_id).
 */
@SpringBootTest(
        classes = SupplychainTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:stock_tenant_iso_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class StockTenantIsolationTest {

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
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void tenantACannotSeeTenantBEvents() {
        // Create event as tenant A
        TenantContextHolder.setTenantId(1L);
        stockEventService.recordEvent(buildReq(1L, "req-a-" + UUID.randomUUID()));

        // Switch to tenant B
        TenantContextHolder.setTenantId(2L);

        // Query events for same item+location — should be empty
        List<StockEventDO> events = stockEventMapper.selectByTenantItemLocation(2L, 1001L, 10L);
        assertThat(events).isEmpty();
    }

    @Test
    void tenantACannotSeeTenantBBalance() {
        // Create balance as tenant A
        TenantContextHolder.setTenantId(1L);
        stockEventService.recordEvent(buildReq(1L, "req-a-" + UUID.randomUUID()));

        // Switch to tenant B
        TenantContextHolder.setTenantId(2L);

        // Query balance — should be 0 (no balance for tenant B)
        BigDecimal available = stockBalanceService.getAvailableQty(2L, 1001L, 10L);
        assertThat(available).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void sameItemLocationDifferentTenantsIndependent() {
        // Tenant A: purchase 100
        TenantContextHolder.setTenantId(1L);
        stockEventService.recordEvent(buildReq(1L, "req-a-" + UUID.randomUUID()));

        // Tenant B: purchase 50
        TenantContextHolder.setTenantId(2L);
        stockEventService.recordEvent(buildReq(2L, "req-b-" + UUID.randomUUID()));

        // Verify tenant A has 100
        TenantContextHolder.setTenantId(1L);
        BigDecimal availA = stockBalanceService.getAvailableQty(1L, 1001L, 10L);
        assertThat(availA).isEqualByComparingTo(new BigDecimal("100"));

        // Verify tenant B has 50
        TenantContextHolder.setTenantId(2L);
        BigDecimal availB = stockBalanceService.getAvailableQty(2L, 1001L, 10L);
        assertThat(availB).isEqualByComparingTo(new BigDecimal("50"));
    }

    @Test
    void eventTenantIdMatchesCreatingTenant() {
        TenantContextHolder.setTenantId(1L);
        Long eventId = stockEventService.recordEvent(buildReq(1L, "req-" + UUID.randomUUID()));

        StockEventDO event = stockEventMapper.selectById(eventId);
        assertThat(event.getTenantId()).isEqualTo(1L);
    }

    // === G2-01B1: Reserve cross-tenant isolation ===

    @Test
    void reserve_crossTenantCannotRelease() {
        // Tenant 1: purchase 100 and reserve 30
        TenantContextHolder.setTenantId(1L);
        stockEventService.recordEvent(buildReq(1L, "req-a-" + UUID.randomUUID()));

        StockReserveReqDTO reserveReq = new StockReserveReqDTO();
        reserveReq.setTenantId(1L);
        reserveReq.setStockItemId(1001L);
        reserveReq.setLocationId(10L);
        reserveReq.setSkuCode("SKU_TEST");
        reserveReq.setQuantity(new BigDecimal("30"));
        reserveReq.setUnit("个");
        reserveReq.setSourceModule("checkout");
        reserveReq.setSourceRecordId(100L);
        reserveReq.setIdempotentKey("reserve-tenant1-" + UUID.randomUUID());
        reserveReq.setOperatorUserId(999L);
        Long reserveId = stockReserveService.reserveStock(reserveReq);
        assertThat(reserveId).isNotNull();

        // Tenant 2: try to release using tenant 1's idempotent key
        TenantContextHolder.setTenantId(2L);
        StockReleaseReqDTO releaseReq = new StockReleaseReqDTO();
        releaseReq.setTenantId(2L);
        releaseReq.setIdempotentKey(reserveReq.getIdempotentKey());
        releaseReq.setOperatorUserId(999L);

        // Should throw RESERVE_NOT_FOUND — tenant 2 has no such reservation
        assertThatThrownBy(() -> stockReserveService.releaseStock(releaseReq))
                .isInstanceOf(StockBusinessException.class);
    }

    @Test
    void reserve_crossTenantCannotCommit() {
        // Tenant 1: purchase 100 and reserve 20
        TenantContextHolder.setTenantId(1L);
        stockEventService.recordEvent(buildReq(1L, "req-b-" + UUID.randomUUID()));

        String idemKey = "reserve-commit-iso-" + UUID.randomUUID();
        StockReserveReqDTO reserveReq = new StockReserveReqDTO();
        reserveReq.setTenantId(1L);
        reserveReq.setStockItemId(1001L);
        reserveReq.setLocationId(10L);
        reserveReq.setSkuCode("SKU_TEST");
        reserveReq.setQuantity(new BigDecimal("20"));
        reserveReq.setUnit("个");
        reserveReq.setSourceModule("checkout");
        reserveReq.setSourceRecordId(100L);
        reserveReq.setIdempotentKey(idemKey);
        reserveReq.setOperatorUserId(999L);
        stockReserveService.reserveStock(reserveReq);

        // Tenant 2: try to commit using tenant 1's idempotent key
        TenantContextHolder.setTenantId(2L);
        com.geihou.module.supplychain.api.stock.dto.StockCommitReqDTO commitReq =
                new com.geihou.module.supplychain.api.stock.dto.StockCommitReqDTO();
        commitReq.setTenantId(2L);
        commitReq.setIdempotentKey(idemKey);
        commitReq.setOperatorUserId(999L);

        // Should throw RESERVE_NOT_FOUND
        assertThatThrownBy(() -> stockReserveService.commitStock(commitReq))
                .isInstanceOf(StockBusinessException.class);
    }

    @Test
    void reserve_tenantAReserveNotVisibleToTenantB() {
        // Tenant 1: purchase 100 and reserve 30
        TenantContextHolder.setTenantId(1L);
        stockEventService.recordEvent(buildReq(1L, "req-c-" + UUID.randomUUID()));

        StockReserveReqDTO reserveReq = new StockReserveReqDTO();
        reserveReq.setTenantId(1L);
        reserveReq.setStockItemId(1001L);
        reserveReq.setLocationId(10L);
        reserveReq.setSkuCode("SKU_TEST");
        reserveReq.setQuantity(new BigDecimal("30"));
        reserveReq.setUnit("个");
        reserveReq.setSourceModule("checkout");
        reserveReq.setSourceRecordId(100L);
        reserveReq.setIdempotentKey("reserve-visible-" + UUID.randomUUID());
        reserveReq.setOperatorUserId(999L);
        stockReserveService.reserveStock(reserveReq);

        // Tenant 1: reserved should be 30
        assertThat(stockBalanceService.getReservedQty(1L, 1001L, 10L))
                .isEqualByComparingTo(new BigDecimal("30"));

        // Tenant 2: reserved should be 0 (no reservation for tenant 2)
        TenantContextHolder.setTenantId(2L);
        assertThat(stockBalanceService.getReservedQty(2L, 1001L, 10L))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    private StockEventReqDTO buildReq(Long tenantId, String clientRequestId) {
        StockEventReqDTO req = new StockEventReqDTO();
        req.setTenantId(tenantId);
        req.setEventTime(LocalDateTime.now());
        req.setBusinessDate(LocalDate.now());
        req.setEventType(StockEventTypeEnum.PURCHASE_IN.getCode());
        req.setDirection(StockDirectionEnum.IN.getCode());
        req.setStockItemId(1001L);
        req.setSkuCode("SKU_TEST");
        req.setLocationId(10L);
        req.setQuantity(tenantId == 1L ? new BigDecimal("100") : new BigDecimal("50"));
        req.setUnit("个");
        req.setOperatorUserId(999L);
        req.setClientRequestId(clientRequestId);
        return req;
    }
}
