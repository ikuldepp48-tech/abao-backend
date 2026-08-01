package com.geihou.module.finance.stock.plan.classifier;

import com.geihou.module.finance.product.enums.StockStrategyEnum;
import com.geihou.module.finance.stock.plan.enums.CheckoutClassificationReason;
import com.geihou.module.finance.stock.plan.enums.CheckoutStockClassification;
import com.geihou.module.supplychain.api.bom.BomApi;
import com.geihou.module.supplychain.api.bom.dto.BomRecipeRespDTO;
import com.geihou.module.supplychain.api.stock.StockQueryApi;
import com.geihou.module.supplychain.api.stock.dto.StockItemQueryRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockLocationQueryRespDTO;
import java.util.Objects;
import java.util.Set;

/**
 * Six-step checkout cart item classifier.
 *
 * <p>G0-04H185 FIN-CONSISTENCY slice 2C-2C-A1.
 *
 * <p>Authority: 00-全局接口契约汇总表.md §5.8.1 (six-step matrix, no overlap).
 *
 * <p>Decision order (任一步骤命中即终止,不得回退、不得重叠):
 * <ol>
 *   <li>Step 1a: skuCode null/blank -> UNMAPPED/INVALID_SKU_CODE. Runs before
 *       any API call. 1a wins on simultaneous 1a+1b failure (1b not executed).</li>
 *   <li>Step 1b: else-if stockStrategy not TRACK_STOCK/UNLIMITED ->
 *       UNMAPPED/INVALID_STOCK_STRATEGY. No API call.</li>
 *   <li>Step 2: stockStrategy=UNLIMITED -> NON_BOM/null. No Bom/Stock call.</li>
 *   <li>Step 3: stockStrategy=TRACK_STOCK -> call {@link BomApi#getActiveRecipeBySkuCode}.</li>
 *   <li>Step 4: lookupStatus=NO_PRODUCT -> UNMAPPED/NO_PRODUCT;
 *       lookupStatus=AMBIGUOUS_PRODUCT -> UNMAPPED/AMBIGUOUS_PRODUCT;
 *       lookupStatus=INVALID_SKU_CODE (after 1a verified non-null) -> technical failure;
 *       lookupStatus null/unknown or BomRecipeRespDTO field-combo mismatch -> technical failure.</li>
 *   <li>Step 5: lookupStatus=FOUND -> query StockQueryApi.getStockLocationByStoreId;
 *       location present + valid -> BOM/null; location missing -> UNMAPPED/NO_LOCATION;
 *       location invalid -> technical failure.</li>
 *   <li>Step 6: lookupStatus=NO_ACTIVE_RECIPE -> query location FIRST; missing ->
 *       UNMAPPED/NO_LOCATION; present + valid -> query StockQueryApi.getStockItemBySkuCode;
 *       missing -> UNMAPPED/NO_STOCK_ITEM; present + valid -> NON_BOM/null;
 *       invalid -> technical failure. <b>Must query location before stockItem; no parallel.</b></li>
 * </ol>
 *
 * <p>Normalization is independent per field: blank {@code skuCode} -> null;
 * invalid {@code stockStrategy} (not TRACK_STOCK/UNLIMITED) -> null. Both may
 * be null simultaneously. NULL is NOT bound to {@code classification_reason};
 * it always mirrors source-value validity. Original invalid values must NOT
 * be persisted.
 *
 * <p>Technical failure handling (强制):
 * <ul>
 *   <li>DTO contract violations throw {@link IllegalStateException}
 *       (lookupStatus null/unknown; BomRecipeRespDTO id/productId combo mismatch;
 *       step 1a verified non-null but step 3 returned INVALID_SKU_CODE;
 *       StockItemQueryRespDTO/StockLocationQueryRespDTO valid-condition violations).</li>
 *   <li>Raw {@link BomApi} / {@link StockQueryApi} exceptions propagate as-is
 *       (no swallow, no wrap, no conversion to UNMAPPED).</li>
 *   <li>Technical failures are NEVER converted to UNMAPPED; no plan write,
 *       no 1004080,沿用现有异常映射 (handled by caller).</li>
 * </ul>
 *
 * <p>Classifier does NOT call ProductApi. Caller supplies source skuCode /
 * stockStrategy. Fixed {@code locationType="STORE"}.
 *
 * <p>Intentionally NOT annotated with {@code @Component}: this class is a
 * stateless logic bean instantiated via its constructor. Declaring it as a
 * Spring component would force every {@code @SpringBootTest} that performs
 * component scanning (e.g. {@code CheckoutCartItemPlanTestConfig}) to
 * provide {@link BomApi} and {@link StockQueryApi} beans, even tests that
 * do not exercise classification. 分类器注册延后至 2D durable foundation
 * 完成后的单独原子接入切片。
 */
