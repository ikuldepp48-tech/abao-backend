package com.geihou.module.finance.stock;

import com.geihou.module.finance.api.product.ProductApi;
import com.geihou.module.finance.api.product.dto.SkuRespDTO;
import com.geihou.module.finance.cart.dal.dataobject.CartItemDO;
import com.geihou.module.finance.cart.dal.mapper.CartItemMapper;
import com.geihou.module.finance.checkout.dal.dataobject.CheckoutSessionDO;
import com.geihou.module.finance.checkout.dal.mapper.CheckoutSessionMapper;
import com.geihou.module.supplychain.api.stock.dto.SalesOutBomReverseReqDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesReverseRestoreReqDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Focused finance tests for G2-02H-3 BOM reverse wiring.
 *
 * <p>Covers the required behaviors from task package section 7:
 * active-BOM skips finished commit, calls salesOutWithBomReverse,
 * non-BOM keeps commit, empty-state keeps non-BOM, refund restore
 * calls salesReverseRestore, idempotency keys deterministic,
 * tenant ID passed through.
 */
class StockIntegrationBomWiringTest {

    private StockTestConfig.MockStockEventApi stockEventApi;
    private StockTestConfig.MockStockCoverageApi stockCoverageApi;
    private StockTestConfig.MockStockQueryApi stockQueryApi;
    private StockTestConfig.MockBomApi bomApi;
    private StockTestConfig.MockStockApi stockApi;
    private ProductApi productApi;
    private CheckoutSessionMapper checkoutSessionMapper;
    private CartItemMapper cartItemMapper;
    private StockIntegrationServiceImpl service;

    private static final Long TENANT_ID = 7L;
    private static final Long SESSION_ID = 100L;
    private static final Long ORDER_ID = 200L;
    private static final Long SHOP_ID = 30L;
    private static final Long CUSTOMER_USER_ID = 40L;
    private static final String ORDER_NO = "T7-ORDER-200";
    private static final Long SKU_ID = 1001L;
    private static final Long CART_ITEM_ID = 9001L;
    private static final Long ORDER_ITEM_ID = 8001L;
    private static final String SKU_CODE = "SKU_BEEF";
    private static final Long PRODUCT_ID = 101L;
    private static final Long RECIPE_ID = 999L;

    @BeforeEach
    void setUp() {
        stockEventApi = new StockTestConfig.MockStockEventApi();
        stockCoverageApi = new StockTestConfig.MockStockCoverageApi();
        stockQueryApi = new StockTestConfig.MockStockQueryApi();
        bomApi = new StockTestConfig.MockBomApi();
        stockApi = new StockTestConfig.MockStockApi();
        productApi = mock(ProductApi.class);
        checkoutSessionMapper = mock(CheckoutSessionMapper.class);
        cartItemMapper = mock(CartItemMapper.class);

        service = new StockIntegrationServiceImpl(
                stockEventApi, stockCoverageApi, stockQueryApi,
                stockApi, bomApi, productApi, checkoutSessionMapper, cartItemMapper);

        CheckoutSessionDO session = new CheckoutSessionDO();
        session.setId(SESSION_ID);
        session.setTenantId(TENANT_ID);
        session.setCartId(50L);
        session.setShopId(SHOP_ID);
        session.setCustomerUserId(CUSTOMER_USER_ID);
        when(checkoutSessionMapper.selectById(SESSION_ID)).thenReturn(session);

        CartItemDO cartItem = cartItem(SKU_ID, 2);
        when(cartItemMapper.selectList(any(), any(), any(), any())).thenReturn(List.of(cartItem));

        when(productApi.batchGetSkus(anyList())).thenReturn(Map.of(SKU_ID, sku(SKU_ID, SKU_CODE)));
    }

    @Test
    void reserve_activeBom_skipsFinishedReserveAndCallsCheckStock() {
        bomApi.activeRecipes.put(SKU_CODE, StockTestConfig.MockBomApi.activeRecipe(PRODUCT_ID, RECIPE_ID));
        stockApi.checkStockAllSufficient = true;

        service.reserveForCheckout(TENANT_ID, SESSION_ID, 50L, SHOP_ID, CUSTOMER_USER_ID);

        assertThat(stockEventApi.getReserveStore()).isEmpty();
        assertThat(stockApi.checkStockTenantIdCalls).containsExactly(TENANT_ID);
    }

