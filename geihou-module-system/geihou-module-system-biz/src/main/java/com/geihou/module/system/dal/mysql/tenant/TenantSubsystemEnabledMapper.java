package com.geihou.module.system.dal.mysql.tenant;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.system.dal.dataobject.tenant.TenantSubsystemEnabledDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TenantSubsystemEnabledMapper extends BaseMapperX<TenantSubsystemEnabledDO> {
}