public class CheckoutClassifier {

    /** Fixed location type for store-front stock location queries. */
    private static final String LOCATION_TYPE_STORE = "STORE";

    private static final Set<String> VALID_STOCK_STRATEGIES = Set.of(
            StockStrategyEnum.TRACK_STOCK.getCode(),
            StockStrategyEnum.UNLIMITED.getCode());

    // BomLookupStatus five values (String DTO field, not enum).
    private static final String LOOKUP_STATUS_FOUND = "FOUND";
    private static final String LOOKUP_STATUS_NO_PRODUCT = "NO_PRODUCT";
    private static final String LOOKUP_STATUS_AMBIGUOUS_PRODUCT = "AMBIGUOUS_PRODUCT";
    private static final String LOOKUP_STATUS_NO_ACTIVE_RECIPE = "NO_ACTIVE_RECIPE";
    private static final String LOOKUP_STATUS_INVALID_SKU_CODE = "INVALID_SKU_CODE";

    private static final Set<String> VALID_LOOKUP_STATUSES = Set.of(
            LOOKUP_STATUS_FOUND, LOOKUP_STATUS_NO_PRODUCT,
            LOOKUP_STATUS_AMBIGUOUS_PRODUCT, LOOKUP_STATUS_NO_ACTIVE_RECIPE,
            LOOKUP_STATUS_INVALID_SKU_CODE);

    private final BomApi bomApi;
    private final StockQueryApi stockQueryApi;

    public CheckoutClassifier(BomApi bomApi, StockQueryApi stockQueryApi) {
        this.bomApi = Objects.requireNonNull(bomApi, "bomApi must not be null");
        this.stockQueryApi = Objects.requireNonNull(stockQueryApi, "stockQueryApi must not be null");
    }

