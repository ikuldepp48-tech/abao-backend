package com.geihou.module.supplychain.bom.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.supplychain.bom.dal.dataobject.BomRecipeDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * Mapper for bom_recipe table.
 *
 * <p>BOM recipe header CRUD, tenant-isolated.
 */
public interface BomRecipeMapper extends BaseMapperX<BomRecipeDO> {

    /**
     * Find by id + tenant_id (tenant isolation).
     */
    @Select("SELECT * FROM bom_recipe WHERE id = #{id} " +
            "AND tenant_id = #{tenantId} AND deleted = false")
    BomRecipeDO selectByIdAndTenant(@Param("id") Long id,
                                     @Param("tenantId") Long tenantId);

    /**
     * List all recipes for a product within a tenant.
     */
    @Select("SELECT * FROM bom_recipe WHERE tenant_id = #{tenantId} " +
            "AND product_id = #{productId} AND deleted = false ORDER BY version_no")
    List<BomRecipeDO> listByTenantProduct(@Param("tenantId") Long tenantId,
                                           @Param("productId") Long productId);

    /**
     * Find active recipe for a product within a tenant.
     */
    @Select("SELECT * FROM bom_recipe WHERE tenant_id = #{tenantId} " +
            "AND product_id = #{productId} AND status = 'ACTIVE' AND deleted = false")
    BomRecipeDO selectActiveByTenantProduct(@Param("tenantId") Long tenantId,
                                             @Param("productId") Long productId);

    /**
     * Find max version_no for a product within a tenant.
     */
    @Select("SELECT COALESCE(MAX(version_no), 0) FROM bom_recipe " +
            "WHERE tenant_id = #{tenantId} AND product_id = #{productId} AND deleted = false")
    Integer selectMaxVersionNo(@Param("tenantId") Long tenantId,
                                @Param("productId") Long productId);

    /**
     * Archive active recipes for a product within a tenant (set status to ARCHIVED).
     */
    @Update("UPDATE bom_recipe SET status = 'ARCHIVED', " +
            "updater = #{updater}, update_time = #{updateTime} " +
            "WHERE tenant_id = #{tenantId} AND product_id = #{productId} " +
            "AND status = 'ACTIVE' AND deleted = false")
    int archiveActiveRecipes(@Param("tenantId") Long tenantId,
                              @Param("productId") Long productId,
                              @Param("updater") String updater,
                              @Param("updateTime") java.time.LocalDateTime updateTime);

    /**
     * Update status by id + tenant_id (tenant-isolated).
     */
    @Update("UPDATE bom_recipe SET status = #{status}, " +
            "updater = #{updater}, update_time = #{updateTime} " +
            "WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = false")
    int updateStatusByIdAndTenant(@Param("id") Long id,
                                   @Param("tenantId") Long tenantId,
                                   @Param("status") String status,
                                   @Param("updater") String updater,
                                   @Param("updateTime") java.time.LocalDateTime updateTime);

    /**
     * Find any non-archived recipe (DRAFT or ACTIVE) for a product within a tenant.
     * Used for activation-time cycle detection that must consider DRAFT recipes.
     */
    @Select("SELECT * FROM bom_recipe WHERE tenant_id = #{tenantId} " +
            "AND product_id = #{productId} AND status IN ('DRAFT','ACTIVE') AND deleted = false")
    List<BomRecipeDO> selectNonArchivedByTenantProduct(@Param("tenantId") Long tenantId,
                                                        @Param("productId") Long productId);
}
