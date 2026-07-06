package com.geihou.module.supplychain.stock.service;

import com.geihou.module.supplychain.api.stock.dto.StockCommitReqDTO;
import com.geihou.module.supplychain.api.stock.dto.StockReleaseReqDTO;
import com.geihou.module.supplychain.api.stock.dto.StockReserveReqDTO;
import com.geihou.module.supplychain.api.stock.enums.StockDirectionEnum;
import com.geihou.module.supplychain.api.stock.enums.StockEventTypeEnum;
import com.geihou.module.supplychain.api.stock.enums.StockReserveStatusEnum;
import com.geihou.module.supplychain.stock.dal.dataobject.StockBalanceDO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockEventDO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockReserveDO;
import com.geihou.module.supplychain.stock.dal.mapper.StockBalanceMapper;
import com.geihou.module.supplychain.stock.dal.mapper.StockEventMapper;
import com.geihou.module.supplychain.stock.dal.mapper.StockReserveMapper;
import com.geihou.module.supplychain.stock.framework.StockBusinessException;
import com.geihou.module.supplychain.stock.framework.StockErrorCodeConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Implementation of {@link StockReserveService}.
 *
 * <p>Implements reserve/release/commit with:
 * - Tenant isolation (all queries filter by tenant_id)
 * - Idempotency (via idempotent_key unique constraint)
 * - Optimistic locking (version field on stock_balance, retry 3 times)
 * - Transactional atomicity (all operations in @Transactional)
 * - No stock_event on reserve/release; commit uses existing CONSUME_OUT
 *
 * <p>Three-value invariant: available_qty = total_qty - reserved_qty
 *
 * <p>Source: TASK-G2-01B1 Section 6.
 */
@Service
public class StockReserveServiceImpl implements StockReserveService {

    private static final int MAX_RETRIES = 3;

    @Autowired
    private StockReserveMapper stockReserveMapper;

    @Autowired
    private StockBalanceMapper stockBalanceMapper;

    @Autowired
    private StockEventMapper stockEventMapper;

    // ==================== reserve ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long reserveStock(StockReserveReqDTO req) {
        // --- Step 1: Validate ---
        validateReserveReq(req);

        // --- Step 2: Idempotency check ---
        StockReserveDO existing = stockReserveMapper.selectByTenantIdempotentKey(
                req.getTenantId(), req.getIdempotentKey());
        if (existing != null) {
            if (StockReserveStatusEnum.RESERVED.getCode().equals(existing.getStatus())) {
                return existing.getId(); // idempotent success
            }
            if (StockReserveStatusEnum.RELEASED.getCode().equals(existing.getStatus())) {
                throw new StockBusinessException(StockErrorCodeConstants.RESERVE_ALREADY_RELEASED);
            }
            if (StockReserveStatusEnum.COMMITTED.getCode().equals(existing.getStatus())) {
                throw new StockBusinessException(StockErrorCodeConstants.RESERVE_ALREADY_COMMITTED);
            }
        }

        // --- Step 3: Get balance ---
        StockBalanceDO balance = stockBalanceMapper.selectByTenantItemLocation(
                req.getTenantId(), req.getStockItemId(), req.getLocationId());
        if (balance == null) {
            throw new StockBusinessException(StockErrorCodeConstants.INSUFFICIENT_AVAILABLE_STOCK,
                    "Balance not found for item=" + req.getStockItemId() + " location=" + req.getLocationId());
        }

        // --- Step 4: Check available + optimistic lock update ---
        BigDecimal currentAvailable = balance.getAvailableQty() != null ? balance.getAvailableQty() : BigDecimal.ZERO;
        BigDecimal currentTotal = balance.getTotalQty() != null ? balance.getTotalQty() : BigDecimal.ZERO;
        BigDecimal currentReserved = balance.getReservedQty() != null ? balance.getReservedQty() : BigDecimal.ZERO;

