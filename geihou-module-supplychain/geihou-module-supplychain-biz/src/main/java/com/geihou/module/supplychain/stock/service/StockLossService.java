package com.geihou.module.supplychain.stock.service;

import com.geihou.common.pojo.PageResult;
import com.geihou.module.supplychain.stock.controller.admin.vo.StockLossCreateReqVO;
import com.geihou.module.supplychain.stock.controller.admin.vo.StockLossRespVO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockLossDO;

/**
 * Stock loss/scrap service interface.
 *
 * <p>Provides create / approve / reject / cancel / query operations for
 * loss and scrap records. Stock deduction is always through
 * {@link StockEventService#recordEvent} — never by directly modifying balance.
 *
 * <p>Source: TASK-G2-02I-3.
 */
public interface StockLossService {

    /**
     * Create a loss/scrap record.
     *
     * <p>Low amount (≤ 1000): auto-APPROVED, stock deducted immediately.
     * High amount (> 1000): PENDING_APPROVE, stock deducted on approval.
     *
     * @param req create request
     * @return created loss record
     */
    StockLossDO createLoss(StockLossCreateReqVO req);

    /**
     * Approve a pending loss/scrap record.
     *
     * <p>Transitions PENDING_APPROVE → APPROVED, deducts stock via recordEvent.
     *
     * @param lossId         loss record ID
     * @param tenantId       tenant ID
     * @param approverUserId approver user ID
     * @return updated loss record
     */
    StockLossDO approveLoss(Long lossId, Long tenantId, Long approverUserId);

    /**
     * Reject a pending loss/scrap record.
     *
     * <p>Transitions PENDING_APPROVE → REJECTED, no stock deduction.
     *
     * @param lossId         loss record ID
     * @param tenantId       tenant ID
     * @param approverUserId approver user ID
     * @param rejectReason   reject reason
     * @return updated loss record
     */
    StockLossDO rejectLoss(Long lossId, Long tenantId, Long approverUserId, String rejectReason);

    /**
     * Cancel a pending loss/scrap record.
     *
     * <p>Transitions PENDING_APPROVE → CANCELLED, no stock deduction.
     *
     * @param lossId          loss record ID
     * @param tenantId        tenant ID
     * @param operatorUserId  operator user ID
     * @return updated loss record
     */
    StockLossDO cancelLoss(Long lossId, Long tenantId, Long operatorUserId);

    /**
     * Get a loss record by ID (tenant-isolated).
     *
     * @param lossId   loss record ID
     * @param tenantId tenant ID
     * @return loss record or null if not found
     */
    StockLossDO getLoss(Long lossId, Long tenantId);

    /**
     * Page query loss records (tenant-isolated).
     *
     * @param tenantId tenant ID
     * @param status   optional status filter (nullable)
     * @param pageNo   page number (1-based)
     * @param pageSize page size
     * @return paged result
     */
    PageResult<StockLossDO> pageLoss(Long tenantId, String status, Integer pageNo, Integer pageSize);
}
