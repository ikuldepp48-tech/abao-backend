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
 * Order expire job.
 *
 * <p>Scans PENDING orders older than 15 minutes and transitions them to EXPIRED.
 * Per PRD Section 4.2: "15 分钟未支付(全局枚举表超时)".
 *
 * <p>Operator is SYSTEM. Uses @Scheduled (Spring built-in), no external job framework.
 */
@Component
public class OrderExpireJob {

    private static final Logger log = LoggerFactory.getLogger(OrderExpireJob.class);
    private static final int PENDING_TIMEOUT_MINUTES = 15;

    private final OrderMapper orderMapper;
    private final OrderStateMachineService stateMachineService;

    public OrderExpireJob(OrderMapper orderMapper, OrderStateMachineService stateMachineService) {
        this.orderMapper = orderMapper;
        this.stateMachineService = stateMachineService;
    }

    /**
     * Run every minute to expire timed-out PENDING orders.
     */
    @Scheduled(fixedRate = 60000)
    public void execute() {
        try {
            expirePendingOrders();
        } catch (Exception e) {
            log.error("OrderExpireJob failed", e);
        }
    }

    /**
     * Expire PENDING orders older than 15 minutes. Can be called manually in tests.
     */
    public void expirePendingOrders() {
        Long savedTenantId = TenantContextHolder.getTenantId();
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusMinutes(PENDING_TIMEOUT_MINUTES);

            LambdaQueryWrapper<OrderDO> wrapper = new LambdaQueryWrapper<OrderDO>()
                    .eq(OrderDO::getStatus, OrderStatusEnum.PENDING.getCode())
                    .lt(OrderDO::getCreateTime, cutoff);

            List<OrderDO> expiredCandidates = orderMapper.selectList(wrapper);

            for (OrderDO order : expiredCandidates) {
                try {
                    TenantContextHolder.setTenantId(order.getTenantId());
                    stateMachineService.transition(
                            order.getId(), OrderStatusEnum.EXPIRED.getCode(), null, "SYSTEM", "{}");
                    log.info("Expired order {} (created at {}, cutoff {})",
                            order.getId(), order.getCreateTime(), cutoff);
                } catch (Exception e) {
                    log.warn("Failed to expire order {}: {}", order.getId(), e.getMessage());
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
