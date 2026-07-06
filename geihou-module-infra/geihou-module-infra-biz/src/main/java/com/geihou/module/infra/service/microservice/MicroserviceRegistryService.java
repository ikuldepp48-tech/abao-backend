package com.geihou.module.infra.service.microservice;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geihou.common.pojo.PageResult;
import com.geihou.module.infra.dal.dataobject.microservice.MicroserviceRegistryDO;
import com.geihou.module.infra.dal.mysql.microservice.MicroserviceRegistryRepository;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class MicroserviceRegistryService {

    private static final Set<String> VALID_SERVICE_TYPES = Set.of("BUSINESS", "COMMON", "GATEWAY");
    private static final int MAX_SERVICE_NAME_LENGTH = 64;
    private static final int MIN_SUBSYSTEM_ID = 1;
    private static final int MAX_SUBSYSTEM_ID = 11;
    private static final int DEFAULT_PAGE_NO = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final MicroserviceRegistryRepository repository;

    public MicroserviceRegistryService(MicroserviceRegistryRepository repository) {
        this.repository = repository;
    }

    public MicroserviceRegistryDO getById(Long id) {
        validateId(id);
        return repository.selectById(id);
    }

    public MicroserviceRegistryDO getByServiceName(String serviceName) {
        validateServiceName(serviceName);
        return repository.selectByServiceName(serviceName);
    }

    public PageResult<MicroserviceRegistryDO> page(MicroserviceRegistryPageReq req) {
        int pageNo = req == null || req.getPageNo() == null ? DEFAULT_PAGE_NO : req.getPageNo();
        int pageSize = req == null || req.getPageSize() == null ? DEFAULT_PAGE_SIZE : req.getPageSize();
        validatePage(pageNo, pageSize, req);
        return repository.selectPage(pageNo, pageSize, buildWrapper(req));
    }

    private void validateId(Long id) {
        if (id == null || id < 1) {
            throw new IllegalArgumentException("id must be positive");
        }
    }

    private void validateServiceName(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            throw new IllegalArgumentException("serviceName must not be blank");
        }
        if (serviceName.length() > MAX_SERVICE_NAME_LENGTH) {
            throw new IllegalArgumentException("serviceName length must not exceed " + MAX_SERVICE_NAME_LENGTH);
        }
    }

    private void validatePage(int pageNo, int pageSize, MicroserviceRegistryPageReq req) {
        if (pageNo < DEFAULT_PAGE_NO) {
            throw new IllegalArgumentException("pageNo must be at least 1");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize must be between 1 and 100");
        }
        if (req == null) {
            return;
        }
        if (req.getServiceName() != null) {
            validateServiceName(req.getServiceName());
        }
        if (req.getServiceType() != null) {
            validateServiceType(req.getServiceType());
        }
        if (req.getSubsystemId() != null
                && (req.getSubsystemId() < MIN_SUBSYSTEM_ID || req.getSubsystemId() > MAX_SUBSYSTEM_ID)) {
            throw new IllegalArgumentException("subsystemId must be between 1 and 11");
        }
    }

    private void validateServiceType(String serviceType) {
        if (serviceType.isBlank() || !VALID_SERVICE_TYPES.contains(serviceType)) {
            throw new IllegalArgumentException("serviceType must be one of BUSINESS, COMMON, GATEWAY");
        }
    }

    private LambdaQueryWrapper<MicroserviceRegistryDO> buildWrapper(MicroserviceRegistryPageReq req) {
        LambdaQueryWrapper<MicroserviceRegistryDO> wrapper = new LambdaQueryWrapper<>();
        if (req == null) {
            return wrapper;
        }
        if (req.getServiceName() != null && !req.getServiceName().isBlank()) {
            wrapper.eq(MicroserviceRegistryDO::getServiceName, req.getServiceName());
        }
        if (req.getServiceType() != null && !req.getServiceType().isBlank()) {
            wrapper.eq(MicroserviceRegistryDO::getServiceType, req.getServiceType());
        }
        if (req.getSubsystemId() != null) {
            wrapper.eq(MicroserviceRegistryDO::getSubsystemId, req.getSubsystemId());
        }
        if (req.getIsRequired() != null) {
            wrapper.eq(MicroserviceRegistryDO::getIsRequired, req.getIsRequired());
        }
        return wrapper;
    }
}
