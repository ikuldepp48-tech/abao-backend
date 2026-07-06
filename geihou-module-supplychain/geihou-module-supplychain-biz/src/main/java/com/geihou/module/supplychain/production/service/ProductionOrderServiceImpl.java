package com.geihou.module.supplychain.production.service;

import com.geihou.common.pojo.PageResult;
import com.geihou.module.supplychain.api.bom.explode.dto.BomExplosionRespDTO;
import com.geihou.module.supplychain.bom.dal.dataobject.BomRecipeDO;
import com.geihou.module.supplychain.bom.dal.dataobject.ProductMasterDO;
import com.geihou.module.supplychain.bom.dal.mapper.BomRecipeMapper;
import com.geihou.module.supplychain.bom.dal.mapper.ProductMasterMapper;
import com.geihou.module.supplychain.bom.explode.BomExplosionService;
import com.geihou.module.supplychain.production.controller.admin.vo.ProductionOrderCreateReqVO;
import com.geihou.module.supplychain.production.controller.admin.vo.ProductionOrderStageTransitionReqVO;
import com.geihou.module.supplychain.production.controller.admin.vo.ProductionOrderUpdateReqVO;
import com.geihou.module.supplychain.production.dal.dataobject.ProductionOrderConsumptionDO;
import com.geihou.module.supplychain.production.dal.dataobject.ProductionOrderDO;
import com.geihou.module.supplychain.production.dal.dataobject.ProductionOrderOutputDO;
import com.geihou.module.supplychain.production.dal.mapper.ProductionOrderConsumptionMapper;
import com.geihou.module.supplychain.production.dal.mapper.ProductionOrderMapper;
import com.geihou.module.supplychain.production.dal.mapper.ProductionOrderOutputMapper;
import com.geihou.module.supplychain.stock.dal.dataobject.StockLocationDO;
import com.geihou.module.supplychain.stock.dal.mapper.StockLocationMapper;
import com.geihou.module.supplychain.stock.framework.StockBusinessException;
import com.geihou.module.supplychain.stock.framework.StockErrorCodeConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Implementation of {@link ProductionOrderService}.
 *
 * <p>Creates production orders with validation against product_master (SEMI_FINISHED),
 * bom_recipe (ACTIVE), and stock_location (is_active=true).
 *
 * <p>State machine (ENUM_PRODUCTION_STAGE):
 * <ul>
 *   <li>CREATED → MATERIAL_REQUEST → IN_PROGRESS → QUALITY_CHECK → COMPLETED / REWORK</li>
 *   <li>REWORK → IN_PROGRESS (返工后重新制作)</li>
 *   <li>IN_PROGRESS → COMPLETED (直通完工, G2-02L)</li>
 *   <li>非终态 → CANCELLED</li>
 * </ul>
 *
 * <p>Source: TASK-G2-02K, TASK-G2-02L, TASK-G2-02M.
 */
@Service
public class ProductionOrderServiceImpl implements ProductionOrderService {

    // --- ENUM_PRODUCTION_STAGE constants ---
    private static final String STAGE_CREATED = "CREATED";
    private static final String STAGE_MATERIAL_REQUEST = "MATERIAL_REQUEST";
    private static final String STAGE_IN_PROGRESS = "IN_PROGRESS";
    private static final String STAGE_QUALITY_CHECK = "QUALITY_CHECK";
    private static final String STAGE_COMPLETED = "COMPLETED";
    private static final String STAGE_REWORK = "REWORK";
    private static final String STAGE_CANCELLED = "CANCELLED";

    // --- Terminal states ---
    private static final java.util.Set<String> TERMINAL_STAGES =
            java.util.Set.of(STAGE_COMPLETED, STAGE_CANCELLED);

    private static final String PRODUCT_TYPE_SEMI_FINISHED = "SEMI_FINISHED";
    private static final String RECIPE_STATUS_ACTIVE = "ACTIVE";

    @Autowired
    private ProductionOrderMapper productionOrderMapper;

    @Autowired
    private ProductMasterMapper productMasterMapper;

