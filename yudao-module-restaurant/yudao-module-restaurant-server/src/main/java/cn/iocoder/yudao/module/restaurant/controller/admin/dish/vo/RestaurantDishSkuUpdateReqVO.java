package cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import jakarta.validation.constraints.NotNull;

@Schema(description = "管理后台 - 菜品SKU更新 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RestaurantDishSkuUpdateReqVO extends RestaurantDishSkuBaseVO {

    @Schema(description = "SKU编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "SKU编号不能为空")
    private Long id;

}
