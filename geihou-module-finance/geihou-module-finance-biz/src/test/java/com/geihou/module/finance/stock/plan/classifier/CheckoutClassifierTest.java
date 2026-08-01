package com.geihou.module.finance.stock.plan.classifier;

import com.geihou.module.finance.product.enums.StockStrategyEnum;
import com.geihou.module.finance.stock.plan.enums.CheckoutClassificationReason;
import com.geihou.module.finance.stock.plan.enums.CheckoutStockClassification;
import com.geihou.module.supplychain.api.bom.BomApi;
import com.geihou.module.supplychain.api.bom.dto.BomRecipeRespDTO;
import com.geihou.module.supplychain.api.stock.StockQueryApi;
import com.geihou.module.supplychain.api.stock.dto.StockItemQueryRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockLocationQueryRespDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CheckoutClassifier} (G0-04H185 SLICE-2C-2C-A1).
 *
 * <p>Coverage matrix (frozen by task package):
 * <ul>
 *   <li>Step 1a / 1b / 1a+1b simultaneous (1a wins) - no API calls.</li>
 *   <li>Step 2 UNLIMITED -> NON_BOM - no Bom/Stock calls.</li>
 *   <li>Step 3 BomApi contract: null DTO, null/unknown lookupStatus, all five
 *       lookupStatus id/productId field-combo violations.</li>
 *   <li>Step 4 NO_PRODUCT / AMBIGUOUS_PRODUCT -> UNMAPPED; INVALID_SKU_CODE -> ISE.</li>
 *   <li>Step 5 FOUND + location missing/present + each StockLocationQueryRespDTO
 *       validation field (id, tenantId, storeId, locationType).</li>
 *   <li>Step 6 NO_ACTIVE_RECIPE + location-first ordering + stockItem missing/present
 *       + each StockItemQueryRespDTO validation field (id, tenantId, skuCode, unit).</li>
 *   <li>Independent normalization: blank skuCode -> null; invalid stockStrategy -> null;
 *       both null simultaneously (reason=INVALID_SKU_CODE).</li>
 *   <li>Raw BomApi / StockQueryApi exception propagation; no swallow, no wrap.</li>
 *   <li>Unit propagation: NON_BOM+TRACK_STOCK carries unit; BOM/UNMAPPED/NON_BOM+UNLIMITED
 *       have stockItemUnit=null.</li>
 * </ul>
 */
class CheckoutClassifierTest {

    private static final long TENANT_ID = 1001L;
    private static final long STORE_ID = 2002L;
    private static final String SKU_CODE = "SKU-001";
    private static final String TRACK_STOCK = StockStrategyEnum.TRACK_STOCK.getCode();
    private static final String UNLIMITED = StockStrategyEnum.UNLIMITED.getCode();

    private BomApi bomApi;
    private StockQueryApi stockQueryApi;
    private CheckoutClassifier classifier;

    @BeforeEach
    void setUp() {
        bomApi = mock(BomApi.class);
        stockQueryApi = mock(StockQueryApi.class);
        classifier = new CheckoutClassifier(bomApi, stockQueryApi);
    }

    // ==================================================================
    // Constructor injection
    // ==================================================================

    @Test
    @DisplayName("constructor rejects null BomApi")
    void constructorRejectsNullBomApi() {
        assertThatThrownBy(() -> new CheckoutClassifier(null, stockQueryApi))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("bomApi");
    }

