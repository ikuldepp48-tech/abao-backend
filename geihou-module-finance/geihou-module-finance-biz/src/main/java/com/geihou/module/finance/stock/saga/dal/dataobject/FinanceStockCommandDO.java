package com.geihou.module.finance.stock.saga.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * Data object for {@code finance_stock_command} table.
 *
 * <p>G0-04H185-FIN-CONSISTENCY slice 1: durable command store for
 * finance->supplychain write commands. Stores the immutable request identity,
 * saga state machine, claim/lease fencing, and optional result snapshot.
 *
 * <p>No soft delete: table has no {@code deleted} column and no
 * {@code @TableLogic}. No {@code creator}/{@code updater} columns.
 *
 * <p>{@code requestBody} and {@code resultBody} are {@code byte[]} (MEDIUMBLOB).
 * Getters and setters perform defensive copies so callers cannot mutate the
 * persisted identity bytes after the fact.
 *
 * <p>Immutable identity (set at create time, never updated):
 * {@code tenantId}, {@code operation}, {@code businessCommandId},
 * {@code transportMode}, {@code c0JournalAvailable},
 * {@code requestSchemaVersion}, {@code requestBody}, {@code requestBodySha256},
 * {@code sagaType}, {@code sagaId}, {@code stepKey}.
 */
@TableName("finance_stock_command")
public class FinanceStockCommandDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    // --- Identity: tenant + saga ---
    private Long tenantId;
    private String sagaType;
    private Long sagaId;
    private String stepKey;
    private Long parentCommandId;

    // --- Identity: remote command ---
    private String operation;
    private String businessCommandId;

    // --- Publish fence ---
    private String transportMode;
    private Boolean c0JournalAvailable;
    private Integer requestSchemaVersion;
    private byte[] requestBody;
    private String requestBodySha256;

    // --- Result snapshot ---
    private Integer resultSchemaVersion;
    private byte[] resultBody;

    // --- State machine ---
    private String status;
    private Boolean abortRequested;
    private Integer dispatchAttempts;
    private Integer resolutionAttempts;
    private Integer maxDispatchAttempts;
    private Integer maxResolutionAttempts;

    // --- Scheduling & lease ---
    private LocalDateTime nextAttemptAt;
    private String claimToken;
    private LocalDateTime leaseUntil;

    // --- Error info ---
    private Integer lastErrorCode;
    private String lastErrorClass;
    private String lastErrorMessage;

    // --- Timestamps ---
    private LocalDateTime remoteExecutedAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getSagaType() { return sagaType; }
    public void setSagaType(String sagaType) { this.sagaType = sagaType; }

    public Long getSagaId() { return sagaId; }
    public void setSagaId(Long sagaId) { this.sagaId = sagaId; }

    public String getStepKey() { return stepKey; }
    public void setStepKey(String stepKey) { this.stepKey = stepKey; }

    public Long getParentCommandId() { return parentCommandId; }
    public void setParentCommandId(Long parentCommandId) { this.parentCommandId = parentCommandId; }

    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }

    public String getBusinessCommandId() { return businessCommandId; }
    public void setBusinessCommandId(String businessCommandId) { this.businessCommandId = businessCommandId; }

    public String getTransportMode() { return transportMode; }
    public void setTransportMode(String transportMode) { this.transportMode = transportMode; }

    public Boolean getC0JournalAvailable() { return c0JournalAvailable; }
    public void setC0JournalAvailable(Boolean c0JournalAvailable) { this.c0JournalAvailable = c0JournalAvailable; }

    public Integer getRequestSchemaVersion() { return requestSchemaVersion; }
    public void setRequestSchemaVersion(Integer requestSchemaVersion) { this.requestSchemaVersion = requestSchemaVersion; }

    /**
     * Returns a defensive copy of the request body bytes.
     *
     * <p>Callers cannot mutate the persisted identity bytes through this getter.
     * Returns {@code null} if the field is unset.
     */
    public byte[] getRequestBody() {
        return requestBody == null ? null : requestBody.clone();
    }

    /**
     * Stores a defensive copy of the request body bytes.
     *
     * <p>The internal array is isolated from the caller's array so later
     * mutations by the caller do not affect the persisted identity.
     */
    public void setRequestBody(byte[] requestBody) {
        this.requestBody = requestBody == null ? null : requestBody.clone();
    }

    public String getRequestBodySha256() { return requestBodySha256; }
    public void setRequestBodySha256(String requestBodySha256) { this.requestBodySha256 = requestBodySha256; }

    public Integer getResultSchemaVersion() { return resultSchemaVersion; }
    public void setResultSchemaVersion(Integer resultSchemaVersion) { this.resultSchemaVersion = resultSchemaVersion; }

    /**
     * Returns a defensive copy of the result body bytes.
     *
     * @return a clone of the internal byte array, or {@code null} if unset
     */
    public byte[] getResultBody() {
        return resultBody == null ? null : resultBody.clone();
    }

    /**
     * Stores a defensive copy of the result body bytes.
     */
    public void setResultBody(byte[] resultBody) {
        this.resultBody = resultBody == null ? null : resultBody.clone();
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Boolean getAbortRequested() { return abortRequested; }
    public void setAbortRequested(Boolean abortRequested) { this.abortRequested = abortRequested; }

    public Integer getDispatchAttempts() { return dispatchAttempts; }
    public void setDispatchAttempts(Integer dispatchAttempts) { this.dispatchAttempts = dispatchAttempts; }

    public Integer getResolutionAttempts() { return resolutionAttempts; }
    public void setResolutionAttempts(Integer resolutionAttempts) { this.resolutionAttempts = resolutionAttempts; }

    public Integer getMaxDispatchAttempts() { return maxDispatchAttempts; }
    public void setMaxDispatchAttempts(Integer maxDispatchAttempts) { this.maxDispatchAttempts = maxDispatchAttempts; }

    public Integer getMaxResolutionAttempts() { return maxResolutionAttempts; }
    public void setMaxResolutionAttempts(Integer maxResolutionAttempts) { this.maxResolutionAttempts = maxResolutionAttempts; }

    public LocalDateTime getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(LocalDateTime nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }

    public String getClaimToken() { return claimToken; }
    public void setClaimToken(String claimToken) { this.claimToken = claimToken; }

    public LocalDateTime getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(LocalDateTime leaseUntil) { this.leaseUntil = leaseUntil; }

    public Integer getLastErrorCode() { return lastErrorCode; }
    public void setLastErrorCode(Integer lastErrorCode) { this.lastErrorCode = lastErrorCode; }

    public String getLastErrorClass() { return lastErrorClass; }
    public void setLastErrorClass(String lastErrorClass) { this.lastErrorClass = lastErrorClass; }

    public String getLastErrorMessage() { return lastErrorMessage; }
    public void setLastErrorMessage(String lastErrorMessage) { this.lastErrorMessage = lastErrorMessage; }

    public LocalDateTime getRemoteExecutedAt() { return remoteExecutedAt; }
    public void setRemoteExecutedAt(LocalDateTime remoteExecutedAt) { this.remoteExecutedAt = remoteExecutedAt; }

    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
