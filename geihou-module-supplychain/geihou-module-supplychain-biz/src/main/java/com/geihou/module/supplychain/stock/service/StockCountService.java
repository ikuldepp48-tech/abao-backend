package com.geihou.module.supplychain.stock.service;

import com.geihou.common.pojo.PageResult;
import com.geihou.module.supplychain.stock.controller.admin.vo.StockCountSessionCreateReqVO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockCountRecordDO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockCountSessionDO;

import java.math.BigDecimal;
import java.util.List;

/**
 * Stock count service interface (G2-02I-2).
 *
 * <p>Provides create / start / record / submit / approve / query operations
 * for stock count sessions. Stock adjustments are always through
 * {@link StockEventService#recordEvent} — never by directly modifying balance.
 *
 * <p>State machine: PLANNING → IN_PROGRESS → DIFF_REVIEW → ADJUSTED
 *
 * <p>Source: TASK-G2-02I-2.
 */
public interface StockCountService {

    /**
     * Create a stock count session (status = PLANNING).
     *
     * @param req create request
     * @return created session
     */
    StockCountSessionDO createSession(StockCountSessionCreateReqVO req);

    /**
     * Start a stock count session (PLANNING → IN_PROGRESS).
     *
     * @param sessionId       session ID
     * @param tenantId        tenant ID
     * @param operatorUserId  operator user ID
     * @return updated session
     */
    StockCountSessionDO startCount(Long sessionId, Long tenantId, Long operatorUserId);

    /**
     * Record a stock count detail (INSERT complete record, no update/delete).
     *
     * @param sessionId      session ID
     * @param tenantId       tenant ID
     * @param stockItemId    stock item ID
     * @param actualQty      actual counted quantity
     * @param diffReason     diff reason (required >=30 chars when diff_qty != 0)
     * @param evidenceUrl    evidence URL (required for high variance)
     * @param operatorUserId operator user ID
     * @return inserted record
     */
    StockCountRecordDO recordCount(Long sessionId, Long tenantId, Long stockItemId,
                                    BigDecimal actualQty, String diffReason,
                                    String evidenceUrl, Long operatorUserId);

    /**
     * Submit count recording (IN_PROGRESS → DIFF_REVIEW).
     * Computes summary: total_items, diff_items, total_diff_value.
     *
     * @param sessionId       session ID
     * @param tenantId        tenant ID
     * @param operatorUserId  operator user ID
     * @return updated session
     */
    StockCountSessionDO submitCount(Long sessionId, Long tenantId, Long operatorUserId);

    /**
     * Approve count session (DIFF_REVIEW → ADJUSTED).
     * Generates COUNT_ADJUST events for all diff_qty != 0 records.
     * Idempotent: re-approval returns existing result without re-adjustment.
     *
     * @param sessionId       session ID
     * @param tenantId        tenant ID
     * @param approverUserId  approver user ID
     * @return updated session
     */
    StockCountSessionDO approveCount(Long sessionId, Long tenantId, Long approverUserId);

    /**
     * Get a session by ID (tenant-isolated).
     *
     * @param sessionId session ID
     * @param tenantId  tenant ID
     * @return session or null if not found
     */
    StockCountSessionDO getSession(Long sessionId, Long tenantId);

    /**
     * Page query sessions (tenant-isolated).
     *
     * @param tenantId  tenant ID
     * @param status    optional status filter
     * @param countType optional count type filter
     * @param locationId optional location ID filter
     * @param pageNo    page number (1-based)
     * @param pageSize  page size
     * @return paged result
     */
    PageResult<StockCountSessionDO> pageSession(Long tenantId, String status, String countType,
                                                 Long locationId, Integer pageNo, Integer pageSize);

    /**
     * List all records for a session (tenant-isolated).
     *
     * @param sessionId session ID
     * @param tenantId  tenant ID
     * @return list of records
     */
    List<StockCountRecordDO> listRecords(Long sessionId, Long tenantId);
}
