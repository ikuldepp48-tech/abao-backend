package com.geihou.module.finance.stock.saga.dal.mapper;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import com.geihou.module.finance.stock.saga.dal.dataobject.FinanceStockCommandDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * Mapper for {@code finance_stock_command} table.
 *
 * <p>All select/update statements include {@code tenant_id} - no cross-tenant
 * access. Write methods are single conditional UPDATEs (CAS); no
 * select-then-unconditional-update is allowed.
 *
 * <p>Fencing: every result-write method matches
 * {@code tenant_id + id + expected_status + claim_token}. A stale or missing
 * token produces 0 affected rows, which the store translates to
 * {@code false} - it never falls through to an unconditional update.
 */
public interface FinanceStockCommandMapper extends BaseMapperX<FinanceStockCommandDO> {

    // ==================== Selects ====================

    @Select("SELECT * FROM finance_stock_command " +
            "WHERE tenant_id = #{tenantId} " +
            "AND operation = #{operation} " +
            "AND business_command_id = #{businessCommandId}")
    FinanceStockCommandDO selectByRemoteIdentity(
            @Param("tenantId") long tenantId,
            @Param("operation") String operation,
            @Param("businessCommandId") String businessCommandId);

    @Select("SELECT * FROM finance_stock_command " +
            "WHERE tenant_id = #{tenantId} " +
            "AND saga_type = #{sagaType} " +
            "AND saga_id = #{sagaId} " +
            "AND step_key = #{stepKey} " +
            "AND operation = #{operation}")
    FinanceStockCommandDO selectByLocalStep(
            @Param("tenantId") long tenantId,
            @Param("sagaType") String sagaType,
            @Param("sagaId") long sagaId,
            @Param("stepKey") String stepKey,
            @Param("operation") String operation);

    @Select("SELECT * FROM finance_stock_command " +
            "WHERE tenant_id = #{tenantId} AND id = #{id}")
    FinanceStockCommandDO selectByIdTenant(
            @Param("tenantId") long tenantId,
            @Param("id") long id);

    // ==================== Dispatch CAS ====================

    @Update("UPDATE finance_stock_command SET " +
            "status = 'IN_FLIGHT', " +
            "claim_token = #{claimToken}, " +
            "lease_until = #{leaseUntil}, " +
            "dispatch_attempts = dispatch_attempts + 1, " +
            "next_attempt_at = NULL, " +
            "update_time = #{now} " +
            "WHERE tenant_id = #{tenantId} " +
            "AND id = #{id} " +
            "AND abort_requested = FALSE " +
            "AND (status = 'PENDING' " +
            "     OR (status = 'RETRY_WAIT' " +
            "         AND next_attempt_at <= #{now}))")
    int claimDispatch(
            @Param("tenantId") long tenantId,
            @Param("id") long id,
            @Param("claimToken") String claimToken,
            @Param("now") LocalDateTime now,
            @Param("leaseUntil") LocalDateTime leaseUntil);

    // ==================== Result writes from IN_FLIGHT ====================

    @Update("UPDATE finance_stock_command SET " +
            "status = 'SUCCEEDED', " +
            "result_body = #{resultBody}, " +
            "result_schema_version = #{resultSchemaVersion}, " +
            "remote_executed_at = #{remoteExecutedAt}, " +
            "resolved_at = #{now}, " +
            "claim_token = NULL, " +
            "lease_until = NULL, " +
            "next_attempt_at = NULL, " +
            "last_error_code = NULL, " +
            "last_error_class = NULL, " +
            "last_error_message = NULL, " +
            "update_time = #{now} " +
            "WHERE tenant_id = #{tenantId} " +
            "AND id = #{id} " +
            "AND status = 'IN_FLIGHT' " +
            "AND claim_token = #{claimToken}")
    int completeSuccess(
            @Param("tenantId") long tenantId,
            @Param("id") long id,
            @Param("claimToken") String claimToken,
            @Param("resultBody") byte[] resultBody,
            @Param("resultSchemaVersion") Integer resultSchemaVersion,
            @Param("remoteExecutedAt") LocalDateTime remoteExecutedAt,
            @Param("now") LocalDateTime now);

