package com.geihou.module.supplychain.transfer.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.supplychain.transfer.dal.dataobject.TransferOrderDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Mapper for transfer_order table.
 *
 * <p>Provides CRUD via MyBatis-Plus BaseMapper plus tenant-scoped queries.
 *
 * <p>Source: TASK-G2-02S.
 */
public interface TransferOrderMapper extends BaseMapperX<TransferOrderDO> {

    /**
     * Find transfer order by tenant_id + transfer_no (unique key).
     */
    @Select("SELECT * FROM transfer_order WHERE tenant_id = #{tenantId} " +
            "AND transfer_no = #{transferNo} AND deleted = false")
    TransferOrderDO selectByTenantAndTransferNo(@Param("tenantId") Long tenantId,
                                                 @Param("transferNo") String transferNo);

    /**
     * Find transfer order by id + tenant_id (tenant isolation).
     */
    @Select("SELECT * FROM transfer_order WHERE id = #{id} " +
            "AND tenant_id = #{tenantId} AND deleted = false")
    TransferOrderDO selectByIdAndTenant(@Param("id") Long id,
                                         @Param("tenantId") Long tenantId);

    /**
     * Update status by id + tenant_id with optimistic check on expectStatus.
     * Also updates shipped_by/shipped_at/received_by/received_at/cancelled_by/cancelled_at/cancel_reason.
     */
    @Update("UPDATE transfer_order SET status = #{newStatus}, " +
            "shipped_by = #{shippedBy}, shipped_at = #{shippedAt}, " +
            "received_by = #{receivedBy}, received_at = #{receivedAt}, " +
            "cancelled_by = #{cancelledBy}, cancelled_at = #{cancelledAt}, " +
            "cancel_reason = #{cancelReason}, " +
            "updater = #{updater}, update_time = #{updateTime} " +
            "WHERE id = #{id} AND tenant_id = #{tenantId} " +
            "AND status = #{expectStatus} AND deleted = false")
    int updateStatusByTenant(@Param("id") Long id,
                              @Param("tenantId") Long tenantId,
                              @Param("newStatus") String newStatus,
                              @Param("expectStatus") String expectStatus,
                              @Param("shippedBy") Long shippedBy,
                              @Param("shippedAt") LocalDateTime shippedAt,
                              @Param("receivedBy") Long receivedBy,
                              @Param("receivedAt") LocalDateTime receivedAt,
                              @Param("cancelledBy") Long cancelledBy,
                              @Param("cancelledAt") LocalDateTime cancelledAt,
                              @Param("cancelReason") String cancelReason,
                              @Param("updater") String updater,
                              @Param("updateTime") LocalDateTime updateTime);

    /**
     * List transfer orders by tenant with optional status filter (tenant isolation).
     */
    @Select("SELECT * FROM transfer_order WHERE tenant_id = #{tenantId} " +
            "AND deleted = false ORDER BY id DESC")
    List<TransferOrderDO> listByTenant(@Param("tenantId") Long tenantId);

    /**
     * List transfer orders by tenant + status (tenant isolation).
     */
    @Select("SELECT * FROM transfer_order WHERE tenant_id = #{tenantId} " +
            "AND status = #{status} AND deleted = false ORDER BY id DESC")
    List<TransferOrderDO> listByTenantAndStatus(@Param("tenantId") Long tenantId,
                                                  @Param("status") String status);
}
