package com.geihou.module.supplychain.stock.service;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.supplychain.api.stock.dto.StockBalanceRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockCommitReqDTO;
import com.geihou.module.supplychain.api.stock.dto.StockEventReqDTO;
import com.geihou.module.supplychain.api.stock.dto.StockReleaseReqDTO;
import com.geihou.module.supplychain.api.stock.dto.StockReserveReqDTO;
import com.geihou.module.supplychain.api.stock.enums.StockDirectionEnum;
import com.geihou.module.supplychain.api.stock.enums.StockEventTypeEnum;
import com.geihou.module.supplychain.api.stock.enums.StockReserveStatusEnum;
import com.geihou.module.supplychain.stock.SupplychainTestConfig;
import com.geihou.module.supplychain.stock.SupplychainTestSchemaInitializer;
import com.geihou.module.supplychain.stock.dal.dataobject.StockBalanceDO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockEventDO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockReserveDO;
import com.geihou.module.supplychain.stock.dal.mapper.StockBalanceMapper;
import com.geihou.module.supplychain.stock.dal.mapper.StockEventMapper;
import com.geihou.module.supplychain.stock.dal.mapper.StockReserveMapper;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link StockReserveService}.
 *
 * <p>Covers: AC-5~AC-8, AC-10~AC-14 (reserve/release/commit, three-value invariant,
 * idempotency, rejections).
 */
