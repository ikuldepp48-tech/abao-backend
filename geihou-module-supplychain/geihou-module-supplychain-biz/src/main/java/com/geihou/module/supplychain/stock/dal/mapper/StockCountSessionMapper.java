package com.geihou.module.supplychain.stock.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.supplychain.stock.dal.dataobject.StockCountSessionDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * Mapper for stock_count_session table.
 *
 * <p>Provides CRUD via MyBatis-Plus BaseMapper plus tenant-scoped queries.
 *
 * <p>Source: TASK-G2-02I-2.
 */
public interface StockCountSessionMapper extends BaseMapperX<StockCountSessionDO> {

    /**
     * Find session by tenant_id + session_code (unique key).
     */
    @Select("SELECT * FROM stock_count_session WHERE tenant_id = #{tenantId} " +
            "AND session_code = #{sessionCode} AND deleted = false")
    StockCountSessionDO selectByTenantAndCode(@Param("tenantId") Long tenantId,
                                               @Param("sessionCode") String sessionCode);

    /**
     * Find session by id + tenant_id (tenant isolation).
     */
    @Select("SELECT * FROM stock_count_session WHERE id = #{id} " +
            "AND tenant_id = #{tenantId} AND deleted = false")
    StockCountSessionDO selectByIdAndTenant(@Param("id") Long id,
                                             @Param("tenantId") Long tenantId);

    /**
     * Update status by id + tenant_id with optimistic check on expected status.
     * Also updates approver, approve_time, end_time, summary fields, updater, update_time.
     */
    @Update("UPDATE stock_count_session SET status = #{status}, " +
            "approver_user_id = #{approverUserId}, " +
            "approve_time = #{approveTime}, " +
            "end_time = #{endTime}, " +
            "total_items = #{totalItems}, " +
            "diff_items = #{diffItems}, " +
            "total_diff_value = #{totalDiffValue}, " +
            "updater = #{updater}, update_time = #{updateTime} " +
            "WHERE id = #{id} AND tenant_id = #{tenantId} " +
            "AND status = #{expectStatus} AND deleted = false")
    int updateStatusByTenant(@Param("id") Long id,
                              @Param("tenantId") Long tenantId,
                              @Param("status") String status,
                              @Param("approverUserId") Long approverUserId,
                              @Param("approveTime") LocalDateTime approveTime,
                              @Param("endTime") LocalDateTime endTime,
                              @Param("totalItems") Integer totalItems,
                              @Param("diffItems") Integer diffItems,
                              @Param("totalDiffValue") java.math.BigDecimal totalDiffValue,
                              @Param("expectStatus") String expectStatus,
                              @Param("updater") String updater,
                              @Param("updateTime") LocalDateTime updateTime);

    /**
     * Update status + start_time by id + tenant_id (for startCount: PLANNING → IN_PROGRESS).
     */
    @Update("UPDATE stock_count_session SET status = #{status}, " +
            "start_time = #{startTime}, " +
            "operator_user_id = #{operatorUserId}, " +
            "updater = #{updater}, update_time = #{updateTime} " +
            "WHERE id = #{id} AND tenant_id = #{tenantId} " +
            "AND status = #{expectStatus} AND deleted = false")
    int updateStartByTenant(@Param("id") Long id,
                             @Param("tenantId") Long tenantId,
                             @Param("status") String status,
                             @Param("startTime") LocalDateTime startTime,
                             @Param("operatorUserId") Long operatorUserId,
                             @Param("expectStatus") String expectStatus,
                             @Param("updater") String updater,
                             @Param("updateTime") LocalDateTime updateTime);
}
