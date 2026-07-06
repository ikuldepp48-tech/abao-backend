package com.geihou.module.finance.order.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.finance.order.dal.dataobject.OrderPaymentDO;

/**
 * Mapper for order_payment table.
 *
 * <p>INSERT-only: no update/delete methods are provided.
 * No @TableLogic on OrderPaymentDO since this table does not support soft delete.
 * Extending BaseMapperX for insert/select capabilities only.
 * Do NOT call updateById, deleteById, or delete methods on this mapper.
 */
public interface OrderPaymentMapper extends BaseMapperX<OrderPaymentDO> {
    // INSERT-only. No update or delete operations.
}
