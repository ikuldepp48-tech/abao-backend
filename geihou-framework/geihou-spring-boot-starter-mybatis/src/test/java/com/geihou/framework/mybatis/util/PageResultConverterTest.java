package com.geihou.framework.mybatis.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geihou.common.pojo.PageResult;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import org.junit.jupiter.api.Test;

class PageResultConverterTest {

    @Test
    void fromShouldMapMybatisPageToPageResult() {
        Page<String> page = new Page<>(2, 10);
        page.setRecords(List.of("a", "b", "c"));
        page.setTotal(30);

        PageResult<String> result = PageResultConverter.from(page);

        assertEquals(List.of("a", "b", "c"), result.getList());
        assertEquals(30L, result.getTotal());
        assertEquals(2, result.getPageNo());
        assertEquals(10, result.getPageSize());
    }

    @Test
    void fromShouldMapEmptyPage() {
        Page<String> page = new Page<>(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);

        PageResult<String> result = PageResultConverter.from(page);

        assertTrue(result.getList().isEmpty());
        assertEquals(0L, result.getTotal());
        assertEquals(1, result.getPageNo());
        assertEquals(20, result.getPageSize());
    }

    @Test
    void fromShouldRejectNullPage() {
        assertThrows(NullPointerException.class, () -> PageResultConverter.from(null));
    }

    @Test
    void fromShouldConvertNullRecordsToEmptyList() {
        Page<String> page = new Page<>(1, 10);
        page.setRecords(null);
        page.setTotal(0);

        PageResult<String> result = PageResultConverter.from(page);

        assertTrue(result.getList().isEmpty());
        assertEquals(0L, result.getTotal());
        assertEquals(1, result.getPageNo());
        assertEquals(10, result.getPageSize());
    }

    @Test
    void fromShouldRejectInvalidCurrent() {
        Page<String> page = pageWithCurrentAndSize(0, 10);
        page.setRecords(List.of());
        page.setTotal(0);

        assertThrows(IllegalArgumentException.class, () -> PageResultConverter.from(page));
    }

    @Test
    void fromShouldRejectInvalidSize() {
        Page<String> page = pageWithCurrentAndSize(1, 0);
        page.setRecords(List.of());
        page.setTotal(0);

        assertThrows(IllegalArgumentException.class, () -> PageResultConverter.from(page));
    }

    @Test
    void fromShouldRejectCurrentOutsideIntegerRange() {
        Page<String> page = pageWithCurrentAndSize((long) Integer.MAX_VALUE + 1L, 10);
        page.setRecords(List.of());
        page.setTotal(0);

        assertThrows(IllegalArgumentException.class, () -> PageResultConverter.from(page));
    }

    @Test
    void constructorShouldRejectReflectionInstantiation() throws Exception {
        Constructor<PageResultConverter> constructor = PageResultConverter.class.getDeclaredConstructor();

        assertFalse(constructor.canAccess(null));
        constructor.setAccessible(true);
        InvocationTargetException exception = assertThrows(InvocationTargetException.class, constructor::newInstance);
        assertInstanceOf(UnsupportedOperationException.class, exception.getCause());
    }

    private static Page<String> pageWithCurrentAndSize(long current, long size) {
        return new Page<>(1, 10) {

            @Override
            public long getCurrent() {
                return current;
            }

            @Override
            public long getSize() {
                return size;
            }
        };
    }
}
