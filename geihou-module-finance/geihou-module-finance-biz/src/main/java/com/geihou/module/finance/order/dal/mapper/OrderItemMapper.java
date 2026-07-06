package com.geihou.module.finance.order.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.finance.order.dal.dataobject.OrderItemDO;

/**
 * Mapper for order_items table.
 *
 * <p>Supports soft delete via @TableLogic on OrderItemDO.
 * Immutable snapshot fields (sku_id/sku_code/sku_name/unit_price/quantity/item_total)
 * have no dedicated update methods — they are set at creation and never modified.
 */
public interface OrderItemMapper extends BaseMapperX<OrderItemDO> {
}
