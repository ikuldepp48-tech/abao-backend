package com.geihou.module.system.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geihou.module.system.dal.dataobject.tenant.TenantDO;
import com.geihou.module.system.dal.mysql.tenant.TenantRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GeihouTenantCodeResolverAdapterTest {

    private final TenantRepository repository = mock(TenantRepository.class);
    private final GeihouTenantCodeResolverAdapter adapter = new GeihouTenantCodeResolverAdapter(repository);

    @Test
    void shouldResolveActiveTenantCodeToTenantId() {
        TenantDO tenant = new TenantDO();
        tenant.setId(8L);
        when(repository.selectActiveByTenantCode("abao")).thenReturn(tenant);

        Optional<Long> tenantId = adapter.resolveTenantId(" abao ");

        assertThat(tenantId).contains(8L);
        verify(repository).selectActiveByTenantCode("abao");
    }

    @Test
    void shouldReturnEmptyForBlankMissingOrInvalidTenant() {
        assertThat(adapter.resolveTenantId(" ")).isEmpty();

        when(repository.selectActiveByTenantCode("missing")).thenReturn(null);
        assertThat(adapter.resolveTenantId("missing")).isEmpty();

        TenantDO invalid = new TenantDO();
        invalid.setId(0L);
        when(repository.selectActiveByTenantCode("invalid")).thenReturn(invalid);
        assertThat(adapter.resolveTenantId("invalid")).isEmpty();
    }
}
