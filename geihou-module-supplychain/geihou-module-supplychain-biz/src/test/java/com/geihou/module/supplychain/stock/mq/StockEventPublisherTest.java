package com.geihou.module.supplychain.stock.mq;

import com.geihou.module.supplychain.stock.mq.event.BomVersionChangedEvent;
import com.geihou.module.supplychain.stock.mq.event.StockExpireWarningEvent;
import com.geihou.module.supplychain.stock.mq.event.StockLowWarningEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * StockEventPublisher after-commit semantics test.
 *
 * <p>Tests:
 * <ul>
 *   <li>Event is published AFTER transaction commits (not during)</li>
 *   <li>Event is NOT published when transaction rolls back</li>
 *   <li>Event is published synchronously when no transaction is active (with warn)</li>
 *   <li>StockExpireWarningEvent can be published and payload preserved</li>
 *   <li>BomVersionChangedEvent can be published and payload preserved</li>
 *   <li>Listener exception does not propagate out of publisher</li>
 * </ul>
 */
@SpringBootTest(
        classes = {StockEventPublisherTest.TestConfig.class},
        properties = {
                "spring.datasource.url=jdbc:h2:mem:stock_event_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class StockEventPublisherTest {

    @Configuration
    @EnableAutoConfiguration
    @ComponentScan("com.geihou.module.supplychain.stock.mq")
    static class TestConfig {
    }

    @Component
    static class TestEventListener {
        final AtomicReference<Object> lastEvent = new AtomicReference<>();
        final AtomicInteger eventCount = new AtomicInteger(0);

        @EventListener
        void onStockLowWarning(StockLowWarningEvent event) {
            lastEvent.set(event);
            eventCount.incrementAndGet();
        }

        @EventListener
        void onStockExpireWarning(StockExpireWarningEvent event) {
            lastEvent.set(event);
            eventCount.incrementAndGet();
        }

        @EventListener
        void onBomVersionChanged(BomVersionChangedEvent event) {
            lastEvent.set(event);
            eventCount.incrementAndGet();
        }

        void reset() {
            lastEvent.set(null);
            eventCount.set(0);
        }
    }

    /**
     * A listener that throws when enabled, to verify the publisher swallows
     * listener exceptions (both in afterCommit and synchronous paths).
     */
    @Component
    static class ThrowingEventListener {
        final AtomicBoolean throwEnabled = new AtomicBoolean(false);

        @EventListener
        void onStockLowWarning(StockLowWarningEvent event) {
            if (throwEnabled.get()) {
                throw new RuntimeException("Simulated listener failure");
            }
        }

        void reset() {
            throwEnabled.set(false);
        }
    }

    @Autowired
    private StockEventPublisher stockEventPublisher;

    @Autowired
    private TestEventListener eventListener;

    @Autowired
    private ThrowingEventListener throwingEventListener;

    @Autowired
    private TransactionalHelper transactionalHelper;

    @BeforeEach
    void setUp() {
        eventListener.reset();
        throwingEventListener.reset();
    }

    @AfterEach
    void tearDown() {
        eventListener.reset();
        throwingEventListener.reset();
    }

    @Test
    void eventPublishedAfterCommit() {
        transactionalHelper.runInTransaction(() -> {
            StockLowWarningEvent event = new StockLowWarningEvent();
            event.setTenantId(1L);
            event.setSourceModule("STOCK");
            event.setSourceRecordId(10L);
            event.setReferenceNo("OUT-2024-0001");
            event.setStockItemId(100L);
            event.setLocationId(200L);
            event.setProductId(300L);
            event.setQuantity(new BigDecimal("5.00"));
            event.setThresholdQuantity(new BigDecimal("10.00"));
            event.setWarningLevel("LOW");
            event.setEventTime(LocalDateTime.now());

            stockEventPublisher.publishStockLowWarning(event);

            // Not published yet (still inside transaction)
            assertThat(eventListener.eventCount.get()).isEqualTo(0);
        });

        // After commit, event should be published
        assertThat(eventListener.eventCount.get()).isEqualTo(1);
        assertThat(eventListener.lastEvent.get()).isInstanceOf(StockLowWarningEvent.class);

        StockLowWarningEvent received = (StockLowWarningEvent) eventListener.lastEvent.get();
        assertThat(received.getStockItemId()).isEqualTo(100L);
        assertThat(received.getQuantity()).isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(received.getThresholdQuantity()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(received.getWarningLevel()).isEqualTo("LOW");
        assertThat(received.getReferenceNo()).isEqualTo("OUT-2024-0001");
    }

    @Test
    void eventNotPublishedOnRollback() {
        assertThatThrownBy(() -> transactionalHelper.runInTransactionWithRollback(() -> {
            StockLowWarningEvent event = new StockLowWarningEvent();
            event.setTenantId(1L);
            event.setStockItemId(101L);
            event.setLocationId(201L);
            event.setProductId(301L);
            event.setQuantity(new BigDecimal("3.00"));
            event.setThresholdQuantity(new BigDecimal("8.00"));
            event.setWarningLevel("CRITICAL");
            event.setEventTime(LocalDateTime.now());

            stockEventPublisher.publishStockLowWarning(event);

            // Not published yet
            assertThat(eventListener.eventCount.get()).isEqualTo(0);
        })).isInstanceOf(RuntimeException.class);

        // After rollback, event should NOT be published
        assertThat(eventListener.eventCount.get()).isEqualTo(0);
    }

    @Test
    void eventPublishedSynchronouslyWithoutTransaction() {
        StockLowWarningEvent event = new StockLowWarningEvent();
        event.setTenantId(1L);
        event.setStockItemId(102L);
        event.setLocationId(202L);
        event.setProductId(302L);
        event.setQuantity(new BigDecimal("2.00"));
        event.setThresholdQuantity(new BigDecimal("6.00"));
        event.setWarningLevel("LOW");
        event.setEventTime(LocalDateTime.now());

        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();

        stockEventPublisher.publishStockLowWarning(event);

        // Should be published immediately
        assertThat(eventListener.eventCount.get()).isEqualTo(1);
        StockLowWarningEvent received = (StockLowWarningEvent) eventListener.lastEvent.get();
        assertThat(received.getStockItemId()).isEqualTo(102L);
        assertThat(received.getQuantity()).isEqualByComparingTo(new BigDecimal("2.00"));
        assertThat(received.getWarningLevel()).isEqualTo("LOW");
    }

    @Test
    void expireReservedEventCanPublish() {
        transactionalHelper.runInTransaction(() -> {
            StockExpireWarningEvent event = new StockExpireWarningEvent();
            event.setTenantId(1L);
            event.setSourceModule("STOCK");
            event.setSourceRecordId(20L);
            event.setReferenceNo("BATCH-2024-0001");
            event.setStockItemId(110L);
            event.setLocationId(210L);
            event.setProductId(310L);
            event.setBatchNo("BN-001");
            event.setExpireDate(LocalDate.of(2024, 12, 31));
            event.setDaysUntilExpire(7);
            event.setQuantity(new BigDecimal("50.00"));
            event.setEventTime(LocalDateTime.now());

            stockEventPublisher.publishStockExpireWarning(event);
        });

        assertThat(eventListener.eventCount.get()).isEqualTo(1);
        assertThat(eventListener.lastEvent.get()).isInstanceOf(StockExpireWarningEvent.class);

        StockExpireWarningEvent received = (StockExpireWarningEvent) eventListener.lastEvent.get();
        assertThat(received.getStockItemId()).isEqualTo(110L);
        assertThat(received.getBatchNo()).isEqualTo("BN-001");
        assertThat(received.getExpireDate()).isEqualTo(LocalDate.of(2024, 12, 31));
        assertThat(received.getDaysUntilExpire()).isEqualTo(7);
        assertThat(received.getQuantity()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    void bomVersionChangedEventCanPublish() {
        transactionalHelper.runInTransaction(() -> {
            BomVersionChangedEvent event = new BomVersionChangedEvent();
            event.setTenantId(1L);
            event.setRecipeId(500L);
            event.setProductId(600L);
            event.setRecipeVersion(3);
            event.setStatus("ACTIVE");
            event.setEventTime(LocalDateTime.now());

            stockEventPublisher.publishBomVersionChanged(event);
        });

        assertThat(eventListener.eventCount.get()).isEqualTo(1);
        assertThat(eventListener.lastEvent.get()).isInstanceOf(BomVersionChangedEvent.class);

        BomVersionChangedEvent received = (BomVersionChangedEvent) eventListener.lastEvent.get();
        assertThat(received.getRecipeId()).isEqualTo(500L);
        assertThat(received.getProductId()).isEqualTo(600L);
        assertThat(received.getRecipeVersion()).isEqualTo(3);
        assertThat(received.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void listenerExceptionDoesNotPropagate() {
        throwingEventListener.throwEnabled.set(true);

        StockLowWarningEvent event = new StockLowWarningEvent();
        event.setTenantId(1L);
        event.setStockItemId(103L);
        event.setLocationId(203L);
        event.setProductId(303L);
        event.setQuantity(new BigDecimal("1.00"));
        event.setThresholdQuantity(new BigDecimal("4.00"));
        event.setWarningLevel("LOW");
        event.setEventTime(LocalDateTime.now());

        // No transaction active -> synchronous path; publisher catches listener exception
        assertThatCode(() -> stockEventPublisher.publishStockLowWarning(event))
                .doesNotThrowAnyException();

        // Also verify afterCommit path swallows listener exception
        eventListener.reset();
        throwingEventListener.throwEnabled.set(true);

        assertThatCode(() -> transactionalHelper.runInTransaction(() -> {
            StockLowWarningEvent event2 = new StockLowWarningEvent();
            event2.setTenantId(1L);
            event2.setStockItemId(104L);
            event2.setLocationId(204L);
            event2.setProductId(304L);
            event2.setQuantity(new BigDecimal("0.50"));
            event2.setThresholdQuantity(new BigDecimal("3.00"));
            event2.setWarningLevel("CRITICAL");
            event2.setEventTime(LocalDateTime.now());

            stockEventPublisher.publishStockLowWarning(event2);
        })).doesNotThrowAnyException();
    }

    /**
     * Helper to run code inside a transaction.
     */
    @Component
    static class TransactionalHelper {

        @Transactional(rollbackFor = Exception.class)
        public void runInTransaction(Runnable action) {
            action.run();
        }

        @Transactional(rollbackFor = Exception.class)
        public void runInTransactionWithRollback(Runnable action) {
            action.run();
            throw new RuntimeException("Intentional rollback for test");
        }
    }
}
