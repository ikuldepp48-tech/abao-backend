package com.geihou.framework.mybatis.util;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.geihou.common.pojo.PageResult;
import java.util.List;
import java.util.Objects;

/**
 * Converts MyBatis-Plus page results to the Geihou common REST page contract.
 */
public final class PageResultConverter {

    private PageResultConverter() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static <T> PageResult<T> from(IPage<T> page) {
        Objects.requireNonNull(page, "page must not be null");
        return PageResult.of(
                recordsOf(page),
                page.getTotal(),
                toInteger(page.getCurrent(), "pageNo"),
                toInteger(page.getSize(), "pageSize"));
    }

    private static <T> List<T> recordsOf(IPage<T> page) {
        List<T> records = page.getRecords();
        return records == null ? List.of() : records;
    }

    private static Integer toInteger(long value, String fieldName) {
        try {
            return Math.toIntExact(value);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(fieldName + " must fit in Integer", ex);
        }
    }
}
