package com.geihou.module.infra.service.microservice;

import java.time.LocalDateTime;

public class ServiceHealthLogPageReq {

    private Integer pageNo;
    private Integer pageSize;
    private String serviceName;
    private String status;
    private LocalDateTime checkTimeStart;
    private LocalDateTime checkTimeEnd;

    public Integer getPageNo() {
        return pageNo;
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCheckTimeStart() {
        return checkTimeStart;
    }

    public void setCheckTimeStart(LocalDateTime checkTimeStart) {
        this.checkTimeStart = checkTimeStart;
    }

    public LocalDateTime getCheckTimeEnd() {
        return checkTimeEnd;
    }

    public void setCheckTimeEnd(LocalDateTime checkTimeEnd) {
        this.checkTimeEnd = checkTimeEnd;
    }
}
