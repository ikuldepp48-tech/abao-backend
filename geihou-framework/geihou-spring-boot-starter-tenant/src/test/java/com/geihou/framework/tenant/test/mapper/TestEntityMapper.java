package com.geihou.framework.tenant.test.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.geihou.framework.tenant.test.TestEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TestEntityMapper extends BaseMapper<TestEntity> {
}
