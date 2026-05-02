package cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class RestaurantDishSkuBaseVO {

    @Schema(description = "SKU ID，更新时传，新增时不传", example = "1")
    private Long id;

    @Schema(description = "SPU ID，独立创建时必填，SPU内嵌时后端自动设置", example = "1")
    private Long spuId;

    @Schema(description = "SKU名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "大份")
    @NotEmpty(message = "SKU名称不能为空")
    private String name;

    @Schema(description = "规格属性 JSON", example = "{\"size\":\"大份\"}")
    private String properties;

    @Schema(description = "售价", requiredMode = Schema.RequiredMode.REQUIRED, example = "17.00")
    @NotNull(message = "售价不能为空")
    private BigDecimal price;

    @Schema(description = "会员价", example = "15.00")
    private BigDecimal memberPrice;

    @Schema(description = "成本价", example = "8.00")
    private BigDecimal costPrice;

    @Schema(description = "重量（克）", example = "500")
    private Integer weight;

    @Schema(description = "条形码", example = "6901234567890")
    private String barcode;

    @Schema(description = "SKU图片", example = "https://xxx.com/sku.png")
    private String picture;

    @Schema(description = "排序", example = "1")
    private Integer sort;

    @Schema(description = "状态：0-启用 1-禁用", example = "0")
    private Integer status;

}