    @Update("UPDATE finance_stock_command SET " +
            "status = 'RETRY_WAIT', " +
            "next_attempt_at = #{nextAttemptAt}, " +
            "last_error_code = #{errorCode}, " +
            "last_error_class = #{errorClass}, " +
            "last_error_message = #{errorMessage}, " +
            "claim_token = NULL, " +
            "lease_until = NULL, " +
            "update_time = #{now} " +
            "WHERE tenant_id = #{tenantId} " +
            "AND id = #{id} " +
            "AND status = 'IN_FLIGHT' " +
            "AND claim_token = #{claimToken}")
    int scheduleRetry(
            @Param("tenantId") long tenantId,
            @Param("id") long id,
            @Param("claimToken") String claimToken,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
            @Param("errorCode") Integer errorCode,
            @Param("errorClass") String errorClass,
            @Param("errorMessage") String errorMessage,
            @Param("now") LocalDateTime now);

    @Update("UPDATE finance_stock_command SET " +
            "status = 'UNKNOWN', " +
            "next_attempt_at = #{nextAttemptAt}, " +
            "last_error_code = #{errorCode}, " +
            "last_error_class = #{errorClass}, " +
            "last_error_message = #{errorMessage}, " +
            "claim_token = NULL, " +
            "lease_until = NULL, " +
            "update_time = #{now} " +
            "WHERE tenant_id = #{tenantId} " +
            "AND id = #{id} " +
            "AND status = 'IN_FLIGHT' " +
            "AND claim_token = #{claimToken}")
    int markUnknown(
            @Param("tenantId") long tenantId,
            @Param("id") long id,
            @Param("claimToken") String claimToken,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
            @Param("errorCode") Integer errorCode,
            @Param("errorClass") String errorClass,
            @Param("errorMessage") String errorMessage,
            @Param("now") LocalDateTime now);

    @Update("UPDATE finance_stock_command SET " +
            "status = 'NO_EFFECT', " +
            "last_error_code = #{errorCode}, " +
            "last_error_class = #{errorClass}, " +
            "last_error_message = #{errorMessage}, " +
            "resolved_at = #{now}, " +
            "claim_token = NULL, " +
            "lease_until = NULL, " +
            "next_attempt_at = NULL, " +
            "update_time = #{now} " +
            "WHERE tenant_id = #{tenantId} " +
            "AND id = #{id} " +
            "AND status = 'IN_FLIGHT' " +
            "AND claim_token = #{claimToken}")
    int completeNoEffect(
            @Param("tenantId") long tenantId,
            @Param("id") long id,
            @Param("claimToken") String claimToken,
            @Param("errorCode") Integer errorCode,
            @Param("errorClass") String errorClass,
            @Param("errorMessage") String errorMessage,
            @Param("now") LocalDateTime now);

    @Update("UPDATE finance_stock_command SET " +
            "status = 'STUCK', " +
            "last_error_code = #{errorCode}, " +
            "last_error_class = #{errorClass}, " +
            "last_error_message = #{errorMessage}, " +
            "resolved_at = #{now}, " +
            "claim_token = NULL, " +
            "lease_until = NULL, " +
            "next_attempt_at = NULL, " +
            "update_time = #{now} " +
            "WHERE tenant_id = #{tenantId} " +
            "AND id = #{id} " +
            "AND status = 'IN_FLIGHT' " +
            "AND claim_token = #{claimToken}")
    int markStuck(
            @Param("tenantId") long tenantId,
            @Param("id") long id,
            @Param("claimToken") String claimToken,
            @Param("errorCode") Integer errorCode,
            @Param("errorClass") String errorClass,
            @Param("errorMessage") String errorMessage,
            @Param("now") LocalDateTime now);

    // ==================== Lease expiry (batch) ====================

    @Update("UPDATE finance_stock_command SET " +
            "status = 'UNKNOWN', " +
            "claim_token = NULL, " +
            "lease_until = NULL, " +
            "next_attempt_at = #{nextAttemptAt}, " +
            "update_time = #{now} " +
            "WHERE tenant_id = #{tenantId} " +
            "AND status = 'IN_FLIGHT' " +
            "AND lease_until < #{now}")
    int expireLeasesToUnknown(
            @Param("tenantId") long tenantId,
            @Param("now") LocalDateTime now,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt);

    // ==================== UNKNOWN resolution ====================

    @Update("UPDATE finance_stock_command SET " +
            "claim_token = #{claimToken}, " +
            "lease_until = #{leaseUntil}, " +
            "resolution_attempts = resolution_attempts + 1, " +
            "update_time = #{now} " +
            "WHERE tenant_id = #{tenantId} " +
            "AND id = #{id} " +
            "AND status = 'UNKNOWN' " +
            "AND next_attempt_at <= #{now} " +
            "AND (claim_token IS NULL OR lease_until < #{now})")
    int claimResolution(
            @Param("tenantId") long tenantId,
            @Param("id") long id,
            @Param("claimToken") String claimToken,
            @Param("now") LocalDateTime now,
            @Param("leaseUntil") LocalDateTime leaseUntil);

