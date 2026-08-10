package com.geihou.module.finance.stock.saga.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.finance.stock.saga.dal.dataobject.FinanceStockSagaIntentDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * Mapper for {@code finance_stock_saga_intent} table.
 *
 * <p>All select/update statements include {@code tenant_id} - no cross-tenant
 * access. {@link #finalizePending} is a single conditional UPDATE (CAS
 * PENDING -> FINALIZED) returning the number of affected rows; the store
 * translates exactly one row into {@code true}.
 *
 * <p>G0-04H185 FIN-CONSISTENCY slice 2C-2D.
 */
public interface FinanceStockSagaIntentMapper extends BaseMapperX<FinanceStockSagaIntentDO> {

    /**
     * Select the single intent for a saga identity.
     * Every select includes {@code tenant_id}.
     */
    @Select("SELECT * FROM finance_stock_saga_intent " +
            "WHERE tenant_id = #{tenantId} " +
            "AND saga_type = #{sagaType} " +
            "AND saga_id = #{sagaId}")
    FinanceStockSagaIntentDO selectByIdentity(
            @Param("tenantId") long tenantId,
            @Param("sagaType") String sagaType,
            @Param("sagaId") long sagaId);

    /**
     * Shared current read for duplicate-key reconciliation. Unlike a
     * consistent snapshot read under MySQL REPEATABLE_READ, {@code FOR SHARE}
     * sees the latest committed winner. The shared lock is intentional:
     * multiple duplicate-insert losers already hold shared duplicate-record
     * locks, so upgrading all of them to {@code FOR UPDATE} can deadlock.
     */
    @Select("SELECT * FROM finance_stock_saga_intent " +
            "WHERE tenant_id = #{tenantId} " +
            "AND saga_type = #{sagaType} " +
            "AND saga_id = #{sagaId} FOR SHARE")
    FinanceStockSagaIntentDO selectByIdentityForShare(
            @Param("tenantId") long tenantId,
            @Param("sagaType") String sagaType,
            @Param("sagaId") long sagaId);

    /**
     * Exclusive current read for finalizer loser verification. Unlike a
     * consistent snapshot read under MySQL REPEATABLE_READ,
     * {@code FOR UPDATE} sees the latest committed winner and keeps the row
     * stable while terminal side effects are verified.
     */
    @Select("SELECT * FROM finance_stock_saga_intent " +
            "WHERE tenant_id = #{tenantId} " +
            "AND saga_type = #{sagaType} " +
            "AND saga_id = #{sagaId} FOR UPDATE")
    FinanceStockSagaIntentDO selectByIdentityForUpdate(
            @Param("tenantId") long tenantId,
            @Param("sagaType") String sagaType,
            @Param("sagaId") long sagaId);

    /**
     * CAS transition {@code finalization_status} PENDING -> FINALIZED.
     * Returns the number of affected rows (0 or 1); never updates an
     * already-finalized intent.
     */
    @Update("UPDATE finance_stock_saga_intent SET " +
            "finalization_status = 'FINALIZED', " +
            "finalized_at = #{now}, " +
            "update_time = #{now} " +
            "WHERE tenant_id = #{tenantId} " +
            "AND saga_type = #{sagaType} " +
            "AND saga_id = #{sagaId} " +
            "AND finalization_status = 'PENDING'")
    int finalizePending(
            @Param("tenantId") long tenantId,
            @Param("sagaType") String sagaType,
            @Param("sagaId") long sagaId,
            @Param("now") LocalDateTime now);
}
