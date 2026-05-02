package cn.iocoder.yudao.module.restaurant.controller.admin.dish.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 菜品分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RestaurantDishSpuPageReqVO extends PageParam {

    @Schema(description = "分类ID", example = "1")
    private Long categoryId;

    @Schema(description = "菜品名称", example = "芝士堡")
    private String name;

    @Schema(description = "状态：0-上架 1-下架", example = "0")
    private Integer status;

}
