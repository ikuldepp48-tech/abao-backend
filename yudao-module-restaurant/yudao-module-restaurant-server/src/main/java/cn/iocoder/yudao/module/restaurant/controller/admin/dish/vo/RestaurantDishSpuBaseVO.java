package cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

@Data
public class RestaurantDishSpuBaseVO {

    @Schema(description = "分类ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "分类不能为空")
    private Long categoryId;

    @Schema(description = "菜品名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "大芝士堡")
    @NotEmpty(message = "菜品名称不能为空")
    private String name;

    @Schema(description = "售价", requiredMode = Schema.RequiredMode.REQUIRED, example = "17.00")
    @NotNull(message = "售价不能为空")
    private BigDecimal price;

    @Schema(description = "图片URL", example = "https://xxx.com/dish.png")
    private String image;

    @Schema(description = "简介描述", example = "招牌芝士牛肉堡")
    private String description;

    @Schema(description = "是否招牌", example = "false")
    private Boolean isSignature;

    @Schema(description = "是否新品", example = "false")
    private Boolean isNew;

    @Schema(description = "排序", example = "1")
    private Integer sort;

    @Schema(description = "状态：0-上架 1-下架", example = "0")
    private Integer status;

    @Schema(description = "SKU列表")
    private List<RestaurantDishSkuBaseVO> skus;

    @Schema(description = "关联加料组名称列表", example = "[\"辣度\", \"加料\"]")
    private List<String> addonGroupNames;

}
