package cn.iocoder.yudao.module.restaurant.controller.admin.addon.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class RestaurantDishAddonBaseVO {

    @Schema(description = "品牌ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "品牌ID不能为空")
    private Long brandId;

    @Schema(description = "加料组名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "配菜加料")
    @NotEmpty(message = "加料组名称不能为空")
    private String groupName;

    @Schema(description = "加料名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "加鸡蛋")
    @NotEmpty(message = "加料名称不能为空")
    private String name;

    @Schema(description = "额外加价", requiredMode = Schema.RequiredMode.REQUIRED, example = "2.00")
    @NotNull(message = "额外加价不能为空")
    private BigDecimal extraPrice;

    @Schema(description = "是否必选", example = "false")
    private Boolean isRequired;

    @Schema(description = "是否可多选", example = "true")
    private Boolean isMulti;

    @Schema(description = "排序", example = "1")
    private Integer sort;

    @Schema(description = "状态：0-启用 1-禁用", example = "0")
    private Integer status;

}