    @Autowired
    private BomRecipeMapper bomRecipeMapper;

    @Autowired
    private StockLocationMapper stockLocationMapper;

    @Autowired
    private ProductionStockEventIntegrationService productionStockEventIntegrationService;

    // G2-02N: scan-pick / scan-output support
    @Autowired
    private ProductionOrderConsumptionMapper productionOrderConsumptionMapper;
    @Autowired
    private ProductionOrderOutputMapper productionOrderOutputMapper;
    @Autowired
    private BomExplosionService bomExplosionService;

    // =========================================================================
    // createProductionOrder
    // =========================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductionOrderDO createProductionOrder(ProductionOrderCreateReqVO req) {
        Objects.requireNonNull(req, "request must not be null");

        // 1. Validate tenantId
        if (req.getTenantId() == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "tenantId");
        }

        // 2. Validate product_id exists and product_type = SEMI_FINISHED
        validateProduct(req.getTenantId(), req.getProductId());

        // 3. Validate recipe_id exists and status = ACTIVE
        validateRecipe(req.getTenantId(), req.getRecipeId());

        // 4. Validate location_id exists and is_active = true
        validateLocation(req.getTenantId(), req.getLocationId());

        // 5. Validate plannedQty > 0
        if (req.getPlannedQty() == null || req.getPlannedQty().compareTo(BigDecimal.ZERO) <= 0) {
            throw new StockBusinessException(StockErrorCodeConstants.PRODUCTION_PLANNED_QTY_MUST_BE_POSITIVE);
        }

        // 6. Generate unique orderNo (with retry)
        String orderNo = generateOrderNo(req.getTenantId());

        // 7. Build DO
        ProductionOrderDO orderDO = new ProductionOrderDO();
        orderDO.setTenantId(req.getTenantId());
        orderDO.setOrderNo(orderNo);
        orderDO.setProductId(req.getProductId());
        orderDO.setRecipeId(req.getRecipeId());
        orderDO.setLocationId(req.getLocationId());
        orderDO.setPlannedQty(req.getPlannedQty());
        orderDO.setActualQty(null);
        orderDO.setProductionStage(STAGE_CREATED);
        orderDO.setPlanStartTime(req.getPlanStartTime());
        orderDO.setPlanEndTime(req.getPlanEndTime());
        orderDO.setActualStartTime(null);
        orderDO.setActualEndTime(null);
        orderDO.setOperatorUserId(req.getOperatorUserId());
        orderDO.setRemark(req.getRemark());
        orderDO.setCreator(req.getOperatorUserId() != null ? String.valueOf(req.getOperatorUserId()) : "system");
        orderDO.setCreateTime(LocalDateTime.now());
        orderDO.setUpdater(req.getOperatorUserId() != null ? String.valueOf(req.getOperatorUserId()) : "system");
        orderDO.setUpdateTime(LocalDateTime.now());
        orderDO.setDeleted(false);

        // 8. Insert
        productionOrderMapper.insert(orderDO);

        return orderDO;
    }

    // =========================================================================
    // getProductionOrder
    // =========================================================================

    @Override
    public ProductionOrderDO getProductionOrder(Long id, Long tenantId) {
        return productionOrderMapper.selectByIdAndTenant(id, tenantId);
    }

    // =========================================================================
    // listProductionOrders
    // =========================================================================

    @Override
    public PageResult<ProductionOrderDO> listProductionOrders(Long tenantId, String productionStage,
                                                               Integer pageNo, Integer pageSize) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(pageNo, "pageNo must not be null");
        Objects.requireNonNull(pageSize, "pageSize must not be null");

        List<ProductionOrderDO> all;
        if (productionStage != null && !productionStage.isBlank()) {
            all = productionOrderMapper.listByTenantAndStage(tenantId, productionStage);
        } else {
            all = productionOrderMapper.listByTenant(tenantId);
        }

        // Manual pagination
        int total = all.size();
        int fromIndex = (pageNo - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, total);
        List<ProductionOrderDO> pageList = fromIndex < total
                ? all.subList(fromIndex, toIndex)
                : java.util.Collections.emptyList();

        return PageResult.of(pageList, (long) total, pageNo, pageSize);
    }

    // =========================================================================
    // updateProductionOrder
    // =========================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductionOrderDO updateProductionOrder(ProductionOrderUpdateReqVO req) {
        Objects.requireNonNull(req, "request must not be null");
        if (req.getId() == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "id");
        }
        if (req.getTenantId() == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "tenantId");
        }

        // 1. Find existing order
        ProductionOrderDO existing = productionOrderMapper.selectByIdAndTenant(req.getId(), req.getTenantId());
        if (existing == null) {
            throw new StockBusinessException(StockErrorCodeConstants.PRODUCTION_ORDER_NOT_FOUND);
        }

        // 2. Verify CREATED stage
        if (!STAGE_CREATED.equals(existing.getProductionStage())) {
            throw new StockBusinessException(StockErrorCodeConstants.PRODUCTION_UPDATE_NOT_ALLOWED,
                    "current stage: " + existing.getProductionStage());
        }

        // 3. Re-validate if productId / recipeId / locationId changed
        Long productId = req.getProductId() != null ? req.getProductId() : existing.getProductId();
        Long recipeId = req.getRecipeId() != null ? req.getRecipeId() : existing.getRecipeId();
        Long locationId = req.getLocationId() != null ? req.getLocationId() : existing.getLocationId();
        BigDecimal plannedQty = req.getPlannedQty() != null ? req.getPlannedQty() : existing.getPlannedQty();

        if (!productId.equals(existing.getProductId())) {
            validateProduct(req.getTenantId(), productId);
        }
        if (!recipeId.equals(existing.getRecipeId())) {
            validateRecipe(req.getTenantId(), recipeId);
        }
        if (!locationId.equals(existing.getLocationId())) {
            validateLocation(req.getTenantId(), locationId);
        }
        if (plannedQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new StockBusinessException(StockErrorCodeConstants.PRODUCTION_PLANNED_QTY_MUST_BE_POSITIVE);
        }

        // 4. Update mutable fields
        String updater = req.getOperatorUserId() != null
                ? String.valueOf(req.getOperatorUserId()) : "system";

        int rows = productionOrderMapper.updateMutableByTenant(
                req.getId(),
                req.getTenantId(),
                productId,
                recipeId,
                locationId,
                plannedQty,
                req.getPlanStartTime() != null ? req.getPlanStartTime() : existing.getPlanStartTime(),
                req.getPlanEndTime() != null ? req.getPlanEndTime() : existing.getPlanEndTime(),
                req.getOperatorUserId() != null ? req.getOperatorUserId() : existing.getOperatorUserId(),
                req.getRemark() != null ? req.getRemark() : existing.getRemark(),
                updater,
                LocalDateTime.now()
        );

        if (rows == 0) {
            throw new StockBusinessException(StockErrorCodeConstants.PRODUCTION_UPDATE_NOT_ALLOWED,
                    "concurrent modification or stage no longer CREATED");
        }

        return productionOrderMapper.selectByIdAndTenant(req.getId(), req.getTenantId());
    }

    // =========================================================================
    // transitionStage
    // =========================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductionOrderDO transitionStage(ProductionOrderStageTransitionReqVO req) {
        Objects.requireNonNull(req, "request must not be null");
        if (req.getId() == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "id");
        }
        if (req.getTenantId() == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "tenantId");
        }
        if (req.getTargetStage() == null || req.getTargetStage().isBlank()) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "targetStage");
        }

        // 1. Find existing order
        ProductionOrderDO existing = productionOrderMapper.selectByIdAndTenant(req.getId(), req.getTenantId());
        if (existing == null) {
            throw new StockBusinessException(StockErrorCodeConstants.PRODUCTION_ORDER_NOT_FOUND);
        }

        String currentStage = existing.getProductionStage();
        String targetStage = req.getTargetStage();

        // 2. Check terminal state — cannot transition from terminal
        if (TERMINAL_STAGES.contains(currentStage)) {
            throw new StockBusinessException(StockErrorCodeConstants.PRODUCTION_INVALID_STAGE_TRANSITION,
                    "current stage " + currentStage + " is terminal, cannot transition to " + targetStage);
        }

        // 3. Resolve operator
        Long operatorUserId = req.getOperatorUserId() != null
                ? req.getOperatorUserId() : existing.getOperatorUserId();

        // 4. Validate and execute specific transitions
        if (STAGE_CANCELLED.equals(targetStage)) {
            // Non-terminal → CANCELLED: remark (cancel reason) required
            if (req.getRemark() == null || req.getRemark().isBlank()) {
                throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING,
                        "remark (cancel reason) required for CANCELLED");
            }
            // G2-02N: block cancel if consumption records exist (阻断)
            int consumptionCount = productionOrderConsumptionMapper.countByOrder(
                    req.getTenantId(), existing.getId());
            if (consumptionCount > 0) {
                throw new StockBusinessException(
                        StockErrorCodeConstants.PRODUCTION_CANCEL_BLOCKED_BY_CONSUMPTION);
            }
            return executeSimpleTransition(req.getId(), req.getTenantId(), targetStage, currentStage,
                    existing.getActualStartTime(), existing.getActualEndTime(), existing.getActualQty(),
                    operatorUserId, req.getRemark());
        } else if (STAGE_MATERIAL_REQUEST.equals(targetStage)) {
            // CREATED → MATERIAL_REQUEST
            if (!STAGE_CREATED.equals(currentStage)) {
                throw new StockBusinessException(StockErrorCodeConstants.PRODUCTION_INVALID_STAGE_TRANSITION,
                        currentStage + " → " + targetStage);
            }
            return executeSimpleTransition(req.getId(), req.getTenantId(), targetStage, currentStage,
                    existing.getActualStartTime(), existing.getActualEndTime(), existing.getActualQty(),
                    operatorUserId, existing.getRemark());
        } else if (STAGE_IN_PROGRESS.equals(targetStage)) {
            // MATERIAL_REQUEST → IN_PROGRESS, or REWORK → IN_PROGRESS
            if (STAGE_MATERIAL_REQUEST.equals(currentStage)) {
                LocalDateTime actualStartTime = LocalDateTime.now();
                return executeSimpleTransition(req.getId(), req.getTenantId(), targetStage, currentStage,
                        actualStartTime, existing.getActualEndTime(), existing.getActualQty(),
                        operatorUserId, existing.getRemark());
            } else if (STAGE_REWORK.equals(currentStage)) {
                // REWORK → IN_PROGRESS: no stock_event, preserve rework_count
                return executeSimpleTransition(req.getId(), req.getTenantId(), targetStage, currentStage,
                        existing.getActualStartTime(), existing.getActualEndTime(), existing.getActualQty(),
                        operatorUserId, existing.getRemark());
            } else {
                throw new StockBusinessException(StockErrorCodeConstants.PRODUCTION_REWORK_RESUME_NOT_ALLOWED,
                        currentStage + " → " + targetStage);
            }
        } else if (STAGE_QUALITY_CHECK.equals(targetStage)) {
            // IN_PROGRESS → QUALITY_CHECK
            if (!STAGE_IN_PROGRESS.equals(currentStage)) {
                throw new StockBusinessException(StockErrorCodeConstants.PRODUCTION_QUALITY_CHECK_SUBMIT_NOT_ALLOWED,
                        currentStage + " → " + targetStage);
            }
            // No stock_event
            return executeSimpleTransition(req.getId(), req.getTenantId(), targetStage, currentStage,
                    existing.getActualStartTime(), existing.getActualEndTime(), existing.getActualQty(),
                    operatorUserId, existing.getRemark());
        } else if (STAGE_COMPLETED.equals(targetStage)) {
            // IN_PROGRESS → COMPLETED (direct, G2-02L), or QUALITY_CHECK → COMPLETED (quality pass, G2-02M)
            if (STAGE_IN_PROGRESS.equals(currentStage)) {
                // Direct completion (G2-02L)
                return executeCompletion(req, existing, operatorUserId, currentStage, false);
            } else if (STAGE_QUALITY_CHECK.equals(currentStage)) {
                // Quality check pass → COMPLETED (G2-02M)
                if (operatorUserId == null) {
                    throw new StockBusinessException(StockErrorCodeConstants.PRODUCTION_QUALITY_CHECK_OPERATOR_REQUIRED);
                }
                return executeCompletion(req, existing, operatorUserId, currentStage, true);
            } else {
                throw new StockBusinessException(StockErrorCodeConstants.PRODUCTION_QUALITY_CHECK_PASS_NOT_ALLOWED,
                        currentStage + " → " + targetStage);
            }
        } else if (STAGE_REWORK.equals(targetStage)) {
            // QUALITY_CHECK → REWORK
            if (!STAGE_QUALITY_CHECK.equals(currentStage)) {
                throw new StockBusinessException(StockErrorCodeConstants.PRODUCTION_QUALITY_CHECK_REJECT_NOT_ALLOWED,
                        currentStage + " → " + targetStage);
            }
            if (operatorUserId == null) {
                throw new StockBusinessException(StockErrorCodeConstants.PRODUCTION_QUALITY_CHECK_OPERATOR_REQUIRED);
            }
            if (req.getRemark() == null || req.getRemark().isBlank()) {
                throw new StockBusinessException(StockErrorCodeConstants.PRODUCTION_REWORK_REASON_REQUIRED);
            }

            // Execute rework transition (SQL-level rework_count increment)
            String updater = String.valueOf(operatorUserId);
            LocalDateTime now = LocalDateTime.now();
            int rows = productionOrderMapper.updateStageToReworkByTenant(
                    req.getId(),
                    req.getTenantId(),
                    STAGE_REWORK,
                    currentStage,
                    "FAIL",
                    req.getRemark(),
                    operatorUserId,
                    now,
                    operatorUserId,
                    existing.getRemark(),
                    updater,
                    now
            );
            if (rows == 0) {
                throw new StockBusinessException(StockErrorCodeConstants.PRODUCTION_INVALID_STAGE_TRANSITION,
                        "concurrent modification detected, current stage may have changed");
            }
            // No stock_event for REWORK
            return productionOrderMapper.selectByIdAndTenant(req.getId(), req.getTenantId());
        } else {
            // Unknown target stage
            throw new StockBusinessException(StockErrorCodeConstants.PRODUCTION_INVALID_STAGE_TRANSITION,
                    "unknown target stage: " + targetStage);
        }
    }

    // =========================================================================
    // Private transition helpers (G2-02M)
    // =========================================================================

    /**
     * Execute a simple stage transition using the standard updateStageByTenant mapper.
     * No quality check fields are set.
     */
    private ProductionOrderDO executeSimpleTransition(Long id, Long tenantId, String targetStage,
                                                       String currentStage,
                                                       LocalDateTime actualStartTime,
                                                       LocalDateTime actualEndTime,
                                                       BigDecimal actualQty,
                                                       Long operatorUserId, String remark) {
        String updater = operatorUserId != null ? String.valueOf(operatorUserId) : "system";
        int rows = productionOrderMapper.updateStageByTenant(
                id, tenantId, targetStage, currentStage,
                actualStartTime, actualEndTime, actualQty,
                operatorUserId, remark, updater, LocalDateTime.now()
        );
        if (rows == 0) {
            throw new StockBusinessException(StockErrorCodeConstants.PRODUCTION_INVALID_STAGE_TRANSITION,
                    "concurrent modification detected, current stage may have changed");
        }
        return productionOrderMapper.selectByIdAndTenant(id, tenantId);
    }

    /**
     * Execute completion transition. If fromQualityCheck, uses the quality-pass mapper
     * to set quality_check_result = PASS.
     *
     * <p>G2-02N 方案 A: If the order has consumption records (scan-pick), do NOT call
     * bulk integrateStockEvents (which writes PRODUCTION_OUT + PRODUCTION_IN). Instead:
     * <ul>
     *   <li>Validate all BOM components have sufficient cumulative pick quantity
     *       (based on actualQty), throwing 2002049 if insufficient.</li>
     *   <li>Only write PRODUCTION_IN if no output records exist (scan-output already
     *       wrote PRODUCTION_IN). If output records exist, skip PRODUCTION_IN.</li>
     * </ul>
     * If no consumption records, preserve G2-02L / G2-02M bulk behavior.
     */
    private ProductionOrderDO executeCompletion(ProductionOrderStageTransitionReqVO req,
                                                 ProductionOrderDO existing,
                                                 Long operatorUserId,
                                                 String currentStage,
                                                 boolean fromQualityCheck) {
        LocalDateTime actualEndTime = LocalDateTime.now();
        if (req.getActualQty() == null || req.getActualQty().compareTo(BigDecimal.ZERO) <= 0) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING,
                    "actualQty required and must be positive for COMPLETED");
        }
        BigDecimal actualQty = req.getActualQty();
        String updater = String.valueOf(operatorUserId);
        LocalDateTime now = LocalDateTime.now();

        // G2-02N: check for consumption records (方案 A branch)
        int consumptionCount = productionOrderConsumptionMapper.countByOrder(
                req.getTenantId(), existing.getId());
        boolean hasConsumption = consumptionCount > 0;

        int rows;
        if (fromQualityCheck) {
            // QUALITY_CHECK → COMPLETED: set quality_check_result = PASS
            rows = productionOrderMapper.updateStageWithQualityPassByTenant(
                    req.getId(), req.getTenantId(),
                    STAGE_COMPLETED, currentStage,
                    actualEndTime, actualQty,
                    operatorUserId, existing.getRemark(),
                    "PASS", operatorUserId, now,
                    updater, now
            );
        } else {
            // IN_PROGRESS → COMPLETED: standard update
            rows = productionOrderMapper.updateStageByTenant(
                    req.getId(), req.getTenantId(),
                    STAGE_COMPLETED, currentStage,
                    existing.getActualStartTime(), actualEndTime, actualQty,
                    operatorUserId, existing.getRemark(),
                    updater, now
            );
        }

        if (rows == 0) {
            throw new StockBusinessException(StockErrorCodeConstants.PRODUCTION_INVALID_STAGE_TRANSITION,
                    "concurrent modification detected, current stage may have changed");
        }

        ProductionOrderDO updated = productionOrderMapper.selectByIdAndTenant(
                req.getId(), req.getTenantId());

        if (hasConsumption) {
            // G2-02N 方案 A: validate sufficient picks, then only write PRODUCTION_IN if needed
            validateSufficientPicks(updated, actualQty);

            // Check if output records exist (scan-output already wrote PRODUCTION_IN)
            int outputCount = productionOrderOutputMapper.countByOrder(
                    req.getTenantId(), existing.getId());
            if (outputCount == 0) {
                // No output records → write a single PRODUCTION_IN for the semi-finished product
                productionStockEventIntegrationService.recordProductionIn(
                        updated, actualQty, "PROD-IN-" + updated.getId());
            }
            // If output records exist, PRODUCTION_IN was already written by scan-output
        } else {
            // G2-02L / G2-02M: Execute bulk stock event integration (same transaction).
            // StockBusinessException propagates directly (not wrapped), triggering transaction rollback.
            productionStockEventIntegrationService.integrateStockEvents(updated);
        }

        return updated;
    }

    /**
     * G2-02N: Validate that cumulative pick quantity for each BOM component
     * meets or exceeds the planned consumption based on actualQty.
     * Throws 2002049 if any component is insufficient.
     */
    private void validateSufficientPicks(ProductionOrderDO order, BigDecimal actualQty) {
        // BOM explode by actualQty to get planned consumption per component
        List<BomExplosionRespDTO> tree = bomExplosionService.explode(
                order.getProductId(), order.getRecipeId(),
                actualQty, order.getTenantId());
        List<BomExplosionRespDTO> leaves = new ArrayList<>();
        collectBomLeaves(tree, leaves);

        // Aggregate planned quantity per componentProductId
        Map<Long, BigDecimal> aggregatedPlannedQty = new LinkedHashMap<>();
        for (BomExplosionRespDTO leaf : leaves) {
            aggregatedPlannedQty.merge(leaf.getProductId(), leaf.getQuantity(), BigDecimal::add);
        }

        // Get all consumption records and aggregate actual picked qty per component
        List<ProductionOrderConsumptionDO> consumptions = productionOrderConsumptionMapper.listByOrder(
                order.getTenantId(), order.getId());
        Map<Long, BigDecimal> cumulativeActualQty = new LinkedHashMap<>();
        for (ProductionOrderConsumptionDO c : consumptions) {
            cumulativeActualQty.merge(c.getInputSkuId(), c.getActualQty(), BigDecimal::add);
        }

        // Compare: cumulative actual must be >= planned for every component
        List<String> insufficientComponents = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : aggregatedPlannedQty.entrySet()) {
            Long componentId = entry.getKey();
            BigDecimal planned = entry.getValue();
            BigDecimal actual = cumulativeActualQty.getOrDefault(componentId, BigDecimal.ZERO);
            if (actual.compareTo(planned) < 0) {
                BigDecimal shortfall = planned.subtract(actual);
                insufficientComponents.add(
                        "component " + componentId + ": picked " + actual + ", planned " + planned
                                + ", shortfall " + shortfall);
            }
        }

        if (!insufficientComponents.isEmpty()) {
            throw new StockBusinessException(
                    StockErrorCodeConstants.PRODUCTION_PICK_INSUFFICIENT_FOR_COMPLETION,
                    String.join("; ", insufficientComponents));
        }
    }

    private void collectBomLeaves(List<BomExplosionRespDTO> nodes, List<BomExplosionRespDTO> leaves) {
        for (BomExplosionRespDTO node : nodes) {
            if (node.getChildren() == null || node.getChildren().isEmpty()) {
                leaves.add(node);
            } else {
                collectBomLeaves(node.getChildren(), leaves);
            }
        }
    }

    // =========================================================================
    // Private validation helpers
    // =========================================================================

    private void validateProduct(Long tenantId, Long productId) {
        if (productId == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "productId");
        }
        ProductMasterDO product = productMasterMapper.selectByIdAndTenant(productId, tenantId);
        if (product == null || !PRODUCT_TYPE_SEMI_FINISHED.equals(product.getProductType())) {
            throw new StockBusinessException(StockErrorCodeConstants.PRODUCTION_PRODUCT_NOT_SEMI_FINISHED);
        }
    }

    private void validateRecipe(Long tenantId, Long recipeId) {
        if (recipeId == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "recipeId");
        }
        BomRecipeDO recipe = bomRecipeMapper.selectByIdAndTenant(recipeId, tenantId);
        if (recipe == null || !RECIPE_STATUS_ACTIVE.equals(recipe.getStatus())) {
            throw new StockBusinessException(StockErrorCodeConstants.PRODUCTION_RECIPE_NOT_ACTIVE);
        }
    }

    private void validateLocation(Long tenantId, Long locationId) {
        if (locationId == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "locationId");
        }
        StockLocationDO location = stockLocationMapper.selectByIdAndTenant(locationId, tenantId);
        if (location == null || !Boolean.TRUE.equals(location.getIsActive())) {
            throw new StockBusinessException(StockErrorCodeConstants.PRODUCTION_LOCATION_NOT_ACTIVE);
        }
    }

    /**
     * Generate a unique order number: PO{yyyyMMddHHmmss}{4位随机}
     * Retries up to 3 times if collision detected.
     */
    private String generateOrderNo(Long tenantId) {
        for (int attempt = 0; attempt < 3; attempt++) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            int random = ThreadLocalRandom.current().nextInt(1000, 9999);
            String orderNo = "PO" + timestamp + random;

            ProductionOrderDO existing = productionOrderMapper.selectByTenantAndOrderNo(tenantId, orderNo);
            if (existing == null) {
                return orderNo;
            }
        }
        throw new StockBusinessException(StockErrorCodeConstants.PRODUCTION_ORDER_NO_CONFLICT);
    }
}
