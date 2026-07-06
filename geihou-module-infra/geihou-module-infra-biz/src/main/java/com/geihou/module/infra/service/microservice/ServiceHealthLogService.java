package com.geihou.module.infra.service.microservice;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geihou.common.pojo.PageResult;
import com.geihou.module.infra.dal.dataobject.microservice.ServiceHealthLogDO;
import com.geihou.module.infra.dal.mysql.microservice.ServiceHealthLogRepository;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ServiceHealthLogService {

    private static final Set<String> VALID_STATUSES = Set.of("UP", "DOWN", "TIMEOUT", "DEGRADED");
    private static final int MAX_SERVICE_NAME_LENGTH = 64;
    private static final int DEFAULT_PAGE_NO = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ServiceHealthLogRepository repository;

    public ServiceHealthLogService(ServiceHealthLogRepository repository) {
        this.repository = repository;
    }

    public ServiceHealthLogDO record(ServiceHealthLogDO entity) {
        validateRecord(entity);
        if (entity.getCreateTime() == null) {
            entity.setCreateTime(LocalDateTime.now());
        }
        repository.insert(entity);
        return entity;
    }

    public ServiceHealthLogDO getById(Long id) {
        return repository.selectById(id);
    }

    public PageResult<ServiceHealthLogDO> page(ServiceHealthLogPageReq req) {
        int pageNo = req == null || req.getPageNo() == null ? DEFAULT_PAGE_NO : req.getPageNo();
        int pageSize = req == null || req.getPageSize() == null ? DEFAULT_PAGE_SIZE : req.getPageSize();
        validatePage(pageNo, pageSize, req);
        return repository.selectPage(pageNo, pageSize, buildWrapper(req));
    }

    private void validateRecord(ServiceHealthLogDO entity) {
        if (entity == null) {
            throw new IllegalArgumentException("entity must not be null");
        }
        if (entity.getServiceName() == null || entity.getServiceName().isBlank()) {
            throw new IllegalArgumentException("serviceName must not be blank");
        }
        if (entity.getServiceName().length() > MAX_SERVICE_NAME_LENGTH) {
            throw new IllegalArgumentException("serviceName length must not exceed " + MAX_SERVICE_NAME_LENGTH);
        }
        if (entity.getCheckTime() == null) {
            throw new IllegalArgumentException("checkTime must not be null");
        }
        if (entity.getCheckTime().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("checkTime must not be in the future");
        }
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            throw new IllegalArgumentException("status must not be blank");
        }
        if (!VALID_STATUSES.contains(entity.getStatus())) {
            throw new IllegalArgumentException("status must be one of UP, DOWN, TIMEOUT, DEGRADED");
        }
        if (entity.getResponseTimeMs() != null && entity.getResponseTimeMs() < 0) {
            throw new IllegalArgumentException("responseTimeMs must not be negative");
        }
    }

    private void validatePage(int pageNo, int pageSize, ServiceHealthLogPageReq req) {
        if (pageNo < DEFAULT_PAGE_NO) {
            throw new IllegalArgumentException("pageNo must be at least 1");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize must be between 1 and 100");
        }
        if (req == null) {
            return;
        }
        if (req.getStatus() != null && !req.getStatus().isBlank()
                && !VALID_STATUSES.contains(req.getStatus())) {
            throw new IllegalArgumentException("status must be one of UP, DOWN, TIMEOUT, DEGRADED");
        }
        if (req.getCheckTimeStart() != null && req.getCheckTimeEnd() != null
                && req.getCheckTimeStart().isAfter(req.getCheckTimeEnd())) {
            throw new IllegalArgumentException("checkTimeStart must not be after checkTimeEnd");
        }
    }

    private LambdaQueryWrapper<ServiceHealthLogDO> buildWrapper(ServiceHealthLogPageReq req) {
        LambdaQueryWrapper<ServiceHealthLogDO> wrapper = new LambdaQueryWrapper<>();
        if (req == null) {
            return wrapper;
        }
        if (req.getServiceName() != null && !req.getServiceName().isBlank()) {
            wrapper.eq(ServiceHealthLogDO::getServiceName, req.getServiceName());
        }
        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            wrapper.eq(ServiceHealthLogDO::getStatus, req.getStatus());
        }
        if (req.getCheckTimeStart() != null) {
            wrapper.ge(ServiceHealthLogDO::getCheckTime, req.getCheckTimeStart());
        }
        if (req.getCheckTimeEnd() != null) {
            wrapper.le(ServiceHealthLogDO::getCheckTime, req.getCheckTimeEnd());
        }
        return wrapper;
    }
}
