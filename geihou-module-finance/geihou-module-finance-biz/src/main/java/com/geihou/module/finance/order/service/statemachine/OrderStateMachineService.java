package com.geihou.module.finance.order.service.statemachine;

import com.geihou.module.finance.order.dal.dataobject.OrderDO;

/**
 * Order state machine service.
 *
 * <p>Encapsulates all legal status transitions per PRD Section 4.2.
 * Rejects illegal transitions with INVALID_STATUS_TRANSFER.
 * Uses optimistic lock (@Version) with retry.
 * Writes order_event_log for every transition.
 */
public interface OrderStateMachineService {

    /**
     * Transition an order to the target status.
     *
     * @param orderId      order ID
     * @param targetStatus target status code (OrderStatusEnum code)
     * @param operatorUserId operator user ID (null for SYSTEM)
     * @param operatorRole   operator role (CUSTOMER / STAFF / OWNER / SYSTEM)
     * @param payload       JSON payload for event log
     * @return updated order DO
     */
    OrderDO transition(Long orderId, String targetStatus, Long operatorUserId, String operatorRole, String payload);

    /**
     * Check if a transition is legal.
     *
     * @param currentStatus current status code
     * @param targetStatus  target status code
     * @return true if transition is legal
     */
    boolean isLegalTransition(String currentStatus, String targetStatus);
}
