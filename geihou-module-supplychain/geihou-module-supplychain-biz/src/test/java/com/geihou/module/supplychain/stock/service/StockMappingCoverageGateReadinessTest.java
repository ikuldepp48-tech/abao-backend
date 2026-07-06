package com.geihou.module.supplychain.stock.service;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.supplychain.api.stock.dto.StockCoverageGateResultRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockCoverageObserveReqDTO;
import com.geihou.module.supplychain.api.stock.enums.StockCoverageGateStatusEnum;
import com.geihou.module.supplychain.api.stock.enums.StockCoverageModeEnum;
import com.geihou.module.supplychain.api.stock.enums.StockCoverageTypeEnum;
import com.geihou.module.supplychain.stock.SupplychainTestConfig;
import com.geihou.module.supplychain.stock.SupplychainTestSchemaInitializer;
import com.geihou.module.supplychain.stock.dal.dataobject.StockItemDO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockLocationDO;
import com.geihou.module.supplychain.stock.dal.mapper.StockItemMapper;
import com.geihou.module.supplychain.stock.dal.mapper.StockLocationMapper;
import com.geihou.module.supplychain.stock.dal.mapper.StockMappingCoverageAuditMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link StockMappingCoverageReportService#evaluateGate(Long)} — gate readiness (G2-01B3C).
 *
 * <p>Covers AC-3 through AC-7 (gate status logic), AC-8 (read-only), AC-9 (no forbidden scope).
 */
