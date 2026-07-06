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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Combo service integration test.
 *
 * <p>AC-6: combo_price < sum(selling_price * quantity) * 0.9 → COMBO_PRICE_UNREASONABLE.
 * AC-8: item_sku_id must reference existing ACTIVE or SOLD_OUT SKU in same tenant.
 * AC-11: combo item uniqueness (combo_id, item_sku_id, deleted).
 * AC-3: soft delete.
 */
@SpringBootTest(
        classes = ProductTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:combo_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class ComboServiceTest {

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

    private Long comboSkuId;
    private Long itemSkuId1;
    private Long itemSkuId2;

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

        // Create SPU for combo SKU
        Long comboSpuId = createSpu("COMBO_SPU", "Combo SPU");
        comboSkuId = createActiveSku(comboSpuId, "COMBO_SKU", "Combo SKU", "50.00", "45.00");

        // Create SPU + SKUs for combo items
        Long itemSpuId1 = createSpu("ITEM_SPU1", "Item SPU 1");
        itemSkuId1 = createActiveSku(itemSpuId1, "ITEM_SKU1", "Item SKU 1", "20.00", "18.00");

        Long itemSpuId2 = createSpu("ITEM_SPU2", "Item SPU 2");
        itemSkuId2 = createActiveSku(itemSpuId2, "ITEM_SKU2", "Item SKU 2", "15.00", "12.00");
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createComboShouldReturnId() {
        Long comboId = createComboWithItems("Combo 1", new BigDecimal("30.00"), List.of(
                buildItemReq(itemSkuId1, 1),
                buildItemReq(itemSkuId2, 1)
        ));
        assertThat(comboId).isNotNull();
    }

    @Test
    void createComboWithUnderpricedShouldFail() {
        // AC-6: sum = 18*1 + 12*1 = 30, threshold = 30 * 0.9 = 27
        // combo_price = 26 < 27 → reject
        assertThatThrownBy(() -> createComboWithItems("Underpriced", new BigDecimal("26.00"), List.of(
                buildItemReq(itemSkuId1, 1),
                buildItemReq(itemSkuId2, 1)
        ))).isInstanceOf(ProductBusinessException.class);
    }

    @Test
    void createComboWithPriceExactlyAt90PercentShouldPass() {
        // sum = 30, threshold = 27, combo_price = 27 → pass (>= threshold)
        Long comboId = createComboWithItems("Borderline", new BigDecimal("27.00"), List.of(
                buildItemReq(itemSkuId1, 1),
                buildItemReq(itemSkuId2, 1)
        ));
        assertThat(comboId).isNotNull();
    }

    @Test
    void createComboWithNonExistentSkuShouldFail() {
        // AC-8: item_sku_id must reference existing SKU
        ComboCreateReqVO req = buildComboReq("Bad SKU", new BigDecimal("30.00"));
        req.setItems(List.of(buildItemReq(99999L, 1)));
        assertThatThrownBy(() -> comboService.createCombo(req))
                .isInstanceOf(ProductBusinessException.class);
    }

    @Test
    void createComboWithNonExistentComboSkuShouldFail() {
        ComboCreateReqVO req = buildComboReq("Bad combo SKU", new BigDecimal("30.00"));
        req.setComboSkuId(99999L);
        assertThatThrownBy(() -> comboService.createCombo(req))
                .isInstanceOf(ProductBusinessException.class);
    }

    @Test
    void createComboWithoutItemsShouldPass() {
        // Combo can be created without items; items added later
        Long comboId = createComboWithItems("No items", new BigDecimal("30.00"), null);
        assertThat(comboId).isNotNull();
    }

    @Test
    void addDuplicateComboItemShouldFail() {
        // AC-11: same item_sku_id in same combo → COMBO_ITEM_DUPLICATE
        Long comboId = createComboWithItems("Dup test", new BigDecimal("30.00"), List.of(
                buildItemReq(itemSkuId1, 1)
        ));

        assertThatThrownBy(() -> comboService.addComboItem(comboId, buildItemReq(itemSkuId1, 1)))
                .isInstanceOf(ProductBusinessException.class);
    }

    @Test
    void getComboShouldIncludeItems() {
        // sum = 18*1 + 12*2 = 42, threshold = 42*0.9 = 37.8, combo_price = 40.00 >= 37.8 → pass
        Long comboId = createComboWithItems("With items", new BigDecimal("40.00"), List.of(
                buildItemReq(itemSkuId1, 1),
                buildItemReq(itemSkuId2, 2)
        ));

        ComboRespVO resp = comboService.getCombo(comboId);
        assertThat(resp.getItems()).hasSize(2);
        assertThat(resp.getComboPrice()).isEqualByComparingTo(new BigDecimal("40.00"));
    }

    @Test
    void changeComboStatusShouldWriteLog() {
        Long comboId = createComboWithItems("Status test", new BigDecimal("30.00"), List.of(
                buildItemReq(itemSkuId1, 1)
        ));

        ComboStatusChangeReqVO statusReq = new ComboStatusChangeReqVO();
        statusReq.setNewStatus("PAUSED");
        statusReq.setReason("Manual pause");
        comboService.changeComboStatus(comboId, statusReq, 200L);

        ComboRespVO resp = comboService.getCombo(comboId);
        assertThat(resp.getStatus()).isEqualTo("PAUSED");
    }

    @Test
    void changeComboStatusWithoutReasonShouldFail() {
        Long comboId = createComboWithItems("No reason", new BigDecimal("30.00"), List.of(
                buildItemReq(itemSkuId1, 1)
        ));

        ComboStatusChangeReqVO statusReq = new ComboStatusChangeReqVO();
        statusReq.setNewStatus("DEPRECATED");
        statusReq.setReason("ok");

        assertThatThrownBy(() -> comboService.changeComboStatus(comboId, statusReq, 200L))
                .isInstanceOf(ProductBusinessException.class);
    }

    @Test
    void deleteComboShouldSoftDelete() {
        Long comboId = createComboWithItems("Delete me", new BigDecimal("30.00"), List.of(
                buildItemReq(itemSkuId1, 1)
        ));

        comboService.deleteCombo(comboId);

        assertThatThrownBy(() -> comboService.getCombo(comboId))
                .isInstanceOf(ProductBusinessException.class);
    }

    @Test
    void comboPriceShouldBeBigDecimal() {
        Long comboId = createComboWithItems("BD test", new BigDecimal("33.50"), List.of(
                buildItemReq(itemSkuId1, 1)
        ));

        ComboRespVO resp = comboService.getCombo(comboId);
        assertThat(resp.getComboPrice()).isInstanceOf(BigDecimal.class);
    }

    @Test
    void deprecatedComboCannotTransitionBack() {
        Long comboId = createComboWithItems("Dep test", new BigDecimal("30.00"), List.of(
                buildItemReq(itemSkuId1, 1)
        ));

        // ACTIVE → DEPRECATED
        ComboStatusChangeReqVO depReq = new ComboStatusChangeReqVO();
        depReq.setNewStatus("DEPRECATED");
        depReq.setReason("Permanently retired");
        comboService.changeComboStatus(comboId, depReq, 200L);

        // DEPRECATED → ACTIVE should fail
        ComboStatusChangeReqVO reviveReq = new ComboStatusChangeReqVO();
        reviveReq.setNewStatus("ACTIVE");
        reviveReq.setReason("Try to revive");
        assertThatThrownBy(() -> comboService.changeComboStatus(comboId, reviveReq, 200L))
                .isInstanceOf(ProductBusinessException.class);
    }

    // ===== Helpers =====

    private Long createSpu(String code, String name) {
        SpuCreateReqVO spuReq = new SpuCreateReqVO();
        spuReq.setSpuCode(code);
        spuReq.setSpuName(name);
        spuReq.setCategoryId(categoryMapper.selectList().get(0).getId());
        spuReq.setSpuType("FINISHED");
        return spuService.createSpu(spuReq);
    }

    private Long createActiveSku(Long spuId, String code, String name, String listPrice, String sellingPrice) {
        SkuCreateReqVO skuReq = new SkuCreateReqVO();
        skuReq.setSpuId(spuId);
        skuReq.setSkuCode(code);
        skuReq.setSkuName(name);
        skuReq.setListPrice(new BigDecimal(listPrice));
        skuReq.setSellingPrice(new BigDecimal(sellingPrice));
        skuReq.setStockStrategy("TRACK_STOCK");
        Long skuId = skuService.createSku(skuReq);
        skuService.changeSkuStatus(skuId, "ACTIVE", "Ready for sale", 200L);
        return skuId;
    }

    private Long createComboWithItems(String name, BigDecimal price, List<ComboItemCreateReqVO> items) {
        ComboCreateReqVO req = buildComboReq(name, price);
        req.setItems(items);
        return comboService.createCombo(req);
    }

    private ComboCreateReqVO buildComboReq(String name, BigDecimal price) {
        ComboCreateReqVO req = new ComboCreateReqVO();
        req.setComboSkuId(comboSkuId);
        req.setComboName(name);
        req.setComboPrice(price);
        return req;
    }

    private ComboItemCreateReqVO buildItemReq(Long skuId, int quantity) {
        ComboItemCreateReqVO req = new ComboItemCreateReqVO();
        req.setItemSkuId(skuId);
        req.setQuantity(quantity);
        return req;
    }
}
