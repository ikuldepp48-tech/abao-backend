package com.geihou.module.supplychain.bom.explode;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.supplychain.api.bom.BomApi;
import com.geihou.module.supplychain.api.bom.dto.BomRecipeRespDTO;
import com.geihou.module.supplychain.api.bom.enums.BomRecipeStatusEnum;
import com.geihou.module.supplychain.bom.BomTestConfig;
import com.geihou.module.supplychain.bom.BomTestSchemaInitializer;
import com.geihou.module.supplychain.bom.dal.dataobject.BomRecipeDO;
import com.geihou.module.supplychain.bom.dal.dataobject.BomRecipeItemDO;
import com.geihou.module.supplychain.bom.dal.dataobject.ProductMasterDO;
import com.geihou.module.supplychain.bom.service.BomRecipeService;
import com.geihou.module.supplychain.bom.service.ProductMasterService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link BomApi#getActiveRecipe(Long, Long)}.
 *
 * <p>Source: TASK-G2-02G.
 */
@SpringBootTest(
        classes = BomTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:bom_api_active_recipe_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class BomApiActiveRecipeTest {

    private static final Long TENANT_A = 1L;
    private static final Long TENANT_B = 2L;

    @Autowired
    private BomApi bomApi;
    @Autowired
    private BomRecipeService bomRecipeService;
    @Autowired
    private ProductMasterService productMasterService;
    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        BomTestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void getActiveRecipe_activeExists_returnsHeaderAndItems() {
        TenantContextHolder.setTenantId(TENANT_A);

        Long productId = createProduct(TENANT_A, "P-A", "Product A", "FINISHED");
        Long rawId = createProduct(TENANT_A, "R-A", "Raw A", "RAW_MATERIAL");

        Long recipeId = bomRecipeService.createDraftRecipe(newRecipe(TENANT_A, productId));
        bomRecipeService.updateDraftItems(TENANT_A, recipeId, List.of(item(rawId, "2", "0.05")));
        bomRecipeService.activateRecipe(TENANT_A, recipeId);

        BomRecipeRespDTO result = bomApi.getActiveRecipe(TENANT_A, productId);

        assertThat(result.getId()).isEqualTo(recipeId);
        assertThat(result.getTenantId()).isEqualTo(TENANT_A);
        assertThat(result.getProductId()).isEqualTo(productId);
        assertThat(result.getStatus()).isEqualTo(BomRecipeStatusEnum.ACTIVE.getCode());
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getComponentProductId()).isEqualTo(rawId);
        assertThat(result.getItems().get(0).getComponentProductCode()).isEqualTo("R-A");
        assertThat(result.getItems().get(0).getComponentProductName()).isEqualTo("Raw A");
        assertThat(result.getItems().get(0).getQuantity()).isEqualByComparingTo("2");
        assertThat(result.getItems().get(0).getWasteRate()).isEqualByComparingTo("0.05");
    }

    @Test
    void getActiveRecipe_noActiveRecipe_returnsExplicitEmptyState() {
        TenantContextHolder.setTenantId(TENANT_A);

        Long productId = createProduct(TENANT_A, "P-A", "Product A", "FINISHED");

        BomRecipeRespDTO result = bomApi.getActiveRecipe(TENANT_A, productId);

        assertThat(result.getId()).isNull();
        assertThat(result.getTenantId()).isEqualTo(TENANT_A);
        assertThat(result.getProductId()).isEqualTo(productId);
        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void getActiveRecipe_draftOnly_returnsExplicitEmptyState() {
        TenantContextHolder.setTenantId(TENANT_A);

        Long productId = createProduct(TENANT_A, "P-A", "Product A", "FINISHED");
        bomRecipeService.createDraftRecipe(newRecipe(TENANT_A, productId));

        BomRecipeRespDTO result = bomApi.getActiveRecipe(TENANT_A, productId);

        assertThat(result.getId()).isNull();
        assertThat(result.getTenantId()).isEqualTo(TENANT_A);
        assertThat(result.getProductId()).isEqualTo(productId);
        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void getActiveRecipe_isTenantIsolated() {
        TenantContextHolder.setTenantId(TENANT_A);

        Long tenantAProductId = createProduct(TENANT_A, "P-A", "Product A", "FINISHED");
        Long tenantARawId = createProduct(TENANT_A, "R-A", "Raw A", "RAW_MATERIAL");
        Long recipeId = bomRecipeService.createDraftRecipe(newRecipe(TENANT_A, tenantAProductId));
        bomRecipeService.updateDraftItems(TENANT_A, recipeId, List.of(item(tenantARawId, "1", "0")));
        bomRecipeService.activateRecipe(TENANT_A, recipeId);

        TenantContextHolder.setTenantId(TENANT_B);

        BomRecipeRespDTO result = bomApi.getActiveRecipe(TENANT_B, tenantAProductId);

        assertThat(result.getId()).isNull();
        assertThat(result.getTenantId()).isEqualTo(TENANT_B);
        assertThat(result.getProductId()).isEqualTo(tenantAProductId);
        assertThat(result.getItems()).isEmpty();
    }

    private Long createProduct(Long tenantId, String code, String name, String type) {
        ProductMasterDO product = new ProductMasterDO();
        product.setTenantId(tenantId);
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
