package com.geihou.module.finance.product.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.finance.product.dal.dataobject.ProductAddonOptionDO;

/**
 * Mapper for product_addon_option table.
 * Supports soft delete via @TableLogic on ProductAddonOptionDO.
 * No hard delete methods are provided; deleteById performs soft delete (UPDATE deleted=1).
 */
public interface ProductAddonOptionMapper extends BaseMapperX<ProductAddonOptionDO> {
}
