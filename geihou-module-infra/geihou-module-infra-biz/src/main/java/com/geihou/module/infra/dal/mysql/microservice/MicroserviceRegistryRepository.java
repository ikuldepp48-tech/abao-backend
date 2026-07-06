package com.geihou.module.infra.dal.mysql.microservice;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geihou.common.pojo.PageResult;
import com.geihou.module.infra.dal.dataobject.microservice.MicroserviceRegistryDO;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MicroserviceRegistryRepository {

    private final MicroserviceRegistryMapper mapper;

    public MicroserviceRegistryRepository(MicroserviceRegistryMapper mapper) {
        this.mapper = mapper;
    }

    public MicroserviceRegistryDO selectById(Long id) {
        return mapper.selectById(id);
    }

    public MicroserviceRegistryDO selectByServiceName(String serviceName) {
        return mapper.selectOne(MicroserviceRegistryDO::getServiceName, serviceName);
    }

    public List<MicroserviceRegistryDO> selectList() {
        return mapper.selectList(applyDefaultOrder(new LambdaQueryWrapper<>()));
    }

    public PageResult<MicroserviceRegistryDO> selectPage(
            Integer pageNo, Integer pageSize, LambdaQueryWrapper<MicroserviceRegistryDO> queryWrapper) {
        return mapper.selectPage(pageNo, pageSize, applyDefaultOrder(queryWrapper));
    }

    private LambdaQueryWrapper<MicroserviceRegistryDO> applyDefaultOrder(
            LambdaQueryWrapper<MicroserviceRegistryDO> queryWrapper) {
        LambdaQueryWrapper<MicroserviceRegistryDO> wrapper =
                queryWrapper == null ? new LambdaQueryWrapper<>() : queryWrapper;
        return wrapper.orderByAsc(MicroserviceRegistryDO::getStartupPriority)
                .orderByAsc(MicroserviceRegistryDO::getId);
    }
}
