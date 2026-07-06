package com.geihou.module.finance.checkout.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.finance.checkout.dal.dataobject.CheckoutSessionDO;

/**
 * Mapper for checkout_session table.
 *
 * <p>Supports soft delete via @TableLogic on CheckoutSessionDO.
 * Core status updates through service layer via updateById.
 */
public interface CheckoutSessionMapper extends BaseMapperX<CheckoutSessionDO> {
}