    /**
     * Classify a single cart item per the six-step matrix.
     *
     * <p>Classifier does not validate caller's TenantContextHolder; tenantId is
     * passed straight through to BomApi / StockQueryApi. Caller is responsible
     * for tenant safety.
     *
     * @param tenantId      tenant scope
     * @param storeId       store id (maps to shopId in finance); used for
     *                      StockQueryApi.getStockLocationByStoreId
     * @param skuCode       source skuCode from cart item; null/blank allowed
     *                      (normalized to null if blank)
     * @param stockStrategy source stockStrategy from SKU master; null/invalid
     *                      allowed (normalized to null if not TRACK_STOCK/UNLIMITED)
     * @return immutable classification result with normalized fields and
     *         reference IDs; never null
     * @throws IllegalStateException on DTO contract violation (lookupStatus
     *         null/unknown, BomRecipeRespDTO field-combo mismatch, step 1a
     *         verified non-null but step 3 returned INVALID_SKU_CODE,
     *         StockItemQueryRespDTO/StockLocationQueryRespDTO valid-condition
     *         violations)
     * @throws RuntimeException      raw propagation of BomApi / StockQueryApi
     *         exceptions (no swallow, no wrap)
     */
    public CheckoutClassificationResult classify(long tenantId, long storeId,
                                                 String skuCode, String stockStrategy) {
        // Independent normalization (used in result; 1a/1b branches use raw)
        String normalizedSkuCode = normalizeSkuCode(skuCode);
        String normalizedStrategy = normalizeStockStrategy(stockStrategy);

        // Step 1a: skuCode null/blank -> UNMAPPED/INVALID_SKU_CODE (no API call)
        if (normalizedSkuCode == null) {
            return new CheckoutClassificationResult(
                    CheckoutStockClassification.UNMAPPED,
                    CheckoutClassificationReason.INVALID_SKU_CODE,
                    null, normalizedStrategy, null, null, null, null);
        }

        // Step 1b: else-if stockStrategy invalid -> UNMAPPED/INVALID_STOCK_STRATEGY (no API call)
        if (normalizedStrategy == null) {
            return new CheckoutClassificationResult(
                    CheckoutStockClassification.UNMAPPED,
                    CheckoutClassificationReason.INVALID_STOCK_STRATEGY,
                    normalizedSkuCode, null, null, null, null, null);
        }

        // Step 2: UNLIMITED -> NON_BOM (no Bom/Stock call)
        if (StockStrategyEnum.UNLIMITED.getCode().equals(normalizedStrategy)) {
            return new CheckoutClassificationResult(
                    CheckoutStockClassification.NON_BOM,
                    null,
                    normalizedSkuCode, normalizedStrategy, null, null, null, null);
        }

        // Step 3+: TRACK_STOCK path
        BomRecipeRespDTO bom = bomApi.getActiveRecipeBySkuCode(tenantId, normalizedSkuCode);
        if (bom == null) {
            throw new IllegalStateException(
                    "BomApi.getActiveRecipeBySkuCode returned null DTO for skuCode=" + normalizedSkuCode);
        }
        String lookupStatus = bom.getLookupStatus();
        if (lookupStatus == null || !VALID_LOOKUP_STATUSES.contains(lookupStatus)) {
            throw new IllegalStateException(
                    "BomRecipeRespDTO.lookupStatus null/unknown: " + lookupStatus
                            + " for skuCode=" + normalizedSkuCode);
        }
        verifyBomFieldCombo(lookupStatus, bom.getId(), bom.getProductId(), normalizedSkuCode);

        // Step 4: query-failure statuses
        switch (lookupStatus) {
            case LOOKUP_STATUS_NO_PRODUCT:
                return unmapped(normalizedSkuCode, normalizedStrategy,
                        CheckoutClassificationReason.NO_PRODUCT);
            case LOOKUP_STATUS_AMBIGUOUS_PRODUCT:
                return unmapped(normalizedSkuCode, normalizedStrategy,
                        CheckoutClassificationReason.AMBIGUOUS_PRODUCT);
            case LOOKUP_STATUS_INVALID_SKU_CODE:
                // Step 1a already verified non-null; receiving INVALID_SKU_CODE here = contract failure.
                throw new IllegalStateException(
                        "BomApi returned INVALID_SKU_CODE after step 1a verified non-null skuCode="
                                + normalizedSkuCode);
            case LOOKUP_STATUS_FOUND:
                return classifyFound(tenantId, storeId, normalizedSkuCode, normalizedStrategy, bom);
            case LOOKUP_STATUS_NO_ACTIVE_RECIPE:
                return classifyNoActiveRecipe(tenantId, storeId, normalizedSkuCode,
                        normalizedStrategy, bom);
            default:
                throw new IllegalStateException("Unreachable lookupStatus: " + lookupStatus);
        }
    }

    // ==================== Step 5: FOUND -> BOM or NO_LOCATION ====================

