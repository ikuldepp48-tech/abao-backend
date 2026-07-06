package com.geihou.module.supplychain.production.service;

import com.geihou.module.supplychain.api.bom.explode.dto.BomExplosionRespDTO;
import com.geihou.module.supplychain.bom.explode.BomExplosionService;
import com.geihou.module.supplychain.production.controller.admin.vo.ScanPickReqVO;
import com.geihou.module.supplychain.production.dal.dataobject.ProductionOrderConsumptionDO;
import com.geihou.module.supplychain.production.dal.dataobject.ProductionOrderDO;
import com.geihou.module.supplychain.production.dal.mapper.ProductionOrderConsumptionMapper;
import com.geihou.module.supplychain.production.dal.mapper.ProductionOrderMapper;
import com.geihou.module.supplychain.stock.framework.StockBusinessException;
import com.geihou.module.supplychain.stock.framework.StockErrorCodeConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Implementation of {@link ProductionConsumptionService}.
 *
 * <p>Handles scan-pick (领料记录): validates order state and BOM component,
 * writes PRODUCTION_OUT stock_event via {@link ProductionStockEventIntegrationService},
 * then inserts consumption record (INSERT-only).
 *
 * <p>Key constraints (TASK-G2-02N):
 * <ul>
 *   <li>Only IN_PROGRESS stage allows scan-pick</li>
 *   <li>Component must be in BOM explosion leaves</li>
 *   <li>pickSeq must be unique per (tenant, order, component)</li>
 *   <li>consumption record is INSERT-only — no update/delete</li>
 *   <li>StockBusinessException propagates directly</li>
 * </ul>
 *
 * <p>Source: TASK-G2-02N.
 */
@Service
public class ProductionConsumptionServiceImpl implements ProductionConsumptionService {

    private static final String STAGE_IN_PROGRESS = "IN_PROGRESS";

    @Autowired
    private ProductionOrderMapper productionOrderMapper;

    @Autowired
    private ProductionOrderConsumptionMapper consumptionMapper;

    @Autowired
    private BomExplosionService bomExplosionService;

    @Autowired
    private ProductionStockEventIntegrationService stockEventIntegrationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductionOrderConsumptionDO scanPick(ScanPickReqVO req) {
        Objects.requireNonNull(req, "request must not be null");
        if (req.getTenantId() == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "tenantId");
        }
        if (req.getComponentProductId() == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "componentProductId");
        }
        if (req.getActualQty() == null || req.getActualQty().compareTo(BigDecimal.ZERO) <= 0) {
            throw new StockBusinessException(StockErrorCodeConstants.QUANTITY_MUST_BE_POSITIVE);
        }
        if (req.getPickSeq() == null || req.getPickSeq() <= 0) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "pickSeq");
        }

        // 1. Find existing order (orderId set by controller from path variable)
        ProductionOrderDO existing = productionOrderMapper.selectByIdAndTenant(
                req.getOrderId(), req.getTenantId());
        if (existing == null) {
            throw new StockBusinessException(StockErrorCodeConstants.PRODUCTION_ORDER_NOT_FOUND);
        }

        // 2. Validate stage = IN_PROGRESS
        if (!STAGE_IN_PROGRESS.equals(existing.getProductionStage())) {
            throw new StockBusinessException(StockErrorCodeConstants.PRODUCTION_SCAN_NOT_ALLOWED,
                    "current stage: " + existing.getProductionStage());
        }

        // 3. BOM explode by plannedQty to get component planned quantities
        List<BomExplosionRespDTO> tree = bomExplosionService.explode(
                existing.getProductId(), existing.getRecipeId(),
                existing.getPlannedQty(), existing.getTenantId());
        List<BomExplosionRespDTO> leaves = new ArrayList<>();
        collectLeaves(tree, leaves);

        // Aggregate by componentProductId
        Map<Long, BigDecimal> aggregatedPlannedQty = new LinkedHashMap<>();
        for (BomExplosionRespDTO leaf : leaves) {
            aggregatedPlannedQty.merge(leaf.getProductId(), leaf.getQuantity(), BigDecimal::add);
        }

        // 4. Validate component is in BOM
        if (!aggregatedPlannedQty.containsKey(req.getComponentProductId())) {
            throw new StockBusinessException(StockErrorCodeConstants.PRODUCTION_PICK_COMPONENT_NOT_IN_BOM);
        }

        // 5. Check pickSeq uniqueness (幂等冲突)
        List<ProductionOrderConsumptionDO> existingConsumptions = consumptionMapper.listByOrder(
                req.getTenantId(), existing.getId());
        for (ProductionOrderConsumptionDO c : existingConsumptions) {
            if (c.getInputSkuId().equals(req.getComponentProductId())
                    && c.getPickSeq().equals(req.getPickSeq())) {
                throw new StockBusinessException(StockErrorCodeConstants.PRODUCTION_SEQ_ALREADY_EXISTS,
                        "pickSeq " + req.getPickSeq() + " already exists for component " + req.getComponentProductId());
            }
        }

        // 6. Calculate planned_qty for this component (per plannedQty BOM explosion)
        BigDecimal plannedQtyForComponent = aggregatedPlannedQty.get(req.getComponentProductId());

        // 7. Write PRODUCTION_OUT stock_event (reuses mapping chain)
        Long stockEventId = stockEventIntegrationService.recordProductionOut(
                existing, req.getComponentProductId(), req.getActualQty(), req.getPickSeq());

        // 8. Insert consumption record (INSERT-only)
        ProductionOrderConsumptionDO consumption = new ProductionOrderConsumptionDO();
        consumption.setTenantId(req.getTenantId());
        consumption.setProductionOrderId(existing.getId());
        consumption.setInputSkuId(req.getComponentProductId());
        consumption.setPickSeq(req.getPickSeq());
        consumption.setPlannedQty(plannedQtyForComponent);
        consumption.setActualQty(req.getActualQty());
        consumption.setDiffQty(req.getActualQty().subtract(plannedQtyForComponent));
        consumption.setDiffReason(req.getDiffReason());
        consumption.setStockEventId(stockEventId);
        String operator = req.getOperatorUserId() != null
                ? String.valueOf(req.getOperatorUserId()) : "system";
        consumption.setCreator(operator);
        consumption.setCreateTime(LocalDateTime.now());
        consumption.setUpdater(operator);
        consumption.setUpdateTime(LocalDateTime.now());
        consumption.setDeleted(false);

        consumptionMapper.insert(consumption);

        return consumption;
    }

    @Override
    public List<ProductionOrderConsumptionDO> listConsumptions(Long orderId, Long tenantId) {
        return consumptionMapper.listByOrder(tenantId, orderId);
    }

    // --- Helpers ---

    private void collectLeaves(List<BomExplosionRespDTO> nodes, List<BomExplosionRespDTO> leaves) {
        for (BomExplosionRespDTO node : nodes) {
            if (node.getChildren() == null || node.getChildren().isEmpty()) {
                leaves.add(node);
            } else {
                collectLeaves(node.getChildren(), leaves);
            }
        }
    }
}
