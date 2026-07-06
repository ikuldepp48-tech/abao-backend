package com.geihou.module.finance.product.controller.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.product.ProductTestConfig;
import com.geihou.module.finance.product.TestSchemaInitializer;
import com.geihou.module.finance.product.dal.dataobject.*;
import com.geihou.module.finance.product.dal.mapper.*;
import com.geihou.module.finance.product.service.MenuAssemblyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CustomerMenuController integration test (G1-02H).
 *
 * <p>Tests the HTTP endpoints:
 * <ul>
 *   <li>GET /app-api/customer/menu?storeId= — normal 200, missing storeId 400</li>
 *   <li>GET /app-api/customer/menu/spu/{id} — normal 200, SPU not found error code, SPU not active error code</li>
 * </ul>
 *
 * <p>Uses standalone MockMvc setup with Jackson message converter to avoid
 * needing full spring-boot-starter-web auto-configuration.
 */
@SpringBootTest(
        classes = ProductTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:menu_ctrl_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class CustomerMenuControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private MenuAssemblyService menuAssemblyService;

    @Autowired
    private ProductCategoryMapper categoryMapper;
    @Autowired
    private ProductSpuMapper spuMapper;
    @Autowired
    private ProductSkuMapper skuMapper;
    @Autowired
    private DataSource dataSource;

    private Long categoryId;
    private Long activeSpuId;

    @BeforeEach
    void setUp() throws Exception {
        TestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(1L);

        // Create test data
        categoryId = createCategory("DRINKS", "Drinks", null, 1, 0, "ACTIVE");
        activeSpuId = createSpu("LATTE", "Latte", categoryId, "FINISHED", "ACTIVE", 0);
        createSku(activeSpuId, "LATTE_M", "Latte Medium", "ACTIVE",
                new BigDecimal("28.00"), new BigDecimal("25.00"));

        // Build standalone MockMvc with Jackson converter
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter();
        jsonConverter.setObjectMapper(objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(new CustomerMenuController(menuAssemblyService))
                .setMessageConverters(jsonConverter)
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // ===== GET /app-api/customer/menu?storeId= =====

    @Test
    void getMenuShouldReturn200WithValidStoreId() throws Exception {
        mockMvc.perform(get("/app-api/customer/menu").param("storeId", "100").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.storeId").value(100))
                .andExpect(jsonPath("$.data.categories").isArray())
                .andExpect(jsonPath("$.data.menuSnapshotTime").isNotEmpty());
    }

    @Test
    void getMenuShouldReturn400WhenStoreIdMissing() throws Exception {
        mockMvc.perform(get("/app-api/customer/menu").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMenuShouldReturnMenuWithCategoryAndSpu() throws Exception {
        mockMvc.perform(get("/app-api/customer/menu").param("storeId", "100").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categories[0].categoryName").value("Drinks"))
                .andExpect(jsonPath("$.data.categories[0].spus[0].spuName").value("Latte"))
                .andExpect(jsonPath("$.data.categories[0].spus[0].spuType").value("FINISHED"))
                .andExpect(jsonPath("$.data.categories[0].spus[0].skus[0].skuName").value("Latte Medium"))
                .andExpect(jsonPath("$.data.categories[0].spus[0].skus[0].status").value("ACTIVE"));
    }

    @Test
    void getMenuShouldReturnPriceFieldsAsNumbers() throws Exception {
        mockMvc.perform(get("/app-api/customer/menu").param("storeId", "100").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categories[0].spus[0].skus[0].listPrice").value(28.00))
                .andExpect(jsonPath("$.data.categories[0].spus[0].skus[0].sellingPrice").value(25.00))
                .andExpect(jsonPath("$.data.categories[0].spus[0].minSellingPrice").value(25.00))
                .andExpect(jsonPath("$.data.categories[0].spus[0].maxSellingPrice").value(25.00));
    }

    // ===== GET /app-api/customer/menu/spu/{id} =====

    @Test
    void getSpuDetailShouldReturn200WithValidId() throws Exception {
        mockMvc.perform(get("/app-api/customer/menu/spu/{id}", activeSpuId).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(activeSpuId.intValue()))
                .andExpect(jsonPath("$.data.spuName").value("Latte"))
                .andExpect(jsonPath("$.data.spuType").value("FINISHED"))
                .andExpect(jsonPath("$.data.categoryId").value(categoryId.intValue()))
                .andExpect(jsonPath("$.data.categoryName").value("Drinks"))
                .andExpect(jsonPath("$.data.skus[0].skuName").value("Latte Medium"))
                .andExpect(jsonPath("$.data.skus[0].status").value("ACTIVE"));
    }

    @Test
    void getSpuDetailShouldReturnErrorWhenNotFound() throws Exception {
        mockMvc.perform(get("/app-api/customer/menu/spu/{id}", 99999).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002109))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void getSpuDetailShouldReturnErrorWhenNotActive() throws Exception {
        Long pausedSpuId = createSpu("PAUSED_SPU", "Paused", categoryId, "FINISHED", "PAUSED", 0);

        mockMvc.perform(get("/app-api/customer/menu/spu/{id}", pausedSpuId).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002110))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void getSpuDetailShouldReturnErrorWhenRawMaterial() throws Exception {
        Long rawSpuId = createSpu("RAW_SPU", "Raw Material", categoryId, "RAW_MATERIAL", "ACTIVE", 0);

        mockMvc.perform(get("/app-api/customer/menu/spu/{id}", rawSpuId).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002111))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void getSpuDetailShouldReturnErrorWhenSemiFinished() throws Exception {
        Long semiSpuId = createSpu("SEMI_SPU", "Semi Finished", categoryId, "SEMI_FINISHED", "ACTIVE", 0);

        mockMvc.perform(get("/app-api/customer/menu/spu/{id}", semiSpuId).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002111))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void getSpuDetailShouldReturnEmptyAddonGroupsWhenNoneMapped() throws Exception {
        mockMvc.perform(get("/app-api/customer/menu/spu/{id}", activeSpuId).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.addonGroups").isArray())
                .andExpect(jsonPath("$.data.addonGroups").isEmpty());
    }

    @Test
    void getSpuDetailShouldReturnEmptyComboItemsWhenNotCombo() throws Exception {
        mockMvc.perform(get("/app-api/customer/menu/spu/{id}", activeSpuId).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.comboItems").isArray())
                .andExpect(jsonPath("$.data.comboItems").isEmpty());
    }

    // ===== Helper methods =====

    private Long createCategory(String code, String name, Long parentId, int level, int sortOrder, String status) {
        ProductCategoryDO cat = new ProductCategoryDO();
        cat.setTenantId(1L);
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
        spu.setTenantId(1L);
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
        sku.setTenantId(1L);
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
}
