package com.geihou.module.supplychain.stock.service;

import com.geihou.module.supplychain.api.stock.dto.StockEventReqDTO;
import com.geihou.module.supplychain.api.stock.enums.StockDirectionEnum;
import com.geihou.module.supplychain.api.stock.enums.StockEventTypeEnum;
import com.geihou.module.supplychain.stock.dal.dataobject.StockBalanceDO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockEventDO;
import com.geihou.module.supplychain.stock.dal.mapper.StockBalanceMapper;
import com.geihou.module.supplychain.stock.dal.mapper.StockEventMapper;
import com.geihou.module.supplychain.stock.framework.StockBusinessException;
import com.geihou.module.supplychain.stock.framework.StockErrorCodeConstants;
import com.geihou.module.supplychain.stock.mq.StockEventPublisher;
import com.geihou.module.supplychain.stock.mq.event.StockLowWarningEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Implementation of {@link StockEventService}.
 *
 * <p>Records immutable stock events and updates balance atomically in the same transaction.
 * Uses optimistic locking (version field) for concurrent balance updates.
 * Idempotency via client_request_id unique constraint.
 *
 * <p>AC-1: stock_event INSERT-only (only calls mapper.insert, never update/delete).
 * AC-4: concurrent deduct does not oversell (optimistic lock + retry).
 * AC-5: idempotent via client_request_id.
 * AC-8: optimistic lock version field effective.
 * AC-11: event + balance update in same transaction (atomic).
 */
@Service
public class StockEventServiceImpl implements StockEventService {

    private static final int MAX_RETRIES = 3;

    @Autowired
    private StockEventMapper stockEventMapper;

    @Autowired
    private StockBalanceMapper stockBalanceMapper;