    private CheckoutClassificationResult classifyFound(long tenantId, long storeId,
                                                       String skuCode, String stockStrategy,
                                                       BomRecipeRespDTO bom) {
        // verifyBomFieldCombo already enforced FOUND: id>0 AND productId>0
        Long bomProductId = bom.getProductId();
        Long bomId = bom.getId();

        // Query location
        StockLocationQueryRespDTO location = stockQueryApi.getStockLocationByStoreId(
                tenantId, storeId, LOCATION_TYPE_STORE);
        if (location == null) {
            // location missing -> UNMAPPED/NO_LOCATION
            return new CheckoutClassificationResult(
                    CheckoutStockClassification.UNMAPPED,
                    CheckoutClassificationReason.NO_LOCATION,
                    skuCode, stockStrategy, null, null, null, null);
        }
        verifyLocation(location, tenantId, storeId);
        return new CheckoutClassificationResult(
                CheckoutStockClassification.BOM,
                null,
                skuCode, stockStrategy, bomProductId, null, location.getId(), null);
    }

    // ==================== Step 6: NO_ACTIVE_RECIPE -> NON_BOM or NO_LOCATION / NO_STOCK_ITEM ====================

    private CheckoutClassificationResult classifyNoActiveRecipe(long tenantId, long storeId,
                                                                String skuCode, String stockStrategy,
                                                                BomRecipeRespDTO bom) {
        // verifyBomFieldCombo already enforced NO_ACTIVE_RECIPE: id==null AND productId>0

        // Step 6: location FIRST
        StockLocationQueryRespDTO location = stockQueryApi.getStockLocationByStoreId(
                tenantId, storeId, LOCATION_TYPE_STORE);
        if (location == null) {
            return new CheckoutClassificationResult(
                    CheckoutStockClassification.UNMAPPED,
                    CheckoutClassificationReason.NO_LOCATION,
                    skuCode, stockStrategy, null, null, null, null);
        }
        verifyLocation(location, tenantId, storeId);

        // location present + valid -> query stockItem
        StockItemQueryRespDTO stockItem = stockQueryApi.getStockItemBySkuCode(tenantId, skuCode);
        if (stockItem == null) {
            return new CheckoutClassificationResult(
                    CheckoutStockClassification.UNMAPPED,
                    CheckoutClassificationReason.NO_STOCK_ITEM,
                    skuCode, stockStrategy, null, null, null, null);
        }
        verifyStockItem(stockItem, tenantId, skuCode);

        return new CheckoutClassificationResult(
                CheckoutStockClassification.NON_BOM,
                null,
                skuCode, stockStrategy, null, stockItem.getId(), location.getId(),
                stockItem.getUnit());
    }

    // ==================== Helpers ====================

    private static CheckoutClassificationResult unmapped(String skuCode, String stockStrategy,
                                                         CheckoutClassificationReason reason) {
        return new CheckoutClassificationResult(
                CheckoutStockClassification.UNMAPPED, reason,
                skuCode, stockStrategy, null, null, null, null);
    }

    private static String normalizeSkuCode(String skuCode) {
        if (skuCode == null || skuCode.isBlank()) {
            return null;
        }
        return skuCode;
    }

    private static String normalizeStockStrategy(String stockStrategy) {
        if (stockStrategy == null || !VALID_STOCK_STRATEGIES.contains(stockStrategy)) {
            return null;
        }
        return stockStrategy;
    }

