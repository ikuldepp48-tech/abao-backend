package cn.iocoder.yudao.module.consulting.dal.dataobject.client;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 咨询客户联系人 DO
 */
@TableName("consulting_client_contact")
@TenantIgnore
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsultingClientContactDO extends BaseDO {

    @TableId
    private Long id;

    /**
     * 咨询业务租户 ID
     */
    private Long tenantId;

    /**
     * 关联客户 ID
     */
    private Long clientId;

    /**
     * 联系人姓名
     */
    private String name;

    /**
     * 角色（字典 consulting_contact_role）
     */
    private String role;

    /**
     * 电话号码
     */
    private String phone;

    /**
     * 电子邮箱
     */
    private String email;

    /**
     * 微信号
     */
    private String wechat;

    /**
     * 是否主联系人（0=否，1=是）
     */
    private Integer isPrimary;

    /**
     * 备注
     */
    private String remark;

}
