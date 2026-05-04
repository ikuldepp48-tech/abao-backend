package cn.iocoder.yudao.module.consulting.controller.admin.client.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 咨询客户档案分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ConsultingClientPageReqVO extends PageParam {

    @Schema(description = "客户公司名", example = "阿堡")
    private String name;

    @Schema(description = "简称", example = "阿堡")
    private String shortName;

    @Schema(description = "行业", example = "catering")
    private String industry;

    @Schema(description = "客户状态", example = "2")
    private Integer status;

    @Schema(description = "咨询师用户 ID", example = "1")
    private Long consultantUserId;

}
