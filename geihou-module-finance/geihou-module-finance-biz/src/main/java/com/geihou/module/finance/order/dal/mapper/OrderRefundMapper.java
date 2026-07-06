package com.geihou.module.finance.order.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.finance.order.dal.dataobject.OrderRefundDO;

/**
 * Mapper for order_refund table.
 *
 * <p>Supports insert/select/update operations (refund records need status updates
 * for approval flow, execution, and failure handling).
 * Soft delete via @TableLogic on OrderRefundDO.
 * No hard delete methods.
 */
public interface OrderRefundMapper extends BaseMapperX<OrderRefundDO> {
    // Supports insert + select + updateById for refund lifecycle.
}
