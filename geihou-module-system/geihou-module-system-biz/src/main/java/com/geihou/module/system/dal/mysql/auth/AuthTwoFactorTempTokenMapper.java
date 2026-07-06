package com.geihou.module.system.dal.mysql.auth;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.system.dal.dataobject.auth.AuthTwoFactorTempTokenDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthTwoFactorTempTokenMapper extends BaseMapperX<AuthTwoFactorTempTokenDO> {
}
