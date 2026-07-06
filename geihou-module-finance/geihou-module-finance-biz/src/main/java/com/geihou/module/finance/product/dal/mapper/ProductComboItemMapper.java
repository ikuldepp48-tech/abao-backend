package com.geihou.module.finance.product.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.finance.product.dal.dataobject.ProductComboItemDO;

/**
 * Mapper for product_combo_item table.
 * Supports soft delete via @TableLogic on ProductComboItemDO.
 * No hard delete methods are provided; deleteById performs soft delete (UPDATE deleted=1).
 */
public interface ProductComboItemMapper extends BaseMapperX<ProductComboItemDO> {
}
