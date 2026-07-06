package com.geihou.module.finance.product.controller.admin.vo;

import java.time.LocalDateTime;

/**
 * Combo item response VO.
 */
public class ComboItemRespVO {

    private Long id;
    private Long comboId;
    private Long itemSkuId;
    private Integer quantity;
    private Boolean isOptional;
    private Integer alternativeGroup;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getComboId() { return comboId; }
    public void setComboId(Long comboId) { this.comboId = comboId; }

    public Long getItemSkuId() { return itemSkuId; }
    public void setItemSkuId(Long itemSkuId) { this.itemSkuId = itemSkuId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Boolean getIsOptional() { return isOptional; }
    public void setIsOptional(Boolean isOptional) { this.isOptional = isOptional; }

    public Integer getAlternativeGroup() { return alternativeGroup; }
    public void setAlternativeGroup(Integer alternativeGroup) { this.alternativeGroup = alternativeGroup; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
