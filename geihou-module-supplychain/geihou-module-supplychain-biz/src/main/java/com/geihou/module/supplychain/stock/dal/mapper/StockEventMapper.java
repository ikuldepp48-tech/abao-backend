package com.geihou.module.supplychain.stock.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.framework.tenant.core.annotation.TenantIgnore;
import com.geihou.module.supplychain.stock.dal.dataobject.StockEventDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Mapper for stock_event table.
 *
 * <p>INSERT-only (不可篡改). This mapper declares ONLY insert and select methods.
 * No update or delete methods are declared on this interface.
 * The stock_event table is an immutable event ledger.
 *
 * <p>AC-1: stock_event INSERT-only — no update/delete methods.
 */
public interface StockEventMapper extends BaseMapperX<StockEventDO> {

    /**
     * Find event by tenant_id + client_request_id (for idempotency check).
     *
     * <p>G2-01A FIX: scope by tenant_id so that different tenants using the
     * same client_request_id do not cross-interfere.
     */
    @Select("SELECT * FROM stock_event WHERE tenant_id = #{tenantId} " +
            "AND client_request_id = #{clientRequestId}")
    StockEventDO selectByClientRequestId(@Param("tenantId") Long tenantId,
                                         @Param("clientRequestId") String clientRequestId);

    /**
     * Find events by tenant + item + location, ordered by event_time.
     */
    @Select("SELECT * FROM stock_event WHERE tenant_id = #{tenantId} " +
            "AND stock_item_id = #{stockItemId} AND location_id = #{locationId} " +
            "ORDER BY event_time ASC, id ASC")
    List<StockEventDO> selectByTenantItemLocation(@Param("tenantId") Long tenantId,
                                                    @Param("stockItemId") Long stockItemId,
                                                    @Param("locationId") Long locationId);

    /**
     * Find events by tenant + business_date + event_type.
     */
    @Select("SELECT * FROM stock_event WHERE tenant_id = #{tenantId} " +
            "AND business_date = #{businessDate} AND event_type = #{eventType} " +
            "ORDER BY event_time ASC")
    List<StockEventDO> selectByTenantDateType(@Param("tenantId") Long tenantId,
                                                @Param("businessDate") LocalDateTime businessDate,
                                                @Param("eventType") String eventType);

    /**
     * Find child events by parent event ID (for BOM reverse consumption tracing).
     * Tenant-scoped, read-only.
     */
    @Select("SELECT * FROM stock_event WHERE tenant_id = #{tenantId} " +
            "AND parent_event_id = #{parentEventId} " +
            "ORDER BY event_time ASC")
    List<StockEventDO> selectByParentEventId(@Param("tenantId") Long tenantId,
                                              @Param("parentEventId") Long parentEventId);

    /**
     * Find all events for a tenant, ordered by stock_item_id, location_id,
     * event_time, id (G2-02J reconciliation — read-only).
     *
     * @param tenantId tenant ID (mandatory for tenant isolation)
     * @return list of events ordered for deterministic replay
     */
    @Select("SELECT * FROM stock_event WHERE tenant_id = #{tenantId} " +
            "ORDER BY stock_item_id ASC, location_id ASC, event_time ASC, id ASC")
    List<StockEventDO> selectAllByTenant(@Param("tenantId") Long tenantId);

    /**
     * Find all events for a tenant + stock item, ordered by location_id,
     * event_time, id (G2-02J reconciliation — read-only).
     *
     * @param tenantId    tenant ID
     * @param stockItemId stock item ID
     * @return list of events ordered for deterministic replay
     */
    @Select("SELECT * FROM stock_event WHERE tenant_id = #{tenantId} " +
            "AND stock_item_id = #{stockItemId} " +
            "ORDER BY location_id ASC, event_time ASC, id ASC")
    List<StockEventDO> selectByTenantItem(@Param("tenantId") Long tenantId,
                                           @Param("stockItemId") Long stockItemId);

    /**
     * Query all distinct tenant_id values that have stock_event data (G2-02J-2).
     *
     * <p>Used by {@code BalanceReconcileJob} to enumerate tenants for batch
     * reconciliation. Annotated with {@link TenantIgnore} to bypass the
     * tenant-line interceptor, since this query intentionally spans all tenants.
     *
     * <p>Read-only: no side effects.
     *
     * @return distinct tenant_id list
     */
    @TenantIgnore
    @Select("SELECT DISTINCT tenant_id FROM stock_event")
    List<Long> selectDistinctTenantIds();
}