    @Test
    @DisplayName("constructor rejects null StockQueryApi")
    void constructorRejectsNullStockQueryApi() {
        assertThatThrownBy(() -> new CheckoutClassifier(bomApi, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("stockQueryApi");
    }

    // ==================================================================
    // Step 1a: skuCode null/blank -> UNMAPPED/INVALID_SKU_CODE (no API call)
    // ==================================================================

    @Nested
    @DisplayName("Step 1a: skuCode null/blank")
    class Step1aSkuCodeBlank {

        @Test
        @DisplayName("null skuCode -> UNMAPPED/INVALID_SKU_CODE; no API call")
        void nullSkuCode() {
            CheckoutClassificationResult r = classifier.classify(
                    TENANT_ID, STORE_ID, null, TRACK_STOCK);

            assertThat(r.classification()).isEqualTo(CheckoutStockClassification.UNMAPPED);
            assertThat(r.reason()).isEqualTo(CheckoutClassificationReason.INVALID_SKU_CODE);
            assertThat(r.skuCode()).isNull();
            assertThat(r.stockStrategy()).isEqualTo(TRACK_STOCK);
            assertThat(r.bomProductId()).isNull();
            assertThat(r.stockItemId()).isNull();
            assertThat(r.locationId()).isNull();
            assertThat(r.stockItemUnit()).isNull();

            verifyNoInteractions(bomApi, stockQueryApi);
        }

        @Test
        @DisplayName("blank skuCode '  ' -> UNMAPPED/INVALID_SKU_CODE; no API call")
        void blankSkuCode() {
            CheckoutClassificationResult r = classifier.classify(
                    TENANT_ID, STORE_ID, "  ", TRACK_STOCK);

            assertThat(r.classification()).isEqualTo(CheckoutStockClassification.UNMAPPED);
            assertThat(r.reason()).isEqualTo(CheckoutClassificationReason.INVALID_SKU_CODE);
            assertThat(r.skuCode()).isNull();
            assertThat(r.stockStrategy()).isEqualTo(TRACK_STOCK);

            verifyNoInteractions(bomApi, stockQueryApi);
        }

        @Test
        @DisplayName("empty skuCode '' -> UNMAPPED/INVALID_SKU_CODE; no API call")
        void emptySkuCode() {
            CheckoutClassificationResult r = classifier.classify(
                    TENANT_ID, STORE_ID, "", UNLIMITED);

            assertThat(r.classification()).isEqualTo(CheckoutStockClassification.UNMAPPED);
            assertThat(r.reason()).isEqualTo(CheckoutClassificationReason.INVALID_SKU_CODE);
            assertThat(r.skuCode()).isNull();
            // stockStrategy is preserved (UNLIMITED is valid)
            assertThat(r.stockStrategy()).isEqualTo(UNLIMITED);

            verifyNoInteractions(bomApi, stockQueryApi);
        }
    }

    // ==================================================================
    // Step 1b: stockStrategy invalid -> UNMAPPED/INVALID_STOCK_STRATEGY
    // ==================================================================

    @Nested
    @DisplayName("Step 1b: stockStrategy invalid")
    class Step1bStockStrategyInvalid {

        @Test
        @DisplayName("null stockStrategy -> UNMAPPED/INVALID_STOCK_STRATEGY; no API call")
        void nullStockStrategy() {
            CheckoutClassificationResult r = classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, null);

            assertThat(r.classification()).isEqualTo(CheckoutStockClassification.UNMAPPED);
            assertThat(r.reason()).isEqualTo(CheckoutClassificationReason.INVALID_STOCK_STRATEGY);
            assertThat(r.skuCode()).isEqualTo(SKU_CODE);
            assertThat(r.stockStrategy()).isNull();

            verifyNoInteractions(bomApi, stockQueryApi);
        }

        @Test
        @DisplayName("unknown stockStrategy 'UNKNOWN' -> UNMAPPED/INVALID_STOCK_STRATEGY")
        void unknownStockStrategy() {
            CheckoutClassificationResult r = classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, "UNKNOWN");

            assertThat(r.classification()).isEqualTo(CheckoutStockClassification.UNMAPPED);
            assertThat(r.reason()).isEqualTo(CheckoutClassificationReason.INVALID_STOCK_STRATEGY);
            assertThat(r.skuCode()).isEqualTo(SKU_CODE);
            assertThat(r.stockStrategy()).isNull();

            verifyNoInteractions(bomApi, stockQueryApi);
        }

        @Test
        @DisplayName("empty stockStrategy '' -> UNMAPPED/INVALID_STOCK_STRATEGY")
        void emptyStockStrategy() {
            CheckoutClassificationResult r = classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, "");

            assertThat(r.classification()).isEqualTo(CheckoutStockClassification.UNMAPPED);
            assertThat(r.reason()).isEqualTo(CheckoutClassificationReason.INVALID_STOCK_STRATEGY);
            assertThat(r.stockStrategy()).isNull();

            verifyNoInteractions(bomApi, stockQueryApi);
        }
    }

    // ==================================================================
    // Step 1a + 1b simultaneous: 1a wins (skuCode checked first)
    // ==================================================================

    @Test
    @DisplayName("both skuCode blank and stockStrategy invalid -> 1a wins: INVALID_SKU_CODE")
    void bothInvalid1aWins() {
        CheckoutClassificationResult r = classifier.classify(
                TENANT_ID, STORE_ID, "  ", "UNKNOWN");

        assertThat(r.classification()).isEqualTo(CheckoutStockClassification.UNMAPPED);
        // 1a wins: reason is INVALID_SKU_CODE, not INVALID_STOCK_STRATEGY
        assertThat(r.reason()).isEqualTo(CheckoutClassificationReason.INVALID_SKU_CODE);
        // Both normalized to null (independent normalization)
        assertThat(r.skuCode()).isNull();
        assertThat(r.stockStrategy()).isNull();

        verifyNoInteractions(bomApi, stockQueryApi);
    }

    // ==================================================================
    // Step 2: UNLIMITED -> NON_BOM (no Bom/Stock call)
    // ==================================================================

    @Nested
    @DisplayName("Step 2: UNLIMITED -> NON_BOM")
    class Step2Unlimited {

        @Test
        @DisplayName("UNLIMITED -> NON_BOM/null; no Bom/Stock call")
        void unlimitedReturnsNonBom() {
            CheckoutClassificationResult r = classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, UNLIMITED);

            assertThat(r.classification()).isEqualTo(CheckoutStockClassification.NON_BOM);
            assertThat(r.reason()).isNull();
            assertThat(r.skuCode()).isEqualTo(SKU_CODE);
            assertThat(r.stockStrategy()).isEqualTo(UNLIMITED);
            assertThat(r.bomProductId()).isNull();
            assertThat(r.stockItemId()).isNull();
            assertThat(r.locationId()).isNull();
            // NON_BOM + UNLIMITED does NOT carry unit (no StockItem call)
            assertThat(r.stockItemUnit()).isNull();

            verifyNoInteractions(bomApi, stockQueryApi);
        }

        @Test
        @DisplayName("UNLIMITED with blank skuCode -> 1a wins (INVALID_SKU_CODE)")
        void unlimitedWithBlankSkuCode1aWins() {
            CheckoutClassificationResult r = classifier.classify(
                    TENANT_ID, STORE_ID, "  ", UNLIMITED);

            assertThat(r.classification()).isEqualTo(CheckoutStockClassification.UNMAPPED);
            assertThat(r.reason()).isEqualTo(CheckoutClassificationReason.INVALID_SKU_CODE);
            // 1a wins means stockStrategy normalization still happens
            assertThat(r.stockStrategy()).isEqualTo(UNLIMITED);

            verifyNoInteractions(bomApi, stockQueryApi);
        }
    }

    // ==================================================================
    // Step 3: BomApi contract violations
    // ==================================================================

    @Nested
    @DisplayName("Step 3: BomApi contract")
    class Step3BomApiContract {

        @Test
        @DisplayName("BomApi returns null DTO -> IllegalStateException")
        void nullBomDtoThrows() {
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(null);

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("returned null DTO");

            verifyNoInteractions(stockQueryApi);
        }

        @Test
        @DisplayName("BomApi returns lookupStatus=null -> IllegalStateException")
        void nullLookupStatusThrows() {
            BomRecipeRespDTO bom = new BomRecipeRespDTO();
            bom.setLookupStatus(null);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("lookupStatus null/unknown");

            verifyNoInteractions(stockQueryApi);
        }

        @Test
        @DisplayName("BomApi returns unknown lookupStatus 'GARBAGE' -> IllegalStateException")
        void unknownLookupStatusThrows() {
            BomRecipeRespDTO bom = new BomRecipeRespDTO();
            bom.setLookupStatus("GARBAGE");
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("lookupStatus null/unknown");

            verifyNoInteractions(stockQueryApi);
        }

        @Test
        @DisplayName("BomApi throws RuntimeException -> propagates as-is, no StockQuery call")
        void bomApiThrowsPropagates() {
            RuntimeException raw = new RuntimeException("bom-db-down");
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenThrow(raw);

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isSameAs(raw);

            verifyNoInteractions(stockQueryApi);
        }
    }

    // ==================================================================
    // BomRecipeRespDTO field-combo matrix (5 lookupStatus x id/productId)
    // ==================================================================

    @Nested
    @DisplayName("BomRecipeRespDTO field-combo verification")
    class BomFieldComboMatrix {

        @Test
        @DisplayName("FOUND requires id>0: id=null -> ISE")
        void foundNullIdThrows() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_FOUND, null, 50L);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("FOUND requires id > 0");

            verifyNoInteractions(stockQueryApi);
        }

        @Test
        @DisplayName("FOUND requires id>0: id=0 -> ISE")
        void foundZeroIdThrows() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_FOUND, 0L, 50L);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("FOUND requires id > 0");

            verifyNoInteractions(stockQueryApi);
        }

        @Test
        @DisplayName("FOUND requires id>0: id=-1 -> ISE")
        void foundNegativeIdThrows() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_FOUND, -1L, 50L);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("FOUND requires id > 0");
        }

        @Test
        @DisplayName("FOUND requires productId>0: productId=null -> ISE")
        void foundNullProductIdThrows() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_FOUND, 10L, null);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("FOUND requires productId > 0");
        }

        @Test
        @DisplayName("FOUND requires productId>0: productId=0 -> ISE")
        void foundZeroProductIdThrows() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_FOUND, 10L, 0L);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("FOUND requires productId > 0");
        }

        @Test
        @DisplayName("FOUND requires productId>0: productId=-1 -> ISE (technical failure)")
        void foundNegativeProductIdThrows() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_FOUND, 10L, -1L);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("FOUND requires productId > 0");

            verifyNoInteractions(stockQueryApi);
        }

        @Test
        @DisplayName("NO_ACTIVE_RECIPE requires id==null: id=10 -> ISE")
        void noActiveRecipeNonNullIdThrows() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_NO_ACTIVE_RECIPE, 10L, 50L);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("NO_ACTIVE_RECIPE requires id == null");
        }

        @Test
        @DisplayName("NO_ACTIVE_RECIPE requires productId>0: productId=null -> ISE")
        void noActiveRecipeNullProductIdThrows() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_NO_ACTIVE_RECIPE, null, null);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("NO_ACTIVE_RECIPE requires productId > 0");
        }

        @Test
        @DisplayName("NO_ACTIVE_RECIPE requires productId>0: productId=0 -> ISE")
        void noActiveRecipeZeroProductIdThrows() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_NO_ACTIVE_RECIPE, null, 0L);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("NO_ACTIVE_RECIPE requires productId > 0");
        }

        @Test
        @DisplayName("NO_ACTIVE_RECIPE requires productId>0: productId=-1 -> ISE (technical failure)")
        void noActiveRecipeNegativeProductIdThrows() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_NO_ACTIVE_RECIPE, null, -1L);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("NO_ACTIVE_RECIPE requires productId > 0");

            verifyNoInteractions(stockQueryApi);
        }

        @Test
        @DisplayName("NO_PRODUCT requires id==null: id=10 -> ISE")
        void noProductNonNullIdThrows() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_NO_PRODUCT, 10L, null);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("NO_PRODUCT requires id == null");
        }

        @Test
        @DisplayName("NO_PRODUCT requires productId==null: productId=50 -> ISE")
        void noProductNonNullProductIdThrows() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_NO_PRODUCT, null, 50L);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("NO_PRODUCT requires productId == null");
        }

        @Test
        @DisplayName("AMBIGUOUS_PRODUCT requires id==null: id=10 -> ISE")
        void ambiguousNonNullIdThrows() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_AMBIGUOUS_PRODUCT, 10L, null);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("AMBIGUOUS_PRODUCT requires id == null");
        }

        @Test
        @DisplayName("AMBIGUOUS_PRODUCT requires productId==null: productId=50 -> ISE")
        void ambiguousNonNullProductIdThrows() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_AMBIGUOUS_PRODUCT, null, 50L);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("AMBIGUOUS_PRODUCT requires productId == null");
        }

        @Test
        @DisplayName("INVALID_SKU_CODE requires id==null: id=10 -> ISE")
        void invalidSkuCodeNonNullIdThrows() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_INVALID_SKU_CODE, 10L, null);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("INVALID_SKU_CODE requires id == null");
        }

        @Test
        @DisplayName("INVALID_SKU_CODE requires productId==null: productId=50 -> ISE")
        void invalidSkuCodeNonNullProductIdThrows() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_INVALID_SKU_CODE, null, 50L);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("INVALID_SKU_CODE requires productId == null");
        }
    }

    // ==================================================================
    // Step 4: NO_PRODUCT / AMBIGUOUS_PRODUCT / INVALID_SKU_CODE
    // ==================================================================

    @Nested
    @DisplayName("Step 4: query-failure statuses")
    class Step4QueryFailures {

        @Test
        @DisplayName("NO_PRODUCT -> UNMAPPED/NO_PRODUCT; no StockQuery call")
        void noProduct() {
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE))
                    .thenReturn(bom(LOOKUP_STATUS_NO_PRODUCT, null, null));

            CheckoutClassificationResult r = classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK);

            assertThat(r.classification()).isEqualTo(CheckoutStockClassification.UNMAPPED);
            assertThat(r.reason()).isEqualTo(CheckoutClassificationReason.NO_PRODUCT);
            assertThat(r.skuCode()).isEqualTo(SKU_CODE);
            assertThat(r.stockStrategy()).isEqualTo(TRACK_STOCK);
            assertThat(r.bomProductId()).isNull();
            assertThat(r.stockItemId()).isNull();
            assertThat(r.locationId()).isNull();
            assertThat(r.stockItemUnit()).isNull();

            verifyNoInteractions(stockQueryApi);
        }

        @Test
        @DisplayName("AMBIGUOUS_PRODUCT -> UNMAPPED/AMBIGUOUS_PRODUCT; no StockQuery call")
        void ambiguousProduct() {
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE))
                    .thenReturn(bom(LOOKUP_STATUS_AMBIGUOUS_PRODUCT, null, null));

            CheckoutClassificationResult r = classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK);

            assertThat(r.classification()).isEqualTo(CheckoutStockClassification.UNMAPPED);
            assertThat(r.reason()).isEqualTo(CheckoutClassificationReason.AMBIGUOUS_PRODUCT);
            assertThat(r.stockItemUnit()).isNull();

            verifyNoInteractions(stockQueryApi);
        }

        @Test
        @DisplayName("INVALID_SKU_CODE after 1a verified non-null -> ISE (technical failure, NOT UNMAPPED)")
        void invalidSkuCodeAfter1aThrows() {
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE))
                    .thenReturn(bom(LOOKUP_STATUS_INVALID_SKU_CODE, null, null));

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("INVALID_SKU_CODE after step 1a verified non-null");

            verifyNoInteractions(stockQueryApi);
        }
    }

    // ==================================================================
    // Step 5: FOUND -> BOM or NO_LOCATION
    // ==================================================================

    @Nested
    @DisplayName("Step 5: FOUND path")
    class Step5Found {

        @Test
        @DisplayName("FOUND + location present -> BOM with bomProductId + locationId; no stockItem call")
        void foundLocationPresentReturnsBom() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_FOUND, 10L, 50L);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);
            StockLocationQueryRespDTO location = location(300L, STORE_ID, "STORE", TENANT_ID);
            when(stockQueryApi.getStockLocationByStoreId(TENANT_ID, STORE_ID, "STORE"))
                    .thenReturn(location);

            CheckoutClassificationResult r = classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK);

            assertThat(r.classification()).isEqualTo(CheckoutStockClassification.BOM);
            assertThat(r.reason()).isNull();
            assertThat(r.skuCode()).isEqualTo(SKU_CODE);
            assertThat(r.stockStrategy()).isEqualTo(TRACK_STOCK);
            assertThat(r.bomProductId()).isEqualTo(50L);
            assertThat(r.locationId()).isEqualTo(300L);
            // BOM does NOT carry stockItemId
            assertThat(r.stockItemId()).isNull();
            // BOM does NOT carry stockItemUnit
            assertThat(r.stockItemUnit()).isNull();

            // Never call stockItem in BOM path
            verify(stockQueryApi, never()).getStockItemBySkuCode(TENANT_ID, SKU_CODE);
        }

        @Test
        @DisplayName("FOUND + location missing -> UNMAPPED/NO_LOCATION; no stockItem call")
        void foundLocationMissingReturnsNoLocation() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_FOUND, 10L, 50L);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);
            when(stockQueryApi.getStockLocationByStoreId(TENANT_ID, STORE_ID, "STORE"))
                    .thenReturn(null);

            CheckoutClassificationResult r = classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK);

            assertThat(r.classification()).isEqualTo(CheckoutStockClassification.UNMAPPED);
            assertThat(r.reason()).isEqualTo(CheckoutClassificationReason.NO_LOCATION);
            assertThat(r.bomProductId()).isNull();
            assertThat(r.stockItemId()).isNull();
            assertThat(r.locationId()).isNull();
            assertThat(r.stockItemUnit()).isNull();

            verify(stockQueryApi, never()).getStockItemBySkuCode(TENANT_ID, SKU_CODE);
        }

        // ----- StockLocationQueryRespDTO field validations -----

        @Test
        @DisplayName("FOUND + location.id=null -> ISE")
        void foundLocationNullIdThrows() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_FOUND, 10L, 50L);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);
            when(stockQueryApi.getStockLocationByStoreId(TENANT_ID, STORE_ID, "STORE"))
                    .thenReturn(location(null, STORE_ID, "STORE", TENANT_ID));

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("StockLocationQueryRespDTO.id must be > 0");
        }

        @Test
        @DisplayName("FOUND + location.id=0 -> ISE")
        void foundLocationZeroIdThrows() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_FOUND, 10L, 50L);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);
            when(stockQueryApi.getStockLocationByStoreId(TENANT_ID, STORE_ID, "STORE"))
                    .thenReturn(location(0L, STORE_ID, "STORE", TENANT_ID));

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("StockLocationQueryRespDTO.id must be > 0");
        }

        @Test
        @DisplayName("FOUND + location.id=-1 -> ISE")
        void foundLocationNegativeIdThrows() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_FOUND, 10L, 50L);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);
            when(stockQueryApi.getStockLocationByStoreId(TENANT_ID, STORE_ID, "STORE"))
                    .thenReturn(location(-1L, STORE_ID, "STORE", TENANT_ID));

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("StockLocationQueryRespDTO.id must be > 0");
        }

        @Test
        @DisplayName("FOUND + location.tenantId mismatch -> ISE")
        void foundLocationTenantMismatchThrows() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_FOUND, 10L, 50L);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);
            when(stockQueryApi.getStockLocationByStoreId(TENANT_ID, STORE_ID, "STORE"))
                    .thenReturn(location(300L, STORE_ID, "STORE", 9999L));

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("StockLocationQueryRespDTO.tenantId mismatch");
        }

        @Test
        @DisplayName("FOUND + location.tenantId=null -> ISE")
        void foundLocationNullTenantThrows() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_FOUND, 10L, 50L);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);
            when(stockQueryApi.getStockLocationByStoreId(TENANT_ID, STORE_ID, "STORE"))
                    .thenReturn(location(300L, STORE_ID, "STORE", null));

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("StockLocationQueryRespDTO.tenantId mismatch");
        }

        @Test
        @DisplayName("FOUND + location.storeId mismatch -> ISE")
        void foundLocationStoreMismatchThrows() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_FOUND, 10L, 50L);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);
            when(stockQueryApi.getStockLocationByStoreId(TENANT_ID, STORE_ID, "STORE"))
                    .thenReturn(location(300L, 8888L, "STORE", TENANT_ID));

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("StockLocationQueryRespDTO.storeId mismatch");
        }

        @Test
        @DisplayName("FOUND + location.storeId=null -> ISE")
        void foundLocationNullStoreThrows() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_FOUND, 10L, 50L);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);
            when(stockQueryApi.getStockLocationByStoreId(TENANT_ID, STORE_ID, "STORE"))
                    .thenReturn(location(300L, null, "STORE", TENANT_ID));

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("StockLocationQueryRespDTO.storeId mismatch");
        }

        @Test
        @DisplayName("FOUND + location.locationType='WAREHOUSE' -> ISE (must be STORE)")
        void foundLocationWrongTypeThrows() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_FOUND, 10L, 50L);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);
            when(stockQueryApi.getStockLocationByStoreId(TENANT_ID, STORE_ID, "STORE"))
                    .thenReturn(location(300L, STORE_ID, "WAREHOUSE", TENANT_ID));

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("StockLocationQueryRespDTO.locationType mismatch");
        }

        @Test
        @DisplayName("FOUND + location.locationType=null -> ISE")
        void foundLocationNullTypeThrows() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_FOUND, 10L, 50L);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);
            when(stockQueryApi.getStockLocationByStoreId(TENANT_ID, STORE_ID, "STORE"))
                    .thenReturn(location(300L, STORE_ID, null, TENANT_ID));

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("StockLocationQueryRespDTO.locationType mismatch");
        }

        @Test
        @DisplayName("FOUND + StockQueryApi.getStockLocationByStoreId throws -> propagates as-is")
        void foundLocationApiThrowsPropagates() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_FOUND, 10L, 50L);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);
            RuntimeException raw = new RuntimeException("stock-db-down");
            when(stockQueryApi.getStockLocationByStoreId(TENANT_ID, STORE_ID, "STORE"))
                    .thenThrow(raw);

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isSameAs(raw);

            verify(stockQueryApi, never()).getStockItemBySkuCode(TENANT_ID, SKU_CODE);
        }
    }

    // ==================================================================
    // Step 6: NO_ACTIVE_RECIPE -> NON_BOM or NO_LOCATION / NO_STOCK_ITEM
    // ==================================================================

    @Nested
    @DisplayName("Step 6: NO_ACTIVE_RECIPE path (location-first ordering)")
    class Step6NoActiveRecipe {

        @Test
        @DisplayName("NO_ACTIVE_RECIPE + location missing -> UNMAPPED/NO_LOCATION; stockItem NEVER called (order enforced)")
        void noActiveRecipeLocationMissingSkipsStockItem() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_NO_ACTIVE_RECIPE, null, 50L);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);
            when(stockQueryApi.getStockLocationByStoreId(TENANT_ID, STORE_ID, "STORE"))
                    .thenReturn(null);

            CheckoutClassificationResult r = classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK);

            assertThat(r.classification()).isEqualTo(CheckoutStockClassification.UNMAPPED);
            assertThat(r.reason()).isEqualTo(CheckoutClassificationReason.NO_LOCATION);
            assertThat(r.bomProductId()).isNull();
            assertThat(r.stockItemId()).isNull();
            assertThat(r.locationId()).isNull();
            assertThat(r.stockItemUnit()).isNull();

            // CRITICAL: location missing means stockItem is NEVER called
            verify(stockQueryApi, never()).getStockItemBySkuCode(TENANT_ID, SKU_CODE);
        }

        @Test
        @DisplayName("NO_ACTIVE_RECIPE + location present + stockItem missing -> UNMAPPED/NO_STOCK_ITEM")
        void noActiveRecipeStockItemMissing() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_NO_ACTIVE_RECIPE, null, 50L);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);
            when(stockQueryApi.getStockLocationByStoreId(TENANT_ID, STORE_ID, "STORE"))
                    .thenReturn(location(300L, STORE_ID, "STORE", TENANT_ID));
            when(stockQueryApi.getStockItemBySkuCode(TENANT_ID, SKU_CODE))
                    .thenReturn(null);

            CheckoutClassificationResult r = classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK);

            assertThat(r.classification()).isEqualTo(CheckoutStockClassification.UNMAPPED);
            assertThat(r.reason()).isEqualTo(CheckoutClassificationReason.NO_STOCK_ITEM);
            assertThat(r.bomProductId()).isNull();
            assertThat(r.stockItemId()).isNull();
            // location was present but stockItem missing - locationId stays null
            assertThat(r.locationId()).isNull();
            assertThat(r.stockItemUnit()).isNull();
        }

        @Test
        @DisplayName("NO_ACTIVE_RECIPE + location + stockItem present -> NON_BOM with stockItemId + locationId + unit")
        void noActiveRecipeAllPresentReturnsNonBomWithUnit() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_NO_ACTIVE_RECIPE, null, 50L);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);
            when(stockQueryApi.getStockLocationByStoreId(TENANT_ID, STORE_ID, "STORE"))
                    .thenReturn(location(300L, STORE_ID, "STORE", TENANT_ID));
            when(stockQueryApi.getStockItemBySkuCode(TENANT_ID, SKU_CODE))
                    .thenReturn(stockItem(400L, SKU_CODE, TENANT_ID, "KG"));

            CheckoutClassificationResult r = classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK);

            assertThat(r.classification()).isEqualTo(CheckoutStockClassification.NON_BOM);
            assertThat(r.reason()).isNull();
            assertThat(r.skuCode()).isEqualTo(SKU_CODE);
            assertThat(r.stockStrategy()).isEqualTo(TRACK_STOCK);
            // NO_ACTIVE_RECIPE: bomProductId is null (id==null, productId>0 but not BOM)
            assertThat(r.bomProductId()).isNull();
            assertThat(r.stockItemId()).isEqualTo(400L);
            assertThat(r.locationId()).isEqualTo(300L);
            // CRITICAL: unit propagates from StockQueryApi call (NOT re-queried later)
            assertThat(r.stockItemUnit()).isEqualTo("KG");
        }

        @Test
        @DisplayName("NO_ACTIVE_RECIPE: location called BEFORE stockItem (InOrder)")
        void noActiveRecipeLocationBeforeStockItemOrder() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_NO_ACTIVE_RECIPE, null, 50L);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);
            when(stockQueryApi.getStockLocationByStoreId(TENANT_ID, STORE_ID, "STORE"))
                    .thenReturn(location(300L, STORE_ID, "STORE", TENANT_ID));
            when(stockQueryApi.getStockItemBySkuCode(TENANT_ID, SKU_CODE))
                    .thenReturn(stockItem(400L, SKU_CODE, TENANT_ID, "KG"));

            classifier.classify(TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK);

            org.mockito.InOrder order = inOrder(stockQueryApi);
            order.verify(stockQueryApi).getStockLocationByStoreId(TENANT_ID, STORE_ID, "STORE");
            order.verify(stockQueryApi).getStockItemBySkuCode(TENANT_ID, SKU_CODE);
            order.verifyNoMoreInteractions();
        }

        // ----- StockItemQueryRespDTO field validations -----

        @Test
        @DisplayName("NO_ACTIVE_RECIPE + stockItem.id=null -> ISE")
        void noActiveRecipeStockItemNullIdThrows() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_NO_ACTIVE_RECIPE, null, 50L);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);
            when(stockQueryApi.getStockLocationByStoreId(TENANT_ID, STORE_ID, "STORE"))
                    .thenReturn(location(300L, STORE_ID, "STORE", TENANT_ID));
            when(stockQueryApi.getStockItemBySkuCode(TENANT_ID, SKU_CODE))
                    .thenReturn(stockItem(null, SKU_CODE, TENANT_ID, "KG"));

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("StockItemQueryRespDTO.id must be > 0");
        }

        @Test
        @DisplayName("NO_ACTIVE_RECIPE + stockItem.id=0 -> ISE")
        void noActiveRecipeStockItemZeroIdThrows() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_NO_ACTIVE_RECIPE, null, 50L);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);
            when(stockQueryApi.getStockLocationByStoreId(TENANT_ID, STORE_ID, "STORE"))
                    .thenReturn(location(300L, STORE_ID, "STORE", TENANT_ID));
            when(stockQueryApi.getStockItemBySkuCode(TENANT_ID, SKU_CODE))
                    .thenReturn(stockItem(0L, SKU_CODE, TENANT_ID, "KG"));

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("StockItemQueryRespDTO.id must be > 0");
        }

        @Test
        @DisplayName("NO_ACTIVE_RECIPE + stockItem.id=-1 -> ISE")
        void noActiveRecipeStockItemNegativeIdThrows() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_NO_ACTIVE_RECIPE, null, 50L);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);
            when(stockQueryApi.getStockLocationByStoreId(TENANT_ID, STORE_ID, "STORE"))
                    .thenReturn(location(300L, STORE_ID, "STORE", TENANT_ID));
            when(stockQueryApi.getStockItemBySkuCode(TENANT_ID, SKU_CODE))
                    .thenReturn(stockItem(-1L, SKU_CODE, TENANT_ID, "KG"));

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("StockItemQueryRespDTO.id must be > 0");
        }

        @Test
        @DisplayName("NO_ACTIVE_RECIPE + stockItem.tenantId mismatch -> ISE")
        void noActiveRecipeStockItemTenantMismatchThrows() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_NO_ACTIVE_RECIPE, null, 50L);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);
            when(stockQueryApi.getStockLocationByStoreId(TENANT_ID, STORE_ID, "STORE"))
                    .thenReturn(location(300L, STORE_ID, "STORE", TENANT_ID));
            when(stockQueryApi.getStockItemBySkuCode(TENANT_ID, SKU_CODE))
                    .thenReturn(stockItem(400L, SKU_CODE, 9999L, "KG"));

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("StockItemQueryRespDTO.tenantId mismatch");
        }

        @Test
        @DisplayName("NO_ACTIVE_RECIPE + stockItem.tenantId=null -> ISE")
        void noActiveRecipeStockItemNullTenantThrows() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_NO_ACTIVE_RECIPE, null, 50L);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);
            when(stockQueryApi.getStockLocationByStoreId(TENANT_ID, STORE_ID, "STORE"))
                    .thenReturn(location(300L, STORE_ID, "STORE", TENANT_ID));
            when(stockQueryApi.getStockItemBySkuCode(TENANT_ID, SKU_CODE))
                    .thenReturn(stockItem(400L, SKU_CODE, null, "KG"));

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("StockItemQueryRespDTO.tenantId mismatch");
        }

        @Test
        @DisplayName("NO_ACTIVE_RECIPE + stockItem.skuCode mismatch -> ISE")
        void noActiveRecipeStockItemSkuMismatchThrows() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_NO_ACTIVE_RECIPE, null, 50L);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);
            when(stockQueryApi.getStockLocationByStoreId(TENANT_ID, STORE_ID, "STORE"))
                    .thenReturn(location(300L, STORE_ID, "STORE", TENANT_ID));
            when(stockQueryApi.getStockItemBySkuCode(TENANT_ID, SKU_CODE))
                    .thenReturn(stockItem(400L, "SKU-OTHER", TENANT_ID, "KG"));

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("StockItemQueryRespDTO.skuCode mismatch");
        }

        @Test
        @DisplayName("NO_ACTIVE_RECIPE + stockItem.unit=null -> ISE")
        void noActiveRecipeStockItemNullUnitThrows() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_NO_ACTIVE_RECIPE, null, 50L);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);
            when(stockQueryApi.getStockLocationByStoreId(TENANT_ID, STORE_ID, "STORE"))
                    .thenReturn(location(300L, STORE_ID, "STORE", TENANT_ID));
            when(stockQueryApi.getStockItemBySkuCode(TENANT_ID, SKU_CODE))
                    .thenReturn(stockItem(400L, SKU_CODE, TENANT_ID, null));

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("StockItemQueryRespDTO.unit must be non-blank");
        }

        @Test
        @DisplayName("NO_ACTIVE_RECIPE + stockItem.unit='' -> ISE")
        void noActiveRecipeStockItemEmptyUnitThrows() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_NO_ACTIVE_RECIPE, null, 50L);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);
            when(stockQueryApi.getStockLocationByStoreId(TENANT_ID, STORE_ID, "STORE"))
                    .thenReturn(location(300L, STORE_ID, "STORE", TENANT_ID));
            when(stockQueryApi.getStockItemBySkuCode(TENANT_ID, SKU_CODE))
                    .thenReturn(stockItem(400L, SKU_CODE, TENANT_ID, ""));

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("StockItemQueryRespDTO.unit must be non-blank");
        }

        @Test
        @DisplayName("NO_ACTIVE_RECIPE + stockItem.unit='  ' -> ISE")
        void noActiveRecipeStockItemBlankUnitThrows() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_NO_ACTIVE_RECIPE, null, 50L);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);
            when(stockQueryApi.getStockLocationByStoreId(TENANT_ID, STORE_ID, "STORE"))
                    .thenReturn(location(300L, STORE_ID, "STORE", TENANT_ID));
            when(stockQueryApi.getStockItemBySkuCode(TENANT_ID, SKU_CODE))
                    .thenReturn(stockItem(400L, SKU_CODE, TENANT_ID, "  "));

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("StockItemQueryRespDTO.unit must be non-blank");
        }

        @Test
        @DisplayName("NO_ACTIVE_RECIPE + getStockItemBySkuCode throws -> propagates as-is")
        void noActiveRecipeStockItemApiThrowsPropagates() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_NO_ACTIVE_RECIPE, null, 50L);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);
            when(stockQueryApi.getStockLocationByStoreId(TENANT_ID, STORE_ID, "STORE"))
                    .thenReturn(location(300L, STORE_ID, "STORE", TENANT_ID));
            RuntimeException raw = new RuntimeException("stock-item-db-down");
            when(stockQueryApi.getStockItemBySkuCode(TENANT_ID, SKU_CODE)).thenThrow(raw);

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isSameAs(raw);
        }

        @Test
        @DisplayName("NO_ACTIVE_RECIPE + getStockLocationByStoreId throws -> propagates; stockItem NOT called")
        void noActiveRecipeLocationApiThrowsPropagates() {
            BomRecipeRespDTO bom = bom(LOOKUP_STATUS_NO_ACTIVE_RECIPE, null, 50L);
            when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE)).thenReturn(bom);
            RuntimeException raw = new RuntimeException("location-db-down");
            when(stockQueryApi.getStockLocationByStoreId(TENANT_ID, STORE_ID, "STORE"))
                    .thenThrow(raw);

            assertThatThrownBy(() -> classifier.classify(
                    TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK))
                    .isSameAs(raw);

            verify(stockQueryApi, never()).getStockItemBySkuCode(TENANT_ID, SKU_CODE);
        }
    }

    // ==================================================================
    // Cross-cutting: api call count + verify arg passing
    // ==================================================================

    @Test
    @DisplayName("Step 3 BomApi called with normalized skuCode + tenantId")
    void bomApiCalledWithNormalizedArgs() {
        when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE))
                .thenReturn(bom(LOOKUP_STATUS_NO_PRODUCT, null, null));

        classifier.classify(TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK);

        verify(bomApi).getActiveRecipeBySkuCode(eq(TENANT_ID), eq(SKU_CODE));
    }

    @Test
    @DisplayName("Step 5 BomApi call happens before StockQuery (InOrder)")
    void step5BomBeforeLocationOrder() {
        when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE))
                .thenReturn(bom(LOOKUP_STATUS_FOUND, 10L, 50L));
        when(stockQueryApi.getStockLocationByStoreId(TENANT_ID, STORE_ID, "STORE"))
                .thenReturn(location(300L, STORE_ID, "STORE", TENANT_ID));

        classifier.classify(TENANT_ID, STORE_ID, SKU_CODE, TRACK_STOCK);

        org.mockito.InOrder order = inOrder(bomApi, stockQueryApi);
        order.verify(bomApi).getActiveRecipeBySkuCode(TENANT_ID, SKU_CODE);
        order.verify(stockQueryApi).getStockLocationByStoreId(TENANT_ID, STORE_ID, "STORE");
        order.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName("skuCode with leading/trailing spaces ' SKU-001 ' is preserved as-is (not trimmed)")
    void skuCodeWithSpacesPreserved() {
        // A skuCode with leading/trailing spaces that is NOT blank (has content)
        // is preserved as-is - classifier does not trim, only nulls on fully-blank.
        String skuWithSpaces = " SKU-001 ";
        when(bomApi.getActiveRecipeBySkuCode(TENANT_ID, skuWithSpaces))
                .thenReturn(bom(LOOKUP_STATUS_NO_PRODUCT, null, null));

        CheckoutClassificationResult r = classifier.classify(
                TENANT_ID, STORE_ID, skuWithSpaces, TRACK_STOCK);

        // Passed to BomApi verbatim
        verify(bomApi).getActiveRecipeBySkuCode(TENANT_ID, skuWithSpaces);
        // Returned in result verbatim
        assertThat(r.skuCode()).isEqualTo(skuWithSpaces);
    }

    // ==================================================================
    // CheckoutClassificationResult compact-constructor matrix
    // ==================================================================

    @Nested
    @DisplayName("CheckoutClassificationResult constructor matrix")
    class ResultConstructorMatrix {

        // ----- Valid combinations (must NOT throw) -----

        @Test
        @DisplayName("BOM valid row: reason=null, skuCode non-null, TRACK_STOCK, bomProductId>0, locationId>0, unit=null")
        void validBomRow() {
            CheckoutClassificationResult r = new CheckoutClassificationResult(
                    CheckoutStockClassification.BOM, null,
                    SKU_CODE, TRACK_STOCK,
                    50L, null, 300L, null);

            assertThat(r.classification()).isEqualTo(CheckoutStockClassification.BOM);
            assertThat(r.bomProductId()).isEqualTo(50L);
            assertThat(r.locationId()).isEqualTo(300L);
            assertThat(r.stockItemUnit()).isNull();
        }

        @Test
        @DisplayName("NON_BOM+UNLIMITED valid row: all IDs/unit null")
        void validNonBomUnlimitedRow() {
            CheckoutClassificationResult r = new CheckoutClassificationResult(
                    CheckoutStockClassification.NON_BOM, null,
                    SKU_CODE, UNLIMITED,
                    null, null, null, null);

            assertThat(r.classification()).isEqualTo(CheckoutStockClassification.NON_BOM);
            assertThat(r.stockStrategy()).isEqualTo(UNLIMITED);
            assertThat(r.bomProductId()).isNull();
            assertThat(r.stockItemId()).isNull();
            assertThat(r.locationId()).isNull();
            assertThat(r.stockItemUnit()).isNull();
        }

        @Test
        @DisplayName("NON_BOM+TRACK_STOCK valid row: stockItemId>0, locationId>0, unit non-blank")
        void validNonBomTrackStockRow() {
            CheckoutClassificationResult r = new CheckoutClassificationResult(
                    CheckoutStockClassification.NON_BOM, null,
                    SKU_CODE, TRACK_STOCK,
                    null, 400L, 300L, "KG");

            assertThat(r.classification()).isEqualTo(CheckoutStockClassification.NON_BOM);
            assertThat(r.stockItemId()).isEqualTo(400L);
            assertThat(r.locationId()).isEqualTo(300L);
            assertThat(r.stockItemUnit()).isEqualTo("KG");
        }

        @Test
        @DisplayName("UNMAPPED valid: INVALID_SKU_CODE + skuCode=null, stockStrategy=null (1a+1b both fail)")
        void validUnmappedInvalidSkuCodeBothNull() {
            new CheckoutClassificationResult(
                    CheckoutStockClassification.UNMAPPED,
                    CheckoutClassificationReason.INVALID_SKU_CODE,
                    null, null, null, null, null, null);
        }

        @Test
        @DisplayName("UNMAPPED valid: INVALID_SKU_CODE + skuCode=null, stockStrategy=TRACK_STOCK (1a fail, strategy valid)")
        void validUnmappedInvalidSkuCodeTrackStock() {
            CheckoutClassificationResult r = new CheckoutClassificationResult(
                    CheckoutStockClassification.UNMAPPED,
                    CheckoutClassificationReason.INVALID_SKU_CODE,
                    null, TRACK_STOCK, null, null, null, null);
            assertThat(r.stockStrategy()).isEqualTo(TRACK_STOCK);
        }

        @Test
        @DisplayName("UNMAPPED valid: INVALID_SKU_CODE + skuCode=null, stockStrategy=UNLIMITED (1a fail with valid UNLIMITED)")
        void validUnmappedInvalidSkuCodeUnlimited() {
            CheckoutClassificationResult r = new CheckoutClassificationResult(
                    CheckoutStockClassification.UNMAPPED,
                    CheckoutClassificationReason.INVALID_SKU_CODE,
                    null, UNLIMITED, null, null, null, null);
            assertThat(r.stockStrategy()).isEqualTo(UNLIMITED);
        }

        @Test
        @DisplayName("UNMAPPED valid: INVALID_STOCK_STRATEGY + skuCode non-null, stockStrategy=null")
        void validUnmappedInvalidStockStrategy() {
            CheckoutClassificationResult r = new CheckoutClassificationResult(
                    CheckoutStockClassification.UNMAPPED,
                    CheckoutClassificationReason.INVALID_STOCK_STRATEGY,
                    SKU_CODE, null, null, null, null, null);
            assertThat(r.skuCode()).isEqualTo(SKU_CODE);
            assertThat(r.stockStrategy()).isNull();
        }

        @Test
        @DisplayName("UNMAPPED valid: NO_PRODUCT + skuCode non-null, stockStrategy=TRACK_STOCK")
        void validUnmappedNoProduct() {
            new CheckoutClassificationResult(
                    CheckoutStockClassification.UNMAPPED,
                    CheckoutClassificationReason.NO_PRODUCT,
                    SKU_CODE, TRACK_STOCK, null, null, null, null);
        }

        @Test
        @DisplayName("UNMAPPED valid: AMBIGUOUS_PRODUCT + skuCode non-null, stockStrategy=TRACK_STOCK")
        void validUnmappedAmbiguousProduct() {
            new CheckoutClassificationResult(
                    CheckoutStockClassification.UNMAPPED,
                    CheckoutClassificationReason.AMBIGUOUS_PRODUCT,
                    SKU_CODE, TRACK_STOCK, null, null, null, null);
        }

        @Test
        @DisplayName("UNMAPPED valid: NO_STOCK_ITEM + skuCode non-null, stockStrategy=TRACK_STOCK")
        void validUnmappedNoStockItem() {
            new CheckoutClassificationResult(
                    CheckoutStockClassification.UNMAPPED,
                    CheckoutClassificationReason.NO_STOCK_ITEM,
                    SKU_CODE, TRACK_STOCK, null, null, null, null);
        }

        @Test
        @DisplayName("UNMAPPED valid: NO_LOCATION + skuCode non-null, stockStrategy=TRACK_STOCK")
        void validUnmappedNoLocation() {
            new CheckoutClassificationResult(
                    CheckoutStockClassification.UNMAPPED,
                    CheckoutClassificationReason.NO_LOCATION,
                    SKU_CODE, TRACK_STOCK, null, null, null, null);
        }

        // ----- UNMAPPED invalid reason-bound cross-combinations -----

        @Test
        @DisplayName("UNMAPPED INVALID_SKU_CODE + skuCode non-null -> IAE")
        void unmappedInvalidSkuCodeNonNullSkuCodeThrows() {
            assertThatThrownBy(() -> new CheckoutClassificationResult(
                    CheckoutStockClassification.UNMAPPED,
                    CheckoutClassificationReason.INVALID_SKU_CODE,
                    SKU_CODE, TRACK_STOCK, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("skuCode must be null for INVALID_SKU_CODE");
        }

        @Test
        @DisplayName("UNMAPPED INVALID_STOCK_STRATEGY + skuCode null -> IAE")
        void unmappedInvalidStockStrategyNullSkuCodeThrows() {
            assertThatThrownBy(() -> new CheckoutClassificationResult(
                    CheckoutStockClassification.UNMAPPED,
                    CheckoutClassificationReason.INVALID_STOCK_STRATEGY,
                    null, null, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("skuCode must be non-null for INVALID_STOCK_STRATEGY");
        }

        @Test
        @DisplayName("UNMAPPED INVALID_STOCK_STRATEGY + stockStrategy=TRACK_STOCK -> IAE")
        void unmappedInvalidStockStrategyTrackStockThrows() {
            assertThatThrownBy(() -> new CheckoutClassificationResult(
                    CheckoutStockClassification.UNMAPPED,
                    CheckoutClassificationReason.INVALID_STOCK_STRATEGY,
                    SKU_CODE, TRACK_STOCK, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("stockStrategy must be null for INVALID_STOCK_STRATEGY");
        }

        @Test
        @DisplayName("UNMAPPED INVALID_STOCK_STRATEGY + stockStrategy=UNLIMITED -> IAE")
        void unmappedInvalidStockStrategyUnlimitedThrows() {
            assertThatThrownBy(() -> new CheckoutClassificationResult(
                    CheckoutStockClassification.UNMAPPED,
                    CheckoutClassificationReason.INVALID_STOCK_STRATEGY,
                    SKU_CODE, UNLIMITED, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("stockStrategy must be null for INVALID_STOCK_STRATEGY");
        }

        @Test
        @DisplayName("UNMAPPED NO_PRODUCT + skuCode null -> IAE")
        void unmappedNoProductNullSkuCodeThrows() {
            assertThatThrownBy(() -> new CheckoutClassificationResult(
                    CheckoutStockClassification.UNMAPPED,
                    CheckoutClassificationReason.NO_PRODUCT,
                    null, TRACK_STOCK, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("skuCode must be non-null for NO_PRODUCT");
        }

        @Test
        @DisplayName("UNMAPPED NO_PRODUCT + stockStrategy null -> IAE")
        void unmappedNoProductNullStrategyThrows() {
            assertThatThrownBy(() -> new CheckoutClassificationResult(
                    CheckoutStockClassification.UNMAPPED,
                    CheckoutClassificationReason.NO_PRODUCT,
                    SKU_CODE, null, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("stockStrategy must be TRACK_STOCK for NO_PRODUCT");
        }

        @Test
        @DisplayName("UNMAPPED NO_PRODUCT + stockStrategy=UNLIMITED -> IAE")
        void unmappedNoProductUnlimitedThrows() {
            assertThatThrownBy(() -> new CheckoutClassificationResult(
                    CheckoutStockClassification.UNMAPPED,
                    CheckoutClassificationReason.NO_PRODUCT,
                    SKU_CODE, UNLIMITED, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("stockStrategy must be TRACK_STOCK for NO_PRODUCT");
        }

        @Test
        @DisplayName("UNMAPPED AMBIGUOUS_PRODUCT + skuCode null -> IAE")
        void unmappedAmbiguousNullSkuCodeThrows() {
            assertThatThrownBy(() -> new CheckoutClassificationResult(
                    CheckoutStockClassification.UNMAPPED,
                    CheckoutClassificationReason.AMBIGUOUS_PRODUCT,
                    null, TRACK_STOCK, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("skuCode must be non-null for AMBIGUOUS_PRODUCT");
        }

        @Test
        @DisplayName("UNMAPPED NO_STOCK_ITEM + skuCode null -> IAE")
        void unmappedNoStockItemNullSkuCodeThrows() {
            assertThatThrownBy(() -> new CheckoutClassificationResult(
                    CheckoutStockClassification.UNMAPPED,
                    CheckoutClassificationReason.NO_STOCK_ITEM,
                    null, TRACK_STOCK, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("skuCode must be non-null for NO_STOCK_ITEM");
        }

        @Test
        @DisplayName("UNMAPPED NO_LOCATION + skuCode null -> IAE")
        void unmappedNoLocationNullSkuCodeThrows() {
            assertThatThrownBy(() -> new CheckoutClassificationResult(
                    CheckoutStockClassification.UNMAPPED,
                    CheckoutClassificationReason.NO_LOCATION,
                    null, TRACK_STOCK, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("skuCode must be non-null for NO_LOCATION");
        }

        // ----- BOM invalid combinations -----

        @Test
        @DisplayName("BOM with non-null reason -> IAE")
        void bomWithReasonThrows() {
            assertThatThrownBy(() -> new CheckoutClassificationResult(
                    CheckoutStockClassification.BOM,
                    CheckoutClassificationReason.NO_PRODUCT,
                    SKU_CODE, TRACK_STOCK, 50L, null, 300L, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reason must be null for BOM");
        }

        @Test
        @DisplayName("BOM with null skuCode -> IAE")
        void bomNullSkuCodeThrows() {
            assertThatThrownBy(() -> new CheckoutClassificationResult(
                    CheckoutStockClassification.BOM, null,
                    null, TRACK_STOCK, 50L, null, 300L, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("skuCode must be non-blank");
        }

        @Test
        @DisplayName("BOM with UNLIMITED strategy -> IAE")
        void bomWrongStrategyThrows() {
            assertThatThrownBy(() -> new CheckoutClassificationResult(
                    CheckoutStockClassification.BOM, null,
                    SKU_CODE, UNLIMITED, 50L, null, 300L, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("stockStrategy must be TRACK_STOCK for BOM");
        }

        @Test
        @DisplayName("BOM with null bomProductId -> IAE")
        void bomNullProductIdThrows() {
            assertThatThrownBy(() -> new CheckoutClassificationResult(
                    CheckoutStockClassification.BOM, null,
                    SKU_CODE, TRACK_STOCK, null, null, 300L, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("bomProductId must be > 0 for BOM");
        }

        @Test
        @DisplayName("BOM with non-null stockItemId -> IAE")
        void bomNonNullStockItemIdThrows() {
            assertThatThrownBy(() -> new CheckoutClassificationResult(
                    CheckoutStockClassification.BOM, null,
                    SKU_CODE, TRACK_STOCK, 50L, 400L, 300L, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("stockItemId must be null for BOM");
        }

        @Test
        @DisplayName("BOM with non-null stockItemUnit -> IAE")
        void bomNonNullUnitThrows() {
            assertThatThrownBy(() -> new CheckoutClassificationResult(
                    CheckoutStockClassification.BOM, null,
                    SKU_CODE, TRACK_STOCK, 50L, null, 300L, "KG"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("stockItemUnit must be null for BOM");
        }

        // ----- NON_BOM invalid combinations -----

        @Test
        @DisplayName("NON_BOM with non-null reason -> IAE")
        void nonBomWithReasonThrows() {
            assertThatThrownBy(() -> new CheckoutClassificationResult(
                    CheckoutStockClassification.NON_BOM,
                    CheckoutClassificationReason.NO_PRODUCT,
                    SKU_CODE, UNLIMITED, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reason must be null for NON_BOM");
        }

        @Test
        @DisplayName("NON_BOM with invalid strategy 'UNKNOWN' -> IAE")
        void nonBomInvalidStrategyThrows() {
            assertThatThrownBy(() -> new CheckoutClassificationResult(
                    CheckoutStockClassification.NON_BOM, null,
                    SKU_CODE, "UNKNOWN", null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("NON_BOM requires stockStrategy=TRACK_STOCK or UNLIMITED");
        }

        @Test
        @DisplayName("NON_BOM+UNLIMITED with non-null stockItemId -> IAE")
        void nonBomUnlimitedWithStockItemIdThrows() {
            assertThatThrownBy(() -> new CheckoutClassificationResult(
                    CheckoutStockClassification.NON_BOM, null,
                    SKU_CODE, UNLIMITED, null, 400L, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("stockItemId must be null for NON_BOM+UNLIMITED");
        }

        @Test
        @DisplayName("NON_BOM+TRACK_STOCK with null stockItemId -> IAE")
        void nonBomTrackStockNullStockItemIdThrows() {
            assertThatThrownBy(() -> new CheckoutClassificationResult(
                    CheckoutStockClassification.NON_BOM, null,
                    SKU_CODE, TRACK_STOCK, null, null, 300L, "KG"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("stockItemId must be > 0 for NON_BOM+TRACK_STOCK");
        }

        @Test
        @DisplayName("NON_BOM+TRACK_STOCK with null stockItemUnit -> IAE")
        void nonBomTrackStockNullUnitThrows() {
            assertThatThrownBy(() -> new CheckoutClassificationResult(
                    CheckoutStockClassification.NON_BOM, null,
                    SKU_CODE, TRACK_STOCK, null, 400L, 300L, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("stockItemUnit must be non-blank for NON_BOM+TRACK_STOCK");
        }

        @Test
        @DisplayName("NON_BOM+TRACK_STOCK with blank stockItemUnit '  ' -> IAE")
        void nonBomTrackStockBlankUnitThrows() {
            assertThatThrownBy(() -> new CheckoutClassificationResult(
                    CheckoutStockClassification.NON_BOM, null,
                    SKU_CODE, TRACK_STOCK, null, 400L, 300L, "  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("stockItemUnit must be non-blank for NON_BOM+TRACK_STOCK");
        }

        @Test
        @DisplayName("NON_BOM+TRACK_STOCK with non-null bomProductId -> IAE")
        void nonBomTrackStockWithBomProductIdThrows() {
            assertThatThrownBy(() -> new CheckoutClassificationResult(
                    CheckoutStockClassification.NON_BOM, null,
                    SKU_CODE, TRACK_STOCK, 50L, 400L, 300L, "KG"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("bomProductId must be null for NON_BOM");
        }

        // ----- UNMAPPED invalid combinations -----

        @Test
        @DisplayName("UNMAPPED with null reason -> IAE")
        void unmappedNullReasonThrows() {
            assertThatThrownBy(() -> new CheckoutClassificationResult(
                    CheckoutStockClassification.UNMAPPED, null,
                    SKU_CODE, TRACK_STOCK, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reason must not be null for UNMAPPED");
        }

        @Test
        @DisplayName("UNMAPPED with non-null bomProductId -> IAE")
        void unmappedWithBomProductIdThrows() {
            assertThatThrownBy(() -> new CheckoutClassificationResult(
                    CheckoutStockClassification.UNMAPPED,
                    CheckoutClassificationReason.NO_PRODUCT,
                    SKU_CODE, TRACK_STOCK, 50L, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("bomProductId must be null for UNMAPPED");
        }

        @Test
        @DisplayName("UNMAPPED with non-null stockItemId -> IAE")
        void unmappedWithStockItemIdThrows() {
            assertThatThrownBy(() -> new CheckoutClassificationResult(
                    CheckoutStockClassification.UNMAPPED,
                    CheckoutClassificationReason.NO_STOCK_ITEM,
                    SKU_CODE, TRACK_STOCK, null, 400L, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("stockItemId must be null for UNMAPPED");
        }

        @Test
        @DisplayName("UNMAPPED with non-null locationId -> IAE")
        void unmappedWithLocationIdThrows() {
            assertThatThrownBy(() -> new CheckoutClassificationResult(
                    CheckoutStockClassification.UNMAPPED,
                    CheckoutClassificationReason.NO_LOCATION,
                    SKU_CODE, TRACK_STOCK, null, null, 300L, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("locationId must be null for UNMAPPED");
        }

        @Test
        @DisplayName("UNMAPPED with non-null stockItemUnit -> IAE")
        void unmappedWithUnitThrows() {
            assertThatThrownBy(() -> new CheckoutClassificationResult(
                    CheckoutStockClassification.UNMAPPED,
                    CheckoutClassificationReason.NO_PRODUCT,
                    SKU_CODE, TRACK_STOCK, null, null, null, "KG"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("stockItemUnit must be null for UNMAPPED");
        }

        @Test
        @DisplayName("UNMAPPED with invalid strategy 'UNKNOWN' -> IAE")
        void unmappedInvalidStrategyThrows() {
            assertThatThrownBy(() -> new CheckoutClassificationResult(
                    CheckoutStockClassification.UNMAPPED,
                    CheckoutClassificationReason.INVALID_STOCK_STRATEGY,
                    SKU_CODE, "UNKNOWN", null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("stockStrategy must be null, TRACK_STOCK, or UNLIMITED for UNMAPPED");
        }

        @Test
        @DisplayName("UNMAPPED with blank skuCode '  ' -> IAE (raw blanks must not persist)")
        void unmappedBlankSkuCodeThrows() {
            assertThatThrownBy(() -> new CheckoutClassificationResult(
                    CheckoutStockClassification.UNMAPPED,
                    CheckoutClassificationReason.INVALID_SKU_CODE,
                    "  ", TRACK_STOCK, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("skuCode must be null or non-blank for UNMAPPED");
        }
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static final String LOOKUP_STATUS_FOUND = "FOUND";
    private static final String LOOKUP_STATUS_NO_PRODUCT = "NO_PRODUCT";
    private static final String LOOKUP_STATUS_AMBIGUOUS_PRODUCT = "AMBIGUOUS_PRODUCT";
    private static final String LOOKUP_STATUS_NO_ACTIVE_RECIPE = "NO_ACTIVE_RECIPE";
    private static final String LOOKUP_STATUS_INVALID_SKU_CODE = "INVALID_SKU_CODE";

    private static BomRecipeRespDTO bom(String lookupStatus, Long id, Long productId) {
        BomRecipeRespDTO b = new BomRecipeRespDTO();
        b.setLookupStatus(lookupStatus);
        b.setId(id);
        b.setProductId(productId);
        return b;
    }

    private static StockLocationQueryRespDTO location(Long id, Long storeId,
                                                      String locationType, Long tenantId) {
        return new StockLocationQueryRespDTO(id, storeId, locationType, tenantId);
    }

    private static StockItemQueryRespDTO stockItem(Long id, String skuCode,
                                                   Long tenantId, String unit) {
        return new StockItemQueryRespDTO(id, skuCode, tenantId, unit);
    }
}
