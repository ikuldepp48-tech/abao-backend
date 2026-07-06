package com.geihou.module.infra.controller.admin.microservice;

import com.geihou.common.pojo.CommonResult;
import com.geihou.common.pojo.PageResult;
import com.geihou.module.infra.controller.admin.microservice.vo.MicroserviceRegistryPageReqVO;
import com.geihou.module.infra.controller.admin.microservice.vo.MicroserviceRegistryRespVO;
import com.geihou.module.infra.dal.dataobject.microservice.MicroserviceRegistryDO;
import com.geihou.module.infra.service.microservice.MicroserviceRegistryPageReq;
import com.geihou.module.infra.service.microservice.MicroserviceRegistryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin-api/infra/microservices")
public class MicroserviceRegistryController {

    private final MicroserviceRegistryService microserviceRegistryService;

    public MicroserviceRegistryController(MicroserviceRegistryService microserviceRegistryService) {
        this.microserviceRegistryService = microserviceRegistryService;
    }

    @GetMapping
    public CommonResult<PageResult<MicroserviceRegistryRespVO>> page(MicroserviceRegistryPageReqVO reqVO) {
        PageResult<MicroserviceRegistryDO> pageResult = microserviceRegistryService.page(toServiceReq(reqVO));
        return CommonResult.success(toRespPage(pageResult));
    }

    private MicroserviceRegistryPageReq toServiceReq(MicroserviceRegistryPageReqVO reqVO) {
        if (reqVO == null) {
            return null;
        }
        MicroserviceRegistryPageReq req = new MicroserviceRegistryPageReq();
        req.setPageNo(reqVO.getPageNo());
        req.setPageSize(reqVO.getPageSize());
        req.setServiceName(reqVO.getServiceName());
        req.setServiceType(reqVO.getServiceType());
        req.setSubsystemId(reqVO.getSubsystemId());
        req.setIsRequired(reqVO.getIsRequired());
        return req;
    }

    private PageResult<MicroserviceRegistryRespVO> toRespPage(PageResult<MicroserviceRegistryDO> pageResult) {
        List<MicroserviceRegistryRespVO> list = pageResult.getList().stream()
                .map(this::toRespVO)
                .toList();
        return PageResult.of(list, pageResult.getTotal(), pageResult.getPageNo(), pageResult.getPageSize());
    }

    private MicroserviceRegistryRespVO toRespVO(MicroserviceRegistryDO entity) {
        MicroserviceRegistryRespVO vo = new MicroserviceRegistryRespVO();
        vo.setId(entity.getId());
        vo.setServiceName(entity.getServiceName());
        vo.setServicePort(entity.getServicePort());
        vo.setSubsystemId(entity.getSubsystemId());
        vo.setServiceType(entity.getServiceType());
        vo.setHealthCheckUrl(entity.getHealthCheckUrl());
        vo.setStartupPriority(entity.getStartupPriority());
        vo.setIsRequired(entity.getIsRequired());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }
}
