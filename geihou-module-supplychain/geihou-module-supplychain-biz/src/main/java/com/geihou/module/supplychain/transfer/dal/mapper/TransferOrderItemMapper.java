package com.geihou.module.supplychain.transfer.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.supplychain.transfer.dal.dataobject.TransferOrderItemDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Mapper for transfer_order_item table.
 *
 * <p>Source: TASK-G2-02S.
 */
public interface TransferOrderItemMapper extends BaseMapperX<TransferOrderItemDO> {

    /**
     * List items by transfer order id + tenant_id (tenant isolation).
     */
    @Select("SELECT * FROM transfer_order_item WHERE transfer_order_id = #{transferOrderId} " +
            "AND tenant_id = #{tenantId} AND deleted = false ORDER BY id ASC")
    List<TransferOrderItemDO> listByOrderAndTenant(@Param("transferOrderId") Long transferOrderId,
                                                    @Param("tenantId") Long tenantId);

    /**
     * Update out_event_id for a specific item (tenant isolation).
     */
    @Update("UPDATE transfer_order_item SET out_event_id = #{outEventId}, " +
            "updater = #{updater}, update_time = #{updateTime} " +
            "WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = false")
    int updateOutEventId(@Param("id") Long id,
                         @Param("tenantId") Long tenantId,
                         @Param("outEventId") Long outEventId,
                         @Param("updater") String updater,
                         @Param("updateTime") LocalDateTime updateTime);

    /**
     * Update in_event_id for a specific item (tenant isolation).
     */
    @Update("UPDATE transfer_order_item SET in_event_id = #{inEventId}, " +
            "updater = #{updater}, update_time = #{updateTime} " +
            "WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = false")
    int updateInEventId(@Param("id") Long id,
                        @Param("tenantId") Long tenantId,
                        @Param("inEventId") Long inEventId,
                        @Param("updater") String updater,
                        @Param("updateTime") LocalDateTime updateTime);
}
