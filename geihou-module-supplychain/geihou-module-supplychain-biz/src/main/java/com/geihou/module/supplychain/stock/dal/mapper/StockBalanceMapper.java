package com.geihou.module.supplychain.stock.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.supplychain.stock.dal.dataobject.StockBalanceDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Mapper for stock_balance table.
 *
 * <p>Balance is computed from events (不可裸改). No method to directly set
 * available_qty or total_qty. The only way to change balance is through
 * {@link #updateBalanceWithOptimisticLock}, which uses an optimistic-lock
 * version check.
 *
 * <p>AC-2: stock_balance 不可裸改 — no setAvailableQty/setTotalQty methods.
 * AC-8: optimistic lock version field effective.
 */
public interface StockBalanceMapper extends BaseMapperX<StockBalanceDO> {

    /**
     * Find balance by tenant + item + location (unique key).
     */
    @Select("SELECT * FROM stock_balance WHERE tenant_id = #{tenantId} " +
            "AND stock_item_id = #{stockItemId} AND location_id = #{locationId} " +
            "AND deleted = false")
    StockBalanceDO selectByTenantItemLocation(@Param("tenantId") Long tenantId,
                                                @Param("stockItemId") Long stockItemId,
                                                @Param("locationId") Long locationId);

    /**
     * Update balance with optimistic lock (version check).
     *
     * <p>This is the ONLY method that changes available_qty / total_qty.
     * Returns 0 if the version doesn't match (concurrent modification).
     *
     * @param id              balance record ID
     * @param tenantId        tenant ID (defensive)
     * @param newAvailableQty new available quantity
     * @param newTotalQty     new total quantity
     * @param eventId         last event ID
     * @param eventTime       last event time
     * @param currentVersion  current version (for optimistic lock check)
     * @param updater         updater
     * @param updateTime      update time
     * @return 1 if updated, 0 if version conflict
     */
    @Update("UPDATE stock_balance SET available_qty = #{newAvailableQty}, " +
            "total_qty = #{newTotalQty}, last_event_id = #{eventId}, " +
            "last_event_time = #{eventTime}, version = version + 1, " +
            "updater = #{updater}, update_time = #{updateTime} " +
            "WHERE id = #{id} AND tenant_id = #{tenantId} " +
            "AND version = #{currentVersion} AND deleted = false")
    int updateBalanceWithOptimisticLock(@Param("id") Long id,
                                         @Param("tenantId") Long tenantId,
                                         @Param("newAvailableQty") BigDecimal newAvailableQty,
                                         @Param("newTotalQty") BigDecimal newTotalQty,
                                         @Param("eventId") Long eventId,
                                         @Param("eventTime") LocalDateTime eventTime,
                                         @Param("currentVersion") Integer currentVersion,
                                         @Param("updater") String updater,
                                         @Param("updateTime") LocalDateTime updateTime);

    /**
     * Update only last_event_id on a balance record (metadata, does NOT change
     * available_qty / total_qty). Used after event insert to link the balance
     * record to the newly created event.
     *
     * <p>G2-01A FIX: since the event is now inserted AFTER the balance update,
     * last_event_id is initially null. This method sets it post-insert within
     * the same transaction.
     */
    @Update("UPDATE stock_balance SET last_event_id = #{eventId} " +
            "WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = false")
    int updateLastEventId(@Param("id") Long id,
                          @Param("tenantId") Long tenantId,
                          @Param("eventId") Long eventId);

    /**
     * Update balance with reserved_qty and optimistic lock (version check).
     *
     * <p>This method is used by reserve/release/commit operations to update
     * available_qty, total_qty, and reserved_qty atomically with version check.
     *
     * @param id              balance record ID
     * @param tenantId        tenant ID (defensive)
     * @param newAvailableQty new available quantity
     * @param newTotalQty     new total quantity
     * @param newReservedQty  new reserved quantity
     * @param eventId         last event ID (nullable for reserve/release which don't write events)
     * @param eventTime       last event time (nullable for reserve/release)
     * @param currentVersion  current version (for optimistic lock check)
     * @param updater         updater
     * @param updateTime      update time
     * @return 1 if updated, 0 if version conflict
     */
    @Update("UPDATE stock_balance SET available_qty = #{newAvailableQty}, " +
            "total_qty = #{newTotalQty}, reserved_qty = #{newReservedQty}, " +
            "last_event_id = COALESCE(#{eventId}, last_event_id), " +
            "last_event_time = COALESCE(#{eventTime}, last_event_time), " +
            "version = version + 1, " +
            "updater = #{updater}, update_time = #{updateTime} " +
            "WHERE id = #{id} AND tenant_id = #{tenantId} " +
            "AND version = #{currentVersion} AND deleted = false")
    int updateBalanceWithReserve(@Param("id") Long id,
                                  @Param("tenantId") Long tenantId,
                                  @Param("newAvailableQty") BigDecimal newAvailableQty,
                                  @Param("newTotalQty") BigDecimal newTotalQty,
                                  @Param("newReservedQty") BigDecimal newReservedQty,
                                  @Param("eventId") Long eventId,
                                  @Param("eventTime") LocalDateTime eventTime,
                                  @Param("currentVersion") Integer currentVersion,
                                  @Param("updater") String updater,
                                  @Param("updateTime") LocalDateTime updateTime);

    /**
     * Sum available_qty across all locations for a given tenant + stock item (read-only).
     *
     * <p>Used by StockCheckService for BOM-aware sufficiency preflight check (G2-02C).
     * Returns 0 if no balance records exist.
     *
     * @param tenantId    tenant ID
     * @param stockItemId stock item ID
     * @return total available quantity summed across all locations; never null
     */
    @Select("SELECT COALESCE(SUM(available_qty), 0) FROM stock_balance " +
            "WHERE tenant_id = #{tenantId} AND stock_item_id = #{stockItemId} " +
            "AND deleted = false")
    BigDecimal sumAvailableQtyByTenantItem(@Param("tenantId") Long tenantId,
                                            @Param("stockItemId") Long stockItemId);

    /**
     * Find all balance records for a tenant (G2-02J reconciliation — read-only).
     *
     * @param tenantId tenant ID (mandatory for tenant isolation)
     * @return list of non-deleted balance records for the tenant
     */
    @Select("SELECT * FROM stock_balance WHERE tenant_id = #{tenantId} " +
            "AND deleted = false ORDER BY stock_item_id ASC, location_id ASC")
    List<StockBalanceDO> selectAllByTenant(@Param("tenantId") Long tenantId);

    /**
     * Find all balance records for a tenant + stock item (G2-02J reconciliation — read-only).
     *
     * @param tenantId    tenant ID
     * @param stockItemId stock item ID
     * @return list of non-deleted balance records
     */
    @Select("SELECT * FROM stock_balance WHERE tenant_id = #{tenantId} " +
            "AND stock_item_id = #{stockItemId} AND deleted = false " +
            "ORDER BY location_id ASC")
    List<StockBalanceDO> selectByTenantItem(@Param("tenantId") Long tenantId,
                                             @Param("stockItemId") Long stockItemId);
}
