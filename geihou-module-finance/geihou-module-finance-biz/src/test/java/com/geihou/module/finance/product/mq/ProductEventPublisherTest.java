package com.geihou.module.finance.product.mq;

import com.geihou.module.finance.product.mq.event.ProductPriceChangedEvent;
import com.geihou.module.finance.product.mq.event.ProductStatusChangedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ProductEventPublisher after-commit semantics test.
 *
 * <p>Tests:
 * <ul>
 *   <li>Event is published AFTER transaction commits (not during)</li>
 *   <li>Event is NOT published when transaction rolls back</li>
 *   <li>Event is published synchronously when no transaction is active (with warn)</li>
 *   <li>Event fields are preserved (BigDecimal, Long, String)</li>
 * </ul>
 */
@SpringBootTest(
        classes = {ProductEventPublisherTest.TestConfig.class},
        properties = {
                "spring.datasource.url=jdbc:h2:mem:event_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class ProductEventPublisherTest {

    @Configuration
    @EnableAutoConfiguration
    @ComponentScan("com.geihou.module.finance.product.mq")
    static class TestConfig {
    }

    @Component
    static class TestEventListener {
        final AtomicReference<Object> lastEvent = new AtomicReference<>();
        final AtomicInteger eventCount = new AtomicInteger(0);

        @EventListener
        void onPriceChanged(ProductPriceChangedEvent event) {
            lastEvent.set(event);
            eventCount.incrementAndGet();
        }

        @EventListener
        void onStatusChanged(ProductStatusChangedEvent event) {
            lastEvent.set(event);
            eventCount.incrementAndGet();
        }

        void reset() {
            lastEvent.set(null);
            eventCount.set(0);
        }
    }

    @Autowired
    private ProductEventPublisher productEventPublisher;

    @Autowired
    private TestEventListener eventListener;

    @Autowired
    private TransactionalHelper transactionalHelper;

    @BeforeEach
    void setUp() {
        eventListener.reset();
    }

    @AfterEach
    void tearDown() {
        eventListener.reset();
    }

    @Test
    void eventPublishedAfterCommit() {
        transactionalHelper.runInTransaction(() -> {
            // Event should NOT be published yet (we're inside the transaction)
            ProductPriceChangedEvent event = new ProductPriceChangedEvent();
            event.setTenantId(1L);
            event.setSkuId(100L);
            event.setSpuId(200L);
            event.setOldSellingPrice(new BigDecimal("18.00"));
            event.setNewSellingPrice(new BigDecimal("19.00"));
            event.setChangeType("MANUAL");
            event.setChangeReason("Market adjustment");
            event.setChangedByUserId(300L);
            event.setChangeTime(LocalDateTime.now());

            productEventPublisher.publishPriceChanged(event);

            // Verify event NOT yet published (still inside transaction)
            assertThat(eventListener.eventCount.get()).isEqualTo(0);
        });

        // After commit, event should be published
        assertThat(eventListener.eventCount.get()).isEqualTo(1);
        assertThat(eventListener.lastEvent.get()).isInstanceOf(ProductPriceChangedEvent.class);

        ProductPriceChangedEvent received = (ProductPriceChangedEvent) eventListener.lastEvent.get();
        assertThat(received.getSkuId()).isEqualTo(100L);
        assertThat(received.getNewSellingPrice()).isEqualByComparingTo(new BigDecimal("19.00"));
        assertThat(received.getChangeType()).isEqualTo("MANUAL");
    }

    @Test
    void eventNotPublishedOnRollback() {
        // The rollback helper throws RuntimeException to trigger rollback.
        // We catch it to verify the event was not published.
        assertThatThrownBy(() -> transactionalHelper.runInTransactionWithRollback(() -> {
            ProductStatusChangedEvent event = new ProductStatusChangedEvent();
            event.setTenantId(1L);
            event.setTargetType("SKU");
            event.setTargetId(100L);
            event.setOldStatus("ACTIVE");
            event.setNewStatus("PAUSED");
            event.setChangeReason("Temporary outage");
            event.setChangedByUserId(300L);
            event.setChangeTime(LocalDateTime.now());

            productEventPublisher.publishStatusChanged(event);

            // Not published yet
            assertThat(eventListener.eventCount.get()).isEqualTo(0);
        })).isInstanceOf(RuntimeException.class);

        // After rollback, event should NOT be published
        assertThat(eventListener.eventCount.get()).isEqualTo(0);
    }

    @Test
    void eventPublishedSynchronouslyWithoutTransaction() {
        // No transaction active — should publish synchronously
        ProductPriceChangedEvent event = new ProductPriceChangedEvent();
        event.setTenantId(1L);
        event.setSkuId(101L);
        event.setSpuId(201L);
        event.setOldSellingPrice(new BigDecimal("10.00"));
        event.setNewSellingPrice(new BigDecimal("11.00"));
        event.setChangeType("COST_BASED");
        event.setChangeReason("Cost increased");
        event.setChangedByUserId(301L);
        event.setChangeTime(LocalDateTime.now());

        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();

        productEventPublisher.publishPriceChanged(event);

        // Should be published immediately
        assertThat(eventListener.eventCount.get()).isEqualTo(1);
        ProductPriceChangedEvent received = (ProductPriceChangedEvent) eventListener.lastEvent.get();
        assertThat(received.getSkuId()).isEqualTo(101L);
        assertThat(received.getNewSellingPrice()).isEqualByComparingTo(new BigDecimal("11.00"));
        assertThat(received.getChangeType()).isEqualTo("COST_BASED");
    }

    @Test
    void eventFieldsPreservedAsBigDecimal() {
        transactionalHelper.runInTransaction(() -> {
            ProductPriceChangedEvent event = new ProductPriceChangedEvent();
            event.setTenantId(1L);
            event.setSkuId(102L);
            event.setSpuId(202L);
            event.setOldSellingPrice(new BigDecimal("18.0000"));
            event.setNewSellingPrice(new BigDecimal("19.5000"));
            event.setOldListPrice(new BigDecimal("20.0000"));
            event.setNewListPrice(new BigDecimal("22.0000"));
            event.setChangeType("MARKET_BASED");
            event.setChangeReason("Market price adjustment");
            event.setChangedByUserId(302L);
            event.setChangeTime(LocalDateTime.now());

            productEventPublisher.publishPriceChanged(event);
        });

        ProductPriceChangedEvent received = (ProductPriceChangedEvent) eventListener.lastEvent.get();
        assertThat(received.getOldSellingPrice()).isInstanceOf(BigDecimal.class);
        assertThat(received.getNewSellingPrice()).isInstanceOf(BigDecimal.class);
        assertThat(received.getOldListPrice()).isInstanceOf(BigDecimal.class);
        assertThat(received.getNewListPrice()).isInstanceOf(BigDecimal.class);
        assertThat(received.getNewSellingPrice()).isEqualByComparingTo(new BigDecimal("19.5000"));
    }

    @Test
    void statusChangedEventFieldsPreserved() {
        transactionalHelper.runInTransaction(() -> {
            ProductStatusChangedEvent event = new ProductStatusChangedEvent();
            event.setTenantId(1L);
            event.setTargetType("COMBO");
            event.setTargetId(999L);
            event.setOldStatus("ACTIVE");
            event.setNewStatus("PAUSED");
            event.setChangeReason("SKU in combo is paused");
            event.setChangedByUserId(303L);
            event.setChangeTime(LocalDateTime.now());

            productEventPublisher.publishStatusChanged(event);
        });

        ProductStatusChangedEvent received = (ProductStatusChangedEvent) eventListener.lastEvent.get();
        assertThat(received.getTargetType()).isEqualTo("COMBO");
        assertThat(received.getTargetId()).isEqualTo(999L);
        assertThat(received.getOldStatus()).isEqualTo("ACTIVE");
        assertThat(received.getNewStatus()).isEqualTo("PAUSED");
        assertThat(received.getChangeReason()).isEqualTo("SKU in combo is paused");
    }

    /**
     * Helper to run code inside a transaction.
     */
    @org.springframework.stereotype.Component
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
