package com.geihou.module.finance.cart.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.finance.cart.dal.dataobject.CartDO;

/**
 * Mapper for cart table.
 *
 * <p>Supports soft delete via @TableLogic on CartDO.
 * No hard delete methods; deleteById performs soft delete (UPDATE deleted=1).
 * Core summary fields updated through service layer via updateById with optimistic lock.
 */
public interface CartMapper extends BaseMapperX<CartDO> {
}
