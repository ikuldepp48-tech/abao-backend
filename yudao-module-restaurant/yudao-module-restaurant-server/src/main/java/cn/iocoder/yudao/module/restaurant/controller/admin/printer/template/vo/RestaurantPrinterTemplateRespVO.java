package cn.iocoder.yudao.module.restaurant.controller.admin.printer.template.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 打印模板 Response VO")
@Data
public class RestaurantPrinterTemplateRespVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "打印机ID")
    private Long printerId;

    @Schema(description = "纸张宽度(mm)")
    private Integer paperWidth;

    @Schema(description = "单据抬头")
    private String headerText;

    @Schema(description = "单据尾部")
    private String footerText;

    @Schema(description = "显示Logo (0/1)")
    private Integer showLogo;

    @Schema(description = "显示二维码 (0/1)")
    private Integer showQr;

    @Schema(description = "自动切纸 (0/1)")
    private Integer autoCut;

    @Schema(description = "打印份数")
    private Integer printCopies;

}
