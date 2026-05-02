package cn.iocoder.yudao.module.restaurant.controller.admin.addon.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import jakarta.validation.constraints.NotNull;

@Schema(description = "管理后台 - 加料更新 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RestaurantDishAddonUpdateReqVO extends RestaurantDishAddonBaseVO {

    @Schema(description = "加料编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "加料编号不能为空")
    private Long id;

}
