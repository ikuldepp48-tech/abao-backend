package com.geihou.module.supplychain.production.controller.admin;

import com.geihou.common.pojo.CommonResult;
import com.geihou.common.pojo.PageResult;
import com.geihou.module.supplychain.production.controller.admin.vo.ProductionOrderCreateReqVO;
import com.geihou.module.supplychain.production.controller.admin.vo.ProductionOrderRespVO;
import com.geihou.module.supplychain.production.controller.admin.vo.ProductionOrderStageTransitionReqVO;
import com.geihou.module.supplychain.production.controller.admin.vo.ProductionOrderUpdateReqVO;
import com.geihou.module.supplychain.production.dal.dataobject.ProductionOrderDO;
import com.geihou.module.supplychain.production.service.ProductionOrderService;
import com.geihou.module.supplychain.stock.framework.StockBusinessException;
import com.geihou.module.supplychain.stock.framework.StockErrorCodeConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lightweight unit tests for {@link ProductionOrderController}.
 *
 * <p>Uses Mockito mocks — no Spring context, no DB.
 *
 * <p>Source: TASK-G2-02K Section 11.2.
 */
@ExtendWith(MockitoExtension.class)
class ProductionOrderControllerTest {

    @Mock
    private ProductionOrderService productionOrderService;

    @InjectMocks
    private ProductionOrderController controller;

    @Test
    void testCreateEndpoint() {
        ProductionOrderCreateReqVO req = new ProductionOrderCreateReqVO();
        req.setTenantId(1L);
        req.setProductId(100L);
        req.setRecipeId(200L);
        req.setLocationId(10L);
        req.setPlannedQty(new BigDecimal("50"));
        req.setOperatorUserId(100L);

        ProductionOrderDO mockOrder = buildMockOrder();
        when(productionOrderService.createProductionOrder(any(ProductionOrderCreateReqVO.class)))
                .thenReturn(mockOrder);

        CommonResult<ProductionOrderRespVO> result = controller.create(req);

        assertThat(result.getCode()).isEqualTo(CommonResult.SUCCESS_CODE);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getOrderNo()).isEqualTo("PO202606291200001234");
        assertThat(result.getData().getProductionStage()).isEqualTo("CREATED");
        verify(productionOrderService).createProductionOrder(eq(req));
    }

    @Test
    void testGetEndpoint() {
        ProductionOrderDO mockOrder = buildMockOrder();
        when(productionOrderService.getProductionOrder(eq(1L), eq(1L))).thenReturn(mockOrder);

        CommonResult<ProductionOrderRespVO> result = controller.get(1L, 1L);

        assertThat(result.getCode()).isEqualTo(CommonResult.SUCCESS_CODE);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getId()).isEqualTo(1L);
        verify(productionOrderService).getProductionOrder(eq(1L), eq(1L));
    }

    @Test
    void testGetEndpointNotFound() {
        when(productionOrderService.getProductionOrder(eq(999L), eq(1L))).thenReturn(null);

        CommonResult<ProductionOrderRespVO> result = controller.get(999L, 1L);

        assertThat(result.getCode()).isEqualTo(CommonResult.SUCCESS_CODE);
        assertThat(result.getData()).isNull();
    }

    @Test
    void testPageEndpoint() {
        ProductionOrderDO mockOrder = buildMockOrder();
        PageResult<ProductionOrderDO> mockPage = PageResult.of(List.of(mockOrder), 1L, 1, 10);
        when(productionOrderService.listProductionOrders(eq(1L), eq("CREATED"), eq(1), eq(10)))
                .thenReturn(mockPage);

        CommonResult<PageResult<ProductionOrderRespVO>> result = controller.page(1L, "CREATED", 1, 10);

        assertThat(result.getCode()).isEqualTo(CommonResult.SUCCESS_CODE);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getList()).hasSize(1);
        assertThat(result.getData().getTotal()).isEqualTo(1L);
        assertThat(result.getData().getList().get(0).getOrderNo()).isEqualTo("PO202606291200001234");
        verify(productionOrderService).listProductionOrders(eq(1L), eq("CREATED"), eq(1), eq(10));
    }

    @Test
    void testUpdateEndpoint() {
        ProductionOrderUpdateReqVO req = new ProductionOrderUpdateReqVO();
        req.setTenantId(1L);
        req.setPlannedQty(new BigDecimal("200"));
        req.setRemark("updated");

        ProductionOrderDO mockOrder = buildMockOrder();
        mockOrder.setPlannedQty(new BigDecimal("200.0000"));
        mockOrder.setRemark("updated");
        when(productionOrderService.updateProductionOrder(any(ProductionOrderUpdateReqVO.class)))
                .thenReturn(mockOrder);

        CommonResult<ProductionOrderRespVO> result = controller.update(1L, req);

        assertThat(result.getCode()).isEqualTo(CommonResult.SUCCESS_CODE);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getPlannedQty()).isEqualByComparingTo(new BigDecimal("200.0000"));
        verify(productionOrderService).updateProductionOrder(any(ProductionOrderUpdateReqVO.class));
    }

    @Test
    void testTransitionEndpoint() {
        ProductionOrderStageTransitionReqVO req = new ProductionOrderStageTransitionReqVO();
        req.setTenantId(1L);
        req.setTargetStage("MATERIAL_REQUEST");

        ProductionOrderDO mockOrder = buildMockOrder();
        mockOrder.setProductionStage("MATERIAL_REQUEST");
        when(productionOrderService.transitionStage(any(ProductionOrderStageTransitionReqVO.class)))
                .thenReturn(mockOrder);

        CommonResult<ProductionOrderRespVO> result = controller.transitionStage(1L, req);

        assertThat(result.getCode()).isEqualTo(CommonResult.SUCCESS_CODE);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getProductionStage()).isEqualTo("MATERIAL_REQUEST");
        verify(productionOrderService).transitionStage(any(ProductionOrderStageTransitionReqVO.class));
    }

    @Test
    void testCreateExceptionPropagates() {
        ProductionOrderCreateReqVO req = new ProductionOrderCreateReqVO();
        req.setTenantId(1L);

        StockBusinessException ex = new StockBusinessException(
                StockErrorCodeConstants.PRODUCTION_PLANNED_QTY_MUST_BE_POSITIVE);
        when(productionOrderService.createProductionOrder(any(ProductionOrderCreateReqVO.class)))
                .thenThrow(ex);

        assertThatThrownBy(() -> controller.create(req))
                .isInstanceOf(StockBusinessException.class)
                .isSameAs(ex);
    }

    private ProductionOrderDO buildMockOrder() {
        ProductionOrderDO order = new ProductionOrderDO();
        order.setId(1L);
        order.setTenantId(1L);
        order.setOrderNo("PO20260629120000" + "1234");
        order.setProductId(100L);
        order.setRecipeId(200L);
        order.setLocationId(10L);
        order.setPlannedQty(new BigDecimal("50.0000"));
        order.setProductionStage("CREATED");
        order.setOperatorUserId(100L);
        order.setRemark("test");
        order.setCreator("100");
        order.setCreateTime(LocalDateTime.now());
        order.setUpdater("100");
        order.setUpdateTime(LocalDateTime.now());
        order.setDeleted(false);
        return order;
    }
}