    @Autowired
    private StockEventPublisher stockEventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long recordEvent(StockEventReqDTO req) {
        // --- Validation ---
        validateRequest(req);

        // --- Idempotency check (scoped by tenant_id + client_request_id) ---
        if (req.getClientRequestId() != null && !req.getClientRequestId().isBlank()) {
            StockEventDO existing = stockEventMapper.selectByClientRequestId(
                    req.getTenantId(), req.getClientRequestId());
            if (existing != null) {
                return existing.getId();
            }
        }

        // --- Validate event type and direction consistency ---
        StockEventTypeEnum eventTypeEnum = StockEventTypeEnum.fromCode(req.getEventType());
        StockDirectionEnum directionEnum = StockDirectionEnum.fromCode(req.getDirection());
        if (!eventTypeEnum.getDirection().equals(directionEnum.getCode())) {
            throw new StockBusinessException(StockErrorCodeConstants.DIRECTION_INVALID,
                    "Direction " + req.getDirection() + " does not match event type " + req.getEventType());
        }

        // --- G2-02I-2A: INTERNAL direction (COUNT_ADJUST) now supported ---
        // adjustmentSign determines positive (盘盈, +1) or negative (盘亏, -1) adjustment.
        // Null defaults to +1. Validate sign for INTERNAL direction.
        if (directionEnum == StockDirectionEnum.INTERNAL) {
            Integer sign = req.getAdjustmentSign();
            if (sign == null) {
                req.setAdjustmentSign(1); // default positive
            } else if (sign != 1 && sign != -1) {
                throw new StockBusinessException(StockErrorCodeConstants.DIRECTION_INVALID,
                        "adjustmentSign must be +1 or -1 for INTERNAL direction, got: " + sign);
            }
        }

        // --- Create JDBC savepoint before balance insert/update + event insert ---
        // G2-02I-1A FIX: savepoint covers balance insert/update + event insert +
        // updateLastEventId. If event insert hits the unique-key constraint
        // (uk_tenant_client_request) due to a read-then-write race, we rollback
        // to this savepoint to undo the balance changes, then re-query and
        // return the existing event id — achieving idempotent return without
        // throwing DuplicateKeyException or marking the transaction rollback-only.
        Object savepoint = TransactionAspectSupport.currentTransactionStatus().createSavepoint();

        // --- Get or create balance ---
        StockBalanceDO balance = stockBalanceMapper.selectByTenantItemLocation(
                req.getTenantId(), req.getStockItemId(), req.getLocationId());
        boolean isNewBalance = (balance == null);

        if (isNewBalance) {
            balance = createInitialBalance(req.getTenantId(), req.getStockItemId(), req.getLocationId());
            stockBalanceMapper.insert(balance);
        }

        // --- Update balance with optimistic lock (retry on conflict) ---
        // G2-01A FIX: balance update FIRST, then insert event with the correct
        // final balance_after. Previously the event was inserted before the
        // optimistic-lock retry loop, so on version conflict the event's
        // balance_after reflected a stale snapshot. Now the event is only
        // inserted after the balance update succeeds, within the same
        // transaction — if the event insert fails, the balance update rolls
        // back too.
        BigDecimal currentAvailable = balance.getAvailableQty() != null ? balance.getAvailableQty() : BigDecimal.ZERO;
        BigDecimal currentTotal = balance.getTotalQty() != null ? balance.getTotalQty() : BigDecimal.ZERO;

        BigDecimal newAvailableQty = null;
        BigDecimal newTotalQty = null;

        boolean updated = false;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            // Calculate new balance based on current snapshot
            if (directionEnum == StockDirectionEnum.IN) {
                newAvailableQty = currentAvailable.add(req.getQuantity());
                newTotalQty = currentTotal.add(req.getQuantity());
            } else if (directionEnum == StockDirectionEnum.OUT) {
                if (currentAvailable.compareTo(req.getQuantity()) < 0) {
                    throw new StockBusinessException(StockErrorCodeConstants.INSUFFICIENT_STOCK,
                            "Available: " + currentAvailable + ", Requested: " + req.getQuantity());
                }
                newAvailableQty = currentAvailable.subtract(req.getQuantity());
                newTotalQty = currentTotal.subtract(req.getQuantity());
            } else { // INTERNAL (COUNT_ADJUST)
                int sign = req.getAdjustmentSign(); // already validated as +1 or -1
                if (sign < 0) {
                    // 盘亏: negative adjustment — check available stock
                    if (currentAvailable.compareTo(req.getQuantity()) < 0) {
                        throw new StockBusinessException(StockErrorCodeConstants.INSUFFICIENT_STOCK,
                                "Available: " + currentAvailable + ", Requested: " + req.getQuantity());
                    }
                    newAvailableQty = currentAvailable.subtract(req.getQuantity());
                    newTotalQty = currentTotal.subtract(req.getQuantity());
                } else {
                    // 盘盈: positive adjustment
                    newAvailableQty = currentAvailable.add(req.getQuantity());
                    newTotalQty = currentTotal.add(req.getQuantity());
                }
            }

            int rows = stockBalanceMapper.updateBalanceWithOptimisticLock(
                    balance.getId(),
                    req.getTenantId(),
                    newAvailableQty,
                    newTotalQty,
                    null,
                    req.getEventTime() != null ? req.getEventTime() : LocalDateTime.now(),
                    balance.getVersion(),
                    String.valueOf(req.getOperatorUserId()),
                    LocalDateTime.now()
            );
            if (rows > 0) {
                updated = true;
                break;
            }
            // Version conflict — reload balance and retry
            balance = stockBalanceMapper.selectByTenantItemLocation(
                    req.getTenantId(), req.getStockItemId(), req.getLocationId());
            if (balance == null) {
                throw new StockBusinessException(StockErrorCodeConstants.BALANCE_CONCURRENT_CONFLICT,
                        "Balance record disappeared during retry");
            }
            currentAvailable = balance.getAvailableQty() != null ? balance.getAvailableQty() : BigDecimal.ZERO;
            currentTotal = balance.getTotalQty() != null ? balance.getTotalQty() : BigDecimal.ZERO;
        }

