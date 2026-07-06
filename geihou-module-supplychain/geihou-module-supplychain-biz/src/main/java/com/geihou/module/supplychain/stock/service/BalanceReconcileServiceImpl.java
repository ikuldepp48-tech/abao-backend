package com.geihou.module.supplychain.stock.service;

import com.geihou.module.supplychain.api.stock.dto.ReconcileItemDTO;
import com.geihou.module.supplychain.api.stock.dto.ReconcileReportRespDTO;
import com.geihou.module.supplychain.api.stock.enums.StockDirectionEnum;
import com.geihou.module.supplychain.api.stock.enums.StockEventTypeEnum;
import com.geihou.module.supplychain.stock.dal.dataobject.StockBalanceDO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockEventDO;
import com.geihou.module.supplychain.stock.dal.mapper.StockBalanceMapper;
import com.geihou.module.supplychain.stock.dal.mapper.StockEventMapper;
import com.geihou.module.supplychain.stock.framework.StockBusinessException;
import com.geihou.module.supplychain.stock.framework.StockErrorCodeConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Implementation of {@link BalanceReconcileService} (G2-02J).
 *
 * <p>Read-only reconciliation: recomputes expected balance from stock_event
 * and compares against stock_balance. No writes of any kind.
 *
 * <p>COUNT_ADJUST sign inference (task package §4.2):
 * <ul>
 *   <li>diff = balance_after − prevBalance (prevBalance = previous event's
 *       balance_after in the same dimension, or 0 for the first event).</li>
 *   <li>diff > 0 → inferred sign = +1 (surplus).</li>
 *   <li>diff < 0 → inferred sign = −1 (loss).</li>
 *   <li>diff = 0 → ambiguous (sign = 0), event skipped in expected computation,
 *       warning recorded.</li>
 * </ul>
 *
 * <p>Tenant isolation: all queries are scoped by tenant_id. No cross-tenant
 * access is possible.
 *
 * <p>reserved_qty is info-only: stock_event does not record reserve/release/
 * commit operations, so reserved_qty is NOT recomputed. It is only surfaced
 * as an informational field in the report.
 */
@Service
public class BalanceReconcileServiceImpl implements BalanceReconcileService {

    @Autowired
    private StockEventMapper stockEventMapper;

    @Autowired
    private StockBalanceMapper stockBalanceMapper;

    @Override
    public ReconcileReportRespDTO reconcileAll(Long tenantId) {
        requireTenantId(tenantId);

        List<StockEventDO> events = stockEventMapper.selectAllByTenant(tenantId);
        List<StockBalanceDO> balances = stockBalanceMapper.selectAllByTenant(tenantId);

        return buildReport(tenantId, events, balances);
    }

    @Override
    public ReconcileReportRespDTO reconcileByItem(Long tenantId, Long stockItemId) {
        requireTenantId(tenantId);
        Objects.requireNonNull(stockItemId, "stockItemId must not be null");

        List<StockEventDO> events = stockEventMapper.selectByTenantItem(tenantId, stockItemId);
        List<StockBalanceDO> balances = stockBalanceMapper.selectByTenantItem(tenantId, stockItemId);

        return buildReport(tenantId, events, balances);
    }

    @Override
    public ReconcileReportRespDTO reconcileByItemLocation(Long tenantId, Long stockItemId, Long locationId) {
        requireTenantId(tenantId);
        Objects.requireNonNull(stockItemId, "stockItemId must not be null");
        Objects.requireNonNull(locationId, "locationId must not be null");

        List<StockEventDO> events = stockEventMapper.selectByTenantItemLocation(tenantId, stockItemId, locationId);
        // Reuse the single-dimension balance lookup
        StockBalanceDO balance = stockBalanceMapper.selectByTenantItemLocation(tenantId, stockItemId, locationId);
        List<StockBalanceDO> balances = balance != null ? List.of(balance) : List.of();

        return buildReport(tenantId, events, balances);
    }

    // ================================================================
    // Core reconciliation logic
    // ================================================================

