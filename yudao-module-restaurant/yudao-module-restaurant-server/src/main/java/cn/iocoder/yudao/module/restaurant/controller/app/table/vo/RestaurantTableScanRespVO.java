package cn.iocoder.yudao.module.restaurant.controller.app.table.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "顾客端 - 桌台扫码响应 VO")
@Data
public class RestaurantTableScanRespVO {

    @Schema(description = "租户ID", example = "162")
    private Long tenantId;

    @Schema(description = "门店ID", example = "1001")
    private Long storeId;

    @Schema(description = "门店名称", example = "阿堡总店")
    private String storeName;

    @Schema(description = "桌台ID", example = "5001")
    private Long tableId;

    @Schema(description = "桌号", example = "A01")
    private String tableNo;

    @Schema(description = "区域", example = "大厅")
    private String area;

    @Schema(description = "容纳人数", example = "4")
    private Integer seatCapacity;

}
