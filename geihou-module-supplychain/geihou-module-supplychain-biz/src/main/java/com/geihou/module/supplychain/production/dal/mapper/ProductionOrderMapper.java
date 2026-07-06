package com.geihou.module.supplychain.production.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.supplychain.production.dal.dataobject.ProductionOrderDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Mapper for production_order table.
 *
 * <p>Provides CRUD via MyBatis-Plus BaseMapper plus tenant-scoped queries.
 *
 * <p>Source: TASK-G2-02K.
 */
public interface ProductionOrderMapper extends BaseMapperX<ProductionOrderDO> {

    /**
     * Find production order by tenant_id + order_no (unique key).
     */
    @Select("SELECT * FROM production_order WHERE tenant_id = #{tenantId} " +
            "AND order_no = #{orderNo} AND deleted = false")
    ProductionOrderDO selectByTenantAndOrderNo(@Param("tenantId") Long tenantId,
                                                @Param("orderNo") String orderNo);

    /**
     * Find production order by id + tenant_id (tenant isolation).
     */
    @Select("SELECT * FROM production_order WHERE id = #{id} " +
            "AND tenant_id = #{tenantId} AND deleted = false")
    ProductionOrderDO selectByIdAndTenant(@Param("id") Long id,
                                           @Param("tenantId") Long tenantId);

    /**
     * Update production_stage by id + tenant_id with optimistic check on expectStage.
     * Also updates actual_start_time / actual_end_time / actual_qty / operator_user_id / remark / updater / update_time.
     */
    @Update("UPDATE production_order SET production_stage = #{newStage}, " +
            "actual_start_time = #{actualStartTime}, " +
            "actual_end_time = #{actualEndTime}, " +
            "actual_qty = #{actualQty}, " +
            "operator_user_id = #{operatorUserId}, " +
            "remark = #{remark}, " +
            "updater = #{updater}, update_time = #{updateTime} " +
            "WHERE id = #{id} AND tenant_id = #{tenantId} " +
            "AND production_stage = #{expectStage} AND deleted = false")
    int updateStageByTenant(@Param("id") Long id,
                             @Param("tenantId") Long tenantId,
                             @Param("newStage") String newStage,
                             @Param("expectStage") String expectStage,
                             @Param("actualStartTime") LocalDateTime actualStartTime,
                             @Param("actualEndTime") LocalDateTime actualEndTime,
                             @Param("actualQty") BigDecimal actualQty,
                             @Param("operatorUserId") Long operatorUserId,
                             @Param("remark") String remark,
                             @Param("updater") String updater,
                             @Param("updateTime") LocalDateTime updateTime);

    /**
     * G2-02M: Update stage to COMPLETED from QUALITY_CHECK, also setting quality check fields.
     * Optimistic lock on expectStage = 'QUALITY_CHECK'.
     */
    @Update("UPDATE production_order SET production_stage = #{newStage}, " +
            "actual_end_time = #{actualEndTime}, " +
            "actual_qty = #{actualQty}, " +
            "operator_user_id = #{operatorUserId}, " +
            "remark = #{remark}, " +
            "quality_check_result = #{qualityCheckResult}, " +
            "quality_checked_by = #{qualityCheckedBy}, " +
            "quality_checked_time = #{qualityCheckedTime}, " +
            "updater = #{updater}, update_time = #{updateTime} " +
            "WHERE id = #{id} AND tenant_id = #{tenantId} " +
            "AND production_stage = #{expectStage} AND deleted = false")
    int updateStageWithQualityPassByTenant(@Param("id") Long id,
                                            @Param("tenantId") Long tenantId,
                                            @Param("newStage") String newStage,
                                            @Param("expectStage") String expectStage,
                                            @Param("actualEndTime") LocalDateTime actualEndTime,
                                            @Param("actualQty") BigDecimal actualQty,
                                            @Param("operatorUserId") Long operatorUserId,
                                            @Param("remark") String remark,
                                            @Param("qualityCheckResult") String qualityCheckResult,
                                            @Param("qualityCheckedBy") Long qualityCheckedBy,
                                            @Param("qualityCheckedTime") LocalDateTime qualityCheckedTime,
                                            @Param("updater") String updater,
                                            @Param("updateTime") LocalDateTime updateTime);

