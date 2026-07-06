package com.geihou.module.supplychain.stock.controller.admin;

import com.geihou.common.pojo.CommonResult;
import com.geihou.common.error.ErrorCode;
import com.geihou.module.supplychain.api.stock.StockApi;
import com.geihou.module.supplychain.api.stock.StockEventApi;
import com.geihou.module.supplychain.api.stock.dto.SalesOutBomReverseReqDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesOutBomReverseRespDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesOutBomReverseItemRespDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesReverseRestoreReqDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesReverseRestoreRespDTO;
import com.geihou.module.supplychain.api.stock.dto.SkuQuantityDTO;
import com.geihou.module.supplychain.api.stock.dto.StockCheckRespDTO;
import com.geihou.module.supplychain.api.stock.enums.StockEventTypeEnum;
import com.geihou.module.supplychain.stock.api.StockApiImpl;
import com.geihou.module.supplychain.stock.check.StockCheckService;
import com.geihou.module.supplychain.stock.framework.StockBusinessException;
import com.geihou.module.supplychain.stock.framework.StockErrorCodeConstants;
import com.geihou.module.supplychain.stock.service.StockBomReverseService;
import com.geihou.module.supplychain.stock.service.StockHealthService;
import com.geihou.module.supplychain.stock.service.ProductCurrentCostService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lightweight unit tests for {@link StockBomReverseController} and
 * {@link StockApiImpl} delegation (G2-02F).
 *
 * <p>Uses direct instantiation + Mockito mocks — no Spring context, no DB.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Controller delegates to {@link StockApi} for both endpoints.</li>
 *   <li>Controller wraps the response in {@link CommonResult#success(Object)}.</li>
 *   <li>{@link StockApiImpl} delegates to {@link StockBomReverseService} for
 *       both BOM-reverse methods.</li>
 *   <li>Controller propagates {@link StockBusinessException} without swallowing
 *       for both sales-out and sales-reverse-restore (G2-02F terminal blocker A/B).</li>
 *   <li>Forbidden-scan: no ADJUST_IN in enum (RETURN_IN added by G2-02Q), no salesOutWithBomReverse /
 *       salesReverseRestore in StockEventApi, no forbidden symbols in finance,
 *       no G2-02F migration files (G2-02F terminal blocker C).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class StockBomReverseControllerTest {

    // ================================================================
    // Controller delegation tests
    // ================================================================

    @Mock
    private StockApi stockApi;

    @InjectMocks
    private StockBomReverseController controller;

    @Test
    void salesOut_delegatesToStockApi_andWrapsInCommonResult() {
        // Arrange
        SalesOutBomReverseReqDTO req = new SalesOutBomReverseReqDTO();
        req.setTenantId(1L);
        req.setProductId(100L);

        SalesOutBomReverseRespDTO mockResp = new SalesOutBomReverseRespDTO();
        mockResp.setProductId(100L);
        // Populate items with eventId so the response is not an empty shell
        SalesOutBomReverseItemRespDTO item = new SalesOutBomReverseItemRespDTO();
        item.setComponentProductId(201L);
        item.setSkuCode("RAW-201");
        item.setUnit("KG");
        item.setStockItemId(3001L);
        item.setQuantity(new BigDecimal("5.00"));
        item.setEventId(9001L);
        item.setClientRequestId("req-001::201");
        item.setRecipeId(501L);
        item.setRecipeVersion(1);
        mockResp.setItems(List.of(item));

        when(stockApi.salesOutWithBomReverse(req)).thenReturn(mockResp);

        // Act
        CommonResult<SalesOutBomReverseRespDTO> result = controller.salesOutWithBomReverse(req);

        // Assert
        verify(stockApi).salesOutWithBomReverse(eq(req));
        assertThat(result.getCode()).isEqualTo(CommonResult.SUCCESS_CODE);
        assertThat(result.getData()).isSameAs(mockResp);
        // Explicitly assert response data is not an empty shell
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getItems()).isNotEmpty();
        assertThat(result.getData().getItems().get(0).getEventId()).isEqualTo(9001L);
    }

    @Test
    void salesReverseRestore_delegatesToStockApi_andWrapsInCommonResult() {
        // Arrange
        SalesReverseRestoreReqDTO req = new SalesReverseRestoreReqDTO();
        req.setTenantId(1L);
        req.setSourceRecordId(2001L);

        SalesReverseRestoreRespDTO mockResp = new SalesReverseRestoreRespDTO();
        mockResp.setRestoredItemCount(2);
        when(stockApi.salesReverseRestore(req)).thenReturn(mockResp);

        // Act
        CommonResult<SalesReverseRestoreRespDTO> result = controller.salesReverseRestore(req);

        // Assert
        verify(stockApi).salesReverseRestore(eq(req));
        assertThat(result.getCode()).isEqualTo(CommonResult.SUCCESS_CODE);
        assertThat(result.getData()).isSameAs(mockResp);
    }

    // ================================================================
    // G2-02F Terminal Blocker A: sales-out exception propagation
    // ================================================================

    @Test
    void salesOut_whenStockApiThrowsStockBusinessException_exceptionPropagates() {
        // Arrange
        SalesOutBomReverseReqDTO req = new SalesOutBomReverseReqDTO();
        req.setTenantId(1L);
        req.setProductId(100L);

        StockBusinessException ex = new StockBusinessException(
                StockErrorCodeConstants.INSUFFICIENT_STOCK,
                "productId=100, component=201");
        when(stockApi.salesOutWithBomReverse(req)).thenThrow(ex);

        // Act & Assert — exception must propagate, not be swallowed into CommonResult
        assertThatThrownBy(() -> controller.salesOutWithBomReverse(req))
                .isInstanceOf(StockBusinessException.class)
                .isSameAs(ex)
                .hasMessageContaining("Insufficient stock");
    }

    // ================================================================
    // G2-02F Terminal Blocker B: sales-reverse-restore exception propagation
    // ================================================================

    @Test
    void salesReverseRestore_whenNoOriginalFlowAndStockApiThrows_exceptionPropagates() {
        // Arrange — simulate "no original CONSUME_OUT events found" scenario
        SalesReverseRestoreReqDTO req = new SalesReverseRestoreReqDTO();
        req.setTenantId(1L);
        req.setSourceModule("SALES");
        req.setSourceRecordId(9999L);
        req.setClientRequestId("restore-001");

        StockBusinessException ex = new StockBusinessException(
                StockErrorCodeConstants.RESERVE_NOT_FOUND,
                "no original CONSUME_OUT events for sourceRecordId=9999");
        when(stockApi.salesReverseRestore(req)).thenThrow(ex);

        // Act & Assert — exception must propagate, not be swallowed into CommonResult
        assertThatThrownBy(() -> controller.salesReverseRestore(req))
                .isInstanceOf(StockBusinessException.class)
                .isSameAs(ex)
                .hasMessageContaining("no original CONSUME_OUT events");
    }

    // ================================================================
    // StockApiImpl delegation to StockBomReverseService
    // ================================================================

    @Test
    void stockApiImpl_salesOutWithBomReverse_delegatesToService() {
        // Arrange
        StockCheckService mockCheckService = org.mockito.Mockito.mock(StockCheckService.class);
        StockBomReverseService mockReverseService = org.mockito.Mockito.mock(StockBomReverseService.class);
        StockHealthService mockHealthService = org.mockito.Mockito.mock(StockHealthService.class);
        ProductCurrentCostService mockCostService = org.mockito.Mockito.mock(ProductCurrentCostService.class);
        StockApiImpl apiImpl = new StockApiImpl(mockCheckService, mockReverseService, mockHealthService, mockCostService);

        SalesOutBomReverseReqDTO req = new SalesOutBomReverseReqDTO();
        req.setTenantId(1L);
        req.setProductId(100L);

        SalesOutBomReverseRespDTO mockResp = new SalesOutBomReverseRespDTO();
        when(mockReverseService.salesOutWithBomReverse(req)).thenReturn(mockResp);

        // Act
        SalesOutBomReverseRespDTO result = apiImpl.salesOutWithBomReverse(req);

        // Assert
        verify(mockReverseService).salesOutWithBomReverse(eq(req));
        assertThat(result).isSameAs(mockResp);
    }

    @Test
    void stockApiImpl_salesReverseRestore_delegatesToService() {
        // Arrange
        StockCheckService mockCheckService = org.mockito.Mockito.mock(StockCheckService.class);
        StockBomReverseService mockReverseService = org.mockito.Mockito.mock(StockBomReverseService.class);
        StockHealthService mockHealthService = org.mockito.Mockito.mock(StockHealthService.class);
        ProductCurrentCostService mockCostService = org.mockito.Mockito.mock(ProductCurrentCostService.class);
        StockApiImpl apiImpl = new StockApiImpl(mockCheckService, mockReverseService, mockHealthService, mockCostService);

        SalesReverseRestoreReqDTO req = new SalesReverseRestoreReqDTO();
        req.setTenantId(1L);
        req.setSourceRecordId(2001L);

        SalesReverseRestoreRespDTO mockResp = new SalesReverseRestoreRespDTO();
        when(mockReverseService.salesReverseRestore(req)).thenReturn(mockResp);

        // Act
        SalesReverseRestoreRespDTO result = apiImpl.salesReverseRestore(req);

        // Assert
        verify(mockReverseService).salesReverseRestore(eq(req));
        assertThat(result).isSameAs(mockResp);
    }

    @Test
    void stockApiImpl_checkStock_delegatesToCheckService() {
        // Arrange — verify the non-reverse method still delegates correctly
        StockCheckService mockCheckService = org.mockito.Mockito.mock(StockCheckService.class);
        StockBomReverseService mockReverseService = org.mockito.Mockito.mock(StockBomReverseService.class);
        StockHealthService mockHealthService = org.mockito.Mockito.mock(StockHealthService.class);
        ProductCurrentCostService mockCostService = org.mockito.Mockito.mock(ProductCurrentCostService.class);
        StockApiImpl apiImpl = new StockApiImpl(mockCheckService, mockReverseService, mockHealthService, mockCostService);

        Long tenantId = 1L;
        java.util.List<SkuQuantityDTO> items = Collections.emptyList();
        StockCheckRespDTO mockResp = new StockCheckRespDTO();
        when(mockCheckService.checkStock(tenantId, items)).thenReturn(mockResp);

        // Act
        StockCheckRespDTO result = apiImpl.checkStock(tenantId, items);

        // Assert
        verify(mockCheckService).checkStock(eq(tenantId), eq(items));
        assertThat(result).isSameAs(mockResp);
    }

    // ================================================================
    // G2-02F Terminal Blocker C: Forbidden-scan tests
    // ================================================================

    /**
     * Locate the repository root by walking up from user.dir until we find
     * a directory containing 'pom.xml' and 'geihou-module-supplychain'.
     */
    private static Path findRepoRoot() {
        Path current = Paths.get(System.getProperty("user.dir"));
        while (current != null && Files.exists(current)) {
            if (Files.exists(current.resolve("pom.xml")) &&
                Files.exists(current.resolve("geihou-module-supplychain"))) {
                return current;
            }
            current = current.getParent();
        }
        // Fallback: use the known absolute path
        return Paths.get("/Users/mac/Desktop/abao-projects/abao-backend");
    }

    @Test
    void forbiddenScan_stockEventTypeEnum_hasNoAdjustIn() throws Exception {
        // G2-02Q added RETURN_IN to the enum. ADJUST_IN must still NOT exist.
        for (StockEventTypeEnum type : StockEventTypeEnum.values()) {
            assertThat(type.name())
                    .as("StockEventTypeEnum must not contain ADJUST_IN (G2-02F)")
                    .isNotEqualTo("ADJUST_IN");
        }
    }

    @Test
    void forbiddenScan_stockEventApi_hasNoSalesOutWithBomReverseOrSalesReverseRestore() throws Exception {
        // Verify StockEventApi interface does NOT declare salesOutWithBomReverse or salesReverseRestore
        java.lang.reflect.Method[] methods = StockEventApi.class.getDeclaredMethods();
        for (java.lang.reflect.Method method : methods) {
            assertThat(method.getName())
                    .as("StockEventApi must not declare salesOutWithBomReverse (G2-02F)")
                    .isNotEqualTo("salesOutWithBomReverse");
            assertThat(method.getName())
                    .as("StockEventApi must not declare salesReverseRestore (G2-02F)")
                    .isNotEqualTo("salesReverseRestore");
        }
    }

    @Test
    void forbiddenScan_financeDirectory_hasNoForbiddenSymbols() throws Exception {
        Path repoRoot = findRepoRoot();
        Path financeDir = repoRoot.resolve("geihou-module-finance");
        assertThat(Files.exists(financeDir))
                .as("finance module directory must exist at %s", financeDir)
                .isTrue();

        // G2-02H-3 allows finance to call StockApi.salesOutWithBomReverse /
        // StockApi.salesReverseRestore through the supplychain API contract.
        // Keep the enum/DDL boundary guard here; StockEventApi remains guarded
        // by forbiddenScan_stockEventApi_hasNoSalesOutWithBomReverseOrSalesReverseRestore.
        List<String> forbiddenTokens = List.of(
                "RETURN_IN",
                "ADJUST_IN"
        );

        try (Stream<Path> walk = Files.walk(financeDir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".sql"))
                    .forEach(p -> {
                        try {
                            String content = Files.readString(p);
                            for (String token : forbiddenTokens) {
                                assertThat(content)
                                        .as("finance file %s must not contain '%s' (G2-02F)", p, token)
                                        .doesNotContain(token);
                            }
                        } catch (java.io.IOException e) {
                            fail("Failed to read finance file: " + p, e);
                        }
                    });
        }
    }

    @Test
    void forbiddenScan_migrationDirectory_hasNoG2_02FNewFiles() throws Exception {
        Path repoRoot = findRepoRoot();
        Path migrationDir = repoRoot.resolve("geihou-module-supplychain")
                .resolve("geihou-module-supplychain-biz")
                .resolve("src")
                .resolve("main")
                .resolve("resources")
                .resolve("db")
                .resolve("migration");
        assertThat(Files.exists(migrationDir))
                .as("supplychain migration directory must exist at %s", migrationDir)
                .isTrue();

        // No migration files should reference G2-02F or contain forbidden symbols
        List<String> forbiddenTokens = List.of(
                "G2-02F", "G2_02F", "g2-02f", "g2_02f",
                "SalesReverseRestore",
                "SalesOutBomReverse",
                "RETURN_IN",
                "ADJUST_IN"
        );

        try (Stream<Path> walk = Files.walk(migrationDir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".sql"))
                    .forEach(p -> {
                        try {
                            String content = Files.readString(p);
                            for (String token : forbiddenTokens) {
                                assertThat(content)
                                        .as("migration file %s must not contain '%s' (G2-02F)", p, token)
                                        .doesNotContain(token);
                            }
                        } catch (java.io.IOException e) {
                            fail("Failed to read migration file: " + p, e);
                        }
                    });
        }
    }
}
