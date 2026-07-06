package com.geihou.module.finance.product.service;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.product.ProductTestConfig;
import com.geihou.module.finance.product.TestSchemaInitializer;
import com.geihou.module.finance.product.controller.admin.vo.AddonGroupCreateReqVO;
import com.geihou.module.finance.product.controller.admin.vo.SpuAddonGroupRespVO;
import com.geihou.module.finance.product.controller.admin.vo.SpuCreateReqVO;
import com.geihou.module.finance.product.dal.dataobject.ProductCategoryDO;
import com.geihou.module.finance.product.dal.mapper.ProductCategoryMapper;
import com.geihou.module.finance.product.framework.ProductBusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SPU-AddonGroup mapping service test.
 *
 * <p>Tests mapping CRUD, duplicate validation, cross-tenant isolation,
 * SPU/addon group existence validation.
 */
@SpringBootTest(
        classes = ProductTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:spu_addon_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class SpuAddonGroupServiceTest {

    @Autowired
    private SpuAddonGroupService spuAddonGroupService;

    @Autowired
    private SpuService spuService;

    @Autowired
    private AddonGroupService addonGroupService;

    @Autowired
    private ProductCategoryMapper categoryMapper;

    @Autowired
    private DataSource dataSource;

    private Long spuId;
    private Long addonGroupId;

    @BeforeEach
    void setUp() throws Exception {
        TestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(1L);

        // Create category
        ProductCategoryDO category = new ProductCategoryDO();
        category.setTenantId(1L);
        category.setCategoryCode("DRINKS");
        category.setCategoryName("Drinks");
        category.setLevel(1);
        category.setSortOrder(0);
        category.setStatus("ACTIVE");
        category.setCreator("");
        category.setCreateTime(LocalDateTime.now());
        category.setUpdater("");
        category.setUpdateTime(LocalDateTime.now());
        category.setDeleted(false);
        categoryMapper.insert(category);

        // Create SPU
        SpuCreateReqVO spuReq = new SpuCreateReqVO();
        spuReq.setSpuCode("COFFEE");
        spuReq.setSpuName("Coffee");
        spuReq.setCategoryId(category.getId());
        spuReq.setSpuType("FINISHED");
        spuId = spuService.createSpu(spuReq);

        // Create addon group
        AddonGroupCreateReqVO groupReq = new AddonGroupCreateReqVO();
        groupReq.setGroupCode("TOPPINGS");
        groupReq.setGroupName("Toppings");
        groupReq.setSelectMin(0);
        groupReq.setSelectMax(3);
        groupReq.setIsRequired(false);
        groupReq.setSortOrder(0);
        addonGroupId = addonGroupService.createAddonGroup(groupReq);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void assignAddonGroupShouldSucceed() {
        Long mappingId = spuAddonGroupService.assignAddonGroup(spuId, addonGroupId, 0);
        assertThat(mappingId).isNotNull();

        List<SpuAddonGroupRespVO> mappings = spuAddonGroupService.listBySpu(spuId);
        assertThat(mappings).hasSize(1);
        assertThat(mappings.get(0).getAddonGroupId()).isEqualTo(addonGroupId);
        assertThat(mappings.get(0).getAddonGroupCode()).isEqualTo("TOPPINGS");
        assertThat(mappings.get(0).getAddonGroupName()).isEqualTo("Toppings");
    }

    @Test
    void assignDuplicateAddonGroupShouldFail() {
        spuAddonGroupService.assignAddonGroup(spuId, addonGroupId, 0);

        assertThatThrownBy(() -> spuAddonGroupService.assignAddonGroup(spuId, addonGroupId, 1))
                .isInstanceOf(ProductBusinessException.class);
    }

    @Test
    void assignAddonGroupWithNonExistentSpuShouldFail() {
        assertThatThrownBy(() -> spuAddonGroupService.assignAddonGroup(99999L, addonGroupId, 0))
                .isInstanceOf(ProductBusinessException.class);
    }

    @Test
    void assignAddonGroupWithNonExistentGroupShouldFail() {
        assertThatThrownBy(() -> spuAddonGroupService.assignAddonGroup(spuId, 99999L, 0))
                .isInstanceOf(ProductBusinessException.class);
    }

    @Test
    void removeAddonGroupShouldSucceed() {
        spuAddonGroupService.assignAddonGroup(spuId, addonGroupId, 0);

        spuAddonGroupService.removeAddonGroup(spuId, addonGroupId);

        List<SpuAddonGroupRespVO> mappings = spuAddonGroupService.listBySpu(spuId);
        assertThat(mappings).isEmpty();
    }

    @Test
    void removeNonExistentMappingShouldFail() {
        assertThatThrownBy(() -> spuAddonGroupService.removeAddonGroup(spuId, addonGroupId))
                .isInstanceOf(ProductBusinessException.class);
    }

    @Test
    void crossTenantIsolationShouldPreventAccess() {
        // Assign in tenant 1
        spuAddonGroupService.assignAddonGroup(spuId, addonGroupId, 0);

        // Switch to tenant 2 — should not see tenant 1's mappings
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(2L);

        List<SpuAddonGroupRespVO> mappings = spuAddonGroupService.listBySpu(spuId);
        assertThat(mappings).isEmpty();

        // Also verify getAddonGroupIdsBySpu returns empty for wrong tenant
        List<Long> groupIds = spuAddonGroupService.getAddonGroupIdsBySpu(spuId);
        assertThat(groupIds).isEmpty();
    }

    @Test
    void multipleAddonGroupsForSameSpu() {
        // Create second addon group
        AddonGroupCreateReqVO groupReq2 = new AddonGroupCreateReqVO();
        groupReq2.setGroupCode("SIZES");
        groupReq2.setGroupName("Sizes");
        groupReq2.setSelectMin(1);
        groupReq2.setSelectMax(1);
        groupReq2.setIsRequired(true);
        groupReq2.setSortOrder(0);
        Long addonGroupId2 = addonGroupService.createAddonGroup(groupReq2);

        spuAddonGroupService.assignAddonGroup(spuId, addonGroupId, 0);
        spuAddonGroupService.assignAddonGroup(spuId, addonGroupId2, 1);

        List<SpuAddonGroupRespVO> mappings = spuAddonGroupService.listBySpu(spuId);
        assertThat(mappings).hasSize(2);
    }
}
