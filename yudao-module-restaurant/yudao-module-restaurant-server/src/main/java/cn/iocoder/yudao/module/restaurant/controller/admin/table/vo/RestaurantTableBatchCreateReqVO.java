package cn.iocoder.yudao.module.restaurant.controller.admin.table.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@Schema(description = "管理后台 - 桌台批量创建 Request VO")
@Data
public class RestaurantTableBatchCreateReqVO {

    @Schema(description = "所属门店ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "所属门店不能为空")
    private Long storeId;

    @Schema(description = "区域", example = "大厅")
    private String area;

    @Schema(description = "桌号前缀", requiredMode = Schema.RequiredMode.REQUIRED, example = "A")
    @NotEmpty(message = "桌号前缀不能为空")
    private String prefix;

    @Schema(description = "起始编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "起始编号不能为空")
    private Integer startNo;

    @Schema(description = "结束编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "20")
    @NotNull(message = "结束编号不能为空")
    private Integer endNo;

    @Schema(description = "每桌默认人数", requiredMode = Schema.RequiredMode.REQUIRED, example = "4")
    @NotNull(message = "每桌默认人数不能为空")
    private Integer seatCapacity;

}
