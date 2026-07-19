package com.geihou.module.finance.stock.plan.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * Data object for {@code checkout_cart_item_plan} table.
 *
 * <p>G0-04H185 FIN-CONSISTENCY slice 2B: durable, write-once stock
 * classification plan for each checkout cart item. Every
 * (checkout_session, cart_item) pair gets exactly one plan row; the
 * classification is one of {@code BOM}, {@code NON_BOM}, {@code UNMAPPED}
 * and is never updated after creation.
 *
 * <p>No soft delete: table has no {@code deleted} column and no
 * {@code @TableLogic}. No {@code creator}/{@code updater} columns.
 *
 * <p>Immutable identity (set at create time, never updated):
 * {@code tenantId}, {@code checkoutSessionId}, {@code cartItemId},
 * {@code skuId}, {@code classification}.
 */
@TableName("checkout_cart_item_plan")
public class CheckoutCartItemPlanDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    // --- Identity: tenant + checkout session + cart item ---
    private Long tenantId;
    private Long checkoutSessionId;
    private Long cartItemId;

    // --- Immutable payload ---
    private Long skuId;
    private String classification;

    // --- Timestamps ---
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getCheckoutSessionId() { return checkoutSessionId; }
    public void setCheckoutSessionId(Long checkoutSessionId) { this.checkoutSessionId = checkoutSessionId; }

    public Long getCartItemId() { return cartItemId; }
    public void setCartItemId(Long cartItemId) { this.cartItemId = cartItemId; }

    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }

    public String getClassification() { return classification; }
    public void setClassification(String classification) { this.classification = classification; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
