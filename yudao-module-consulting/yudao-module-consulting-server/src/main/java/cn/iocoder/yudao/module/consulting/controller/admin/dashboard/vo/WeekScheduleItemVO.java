package cn.iocoder.yudao.module.consulting.controller.admin.dashboard.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 本周日程项 Response VO")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeekScheduleItemVO {

    @Schema(description = "日期", example = "2026-05-05", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate date;

    @Schema(description = "时间", example = "2026-05-05T14:30:00")
    private LocalDateTime time;

    @Schema(description = "日程类型", example = "phase_completed", requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;

    @Schema(description = "标题", example = "阶段1完成：项目启动+共识锚定", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(description = "客户ID", example = "1")
    private Long clientId;

    @Schema(description = "客户名称", example = "XX餐饮公司")
    private String clientName;

    @Schema(description = "项目ID", example = "1")
    private Long engagementId;

    @Schema(description = "项目名称", example = "年度顾问服务")
    private String engagementTitle;

}