    private ReconcileReportRespDTO buildReport(Long tenantId,
                                                List<StockEventDO> events,
                                                List<StockBalanceDO> balances) {
        // Group events by (stockItemId, locationId) — use TreeMap for deterministic ordering
        Map<DimensionKey, List<StockEventDO>> eventsByDim = new TreeMap<>();
        for (StockEventDO event : events) {
            DimensionKey key = new DimensionKey(event.getStockItemId(), event.getLocationId());
            eventsByDim.computeIfAbsent(key, k -> new ArrayList<>()).add(event);
        }

        // Group balances by (stockItemId, locationId)
        Map<DimensionKey, StockBalanceDO> balancesByDim = new TreeMap<>();
        for (StockBalanceDO balance : balances) {
            DimensionKey key = new DimensionKey(balance.getStockItemId(), balance.getLocationId());
            balancesByDim.put(key, balance);
        }

        // Merge keys
        java.util.Set<DimensionKey> allKeys = new java.util.TreeSet<>();
        allKeys.addAll(eventsByDim.keySet());
        allKeys.addAll(balancesByDim.keySet());

        List<ReconcileItemDTO> items = new ArrayList<>();
        int matchedCount = 0;
        int mismatchedCount = 0;
        int eventsWithoutBalanceCount = 0;
        int balancesWithoutEventsCount = 0;
        int ambiguousSignCount = 0;
        int lineTraceGapCount = 0;

        for (DimensionKey key : allKeys) {
            List<StockEventDO> dimEvents = eventsByDim.getOrDefault(key, List.of());
            StockBalanceDO dimBalance = balancesByDim.get(key);

            ReconcileItemDTO item = reconcileDimension(key, dimEvents, dimBalance);
            items.add(item);

            switch (item.getStatus()) {
                case "MATCH" -> matchedCount++;
                case "MISMATCH" -> mismatchedCount++;
                case "EVENT_WITHOUT_BALANCE" -> eventsWithoutBalanceCount++;
                case "BALANCE_WITHOUT_EVENT" -> balancesWithoutEventsCount++;
            }
            if (item.isHasAmbiguousSign()) {
                ambiguousSignCount++;
            }
            if (item.isHasLineTraceGap()) {
                lineTraceGapCount++;
            }
        }

        ReconcileReportRespDTO report = new ReconcileReportRespDTO();
        report.setTenantId(tenantId);
        report.setReconcileTime(LocalDateTime.now());
        report.setTotalDimensions(allKeys.size());
        report.setMatchedCount(matchedCount);
        report.setMismatchedCount(mismatchedCount);
        report.setEventsWithoutBalanceCount(eventsWithoutBalanceCount);
        report.setBalancesWithoutEventsCount(balancesWithoutEventsCount);
        report.setAmbiguousSignCount(ambiguousSignCount);
        report.setLineTraceGapCount(lineTraceGapCount);
        report.setItems(items);
        return report;
    }

