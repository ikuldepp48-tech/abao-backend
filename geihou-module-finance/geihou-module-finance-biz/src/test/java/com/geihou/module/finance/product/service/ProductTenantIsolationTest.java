package com.geihou.module.finance.product.service;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.product.ProductTestConfig;
import com.geihou.module.finance.product.TestSchemaInitializer;
import com.geihou.module.finance.product.controller.admin.vo.SpuCreateReqVO;
import com.geihou.module.finance.product.controller.admin.vo.SpuPageReqVO;
import com.geihou.module.finance.product.controller.admin.vo.SpuRespVO;
import com.geihou.common.pojo.PageResult;
import com.geihou.module.finance.product.dal.dataobject.ProductCategoryDO;
import com.geihou.module.finance.product.dal.mapper.ProductCategoryMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tenant isolation integration test.
 *
 * <p>AC-1: All queries include tenant_id filtering; cross-tenant data is invisible.
 * Verifies that SPUs created in one tenant are not visible from another tenant.
 */
@SpringBootTest(
        classes = ProductTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:tenant_iso_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class ProductTenantIsolationTest {

    @Autowired
    private SpuService spuService;

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
    void spuCreatedInTenant1ShouldNotBeVisibleInTenant2() {
        // Create SPU in tenant 1
        TenantContextHolder.setTenantId(1L);
        Long categoryId = createCategoryForCurrentTenant("CAT_T1", "Tenant 1 Category");
        SpuCreateReqVO req = new SpuCreateReqVO();
        req.setSpuCode("T1_SPU");
        req.setSpuName("Tenant 1 SPU");
        req.setCategoryId(categoryId);
        req.setSpuType("FINISHED");
        Long spuId = spuService.createSpu(req);

        // Switch to tenant 2 and verify SPU is not visible
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(2L);
        SpuPageReqVO pageReq = new SpuPageReqVO();
        pageReq.setPageNo(1);
        pageReq.setPageSize(10);
        PageResult<SpuRespVO> page = spuService.pageSpu(pageReq);
        assertThat(page.getList()).isEmpty();
        assertThat(page.getTotal()).isEqualTo(0L);
    }

    @Test
    void sameSpuCodeInDifferentTenantsShouldNotConflict() {
        // Create SPU with code "SHARED_CODE" in tenant 1
        TenantContextHolder.setTenantId(1L);
        Long cat1 = createCategoryForCurrentTenant("C1", "Cat 1");
        SpuCreateReqVO req1 = new SpuCreateReqVO();
        req1.setSpuCode("SHARED_CODE");
        req1.setSpuName("Tenant 1 Product");
        req1.setCategoryId(cat1);
        req1.setSpuType("FINISHED");
        spuService.createSpu(req1);

        // Create SPU with same code in tenant 2 - should succeed
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(2L);
        Long cat2 = createCategoryForCurrentTenant("C2", "Cat 2");
        SpuCreateReqVO req2 = new SpuCreateReqVO();
        req2.setSpuCode("SHARED_CODE");
        req2.setSpuName("Tenant 2 Product");
        req2.setCategoryId(cat2);
        req2.setSpuType("FINISHED");
        Long spuId2 = spuService.createSpu(req2);

        assertThat(spuId2).isNotNull();

        // Verify each tenant only sees their own
        SpuPageReqVO pageReq = new SpuPageReqVO();
        pageReq.setPageNo(1);
        pageReq.setPageSize(10);

        PageResult<SpuRespVO> tenant2Page = spuService.pageSpu(pageReq);
        assertThat(tenant2Page.getList()).hasSize(1);
        assertThat(tenant2Page.getList().get(0).getSpuName()).isEqualTo("Tenant 2 Product");

        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(1L);
        PageResult<SpuRespVO> tenant1Page = spuService.pageSpu(pageReq);
        assertThat(tenant1Page.getList()).hasSize(1);
        assertThat(tenant1Page.getList().get(0).getSpuName()).isEqualTo("Tenant 1 Product");
    }

    @Test
    void getCategoryTreeShouldOnlyReturnCurrentTenantCategories() {
        // Create categories in tenant 1
        TenantContextHolder.setTenantId(1L);
        createCategoryForCurrentTenant("T1_CAT", "Tenant 1 Category");

        // Create categories in tenant 2
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(2L);
        createCategoryForCurrentTenant("T2_CAT", "Tenant 2 Category");

        // Verify tenant 1 only sees its category
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(1L);
        var cats1 = categoryMapper.selectList();
        assertThat(cats1).hasSize(1);
        assertThat(cats1.get(0).getCategoryCode()).isEqualTo("T1_CAT");

        // Verify tenant 2 only sees its category
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(2L);
        var cats2 = categoryMapper.selectList();
        assertThat(cats2).hasSize(1);
        assertThat(cats2.get(0).getCategoryCode()).isEqualTo("T2_CAT");
    }

    private Long createCategoryForCurrentTenant(String code, String name) {
        Long tenantId = TenantContextHolder.getTenantId();
        ProductCategoryDO category = new ProductCategoryDO();
        category.setTenantId(tenantId);
        category.setCategoryCode(code);
        category.setCategoryName(name);
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
}