    @Test
    void reserve_activeBom_insufficientStock_failsClosed() {
        bomApi.activeRecipes.put(SKU_CODE, StockTestConfig.MockBomApi.activeRecipe(PRODUCT_ID, RECIPE_ID));
        stockApi.checkStockAllSufficient = false;

        assertThatThrownBy(() -> service.reserveForCheckout(TENANT_ID, SESSION_ID, 50L, SHOP_ID, CUSTOMER_USER_ID))
                .isInstanceOf(StockIntegrationServiceImpl.BomStockInsufficientException.class);

        assertThat(stockEventApi.getReserveStore()).isEmpty();
    }

    @Test
    void reserve_bomLookupThrows_failsClosed() {
        bomApi.shouldThrow = true;

        assertThatThrownBy(() -> service.reserveForCheckout(TENANT_ID, SESSION_ID, 50L, SHOP_ID, CUSTOMER_USER_ID))
                .isInstanceOf(StockIntegrationServiceImpl.BomLookupFailureException.class);

        assertThat(stockEventApi.getReserveStore()).isEmpty();
    }

    @Test
    void reserve_nonBom_keepsFinishedReserve() {
        service.reserveForCheckout(TENANT_ID, SESSION_ID, 50L, SHOP_ID, CUSTOMER_USER_ID);

        assertThat(stockEventApi.getReserveStore()).hasSize(1);
        assertThat(stockEventApi.getReserveStore()).containsKey("checkout-" + SESSION_ID + "-sku-" + SKU_ID);
        assertThat(stockApi.checkStockTenantIdCalls).isEmpty();
    }