@SpringBootTest(
        classes = SupplychainTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:stock_coverage_gate_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
@TestPropertySource(properties = {
        "geihou.stock.mapping.coverage.mode=AUDIT_ONLY"
})
class StockMappingCoverageGateReadinessTest {

    @Autowired
    private StockMappingCoverageReportService reportService;
    @Autowired
    private StockMappingCoverageAuditService auditService;
    @Autowired
    private StockItemMapper stockItemMapper;
    @Autowired
    private StockLocationMapper stockLocationMapper;
    @Autowired
    private StockMappingCoverageAuditMapper auditMapper;
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

    // --- AC-3: AUDIT_ONLY with unresolved → READY_WITH_WARNINGS, never NOT_READY ---

    @Test
    void auditOnly_withUnresolved_returnsReadyWithWarnings() {
        auditService.observeMissingMapping(observeReq(StockCoverageTypeEnum.STOCK_ITEM_MISSING, "SKU_A", 1L, "STORE", "key-a3-1"));
        auditService.observeMissingMapping(observeReq(StockCoverageTypeEnum.LOCATION_MISSING, "SKU_B", 2L, "WAREHOUSE", "key-a3-2"));

        StockCoverageGateResultRespDTO gate = reportService.evaluateGate(1L);

        assertThat(gate.getStatus()).isEqualTo(StockCoverageGateStatusEnum.READY_WITH_WARNINGS);
        assertThat(gate.getMode()).isEqualTo(StockCoverageModeEnum.AUDIT_ONLY);
        assertThat(gate.getTotalUnresolved()).isEqualTo(2);
        assertThat(gate.getBlockReasons()).isEmpty();
    }

    // --- AC-4: AUDIT_ONLY with zero unresolved → READY ---

    @Test
    void auditOnly_withZeroUnresolved_returnsReady() {
        StockCoverageGateResultRespDTO gate = reportService.evaluateGate(1L);

        assertThat(gate.getStatus()).isEqualTo(StockCoverageGateStatusEnum.READY);
        assertThat(gate.getMode()).isEqualTo(StockCoverageModeEnum.AUDIT_ONLY);
        assertThat(gate.getTotalUnresolved()).isEqualTo(0);
        assertThat(gate.getBlockReasons()).isEmpty();
    }

    @Test
    void auditOnly_withResolvedObservations_returnsReady() {
        // Insert observation, then resolve it by creating the mapping
        auditService.observeMissingMapping(observeReq(StockCoverageTypeEnum.STOCK_ITEM_MISSING, "SKU_RESOLVED_A4", 1L, "STORE", "key-a4-1"));
        createStockItem("SKU_RESOLVED_A4");

        StockCoverageGateResultRespDTO gate = reportService.evaluateGate(1L);

        assertThat(gate.getStatus()).isEqualTo(StockCoverageGateStatusEnum.READY);
        assertThat(gate.getTotalUnresolved()).isEqualTo(0);
    }

    // --- AC-5: ENFORCE with unresolved LOCATION_MISSING or STOCK_ITEM_MISSING → NOT_READY ---

    @Test
    void enforce_withUnresolvedLocationMissing_returnsNotReady() {
        // Switch to ENFORCE mode by using a dedicated service instance
        StockMappingCoverageReportService enforceService = createEnforceService();

        auditService.observeMissingMapping(observeReq(StockCoverageTypeEnum.LOCATION_MISSING, "SKU_LOC", 3L, "STORE", "key-a5-1"));

        StockCoverageGateResultRespDTO gate = enforceService.evaluateGate(1L);

        assertThat(gate.getStatus()).isEqualTo(StockCoverageGateStatusEnum.NOT_READY);
        assertThat(gate.getMode()).isEqualTo(StockCoverageModeEnum.ENFORCE);
        assertThat(gate.getTotalUnresolved()).isEqualTo(1);
        assertThat(gate.getBlockReasons()).anyMatch(r -> r.contains("LOCATION_MISSING"));
    }

    @Test
    void enforce_withUnresolvedStockItemMissing_returnsNotReady() {
        StockMappingCoverageReportService enforceService = createEnforceService();

        auditService.observeMissingMapping(observeReq(StockCoverageTypeEnum.STOCK_ITEM_MISSING, "SKU_ITEM_ENF", 1L, "STORE", "key-a5-2"));

        StockCoverageGateResultRespDTO gate = enforceService.evaluateGate(1L);

        assertThat(gate.getStatus()).isEqualTo(StockCoverageGateStatusEnum.NOT_READY);
        assertThat(gate.getMode()).isEqualTo(StockCoverageModeEnum.ENFORCE);
        assertThat(gate.getBlockReasons()).anyMatch(r -> r.contains("STOCK_ITEM_MISSING"));
    }

    @Test
    void enforce_withBothLocationAndStockItemMissing_returnsNotReadyWithBothReasons() {
        StockMappingCoverageReportService enforceService = createEnforceService();

        auditService.observeMissingMapping(observeReq(StockCoverageTypeEnum.LOCATION_MISSING, "SKU_L", 3L, "STORE", "key-a5-3"));
        auditService.observeMissingMapping(observeReq(StockCoverageTypeEnum.STOCK_ITEM_MISSING, "SKU_I", 1L, "STORE", "key-a5-4"));

        StockCoverageGateResultRespDTO gate = enforceService.evaluateGate(1L);

        assertThat(gate.getStatus()).isEqualTo(StockCoverageGateStatusEnum.NOT_READY);
        assertThat(gate.getBlockReasons()).hasSize(2);
    }

    // --- AC-6: ENFORCE with only SKU_CODE_MISSING → READY_WITH_WARNINGS ---

    @Test
    void enforce_withOnlySkuCodeMissing_returnsReadyWithWarnings() {
        StockMappingCoverageReportService enforceService = createEnforceService();

        auditService.observeMissingMapping(observeReq(StockCoverageTypeEnum.SKU_CODE_MISSING, null, 1L, "STORE", "key-a6-1"));

        StockCoverageGateResultRespDTO gate = enforceService.evaluateGate(1L);

        assertThat(gate.getStatus()).isEqualTo(StockCoverageGateStatusEnum.READY_WITH_WARNINGS);
        assertThat(gate.getMode()).isEqualTo(StockCoverageModeEnum.ENFORCE);
        assertThat(gate.getTotalUnresolved()).isEqualTo(1);
        assertThat(gate.getBlockReasons()).isEmpty();
    }

    // --- AC-7: ENFORCE with zero unresolved → READY ---

    @Test
    void enforce_withZeroUnresolved_returnsReady() {
        StockMappingCoverageReportService enforceService = createEnforceService();

        StockCoverageGateResultRespDTO gate = enforceService.evaluateGate(1L);

        assertThat(gate.getStatus()).isEqualTo(StockCoverageGateStatusEnum.READY);
        assertThat(gate.getMode()).isEqualTo(StockCoverageModeEnum.ENFORCE);
        assertThat(gate.getTotalUnresolved()).isEqualTo(0);
    }

    @Test
    void enforce_withResolvedObservations_returnsReady() {
        StockMappingCoverageReportService enforceService = createEnforceService();

        // Insert observation then resolve it
        auditService.observeMissingMapping(observeReq(StockCoverageTypeEnum.LOCATION_MISSING, "SKU_LR", 7L, "STORE", "key-a7-1"));
        createStockLocation(7L, "STORE");

        StockCoverageGateResultRespDTO gate = enforceService.evaluateGate(1L);

        assertThat(gate.getStatus()).isEqualTo(StockCoverageGateStatusEnum.READY);
        assertThat(gate.getTotalUnresolved()).isEqualTo(0);
    }

    // --- AC-8: Read-only ---

    @Test
    void evaluateGate_doesNotInsertOrUpdateAuditRows() {
        auditService.observeMissingMapping(observeReq(StockCoverageTypeEnum.SKU_CODE_MISSING, null, 1L, "STORE", "key-a8-1"));

        StockCoverageGateResultRespDTO before = reportService.evaluateGate(1L);
        int unresolvedBefore = before.getTotalUnresolved();

        reportService.evaluateGate(1L);
        reportService.evaluateGate(1L);

        StockCoverageGateResultRespDTO after = reportService.evaluateGate(1L);
        assertThat(after.getTotalUnresolved()).isEqualTo(unresolvedBefore);
    }

    // --- AC-9: No forbidden scope (verified by forbidden checks script, but also no exceptions) ---

    @Test
    void evaluateGate_allCoverageTypesMixed_worksCorrectly() {
        // Mixed: resolved STOCK_ITEM_MISSING + unresolved LOCATION_MISSING + unresolved SKU_CODE_MISSING
        auditService.observeMissingMapping(observeReq(StockCoverageTypeEnum.STOCK_ITEM_MISSING, "SKU_MIX", 1L, "STORE", "key-a9-1"));
        createStockItem("SKU_MIX");

        auditService.observeMissingMapping(observeReq(StockCoverageTypeEnum.LOCATION_MISSING, "SKU_MIX2", 9L, "WAREHOUSE", "key-a9-2"));
        auditService.observeMissingMapping(observeReq(StockCoverageTypeEnum.SKU_CODE_MISSING, null, 1L, "STORE", "key-a9-3"));

        StockCoverageGateResultRespDTO gate = reportService.evaluateGate(1L);

        // AUDIT_ONLY mode → never NOT_READY
        assertThat(gate.getStatus()).isEqualTo(StockCoverageGateStatusEnum.READY_WITH_WARNINGS);
        assertThat(gate.getTotalUnresolved()).isEqualTo(2);
    }

    // --- Helpers ---

    private StockMappingCoverageReportService createEnforceService() {
        // Create a new instance with ENFORCE mode
        return new StockMappingCoverageReportServiceImpl(
                auditMapper,
                stockItemMapper,
                stockLocationMapper,
                "ENFORCE",
                100
        );
    }

    private StockCoverageObserveReqDTO observeReq(StockCoverageTypeEnum type, String skuCode, Long storeId, String locationType, String key) {
        StockCoverageObserveReqDTO req = new StockCoverageObserveReqDTO();
        req.setTenantId(1L);
        req.setCoverageType(type);
        req.setSkuCode(skuCode);
        req.setSkuId(skuCode != null ? 200L : null);
        req.setStoreId(storeId);
        req.setLocationType(locationType);
        req.setSourceModule("checkout");
        req.setSourceRecordId(1L);
        req.setIdempotentKey(key);
        return req;
    }

    private void createStockItem(String skuCode) {
        StockItemDO item = new StockItemDO();
        item.setTenantId(1L);
        item.setSkuCode(skuCode);
        item.setItemName("Item " + skuCode);
        item.setUnit("KG");
        item.setIsActive(true);
        item.setCreator("test");
        item.setCreateTime(LocalDateTime.now());
        item.setUpdater("test");
        item.setUpdateTime(LocalDateTime.now());
        item.setDeleted(false);
        stockItemMapper.insert(item);
    }

    private void createStockLocation(Long storeId, String locationType) {
        StockLocationDO loc = new StockLocationDO();
        loc.setTenantId(1L);
        loc.setLocationCode("LOC_" + storeId + "_" + locationType);
        loc.setLocationName("Location " + storeId);
        loc.setLocationType(locationType);
        loc.setStoreId(storeId);
        loc.setIsActive(true);
        loc.setCreator("test");
        loc.setCreateTime(LocalDateTime.now());
        loc.setUpdater("test");
        loc.setUpdateTime(LocalDateTime.now());
        loc.setDeleted(false);
        stockLocationMapper.insert(loc);
    }
}
