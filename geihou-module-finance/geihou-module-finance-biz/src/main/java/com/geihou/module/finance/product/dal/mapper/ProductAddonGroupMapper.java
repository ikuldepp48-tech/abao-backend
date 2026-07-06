package com.geihou.module.finance.product.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.finance.product.dal.dataobject.ProductAddonGroupDO;

/**
 * Mapper for product_addon_group table.
 * Supports soft delete via @TableLogic on ProductAddonGroupDO.
 * No hard delete methods are provided; deleteById performs soft delete (UPDATE deleted=1).
 */
public interface ProductAddonGroupMapper extends BaseMapperX<ProductAddonGroupDO> {
}
