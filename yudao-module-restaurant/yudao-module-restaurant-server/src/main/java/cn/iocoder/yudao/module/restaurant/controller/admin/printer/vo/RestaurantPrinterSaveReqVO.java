package cn.iocoder.yudao.module.restaurant.controller.admin.printer.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 打印机 新增/编辑 Request VO")
@Data
public class RestaurantPrinterSaveReqVO {

    @Schema(description = "打印机编号", example = "1")
    private Long id;

    @Schema(description = "打印机名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "阿堡总店厨房打印机")
    @NotEmpty(message = "打印机名称不能为空")
    private String name;

    @Schema(description = "类型: 1=厨打 2=客联", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "类型不能为空")
    private Integer type;

    @Schema(description = "厂商: mock/feieyun/yiliantong/xinye", requiredMode = Schema.RequiredMode.REQUIRED, example = "mock")
    @NotEmpty(message = "厂商不能为空")
    private String provider;

    @Schema(description = "设备号", requiredMode = Schema.RequiredMode.REQUIRED, example = "DEV001")
    @NotEmpty(message = "设备号不能为空")
    private String deviceNo;

    @Schema(description = "设备密钥", requiredMode = Schema.RequiredMode.REQUIRED, example = "KEY123")
    @NotEmpty(message = "设备密钥不能为空")
    private String deviceKey;

    @Schema(description = "所属门店ID", example = "1")
    private Long storeId;

    @Schema(description = "状态: 0=离线 1=在线", example = "1")
    private Integer status;

}
