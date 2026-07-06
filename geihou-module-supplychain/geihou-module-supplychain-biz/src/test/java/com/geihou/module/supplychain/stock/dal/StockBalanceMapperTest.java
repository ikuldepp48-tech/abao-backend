package com.geihou.module.supplychain.stock.dal;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.supplychain.stock.SupplychainTestConfig;
import com.geihou.module.supplychain.stock.SupplychainTestSchemaInitializer;
import com.geihou.module.supplychain.stock.dal.dataobject.StockBalanceDO;
import com.geihou.module.supplychain.stock.dal.mapper.StockBalanceMapper;
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
 * Tests for {@link StockBalanceMapper}.
 *
 * <p>Covers: AC-8 (optimistic lock version effective).
 */
@SpringBootTest(
        classes = SupplychainTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:stock_balance_mapper_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class StockBalanceMapperTest {

    @Autowired
    private StockBalanceMapper stockBalanceMapper;
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
    void insertAndSelectByTenantItemLocation() {
        StockBalanceDO balance = buildBalance();
        stockBalanceMapper.insert(balance);

        StockBalanceDO found = stockBalanceMapper.selectByTenantItemLocation(1L, 1001L, 10L);
        assertThat(found).isNotNull();
        assertThat(found.getAvailableQty()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(found.getVersion()).isEqualTo(0);
    }

    @Test
    void updateBalanceWithOptimisticLock_success() {
        StockBalanceDO balance = buildBalance();
        stockBalanceMapper.insert(balance);

        int rows = stockBalanceMapper.updateBalanceWithOptimisticLock(
                balance.getId(),
                1L,
                new BigDecimal("80"),
                new BigDecimal("80"),
                123L,
                LocalDateTime.now(),
                0, // correct version
                "tester",
                LocalDateTime.now()
        );

        assertThat(rows).isEqualTo(1);

        StockBalanceDO updated = stockBalanceMapper.selectById(balance.getId());
        assertThat(updated.getAvailableQty()).isEqualByComparingTo(new BigDecimal("80"));
        assertThat(updated.getVersion()).isEqualTo(1);
    }

    @Test
    void updateBalanceWithOptimisticLock_versionConflictReturnsZero() {
        StockBalanceDO balance = buildBalance();
        stockBalanceMapper.insert(balance);

        int rows = stockBalanceMapper.updateBalanceWithOptimisticLock(
                balance.getId(),
                1L,
                new BigDecimal("80"),
                new BigDecimal("80"),
                123L,
                LocalDateTime.now(),
                99, // wrong version
                "tester",
                LocalDateTime.now()
        );

        assertThat(rows).isEqualTo(0);

        StockBalanceDO unchanged = stockBalanceMapper.selectById(balance.getId());
        assertThat(unchanged.getAvailableQty()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(unchanged.getVersion()).isEqualTo(0);
    }

    @Test
    void updateBalanceWithOptimisticLock_tenantIsolation() {
        StockBalanceDO balance = buildBalance();
        stockBalanceMapper.insert(balance);

        // Try to update with wrong tenant
        int rows = stockBalanceMapper.updateBalanceWithOptimisticLock(
                balance.getId(),
                999L, // wrong tenant
                new BigDecimal("80"),
                new BigDecimal("80"),
                123L,
                LocalDateTime.now(),
                0,
                "tester",
                LocalDateTime.now()
        );

        assertThat(rows).isEqualTo(0);
    }

    @Test
    void updateBalanceWithReserve_success() {
        StockBalanceDO balance = buildBalance();
        stockBalanceMapper.insert(balance);

        int rows = stockBalanceMapper.updateBalanceWithReserve(
                balance.getId(),
                1L,
                new BigDecimal("80"),  // new available
                new BigDecimal("100"), // total unchanged
                new BigDecimal("20"),  // reserved
                null, null,
                0, // correct version
                "tester",
                LocalDateTime.now()
        );

        assertThat(rows).isEqualTo(1);

        StockBalanceDO updated = stockBalanceMapper.selectById(balance.getId());
        assertThat(updated.getAvailableQty()).isEqualByComparingTo(new BigDecimal("80"));
        assertThat(updated.getReservedQty()).isEqualByComparingTo(new BigDecimal("20"));
        assertThat(updated.getTotalQty()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(updated.getVersion()).isEqualTo(1);
    }

    @Test
    void updateBalanceWithReserve_versionConflictReturnsZero() {
        StockBalanceDO balance = buildBalance();
        stockBalanceMapper.insert(balance);

        int rows = stockBalanceMapper.updateBalanceWithReserve(
                balance.getId(),
                1L,
                new BigDecimal("80"),
                new BigDecimal("100"),
                new BigDecimal("20"),
                null, null,
                99, // wrong version
                "tester",
                LocalDateTime.now()
        );

        assertThat(rows).isEqualTo(0);

        StockBalanceDO unchanged = stockBalanceMapper.selectById(balance.getId());
        assertThat(unchanged.getAvailableQty()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(unchanged.getVersion()).isEqualTo(0);
    }

    private StockBalanceDO buildBalance() {
        StockBalanceDO balance = new StockBalanceDO();
        balance.setTenantId(1L);
        balance.setStockItemId(1001L);
        balance.setLocationId(10L);
        balance.setAvailableQty(new BigDecimal("100"));
        balance.setTotalQty(new BigDecimal("100"));
        balance.setReservedQty(BigDecimal.ZERO);
        balance.setAvgUnitCost(BigDecimal.ZERO);
        balance.setVersion(0);
        balance.setCreator("test");
        balance.setCreateTime(LocalDateTime.now());
        balance.setUpdater("test");
        balance.setUpdateTime(LocalDateTime.now());
        balance.setDeleted(false);
        return balance;
    }
}
