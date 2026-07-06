package com.geihou.module.supplychain.api.stock;

import com.geihou.module.supplychain.api.stock.dto.StockBalanceRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockCommitReqDTO;
import com.geihou.module.supplychain.api.stock.dto.StockEventReqDTO;
import com.geihou.module.supplychain.api.stock.dto.StockReleaseReqDTO;
import com.geihou.module.supplychain.api.stock.dto.StockReserveReqDTO;

import java.math.BigDecimal;

/**
 * Stock event API contract (G2-01A first slice, extended in G2-01B1).
 *
 * <p>This is a pure Java contract in the supplychain API module.
 * RPC annotations, implementation, and runtime behavior are out of scope for G2-01A/B1.
 * The implementation is provided in geihou-module-supplychain-biz.
 *
 * <p>G2-01B1: Added reserveStock / releaseStock / commitStock methods.
 *
 * <p>Source: TASK-G2-01A Section 5.1, TASK-G2-01B1 Section 5.1
 */
public interface StockEventApi {

    /**
     * Record a stock event (idempotent).
     *
     * <p>INSERT stock_event (event_type uses the 11 values from the global enum table, CG-12-A).
     * UPDATE stock_balance in the same transaction with optimistic lock (version field).
     * Idempotency: client_request_id (if provided, duplicate requests return the existing event ID).
     *
     * @param req event request DTO
     * @return event ID
     */
    Long recordEvent(StockEventReqDTO req);

    /**
     * Query available balance for a specific item + location.
     *
     * @param tenantId   tenant ID
     * @param itemId     stock item ID
     * @param locationId location ID
     * @return available quantity (0 if no balance record exists)
     */
    BigDecimal getAvailableQty(Long tenantId, Long itemId, Long locationId);

    /**
     * Check whether stock is sufficient for the requested quantity.
     *
     * @param tenantId     tenant ID
     * @param itemId       stock item ID
     * @param locationId   location ID
     * @param requiredQty  required quantity
     * @return true if available qty >= required qty
     */
    boolean checkAvailable(Long tenantId, Long itemId, Long locationId, BigDecimal requiredQty);

    // === G2-01B1 新增方法 ===

    /**
     * 预留库存（reserve）。
     *
     * 语义：
     *   stock_balance.reserved_qty += qty
     *   stock_balance.available_qty -= qty
     *   stock_balance.total_qty 不变
     *   INSERT stock_reserve (status = RESERVED)
     *   不写 stock_event
     *
     * 幂等：同 tenant + idempotent_key 只创建一条 RESERVED 记录
     * 并发安全：乐观锁更新 stock_balance
     *
     * @param req 预留请求
     * @return 预留记录 ID
     */
    Long reserveStock(StockReserveReqDTO req);

    /**
     * 释放预留（release）。
     *
     * 语义：
     *   stock_balance.reserved_qty -= qty
     *   stock_balance.available_qty += qty
     *   stock_balance.total_qty 不变
     *   UPDATE stock_reserve SET status = RELEASED
     *   不写 stock_event
     *
     * 幂等：已 RELEASED 的记录重复 release 返回成功（幂等）
     * 拒绝：已 COMMITTED 的记录不可 release
     *
     * @param req 释放请求
     */
    void releaseStock(StockReleaseReqDTO req);

    /**
     * 提交预留（commit）— 真实扣库存。
     *
     * 语义：
     *   stock_balance.reserved_qty -= qty
     *   stock_balance.total_qty -= qty
     *   stock_balance.available_qty 不变
     *   UPDATE stock_reserve SET status = COMMITTED
     *   INSERT stock_event (event_type = CONSUME_OUT, direction = OUT)
     *
     * 幂等：已 COMMITTED 的记录重复 commit 返回成功
     * 拒绝：已 RELEASED 的记录不可 commit
     *
     * @param req 提交请求
     * @return stock_event ID（CONSUME_OUT 事件 ID）
     */
    Long commitStock(StockCommitReqDTO req);
}
