package com.geihou.framework.mybatis.test.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.framework.mybatis.test.TestDataObject;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TestDataObjectMapper extends BaseMapperX<TestDataObject> {
}
