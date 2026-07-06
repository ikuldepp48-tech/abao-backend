package com.geihou.module.finance.product.dal;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.product.ProductTestConfig;
import com.geihou.module.finance.product.TestSchemaInitializer;
import com.geihou.module.finance.product.controller.admin.vo.*;
import com.geihou.module.finance.product.dal.dataobject.ProductCategoryDO;
import com.geihou.module.finance.product.dal.mapper.ProductCategoryMapper;
import com.geihou.module.finance.product.service.AddonGroupService;
import com.geihou.module.finance.product.service.ComboService;
import com.geihou.module.finance.product.service.SkuService;
import com.geihou.module.finance.product.service.SpuService;
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
 * Tenant isolation integration test for G1-02F addon/combo tables.
 *
 * <p>AC-1: All queries include tenant_id filtering; cross-tenant data is invisible.
 * Verifies that addon groups, addon options, combos, and combo items created
 * in one tenant are not visible from another tenant.
 */
@SpringBootTest(
        classes = ProductTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:addon_combo_tenant_iso;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class AddonComboTenantIsolationTest {

    @Autowired
    private AddonGroupService addonGroupService;

    @Autowired
    private ComboService comboService;

    @Autowired
    private SpuService spuService;

    @Autowired
    private SkuService skuService;

    @Autowired
    private ProductCategoryMapper categoryMapper;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        TestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void addonGroupCreatedInTenant1ShouldNotBeVisibleInTenant2() {
        // Create addon group in tenant 1
        TenantContextHolder.setTenantId(1L);
        Long groupId = addonGroupService.createAddonGroup(buildGroupReq("GRP_T1", "Tenant 1 Group", 0, 2));

        // Switch to tenant 2 and verify not visible
        TenantContextHolder.setTenantId(2L);
        List<AddonGroupRespVO> tenant2Groups = addonGroupService.listAddonGroups();
        assertThat(tenant2Groups).isEmpty();

        // Direct get should fail
        assertThatThrownBy(() -> addonGroupService.getAddonGroup(groupId))
                .isInstanceOf(ProductBusinessException.class);
    }

    @Test
    void sameAddonGroupCodeInDifferentTenantsShouldNotConflict() {
        // Create group with code "SHARED" in tenant 1
        TenantContextHolder.setTenantId(1L);
        addonGroupService.createAddonGroup(buildGroupReq("SHARED", "Tenant 1 Group", 0, 2));

        // Create group with same code in tenant 2 — should succeed
        TenantContextHolder.setTenantId(2L);
        Long groupId2 = addonGroupService.createAddonGroup(buildGroupReq("SHARED", "Tenant 2 Group", 0, 2));
        assertThat(groupId2).isNotNull();
    }

    @Test
    void comboCreatedInTenant1ShouldNotBeVisibleInTenant2() {
        // Set up tenant 1: category, SPU, SKU, combo
        TenantContextHolder.setTenantId(1L);
        Long catId = createCategory("CAT_T1");
        Long spuId = createSpu(catId, "COMBO_SPU_T1");
        Long comboSkuId = createActiveSku(spuId, "COMBO_SKU_T1", "50.00", "45.00");
        Long itemSpuId = createSpu(catId, "ITEM_SPU_T1");
        Long itemSkuId = createActiveSku(itemSpuId, "ITEM_SKU_T1", "20.00", "18.00");

        ComboCreateReqVO comboReq = new ComboCreateReqVO();
        comboReq.setComboSkuId(comboSkuId);
        comboReq.setComboName("Tenant 1 Combo");
        comboReq.setComboPrice(new BigDecimal("18.00"));
        ComboItemCreateReqVO itemReq = new ComboItemCreateReqVO();
        itemReq.setItemSkuId(itemSkuId);
        itemReq.setQuantity(1);
        comboReq.setItems(List.of(itemReq));
        Long comboId = comboService.createCombo(comboReq);

        // Switch to tenant 2 and verify combo not visible
        TenantContextHolder.setTenantId(2L);
        List<ComboRespVO> tenant2Combos = comboService.listCombos();
        assertThat(tenant2Combos).isEmpty();

        assertThatThrownBy(() -> comboService.getCombo(comboId))
                .isInstanceOf(ProductBusinessException.class);
    }

    @Test
    void comboItemShouldNotLeakAcrossTenants() {
        // Tenant 1: create combo with item
        TenantContextHolder.setTenantId(1L);
        Long catId = createCategory("CAT_A");
        Long comboSpuId = createSpu(catId, "C_SPU_A");
        Long comboSkuId = createActiveSku(comboSpuId, "C_SKU_A", "50.00", "45.00");
        Long itemSpuId = createSpu(catId, "I_SPU_A");
        Long itemSkuId = createActiveSku(itemSpuId, "I_SKU_A", "20.00", "18.00");

        ComboCreateReqVO comboReq = new ComboCreateReqVO();
        comboReq.setComboSkuId(comboSkuId);
        comboReq.setComboName("Combo A");
        comboReq.setComboPrice(new BigDecimal("18.00"));
        ComboItemCreateReqVO itemReq = new ComboItemCreateReqVO();
        itemReq.setItemSkuId(itemSkuId);
        itemReq.setQuantity(1);
        comboReq.setItems(List.of(itemReq));
        Long comboId = comboService.createCombo(comboReq);

        // Tenant 2: try to add tenant 1's item SKU to a new combo — should fail (SKU not found in tenant 2)
        TenantContextHolder.setTenantId(2L);
        Long cat2Id = createCategory("CAT_B");
        Long combo2SpuId = createSpu(cat2Id, "C_SPU_B");
        Long combo2SkuId = createActiveSku(combo2SpuId, "C_SKU_B", "50.00", "45.00");

        ComboCreateReqVO comboReq2 = new ComboCreateReqVO();
        comboReq2.setComboSkuId(combo2SkuId);
        comboReq2.setComboName("Combo B");
        comboReq2.setComboPrice(new BigDecimal("18.00"));
        ComboItemCreateReqVO crossTenantItem = new ComboItemCreateReqVO();
        crossTenantItem.setItemSkuId(itemSkuId); // tenant 1's SKU
        crossTenantItem.setQuantity(1);
        comboReq2.setItems(List.of(crossTenantItem));

        // Should fail because item_sku_id doesn't exist in tenant 2
        assertThatThrownBy(() -> comboService.createCombo(comboReq2))
                .isInstanceOf(ProductBusinessException.class);
    }

    // ===== Helpers =====

    private Long createCategory(String code) {
        Long tenantId = TenantContextHolder.getTenantId();
        ProductCategoryDO category = new ProductCategoryDO();
        category.setTenantId(tenantId);
        category.setCategoryCode(code);
        category.setCategoryName(code);
        category.setLevel(1);
        category.setSortOrder(0);
        category.setStatus("ACTIVE");
        category.setCreator("");
        category.setCreateTime(LocalDateTime.now());
        category.setUpdater("");
        category.setUpdateTime(LocalDateTime.now());
        category.setDeleted(false);
        categoryMapper.insert(category);
        return category.getId();
    }

    private Long createSpu(Long categoryId, String code) {
        SpuCreateReqVO spuReq = new SpuCreateReqVO();
        spuReq.setSpuCode(code);
        spuReq.setSpuName(code);
        spuReq.setCategoryId(categoryId);
        spuReq.setSpuType("FINISHED");
        return spuService.createSpu(spuReq);
    }

    private Long createActiveSku(Long spuId, String code, String listPrice, String sellingPrice) {
        SkuCreateReqVO skuReq = new SkuCreateReqVO();
        skuReq.setSpuId(spuId);
        skuReq.setSkuCode(code);
        skuReq.setSkuName(code);
        skuReq.setListPrice(new BigDecimal(listPrice));
        skuReq.setSellingPrice(new BigDecimal(sellingPrice));
        skuReq.setStockStrategy("TRACK_STOCK");
        Long skuId = skuService.createSku(skuReq);
        skuService.changeSkuStatus(skuId, "ACTIVE", "Ready for sale", 200L);
        return skuId;
    }

    private AddonGroupCreateReqVO buildGroupReq(String code, String name, int min, int max) {
        AddonGroupCreateReqVO req = new AddonGroupCreateReqVO();
        req.setGroupCode(code);
        req.setGroupName(name);
        req.setSelectMin(min);
        req.setSelectMax(max);
        return req;
    }
}
