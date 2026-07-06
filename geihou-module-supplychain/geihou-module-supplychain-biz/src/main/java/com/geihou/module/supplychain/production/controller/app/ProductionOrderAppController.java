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
 * ck-worker app-api controller for production orders (G2-02P).
 *
 * <p>Exposes the scan-pick / scan-output / consumptions / outputs endpoints
 * under the {@code /app-api/ck-worker/production} path prefix, reusing the
 * G2-02N service layer ({@link ProductionConsumptionService},
 * {@link ProductionOutputService}) and the admin VOs.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /app-api/ck-worker/production/scan-pick} — 领料记录</li>
 *   <li>{@code POST /app-api/ck-worker/production/scan-output} — 产出登记</li>
 *   <li>{@code GET  /app-api/ck-worker/production/{id}/consumptions} — 工单领料记录列表</li>
 *   <li>{@code GET  /app-api/ck-worker/production/{id}/outputs} — 工单产出记录列表</li>
 * </ul>
 *
 * <p><b>租户策略（过渡）</b>：当前项目尚未形成统一的 ck-worker app-api
 * 从 token 提取 {@code tenantId} 的成熟范式。本切片采用保守过渡策略：
 * <ul>
 *   <li>POST 端点沿用请求体 VO 中的显式 {@code tenantId} 字段；</li>
 *   <li>GET 端点使用 {@code tenantId} 查询参数。</li>
 * </ul>
 * 这 <b>不是</b> 完整的 C5 鉴权闭环；真实 ck-worker token scope/角色/权限
 * 需在后续鉴权切片中补齐。租户隔离的实际校验由 service 层完成
 * （order.tenantId 必须匹配 req.tenantId，否则抛
 * {@code PRODUCTION_ORDER_NOT_FOUND}）。
 *
 * <p>本 controller 不引入任何 service 业务逻辑变更，仅做端点暴露与
 * DO→VO 转换，行为与 admin controller 等价。
 *
 * <p>Source: TASK-G2-02P.
 */
@RestController
@RequestMapping("/app-api/ck-worker/production")
public class ProductionOrderAppController {

    @Autowired
    private ProductionConsumptionService productionConsumptionService;

    @Autowired
    private ProductionOutputService productionOutputService;

    /**
     * 领料记录 (scan-pick).
     *
     * <p>请求体需携带 {@code orderId} 与 {@code tenantId}。
     * 委托 {@link ProductionConsumptionService#scanPick(ScanPickReqVO)} 执行，
     * 业务异常（{@code StockBusinessException}）原样向上传播，符合现有 controller 风格。
     *
     * @param req scan-pick 请求（orderId/tenantId 由调用方提供）
     * @return 创建的领料记录
     */
    @PostMapping("/scan-pick")
    public CommonResult<ProductionOrderConsumptionRespVO> scanPick(
            @RequestBody ScanPickReqVO req) {
        ProductionOrderConsumptionDO result = productionConsumptionService.scanPick(req);
        return CommonResult.success(toConsumptionRespVO(result));
    }

    /**
     * 产出登记 (scan-output).
     *
     * <p>请求体需携带 {@code orderId} 与 {@code tenantId}。
     * 委托 {@link ProductionOutputService#scanOutput(ScanOutputReqVO)} 执行，
     * 业务异常（{@code StockBusinessException}）原样向上传播。
     *
     * @param req scan-output 请求（orderId/tenantId 由调用方提供）
     * @return 创建的产出记录
     */
    @PostMapping("/scan-output")
    public CommonResult<ProductionOrderOutputRespVO> scanOutput(
            @RequestBody ScanOutputReqVO req) {
        ProductionOrderOutputDO result = productionOutputService.scanOutput(req);
        return CommonResult.success(toOutputRespVO(result));
    }

    /**
     * 查询工单领料记录列表（租户隔离）。
     *
     * @param id       工单 ID
     * @param tenantId 租户 ID（显式参数，过渡策略）
     */
    @GetMapping("/{id}/consumptions")
    public CommonResult<List<ProductionOrderConsumptionRespVO>> consumptions(
            @PathVariable("id") Long id,
            @RequestParam Long tenantId) {
        List<ProductionOrderConsumptionDO> list =
                productionConsumptionService.listConsumptions(id, tenantId);
        List<ProductionOrderConsumptionRespVO> voList = list.stream()
                .map(this::toConsumptionRespVO)
                .collect(Collectors.toList());
        return CommonResult.success(voList);
    }

    /**
     * 查询工单产出记录列表（租户隔离）。
     *
     * @param id       工单 ID
     * @param tenantId 租户 ID（显式参数，过渡策略）
     */
    @GetMapping("/{id}/outputs")
    public CommonResult<List<ProductionOrderOutputRespVO>> outputs(
            @PathVariable("id") Long id,
            @RequestParam Long tenantId) {
        List<ProductionOrderOutputDO> list =
                productionOutputService.listOutputs(id, tenantId);
        List<ProductionOrderOutputRespVO> voList = list.stream()
                .map(this::toOutputRespVO)
                .collect(Collectors.toList());
        return CommonResult.success(voList);
    }

    // --- Helpers (DO → VO，与 admin controller 字段映射保持一致) ---

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
