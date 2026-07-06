package com.geihou.module.finance.product.service;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.product.ProductTestConfig;
import com.geihou.module.finance.product.TestSchemaInitializer;
import com.geihou.module.finance.product.controller.admin.vo.SkuCreateReqVO;
import com.geihou.module.finance.product.controller.admin.vo.SkuRespVO;
import com.geihou.module.finance.product.controller.admin.vo.SpuCreateReqVO;
import com.geihou.module.finance.product.dal.dataobject.ProductCategoryDO;
import com.geihou.module.finance.product.dal.mapper.ProductCategoryMapper;
import com.geihou.module.finance.product.framework.ProductBusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SKU service integration test.
 *
 * <p>Tests SKU CRUD, SPU ownership validation, status change.
 * Verifies BigDecimal price type assertion.
 */
@SpringBootTest(
        classes = ProductTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:sku_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class SkuServiceTest {

    @Autowired
    private SkuService skuService;

    @Autowired
    private SpuService spuService;

    @Autowired
    private ProductCategoryMapper categoryMapper;

    @Autowired
    private DataSource dataSource;

    private Long spuId;

    @BeforeEach
    void setUp() throws Exception {
        TestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(1L);

        // Create category
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

        // Create SPU
        SpuCreateReqVO spuReq = new SpuCreateReqVO();
        spuReq.setSpuCode("BURGER");
        spuReq.setSpuName("Burger");
        spuReq.setCategoryId(category.getId());
        spuReq.setSpuType("FINISHED");
        spuId = spuService.createSpu(spuReq);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createSkuShouldReturnId() {
        SkuCreateReqVO reqVO = buildSkuCreateReq("SKU_BEEF", "Beef Burger", "12.00", "10.00");
        Long id = skuService.createSku(reqVO);
        assertThat(id).isNotNull();
    }

    @Test
    void createSkuWithoutSpuShouldFail() {
        SkuCreateReqVO reqVO = buildSkuCreateReq("SKU_NO_SPU", "No SPU", "12.00", "10.00");
        reqVO.setSpuId(null);

        assertThatThrownBy(() -> skuService.createSku(reqVO))
                .isInstanceOf(ProductBusinessException.class);
    }

    @Test
    void createSkuWithNonExistentSpuShouldFail() {
        SkuCreateReqVO reqVO = buildSkuCreateReq("SKU_BAD_SPU", "Bad SPU", "12.00", "10.00");
        reqVO.setSpuId(99999L);

        assertThatThrownBy(() -> skuService.createSku(reqVO))
                .isInstanceOf(ProductBusinessException.class);
    }

    @Test
    void getSkuShouldReturnBigDecimalPrices() {
        SkuCreateReqVO reqVO = buildSkuCreateReq("SKU_CHICKEN", "Chicken Burger", "15.00", "12.50");
        Long id = skuService.createSku(reqVO);

        SkuRespVO resp = skuService.getSku(id);
        assertThat(resp.getListPrice()).isInstanceOf(BigDecimal.class);
        assertThat(resp.getSellingPrice()).isInstanceOf(BigDecimal.class);
        assertThat(resp.getListPrice()).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(resp.getSellingPrice()).isEqualByComparingTo(new BigDecimal("12.50"));
    }

    @Test
    void createSkuWithSellingPriceExceedingListBy110ShouldFail() {
        // list=100, selling=121 (> 110) should fail
        SkuCreateReqVO reqVO = buildSkuCreateReq("SKU_OVERPRICED", "Overpriced", "100.00", "121.00");

        assertThatThrownBy(() -> skuService.createSku(reqVO))
                .isInstanceOf(ProductBusinessException.class);
    }

    @Test
    void createSkuWithSellingPriceAtExactly110PercentShouldPass() {
        // list=100, selling=110 (= 110%) should pass
        SkuCreateReqVO reqVO = buildSkuCreateReq("SKU_BORDERLINE", "Borderline", "100.00", "110.00");
        Long id = skuService.createSku(reqVO);
        assertThat(id).isNotNull();
    }

    @Test
    void changeSkuStatusShouldWriteLog() {
        SkuCreateReqVO reqVO = buildSkuCreateReq("SKU_STATUS", "Status Test", "12.00", "10.00");
        Long id = skuService.createSku(reqVO);

        skuService.changeSkuStatus(id, "ACTIVE", "Ready for sale", 200L);

        SkuRespVO resp = skuService.getSku(id);
        assertThat(resp.getStatus()).isEqualTo("ACTIVE");

        // Verify log
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer logCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM product_availability_log WHERE target_type = 'SKU' AND target_id = ?",
                Integer.class, id);
        assertThat(logCount).isEqualTo(1);
    }

    @Test
    void changeSkuStatusWithoutReasonShouldFail() {
        SkuCreateReqVO reqVO = buildSkuCreateReq("SKU_NOREASON", "No Reason", "12.00", "10.00");
        Long id = skuService.createSku(reqVO);

        assertThatThrownBy(() -> skuService.changeSkuStatus(id, "ACTIVE", "ok", 200L))
                .isInstanceOf(ProductBusinessException.class);
    }

    @Test
    void deprecatedSkuCannotTransitionBack() {
        SkuCreateReqVO reqVO = buildSkuCreateReq("SKU_DEP", "Deprecated Test", "12.00", "10.00");
        Long id = skuService.createSku(reqVO);

        // NEW -> ACTIVE
        skuService.changeSkuStatus(id, "ACTIVE", "Now available", 200L);
        // ACTIVE -> DEPRECATED
        skuService.changeSkuStatus(id, "DEPRECATED", "Permanently retired", 200L);
        // DEPRECATED -> ACTIVE should fail
        assertThatThrownBy(() -> skuService.changeSkuStatus(id, "ACTIVE", "Try to revive", 200L))
                .isInstanceOf(ProductBusinessException.class);
    }

    private SkuCreateReqVO buildSkuCreateReq(String code, String name, String listPrice, String sellingPrice) {
        // Note: listPrice is the original price, sellingPrice is the current price.
        // sellingPrice must not exceed 110% of listPrice (PRD-G1-02 Section 5.2).
        // Typical case: sellingPrice <= listPrice (discount).
        SkuCreateReqVO reqVO = new SkuCreateReqVO();
        reqVO.setSpuId(spuId);
        reqVO.setSkuCode(code);
        reqVO.setSkuName(name);
        reqVO.setListPrice(new BigDecimal(listPrice));
        reqVO.setSellingPrice(new BigDecimal(sellingPrice));
        reqVO.setStockStrategy("TRACK_STOCK");
        return reqVO;
    }
}
