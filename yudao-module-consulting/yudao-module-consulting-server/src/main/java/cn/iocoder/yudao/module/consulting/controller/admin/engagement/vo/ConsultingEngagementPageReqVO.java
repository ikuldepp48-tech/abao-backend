package cn.iocoder.yudao.module.consulting.controller.admin.engagement.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 咨询项目分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ConsultingEngagementPageReqVO extends PageParam {

    @Schema(description = "关联客户 ID", example = "1")
    private Long clientId;

    @Schema(description = "项目类型", example = "annual_advisor")
    private String type;

    @Schema(description = "项目状态", example = "in_progress")
    private String status;

    @Schema(description = "当前阶段", example = "2")
    private Integer currentPhase;

    @Schema(description = "项目名称", example = "阿堡")
    private String title;

}
