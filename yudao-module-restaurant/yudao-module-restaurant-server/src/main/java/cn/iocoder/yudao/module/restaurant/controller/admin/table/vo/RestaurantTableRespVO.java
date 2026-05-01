package cn.iocoder.yudao.module.restaurant.controller.admin.table.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 桌台 Response VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RestaurantTableRespVO extends RestaurantTableBaseVO {

    @Schema(description = "桌台编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "租户编号", example = "1")
    private Long tenantId;

    @Schema(description = "二维码 URL")
    private String qrCode;

    @Schema(description = "当前订单ID")
    private Long currentOrderId;

    @Schema(description = "开台时间")
    private LocalDateTime openTime;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
