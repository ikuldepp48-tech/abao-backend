package com.geihou.module.supplychain.transfer.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data object for transfer_order_item table.
 *
 * <p>调拨单明细 — 每行记录一个物料的调拨数量及关联的库存事件。
 *
 * <p>Source: TASK-G2-02S, PRD-组2-02 §2.1。
 */
@TableName("transfer_order_item")
public class TransferOrderItemDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;

    /** 调拨单ID */
    private Long transferOrderId;

    /** 产品ID(product_master) */
    private Long productId;
    /** 库存品项ID(stock_item) */
    private Long stockItemId;
    /** SKU编码(冗余) */
    private String skuCode;

    /** 调拨数量(正数) */
    private BigDecimal quantity;
    /** 单位 */
    private String unit;

    /** 发货时写入的 TRANSFER_OUT stock_event ID */
    private Long outEventId;
    /** 收货时写入的 TRANSFER_IN stock_event ID */
    private Long inEventId;

    // --- Audit ---
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

    public Long getTransferOrderId() { return transferOrderId; }
    public void setTransferOrderId(Long transferOrderId) { this.transferOrderId = transferOrderId; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public Long getStockItemId() { return stockItemId; }
    public void setStockItemId(Long stockItemId) { this.stockItemId = stockItemId; }

    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Long getOutEventId() { return outEventId; }
    public void setOutEventId(Long outEventId) { this.outEventId = outEventId; }

    public Long getInEventId() { return inEventId; }
    public void setInEventId(Long inEventId) { this.inEventId = inEventId; }

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
