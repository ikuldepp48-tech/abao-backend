package com.geihou.module.finance.stock;

import com.geihou.module.supplychain.api.bom.BomApi;
import com.geihou.module.supplychain.api.bom.dto.BomRecipeRespDTO;
import com.geihou.module.supplychain.api.bom.explode.dto.BomExplosionRespDTO;
import com.geihou.module.supplychain.api.bom.preview.dto.BomCostPreviewRespDTO;
import com.geihou.module.supplychain.api.stock.StockApi;
import com.geihou.module.supplychain.api.stock.StockEventApi;
import com.geihou.module.supplychain.api.stock.StockCoverageApi;
import com.geihou.module.supplychain.api.stock.StockQueryApi;
import com.geihou.module.supplychain.api.stock.dto.SalesOutBomReverseReqDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesOutBomReverseRespDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesReverseRestoreItemRespDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesReverseRestoreReqDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesReverseRestoreRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockCoverageDecisionRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockCoverageObserveReqDTO;
import com.geihou.module.supplychain.api.stock.dto.StockItemQueryRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockLocationQueryRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockCheckRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockCheckItemResultDTO;
import com.geihou.module.supplychain.api.stock.dto.SkuQuantityDTO;
import com.geihou.module.supplychain.api.stock.dto.ProductCurrentCostRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockHealthRespDTO;
import com.geihou.module.supplychain.api.stock.enums.StockCoverageModeEnum;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * Test configuration providing mock StockEventApi + StockQueryApi + BomApi + StockApi beans.
 *
 * <p>Used by finance checkout/order/refund tests to avoid depending on supplychain runtime.
 * The mocks return configurable responses for reserve/release/commit, BOM detection,
 * BOM reverse sales-out, and BOM restore operations.
 *
 * <p>Source: TASK-G2-01B2 Section 8, extended by TASK-G2-02H-3.
 */
@TestConfiguration
public class StockTestConfig {

    @Bean
    @Primary
    public StockEventApi mockStockEventApi() {
        return new MockStockEventApi();
    }

    @Bean
    @Primary
    public StockQueryApi mockStockQueryApi() {
        return new MockStockQueryApi();
    }

    @Bean
    @Primary
    public StockCoverageApi mockStockCoverageApi() {
        return new MockStockCoverageApi();
    }

    @Bean
    @Primary
    public BomApi mockBomApi() {
        return new MockBomApi();
    }

    @Bean
    @Primary
    public StockApi mockStockApi() {
        return new MockStockApi();
    }

    /**
     * Configurable mock StockEventApi.
     *
     * Default behavior: reserve returns 1L, release succeeds, commit returns 100L.
     * Can be programmatically configured to throw exceptions for failure testing.
     */
    public static class MockStockEventApi implements StockEventApi {

        public boolean shouldFailReserve = false;
        public boolean shouldFailRelease = false;
        public boolean shouldFailCommit = false;
        public RuntimeException reserveException = null;
        public RuntimeException commitException = null;

        private final Map<String, Long> reserveStore = new HashMap<>();
        private long nextReserveId = 1L;
        private long nextEventId = 100L;

        @Override
        public Long recordEvent(com.geihou.module.supplychain.api.stock.dto.StockEventReqDTO req) {
            return nextEventId++;
        }

        @Override
        public BigDecimal getAvailableQty(Long tenantId, Long itemId, Long locationId) {
            return new BigDecimal("100");
        }

        @Override
        public boolean checkAvailable(Long tenantId, Long itemId, Long locationId, BigDecimal requiredQty) {
            return true;
        }

        @Override
        public Long reserveStock(com.geihou.module.supplychain.api.stock.dto.StockReserveReqDTO req) {
            if (shouldFailReserve) {
                throw reserveException != null ? reserveException
                        : new RuntimeException("Mock reserve failure");
            }
            // Idempotent: same idempotentKey returns same reserveId
            Long existing = reserveStore.get(req.getIdempotentKey());
            if (existing != null) {
                return existing;
            }
            Long reserveId = nextReserveId++;
            reserveStore.put(req.getIdempotentKey(), reserveId);
            return reserveId;
        }

        @Override
        public void releaseStock(com.geihou.module.supplychain.api.stock.dto.StockReleaseReqDTO req) {
            if (shouldFailRelease) {
                throw new RuntimeException("Mock release failure");
            }
            // No-op (idempotent)
        }

