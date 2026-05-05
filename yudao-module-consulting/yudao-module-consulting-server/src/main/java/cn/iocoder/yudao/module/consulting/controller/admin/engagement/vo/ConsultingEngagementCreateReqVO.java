package cn.iocoder.yudao.module.consulting.controller.admin.engagement.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 咨询项目创建 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ConsultingEngagementCreateReqVO extends ConsultingEngagementBaseVO {
}
