package com.geihou.module.finance.order.service.statemachine;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.api.order.enums.OrderStatusEnum;
import com.geihou.module.finance.order.dal.dataobject.OrderDO;
import com.geihou.module.finance.order.dal.dataobject.OrderEventLogDO;
import com.geihou.module.finance.order.dal.mapper.OrderEventLogMapper;
import com.geihou.module.finance.order.dal.mapper.OrderMapper;
import com.geihou.module.finance.order.enums.OrderEventTypeEnum;
import com.geihou.module.finance.order.framework.OrderBusinessException;
import com.geihou.module.finance.order.framework.OrderErrorCodeConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Self-contained order state machine implementation.
 *
 * <p>Uses an immutable Map<OrderStatusEnum, Set<OrderStatusEnum>> whitelist
 * to define legal transitions per PRD Section 4.2.
 *
 * <p>Optimistic lock retry: up to 3 attempts on version conflict,
 * then throws ORDER_CONFLICT.
 *
 * <p>Every transition writes an order_event_log entry (INSERT-only).
 */
@Service
public class OrderStateMachineServiceImpl implements OrderStateMachineService {

    private static final Logger log = LoggerFactory.getLogger(OrderStateMachineServiceImpl.class);

    private static final int MAX_RETRY = 3;

    /**
     * Immutable transition whitelist.
     * Key = source status, Value = set of legal target statuses.
     */
    private static final Map<OrderStatusEnum, Set<OrderStatusEnum>> TRANSITION_WHITELIST;

    static {
        Map<OrderStatusEnum, Set<OrderStatusEnum>> map = new EnumMap<>(OrderStatusEnum.class);
        map.put(OrderStatusEnum.PENDING, Set.of(
                OrderStatusEnum.PAID,
                OrderStatusEnum.CANCELLED,
                OrderStatusEnum.EXPIRED
        ));
        map.put(OrderStatusEnum.PAID, Set.of(
                OrderStatusEnum.ACCEPTED
        ));
        map.put(OrderStatusEnum.ACCEPTED, Set.of(
                OrderStatusEnum.PREPARING
        ));
        map.put(OrderStatusEnum.PREPARING, Set.of(
                OrderStatusEnum.READY
        ));
        map.put(OrderStatusEnum.READY, Set.of(
                OrderStatusEnum.DELIVERED,
                OrderStatusEnum.DELIVERING
        ));
        map.put(OrderStatusEnum.DELIVERING, Set.of(
                OrderStatusEnum.DELIVERED
        ));
        map.put(OrderStatusEnum.DELIVERED, Set.of(
                OrderStatusEnum.COMPLETED
        ));
        map.put(OrderStatusEnum.COMPLETED, Set.of(
                OrderStatusEnum.REFUNDING
        ));
        map.put(OrderStatusEnum.REFUNDING, Set.of(
                OrderStatusEnum.REFUNDED,
                OrderStatusEnum.COMPLETED
        ));
        // Terminal states: no outgoing transitions
        map.put(OrderStatusEnum.REFUNDED, Collections.emptySet());
        map.put(OrderStatusEnum.CANCELLED, Collections.emptySet());
        map.put(OrderStatusEnum.EXPIRED, Collections.emptySet());
        TRANSITION_WHITELIST = Collections.unmodifiableMap(map);
    }

    private final OrderMapper orderMapper;
    private final OrderEventLogMapper eventLogMapper;

