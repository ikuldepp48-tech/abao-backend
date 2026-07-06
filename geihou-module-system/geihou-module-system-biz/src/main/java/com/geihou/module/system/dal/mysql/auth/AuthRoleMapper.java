package com.geihou.module.system.dal.mysql.auth;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.system.dal.dataobject.auth.AuthRoleDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthRoleMapper extends BaseMapperX<AuthRoleDO> {
}
