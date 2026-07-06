package com.geihou.module.supplychain.production.controller.app;

import com.geihou.common.pojo.CommonResult;
import com.geihou.module.supplychain.production.controller.admin.vo.ProductionOrderConsumptionRespVO;
import com.geihou.module.supplychain.production.controller.admin.vo.ProductionOrderOutputRespVO;
import com.geihou.module.supplychain.production.controller.admin.vo.ScanOutputReqVO;
import com.geihou.module.supplychain.production.controller.admin.vo.ScanPickReqVO;
import com.geihou.module.supplychain.production.dal.dataobject.ProductionOrderConsumptionDO;
import com.geihou.module.supplychain.production.dal.dataobject.ProductionOrderOutputDO;
import com.geihou.module.supplychain.production.service.ProductionConsumptionService;
import com.geihou.module.supplychain.production.service.ProductionOutputService;
import com.geihou.module.supplychain.stock.framework.StockBusinessException;
import com.geihou.module.supplychain.stock.framework.StockErrorCodeConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
 * Lightweight unit tests for {@link ProductionOrderAppController} (G2-02P).
 *
 * <p>Uses Mockito mocks — no Spring context, no DB. Covers the 4 required
 * endpoints, orderId pass-through, tenantId propagation, error propagation,
 * and tenant-isolation-by-service-mock semantics.
 *
 * <p>Source: TASK-G2-02P §7.
 */
@ExtendWith(MockitoExtension.class)
class ProductionOrderAppControllerTest {

    private static final Long TENANT_A = 1L;
    private static final Long TENANT_B = 2L;
    private static final Long ORDER_ID = 50L;
    private static final Long OPERATOR_ID = 100L;

    @Mock
    private ProductionConsumptionService productionConsumptionService;

    @Mock
    private ProductionOutputService productionOutputService;

    @InjectMocks
    private ProductionOrderAppController controller;

    // =====================================================================
    // POST /app-api/ck-worker/production/scan-pick
    // =====================================================================

    @Test
    void testScanPickDelegatesToServiceAndPassesOrderIdTenantId() {
        ScanPickReqVO req = buildScanPickReq(ORDER_ID, TENANT_A, 10L, new BigDecimal("20"), 1);

        ProductionOrderConsumptionDO mockDO = buildConsumptionDO(ORDER_ID, TENANT_A);
        when(productionConsumptionService.scanPick(any(ScanPickReqVO.class)))
                .thenReturn(mockDO);

        CommonResult<ProductionOrderConsumptionRespVO> result = controller.scanPick(req);

        assertThat(result.getCode()).isEqualTo(CommonResult.SUCCESS_CODE);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getId()).isEqualTo(mockDO.getId());
        assertThat(result.getData().getProductionOrderId()).isEqualTo(ORDER_ID);
        assertThat(result.getData().getTenantId()).isEqualTo(TENANT_A);

