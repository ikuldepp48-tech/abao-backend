package com.geihou.module.finance.checkout.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.finance.checkout.dal.dataobject.CheckoutIdempotentDO;

/**
 * Mapper for checkout_idempotent table.
 *
 * <p>INSERT-only: no update/delete path. No @TableLogic.
 * DB unique key (tenant_id, idempotent_key) provides idempotent guarantee.
 */
public interface CheckoutIdempotentMapper extends BaseMapperX<CheckoutIdempotentDO> {
    // INSERT-only. No update or delete operations.
}
