package cn.iocoder.yudao.module.restaurant.controller.app.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "顾客端 - 订单响应 VO")
@Data
public class AppOrderRespVO {

    @Schema(description = "订单ID")
    private Long id;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "门店ID")
    private Long storeId;

    @Schema(description = "桌台ID")
    private Long tableId;

    @Schema(description = "订单类型")
    private Integer orderType;

    @Schema(description = "就餐人数")
    private Integer dinerCount;

    @Schema(description = "订单状态")
    private Integer status;

    @Schema(description = "支付状态")
    private Integer payStatus;

    @Schema(description = "原价")
    private BigDecimal originalAmount;

    @Schema(description = "优惠金额")
    private BigDecimal discountAmount;

    @Schema(description = "实付金额")
    private BigDecimal payAmount;

    @Schema(description = "订单备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "订单明细")
    private List<OrderItem> items;

    @Data
    @Schema(description = "订单明细")
    public static class OrderItem {
        @Schema(description = "菜品名")
        private String spuName;

        @Schema(description = "SKU名")
        private String skuName;

        @Schema(description = "单价")
        private BigDecimal unitPrice;

        @Schema(description = "数量")
        private Integer quantity;

        @Schema(description = "加料描述")
        private String addonsDesc;

        @Schema(description = "顾客备注")
        private String customerRemark;

        @Schema(description = "小计")
        private BigDecimal subtotal;
    }

}
