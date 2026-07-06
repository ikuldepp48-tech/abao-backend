package com.geihou.module.supplychain.transfer.controller.admin;

import com.geihou.common.pojo.CommonResult;
import com.geihou.common.pojo.PageResult;
import com.geihou.module.supplychain.stock.framework.StockBusinessException;
import com.geihou.module.supplychain.stock.framework.StockErrorCodeConstants;
import com.geihou.module.supplychain.transfer.controller.admin.vo.TransferOrderCancelReqVO;
import com.geihou.module.supplychain.transfer.controller.admin.vo.TransferOrderCreateReqVO;
import com.geihou.module.supplychain.transfer.controller.admin.vo.TransferOrderItemReqVO;
import com.geihou.module.supplychain.transfer.controller.admin.vo.TransferOrderItemRespVO;
import com.geihou.module.supplychain.transfer.controller.admin.vo.TransferOrderReceiveReqVO;
import com.geihou.module.supplychain.transfer.controller.admin.vo.TransferOrderRespVO;
import com.geihou.module.supplychain.transfer.controller.admin.vo.TransferOrderShipReqVO;
import com.geihou.module.supplychain.transfer.dal.dataobject.TransferOrderDO;
import com.geihou.module.supplychain.transfer.dal.dataobject.TransferOrderItemDO;
import com.geihou.module.supplychain.transfer.dal.mapper.TransferOrderItemMapper;
import com.geihou.module.supplychain.transfer.service.TransferOrderService;
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
 * Lightweight unit tests for {@link TransferOrderController}.
 *
 * <p>Uses Mockito mocks — no Spring context, no DB.
 *
 * <p>Source: TASK-G2-02S Section 11.2.
 */
@ExtendWith(MockitoExtension.class)
class TransferOrderControllerTest {

    @Mock
    private TransferOrderService transferOrderService;

    @Mock
    private TransferOrderItemMapper transferOrderItemMapper;

    @InjectMocks
    private TransferOrderController controller;