        if (!updated) {
            throw new StockBusinessException(StockErrorCodeConstants.BALANCE_CONCURRENT_CONFLICT,
                    "Failed after " + MAX_RETRIES + " retries");
        }

        // --- Insert event (INSERT-only) AFTER balance update succeeds ---
        // balance_after now reflects the true final balance, not a stale snapshot.
        StockEventDO eventDO = buildEventDO(req, newAvailableQty);
        try {
            stockEventMapper.insert(eventDO);
        } catch (DuplicateKeyException e) {
            // Concurrent race: another thread already inserted an event with
            // the same tenant_id + client_request_id. Rollback balance changes
            // made after the savepoint, then return the existing event id.
            TransactionAspectSupport.currentTransactionStatus().rollbackToSavepoint(savepoint);
            StockEventDO existing = stockEventMapper.selectByClientRequestId(
                    req.getTenantId(), req.getClientRequestId());
            if (existing != null) {
                return existing.getId();
            }
            // Should not reach here: unique-key conflict but re-query returns null
            throw e;
        }

        // Link the balance record to the newly created event (metadata only,
        // does not change available_qty / total_qty).
        stockBalanceMapper.updateLastEventId(balance.getId(), req.getTenantId(), eventDO.getId());

        // Release savepoint after updateLastEventId — all post-savepoint work
        // succeeded, so the savepoint is no longer needed.
        TransactionAspectSupport.currentTransactionStatus().releaseSavepoint(savepoint);

        publishLowWarningIfNeeded(req, balance, newAvailableQty);

