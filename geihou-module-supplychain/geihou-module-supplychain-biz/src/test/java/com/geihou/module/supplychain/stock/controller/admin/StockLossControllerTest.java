package com.geihou.module.supplychain.stock.controller.admin;

import com.geihou.common.pojo.CommonResult;
import com.geihou.common.pojo.PageResult;
import com.geihou.module.supplychain.stock.controller.admin.vo.StockLossApproveReqVO;
import com.geihou.module.supplychain.stock.controller.admin.vo.StockLossCancelReqVO;
import com.geihou.module.supplychain.stock.controller.admin.vo.StockLossCreateReqVO;
import com.geihou.module.supplychain.stock.controller.admin.vo.StockLossRejectReqVO;
import com.geihou.module.supplychain.stock.controller.admin.vo.StockLossRespVO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockLossDO;
import com.geihou.module.supplychain.stock.framework.StockBusinessException;
import com.geihou.module.supplychain.stock.framework.StockErrorCodeConstants;
import com.geihou.module.supplychain.stock.service.StockLossService;
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
 * Lightweight unit tests for {@link StockLossController}.
 *
 * <p>Uses Mockito mocks — no Spring context, no DB.
 *
 * <p>Source: TASK-G2-02I-3 Section 7.1.
 */
@ExtendWith(MockitoExtension.class)
class StockLossControllerTest {

    @Mock
    private StockLossService stockLossService;

    @InjectMocks
    private StockLossController controller;

    @Test
    void create_delegatesToService_andWrapsInCommonResult() {
        StockLossCreateReqVO req = new StockLossCreateReqVO();
        req.setTenantId(1L);
        req.setLossType("LOSS");
        req.setStockItemId(1001L);
        req.setSkuCode("SKU_TEST");
        req.setLocationId(10L);
        req.setQuantity(new BigDecimal("5"));
        req.setUnit("KG");
        req.setLossReason("EXPIRY");
        req.setOperatorUserId(100L);

        StockLossDO mockLoss = buildMockLoss();
        when(stockLossService.createLoss(any(StockLossCreateReqVO.class))).thenReturn(mockLoss);

        CommonResult<StockLossRespVO> result = controller.create(req);

        assertThat(result.getCode()).isEqualTo(CommonResult.SUCCESS_CODE);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getLossNo()).isEqualTo("LOSS-1-001");
        assertThat(result.getData().getStatus()).isEqualTo("APPROVED");
        verify(stockLossService).createLoss(eq(req));
    }

    @Test
    void approve_delegatesToService_andWrapsInCommonResult() {
        StockLossApproveReqVO req = new StockLossApproveReqVO();
        req.setLossId(1L);
        req.setTenantId(1L);
        req.setApproverUserId(200L);

        StockLossDO mockLoss = buildMockLoss();
        mockLoss.setStatus("APPROVED");
        when(stockLossService.approveLoss(eq(1L), eq(1L), eq(200L))).thenReturn(mockLoss);

        CommonResult<StockLossRespVO> result = controller.approve(req);

        assertThat(result.getCode()).isEqualTo(CommonResult.SUCCESS_CODE);
        assertThat(result.getData().getStatus()).isEqualTo("APPROVED");
        verify(stockLossService).approveLoss(eq(1L), eq(1L), eq(200L));
    }

    @Test
    void reject_delegatesToService_andWrapsInCommonResult() {
        StockLossRejectReqVO req = new StockLossRejectReqVO();
        req.setLossId(1L);
        req.setTenantId(1L);
        req.setApproverUserId(200L);
        req.setRejectReason("not enough evidence");

        StockLossDO mockLoss = buildMockLoss();
        mockLoss.setStatus("REJECTED");
        when(stockLossService.rejectLoss(eq(1L), eq(1L), eq(200L), eq("not enough evidence")))
                .thenReturn(mockLoss);

        CommonResult<StockLossRespVO> result = controller.reject(req);

        assertThat(result.getCode()).isEqualTo(CommonResult.SUCCESS_CODE);
        assertThat(result.getData().getStatus()).isEqualTo("REJECTED");
        verify(stockLossService).rejectLoss(eq(1L), eq(1L), eq(200L), eq("not enough evidence"));
    }

    @Test
    void page_delegatesToService_andWrapsInCommonResult() {
        StockLossDO mockLoss = buildMockLoss();
        PageResult<StockLossDO> mockPage = PageResult.of(List.of(mockLoss), 1L, 1, 20);
        when(stockLossService.pageLoss(eq(1L), eq("APPROVED"), eq(1), eq(20)))
                .thenReturn(mockPage);

        CommonResult<PageResult<StockLossRespVO>> result = controller.page(1L, "APPROVED", 1, 20);

        assertThat(result.getCode()).isEqualTo(CommonResult.SUCCESS_CODE);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getList()).hasSize(1);
        assertThat(result.getData().getTotal()).isEqualTo(1L);
        assertThat(result.getData().getList().get(0).getLossNo()).isEqualTo("LOSS-1-001");
        verify(stockLossService).pageLoss(eq(1L), eq("APPROVED"), eq(1), eq(20));
    }

    @Test
    void getDetail_delegatesToService_andWrapsInCommonResult() {
        StockLossDO mockLoss = buildMockLoss();
        when(stockLossService.getLoss(eq(1L), eq(1L))).thenReturn(mockLoss);

        CommonResult<StockLossRespVO> result = controller.get(1L, 1L);

        assertThat(result.getCode()).isEqualTo(CommonResult.SUCCESS_CODE);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getId()).isEqualTo(1L);
        verify(stockLossService).getLoss(eq(1L), eq(1L));
    }

    @Test
    void getDetail_notFound_returnsNullDataInCommonResult() {
        when(stockLossService.getLoss(eq(999L), eq(1L))).thenReturn(null);

        CommonResult<StockLossRespVO> result = controller.get(999L, 1L);

        assertThat(result.getCode()).isEqualTo(CommonResult.SUCCESS_CODE);
        assertThat(result.getData()).isNull();
    }

    @Test
    void create_exceptionPropagates_notSwallowed() {
        StockLossCreateReqVO req = new StockLossCreateReqVO();
        req.setTenantId(1L);

        StockBusinessException ex = new StockBusinessException(
                StockErrorCodeConstants.QUANTITY_MUST_BE_POSITIVE);
        when(stockLossService.createLoss(any(StockLossCreateReqVO.class))).thenThrow(ex);

        assertThatThrownBy(() -> controller.create(req))
                .isInstanceOf(StockBusinessException.class)
                .isSameAs(ex);
    }

    private StockLossDO buildMockLoss() {
        StockLossDO loss = new StockLossDO();
        loss.setId(1L);
        loss.setTenantId(1L);
        loss.setLossNo("LOSS-1-001");
        loss.setLossType("LOSS");
        loss.setStockItemId(1001L);
        loss.setSkuCode("SKU_TEST");
        loss.setLocationId(10L);
        loss.setQuantity(new BigDecimal("5.0000"));
        loss.setUnit("KG");
        loss.setUnitCost(new BigDecimal("10.0000"));
        loss.setTotalAmount(new BigDecimal("50.0000"));
        loss.setLossReason("EXPIRY");
        loss.setStatus("APPROVED");
        loss.setApproverUserId(100L);
        loss.setApproveTime(LocalDateTime.now());
        loss.setStockEventId(5001L);
        loss.setOperatorUserId(100L);
        loss.setCreateTime(LocalDateTime.now());
        loss.setUpdateTime(LocalDateTime.now());
        return loss;
    }
}
