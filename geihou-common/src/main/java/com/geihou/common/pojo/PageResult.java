package com.geihou.common.pojo;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Generic paged result for Geihou REST responses.
 *
 * @param <T> row type
 */
public final class PageResult<T> {

    private final List<T> list;
    private final Long total;
    private final Integer pageNo;
    private final Integer pageSize;

    private PageResult(List<T> list, Long total, Integer pageNo, Integer pageSize) {
        this.list = List.copyOf(list);
        this.total = total;
        this.pageNo = pageNo;
        this.pageSize = pageSize;
    }

    public static <T> PageResult<T> of(List<T> list, Long total, Integer pageNo, Integer pageSize) {
        Objects.requireNonNull(list, "list must not be null");
        Objects.requireNonNull(total, "total must not be null");
        Objects.requireNonNull(pageNo, "pageNo must not be null");
        Objects.requireNonNull(pageSize, "pageSize must not be null");
        if (total < 0) {
            throw new IllegalArgumentException("total must not be negative");
        }
        if (pageNo < 1) {
            throw new IllegalArgumentException("pageNo must be at least 1");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be at least 1");
        }
        return new PageResult<>(list, total, pageNo, pageSize);
    }

    public static <T> PageResult<T> empty(Integer pageNo, Integer pageSize) {
        return of(Collections.emptyList(), 0L, pageNo, pageSize);
    }

    public List<T> getList() {
        return list;
    }

    public Long getTotal() {
        return total;
    }

    public Integer getPageNo() {
        return pageNo;
    }

    public Integer getPageSize() {
        return pageSize;
    }
}
