package com.geihou.module.system.dal.mysql.auth;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.system.dal.dataobject.auth.AuthRbacProvisioningAuditDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * Bare mapper for {@link AuthRbacProvisioningAuditDO}.
 * H148: append-only, no custom methods, no Mapper XML.
 */
@Mapper
public interface AuthRbacProvisioningAuditMapper extends BaseMapperX<AuthRbacProvisioningAuditDO> {
}
