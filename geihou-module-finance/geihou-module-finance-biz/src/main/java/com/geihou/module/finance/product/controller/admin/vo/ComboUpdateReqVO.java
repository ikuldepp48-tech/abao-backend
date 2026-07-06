package com.geihou.module.finance.product.controller.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Combo update request VO.
 */
public class ComboUpdateReqVO {

    private String comboName;
    private BigDecimal comboPrice;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveUntil;

    public String getComboName() { return comboName; }
    public void setComboName(String comboName) { this.comboName = comboName; }

    public BigDecimal getComboPrice() { return comboPrice; }
    public void setComboPrice(BigDecimal comboPrice) { this.comboPrice = comboPrice; }

    public LocalDateTime getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDateTime effectiveFrom) { this.effectiveFrom = effectiveFrom; }

    public LocalDateTime getEffectiveUntil() { return effectiveUntil; }
    public void setEffectiveUntil(LocalDateTime effectiveUntil) { this.effectiveUntil = effectiveUntil; }
}
