package com.geihou.module.finance.product.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.finance.product.dal.dataobject.ProductComboDO;

/**
 * Mapper for product_combo table.
 * Supports soft delete via @TableLogic on ProductComboDO.
 * No hard delete methods are provided; deleteById performs soft delete (UPDATE deleted=1).
 */
public interface ProductComboMapper extends BaseMapperX<ProductComboDO> {
}
