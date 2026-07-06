package com.geihou.module.supplychain.production.service;

import com.geihou.module.supplychain.api.bom.explode.dto.BomExplosionRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockEventReqDTO;
import com.geihou.module.supplychain.api.stock.enums.StockDirectionEnum;
import com.geihou.module.supplychain.api.stock.enums.StockEventTypeEnum;
import com.geihou.module.supplychain.bom.dal.dataobject.BomRecipeDO;
import com.geihou.module.supplychain.bom.dal.dataobject.ProductMasterDO;
import com.geihou.module.supplychain.bom.explode.BomExplosionService;
import com.geihou.module.supplychain.bom.service.BomRecipeService;
import com.geihou.module.supplychain.bom.service.ProductMasterService;
import com.geihou.module.supplychain.production.dal.dataobject.ProductionOrderDO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockItemDO;
import com.geihou.module.supplychain.stock.framework.StockBusinessException;
import com.geihou.module.supplychain.stock.framework.StockErrorCodeConstants;
import com.geihou.module.supplychain.stock.service.StockEventService;
import com.geihou.module.supplychain.stock.service.StockItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of {@link ProductionStockEventIntegrationService}.
 *
 * <p>Encapsulates PRODUCTION_OUT (BOM component deduction) and PRODUCTION_IN
 * (semi-finished product receipt) logic for production order completion.
 *
 * <p>Key constraints (TASK-G2-02L guardrails):
 * <ul>
 *   <li>stockItemId must come from product_master.skuCode → stock_item.id mapping,
 *       never directly from productId.</li>
 *   <li>BOM leaves must be aggregated by componentProductId (BigDecimal sum) before
 *       writing PRODUCTION_OUT events — one event per unique componentProductId.</li>
 *   <li>Uses actualQty (not plannedQty) for BOM explosion.</li>
 *   <li>StockBusinessException propagates directly — not wrapped as 2002041.</li>
 *   <li>No @Transactional on this method — caller's transaction covers it.</li>
 *   <li>clientRequestId format: PROD-OUT-{orderId}-{componentProductId} / PROD-IN-{orderId}</li>
 * </ul>
 *
 * <p>Source: TASK-G2-02L.
 */
