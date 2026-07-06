package com.geihou.module.supplychain.stock.service;

import com.geihou.common.pojo.CommonResult;
import com.geihou.module.supplychain.api.bom.BomApi;
import com.geihou.module.supplychain.api.bom.dto.BomRecipeRespDTO;
import com.geihou.module.supplychain.api.stock.StockApi;
import com.geihou.module.supplychain.api.stock.dto.ProductCurrentCostRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockHealthRespDTO;
import com.geihou.module.supplychain.bom.controller.admin.ActiveRecipeController;
import com.geihou.module.supplychain.stock.controller.admin.ProductCurrentCostController;
import com.geihou.module.supplychain.stock.controller.admin.StockHealthController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Controller delegation tests for the three G2-02G admin GET endpoints.
 *
 * <p>Verifies that each controller:
 * <ul>
 *   <li>Passes the correct parameters to the underlying API.</li>
 *   <li>Wraps the API response in {@link CommonResult#success(Object)}.</li>
 *   <li>Does not perform any business logic itself.</li>
 * </ul>
 *
 * <p>Uses Mockito mocks — no Spring context, no DB.
 *
 * <p>Source: TASK-G2-02G terminal review fix.
 */
@ExtendWith(MockitoExtension.class)
class StockHealthContractCompletionTest {

    @Mock
    private StockApi stockApi;

    @Mock
    private BomApi bomApi;

    @InjectMocks
    private ProductCurrentCostController productCurrentCostController;

    @InjectMocks
    private StockHealthController stockHealthController;

    @InjectMocks
    private ActiveRecipeController activeRecipeController;

    // ================================================================
    // GET /admin/stock/products/{productId}/current-cost
    // ================================================================

    @Test
    void getProductCurrentCost_delegatesToStockApi_andWrapsInCommonResult() {
        Long tenantId = 1L;
        Long productId = 100L;

        ProductCurrentCostRespDTO mockResp = new ProductCurrentCostRespDTO();
        mockResp.setTenantId(tenantId);
        mockResp.setProductId(productId);
        mockResp.setCalculationMode("BOM_STOCK_BALANCE_ESTIMATE");
        mockResp.setCostSource("STOCK_BALANCE_AVG_UNIT_COST");
        mockResp.setCurrentCost(new BigDecimal("20.00"));

        when(stockApi.getProductCurrentCost(tenantId, productId)).thenReturn(mockResp);

        CommonResult<ProductCurrentCostRespDTO> result =
                productCurrentCostController.getProductCurrentCost(tenantId, productId);

        verify(stockApi).getProductCurrentCost(eq(tenantId), eq(productId));
        assertThat(result.getCode()).isEqualTo(CommonResult.SUCCESS_CODE);
        assertThat(result.getData()).isSameAs(mockResp);
    }

    // ================================================================
    // GET /admin/stock/health
    // ================================================================

    @Test
    void getStockHealth_delegatesToStockApi_andWrapsInCommonResult() {
        Long tenantId = 1L;

        StockHealthRespDTO mockResp = new StockHealthRespDTO();
        mockResp.setTenantId(tenantId);
        mockResp.setLowStockItemCount(3);
        mockResp.setNegativeStockItemCount(1);
        mockResp.setReservedStockItemCount(2);
        mockResp.setRecentEventCount(10);
        mockResp.setLatestEventTime(LocalDateTime.now());

        when(stockApi.getStockHealth(tenantId)).thenReturn(mockResp);

        CommonResult<StockHealthRespDTO> result =
                stockHealthController.getStockHealth(tenantId);

        verify(stockApi).getStockHealth(eq(tenantId));
        assertThat(result.getCode()).isEqualTo(CommonResult.SUCCESS_CODE);
        assertThat(result.getData()).isSameAs(mockResp);
    }

    // ================================================================
    // GET /admin/bom/products/{productId}/active-recipe
    // ================================================================

    @Test
    void getActiveRecipe_delegatesToBomApi_andWrapsInCommonResult() {
        Long tenantId = 1L;
        Long productId = 200L;

        BomRecipeRespDTO mockResp = new BomRecipeRespDTO();
        mockResp.setId(501L);
        mockResp.setTenantId(tenantId);
        mockResp.setProductId(productId);
        mockResp.setItems(new ArrayList<>());

        when(bomApi.getActiveRecipe(tenantId, productId)).thenReturn(mockResp);

        CommonResult<BomRecipeRespDTO> result =
                activeRecipeController.getActiveRecipe(tenantId, productId);

        verify(bomApi).getActiveRecipe(eq(tenantId), eq(productId));
        assertThat(result.getCode()).isEqualTo(CommonResult.SUCCESS_CODE);
        assertThat(result.getData()).isSameAs(mockResp);
    }
}
