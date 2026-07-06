package com.geihou.module.supplychain.stock.service;

import com.geihou.module.supplychain.api.stock.dto.SalesOutBomReverseReqDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesOutBomReverseRespDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesReverseRestoreReqDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesReverseRestoreRespDTO;

/**
 * Service for sales-out BOM reverse consumption and sales reverse restore.
 *
 * <p>Given a finished product and a sold quantity, explodes the BOM to
 * raw-material leaves and records a {@code CONSUME_OUT} stock event for
 * each leaf component.
 *
 * <p>Key rules (TASK-G2-02D):
 * <ul>
 *   <li>No parent stock_event is created.</li>
 *   <li>parent_event_id stays null in every created event.</li>
 *   <li>Sales traceability uses sourceModule / sourceRecordId / referenceNo.</li>
 *   <li>One transaction for all component deductions — rollback on any error.</li>
 * </ul>
 *
 * <p>Reverse restore (TASK-G2-02E):
 * <ul>
 *   <li>Locates original raw-material CONSUME_OUT events by source fields.</li>
 *   <li>Creates matching IN restore events with parentEventId = null.</li>
 *   <li>Idempotent: same clientRequestId repeat returns existing events.</li>
 *   <li>Double-restore prevention: different clientRequestId for same source is rejected.</li>
 * </ul>
 */
public interface StockBomReverseService {

    /**
     * Execute sales-out BOM reverse consumption.
     *
     * @param req request DTO
     * @return response with created event IDs and per-component deduction rows
     */
    SalesOutBomReverseRespDTO salesOutWithBomReverse(SalesOutBomReverseReqDTO req);

    /**
     * Execute sales reverse restore.
     *
     * <p>Finds the original raw-material {@code CONSUME_OUT} events created by
     * {@link #salesOutWithBomReverse(SalesOutBomReverseReqDTO)} for the given
     * source sale, and creates matching inbound restore events.
     *
     * @param req request DTO
     * @return response with restore event IDs and per-component rows
     */
    SalesReverseRestoreRespDTO salesReverseRestore(SalesReverseRestoreReqDTO req);
}
