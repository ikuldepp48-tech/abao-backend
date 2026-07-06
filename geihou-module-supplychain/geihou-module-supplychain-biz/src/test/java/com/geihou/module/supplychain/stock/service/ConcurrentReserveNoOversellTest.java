package com.geihou.module.supplychain.stock.service;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.supplychain.api.stock.dto.StockEventReqDTO;
import com.geihou.module.supplychain.api.stock.dto.StockReserveReqDTO;
import com.geihou.module.supplychain.api.stock.enums.StockDirectionEnum;
import com.geihou.module.supplychain.api.stock.enums.StockEventTypeEnum;
import com.geihou.module.supplychain.stock.SupplychainTestConfig;
import com.geihou.module.supplychain.stock.SupplychainTestSchemaInitializer;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrent test: 100 threads reserve from the same (item, location) with limited stock.
 *
 * <p>Covers: AC-9 (no oversell under concurrency), AC-8 (optimistic lock effective).
 * Total reservations must not exceed available_qty.
 */
@SpringBootTest(
        classes = SupplychainTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:stock_concurrent_reserve_test;DB_CLOSE_DELAY=-1;MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class ConcurrentReserveNoOversellTest {

    @Autowired
    private StockReserveService stockReserveService;
    @Autowired
    private StockEventService stockEventService;
    @Autowired
    private StockBalanceService stockBalanceService;
    @Autowired
    private DataSource dataSource;

    private static final int THREAD_COUNT = 100;
    private static final BigDecimal INITIAL_STOCK = new BigDecimal("50");
    private static final BigDecimal RESERVE_PER_THREAD = BigDecimal.ONE;

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
    void concurrentReserveNoOversell() throws InterruptedException {
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

                    StockReserveReqDTO req = new StockReserveReqDTO();
                    req.setTenantId(1L);
                    req.setStockItemId(1001L);
                    req.setLocationId(10L);
                    req.setSkuCode("SKU_TEST");
                    req.setQuantity(RESERVE_PER_THREAD);
                    req.setUnit("个");
                    req.setSourceModule("checkout");
                    req.setSourceRecordId(200L + index);
                    req.setReferenceNo("ORDER-" + index);
                    req.setIdempotentKey("reserve-" + index + "-" + UUID.randomUUID());
                    req.setOperatorUserId(999L);

                    stockReserveService.reserveStock(req);
                    successCount.incrementAndGet();
                } catch (StockBusinessException e) {
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

        // 50 threads should succeed, 50 should fail
        assertThat(successCount.get() + failureCount.get()).isEqualTo(THREAD_COUNT);

        // Verify remaining available is 0, reserved is 50, total is 50
        BigDecimal available = stockBalanceService.getAvailableQty(1L, 1001L, 10L);
        assertThat(available).isEqualByComparingTo(BigDecimal.ZERO);

        BigDecimal reserved = stockBalanceService.getReservedQty(1L, 1001L, 10L);
        assertThat(reserved).isEqualByComparingTo(INITIAL_STOCK);

        // Three-value invariant: available = total - reserved
        var balance = stockBalanceService.getBalance(1L, 1001L, 10L);
        assertThat(balance.getAvailableQty()).isEqualByComparingTo(
                balance.getTotalQty().subtract(balance.getReservedQty()));
    }
}
