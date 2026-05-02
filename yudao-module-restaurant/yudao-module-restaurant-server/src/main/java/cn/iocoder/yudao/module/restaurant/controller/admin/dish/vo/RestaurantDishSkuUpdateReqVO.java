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

    // id 字段继承自父类 RestaurantDishSkuBaseVO

}
