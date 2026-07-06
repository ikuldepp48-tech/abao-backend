package com.geihou.module.finance.product.service;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.product.ProductTestConfig;
import com.geihou.module.finance.product.TestSchemaInitializer;
import com.geihou.module.finance.product.controller.app.vo.*;
import com.geihou.module.finance.product.dal.dataobject.*;
import com.geihou.module.finance.product.dal.mapper.*;
import com.geihou.module.finance.product.framework.ProductBusinessException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MenuAssemblyService integration test (G1-02H).
 *
 * <p>Tests menu assembly with filtering rules:
 * ACTIVE-only categories, ACTIVE-only SPUs (excluding RAW_MATERIAL/SEMI_FINISHED),
 * ACTIVE+SOLD_OUT SKUs, ACTIVE+SOLD_OUT addon options, ACTIVE combo only,
 * tenant isolation, empty menu, BigDecimal price type assertions.
 */
@SpringBootTest(
        classes = ProductTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:menu_assembly_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class MenuAssemblyServiceTest {

    @Autowired
    private MenuAssemblyService menuAssemblyService;

    @Autowired
    private ProductCategoryMapper categoryMapper;
    @Autowired
    private ProductSpuMapper spuMapper;
    @Autowired
    private ProductSkuMapper skuMapper;
    @Autowired
    private ProductAddonGroupMapper addonGroupMapper;
    @Autowired
    private ProductAddonOptionMapper addonOptionMapper;
    @Autowired
    private ProductComboMapper comboMapper;
    @Autowired
    private ProductComboItemMapper comboItemMapper;
    @Autowired
    private ProductSpuAddonGroupMapper spuAddonGroupMapper;
    @Autowired
    private DataSource dataSource;

    private Long categoryId;

    @BeforeEach
    void setUp() throws Exception {
        TestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(1L);

        // Create a default ACTIVE category
        categoryId = createCategory("DRINKS", "Drinks", null, 1, 0, "ACTIVE");
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // ===== Normal flow tests =====

    @Test
    void assembleMenuShouldReturnFullMenu() {
        Long spuId = createSpu("LATTE", "Latte", categoryId, "FINISHED", "ACTIVE", 0);
        createSku(spuId, "LATTE_M", "Latte Medium", "ACTIVE", new BigDecimal("28.00"), new BigDecimal("25.00"));

        MenuVO menu = menuAssemblyService.assembleMenu(100L);
        assertThat(menu).isNotNull();
        assertThat(menu.getStoreId()).isEqualTo(100L);
        assertThat(menu.getMenuSnapshotTime()).isNotNull();
        assertThat(menu.getCategories()).hasSize(1);
        assertThat(menu.getCategories().get(0).getCategoryName()).isEqualTo("Drinks");
        assertThat(menu.getCategories().get(0).getSpus()).hasSize(1);
        assertThat(menu.getCategories().get(0).getSpus().get(0).getSpuName()).isEqualTo("Latte");
        assertThat(menu.getCategories().get(0).getSpus().get(0).getSkus()).hasSize(1);
        assertThat(menu.getCategories().get(0).getSpus().get(0).getMinSellingPrice()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(menu.getCategories().get(0).getSpus().get(0).getMaxSellingPrice()).isEqualByComparingTo(new BigDecimal("25.00"));
    }

    // ===== ACTIVE-only category filtering =====

    @Test
    void assembleMenuShouldFilterDisabledCategories() {
        createCategory("DISABLED_CAT", "Disabled Cat", null, 1, 1, "DISABLED");
        Long spuId = createSpu("SPU1", "SPU1", categoryId, "FINISHED", "ACTIVE", 0);
        createSku(spuId, "SKU1", "SKU1", "ACTIVE", new BigDecimal("10.00"), new BigDecimal("8.00"));

        MenuVO menu = menuAssemblyService.assembleMenu(100L);
        assertThat(menu.getCategories()).hasSize(1);
        assertThat(menu.getCategories()).extracting(MenuCategoryVO::getCategoryName)
                .containsExactly("Drinks");
    }

    // ===== ACTIVE-only SPU filtering =====

    @Test
    void assembleMenuShouldFilterNonActiveSpus() {
        createSpu("SPU_ACTIVE", "Active SPU", categoryId, "FINISHED", "ACTIVE", 0);
        createSpu("SPU_PAUSED", "Paused SPU", categoryId, "FINISHED", "PAUSED", 1);
        createSpu("SPU_DEPRECATED", "Deprecated SPU", categoryId, "FINISHED", "DEPRECATED", 2);
        createSpu("SPU_NEW", "New SPU", categoryId, "FINISHED", "NEW", 3);

        MenuVO menu = menuAssemblyService.assembleMenu(100L);
        List<MenuSpuVO> spus = menu.getCategories().get(0).getSpus();
        assertThat(spus).hasSize(1);
        assertThat(spus.get(0).getSpuName()).isEqualTo("Active SPU");
    }

    // ===== ACTIVE+SOLD_OUT SKU filtering =====

    @Test
    void assembleMenuShouldReturnActiveAndSoldOutSkusOnly() {
        Long spuId = createSpu("SPU_SKU", "SKU Test SPU", categoryId, "FINISHED", "ACTIVE", 0);
        createSku(spuId, "SKU_ACTIVE", "Active SKU", "ACTIVE", new BigDecimal("10.00"), new BigDecimal("8.00"));
        createSku(spuId, "SKU_SOLD_OUT", "Sold Out SKU", "SOLD_OUT", new BigDecimal("12.00"), new BigDecimal("10.00"));
        createSku(spuId, "SKU_PAUSED", "Paused SKU", "PAUSED", new BigDecimal("14.00"), new BigDecimal("12.00"));
        createSku(spuId, "SKU_DEPRECATED", "Deprecated SKU", "DEPRECATED", new BigDecimal("16.00"), new BigDecimal("14.00"));
        createSku(spuId, "SKU_NEW", "New SKU", "NEW", new BigDecimal("18.00"), new BigDecimal("16.00"));

        MenuVO menu = menuAssemblyService.assembleMenu(100L);
        List<MenuSkuVO> skus = menu.getCategories().get(0).getSpus().get(0).getSkus();
        assertThat(skus).hasSize(2);
        assertThat(skus).extracting(MenuSkuVO::getStatus)
                .containsExactlyInAnyOrder("ACTIVE", "SOLD_OUT");
    }

    // ===== DEPRECATED SKU not returned =====

    @Test
    void assembleMenuShouldNotReturnDeprecatedSkus() {
        Long spuId = createSpu("SPU_DEP", "Dep Test", categoryId, "FINISHED", "ACTIVE", 0);
        createSku(spuId, "SKU_DEP", "Dep SKU", "DEPRECATED", new BigDecimal("10.00"), new BigDecimal("8.00"));

        MenuVO menu = menuAssemblyService.assembleMenu(100L);
        List<MenuSkuVO> skus = menu.getCategories().get(0).getSpus().get(0).getSkus();
        assertThat(skus).isEmpty();
    }

    // ===== RAW_MATERIAL / SEMI_FINISHED SPU not returned =====

    @Test
    void assembleMenuShouldNotReturnRawMaterialSpus() {
        createSpu("SPU_RAW", "Raw Material", categoryId, "RAW_MATERIAL", "ACTIVE", 0);
        createSpu("SPU_FINISHED", "Finished", categoryId, "FINISHED", "ACTIVE", 1);

        MenuVO menu = menuAssemblyService.assembleMenu(100L);
        List<MenuSpuVO> spus = menu.getCategories().get(0).getSpus();
        assertThat(spus).hasSize(1);
        assertThat(spus.get(0).getSpuName()).isEqualTo("Finished");
    }

    @Test
    void assembleMenuShouldNotReturnSemiFinishedSpus() {
        createSpu("SPU_SEMI", "Semi Finished", categoryId, "SEMI_FINISHED", "ACTIVE", 0);
        createSpu("SPU_FINISHED2", "Finished 2", categoryId, "FINISHED", "ACTIVE", 1);

        MenuVO menu = menuAssemblyService.assembleMenu(100L);
        List<MenuSpuVO> spus = menu.getCategories().get(0).getSpus();
        assertThat(spus).hasSize(1);
        assertThat(spus.get(0).getSpuName()).isEqualTo("Finished 2");
    }

    // ===== SERVICE type SPU returned =====

    @Test
    void assembleMenuShouldReturnServiceSpus() {
        createSpu("SPU_SERVICE", "Tea Seat Fee", categoryId, "SERVICE", "ACTIVE", 0);

        MenuVO menu = menuAssemblyService.assembleMenu(100L);
        List<MenuSpuVO> spus = menu.getCategories().get(0).getSpus();
        assertThat(spus).hasSize(1);
        assertThat(spus.get(0).getSpuType()).isEqualTo("SERVICE");
    }

    // ===== Empty menu =====

    @Test
    void assembleMenuShouldReturnEmptyMenuWhenNoActiveProducts() {
        // Switch to a tenant with no data at all
        TenantContextHolder.setTenantId(999L);
        MenuVO menu = menuAssemblyService.assembleMenu(100L);
        assertThat(menu).isNotNull();
        assertThat(menu.getCategories()).isEmpty();
        assertThat(menu.getStoreId()).isEqualTo(100L);
        assertThat(menu.getMenuSnapshotTime()).isNotNull();
    }

    // ===== Tenant isolation =====

    @Test
    void assembleMenuShouldIsolateByTenant() {
        // Tenant 1 data
        createSpu("SPU_T1", "Tenant 1 SPU", categoryId, "FINISHED", "ACTIVE", 0);

        // Switch to tenant 2
        TenantContextHolder.setTenantId(2L);
        Long cat2Id = createCategory("T2_CAT", "Tenant 2 Cat", null, 1, 0, "ACTIVE");
        createSpu("SPU_T2", "Tenant 2 SPU", cat2Id, "FINISHED", "ACTIVE", 0);

        MenuVO menu = menuAssemblyService.assembleMenu(200L);
        assertThat(menu.getCategories()).hasSize(1);
        assertThat(menu.getCategories().get(0).getCategoryName()).isEqualTo("Tenant 2 Cat");
        assertThat(menu.getCategories().get(0).getSpus()).hasSize(1);
        assertThat(menu.getCategories().get(0).getSpus().get(0).getSpuName()).isEqualTo("Tenant 2 SPU");
    }

    // ===== BigDecimal price type assertion =====

    @Test
    void menuSkuPriceFieldsShouldBeBigDecimal() {
        Long spuId = createSpu("SPU_BIG", "BigDecimal Test", categoryId, "FINISHED", "ACTIVE", 0);
        createSku(spuId, "SKU_BIG", "BigDecimal SKU", "ACTIVE", new BigDecimal("33.0000"), new BigDecimal("29.9900"));

        MenuVO menu = menuAssemblyService.assembleMenu(100L);
        MenuSkuVO sku = menu.getCategories().get(0).getSpus().get(0).getSkus().get(0);
        assertThat(sku.getListPrice()).isInstanceOf(BigDecimal.class);
        assertThat(sku.getSellingPrice()).isInstanceOf(BigDecimal.class);
        assertThat(sku.getListPrice()).isEqualByComparingTo(new BigDecimal("33.0000"));
        assertThat(sku.getSellingPrice()).isEqualByComparingTo(new BigDecimal("29.9900"));
    }

    // ===== SPU Detail tests =====

    @Test
    void assembleSpuDetailShouldReturnFullDetail() {
        Long spuId = createSpu("SPU_DETAIL", "Detail Test", categoryId, "FINISHED", "ACTIVE", 0);
        createSku(spuId, "SKU_D1", "Detail SKU 1", "ACTIVE", new BigDecimal("20.00"), new BigDecimal("18.00"));
        createSku(spuId, "SKU_D2", "Detail SKU 2", "SOLD_OUT", new BigDecimal("22.00"), new BigDecimal("20.00"));

        MenuSpuDetailVO detail = menuAssemblyService.assembleSpuDetail(spuId);
        assertThat(detail).isNotNull();
        assertThat(detail.getId()).isEqualTo(spuId);
        assertThat(detail.getSpuName()).isEqualTo("Detail Test");
        assertThat(detail.getCategoryId()).isEqualTo(categoryId);
        assertThat(detail.getCategoryName()).isEqualTo("Drinks");
        assertThat(detail.getSkus()).hasSize(2);
        assertThat(detail.getSkus()).extracting(MenuSkuVO::getStatus)
                .containsExactlyInAnyOrder("ACTIVE", "SOLD_OUT");
    }

    @Test
    void assembleSpuDetailShouldThrowWhenNotFound() {
        assertThatThrownBy(() -> menuAssemblyService.assembleSpuDetail(99999L))
                .isInstanceOf(ProductBusinessException.class);
    }

    @Test
    void assembleSpuDetailShouldThrowWhenNotActive() {
        Long spuId = createSpu("SPU_PAUSED", "Paused SPU", categoryId, "FINISHED", "PAUSED", 0);
        assertThatThrownBy(() -> menuAssemblyService.assembleSpuDetail(spuId))
                .isInstanceOf(ProductBusinessException.class);
    }

    @Test
    void assembleSpuDetailShouldThrowWhenRawMaterial() {
        Long spuId = createSpu("SPU_RAW_DETAIL", "Raw Material Detail", categoryId, "RAW_MATERIAL", "ACTIVE", 0);
        assertThatThrownBy(() -> menuAssemblyService.assembleSpuDetail(spuId))
                .isInstanceOf(ProductBusinessException.class);
    }

    @Test
    void assembleSpuDetailShouldThrowWhenSemiFinished() {
        Long spuId = createSpu("SPU_SEMI_DETAIL", "Semi Finished Detail", categoryId, "SEMI_FINISHED", "ACTIVE", 0);
        assertThatThrownBy(() -> menuAssemblyService.assembleSpuDetail(spuId))
                .isInstanceOf(ProductBusinessException.class);
    }

    // ===== Addon group filtering in SPU detail =====

    @Test
    void assembleSpuDetailShouldReturnActiveAndSoldOutAddonOptionsOnly() {
        Long spuId = createSpu("SPU_ADDON", "Addon Test", categoryId, "FINISHED", "ACTIVE", 0);
        createSku(spuId, "SKU_ADDON", "Addon SKU", "ACTIVE", new BigDecimal("10.00"), new BigDecimal("8.00"));

        // Create addon group
        Long groupId = createAddonGroup("ADDON_G1", "Toppings", 0, 2);
        // Map SPU to addon group
        createSpuAddonGroupMapping(spuId, groupId, 0);
        // Create options with different statuses
        Long optSkuId = createSku(spuId, "OPT_SKU", "Option SKU", "ACTIVE", new BigDecimal("1.00"), new BigDecimal("1.00"));
        createAddonOption(groupId, optSkuId, "Extra Shot", new BigDecimal("3.00"), "ACTIVE", 0);
        createAddonOption(groupId, optSkuId, "Whipped Cream", new BigDecimal("2.00"), "SOLD_OUT", 1);
        createAddonOption(groupId, optSkuId, "Disabled Option", new BigDecimal("5.00"), "DISABLED", 2);

        MenuSpuDetailVO detail = menuAssemblyService.assembleSpuDetail(spuId);
        assertThat(detail.getAddonGroups()).hasSize(1);
        List<MenuAddonOptionVO> options = detail.getAddonGroups().get(0).getOptions();
        assertThat(options).hasSize(2);
        assertThat(options).extracting(MenuAddonOptionVO::getStatus)
                .containsExactlyInAnyOrder("ACTIVE", "SOLD_OUT");
        // DISABLED should not be returned
        assertThat(options).extracting(MenuAddonOptionVO::getOptionName)
                .doesNotContain("Disabled Option");
    }

    @Test
    void assembleSpuDetailAddonOptionExtraPriceShouldBeBigDecimal() {
        Long spuId = createSpu("SPU_BIG2", "BigDecimal Addon", categoryId, "FINISHED", "ACTIVE", 0);
        createSku(spuId, "SKU_BIG2", "SKU", "ACTIVE", new BigDecimal("10.00"), new BigDecimal("8.00"));

        Long groupId = createAddonGroup("ADDON_BIG", "Big Group", 0, 1);
        createSpuAddonGroupMapping(spuId, groupId, 0);
        Long optSkuId = createSku(spuId, "OPT_SKU2", "Opt SKU", "ACTIVE", new BigDecimal("1.00"), new BigDecimal("1.00"));
        createAddonOption(groupId, optSkuId, "Big Option", new BigDecimal("2.5000"), "ACTIVE", 0);

        MenuSpuDetailVO detail = menuAssemblyService.assembleSpuDetail(spuId);
        MenuAddonOptionVO option = detail.getAddonGroups().get(0).getOptions().get(0);
        assertThat(option.getExtraPrice()).isInstanceOf(BigDecimal.class);
        assertThat(option.getExtraPrice()).isEqualByComparingTo(new BigDecimal("2.5000"));
    }

    // ===== Combo item filtering in SPU detail =====

    @Test
    void assembleSpuDetailComboItemsShouldBeEmptyWhenComboPaused() {
        Long comboSpuId = createSpu("SPU_COMBO", "Combo Meal", categoryId, "COMBO", "ACTIVE", 0);
        Long comboSkuId = createSku(comboSpuId, "COMBO_SKU", "Combo SKU", "ACTIVE", new BigDecimal("50.00"), new BigDecimal("45.00"));

        // Create combo with PAUSED status
        Long comboId = createCombo(comboSkuId, "Combo Meal", new BigDecimal("45.00"), "PAUSED");
        // Create an item SKU
        Long itemSpuId = createSpu("SPU_ITEM", "Item SPU", categoryId, "FINISHED", "ACTIVE", 0);
        Long itemSkuId = createSku(itemSpuId, "ITEM_SKU", "Item SKU", "ACTIVE", new BigDecimal("20.00"), new BigDecimal("18.00"));
        createComboItem(comboId, itemSkuId, 1, false, null);

        MenuSpuDetailVO detail = menuAssemblyService.assembleSpuDetail(comboSpuId);
        assertThat(detail.getComboItems()).isEmpty();
    }

    @Test
    void assembleSpuDetailComboItemsShouldBePopulatedWhenComboActive() {
        Long comboSpuId = createSpu("SPU_COMBO2", "Combo Meal 2", categoryId, "COMBO", "ACTIVE", 0);
        Long comboSkuId = createSku(comboSpuId, "COMBO_SKU2", "Combo SKU 2", "ACTIVE", new BigDecimal("50.00"), new BigDecimal("45.00"));

        Long comboId = createCombo(comboSkuId, "Combo Meal 2", new BigDecimal("45.00"), "ACTIVE");
        Long itemSpuId = createSpu("SPU_ITEM2", "Item SPU 2", categoryId, "FINISHED", "ACTIVE", 0);
        Long itemSkuId = createSku(itemSpuId, "ITEM_SKU2", "Item SKU 2", "ACTIVE", new BigDecimal("20.00"), new BigDecimal("18.00"));
        createComboItem(comboId, itemSkuId, 2, false, null);

        MenuSpuDetailVO detail = menuAssemblyService.assembleSpuDetail(comboSpuId);
        assertThat(detail.getComboItems()).hasSize(1);
        assertThat(detail.getComboItems().get(0).getItemSkuName()).isEqualTo("Item SKU 2");
        assertThat(detail.getComboItems().get(0).getQuantity()).isEqualTo(2);
    }

    @Test
    void assembleSpuDetailComboItemsShouldExcludeDeprecatedSkus() {
        Long comboSpuId = createSpu("SPU_COMBO3", "Combo 3", categoryId, "COMBO", "ACTIVE", 0);
        Long comboSkuId = createSku(comboSpuId, "COMBO_SKU3", "Combo SKU 3", "ACTIVE", new BigDecimal("50.00"), new BigDecimal("45.00"));

        Long comboId = createCombo(comboSkuId, "Combo 3", new BigDecimal("45.00"), "ACTIVE");
        Long itemSpuId = createSpu("SPU_ITEM3", "Item 3", categoryId, "FINISHED", "ACTIVE", 0);
        // Create a DEPRECATED item SKU
        Long depSkuId = createSku(itemSpuId, "DEP_ITEM", "Deprecated Item", "DEPRECATED", new BigDecimal("10.00"), new BigDecimal("8.00"));
        // Create an ACTIVE item SKU
        Long actSkuId = createSku(itemSpuId, "ACT_ITEM", "Active Item", "ACTIVE", new BigDecimal("12.00"), new BigDecimal("10.00"));
        createComboItem(comboId, depSkuId, 1, false, null);
        createComboItem(comboId, actSkuId, 1, true, 1);

        MenuSpuDetailVO detail = menuAssemblyService.assembleSpuDetail(comboSpuId);
        assertThat(detail.getComboItems()).hasSize(1);
        assertThat(detail.getComboItems().get(0).getItemSkuName()).isEqualTo("Active Item");
    }

    @Test
    void assembleSpuDetailComboItemsShouldBeEmptyWhenSpuNotCombo() {
        Long spuId = createSpu("SPU_NOT_COMBO", "Regular SPU", categoryId, "FINISHED", "ACTIVE", 0);
        createSku(spuId, "SKU_REG", "Regular SKU", "ACTIVE", new BigDecimal("10.00"), new BigDecimal("8.00"));

        MenuSpuDetailVO detail = menuAssemblyService.assembleSpuDetail(spuId);
        assertThat(detail.getComboItems()).isEmpty();
    }

    // ===== Multi-level category tree =====

    @Test
    void assembleMenuShouldBuildMultiLevelCategoryTree() {
        Long parentId = createCategory("PARENT", "Parent", null, 1, 0, "ACTIVE");
        Long childId = createCategory("CHILD", "Child", parentId, 2, 0, "ACTIVE");
        Long spuId = createSpu("SPU_CHILD", "Child SPU", childId, "FINISHED", "ACTIVE", 0);
        createSku(spuId, "SKU_CHILD", "Child SKU", "ACTIVE", new BigDecimal("10.00"), new BigDecimal("8.00"));

        MenuVO menu = menuAssemblyService.assembleMenu(100L);
        assertThat(menu.getCategories()).hasSize(2); // "Drinks" and "Parent"
        // Find the parent category
        MenuCategoryVO parentCat = menu.getCategories().stream()
                .filter(c -> "Parent".equals(c.getCategoryName()))
                .findFirst().orElse(null);
        assertThat(parentCat).isNotNull();
        assertThat(parentCat.getChildren()).hasSize(1);
        assertThat(parentCat.getChildren().get(0).getCategoryName()).isEqualTo("Child");
        assertThat(parentCat.getChildren().get(0).getSpus()).hasSize(1);
    }

    // ===== Helper methods =====

    private Long createCategory(String code, String name, Long parentId, int level, int sortOrder, String status) {
        ProductCategoryDO cat = new ProductCategoryDO();
        cat.setTenantId(TenantContextHolder.getTenantId());
        cat.setCategoryCode(code);
        cat.setCategoryName(name);
        cat.setParentCategoryId(parentId);
        cat.setLevel(level);
        cat.setSortOrder(sortOrder);
        cat.setStatus(status);
        cat.setCreator("");
        cat.setCreateTime(LocalDateTime.now());
        cat.setUpdater("");
        cat.setUpdateTime(LocalDateTime.now());
        cat.setDeleted(false);
        categoryMapper.insert(cat);
        return cat.getId();
    }

    private Long createSpu(String code, String name, Long categoryId, String spuType, String status, int sortOrder) {
        ProductSpuDO spu = new ProductSpuDO();
        spu.setTenantId(TenantContextHolder.getTenantId());
        spu.setSpuCode(code);
        spu.setSpuName(name);
        spu.setCategoryId(categoryId);
        spu.setSpuType(spuType);
        spu.setIsRecommended(false);
        spu.setIsNewArrival(false);
        spu.setSortOrder(sortOrder);
        spu.setTotalSoldCount(0);
        spu.setStatus(status);
        spu.setCreator("");
        spu.setCreateTime(LocalDateTime.now());
        spu.setUpdater("");
        spu.setUpdateTime(LocalDateTime.now());
        spu.setDeleted(false);
        spuMapper.insert(spu);
        return spu.getId();
    }

    private Long createSku(Long spuId, String code, String name, String status, BigDecimal listPrice, BigDecimal sellingPrice) {
        ProductSkuDO sku = new ProductSkuDO();
        sku.setTenantId(TenantContextHolder.getTenantId());
        sku.setSpuId(spuId);
        sku.setSkuCode(code);
        sku.setSkuName(name);
        sku.setListPrice(listPrice);
        sku.setSellingPrice(sellingPrice);
        sku.setMinOrderQuantity(1);
        sku.setStockStrategy("UNLIMITED");
        sku.setStatus(status);
        sku.setStatusReason("test");
        sku.setTotalSoldCount(0);
        sku.setCreator("");
        sku.setCreateTime(LocalDateTime.now());
        sku.setUpdater("");
        sku.setUpdateTime(LocalDateTime.now());
        sku.setDeleted(false);
        skuMapper.insert(sku);
        return sku.getId();
    }

    private Long createAddonGroup(String code, String name, int selectMin, int selectMax) {
        ProductAddonGroupDO group = new ProductAddonGroupDO();
        group.setTenantId(TenantContextHolder.getTenantId());
        group.setGroupCode(code);
        group.setGroupName(name);
        group.setSelectMin(selectMin);
        group.setSelectMax(selectMax);
        group.setIsRequired(false);
        group.setSortOrder(0);
        group.setCreator("");
        group.setCreateTime(LocalDateTime.now());
        group.setUpdater("");
        group.setUpdateTime(LocalDateTime.now());
        group.setDeleted(false);
        addonGroupMapper.insert(group);
        return group.getId();
    }

    private void createAddonOption(Long groupId, Long optionSkuId, String name, BigDecimal extraPrice, String status, int sortOrder) {
        ProductAddonOptionDO option = new ProductAddonOptionDO();
        option.setTenantId(TenantContextHolder.getTenantId());
        option.setAddonGroupId(groupId);
        option.setOptionSkuId(optionSkuId);
        option.setOptionName(name);
        option.setExtraPrice(extraPrice);
        option.setSortOrder(sortOrder);
        option.setStatus(status);
        option.setCreator("");
        option.setCreateTime(LocalDateTime.now());
        option.setUpdater("");
        option.setUpdateTime(LocalDateTime.now());
        option.setDeleted(false);
        addonOptionMapper.insert(option);
    }

    private void createSpuAddonGroupMapping(Long spuId, Long addonGroupId, int sortOrder) {
        ProductSpuAddonGroupDO mapping = new ProductSpuAddonGroupDO();
        mapping.setTenantId(TenantContextHolder.getTenantId());
        mapping.setSpuId(spuId);
        mapping.setAddonGroupId(addonGroupId);
        mapping.setSortOrder(sortOrder);
        mapping.setCreator("");
        mapping.setCreateTime(LocalDateTime.now());
        mapping.setUpdater("");
        mapping.setUpdateTime(LocalDateTime.now());
        mapping.setDeleted(false);
        spuAddonGroupMapper.insert(mapping);
    }

    private Long createCombo(Long comboSkuId, String name, BigDecimal price, String status) {
        ProductComboDO combo = new ProductComboDO();
        combo.setTenantId(TenantContextHolder.getTenantId());
        combo.setComboSkuId(comboSkuId);
        combo.setComboName(name);
        combo.setComboPrice(price);
        combo.setStatus(status);
        combo.setCreator("");
        combo.setCreateTime(LocalDateTime.now());
        combo.setUpdater("");
        combo.setUpdateTime(LocalDateTime.now());
        combo.setDeleted(false);
        comboMapper.insert(combo);
        return combo.getId();
    }

    private void createComboItem(Long comboId, Long itemSkuId, int quantity, boolean isOptional, Integer alternativeGroup) {
        ProductComboItemDO item = new ProductComboItemDO();
        item.setTenantId(TenantContextHolder.getTenantId());
        item.setComboId(comboId);
        item.setItemSkuId(itemSkuId);
        item.setQuantity(quantity);
        item.setIsOptional(isOptional);
        item.setAlternativeGroup(alternativeGroup);
        item.setCreator("");
        item.setCreateTime(LocalDateTime.now());
        item.setUpdater("");
        item.setUpdateTime(LocalDateTime.now());
        item.setDeleted(false);
        comboItemMapper.insert(item);
    }
}
