package com.geihou.module.supplychain.stock.controller.admin;

import com.geihou.common.pojo.CommonResult;
import com.geihou.common.pojo.PageResult;
import com.geihou.module.supplychain.stock.controller.admin.vo.StockCountApproveReqVO;
import com.geihou.module.supplychain.stock.controller.admin.vo.StockCountRecordReqVO;
import com.geihou.module.supplychain.stock.controller.admin.vo.StockCountRecordRespVO;
import com.geihou.module.supplychain.stock.controller.admin.vo.StockCountSessionCreateReqVO;
import com.geihou.module.supplychain.stock.controller.admin.vo.StockCountSessionRespVO;
import com.geihou.module.supplychain.stock.controller.admin.vo.StockCountSessionStartReqVO;
import com.geihou.module.supplychain.stock.controller.admin.vo.StockCountSubmitReqVO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockCountRecordDO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockCountSessionDO;
import com.geihou.module.supplychain.stock.framework.StockBusinessException;
import com.geihou.module.supplychain.stock.framework.StockErrorCodeConstants;
import com.geihou.module.supplychain.stock.service.StockCountService;
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
 * Lightweight unit tests for {@link StockCountController}.
 *
 * <p>Uses Mockito mocks — no Spring context, no DB.
 *
 * <p>Source: TASK-G2-02I-2 §9.1.
 */
@ExtendWith(MockitoExtension.class)
class StockCountControllerTest {

    @Mock
    private StockCountService stockCountService;

    @InjectMocks
    private StockCountController controller;

