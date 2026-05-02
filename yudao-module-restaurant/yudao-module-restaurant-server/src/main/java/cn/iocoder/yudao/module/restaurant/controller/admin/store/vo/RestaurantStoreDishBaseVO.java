package cn.iocoder.yudao.module.restaurant.controller.admin.store.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class RestaurantStoreDishBaseVO {

    @Schema(description = "门店ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "门店ID不能为空")
    private Long storeId;

    @Schema(description = "菜品SPU ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "菜品ID不能为空")
    private Long spuId;

    @Schema(description = "是否可售", example = "true")
    private Boolean isAvailable;

    @Schema(description = "每日限量（0=不限）", example = "100")
    private Integer dailyLimit;

    @Schema(description = "是否沽清", example = "false")
    private Boolean isSoldOut;

    @Schema(description = "今日已售", example = "0")
    private Integer todaySold;

    @Schema(description = "门店自定义价格", example = "18.00")
    private BigDecimal price;

    @Schema(description = "排序", example = "1")
    private Integer sort;

    @Schema(description = "状态：0-启用 1-禁用", example = "0")
    private Integer status;

}
