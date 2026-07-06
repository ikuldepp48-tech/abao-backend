package com.geihou.module.finance.order.controller.app.customer;

import com.geihou.common.pojo.CommonResult;
import com.geihou.common.pojo.PageResult;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderCancelReqVO;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderCreateReqVO;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderCreateRespVO;
import com.geihou.module.finance.order.controller.app.customer.vo.RefundRequestReqVO;
import com.geihou.module.finance.order.dal.dataobject.OrderDO;
import com.geihou.module.finance.order.dal.dataobject.OrderItemDO;
import com.geihou.module.finance.order.dal.dataobject.OrderRefundDO;
import com.geihou.module.finance.order.framework.OrderBusinessException;
import com.geihou.module.finance.order.service.OrderService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Customer order controller (G1-01A first slice).
 *
 * <p>Provides customer-facing order creation and query endpoints.
 * Base path: /app-api/customer/order
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /app-api/customer/order/create — Create order (requires Idempotent-Key header)</li>
 *   <li>GET /app-api/customer/order/{orderId} — Get order detail (includes items)</li>
 *   <li>GET /app-api/customer/order/my-list — List my orders (paginated)</li>
 * </ul>
 *
 * <p>No staff/admin/consultant controllers in this slice.
 * No payment/refund/cancel/modify endpoints in this slice.
 */
@RestController
@RequestMapping("/app-api/customer/order")
public class CustomerOrderController {

    private final OrderService orderService;

    public CustomerOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Create a new order.
     *
     * @param reqVO         order creation request body
     * @param idempotentKey Idempotent-Key header (required)
     * @return CommonResult containing OrderCreateRespVO
     */
    @PostMapping("/create")
    public CommonResult<OrderCreateRespVO> createOrder(
            @RequestBody OrderCreateReqVO reqVO,
            @RequestHeader("Idempotent-Key") String idempotentKey) {
        try {
            OrderCreateRespVO resp = orderService.createOrder(reqVO, idempotentKey);
            return CommonResult.success(resp);
        } catch (OrderBusinessException e) {
            return CommonResult.error(e.getCode(), e.getMessage());
        }
    }

    /**
     * Get order detail by ID (includes order items).
     *
     * @param orderId order ID
     * @return CommonResult containing order detail
     */
    @GetMapping("/{orderId}")
    public CommonResult<OrderCreateRespVO> getOrder(@PathVariable Long orderId) {
        try {
            OrderDO order = orderService.getOrder(orderId);
            List<OrderItemDO> items = orderService.getOrderItems(orderId);
            OrderCreateRespVO resp = new OrderCreateRespVO();
            resp.setOrderId(order.getId());
            resp.setOrderNo(order.getOrderNo());
            resp.setTotalAmount(order.getTotalAmount());
            resp.setBusinessDate(order.getBusinessDate());
            resp.setStatus(order.getStatus());
            resp.setChannel(order.getChannel());
            resp.setCreateTime(order.getCreateTime());

            List<OrderCreateRespVO.OrderItemRespVO> itemResps = items.stream().map(item -> {
                OrderCreateRespVO.OrderItemRespVO itemResp = new OrderCreateRespVO.OrderItemRespVO();
                itemResp.setSkuId(item.getSkuId());
                itemResp.setSkuName(item.getSkuName());
                itemResp.setUnitPrice(item.getUnitPrice());
                itemResp.setQuantity(item.getQuantity());
                itemResp.setItemTotal(item.getItemTotal());
                return itemResp;
            }).toList();
            resp.setItems(itemResps);

            return CommonResult.success(resp);
        } catch (OrderBusinessException e) {
            return CommonResult.error(e.getCode(), e.getMessage());
        }
    }

    /**
     * List my orders (paginated).
     *
     * @param customerUserId customer user ID (from query param for now; auth integration later)
     * @param pageNo         page number (1-based, default 1)
     * @param pageSize       page size (default 10)
     * @return CommonResult containing paginated order list
     */
    @GetMapping("/my-list")
    public CommonResult<PageResult<OrderDO>> listMyOrders(
            @RequestParam Long customerUserId,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        PageResult<OrderDO> result = orderService.listMyOrders(customerUserId, pageNo, pageSize);
        return CommonResult.success(result);
    }

    /**
     * Cancel an order (only PENDING status can be cancelled).
     *
     * @param orderId order ID
     * @param reqVO   cancel request body
     * @return CommonResult
     */
    @PostMapping("/{orderId}/cancel")
    public CommonResult<Void> cancelOrder(@PathVariable Long orderId,
                                          @RequestBody(required = false) OrderCancelReqVO reqVO) {
        try {
            String cancelReason = reqVO != null ? reqVO.getCancelReason() : null;
            orderService.cancelOrder(orderId, cancelReason);
            return CommonResult.success(null);
        } catch (OrderBusinessException e) {
            return CommonResult.error(e.getCode(), e.getMessage());
        }
    }

    /**
     * Customer refund request (G1-01C).
     * Creates order_refund record, transitions order COMPLETED → REFUNDING.
     */
    @PostMapping("/{orderId}/refund-request")
    public CommonResult<OrderRefundDO> refundRequest(@PathVariable Long orderId,
                                                      @RequestBody RefundRequestReqVO reqVO) {
        try {
            OrderRefundDO refund = orderService.refundRequest(
                    orderId,
                    reqVO.getRefundType(),
                    reqVO.getRefundAmount(),
                    reqVO.getReasonType(),
                    reqVO.getReasonDetail(),
                    reqVO.getRefundItemIds(),
                    null, // operatorUserId: null for anonymous customer (auth not integrated)
                    "CUSTOMER");
            return CommonResult.success(refund);
        } catch (OrderBusinessException e) {
            return CommonResult.error(e.getCode(), e.getMessage());
        }
    }
}
