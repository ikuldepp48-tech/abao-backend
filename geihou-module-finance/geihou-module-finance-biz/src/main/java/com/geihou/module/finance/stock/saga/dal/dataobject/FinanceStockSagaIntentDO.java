package com.geihou.module.finance.stock.saga.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.geihou.module.finance.stock.saga.enums.FinanceStockSagaType;

import java.time.LocalDateTime;

/**
 * Data object for {@code finance_stock_saga_intent} table.
 *
 * <p>G0-04H185 FIN-CONSISTENCY slice 2C-2D: durable saga intent that
 * freezes the terminalization parameters for one checkout saga
 * ({@code saga_type + saga_id = checkout_session.id}). The finalizer
 * reads this intent and performs the terminal CAS, cart unlock, cart
 * event insert and intent finalization in one transaction.
 *
 * <p>No soft delete: table has no {@code deleted} column and no
 * {@code @TableLogic}. No {@code creator}/{@code updater} columns.
 *
 * <p>Immutable identity (set at create time, never updated):
 * {@code tenantId}, {@code sagaType}, {@code sagaId}. All fields except
 * {@code finalizationStatus}/{@code finalizedAt}/{@code updateTime} are
 * write-once.
 *
 */
@TableName("finance_stock_saga_intent")
public class FinanceStockSagaIntentDO {

    public static final String FINALIZATION_STATUS_PENDING = "PENDING";
    public static final String FINALIZATION_STATUS_FINALIZED = "FINALIZED";

    @TableId(type = IdType.AUTO)
    private Long id;

    // --- Identity: tenant + saga ---
    private Long tenantId;
    private FinanceStockSagaType sagaType;
    private Long sagaId;

    // --- Frozen terminalization parameters ---
    private Long cartId;
    private String expectedCheckoutStatus;
    private String targetCheckoutStatus;
    private String cartEventType;
    private Long operatorUserId;
    private String operatorRole;

    // --- Finalization state machine ---
    private String finalizationStatus;
    private LocalDateTime finalizedAt;

    // --- Timestamps ---
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public FinanceStockSagaType getSagaType() { return sagaType; }
    public void setSagaType(FinanceStockSagaType sagaType) { this.sagaType = sagaType; }

    public Long getSagaId() { return sagaId; }
    public void setSagaId(Long sagaId) { this.sagaId = sagaId; }

    public Long getCartId() { return cartId; }
    public void setCartId(Long cartId) { this.cartId = cartId; }

    public String getExpectedCheckoutStatus() { return expectedCheckoutStatus; }
    public void setExpectedCheckoutStatus(String expectedCheckoutStatus) { this.expectedCheckoutStatus = expectedCheckoutStatus; }

    public String getTargetCheckoutStatus() { return targetCheckoutStatus; }
    public void setTargetCheckoutStatus(String targetCheckoutStatus) { this.targetCheckoutStatus = targetCheckoutStatus; }

    public String getCartEventType() { return cartEventType; }
    public void setCartEventType(String cartEventType) { this.cartEventType = cartEventType; }

    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }

    public String getOperatorRole() { return operatorRole; }
    public void setOperatorRole(String operatorRole) { this.operatorRole = operatorRole; }

    public String getFinalizationStatus() { return finalizationStatus; }
    public void setFinalizationStatus(String finalizationStatus) { this.finalizationStatus = finalizationStatus; }

    public LocalDateTime getFinalizedAt() { return finalizedAt; }
    public void setFinalizedAt(LocalDateTime finalizedAt) { this.finalizedAt = finalizedAt; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
