package com.geihou.module.supplychain.stock.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.supplychain.stock.dal.dataobject.StockCountRecordDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Mapper for stock_count_record table.
 *
 * <p>INSERT-only (不可篡改). This mapper declares ONLY insert and select methods.
 * No update or delete methods are declared on this interface.
 * The stock_count_record table is an immutable audit trail of count results.
 *
 * <p>AC: stock_count_record INSERT-only — no update/delete methods.
 * adjustment_event_id is NOT backfilled (no UPDATE operations).
 *
 * <p>Source: TASK-G2-02I-2.
 */
public interface StockCountRecordMapper extends BaseMapperX<StockCountRecordDO> {

    /**
     * Find record by id (tenant isolation via tenant_id).
     */
    @Select("SELECT * FROM stock_count_record WHERE id = #{id} " +
            "AND tenant_id = #{tenantId}")
    StockCountRecordDO selectByIdAndTenant(@Param("id") Long id,
                                            @Param("tenantId") Long tenantId);

    /**
     * List all records for a session (tenant isolation).
     */
    @Select("SELECT * FROM stock_count_record WHERE session_id = #{sessionId} " +
            "AND tenant_id = #{tenantId} ORDER BY id ASC")
    List<StockCountRecordDO> selectBySession(@Param("sessionId") Long sessionId,
                                              @Param("tenantId") Long tenantId);

    /**
     * Find record by session_id + stock_item_id (tenant isolation).
     * Used to check if an item has already been recorded.
     */
    @Select("SELECT * FROM stock_count_record WHERE session_id = #{sessionId} " +
            "AND stock_item_id = #{stockItemId} AND tenant_id = #{tenantId}")
    StockCountRecordDO selectBySessionAndItem(@Param("sessionId") Long sessionId,
                                               @Param("stockItemId") Long stockItemId,
                                               @Param("tenantId") Long tenantId);

    /**
     * List all records with diff_qty != 0 for a session (tenant isolation).
     * Used by approveCount to generate COUNT_ADJUST events.
     */
    @Select("SELECT * FROM stock_count_record WHERE session_id = #{sessionId} " +
            "AND tenant_id = #{tenantId} AND diff_qty != 0 ORDER BY id ASC")
    List<StockCountRecordDO> selectDiffBySession(@Param("sessionId") Long sessionId,
                                                  @Param("tenantId") Long tenantId);

    /**
     * Count records for a session (tenant isolation).
     */
    @Select("SELECT COUNT(*) FROM stock_count_record WHERE session_id = #{sessionId} " +
            "AND tenant_id = #{tenantId}")
    long countBySession(@Param("sessionId") Long sessionId,
                        @Param("tenantId") Long tenantId);
}
