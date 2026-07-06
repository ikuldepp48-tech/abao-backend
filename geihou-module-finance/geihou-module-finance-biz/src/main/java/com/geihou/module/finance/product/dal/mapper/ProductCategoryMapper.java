package com.geihou.module.finance.product.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.finance.product.dal.dataobject.ProductCategoryDO;

/**
 * Mapper for product_category table.
 * Supports soft delete via @TableLogic on ProductCategoryDO.
 */
public interface ProductCategoryMapper extends BaseMapperX<ProductCategoryDO> {
}
