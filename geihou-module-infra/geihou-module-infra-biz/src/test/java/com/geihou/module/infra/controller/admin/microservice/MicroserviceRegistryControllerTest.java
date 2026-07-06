package com.geihou.module.infra.controller.admin.microservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geihou.common.pojo.CommonResult;
import com.geihou.common.pojo.PageResult;
import com.geihou.module.infra.controller.admin.microservice.vo.MicroserviceRegistryPageReqVO;
import com.geihou.module.infra.controller.admin.microservice.vo.MicroserviceRegistryRespVO;
import com.geihou.module.infra.dal.dataobject.microservice.MicroserviceRegistryDO;
import com.geihou.module.infra.service.microservice.MicroserviceRegistryPageReq;
import com.geihou.module.infra.service.microservice.MicroserviceRegistryService;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MicroserviceRegistryControllerTest {

    @Mock
    private MicroserviceRegistryService microserviceRegistryService;

    private MicroserviceRegistryController controller;

    @BeforeEach
    void setUp() {
        controller = new MicroserviceRegistryController(microserviceRegistryService);
    }

    @Test
    void pagePassesNullRequestToServiceAndReturnsSuccessEnvelope() {
        when(microserviceRegistryService.page(null)).thenReturn(PageResult.empty(1, 20));

        CommonResult<PageResult<MicroserviceRegistryRespVO>> result = controller.page(null);

        assertThat(result.getCode()).isEqualTo(CommonResult.SUCCESS_CODE);
        assertThat(result.getMsg()).isEqualTo(CommonResult.SUCCESS_MSG);
        assertThat(result.getData().getPageNo()).isEqualTo(1);
        assertThat(result.getData().getPageSize()).isEqualTo(20);
        assertThat(result.getData().getTotal()).isZero();
        assertThat(result.getData().getList()).isEmpty();
        verify(microserviceRegistryService).page(null);
    }

    @Test
    void pageMapsAllQueryParamsToServiceRequest() {
        MicroserviceRegistryPageReqVO reqVO = new MicroserviceRegistryPageReqVO();
        reqVO.setPageNo(2);
        reqVO.setPageSize(50);
        reqVO.setServiceName("geihou-module-infra");
        reqVO.setServiceType("COMMON");
        reqVO.setSubsystemId(3);
        reqVO.setIsRequired(true);
        when(microserviceRegistryService.page(any())).thenReturn(PageResult.empty(2, 50));

        controller.page(reqVO);

        ArgumentCaptor<MicroserviceRegistryPageReq> captor =
                ArgumentCaptor.forClass(MicroserviceRegistryPageReq.class);
        verify(microserviceRegistryService).page(captor.capture());
        MicroserviceRegistryPageReq captured = captor.getValue();
        assertThat(captured.getPageNo()).isEqualTo(2);
        assertThat(captured.getPageSize()).isEqualTo(50);
        assertThat(captured.getServiceName()).isEqualTo("geihou-module-infra");
        assertThat(captured.getServiceType()).isEqualTo("COMMON");
        assertThat(captured.getSubsystemId()).isEqualTo(3);
        assertThat(captured.getIsRequired()).isTrue();
    }

    @Test
    void pageMapsDoToResponseVoAndKeepsPageMetadata() {
        LocalDateTime createTime = LocalDateTime.of(2026, 6, 12, 9, 30);
        LocalDateTime updateTime = LocalDateTime.of(2026, 6, 12, 10, 45);
        MicroserviceRegistryDO entity = new MicroserviceRegistryDO();
        entity.setId(7L);
        entity.setServiceName("geihou-module-system");
        entity.setServicePort(48081);
        entity.setSubsystemId(2);
        entity.setServiceType("BUSINESS");
        entity.setHealthCheckUrl("/actuator/ready");
        entity.setStartupPriority(5);
        entity.setIsRequired(false);
        entity.setCreator("hidden-creator");
        entity.setUpdater("hidden-updater");
        entity.setDeleted(false);
        entity.setCreateTime(createTime);
        entity.setUpdateTime(updateTime);
        when(microserviceRegistryService.page(any()))
                .thenReturn(PageResult.of(List.of(entity), 1L, 3, 20));

        CommonResult<PageResult<MicroserviceRegistryRespVO>> result =
                controller.page(new MicroserviceRegistryPageReqVO());

        assertThat(result.getCode()).isEqualTo(CommonResult.SUCCESS_CODE);
        PageResult<MicroserviceRegistryRespVO> page = result.getData();
        assertThat(page.getTotal()).isEqualTo(1L);
        assertThat(page.getPageNo()).isEqualTo(3);
        assertThat(page.getPageSize()).isEqualTo(20);
        assertThat(page.getList()).hasSize(1);
        MicroserviceRegistryRespVO row = page.getList().get(0);
        assertThat(row.getId()).isEqualTo(7L);
        assertThat(row.getServiceName()).isEqualTo("geihou-module-system");
        assertThat(row.getServicePort()).isEqualTo(48081);
        assertThat(row.getSubsystemId()).isEqualTo(2);
        assertThat(row.getServiceType()).isEqualTo("BUSINESS");
        assertThat(row.getHealthCheckUrl()).isEqualTo("/actuator/ready");
        assertThat(row.getStartupPriority()).isEqualTo(5);
        assertThat(row.getIsRequired()).isFalse();
        assertThat(row.getCreateTime()).isEqualTo(createTime);
        assertThat(row.getUpdateTime()).isEqualTo(updateTime);
    }

    @Test
    void responseVoContainsOnlyAcceptedFields() {
        assertThat(Arrays.stream(MicroserviceRegistryRespVO.class.getDeclaredFields())
                .map(Field::getName)
                .toList())
                .containsExactlyInAnyOrder(
                        "id",
                        "serviceName",
                        "servicePort",
                        "subsystemId",
                        "serviceType",
                        "healthCheckUrl",
                        "startupPriority",
                        "isRequired",
                        "createTime",
                        "updateTime");
    }

    @Test
    void pageDoesNotSwallowServiceValidationExceptions() {
        MicroserviceRegistryPageReqVO reqVO = new MicroserviceRegistryPageReqVO();
        reqVO.setServiceType("REST");
        when(microserviceRegistryService.page(any()))
                .thenThrow(new IllegalArgumentException("serviceType must be one of BUSINESS, COMMON, GATEWAY"));

        assertThatThrownBy(() -> controller.page(reqVO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("serviceType");
    }
}
