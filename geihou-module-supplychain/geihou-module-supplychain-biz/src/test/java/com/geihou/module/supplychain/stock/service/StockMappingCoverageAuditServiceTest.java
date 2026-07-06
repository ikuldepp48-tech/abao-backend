package com.geihou.module.supplychain.stock.service;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.supplychain.api.stock.dto.StockCoverageDecisionRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockCoverageObserveReqDTO;
import com.geihou.module.supplychain.api.stock.enums.StockCoverageModeEnum;
import com.geihou.module.supplychain.api.stock.enums.StockCoverageTypeEnum;
import com.geihou.module.supplychain.stock.SupplychainTestConfig;
import com.geihou.module.supplychain.stock.SupplychainTestSchemaInitializer;
import com.geihou.module.supplychain.stock.dal.dataobject.StockMappingCoverageAuditDO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = SupplychainTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:stock_mapping_coverage_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class StockMappingCoverageAuditServiceTest {

    @Autowired
    private StockMappingCoverageAuditService service;
    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        SupplychainTestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void observeMissingMapping_insertsAuditOnlyRow() {
        StockCoverageDecisionRespDTO decision = service.observeMissingMapping(req(1L, "checkout-1-sku-1001"));

        assertThat(decision.getMode()).isEqualTo(StockCoverageModeEnum.AUDIT_ONLY);
        assertThat(decision.isEnforce()).isFalse();

        List<StockMappingCoverageAuditDO> rows = service.listRecent(1L, 10);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getCoverageType()).isEqualTo("STOCK_ITEM_MISSING");
        assertThat(rows.get(0).getSeenCount()).isEqualTo(1);
    }

    @Test
    void observeMissingMapping_sameLogicalObservationIncrementsSeenCount() {
        service.observeMissingMapping(req(1L, "checkout-1-sku-1001"));
        service.observeMissingMapping(req(1L, "checkout-1-sku-1001"));

        List<StockMappingCoverageAuditDO> rows = service.listRecent(1L, 10);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getSeenCount()).isEqualTo(2);
    }

    @Test
    void listRecent_isTenantScoped() {
        TenantContextHolder.setTenantId(1L);
        service.observeMissingMapping(req(1L, "checkout-1-sku-1001"));
        TenantContextHolder.setTenantId(2L);
        service.observeMissingMapping(req(2L, "checkout-2-sku-1001"));

        TenantContextHolder.setTenantId(1L);
        assertThat(service.listRecent(1L, 10)).hasSize(1);
        TenantContextHolder.setTenantId(2L);
        assertThat(service.listRecent(2L, 10)).hasSize(1);
        TenantContextHolder.setTenantId(999L);
        assertThat(service.listRecent(999L, 10)).isEmpty();
    }

    private StockCoverageObserveReqDTO req(Long tenantId, String key) {
        StockCoverageObserveReqDTO req = new StockCoverageObserveReqDTO();
        req.setTenantId(tenantId);
        req.setCoverageType(StockCoverageTypeEnum.STOCK_ITEM_MISSING);
        req.setSkuId(1001L);
        req.setSkuCode("SKU_BEEF");
        req.setStoreId(1L);
        req.setLocationType("STORE");
        req.setSourceModule("checkout");
        req.setSourceRecordId(1L);
        req.setIdempotentKey(key);
        return req;
    }
}
