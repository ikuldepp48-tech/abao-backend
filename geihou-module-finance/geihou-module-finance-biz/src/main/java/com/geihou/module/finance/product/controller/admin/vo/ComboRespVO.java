package com.geihou.module.finance.product.controller.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Combo response VO (includes items list).
 */
public class ComboRespVO {

    private Long id;
    private Long comboSkuId;
    private String comboName;
    private BigDecimal comboPrice;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveUntil;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<ComboItemRespVO> items;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getComboSkuId() { return comboSkuId; }
    public void setComboSkuId(Long comboSkuId) { this.comboSkuId = comboSkuId; }

    public String getComboName() { return comboName; }
    public void setComboName(String comboName) { this.comboName = comboName; }

    public BigDecimal getComboPrice() { return comboPrice; }
    public void setComboPrice(BigDecimal comboPrice) { this.comboPrice = comboPrice; }

    public LocalDateTime getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDateTime effectiveFrom) { this.effectiveFrom = effectiveFrom; }

    public LocalDateTime getEffectiveUntil() { return effectiveUntil; }
    public void setEffectiveUntil(LocalDateTime effectiveUntil) { this.effectiveUntil = effectiveUntil; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public List<ComboItemRespVO> getItems() { return items; }
    public void setItems(List<ComboItemRespVO> items) { this.items = items; }
}
