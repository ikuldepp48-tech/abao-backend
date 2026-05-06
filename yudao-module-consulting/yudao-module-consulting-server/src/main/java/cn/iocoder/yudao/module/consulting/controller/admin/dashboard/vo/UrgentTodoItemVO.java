package cn.iocoder.yudao.module.consulting.controller.admin.dashboard.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "管理后台 - 紧急待办项 Response VO")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UrgentTodoItemVO {

    @Schema(description = "待办类型", example = "contract_expiring", requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;

    @Schema(description = "标题", example = "XX餐饮公司服务合同即将到期", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(description = "客户ID", example = "1")
    private Long clientId;

    @Schema(description = "客户名称", example = "XX餐饮公司")
    private String clientName;

    @Schema(description = "优先级: critical/warning/info", example = "critical", requiredMode = Schema.RequiredMode.REQUIRED)
    private String priority;

    @Schema(description = "截止日期", example = "2026-06-15")
    private LocalDate deadline;

    @Schema(description = "距截止天数，负数表示已超时", example = "7")
    private Integer daysRemaining;

    @Schema(description = "详细信息", example = "合同将于15天后到期")
    private String detail;

    @Schema(description = "可执行操作按钮列表", example = "[\"续约\", \"终止\"]")
    private List<String> actions;

}
