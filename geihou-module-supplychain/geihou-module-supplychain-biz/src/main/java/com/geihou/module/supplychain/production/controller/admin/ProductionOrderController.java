package com.geihou.module.supplychain.production.controller.admin;

import com.geihou.common.pojo.CommonResult;
import com.geihou.common.pojo.PageResult;
import com.geihou.module.supplychain.production.controller.admin.vo.ProductionOrderConsumptionRespVO;
import com.geihou.module.supplychain.production.controller.admin.vo.ProductionOrderCreateReqVO;
import com.geihou.module.supplychain.production.controller.admin.vo.ProductionOrderOutputRespVO;
import com.geihou.module.supplychain.production.controller.admin.vo.ProductionOrderRespVO;
import com.geihou.module.supplychain.production.controller.admin.vo.ProductionOrderStageTransitionReqVO;
import com.geihou.module.supplychain.production.controller.admin.vo.ProductionOrderUpdateReqVO;
import com.geihou.module.supplychain.production.controller.admin.vo.ScanOutputReqVO;
import com.geihou.module.supplychain.production.controller.admin.vo.ScanPickReqVO;
import com.geihou.module.supplychain.production.dal.dataobject.ProductionOrderConsumptionDO;
import com.geihou.module.supplychain.production.dal.dataobject.ProductionOrderDO;
import com.geihou.module.supplychain.production.dal.dataobject.ProductionOrderOutputDO;
import com.geihou.module.supplychain.production.service.ProductionConsumptionService;
import com.geihou.module.supplychain.production.service.ProductionOrderService;
import com.geihou.module.supplychain.production.service.ProductionOutputService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Admin controller for production orders (G2-02K).
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST   /admin-api/supplychain/production/orders} — create production order</li>
 *   <li>{@code GET    /admin-api/supplychain/production/orders/{id}} — get by ID</li>
 *   <li>{@code GET    /admin-api/supplychain/production/orders/page} — paged query</li>
 *   <li>{@code PUT    /admin-api/supplychain/production/orders/{id}} — update (CREATED only)</li>
 *   <li>{@code POST   /admin-api/supplychain/production/orders/{id}/stage-transition} — stage transition</li>
 * </ul>
 *
 * <p>Path deviation from PRD §3.2 ({@code /admin-api/ck/production/orders}):
 * uses {@code /admin-api/supplychain/production/orders} to align with existing
 * supplychain controllers (BomExplodeController, BomCostPreviewController).
 *
 * <p>Source: TASK-G2-02K.
 */
@RestController
@RequestMapping("/admin-api/supplychain/production/orders")
public class ProductionOrderController {

    @Autowired
    private ProductionOrderService productionOrderService;

    @Autowired
    private ProductionConsumptionService productionConsumptionService;

    @Autowired
    private ProductionOutputService productionOutputService;

    /** 创建生产工单 */
    @PostMapping
    public CommonResult<ProductionOrderRespVO> create(@RequestBody ProductionOrderCreateReqVO req) {
        ProductionOrderDO order = productionOrderService.createProductionOrder(req);
        return CommonResult.success(toRespVO(order));
    }

    /** 查询生产工单详情 */
    @GetMapping("/{id}")
    public CommonResult<ProductionOrderRespVO> get(@PathVariable("id") Long id,
                                                    @RequestParam Long tenantId) {
        ProductionOrderDO order = productionOrderService.getProductionOrder(id, tenantId);
        return CommonResult.success(toRespVO(order));
    }

    /** 分页查询生产工单列表 */
    @GetMapping("/page")
    public CommonResult<PageResult<ProductionOrderRespVO>> page(
            @RequestParam Long tenantId,
            @RequestParam(required = false) String productionStage,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        PageResult<ProductionOrderDO> page = productionOrderService.listProductionOrders(
                tenantId, productionStage, pageNo, pageSize);
        List<ProductionOrderRespVO> voList = page.getList().stream()
                .map(this::toRespVO)
                .collect(Collectors.toList());
        PageResult<ProductionOrderRespVO> voPage = PageResult.of(voList, page.getTotal(),
                page.getPageNo(), page.getPageSize());
        return CommonResult.success(voPage);
    }

    /** 更新生产工单（仅 CREATED 状态可更新） */
    @PutMapping("/{id}")
    public CommonResult<ProductionOrderRespVO> update(@PathVariable("id") Long id,
                                                       @RequestBody ProductionOrderUpdateReqVO req) {
        req.setId(id);
        ProductionOrderDO order = productionOrderService.updateProductionOrder(req);
        return CommonResult.success(toRespVO(order));
    }

    /** 状态流转 */
    @PostMapping("/{id}/stage-transition")
    public CommonResult<ProductionOrderRespVO> transitionStage(
            @PathVariable("id") Long id,
            @RequestBody ProductionOrderStageTransitionReqVO req) {
        req.setId(id);
        ProductionOrderDO order = productionOrderService.transitionStage(req);
        return CommonResult.success(toRespVO(order));
    }

    // --- G2-02N: scan-pick / scan-output / consumptions / outputs ---

