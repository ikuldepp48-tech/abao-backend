package com.geihou.module.supplychain.stock.service;

import com.geihou.common.pojo.PageResult;
import com.geihou.module.supplychain.api.stock.dto.StockEventReqDTO;
import com.geihou.module.supplychain.api.stock.enums.StockDirectionEnum;
import com.geihou.module.supplychain.api.stock.enums.StockEventTypeEnum;
import com.geihou.module.supplychain.stock.controller.admin.vo.StockLossCreateReqVO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockBalanceDO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockLossDO;
import com.geihou.module.supplychain.stock.dal.mapper.StockBalanceMapper;
import com.geihou.module.supplychain.stock.dal.mapper.StockLossMapper;
import com.geihou.module.supplychain.stock.enums.LossReasonEnum;
import com.geihou.module.supplychain.stock.framework.StockBusinessException;
import com.geihou.module.supplychain.stock.framework.StockErrorCodeConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Implementation of {@link StockLossService}.
 *
 * <p>Creates loss/scrap records with approval workflow.
 * Low amount (≤ 1000) is auto-approved with immediate stock deduction.
 * High amount (> 1000) requires manual approval before stock deduction.
 *
 * <p>Stock deduction is always through {@link StockEventService#recordEvent}
 * with non-null clientRequestId for idempotency:
 * <ul>
 *   <li>Low amount direct: {@code stock-loss-create-{lossId}}</li>
 *   <li>High amount approve: {@code stock-loss-approve-{lossId}}</li>
 * </ul>
 *
 * <p>Source: TASK-G2-02I-3.
 */
@Service
public class StockLossServiceImpl implements StockLossService {

    private static final BigDecimal HIGH_AMOUNT_THRESHOLD = BigDecimal.valueOf(1000);

    private static final String STATUS_PENDING_APPROVE = "PENDING_APPROVE";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_CANCELLED = "CANCELLED";

    private static final String LOSS_TYPE_LOSS = "LOSS";
    private static final String LOSS_TYPE_SCRAP = "SCRAP";

    private static final String SOURCE_MODULE = "stock_loss";

    @Autowired
    private StockLossMapper stockLossMapper;

    @Autowired
    private StockEventService stockEventService;

    @Autowired
    private StockBalanceMapper stockBalanceMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockLossDO createLoss(StockLossCreateReqVO req) {
        // --- Validation ---
        validateCreateRequest(req);

        // --- Read avg_unit_cost from stock_balance for amount estimation ---
        StockBalanceDO balance = stockBalanceMapper.selectByTenantItemLocation(
                req.getTenantId(), req.getStockItemId(), req.getLocationId());
        BigDecimal unitCost = BigDecimal.ZERO;
        if (balance != null && balance.getAvgUnitCost() != null) {
            unitCost = balance.getAvgUnitCost();
        }
        BigDecimal totalAmount = req.getQuantity().multiply(unitCost);

        // --- Determine status based on amount ---
        boolean isHighAmount = totalAmount.compareTo(HIGH_AMOUNT_THRESHOLD) > 0;
        String status = isHighAmount ? STATUS_PENDING_APPROVE : STATUS_APPROVED;

        // --- Build DO ---
        StockLossDO lossDO = new StockLossDO();
        lossDO.setTenantId(req.getTenantId());
        lossDO.setLossNo(generateLossNo(req.getTenantId()));
        lossDO.setLossType(req.getLossType());
        lossDO.setStockItemId(req.getStockItemId());
        lossDO.setSkuCode(req.getSkuCode());
        lossDO.setLocationId(req.getLocationId());
        lossDO.setQuantity(req.getQuantity());
        lossDO.setUnit(req.getUnit());
        lossDO.setUnitCost(unitCost);
        lossDO.setTotalAmount(totalAmount);
        lossDO.setLossReason(req.getLossReason());
        lossDO.setRemark(req.getRemark());
        lossDO.setStatus(status);
        lossDO.setOperatorUserId(req.getOperatorUserId());
        lossDO.setCreator(String.valueOf(req.getOperatorUserId()));
        lossDO.setCreateTime(LocalDateTime.now());
        lossDO.setUpdater(String.valueOf(req.getOperatorUserId()));
        lossDO.setUpdateTime(LocalDateTime.now());
        lossDO.setDeleted(false);

        // For low amount: set approver = operator, approve_time = now
        if (!isHighAmount) {
            lossDO.setApproverUserId(req.getOperatorUserId());
            lossDO.setApproveTime(LocalDateTime.now());
        }

        // --- Insert ---
        stockLossMapper.insert(lossDO);

        // --- For low amount: deduct stock immediately via recordEvent ---
        if (!isHighAmount) {
            Long eventId = recordLossEvent(lossDO, "stock-loss-create-" + lossDO.getId(), req.getOperatorUserId());
            // Update loss record with stock_event_id
            stockLossMapper.updateStatusByTenant(
                    lossDO.getId(),
                    lossDO.getTenantId(),
                    STATUS_APPROVED,
                    req.getOperatorUserId(),
                    lossDO.getApproveTime(),
                    null,
                    eventId,
                    STATUS_APPROVED, // expectStatus already APPROVED (just inserted)
                    String.valueOf(req.getOperatorUserId()),
                    LocalDateTime.now()
            );
            lossDO.setStockEventId(eventId);
        }

        return lossDO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockLossDO approveLoss(Long lossId, Long tenantId, Long approverUserId) {
        StockLossDO loss = stockLossMapper.selectByIdAndTenant(lossId, tenantId);
        if (loss == null) {
            throw new StockBusinessException(StockErrorCodeConstants.LOSS_NOT_FOUND);
        }
        if (STATUS_APPROVED.equals(loss.getStatus())) {
            // Idempotent: duplicate approval returns existing result, no re-deduction
            return loss;
        }
        if (STATUS_REJECTED.equals(loss.getStatus())) {
            throw new StockBusinessException(StockErrorCodeConstants.LOSS_ALREADY_REJECTED);
        }
        if (!STATUS_PENDING_APPROVE.equals(loss.getStatus())) {
            throw new StockBusinessException(StockErrorCodeConstants.LOSS_INVALID_STATUS,
                    "current status: " + loss.getStatus());
        }

        // Deduct stock via recordEvent with idempotency key
        Long eventId = recordLossEvent(loss, "stock-loss-approve-" + lossId, approverUserId);

        // Update status
        int rows = stockLossMapper.updateStatusByTenant(
                lossId,
                tenantId,
                STATUS_APPROVED,
                approverUserId,
                LocalDateTime.now(),
                null,
                eventId,
                STATUS_PENDING_APPROVE,
                String.valueOf(approverUserId),
                LocalDateTime.now()
        );
        if (rows == 0) {
            // Concurrent modification — re-read and check if already approved
            StockLossDO refreshed = stockLossMapper.selectByIdAndTenant(lossId, tenantId);
            if (refreshed != null && STATUS_APPROVED.equals(refreshed.getStatus())) {
                // Idempotent: already approved by another thread, return existing
                return refreshed;
            }
            throw new StockBusinessException(StockErrorCodeConstants.LOSS_INVALID_STATUS,
                    "concurrent modification detected");
        }

        return stockLossMapper.selectByIdAndTenant(lossId, tenantId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockLossDO rejectLoss(Long lossId, Long tenantId, Long approverUserId, String rejectReason) {
        StockLossDO loss = stockLossMapper.selectByIdAndTenant(lossId, tenantId);
        if (loss == null) {
            throw new StockBusinessException(StockErrorCodeConstants.LOSS_NOT_FOUND);
        }
        if (STATUS_APPROVED.equals(loss.getStatus())) {
            throw new StockBusinessException(StockErrorCodeConstants.LOSS_ALREADY_APPROVED);
        }
        if (STATUS_REJECTED.equals(loss.getStatus())) {
            throw new StockBusinessException(StockErrorCodeConstants.LOSS_ALREADY_REJECTED);
        }
        if (!STATUS_PENDING_APPROVE.equals(loss.getStatus())) {
            throw new StockBusinessException(StockErrorCodeConstants.LOSS_INVALID_STATUS,
                    "current status: " + loss.getStatus());
        }

        int rows = stockLossMapper.updateStatusByTenant(
                lossId,
                tenantId,
                STATUS_REJECTED,
                approverUserId,
                LocalDateTime.now(),
                rejectReason,
                null,
                STATUS_PENDING_APPROVE,
                String.valueOf(approverUserId),
                LocalDateTime.now()
        );
        if (rows == 0) {
            throw new StockBusinessException(StockErrorCodeConstants.LOSS_INVALID_STATUS,
                    "concurrent modification detected");
        }

        return stockLossMapper.selectByIdAndTenant(lossId, tenantId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockLossDO cancelLoss(Long lossId, Long tenantId, Long operatorUserId) {
        StockLossDO loss = stockLossMapper.selectByIdAndTenant(lossId, tenantId);
        if (loss == null) {
            throw new StockBusinessException(StockErrorCodeConstants.LOSS_NOT_FOUND);
        }
        if (!STATUS_PENDING_APPROVE.equals(loss.getStatus())) {
            throw new StockBusinessException(StockErrorCodeConstants.LOSS_INVALID_STATUS,
                    "only PENDING_APPROVE can be cancelled, current: " + loss.getStatus());
        }

        int rows = stockLossMapper.updateStatusByTenant(
                lossId,
                tenantId,
                STATUS_CANCELLED,
                operatorUserId,
                LocalDateTime.now(),
                null,
                null,
                STATUS_PENDING_APPROVE,
                String.valueOf(operatorUserId),
                LocalDateTime.now()
        );
        if (rows == 0) {
            throw new StockBusinessException(StockErrorCodeConstants.LOSS_INVALID_STATUS,
                    "concurrent modification detected");
        }

        return stockLossMapper.selectByIdAndTenant(lossId, tenantId);
    }

    @Override
    public StockLossDO getLoss(Long lossId, Long tenantId) {
        return stockLossMapper.selectByIdAndTenant(lossId, tenantId);
    }

    @Override
    public PageResult<StockLossDO> pageLoss(Long tenantId, String status, Integer pageNo, Integer pageSize) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(pageNo, "pageNo must not be null");
        Objects.requireNonNull(pageSize, "pageSize must not be null");

        if (status != null && !status.isBlank()) {
            return stockLossMapper.selectPage(
                    com.geihou.module.supplychain.stock.dal.dataobject.StockLossDO::getTenantId, tenantId,
                    com.geihou.module.supplychain.stock.dal.dataobject.StockLossDO::getStatus, status,
                    pageNo, pageSize);
        } else {
            return stockLossMapper.selectPage(
                    com.geihou.module.supplychain.stock.dal.dataobject.StockLossDO::getTenantId, tenantId,
                    pageNo, pageSize);
        }
    }

    // --- Private helpers ---

    private void validateCreateRequest(StockLossCreateReqVO req) {
        Objects.requireNonNull(req, "request must not be null");
        if (req.getTenantId() == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "tenantId");
        }
        if (req.getLossType() == null || (!LOSS_TYPE_LOSS.equals(req.getLossType()) && !LOSS_TYPE_SCRAP.equals(req.getLossType()))) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "lossType (must be LOSS or SCRAP)");
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
        if (!LossReasonEnum.isValidCode(req.getLossReason())) {
            throw new StockBusinessException(StockErrorCodeConstants.EVENT_TYPE_INVALID,
                    "invalid lossReason: " + req.getLossReason());
        }
        if (LossReasonEnum.OTHER.getCode().equals(req.getLossReason()) &&
                (req.getRemark() == null || req.getRemark().isBlank())) {
            throw new StockBusinessException(StockErrorCodeConstants.LOSS_REASON_REMARK_REQUIRED);
        }
        if (req.getOperatorUserId() == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "operatorUserId");
        }
    }

    /**
     * Record a LOSS_OUT or SCRAP_OUT event via StockEventService.
     *
     * @param loss            the loss record
     * @param clientRequestId idempotency key (non-null)
     * @param operatorUserId  operator user ID
     * @return stock event ID
     */
    private Long recordLossEvent(StockLossDO loss, String clientRequestId, Long operatorUserId) {
        StockEventReqDTO eventReq = new StockEventReqDTO();
        eventReq.setTenantId(loss.getTenantId());
        eventReq.setEventTime(LocalDateTime.now());
        eventReq.setBusinessDate(LocalDateTime.now().toLocalDate());

        // Determine event type based on loss type
        String eventType = LOSS_TYPE_SCRAP.equals(loss.getLossType())
                ? StockEventTypeEnum.SCRAP_OUT.getCode()
                : StockEventTypeEnum.LOSS_OUT.getCode();
        eventReq.setEventType(eventType);
        eventReq.setDirection(StockDirectionEnum.OUT.getCode());

        eventReq.setStockItemId(loss.getStockItemId());
        eventReq.setSkuCode(loss.getSkuCode());
        eventReq.setLocationId(loss.getLocationId());
        eventReq.setQuantity(loss.getQuantity());
        eventReq.setUnit(loss.getUnit());
        eventReq.setUnitCost(loss.getUnitCost());
        eventReq.setTotalCost(loss.getTotalAmount());

        eventReq.setSourceModule(SOURCE_MODULE);
        eventReq.setSourceRecordId(loss.getId());
        eventReq.setReferenceNo(loss.getLossNo());

        // clientRequestId MUST be non-null for idempotency
        eventReq.setClientRequestId(clientRequestId);
        eventReq.setOperatorUserId(operatorUserId);

        return stockEventService.recordEvent(eventReq);
    }

    /**
     * Generate a unique loss number: LOSS-{tenantId}-{timestamp}{seq}
     */
    private String generateLossNo(Long tenantId) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int seq = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "LOSS-" + tenantId + "-" + timestamp + "-" + seq;
    }
}