    public OrderStateMachineServiceImpl(OrderMapper orderMapper, OrderEventLogMapper eventLogMapper) {
        this.orderMapper = orderMapper;
        this.eventLogMapper = eventLogMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderDO transition(Long orderId, String targetStatus, Long operatorUserId, String operatorRole, String payload) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(targetStatus, "targetStatus must not be null");

        OrderStatusEnum targetEnum = OrderStatusEnum.fromCode(targetStatus);

        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            OrderDO order = orderMapper.selectById(orderId);
            if (order == null) {
                throw new OrderBusinessException(OrderErrorCodeConstants.ORDER_NOT_FOUND);
            }

            String currentStatus = order.getStatus();
            if (!isLegalTransition(currentStatus, targetStatus)) {
                throw new OrderBusinessException(OrderErrorCodeConstants.INVALID_STATUS_TRANSFER,
                        "from=" + currentStatus + " to=" + targetStatus);
            }

            String beforeStatus = currentStatus;
            order.setStatus(targetStatus);
            order.setUpdater(operatorRole != null ? operatorRole : "");
            order.setUpdateTime(LocalDateTime.now());

            // Set timestamp fields based on target status
            LocalDateTime now = LocalDateTime.now();
            switch (targetEnum) {
                case PAID -> order.setPayTime(now);
                case PREPARING -> order.setKitchenTime(now);
                case READY -> order.setReadyTime(now);
                case DELIVERED -> order.setDeliveredTime(now);
                case COMPLETED -> order.setCompletedTime(now);
                case CANCELLED -> {
                    order.setCancelledTime(now);
                    if (payload != null && !payload.isBlank()) {
                        // cancelReason is set by caller in payload; order field stays null unless set
                    }
                }
                case EXPIRED -> order.setCancelledTime(now);
                default -> { }
            }

            int updated = orderMapper.updateById(order);
            if (updated > 0) {
                // Success — write event log
                writeEventLog(order, beforeStatus, targetStatus, operatorUserId, operatorRole, payload, now);
                return order;
            }

            // Optimistic lock conflict — retry
            log.warn("Optimistic lock conflict on order {} transition {}->{} (attempt {}/{})",
                    orderId, beforeStatus, targetStatus, attempt, MAX_RETRY);
        }

        throw new OrderBusinessException(OrderErrorCodeConstants.ORDER_CONFLICT,
                "orderId=" + orderId + " after " + MAX_RETRY + " retries");
    }

    @Override
    public boolean isLegalTransition(String currentStatus, String targetStatus) {
        OrderStatusEnum current = OrderStatusEnum.fromCode(currentStatus);
        OrderStatusEnum target = OrderStatusEnum.fromCode(targetStatus);
        Set<OrderStatusEnum> legalTargets = TRANSITION_WHITELIST.get(current);
        return legalTargets != null && legalTargets.contains(target);
    }

    private void writeEventLog(OrderDO order, String beforeStatus, String afterStatus,
                                Long operatorUserId, String operatorRole,
                                String payload, LocalDateTime eventTime) {
        OrderEventLogDO eventLog = new OrderEventLogDO();
        eventLog.setTenantId(order.getTenantId());
        eventLog.setOrderId(order.getId());
        eventLog.setEventType(resolveEventType(afterStatus).getCode());
        eventLog.setBeforeStatus(beforeStatus);
        eventLog.setAfterStatus(afterStatus);
        eventLog.setOperatorUserId(operatorUserId);
        eventLog.setOperatorRole(operatorRole != null ? operatorRole : "SYSTEM");
        eventLog.setPayload(payload != null ? payload : "{}");
        eventLog.setEventTime(eventTime);
        eventLog.setCreateTime(eventTime);
        eventLogMapper.insert(eventLog);
    }

    private OrderEventTypeEnum resolveEventType(String afterStatus) {
        return switch (afterStatus) {
            case "PAID" -> OrderEventTypeEnum.PAY;
            case "ACCEPTED" -> OrderEventTypeEnum.KITCHEN_IN;
            case "PREPARING" -> OrderEventTypeEnum.KITCHEN_IN;
            case "READY" -> OrderEventTypeEnum.READY;
            case "DELIVERED" -> OrderEventTypeEnum.DELIVER;
            case "COMPLETED" -> OrderEventTypeEnum.COMPLETE;
            case "CANCELLED" -> OrderEventTypeEnum.CANCEL;
            case "EXPIRED" -> OrderEventTypeEnum.EXPIRE;
            case "REFUNDING" -> OrderEventTypeEnum.REFUND_INITIATE;
            case "REFUNDED" -> OrderEventTypeEnum.REFUND_COMPLETE;
            default -> OrderEventTypeEnum.CREATE;
        };
    }
}
