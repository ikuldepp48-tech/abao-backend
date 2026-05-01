package cn.iocoder.yudao.module.restaurant.controller.admin.brand.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import jakarta.validation.constraints.NotNull;

@Schema(description = "管理后台 - 餐饮品牌更新 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RestaurantBrandUpdateReqVO extends RestaurantBrandBaseVO {

    @Schema(description = "品牌编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "品牌编号不能为空")
    private Long id;

}
