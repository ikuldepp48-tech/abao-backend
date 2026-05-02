package cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 菜品SKU分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RestaurantDishSkuPageReqVO extends PageParam {

    @Schema(description = "SPU ID", example = "1")
    private Long spuId;

    @Schema(description = "SKU名称", example = "大份")
    private String name;

    @Schema(description = "状态：0-启用 1-禁用", example = "0")
    private Integer status;

}
