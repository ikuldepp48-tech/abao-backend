package cn.iocoder.yudao.module.restaurant.service.kitchen;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * KDS WebSocket 推送消息
 */
@Data
@Builder
public class KdsPushMessage {

    /** 消息类型 */
    private String type;

    /** 订单数据 */
    private OrderData data;

    @Data
    @Builder
    public static class OrderData {
        private Long orderId;
        private String orderNo;
        private LocalDateTime createTime;
        private List<OrderItemData> items;
    }

    @Data
    @Builder
    public static class OrderItemData {
        private String spuName;
        private String skuName;
        private Integer quantity;
        private String addonsDesc;
        private String customerRemark;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;
    }

}
