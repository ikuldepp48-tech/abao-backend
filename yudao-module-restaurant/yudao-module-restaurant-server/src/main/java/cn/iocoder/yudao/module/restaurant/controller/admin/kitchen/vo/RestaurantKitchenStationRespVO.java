package cn.iocoder.yudao.module.restaurant.controller.admin.kitchen.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 厨房档口 Response VO")
@Data
public class RestaurantKitchenStationRespVO {

    @Schema(description = "档口编号", example = "1")
    private Long id;

    @Schema(description = "档口名称", example = "炸鸡档口")
    private String name;

    @Schema(description = "负责的菜品分类ID列表", example = "[1,2,3]")
    private List<Long> dishCategories;

    @Schema(description = "排序", example = "1")
    private Integer sort;

    @Schema(description = "状态: 0=停用, 1=启用", example = "1")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
