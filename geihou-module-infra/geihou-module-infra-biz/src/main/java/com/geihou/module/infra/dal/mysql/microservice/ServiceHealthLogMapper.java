package com.geihou.module.infra.dal.mysql.microservice;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.infra.dal.dataobject.microservice.ServiceHealthLogDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus mapper for {@code service_health_log}.
 *
 * <p>Runtime code should depend on {@link ServiceHealthLogRepository}; direct mapper usage is reserved for
 * repository internals and mapper smoke tests.</p>
 */
@Mapper
public interface ServiceHealthLogMapper extends BaseMapperX<ServiceHealthLogDO> {
}
