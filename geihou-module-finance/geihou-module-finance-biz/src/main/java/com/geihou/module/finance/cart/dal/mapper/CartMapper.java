package com.geihou.module.finance.cart.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.finance.cart.dal.dataobject.CartDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * Mapper for cart table.
 *
 * <p>Supports soft delete via @TableLogic on CartDO.
 * No hard delete methods; deleteById performs soft delete (UPDATE deleted=1).
 * Core summary fields updated through service layer via updateById with optimistic lock.
 *
 * <p>{@link #unlockFromCheckout} is a conditional UPDATE (CHECKOUT -> ACTIVE)
 * that also bumps the optimistic-lock version; the finalizer uses it as part
 * of the same-transaction terminalization (G0-04H185 FIN-CONSISTENCY
 * slice 2C-2D).
 */
public interface CartMapper extends BaseMapperX<CartDO> {

    /**
     * Unlock a cart that is currently in CHECKOUT status, bumping the
     * optimistic-lock version. Returns the number of affected rows (0 or 1).
     * Never unlocks a cart that is not CHECKOUT.
     */
    @Update("UPDATE cart SET " +
            "status = 'ACTIVE', " +
            "last_activity_time = #{now}, " +
            "updater = #{updater}, " +
            "update_time = #{now}, " +
            "version = version + 1 " +
            "WHERE tenant_id = #{tenantId} " +
            "AND id = #{cartId} " +
            "AND status = 'CHECKOUT' " +
            "AND deleted = false")
    int unlockFromCheckout(
            @Param("tenantId") long tenantId,
            @Param("cartId") long cartId,
            @Param("updater") String updater,
            @Param("now") LocalDateTime now);
}
