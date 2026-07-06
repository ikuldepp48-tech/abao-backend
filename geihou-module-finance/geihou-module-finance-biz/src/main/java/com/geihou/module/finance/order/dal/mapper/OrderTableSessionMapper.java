package com.geihou.module.finance.order.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.finance.order.dal.dataobject.OrderTableSessionDO;

/**
 * Mapper for order_table_session table.
 *
 * <p>Supports soft delete via @TableLogic on OrderTableSessionDO.
 * No hard delete methods; deleteById performs soft delete (UPDATE deleted=1).
 * Core field session_no has no dedicated update method.
 */
public interface OrderTableSessionMapper extends BaseMapperX<OrderTableSessionDO> {
}
