package com.geihou.module.finance.cart.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.finance.cart.dal.dataobject.CartItemDO;

/**
 * Mapper for cart_item table.
 *
 * <p>Supports soft delete via @TableLogic on CartItemDO.
 * Immutable snapshot fields (sku_name_snapshot, unit_price_snapshot) have no
 * dedicated update methods — they are set at creation and never modified (AC-9).
 */
public interface CartItemMapper extends BaseMapperX<CartItemDO> {
}
