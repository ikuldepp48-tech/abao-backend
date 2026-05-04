package cn.iocoder.yudao.module.consulting.dal.mysql.tenant;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.consulting.dal.dataobject.tenant.SystemTenantDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 仅供创建客户时自动创建租户使用，不修改 system 模块原有代码
 */
@Mapper
public interface SystemTenantMapper extends BaseMapperX<SystemTenantDO> {
}
