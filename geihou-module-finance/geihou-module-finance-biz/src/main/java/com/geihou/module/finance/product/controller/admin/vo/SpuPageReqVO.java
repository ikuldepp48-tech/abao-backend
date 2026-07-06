package com.geihou.module.finance.product.controller.admin.vo;

/**
 * SPU page query request VO.
 */
public class SpuPageReqVO {

    private Integer pageNo = 1;
    private Integer pageSize = 10;
    private String spuCode;
    private String spuName;
    private Long categoryId;
    private String status;
    private String spuType;

    public Integer getPageNo() { return pageNo; }
    public void setPageNo(Integer pageNo) { this.pageNo = pageNo; }

    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }

    public String getSpuCode() { return spuCode; }
    public void setSpuCode(String spuCode) { this.spuCode = spuCode; }

    public String getSpuName() { return spuName; }
    public void setSpuName(String spuName) { this.spuName = spuName; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSpuType() { return spuType; }
    public void setSpuType(String spuType) { this.spuType = spuType; }
}
