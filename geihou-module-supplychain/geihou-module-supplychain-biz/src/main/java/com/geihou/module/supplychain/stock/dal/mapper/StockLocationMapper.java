package com.geihou.module.supplychain.stock.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.supplychain.stock.dal.dataobject.StockLocationDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Mapper for stock_location table.
 *
 * <p>Stock location master data CRUD and mapping governance.
 */
public interface StockLocationMapper extends BaseMapperX<StockLocationDO> {

    /**
     * Find location by tenant_id + location_code (unique key).
     */
    @Select("SELECT * FROM stock_location WHERE tenant_id = #{tenantId} " +
            "AND location_code = #{locationCode} AND deleted = false")
    StockLocationDO selectByTenantCode(@Param("tenantId") Long tenantId,
                                        @Param("locationCode") String locationCode);

    /**
     * Find location by tenant_id + store_id + location_type (mapping unique key).
     */
    @Select("SELECT * FROM stock_location WHERE tenant_id = #{tenantId} " +
            "AND store_id = #{storeId} AND location_type = #{locationType} " +
            "AND deleted = false")
    StockLocationDO selectByTenantStoreIdType(@Param("tenantId") Long tenantId,
                                               @Param("storeId") Long storeId,
                                               @Param("locationType") String locationType);

    /**
     * Find active location by tenant_id + store_id + location_type (for StockQueryApi).
     */
    @Select("SELECT * FROM stock_location WHERE tenant_id = #{tenantId} " +
            "AND store_id = #{storeId} AND location_type = #{locationType} " +
            "AND is_active = true AND deleted = false")
    StockLocationDO selectActiveByTenantStoreIdType(@Param("tenantId") Long tenantId,
                                                     @Param("storeId") Long storeId,
                                                     @Param("locationType") String locationType);

    /**
     * Find location by id + tenant_id (for tenant isolation).
     */
    @Select("SELECT * FROM stock_location WHERE id = #{id} " +
            "AND tenant_id = #{tenantId} AND deleted = false")
    StockLocationDO selectByIdAndTenant(@Param("id") Long id,
                                         @Param("tenantId") Long tenantId);

    /**
     * List all locations for a tenant.
     */
    @Select("SELECT * FROM stock_location WHERE tenant_id = #{tenantId} " +
            "AND deleted = false ORDER BY id")
    List<StockLocationDO> listByTenant(@Param("tenantId") Long tenantId);

    /**
     * Update is_active status by id + tenant_id (tenant-isolated).
     */
    @Update("UPDATE stock_location SET is_active = #{isActive}, " +
            "updater = #{updater}, update_time = #{updateTime} " +
            "WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = false")
    int updateActiveByIdAndTenant(@Param("id") Long id,
                                   @Param("tenantId") Long tenantId,
                                   @Param("isActive") Boolean isActive,
                                   @Param("updater") String updater,
                                   @Param("updateTime") LocalDateTime updateTime);
}
