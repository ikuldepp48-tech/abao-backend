package cn.iocoder.yudao.module.restaurant.controller.admin.printer.template.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 打印模板 新增/编辑 Request VO")
@Data
public class RestaurantPrinterTemplateSaveReqVO {

    @Schema(description = "编号", example = "1")
    private Long id;

    @Schema(description = "打印机ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "3")
    @NotNull(message = "打印机ID不能为空")
    private Long printerId;

    @Schema(description = "纸张宽度(mm)", example = "58")
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
