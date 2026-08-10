package com.geihou.module.finance.checkout.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.finance.checkout.dal.dataobject.CheckoutSessionDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * Mapper for checkout_session table.
 *
 * <p>Supports soft delete via @TableLogic on CheckoutSessionDO.
 * Core status updates through service layer via updateById.
 *
 * <p>{@link #selectByIdTenant} and {@link #casTerminalStatus} are the
 * tenant-scoped accessors used by the checkout saga finalizer
 * (G0-04H185 FIN-CONSISTENCY slice 2C-2D).
 */
public interface CheckoutSessionMapper extends BaseMapperX<CheckoutSessionDO> {

    /**
     * Select a session by tenant + id. Every select includes tenant_id and
     * the soft-delete guard.
     */
    @Select("SELECT * FROM checkout_session " +
            "WHERE tenant_id = #{tenantId} " +
            "AND id = #{id} " +
            "AND deleted = false")
    CheckoutSessionDO selectByIdTenant(
            @Param("tenantId") long tenantId,
            @Param("id") long id);

    /** Current read used to verify a failed terminal CAS against committed state. */
    @Select("SELECT * FROM checkout_session " +
            "WHERE tenant_id = #{tenantId} " +
            "AND id = #{id} " +
            "AND deleted = false FOR UPDATE")
    CheckoutSessionDO selectByIdTenantForUpdate(
            @Param("tenantId") long tenantId,
            @Param("id") long id);

    /**
     * CAS the session status {@code expectedStatus -> targetStatus} (e.g.
     * INITIATED -> ABANDONED). Returns the number of affected rows (0 or 1);
     * never updates a session whose status already changed, so it is safe to
     * use as a race synchronization point.
     */
    @Update("UPDATE checkout_session SET " +
            "status = #{targetStatus}, " +
            "updater = #{updater}, " +
            "update_time = #{now} " +
            "WHERE tenant_id = #{tenantId} " +
            "AND id = #{sessionId} " +
            "AND status = #{expectedStatus} " +
            "AND deleted = false")
    int casTerminalStatus(
            @Param("tenantId") long tenantId,
            @Param("sessionId") long sessionId,
            @Param("expectedStatus") String expectedStatus,
            @Param("targetStatus") String targetStatus,
            @Param("updater") String updater,
            @Param("now") LocalDateTime now);
}
