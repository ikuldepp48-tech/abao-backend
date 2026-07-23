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
 * Tests for BomApi.getActiveRecipeBySkuCode(Long, String).
 *
 * Covers: normal active recipe, skuCode not found (0 results), null/blank input,
 * multiple results (>=2), cross-tenant isolation, product exists but no active recipe.
 *
 * Source: TASK-G2-02H-2A.
 */
@SpringBootTest(
        classes = BomTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:bom_api_sku_code_recipe_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class BomApiSkuCodeRecipeTest {

    private static final Long TENANT_A = 1L;
    private static final Long TENANT_B = 2L;
    private static final String SKU_001 = "SKU001";

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
    void getActiveRecipeBySkuCode_normalPath_returnsHeaderAndItems() {
        TenantContextHolder.setTenantId(TENANT_A);

        Long productId = createFinishedProduct(TENANT_A, "P-A", "Product A", SKU_001);
        Long rawId = createProduct(TENANT_A, "R-A", "Raw A", "RAW_MATERIAL", null);

        Long recipeId = bomRecipeService.createDraftRecipe(newRecipe(TENANT_A, productId));
        bomRecipeService.updateDraftItems(TENANT_A, recipeId, List.of(item(rawId, "2", "0.05")));
        bomRecipeService.activateRecipe(TENANT_A, recipeId);

        BomRecipeRespDTO result = bomApi.getActiveRecipeBySkuCode(TENANT_A, SKU_001);

        assertThat(result.getId()).isEqualTo(recipeId);
        assertThat(result.getTenantId()).isEqualTo(TENANT_A);
        assertThat(result.getProductId()).isEqualTo(productId);
        assertThat(result.getLookupStatus()).isEqualTo("FOUND");
        assertThat(result.getStatus()).isEqualTo(BomRecipeStatusEnum.ACTIVE.getCode());
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getComponentProductId()).isEqualTo(rawId);
        assertThat(result.getItems().get(0).getComponentProductCode()).isEqualTo("R-A");
        assertThat(result.getItems().get(0).getQuantity()).isEqualByComparingTo("2");
    }

    @Test
    void getActiveRecipeBySkuCode_skuNotFound_returnsEmptyState() {
        TenantContextHolder.setTenantId(TENANT_A);

        BomRecipeRespDTO result = bomApi.getActiveRecipeBySkuCode(TENANT_A, "SKU999");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isNull();
        assertThat(result.getProductId()).isNull();
        assertThat(result.getLookupStatus()).isEqualTo("NO_PRODUCT");
        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void getActiveRecipeBySkuCode_nullSkuCode_returnsEmptyState() {
        TenantContextHolder.setTenantId(TENANT_A);

        BomRecipeRespDTO result = bomApi.getActiveRecipeBySkuCode(TENANT_A, null);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isNull();
        assertThat(result.getProductId()).isNull();
        assertThat(result.getLookupStatus()).isEqualTo("INVALID_SKU_CODE");
        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void getActiveRecipeBySkuCode_blankSkuCode_returnsEmptyState() {
        TenantContextHolder.setTenantId(TENANT_A);

        BomRecipeRespDTO result = bomApi.getActiveRecipeBySkuCode(TENANT_A, "   ");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isNull();
        assertThat(result.getProductId()).isNull();
        assertThat(result.getLookupStatus()).isEqualTo("INVALID_SKU_CODE");
        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void getActiveRecipeBySkuCode_multipleResults_returnsEmptyState() {
        TenantContextHolder.setTenantId(TENANT_A);

        createFinishedProduct(TENANT_A, "P-A1", "Product A1", SKU_001);
        createFinishedProduct(TENANT_A, "P-A2", "Product A2", SKU_001);

        BomRecipeRespDTO result = bomApi.getActiveRecipeBySkuCode(TENANT_A, SKU_001);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isNull();
        assertThat(result.getProductId()).isNull();
        assertThat(result.getLookupStatus()).isEqualTo("AMBIGUOUS_PRODUCT");
        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void getActiveRecipeBySkuCode_crossTenantIsolated_returnsEmptyState() {
        TenantContextHolder.setTenantId(TENANT_B);

        Long productIdB = createFinishedProduct(TENANT_B, "P-B", "Product B", SKU_001);
        Long rawIdB = createProduct(TENANT_B, "R-B", "Raw B", "RAW_MATERIAL", null);
        Long recipeIdB = bomRecipeService.createDraftRecipe(newRecipe(TENANT_B, productIdB));
        bomRecipeService.updateDraftItems(TENANT_B, recipeIdB, List.of(item(rawIdB, "1", "0")));
        bomRecipeService.activateRecipe(TENANT_B, recipeIdB);

        BomRecipeRespDTO result = bomApi.getActiveRecipeBySkuCode(TENANT_A, SKU_001);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isNull();
        assertThat(result.getProductId()).isNull();
        assertThat(result.getLookupStatus()).isEqualTo("NO_PRODUCT");
        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void getActiveRecipeBySkuCode_productExistsButNoActiveRecipe_returnsEmptyState() {
        TenantContextHolder.setTenantId(TENANT_A);

        Long productId = createFinishedProduct(TENANT_A, "P-A", "Product A", SKU_001);

        BomRecipeRespDTO result = bomApi.getActiveRecipeBySkuCode(TENANT_A, SKU_001);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isNull();
        assertThat(result.getProductId()).isEqualTo(productId);
        assertThat(result.getLookupStatus()).isEqualTo("NO_ACTIVE_RECIPE");
        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void getActiveRecipeBySkuCode_inactiveProductNotMatched_returnsEmptyState() {
        TenantContextHolder.setTenantId(TENANT_A);

        Long productId = createFinishedProduct(TENANT_A, "P-A", "Product A", SKU_001);
        productMasterService.deactivate(productId, TENANT_A);

        BomRecipeRespDTO result = bomApi.getActiveRecipeBySkuCode(TENANT_A, SKU_001);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isNull();
        assertThat(result.getProductId()).isNull();
        assertThat(result.getLookupStatus()).isEqualTo("NO_PRODUCT");
        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void getActiveRecipeBySkuCode_nonFinishedProductNotMatched_returnsEmptyState() {
        TenantContextHolder.setTenantId(TENANT_A);

        createProduct(TENANT_A, "R-A", "Raw A", "RAW_MATERIAL", SKU_001);

        BomRecipeRespDTO result = bomApi.getActiveRecipeBySkuCode(TENANT_A, SKU_001);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isNull();
        assertThat(result.getProductId()).isNull();
        assertThat(result.getLookupStatus()).isEqualTo("NO_PRODUCT");
        assertThat(result.getItems()).isEmpty();
    }

    private Long createFinishedProduct(Long tenantId, String code, String name, String skuCode) {
        return createProduct(tenantId, code, name, "FINISHED", skuCode);
    }

    private Long createProduct(Long tenantId, String code, String name, String type, String skuCode) {
        ProductMasterDO product = new ProductMasterDO();
        product.setTenantId(tenantId);
        product.setProductCode(code);
        product.setProductName(name);
        product.setProductType(type);
        product.setSkuCode(skuCode);
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
