package com.geihou.module.finance.order.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.finance.order.dal.dataobject.OrderDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Mapper for orders table.
 *
 * <p>Supports soft delete via @TableLogic on OrderDO.
 * No hard delete methods; deleteById performs soft delete (UPDATE deleted=1).
 * Core fields (order_no, total_amount, business_date) have no dedicated update methods.
 *
 * <p>G1-01G: Appended 3 SQL aggregation methods for business daily summary.
 * Uses @Select with manual tenant_id condition (defensive: interceptor may also add it).
 * Does not load full orders into memory — SQL COUNT/SUM aggregation.
 */
public interface OrderMapper extends BaseMapperX<OrderDO> {

    /**
     * Aggregate daily summary for a single business date.
     * Returns a single row with COUNT and SUM values.
     * Returns zero-values (via COALESCE) when no orders match.
     *
     * @param tenantId     tenant ID (from TenantContextHolder)
     * @param businessDate business date
     * @param shopId       optional shop filter (null = all shops)
     * @return Map with keys: order_count, total_amount, paid_amount, discount_amount, refund_amount, platform_fee
     */
    @Select("<script>"
            + "SELECT COUNT(*) AS order_count, "
            + "COALESCE(SUM(total_amount), 0) AS total_amount, "
            + "COALESCE(SUM(paid_amount), 0) AS paid_amount, "
            + "COALESCE(SUM(discount_amount), 0) AS discount_amount, "
            + "COALESCE(SUM(refund_amount), 0) AS refund_amount, "
            + "COALESCE(SUM(platform_fee), 0) AS platform_fee "
            + "FROM orders "
            + "WHERE tenant_id = #{tenantId} "
            + "AND business_date = #{businessDate} "
            + "AND deleted = false "
            + "<if test='shopId != null'>AND shop_id = #{shopId} </if>"
            + "</script>")
    Map<String, Object> selectDailySummary(@Param("tenantId") Long tenantId,
                                           @Param("businessDate") LocalDate businessDate,
                                           @Param("shopId") Long shopId);

    /**
     * Aggregate daily summary grouped by channel.
     * Returns one row per channel that has orders on the given business date.
     *
     * @param tenantId     tenant ID
     * @param businessDate business date
     * @param shopId       optional shop filter
     * @return List of Maps with keys: channel, order_count, total_amount, paid_amount, discount_amount, refund_amount, platform_fee
     */
    @Select("<script>"
            + "SELECT channel, "
            + "COUNT(*) AS order_count, "
            + "COALESCE(SUM(total_amount), 0) AS total_amount, "
            + "COALESCE(SUM(paid_amount), 0) AS paid_amount, "
            + "COALESCE(SUM(discount_amount), 0) AS discount_amount, "
            + "COALESCE(SUM(refund_amount), 0) AS refund_amount, "
            + "COALESCE(SUM(platform_fee), 0) AS platform_fee "
            + "FROM orders "
            + "WHERE tenant_id = #{tenantId} "
            + "AND business_date = #{businessDate} "
            + "AND deleted = false "
            + "<if test='shopId != null'>AND shop_id = #{shopId} </if>"
            + "GROUP BY channel"
            + "</script>")
    List<Map<String, Object>> selectDailySummaryByChannel(@Param("tenantId") Long tenantId,
                                                           @Param("businessDate") LocalDate businessDate,
                                                           @Param("shopId") Long shopId);

    /**
     * Aggregate daily summary grouped by status.
     * Returns one row per status that has orders on the given business date.
     *
     * @param tenantId     tenant ID
     * @param businessDate business date
     * @param shopId       optional shop filter
     * @return List of Maps with keys: status, order_count, total_amount, paid_amount, refund_amount
     */
    @Select("<script>"
            + "SELECT status, "
            + "COUNT(*) AS order_count, "
            + "COALESCE(SUM(total_amount), 0) AS total_amount, "
            + "COALESCE(SUM(paid_amount), 0) AS paid_amount, "
            + "COALESCE(SUM(refund_amount), 0) AS refund_amount "
            + "FROM orders "
            + "WHERE tenant_id = #{tenantId} "
            + "AND business_date = #{businessDate} "
            + "AND deleted = false "
            + "<if test='shopId != null'>AND shop_id = #{shopId} </if>"
            + "GROUP BY status"
            + "</script>")
    List<Map<String, Object>> selectDailySummaryByStatus(@Param("tenantId") Long tenantId,
                                                          @Param("businessDate") LocalDate businessDate,
                                                          @Param("shopId") Long shopId);
}
