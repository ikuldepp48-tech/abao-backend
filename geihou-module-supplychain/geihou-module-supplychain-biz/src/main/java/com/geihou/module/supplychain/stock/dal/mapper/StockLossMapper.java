package com.geihou.module.supplychain.stock.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.supplychain.stock.dal.dataobject.StockLossDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Mapper for stock_loss table.
 *
 * <p>Provides CRUD via MyBatis-Plus BaseMapper plus tenant-scoped queries.
 *
 * <p>Source: TASK-G2-02I-3.
 */
public interface StockLossMapper extends BaseMapperX<StockLossDO> {

    /**
     * Find loss record by tenant_id + loss_no (unique key).
     */
    @Select("SELECT * FROM stock_loss WHERE tenant_id = #{tenantId} " +
            "AND loss_no = #{lossNo} AND deleted = false")
    StockLossDO selectByTenantAndLossNo(@Param("tenantId") Long tenantId,
                                         @Param("lossNo") String lossNo);

    /**
     * Find loss record by id + tenant_id (tenant isolation).
     */
    @Select("SELECT * FROM stock_loss WHERE id = #{id} " +
            "AND tenant_id = #{tenantId} AND deleted = false")
    StockLossDO selectByIdAndTenant(@Param("id") Long id,
                                     @Param("tenantId") Long tenantId);

    /**
     * Update status by id + tenant_id (tenant-isolated).
     * Also updates approver, approve_time, reject_reason, stock_event_id, updater, update_time.
     */
    @Update("UPDATE stock_loss SET status = #{status}, " +
            "approver_user_id = #{approverUserId}, " +
            "approve_time = #{approveTime}, " +
            "reject_reason = #{rejectReason}, " +
            "stock_event_id = #{stockEventId}, " +
            "updater = #{updater}, update_time = #{updateTime} " +
            "WHERE id = #{id} AND tenant_id = #{tenantId} " +
            "AND status = #{expectStatus} AND deleted = false")
    int updateStatusByTenant(@Param("id") Long id,
                              @Param("tenantId") Long tenantId,
                              @Param("status") String status,
                              @Param("approverUserId") Long approverUserId,
                              @Param("approveTime") LocalDateTime approveTime,
                              @Param("rejectReason") String rejectReason,
                              @Param("stockEventId") Long stockEventId,
                              @Param("expectStatus") String expectStatus,
                              @Param("updater") String updater,
                              @Param("updateTime") LocalDateTime updateTime);

    /**
     * List loss records by tenant with optional status filter (tenant isolation).
     */
    @Select("SELECT * FROM stock_loss WHERE tenant_id = #{tenantId} " +
            "AND deleted = false ORDER BY id DESC")
    List<StockLossDO> listByTenant(@Param("tenantId") Long tenantId);

    /**
     * List loss records by tenant + status (tenant isolation).
     */
    @Select("SELECT * FROM stock_loss WHERE tenant_id = #{tenantId} " +
            "AND status = #{status} AND deleted = false ORDER BY id DESC")
    List<StockLossDO> listByTenantAndStatus(@Param("tenantId") Long tenantId,
                                              @Param("status") String status);
}
