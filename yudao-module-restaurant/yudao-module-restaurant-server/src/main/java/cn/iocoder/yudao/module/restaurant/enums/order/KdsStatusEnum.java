package cn.iocoder.yudao.module.restaurant.enums.order;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * KDS 菜品制作状态
 */
@Getter
@AllArgsConstructor
public enum KdsStatusEnum {

    PENDING(0, "待制作"),
    COOKING(1, "制作中"),
    DONE(2, "已出餐");

    private final Integer status;
    private final String name;

}
