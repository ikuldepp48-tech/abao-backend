package com.geihou.module.finance.order.job;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.api.order.enums.OrderStatusEnum;
import com.geihou.module.finance.order.dal.dataobject.OrderDO;
import com.geihou.module.finance.order.dal.mapper.OrderMapper;
import com.geihou.module.finance.order.service.statemachine.OrderStateMachineService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Order completion job.
 *
 * <p>Scans DELIVERED orders older than 24 hours and transitions them to COMPLETED.
 * Per PRD Section 4.2: "系统(24h)".
 *
 * <p>Operator is SYSTEM. Uses @Scheduled (Spring built-in), no external job framework.
 */
@Component
public class OrderCompletionJob {

    private static final Logger log = LoggerFactory.getLogger(OrderCompletionJob.class);
    private static final int DELIVERED_TIMEOUT_HOURS = 24;

    private final OrderMapper orderMapper;
    private final OrderStateMachineService stateMachineService;

    public OrderCompletionJob(OrderMapper orderMapper, OrderStateMachineService stateMachineService) {
        this.orderMapper = orderMapper;
        this.stateMachineService = stateMachineService;
    }

    /**
     * Run every hour to complete delivered orders past 24 hours.
     */
    @Scheduled(fixedRate = 3600000)
    public void execute() {
        try {
            completeDeliveredOrders();
        } catch (Exception e) {
            log.error("OrderCompletionJob failed", e);
        }
    }

    /**
     * Complete DELIVERED orders older than 24 hours. Can be called manually in tests.
     */
    public void completeDeliveredOrders() {
        Long savedTenantId = TenantContextHolder.getTenantId();
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusHours(DELIVERED_TIMEOUT_HOURS);

            LambdaQueryWrapper<OrderDO> wrapper = new LambdaQueryWrapper<OrderDO>()
                    .eq(OrderDO::getStatus, OrderStatusEnum.DELIVERED.getCode())
                    .lt(OrderDO::getDeliveredTime, cutoff);

            List<OrderDO> completionCandidates = orderMapper.selectList(wrapper);

            for (OrderDO order : completionCandidates) {
                try {
                    TenantContextHolder.setTenantId(order.getTenantId());
                    stateMachineService.transition(
                            order.getId(), OrderStatusEnum.COMPLETED.getCode(), null, "SYSTEM", "{}");
                    log.info("Completed order {} (delivered at {}, cutoff {})",
                            order.getId(), order.getDeliveredTime(), cutoff);
                } catch (Exception e) {
                    log.warn("Failed to complete order {}: {}", order.getId(), e.getMessage());
                }
            }
        } finally {
            // Restore tenant context
            if (savedTenantId != null) {
                TenantContextHolder.setTenantId(savedTenantId);
            } else {
                TenantContextHolder.clear();
            }
        }
    }
}
