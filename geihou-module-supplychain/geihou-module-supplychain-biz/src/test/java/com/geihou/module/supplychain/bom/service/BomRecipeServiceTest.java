package com.geihou.module.supplychain.bom.service;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.supplychain.api.bom.enums.BomRecipeStatusEnum;
import com.geihou.module.supplychain.bom.BomTestConfig;
import com.geihou.module.supplychain.bom.BomTestSchemaInitializer;
import com.geihou.module.supplychain.bom.dal.dataobject.BomRecipeDO;
import com.geihou.module.supplychain.bom.dal.dataobject.BomRecipeItemDO;
import com.geihou.module.supplychain.bom.dal.dataobject.ProductMasterDO;
import com.geihou.module.supplychain.stock.mq.StockEventPublisher;
import com.geihou.module.supplychain.stock.mq.event.BomVersionChangedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link BomRecipeService}.
 *
 * <p>Covers: recipe lifecycle (DRAFT/ACTIVE/ARCHIVED), version increment,
 * validation (quantity, waste, component), circular reference detection
 * (self, direct, multi-level, DRAFT-to-DRAFT on activation), and no-cycle pass.
 *
 * <p>Source: TASK-G2-02A fix-2.
 */
@SpringBootTest(
        classes = BomTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:bom_recipe_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class BomRecipeServiceTest {

    @Autowired
    private BomRecipeService bomRecipeService;
    @Autowired
    private ProductMasterService productMasterService;
    @Autowired
    private DataSource dataSource;
    @MockBean
    private StockEventPublisher stockEventPublisher;

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

    // ==================== DRAFT / version increment ====================

    @Test
    void createDraftRecipe_defaultsToDraftAndVersionIncrement() {
        Long prodA = createProduct("P-A", "Product A");
        Long prodB = createProduct("P-B", "Product B");

        Long r1 = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, prodA));
        Long r2 = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, prodA));
        Long r3 = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, prodB));

        BomRecipeDO recipe1 = bomRecipeService.getById(r1, TENANT_ID);
        BomRecipeDO recipe2 = bomRecipeService.getById(r2, TENANT_ID);
        BomRecipeDO recipe3 = bomRecipeService.getById(r3, TENANT_ID);

        assertThat(recipe1.getStatus()).isEqualTo(BomRecipeStatusEnum.DRAFT.getCode());
        assertThat(recipe1.getVersionNo()).isEqualTo(1);

        assertThat(recipe2.getStatus()).isEqualTo(BomRecipeStatusEnum.DRAFT.getCode());
        assertThat(recipe2.getVersionNo()).isEqualTo(2);

        // Different product → version starts at 1
        assertThat(recipe3.getStatus()).isEqualTo(BomRecipeStatusEnum.DRAFT.getCode());
        assertThat(recipe3.getVersionNo()).isEqualTo(1);
    }

    // ==================== ACTIVE / ARCHIVED not editable ====================

    @Test
    void updateDraftItems_activeRecipeNotEditable() {
        Long prodA = createProduct("P-A", "Product A");
        Long prodB = createProduct("P-B", "Product B");

        Long recipeId = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, prodA));
        bomRecipeService.updateDraftItems(TENANT_ID, recipeId, List.of(item(prodB, "2", "0")));
        bomRecipeService.activateRecipe(TENANT_ID, recipeId);

        assertThatThrownBy(() -> bomRecipeService.updateDraftItems(TENANT_ID, recipeId,
                List.of(item(prodB, "3", "0"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("only DRAFT recipe can edit items");
    }

    @Test
    void updateDraftItems_archivedRecipeNotEditable() {
        Long prodA = createProduct("P-A", "Product A");
        Long prodB = createProduct("P-B", "Product B");

        // Create and activate first recipe
        Long r1 = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, prodA));
        bomRecipeService.updateDraftItems(TENANT_ID, r1, List.of(item(prodB, "2", "0")));
        bomRecipeService.activateRecipe(TENANT_ID, r1);

        // Create second recipe and activate → archives r1
        Long r2 = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, prodA));
        bomRecipeService.updateDraftItems(TENANT_ID, r2, List.of(item(prodB, "3", "0")));
        bomRecipeService.activateRecipe(TENANT_ID, r2);

        // r1 should now be ARCHIVED and not editable
        BomRecipeDO r1Recipe = bomRecipeService.getById(r1, TENANT_ID);
        assertThat(r1Recipe.getStatus()).isEqualTo(BomRecipeStatusEnum.ARCHIVED.getCode());

        assertThatThrownBy(() -> bomRecipeService.updateDraftItems(TENANT_ID, r1,
                List.of(item(prodB, "5", "0"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("only DRAFT recipe can edit items");
    }

    // ==================== Activation: archives old, only one active ====================

    @Test
    void activateRecipe_archivesOldActiveAndOnlyOneActive() {
        Long prodA = createProduct("P-A", "Product A");
        Long prodB = createProduct("P-B", "Product B");

        // First recipe → activate
        Long r1 = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, prodA));
        bomRecipeService.updateDraftItems(TENANT_ID, r1, List.of(item(prodB, "2", "0")));
        bomRecipeService.activateRecipe(TENANT_ID, r1);

        BomRecipeDO active1 = bomRecipeService.getActiveRecipe(prodA, TENANT_ID);
        assertThat(active1.getId()).isEqualTo(r1);
        assertThat(active1.getStatus()).isEqualTo(BomRecipeStatusEnum.ACTIVE.getCode());
        verify(stockEventPublisher, times(1)).publishBomVersionChanged(argThat((BomVersionChangedEvent event) ->
                event.getTenantId().equals(TENANT_ID)
                        && event.getRecipeId().equals(r1)
                        && event.getProductId().equals(prodA)
                        && event.getRecipeVersion().equals(1)
                        && event.getStatus().equals(BomRecipeStatusEnum.ACTIVE.getCode())));

        // Second recipe → activate, should archive r1
        Long r2 = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, prodA));
        bomRecipeService.updateDraftItems(TENANT_ID, r2, List.of(item(prodB, "3", "0")));
        bomRecipeService.activateRecipe(TENANT_ID, r2);

        // r1 should be ARCHIVED
        BomRecipeDO r1Recipe = bomRecipeService.getById(r1, TENANT_ID);
        assertThat(r1Recipe.getStatus()).isEqualTo(BomRecipeStatusEnum.ARCHIVED.getCode());

        // Only one ACTIVE recipe for the product
        BomRecipeDO active2 = bomRecipeService.getActiveRecipe(prodA, TENANT_ID);
        assertThat(active2.getId()).isEqualTo(r2);
        assertThat(active2.getStatus()).isEqualTo(BomRecipeStatusEnum.ACTIVE.getCode());
        verify(stockEventPublisher, times(1)).publishBomVersionChanged(argThat((BomVersionChangedEvent event) ->
                event.getTenantId().equals(TENANT_ID)
                        && event.getRecipeId().equals(r2)
                        && event.getProductId().equals(prodA)
                        && event.getRecipeVersion().equals(2)
                        && event.getStatus().equals(BomRecipeStatusEnum.ACTIVE.getCode())));
    }

    // ==================== Invalid quantity / waste / component ====================

    @Test
    void updateDraftItems_invalidQuantityZeroThrows() {
        Long prodA = createProduct("P-A", "Product A");
        Long prodB = createProduct("P-B", "Product B");

        Long recipeId = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, prodA));

        assertThatThrownBy(() -> bomRecipeService.updateDraftItems(TENANT_ID, recipeId,
                List.of(item(prodB, "0", "0"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("quantity must be > 0");
    }

    @Test
    void updateDraftItems_invalidQuantityNegativeThrows() {
        Long prodA = createProduct("P-A", "Product A");
        Long prodB = createProduct("P-B", "Product B");

        Long recipeId = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, prodA));

        assertThatThrownBy(() -> bomRecipeService.updateDraftItems(TENANT_ID, recipeId,
                List.of(item(prodB, "-1", "0"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("quantity must be > 0");
    }

    @Test
    void updateDraftItems_invalidWasteRateNegativeThrows() {
        Long prodA = createProduct("P-A", "Product A");
        Long prodB = createProduct("P-B", "Product B");

        Long recipeId = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, prodA));

        assertThatThrownBy(() -> bomRecipeService.updateDraftItems(TENANT_ID, recipeId,
                List.of(item(prodB, "1", "-0.1"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("waste_rate must be >= 0 and < 1");
    }

    @Test
    void updateDraftItems_invalidWasteRateOneOrMoreThrows() {
        Long prodA = createProduct("P-A", "Product A");
        Long prodB = createProduct("P-B", "Product B");

        Long recipeId = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, prodA));

        assertThatThrownBy(() -> bomRecipeService.updateDraftItems(TENANT_ID, recipeId,
                List.of(item(prodB, "1", "1"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("waste_rate must be >= 0 and < 1");
    }

    @Test
    void updateDraftItems_componentNotFoundThrows() {
        Long prodA = createProduct("P-A", "Product A");

        Long recipeId = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, prodA));

        // Product ID 9999 does not exist
        assertThatThrownBy(() -> bomRecipeService.updateDraftItems(TENANT_ID, recipeId,
                List.of(item(9999L, "1", "0"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("component product not found");
    }

    // ==================== Circular reference: self / direct / multi-level ====================

    @Test
    void updateDraftItems_selfCycleRejected() {
        Long prodA = createProduct("P-A", "Product A");

        Long recipeId = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, prodA));

        // Recipe for A references A itself
        assertThatThrownBy(() -> bomRecipeService.updateDraftItems(TENANT_ID, recipeId,
                List.of(item(prodA, "1", "0"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("circular reference detected");
    }

    @Test
    void updateDraftItems_directCycleRejected() {
        Long prodA = createProduct("P-A", "Product A");
        Long prodB = createProduct("P-B", "Product B");

        // A → B (active)
        Long r1 = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, prodA));
        bomRecipeService.updateDraftItems(TENANT_ID, r1, List.of(item(prodB, "1", "0")));
        bomRecipeService.activateRecipe(TENANT_ID, r1);

        // B → A should be rejected (A's active recipe references B)
        Long r2 = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, prodB));
        assertThatThrownBy(() -> bomRecipeService.updateDraftItems(TENANT_ID, r2,
                List.of(item(prodA, "1", "0"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("circular reference detected");
    }

    @Test
    void updateDraftItems_multiLevelCycleRejected() {
        Long prodA = createProduct("P-A", "Product A");
        Long prodB = createProduct("P-B", "Product B");
        Long prodC = createProduct("P-C", "Product C");

        // A → B (active)
        Long r1 = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, prodA));
        bomRecipeService.updateDraftItems(TENANT_ID, r1, List.of(item(prodB, "1", "0")));
        bomRecipeService.activateRecipe(TENANT_ID, r1);

        // B → C (active)
        Long r2 = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, prodB));
        bomRecipeService.updateDraftItems(TENANT_ID, r2, List.of(item(prodC, "1", "0")));
        bomRecipeService.activateRecipe(TENANT_ID, r2);

        // C → A should be rejected (A→B→C→A cycle)
        Long r3 = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, prodC));
        assertThatThrownBy(() -> bomRecipeService.updateDraftItems(TENANT_ID, r3,
                List.of(item(prodA, "1", "0"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("circular reference detected");
    }

    // ==================== DRAFT-to-DRAFT cycle on activation ====================

    @Test
    void activateRecipe_draftToDraftDirectCycleRejected() {
        Long prodA = createProduct("P-A", "Product A");
        Long prodB = createProduct("P-B", "Product B");

        // A → B (DRAFT, not yet active)
        Long r1 = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, prodA));
        bomRecipeService.updateDraftItems(TENANT_ID, r1, List.of(item(prodB, "1", "0")));

        // B → A (DRAFT, not yet active)
        Long r2 = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, prodB));
        bomRecipeService.updateDraftItems(TENANT_ID, r2, List.of(item(prodA, "1", "0")));

        // Activating r1 should fail: r2 (DRAFT) references A → cycle
        assertThatThrownBy(() -> bomRecipeService.activateRecipe(TENANT_ID, r1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("circular reference detected");
    }

    @Test
    void activateRecipe_draftToDraftMultiLevelCycleRejected() {
        Long prodA = createProduct("P-A", "Product A");
        Long prodB = createProduct("P-B", "Product B");
        Long prodC = createProduct("P-C", "Product C");

        // A → B (DRAFT)
        Long r1 = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, prodA));
        bomRecipeService.updateDraftItems(TENANT_ID, r1, List.of(item(prodB, "1", "0")));

        // B → C (DRAFT)
        Long r2 = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, prodB));
        bomRecipeService.updateDraftItems(TENANT_ID, r2, List.of(item(prodC, "1", "0")));

        // C → A (DRAFT)
        Long r3 = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, prodC));
        bomRecipeService.updateDraftItems(TENANT_ID, r3, List.of(item(prodA, "1", "0")));

        // Activating r1 should fail: A→B→C→A cycle via DRAFT recipes
        assertThatThrownBy(() -> bomRecipeService.activateRecipe(TENANT_ID, r1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("circular reference detected");
    }

    // ==================== No-cycle pass ====================

    @Test
    void updateDraftItems_noCyclePasses() {
        Long prodA = createProduct("P-A", "Product A");
        Long prodB = createProduct("P-B", "Product B");
        Long prodC = createProduct("P-C", "Product C");

        // A → B (active)
        Long r1 = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, prodA));
        bomRecipeService.updateDraftItems(TENANT_ID, r1, List.of(item(prodB, "1", "0")));
        bomRecipeService.activateRecipe(TENANT_ID, r1);

        // B → C (active) — no cycle since C has no recipe pointing back
        Long r2 = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, prodB));
        bomRecipeService.updateDraftItems(TENANT_ID, r2, List.of(item(prodC, "1", "0")));
        bomRecipeService.activateRecipe(TENANT_ID, r2);

        // Verify both are ACTIVE
        BomRecipeDO activeA = bomRecipeService.getActiveRecipe(prodA, TENANT_ID);
        BomRecipeDO activeB = bomRecipeService.getActiveRecipe(prodB, TENANT_ID);
        assertThat(activeA.getId()).isEqualTo(r1);
        assertThat(activeB.getId()).isEqualTo(r2);
    }

    @Test
    void activateRecipe_noCyclePasses() {
        Long prodA = createProduct("P-A", "Product A");
        Long prodB = createProduct("P-B", "Product B");

        // A → B (DRAFT), then activate — no cycle
        Long r1 = bomRecipeService.createDraftRecipe(newRecipe(TENANT_ID, prodA));
        bomRecipeService.updateDraftItems(TENANT_ID, r1, List.of(item(prodB, "1", "0")));
        bomRecipeService.activateRecipe(TENANT_ID, r1);

        BomRecipeDO active = bomRecipeService.getActiveRecipe(prodA, TENANT_ID);
        assertThat(active).isNotNull();
        assertThat(active.getStatus()).isEqualTo(BomRecipeStatusEnum.ACTIVE.getCode());
    }

    // ==================== Helpers ====================

    private Long createProduct(String code, String name) {
        ProductMasterDO product = new ProductMasterDO();
        product.setTenantId(TENANT_ID);
        product.setProductCode(code);
        product.setProductName(name);
        product.setProductType("FINISHED");
        product.setUnit("kg");
        return productMasterService.createProduct(product);
    }

    private BomRecipeDO newRecipe(Long tenantId, Long productId) {
        BomRecipeDO recipe = new BomRecipeDO();
        recipe.setTenantId(tenantId);
        recipe.setProductId(productId);
        return recipe;
    }

    private BomRecipeItemDO item(Long componentProductId, String quantity, String wasteRate) {
        BomRecipeItemDO item = new BomRecipeItemDO();
        item.setComponentProductId(componentProductId);
        item.setQuantity(new BigDecimal(quantity));
        item.setWasteRate(new BigDecimal(wasteRate));
        return item;
    }
}
