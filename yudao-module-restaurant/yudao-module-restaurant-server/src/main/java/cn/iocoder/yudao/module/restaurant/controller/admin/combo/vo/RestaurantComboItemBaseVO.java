package cn.iocoder.yudao.module.restaurant.controller.admin.combo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class RestaurantComboItemBaseVO {

    @Schema(description = "ID（编辑时传入）", example = "1")
    private Long id;

    @Schema(description = "SPU ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long spuId;

    @Schema(description = "SKU ID（可选，默认SKU）", example = "1")
    private Long skuId;

    @Schema(description = "数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer quantity;

    @Schema(description = "额外加价", example = "0.00")
    private BigDecimal extraPrice;

    @Schema(description = "是否必选", example = "true")
    private Boolean isRequired;

    @Schema(description = "可替换的SPU ID列表（JSON）", example = "[2,3]")
    private String replaceableSpuIds;

    @Schema(description = "排序", example = "1")
    private Integer sort;

}
