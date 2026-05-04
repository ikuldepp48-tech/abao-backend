package cn.iocoder.yudao.module.restaurant.controller.admin.kitchen.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 厨房档口 新增/编辑 Request VO")
@Data
public class RestaurantKitchenStationSaveReqVO {

    @Schema(description = "档口编号", example = "1")
    private Long id;

    @Schema(description = "档口名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "炸鸡档口")
    @NotEmpty(message = "档口名称不能为空")
    private String name;

    @Schema(description = "负责的菜品分类ID列表", requiredMode = Schema.RequiredMode.REQUIRED, example = "[1,2,3]")
    @NotNull(message = "菜品分类不能为空")
    private List<Long> dishCategories;

    @Schema(description = "排序", example = "1")
    private Integer sort;

    @Schema(description = "状态: 0=停用, 1=启用", example = "1")
    private Integer status;

}
