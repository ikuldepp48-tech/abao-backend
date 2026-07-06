package com.geihou.module.supplychain.production.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.supplychain.production.dal.dataobject.ProductionOrderConsumptionDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Mapper for production_order_consumption table.
 *
 * <p>INSERT-only: only insert and select methods. No update / delete operations.
 *
 * <p>Source: TASK-G2-02N.
 */
public interface ProductionOrderConsumptionMapper extends BaseMapperX<ProductionOrderConsumptionDO> {

    /**
     * List consumption records by production order (tenant-isolated).
     */
    @Select("SELECT * FROM production_order_consumption WHERE tenant_id = #{tenantId} " +
            "AND production_order_id = #{productionOrderId} AND deleted = false ORDER BY pick_seq ASC")
    List<ProductionOrderConsumptionDO> listByOrder(@Param("tenantId") Long tenantId,
                                                    @Param("productionOrderId") Long productionOrderId);

    /**
     * Check if any consumption records exist for the given order (tenant-isolated).
     */
    @Select("SELECT COUNT(*) FROM production_order_consumption WHERE tenant_id = #{tenantId} " +
            "AND production_order_id = #{productionOrderId} AND deleted = false")
    int countByOrder(@Param("tenantId") Long tenantId,
                     @Param("productionOrderId") Long productionOrderId);
}
