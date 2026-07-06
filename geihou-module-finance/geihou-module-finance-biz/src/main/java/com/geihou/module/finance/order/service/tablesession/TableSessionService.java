package com.geihou.module.finance.order.service.tablesession;

import com.geihou.module.finance.order.dal.dataobject.OrderDO;
import com.geihou.module.finance.order.dal.dataobject.OrderTableSessionDO;

import java.util.List;

/**
 * Table session service for managing dine-in table sessions.
 *
 * <p>G1-01D slice: open, query, settle, close, and DINE_IN validation.
 * State flow: OPEN → ORDERING → {SERVING} → SETTLING → CLOSED
 * (SERVING is optional per CG-TS2)
 */
public interface TableSessionService {

    /**
     * Open a new table session (staff operation).
     *
     * @param shopId        shop ID
     * @param tableId       table ID
     * @param tableNo       table number
     * @param customerCount customer count (optional)
     * @return created session DO
     */
    OrderTableSessionDO openSession(Long shopId, Long tableId, String tableNo, Integer customerCount);

    /**
     * Get session by session_no, including associated orders.
     *
     * @param sessionNo session number
     * @return session DO
     */
    OrderTableSessionDO getBySessionNo(String sessionNo);

    /**
     * Get orders associated with a session.
     *
     * @param sessionId session ID
     * @return list of orders
     */
    List<OrderDO> getSessionOrders(Long sessionId);

    /**
     * Settle a session: transition ORDERING/SERVING → SETTLING,
     * aggregate total_amount/paid_amount/order_count from orders.
     *
     * @param sessionNo session number
     * @return updated session DO
     */
    OrderTableSessionDO settleSession(String sessionNo);

    /**
     * Close a session: transition SETTLING → CLOSED, set close_time.
     *
     * @param sessionNo session number
     * @return updated session DO
     */
    OrderTableSessionDO closeSession(String sessionNo);

    /**
     * Validate a table session for DINE_IN order creation.
     * Checks: session exists, status is OPEN or ORDERING.
     * If status is OPEN, transitions to ORDERING.
     *
     * @param tableSessionId table session ID
     * @return validated session DO (status may be updated to ORDERING)
     */
    OrderTableSessionDO validateForDineIn(Long tableSessionId);

    /**
     * Validate that non-DINE_IN channels do not carry a tableSessionId.
     *
     * @param tableSessionId the tableSessionId from request (should be null)
     */
    void validateNonDineIn(Long tableSessionId);
}
