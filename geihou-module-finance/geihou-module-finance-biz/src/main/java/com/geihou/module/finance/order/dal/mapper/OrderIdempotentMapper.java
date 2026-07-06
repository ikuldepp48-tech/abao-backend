package com.geihou.module.finance.order.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.finance.order.dal.dataobject.OrderIdempotentDO;

/**
 * Mapper for order_idempotent table.
 *
 * <p>No @TableLogic on OrderIdempotentDO (no soft delete).
 * DB unique key (tenant_id, idempotent_key) provides idempotency fallback.
 * Expired records are cleaned up by a future scheduled job, not soft-deleted.
 */
public interface OrderIdempotentMapper extends BaseMapperX<OrderIdempotentDO> {
}
