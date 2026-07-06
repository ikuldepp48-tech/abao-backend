package com.geihou.module.supplychain.bom.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.supplychain.bom.dal.dataobject.BomRecipeItemDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * Mapper for bom_recipe_item table.
 *
 * <p>BOM recipe item CRUD, tenant-isolated.
 */
public interface BomRecipeItemMapper extends BaseMapperX<BomRecipeItemDO> {

    /**
     * List items by recipe_id + tenant_id.
     */
    @Select("SELECT * FROM bom_recipe_item WHERE tenant_id = #{tenantId} " +
            "AND recipe_id = #{recipeId} AND deleted = false ORDER BY id")
    List<BomRecipeItemDO> listByTenantRecipe(@Param("tenantId") Long tenantId,
                                              @Param("recipeId") Long recipeId);

    /**
     * Soft-delete all items for a recipe (tenant-isolated).
     */
    @Update("UPDATE bom_recipe_item SET deleted = true, " +
            "updater = #{updater}, update_time = #{updateTime} " +
            "WHERE tenant_id = #{tenantId} AND recipe_id = #{recipeId} AND deleted = false")
    int softDeleteByTenantRecipe(@Param("tenantId") Long tenantId,
                                  @Param("recipeId") Long recipeId,
                                  @Param("updater") String updater,
                                  @Param("updateTime") java.time.LocalDateTime updateTime);
}
