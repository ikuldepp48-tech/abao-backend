package com.geihou.module.finance.product.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.finance.product.dal.dataobject.ProductSkuDO;

/**
 * Mapper for product_sku table.
 * Supports soft delete via @TableLogic on ProductSkuDO.
 * No hard delete methods are provided; deleteById performs soft delete (UPDATE deleted=1).
 */
public interface ProductSkuMapper extends BaseMapperX<ProductSkuDO> {
}
