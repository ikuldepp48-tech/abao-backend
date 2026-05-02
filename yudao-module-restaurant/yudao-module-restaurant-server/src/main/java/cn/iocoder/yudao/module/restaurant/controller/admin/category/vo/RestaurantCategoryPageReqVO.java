package cn.iocoder.yudao.module.restaurant.controller.admin.category.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 菜品分类分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RestaurantCategoryPageReqVO extends PageParam {

    @Schema(description = "分类名称", example = "汉堡")
    private String name;

    @Schema(description = "父分类ID", example = "0")
    private Long parentId;

    @Schema(description = "状态：0-启用 1-禁用", example = "0")
    private Integer status;

}
