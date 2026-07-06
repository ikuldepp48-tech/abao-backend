package com.geihou.module.finance.product.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data object for product_addon_option table.
 *
 * <p>加料选项。option_sku_id 关联同租户 product_sku.id。
 * extra_price uses BigDecimal (never double/float).
 * status stores ENUM_ADDON_OPTION_STATUS code values (ACTIVE/SOLD_OUT/DISABLED).
 */
@TableName("product_addon_option")
public class ProductAddonOptionDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long addonGroupId;

    private Long optionSkuId;
    private String optionName;
    private BigDecimal extraPrice;
    private Integer sortOrder;
    private String status;

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

    public Long getAddonGroupId() { return addonGroupId; }
    public void setAddonGroupId(Long addonGroupId) { this.addonGroupId = addonGroupId; }

    public Long getOptionSkuId() { return optionSkuId; }
    public void setOptionSkuId(Long optionSkuId) { this.optionSkuId = optionSkuId; }

    public String getOptionName() { return optionName; }
    public void setOptionName(String optionName) { this.optionName = optionName; }

    public BigDecimal getExtraPrice() { return extraPrice; }
    public void setExtraPrice(BigDecimal extraPrice) { this.extraPrice = extraPrice; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

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
