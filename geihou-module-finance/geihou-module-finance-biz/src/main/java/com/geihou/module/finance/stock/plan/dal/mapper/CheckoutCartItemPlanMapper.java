package com.geihou.module.finance.stock.plan.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.finance.stock.plan.dal.dataobject.CheckoutCartItemPlanDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Mapper for {@code checkout_cart_item_plan} table.
 *
 * <p>All select statements include {@code tenant_id} - no cross-tenant
 * access. Writes are single-statement: insert with the unique-key
 * constraint {@code uk_ccip_tenant_session_cart} as the race
 * synchronization point; no select-then-unconditional-insert.
 *
 * <p>No update methods: the plan is write-once. There is no
 * {@code update*} method in this mapper.
 */
public interface CheckoutCartItemPlanMapper extends BaseMapperX<CheckoutCartItemPlanDO> {

    @Select("SELECT * FROM checkout_cart_item_plan " +
            "WHERE tenant_id = #{tenantId} " +
            "AND checkout_session_id = #{checkoutSessionId} " +
            "AND cart_item_id = #{cartItemId}")
    CheckoutCartItemPlanDO selectBySessionAndCartItem(
            @Param("tenantId") long tenantId,
            @Param("checkoutSessionId") long checkoutSessionId,
            @Param("cartItemId") long cartItemId);

    @Select("SELECT * FROM checkout_cart_item_plan " +
            "WHERE tenant_id = #{tenantId} " +
            "AND checkout_session_id = #{checkoutSessionId} " +
            "ORDER BY cart_item_id ASC, id ASC")
    List<CheckoutCartItemPlanDO> listBySession(
            @Param("tenantId") long tenantId,
            @Param("checkoutSessionId") long checkoutSessionId);
}