    @Test
    void commit_activeBom_skipsFinishedCommitAndCallsSalesOutWithBomReverse() {
        bomApi.activeRecipes.put(SKU_CODE, StockTestConfig.MockBomApi.activeRecipe(PRODUCT_ID, RECIPE_ID));

        service.commitByCheckoutSession(TENANT_ID, SESSION_ID, ORDER_ID, CUSTOMER_USER_ID,
                LocalDate.of(2026, 6, 27), ORDER_NO, sourceOrderItemMap());

        assertThat(stockEventApi.getNextEventId()).isEqualTo(100L);
        assertThat(stockApi.salesOutRequests).hasSize(1);
        SalesOutBomReverseReqDTO req = stockApi.salesOutRequests.get(0);
        assertThat(req.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(req.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(req.getSkuCode()).isEqualTo(SKU_CODE);
        assertThat(req.getQuantity()).isEqualByComparingTo(new BigDecimal("2"));
        assertThat(req.getLocationId()).isEqualTo(1L);
        assertThat(req.getSourceModule()).isEqualTo("checkout");
        assertThat(req.getSourceRecordId()).isEqualTo(ORDER_ID);
        assertThat(req.getSourceOrderItemId()).isEqualTo(ORDER_ITEM_ID);
        assertThat(req.getReferenceNo()).isEqualTo(ORDER_NO);
        assertThat(req.getClientRequestId())
                .isEqualTo("checkout-bom-oi-" + ORDER_ITEM_ID + "-sku-" + SKU_ID);
    }

    @Test
    void commit_nonBom_keepsFinishedCommit() {
        service.commitByCheckoutSession(TENANT_ID, SESSION_ID, ORDER_ID, CUSTOMER_USER_ID,
                LocalDate.of(2026, 6, 27), ORDER_NO, sourceOrderItemMap());

        assertThat(stockEventApi.getNextEventId()).isEqualTo(101L);
        assertThat(stockApi.salesOutRequests).isEmpty();
    }

    @Test
    void commit_emptyStateBom_keepsNonBomPath() {
        bomApi.activeRecipes.clear();

        service.commitByCheckoutSession(TENANT_ID, SESSION_ID, ORDER_ID, CUSTOMER_USER_ID,
                LocalDate.of(2026, 6, 27), ORDER_NO, sourceOrderItemMap());

        assertThat(stockEventApi.getNextEventId()).isEqualTo(101L);
        assertThat(stockApi.salesOutRequests).isEmpty();
    }

    @Test
    void commit_bomLookupThrows_failsClosed() {
        bomApi.shouldThrow = true;

        assertThatThrownBy(() -> service.commitByCheckoutSession(TENANT_ID, SESSION_ID, ORDER_ID,
                CUSTOMER_USER_ID, LocalDate.of(2026, 6, 27), ORDER_NO, sourceOrderItemMap()))
                .isInstanceOf(StockIntegrationServiceImpl.BomLookupFailureException.class);

        assertThat(stockApi.salesOutRequests).isEmpty();
        assertThat(stockEventApi.getNextEventId()).isEqualTo(100L);
    }

    @Test
    void commit_activeBom_locationMissing_failsClosed() {
        bomApi.activeRecipes.put(SKU_CODE, StockTestConfig.MockBomApi.activeRecipe(PRODUCT_ID, RECIPE_ID));
        stockQueryApi.hasStockLocationMapping = false;

        assertThatThrownBy(() -> service.commitByCheckoutSession(TENANT_ID, SESSION_ID, ORDER_ID,
                CUSTOMER_USER_ID, LocalDate.of(2026, 6, 27), ORDER_NO, sourceOrderItemMap()))
                .isInstanceOf(StockIntegrationServiceImpl.BomLocationMissingException.class);

        assertThat(stockApi.salesOutRequests).isEmpty();
    }

    @Test
    void commit_activeBom_missingOrderItemTrace_failsClosed() {
        bomApi.activeRecipes.put(SKU_CODE, StockTestConfig.MockBomApi.activeRecipe(PRODUCT_ID, RECIPE_ID));

        assertThatThrownBy(() -> service.commitByCheckoutSession(TENANT_ID, SESSION_ID, ORDER_ID,
                CUSTOMER_USER_ID, LocalDate.of(2026, 6, 27), ORDER_NO, Map.of()))
                .isInstanceOf(StockIntegrationServiceImpl.OrderItemTraceMissingException.class)
                .hasMessageContaining("cartItemId=" + CART_ITEM_ID);

        assertThat(stockApi.salesOutRequests).isEmpty();
    }

    @Test
    void restoreForRefund_bomSaleSource_callsSalesReverseRestore() {
        stockApi.restoreShouldThrowNoOriginal = false;
        stockApi.restoreItemCount = 2;

        service.restoreForRefund(TENANT_ID, ORDER_ID, ORDER_NO, 500L, CUSTOMER_USER_ID);

        assertThat(stockApi.restoreRequests).hasSize(1);
        SalesReverseRestoreReqDTO req = stockApi.restoreRequests.get(0);
        assertThat(req.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(req.getSourceModule()).isEqualTo("checkout");
        assertThat(req.getSourceRecordId()).isEqualTo(ORDER_ID);
        assertThat(req.getReferenceNo()).isEqualTo(ORDER_NO);
        assertThat(req.getClientRequestId()).isEqualTo("refund-500-order-" + ORDER_ID + "-bom-restore");
    }

    @Test
    void restoreForRefund_itemScope_passesSourceOrderItemIds() {
        stockApi.restoreShouldThrowNoOriginal = false;
        stockApi.restoreItemCount = 2;

        service.restoreForRefund(TENANT_ID, ORDER_ID, ORDER_NO, 500L,
                CUSTOMER_USER_ID, List.of(ORDER_ITEM_ID));

        assertThat(stockApi.restoreRequests).hasSize(1);
        SalesReverseRestoreReqDTO req = stockApi.restoreRequests.get(0);
        assertThat(req.getSourceOrderItemIds()).containsExactly(ORDER_ITEM_ID);
    }

    @Test
    void restoreForRefund_itemScopeNoOriginal_failsClosed() {
        assertThatThrownBy(() -> service.restoreForRefund(TENANT_ID, ORDER_ID, ORDER_NO, 500L,
                CUSTOMER_USER_ID, List.of(ORDER_ITEM_ID)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("no original CONSUME_OUT events found");

        assertThat(stockApi.restoreRequests).hasSize(1);
        assertThat(stockApi.restoreRequests.get(0).getSourceOrderItemIds()).containsExactly(ORDER_ITEM_ID);
    }

    @Test
    void restoreForRefund_itemScopeZeroRestoreItems_failsClosed() {
        stockApi.restoreShouldThrowNoOriginal = false;
        stockApi.restoreItemCount = 0;

        assertThatThrownBy(() -> service.restoreForRefund(TENANT_ID, ORDER_ID, ORDER_NO, 500L,
                CUSTOMER_USER_ID, List.of(ORDER_ITEM_ID)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("returned 0 items");

        assertThat(stockApi.restoreRequests).hasSize(1);
        assertThat(stockApi.restoreRequests.get(0).getSourceOrderItemIds()).containsExactly(ORDER_ITEM_ID);
    }

    @Test
    void restoreForRefund_nonBomOrder_noOriginalEvents_isNoOp() {
        service.restoreForRefund(TENANT_ID, ORDER_ID, ORDER_NO, 500L, CUSTOMER_USER_ID);

        assertThat(stockApi.restoreRequests).hasSize(1);
    }

    @Test
    void restoreForRefund_conflictExceptionPropagates() {
        stockApi.restoreShouldThrowNoOriginal = false;
        stockApi.restoreException = new RuntimeException("client_request_id conflict");

        assertThatThrownBy(() -> service.restoreForRefund(TENANT_ID, ORDER_ID, ORDER_NO, 500L, CUSTOMER_USER_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("client_request_id conflict");
    }

    @Test
    void commit_idempotencyKey_isDeterministic() {
        bomApi.activeRecipes.put(SKU_CODE, StockTestConfig.MockBomApi.activeRecipe(PRODUCT_ID, RECIPE_ID));

        service.commitByCheckoutSession(TENANT_ID, SESSION_ID, ORDER_ID, CUSTOMER_USER_ID,
                LocalDate.of(2026, 6, 27), ORDER_NO, sourceOrderItemMap());

        String expected = "checkout-bom-oi-" + ORDER_ITEM_ID + "-sku-" + SKU_ID;
        assertThat(stockApi.salesOutRequests.get(0).getClientRequestId()).isEqualTo(expected);
    }

    @Test
    void restore_idempotencyKey_isDeterministic() {
        stockApi.restoreShouldThrowNoOriginal = false;

        Long refundId = 777L;
        service.restoreForRefund(TENANT_ID, ORDER_ID, ORDER_NO, refundId, CUSTOMER_USER_ID);

        String expected = "refund-" + refundId + "-order-" + ORDER_ID + "-bom-restore";
        assertThat(stockApi.restoreRequests.get(0).getClientRequestId()).isEqualTo(expected);
    }

    @Test
    void commit_tenantIdPassedToBomAndStockApis() {
        bomApi.activeRecipes.put(SKU_CODE, StockTestConfig.MockBomApi.activeRecipe(PRODUCT_ID, RECIPE_ID));

        service.commitByCheckoutSession(TENANT_ID, SESSION_ID, ORDER_ID, CUSTOMER_USER_ID,
                LocalDate.of(2026, 6, 27), ORDER_NO, sourceOrderItemMap());

        assertThat(bomApi.tenantIdCalls).containsExactly(TENANT_ID);
        assertThat(stockApi.salesOutRequests.get(0).getTenantId()).isEqualTo(TENANT_ID);
    }

    @Test
    void reserve_tenantIdPassedToBomAndStockApis() {
        bomApi.activeRecipes.put(SKU_CODE, StockTestConfig.MockBomApi.activeRecipe(PRODUCT_ID, RECIPE_ID));
        stockApi.checkStockAllSufficient = true;

        service.reserveForCheckout(TENANT_ID, SESSION_ID, 50L, SHOP_ID, CUSTOMER_USER_ID);

        assertThat(bomApi.tenantIdCalls).containsExactly(TENANT_ID);
        assertThat(stockApi.checkStockTenantIdCalls).containsExactly(TENANT_ID);
    }

    private CartItemDO cartItem(Long skuId, Integer quantity) {
        CartItemDO item = new CartItemDO();
        item.setId(CART_ITEM_ID);
        item.setSkuId(skuId);
        item.setQuantity(quantity);
        return item;
    }

    private Map<Long, Long> sourceOrderItemMap() {
        return Map.of(CART_ITEM_ID, ORDER_ITEM_ID);
    }

    private SkuRespDTO sku(Long id, String code) {
        SkuRespDTO sku = new SkuRespDTO();
        sku.setId(id);
        sku.setSkuCode(code);
        return sku;
    }
}
