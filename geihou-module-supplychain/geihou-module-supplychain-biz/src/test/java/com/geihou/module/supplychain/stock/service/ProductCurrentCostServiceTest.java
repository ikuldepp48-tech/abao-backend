package com.geihou.module.supplychain.stock.service;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.supplychain.api.stock.dto.ProductCurrentCostRespDTO;
import com.geihou.module.supplychain.bom.BomTestSchemaInitializer;
import com.geihou.module.supplychain.bom.dal.dataobject.BomRecipeDO;
import com.geihou.module.supplychain.bom.dal.dataobject.BomRecipeItemDO;
import com.geihou.module.supplychain.bom.dal.dataobject.ProductMasterDO;
import com.geihou.module.supplychain.bom.service.BomRecipeService;
import com.geihou.module.supplychain.bom.service.ProductMasterService;
import com.geihou.module.supplychain.stock.SupplychainTestConfig;
import com.geihou.module.supplychain.stock.SupplychainTestSchemaInitializer;
import com.geihou.module.supplychain.stock.dal.dataobject.StockBalanceDO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockItemDO;
import com.geihou.module.supplychain.stock.dal.mapper.StockBalanceMapper;
import com.geihou.module.supplychain.stock.dal.mapper.StockItemMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ProductCurrentCostService}.
 *
 * <p>Source: TASK-G2-02G.
 */
@SpringBootTest(
        classes = SupplychainTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:product_current_cost_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class ProductCurrentCostServiceTest {

    private static final Long TENANT_ID = 1L;

    @Autowired
    private ProductCurrentCostService productCurrentCostService;
    @Autowired
    private ProductMasterService productMasterService;
    @Autowired
    private BomRecipeService bomRecipeService;
    @Autowired
    private StockItemMapper stockItemMapper;
    @Autowired
    private StockBalanceMapper stockBalanceMapper;
    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        SupplychainTestSchemaInitializer.initialize(dataSource);
        BomTestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void getProductCurrentCost_activeBomAndStockCost_returnsEstimate() {
        Long finishedId = createProduct("P-A", "Product A", "FINISHED");
        Long rawId = createProduct("R-A", "Raw A", "RAW_MATERIAL");
        Long recipeId = bomRecipeService.createDraftRecipe(newRecipe(finishedId));
        bomRecipeService.updateDraftItems(TENANT_ID, recipeId, List.of(item(rawId, "2", "0")));
        bomRecipeService.activateRecipe(TENANT_ID, recipeId);
        Long stockItemId = insertStockItem("R-A", "Raw A");
        insertBalance(stockItemId, 10L, "10", "10");

        ProductCurrentCostRespDTO result =
                productCurrentCostService.getProductCurrentCost(TENANT_ID, finishedId);

        assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(result.getProductId()).isEqualTo(finishedId);
        assertThat(result.getCalculationMode()).isEqualTo("BOM_STOCK_BALANCE_ESTIMATE");
        assertThat(result.getCostSource()).isEqualTo("STOCK_BALANCE_AVG_UNIT_COST");
        assertThat(result.getCurrentCost()).isEqualByComparingTo("20.000000");
        assertThat(result.getComponentCount()).isEqualTo(1);
        assertThat(result.getCostedComponentCount()).isEqualTo(1);
        assertThat(result.getComponents()).hasSize(1);
        assertThat(result.getComponents().get(0).getProductCode()).isEqualTo("R-A");
        assertThat(result.getComponents().get(0).getUnitCost()).isEqualByComparingTo("10");
        assertThat(result.getComponents().get(0).getTotalCost()).isEqualByComparingTo("20.000000");
    }

    @Test
    void getProductCurrentCost_noActiveBom_returnsExplicitNoActiveBom() {
        Long finishedId = createProduct("P-A", "Product A", "FINISHED");

        ProductCurrentCostRespDTO result =
                productCurrentCostService.getProductCurrentCost(TENANT_ID, finishedId);

        assertThat(result.getCalculationMode()).isEqualTo("NO_ACTIVE_BOM");
        assertThat(result.getCurrentCost()).isNull();
        assertThat(result.getComponentCount()).isZero();
        assertThat(result.getCostedComponentCount()).isZero();
        assertThat(result.getComponents()).isEmpty();
    }

    private Long createProduct(String code, String name, String type) {
        ProductMasterDO product = new ProductMasterDO();
        product.setTenantId(TENANT_ID);
        product.setProductCode(code);
        product.setProductName(name);
        product.setProductType(type);
        product.setUnit("kg");
        return productMasterService.createProduct(product);
    }

    private BomRecipeDO newRecipe(Long productId) {
        BomRecipeDO recipe = new BomRecipeDO();
        recipe.setTenantId(TENANT_ID);
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

    private Long insertStockItem(String skuCode, String itemName) {
        StockItemDO item = new StockItemDO();
        item.setTenantId(TENANT_ID);
        item.setSkuCode(skuCode);
        item.setItemName(itemName);
        item.setUnit("kg");
        item.setIsRawMaterial(true);
        item.setIsSemiFinished(false);
        item.setIsFinished(false);
        item.setIsActive(true);
        item.setCreator("test");
        item.setCreateTime(LocalDateTime.now());
        item.setUpdater("test");
        item.setUpdateTime(LocalDateTime.now());
        item.setDeleted(false);
        stockItemMapper.insert(item);
        return item.getId();
    }

    private void insertBalance(Long stockItemId, Long locationId, String totalQty, String avgUnitCost) {
        StockBalanceDO balance = new StockBalanceDO();
        balance.setTenantId(TENANT_ID);
        balance.setStockItemId(stockItemId);
        balance.setLocationId(locationId);
        balance.setAvailableQty(new BigDecimal(totalQty));
        balance.setTotalQty(new BigDecimal(totalQty));
        balance.setReservedQty(BigDecimal.ZERO);
        balance.setAvgUnitCost(new BigDecimal(avgUnitCost));
        balance.setVersion(0);
        balance.setCreator("test");
        balance.setCreateTime(LocalDateTime.now());
        balance.setUpdater("test");
        balance.setUpdateTime(LocalDateTime.now());
        balance.setDeleted(false);
        stockBalanceMapper.insert(balance);
    }
}
