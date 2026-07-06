package com.geihou.module.finance.cart.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.finance.cart.dal.dataobject.CartEventLogDO;

/**
 * Mapper for cart_event_log table.
 *
 * <p>INSERT-only: no update/delete methods are provided.
 * No @TableLogic on CartEventLogDO since this table does not support soft delete.
 * Extending BaseMapperX for insert/select capabilities only.
 * Do NOT call updateById, deleteById, or delete methods on this mapper (AC-4).
 */
public interface CartEventLogMapper extends BaseMapperX<CartEventLogDO> {
    // INSERT-only. No update or delete operations.
}
