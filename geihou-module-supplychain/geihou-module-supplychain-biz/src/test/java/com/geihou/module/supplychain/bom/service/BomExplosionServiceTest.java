package com.geihou.module.supplychain.bom.service;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.supplychain.api.bom.explode.dto.BomExplosionRespDTO;
import com.geihou.module.supplychain.bom.BomTestConfig;
import com.geihou.module.supplychain.bom.BomTestSchemaInitializer;
import com.geihou.module.supplychain.bom.dal.dataobject.BomRecipeDO;
import com.geihou.module.supplychain.bom.dal.dataobject.BomRecipeItemDO;
import com.geihou.module.supplychain.bom.dal.dataobject.ProductMasterDO;
import com.geihou.module.supplychain.bom.explode.BomExplosionService;
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
 * Tests for {@link BomExplosionService}.
 *
 * <p>Covers: single-level explode, waste_rate quantity calculation,
 * multi-level explode, missing active recipe, and tenant isolation.
 *
 * <p>Source: TASK-G2-02B.
 */
@SpringBootTest(
        classes = BomTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:bom_explosion_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class BomExplosionServiceTest {

    @Autowired
    private BomExplosionService bomExplosionService;
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

    // ==================== Single-level explode ====================

    @Test
    void explode_singleLevel_returnsTopLevelItems() {
        Long prodA = createProduct("P-A", "Product A", "FINISHED");
        Long raw1 = createProduct("R-1", "Raw Material 1", "RAW_MATERIAL");
        Long raw2 = createProduct("R-2", "Raw Material 2", "RAW_MATERIAL");

        Long recipeId = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, prodA));
        bomRecipeService.updateDraftItems(TENANT_ID, recipeId, List.of(
                item(raw1, "2", "0", "RAW_MATERIAL", "kg"),
                item(raw2, "3", "0", "RAW_MATERIAL", "kg")
        ));
        bomRecipeService.activateRecipe(TENANT_ID, recipeId);

        List<BomExplosionRespDTO> result = bomExplosionService.explode(
                prodA, null, BigDecimal.ONE, TENANT_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getProductId()).isEqualTo(raw1);
        assertThat(result.get(0).getDepth()).isEqualTo(1);
        assertThat(result.get(0).getQuantity()).isEqualByComparingTo(new BigDecimal("2.000000"));
        assertThat(result.get(0).getComponentType()).isEqualTo("RAW_MATERIAL");
        assertThat(result.get(0).isTruncated()).isFalse();
        assertThat(result.get(0).isCycleDetected()).isFalse();
        assertThat(result.get(0).getChildren()).isEmpty();

        assertThat(result.get(1).getProductId()).isEqualTo(raw2);
        assertThat(result.get(1).getQuantity()).isEqualByComparingTo(new BigDecimal("3.000000"));
    }

    // ==================== Waste rate quantity ====================

    @Test
    void explode_wasteRateInflatesQuantity() {
        Long prodA = createProduct("P-A", "Product A", "FINISHED");
        Long raw1 = createProduct("R-1", "Raw Material 1", "RAW_MATERIAL");

        Long recipeId = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, prodA));
        // quantity=10, wasteRate=0.1 → effective qty = 10 * (1 + 0.1) = 11
        bomRecipeService.updateDraftItems(TENANT_ID, recipeId, List.of(
                item(raw1, "10", "0.1", "RAW_MATERIAL", "kg")
        ));
        bomRecipeService.activateRecipe(TENANT_ID, recipeId);

        List<BomExplosionRespDTO> result = bomExplosionService.explode(
                prodA, null, BigDecimal.ONE, TENANT_ID);

        assertThat(result).hasSize(1);
        BigDecimal expected = new BigDecimal("10").multiply(BigDecimal.ONE.add(new BigDecimal("0.1")))
                .setScale(6, RoundingMode.HALF_UP);
        assertThat(result.get(0).getQuantity()).isEqualByComparingTo(expected);
        assertThat(result.get(0).getWasteRate()).isEqualByComparingTo(new BigDecimal("0.1"));
    }

    @Test
    void explode_requestedQuantityScalesProportionally() {
        Long prodA = createProduct("P-A", "Product A", "FINISHED");
        Long raw1 = createProduct("R-1", "Raw Material 1", "RAW_MATERIAL");

        // Recipe with output_quantity=2
        Long recipeId = bomRecipeService.createDraftRecipe(newRecipeWithOutput(TENANT_ID, prodA, "2"));
        bomRecipeService.updateDraftItems(TENANT_ID, recipeId, List.of(
                item(raw1, "3", "0", "RAW_MATERIAL", "kg")
        ));
        bomRecipeService.activateRecipe(TENANT_ID, recipeId);

        // Request 4 units → scale = 4/2 = 2 → qty = 3 * 2 = 6
        List<BomExplosionRespDTO> result = bomExplosionService.explode(
                prodA, null, new BigDecimal("4"), TENANT_ID);

        assertThat(result).hasSize(1);
        BigDecimal expected = new BigDecimal("4").divide(new BigDecimal("2"), 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("3")).setScale(6, RoundingMode.HALF_UP);
        assertThat(result.get(0).getQuantity()).isEqualByComparingTo(expected);
    }

    // ==================== Multi-level explode ====================

    @Test
    void explode_multiLevelExpandsChildren() {
        Long prodA = createProduct("P-A", "Product A", "FINISHED");
        Long semiB = createProduct("S-B", "Semi B", "SEMI_FINISHED");
        Long rawC = createProduct("R-C", "Raw C", "RAW_MATERIAL");

        // A → B (active, quantity=2)
        Long r1 = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, prodA));
        bomRecipeService.updateDraftItems(TENANT_ID, r1, List.of(
                item(semiB, "2", "0", "SEMI_FINISHED", "kg")
        ));
        bomRecipeService.activateRecipe(TENANT_ID, r1);

        // B → C (active, quantity=5)
        Long r2 = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, semiB));
        bomRecipeService.updateDraftItems(TENANT_ID, r2, List.of(
                item(rawC, "5", "0", "RAW_MATERIAL", "kg")
        ));
        bomRecipeService.activateRecipe(TENANT_ID, r2);

        List<BomExplosionRespDTO> result = bomExplosionService.explode(
                prodA, null, BigDecimal.ONE, TENANT_ID);

        assertThat(result).hasSize(1);
        BomExplosionRespDTO topNode = result.get(0);
        assertThat(topNode.getProductId()).isEqualTo(semiB);
        assertThat(topNode.getDepth()).isEqualTo(1);
        assertThat(topNode.getQuantity()).isEqualByComparingTo(new BigDecimal("2.000000"));
        assertThat(topNode.getComponentType()).isEqualTo("SEMI_FINISHED");

        // Should have one child: rawC at depth 2
        assertThat(topNode.getChildren()).hasSize(1);
        BomExplosionRespDTO childNode = topNode.getChildren().get(0);
        assertThat(childNode.getProductId()).isEqualTo(rawC);
        assertThat(childNode.getDepth()).isEqualTo(2);
        assertThat(childNode.getComponentType()).isEqualTo("RAW_MATERIAL");
        // qty = scale(2/1=2) * item_qty(5) = 10
        assertThat(childNode.getQuantity()).isEqualByComparingTo(new BigDecimal("10.000000"));
    }

    @Test
    void explode_rawMaterialLeafHasNoChildren() {
        Long prodA = createProduct("P-A", "Product A", "FINISHED");
        Long raw1 = createProduct("R-1", "Raw Material 1", "RAW_MATERIAL");

        Long recipeId = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, prodA));
        bomRecipeService.updateDraftItems(TENANT_ID, recipeId, List.of(
                item(raw1, "1", "0", "RAW_MATERIAL", "kg")
        ));
        bomRecipeService.activateRecipe(TENANT_ID, recipeId);

        List<BomExplosionRespDTO> result = bomExplosionService.explode(
                prodA, null, BigDecimal.ONE, TENANT_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getChildren()).isEmpty();
    }

    // ==================== Missing active recipe ====================

    @Test
    void explode_noActiveRecipeReturnsEmpty() {
        Long prodA = createProduct("P-A", "Product A", "FINISHED");

        // No recipe created at all
        List<BomExplosionRespDTO> result = bomExplosionService.explode(
                prodA, null, BigDecimal.ONE, TENANT_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void explode_draftOnlyRecipeReturnsEmpty() {
        Long prodA = createProduct("P-A", "Product A", "FINISHED");
        Long raw1 = createProduct("R-1", "Raw Material 1", "RAW_MATERIAL");

        // Create DRAFT recipe but do NOT activate
        Long recipeId = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, prodA));
        bomRecipeService.updateDraftItems(TENANT_ID, recipeId, List.of(
                item(raw1, "1", "0", "RAW_MATERIAL", "kg")
        ));

        List<BomExplosionRespDTO> result = bomExplosionService.explode(
                prodA, null, BigDecimal.ONE, TENANT_ID);

        assertThat(result).isEmpty();
    }

    // ==================== Tenant isolation ====================

    @Test
    void explode_tenantIsolationReturnsEmptyForWrongTenant() {
        Long prodA = createProduct("P-A", "Product A", "FINISHED");
        Long raw1 = createProduct("R-1", "Raw Material 1", "RAW_MATERIAL");

        Long recipeId = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, prodA));
        bomRecipeService.updateDraftItems(TENANT_ID, recipeId, List.of(
                item(raw1, "1", "0", "RAW_MATERIAL", "kg")
        ));
        bomRecipeService.activateRecipe(TENANT_ID, recipeId);

        // Query from a different tenant → should get empty (no recipe visible)
        List<BomExplosionRespDTO> result = bomExplosionService.explode(
                prodA, null, BigDecimal.ONE, 999L);

        assertThat(result).isEmpty();
    }

    @Test
    void explode_explicitRecipeIdWrongTenantReturnsEmpty() {
        Long prodA = createProduct("P-A", "Product A", "FINISHED");
        Long raw1 = createProduct("R-1", "Raw Material 1", "RAW_MATERIAL");

        Long recipeId = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, prodA));
        bomRecipeService.updateDraftItems(TENANT_ID, recipeId, List.of(
                item(raw1, "1", "0", "RAW_MATERIAL", "kg")
        ));
        bomRecipeService.activateRecipe(TENANT_ID, recipeId);

        // Explicit recipeId but wrong tenant → empty
        List<BomExplosionRespDTO> result = bomExplosionService.explode(
                prodA, recipeId, BigDecimal.ONE, 999L);

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

    private BomRecipeDO newRecipeWithOutput(Long tenantId, Long productId, String outputQty) {
        BomRecipeDO recipe = newRecipe(tenantId, productId);
        recipe.setOutputQuantity(new BigDecimal(outputQty));
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
