package cn.iocoder.yudao.module.consulting.controller.admin.engagement.vo;

import cn.iocoder.yudao.module.consulting.dal.dataobject.engagement.DeliverableItem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 咨询项目 Base VO
 */
@Data
public class ConsultingEngagementBaseVO {

    @Schema(description = "关联客户 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "关联客户不能为空")
    private Long clientId;

    @Schema(description = "咨询师用户 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long consultantUserId;

    @Schema(description = "项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "ENG-2026-001")
    @NotEmpty(message = "项目编号不能为空")
    private String code;

    @Schema(description = "项目名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "阿堡2026年度顾问")
    @NotEmpty(message = "项目名称不能为空")
    private String title;

    @Schema(description = "项目类型（字典 consulting_engagement_type）", example = "annual_advisor")
    @NotEmpty(message = "项目类型不能为空")
    private String type;

    @Schema(description = "项目状态（字典 consulting_engagement_status）", example = "in_progress")
    private String status;

    @Schema(description = "项目开始日期")
    private LocalDate startDate;

    @Schema(description = "计划结束日期")
    private LocalDate endDate;

    @Schema(description = "实际结束日期")
    private LocalDate actualEndDate;

    @Schema(description = "合同金额（万元）", example = "12.00")
    private BigDecimal contractAmount;

    @Schema(description = "已收款（万元）", example = "0.00")
    private BigDecimal paidAmount;

    @Schema(description = "当前阶段（1-6）", example = "2")
    private Integer currentPhase;

    @Schema(description = "项目目标", example = "3个月内将翻台率从X提升到Y")
    private String objectives;

    @Schema(description = "服务范围", example = "全门店运营诊断")
    private String scope;

    @Schema(description = "边界约束", example = "预算上限15万")
    private String constraints;

    @Schema(description = "交付物清单")
    private List<DeliverableItem> deliverables;

}
