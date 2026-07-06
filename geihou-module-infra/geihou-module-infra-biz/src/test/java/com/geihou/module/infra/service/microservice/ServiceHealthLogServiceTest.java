package com.geihou.module.infra.service.microservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.geihou.common.pojo.PageResult;
import com.geihou.module.infra.dal.dataobject.microservice.ServiceHealthLogDO;
import com.geihou.module.infra.dal.mysql.microservice.ServiceHealthLogRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServiceHealthLogServiceTest {

    @Mock
    private ServiceHealthLogRepository repository;

    private ServiceHealthLogService service;

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(ServiceHealthLogDO.class) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                    new MybatisConfiguration(), ServiceHealthLogDO.class.getName());
            TableInfoHelper.initTableInfo(assistant, ServiceHealthLogDO.class);
        }
    }

    @BeforeEach
    void setUp() {
        service = new ServiceHealthLogService(repository);
    }

    @Test
    void recordInsertsValidEntityAndReturnsIt() {
        ServiceHealthLogDO entity = validEntity();
        when(repository.insert(entity)).thenReturn(1);

        ServiceHealthLogDO result = service.record(entity);

        assertThat(result).isSameAs(entity);
        verify(repository).insert(entity);
    }

    @Test
    void recordRejectsNullEntity() {
        assertThatThrownBy(() -> service.record(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entity");
        verify(repository, never()).insert(any());
    }

    @Test
    void recordRejectsInvalidServiceName() {
        for (String serviceName : new String[] {null, "", "   ", "a".repeat(65)}) {
            ServiceHealthLogDO entity = validEntity();
            entity.setServiceName(serviceName);

            assertThatThrownBy(() -> service.record(entity))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("serviceName");
        }
        verify(repository, never()).insert(any());
    }

    @Test
    void recordRejectsInvalidCheckTime() {
        ServiceHealthLogDO withoutCheckTime = validEntity();
        withoutCheckTime.setCheckTime(null);
        assertThatThrownBy(() -> service.record(withoutCheckTime))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("checkTime");

        ServiceHealthLogDO futureCheckTime = validEntity();
        futureCheckTime.setCheckTime(LocalDateTime.now().plusDays(1));
        assertThatThrownBy(() -> service.record(futureCheckTime))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("checkTime");

        verify(repository, never()).insert(any());
    }

    @Test
    void recordRejectsInvalidStatus() {
        for (String status : new String[] {null, "", "   ", "UNKNOWN"}) {
            ServiceHealthLogDO entity = validEntity();
            entity.setStatus(status);

            assertThatThrownBy(() -> service.record(entity))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("status");
        }
        verify(repository, never()).insert(any());
    }

    @Test
    void recordAcceptsAllValidStatuses() {
        for (String status : List.of("UP", "DOWN", "TIMEOUT", "DEGRADED")) {
            ServiceHealthLogDO entity = validEntity();
            entity.setStatus(status);
            when(repository.insert(entity)).thenReturn(1);

            service.record(entity);

            verify(repository).insert(entity);
        }
    }

    @Test
    void recordRejectsNegativeResponseTimeMs() {
        ServiceHealthLogDO entity = validEntity();
        entity.setResponseTimeMs(-1);

        assertThatThrownBy(() -> service.record(entity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("responseTimeMs");
        verify(repository, never()).insert(any());
    }

    @Test
    void recordAcceptsNullResponseTimeMs() {
        ServiceHealthLogDO entity = validEntity();
        entity.setResponseTimeMs(null);
        when(repository.insert(entity)).thenReturn(1);

        ServiceHealthLogDO result = service.record(entity);

        assertThat(result.getResponseTimeMs()).isNull();
        verify(repository).insert(entity);
    }

    @Test
    void recordSetsCreateTimeWhenNull() {
        ServiceHealthLogDO entity = validEntity();
        entity.setCreateTime(null);
        when(repository.insert(entity)).thenReturn(1);

        ServiceHealthLogDO result = service.record(entity);

        assertThat(result.getCreateTime()).isNotNull();
        verify(repository).insert(entity);
    }

    @Test
    void recordPreservesExistingCreateTime() {
        LocalDateTime existingCreateTime = LocalDateTime.parse("2026-06-10T12:00:00");
        ServiceHealthLogDO entity = validEntity();
        entity.setCreateTime(existingCreateTime);
        when(repository.insert(entity)).thenReturn(1);

        service.record(entity);

        assertThat(entity.getCreateTime()).isEqualTo(existingCreateTime);
        verify(repository).insert(entity);
    }

    @Test
    void getByIdReturnsEntityOrNullThroughRepository() {
        ServiceHealthLogDO entity = validEntity();
        entity.setId(1L);
        when(repository.selectById(1L)).thenReturn(entity);
        when(repository.selectById(2L)).thenReturn(null);

        assertThat(service.getById(1L)).isSameAs(entity);
        assertThat(service.getById(2L)).isNull();
    }

    @Test
    void pageUsesDefaultsWhenRequestIsNull() {
        PageResult<ServiceHealthLogDO> pageResult = PageResult.empty(1, 20);
        when(repository.selectPage(eq(1), eq(20), any())).thenReturn(pageResult);

        PageResult<ServiceHealthLogDO> result = service.page(null);

        assertThat(result).isSameAs(pageResult);
        verify(repository).selectPage(eq(1), eq(20), any());
    }

    @Test
    void pageUsesDefaultsWhenPageNoAndPageSizeAreNull() {
        ServiceHealthLogPageReq req = new ServiceHealthLogPageReq();
        PageResult<ServiceHealthLogDO> pageResult = PageResult.empty(1, 20);
        when(repository.selectPage(eq(1), eq(20), any())).thenReturn(pageResult);

        PageResult<ServiceHealthLogDO> result = service.page(req);

        assertThat(result.getPageNo()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(20);
    }

    @Test
    void pageRejectsInvalidPageNo() {
        ServiceHealthLogPageReq req = new ServiceHealthLogPageReq();
        req.setPageNo(0);

        assertThatThrownBy(() -> service.page(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pageNo");
    }

    @Test
    void pageRejectsInvalidPageSize() {
        for (Integer pageSize : List.of(0, 101)) {
            ServiceHealthLogPageReq req = new ServiceHealthLogPageReq();
            req.setPageSize(pageSize);

            assertThatThrownBy(() -> service.page(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("pageSize");
        }
    }

    @Test
    void pageAppliesStatusFilter() {
        ServiceHealthLogPageReq req = new ServiceHealthLogPageReq();
        req.setStatus("UP");
        when(repository.selectPage(eq(1), eq(20), any())).thenReturn(PageResult.empty(1, 20));

        service.page(req);

        LambdaQueryWrapper<ServiceHealthLogDO> wrapper = captureWrapper();
        assertThat(wrapper.getSqlSegment()).contains("status");
    }

    @Test
    void pageRejectsInvalidStatusFilter() {
        ServiceHealthLogPageReq req = new ServiceHealthLogPageReq();
        req.setStatus("INVALID");

        assertThatThrownBy(() -> service.page(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("status");
    }

    @Test
    void pageRejectsCheckTimeStartAfterCheckTimeEnd() {
        ServiceHealthLogPageReq req = new ServiceHealthLogPageReq();
        req.setCheckTimeStart(LocalDateTime.parse("2026-06-12T10:00:00"));
        req.setCheckTimeEnd(LocalDateTime.parse("2026-06-12T08:00:00"));

        assertThatThrownBy(() -> service.page(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("checkTimeStart");
    }

    @Test
    void pageAppliesOptionalFilters() {
        ServiceHealthLogPageReq req = new ServiceHealthLogPageReq();
        req.setPageNo(2);
        req.setPageSize(50);
        req.setServiceName("geihou-module-gateway");
        req.setCheckTimeStart(LocalDateTime.parse("2026-06-12T00:00:00"));
        req.setCheckTimeEnd(LocalDateTime.parse("2026-06-12T23:59:59"));
        when(repository.selectPage(eq(2), eq(50), any())).thenReturn(PageResult.empty(2, 50));

        PageResult<ServiceHealthLogDO> result = service.page(req);

        assertThat(result.getPageNo()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(50);
        LambdaQueryWrapper<ServiceHealthLogDO> wrapper = captureWrapper();
        assertThat(wrapper.getSqlSegment()).contains("service_name", "check_time");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private LambdaQueryWrapper<ServiceHealthLogDO> captureWrapper() {
        ArgumentCaptor<LambdaQueryWrapper> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(repository).selectPage(any(), any(), captor.capture());
        return captor.getValue();
    }

    private static ServiceHealthLogDO validEntity() {
        ServiceHealthLogDO entity = new ServiceHealthLogDO();
        entity.setServiceName("geihou-module-gateway");
        entity.setCheckTime(LocalDateTime.parse("2026-06-12T08:00:00"));
        entity.setStatus("UP");
        entity.setResponseTimeMs(31);
        entity.setCreateTime(LocalDateTime.parse("2026-06-12T08:00:01"));
        return entity;
    }
}
