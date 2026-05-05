package cn.iocoder.yudao.module.consulting.controller.admin.dashboard.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Schema(description = "管理后台 - 工作台摘要统计 Response VO")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryRespVO {

    @Schema(description = "客户总数", example = "12")
    private Long totalClients;

    @Schema(description = "进行中项目数", example = "5")
    private Long activeEngagements;

    @Schema(description = "已完成项目数", example = "8")
    private Long completedEngagements;

    @Schema(description = "合同总金额（万元）", example = "150.00")
    private BigDecimal totalContractAmount;

    @Schema(description = "已收款总金额（万元）", example = "85.50")
    private BigDecimal totalPaidAmount;

}
