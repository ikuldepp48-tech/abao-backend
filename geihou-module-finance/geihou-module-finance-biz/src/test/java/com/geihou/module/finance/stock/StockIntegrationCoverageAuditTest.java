package com.geihou.module.finance.stock;

import com.geihou.module.finance.api.product.ProductApi;
import com.geihou.module.finance.api.product.dto.SkuRespDTO;
import com.geihou.module.finance.cart.dal.dataobject.CartItemDO;
import com.geihou.module.finance.cart.dal.mapper.CartItemMapper;
import com.geihou.module.finance.checkout.dal.mapper.CheckoutSessionMapper;
import com.geihou.module.supplychain.api.stock.enums.StockCoverageModeEnum;
import com.geihou.module.supplychain.api.stock.enums.StockCoverageTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StockIntegrationCoverageAuditTest {

    private StockTestConfig.MockStockEventApi stockEventApi;
    private StockTestConfig.MockStockCoverageApi stockCoverageApi;
    private StockTestConfig.MockStockQueryApi stockQueryApi;
    private StockTestConfig.MockBomApi bomApi;
    private StockTestConfig.MockStockApi stockApi;
    private ProductApi productApi;
    private CartItemMapper cartItemMapper;
    private StockIntegrationServiceImpl service;

    @BeforeEach
    void setUp() {
        stockEventApi = new StockTestConfig.MockStockEventApi();
        stockCoverageApi = new StockTestConfig.MockStockCoverageApi();
        stockQueryApi = new StockTestConfig.MockStockQueryApi();
        bomApi = new StockTestConfig.MockBomApi();
        stockApi = new StockTestConfig.MockStockApi();
        productApi = mock(ProductApi.class);
        CheckoutSessionMapper checkoutSessionMapper = mock(CheckoutSessionMapper.class);
        cartItemMapper = mock(CartItemMapper.class);
        service = new StockIntegrationServiceImpl(stockEventApi, stockCoverageApi, stockQueryApi,
                stockApi, bomApi, productApi, checkoutSessionMapper, cartItemMapper);
    }

    @Test
    void reserveForCheckout_locationMissing_auditOnlyRecordsAndSkips() {
        stockQueryApi.hasStockLocationMapping = false;

        service.reserveForCheckout(1L, 10L, 20L, 30L, 40L);

        assertThat(stockCoverageApi.observations).hasSize(1);
        assertThat(stockCoverageApi.observations.get(0).getCoverageType())
                .isEqualTo(StockCoverageTypeEnum.LOCATION_MISSING);
    }

    @Test
    void reserveForCheckout_stockItemMissing_auditOnlyRecordsAndSkips() {
        stockQueryApi.hasStockItemMapping = false;
        CartItemDO item = cartItem(1001L, 2);
        when(cartItemMapper.selectList(any(), any(), any(), any())).thenReturn(List.of(item));
        when(productApi.batchGetSkus(anyList())).thenReturn(Map.of(1001L, sku(1001L, "SKU_BEEF")));

        service.reserveForCheckout(1L, 10L, 20L, 30L, 40L);

        assertThat(stockCoverageApi.observations).hasSize(1);
        assertThat(stockCoverageApi.observations.get(0).getCoverageType())
                .isEqualTo(StockCoverageTypeEnum.STOCK_ITEM_MISSING);
    }

    @Test
    void reserveForCheckout_missingMapping_enforceThrowsBeforeReserve() {
        stockCoverageApi.mode = StockCoverageModeEnum.ENFORCE;
        stockQueryApi.hasStockItemMapping = false;
        CartItemDO item = cartItem(1001L, 2);
        when(cartItemMapper.selectList(any(), any(), any(), any())).thenReturn(List.of(item));
        when(productApi.batchGetSkus(anyList())).thenReturn(Map.of(1001L, sku(1001L, "SKU_BEEF")));

        assertThatThrownBy(() -> service.reserveForCheckout(1L, 10L, 20L, 30L, 40L))
                .isInstanceOf(StockIntegrationServiceImpl.StockMappingCoverageViolationException.class)
                .hasMessageContaining("STOCK_MAPPING_COVERAGE_VIOLATION");
    }

    @Test
    void reserveForCheckout_mappedSkuAndLocationDoesNotRecordCoverage() {
        CartItemDO item = cartItem(1001L, 2);
        when(cartItemMapper.selectList(any(), any(), any(), any())).thenReturn(List.of(item));
        when(productApi.batchGetSkus(anyList())).thenReturn(Map.of(1001L, sku(1001L, "SKU_BEEF")));

        service.reserveForCheckout(1L, 10L, 20L, 30L, 40L);

        assertThat(stockCoverageApi.observations).isEmpty();
    }

    private CartItemDO cartItem(Long skuId, Integer quantity) {
        CartItemDO item = new CartItemDO();
        item.setSkuId(skuId);
        item.setQuantity(quantity);
        return item;
    }

    private SkuRespDTO sku(Long id, String code) {
        SkuRespDTO sku = new SkuRespDTO();
        sku.setId(id);
        sku.setSkuCode(code);
        return sku;
    }
}
