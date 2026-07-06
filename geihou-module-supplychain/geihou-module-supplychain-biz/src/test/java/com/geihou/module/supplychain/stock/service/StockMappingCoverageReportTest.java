package com.geihou.module.supplychain.stock.service;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.supplychain.api.stock.dto.StockCoverageDecisionRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockCoverageDetailRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockCoverageReportRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockCoverageTypeSummaryRespDTO;
import com.geihou.module.supplychain.api.stock.enums.StockCoverageModeEnum;
import com.geihou.module.supplychain.api.stock.enums.StockCoverageTypeEnum;
import com.geihou.module.supplychain.stock.SupplychainTestConfig;
import com.geihou.module.supplychain.stock.SupplychainTestSchemaInitializer;
import com.geihou.module.supplychain.stock.dal.dataobject.StockItemDO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockLocationDO;
import com.geihou.module.supplychain.stock.dal.mapper.StockItemMapper;
import com.geihou.module.supplychain.stock.dal.mapper.StockLocationMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link StockMappingCoverageReportService} — report generation (G2-01B3C).
 *
 * <p>Covers AC-1 (report format), AC-2 (tenant scoping), AC-8 (read-only).
 */
@SpringBootTest(
        classes = SupplychainTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:stock_coverage_report_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class StockMappingCoverageReportTest {

    @Autowired
    private StockMappingCoverageReportService reportService;
    @Autowired
    private StockMappingCoverageAuditService auditService;
    @Autowired
    private StockItemMapper stockItemMapper;
    @Autowired
    private StockLocationMapper stockLocationMapper;
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

    // --- AC-1: Report format ---

    @Test
    void generateReport_returnsAllRequiredFields() {
        // Insert some audit observations
        auditService.observeMissingMapping(observeReq(1L, StockCoverageTypeEnum.STOCK_ITEM_MISSING, "SKU_A", 1L, "STORE", "key-1"));
        auditService.observeMissingMapping(observeReq(1L, StockCoverageTypeEnum.LOCATION_MISSING, "SKU_B", 2L, "WAREHOUSE", "key-2"));
        auditService.observeMissingMapping(observeReq(1L, StockCoverageTypeEnum.SKU_CODE_MISSING, null, 3L, "STORE", "key-3"));

        StockCoverageReportRespDTO report = reportService.generateReport(1L);

        assertThat(report.getTenantId()).isEqualTo(1L);
        assertThat(report.getGeneratedTime()).isNotNull();
        assertThat(report.getMode()).isEqualTo(StockCoverageModeEnum.AUDIT_ONLY);
        assertThat(report.getTotalObservations()).isEqualTo(3);
        assertThat(report.getTotalUnresolved()).isEqualTo(3);

        // Per-type summaries
        Map<String, StockCoverageTypeSummaryRespDTO> byType = report.getTypeSummaries().stream()
                .collect(Collectors.toMap(StockCoverageTypeSummaryRespDTO::getCoverageType, s -> s));
        assertThat(byType).containsKeys(
                StockCoverageTypeEnum.STOCK_ITEM_MISSING.getCode(),
                StockCoverageTypeEnum.LOCATION_MISSING.getCode(),
                StockCoverageTypeEnum.SKU_CODE_MISSING.getCode());
        assertThat(byType.get(StockCoverageTypeEnum.STOCK_ITEM_MISSING.getCode()).getTotal()).isEqualTo(1);
        assertThat(byType.get(StockCoverageTypeEnum.LOCATION_MISSING.getCode()).getTotal()).isEqualTo(1);
        assertThat(byType.get(StockCoverageTypeEnum.SKU_CODE_MISSING.getCode()).getTotal()).isEqualTo(1);

        // Capped unresolved details
        assertThat(report.getUnresolvedDetails()).hasSize(3);
        StockCoverageDetailRespDTO detail = report.getUnresolvedDetails().get(0);
        assertThat(detail.getCoverageType()).isNotNull();
        assertThat(detail.getSourceModule()).isEqualTo("checkout");
        assertThat(detail.getSeenCount()).isNotNull();
    }

    @Test
    void generateReport_resolvedObservationsAreExcludedFromUnresolved() {
        // Insert STOCK_ITEM_MISSING observation
        auditService.observeMissingMapping(observeReq(1L, StockCoverageTypeEnum.STOCK_ITEM_MISSING, "SKU_RESOLVED", 1L, "STORE", "key-r1"));

        // Now create the matching stock_item mapping (active, not deleted)
        StockItemDO item = new StockItemDO();
        item.setTenantId(1L);
        item.setSkuCode("SKU_RESOLVED");
        item.setItemName("Resolved Item");
        item.setUnit("KG");
        item.setIsActive(true);
        item.setCreator("test");
        item.setCreateTime(LocalDateTime.now());
        item.setUpdater("test");
        item.setUpdateTime(LocalDateTime.now());
        item.setDeleted(false);
        stockItemMapper.insert(item);

        StockCoverageReportRespDTO report = reportService.generateReport(1L);

        assertThat(report.getTotalObservations()).isEqualTo(1);
        assertThat(report.getTotalUnresolved()).isEqualTo(0);
        assertThat(report.getUnresolvedDetails()).isEmpty();
    }

    @Test
    void generateReport_resolvedLocationMissingExcludedFromUnresolved() {
        auditService.observeMissingMapping(observeReq(1L, StockCoverageTypeEnum.LOCATION_MISSING, "SKU_X", 5L, "STORE", "key-loc-1"));

        // Create matching stock_location (active)
        StockLocationDO loc = new StockLocationDO();
        loc.setTenantId(1L);
        loc.setLocationCode("LOC_5_STORE");
        loc.setLocationName("Store 5");
        loc.setLocationType("STORE");
        loc.setStoreId(5L);
        loc.setIsActive(true);
        loc.setCreator("test");
        loc.setCreateTime(LocalDateTime.now());
        loc.setUpdater("test");
        loc.setUpdateTime(LocalDateTime.now());
        loc.setDeleted(false);
        stockLocationMapper.insert(loc);

        StockCoverageReportRespDTO report = reportService.generateReport(1L);
        assertThat(report.getTotalObservations()).isEqualTo(1);
        assertThat(report.getTotalUnresolved()).isEqualTo(0);
    }

    @Test
    void generateReport_inactiveMappingCountsAsUnresolved() {
        auditService.observeMissingMapping(observeReq(1L, StockCoverageTypeEnum.STOCK_ITEM_MISSING, "SKU_INACTIVE", 1L, "STORE", "key-inact"));

        // Create matching stock_item but inactive
        StockItemDO item = new StockItemDO();
        item.setTenantId(1L);
        item.setSkuCode("SKU_INACTIVE");
        item.setItemName("Inactive Item");
        item.setUnit("KG");
        item.setIsActive(false);
        item.setCreator("test");
        item.setCreateTime(LocalDateTime.now());
        item.setUpdater("test");
        item.setUpdateTime(LocalDateTime.now());
        item.setDeleted(false);
        stockItemMapper.insert(item);

        StockCoverageReportRespDTO report = reportService.generateReport(1L);
        assertThat(report.getTotalUnresolved()).isEqualTo(1);
    }

    @Test
    void generateReport_emptyTenantReturnsZeroCounts() {
        TenantContextHolder.setTenantId(999L);
        StockCoverageReportRespDTO report = reportService.generateReport(999L);

        assertThat(report.getTotalObservations()).isEqualTo(0);
        assertThat(report.getTotalUnresolved()).isEqualTo(0);
        assertThat(report.getUnresolvedDetails()).isEmpty();
        assertThat(report.getTypeSummaries()).hasSize(3); // all types with zero counts
    }

    // --- AC-2: Tenant scoping ---

    @Test
    void generateReport_isTenantScoped() {
        TenantContextHolder.setTenantId(1L);
        auditService.observeMissingMapping(observeReq(1L, StockCoverageTypeEnum.STOCK_ITEM_MISSING, "SKU_T1", 1L, "STORE", "key-t1"));

        TenantContextHolder.setTenantId(2L);
        auditService.observeMissingMapping(observeReq(2L, StockCoverageTypeEnum.LOCATION_MISSING, "SKU_T2", 2L, "WAREHOUSE", "key-t2"));

        // Tenant 1 report should only see tenant 1 observations
        TenantContextHolder.setTenantId(1L);
        StockCoverageReportRespDTO report1 = reportService.generateReport(1L);
        assertThat(report1.getTotalObservations()).isEqualTo(1);
        assertThat(report1.getUnresolvedDetails()).allSatisfy(d ->
                assertThat(d.getSkuCode()).isEqualTo("SKU_T1"));

        // Tenant 2 report should only see tenant 2 observations
        TenantContextHolder.setTenantId(2L);
        StockCoverageReportRespDTO report2 = reportService.generateReport(2L);
        assertThat(report2.getTotalObservations()).isEqualTo(1);
        assertThat(report2.getUnresolvedDetails()).allSatisfy(d ->
                assertThat(d.getSkuCode()).isEqualTo("SKU_T2"));

        // Tenant 999 sees nothing
        TenantContextHolder.setTenantId(999L);
        StockCoverageReportRespDTO report999 = reportService.generateReport(999L);
        assertThat(report999.getTotalObservations()).isEqualTo(0);
    }

    // --- AC-8: Read-only ---

    @Test
    void generateReport_doesNotInsertOrUpdateAuditRows() {
        auditService.observeMissingMapping(observeReq(1L, StockCoverageTypeEnum.STOCK_ITEM_MISSING, "SKU_RO", 1L, "STORE", "key-ro"));

        StockCoverageReportRespDTO before = reportService.generateReport(1L);
        int observationsBefore = before.getTotalObservations();

        // Call report multiple times
        reportService.generateReport(1L);
        reportService.generateReport(1L);

        StockCoverageReportRespDTO after = reportService.generateReport(1L);
        assertThat(after.getTotalObservations()).isEqualTo(observationsBefore);
    }

    // --- Helper ---

    private com.geihou.module.supplychain.api.stock.dto.StockCoverageObserveReqDTO observeReq(
            Long tenantId, StockCoverageTypeEnum type, String skuCode, Long storeId, String locationType, String key) {
        var req = new com.geihou.module.supplychain.api.stock.dto.StockCoverageObserveReqDTO();
        req.setTenantId(tenantId);
        req.setCoverageType(type);
        req.setSkuCode(skuCode);
        req.setSkuId(skuCode != null ? 100L : null);
        req.setStoreId(storeId);
        req.setLocationType(locationType);
        req.setSourceModule("checkout");
        req.setSourceRecordId(1L);
        req.setIdempotentKey(key);
        return req;
    }
}
