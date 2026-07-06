package com.geihou.module.system.dal.mysql.auth;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.geihou.module.system.dal.dataobject.auth.AuthRefreshTokenDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthRefreshTokenMapper extends BaseMapper<AuthRefreshTokenDO> {
}
