package cn.iocoder.yudao.module.restaurant.enums.order;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;
import java.util.Set;

/**
 * 订单状态枚举
 */
@Getter
@AllArgsConstructor
public enum OrderStatusEnum {

    UNPAID(0, "待支付"),
    PAID_PENDING(1, "已支付"),
    PREPARING(2, "备餐中"),
    READY(3, "已出餐"),
    COMPLETED(4, "已完成"),
    CANCELLED(5, "已取消");

    private final Integer status;
    private final String name;

    private static final Map<Integer, OrderStatusEnum> MAP = new java.util.HashMap<>();

    static {
        for (OrderStatusEnum e : values()) {
            MAP.put(e.status, e);
        }
    }

    public static OrderStatusEnum valueOf(Integer status) {
        return MAP.get(status);
    }

    /**
     * 校验状态流转是否合法
     *
     * @param from 当前状态
     * @param to   目标状态
     * @return true=合法流转
     */
    public static boolean canTransition(Integer from, Integer to) {
        if (from == null || to == null) return false;
        // 同一状态不允许转换
        if (from.equals(to)) return false;
        // UNPAID → PAID_PENDING (支付成功) / CANCELLED (取消)
        if (from == 0) return to == 1 || to == 5;
        // PAID_PENDING → PREPARING (商家接单) / CANCELLED (取消)
        if (from == 1) return to == 2 || to == 5;
        // PREPARING → READY (出餐)
        if (from == 2) return to == 3;
        // READY → COMPLETED (完成)
        if (from == 3) return to == 4;
        // COMPLETED / CANCELLED 为终态，不可再转
        return false;
    }
}
