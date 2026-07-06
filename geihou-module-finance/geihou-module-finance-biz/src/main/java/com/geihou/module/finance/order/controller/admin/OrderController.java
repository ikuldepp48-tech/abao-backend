package com.geihou.module.finance.order.controller.admin;

import com.geihou.common.pojo.CommonResult;
import com.geihou.common.pojo.PageResult;
import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.order.controller.admin.vo.OrderDailySummaryChannelVO;
import com.geihou.module.finance.order.controller.admin.vo.OrderDailySummaryRespVO;
import com.geihou.module.finance.order.controller.admin.vo.OrderDailySummaryStatusVO;
import com.geihou.module.finance.order.controller.admin.vo.OrderPageReqVO;
import com.geihou.module.finance.order.controller.admin.vo.OrderRespVO;
import com.geihou.module.finance.order.controller.admin.vo.RefundApproveReqVO;
import com.geihou.module.finance.order.dal.dataobject.OrderDO;
import com.geihou.module.finance.order.dal.dataobject.OrderRefundDO;
import com.geihou.module.finance.order.dal.mapper.OrderMapper;
import com.geihou.module.finance.order.framework.OrderBusinessException;
import com.geihou.module.finance.order.service.refund.RefundService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Admin order controller (G1-01B slice).
 *
 * <p>Endpoints for admin/owner to view orders and daily summaries:
 * <ul>
 *   <li>GET /admin-api/order/page — Paginated order list with filters</li>
 *   <li>GET /admin-api/order/business-daily-summary — Daily summary by business date</li>
 * </ul>
 */
@RestController
@RequestMapping("/admin-api/order")
public class OrderController {

    private final OrderMapper orderMapper;
    private final RefundService refundService;

    public OrderController(OrderMapper orderMapper, RefundService refundService) {
        this.orderMapper = orderMapper;
        this.refundService = refundService;
    }

    /**
     * Paginated order list with filters (businessDate, channel, status, shopId).
     */
    @GetMapping("/page")
    public CommonResult<PageResult<OrderRespVO>> page(OrderPageReqVO reqVO) {
        LambdaQueryWrapper<OrderDO> wrapper = new LambdaQueryWrapper<OrderDO>()
                .orderByDesc(OrderDO::getCreateTime);

        if (reqVO.getBusinessDate() != null) {
            wrapper.eq(OrderDO::getBusinessDate, reqVO.getBusinessDate());
        }
        if (reqVO.getChannel() != null && !reqVO.getChannel().isBlank()) {
            wrapper.eq(OrderDO::getChannel, reqVO.getChannel());
        }
        if (reqVO.getStatus() != null && !reqVO.getStatus().isBlank()) {
            wrapper.eq(OrderDO::getStatus, reqVO.getStatus());
        }
        if (reqVO.getShopId() != null) {
            wrapper.eq(OrderDO::getShopId, reqVO.getShopId());
        }

        PageResult<OrderDO> result = orderMapper.selectPage(reqVO.getPageNo(), reqVO.getPageSize(), wrapper);

        // Convert to VO
        List<OrderRespVO> voList = result.getList().stream().map(this::toRespVO).toList();
        PageResult<OrderRespVO> voResult = PageResult.of(voList, result.getTotal(),
                reqVO.getPageNo(), reqVO.getPageSize());
        return CommonResult.success(voResult);
    }

    /**
     * Daily summary by business date (G1-01G enhanced).
     *
     * <p>SQL aggregation via OrderMapper — no full in-memory order load.
     * Supports optional shopId filter. Returns 9 top-level fields + channel/status breakdowns.
     * netRevenue = paidAmount - refundAmount - platformFee (per CG-DS2 ruling).
     *
     * @param businessDate optional business date (defaults to today)
     * @param shopId       optional shop filter (null = all shops)
     */
    @GetMapping("/business-daily-summary")
    public CommonResult<OrderDailySummaryRespVO> businessDailySummary(
            @RequestParam(required = false) LocalDate businessDate,
            @RequestParam(required = false) Long shopId) {
        if (businessDate == null) {
            businessDate = LocalDate.now();
        }

        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context is required for daily summary");
        }

        // SQL aggregation — no selectList / in-memory loop
        Map<String, Object> summary = orderMapper.selectDailySummary(tenantId, businessDate, shopId);
        List<Map<String, Object>> channelRows = orderMapper.selectDailySummaryByChannel(tenantId, businessDate, shopId);
        List<Map<String, Object>> statusRows = orderMapper.selectDailySummaryByStatus(tenantId, businessDate, shopId);

