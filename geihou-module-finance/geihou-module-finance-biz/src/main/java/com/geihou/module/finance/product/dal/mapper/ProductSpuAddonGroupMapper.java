package com.geihou.module.finance.product.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.finance.product.dal.dataobject.ProductSpuAddonGroupDO;

/**
 * Mapper for product_spu_addon_group mapping table.
 * Supports soft delete via @TableLogic on ProductSpuAddonGroupDO.
 * No hard delete methods are provided; deleteById performs soft delete (UPDATE deleted=1).
 */
public interface ProductSpuAddonGroupMapper extends BaseMapperX<ProductSpuAddonGroupDO> {
}