        boolean updated = false;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            // Check available
            if (currentAvailable.compareTo(req.getQuantity()) < 0) {
                throw new StockBusinessException(StockErrorCodeConstants.INSUFFICIENT_AVAILABLE_STOCK,
                        "Available: " + currentAvailable + ", Requested: " + req.getQuantity());
            }

            BigDecimal newAvailableQty = currentAvailable.subtract(req.getQuantity());
            BigDecimal newReservedQty = currentReserved.add(req.getQuantity());
            // total_qty unchanged

            int rows = stockBalanceMapper.updateBalanceWithReserve(
                    balance.getId(),
                    req.getTenantId(),
                    newAvailableQty,
                    currentTotal,
                    newReservedQty,
                    null,
                    null,
                    balance.getVersion(),
                    String.valueOf(req.getOperatorUserId()),
                    LocalDateTime.now()
            );
            if (rows > 0) {
                updated = true;
                break;
            }
            // Version conflict — reload and retry
            balance = stockBalanceMapper.selectByTenantItemLocation(
                    req.getTenantId(), req.getStockItemId(), req.getLocationId());
            if (balance == null) {
                throw new StockBusinessException(StockErrorCodeConstants.BALANCE_CONCURRENT_CONFLICT,
                        "Balance record disappeared during retry");
            }
            currentAvailable = balance.getAvailableQty() != null ? balance.getAvailableQty() : BigDecimal.ZERO;
            currentTotal = balance.getTotalQty() != null ? balance.getTotalQty() : BigDecimal.ZERO;
            currentReserved = balance.getReservedQty() != null ? balance.getReservedQty() : BigDecimal.ZERO;
        }

        if (!updated) {
            throw new StockBusinessException(StockErrorCodeConstants.BALANCE_CONCURRENT_CONFLICT,
                    "Failed after " + MAX_RETRIES + " retries");
        }

        // --- Step 5: INSERT stock_reserve ---
        StockReserveDO reserveDO = new StockReserveDO();
        reserveDO.setTenantId(req.getTenantId());
        reserveDO.setStockItemId(req.getStockItemId());
        reserveDO.setLocationId(req.getLocationId());
        reserveDO.setSkuCode(req.getSkuCode());
        reserveDO.setQuantity(req.getQuantity());
        reserveDO.setUnit(req.getUnit());
        reserveDO.setSourceModule(req.getSourceModule());
        reserveDO.setSourceRecordId(req.getSourceRecordId());
        reserveDO.setReferenceNo(req.getReferenceNo());
        reserveDO.setIdempotentKey(req.getIdempotentKey());
        reserveDO.setStatus(StockReserveStatusEnum.RESERVED.getCode());
        reserveDO.setOperatorUserId(req.getOperatorUserId());
        reserveDO.setCreator(String.valueOf(req.getOperatorUserId()));
        reserveDO.setCreateTime(LocalDateTime.now());
        reserveDO.setUpdater(String.valueOf(req.getOperatorUserId()));
        reserveDO.setUpdateTime(LocalDateTime.now());
        reserveDO.setDeleted(false);
        stockReserveMapper.insert(reserveDO);

        // ⚠️ No stock_event written

        return reserveDO.getId();
    }

    // ==================== release ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseStock(StockReleaseReqDTO req) {
        // --- Step 1: Validate ---
        Objects.requireNonNull(req, "release request must not be null");
        if (req.getTenantId() == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "tenantId");
        }
        if (req.getIdempotentKey() == null || req.getIdempotentKey().isBlank()) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "idempotentKey");
        }
        if (req.getOperatorUserId() == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "operatorUserId");
        }

        // --- Step 2: Idempotency check ---
        StockReserveDO reserve = stockReserveMapper.selectByTenantIdempotentKey(
                req.getTenantId(), req.getIdempotentKey());
        if (reserve == null) {
            throw new StockBusinessException(StockErrorCodeConstants.RESERVE_NOT_FOUND,
                    "idempotentKey=" + req.getIdempotentKey());
        }

        if (StockReserveStatusEnum.RELEASED.getCode().equals(reserve.getStatus())) {
            return; // idempotent success
        }
        if (StockReserveStatusEnum.COMMITTED.getCode().equals(reserve.getStatus())) {
            throw new StockBusinessException(StockErrorCodeConstants.RESERVE_ALREADY_COMMITTED);
        }

        // --- Optional reserveId secondary validation ---
        if (req.getReserveId() != null && !req.getReserveId().equals(reserve.getId())) {
            throw new StockBusinessException(StockErrorCodeConstants.RESERVE_NOT_FOUND,
                    "reserveId mismatch: expected=" + reserve.getId() + ", got=" + req.getReserveId());
        }

        // --- Step 3: Optimistic lock update balance ---
        StockBalanceDO balance = stockBalanceMapper.selectByTenantItemLocation(
                req.getTenantId(), reserve.getStockItemId(), reserve.getLocationId());
        if (balance == null) {
            throw new StockBusinessException(StockErrorCodeConstants.BALANCE_CONCURRENT_CONFLICT,
                    "Balance not found during release");
        }

        BigDecimal currentAvailable = balance.getAvailableQty() != null ? balance.getAvailableQty() : BigDecimal.ZERO;
        BigDecimal currentTotal = balance.getTotalQty() != null ? balance.getTotalQty() : BigDecimal.ZERO;
        BigDecimal currentReserved = balance.getReservedQty() != null ? balance.getReservedQty() : BigDecimal.ZERO;

        boolean updated = false;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            BigDecimal newAvailableQty = currentAvailable.add(reserve.getQuantity());
            BigDecimal newReservedQty = currentReserved.subtract(reserve.getQuantity());
            // total_qty unchanged

            int rows = stockBalanceMapper.updateBalanceWithReserve(
                    balance.getId(),
                    req.getTenantId(),
                    newAvailableQty,
                    currentTotal,
                    newReservedQty,
                    null,
                    null,
                    balance.getVersion(),
                    String.valueOf(req.getOperatorUserId()),
                    LocalDateTime.now()
            );
            if (rows > 0) {
                updated = true;
                break;
            }
            balance = stockBalanceMapper.selectByTenantItemLocation(
                    req.getTenantId(), reserve.getStockItemId(), reserve.getLocationId());
            if (balance == null) {
                throw new StockBusinessException(StockErrorCodeConstants.BALANCE_CONCURRENT_CONFLICT,
                        "Balance record disappeared during retry");
            }
            currentAvailable = balance.getAvailableQty() != null ? balance.getAvailableQty() : BigDecimal.ZERO;
            currentTotal = balance.getTotalQty() != null ? balance.getTotalQty() : BigDecimal.ZERO;
            currentReserved = balance.getReservedQty() != null ? balance.getReservedQty() : BigDecimal.ZERO;
        }

        if (!updated) {
            throw new StockBusinessException(StockErrorCodeConstants.BALANCE_CONCURRENT_CONFLICT,
                    "Failed after " + MAX_RETRIES + " retries");
        }

        // --- Step 4: UPDATE stock_reserve status (CAS on expectedStatus=RESERVED) ---
        int reserveRows = stockReserveMapper.updateStatus(
                reserve.getId(),
                req.getTenantId(),
                StockReserveStatusEnum.RELEASED.getCode(),
                StockReserveStatusEnum.RESERVED.getCode(),
                null,
                String.valueOf(req.getOperatorUserId()),
                LocalDateTime.now()
        );
        if (reserveRows == 0) {
            // Concurrent release/commit already changed the status — rollback balance update
            throw new StockBusinessException(StockErrorCodeConstants.BALANCE_CONCURRENT_CONFLICT,
                    "Reserve status was already changed by a concurrent transaction (release)");
        }

        // ⚠️ No stock_event written
    }

    // ==================== commit ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long commitStock(StockCommitReqDTO req) {
        // --- Step 1: Validate ---
        Objects.requireNonNull(req, "commit request must not be null");
        if (req.getTenantId() == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "tenantId");
        }
        if (req.getIdempotentKey() == null || req.getIdempotentKey().isBlank()) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "idempotentKey");
        }
        if (req.getOperatorUserId() == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "operatorUserId");
        }

        // --- Step 2: Idempotency check ---
        StockReserveDO reserve = stockReserveMapper.selectByTenantIdempotentKey(
                req.getTenantId(), req.getIdempotentKey());
        if (reserve == null) {
            throw new StockBusinessException(StockErrorCodeConstants.RESERVE_NOT_FOUND,
                    "idempotentKey=" + req.getIdempotentKey());
        }

        if (StockReserveStatusEnum.COMMITTED.getCode().equals(reserve.getStatus())) {
            return reserve.getCommitEventId(); // idempotent success
        }
        if (StockReserveStatusEnum.RELEASED.getCode().equals(reserve.getStatus())) {
            throw new StockBusinessException(StockErrorCodeConstants.RESERVE_ALREADY_RELEASED);
        }

        // --- Optional reserveId secondary validation ---
        if (req.getReserveId() != null && !req.getReserveId().equals(reserve.getId())) {
            throw new StockBusinessException(StockErrorCodeConstants.RESERVE_NOT_FOUND,
                    "reserveId mismatch: expected=" + reserve.getId() + ", got=" + req.getReserveId());
        }

        // --- Step 3: Optimistic lock update balance ---
        StockBalanceDO balance = stockBalanceMapper.selectByTenantItemLocation(
                req.getTenantId(), reserve.getStockItemId(), reserve.getLocationId());
        if (balance == null) {
            throw new StockBusinessException(StockErrorCodeConstants.BALANCE_CONCURRENT_CONFLICT,
                    "Balance not found during commit");
        }

        BigDecimal currentAvailable = balance.getAvailableQty() != null ? balance.getAvailableQty() : BigDecimal.ZERO;
        BigDecimal currentTotal = balance.getTotalQty() != null ? balance.getTotalQty() : BigDecimal.ZERO;
        BigDecimal currentReserved = balance.getReservedQty() != null ? balance.getReservedQty() : BigDecimal.ZERO;

        // commit: reserved_qty -= qty, total_qty -= qty, available_qty unchanged
        BigDecimal newAvailableQty = currentAvailable; // unchanged
        BigDecimal newTotalQty = currentTotal.subtract(reserve.getQuantity());
        BigDecimal newReservedQty = currentReserved.subtract(reserve.getQuantity());

        boolean updated = false;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            int rows = stockBalanceMapper.updateBalanceWithReserve(
                    balance.getId(),
                    req.getTenantId(),
                    newAvailableQty,
                    newTotalQty,
                    newReservedQty,
                    null,
                    null,
                    balance.getVersion(),
                    String.valueOf(req.getOperatorUserId()),
                    LocalDateTime.now()
            );
            if (rows > 0) {
                updated = true;
                break;
            }
            balance = stockBalanceMapper.selectByTenantItemLocation(
                    req.getTenantId(), reserve.getStockItemId(), reserve.getLocationId());
            if (balance == null) {
                throw new StockBusinessException(StockErrorCodeConstants.BALANCE_CONCURRENT_CONFLICT,
                        "Balance record disappeared during retry");
            }
            // Recalculate since balance may have changed
            currentAvailable = balance.getAvailableQty() != null ? balance.getAvailableQty() : BigDecimal.ZERO;
            currentTotal = balance.getTotalQty() != null ? balance.getTotalQty() : BigDecimal.ZERO;
            currentReserved = balance.getReservedQty() != null ? balance.getReservedQty() : BigDecimal.ZERO;
            newAvailableQty = currentAvailable; // unchanged
            newTotalQty = currentTotal.subtract(reserve.getQuantity());
            newReservedQty = currentReserved.subtract(reserve.getQuantity());
        }

        if (!updated) {
            throw new StockBusinessException(StockErrorCodeConstants.BALANCE_CONCURRENT_CONFLICT,
                    "Failed after " + MAX_RETRIES + " retries");
        }

        // --- Step 4: INSERT stock_event (CONSUME_OUT, OUT) ---
        // commit uses existing CONSUME_OUT enum value, NOT a new RESERVE_* value
        StockEventDO eventDO = new StockEventDO();
        eventDO.setTenantId(req.getTenantId());
        eventDO.setEventTime(req.getEventTime() != null ? req.getEventTime() : LocalDateTime.now());
        eventDO.setBusinessDate(req.getBusinessDate() != null ? req.getBusinessDate()
                : (req.getEventTime() != null ? req.getEventTime().toLocalDate() : LocalDateTime.now().toLocalDate()));
        eventDO.setEventType(StockEventTypeEnum.CONSUME_OUT.getCode());
        eventDO.setDirection(StockDirectionEnum.OUT.getCode());
        eventDO.setStockItemId(reserve.getStockItemId());
        eventDO.setSkuCode(reserve.getSkuCode());
        eventDO.setLocationId(reserve.getLocationId());
        eventDO.setQuantity(reserve.getQuantity());
        eventDO.setUnit(reserve.getUnit());
        eventDO.setSourceModule(reserve.getSourceModule());
        eventDO.setSourceRecordId(reserve.getSourceRecordId());
        eventDO.setReferenceNo(req.getReferenceNo() != null ? req.getReferenceNo() : reserve.getReferenceNo());
        eventDO.setClientRequestId(reserve.getIdempotentKey()); // double idempotency
        eventDO.setOperatorUserId(req.getOperatorUserId());
        eventDO.setBalanceAfter(newAvailableQty); // available_qty unchanged after commit
        eventDO.setCreateTime(LocalDateTime.now());
        stockEventMapper.insert(eventDO);

        // --- Step 4b: Update stock_balance.last_event_id to point to CONSUME_OUT event ---
        stockBalanceMapper.updateLastEventId(balance.getId(), req.getTenantId(), eventDO.getId());

        // --- Step 5: UPDATE stock_reserve status (CAS on expectedStatus=RESERVED) ---
        int reserveRows = stockReserveMapper.updateStatus(
                reserve.getId(),
                req.getTenantId(),
                StockReserveStatusEnum.COMMITTED.getCode(),
                StockReserveStatusEnum.RESERVED.getCode(),
                eventDO.getId(),
                String.valueOf(req.getOperatorUserId()),
                LocalDateTime.now()
        );
        if (reserveRows == 0) {
            // Concurrent commit/release already changed the status — rollback balance update + event insert
            throw new StockBusinessException(StockErrorCodeConstants.BALANCE_CONCURRENT_CONFLICT,
                    "Reserve status was already changed by a concurrent transaction (commit)");
        }

        return eventDO.getId();
    }

    // ==================== Validation ====================

    private void validateReserveReq(StockReserveReqDTO req) {
        Objects.requireNonNull(req, "reserve request must not be null");
        if (req.getTenantId() == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "tenantId");
        }
        if (req.getStockItemId() == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "stockItemId");
        }
        if (req.getLocationId() == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "locationId");
        }
        if (req.getSkuCode() == null || req.getSkuCode().isBlank()) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "skuCode");
        }
        if (req.getQuantity() == null || req.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new StockBusinessException(StockErrorCodeConstants.QUANTITY_MUST_BE_POSITIVE);
        }
        if (req.getUnit() == null || req.getUnit().isBlank()) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "unit");
        }
        if (req.getSourceModule() == null || req.getSourceModule().isBlank()) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "sourceModule");
        }
        if (req.getSourceRecordId() == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "sourceRecordId");
        }
        if (req.getIdempotentKey() == null || req.getIdempotentKey().isBlank()) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "idempotentKey");
        }
        if (req.getOperatorUserId() == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "operatorUserId");
        }
    }
}
