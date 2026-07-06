package com.geihou.module.supplychain.stock.service;

import com.geihou.common.pojo.PageResult;
import com.geihou.module.supplychain.api.stock.dto.StockEventReqDTO;
import com.geihou.module.supplychain.api.stock.enums.StockDirectionEnum;
import com.geihou.module.supplychain.api.stock.enums.StockEventTypeEnum;
import com.geihou.module.supplychain.stock.controller.admin.vo.StockCountSessionCreateReqVO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockBalanceDO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockCountRecordDO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockCountSessionDO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockItemDO;
import com.geihou.module.supplychain.stock.dal.mapper.StockBalanceMapper;
import com.geihou.module.supplychain.stock.dal.mapper.StockCountRecordMapper;
import com.geihou.module.supplychain.stock.dal.mapper.StockCountSessionMapper;
import com.geihou.module.supplychain.stock.dal.mapper.StockItemMapper;
import com.geihou.module.supplychain.stock.enums.StockCountStatusEnum;
import com.geihou.module.supplychain.stock.enums.StockCountTypeEnum;
import com.geihou.module.supplychain.stock.framework.StockBusinessException;
import com.geihou.module.supplychain.stock.framework.StockErrorCodeConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Implementation of {@link StockCountService}.
 *
 * <p>Stock count lifecycle: create → start → record → submit → approve.
 * Approval generates COUNT_ADJUST events via {@link StockEventService#recordEvent}
 * with adjustmentSign +1 (盘盈) / -1 (盘亏), sourceModule=stock_count.
 *
 * <p>Key constraints:
 * <ul>
 *   <li>stock_count_record is INSERT-only (no update/delete mapper methods).</li>
 *   <li>diff_qty != 0 requires diff_reason >= 30 chars (unconditional).</li>
 *   <li>adjustment_event_id is NOT backfilled (INSERT-only constraint).</li>
 *   <li>Idempotent approval: re-approval returns existing result.</li>
 *   <li>Negative variance (盘亏) fails if available_qty insufficient.</li>
 * </ul>
 *
 * <p>Source: TASK-G2-02I-2.
 */
@Service
public class StockCountServiceImpl implements StockCountService {

    /** Evidence photo threshold for |diff_qty × avg_unit_cost| (Review Fix #3). */
    private static final BigDecimal DIFF_AMOUNT_THRESHOLD = BigDecimal.valueOf(100);

    /** Minimum diff_reason length when diff_qty != 0 (Review Fix #3, unconditional). */
    private static final int MIN_DIFF_REASON_LENGTH = 30;

    private static final String SOURCE_MODULE = "stock_count";

    @Autowired
    private StockCountSessionMapper sessionMapper;

    @Autowired
    private StockCountRecordMapper recordMapper;

    @Autowired
    private StockEventService stockEventService;

    @Autowired
    private StockBalanceMapper stockBalanceMapper;

    @Autowired
    private StockItemMapper stockItemMapper;

    // --- 6.1 Create session ---

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockCountSessionDO createSession(StockCountSessionCreateReqVO req) {
        Objects.requireNonNull(req, "request must not be null");

        // --- Validation ---
        if (req.getTenantId() == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "tenantId");
        }
        if (req.getLocationId() == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "locationId");
        }
        if (!StockCountTypeEnum.isValidCode(req.getCountType())) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING,
                    "countType (must be FULL, CYCLE, or SPOT)");
        }
        if (req.getOperatorUserId() == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "operatorUserId");
        }

        // --- Build DO ---
        StockCountSessionDO session = new StockCountSessionDO();
        session.setTenantId(req.getTenantId());
        session.setSessionCode(generateSessionCode(req.getTenantId()));
        session.setLocationId(req.getLocationId());
        session.setCountType(req.getCountType());
        session.setScheduledTime(req.getScheduledTime());
        session.setStatus(StockCountStatusEnum.PLANNING.getCode()); // explicit, not DDL default
        session.setOperatorUserId(req.getOperatorUserId());
        session.setCreator(String.valueOf(req.getOperatorUserId()));
        session.setCreateTime(LocalDateTime.now());
        session.setUpdater(String.valueOf(req.getOperatorUserId()));
        session.setUpdateTime(LocalDateTime.now());
        session.setDeleted(false);

        // --- Insert ---
        sessionMapper.insert(session);

        return session;
    }

    // --- 6.2 Start count ---

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockCountSessionDO startCount(Long sessionId, Long tenantId, Long operatorUserId) {
        StockCountSessionDO session = sessionMapper.selectByIdAndTenant(sessionId, tenantId);
        if (session == null) {
            throw new StockBusinessException(StockErrorCodeConstants.COUNT_SESSION_NOT_FOUND);
        }
        if (!StockCountStatusEnum.PLANNING.getCode().equals(session.getStatus())) {
            throw new StockBusinessException(StockErrorCodeConstants.COUNT_INVALID_STATUS,
                    "current status: " + session.getStatus() + ", expected: PLANNING");
        }

        int rows = sessionMapper.updateStartByTenant(
                sessionId, tenantId,
                StockCountStatusEnum.IN_PROGRESS.getCode(),
                LocalDateTime.now(),
                operatorUserId,
                StockCountStatusEnum.PLANNING.getCode(),
                String.valueOf(operatorUserId),
                LocalDateTime.now()
        );
        if (rows == 0) {
            throw new StockBusinessException(StockErrorCodeConstants.COUNT_INVALID_STATUS,
                    "concurrent modification detected");
        }

        return sessionMapper.selectByIdAndTenant(sessionId, tenantId);
    }

    // --- 6.3 Record count (INSERT-only, complete record) ---

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockCountRecordDO recordCount(Long sessionId, Long tenantId, Long stockItemId,
                                           BigDecimal actualQty, String diffReason,
                                           String evidenceUrl, Long operatorUserId) {
        // --- Validate session ---
        StockCountSessionDO session = sessionMapper.selectByIdAndTenant(sessionId, tenantId);
        if (session == null) {
            throw new StockBusinessException(StockErrorCodeConstants.COUNT_SESSION_NOT_FOUND);
        }
        if (!StockCountStatusEnum.IN_PROGRESS.getCode().equals(session.getStatus())) {
            throw new StockBusinessException(StockErrorCodeConstants.COUNT_INVALID_STATUS,
                    "current status: " + session.getStatus() + ", expected: IN_PROGRESS");
        }

        // --- Validate inputs ---
        Objects.requireNonNull(stockItemId, "stockItemId must not be null");
        Objects.requireNonNull(actualQty, "actualQty must not be null");
        if (actualQty.compareTo(BigDecimal.ZERO) < 0) {
            throw new StockBusinessException(StockErrorCodeConstants.QUANTITY_MUST_BE_POSITIVE,
                    "actualQty must be >= 0");
        }

        // --- Read system_qty and avg_unit_cost from stock_balance ---
        StockBalanceDO balance = stockBalanceMapper.selectByTenantItemLocation(
                tenantId, stockItemId, session.getLocationId());
        if (balance == null) {
            throw new StockBusinessException(StockErrorCodeConstants.COUNT_RECORD_NOT_FOUND,
                    "no stock_balance record found for stockItemId=" + stockItemId +
                    " at locationId=" + session.getLocationId());
        }

        BigDecimal systemQty = balance.getAvailableQty() != null
                ? balance.getAvailableQty() : BigDecimal.ZERO;
        BigDecimal avgUnitCost = balance.getAvgUnitCost() != null
                ? balance.getAvgUnitCost() : BigDecimal.ZERO;

        // --- Calculate diff_qty ---
        BigDecimal diffQty = actualQty.subtract(systemQty);

        // --- Validate diff_reason (unconditional for diff_qty != 0, Review Fix #3) ---
        if (diffQty.compareTo(BigDecimal.ZERO) != 0) {
            if (diffReason == null || diffReason.isBlank()) {
                throw new StockBusinessException(StockErrorCodeConstants.COUNT_DIFF_REASON_REQUIRED);
            }
            if (diffReason.length() < MIN_DIFF_REASON_LENGTH) {
                throw new StockBusinessException(StockErrorCodeConstants.COUNT_DIFF_REASON_REQUIRED,
                        "diff_reason must be >= " + MIN_DIFF_REASON_LENGTH + " chars, got: " + diffReason.length());
            }
        }

        // --- Validate evidence_url for high variance (Review Fix #3) ---
        if (diffQty.compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal diffAmount = diffQty.abs().multiply(avgUnitCost);
            if (diffAmount.compareTo(DIFF_AMOUNT_THRESHOLD) > 0) {
                if (evidenceUrl == null || evidenceUrl.isBlank()) {
                    throw new StockBusinessException(StockErrorCodeConstants.COUNT_DIFF_EVIDENCE_REQUIRED);
                }
            }
        }

        // --- INSERT complete record (no pre-insert + update) ---
        StockCountRecordDO record = new StockCountRecordDO();
        record.setTenantId(tenantId);
        record.setSessionId(sessionId);
        record.setStockItemId(stockItemId);
        record.setSystemQty(systemQty);
        record.setActualQty(actualQty);
        record.setDiffQty(diffQty);
        record.setDiffReason(diffQty.compareTo(BigDecimal.ZERO) != 0 ? diffReason : null);
        record.setEvidenceUrl(evidenceUrl);
        // adjustment_event_id NOT set (remains null, not backfilled)
        record.setCreateTime(LocalDateTime.now());

        recordMapper.insert(record);

        return record;
    }

    // --- 6.4 Submit count (IN_PROGRESS → DIFF_REVIEW) ---

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockCountSessionDO submitCount(Long sessionId, Long tenantId, Long operatorUserId) {
        StockCountSessionDO session = sessionMapper.selectByIdAndTenant(sessionId, tenantId);
        if (session == null) {
            throw new StockBusinessException(StockErrorCodeConstants.COUNT_SESSION_NOT_FOUND);
        }
        if (!StockCountStatusEnum.IN_PROGRESS.getCode().equals(session.getStatus())) {
            throw new StockBusinessException(StockErrorCodeConstants.COUNT_INVALID_STATUS,
                    "current status: " + session.getStatus() + ", expected: IN_PROGRESS");
        }

        // --- Validate at least one record exists ---
        long recordCount = recordMapper.countBySession(sessionId, tenantId);
        if (recordCount == 0) {
            throw new StockBusinessException(StockErrorCodeConstants.COUNT_NOT_ALL_RECORDED,
                    "no records found for session");
        }

        // --- Compute summary ---
        List<StockCountRecordDO> records = recordMapper.selectBySession(sessionId, tenantId);
        int totalItems = records.size();
        int diffItems = 0;
        BigDecimal totalDiffValue = BigDecimal.ZERO;

        for (StockCountRecordDO record : records) {
            if (record.getDiffQty().compareTo(BigDecimal.ZERO) != 0) {
                diffItems++;
                // Read avg_unit_cost from stock_balance for value calculation
                StockBalanceDO bal = stockBalanceMapper.selectByTenantItemLocation(
                        tenantId, record.getStockItemId(), session.getLocationId());
                BigDecimal unitCost = (bal != null && bal.getAvgUnitCost() != null)
                        ? bal.getAvgUnitCost() : BigDecimal.ZERO;
                totalDiffValue = totalDiffValue.add(record.getDiffQty().abs().multiply(unitCost));
            }
        }

        // --- Update session status + summary ---
        int rows = sessionMapper.updateStatusByTenant(
                sessionId, tenantId,
                StockCountStatusEnum.DIFF_REVIEW.getCode(),
                null, // approver not set yet
                null, // approve_time not set yet
                null, // end_time not set yet
                totalItems,
                diffItems,
                totalDiffValue,
                StockCountStatusEnum.IN_PROGRESS.getCode(),
                String.valueOf(operatorUserId),
                LocalDateTime.now()
        );
        if (rows == 0) {
            throw new StockBusinessException(StockErrorCodeConstants.COUNT_INVALID_STATUS,
                    "concurrent modification detected");
        }

        return sessionMapper.selectByIdAndTenant(sessionId, tenantId);
    }

    // --- 6.5 Approve count (DIFF_REVIEW → ADJUSTED, single transaction) ---

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockCountSessionDO approveCount(Long sessionId, Long tenantId, Long approverUserId) {
        // 1. Query session, validate tenant + status = DIFF_REVIEW
        StockCountSessionDO session = sessionMapper.selectByIdAndTenant(sessionId, tenantId);
        if (session == null) {
            throw new StockBusinessException(StockErrorCodeConstants.COUNT_SESSION_NOT_FOUND);
        }

        // Idempotent: if already ADJUSTED, return existing result
        if (StockCountStatusEnum.ADJUSTED.getCode().equals(session.getStatus())) {
            return session;
        }

        if (!StockCountStatusEnum.DIFF_REVIEW.getCode().equals(session.getStatus())) {
            throw new StockBusinessException(StockErrorCodeConstants.COUNT_INVALID_STATUS,
                    "current status: " + session.getStatus() + ", expected: DIFF_REVIEW");
        }

        // 2. Query all diff records (diff_qty != 0)
        List<StockCountRecordDO> diffRecords = recordMapper.selectDiffBySession(sessionId, tenantId);

        // 3. Generate COUNT_ADJUST events for each diff record
        for (StockCountRecordDO record : diffRecords) {
            BigDecimal absDiff = record.getDiffQty().abs();
            int sign = record.getDiffQty().compareTo(BigDecimal.ZERO) > 0 ? 1 : -1;

            // Read stock_item for skuCode and unit
            StockItemDO item = stockItemMapper.selectByIdAndTenant(record.getStockItemId(), tenantId);
            if (item == null) {
                throw new StockBusinessException(StockErrorCodeConstants.COUNT_RECORD_NOT_FOUND,
                        "stock_item not found for id=" + record.getStockItemId());
            }

            // Read avg_unit_cost from stock_balance
            StockBalanceDO balance = stockBalanceMapper.selectByTenantItemLocation(
                    tenantId, record.getStockItemId(), session.getLocationId());
            BigDecimal unitCost = (balance != null && balance.getAvgUnitCost() != null)
                    ? balance.getAvgUnitCost() : BigDecimal.ZERO;
            BigDecimal totalCost = absDiff.multiply(unitCost);

            StockEventReqDTO eventReq = new StockEventReqDTO();
            eventReq.setTenantId(tenantId);
            eventReq.setEventTime(LocalDateTime.now());
            eventReq.setBusinessDate(LocalDateTime.now().toLocalDate());
            eventReq.setEventType(StockEventTypeEnum.COUNT_ADJUST.getCode());
            eventReq.setDirection(StockDirectionEnum.INTERNAL.getCode());
            eventReq.setStockItemId(record.getStockItemId());
            eventReq.setSkuCode(item.getSkuCode());
            eventReq.setLocationId(session.getLocationId());
            eventReq.setQuantity(absDiff);           // positive
            eventReq.setAdjustmentSign(sign);         // +1 盘盈 / -1 盘亏
            eventReq.setUnit(item.getUnit());
            eventReq.setUnitCost(unitCost);
            eventReq.setTotalCost(totalCost);
            eventReq.setSourceModule(SOURCE_MODULE);
            eventReq.setSourceRecordId(record.getId());
            eventReq.setReferenceNo(session.getSessionCode());
            // Deterministic clientRequestId for idempotency
            eventReq.setClientRequestId("stock-count-approve-" + sessionId + "-" + record.getId());
            eventReq.setOperatorUserId(approverUserId);

            // Review Fix #3: set adjustmentReason from record.diffReason
            eventReq.setAdjustmentReason(record.getDiffReason());

            // recordEvent may throw INSUFFICIENT_STOCK if 盘亏 and available_qty < |diff|
            Long eventId = stockEventService.recordEvent(eventReq);

            // Review Fix #1: do NOT backfill adjustment_event_id (INSERT-only, no UPDATE)
        }

        // 4. Update session status → ADJUSTED (not through APPROVED)
        int rows = sessionMapper.updateStatusByTenant(
                sessionId, tenantId,
                StockCountStatusEnum.ADJUSTED.getCode(),
                approverUserId,
                LocalDateTime.now(),
                LocalDateTime.now(), // end_time
                session.getTotalItems(),
                session.getDiffItems(),
                session.getTotalDiffValue(),
                StockCountStatusEnum.DIFF_REVIEW.getCode(),
                String.valueOf(approverUserId),
                LocalDateTime.now()
        );
        if (rows == 0) {
            // Concurrent modification — re-read and check if already adjusted
            StockCountSessionDO refreshed = sessionMapper.selectByIdAndTenant(sessionId, tenantId);
            if (refreshed != null && StockCountStatusEnum.ADJUSTED.getCode().equals(refreshed.getStatus())) {
                return refreshed;
            }
            throw new StockBusinessException(StockErrorCodeConstants.COUNT_INVALID_STATUS,
                    "concurrent modification detected");
        }

        return sessionMapper.selectByIdAndTenant(sessionId, tenantId);
    }

    // --- 6.7 Query ---

    @Override
    public StockCountSessionDO getSession(Long sessionId, Long tenantId) {
        return sessionMapper.selectByIdAndTenant(sessionId, tenantId);
    }

    @Override
    public PageResult<StockCountSessionDO> pageSession(Long tenantId, String status, String countType,
                                                        Long locationId, Integer pageNo, Integer pageSize) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(pageNo, "pageNo must not be null");
        Objects.requireNonNull(pageSize, "pageSize must not be null");

        // Use lambda-based selectPage with available filters
        // BaseMapperX supports up to 2 field filters; for more, use wrapper
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StockCountSessionDO> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(StockCountSessionDO::getTenantId, tenantId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(StockCountSessionDO::getStatus, status);
        }
        if (countType != null && !countType.isBlank()) {
            wrapper.eq(StockCountSessionDO::getCountType, countType);
        }
        if (locationId != null) {
            wrapper.eq(StockCountSessionDO::getLocationId, locationId);
        }
        wrapper.orderByDesc(StockCountSessionDO::getId);

        return sessionMapper.selectPage(pageNo, pageSize, wrapper);
    }

    @Override
    public List<StockCountRecordDO> listRecords(Long sessionId, Long tenantId) {
        return recordMapper.selectBySession(sessionId, tenantId);
    }

    // --- Helpers ---

    /**
     * Generate a unique session code: COUNT-{tenantId}-{timestamp}{seq}
     */
    private String generateSessionCode(Long tenantId) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int seq = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "COUNT-" + tenantId + "-" + timestamp + "-" + seq;
    }
}
