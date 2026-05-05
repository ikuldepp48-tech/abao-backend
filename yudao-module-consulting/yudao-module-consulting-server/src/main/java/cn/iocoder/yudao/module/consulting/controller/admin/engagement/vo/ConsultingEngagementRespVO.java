package cn.iocoder.yudao.module.consulting.controller.admin.engagement.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 咨询项目 Response VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ConsultingEngagementRespVO extends ConsultingEngagementBaseVO {

    @Schema(description = "项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "租户 ID", example = "1")
    private Long tenantId;

    @Schema(description = "阶段1完成时间")
    private LocalDateTime phase1DoneAt;

    @Schema(description = "阶段2完成时间")
    private LocalDateTime phase2DoneAt;

    @Schema(description = "阶段3完成时间")
    private LocalDateTime phase3DoneAt;

    @Schema(description = "阶段4完成时间")
    private LocalDateTime phase4DoneAt;

    @Schema(description = "阶段5完成时间")
    private LocalDateTime phase5DoneAt;

    @Schema(description = "阶段6完成时间")
    private LocalDateTime phase6DoneAt;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
