package com.geihou.module.supplychain.stock.check;

import com.geihou.module.supplychain.api.bom.explode.dto.BomExplosionRespDTO;
import com.geihou.module.supplychain.api.stock.dto.SkuQuantityDTO;
import com.geihou.module.supplychain.api.stock.dto.StockCheckItemResultDTO;
import com.geihou.module.supplychain.api.stock.dto.StockCheckRespDTO;
import com.geihou.module.supplychain.bom.dal.dataobject.ProductMasterDO;
import com.geihou.module.supplychain.bom.dal.mapper.ProductMasterMapper;
import com.geihou.module.supplychain.bom.explode.BomExplosionService;
import com.geihou.module.supplychain.stock.dal.dataobject.StockItemDO;
import com.geihou.module.supplychain.stock.dal.mapper.StockBalanceMapper;
import com.geihou.module.supplychain.stock.dal.mapper.StockItemMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Focused unit tests for {@link StockCheckServiceImpl} (G2-02C).
 *
 * <p>Fast, pure-unit tests using Mockito mocks — no Spring context, no DB.
 * Covers: empty list, unmapped component, insufficient stock, sufficient stock,
 * null-items, unresolved product, and aggregation across multiple input SKUs.
 */
@ExtendWith(MockitoExtension.class)
class StockCheckServiceTest {

    private static final Long TENANT_ID = 1L;

    @Mock
    private ProductMasterMapper productMasterMapper;
    @Mock
    private BomExplosionService bomExplosionService;
    @Mock
    private StockItemMapper stockItemMapper;
    @Mock
    private StockBalanceMapper stockBalanceMapper;

    @InjectMocks
    private StockCheckServiceImpl stockCheckService;

    // ------------------------------------------------------------------
    // 1. Empty / null items list
    // ------------------------------------------------------------------