        @Override
        public Long commitStock(com.geihou.module.supplychain.api.stock.dto.StockCommitReqDTO req) {
            if (shouldFailCommit) {
                throw commitException != null ? commitException
                        : new RuntimeException("Mock commit failure");
            }
            return nextEventId++;
        }

        public void reset() {
            shouldFailReserve = false;
            shouldFailRelease = false;
            shouldFailCommit = false;
            reserveException = null;
            commitException = null;
            reserveStore.clear();
            nextReserveId = 1L;
            nextEventId = 100L;
        }

        public Map<String, Long> getReserveStore() {
            return reserveStore;
        }

        public long getNextEventId() {
            return nextEventId;
        }
    }

    /**
     * Configurable mock StockQueryApi.
     *
     * Default behavior: returns mock mappings for SKU_BEEF and shopId=1.
     * Can be programmatically configured to return null for unmapped SKUs.
     */
    public static class MockStockQueryApi implements StockQueryApi {

        public boolean hasStockItemMapping = true;
        public boolean hasStockLocationMapping = true;

        @Override
        public StockItemQueryRespDTO getStockItemBySkuCode(Long tenantId, String skuCode) {
            if (!hasStockItemMapping) {
                return null;
            }
            return new StockItemQueryRespDTO(1L, skuCode, tenantId, "份");
        }

        @Override
        public StockLocationQueryRespDTO getStockLocationByStoreId(Long tenantId, Long storeId, String locationType) {
            if (!hasStockLocationMapping) {
                return null;
            }
            return new StockLocationQueryRespDTO(1L, storeId, locationType, tenantId);
        }

        public void reset() {
            hasStockItemMapping = true;
            hasStockLocationMapping = true;
        }
    }

    public static class MockStockCoverageApi implements StockCoverageApi {

        public StockCoverageModeEnum mode = StockCoverageModeEnum.AUDIT_ONLY;
        public final List<StockCoverageObserveReqDTO> observations = new ArrayList<>();

        @Override
        public StockCoverageDecisionRespDTO observeMissingMapping(StockCoverageObserveReqDTO req) {
            observations.add(req);
            return new StockCoverageDecisionRespDTO(mode, mode == StockCoverageModeEnum.ENFORCE);
        }

        public void reset() {
            mode = StockCoverageModeEnum.AUDIT_ONLY;
            observations.clear();
        }
    }

    /**
     * Configurable mock BomApi for G2-02H-3 tests.
     *
     * Default behavior: returns empty-state DTO (id=null) for all skuCodes, meaning
     * "no active BOM" → finance keeps the existing non-BOM reserve/commit path.
     * Tests can configure active BOMs via {@link #activeRecipes} or force a lookup
     * failure via {@link #shouldThrow}.
     */
    public static class MockBomApi implements BomApi {

        /** skuCode → active recipe DTO. If absent, returns empty-state DTO (id=null). */
        public final Map<String, BomRecipeRespDTO> activeRecipes = new HashMap<>();
        public boolean shouldThrow = false;
        public RuntimeException throwException = null;
        public final List<Long> tenantIdCalls = new ArrayList<>();
        public final List<String> skuCodeCalls = new ArrayList<>();

        @Override
        public List<BomExplosionRespDTO> explode(Long productId, Long recipeId,
                                                  BigDecimal requestedQuantity, Long tenantId) {
            return new ArrayList<>();
        }

        @Override
        public List<BomCostPreviewRespDTO> previewCost(Long productId, Long recipeId,
                                                        BigDecimal requestedQuantity, Long tenantId) {
            return new ArrayList<>();
        }

        @Override
        public BomRecipeRespDTO getActiveRecipe(Long tenantId, Long productId) {
            return emptyState();
        }

        @Override
        public BomRecipeRespDTO getActiveRecipeBySkuCode(Long tenantId, String skuCode) {
            tenantIdCalls.add(tenantId);
            skuCodeCalls.add(skuCode);
            if (shouldThrow) {
                throw throwException != null ? throwException
                        : new RuntimeException("Mock BomApi failure");
            }
            BomRecipeRespDTO dto = activeRecipes.get(skuCode);
            return dto != null ? dto : emptyState();
        }

        public void reset() {
            activeRecipes.clear();
            shouldThrow = false;
            throwException = null;
            tenantIdCalls.clear();
            skuCodeCalls.clear();
        }

