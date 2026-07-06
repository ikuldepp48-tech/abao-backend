package com.geihou.module.finance.product.mq;

import com.geihou.module.finance.product.mq.event.ProductPriceChangedEvent;
import com.geihou.module.finance.product.mq.event.ProductStatusChangedEvent;

/**
 * Product event publisher abstraction for MQ event dispatch.
 *
 * <p>This is a transitional in-process abstraction. The current implementation
 * uses Spring {@code ApplicationEventPublisher} with {@code TransactionSynchronization.afterCommit()}
 * to ensure events are only published after the transaction commits.
 *
 * <p>When real MQ infrastructure (geihou-spring-boot-starter-mq) is available,
 * only the implementation class ({@code ProductEventPublisherImpl}) needs to be
 * replaced — the interface and event classes remain unchanged.
 *
 * <p>Callers (PriceService, SpuService, SkuService, ComboService) must use this
 * interface, NOT {@code ApplicationEventPublisher.publishEvent()} directly,
 * to enforce after-commit semantics and prevent fire-and-forget.
 */
public interface ProductEventPublisher {

    /**
     * Publish a product.price.changed event after the current transaction commits.
     * If no transaction is active, publishes synchronously with a warning log.
     *
     * @param event the price changed event (constructed within the transaction)
     */
    void publishPriceChanged(ProductPriceChangedEvent event);

    /**
     * Publish a product.status.changed event after the current transaction commits.
     * If no transaction is active, publishes synchronously with a warning log.
     *
     * @param event the status changed event (constructed within the transaction)
     */
    void publishStatusChanged(ProductStatusChangedEvent event);
}