    @Test
    void checkStock_emptyList_returnsAllSufficientWithNoItems() {
        StockCheckRespDTO resp = stockCheckService.checkStock(TENANT_ID, List.of());

        assertThat(resp).isNotNull();
        assertThat(resp.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(resp.isAllSufficient()).isTrue();
        assertThat(resp.getItems()).isEmpty();
        assertThat(resp.getUnmappedItems()).isEmpty();
    }

    @Test
    void checkStock_nullItems_returnsAllSufficientWithNoItems() {
        StockCheckRespDTO resp = stockCheckService.checkStock(TENANT_ID, null);

        assertThat(resp).isNotNull();
        assertThat(resp.isAllSufficient()).isTrue();
        assertThat(resp.getItems()).isEmpty();
    }

    // ------------------------------------------------------------------
    // 2. Unmapped component (no stock_item found)
    // ------------------------------------------------------------------

    @Test
    void checkStock_unmappedComponent_returnsUnmappedStatusAndFalseAllSufficient() {
        // --- Arrange: finished product resolves by product_code ---
        ProductMasterDO finished = new ProductMasterDO();
        finished.setId(10L);
        finished.setProductCode("FIN-001");
        finished.setSkuCode("SKU-FIN-001");
        when(productMasterMapper.selectByTenantProductCode(TENANT_ID, "FIN-001"))
                .thenReturn(finished);

        // --- BOM explosion returns one RAW_MATERIAL leaf ---
        BomExplosionRespDTO raw = rawMaterialNode(20L, "RAW-001", "Raw Material A",
                new BigDecimal("4.0000"));
        when(bomExplosionService.explode(eq(10L), eq(null),
                eq(new BigDecimal("2")), eq(TENANT_ID)))
                .thenReturn(List.of(raw));

        // --- Component product lookup (for skuCode resolution) ---
        ProductMasterDO compProduct = new ProductMasterDO();
        compProduct.setId(20L);
        compProduct.setProductCode("RAW-001");
        compProduct.setSkuCode("SKU-RAW-001");
        when(productMasterMapper.selectByIdAndTenant(20L, TENANT_ID))
                .thenReturn(compProduct);

        // --- No stock_item mapping → UNMAPPED ---
        when(stockItemMapper.selectActiveByTenantSkuCode(TENANT_ID, "SKU-RAW-001"))
                .thenReturn(null);

        // --- Act ---
        SkuQuantityDTO input = sku("FIN-001", new BigDecimal("2"));
        StockCheckRespDTO resp = stockCheckService.checkStock(TENANT_ID, List.of(input));

        // --- Assert ---
        assertThat(resp).isNotNull();
        assertThat(resp.isAllSufficient()).isFalse();
        assertThat(resp.getItems()).hasSize(1);

        StockCheckItemResultDTO item = resp.getItems().get(0);
        assertThat(item.getProductCode()).isEqualTo("RAW-001");
        assertThat(item.getSkuCode()).isEqualTo("SKU-RAW-001");
        assertThat(item.getStatus()).isEqualTo("UNMAPPED");
        assertThat(item.getAvailableQty()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(item.getStockItemId()).isNull();
        assertThat(item.getRequiredQty()).isEqualByComparingTo(new BigDecimal("4.0000"));

        assertThat(resp.getUnmappedItems()).hasSize(1);
        assertThat(resp.getUnmappedItems().get(0).getStatus()).isEqualTo("UNMAPPED");
    }

    // ------------------------------------------------------------------
    // 3. Insufficient stock
    // ------------------------------------------------------------------

    @Test
    void checkStock_insufficientStock_returnsInsufficientStatusAndFalseAllSufficient() {
        // --- Arrange ---
        ProductMasterDO finished = new ProductMasterDO();
        finished.setId(10L);
        finished.setProductCode("FIN-002");
        finished.setSkuCode("SKU-FIN-002");
        when(productMasterMapper.selectByTenantProductCode(TENANT_ID, "FIN-002"))
                .thenReturn(finished);

        BomExplosionRespDTO raw = rawMaterialNode(20L, "RAW-002", "Raw Material B",
                new BigDecimal("10.0000"));
        when(bomExplosionService.explode(eq(10L), eq(null),
                eq(new BigDecimal("5")), eq(TENANT_ID)))
                .thenReturn(List.of(raw));

        ProductMasterDO compProduct = new ProductMasterDO();
        compProduct.setId(20L);
        compProduct.setProductCode("RAW-002");
        compProduct.setSkuCode("SKU-RAW-002");
        when(productMasterMapper.selectByIdAndTenant(20L, TENANT_ID))
                .thenReturn(compProduct);

        StockItemDO stockItem = new StockItemDO();
        stockItem.setId(30L);
        stockItem.setSkuCode("SKU-RAW-002");
        when(stockItemMapper.selectActiveByTenantSkuCode(TENANT_ID, "SKU-RAW-002"))
                .thenReturn(stockItem);

        // Available = 3, required = 10 → INSUFFICIENT
        when(stockBalanceMapper.sumAvailableQtyByTenantItem(TENANT_ID, 30L))
                .thenReturn(new BigDecimal("3"));

        // --- Act ---
        SkuQuantityDTO input = sku("FIN-002", new BigDecimal("5"));
        StockCheckRespDTO resp = stockCheckService.checkStock(TENANT_ID, List.of(input));

        // --- Assert ---
        assertThat(resp).isNotNull();
        assertThat(resp.isAllSufficient()).isFalse();
        assertThat(resp.getItems()).hasSize(1);

        StockCheckItemResultDTO item = resp.getItems().get(0);
        assertThat(item.getStatus()).isEqualTo("INSUFFICIENT");
        assertThat(item.getStockItemId()).isEqualTo(30L);
        assertThat(item.getAvailableQty()).isEqualByComparingTo(new BigDecimal("3"));
        assertThat(item.getRequiredQty()).isEqualByComparingTo(new BigDecimal("10.0000"));
        assertThat(resp.getUnmappedItems()).isEmpty();
    }

    // ------------------------------------------------------------------
    // 4. Sufficient stock (happy path)
    // ------------------------------------------------------------------

    @Test
    void checkStock_sufficientStock_returnsSufficientStatusAndTrueAllSufficient() {
        ProductMasterDO finished = new ProductMasterDO();
        finished.setId(10L);
        finished.setProductCode("FIN-003");
        finished.setSkuCode("SKU-FIN-003");
        when(productMasterMapper.selectByTenantProductCode(TENANT_ID, "FIN-003"))
                .thenReturn(finished);

        BomExplosionRespDTO raw = rawMaterialNode(20L, "RAW-003", "Raw Material C",
                new BigDecimal("2.0000"));
        when(bomExplosionService.explode(eq(10L), eq(null),
                eq(new BigDecimal("2")), eq(TENANT_ID)))
                .thenReturn(List.of(raw));

        ProductMasterDO compProduct = new ProductMasterDO();
        compProduct.setId(20L);
        compProduct.setProductCode("RAW-003");
        compProduct.setSkuCode("SKU-RAW-003");
        when(productMasterMapper.selectByIdAndTenant(20L, TENANT_ID))
                .thenReturn(compProduct);

        StockItemDO stockItem = new StockItemDO();
        stockItem.setId(40L);
        stockItem.setSkuCode("SKU-RAW-003");
        when(stockItemMapper.selectActiveByTenantSkuCode(TENANT_ID, "SKU-RAW-003"))
                .thenReturn(stockItem);

        // Available = 5, required = 2 → SUFFICIENT
        when(stockBalanceMapper.sumAvailableQtyByTenantItem(TENANT_ID, 40L))
                .thenReturn(new BigDecimal("5"));

        SkuQuantityDTO input = sku("FIN-003", new BigDecimal("2"));
        StockCheckRespDTO resp = stockCheckService.checkStock(TENANT_ID, List.of(input));

        assertThat(resp).isNotNull();
        assertThat(resp.isAllSufficient()).isTrue();
        assertThat(resp.getItems()).hasSize(1);
        assertThat(resp.getItems().get(0).getStatus()).isEqualTo("SUFFICIENT");
        assertThat(resp.getItems().get(0).getAvailableQty()).isEqualByComparingTo(new BigDecimal("5"));
    }

    // ------------------------------------------------------------------
    // 5. Unresolved product (no product_master match) → skipped
    // ------------------------------------------------------------------

    @Test
    void checkStock_unresolvedProduct_returnsAllSufficientWithNoItems() {
        when(productMasterMapper.selectByTenantProductCode(TENANT_ID, "UNKNOWN"))
                .thenReturn(null);
        when(productMasterMapper.listByTenant(TENANT_ID))
                .thenReturn(List.of());

        SkuQuantityDTO input = sku("UNKNOWN", BigDecimal.ONE);
        StockCheckRespDTO resp = stockCheckService.checkStock(TENANT_ID, List.of(input));

        assertThat(resp).isNotNull();
        assertThat(resp.isAllSufficient()).isTrue();
        assertThat(resp.getItems()).isEmpty();
    }

    // ------------------------------------------------------------------
    // 6. Aggregation across multiple input SKUs (same raw material)
    // ------------------------------------------------------------------

    @Test
    void checkStock_multipleSkus_aggregatesRawMaterialRequirements() {
        // Two finished products both consuming the same raw material
        ProductMasterDO fin1 = new ProductMasterDO();
        fin1.setId(10L);
        fin1.setProductCode("FIN-A");
        fin1.setSkuCode("SKU-FIN-A");
        when(productMasterMapper.selectByTenantProductCode(TENANT_ID, "FIN-A"))
                .thenReturn(fin1);

        ProductMasterDO fin2 = new ProductMasterDO();
        fin2.setId(11L);
        fin2.setProductCode("FIN-B");
        fin2.setSkuCode("SKU-FIN-B");
        when(productMasterMapper.selectByTenantProductCode(TENANT_ID, "FIN-B"))
                .thenReturn(fin2);

        // FIN-A needs 3 units of RAW-X (qty=1 → 3)
        BomExplosionRespDTO rawA = rawMaterialNode(20L, "RAW-X", "Raw X",
                new BigDecimal("3.0000"));
        when(bomExplosionService.explode(eq(10L), eq(null),
                eq(BigDecimal.ONE), eq(TENANT_ID)))
                .thenReturn(List.of(rawA));

        // FIN-B needs 2 units of RAW-X (qty=2 → 4)
        BomExplosionRespDTO rawB = rawMaterialNode(20L, "RAW-X", "Raw X",
                new BigDecimal("4.0000"));
        when(bomExplosionService.explode(eq(11L), eq(null),
                eq(new BigDecimal("2")), eq(TENANT_ID)))
                .thenReturn(List.of(rawB));

        // Component product lookup
        ProductMasterDO compProduct = new ProductMasterDO();
        compProduct.setId(20L);
        compProduct.setProductCode("RAW-X");
        compProduct.setSkuCode("SKU-RAW-X");
        when(productMasterMapper.selectByIdAndTenant(20L, TENANT_ID))
                .thenReturn(compProduct);

        StockItemDO stockItem = new StockItemDO();
        stockItem.setId(50L);
        stockItem.setSkuCode("SKU-RAW-X");
        when(stockItemMapper.selectActiveByTenantSkuCode(TENANT_ID, "SKU-RAW-X"))
                .thenReturn(stockItem);

        // Available = 10, aggregated required = 3 + 4 = 7 → SUFFICIENT
        when(stockBalanceMapper.sumAvailableQtyByTenantItem(TENANT_ID, 50L))
                .thenReturn(new BigDecimal("10"));

        SkuQuantityDTO input1 = sku("FIN-A", BigDecimal.ONE);
        SkuQuantityDTO input2 = sku("FIN-B", new BigDecimal("2"));
        StockCheckRespDTO resp = stockCheckService.checkStock(TENANT_ID,
                List.of(input1, input2));

        assertThat(resp).isNotNull();
        assertThat(resp.isAllSufficient()).isTrue();
        assertThat(resp.getItems()).hasSize(1); // aggregated into one row

        StockCheckItemResultDTO item = resp.getItems().get(0);
        assertThat(item.getProductCode()).isEqualTo("RAW-X");
        assertThat(item.getRequiredQty()).isEqualByComparingTo(new BigDecimal("7.0000"));
        assertThat(item.getStatus()).isEqualTo("SUFFICIENT");
    }

    // ------------------------------------------------------------------
    // 7. Null/blank skuCode in input → skipped
    // ------------------------------------------------------------------

    @Test
    void checkStock_blankSkuCodeInput_isSkipped() {
        SkuQuantityDTO blank = sku("  ", BigDecimal.ONE);
        SkuQuantityDTO nullSku = sku(null, BigDecimal.ONE);
        StockCheckRespDTO resp = stockCheckService.checkStock(TENANT_ID,
                List.of(blank, nullSku));

        assertThat(resp).isNotNull();
        assertThat(resp.isAllSufficient()).isTrue();
        assertThat(resp.getItems()).isEmpty();
    }

    // ------------------------------------------------------------------
    // 8. Null quantity defaults to ONE
    // ------------------------------------------------------------------

    @Test
    void checkStock_nullQuantity_defaultsToOne() {
        ProductMasterDO finished = new ProductMasterDO();
        finished.setId(10L);
        finished.setProductCode("FIN-Q");
        finished.setSkuCode("SKU-FIN-Q");
        when(productMasterMapper.selectByTenantProductCode(TENANT_ID, "FIN-Q"))
                .thenReturn(finished);

        BomExplosionRespDTO raw = rawMaterialNode(20L, "RAW-Q", "Raw Q",
                new BigDecimal("2.0000"));
        // null quantity → defaults to BigDecimal.ONE
        when(bomExplosionService.explode(eq(10L), eq(null),
                eq(BigDecimal.ONE), eq(TENANT_ID)))
                .thenReturn(List.of(raw));

        ProductMasterDO compProduct = new ProductMasterDO();
        compProduct.setId(20L);
        compProduct.setProductCode("RAW-Q");
        compProduct.setSkuCode("SKU-RAW-Q");
        when(productMasterMapper.selectByIdAndTenant(20L, TENANT_ID))
                .thenReturn(compProduct);

        StockItemDO stockItem = new StockItemDO();
        stockItem.setId(60L);
        stockItem.setSkuCode("SKU-RAW-Q");
        when(stockItemMapper.selectActiveByTenantSkuCode(TENANT_ID, "SKU-RAW-Q"))
                .thenReturn(stockItem);

        when(stockBalanceMapper.sumAvailableQtyByTenantItem(TENANT_ID, 60L))
                .thenReturn(new BigDecimal("5"));

        SkuQuantityDTO input = sku("FIN-Q", null);
        StockCheckRespDTO resp = stockCheckService.checkStock(TENANT_ID, List.of(input));

        assertThat(resp).isNotNull();
        assertThat(resp.isAllSufficient()).isTrue();
        assertThat(resp.getItems().get(0).getRequiredQty())
                .isEqualByComparingTo(new BigDecimal("2.0000"));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private SkuQuantityDTO sku(String skuCode, BigDecimal quantity) {
        SkuQuantityDTO dto = new SkuQuantityDTO();
        dto.setSkuCode(skuCode);
        dto.setQuantity(quantity);
        return dto;
    }

    private BomExplosionRespDTO rawMaterialNode(Long productId, String productCode,
                                                 String productName, BigDecimal quantity) {
        BomExplosionRespDTO node = new BomExplosionRespDTO();
        node.setProductId(productId);
        node.setProductCode(productCode);
        node.setProductName(productName);
        node.setComponentType("RAW_MATERIAL");
        node.setQuantity(quantity);
        node.setDepth(2);
        return node;
    }
}
