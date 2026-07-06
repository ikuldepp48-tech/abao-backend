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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrent test: 100 threads consume from the same (item, location) with limited stock.
 *
 * <p>Covers: AC-4 (no oversell under concurrency), AC-8 (optimistic lock effective).
 * Total deductions must not exceed available_qty.
 */
@SpringBootTest(
        classes = SupplychainTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:stock_concurrent_test;DB_CLOSE_DELAY=-1;MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class ConcurrentRecordNoOversellTest {

    @Autowired
    private StockEventService stockEventService;
    @Autowired
    private StockBalanceService stockBalanceService;
    @Autowired
    private StockEventMapper stockEventMapper;
    @Autowired
    private DataSource dataSource;

    private static final int THREAD_COUNT = 100;
    private static final BigDecimal INITIAL_STOCK = new BigDecimal("50");
    private static final BigDecimal CONSUME_PER_THREAD = BigDecimal.ONE;

    @BeforeEach
    void setUp() throws Exception {
        SupplychainTestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(1L);

        // Purchase in 50 units
        StockEventReqDTO inReq = new StockEventReqDTO();
        inReq.setTenantId(1L);
        inReq.setEventTime(LocalDateTime.now());
        inReq.setBusinessDate(LocalDate.now());
        inReq.setEventType(StockEventTypeEnum.PURCHASE_IN.getCode());
        inReq.setDirection(StockDirectionEnum.IN.getCode());
        inReq.setStockItemId(1001L);
        inReq.setSkuCode("SKU_TEST");
        inReq.setLocationId(10L);
        inReq.setQuantity(INITIAL_STOCK);
        inReq.setUnit("个");
        inReq.setOperatorUserId(999L);
        inReq.setClientRequestId("init-" + UUID.randomUUID());
        stockEventService.recordEvent(inReq);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void concurrentConsumeNoOversell() throws InterruptedException {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    TenantContextHolder.setTenantId(1L);

                    StockEventReqDTO req = new StockEventReqDTO();
                    req.setTenantId(1L);
                    req.setEventTime(LocalDateTime.now());
                    req.setBusinessDate(LocalDate.now());
                    req.setEventType(StockEventTypeEnum.CONSUME_OUT.getCode());
                    req.setDirection(StockDirectionEnum.OUT.getCode());
                    req.setStockItemId(1001L);
                    req.setSkuCode("SKU_TEST");
                    req.setLocationId(10L);
                    req.setQuantity(CONSUME_PER_THREAD);
                    req.setUnit("个");
                    req.setOperatorUserId(999L);
                    req.setClientRequestId("consume-" + index + "-" + UUID.randomUUID());

                    stockEventService.recordEvent(req);
                    successCount.incrementAndGet();
                } catch (StockBusinessException e) {
                    // Insufficient stock or optimistic lock conflict — expected
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    TenantContextHolder.clear();
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        // Verify no oversell: success count should be <= 50 (initial stock)
        assertThat(successCount.get()).isLessThanOrEqualTo(INITIAL_STOCK.intValue());
        assertThat(successCount.get()).isEqualTo(INITIAL_STOCK.intValue());

        // Verify remaining balance is 0
        BigDecimal remaining = stockBalanceService.getAvailableQty(1L, 1001L, 10L);
        assertThat(remaining).isEqualByComparingTo(BigDecimal.ZERO);

        // 50 threads should succeed, 50 should fail
        assertThat(successCount.get() + failureCount.get()).isEqualTo(THREAD_COUNT);

        // G2-01A FIX: verify balance_after on each successful event is correct
        // — no stale snapshot from failed retry attempts.
        List<StockEventDO> consumeEvents = stockEventMapper.selectByTenantItemLocation(1L, 1001L, 10L);
        // Filter to only CONSUME_OUT events (exclude the initial PURCHASE_IN)
        List<StockEventDO> outEvents = consumeEvents.stream()
                .filter(e -> StockEventTypeEnum.CONSUME_OUT.getCode().equals(e.getEventType()))
                .toList();

        assertThat(outEvents).hasSize(INITIAL_STOCK.intValue());

        // Every balance_after must be non-negative
        for (StockEventDO e : outEvents) {
            assertThat(e.getBalanceAfter()).isNotNull();
            assertThat(e.getBalanceAfter().compareTo(BigDecimal.ZERO)).isGreaterThanOrEqualTo(0);
        }

        // Every balance_after must be distinct (each deduction reduces by exactly 1,
        // so values should be 49, 48, 47, ..., 0 in some order)
        java.util.Set<BigDecimal> distinctAfterValues = new java.util.HashSet<>();
        for (StockEventDO e : outEvents) {
            assertThat(distinctAfterValues.add(e.getBalanceAfter()))
                    .as("duplicate balance_after found — likely stale snapshot from retry")
                    .isTrue();
        }

        // The set of balance_after values should be {0, 1, 2, ..., 49}
        // Use compareTo-based check since DECIMAL(18,4) stores 0.0000 not 0
        for (int i = 0; i < INITIAL_STOCK.intValue(); i++) {
            BigDecimal expected = BigDecimal.valueOf(i);
            boolean found = distinctAfterValues.stream()
                    .anyMatch(v -> v.compareTo(expected) == 0);
            assertThat(found)
                    .as("expected balance_after value %d not found among events", i)
                    .isTrue();
        }
    }
}
