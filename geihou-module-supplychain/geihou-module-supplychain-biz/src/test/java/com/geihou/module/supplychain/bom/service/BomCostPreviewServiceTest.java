package com.geihou.module.supplychain.bom.service;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.supplychain.api.bom.preview.dto.BomCostPreviewRespDTO;
import com.geihou.module.supplychain.bom.BomTestConfig;
import com.geihou.module.supplychain.bom.BomTestSchemaInitializer;
import com.geihou.module.supplychain.bom.dal.dataobject.BomRecipeDO;
import com.geihou.module.supplychain.bom.dal.dataobject.BomRecipeItemDO;
import com.geihou.module.supplychain.bom.dal.dataobject.ProductMasterDO;
import com.geihou.module.supplychain.bom.preview.BomCostPreviewService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link BomCostPreviewService}.
 *
 * <p>Covers: cost preview aggregates RAW leaves, multi-level aggregation,
 * same raw material appearing in multiple paths is aggregated into one row,
 * zero/null cost placeholder, and tenant isolation.
 *
 * <p>Source: TASK-G2-02B.
 */
@SpringBootTest(
        classes = BomTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:bom_cost_preview_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class BomCostPreviewServiceTest {

    @Autowired
    private BomCostPreviewService bomCostPreviewService;
    @Autowired
    private BomRecipeService bomRecipeService;
    @Autowired
    private ProductMasterService productMasterService;
    @Autowired
    private DataSource dataSource;

    private static final Long TENANT_ID = 1L;

    @BeforeEach
    void setUp() throws Exception {
        BomTestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // ==================== Cost preview aggregates RAW leaves ====================

    @Test
    void previewCost_singleLevelAggregatesRawMaterials() {
        Long prodA = createProduct("P-A", "Product A", "FINISHED");
        Long raw1 = createProduct("R-1", "Raw 1", "RAW_MATERIAL");
        Long raw2 = createProduct("R-2", "Raw 2", "RAW_MATERIAL");

        Long recipeId = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, prodA));
        bomRecipeService.updateDraftItems(TENANT_ID, recipeId, List.of(
                item(raw1, "2", "0", "RAW_MATERIAL", "kg"),
                item(raw2, "3", "0", "RAW_MATERIAL", "kg")
        ));
        bomRecipeService.activateRecipe(TENANT_ID, recipeId);

        List<BomCostPreviewRespDTO> result = bomCostPreviewService.previewCost(
                prodA, null, BigDecimal.ONE, TENANT_ID);

        assertThat(result).hasSize(2);
        // Each raw material should appear once
        assertThat(result).extracting(BomCostPreviewRespDTO::getProductId)
                .containsExactlyInAnyOrder(raw1, raw2);
        assertThat(result).extracting(BomCostPreviewRespDTO::getComponentType)
                .containsOnly("RAW_MATERIAL");

        // Verify quantities
        BomCostPreviewRespDTO row1 = result.stream().filter(r -> r.getProductId().equals(raw1)).findFirst().orElseThrow();
        assertThat(row1.getTotalQuantity()).isEqualByComparingTo(new BigDecimal("2.000000"));
        BomCostPreviewRespDTO row2 = result.stream().filter(r -> r.getProductId().equals(raw2)).findFirst().orElseThrow();
        assertThat(row2.getTotalQuantity()).isEqualByComparingTo(new BigDecimal("3.000000"));
    }

    // ==================== Multi-level aggregation ====================

    @Test
    void previewCost_multiLevelAggregatesRawLeavesOnly() {
        Long prodA = createProduct("P-A", "Product A", "FINISHED");
        Long semiB = createProduct("S-B", "Semi B", "SEMI_FINISHED");
        Long rawC = createProduct("R-C", "Raw C", "RAW_MATERIAL");

        // A → B (quantity=2)
        Long r1 = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, prodA));
        bomRecipeService.updateDraftItems(TENANT_ID, r1, List.of(
                item(semiB, "2", "0", "SEMI_FINISHED", "kg")
        ));
        bomRecipeService.activateRecipe(TENANT_ID, r1);

        // B → C (quantity=5)
        Long r2 = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, semiB));
        bomRecipeService.updateDraftItems(TENANT_ID, r2, List.of(
                item(rawC, "5", "0", "RAW_MATERIAL", "kg")
        ));
        bomRecipeService.activateRecipe(TENANT_ID, r2);

        List<BomCostPreviewRespDTO> result = bomCostPreviewService.previewCost(
                prodA, null, BigDecimal.ONE, TENANT_ID);

        // Only rawC should be in the result (semiB is SEMI_FINISHED, not aggregated)
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProductId()).isEqualTo(rawC);
        assertThat(result.get(0).getComponentType()).isEqualTo("RAW_MATERIAL");
        // scale = 1/1 = 1 → B qty = 2 → child scale = 2/1 = 2 → C qty = 5 * 2 = 10
        assertThat(result.get(0).getTotalQuantity()).isEqualByComparingTo(new BigDecimal("10.000000"));
    }

    // ==================== Same raw material in multiple paths aggregated ====================

    @Test
    void previewCost_sameRawMaterialInMultiplePathsAggregated() {
        Long prodA = createProduct("P-A", "Product A", "FINISHED");
        Long semiB = createProduct("S-B", "Semi B", "SEMI_FINISHED");
        Long rawC = createProduct("R-C", "Raw C", "RAW_MATERIAL");

        // A → B (quantity=1) + A → C (quantity=3, direct raw)
        Long r1 = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, prodA));
        bomRecipeService.updateDraftItems(TENANT_ID, r1, List.of(
                item(semiB, "1", "0", "SEMI_FINISHED", "kg"),
                item(rawC, "3", "0", "RAW_MATERIAL", "kg")
        ));
        bomRecipeService.activateRecipe(TENANT_ID, r1);

        // B → C (quantity=2)
        Long r2 = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, semiB));
        bomRecipeService.updateDraftItems(TENANT_ID, r2, List.of(
                item(rawC, "2", "0", "RAW_MATERIAL", "kg")
        ));
        bomRecipeService.activateRecipe(TENANT_ID, r2);

        List<BomCostPreviewRespDTO> result = bomCostPreviewService.previewCost(
                prodA, null, BigDecimal.ONE, TENANT_ID);

        // rawC appears in two paths: direct (qty=3) and via B (qty=1*2=2) → aggregated = 5
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProductId()).isEqualTo(rawC);
        assertThat(result.get(0).getTotalQuantity()).isEqualByComparingTo(new BigDecimal("5.000000"));
    }

    // ==================== Zero / null cost placeholder ====================

    @Test
    void previewCost_costFieldsAreNullPlaceholders() {
        Long prodA = createProduct("P-A", "Product A", "FINISHED");
        Long raw1 = createProduct("R-1", "Raw 1", "RAW_MATERIAL");

        Long recipeId = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, prodA));
        bomRecipeService.updateDraftItems(TENANT_ID, recipeId, List.of(
                item(raw1, "1", "0", "RAW_MATERIAL", "kg")
        ));
        bomRecipeService.activateRecipe(TENANT_ID, recipeId);

        List<BomCostPreviewRespDTO> result = bomCostPreviewService.previewCost(
                prodA, null, BigDecimal.ONE, TENANT_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUnitCost()).isNull();
        assertThat(result.get(0).getTotalCost()).isNull();
    }

    @Test
    void previewCost_wasteRateInflatesAggregatedQuantity() {
        Long prodA = createProduct("P-A", "Product A", "FINISHED");
        Long raw1 = createProduct("R-1", "Raw 1", "RAW_MATERIAL");

        Long recipeId = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, prodA));
        // quantity=10, wasteRate=0.2 → effective = 10 * 1.2 = 12
        bomRecipeService.updateDraftItems(TENANT_ID, recipeId, List.of(
                item(raw1, "10", "0.2", "RAW_MATERIAL", "kg")
        ));
        bomRecipeService.activateRecipe(TENANT_ID, recipeId);

        List<BomCostPreviewRespDTO> result = bomCostPreviewService.previewCost(
                prodA, null, BigDecimal.ONE, TENANT_ID);

        assertThat(result).hasSize(1);
        BigDecimal expected = new BigDecimal("10")
                .multiply(BigDecimal.ONE.add(new BigDecimal("0.2")))
                .setScale(6, RoundingMode.HALF_UP);
        assertThat(result.get(0).getTotalQuantity()).isEqualByComparingTo(expected);
    }

    // ==================== No active recipe → empty ====================

    @Test
    void previewCost_noActiveRecipeReturnsEmpty() {
        Long prodA = createProduct("P-A", "Product A", "FINISHED");

        List<BomCostPreviewRespDTO> result = bomCostPreviewService.previewCost(
                prodA, null, BigDecimal.ONE, TENANT_ID);

        assertThat(result).isEmpty();
    }

    // ==================== Tenant isolation ====================

    @Test
    void previewCost_tenantIsolationReturnsEmptyForWrongTenant() {
        Long prodA = createProduct("P-A", "Product A", "FINISHED");
        Long raw1 = createProduct("R-1", "Raw 1", "RAW_MATERIAL");

        Long recipeId = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, prodA));
        bomRecipeService.updateDraftItems(TENANT_ID, recipeId, List.of(
                item(raw1, "1", "0", "RAW_MATERIAL", "kg")
        ));
        bomRecipeService.activateRecipe(TENANT_ID, recipeId);

        List<BomCostPreviewRespDTO> result = bomCostPreviewService.previewCost(
                prodA, null, BigDecimal.ONE, 999L);

        assertThat(result).isEmpty();
    }

    // ==================== Helpers ====================

    private Long createProduct(String code, String name, String type) {
        ProductMasterDO product = new ProductMasterDO();
        product.setTenantId(TENANT_ID);
        product.setProductCode(code);
        product.setProductName(name);
        product.setProductType(type);
        product.setUnit("kg");
        return productMasterService.createProduct(product);
    }

    private BomRecipeDO newRecipe(Long tenantId, Long productId) {
        BomRecipeDO recipe = new BomRecipeDO();
        recipe.setTenantId(tenantId);
        recipe.setProductId(productId);
        recipe.setOutputQuantity(BigDecimal.ONE);
        recipe.setOutputUnit("kg");
        return recipe;
    }

    private BomRecipeItemDO item(Long componentProductId, String quantity, String wasteRate,
                                  String componentType, String unit) {
        BomRecipeItemDO item = new BomRecipeItemDO();
        item.setComponentProductId(componentProductId);
        item.setQuantity(new BigDecimal(quantity));
        item.setWasteRate(new BigDecimal(wasteRate));
        item.setComponentType(componentType);
        item.setUnit(unit);
        return item;
    }
}