    /**
     * G2-02M: Update stage to REWORK from QUALITY_CHECK, setting quality check fields
     * and incrementing rework_count in SQL (avoid read-modify-write).
     * Optimistic lock on expectStage = 'QUALITY_CHECK'.
     */
    @Update("UPDATE production_order SET production_stage = #{newStage}, " +
            "quality_check_result = #{qualityCheckResult}, " +
            "quality_check_remark = #{qualityCheckRemark}, " +
            "quality_checked_by = #{qualityCheckedBy}, " +
            "quality_checked_time = #{qualityCheckedTime}, " +
            "rework_count = rework_count + 1, " +
            "operator_user_id = #{operatorUserId}, " +
            "remark = #{remark}, " +
            "updater = #{updater}, update_time = #{updateTime} " +
            "WHERE id = #{id} AND tenant_id = #{tenantId} " +
            "AND production_stage = #{expectStage} AND deleted = false")
    int updateStageToReworkByTenant(@Param("id") Long id,
                                     @Param("tenantId") Long tenantId,
                                     @Param("newStage") String newStage,
                                     @Param("expectStage") String expectStage,
                                     @Param("qualityCheckResult") String qualityCheckResult,
                                     @Param("qualityCheckRemark") String qualityCheckRemark,
                                     @Param("qualityCheckedBy") Long qualityCheckedBy,
                                     @Param("qualityCheckedTime") LocalDateTime qualityCheckedTime,
                                     @Param("operatorUserId") Long operatorUserId,
                                     @Param("remark") String remark,
                                     @Param("updater") String updater,
                                     @Param("updateTime") LocalDateTime updateTime);

    /**
     * Update mutable fields by id + tenant_id (tenant-isolated).
     * Used for updateProductionOrder — only allowed in CREATED stage.
     */
    @Update("UPDATE production_order SET product_id = #{productId}, " +
            "recipe_id = #{recipeId}, " +
            "location_id = #{locationId}, " +
            "planned_qty = #{plannedQty}, " +
            "plan_start_time = #{planStartTime}, " +
            "plan_end_time = #{planEndTime}, " +
            "operator_user_id = #{operatorUserId}, " +
            "remark = #{remark}, " +
            "updater = #{updater}, update_time = #{updateTime} " +
            "WHERE id = #{id} AND tenant_id = #{tenantId} " +
            "AND production_stage = 'CREATED' AND deleted = false")
    int updateMutableByTenant(@Param("id") Long id,
                               @Param("tenantId") Long tenantId,
                               @Param("productId") Long productId,
                               @Param("recipeId") Long recipeId,
                               @Param("locationId") Long locationId,
                               @Param("plannedQty") BigDecimal plannedQty,
                               @Param("planStartTime") LocalDateTime planStartTime,
                               @Param("planEndTime") LocalDateTime planEndTime,
                               @Param("operatorUserId") Long operatorUserId,
                               @Param("remark") String remark,
                               @Param("updater") String updater,
                               @Param("updateTime") LocalDateTime updateTime);

    /**
     * List production orders by tenant with optional stage filter (tenant isolation).
     */
    @Select("SELECT * FROM production_order WHERE tenant_id = #{tenantId} " +
            "AND deleted = false ORDER BY id DESC")
    List<ProductionOrderDO> listByTenant(@Param("tenantId") Long tenantId);

    /**
     * List production orders by tenant + production_stage (tenant isolation).
     */
    @Select("SELECT * FROM production_order WHERE tenant_id = #{tenantId} " +
            "AND production_stage = #{productionStage} AND deleted = false ORDER BY id DESC")
    List<ProductionOrderDO> listByTenantAndStage(@Param("tenantId") Long tenantId,
                                                   @Param("productionStage") String productionStage);
}
