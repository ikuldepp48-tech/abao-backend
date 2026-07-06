package com.geihou.module.supplychain.stock.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.supplychain.stock.dal.dataobject.StockReserveDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Mapper for stock_reserve table.
 *
 * <p>Handles reservation records with idempotency via (tenant_id, idempotent_key) unique key.
 * status is a table-internal status (RESERVED/RELEASED/COMMITTED).
 */
public interface StockReserveMapper extends BaseMapperX<StockReserveDO> {

    /**
     * Find reserve by tenant_id + idempotent_key (for idempotency check).
     */
    @Select("SELECT * FROM stock_reserve WHERE tenant_id = #{tenantId} " +
            "AND idempotent_key = #{idempotentKey} AND deleted = false")
    StockReserveDO selectByTenantIdempotentKey(@Param("tenantId") Long tenantId,
                                                @Param("idempotentKey") String idempotentKey);

    /**
     * Find reserve by id + tenant_id (for tenant isolation).
     */
    @Select("SELECT * FROM stock_reserve WHERE id = #{id} " +
            "AND tenant_id = #{tenantId} AND deleted = false")
    StockReserveDO selectByIdAndTenant(@Param("id") Long id,
                                        @Param("tenantId") Long tenantId);

    /**
     * Update reserve status (for release/commit).
     */
    @Update("UPDATE stock_reserve SET status = #{status}, " +
            "commit_event_id = COALESCE(#{commitEventId}, commit_event_id), " +
            "updater = #{updater}, update_time = #{updateTime} " +
            "WHERE id = #{id} AND tenant_id = #{tenantId} " +
            "AND status = #{expectedStatus} AND deleted = false")
    int updateStatus(@Param("id") Long id,
                     @Param("tenantId") Long tenantId,
                     @Param("status") String status,
                     @Param("expectedStatus") String expectedStatus,
                     @Param("commitEventId") Long commitEventId,
                     @Param("updater") String updater,
                     @Param("updateTime") LocalDateTime updateTime);

    /**
     * Scan timed-out RESERVED records for compensation.
     *
     * <p>Selects records where status = RESERVED, deleted = false, and create_time &lt; cutoff.
     * Results are ordered by create_time ASC and limited by batchSize.
     *
     * <p>G2-01B4: Compensation scan query — only reads, never updates directly.
     *
     * @param tenantId  tenant ID (optional, null for all tenants)
     * @param cutoff    create_time must be before this timestamp
     * @param batchSize max records to return
     * @return list of timed-out RESERVED records
     */
    @Select("SELECT * FROM stock_reserve WHERE status = 'RESERVED' " +
            "AND deleted = false " +
            "AND create_time < #{cutoff} " +
            "AND (#{tenantId} IS NULL OR tenant_id = #{tenantId}) " +
            "ORDER BY create_time ASC " +
            "LIMIT #{batchSize}")
    List<StockReserveDO> selectTimeoutReserved(@Param("tenantId") Long tenantId,
                                                @Param("cutoff") LocalDateTime cutoff,
                                                @Param("batchSize") int batchSize);
}
