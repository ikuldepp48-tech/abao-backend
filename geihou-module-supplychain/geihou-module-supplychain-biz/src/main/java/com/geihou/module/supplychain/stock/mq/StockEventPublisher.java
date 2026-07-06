package com.geihou.module.supplychain.stock.mq;

import com.geihou.module.supplychain.stock.mq.event.StockExpireWarningEvent;
import com.geihou.module.supplychain.stock.mq.event.StockLowWarningEvent;
import com.geihou.module.supplychain.stock.mq.event.BomVersionChangedEvent;

/**
 * Supplychain event publisher abstraction for MQ event dispatch.
 *
 * <p>This is a transitional in-process abstraction. The current implementation
 * uses Spring {@code ApplicationEventPublisher} with {@code TransactionSynchronization.afterCommit()}
 * to ensure events are only published after the transaction commits.
 *
 * <p>When real MQ infrastructure (geihou-spring-boot-starter-mq) is available,
 * only the implementation class ({@code StockEventPublisherImpl}) needs to be
 * replaced — the interface and event classes remain unchanged.
 *
 * <p>Callers (StockEventServiceImpl, BomRecipeServiceImpl, etc.) must use this
 * interface, NOT {@code ApplicationEventPublisher.publishEvent()} directly,
 * to enforce after-commit semantics and prevent fire-and-forget.
 *
 * <p>Topics:
 * <ul>
 *   <li>{@code stock.low.warning} — published when stock falls below warning threshold</li>
 *   <li>{@code stock.expire.warning} — reserved for batch expiry warning (not yet business-triggered)</li>
 *   <li>{@code bom.version.changed} — published when BOM recipe is activated</li>
 * </ul>
 */
public interface StockEventPublisher {

    /**
     * Publish a {@code stock.low.warning} event after the current transaction commits.
     * If no transaction is active, publishes synchronously with a warning log.
     *
     * @param event the stock low warning event (constructed within the transaction)
     */
    void publishStockLowWarning(StockLowWarningEvent event);

    /**
     * Publish a {@code stock.expire.warning} event after the current transaction commits.
     * If no transaction is active, publishes synchronously with a warning log.
     *
     * <p><b>Reserved:</b> This method and event class are provided for future batch-expiry
     * scanning. As of G2-02O, no business code triggers this event because the current
     * schema lacks a dedicated batch/expiry scan entry point. Do not claim business
     * triggering until a verified expiry field and scan entry exist.
     *
     * @param event the stock expire warning event (constructed within the transaction)
     */
    void publishStockExpireWarning(StockExpireWarningEvent event);

    /**
     * Publish a {@code bom.version.changed} event after the current transaction commits.
     * If no transaction is active, publishes synchronously with a warning log.
     *
     * @param event the BOM version changed event (constructed within the transaction)
     */
    void publishBomVersionChanged(BomVersionChangedEvent event);
}
