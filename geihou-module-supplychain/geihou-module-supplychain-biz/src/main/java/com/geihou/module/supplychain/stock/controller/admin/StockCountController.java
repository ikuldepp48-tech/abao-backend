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
import com.geihou.module.supplychain.stock.service.StockCountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Admin controller for stock count sessions (G2-02I-2).
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /admin/stock/count/session/create} — create count session</li>
 *   <li>{@code POST /admin/stock/count/session/start} — start count (PLANNING → IN_PROGRESS)</li>
 *   <li>{@code POST /admin/stock/count/record} — record count detail (INSERT complete record)</li>
 *   <li>{@code POST /admin/stock/count/submit} — submit count (IN_PROGRESS → DIFF_REVIEW)</li>
 *   <li>{@code POST /admin/stock/count/approve} — approve count (DIFF_REVIEW → ADJUSTED)</li>
 *   <li>{@code GET  /admin/stock/count/session/page} — paged query sessions</li>
 *   <li>{@code GET  /admin/stock/count/session/{id}} — get session detail</li>
 *   <li>{@code GET  /admin/stock/count/records/{sessionId}} — list records for session</li>
 * </ul>
 *
 * <p>No reject/cancel endpoints (PRD §3.1 does not define them).
 *
 * <p>Source: TASK-G2-02I-2.
 */
@RestController
@RequestMapping("/admin/stock/count")
public class StockCountController {

    @Autowired
    private StockCountService stockCountService;

    @PostMapping("/session/create")
    public CommonResult<StockCountSessionRespVO> createSession(@RequestBody StockCountSessionCreateReqVO req) {
        StockCountSessionDO session = stockCountService.createSession(req);
        return CommonResult.success(toSessionRespVO(session));
    }

    @PostMapping("/session/start")
    public CommonResult<StockCountSessionRespVO> startSession(@RequestBody StockCountSessionStartReqVO req) {
        StockCountSessionDO session = stockCountService.startCount(
                req.getSessionId(), req.getTenantId(), req.getOperatorUserId());
        return CommonResult.success(toSessionRespVO(session));
    }

    @PostMapping("/record")
    public CommonResult<StockCountRecordRespVO> record(@RequestBody StockCountRecordReqVO req) {
        StockCountRecordDO record = stockCountService.recordCount(
                req.getSessionId(), req.getTenantId(), req.getStockItemId(),
                req.getActualQty(), req.getDiffReason(), req.getEvidenceUrl(),
                req.getOperatorUserId());
        return CommonResult.success(toRecordRespVO(record));
    }

    @PostMapping("/submit")
    public CommonResult<StockCountSessionRespVO> submit(@RequestBody StockCountSubmitReqVO req) {
        StockCountSessionDO session = stockCountService.submitCount(
                req.getSessionId(), req.getTenantId(), req.getOperatorUserId());
        return CommonResult.success(toSessionRespVO(session));
    }

    @PostMapping("/approve")
    public CommonResult<StockCountSessionRespVO> approve(@RequestBody StockCountApproveReqVO req) {
        StockCountSessionDO session = stockCountService.approveCount(
                req.getSessionId(), req.getTenantId(), req.getApproverUserId());
        return CommonResult.success(toSessionRespVO(session));
    }

    @GetMapping("/session/page")
    public CommonResult<PageResult<StockCountSessionRespVO>> page(
            @RequestParam Long tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String countType,
            @RequestParam(required = false) Long locationId,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        PageResult<StockCountSessionDO> page = stockCountService.pageSession(
                tenantId, status, countType, locationId, pageNo, pageSize);
        List<StockCountSessionRespVO> voList = page.getList().stream()
                .map(this::toSessionRespVO)
                .collect(Collectors.toList());
        PageResult<StockCountSessionRespVO> voPage = PageResult.of(voList, page.getTotal(), page.getPageNo(), page.getPageSize());
        return CommonResult.success(voPage);
    }

    @GetMapping("/session/{id}")
    public CommonResult<StockCountSessionRespVO> getSession(@PathVariable("id") Long id,
                                                              @RequestParam Long tenantId) {
        StockCountSessionDO session = stockCountService.getSession(id, tenantId);
        return CommonResult.success(toSessionRespVO(session));
    }

    @GetMapping("/records/{sessionId}")
    public CommonResult<List<StockCountRecordRespVO>> listRecords(@PathVariable("sessionId") Long sessionId,
                                                                    @RequestParam Long tenantId) {
        List<StockCountRecordDO> records = stockCountService.listRecords(sessionId, tenantId);
        List<StockCountRecordRespVO> voList = records.stream()
                .map(this::toRecordRespVO)
                .collect(Collectors.toList());
        return CommonResult.success(voList);
    }

    // --- Helpers ---

    private StockCountSessionRespVO toSessionRespVO(StockCountSessionDO session) {
        if (session == null) {
            return null;
        }
        StockCountSessionRespVO vo = new StockCountSessionRespVO();
        vo.setId(session.getId());
        vo.setTenantId(session.getTenantId());
        vo.setSessionCode(session.getSessionCode());
        vo.setLocationId(session.getLocationId());
        vo.setCountType(session.getCountType());
        vo.setScheduledTime(session.getScheduledTime());
        vo.setStartTime(session.getStartTime());
        vo.setEndTime(session.getEndTime());
        vo.setStatus(session.getStatus());
        vo.setTotalItems(session.getTotalItems());
        vo.setDiffItems(session.getDiffItems());
        vo.setTotalDiffValue(session.getTotalDiffValue());
        vo.setOperatorUserId(session.getOperatorUserId());
        vo.setApproverUserId(session.getApproverUserId());
        vo.setApproveTime(session.getApproveTime());
        vo.setCreateTime(session.getCreateTime());
        vo.setUpdateTime(session.getUpdateTime());
        return vo;
    }

    private StockCountRecordRespVO toRecordRespVO(StockCountRecordDO record) {
        if (record == null) {
            return null;
        }
        StockCountRecordRespVO vo = new StockCountRecordRespVO();
        vo.setId(record.getId());
        vo.setTenantId(record.getTenantId());
        vo.setSessionId(record.getSessionId());
        vo.setStockItemId(record.getStockItemId());
        vo.setSystemQty(record.getSystemQty());
        vo.setActualQty(record.getActualQty());
        vo.setDiffQty(record.getDiffQty());
        vo.setDiffReason(record.getDiffReason());
        vo.setEvidenceUrl(record.getEvidenceUrl());
        vo.setAdjustmentEventId(record.getAdjustmentEventId());
        vo.setCreateTime(record.getCreateTime());
        return vo;
    }
}
