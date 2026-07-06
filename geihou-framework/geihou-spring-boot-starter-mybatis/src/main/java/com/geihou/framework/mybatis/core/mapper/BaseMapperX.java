package com.geihou.framework.mybatis.core.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geihou.common.pojo.PageResult;
import com.geihou.framework.mybatis.util.PageResultConverter;
import java.util.List;
import java.util.Objects;

/**
 * Minimal Geihou extension over MyBatis-Plus {@link BaseMapper}.
 *
 * @param <T> data object type
 */
public interface BaseMapperX<T> extends BaseMapper<T> {

    default T selectOne(SFunction<T, ?> field, Object value) {
        return selectOne(new LambdaQueryWrapper<T>().eq(field, value));
    }

    default T selectOne(SFunction<T, ?> field1, Object value1, SFunction<T, ?> field2, Object value2) {
        return selectOne(new LambdaQueryWrapper<T>().eq(field1, value1).eq(field2, value2));
    }

    default List<T> selectList() {
        return selectList(new LambdaQueryWrapper<T>());
    }

    default List<T> selectList(SFunction<T, ?> field, Object value) {
        return selectList(new LambdaQueryWrapper<T>().eq(field, value));
    }

    default List<T> selectList(SFunction<T, ?> field1, Object value1, SFunction<T, ?> field2, Object value2) {
        return selectList(new LambdaQueryWrapper<T>().eq(field1, value1).eq(field2, value2));
    }

    default Long selectCount(SFunction<T, ?> field, Object value) {
        return selectCount(new LambdaQueryWrapper<T>().eq(field, value));
    }

    default int delete(SFunction<T, ?> field, Object value) {
        return delete(new LambdaQueryWrapper<T>().eq(field, value));
    }

    default PageResult<T> selectPage(Integer pageNo, Integer pageSize) {
        return selectPage(pageNo, pageSize, new LambdaQueryWrapper<T>());
    }

    default PageResult<T> selectPage(Integer pageNo, Integer pageSize, Wrapper<T> queryWrapper) {
        Objects.requireNonNull(queryWrapper, "queryWrapper must not be null");
        IPage<T> page = new Page<>(requirePositive(pageNo, "pageNo"), requirePositive(pageSize, "pageSize"));
        return PageResultConverter.from(selectPage(page, queryWrapper));
    }

    default PageResult<T> selectPage(SFunction<T, ?> field, Object value, Integer pageNo, Integer pageSize) {
        return selectPage(pageNo, pageSize, new LambdaQueryWrapper<T>().eq(field, value));
    }

    default PageResult<T> selectPage(SFunction<T, ?> field1, Object value1, SFunction<T, ?> field2, Object value2,
            Integer pageNo, Integer pageSize) {
        return selectPage(pageNo, pageSize, new LambdaQueryWrapper<T>().eq(field1, value1).eq(field2, value2));
    }

    private static int requirePositive(Integer value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value < 1) {
            throw new IllegalArgumentException(fieldName + " must be at least 1");
        }
        return value;
    }
}
