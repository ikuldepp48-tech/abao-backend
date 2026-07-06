package com.geihou.module.finance.product.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.geihou.module.finance.product.dal.dataobject.ProductAvailabilityLogDO;

/**
 * Mapper for product_availability_log table.
 *
 * <p><b>INSERT-only table.</b> This mapper intentionally extends BaseMapper (not BaseMapperX)
 * and does NOT declare any custom update or delete methods. The service layer must only
 * call {@code insert()} and {@code selectList()}. No update/delete path exists for this table.
 *
 * <p>The DO has no 'deleted' field and no @TableLogic annotation, reinforcing the
 * INSERT-only invariant.
 */
public interface ProductAvailabilityLogMapper extends BaseMapper<ProductAvailabilityLogDO> {
    // No custom methods: only inherited insert() and selectList() are used.
    // UPDATE and DELETE operations are prohibited for this INSERT-only table.
}
