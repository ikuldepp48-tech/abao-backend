package cn.iocoder.yudao.module.consulting.controller.admin.client.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * 咨询客户联系人 Base VO
 */
@Data
public class ConsultingClientContactBaseVO {

    @Schema(description = "关联客户 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "关联客户不能为空")
    private Long clientId;

    @Schema(description = "联系人姓名", requiredMode = Schema.RequiredMode.REQUIRED, example = "张三")
    @NotEmpty(message = "联系人姓名不能为空")
    private String name;

    @Schema(description = "角色（字典 consulting_contact_role）", example = "boss")
    private String role;

    @Schema(description = "电话号码", example = "13800138000")
    private String phone;

    @Schema(description = "电子邮箱", example = "zhangsan@example.com")
    private String email;

    @Schema(description = "微信号", example = "wx_zhangsan")
    private String wechat;

    @Schema(description = "是否主联系人（0=否，1=是）", example = "1")
    private Integer isPrimary;

    @Schema(description = "备注")
    private String remark;

}