    /**
     * Reconcile a single (stockItem, location) dimension.
     */
    private ReconcileItemDTO reconcileDimension(DimensionKey key,
                                                 List<StockEventDO> events,
                                                 StockBalanceDO balance) {
        ReconcileItemDTO item = new ReconcileItemDTO();
        item.setStockItemId(key.stockItemId);
        item.setLocationId(key.locationId);

        // Determine skuCode from the last event or the balance record
        String skuCode = null;
        if (!events.isEmpty()) {
            skuCode = events.get(events.size() - 1).getSkuCode();
        } else if (balance != null) {
            // StockBalanceDO does not carry skuCode; leave null if no events
            skuCode = null;
        }
        item.setSkuCode(skuCode);
        applyLineTraceAudit(item, computeLineTraceAudit(events));

        // Case: no events, has balance → BALANCE_WITHOUT_EVENT
        if (events.isEmpty() && balance != null) {
            item.setStatus("BALANCE_WITHOUT_EVENT");
            item.setExpectedTotalQty(BigDecimal.ZERO);
            item.setExpectedAvailableQty(BigDecimal.ZERO);
            item.setActualTotalQty(balance.getTotalQty());
            item.setActualAvailableQty(balance.getAvailableQty());
            item.setActualReservedQty(balance.getReservedQty());
            item.setTotalQtyDiff(BigDecimal.ZERO.subtract(balance.getTotalQty()));
            item.setAvailableQtyDiff(BigDecimal.ZERO.subtract(balance.getAvailableQty()));
            item.setEventCount(0);
            item.setLastEventId(null);
            item.setLastEventTime(null);
            item.setInferredAdjustmentSign(null);
            item.setPersistedAdjustmentSign(null);
            item.setHasAmbiguousSign(false);
            item.setInferenceWarnings(List.of());
            return item;
        }

        // Case: has events, no balance → EVENT_WITHOUT_BALANCE
        if (!events.isEmpty() && balance == null) {
            ComputedExpected computed = computeExpected(events);
            item.setStatus("EVENT_WITHOUT_BALANCE");
            item.setExpectedTotalQty(computed.expectedTotalQty);
            item.setExpectedAvailableQty(computed.expectedAvailableQty);
            item.setActualTotalQty(BigDecimal.ZERO);
            item.setActualAvailableQty(BigDecimal.ZERO);
            item.setActualReservedQty(BigDecimal.ZERO);
            item.setTotalQtyDiff(computed.expectedTotalQty);
            item.setAvailableQtyDiff(computed.expectedAvailableQty);
            item.setEventCount(events.size());
            StockEventDO lastEvent = events.get(events.size() - 1);
            item.setLastEventId(lastEvent.getId());
            item.setLastEventTime(lastEvent.getEventTime());
            item.setInferredAdjustmentSign(computed.lastInferredSign);
            item.setPersistedAdjustmentSign(computed.lastPersistedSign);
            item.setHasAmbiguousSign(computed.hasAmbiguous);
            item.setInferenceWarnings(computed.warnings);
            return item;
        }

        // Case: has both events and balance → compare
        ComputedExpected computed = computeExpected(events);
        item.setExpectedTotalQty(computed.expectedTotalQty);
        item.setExpectedAvailableQty(computed.expectedAvailableQty);
        item.setActualTotalQty(balance.getTotalQty());
        item.setActualAvailableQty(balance.getAvailableQty());
        item.setActualReservedQty(balance.getReservedQty());
        item.setTotalQtyDiff(computed.expectedTotalQty.subtract(balance.getTotalQty()));
        item.setAvailableQtyDiff(computed.expectedAvailableQty.subtract(balance.getAvailableQty()));
        item.setEventCount(events.size());
        StockEventDO lastEvent = events.get(events.size() - 1);
        item.setLastEventId(lastEvent.getId());
        item.setLastEventTime(lastEvent.getEventTime());
        item.setInferredAdjustmentSign(computed.lastInferredSign);
        item.setPersistedAdjustmentSign(computed.lastPersistedSign);
        item.setHasAmbiguousSign(computed.hasAmbiguous);
        item.setInferenceWarnings(computed.warnings);

        boolean totalMatch = computed.expectedTotalQty.compareTo(balance.getTotalQty()) == 0;
        boolean availableMatch = computed.expectedAvailableQty.compareTo(balance.getAvailableQty()) == 0;
        item.setStatus(totalMatch && availableMatch ? "MATCH" : "MISMATCH");

        return item;
    }

    /**
     * Surfaces line-level audit gaps for BOM consume/restore events without
     * changing the stock balance reconciliation result.
     */
    private LineTraceAudit computeLineTraceAudit(List<StockEventDO> events) {
        LineTraceAudit audit = new LineTraceAudit();
        audit.warnings = new ArrayList<>();

        for (StockEventDO event : events) {
            if (!isBomLineTraceEvent(event)) {
                continue;
            }

            boolean restoreEvent = isBomRestoreEvent(event);
            if (event.getSourceOrderItemId() != null) {
                audit.lineTraceableEventCount++;
                if (restoreEvent) {
                    audit.lineRestoreEventCount++;
                }
                continue;
            }

            audit.lineTraceMissingEventCount++;
            audit.warnings.add("source_order_item_id missing for BOM " + event.getEventType()
                    + " event id=" + event.getId()
                    + ", source_record_id=" + event.getSourceRecordId()
                    + ", reference_no=" + event.getReferenceNo());
            if (restoreEvent) {
                audit.historicalNullRestoreEventCount++;
                audit.warnings.add("historical NULL source_order_item_id restore event detected: "
                        + event.getEventType() + " event id=" + event.getId());
            }
        }

        audit.hasLineTraceGap = audit.lineTraceMissingEventCount > 0;
        if (audit.lineTraceableEventCount > 0 && audit.lineTraceMissingEventCount > 0) {
            audit.warnings.add("mixed line-traceable and source_order_item_id NULL BOM events in same stock dimension");
        }
        return audit;
    }

