package cn.iocoder.yudao.module.restaurant.controller.admin.combo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

@Data
public class RestaurantComboBaseVO {

    @Schema(description = "品牌ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "品牌ID不能为空")
    private Long brandId;

    @Schema(description = "套餐名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "超值双人套餐")
    @NotEmpty(message = "套餐名称不能为空")
    private String name;

    @Schema(description = "套餐图片", example = "https://xxx.com/combo.png")
    private String image;

    @Schema(description = "套餐描述", example = "含2份主食+2杯饮品")
    private String description;

    @Schema(description = "套餐价", requiredMode = Schema.RequiredMode.REQUIRED, example = "39.90")
    @NotNull(message = "套餐价不能为空")
    private BigDecimal comboPrice;

    @Schema(description = "原价合计", example = "45.00")
    private BigDecimal originalPrice;

    @Schema(description = "排序", example = "1")
    private Integer sort;

    @Schema(description = "状态：0-启用 1-禁用", example = "0")
    private Integer status;

    @Schema(description = "是否支持堂食", example = "true")
    private Boolean validForDineIn;

    @Schema(description = "是否支持外卖", example = "true")
    private Boolean validForTakeout;

    @Schema(description = "套餐明细")
    private List<RestaurantComboItemBaseVO> items;

}
