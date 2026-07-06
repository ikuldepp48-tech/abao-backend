package com.geihou.module.system.dal.mysql.auth;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.system.dal.dataobject.auth.AuthRolePermissionDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthRolePermissionMapper extends BaseMapperX<AuthRolePermissionDO> {
}