    /** 领料记录 (scan-pick) */
    @PostMapping("/{id}/scan-pick")
    public CommonResult<ProductionOrderConsumptionRespVO> scanPick(
            @PathVariable("id") Long id,
            @RequestBody ScanPickReqVO req) {
        req.setOrderId(id);
        ProductionOrderConsumptionDO result = productionConsumptionService.scanPick(req);
        return CommonResult.success(toConsumptionRespVO(result));
    }

    /** 产出登记 (scan-output) */
    @PostMapping("/{id}/scan-output")
    public CommonResult<ProductionOrderOutputRespVO> scanOutput(
            @PathVariable("id") Long id,
            @RequestBody ScanOutputReqVO req) {
        req.setOrderId(id);
        ProductionOrderOutputDO result = productionOutputService.scanOutput(req);
        return CommonResult.success(toOutputRespVO(result));
    }

    /** 查询工单领料记录列表 */
    @GetMapping("/{id}/consumptions")
    public CommonResult<List<ProductionOrderConsumptionRespVO>> consumptions(
            @PathVariable("id") Long id,
            @RequestParam Long tenantId) {
        List<ProductionOrderConsumptionDO> list = productionConsumptionService.listConsumptions(id, tenantId);
        List<ProductionOrderConsumptionRespVO> voList = list.stream()
                .map(this::toConsumptionRespVO)
                .collect(Collectors.toList());
        return CommonResult.success(voList);
    }

    /** 查询工单产出记录列表 */
    @GetMapping("/{id}/outputs")
    public CommonResult<List<ProductionOrderOutputRespVO>> outputs(
            @PathVariable("id") Long id,
            @RequestParam Long tenantId) {
        List<ProductionOrderOutputDO> list = productionOutputService.listOutputs(id, tenantId);
        List<ProductionOrderOutputRespVO> voList = list.stream()
                .map(this::toOutputRespVO)
                .collect(Collectors.toList());
        return CommonResult.success(voList);
    }

    // --- Helper ---

    private ProductionOrderRespVO toRespVO(ProductionOrderDO order) {
        if (order == null) {
            return null;
        }
        ProductionOrderRespVO vo = new ProductionOrderRespVO();
        vo.setId(order.getId());
        vo.setTenantId(order.getTenantId());
        vo.setOrderNo(order.getOrderNo());
        vo.setProductId(order.getProductId());
        vo.setRecipeId(order.getRecipeId());
        vo.setLocationId(order.getLocationId());
        vo.setPlannedQty(order.getPlannedQty());
        vo.setActualQty(order.getActualQty());
        vo.setProductionStage(order.getProductionStage());
        vo.setPlanStartTime(order.getPlanStartTime());
        vo.setPlanEndTime(order.getPlanEndTime());
        vo.setActualStartTime(order.getActualStartTime());
        vo.setActualEndTime(order.getActualEndTime());
        vo.setOperatorUserId(order.getOperatorUserId());
        vo.setRemark(order.getRemark());
        vo.setReworkCount(order.getReworkCount());
        vo.setQualityCheckResult(order.getQualityCheckResult());
        vo.setQualityCheckRemark(order.getQualityCheckRemark());
        vo.setQualityCheckedBy(order.getQualityCheckedBy());
        vo.setQualityCheckedTime(order.getQualityCheckedTime());
        vo.setCreator(order.getCreator());
        vo.setCreateTime(order.getCreateTime());
        vo.setUpdater(order.getUpdater());
        vo.setUpdateTime(order.getUpdateTime());
        return vo;
    }

    private ProductionOrderConsumptionRespVO toConsumptionRespVO(ProductionOrderConsumptionDO c) {
        if (c == null) {
            return null;
        }
        ProductionOrderConsumptionRespVO vo = new ProductionOrderConsumptionRespVO();
        vo.setId(c.getId());
        vo.setTenantId(c.getTenantId());
        vo.setProductionOrderId(c.getProductionOrderId());
        vo.setInputSkuId(c.getInputSkuId());
        vo.setPickSeq(c.getPickSeq());
        vo.setPlannedQty(c.getPlannedQty());
        vo.setActualQty(c.getActualQty());
        vo.setDiffQty(c.getDiffQty());
        vo.setDiffReason(c.getDiffReason());
        vo.setStockEventId(c.getStockEventId());
        vo.setCreator(c.getCreator());
        vo.setCreateTime(c.getCreateTime());
        return vo;
    }

    private ProductionOrderOutputRespVO toOutputRespVO(ProductionOrderOutputDO o) {
        if (o == null) {
            return null;
        }
        ProductionOrderOutputRespVO vo = new ProductionOrderOutputRespVO();
        vo.setId(o.getId());
        vo.setTenantId(o.getTenantId());
        vo.setProductionOrderId(o.getProductionOrderId());
        vo.setOutputSkuId(o.getOutputSkuId());
        vo.setOutputSeq(o.getOutputSeq());
        vo.setActualOutputQty(o.getActualOutputQty());
        vo.setStockEventId(o.getStockEventId());
        vo.setBatchNo(o.getBatchNo());
        vo.setProducedTime(o.getProducedTime());
        vo.setExpireTime(o.getExpireTime());
        vo.setCreator(o.getCreator());
        vo.setCreateTime(o.getCreateTime());
        return vo;
    }
}
