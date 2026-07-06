package com.geihou.module.supplychain.stock.controller.admin;

import com.geihou.common.pojo.CommonResult;
import com.geihou.common.pojo.PageResult;
import com.geihou.module.supplychain.stock.controller.admin.vo.StockLossApproveReqVO;
import com.geihou.module.supplychain.stock.controller.admin.vo.StockLossCancelReqVO;
import com.geihou.module.supplychain.stock.controller.admin.vo.StockLossCreateReqVO;
import com.geihou.module.supplychain.stock.controller.admin.vo.StockLossRejectReqVO;
import com.geihou.module.supplychain.stock.controller.admin.vo.StockLossRespVO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockLossDO;
import com.geihou.module.supplychain.stock.service.StockLossService;
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
 * Admin controller for stock loss/scrap records (G2-02I-3).
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /admin/stock/loss/create} — create loss/scrap record</li>
 *   <li>{@code POST /admin/stock/loss/approve} — approve pending record</li>
 *   <li>{@code POST /admin/stock/loss/reject} — reject pending record</li>
 *   <li>{@code POST /admin/stock/loss/cancel} — cancel pending record</li>
 *   <li>{@code GET  /admin/stock/loss/page} — paged query</li>
 *   <li>{@code GET  /admin/stock/loss/{id}} — get by ID</li>
 * </ul>
 *
 * <p>Source: TASK-G2-02I-3.
 */
@RestController
@RequestMapping("/admin/stock/loss")
public class StockLossController {

    @Autowired
    private StockLossService stockLossService;

    @PostMapping("/create")
    public CommonResult<StockLossRespVO> create(@RequestBody StockLossCreateReqVO req) {
        StockLossDO loss = stockLossService.createLoss(req);
        return CommonResult.success(toRespVO(loss));
    }

    @PostMapping("/approve")
    public CommonResult<StockLossRespVO> approve(@RequestBody StockLossApproveReqVO req) {
        StockLossDO loss = stockLossService.approveLoss(req.getLossId(), req.getTenantId(), req.getApproverUserId());
        return CommonResult.success(toRespVO(loss));
    }

    @PostMapping("/reject")
    public CommonResult<StockLossRespVO> reject(@RequestBody StockLossRejectReqVO req) {
        StockLossDO loss = stockLossService.rejectLoss(req.getLossId(), req.getTenantId(),
                req.getApproverUserId(), req.getRejectReason());
        return CommonResult.success(toRespVO(loss));
    }

    @PostMapping("/cancel")
    public CommonResult<StockLossRespVO> cancel(@RequestBody StockLossCancelReqVO req) {
        StockLossDO loss = stockLossService.cancelLoss(req.getLossId(), req.getTenantId(), req.getOperatorUserId());
        return CommonResult.success(toRespVO(loss));
    }

    @GetMapping("/page")
    public CommonResult<PageResult<StockLossRespVO>> page(
            @RequestParam Long tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        PageResult<StockLossDO> page = stockLossService.pageLoss(tenantId, status, pageNo, pageSize);
        List<StockLossRespVO> voList = page.getList().stream()
                .map(this::toRespVO)
                .collect(Collectors.toList());
        PageResult<StockLossRespVO> voPage = PageResult.of(voList, page.getTotal(), page.getPageNo(), page.getPageSize());
        return CommonResult.success(voPage);
    }

    @GetMapping("/{id}")
    public CommonResult<StockLossRespVO> get(@PathVariable("id") Long id,
                                              @RequestParam Long tenantId) {
        StockLossDO loss = stockLossService.getLoss(id, tenantId);
        return CommonResult.success(toRespVO(loss));
    }

    // --- Helper ---

    private StockLossRespVO toRespVO(StockLossDO loss) {
        if (loss == null) {
            return null;
        }
        StockLossRespVO vo = new StockLossRespVO();
        vo.setId(loss.getId());
        vo.setTenantId(loss.getTenantId());
        vo.setLossNo(loss.getLossNo());
        vo.setLossType(loss.getLossType());
        vo.setStockItemId(loss.getStockItemId());
        vo.setSkuCode(loss.getSkuCode());
        vo.setLocationId(loss.getLocationId());
        vo.setQuantity(loss.getQuantity());
        vo.setUnit(loss.getUnit());
        vo.setUnitCost(loss.getUnitCost());
        vo.setTotalAmount(loss.getTotalAmount());
        vo.setLossReason(loss.getLossReason());
        vo.setRemark(loss.getRemark());
        vo.setStatus(loss.getStatus());
        vo.setApproverUserId(loss.getApproverUserId());
        vo.setApproveTime(loss.getApproveTime());
        vo.setRejectReason(loss.getRejectReason());
        vo.setStockEventId(loss.getStockEventId());
        vo.setOperatorUserId(loss.getOperatorUserId());
        vo.setCreateTime(loss.getCreateTime());
        vo.setUpdateTime(loss.getUpdateTime());
        return vo;
    }
}
