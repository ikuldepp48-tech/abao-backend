package cn.iocoder.yudao.module.restaurant.controller.admin.printer.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 打印机 Response VO")
@Data
public class RestaurantPrinterRespVO {

    @Schema(description = "打印机编号", example = "1")
    private Long id;

    @Schema(description = "打印机名称", example = "阿堡总店厨房打印机")
    private String name;

    @Schema(description = "类型: 1=厨打 2=客联", example = "1")
    private Integer type;

    @Schema(description = "厂商", example = "mock")
    private String provider;

    @Schema(description = "设备号", example = "DEV001")
    private String deviceNo;

    @Schema(description = "所属门店ID", example = "1")
    private Long storeId;

    @Schema(description = "状态: 0=离线 1=在线", example = "1")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