        // Verify the exact request object (with orderId + tenantId) is passed through
        ArgumentCaptor<ScanPickReqVO> captor = ArgumentCaptor.forClass(ScanPickReqVO.class);
        verify(productionConsumptionService).scanPick(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(ORDER_ID);
        assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT_A);
        assertThat(captor.getValue().getComponentProductId()).isEqualTo(10L);
        assertThat(captor.getValue().getActualQty()).isEqualByComparingTo(new BigDecimal("20"));
        assertThat(captor.getValue().getPickSeq()).isEqualTo(1);
    }

    @Test
    void testScanPickErrorPropagates() {
        ScanPickReqVO req = buildScanPickReq(ORDER_ID, TENANT_A, 10L, new BigDecimal("20"), 1);

        StockBusinessException ex = new StockBusinessException(
                StockErrorCodeConstants.PRODUCTION_SCAN_NOT_ALLOWED);
        when(productionConsumptionService.scanPick(any(ScanPickReqVO.class)))
                .thenThrow(ex);

        assertThatThrownBy(() -> controller.scanPick(req))
                .isInstanceOf(StockBusinessException.class)
                .isSameAs(ex);
    }

    @Test
    void testScanPickTenantIsolationDelegatedToService() {
        // Tenant B attempts to scan-pick Tenant A's order.
        // The controller does not perform tenant checks itself; it forwards the
        // tenantId to the service, which is responsible for rejecting mismatched
        // tenants with PRODUCTION_ORDER_NOT_FOUND. Here we verify the controller
        // faithfully propagates the service-thrown isolation error.
        ScanPickReqVO req = buildScanPickReq(ORDER_ID, TENANT_B, 10L, new BigDecimal("20"), 1);

        StockBusinessException notFound = new StockBusinessException(
                StockErrorCodeConstants.PRODUCTION_ORDER_NOT_FOUND);
        when(productionConsumptionService.scanPick(any(ScanPickReqVO.class)))
                .thenThrow(notFound);

        assertThatThrownBy(() -> controller.scanPick(req))
                .isInstanceOf(StockBusinessException.class)
                .isSameAs(notFound);

        // Confirm tenantId=B was actually forwarded to the service
        ArgumentCaptor<ScanPickReqVO> captor = ArgumentCaptor.forClass(ScanPickReqVO.class);
        verify(productionConsumptionService).scanPick(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT_B);
    }

    // =====================================================================
    // POST /app-api/ck-worker/production/scan-output
    // =====================================================================

    @Test
    void testScanOutputDelegatesToServiceAndPassesOrderIdTenantId() {
        ScanOutputReqVO req = buildScanOutputReq(ORDER_ID, TENANT_A, new BigDecimal("10"), 1);

        ProductionOrderOutputDO mockDO = buildOutputDO(ORDER_ID, TENANT_A);
        when(productionOutputService.scanOutput(any(ScanOutputReqVO.class)))
                .thenReturn(mockDO);

        CommonResult<ProductionOrderOutputRespVO> result = controller.scanOutput(req);

        assertThat(result.getCode()).isEqualTo(CommonResult.SUCCESS_CODE);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getId()).isEqualTo(mockDO.getId());
        assertThat(result.getData().getProductionOrderId()).isEqualTo(ORDER_ID);
        assertThat(result.getData().getTenantId()).isEqualTo(TENANT_A);

        ArgumentCaptor<ScanOutputReqVO> captor = ArgumentCaptor.forClass(ScanOutputReqVO.class);
        verify(productionOutputService).scanOutput(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(ORDER_ID);
        assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT_A);
        assertThat(captor.getValue().getActualOutputQty()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(captor.getValue().getOutputSeq()).isEqualTo(1);
    }

    @Test
    void testScanOutputErrorPropagates() {
        ScanOutputReqVO req = buildScanOutputReq(ORDER_ID, TENANT_A, new BigDecimal("10"), 1);

        StockBusinessException ex = new StockBusinessException(
                StockErrorCodeConstants.PRODUCTION_SCAN_NOT_ALLOWED);
        when(productionOutputService.scanOutput(any(ScanOutputReqVO.class)))
                .thenThrow(ex);

        assertThatThrownBy(() -> controller.scanOutput(req))
                .isInstanceOf(StockBusinessException.class)
                .isSameAs(ex);
    }

    @Test
    void testScanOutputTenantIsolationDelegatedToService() {
        ScanOutputReqVO req = buildScanOutputReq(ORDER_ID, TENANT_B, new BigDecimal("10"), 1);

        StockBusinessException notFound = new StockBusinessException(
                StockErrorCodeConstants.PRODUCTION_ORDER_NOT_FOUND);
        when(productionOutputService.scanOutput(any(ScanOutputReqVO.class)))
                .thenThrow(notFound);

        assertThatThrownBy(() -> controller.scanOutput(req))
                .isInstanceOf(StockBusinessException.class)
                .isSameAs(notFound);

        ArgumentCaptor<ScanOutputReqVO> captor = ArgumentCaptor.forClass(ScanOutputReqVO.class);
        verify(productionOutputService).scanOutput(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT_B);
    }

    // =====================================================================
    // GET /app-api/ck-worker/production/{id}/consumptions
    // =====================================================================

    @Test
    void testConsumptionsDelegatesAndReturnsList() {
        ProductionOrderConsumptionDO c1 = buildConsumptionDO(ORDER_ID, TENANT_A);
        c1.setId(1L);
        ProductionOrderConsumptionDO c2 = buildConsumptionDO(ORDER_ID, TENANT_A);
        c2.setId(2L);
        when(productionConsumptionService.listConsumptions(eq(ORDER_ID), eq(TENANT_A)))
                .thenReturn(List.of(c1, c2));

        CommonResult<List<ProductionOrderConsumptionRespVO>> result =
                controller.consumptions(ORDER_ID, TENANT_A);

        assertThat(result.getCode()).isEqualTo(CommonResult.SUCCESS_CODE);
        assertThat(result.getData()).hasSize(2);
        assertThat(result.getData().get(0).getId()).isEqualTo(1L);
        assertThat(result.getData().get(1).getId()).isEqualTo(2L);
        assertThat(result.getData()).allSatisfy(vo -> {
            assertThat(vo.getProductionOrderId()).isEqualTo(ORDER_ID);
            assertThat(vo.getTenantId()).isEqualTo(TENANT_A);
        });
        verify(productionConsumptionService).listConsumptions(eq(ORDER_ID), eq(TENANT_A));
    }

    @Test
    void testConsumptionsTenantIsolationDelegatedToService() {
        // Tenant B queries Tenant A's order consumptions — service returns empty
        // (or throws, depending on impl). Here we verify the controller forwards
        // tenantId=B and returns whatever the service yields.
        when(productionConsumptionService.listConsumptions(eq(ORDER_ID), eq(TENANT_B)))
                .thenReturn(List.of());

        CommonResult<List<ProductionOrderConsumptionRespVO>> result =
                controller.consumptions(ORDER_ID, TENANT_B);

        assertThat(result.getCode()).isEqualTo(CommonResult.SUCCESS_CODE);
        assertThat(result.getData()).isEmpty();
        verify(productionConsumptionService).listConsumptions(eq(ORDER_ID), eq(TENANT_B));
    }

    // =====================================================================
    // GET /app-api/ck-worker/production/{id}/outputs
    // =====================================================================

    @Test
    void testOutputsDelegatesAndReturnsList() {
        ProductionOrderOutputDO o1 = buildOutputDO(ORDER_ID, TENANT_A);
        o1.setId(1L);
        ProductionOrderOutputDO o2 = buildOutputDO(ORDER_ID, TENANT_A);
        o2.setId(2L);
        when(productionOutputService.listOutputs(eq(ORDER_ID), eq(TENANT_A)))
                .thenReturn(List.of(o1, o2));

        CommonResult<List<ProductionOrderOutputRespVO>> result =
                controller.outputs(ORDER_ID, TENANT_A);

        assertThat(result.getCode()).isEqualTo(CommonResult.SUCCESS_CODE);
        assertThat(result.getData()).hasSize(2);
        assertThat(result.getData().get(0).getId()).isEqualTo(1L);
        assertThat(result.getData().get(1).getId()).isEqualTo(2L);
        assertThat(result.getData()).allSatisfy(vo -> {
            assertThat(vo.getProductionOrderId()).isEqualTo(ORDER_ID);
            assertThat(vo.getTenantId()).isEqualTo(TENANT_A);
        });
        verify(productionOutputService).listOutputs(eq(ORDER_ID), eq(TENANT_A));
    }

    @Test
    void testOutputsTenantIsolationDelegatedToService() {
        when(productionOutputService.listOutputs(eq(ORDER_ID), eq(TENANT_B)))
                .thenReturn(List.of());

        CommonResult<List<ProductionOrderOutputRespVO>> result =
                controller.outputs(ORDER_ID, TENANT_B);

        assertThat(result.getCode()).isEqualTo(CommonResult.SUCCESS_CODE);
        assertThat(result.getData()).isEmpty();
        verify(productionOutputService).listOutputs(eq(ORDER_ID), eq(TENANT_B));
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private ScanPickReqVO buildScanPickReq(Long orderId, Long tenantId,
                                            Long componentProductId,
                                            BigDecimal actualQty, Integer pickSeq) {
        ScanPickReqVO req = new ScanPickReqVO();
        req.setOrderId(orderId);
        req.setTenantId(tenantId);
        req.setComponentProductId(componentProductId);
        req.setActualQty(actualQty);
        req.setPickSeq(pickSeq);
        req.setOperatorUserId(OPERATOR_ID);
        return req;
    }

    private ScanOutputReqVO buildScanOutputReq(Long orderId, Long tenantId,
                                                BigDecimal actualOutputQty, Integer outputSeq) {
        ScanOutputReqVO req = new ScanOutputReqVO();
        req.setOrderId(orderId);
        req.setTenantId(tenantId);
        req.setActualOutputQty(actualOutputQty);
        req.setOutputSeq(outputSeq);
        req.setOperatorUserId(OPERATOR_ID);
        return req;
    }

    private ProductionOrderConsumptionDO buildConsumptionDO(Long orderId, Long tenantId) {
        ProductionOrderConsumptionDO c = new ProductionOrderConsumptionDO();
        c.setId(99L);
        c.setTenantId(tenantId);
        c.setProductionOrderId(orderId);
        c.setInputSkuId(10L);
        c.setPickSeq(1);
        c.setPlannedQty(new BigDecimal("200.0000"));
        c.setActualQty(new BigDecimal("20.0000"));
        c.setDiffQty(new BigDecimal("-180.0000"));
        c.setDiffReason("test");
        c.setStockEventId(777L);
        c.setCreator("100");
        c.setCreateTime(LocalDateTime.now());
        return c;
    }

    private ProductionOrderOutputDO buildOutputDO(Long orderId, Long tenantId) {
        ProductionOrderOutputDO o = new ProductionOrderOutputDO();
        o.setId(88L);
        o.setTenantId(tenantId);
        o.setProductionOrderId(orderId);
        o.setOutputSkuId(20L);
        o.setOutputSeq(1);
        o.setActualOutputQty(new BigDecimal("10.0000"));
        o.setStockEventId(888L);
        o.setBatchNo("BATCH-001");
        o.setProducedTime(LocalDateTime.now());
        o.setExpireTime(LocalDateTime.now().plusDays(7));
        o.setCreator("100");
        o.setCreateTime(LocalDateTime.now());
        return o;
    }
}