    @Update("UPDATE finance_stock_command SET " +
            "status = 'SUCCEEDED', " +
            "result_body = #{resultBody}, " +
            "result_schema_version = #{resultSchemaVersion}, " +
            "remote_executed_at = #{remoteExecutedAt}, " +
            "resolved_at = #{now}, " +
            "claim_token = NULL, " +
            "lease_until = NULL, " +
            "next_attempt_at = NULL, " +
            "last_error_code = NULL, " +
            "last_error_class = NULL, " +
            "last_error_message = NULL, " +
            "update_time = #{now} " +
            "WHERE tenant_id = #{tenantId} " +
            "AND id = #{id} " +
            "AND status = 'UNKNOWN' " +
            "AND claim_token = #{claimToken}")
    int completeSuccessFromUnknown(
            @Param("tenantId") long tenantId,
            @Param("id") long id,
            @Param("claimToken") String claimToken,
            @Param("resultBody") byte[] resultBody,
            @Param("resultSchemaVersion") Integer resultSchemaVersion,
            @Param("remoteExecutedAt") LocalDateTime remoteExecutedAt,
            @Param("now") LocalDateTime now);

    @Update("UPDATE finance_stock_command SET " +
            "status = 'NO_EFFECT', " +
            "last_error_code = #{errorCode}, " +
            "last_error_class = #{errorClass}, " +
            "last_error_message = #{errorMessage}, " +
            "resolved_at = #{now}, " +
            "claim_token = NULL, " +
            "lease_until = NULL, " +
            "next_attempt_at = NULL, " +
            "update_time = #{now} " +
            "WHERE tenant_id = #{tenantId} " +
            "AND id = #{id} " +
            "AND status = 'UNKNOWN' " +
            "AND claim_token = #{claimToken}")
    int completeNoEffectFromUnknown(
            @Param("tenantId") long tenantId,
            @Param("id") long id,
            @Param("claimToken") String claimToken,
            @Param("errorCode") Integer errorCode,
            @Param("errorClass") String errorClass,
            @Param("errorMessage") String errorMessage,
            @Param("now") LocalDateTime now);

    @Update("UPDATE finance_stock_command SET " +
            "status = 'STUCK', " +
            "last_error_code = #{errorCode}, " +
            "last_error_class = #{errorClass}, " +
            "last_error_message = #{errorMessage}, " +
            "resolved_at = #{now}, " +
            "claim_token = NULL, " +
            "lease_until = NULL, " +
            "next_attempt_at = NULL, " +
            "update_time = #{now} " +
            "WHERE tenant_id = #{tenantId} " +
            "AND id = #{id} " +
            "AND status = 'UNKNOWN' " +
            "AND claim_token = #{claimToken}")
    int markStuckFromUnknown(
            @Param("tenantId") long tenantId,
            @Param("id") long id,
            @Param("claimToken") String claimToken,
            @Param("errorCode") Integer errorCode,
            @Param("errorClass") String errorClass,
            @Param("errorMessage") String errorMessage,
            @Param("now") LocalDateTime now);

    // ==================== Abort ====================

    @Update("UPDATE finance_stock_command SET " +
            "status = 'CANCELLED', " +
            "abort_requested = TRUE, " +
            "claim_token = NULL, " +
            "lease_until = NULL, " +
            "next_attempt_at = NULL, " +
            "update_time = #{now} " +
            "WHERE tenant_id = #{tenantId} " +
            "AND id = #{id} " +
            "AND status IN ('PENDING', 'RETRY_WAIT') " +
            "AND claim_token IS NULL")
    int cancelUnclaimed(
            @Param("tenantId") long tenantId,
            @Param("id") long id,
            @Param("now") LocalDateTime now);

    @Update("UPDATE finance_stock_command SET " +
            "abort_requested = TRUE, " +
            "update_time = #{now} " +
            "WHERE tenant_id = #{tenantId} " +
            "AND id = #{id} " +
            "AND status IN ('IN_FLIGHT', 'UNKNOWN')")
    int requestAbortInFlight(
            @Param("tenantId") long tenantId,
            @Param("id") long id,
            @Param("now") LocalDateTime now);
}
