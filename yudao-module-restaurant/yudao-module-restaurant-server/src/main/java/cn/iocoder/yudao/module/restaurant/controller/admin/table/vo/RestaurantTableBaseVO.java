package cn.iocoder.yudao.module.restaurant.controller.admin.table.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@Data
public class RestaurantTableBaseVO {

    @Schema(description = "所属门店ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "所属门店不能为空")
    private Long storeId;

    @Schema(description = "区域（大厅/包厢/二楼）", example = "大厅")
    private String area;

    @Schema(description = "桌号", requiredMode = Schema.RequiredMode.REQUIRED, example = "A01")
    @NotEmpty(message = "桌号不能为空")
    private String tableNo;

    @Schema(description = "座位数", requiredMode = Schema.RequiredMode.REQUIRED, example = "4")
    @NotNull(message = "座位数不能为空")
    private Integer seatCapacity;

    @Schema(description = "状态：0-空闲 1-用餐中 2-待结账 3-已锁定", example = "0")
    private Integer status;

}