@SpringBootTest(
        classes = SupplychainTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:stock_reserve_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class StockReserveServiceTest {

    @Autowired
    private StockReserveService stockReserveService;
    @Autowired
    private StockEventService stockEventService;
    @Autowired
    private StockBalanceService stockBalanceService;
    @Autowired
    private StockReserveMapper stockReserveMapper;
    @Autowired
    private StockEventMapper stockEventMapper;
    @Autowired
    private StockBalanceMapper stockBalanceMapper;
    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        SupplychainTestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(1L);
        // Purchase 100 units to have stock to reserve
        stockEventService.recordEvent(buildInReq(new BigDecimal("100"), "init-" + UUID.randomUUID()));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // === AC-5: reserve ===

    @Test
    void reserve_decreasesAvailableIncreasesReservedTotalUnchanged() {
        StockBalanceRespDTO before = stockBalanceService.getBalance(1L, 1001L, 10L);
        assertThat(before.getAvailableQty()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(before.getTotalQty()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(before.getReservedQty()).isEqualByComparingTo(BigDecimal.ZERO);

        Long reserveId = stockReserveService.reserveStock(buildReserveReq(new BigDecimal("30"), "res-" + UUID.randomUUID()));

        assertThat(reserveId).isNotNull();

        StockBalanceRespDTO after = stockBalanceService.getBalance(1L, 1001L, 10L);
        assertThat(after.getAvailableQty()).isEqualByComparingTo(new BigDecimal("70"));
        assertThat(after.getReservedQty()).isEqualByComparingTo(new BigDecimal("30"));
        assertThat(after.getTotalQty()).isEqualByComparingTo(new BigDecimal("100")); // unchanged
    }

    @Test
    void reserve_createsReserveRecordStatusReserved() {
        String idemKey = "res-" + UUID.randomUUID();
        Long reserveId = stockReserveService.reserveStock(buildReserveReq(new BigDecimal("20"), idemKey));

        StockReserveDO reserve = stockReserveMapper.selectById(reserveId);
        assertThat(reserve).isNotNull();
        assertThat(reserve.getStatus()).isEqualTo(StockReserveStatusEnum.RESERVED.getCode());
        assertThat(reserve.getQuantity()).isEqualByComparingTo(new BigDecimal("20"));
        assertThat(reserve.getCommitEventId()).isNull();
    }

    @Test
    void reserve_doesNotWriteStockEvent() {
        stockReserveService.reserveStock(buildReserveReq(new BigDecimal("10"), "res-" + UUID.randomUUID()));

        // Only the initial PURCHASE_IN event should exist
        var events = stockEventMapper.selectByTenantItemLocation(1L, 1001L, 10L);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo("PURCHASE_IN");
    }

    // === AC-6: release ===

    @Test
    void release_decreasesReservedIncreasesAvailableTotalUnchanged() {
        String idemKey = "res-" + UUID.randomUUID();
        stockReserveService.reserveStock(buildReserveReq(new BigDecimal("30"), idemKey));

        stockReserveService.releaseStock(buildReleaseReq(idemKey));

        StockBalanceRespDTO after = stockBalanceService.getBalance(1L, 1001L, 10L);
        assertThat(after.getAvailableQty()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(after.getReservedQty()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(after.getTotalQty()).isEqualByComparingTo(new BigDecimal("100")); // unchanged
    }

    @Test
    void release_setsStatusToReleased() {
        String idemKey = "res-" + UUID.randomUUID();
        Long reserveId = stockReserveService.reserveStock(buildReserveReq(new BigDecimal("20"), idemKey));

        stockReserveService.releaseStock(buildReleaseReq(idemKey));

        StockReserveDO reserve = stockReserveMapper.selectById(reserveId);
        assertThat(reserve.getStatus()).isEqualTo(StockReserveStatusEnum.RELEASED.getCode());
    }

    @Test
    void release_doesNotWriteStockEvent() {
        String idemKey = "res-" + UUID.randomUUID();
        stockReserveService.reserveStock(buildReserveReq(new BigDecimal("10"), idemKey));
        stockReserveService.releaseStock(buildReleaseReq(idemKey));

        var events = stockEventMapper.selectByTenantItemLocation(1L, 1001L, 10L);
        assertThat(events).hasSize(1); // only the initial PURCHASE_IN
    }

    // === AC-7: commit ===

    @Test
    void commit_decreasesReservedAndTotalAvailableUnchanged() {
        String idemKey = "res-" + UUID.randomUUID();
        stockReserveService.reserveStock(buildReserveReq(new BigDecimal("30"), idemKey));

        Long eventId = stockReserveService.commitStock(buildCommitReq(idemKey));

        assertThat(eventId).isNotNull();

        StockBalanceRespDTO after = stockBalanceService.getBalance(1L, 1001L, 10L);
        assertThat(after.getAvailableQty()).isEqualByComparingTo(new BigDecimal("70")); // unchanged from reserve
        assertThat(after.getReservedQty()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(after.getTotalQty()).isEqualByComparingTo(new BigDecimal("70")); // total decreased
    }

    @Test
    void commit_setsStatusToCommittedAndLinksEvent() {
        String idemKey = "res-" + UUID.randomUUID();
        Long reserveId = stockReserveService.reserveStock(buildReserveReq(new BigDecimal("20"), idemKey));

        Long eventId = stockReserveService.commitStock(buildCommitReq(idemKey));

        StockReserveDO reserve = stockReserveMapper.selectById(reserveId);
        assertThat(reserve.getStatus()).isEqualTo(StockReserveStatusEnum.COMMITTED.getCode());
        assertThat(reserve.getCommitEventId()).isEqualTo(eventId);
    }

    @Test
    void commit_writesConsumeOutEvent() {
        String idemKey = "res-" + UUID.randomUUID();
        stockReserveService.reserveStock(buildReserveReq(new BigDecimal("20"), idemKey));

        Long eventId = stockReserveService.commitStock(buildCommitReq(idemKey));

        StockEventDO event = stockEventMapper.selectById(eventId);
        assertThat(event).isNotNull();
        assertThat(event.getEventType()).isEqualTo(StockEventTypeEnum.CONSUME_OUT.getCode());
        assertThat(event.getDirection()).isEqualTo(StockDirectionEnum.OUT.getCode());
        assertThat(event.getQuantity()).isEqualByComparingTo(new BigDecimal("20"));
    }

    // === AC-8: three-value invariant ===

    @Test
    void threeValueInvariantHoldsAfterReserve() {
        stockReserveService.reserveStock(buildReserveReq(new BigDecimal("30"), "res-" + UUID.randomUUID()));

        StockBalanceRespDTO balance = stockBalanceService.getBalance(1L, 1001L, 10L);
        BigDecimal expectedAvailable = balance.getTotalQty().subtract(balance.getReservedQty());
        assertThat(balance.getAvailableQty()).isEqualByComparingTo(expectedAvailable);
    }

    @Test
    void threeValueInvariantHoldsAfterRelease() {
        String idemKey = "res-" + UUID.randomUUID();
        stockReserveService.reserveStock(buildReserveReq(new BigDecimal("30"), idemKey));
        stockReserveService.releaseStock(buildReleaseReq(idemKey));

        StockBalanceRespDTO balance = stockBalanceService.getBalance(1L, 1001L, 10L);
        BigDecimal expectedAvailable = balance.getTotalQty().subtract(balance.getReservedQty());
        assertThat(balance.getAvailableQty()).isEqualByComparingTo(expectedAvailable);
    }

    @Test
    void threeValueInvariantHoldsAfterCommit() {
        String idemKey = "res-" + UUID.randomUUID();
        stockReserveService.reserveStock(buildReserveReq(new BigDecimal("30"), idemKey));
        stockReserveService.commitStock(buildCommitReq(idemKey));

        StockBalanceRespDTO balance = stockBalanceService.getBalance(1L, 1001L, 10L);
        BigDecimal expectedAvailable = balance.getTotalQty().subtract(balance.getReservedQty());
        assertThat(balance.getAvailableQty()).isEqualByComparingTo(expectedAvailable);
    }

    // === AC-10: idempotent reserve ===

    @Test
    void reserve_idempotentSameKeyReturnsSameId() {
        String idemKey = "res-idem-" + UUID.randomUUID();
        StockReserveReqDTO req = buildReserveReq(new BigDecimal("20"), idemKey);

        Long id1 = stockReserveService.reserveStock(req);
        Long id2 = stockReserveService.reserveStock(req);

        assertThat(id1).isEqualTo(id2);

        // Balance should only reflect one reservation
        StockBalanceRespDTO balance = stockBalanceService.getBalance(1L, 1001L, 10L);
        assertThat(balance.getReservedQty()).isEqualByComparingTo(new BigDecimal("20"));
    }

    // === AC-11: idempotent release ===

    @Test
    void release_idempotentAlreadyReleasedReturnsSuccess() {
        String idemKey = "res-" + UUID.randomUUID();
        stockReserveService.reserveStock(buildReserveReq(new BigDecimal("20"), idemKey));

        stockReserveService.releaseStock(buildReleaseReq(idemKey));
        // Second release should not throw
        stockReserveService.releaseStock(buildReleaseReq(idemKey));

        StockBalanceRespDTO balance = stockBalanceService.getBalance(1L, 1001L, 10L);
        assertThat(balance.getReservedQty()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // === AC-12: idempotent commit ===

    @Test
    void commit_idempotentAlreadyCommittedReturnsSameEventId() {
        String idemKey = "res-" + UUID.randomUUID();
        stockReserveService.reserveStock(buildReserveReq(new BigDecimal("20"), idemKey));

        Long eventId1 = stockReserveService.commitStock(buildCommitReq(idemKey));
        Long eventId2 = stockReserveService.commitStock(buildCommitReq(idemKey));

        assertThat(eventId1).isEqualTo(eventId2);
    }

    // === AC-13: release COMMITTED → rejected ===

    @Test
    void release_committedThrowsAlreadyCommitted() {
        String idemKey = "res-" + UUID.randomUUID();
        stockReserveService.reserveStock(buildReserveReq(new BigDecimal("20"), idemKey));
        stockReserveService.commitStock(buildCommitReq(idemKey));

        assertThatThrownBy(() -> stockReserveService.releaseStock(buildReleaseReq(idemKey)))
                .isInstanceOf(StockBusinessException.class);
    }

    // === AC-14: commit RELEASED → rejected ===

    @Test
    void commit_releasedThrowsAlreadyReleased() {
        String idemKey = "res-" + UUID.randomUUID();
        stockReserveService.reserveStock(buildReserveReq(new BigDecimal("20"), idemKey));
        stockReserveService.releaseStock(buildReleaseReq(idemKey));

        assertThatThrownBy(() -> stockReserveService.commitStock(buildCommitReq(idemKey)))
                .isInstanceOf(StockBusinessException.class);
    }

    // === Validation tests ===

    @Test
    void reserve_insufficientAvailableThrows() {
        // available is 100, try to reserve 200
        assertThatThrownBy(() -> stockReserveService.reserveStock(buildReserveReq(new BigDecimal("200"), "res-" + UUID.randomUUID())))
                .isInstanceOf(StockBusinessException.class);
    }

    @Test
    void reserve_noBalanceThrows() {
        // No balance for item 9999
        StockReserveReqDTO req = buildReserveReq(new BigDecimal("10"), "res-" + UUID.randomUUID());
        req.setStockItemId(9999L);
        assertThatThrownBy(() -> stockReserveService.reserveStock(req))
                .isInstanceOf(StockBusinessException.class);
    }

    @Test
    void reserve_nullTenantIdThrows() {
        StockReserveReqDTO req = buildReserveReq(new BigDecimal("10"), "res-" + UUID.randomUUID());
        req.setTenantId(null);
        assertThatThrownBy(() -> stockReserveService.reserveStock(req))
                .isInstanceOf(StockBusinessException.class);
    }

    @Test
    void reserve_nullIdempotentKeyThrows() {
        StockReserveReqDTO req = buildReserveReq(new BigDecimal("10"), "res-" + UUID.randomUUID());
        req.setIdempotentKey(null);
        assertThatThrownBy(() -> stockReserveService.reserveStock(req))
                .isInstanceOf(StockBusinessException.class);
    }

    @Test
    void release_notFoundThrows() {
        assertThatThrownBy(() -> stockReserveService.releaseStock(buildReleaseReq("nonexistent-key")))
                .isInstanceOf(StockBusinessException.class);
    }

    @Test
    void commit_notFoundThrows() {
        assertThatThrownBy(() -> stockReserveService.commitStock(buildCommitReq("nonexistent-key")))
                .isInstanceOf(StockBusinessException.class);
    }

    // === G2-01B1 FIX: last_event_id points to CONSUME_OUT after commit ===

    @Test
    void commit_updatesBalanceLastEventIdToConsumeOutEvent() {
        String idemKey = "res-" + UUID.randomUUID();
        stockReserveService.reserveStock(buildReserveReq(new BigDecimal("20"), idemKey));

        Long eventId = stockReserveService.commitStock(buildCommitReq(idemKey));

        // Reload balance from DB
        StockBalanceDO balance = stockBalanceMapper.selectByTenantItemLocation(1L, 1001L, 10L);
        assertThat(balance).isNotNull();
        assertThat(balance.getLastEventId()).isEqualTo(eventId);

        // Verify the event is indeed CONSUME_OUT
        StockEventDO event = stockEventMapper.selectById(eventId);
        assertThat(event).isNotNull();
        assertThat(event.getEventType()).isEqualTo(StockEventTypeEnum.CONSUME_OUT.getCode());
    }

    // === G2-01B1 FIX: reserveId secondary validation ===

    @Test
    void release_reserveIdMismatchThrows() {
        String idemKey = "res-" + UUID.randomUUID();
        stockReserveService.reserveStock(buildReserveReq(new BigDecimal("20"), idemKey));

        StockReleaseReqDTO req = buildReleaseReq(idemKey);
        req.setReserveId(99999L); // wrong reserveId

        assertThatThrownBy(() -> stockReserveService.releaseStock(req))
                .isInstanceOf(StockBusinessException.class);
    }

    @Test
    void release_reserveIdMatchSucceeds() {
        String idemKey = "res-" + UUID.randomUUID();
        Long reserveId = stockReserveService.reserveStock(buildReserveReq(new BigDecimal("20"), idemKey));

        StockReleaseReqDTO req = buildReleaseReq(idemKey);
        req.setReserveId(reserveId); // correct reserveId

        stockReserveService.releaseStock(req);

        StockBalanceRespDTO after = stockBalanceService.getBalance(1L, 1001L, 10L);
        assertThat(after.getReservedQty()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void commit_reserveIdMismatchThrows() {
        String idemKey = "res-" + UUID.randomUUID();
        stockReserveService.reserveStock(buildReserveReq(new BigDecimal("20"), idemKey));

        StockCommitReqDTO req = buildCommitReq(idemKey);
        req.setReserveId(99999L); // wrong reserveId

        assertThatThrownBy(() -> stockReserveService.commitStock(req))
                .isInstanceOf(StockBusinessException.class);
    }

    @Test
    void commit_reserveIdMatchSucceeds() {
        String idemKey = "res-" + UUID.randomUUID();
        Long reserveId = stockReserveService.reserveStock(buildReserveReq(new BigDecimal("20"), idemKey));

        StockCommitReqDTO req = buildCommitReq(idemKey);
        req.setReserveId(reserveId); // correct reserveId

        Long eventId = stockReserveService.commitStock(req);
        assertThat(eventId).isNotNull();
    }

    @Test
    void release_reserveIdNullSkipsValidation() {
        // When reserveId is null, no validation is performed (backwards compatible)
        String idemKey = "res-" + UUID.randomUUID();
        stockReserveService.reserveStock(buildReserveReq(new BigDecimal("20"), idemKey));

        StockReleaseReqDTO req = buildReleaseReq(idemKey);
        req.setReserveId(null); // null = skip check

        stockReserveService.releaseStock(req); // should not throw

        StockBalanceRespDTO after = stockBalanceService.getBalance(1L, 1001L, 10L);
        assertThat(after.getReservedQty()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // === G2-01B1 FIX: concurrent double release ===

    @Test
    void concurrentDoubleRelease_onlyOneSucceedsBalanceNotDoubleReleased() throws InterruptedException {
        String idemKey = "res-dblrelease-" + UUID.randomUUID();
        stockReserveService.reserveStock(buildReserveReq(new BigDecimal("30"), idemKey));

        int threadCount = 2;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    TenantContextHolder.setTenantId(1L);
                    stockReserveService.releaseStock(buildReleaseReq(idemKey));
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
        endLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // At least one must succeed; the other is idempotent (returns without error) or fails
        assertThat(successCount.get()).isGreaterThanOrEqualTo(1);

        // Verify balance is not double-released
        StockBalanceRespDTO after = stockBalanceService.getBalance(1L, 1001L, 10L);
        assertThat(after.getReservedQty()).isEqualByComparingTo(BigDecimal.ZERO); // not negative
        assertThat(after.getAvailableQty()).isEqualByComparingTo(new BigDecimal("100")); // not over total
        assertThat(after.getTotalQty()).isEqualByComparingTo(new BigDecimal("100")); // unchanged
    }

    // === G2-01B1 FIX: concurrent double commit ===

    @Test
    void concurrentDoubleCommit_onlyOneConsumeOutEventBalanceDeductedOnce() throws InterruptedException {
        String idemKey = "res-dblcommit-" + UUID.randomUUID();
        stockReserveService.reserveStock(buildReserveReq(new BigDecimal("30"), idemKey));

        int threadCount = 2;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    TenantContextHolder.setTenantId(1L);
                    stockReserveService.commitStock(buildCommitReq(idemKey));
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
        endLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // At least one must succeed
        assertThat(successCount.get()).isGreaterThanOrEqualTo(1);

        // Verify only one CONSUME_OUT event exists
        var events = stockEventMapper.selectByTenantItemLocation(1L, 1001L, 10L);
        long consumeOutCount = events.stream()
                .filter(e -> StockEventTypeEnum.CONSUME_OUT.getCode().equals(e.getEventType()))
                .count();
        assertThat(consumeOutCount).isEqualTo(1);

        // Verify balance was deducted only once
        StockBalanceRespDTO after = stockBalanceService.getBalance(1L, 1001L, 10L);
        assertThat(after.getTotalQty()).isEqualByComparingTo(new BigDecimal("70")); // 100 - 30 = 70
        assertThat(after.getReservedQty()).isEqualByComparingTo(BigDecimal.ZERO); // not negative
        assertThat(after.getAvailableQty()).isEqualByComparingTo(new BigDecimal("70")); // available = total - reserved

        // Verify reserve status is COMMITTED
        StockReserveDO reserve = stockReserveMapper.selectByTenantIdempotentKey(1L, idemKey);
        assertThat(reserve).isNotNull();
        assertThat(reserve.getStatus()).isEqualTo(StockReserveStatusEnum.COMMITTED.getCode());
        assertThat(reserve.getCommitEventId()).isNotNull();
    }

    // === Helper methods ===

    private StockEventReqDTO buildInReq(BigDecimal quantity, String clientRequestId) {
        StockEventReqDTO req = new StockEventReqDTO();
        req.setTenantId(1L);
        req.setEventTime(LocalDateTime.now());
        req.setBusinessDate(LocalDate.now());
        req.setEventType(StockEventTypeEnum.PURCHASE_IN.getCode());
        req.setDirection(StockDirectionEnum.IN.getCode());
        req.setStockItemId(1001L);
        req.setSkuCode("SKU_TEST");
        req.setLocationId(10L);
        req.setQuantity(quantity);
        req.setUnit("个");
        req.setOperatorUserId(999L);
        req.setClientRequestId(clientRequestId);
        return req;
    }

    private StockReserveReqDTO buildReserveReq(BigDecimal quantity, String idempotentKey) {
        StockReserveReqDTO req = new StockReserveReqDTO();
        req.setTenantId(1L);
        req.setStockItemId(1001L);
        req.setLocationId(10L);
        req.setSkuCode("SKU_TEST");
        req.setQuantity(quantity);
        req.setUnit("个");
        req.setSourceModule("checkout");
        req.setSourceRecordId(100L);
        req.setReferenceNo("ORDER-001");
        req.setIdempotentKey(idempotentKey);
        req.setOperatorUserId(999L);
        return req;
    }

    private StockReleaseReqDTO buildReleaseReq(String idempotentKey) {
        StockReleaseReqDTO req = new StockReleaseReqDTO();
        req.setTenantId(1L);
        req.setIdempotentKey(idempotentKey);
        req.setOperatorUserId(999L);
        return req;
    }

    private StockCommitReqDTO buildCommitReq(String idempotentKey) {
        StockCommitReqDTO req = new StockCommitReqDTO();
        req.setTenantId(1L);
        req.setIdempotentKey(idempotentKey);
        req.setOperatorUserId(999L);
        req.setEventTime(LocalDateTime.now());
        req.setBusinessDate(LocalDate.now());
        return req;
    }
}