        // Build top-level summary
        OrderDailySummaryRespVO resp = new OrderDailySummaryRespVO();
        resp.setBusinessDate(businessDate);
        resp.setShopId(shopId);
        resp.setOrderCount(getIntValue(summary, "order_count"));
        resp.setTotalAmount(getDecimalValue(summary, "total_amount"));
        resp.setPaidAmount(getDecimalValue(summary, "paid_amount"));
        resp.setDiscountAmount(getDecimalValue(summary, "discount_amount"));
        resp.setRefundAmount(getDecimalValue(summary, "refund_amount"));
        resp.setPlatformFee(getDecimalValue(summary, "platform_fee"));
        // netRevenue = paidAmount - refundAmount - platformFee (CG-DS2: do not subtract discountAmount)
        resp.setNetRevenue(resp.getPaidAmount().subtract(resp.getRefundAmount()).subtract(resp.getPlatformFee()));

        // Channel summary
        List<OrderDailySummaryChannelVO> channelSummary = new ArrayList<>();
        if (channelRows != null) {
            for (Map<String, Object> row : channelRows) {
                OrderDailySummaryChannelVO vo = new OrderDailySummaryChannelVO();
                vo.setChannel(getStringValue(row, "channel"));
                vo.setOrderCount(getIntValue(row, "order_count"));
                vo.setTotalAmount(getDecimalValue(row, "total_amount"));
                vo.setPaidAmount(getDecimalValue(row, "paid_amount"));
                vo.setRefundAmount(getDecimalValue(row, "refund_amount"));
                BigDecimal rowPlatformFee = getDecimalValue(row, "platform_fee");
                vo.setNetRevenue(vo.getPaidAmount().subtract(vo.getRefundAmount()).subtract(rowPlatformFee));
                channelSummary.add(vo);
            }
        }
        resp.setChannelSummary(channelSummary);

        // Status summary
        List<OrderDailySummaryStatusVO> statusSummary = new ArrayList<>();
        if (statusRows != null) {
            for (Map<String, Object> row : statusRows) {
                OrderDailySummaryStatusVO vo = new OrderDailySummaryStatusVO();
                vo.setStatus(getStringValue(row, "status"));
                vo.setOrderCount(getIntValue(row, "order_count"));
                vo.setTotalAmount(getDecimalValue(row, "total_amount"));
                vo.setPaidAmount(getDecimalValue(row, "paid_amount"));
                vo.setRefundAmount(getDecimalValue(row, "refund_amount"));
                statusSummary.add(vo);
            }
        }
        resp.setStatusSummary(statusSummary);

        return CommonResult.success(resp);
    }

    // --- Map extraction helpers (case-insensitive key lookup for H2/MySQL compatibility) ---

    private static BigDecimal getDecimalValue(Map<String, Object> map, String key) {
        if (map == null) return BigDecimal.ZERO;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                Object value = entry.getValue();
                if (value == null) return BigDecimal.ZERO;
                if (value instanceof BigDecimal) return (BigDecimal) value;
                if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
                return new BigDecimal(value.toString());
            }
        }
        return BigDecimal.ZERO;
    }

    private static Integer getIntValue(Map<String, Object> map, String key) {
        if (map == null) return 0;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                Object value = entry.getValue();
                if (value == null) return 0;
                if (value instanceof Integer) return (Integer) value;
                if (value instanceof Number) return ((Number) value).intValue();
                return Integer.parseInt(value.toString());
            }
        }
        return 0;
    }

    private static String getStringValue(Map<String, Object> map, String key) {
        if (map == null) return null;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                Object value = entry.getValue();
                return value == null ? null : value.toString();
            }
        }
        return null;
    }

    private OrderRespVO toRespVO(OrderDO order) {
        OrderRespVO vo = new OrderRespVO();
        vo.setOrderId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setBusinessDate(order.getBusinessDate());
        vo.setChannel(order.getChannel());
        vo.setStatus(order.getStatus());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setPaidAmount(order.getPaidAmount());
        vo.setDiscountAmount(order.getDiscountAmount());
        vo.setPaymentMethod(order.getPaymentMethod());
        vo.setPayTime(order.getPayTime());
        vo.setCreateTime(order.getCreateTime());
        vo.setShopId(order.getShopId());
        return vo;
    }

    /**
     * Owner/admin refund approve/reject (G1-01C).
     * Action "approve" → refund PENDING_REVIEW → REFUNDING.
     * Action "reject" → refund PENDING_REVIEW → REJECTED + order REFUNDING → COMPLETED.
     */
    @PostMapping("/{orderId}/refund/approve")
    public CommonResult<OrderRefundDO> refundApprove(@PathVariable Long orderId,
                                                      @RequestBody RefundApproveReqVO reqVO) {
        try {
            OrderRefundDO refund;
            if ("approve".equalsIgnoreCase(reqVO.getAction())) {
                refund = refundService.approveRefund(reqVO.getRefundId(), null, reqVO.getApproveRemark());
            } else if ("reject".equalsIgnoreCase(reqVO.getAction())) {
                refund = refundService.rejectRefund(reqVO.getRefundId(), null, reqVO.getApproveRemark());
            } else {
                return CommonResult.error(400, "Invalid action: must be 'approve' or 'reject'");
            }
            return CommonResult.success(refund);
        } catch (OrderBusinessException e) {
            return CommonResult.error(e.getCode(), e.getMessage());
        }
    }
}
