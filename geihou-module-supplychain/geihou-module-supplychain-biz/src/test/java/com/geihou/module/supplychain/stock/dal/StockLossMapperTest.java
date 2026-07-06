package com.geihou.module.supplychain.stock.dal;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.supplychain.stock.SupplychainTestConfig;
import com.geihou.module.supplychain.stock.SupplychainTestSchemaInitializer;
import com.geihou.module.supplychain.stock.dal.dataobject.StockLossDO;
import com.geihou.module.supplychain.stock.dal.mapper.StockLossMapper;
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

/**
 * Tests for {@link StockLossMapper}.
 *
 * <p>Covers: insert + selectById, selectByTenantAndLossNo, update status,
 * selectList tenant isolation.
 *
 * <p>Source: TASK-G2-02I-3 Section 7.1.
 */
@SpringBootTest(
        classes = SupplychainTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:stock_loss_mapper_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class StockLossMapperTest {

    @Autowired
    private StockLossMapper stockLossMapper;
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
    void insert_andSelectById_success() {
        StockLossDO loss = buildLossDO(1L, "LOSS-1-001", "LOSS");
        stockLossMapper.insert(loss);

        StockLossDO found = stockLossMapper.selectByIdAndTenant(loss.getId(), 1L);
        assertThat(found).isNotNull();
        assertThat(found.getLossNo()).isEqualTo("LOSS-1-001");
        assertThat(found.getLossType()).isEqualTo("LOSS");
        assertThat(found.getStatus()).isEqualTo("PENDING_APPROVE");
        assertThat(found.getQuantity()).isEqualByComparingTo(new BigDecimal("5.0000"));
    }

    @Test
    void selectByTenantAndLossNo_success() {
        StockLossDO loss = buildLossDO(1L, "LOSS-1-002", "SCRAP");
        stockLossMapper.insert(loss);

        StockLossDO found = stockLossMapper.selectByTenantAndLossNo(1L, "LOSS-1-002");
        assertThat(found).isNotNull();
        assertThat(found.getLossType()).isEqualTo("SCRAP");
    }

    @Test
    void selectByTenantAndLossNo_tenantIsolation_returnsNull() {
        StockLossDO loss = buildLossDO(1L, "LOSS-1-003", "LOSS");
        stockLossMapper.insert(loss);

        // Switch to tenant 2 and query — should return null (tenant isolation)
        TenantContextHolder.setTenantId(2L);
        StockLossDO found = stockLossMapper.selectByTenantAndLossNo(2L, "LOSS-1-003");
        assertThat(found).isNull();
    }

    @Test
    void updateStatus_success() {
        StockLossDO loss = buildLossDO(1L, "LOSS-1-004", "LOSS");
        stockLossMapper.insert(loss);

        int rows = stockLossMapper.updateStatusByTenant(
                loss.getId(), 1L,
                "APPROVED",
                200L,
                LocalDateTime.now(),
                null,
                9999L,
                "PENDING_APPROVE",
                "200",
                LocalDateTime.now()
        );
        assertThat(rows).isEqualTo(1);

        StockLossDO updated = stockLossMapper.selectByIdAndTenant(loss.getId(), 1L);
        assertThat(updated.getStatus()).isEqualTo("APPROVED");
        assertThat(updated.getApproverUserId()).isEqualTo(200L);
        assertThat(updated.getStockEventId()).isEqualTo(9999L);
    }

    @Test
    void updateStatus_wrongExpectStatus_returnsZero() {
        StockLossDO loss = buildLossDO(1L, "LOSS-1-005", "LOSS");
        stockLossMapper.insert(loss);

        // Try to update with wrong expectStatus
        int rows = stockLossMapper.updateStatusByTenant(
                loss.getId(), 1L,
                "APPROVED",
                200L,
                LocalDateTime.now(),
                null,
                null,
                "APPROVED", // wrong — actual is PENDING_APPROVE
                "200",
                LocalDateTime.now()
        );
        assertThat(rows).isEqualTo(0);
    }

    @Test
    void listByTenant_tenantIsolation() {
        stockLossMapper.insert(buildLossDO(1L, "LOSS-1-006", "LOSS"));
        stockLossMapper.insert(buildLossDO(1L, "LOSS-1-007", "SCRAP"));

        // Switch to tenant 2 to insert tenant 2 data
        TenantContextHolder.setTenantId(2L);
        stockLossMapper.insert(buildLossDO(2L, "LOSS-2-001", "LOSS"));

        // Query as tenant 2
        List<StockLossDO> tenant2List = stockLossMapper.listByTenant(2L);
        assertThat(tenant2List).hasSize(1);
        assertThat(tenant2List.get(0).getLossNo()).isEqualTo("LOSS-2-001");

        // Switch back to tenant 1
        TenantContextHolder.setTenantId(1L);
        List<StockLossDO> tenant1List = stockLossMapper.listByTenant(1L);
        assertThat(tenant1List).hasSize(2);
    }

    @Test
    void listByTenantAndStatus_success() {
        StockLossDO loss1 = buildLossDO(1L, "LOSS-1-008", "LOSS");
        loss1.setStatus("APPROVED");
        stockLossMapper.insert(loss1);

        StockLossDO loss2 = buildLossDO(1L, "LOSS-1-009", "LOSS");
        stockLossMapper.insert(loss2);

        List<StockLossDO> pendingList = stockLossMapper.listByTenantAndStatus(1L, "PENDING_APPROVE");
        assertThat(pendingList).hasSize(1);
        assertThat(pendingList.get(0).getLossNo()).isEqualTo("LOSS-1-009");

        List<StockLossDO> approvedList = stockLossMapper.listByTenantAndStatus(1L, "APPROVED");
        assertThat(approvedList).hasSize(1);
        assertThat(approvedList.get(0).getLossNo()).isEqualTo("LOSS-1-008");
    }

    private StockLossDO buildLossDO(Long tenantId, String lossNo, String lossType) {
        StockLossDO loss = new StockLossDO();
        loss.setTenantId(tenantId);
        loss.setLossNo(lossNo);
        loss.setLossType(lossType);
        loss.setStockItemId(1001L);
        loss.setSkuCode("SKU_TEST");
        loss.setLocationId(10L);
        loss.setQuantity(new BigDecimal("5.0000"));
        loss.setUnit("KG");
        loss.setUnitCost(new BigDecimal("10.0000"));
        loss.setTotalAmount(new BigDecimal("50.0000"));
        loss.setLossReason("EXPIRY");
        loss.setStatus("PENDING_APPROVE");
        loss.setOperatorUserId(100L);
        loss.setCreator("100");
        loss.setCreateTime(LocalDateTime.now());
        loss.setUpdater("100");
        loss.setUpdateTime(LocalDateTime.now());
        loss.setDeleted(false);
        return loss;
    }
}
