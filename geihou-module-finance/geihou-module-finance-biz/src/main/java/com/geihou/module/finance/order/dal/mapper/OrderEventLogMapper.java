package com.geihou.module.finance.order.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.finance.order.dal.dataobject.OrderEventLogDO;

/**
 * Mapper for order_event_log table.
 *
 * <p>INSERT-only: no update/delete methods are provided.
 * No @TableLogic on OrderEventLogDO since this table does not support soft delete.
 * Extending BaseMapperX for insert/select capabilities only.
 * Do NOT call updateById, deleteById, or delete methods on this mapper.
 */
public interface OrderEventLogMapper extends BaseMapperX<OrderEventLogDO> {
    // INSERT-only. No update or delete operations.
}
