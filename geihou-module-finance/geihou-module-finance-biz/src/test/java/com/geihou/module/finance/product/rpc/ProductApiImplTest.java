package com.geihou.module.finance.product.rpc;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.api.product.dto.*;
import com.geihou.module.finance.product.ProductTestConfig;
import com.geihou.module.finance.product.TestSchemaInitializer;
import com.geihou.module.finance.product.controller.admin.vo.*;
import com.geihou.module.finance.product.dal.dataobject.ProductCategoryDO;
import com.geihou.module.finance.product.dal.mapper.ProductCategoryMapper;
import com.geihou.module.finance.product.service.AddonGroupService;
import com.geihou.module.finance.product.service.ComboService;
import com.geihou.module.finance.product.service.PriceService;
import com.geihou.module.finance.product.service.SkuService;
import com.geihou.module.finance.product.service.SpuAddonGroupService;
import com.geihou.module.finance.product.service.SpuService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ProductApiImpl integration test.
 *
 * <p>Tests all 6 ProductApi methods:
 * getSpu, getSku, batchGetSkus, checkAvailability, expandCombo, getAddonGroupsBySpu.
 *
 * <p>Verifies tenant context enforcement, BigDecimal price types,
 * ACTIVE-only filtering for combos and addon options.
 */
@SpringBootTest(
        classes = ProductTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:api_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class ProductApiImplTest {

    @Autowired
    private ProductApiImpl productApi;

    @Autowired
    private SpuService spuService;

    @Autowired
    private SkuService skuService;

    @Autowired
    private PriceService priceService;

    @Autowired
    private AddonGroupService addonGroupService;

    @Autowired
    private ComboService comboService;

    @Autowired
    private SpuAddonGroupService spuAddonGroupService;

    @Autowired
    private ProductCategoryMapper categoryMapper;

    @Autowired
    private DataSource dataSource;

    private Long spuId;
    private Long skuId;

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
        spuReq.setSpuShortName("Coffee");
        spuReq.setCategoryId(category.getId());
        spuReq.setSpuType("FINISHED");
        spuReq.setIsRecommended(true);
        spuId = spuService.createSpu(spuReq);

        // Activate SPU for availability tests
        SpuStatusChangeReqVO spuStatusReq = new SpuStatusChangeReqVO();
        spuStatusReq.setNewStatus("ACTIVE");
        spuStatusReq.setReason("Initial SPU activation");
        spuService.changeSpuStatus(spuId, spuStatusReq, 100L);

        // Create SKU
        SkuCreateReqVO skuReq = new SkuCreateReqVO();
        skuReq.setSpuId(spuId);
        skuReq.setSkuCode("SKU_LARGE");
        skuReq.setSkuName("Large Coffee");
        skuReq.setListPrice(new BigDecimal("20.00"));
        skuReq.setSellingPrice(new BigDecimal("18.00"));
        skuReq.setStockStrategy("TRACK_STOCK");
        skuReq.setPerOrderLimit(10);
        skuId = skuService.createSku(skuReq);

        // Activate SKU for availability tests
        skuService.changeSkuStatus(skuId, "ACTIVE", "Initial activation", 100L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void getSpuReturnsCorrectDto() {
        SpuRespDTO dto = productApi.getSpu(spuId);

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(spuId);
        assertThat(dto.getTenantId()).isEqualTo(1L);
        assertThat(dto.getSpuCode()).isEqualTo("COFFEE");
        assertThat(dto.getSpuName()).isEqualTo("Coffee");
        assertThat(dto.getSpuType()).isEqualTo("FINISHED");
        assertThat(dto.getStatus()).isEqualTo("ACTIVE");
        assertThat(dto.getIsRecommended()).isTrue();
    }

    @Test
    void getSpuReturnsNullForNotFound() {
        SpuRespDTO dto = productApi.getSpu(99999L);
        assertThat(dto).isNull();
    }

    @Test
    void getSkuReturnsCorrectDtoWithBigDecimalPrices() {
        SkuRespDTO dto = productApi.getSku(skuId);

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(skuId);
        assertThat(dto.getTenantId()).isEqualTo(1L);
        assertThat(dto.getSpuId()).isEqualTo(spuId);
        assertThat(dto.getListPrice()).isInstanceOf(BigDecimal.class);
        assertThat(dto.getSellingPrice()).isInstanceOf(BigDecimal.class);
        assertThat(dto.getListPrice()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(dto.getSellingPrice()).isEqualByComparingTo(new BigDecimal("18.00"));
        assertThat(dto.getStatus()).isEqualTo("ACTIVE");
        assertThat(dto.getStockStrategy()).isEqualTo("TRACK_STOCK");
        assertThat(dto.getPerOrderLimit()).isEqualTo(10);
    }

    @Test
    void getSkuReturnsNullForNotFound() {
        SkuRespDTO dto = productApi.getSku(99999L);
        assertThat(dto).isNull();
    }

    @Test
    void batchGetSkusReturnsMap() {
        // Create second SKU
        SkuCreateReqVO skuReq2 = new SkuCreateReqVO();
        skuReq2.setSpuId(spuId);
        skuReq2.setSkuCode("SKU_SMALL");
        skuReq2.setSkuName("Small Coffee");
        skuReq2.setListPrice(new BigDecimal("15.00"));
        skuReq2.setSellingPrice(new BigDecimal("13.00"));
        skuReq2.setStockStrategy("TRACK_STOCK");
        Long skuId2 = skuService.createSku(skuReq2);

        Map<Long, SkuRespDTO> result = productApi.batchGetSkus(List.of(skuId, skuId2, 99999L));

        assertThat(result).hasSize(2);
        assertThat(result.keySet()).contains(skuId, skuId2);
        assertThat(result.get(skuId).getSkuName()).isEqualTo("Large Coffee");
        assertThat(result.get(skuId2).getSkuName()).isEqualTo("Small Coffee");
    }

    @Test
    void batchGetSkusEmptyListReturnsEmptyMap() {
        Map<Long, SkuRespDTO> result = productApi.batchGetSkus(List.of());
        assertThat(result).isEmpty();
    }

    @Test
    void checkAvailabilityActiveSkuReturnsTrue() {
        SkuAvailabilityRespDTO resp = productApi.checkAvailability(skuId, 5);

        assertThat(resp.getSkuId()).isEqualTo(skuId);
        assertThat(resp.getAvailable()).isTrue();
        assertThat(resp.getRequestedQuantity()).isEqualTo(5);
    }

    @Test
    void checkAvailabilitySoldOutReturnsFalse() {
        skuService.changeSkuStatus(skuId, "SOLD_OUT", "Temporarily sold out", 100L);

        SkuAvailabilityRespDTO resp = productApi.checkAvailability(skuId, 1);

        assertThat(resp.getAvailable()).isFalse();
        assertThat(resp.getReason()).isEqualTo("SOLD_OUT");
    }

    @Test
    void checkAvailabilityPausedReturnsFalse() {
        skuService.changeSkuStatus(skuId, "PAUSED", "Temporarily paused sale", 100L);

        SkuAvailabilityRespDTO resp = productApi.checkAvailability(skuId, 1);

        assertThat(resp.getAvailable()).isFalse();
        assertThat(resp.getReason()).isEqualTo("PAUSED");
    }

    @Test
    void checkAvailabilityDeprecatedReturnsFalse() {
        skuService.changeSkuStatus(skuId, "DEPRECATED", "Permanently discontinued", 100L);

        SkuAvailabilityRespDTO resp = productApi.checkAvailability(skuId, 1);

        assertThat(resp.getAvailable()).isFalse();
        assertThat(resp.getReason()).isEqualTo("DEPRECATED");
    }

    @Test
    void checkAvailabilityExceedsPerOrderLimitReturnsFalse() {
        SkuAvailabilityRespDTO resp = productApi.checkAvailability(skuId, 11);

        assertThat(resp.getAvailable()).isFalse();
        assertThat(resp.getReason()).isEqualTo("EXCEEDS_PER_ORDER_LIMIT");
        assertThat(resp.getMaxAllowedQuantity()).isEqualTo(10);
    }

    @Test
    void checkAvailabilityWithinPerOrderLimitReturnsTrue() {
        SkuAvailabilityRespDTO resp = productApi.checkAvailability(skuId, 10);

        assertThat(resp.getAvailable()).isTrue();
    }

    @Test
    void checkAvailabilitySkuNotFoundReturnsFalse() {
        SkuAvailabilityRespDTO resp = productApi.checkAvailability(99999L, 1);

        assertThat(resp.getAvailable()).isFalse();
        assertThat(resp.getReason()).isEqualTo("SKU_NOT_FOUND");
    }

    @Test
    void expandComboActiveReturnsItems() {
        // Create combo SKU
        SkuCreateReqVO comboSkuReq = new SkuCreateReqVO();
        comboSkuReq.setSpuId(spuId);
        comboSkuReq.setSkuCode("SKU_COMBO");
        comboSkuReq.setSkuName("Combo Coffee");
        comboSkuReq.setListPrice(new BigDecimal("30.00"));
        comboSkuReq.setSellingPrice(new BigDecimal("25.00"));
        comboSkuReq.setStockStrategy("UNLIMITED");
        Long comboSkuId = skuService.createSku(comboSkuReq);
        skuService.changeSkuStatus(comboSkuId, "ACTIVE", "Activate combo sku", 100L);

        // Create combo
        ComboCreateReqVO comboReq = new ComboCreateReqVO();
        comboReq.setComboSkuId(comboSkuId);
        comboReq.setComboName("Coffee Combo");
        comboReq.setComboPrice(new BigDecimal("35.00"));
        ComboItemCreateReqVO itemReq = new ComboItemCreateReqVO();
        itemReq.setItemSkuId(skuId);
        itemReq.setQuantity(2);
        itemReq.setIsOptional(false);
        comboReq.setItems(List.of(itemReq));
        Long comboId = comboService.createCombo(comboReq);

        // Expand combo
        List<ComboItemRespDTO> items = productApi.expandCombo(comboSkuId);

        assertThat(items).hasSize(1);
        ComboItemRespDTO item = items.get(0);
        assertThat(item.getComboSkuId()).isEqualTo(comboSkuId);
        assertThat(item.getItemSkuId()).isEqualTo(skuId);
        assertThat(item.getQuantity()).isEqualTo(2);
        assertThat(item.getItemSkuName()).isEqualTo("Large Coffee");
        assertThat(item.getItemSkuSellingPrice()).isEqualByComparingTo(new BigDecimal("18.00"));
    }

    @Test
    void expandComboPausedReturnsEmpty() {
        // Create combo SKU
        SkuCreateReqVO comboSkuReq = new SkuCreateReqVO();
        comboSkuReq.setSpuId(spuId);
        comboSkuReq.setSkuCode("SKU_COMBO2");
        comboSkuReq.setSkuName("Combo Coffee 2");
        comboSkuReq.setListPrice(new BigDecimal("30.00"));
        comboSkuReq.setSellingPrice(new BigDecimal("25.00"));
        comboSkuReq.setStockStrategy("UNLIMITED");
        Long comboSkuId = skuService.createSku(comboSkuReq);
        skuService.changeSkuStatus(comboSkuId, "ACTIVE", "Activate combo sku", 100L);

        // Create combo
        ComboCreateReqVO comboReq = new ComboCreateReqVO();
        comboReq.setComboSkuId(comboSkuId);
        comboReq.setComboName("Coffee Combo 2");
        comboReq.setComboPrice(new BigDecimal("25.00"));
        ComboItemCreateReqVO itemReq = new ComboItemCreateReqVO();
        itemReq.setItemSkuId(skuId);
        itemReq.setQuantity(1);
        itemReq.setIsOptional(false);
        comboReq.setItems(List.of(itemReq));
        Long comboId = comboService.createCombo(comboReq);

        // Pause combo
        ComboStatusChangeReqVO statusReq = new ComboStatusChangeReqVO();
        statusReq.setNewStatus("PAUSED");
        statusReq.setReason("Temporary pause for testing");
        comboService.changeComboStatus(comboId, statusReq, 100L);

        // Expand should return empty
        List<ComboItemRespDTO> items = productApi.expandCombo(comboSkuId);
        assertThat(items).isEmpty();
    }

    @Test
    void expandComboNotFoundReturnsEmpty() {
        List<ComboItemRespDTO> items = productApi.expandCombo(99999L);
        assertThat(items).isEmpty();
    }

    @Test
    void getAddonGroupsBySpuReturnsMappedGroups() {
        // Create addon group
        AddonGroupCreateReqVO groupReq = new AddonGroupCreateReqVO();
        groupReq.setGroupCode("TOPPINGS");
        groupReq.setGroupName("Toppings");
        groupReq.setSelectMin(0);
        groupReq.setSelectMax(3);
        groupReq.setIsRequired(false);
        groupReq.setSortOrder(0);
        Long groupId = addonGroupService.createAddonGroup(groupReq);

        // Create addon option (needs a SKU)
        SkuCreateReqVO optionSkuReq = new SkuCreateReqVO();
        optionSkuReq.setSpuId(spuId);
        optionSkuReq.setSkuCode("SKU_CREAM");
        optionSkuReq.setSkuName("Extra Cream");
        optionSkuReq.setListPrice(new BigDecimal("2.00"));
        optionSkuReq.setSellingPrice(new BigDecimal("2.00"));
        optionSkuReq.setStockStrategy("UNLIMITED");
        Long optionSkuId = skuService.createSku(optionSkuReq);
        skuService.changeSkuStatus(optionSkuId, "ACTIVE", "Activate option sku", 100L);

        AddonOptionCreateReqVO optionReq = new AddonOptionCreateReqVO();
        optionReq.setOptionSkuId(optionSkuId);
        optionReq.setOptionName("Add Cream");
        optionReq.setExtraPrice(new BigDecimal("2.00"));
        optionReq.setSortOrder(0);
        addonGroupService.createAddonOption(groupId, optionReq);

        // Map addon group to SPU
        spuAddonGroupService.assignAddonGroup(spuId, groupId, 0);

        // Query via API
        List<AddonGroupRespDTO> groups = productApi.getAddonGroupsBySpu(spuId);

        assertThat(groups).hasSize(1);
        AddonGroupRespDTO group = groups.get(0);
        assertThat(group.getGroupId()).isEqualTo(groupId);
        assertThat(group.getGroupCode()).isEqualTo("TOPPINGS");
        assertThat(group.getGroupName()).isEqualTo("Toppings");
        assertThat(group.getSelectMin()).isEqualTo(0);
        assertThat(group.getSelectMax()).isEqualTo(3);

        // Verify options
        assertThat(group.getOptions()).hasSize(1);
        AddonOptionRespDTO option = group.getOptions().get(0);
        assertThat(option.getOptionName()).isEqualTo("Add Cream");
        assertThat(option.getExtraPrice()).isInstanceOf(BigDecimal.class);
        assertThat(option.getExtraPrice()).isEqualByComparingTo(new BigDecimal("2.00"));
        assertThat(option.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void getAddonGroupsBySpuReturnsEmptyWhenNoMapping() {
        List<AddonGroupRespDTO> groups = productApi.getAddonGroupsBySpu(spuId);
        assertThat(groups).isEmpty();
    }

    @Test
    void getAddonGroupsBySpuFiltersNonActiveOptions() {
        // Create addon group
        AddonGroupCreateReqVO groupReq = new AddonGroupCreateReqVO();
        groupReq.setGroupCode("EXTRAS");
        groupReq.setGroupName("Extras");
        groupReq.setSelectMin(0);
        groupReq.setSelectMax(5);
        groupReq.setIsRequired(false);
        groupReq.setSortOrder(0);
        Long groupId = addonGroupService.createAddonGroup(groupReq);

        // Create two option SKUs
        SkuCreateReqVO optSku1 = new SkuCreateReqVO();
        optSku1.setSpuId(spuId);
        optSku1.setSkuCode("SKU_OPT1");
        optSku1.setSkuName("Option 1");
        optSku1.setListPrice(new BigDecimal("1.00"));
        optSku1.setSellingPrice(new BigDecimal("1.00"));
        optSku1.setStockStrategy("UNLIMITED");
        Long optSkuId1 = skuService.createSku(optSku1);
        skuService.changeSkuStatus(optSkuId1, "ACTIVE", "Activate option 1", 100L);

        SkuCreateReqVO optSku2 = new SkuCreateReqVO();
        optSku2.setSpuId(spuId);
        optSku2.setSkuCode("SKU_OPT2");
        optSku2.setSkuName("Option 2");
        optSku2.setListPrice(new BigDecimal("1.00"));
        optSku2.setSellingPrice(new BigDecimal("1.00"));
        optSku2.setStockStrategy("UNLIMITED");
        Long optSkuId2 = skuService.createSku(optSku2);
        skuService.changeSkuStatus(optSkuId2, "ACTIVE", "Activate option 2", 100L);

        // Create two options — one ACTIVE, one SOLD_OUT
        AddonOptionCreateReqVO opt1 = new AddonOptionCreateReqVO();
        opt1.setOptionSkuId(optSkuId1);
        opt1.setOptionName("Active Option");
        opt1.setExtraPrice(new BigDecimal("1.00"));
        opt1.setSortOrder(0);
        addonGroupService.createAddonOption(groupId, opt1);

        AddonOptionCreateReqVO opt2 = new AddonOptionCreateReqVO();
        opt2.setOptionSkuId(optSkuId2);
        opt2.setOptionName("Sold Out Option");
        opt2.setExtraPrice(new BigDecimal("1.00"));
        opt2.setSortOrder(1);
        Long opt2Id = addonGroupService.createAddonOption(groupId, opt2);

        // Set opt2 to SOLD_OUT
        AddonOptionStatusChangeReqVO statusReq = new AddonOptionStatusChangeReqVO();
        statusReq.setNewStatus("SOLD_OUT");
        statusReq.setReason("Temporarily out of stock");
        addonGroupService.changeAddonOptionStatus(groupId, opt2Id, statusReq);

        // Map to SPU
        spuAddonGroupService.assignAddonGroup(spuId, groupId, 0);

        // Query — should only return ACTIVE option
        List<AddonGroupRespDTO> groups = productApi.getAddonGroupsBySpu(spuId);
        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).getOptions()).hasSize(1);
        assertThat(groups.get(0).getOptions().get(0).getOptionName()).isEqualTo("Active Option");
    }

    @Test
    void crossTenantIsolationPreventsDataLeak() {
        // Tenant 1 data already created in setUp

        // Switch to tenant 2
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(2L);

        // getSpu should return null (tenant 2 can't see tenant 1's SPU)
        SpuRespDTO spu = productApi.getSpu(spuId);
        assertThat(spu).isNull();

        // getSku should return null
        SkuRespDTO sku = productApi.getSku(skuId);
        assertThat(sku).isNull();

        // checkAvailability should return not found
        SkuAvailabilityRespDTO avail = productApi.checkAvailability(skuId, 1);
        assertThat(avail.getAvailable()).isFalse();
        assertThat(avail.getReason()).isEqualTo("SKU_NOT_FOUND");
    }
}
