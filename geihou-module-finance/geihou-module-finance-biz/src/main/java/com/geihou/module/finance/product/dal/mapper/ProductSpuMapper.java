package com.geihou.module.finance.product.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.finance.product.dal.dataobject.ProductSpuDO;

/**
 * Mapper for product_spu table.
 * Supports soft delete via @TableLogic on ProductSpuDO.
 * No hard delete methods are provided; deleteById performs soft delete (UPDATE deleted=1).
 */
public interface ProductSpuMapper extends BaseMapperX<ProductSpuDO> {
}
