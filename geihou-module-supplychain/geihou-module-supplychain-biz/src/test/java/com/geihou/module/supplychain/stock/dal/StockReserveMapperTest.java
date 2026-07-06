package com.geihou.module.supplychain.stock.dal;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.supplychain.api.stock.enums.StockReserveStatusEnum;
import com.geihou.module.supplychain.stock.SupplychainTestConfig;
import com.geihou.module.supplychain.stock.SupplychainTestSchemaInitializer;
import com.geihou.module.supplychain.stock.dal.dataobject.StockReserveDO;
import com.geihou.module.supplychain.stock.dal.mapper.StockReserveMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link StockReserveMapper}.
 *
 * <p>Covers: AC-4 (stock_reserve table with tenant_id + UNIQUE + status),
 * AC-15 (tenant isolation).
 */
@SpringBootTest(
        classes = SupplychainTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:stock_reserve_mapper_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class StockReserveMapperTest {

    @Autowired
    private StockReserveMapper stockReserveMapper;
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
    void insertAndSelectByTenantIdempotentKey() {
        StockReserveDO reserve = buildReserve(1L, "idem-001");
        stockReserveMapper.insert(reserve);

        StockReserveDO found = stockReserveMapper.selectByTenantIdempotentKey(1L, "idem-001");
        assertThat(found).isNotNull();
        assertThat(found.getQuantity()).isEqualByComparingTo(new BigDecimal("20"));
        assertThat(found.getStatus()).isEqualTo(StockReserveStatusEnum.RESERVED.getCode());
    }

    @Test
    void selectByIdAndTenant_tenantIsolation() {
        StockReserveDO reserve = buildReserve(1L, "idem-002");
        stockReserveMapper.insert(reserve);

        // Correct tenant finds it
        StockReserveDO found = stockReserveMapper.selectByIdAndTenant(reserve.getId(), 1L);
        assertThat(found).isNotNull();

        // Wrong tenant does not find it
        StockReserveDO notFound = stockReserveMapper.selectByIdAndTenant(reserve.getId(), 999L);
        assertThat(notFound).isNull();
    }

    @Test
    void updateStatus_success() {
        StockReserveDO reserve = buildReserve(1L, "idem-003");
        stockReserveMapper.insert(reserve);

        int rows = stockReserveMapper.updateStatus(
                reserve.getId(), 1L,
                StockReserveStatusEnum.RELEASED.getCode(),
                StockReserveStatusEnum.RESERVED.getCode(),
                null, "tester", LocalDateTime.now());

        assertThat(rows).isEqualTo(1);

        StockReserveDO updated = stockReserveMapper.selectById(reserve.getId());
        assertThat(updated.getStatus()).isEqualTo(StockReserveStatusEnum.RELEASED.getCode());
    }

    @Test
    void updateStatus_wrongExpectedStatusReturnsZero() {
        StockReserveDO reserve = buildReserve(1L, "idem-004");
        stockReserveMapper.insert(reserve);

        // Try to update from COMMITTED (wrong expected status — actual is RESERVED)
        int rows = stockReserveMapper.updateStatus(
                reserve.getId(), 1L,
                StockReserveStatusEnum.RELEASED.getCode(),
                StockReserveStatusEnum.COMMITTED.getCode(),
                null, "tester", LocalDateTime.now());

        assertThat(rows).isEqualTo(0);
    }

    @Test
    void updateStatus_withCommitEventId() {
        StockReserveDO reserve = buildReserve(1L, "idem-005");
        stockReserveMapper.insert(reserve);

        int rows = stockReserveMapper.updateStatus(
                reserve.getId(), 1L,
                StockReserveStatusEnum.COMMITTED.getCode(),
                StockReserveStatusEnum.RESERVED.getCode(),
                12345L, "tester", LocalDateTime.now());

        assertThat(rows).isEqualTo(1);

        StockReserveDO updated = stockReserveMapper.selectById(reserve.getId());
        assertThat(updated.getStatus()).isEqualTo(StockReserveStatusEnum.COMMITTED.getCode());
        assertThat(updated.getCommitEventId()).isEqualTo(12345L);
    }

    @Test
    void updateStatus_tenantIsolation() {
        StockReserveDO reserve = buildReserve(1L, "idem-006");
        stockReserveMapper.insert(reserve);

        int rows = stockReserveMapper.updateStatus(
                reserve.getId(), 999L, // wrong tenant
                StockReserveStatusEnum.RELEASED.getCode(),
                StockReserveStatusEnum.RESERVED.getCode(),
                null, "tester", LocalDateTime.now());

        assertThat(rows).isEqualTo(0);
    }

    private StockReserveDO buildReserve(Long tenantId, String idempotentKey) {
        StockReserveDO reserve = new StockReserveDO();
        reserve.setTenantId(tenantId);
        reserve.setStockItemId(1001L);
        reserve.setLocationId(10L);
        reserve.setSkuCode("SKU_TEST");
        reserve.setQuantity(new BigDecimal("20"));
        reserve.setUnit("个");
        reserve.setSourceModule("checkout");
        reserve.setSourceRecordId(100L);
        reserve.setReferenceNo("ORDER-001");
        reserve.setIdempotentKey(idempotentKey);
        reserve.setStatus(StockReserveStatusEnum.RESERVED.getCode());
        reserve.setOperatorUserId(999L);
        reserve.setCreator("test");
        reserve.setCreateTime(LocalDateTime.now());
        reserve.setUpdater("test");
        reserve.setUpdateTime(LocalDateTime.now());
        reserve.setDeleted(false);
        return reserve;
    }
}