    /**
     * Verify BomRecipeRespDTO id/productId combination matches the lookupStatus
     * contract (00 §5.8.3 lines 665-668).
     *
     * <ul>
     *   <li>FOUND: id > 0 AND productId > 0</li>
     *   <li>NO_ACTIVE_RECIPE: id == null AND productId > 0</li>
     *   <li>NO_PRODUCT / AMBIGUOUS_PRODUCT / INVALID_SKU_CODE: id == null AND productId == null</li>
     * </ul>
     */
    private static void verifyBomFieldCombo(String lookupStatus, Long id, Long productId,
                                            String skuCode) {
        switch (lookupStatus) {
            case LOOKUP_STATUS_FOUND:
                if (id == null || id <= 0) {
                    throw new IllegalStateException(
                            "FOUND requires id > 0, got id=" + id + " for skuCode=" + skuCode);
                }
                if (productId == null || productId <= 0) {
                    throw new IllegalStateException(
                            "FOUND requires productId > 0, got productId=" + productId
                                    + " for skuCode=" + skuCode);
                }
                break;
            case LOOKUP_STATUS_NO_ACTIVE_RECIPE:
                if (id != null) {
                    throw new IllegalStateException(
                            "NO_ACTIVE_RECIPE requires id == null, got id=" + id
                                    + " for skuCode=" + skuCode);
                }
                if (productId == null || productId <= 0) {
                    throw new IllegalStateException(
                            "NO_ACTIVE_RECIPE requires productId > 0, got productId=" + productId
                                    + " for skuCode=" + skuCode);
                }
                break;
            case LOOKUP_STATUS_NO_PRODUCT:
            case LOOKUP_STATUS_AMBIGUOUS_PRODUCT:
            case LOOKUP_STATUS_INVALID_SKU_CODE:
                if (id != null) {
                    throw new IllegalStateException(
                            lookupStatus + " requires id == null, got id=" + id
                                    + " for skuCode=" + skuCode);
                }
                if (productId != null) {
                    throw new IllegalStateException(
                            lookupStatus + " requires productId == null, got productId=" + productId
                                    + " for skuCode=" + skuCode);
                }
                break;
            default:
                throw new IllegalStateException("Unreachable lookupStatus: " + lookupStatus);
        }
    }

    /**
     * Verify StockLocationQueryRespDTO valid conditions (00 §5.8.3 line 670).
     */
    private static void verifyLocation(StockLocationQueryRespDTO location, long tenantId, long storeId) {
        if (location.getId() == null || location.getId() <= 0) {
            throw new IllegalStateException(
                    "StockLocationQueryRespDTO.id must be > 0, got id=" + location.getId());
        }
        if (location.getTenantId() == null || location.getTenantId() != tenantId) {
            throw new IllegalStateException(
                    "StockLocationQueryRespDTO.tenantId mismatch: expected=" + tenantId
                            + ", got=" + location.getTenantId());
        }
        if (location.getStoreId() == null || location.getStoreId() != storeId) {
            throw new IllegalStateException(
                    "StockLocationQueryRespDTO.storeId mismatch: expected=" + storeId
                            + ", got=" + location.getStoreId());
        }
        if (!LOCATION_TYPE_STORE.equals(location.getLocationType())) {
            throw new IllegalStateException(
                    "StockLocationQueryRespDTO.locationType mismatch: expected=" + LOCATION_TYPE_STORE
                            + ", got=" + location.getLocationType());
        }
    }

    /**
     * Verify StockItemQueryRespDTO valid conditions (00 §5.8.3 line 669).
     * id > 0, tenantId matches, skuCode matches, unit != null && !unit.isBlank().
     */
    private static void verifyStockItem(StockItemQueryRespDTO stockItem, long tenantId, String skuCode) {
        if (stockItem.getId() == null || stockItem.getId() <= 0) {
            throw new IllegalStateException(
                    "StockItemQueryRespDTO.id must be > 0, got id=" + stockItem.getId());
        }
        if (stockItem.getTenantId() == null || stockItem.getTenantId() != tenantId) {
            throw new IllegalStateException(
                    "StockItemQueryRespDTO.tenantId mismatch: expected=" + tenantId
                            + ", got=" + stockItem.getTenantId());
        }
        if (!skuCode.equals(stockItem.getSkuCode())) {
            throw new IllegalStateException(
                    "StockItemQueryRespDTO.skuCode mismatch: expected=" + skuCode
                            + ", got=" + stockItem.getSkuCode());
        }
        if (stockItem.getUnit() == null || stockItem.getUnit().isBlank()) {
            throw new IllegalStateException(
                    "StockItemQueryRespDTO.unit must be non-blank, got unit=" + stockItem.getUnit());
        }
    }
}
