package cn.iocoder.yudao.module.restaurant.controller.admin.brand.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;

@Data
public class RestaurantBrandBaseVO {

    @Schema(description = "品牌名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "阿堡")
    @NotEmpty(message = "品牌名称不能为空")
    private String name;

    @Schema(description = "品牌编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "abao")
    @NotEmpty(message = "品牌编码不能为空")
    private String code;

    @Schema(description = "品牌Logo URL", example = "https://xxx.com/logo.png")
    private String logo;

    @Schema(description = "品牌简介", example = "中式快餐品牌")
    private String description;

    @Schema(description = "品类", example = "中式快餐")
    private String category;

    @Schema(description = "状态：0-启用 1-禁用", example = "0")
    private Integer status;

}