    @Test
    void testCreateEndpoint() {
        TransferOrderCreateReqVO req = new TransferOrderCreateReqVO();
        req.setTenantId(1L);
        req.setFromLocationId(10L);
        req.setToLocationId(20L);
        req.setCreatedBy(100L);
        TransferOrderItemReqVO item = new TransferOrderItemReqVO();
        item.setStockItemId(2001L);
        item.setSkuCode("SKU_TEST");
        item.setQuantity(new BigDecimal("5"));
        item.setUnit("KG");
        req.setItems(List.of(item));

        TransferOrderDO mockOrder = buildMockOrder();
        when(transferOrderService.createTransferOrder(any(TransferOrderCreateReqVO.class))).thenReturn(mockOrder);
        when(transferOrderItemMapper.listByOrderAndTenant(eq(1L), eq(1L))).thenReturn(List.of());

        CommonResult<TransferOrderRespVO> result = controller.create(req);

        assertThat(result.getCode()).isEqualTo(CommonResult.SUCCESS_CODE);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getTransferNo()).isEqualTo("TO001");
        assertThat(result.getData().getStatus()).isEqualTo("PENDING");
        verify(transferOrderService).createTransferOrder(eq(req));
    }

    @Test
    void testShipEndpoint() {
        TransferOrderShipReqVO req = new TransferOrderShipReqVO();
        req.setTenantId(1L);
        req.setShippedBy(200L);

        TransferOrderDO mockOrder = buildMockOrder();
        mockOrder.setStatus("SENT");
        mockOrder.setShippedBy(200L);
        mockOrder.setShippedAt(LocalDateTime.now());
        when(transferOrderService.shipTransferOrder(any(TransferOrderShipReqVO.class))).thenReturn(mockOrder);
        when(transferOrderItemMapper.listByOrderAndTenant(eq(1L), eq(1L))).thenReturn(List.of());

        CommonResult<TransferOrderRespVO> result = controller.ship(1L, req);

        assertThat(result.getCode()).isEqualTo(CommonResult.SUCCESS_CODE);
        assertThat(result.getData().getStatus()).isEqualTo("SENT");
        assertThat(req.getId()).isEqualTo(1L);
        verify(transferOrderService).shipTransferOrder(any(TransferOrderShipReqVO.class));
    }

    @Test
    void testReceiveEndpoint() {
        TransferOrderReceiveReqVO req = new TransferOrderReceiveReqVO();
        req.setTenantId(1L);
        req.setReceivedBy(300L);

        TransferOrderDO mockOrder = buildMockOrder();
        mockOrder.setStatus("RECEIVED");
        mockOrder.setReceivedBy(300L);
        mockOrder.setReceivedAt(LocalDateTime.now());
        when(transferOrderService.receiveTransferOrder(any(TransferOrderReceiveReqVO.class))).thenReturn(mockOrder);
        when(transferOrderItemMapper.listByOrderAndTenant(eq(1L), eq(1L))).thenReturn(List.of());

        CommonResult<TransferOrderRespVO> result = controller.receive(1L, req);

        assertThat(result.getCode()).isEqualTo(CommonResult.SUCCESS_CODE);
        assertThat(result.getData().getStatus()).isEqualTo("RECEIVED");
        assertThat(req.getId()).isEqualTo(1L);
        verify(transferOrderService).receiveTransferOrder(any(TransferOrderReceiveReqVO.class));
    }

    @Test
    void testCancelEndpoint() {
        TransferOrderCancelReqVO req = new TransferOrderCancelReqVO();
        req.setTenantId(1L);
        req.setCancelledBy(100L);
        req.setCancelReason("wrong location");

        TransferOrderDO mockOrder = buildMockOrder();
        mockOrder.setStatus("CANCELLED");
        mockOrder.setCancelledBy(100L);
        mockOrder.setCancelledAt(LocalDateTime.now());
        mockOrder.setCancelReason("wrong location");
        when(transferOrderService.cancelTransferOrder(any(TransferOrderCancelReqVO.class))).thenReturn(mockOrder);
        when(transferOrderItemMapper.listByOrderAndTenant(eq(1L), eq(1L))).thenReturn(List.of());

        CommonResult<TransferOrderRespVO> result = controller.cancel(1L, req);

        assertThat(result.getCode()).isEqualTo(CommonResult.SUCCESS_CODE);
        assertThat(result.getData().getStatus()).isEqualTo("CANCELLED");
        assertThat(result.getData().getCancelReason()).isEqualTo("wrong location");
        assertThat(req.getId()).isEqualTo(1L);
        verify(transferOrderService).cancelTransferOrder(any(TransferOrderCancelReqVO.class));
    }

    @Test
    void testGetEndpoint() {
        TransferOrderDO mockOrder = buildMockOrder();
        when(transferOrderService.getTransferOrder(eq(1L), eq(1L))).thenReturn(mockOrder);
        when(transferOrderItemMapper.listByOrderAndTenant(eq(1L), eq(1L))).thenReturn(List.of(buildMockItem()));

        CommonResult<TransferOrderRespVO> result = controller.get(1L, 1L);

        assertThat(result.getCode()).isEqualTo(CommonResult.SUCCESS_CODE);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getId()).isEqualTo(1L);
        assertThat(result.getData().getItems()).hasSize(1);
        verify(transferOrderService).getTransferOrder(eq(1L), eq(1L));
    }

    @Test
    void testGetEndpointNotFound() {
        when(transferOrderService.getTransferOrder(eq(999L), eq(1L))).thenReturn(null);

        CommonResult<TransferOrderRespVO> result = controller.get(999L, 1L);

        assertThat(result.getCode()).isEqualTo(CommonResult.SUCCESS_CODE);
        assertThat(result.getData()).isNull();
    }

    @Test
    void testPageEndpoint() {
        TransferOrderDO mockOrder = buildMockOrder();
        PageResult<TransferOrderDO> mockPage = PageResult.of(List.of(mockOrder), 1L, 1, 10);
        when(transferOrderService.listTransferOrders(eq(1L), eq("PENDING"), eq(1), eq(10)))
                .thenReturn(mockPage);
        when(transferOrderItemMapper.listByOrderAndTenant(eq(1L), eq(1L))).thenReturn(List.of());

        CommonResult<PageResult<TransferOrderRespVO>> result = controller.page(1L, "PENDING", 1, 10);

        assertThat(result.getCode()).isEqualTo(CommonResult.SUCCESS_CODE);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getList()).hasSize(1);
        assertThat(result.getData().getTotal()).isEqualTo(1L);
        assertThat(result.getData().getList().get(0).getTransferNo()).isEqualTo("TO001");
        verify(transferOrderService).listTransferOrders(eq(1L), eq("PENDING"), eq(1), eq(10));
    }

    @Test
    void testCreateExceptionPropagates() {
        TransferOrderCreateReqVO req = new TransferOrderCreateReqVO();
        req.setTenantId(1L);

        StockBusinessException ex = new StockBusinessException(
                StockErrorCodeConstants.TRANSFER_FROM_TO_SAME);
        when(transferOrderService.createTransferOrder(any(TransferOrderCreateReqVO.class))).thenThrow(ex);

        assertThatThrownBy(() -> controller.create(req))
                .isInstanceOf(StockBusinessException.class)
                .isSameAs(ex);
    }

    // --- Helpers ---

    private TransferOrderDO buildMockOrder() {
        TransferOrderDO order = new TransferOrderDO();
        order.setId(1L);
        order.setTenantId(1L);
        order.setTransferNo("TO001");
        order.setFromLocationId(10L);
        order.setToLocationId(20L);
        order.setStatus("PENDING");
        order.setCreatedBy(100L);
        order.setCreator("100");
        order.setCreateTime(LocalDateTime.now());
        order.setUpdater("100");
        order.setUpdateTime(LocalDateTime.now());
        return order;
    }

    private TransferOrderItemDO buildMockItem() {
        TransferOrderItemDO item = new TransferOrderItemDO();
        item.setId(1L);
        item.setTenantId(1L);
        item.setTransferOrderId(1L);
        item.setProductId(1L);
        item.setStockItemId(2001L);
        item.setSkuCode("SKU_TEST");
        item.setQuantity(new BigDecimal("5.0000"));
        item.setUnit("KG");
        return item;
    }
}
