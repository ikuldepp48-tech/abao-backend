package cn.iocoder.yudao.module.consulting.controller.admin.client.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 咨询客户联系人分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ConsultingClientContactPageReqVO extends PageParam {

    @Schema(description = "关联客户 ID", example = "1")
    private Long clientId;

    @Schema(description = "联系人姓名", example = "张三")
    private String name;

    @Schema(description = "角色", example = "boss")
    private String role;

}
