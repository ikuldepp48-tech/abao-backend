package com.geihou.module.supplychain.production.dal;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.supplychain.production.dal.dataobject.ProductionOrderDO;
import com.geihou.module.supplychain.production.dal.mapper.ProductionOrderMapper;
import com.geihou.module.supplychain.stock.SupplychainTestConfig;
import com.geihou.module.supplychain.stock.SupplychainTestSchemaInitializer;
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
 * Tests for {@link ProductionOrderMapper}.
 *
 * <p>Verifies CRUD, tenant isolation, unique key, stage update with optimistic lock,
 * and list queries.
 *
 * <p>Source: TASK-G2-02K.
 */
@SpringBootTest(
        classes = SupplychainTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:production_order_mapper_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class ProductionOrderMapperTest {

    @Autowired
    private ProductionOrderMapper productionOrderMapper;
    @Autowired
    private DataSource dataSource;

    private static final Long TENANT_A = 1L;
    private static final Long TENANT_B = 2L;

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

    @Test
    void testInsertAndSelectByIdAndTenant() {
        ProductionOrderDO order = buildOrder(TENANT_A, "PO_TEST_001");
        productionOrderMapper.insert(order);

        ProductionOrderDO found = productionOrderMapper.selectByIdAndTenant(order.getId(), TENANT_A);
        assertThat(found).isNotNull();
        assertThat(found.getOrderNo()).isEqualTo("PO_TEST_001");
        assertThat(found.getProductionStage()).isEqualTo("CREATED");
    }

    @Test
    void testSelectByTenantAndOrderNo() {
        ProductionOrderDO order = buildOrder(TENANT_A, "PO_TEST_002");
        productionOrderMapper.insert(order);

        ProductionOrderDO found = productionOrderMapper.selectByTenantAndOrderNo(TENANT_A, "PO_TEST_002");
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(order.getId());
    }

    @Test
    void testSelectByIdAndTenantCrossTenantReturnsNull() {
        ProductionOrderDO order = buildOrder(TENANT_A, "PO_TEST_003");
        productionOrderMapper.insert(order);

        ProductionOrderDO found = productionOrderMapper.selectByIdAndTenant(order.getId(), TENANT_B);
        assertThat(found).isNull();
    }

    @Test
    void testUpdateStageByTenantSuccess() {
        ProductionOrderDO order = buildOrder(TENANT_A, "PO_TEST_004");
        productionOrderMapper.insert(order);

        int rows = productionOrderMapper.updateStageByTenant(
                order.getId(), TENANT_A,
                "MATERIAL_REQUEST", "CREATED",
                null, null, null, null, null,
                "system", LocalDateTime.now()
        );

        assertThat(rows).isEqualTo(1);
        ProductionOrderDO updated = productionOrderMapper.selectByIdAndTenant(order.getId(), TENANT_A);
        assertThat(updated.getProductionStage()).isEqualTo("MATERIAL_REQUEST");
    }

    @Test
    void testUpdateStageByTenantOptimisticLockFails() {
        ProductionOrderDO order = buildOrder(TENANT_A, "PO_TEST_005");
        productionOrderMapper.insert(order);

        // Expect wrong stage → 0 rows
        int rows = productionOrderMapper.updateStageByTenant(
                order.getId(), TENANT_A,
                "MATERIAL_REQUEST", "IN_PROGRESS", // wrong expectStage
                null, null, null, null, null,
                "system", LocalDateTime.now()
        );

        assertThat(rows).isEqualTo(0);
    }

    @Test
    void testUpdateMutableByTenant() {
        ProductionOrderDO order = buildOrder(TENANT_A, "PO_TEST_006");
        productionOrderMapper.insert(order);

        int rows = productionOrderMapper.updateMutableByTenant(
                order.getId(), TENANT_A,
                100L, 200L, 10L,
                new BigDecimal("200.0000"),
                null, null, 100L, "updated remark",
                "system", LocalDateTime.now()
        );

        assertThat(rows).isEqualTo(1);
        ProductionOrderDO updated = productionOrderMapper.selectByIdAndTenant(order.getId(), TENANT_A);
        assertThat(updated.getPlannedQty()).isEqualByComparingTo(new BigDecimal("200.0000"));
        assertThat(updated.getRemark()).isEqualTo("updated remark");
    }

    @Test
    void testUpdateMutableByTenantFailsAfterStageChange() {
        ProductionOrderDO order = buildOrder(TENANT_A, "PO_TEST_007");
        productionOrderMapper.insert(order);

        // Change stage first
        productionOrderMapper.updateStageByTenant(
                order.getId(), TENANT_A,
                "MATERIAL_REQUEST", "CREATED",
                null, null, null, null, null,
                "system", LocalDateTime.now()
        );

        // Try mutable update — should fail (stage no longer CREATED)
        int rows = productionOrderMapper.updateMutableByTenant(
                order.getId(), TENANT_A,
                100L, 200L, 10L,
                new BigDecimal("300.0000"),
                null, null, 100L, "should fail",
                "system", LocalDateTime.now()
        );

        assertThat(rows).isEqualTo(0);
    }

    @Test
    void testListByTenant() {
        productionOrderMapper.insert(buildOrder(TENANT_A, "PO_TEST_008"));
        productionOrderMapper.insert(buildOrder(TENANT_A, "PO_TEST_009"));

        TenantContextHolder.setTenantId(TENANT_B);
        productionOrderMapper.insert(buildOrder(TENANT_B, "PO_TEST_010"));

        List<ProductionOrderDO> tenantBList = productionOrderMapper.listByTenant(TENANT_B);

        TenantContextHolder.setTenantId(TENANT_A);
        List<ProductionOrderDO> tenantAList = productionOrderMapper.listByTenant(TENANT_A);

        assertThat(tenantAList).hasSize(2);
        assertThat(tenantBList).hasSize(1);
    }

    @Test
    void testListByTenantAndStage() {
        ProductionOrderDO order1 = buildOrder(TENANT_A, "PO_TEST_011");
        productionOrderMapper.insert(order1);

        ProductionOrderDO order2 = buildOrder(TENANT_A, "PO_TEST_012");
        productionOrderMapper.insert(order2);

        // Transition order1 to MATERIAL_REQUEST
        productionOrderMapper.updateStageByTenant(
                order1.getId(), TENANT_A,
                "MATERIAL_REQUEST", "CREATED",
                null, null, null, null, null,
                "system", LocalDateTime.now()
        );

        List<ProductionOrderDO> createdList = productionOrderMapper.listByTenantAndStage(TENANT_A, "CREATED");
        List<ProductionOrderDO> materialRequestList = productionOrderMapper.listByTenantAndStage(TENANT_A, "MATERIAL_REQUEST");

        assertThat(createdList).hasSize(1);
        assertThat(materialRequestList).hasSize(1);
        assertThat(createdList.get(0).getOrderNo()).isEqualTo("PO_TEST_012");
        assertThat(materialRequestList.get(0).getOrderNo()).isEqualTo("PO_TEST_011");
    }

    // --- Helper ---

    private ProductionOrderDO buildOrder(Long tenantId, String orderNo) {
        ProductionOrderDO order = new ProductionOrderDO();
        order.setTenantId(tenantId);
        order.setOrderNo(orderNo);
        order.setProductId(100L);
        order.setRecipeId(200L);
        order.setLocationId(10L);
        order.setPlannedQty(new BigDecimal("100.0000"));
        order.setProductionStage("CREATED");
        order.setOperatorUserId(100L);
        order.setRemark("test");
        order.setCreator("system");
        order.setCreateTime(LocalDateTime.now());
        order.setUpdater("system");
        order.setUpdateTime(LocalDateTime.now());
        order.setDeleted(false);
        return order;
    }
}
