package com.geihou.module.finance.product.controller.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Combo creation request VO.
 *
 * <p>comboPrice uses BigDecimal (never double/float).
 * Optional initial combo items can be provided; validation is in the service layer.
 */
public class ComboCreateReqVO {

    private Long comboSkuId;
    private String comboName;
    private BigDecimal comboPrice;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveUntil;
    private List<ComboItemCreateReqVO> items;

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

    public List<ComboItemCreateReqVO> getItems() { return items; }
    public void setItems(List<ComboItemCreateReqVO> items) { this.items = items; }
}