@Service
public class ProductionStockEventIntegrationServiceImpl
        implements ProductionStockEventIntegrationService {

    @Autowired
    private BomExplosionService bomExplosionService;

    @Autowired
    private ProductMasterService productMasterService;

    @Autowired
    private StockItemService stockItemService;

    @Autowired
    private StockEventService stockEventService;

    @Autowired
    private BomRecipeService bomRecipeService;

    @Override
    public void integrateStockEvents(ProductionOrderDO order) {
        // 1. Load recipe snapshot for traceability
        BomRecipeDO recipe = bomRecipeService.getById(order.getRecipeId(), order.getTenantId());
        Long recipeId = recipe != null ? recipe.getId() : order.getRecipeId();
        Integer recipeVersion = recipe != null ? recipe.getVersionNo() : null;

        // 2. BOM explode by actualQty (NOT plannedQty)
        List<BomExplosionRespDTO> tree = bomExplosionService.explode(
                order.getProductId(), order.getRecipeId(),
                order.getActualQty(), order.getTenantId());

        List<BomExplosionRespDTO> leaves = new ArrayList<>();
        collectLeaves(tree, leaves);

        if (leaves.isEmpty()) {
            throw new StockBusinessException(
                    StockErrorCodeConstants.PRODUCTION_BOM_EXPLOSION_EMPTY);
        }

        // 3. ★ Aggregate by componentProductId (BigDecimal sum)
        //    Multi-layer BOM may have the same raw material in multiple leaves.
        //    Must aggregate before writing PRODUCTION_OUT — one event per componentProductId.
        Map<Long, BigDecimal> aggregatedQuantities = new LinkedHashMap<>();
        Map<Long, BomExplosionRespDTO> leafByComponent = new LinkedHashMap<>();
        for (BomExplosionRespDTO leaf : leaves) {
            Long componentProductId = leaf.getProductId();
            aggregatedQuantities.merge(componentProductId, leaf.getQuantity(), BigDecimal::add);
            leafByComponent.putIfAbsent(componentProductId, leaf);
        }

        // 4. PRODUCTION_OUT: one event per aggregated componentProductId
        for (Map.Entry<Long, BigDecimal> entry : aggregatedQuantities.entrySet()) {
            Long componentProductId = entry.getKey();
            BigDecimal totalQuantity = entry.getValue();
            BomExplosionRespDTO representativeLeaf = leafByComponent.get(componentProductId);
            deductComponent(componentProductId, totalQuantity, representativeLeaf,
                    order, recipeId, recipeVersion);
        }

        // 5. PRODUCTION_IN: receive semi-finished product
        receiveSemiFinished(order, recipeId, recipeVersion);
    }

    // =========================================================================
    // G2-02N: Single stock_event write methods for scan-pick / scan-output
    // =========================================================================

    @Override
    public Long recordProductionOut(ProductionOrderDO order, Long componentProductId,
                                     BigDecimal quantity, Integer pickSeq) {
        // Load recipe snapshot for traceability
        BomRecipeDO recipe = bomRecipeService.getById(order.getRecipeId(), order.getTenantId());
        Long recipeId = recipe != null ? recipe.getId() : order.getRecipeId();
        Integer recipeVersion = recipe != null ? recipe.getVersionNo() : null;

        // Map: componentProductId → product_master.skuCode → stock_item.id
        ProductMasterDO product = productMasterService.getById(
                componentProductId, order.getTenantId());
        if (product == null || product.getSkuCode() == null
                || product.getSkuCode().isBlank()) {
            throw new StockBusinessException(
                    StockErrorCodeConstants.PRODUCTION_COMPONENT_SKU_CODE_MISSING);
        }
        StockItemDO stockItem = stockItemService.getBySkuCode(
                product.getSkuCode(), order.getTenantId());
        if (stockItem == null) {
            throw new StockBusinessException(
                    StockErrorCodeConstants.PRODUCTION_STOCK_ITEM_NOT_FOUND);
        }

        StockEventReqDTO req = new StockEventReqDTO();
        req.setTenantId(order.getTenantId());
        req.setEventTime(LocalDateTime.now());
        req.setBusinessDate(LocalDate.now());
        req.setEventType(StockEventTypeEnum.PRODUCTION_OUT.getCode());
        req.setDirection(StockDirectionEnum.OUT.getCode());
        req.setStockItemId(stockItem.getId());
        req.setSkuCode(product.getSkuCode());
        req.setLocationId(order.getLocationId());
        req.setQuantity(quantity);
        req.setUnit(product.getUnit());
        req.setSourceModule("PRODUCTION");
        req.setSourceRecordId(order.getId());
        req.setReferenceNo(order.getOrderNo());
        req.setClientRequestId("PROD-PICK-" + order.getId() + "-" + componentProductId + "-" + pickSeq);
        req.setOperatorUserId(order.getOperatorUserId());
        req.setRecipeId(recipeId);
        req.setRecipeVersion(recipeVersion);

        return stockEventService.recordEvent(req);
    }

    @Override
    public Long recordProductionIn(ProductionOrderDO order, BigDecimal quantity,
                                    String clientRequestId) {
        // Load recipe snapshot for traceability
        BomRecipeDO recipe = bomRecipeService.getById(order.getRecipeId(), order.getTenantId());
        Long recipeId = recipe != null ? recipe.getId() : order.getRecipeId();
        Integer recipeVersion = recipe != null ? recipe.getVersionNo() : null;

        // Map: productId → product_master.skuCode → stock_item.id
        ProductMasterDO product = productMasterService.getById(
                order.getProductId(), order.getTenantId());
        if (product == null || product.getSkuCode() == null
                || product.getSkuCode().isBlank()) {
            throw new StockBusinessException(
                    StockErrorCodeConstants.PRODUCTION_COMPONENT_SKU_CODE_MISSING);
        }
        StockItemDO stockItem = stockItemService.getBySkuCode(
                product.getSkuCode(), order.getTenantId());
        if (stockItem == null) {
            throw new StockBusinessException(
                    StockErrorCodeConstants.PRODUCTION_STOCK_ITEM_NOT_FOUND);
        }

        StockEventReqDTO req = new StockEventReqDTO();
        req.setTenantId(order.getTenantId());
        req.setEventTime(LocalDateTime.now());
        req.setBusinessDate(LocalDate.now());
        req.setEventType(StockEventTypeEnum.PRODUCTION_IN.getCode());
        req.setDirection(StockDirectionEnum.IN.getCode());
        req.setStockItemId(stockItem.getId());
        req.setSkuCode(product.getSkuCode());
        req.setLocationId(order.getLocationId());
        req.setQuantity(quantity);
        req.setUnit(product.getUnit());
        req.setSourceModule("PRODUCTION");
        req.setSourceRecordId(order.getId());
        req.setReferenceNo(order.getOrderNo());
        req.setClientRequestId(clientRequestId);
        req.setOperatorUserId(order.getOperatorUserId());
        req.setRecipeId(recipeId);
        req.setRecipeVersion(recipeVersion);

        return stockEventService.recordEvent(req);
    }

    /**
     * Deduct one aggregated component via PRODUCTION_OUT stock event.
     *
     * <p>Mapping chain: componentProductId → ProductMasterService.getById → skuCode
     * → StockItemService.getBySkuCode → stockItemId.
     * StockBusinessException from recordEvent propagates directly (not wrapped).
     */
    private void deductComponent(Long componentProductId, BigDecimal totalQuantity,
                                  BomExplosionRespDTO representativeLeaf,
                                  ProductionOrderDO order,
                                  Long recipeId, Integer recipeVersion) {
        // Map: componentProductId → product_master.skuCode → stock_item.id
        ProductMasterDO product = productMasterService.getById(
                componentProductId, order.getTenantId());
        if (product == null || product.getSkuCode() == null
                || product.getSkuCode().isBlank()) {
            throw new StockBusinessException(
                    StockErrorCodeConstants.PRODUCTION_COMPONENT_SKU_CODE_MISSING);
        }
        StockItemDO stockItem = stockItemService.getBySkuCode(
                product.getSkuCode(), order.getTenantId());
        if (stockItem == null) {
            throw new StockBusinessException(
                    StockErrorCodeConstants.PRODUCTION_STOCK_ITEM_NOT_FOUND);
        }

        StockEventReqDTO req = new StockEventReqDTO();
        req.setTenantId(order.getTenantId());
        req.setEventTime(LocalDateTime.now());
        req.setBusinessDate(LocalDate.now());
        req.setEventType(StockEventTypeEnum.PRODUCTION_OUT.getCode());
        req.setDirection(StockDirectionEnum.OUT.getCode());
        req.setStockItemId(stockItem.getId());
        req.setSkuCode(product.getSkuCode());
        req.setLocationId(order.getLocationId());
        req.setQuantity(totalQuantity); // ★ aggregated total
        req.setUnit(representativeLeaf.getUnit() != null
                ? representativeLeaf.getUnit() : product.getUnit());
        req.setSourceModule("PRODUCTION");
        req.setSourceRecordId(order.getId());
        req.setReferenceNo(order.getOrderNo());
        req.setClientRequestId("PROD-OUT-" + order.getId() + "-" + componentProductId);
        req.setOperatorUserId(order.getOperatorUserId());
        req.setRecipeId(recipeId);
        req.setRecipeVersion(recipeVersion);

        // ★ StockBusinessException propagates directly — not wrapped as 2002041
        stockEventService.recordEvent(req);
    }

    /**
     * Receive semi-finished product via PRODUCTION_IN stock event.
     *
     * <p>Mapping chain: productId → ProductMasterService.getById → skuCode
     * → StockItemService.getBySkuCode → stockItemId.
     */
    private void receiveSemiFinished(ProductionOrderDO order,
                                      Long recipeId, Integer recipeVersion) {
        // Map: productId → product_master.skuCode → stock_item.id
        ProductMasterDO product = productMasterService.getById(
                order.getProductId(), order.getTenantId());
        if (product == null || product.getSkuCode() == null
                || product.getSkuCode().isBlank()) {
            throw new StockBusinessException(
                    StockErrorCodeConstants.PRODUCTION_COMPONENT_SKU_CODE_MISSING);
        }
        StockItemDO stockItem = stockItemService.getBySkuCode(
                product.getSkuCode(), order.getTenantId());
        if (stockItem == null) {
            throw new StockBusinessException(
                    StockErrorCodeConstants.PRODUCTION_STOCK_ITEM_NOT_FOUND);
        }

        StockEventReqDTO req = new StockEventReqDTO();
        req.setTenantId(order.getTenantId());
        req.setEventTime(LocalDateTime.now());
        req.setBusinessDate(LocalDate.now());
        req.setEventType(StockEventTypeEnum.PRODUCTION_IN.getCode());
        req.setDirection(StockDirectionEnum.IN.getCode());
        req.setStockItemId(stockItem.getId());
        req.setSkuCode(product.getSkuCode());
        req.setLocationId(order.getLocationId());
        req.setQuantity(order.getActualQty());
        req.setUnit(product.getUnit());
        req.setSourceModule("PRODUCTION");
        req.setSourceRecordId(order.getId());
        req.setReferenceNo(order.getOrderNo());
        req.setClientRequestId("PROD-IN-" + order.getId());
        req.setOperatorUserId(order.getOperatorUserId());
        req.setRecipeId(recipeId);
        req.setRecipeVersion(recipeVersion);

        // ★ StockBusinessException propagates directly — not wrapped as 2002041
        stockEventService.recordEvent(req);
    }

    /**
     * Recursively collect all leaf nodes (nodes with no children) from the explosion tree.
     */
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
