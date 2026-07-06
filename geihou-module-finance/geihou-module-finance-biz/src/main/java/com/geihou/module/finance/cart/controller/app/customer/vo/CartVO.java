package com.geihou.module.finance.cart.controller.app.customer.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Cart VO for customer-facing responses.
 *
 * <p>All money fields use BigDecimal (never double/float).
 * Returned by all 5 customer cart endpoints.
 */
public class CartVO {

    private Long id;
    private Long customerUserId;
    private Long shopId;
    private Long tableId;
    private String channel;
    private String status;
    private Integer itemCount;
    private Integer totalQuantity;
    private BigDecimal subtotalAmount;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private Integer version;
    private LocalDateTime lastActivityTime;
    private LocalDateTime createTime;
    private List<CartItemVO> items;

    // Staff-assisted fields (G1-04F) — optional, mapped from CartDO
    private Boolean isStaffAssisted;
    private Long assistedByUserId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public LocalDateTime getLastActivityTime() { return lastActivityTime; }
    public void setLastActivityTime(LocalDateTime lastActivityTime) { this.lastActivityTime = lastActivityTime; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public List<CartItemVO> getItems() { return items; }
    public void setItems(List<CartItemVO> items) { this.items = items; }

    public Boolean getIsStaffAssisted() { return isStaffAssisted; }
    public void setIsStaffAssisted(Boolean isStaffAssisted) { this.isStaffAssisted = isStaffAssisted; }

    public Long getAssistedByUserId() { return assistedByUserId; }
    public void setAssistedByUserId(Long assistedByUserId) { this.assistedByUserId = assistedByUserId; }
}