        return eventDO.getId();
    }

    private void publishLowWarningIfNeeded(StockEventReqDTO req, StockBalanceDO balance, BigDecimal newAvailableQty) {
        if (balance.getMinThreshold() == null || newAvailableQty == null
                || newAvailableQty.compareTo(balance.getMinThreshold()) > 0) {
            return;
        }

        StockLowWarningEvent event = new StockLowWarningEvent();
        event.setTenantId(req.getTenantId());
        event.setSourceModule(req.getSourceModule());
        event.setSourceRecordId(req.getSourceRecordId());
        event.setReferenceNo(req.getReferenceNo());
        event.setStockItemId(req.getStockItemId());
        event.setLocationId(req.getLocationId());
        event.setQuantity(newAvailableQty);
        event.setThresholdQuantity(balance.getMinThreshold());
        event.setWarningLevel("LOW");
        event.setEventTime(req.getEventTime() != null ? req.getEventTime() : LocalDateTime.now());
        stockEventPublisher.publishStockLowWarning(event);
    }

    private void validateRequest(StockEventReqDTO req) {
        Objects.requireNonNull(req, "request must not be null");
        if (req.getTenantId() == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "tenantId");
        }
        if (req.getEventType() == null || req.getEventType().isBlank()) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "eventType");
        }
        if (req.getDirection() == null || req.getDirection().isBlank()) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "direction");
        }
        if (req.getStockItemId() == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "stockItemId");
        }
        if (req.getSkuCode() == null || req.getSkuCode().isBlank()) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "skuCode");
        }
        if (req.getLocationId() == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "locationId");
        }
        if (req.getQuantity() == null || req.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new StockBusinessException(StockErrorCodeConstants.QUANTITY_MUST_BE_POSITIVE);
        }
        if (req.getUnit() == null || req.getUnit().isBlank()) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "unit");
        }
        if (req.getOperatorUserId() == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "operatorUserId");
        }
        // Validate event type is one of the 11 values
        try {
            StockEventTypeEnum.fromCode(req.getEventType());
        } catch (IllegalArgumentException e) {
            throw new StockBusinessException(StockErrorCodeConstants.EVENT_TYPE_INVALID, req.getEventType());
        }
        // Validate direction
        try {
            StockDirectionEnum.fromCode(req.getDirection());
        } catch (IllegalArgumentException e) {
            throw new StockBusinessException(StockErrorCodeConstants.DIRECTION_INVALID, req.getDirection());
        }
        // G2-02I-2: COUNT_ADJUST events require adjustmentReason non-blank and >=30 chars (PRD §5)
        if (StockEventTypeEnum.COUNT_ADJUST.getCode().equals(req.getEventType())) {
            if (req.getAdjustmentReason() == null || req.getAdjustmentReason().isBlank()) {
                throw new StockBusinessException(StockErrorCodeConstants.ADJUSTMENT_REASON_TOO_SHORT,
                        "adjustmentReason is required for COUNT_ADJUST event");
            }
            if (req.getAdjustmentReason().length() < 30) {
                throw new StockBusinessException(StockErrorCodeConstants.ADJUSTMENT_REASON_TOO_SHORT,
                        "adjustmentReason must be >= 30 chars, got: " + req.getAdjustmentReason().length());
            }
        }
    }

    private StockBalanceDO createInitialBalance(Long tenantId, Long stockItemId, Long locationId) {
        StockBalanceDO balance = new StockBalanceDO();
        balance.setTenantId(tenantId);
        balance.setStockItemId(stockItemId);
        balance.setLocationId(locationId);
        balance.setAvailableQty(BigDecimal.ZERO);
        balance.setTotalQty(BigDecimal.ZERO);
        balance.setReservedQty(BigDecimal.ZERO);
        balance.setAvgUnitCost(BigDecimal.ZERO);
        balance.setVersion(0);
        balance.setCreator("system");
        balance.setCreateTime(LocalDateTime.now());
        balance.setUpdater("system");
        balance.setUpdateTime(LocalDateTime.now());
        balance.setDeleted(false);
        return balance;
    }

    private StockEventDO buildEventDO(StockEventReqDTO req, BigDecimal balanceAfter) {
        StockEventDO eventDO = new StockEventDO();
        eventDO.setTenantId(req.getTenantId());
        eventDO.setEventTime(req.getEventTime() != null ? req.getEventTime() : LocalDateTime.now());
        eventDO.setBusinessDate(req.getBusinessDate() != null ? req.getBusinessDate()
                : (req.getEventTime() != null ? req.getEventTime().toLocalDate() : LocalDateTime.now().toLocalDate()));
        eventDO.setEventType(req.getEventType());
        eventDO.setDirection(req.getDirection());
        eventDO.setStockItemId(req.getStockItemId());
        eventDO.setSkuCode(req.getSkuCode());
        eventDO.setLocationId(req.getLocationId());
        eventDO.setQuantity(req.getQuantity());
        eventDO.setUnit(req.getUnit());
        eventDO.setUnitCost(req.getUnitCost());
        eventDO.setTotalCost(req.getTotalCost());
        eventDO.setSourceModule(req.getSourceModule());
        eventDO.setSourceRecordId(req.getSourceRecordId());
        eventDO.setSourceOrderItemId(req.getSourceOrderItemId());
        eventDO.setReferenceNo(req.getReferenceNo());
        eventDO.setClientRequestId(req.getClientRequestId());
        eventDO.setOperatorUserId(req.getOperatorUserId());
        eventDO.setBalanceAfter(balanceAfter);
        eventDO.setCreateTime(LocalDateTime.now());
        // G2-02D-pre: BOM reverse consumption pass-through (nullable, no business logic)
        eventDO.setParentEventId(req.getParentEventId());
        eventDO.setRecipeId(req.getRecipeId());
        eventDO.setRecipeVersion(req.getRecipeVersion());
        // G2-02I-2: adjustment reason pass-through (PRD §5)
        eventDO.setAdjustmentReason(req.getAdjustmentReason());
        // G2-02J-1: persist adjustmentSign for COUNT_ADJUST/INTERNAL events
        // For IN/OUT direction, req.adjustmentSign is null (callers don't set it).
        // For INTERNAL direction, req.adjustmentSign has been validated/defaulted
        // in the recordEvent validation block (lines 72-80).
        eventDO.setAdjustmentSign(req.getAdjustmentSign());
        return eventDO;
    }
}
