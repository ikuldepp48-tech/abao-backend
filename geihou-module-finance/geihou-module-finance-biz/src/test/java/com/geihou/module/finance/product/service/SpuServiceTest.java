package com.geihou.module.finance.product.service;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.product.ProductTestConfig;
import com.geihou.module.finance.product.TestSchemaInitializer;
import com.geihou.module.finance.product.controller.admin.vo.SpuCreateReqVO;
import com.geihou.module.finance.product.controller.admin.vo.SpuPageReqVO;
import com.geihou.module.finance.product.controller.admin.vo.SpuRespVO;
import com.geihou.module.finance.product.controller.admin.vo.SpuStatusChangeReqVO;
import com.geihou.module.finance.product.controller.admin.vo.SpuUpdateReqVO;
import com.geihou.module.finance.product.dal.dataobject.ProductCategoryDO;
import com.geihou.module.finance.product.dal.mapper.ProductCategoryMapper;
import com.geihou.module.finance.product.framework.ProductBusinessException;
import com.geihou.common.pojo.PageResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SPU service integration test.
 *
 * <p>Tests SPU CRUD, code uniqueness, status change, soft delete.
 * Uses H2 in MySQL mode with real MyBatis-Plus and tenant interceptor.
 */
@SpringBootTest(
        classes = ProductTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:spu_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class SpuServiceTest {

    @Autowired
    private SpuService spuService;

    @Autowired
    private ProductCategoryMapper categoryMapper;

    @Autowired
    private DataSource dataSource;

    private Long categoryId;

    @BeforeEach
    void setUp() throws Exception {
        TestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(1L);

        // Create a category for SPU
        ProductCategoryDO category = new ProductCategoryDO();
        category.setTenantId(1L);
        category.setCategoryCode("DRINK");
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
        categoryId = category.getId();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createSpuShouldReturnId() {
        SpuCreateReqVO reqVO = new SpuCreateReqVO();
        reqVO.setSpuCode("AMERICANO");
        reqVO.setSpuName("Americano");
        reqVO.setCategoryId(categoryId);
        reqVO.setSpuType("FINISHED");

        Long id = spuService.createSpu(reqVO);
        assertThat(id).isNotNull();
    }

    @Test
    void createSpuWithDuplicateCodeShouldFail() {
        SpuCreateReqVO reqVO = new SpuCreateReqVO();
        reqVO.setSpuCode("LATTE");
        reqVO.setSpuName("Latte");
        reqVO.setCategoryId(categoryId);
        reqVO.setSpuType("FINISHED");
        spuService.createSpu(reqVO);

        SpuCreateReqVO dup = new SpuCreateReqVO();
        dup.setSpuCode("LATTE");
        dup.setSpuName("Another Latte");
        dup.setCategoryId(categoryId);
        dup.setSpuType("FINISHED");

        assertThatThrownBy(() -> spuService.createSpu(dup))
                .isInstanceOf(ProductBusinessException.class);
    }

    @Test
    void getSpuShouldReturnCorrectData() {
        SpuCreateReqVO reqVO = new SpuCreateReqVO();
        reqVO.setSpuCode("CAPPUCCINO");
        reqVO.setSpuName("Cappuccino");
        reqVO.setCategoryId(categoryId);
        reqVO.setSpuType("FINISHED");
        Long id = spuService.createSpu(reqVO);

        SpuRespVO resp = spuService.getSpu(id);
        assertThat(resp.getSpuCode()).isEqualTo("CAPPUCCINO");
        assertThat(resp.getSpuName()).isEqualTo("Cappuccino");
        assertThat(resp.getStatus()).isEqualTo("NEW");
        assertThat(resp.getSpuType()).isEqualTo("FINISHED");
    }

    @Test
    void pageSpuShouldReturnResults() {
        SpuCreateReqVO req1 = new SpuCreateReqVO();
        req1.setSpuCode("ESPRESSO");
        req1.setSpuName("Espresso");
        req1.setCategoryId(categoryId);
        req1.setSpuType("FINISHED");
        spuService.createSpu(req1);

        SpuCreateReqVO req2 = new SpuCreateReqVO();
        req2.setSpuCode("MOCHA");
        req2.setSpuName("Mocha");
        req2.setCategoryId(categoryId);
        req2.setSpuType("FINISHED");
        spuService.createSpu(req2);

        SpuPageReqVO pageReq = new SpuPageReqVO();
        pageReq.setPageNo(1);
        pageReq.setPageSize(10);

        PageResult<SpuRespVO> page = spuService.pageSpu(pageReq);
        assertThat(page.getList()).hasSize(2);
        assertThat(page.getTotal()).isEqualTo(2L);
    }

    @Test
    void updateSpuShouldChangeName() {
        SpuCreateReqVO reqVO = new SpuCreateReqVO();
        reqVO.setSpuCode("FLAT_WHITE");
        reqVO.setSpuName("Flat White");
        reqVO.setCategoryId(categoryId);
        reqVO.setSpuType("FINISHED");
        Long id = spuService.createSpu(reqVO);

        SpuUpdateReqVO updateReq = new SpuUpdateReqVO();
        updateReq.setId(id);
        updateReq.setSpuName("Flat White Updated");
        spuService.updateSpu(updateReq);

        SpuRespVO resp = spuService.getSpu(id);
        assertThat(resp.getSpuName()).isEqualTo("Flat White Updated");
    }

    @Test
    void changeSpuStatusShouldWriteLog() {
        SpuCreateReqVO reqVO = new SpuCreateReqVO();
        reqVO.setSpuCode("COLD_BREW");
        reqVO.setSpuName("Cold Brew");
        reqVO.setCategoryId(categoryId);
        reqVO.setSpuType("FINISHED");
        Long id = spuService.createSpu(reqVO);

        SpuStatusChangeReqVO statusReq = new SpuStatusChangeReqVO();
        statusReq.setNewStatus("ACTIVE");
        statusReq.setReason("Ready for sale");
        spuService.changeSpuStatus(id, statusReq, 100L);

        SpuRespVO resp = spuService.getSpu(id);
        assertThat(resp.getStatus()).isEqualTo("ACTIVE");

        // Verify availability log was written
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer logCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM product_availability_log WHERE target_type = 'SPU' AND target_id = ?",
                Integer.class, id);
        assertThat(logCount).isEqualTo(1);
    }

    @Test
    void changeSpuStatusWithoutReasonShouldFail() {
        SpuCreateReqVO reqVO = new SpuCreateReqVO();
        reqVO.setSpuCode("MACCHIATO");
        reqVO.setSpuName("Macchiato");
        reqVO.setCategoryId(categoryId);
        reqVO.setSpuType("FINISHED");
        Long id = spuService.createSpu(reqVO);

        SpuStatusChangeReqVO statusReq = new SpuStatusChangeReqVO();
        statusReq.setNewStatus("ACTIVE");
        statusReq.setReason("ok");  // less than 5 chars

        assertThatThrownBy(() -> spuService.changeSpuStatus(id, statusReq, 100L))
                .isInstanceOf(ProductBusinessException.class);
    }

    @Test
    void deleteSpuShouldSoftDelete() {
        SpuCreateReqVO reqVO = new SpuCreateReqVO();
        reqVO.setSpuCode("RAF");
        reqVO.setSpuName("Raf Coffee");
        reqVO.setCategoryId(categoryId);
        reqVO.setSpuType("FINISHED");
        Long id = spuService.createSpu(reqVO);

        // Must DEPRECATED first before delete
        SpuStatusChangeReqVO statusReq = new SpuStatusChangeReqVO();
        statusReq.setNewStatus("DEPRECATED");
        statusReq.setReason("Permanently retired");
        spuService.changeSpuStatus(id, statusReq, 100L);

        spuService.deleteSpu(id);

        // Should not be findable after soft delete
        assertThatThrownBy(() -> spuService.getSpu(id))
                .isInstanceOf(ProductBusinessException.class);
    }

    @Test
    void createSpuWithNormalTypeShouldFail() {
        // G1-02C ruling: NORMAL is not allowed, must use FINISHED
        SpuCreateReqVO reqVO = new SpuCreateReqVO();
        reqVO.setSpuCode("BAD_TYPE");
        reqVO.setSpuName("Bad Type");
        reqVO.setCategoryId(categoryId);
        reqVO.setSpuType("NORMAL");  // Should fail

        assertThatThrownBy(() -> spuService.createSpu(reqVO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