    @Test
    void createSession_delegatesToService_andWrapsInCommonResult() {
        StockCountSessionCreateReqVO req = new StockCountSessionCreateReqVO();
        req.setTenantId(1L);
        req.setLocationId(10L);
        req.setCountType("FULL");
        req.setOperatorUserId(100L);

        StockCountSessionDO mockSession = buildMockSession();
        when(stockCountService.createSession(any(StockCountSessionCreateReqVO.class))).thenReturn(mockSession);

        CommonResult<StockCountSessionRespVO> result = controller.createSession(req);

        assertThat(result.getCode()).isEqualTo(CommonResult.SUCCESS_CODE);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getSessionCode()).isEqualTo("COUNT-1-001");
        assertThat(result.getData().getStatus()).isEqualTo("PLANNING");
        verify(stockCountService).createSession(eq(req));
    }

    @Test
    void startSession_delegatesToService_andWrapsInCommonResult() {
        StockCountSessionStartReqVO req = new StockCountSessionStartReqVO();
        req.setSessionId(1L);
        req.setTenantId(1L);
        req.setOperatorUserId(100L);

        StockCountSessionDO mockSession = buildMockSession();
        mockSession.setStatus("IN_PROGRESS");
        when(stockCountService.startCount(eq(1L), eq(1L), eq(100L))).thenReturn(mockSession);

        CommonResult<StockCountSessionRespVO> result = controller.startSession(req);

        assertThat(result.getCode()).isEqualTo(CommonResult.SUCCESS_CODE);
        assertThat(result.getData().getStatus()).isEqualTo("IN_PROGRESS");
        verify(stockCountService).startCount(eq(1L), eq(1L), eq(100L));
    }

    @Test
    void record_delegatesToService_andWrapsInCommonResult() {
        StockCountRecordReqVO req = new StockCountRecordReqVO();
        req.setSessionId(1L);
        req.setTenantId(1L);
        req.setStockItemId(1001L);
        req.setActualQty(new BigDecimal("95"));
        req.setDiffReason("这是一条差异原因说明，长度大于三十个字符。");
        req.setOperatorUserId(100L);

        StockCountRecordDO mockRecord = buildMockRecord();
        when(stockCountService.recordCount(eq(1L), eq(1L), eq(1001L),
                eq(new BigDecimal("95")), eq("这是一条差异原因说明，长度大于三十个字符。"),
                eq(null), eq(100L))).thenReturn(mockRecord);

        CommonResult<StockCountRecordRespVO> result = controller.record(req);

        assertThat(result.getCode()).isEqualTo(CommonResult.SUCCESS_CODE);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getActualQty()).isEqualByComparingTo(new BigDecimal("95"));
        verify(stockCountService).recordCount(eq(1L), eq(1L), eq(1001L),
                eq(new BigDecimal("95")), eq("这是一条差异原因说明，长度大于三十个字符。"),
                eq(null), eq(100L));
    }

    @Test
    void submit_delegatesToService_andWrapsInCommonResult() {
        StockCountSubmitReqVO req = new StockCountSubmitReqVO();
        req.setSessionId(1L);
        req.setTenantId(1L);
        req.setOperatorUserId(100L);

        StockCountSessionDO mockSession = buildMockSession();
        mockSession.setStatus("DIFF_REVIEW");
        when(stockCountService.submitCount(eq(1L), eq(1L), eq(100L))).thenReturn(mockSession);

        CommonResult<StockCountSessionRespVO> result = controller.submit(req);

        assertThat(result.getCode()).isEqualTo(CommonResult.SUCCESS_CODE);
        assertThat(result.getData().getStatus()).isEqualTo("DIFF_REVIEW");
        verify(stockCountService).submitCount(eq(1L), eq(1L), eq(100L));
    }

    @Test
    void approve_delegatesToService_andWrapsInCommonResult() {
        StockCountApproveReqVO req = new StockCountApproveReqVO();
        req.setSessionId(1L);
        req.setTenantId(1L);
        req.setApproverUserId(200L);

        StockCountSessionDO mockSession = buildMockSession();
        mockSession.setStatus("ADJUSTED");
        when(stockCountService.approveCount(eq(1L), eq(1L), eq(200L))).thenReturn(mockSession);

        CommonResult<StockCountSessionRespVO> result = controller.approve(req);

        assertThat(result.getCode()).isEqualTo(CommonResult.SUCCESS_CODE);
        assertThat(result.getData().getStatus()).isEqualTo("ADJUSTED");
        verify(stockCountService).approveCount(eq(1L), eq(1L), eq(200L));
    }

    @Test
    void page_delegatesToService_andWrapsInCommonResult() {
        StockCountSessionDO mockSession = buildMockSession();
        PageResult<StockCountSessionDO> mockPage = PageResult.of(List.of(mockSession), 1L, 1, 20);
        when(stockCountService.pageSession(eq(1L), eq("PLANNING"), eq(null), eq(null), eq(1), eq(20)))
                .thenReturn(mockPage);

        CommonResult<PageResult<StockCountSessionRespVO>> result = controller.page(
                1L, "PLANNING", null, null, 1, 20);

        assertThat(result.getCode()).isEqualTo(CommonResult.SUCCESS_CODE);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getList()).hasSize(1);
        assertThat(result.getData().getTotal()).isEqualTo(1L);
        verify(stockCountService).pageSession(eq(1L), eq("PLANNING"), eq(null), eq(null), eq(1), eq(20));
    }

    @Test
    void getSession_notFound_returnsNullDataInCommonResult() {
        when(stockCountService.getSession(eq(999L), eq(1L))).thenReturn(null);

        CommonResult<StockCountSessionRespVO> result = controller.getSession(999L, 1L);

        assertThat(result.getCode()).isEqualTo(CommonResult.SUCCESS_CODE);
        assertThat(result.getData()).isNull();
    }

    @Test
    void createSession_exceptionPropagates_notSwallowed() {
        StockCountSessionCreateReqVO req = new StockCountSessionCreateReqVO();
        req.setTenantId(1L);

        StockBusinessException ex = new StockBusinessException(
                StockErrorCodeConstants.REQUIRED_FIELD_MISSING);
        when(stockCountService.createSession(any(StockCountSessionCreateReqVO.class))).thenThrow(ex);

        assertThatThrownBy(() -> controller.createSession(req))
                .isInstanceOf(StockBusinessException.class)
                .isSameAs(ex);
    }

    // --- Helpers ---

    private StockCountSessionDO buildMockSession() {
        StockCountSessionDO session = new StockCountSessionDO();
        session.setId(1L);
        session.setTenantId(1L);
        session.setSessionCode("COUNT-1-001");
        session.setLocationId(10L);
        session.setCountType("FULL");
        session.setStatus("PLANNING");
        session.setOperatorUserId(100L);
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        return session;
    }

    private StockCountRecordDO buildMockRecord() {
        StockCountRecordDO record = new StockCountRecordDO();
        record.setId(1L);
        record.setTenantId(1L);
        record.setSessionId(1L);
        record.setStockItemId(1001L);
        record.setSystemQty(new BigDecimal("100.0000"));
        record.setActualQty(new BigDecimal("95.0000"));
        record.setDiffQty(new BigDecimal("-5.0000"));
        record.setDiffReason("这是一条差异原因说明，长度大于三十个字符。");
        record.setCreateTime(LocalDateTime.now());
        return record;
    }
}
