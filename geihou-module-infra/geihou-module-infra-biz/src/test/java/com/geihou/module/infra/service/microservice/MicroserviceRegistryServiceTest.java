package com.geihou.module.infra.service.microservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.geihou.common.pojo.PageResult;
import com.geihou.module.infra.dal.dataobject.microservice.MicroserviceRegistryDO;
import com.geihou.module.infra.dal.mysql.microservice.MicroserviceRegistryRepository;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MicroserviceRegistryServiceTest {

    @Mock
    private MicroserviceRegistryRepository repository;

    private MicroserviceRegistryService service;

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(MicroserviceRegistryDO.class) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                    new MybatisConfiguration(), MicroserviceRegistryDO.class.getName());
            TableInfoHelper.initTableInfo(assistant, MicroserviceRegistryDO.class);
        }
    }

    @BeforeEach
    void setUp() {
        service = new MicroserviceRegistryService(repository);
    }

    @Test
    void getByIdValidatesAndDelegates() {
        MicroserviceRegistryDO entity = newRegistry("geihou-module-gateway");
        when(repository.selectById(1L)).thenReturn(entity);

        assertThat(service.getById(1L)).isSameAs(entity);
        verify(repository).selectById(1L);
    }

    @Test
    void getByIdRejectsInvalidId() {
        for (Long id : new Long[] {null, 0L, -1L}) {
            assertThatThrownBy(() -> service.getById(id))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("id");
        }
    }

    @Test
    void getByServiceNameValidatesAndDelegates() {
        MicroserviceRegistryDO entity = newRegistry("geihou-module-system");
        when(repository.selectByServiceName("geihou-module-system")).thenReturn(entity);

        assertThat(service.getByServiceName("geihou-module-system")).isSameAs(entity);
        verify(repository).selectByServiceName("geihou-module-system");
    }

    @Test
    void getByServiceNameRejectsInvalidName() {
        for (String serviceName : new String[] {null, "", "   ", "a".repeat(65)}) {
            assertThatThrownBy(() -> service.getByServiceName(serviceName))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("serviceName");
        }
    }

    @Test
    void pageUsesDefaultsWhenRequestIsNull() {
        PageResult<MicroserviceRegistryDO> pageResult = PageResult.empty(1, 20);
        when(repository.selectPage(eq(1), eq(20), any())).thenReturn(pageResult);

        PageResult<MicroserviceRegistryDO> result = service.page(null);

        assertThat(result).isSameAs(pageResult);
        verify(repository).selectPage(eq(1), eq(20), any());
    }

    @Test
    void pageRejectsInvalidPageNo() {
        MicroserviceRegistryPageReq req = new MicroserviceRegistryPageReq();
        req.setPageNo(0);

        assertThatThrownBy(() -> service.page(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pageNo");
    }

    @Test
    void pageRejectsInvalidPageSize() {
        for (Integer pageSize : new Integer[] {0, 101}) {
            MicroserviceRegistryPageReq req = new MicroserviceRegistryPageReq();
            req.setPageSize(pageSize);

            assertThatThrownBy(() -> service.page(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("pageSize");
        }
    }

    @Test
    void pageRejectsInvalidServiceType() {
        for (String serviceType : new String[] {"", "   ", "REST", "JOB"}) {
            MicroserviceRegistryPageReq req = new MicroserviceRegistryPageReq();
            req.setServiceType(serviceType);

            assertThatThrownBy(() -> service.page(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("serviceType");
        }
    }

    @Test
    void pageRejectsInvalidSubsystemId() {
        for (Integer subsystemId : new Integer[] {0, 12}) {
            MicroserviceRegistryPageReq req = new MicroserviceRegistryPageReq();
            req.setSubsystemId(subsystemId);

            assertThatThrownBy(() -> service.page(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("subsystemId");
        }
    }

    @Test
    void pageAppliesFilters() {
        MicroserviceRegistryPageReq req = new MicroserviceRegistryPageReq();
        req.setPageNo(2);
        req.setPageSize(50);
        req.setServiceName("geihou-module-finance");
        req.setServiceType("BUSINESS");
        req.setSubsystemId(1);
        req.setIsRequired(true);
        when(repository.selectPage(eq(2), eq(50), any())).thenReturn(PageResult.empty(2, 50));

        PageResult<MicroserviceRegistryDO> result = service.page(req);

        assertThat(result.getPageNo()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(50);
        LambdaQueryWrapper<MicroserviceRegistryDO> wrapper = captureWrapper();
        assertThat(wrapper.getSqlSegment())
                .contains("service_name", "service_type", "subsystem_id", "is_required");
    }

    @Test
    void pageAcceptsAllValidServiceTypes() {
        for (String serviceType : new String[] {"BUSINESS", "COMMON", "GATEWAY"}) {
            MicroserviceRegistryPageReq req = new MicroserviceRegistryPageReq();
            req.setServiceType(serviceType);
            when(repository.selectPage(eq(1), eq(20), any())).thenReturn(PageResult.empty(1, 20));

            service.page(req);
        }
        verify(repository, times(3)).selectPage(eq(1), eq(20), any());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private LambdaQueryWrapper<MicroserviceRegistryDO> captureWrapper() {
        ArgumentCaptor<LambdaQueryWrapper> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(repository).selectPage(any(), any(), captor.capture());
        return captor.getValue();
    }

    private static MicroserviceRegistryDO newRegistry(String serviceName) {
        MicroserviceRegistryDO entity = new MicroserviceRegistryDO();
        entity.setServiceName(serviceName);
        entity.setServicePort(48080);
        entity.setServiceType("COMMON");
        entity.setHealthCheckUrl("/actuator/health");
        entity.setStartupPriority(10);
        entity.setIsRequired(true);
        entity.setDeleted(false);
        return entity;
    }
}
