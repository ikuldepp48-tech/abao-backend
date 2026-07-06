package com.geihou.module.infra.dal.mysql.microservice;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.geihou.common.pojo.PageResult;
import com.geihou.module.infra.dal.dataobject.microservice.ServiceHealthLogDO;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class ServiceHealthLogRepository {

    private final ServiceHealthLogMapper mapper;

    public ServiceHealthLogRepository(ServiceHealthLogMapper mapper) {
        this.mapper = mapper;
    }

    public int insert(ServiceHealthLogDO entity) {
        return mapper.insert(entity);
    }

    public ServiceHealthLogDO selectById(Long id) {
        return mapper.selectById(id);
    }

    public <V> ServiceHealthLogDO selectOne(SFunction<ServiceHealthLogDO, V> field, V value) {
        return mapper.selectOne(field, value);
    }

    public <V1, V2> ServiceHealthLogDO selectOne(
            SFunction<ServiceHealthLogDO, V1> field1, V1 value1,
            SFunction<ServiceHealthLogDO, V2> field2, V2 value2) {
        return mapper.selectOne(field1, value1, field2, value2);
    }

    public List<ServiceHealthLogDO> selectList() {
        return mapper.selectList();
    }

    public <V> List<ServiceHealthLogDO> selectList(SFunction<ServiceHealthLogDO, V> field, V value) {
        return mapper.selectList(field, value);
    }

    public <V1, V2> List<ServiceHealthLogDO> selectList(
            SFunction<ServiceHealthLogDO, V1> field1, V1 value1,
            SFunction<ServiceHealthLogDO, V2> field2, V2 value2) {
        return mapper.selectList(field1, value1, field2, value2);
    }

    public <V> Long selectCount(SFunction<ServiceHealthLogDO, V> field, V value) {
        return mapper.selectCount(field, value);
    }

    public PageResult<ServiceHealthLogDO> selectPage(Integer pageNo, Integer pageSize) {
        return mapper.selectPage(pageNo, pageSize);
    }

    public PageResult<ServiceHealthLogDO> selectPage(
            Integer pageNo, Integer pageSize, LambdaQueryWrapper<ServiceHealthLogDO> queryWrapper) {
        return mapper.selectPage(pageNo, pageSize, queryWrapper);
    }

    public <V> PageResult<ServiceHealthLogDO> selectPage(
            SFunction<ServiceHealthLogDO, V> field, V value, Integer pageNo, Integer pageSize) {
        return mapper.selectPage(field, value, pageNo, pageSize);
    }

    public <V1, V2> PageResult<ServiceHealthLogDO> selectPage(
            SFunction<ServiceHealthLogDO, V1> field1, V1 value1,
            SFunction<ServiceHealthLogDO, V2> field2, V2 value2,
            Integer pageNo, Integer pageSize) {
        return mapper.selectPage(field1, value1, field2, value2, pageNo, pageSize);
    }
}
