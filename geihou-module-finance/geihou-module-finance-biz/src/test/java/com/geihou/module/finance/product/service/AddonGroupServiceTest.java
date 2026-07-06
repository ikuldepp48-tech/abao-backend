package com.geihou.module.finance.product.service;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.product.ProductTestConfig;
import com.geihou.module.finance.product.TestSchemaInitializer;
import com.geihou.module.finance.product.controller.admin.vo.*;
import com.geihou.module.finance.product.dal.dataobject.ProductCategoryDO;
import com.geihou.module.finance.product.dal.mapper.ProductCategoryMapper;
import com.geihou.module.finance.product.framework.ProductBusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Addon group service integration test.
 *
 * <p>AC-7: select_min <= select_max validation (ADDON_GROUP_INVALID).
 * AC-9: option_sku_id must reference existing SKU in same tenant.
 * AC-3: soft delete (no hard delete).
 */
@SpringBootTest(
        classes = ProductTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:addon_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class AddonGroupServiceTest {

    @Autowired
    private AddonGroupService addonGroupService;

    @Autowired
    private SpuService spuService;

    @Autowired
    private SkuService skuService;

    @Autowired
    private ProductCategoryMapper categoryMapper;

    @Autowired
    private DataSource dataSource;

    private Long skuId;

    @BeforeEach
    void setUp() throws Exception {
        TestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(1L);

        // Create category + SPU + SKU for option_sku_id reference
        ProductCategoryDO category = new ProductCategoryDO();
        category.setTenantId(1L);
        category.setCategoryCode("FOOD");
        category.setCategoryName("Food");
        category.setLevel(1);
        category.setSortOrder(0);
        category.setStatus("ACTIVE");
        category.setCreator("");
        category.setCreateTime(LocalDateTime.now());
        category.setUpdater("");
        category.setUpdateTime(LocalDateTime.now());
        category.setDeleted(false);
        categoryMapper.insert(category);

        SpuCreateReqVO spuReq = new SpuCreateReqVO();
        spuReq.setSpuCode("SPU_TEST");
        spuReq.setSpuName("Test SPU");
        spuReq.setCategoryId(category.getId());
        spuReq.setSpuType("FINISHED");
        Long spuId = spuService.createSpu(spuReq);

        SkuCreateReqVO skuReq = new SkuCreateReqVO();
        skuReq.setSpuId(spuId);
        skuReq.setSkuCode("SKU_TEST");
        skuReq.setSkuName("Test SKU");
        skuReq.setListPrice(new BigDecimal("10.00"));
        skuReq.setSellingPrice(new BigDecimal("8.00"));
        skuReq.setStockStrategy("TRACK_STOCK");
        skuId = skuService.createSku(skuReq);

        // Activate SKU so it can be used as option_sku_id
        skuService.changeSkuStatus(skuId, "ACTIVE", "Ready for sale", 200L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createAddonGroupShouldReturnId() {
        Long id = addonGroupService.createAddonGroup(buildGroupReq("GRP_1", "Group 1", 0, 3));
        assertThat(id).isNotNull();
    }

    @Test
    void createAddonGroupWithDuplicateCodeShouldFail() {
        addonGroupService.createAddonGroup(buildGroupReq("GRP_DUP", "Group 1", 0, 3));
        assertThatThrownBy(() -> addonGroupService.createAddonGroup(buildGroupReq("GRP_DUP", "Group 2", 0, 3)))
                .isInstanceOf(ProductBusinessException.class);
    }

    @Test
    void createAddonGroupWithSelectMinGreaterThanMaxShouldFail() {
        // AC-7: select_min > select_max → ADDON_GROUP_INVALID
        assertThatThrownBy(() -> addonGroupService.createAddonGroup(buildGroupReq("GRP_BAD", "Bad", 5, 3)))
                .isInstanceOf(ProductBusinessException.class);
    }

    @Test
    void createAddonGroupWithEqualMinAndMaxShouldPass() {
        Long id = addonGroupService.createAddonGroup(buildGroupReq("GRP_EQ", "Equal", 2, 2));
        assertThat(id).isNotNull();
    }

    @Test
    void getAddonGroupShouldIncludeOptions() {
        Long groupId = addonGroupService.createAddonGroup(buildGroupReq("GRP_OPT", "Options", 0, 2));

        AddonOptionCreateReqVO optReq = new AddonOptionCreateReqVO();
        optReq.setOptionSkuId(skuId);
        optReq.setOptionName("Extra cheese");
        optReq.setExtraPrice(new BigDecimal("2.50"));
        Long optionId = addonGroupService.createAddonOption(groupId, optReq);

        AddonGroupRespVO resp = addonGroupService.getAddonGroup(groupId);
        assertThat(resp.getOptions()).hasSize(1);
        assertThat(resp.getOptions().get(0).getId()).isEqualTo(optionId);
        assertThat(resp.getOptions().get(0).getExtraPrice()).isEqualByComparingTo(new BigDecimal("2.50"));
    }

    @Test
    void createAddonOptionWithNonExistentSkuShouldFail() {
        // AC-9: option_sku_id must reference existing SKU in same tenant
        Long groupId = addonGroupService.createAddonGroup(buildGroupReq("GRP_SKU", "SKU check", 0, 1));

        AddonOptionCreateReqVO optReq = new AddonOptionCreateReqVO();
        optReq.setOptionSkuId(99999L);
        optReq.setOptionName("Bad option");
        optReq.setExtraPrice(BigDecimal.ZERO);

        assertThatThrownBy(() -> addonGroupService.createAddonOption(groupId, optReq))
                .isInstanceOf(ProductBusinessException.class);
    }

    @Test
    void updateAddonGroupShouldChangeName() {
        Long groupId = addonGroupService.createAddonGroup(buildGroupReq("GRP_UPD", "Original", 0, 1));

        AddonGroupUpdateReqVO upd = new AddonGroupUpdateReqVO();
        upd.setGroupName("Updated");
        addonGroupService.updateAddonGroup(groupId, upd);

        AddonGroupRespVO resp = addonGroupService.getAddonGroup(groupId);
        assertThat(resp.getGroupName()).isEqualTo("Updated");
    }

    @Test
    void deleteAddonGroupShouldSoftDelete() {
        Long groupId = addonGroupService.createAddonGroup(buildGroupReq("GRP_DEL", "Delete me", 0, 1));
        addonGroupService.deleteAddonGroup(groupId);

        // Should not be found after soft delete
        assertThatThrownBy(() -> addonGroupService.getAddonGroup(groupId))
                .isInstanceOf(ProductBusinessException.class);
    }

    @Test
    void changeAddonOptionStatusShouldUpdateStatus() {
        Long groupId = addonGroupService.createAddonGroup(buildGroupReq("GRP_STATUS", "Status", 0, 1));

        AddonOptionCreateReqVO optReq = new AddonOptionCreateReqVO();
        optReq.setOptionSkuId(skuId);
        optReq.setOptionName("Option");
        optReq.setExtraPrice(BigDecimal.ONE);
        Long optionId = addonGroupService.createAddonOption(groupId, optReq);

        AddonOptionStatusChangeReqVO statusReq = new AddonOptionStatusChangeReqVO();
        statusReq.setNewStatus("SOLD_OUT");
        statusReq.setReason("Stock depleted");
        addonGroupService.changeAddonOptionStatus(groupId, optionId, statusReq);

        AddonGroupRespVO resp = addonGroupService.getAddonGroup(groupId);
        assertThat(resp.getOptions().get(0).getStatus()).isEqualTo("SOLD_OUT");
    }

    @Test
    void changeAddonOptionStatusWithoutReasonShouldFail() {
        Long groupId = addonGroupService.createAddonGroup(buildGroupReq("GRP_NOR", "No reason", 0, 1));

        AddonOptionCreateReqVO optReq = new AddonOptionCreateReqVO();
        optReq.setOptionSkuId(skuId);
        optReq.setOptionName("Option");
        optReq.setExtraPrice(BigDecimal.ONE);
        Long optionId = addonGroupService.createAddonOption(groupId, optReq);

        AddonOptionStatusChangeReqVO statusReq = new AddonOptionStatusChangeReqVO();
        statusReq.setNewStatus("DISABLED");
        statusReq.setReason("ok");

        assertThatThrownBy(() -> addonGroupService.changeAddonOptionStatus(groupId, optionId, statusReq))
                .isInstanceOf(ProductBusinessException.class);
    }

    @Test
    void extraPriceShouldBeBigDecimal() {
        Long groupId = addonGroupService.createAddonGroup(buildGroupReq("GRP_BD", "BigDecimal", 0, 1));

        AddonOptionCreateReqVO optReq = new AddonOptionCreateReqVO();
        optReq.setOptionSkuId(skuId);
        optReq.setOptionName("Option");
        optReq.setExtraPrice(new BigDecimal("3.50"));
        Long optionId = addonGroupService.createAddonOption(groupId, optReq);

        AddonGroupRespVO resp = addonGroupService.getAddonGroup(groupId);
        assertThat(resp.getOptions().get(0).getExtraPrice()).isInstanceOf(BigDecimal.class);
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
