package com.geihou.module.finance.order.controller.app.staff;

import com.geihou.common.pojo.CommonResult;
import com.geihou.common.pojo.PageResult;
import com.geihou.module.finance.api.order.enums.OrderStatusEnum;
import com.geihou.module.finance.order.controller.app.staff.vo.StaffMarkPaidReqVO;
import com.geihou.module.finance.order.controller.app.staff.vo.StaffExecuteRefundReqVO;
import com.geihou.module.finance.order.controller.app.staff.vo.StaffOrderPendingListReqVO;
import com.geihou.module.finance.order.dal.dataobject.OrderDO;
import com.geihou.module.finance.order.dal.dataobject.OrderRefundDO;
import com.geihou.module.finance.order.framework.OrderBusinessException;
import com.geihou.module.finance.order.service.OrderService;
import com.geihou.module.finance.order.service.refund.RefundService;
import com.geihou.module.finance.order.service.statemachine.OrderStateMachineService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geihou.module.finance.order.dal.mapper.OrderMapper;
import org.springframework.web.bind.annotation.*;

/**
 * Staff order controller (G1-01B slice).
 *
 * <p>Endpoints for staff to manage order lifecycle:
 * <ul>
 *   <li>GET /app-api/staff/order/pending-list — View pending (PAID) orders</li>
 *   <li>POST /app-api/staff/order/{orderId}/accept — Accept order (PAID→ACCEPTED→PREPARING)</li>
 *   <li>POST /app-api/staff/order/{orderId}/mark-ready — Mark ready (PREPARING→READY)</li>
 *   <li>POST /app-api/staff/order/{orderId}/deliver — Mark delivered (READY→DELIVERED)</li>
 *   <li>POST /app-api/staff/order/{orderId}/mark-paid — Manual mark-as-paid (PENDING→PAID)</li>
 * </ul>
 */
@RestController
@RequestMapping("/app-api/staff/order")
public class StaffOrderController {

    private final OrderService orderService;
    private final OrderStateMachineService stateMachineService;
    private final OrderMapper orderMapper;
    private final RefundService refundService;

    public StaffOrderController(OrderService orderService,
                                OrderStateMachineService stateMachineService,
                                OrderMapper orderMapper,
                                RefundService refundService) {
        this.orderService = orderService;
        this.stateMachineService = stateMachineService;
        this.orderMapper = orderMapper;
        this.refundService = refundService;
    }

    /**
     * View pending orders (PAID status, awaiting acceptance).
     */
    @GetMapping("/pending-list")
    public CommonResult<PageResult<OrderDO>> pendingList(StaffOrderPendingListReqVO reqVO) {
        LambdaQueryWrapper<OrderDO> wrapper = new LambdaQueryWrapper<OrderDO>()
                .eq(OrderDO::getStatus, OrderStatusEnum.PAID.getCode())
                .orderByDesc(OrderDO::getCreateTime);
        if (reqVO.getShopId() != null) {
            wrapper.eq(OrderDO::getShopId, reqVO.getShopId());
        }
        PageResult<OrderDO> result = orderMapper.selectPage(reqVO.getPageNo(), reqVO.getPageSize(), wrapper);
        return CommonResult.success(result);
    }

    /**
     * Accept order: PAID → ACCEPTED → PREPARING (merged step, no KDS yet).
     *
     * <p>Delegates to {@link OrderService#acceptOrder(Long)} which executes both
     * transitions within a single transaction boundary, ensuring atomicity.
     */
    @PostMapping("/{orderId}/accept")
    public CommonResult<OrderDO> acceptOrder(@PathVariable Long orderId) {
        try {
            OrderDO order = orderService.acceptOrder(orderId);
            return CommonResult.success(order);
        } catch (OrderBusinessException e) {
            return CommonResult.error(e.getCode(), e.getMessage());
        }
    }

    /**
     * Mark order as ready (出餐完成): PREPARING → READY.
     */
    @PostMapping("/{orderId}/mark-ready")
    public CommonResult<OrderDO> markReady(@PathVariable Long orderId) {
        try {
            OrderDO order = stateMachineService.transition(
                    orderId, OrderStatusEnum.READY.getCode(), null, "STAFF", "{}");
            return CommonResult.success(order);
        } catch (OrderBusinessException e) {
            return CommonResult.error(e.getCode(), e.getMessage());
        }
    }

    /**
     * Mark order as delivered (堂食/自提): READY → DELIVERED.
     */
    @PostMapping("/{orderId}/deliver")
    public CommonResult<OrderDO> deliver(@PathVariable Long orderId) {
        try {
            OrderDO order = stateMachineService.transition(
                    orderId, OrderStatusEnum.DELIVERED.getCode(), null, "STAFF", "{}");
            return CommonResult.success(order);
        } catch (OrderBusinessException e) {
            return CommonResult.error(e.getCode(), e.getMessage());
        }
    }

    /**
     * Staff manual mark-as-paid (payment bridge, D-1 decision): PENDING → PAID.
     * Creates order_payment record and transitions order status.
     */
    @PostMapping("/{orderId}/mark-paid")
    public CommonResult<OrderDO> markPaid(@PathVariable Long orderId,
                                          @RequestBody StaffMarkPaidReqVO reqVO) {
        try {
            OrderDO order = orderService.markOrderPaid(
                    orderId, reqVO.getPaymentMethod(), reqVO.getPaymentAmount(), reqVO.getExternalNo());
            return CommonResult.success(order);
        } catch (OrderBusinessException e) {
            return CommonResult.error(e.getCode(), e.getMessage());
        }
    }

    /**
     * Staff execute refund bridge (G1-01C, CG-R4/D-R1).
     * Marks refund as completed: refund record REFUNDING → REFUNDED,
     * order REFUNDING → REFUNDED, updates orders.refund_amount.
     */
    @PostMapping("/{orderId}/execute-refund")
    public CommonResult<OrderRefundDO> executeRefund(@PathVariable Long orderId,
                                                      @RequestBody StaffExecuteRefundReqVO reqVO) {
        try {
            OrderRefundDO refund = refundService.executeRefund(
                    reqVO.getRefundId(), null, reqVO.getExternalRefundNo());
            return CommonResult.success(refund);
        } catch (OrderBusinessException e) {
            return CommonResult.error(e.getCode(), e.getMessage());
        }
    }
}