    private void applyLineTraceAudit(ReconcileItemDTO item, LineTraceAudit audit) {
        item.setLineTraceableEventCount(audit.lineTraceableEventCount);
        item.setLineTraceMissingEventCount(audit.lineTraceMissingEventCount);
        item.setLineRestoreEventCount(audit.lineRestoreEventCount);
        item.setHistoricalNullRestoreEventCount(audit.historicalNullRestoreEventCount);
        item.setHasLineTraceGap(audit.hasLineTraceGap);
        item.setLineTraceWarnings(audit.warnings);
    }

    private boolean isBomLineTraceEvent(StockEventDO event) {
        if (event.getRecipeId() == null) {
            return false;
        }
        String eventType = event.getEventType();
        return StockEventTypeEnum.CONSUME_OUT.getCode().equals(eventType)
                || isBomRestoreEvent(event);
    }

    private boolean isBomRestoreEvent(StockEventDO event) {
        String eventType = event.getEventType();
        return StockEventTypeEnum.RETURN_IN.getCode().equals(eventType)
                || StockEventTypeEnum.PURCHASE_IN.getCode().equals(eventType);
    }

    /**
     * Recompute expected total/available qty from the event list (already sorted
     * by event_time ASC, id ASC).
     *
     * <p>For COUNT_ADJUST (INTERNAL) events, the sign is inferred from
     * balance_after diff (task package §4.2).
     */
    private ComputedExpected computeExpected(List<StockEventDO> events) {
        BigDecimal expectedTotalQty = BigDecimal.ZERO;
        BigDecimal expectedAvailableQty = BigDecimal.ZERO;
        BigDecimal prevBalance = BigDecimal.ZERO; // prior event's balance_after, 0 for first
        List<String> warnings = new ArrayList<>();
        boolean hasAmbiguous = false;
        Integer lastInferredSign = null;
        Integer lastPersistedSign = null;

        for (StockEventDO event : events) {
            String direction = event.getDirection();
            BigDecimal qty = event.getQuantity() != null ? event.getQuantity() : BigDecimal.ZERO;
            BigDecimal balanceAfter = event.getBalanceAfter() != null ? event.getBalanceAfter() : BigDecimal.ZERO;

            if (StockDirectionEnum.IN.getCode().equals(direction)) {
                expectedTotalQty = expectedTotalQty.add(qty);
                expectedAvailableQty = expectedAvailableQty.add(qty);
            } else if (StockDirectionEnum.OUT.getCode().equals(direction)) {
                expectedTotalQty = expectedTotalQty.subtract(qty);
                expectedAvailableQty = expectedAvailableQty.subtract(qty);
            } else if (StockDirectionEnum.INTERNAL.getCode().equals(direction)) {
                // G2-02J-1: prefer persisted adjustment_sign
                Integer persistedSign = event.getAdjustmentSign();
                if (persistedSign != null) {
                    // Use persisted sign directly
                    if (persistedSign > 0) {
                        // Surplus (盘盈)
                        expectedTotalQty = expectedTotalQty.add(qty);
                        expectedAvailableQty = expectedAvailableQty.add(qty);
                        lastInferredSign = persistedSign; // persisted == inferred for new events
                        lastPersistedSign = persistedSign;
                    } else if (persistedSign < 0) {
                        // Loss (盘亏)
                        expectedTotalQty = expectedTotalQty.subtract(qty);
                        expectedAvailableQty = expectedAvailableQty.subtract(qty);
                        lastInferredSign = persistedSign;
                        lastPersistedSign = persistedSign;
                    } else {
                        // persistedSign == 0 should not occur (validated as ±1 in recordEvent),
                        // but handle defensively
                        hasAmbiguous = true;
                        lastInferredSign = 0;
                        lastPersistedSign = 0;
                        warnings.add("COUNT_ADJUST event id=" + event.getId()
                                + " has persisted adjustment_sign=0, treated as ambiguous");
                    }
                } else {
                    // Fallback: legacy NULL row, infer from balance_after diff (G2-02J §4.2)
                    BigDecimal diff = balanceAfter.subtract(prevBalance);
                    int cmp = diff.compareTo(BigDecimal.ZERO);
                    if (cmp > 0) {
                        // Surplus (盘盈)
                        expectedTotalQty = expectedTotalQty.add(qty);
                        expectedAvailableQty = expectedAvailableQty.add(qty);
                        lastInferredSign = 1;
                    } else if (cmp < 0) {
                        // Loss (盘亏)
                        expectedTotalQty = expectedTotalQty.subtract(qty);
                        expectedAvailableQty = expectedAvailableQty.subtract(qty);
                        lastInferredSign = -1;
                    } else {
                        // Ambiguous: diff == 0 but quantity > 0
                        hasAmbiguous = true;
                        lastInferredSign = 0;
                        warnings.add("COUNT_ADJUST event id=" + event.getId()
                                + " balance_after=" + balanceAfter
                                + " equals prevBalance=" + prevBalance
                                + ", sign ambiguous (inferred=0, legacy NULL), event skipped in expected computation");
                    }
                    lastPersistedSign = null; // legacy row, not persisted
                }
            } else {
                warnings.add("Unknown direction '" + direction + "' for event id=" + event.getId()
                        + ", event skipped");
            }

            // Update prevBalance to this event's balance_after for the next iteration
            prevBalance = balanceAfter;
        }

        ComputedExpected result = new ComputedExpected();
        result.expectedTotalQty = expectedTotalQty;
        result.expectedAvailableQty = expectedAvailableQty;
        result.hasAmbiguous = hasAmbiguous;
        result.lastInferredSign = lastInferredSign;
        result.lastPersistedSign = lastPersistedSign;
        result.warnings = warnings;
        return result;
    }

