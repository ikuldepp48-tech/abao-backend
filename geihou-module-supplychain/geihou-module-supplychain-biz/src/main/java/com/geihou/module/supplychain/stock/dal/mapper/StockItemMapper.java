package com.geihou.module.supplychain.stock.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.supplychain.stock.dal.dataobject.StockItemDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * Mapper for stock_item table.
 *
 * <p>Stock item master data CRUD and mapping governance.
 */
public interface StockItemMapper extends BaseMapperX<StockItemDO> {

    /**
     * Find item by tenant_id + sku_code (unique key).
     */
    @Select("SELECT * FROM stock_item WHERE tenant_id = #{tenantId} " +
            "AND sku_code = #{skuCode} AND deleted = false")
    StockItemDO selectByTenantSkuCode(@Param("tenantId") Long tenantId,
                                       @Param("skuCode") String skuCode);

    /**
     * Find active item by tenant_id + sku_code (for StockQueryApi).
     */
    @Select("SELECT * FROM stock_item WHERE tenant_id = #{tenantId} " +
            "AND sku_code = #{skuCode} AND is_active = true AND deleted = false")
    StockItemDO selectActiveByTenantSkuCode(@Param("tenantId") Long tenantId,
                                             @Param("skuCode") String skuCode);

    /**
     * Find item by id + tenant_id (for tenant isolation).
     */
    @Select("SELECT * FROM stock_item WHERE id = #{id} " +
            "AND tenant_id = #{tenantId} AND deleted = false")
    StockItemDO selectByIdAndTenant(@Param("id") Long id,
                                     @Param("tenantId") Long tenantId);

    /**
     * List all items for a tenant.
     */
    @Select("SELECT * FROM stock_item WHERE tenant_id = #{tenantId} " +
            "AND deleted = false ORDER BY id")
    List<StockItemDO> listByTenant(@Param("tenantId") Long tenantId);

    /**
     * Update is_active status by id + tenant_id (tenant-isolated).
     */
    @Update("UPDATE stock_item SET is_active = #{isActive}, " +
            "updater = #{updater}, update_time = #{updateTime} " +
            "WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = false")
    int updateActiveByIdAndTenant(@Param("id") Long id,
                                   @Param("tenantId") Long tenantId,
                                   @Param("isActive") Boolean isActive,
                                   @Param("updater") String updater,
                                   @Param("updateTime") java.time.LocalDateTime updateTime);
}
