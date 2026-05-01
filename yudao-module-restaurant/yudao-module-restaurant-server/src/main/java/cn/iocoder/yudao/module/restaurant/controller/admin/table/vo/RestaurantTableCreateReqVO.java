package cn.iocoder.yudao.module.restaurant.controller.admin.table.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 桌台创建 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RestaurantTableCreateReqVO extends RestaurantTableBaseVO {
}
