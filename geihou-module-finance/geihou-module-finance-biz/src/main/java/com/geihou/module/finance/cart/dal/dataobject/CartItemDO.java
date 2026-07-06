package com.geihou.module.finance.cart.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data object for cart_item table.
 *
 * <p>Immutable snapshot fields: sku_name_snapshot, unit_price_snapshot —
 * these are set at creation and never modified (AC-9).
 * sku_id is Long (BIGINT NOT NULL — Cart root-cause 2 defense).
 * All money fields use BigDecimal (never double/float).
 * item_state stores ENUM_CART_ITEM_STATE values (default NORMAL, CG-5 degradation).
 * Soft delete via @TableLogic.
 */
@TableName("cart_item")
public class CartItemDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long cartId;

    // SKU — strict Long (root-cause 2 defense)
    private Long skuId;
    private Long spuId;

    // Snapshot (frozen at add time — AC-9)
    private String skuNameSnapshot;
    private String skuImageSnapshot;
    private BigDecimal unitPriceSnapshot;

    // Quantity
    private Integer quantity;

    // Options (JSON)
    private String options;
    private BigDecimal optionsExtraPrice;

    // Money — BigDecimal only
    private BigDecimal itemSubtotal;
    private BigDecimal itemDiscount;
    private BigDecimal itemTotal;

    // Promotion
    private Long appliedPromotionId;

    // Item state — ENUM_CART_ITEM_STATE
    private String itemState;

    // Audit
    private String creator;
    private LocalDateTime createTime;
    private String updater;
    private LocalDateTime updateTime;

    @TableLogic
    private Boolean deleted;

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getCartId() { return cartId; }
    public void setCartId(Long cartId) { this.cartId = cartId; }

    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }

    public Long getSpuId() { return spuId; }
    public void setSpuId(Long spuId) { this.spuId = spuId; }

    public String getSkuNameSnapshot() { return skuNameSnapshot; }
    public void setSkuNameSnapshot(String skuNameSnapshot) { this.skuNameSnapshot = skuNameSnapshot; }

    public String getSkuImageSnapshot() { return skuImageSnapshot; }
    public void setSkuImageSnapshot(String skuImageSnapshot) { this.skuImageSnapshot = skuImageSnapshot; }

    public BigDecimal getUnitPriceSnapshot() { return unitPriceSnapshot; }
    public void setUnitPriceSnapshot(BigDecimal unitPriceSnapshot) { this.unitPriceSnapshot = unitPriceSnapshot; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getOptions() { return options; }
    public void setOptions(String options) { this.options = options; }

    public BigDecimal getOptionsExtraPrice() { return optionsExtraPrice; }
    public void setOptionsExtraPrice(BigDecimal optionsExtraPrice) { this.optionsExtraPrice = optionsExtraPrice; }

    public BigDecimal getItemSubtotal() { return itemSubtotal; }
    public void setItemSubtotal(BigDecimal itemSubtotal) { this.itemSubtotal = itemSubtotal; }

    public BigDecimal getItemDiscount() { return itemDiscount; }
    public void setItemDiscount(BigDecimal itemDiscount) { this.itemDiscount = itemDiscount; }

    public BigDecimal getItemTotal() { return itemTotal; }
    public void setItemTotal(BigDecimal itemTotal) { this.itemTotal = itemTotal; }

    public Long getAppliedPromotionId() { return appliedPromotionId; }
    public void setAppliedPromotionId(Long appliedPromotionId) { this.appliedPromotionId = appliedPromotionId; }

    public String getItemState() { return itemState; }
    public void setItemState(String itemState) { this.itemState = itemState; }

    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public String getUpdater() { return updater; }
    public void setUpdater(String updater) { this.updater = updater; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
}