    private void requireTenantId(Long tenantId) {
        if (tenantId == null) {
            throw new StockBusinessException(StockErrorCodeConstants.RECONCILE_TENANT_ID_REQUIRED);
        }
    }

    // ================================================================
    // Helper classes
    // ================================================================

    /** Composite key for (stockItemId, locationId) — Comparable for deterministic ordering. */
    private static final class DimensionKey implements Comparable<DimensionKey> {
        final Long stockItemId;
        final Long locationId;

        DimensionKey(Long stockItemId, Long locationId) {
            this.stockItemId = stockItemId;
            this.locationId = locationId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DimensionKey that)) return false;
            return Objects.equals(stockItemId, that.stockItemId)
                    && Objects.equals(locationId, that.locationId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(stockItemId, locationId);
        }

        @Override
        public int compareTo(DimensionKey o) {
            int c = this.stockItemId.compareTo(o.stockItemId);
            if (c != 0) return c;
            return this.locationId.compareTo(o.locationId);
        }
    }

    /** Internal result holder for computeExpected. */
    private static final class ComputedExpected {
        BigDecimal expectedTotalQty;
        BigDecimal expectedAvailableQty;
        boolean hasAmbiguous;
        Integer lastInferredSign;
        Integer lastPersistedSign;
        List<String> warnings;
    }

    /** Internal holder for BOM source_order_item_id audit statistics. */
    private static final class LineTraceAudit {
        int lineTraceableEventCount;
        int lineTraceMissingEventCount;
        int lineRestoreEventCount;
        int historicalNullRestoreEventCount;
        boolean hasLineTraceGap;
        List<String> warnings;
    }
}
