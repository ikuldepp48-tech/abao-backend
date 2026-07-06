package com.geihou.module.infra.dal.mysql.microservice;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.infra.dal.dataobject.microservice.MicroserviceRegistryDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MicroserviceRegistryMapper extends BaseMapperX<MicroserviceRegistryDO> {
}
