package cn.iocoder.yudao.module.consulting.dal.dataobject.tenant;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 咨询模块 → system_tenant 表的轻量映射
 * <p>
 * 仅供创建客户时自动创建租户使用，仅含必需字段。
 * system_tenant 表本身不含 tenant_id 列，标记 @TenantIgnore 避免拦截器添加。
 */
@TableName("system_tenant")
@TenantIgnore
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemTenantDO {

    @TableId
    private Long id;

    private String name;

    private Long contactUserId;

    private String contactName;

    private String contactMobile;

    private Integer status;

    private String websites;

    private Long packageId;

    private LocalDateTime expireTime;

    private Integer accountCount;

    private String creator;

    private LocalDateTime createTime;

}
