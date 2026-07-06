package com.geihou.module.finance.product.mq;

import com.geihou.module.finance.product.mq.event.ProductPriceChangedEvent;
import com.geihou.module.finance.product.mq.event.ProductStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Implementation of {@link ProductEventPublisher} using Spring {@code ApplicationEventPublisher}
 * with {@code TransactionSynchronization.afterCommit()} to guarantee events are dispatched
 * only after the transaction successfully commits.
 *
 * <p><b>Transitional in-process MQ abstraction.</b> This implementation publishes
 * Spring ApplicationEvents which are process-local. When real cross-process MQ
 * infrastructure (geihou-spring-boot-starter-mq) is ready, this class will be
 * replaced with a real MQ producer implementation. The interface and event
 * classes remain unchanged.
 *
 * <p><b>After-commit semantics:</b>
 * <ul>
 *   <li>If a transaction is active: registers a {@link TransactionSynchronization}
 *       and publishes the event in {@code afterCommit()}. This means:
 *       <ul>
 *         <li>If the transaction commits → event is published</li>
 *         <li>If the transaction rolls back → event is NOT published</li>
 *       </ul>
 *   </li>
 *   <li>If no transaction is active: publishes synchronously immediately
 *       and logs a warning (this should be rare in production).</li>
 * </ul>
 *
 * <p><b>Failure handling in afterCommit:</b> Exceptions thrown in afterCommit
 * are caught and logged as errors — they do NOT roll back the already-committed
 * transaction. Event loss risk is mitigated by future real MQ retry mechanisms.
 */
@Component
public class ProductEventPublisherImpl implements ProductEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ProductEventPublisherImpl.class);

    private final ApplicationEventPublisher applicationEventPublisher;

    public ProductEventPublisherImpl(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publishPriceChanged(ProductPriceChangedEvent event) {
        publishAfterCommit(event, "product.price.changed");
    }

    @Override
    public void publishStatusChanged(ProductStatusChangedEvent event) {
        publishAfterCommit(event, "product.status.changed");
    }

    /**
     * Core dispatch logic: if a transaction is active, register afterCommit callback;
     * otherwise publish synchronously with a warning.
     */
    private void publishAfterCommit(Object event, String topic) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        applicationEventPublisher.publishEvent(event);
                        log.debug("Published {} event after commit: {}", topic, event);
                    } catch (Exception e) {
                        // afterCommit exceptions cannot roll back the transaction.
                        // Log the error; event loss risk is handled by future real MQ retry.
                        log.error("Failed to publish {} event after commit: {}", topic, e.getMessage(), e);
                    }
                }
            });
        } else {
            log.warn("No active transaction, event published synchronously: {}", topic);
            try {
                applicationEventPublisher.publishEvent(event);
            } catch (Exception e) {
                log.error("Failed to publish {} event synchronously: {}", topic, e.getMessage(), e);
            }
        }
    }
}