        public static BomRecipeRespDTO emptyState() {
            BomRecipeRespDTO dto = new BomRecipeRespDTO();
            dto.setId(null);
            dto.setItems(new ArrayList<>());
            return dto;
        }

        public static BomRecipeRespDTO activeRecipe(Long productId, Long recipeId) {
            BomRecipeRespDTO dto = new BomRecipeRespDTO();
            dto.setId(recipeId);
            dto.setProductId(productId);
            dto.setItems(new ArrayList<>());
            return dto;
        }
    }

    /**
     * Configurable mock StockApi for G2-02H-3 tests.
     *
     * Default behavior:
     * - checkStock returns allSufficient=true
     * - salesOutWithBomReverse returns an empty-items response
     * - salesReverseRestore throws the "no original CONSUME_OUT events found" message
     *   (mimicking the supplychain-biz contract for non-BOM orders)
     *
     * Tests can configure sufficiency, capture requests, and override restore behavior.
     */
    public static class MockStockApi implements StockApi {

        public boolean checkStockAllSufficient = true;
        public boolean checkStockShouldThrow = false;
        public RuntimeException checkStockException = null;
        public boolean salesOutShouldThrow = false;
        public RuntimeException salesOutException = null;
        public boolean restoreShouldThrowNoOriginal = true;
        public RuntimeException restoreException = null;
        public int restoreItemCount = 0;

        public final List<SalesOutBomReverseReqDTO> salesOutRequests = new ArrayList<>();
        public final List<SalesReverseRestoreReqDTO> restoreRequests = new ArrayList<>();
        public final List<Long> checkStockTenantIdCalls = new ArrayList<>();

        @Override
        public StockCheckRespDTO checkStock(Long tenantId, List<SkuQuantityDTO> items) {
            checkStockTenantIdCalls.add(tenantId);
            if (checkStockShouldThrow) {
                throw checkStockException != null ? checkStockException
                        : new RuntimeException("Mock checkStock failure");
            }
            StockCheckRespDTO resp = new StockCheckRespDTO();
            resp.setTenantId(tenantId);
            resp.setAllSufficient(checkStockAllSufficient);
            resp.setItems(new ArrayList<>());
            resp.setUnmappedItems(new ArrayList<>());
            return resp;
        }

        @Override
        public StockHealthRespDTO getStockHealth(Long tenantId) {
            return new StockHealthRespDTO();
        }

        @Override
        public ProductCurrentCostRespDTO getProductCurrentCost(Long tenantId, Long productId) {
            return new ProductCurrentCostRespDTO();
        }

        @Override
        public SalesOutBomReverseRespDTO salesOutWithBomReverse(SalesOutBomReverseReqDTO req) {
            salesOutRequests.add(req);
            if (salesOutShouldThrow) {
                throw salesOutException != null ? salesOutException
                        : new RuntimeException("Mock salesOutWithBomReverse failure");
            }
            SalesOutBomReverseRespDTO resp = new SalesOutBomReverseRespDTO();
            resp.setTenantId(req.getTenantId());
            resp.setProductId(req.getProductId());
            resp.setSkuCode(req.getSkuCode());
            resp.setQuantity(req.getQuantity());
            resp.setItems(new ArrayList<>());
            return resp;
        }

        @Override
        public SalesReverseRestoreRespDTO salesReverseRestore(SalesReverseRestoreReqDTO req) {
            restoreRequests.add(req);
            if (restoreShouldThrowNoOriginal) {
                throw new RuntimeException(
                        "no original CONSUME_OUT events found for sourceRecordId=" + req.getSourceRecordId());
            }
            if (restoreException != null) {
                throw restoreException;
            }
            SalesReverseRestoreRespDTO resp = new SalesReverseRestoreRespDTO();
            resp.setRestoredItemCount(restoreItemCount);
            List<SalesReverseRestoreItemRespDTO> items = new ArrayList<>();
            for (int i = 0; i < restoreItemCount; i++) {
                items.add(new SalesReverseRestoreItemRespDTO());
            }
            resp.setItems(items);
            return resp;
        }

        public void reset() {
            checkStockAllSufficient = true;
            checkStockShouldThrow = false;
            checkStockException = null;
            salesOutShouldThrow = false;
            salesOutException = null;
            restoreShouldThrowNoOriginal = true;
            restoreException = null;
            restoreItemCount = 0;
            salesOutRequests.clear();
            restoreRequests.clear();
            checkStockTenantIdCalls.clear();
        }
    }
}
