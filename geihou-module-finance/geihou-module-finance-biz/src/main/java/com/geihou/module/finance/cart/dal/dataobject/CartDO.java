package com.geihou.module.finance.cart.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Data object for cart table.
 *
 * <p>All money fields use BigDecimal (never double/float).
 * Status field stores ENUM_CART_STATUS code values.
 * Channel field stores ENUM_ORDER_CHANNEL 9 values (SSOT, CG-6 ruling).
 * Soft delete via @TableLogic.
 * Optimistic locking via @Version.
 */
@TableName("cart")
public class CartDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    // Customer & shop
    private Long customerUserId;
    private Long shopId;
    private Long tableId;

    // Channel — ENUM_ORDER_CHANNEL 9 values (SSOT)
    private String channel;

    // Status — ENUM_CART_STATUS 4 values
    private String status;

    // Summary fields
    private Integer itemCount;
    private Integer totalQuantity;

    // Money — BigDecimal only
    private BigDecimal subtotalAmount;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;

    // Business date
    private LocalDate businessDate;

    // Staff assisted
    private Boolean isStaffAssisted;
    private Long assistedByUserId;

    // Optimistic lock
    @Version
    private Integer version;

    // Activity time
    private LocalDateTime lastActivityTime;

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

    public Long getCustomerUserId() { return customerUserId; }
    public void setCustomerUserId(Long customerUserId) { this.customerUserId = customerUserId; }

    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }

    public Long getTableId() { return tableId; }
    public void setTableId(Long tableId) { this.tableId = tableId; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getItemCount() { return itemCount; }
    public void setItemCount(Integer itemCount) { this.itemCount = itemCount; }

    public Integer getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(Integer totalQuantity) { this.totalQuantity = totalQuantity; }

    public BigDecimal getSubtotalAmount() { return subtotalAmount; }
    public void setSubtotalAmount(BigDecimal subtotalAmount) { this.subtotalAmount = subtotalAmount; }

    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public LocalDate getBusinessDate() { return businessDate; }
    public void setBusinessDate(LocalDate businessDate) { this.businessDate = businessDate; }

    public Boolean getIsStaffAssisted() { return isStaffAssisted; }
    public void setIsStaffAssisted(Boolean isStaffAssisted) { this.isStaffAssisted = isStaffAssisted; }

    public Long getAssistedByUserId() { return assistedByUserId; }
    public void setAssistedByUserId(Long assistedByUserId) { this.assistedByUserId = assistedByUserId; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public LocalDateTime getLastActivityTime() { return lastActivityTime; }
    public void setLastActivityTime(LocalDateTime lastActivityTime) { this.lastActivityTime = lastActivityTime; }

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
