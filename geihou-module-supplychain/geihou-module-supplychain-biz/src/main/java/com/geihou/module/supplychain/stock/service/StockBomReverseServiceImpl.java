package com.geihou.module.supplychain.stock.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geihou.module.supplychain.api.bom.explode.dto.BomExplosionRespDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesOutBomReverseItemRespDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesOutBomReverseReqDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesOutBomReverseRespDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesReverseRestoreItemRespDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesReverseRestoreReqDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesReverseRestoreRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockEventReqDTO;
import com.geihou.module.supplychain.api.stock.enums.StockDirectionEnum;
import com.geihou.module.supplychain.api.stock.enums.StockEventTypeEnum;
import com.geihou.module.supplychain.bom.dal.dataobject.BomRecipeDO;
import com.geihou.module.supplychain.bom.dal.dataobject.ProductMasterDO;
import com.geihou.module.supplychain.bom.dal.mapper.ProductMasterMapper;
import com.geihou.module.supplychain.bom.explode.BomExplosionService;
import com.geihou.module.supplychain.bom.service.BomRecipeService;
import com.geihou.module.supplychain.bom.service.ProductMasterService;
import com.geihou.module.supplychain.stock.dal.dataobject.StockEventDO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockItemDO;
import com.geihou.module.supplychain.stock.dal.mapper.StockEventMapper;
import com.geihou.module.supplychain.stock.framework.StockBusinessException;
import com.geihou.module.supplychain.stock.framework.StockErrorCodeConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Implementation of {@link StockBomReverseService}.
 *
 * <p>Explodes the BOM of a finished product, then records a CONSUME_OUT
 * stock event for each raw-material leaf and each SEMI_FINISHED node.
 * All deductions occur in a single transaction; any failure rolls back
 * every component event and balance change.
 *
 * <p>Architecture constraints:
 * <ul>
 *   <li>No parent stock_event is created — parentEventId is always null.</li>
 *   <li>Uses CONSUME_OUT + OUT for every component deduction (RAW leaves and SEMI nodes).</li>
 *   <li>No SALE_OUT or PRODUCTION_OUT event types are used.</li>
 *   <li>Traceability via sourceModule / sourceRecordId / referenceNo.</li>
 *   <li>Per-component idempotency key: clientRequestId + "::" + componentProductId.</li>
 *   <li>Recipe ID and version are snapshotted onto each event.</li>
 * </ul>
 */
@Service
public class StockBomReverseServiceImpl implements StockBomReverseService {

    @Autowired
    private BomExplosionService bomExplosionService;

    @Autowired
    private BomRecipeService bomRecipeService;

    @Autowired
    private ProductMasterService productMasterService;

    @Autowired
    private ProductMasterMapper productMasterMapper;

    @Autowired
    private StockItemService stockItemService;

    @Autowired
    private StockEventService stockEventService;

    @Autowired
    private StockEventMapper stockEventMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOutBomReverseRespDTO salesOutWithBomReverse(SalesOutBomReverseReqDTO req) {
        // --- 1. Validate required fields ---
        validateRequest(req);

        // --- 2. Resolve the finished product / SKU ---
        Long productId = req.getProductId();
        ProductMasterDO product;

        if (productId != null) {
            product = productMasterService.getById(productId, req.getTenantId());
        } else {
            // skuCode provided, productId null — use ProductMasterMapper.selectOne
            product = productMasterMapper.selectOne(
                    new LambdaQueryWrapper<ProductMasterDO>()
                            .eq(ProductMasterDO::getTenantId, req.getTenantId())
                            .eq(ProductMasterDO::getSkuCode, req.getSkuCode())
                            .eq(ProductMasterDO::getDeleted, false)
            );
            if (product != null) {
                productId = product.getId();
            }
        }

        if (product == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING,
                    "product not found for productId=" + req.getProductId() + " skuCode=" + req.getSkuCode());
        }

        // --- 3. Load the active BOM recipe for snapshot ---
        BomRecipeDO recipe = bomRecipeService.getActiveRecipe(productId, req.getTenantId());
        if (recipe == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING,
                    "no active BOM recipe for productId=" + productId);
        }
        Long recipeId = recipe.getId();
        Integer recipeVersion = recipe.getVersionNo();

        // --- 4. Explode BOM to raw-material leaves ---
        List<BomExplosionRespDTO> explosionTree = bomExplosionService.explode(
                productId, null, req.getQuantity(), req.getTenantId());
        List<BomExplosionRespDTO> leaves = new ArrayList<>();
        collectLeaves(explosionTree, leaves);

        if (leaves.isEmpty()) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING,
                    "BOM explosion produced no raw-material leaves for productId=" + productId);
        }

        // --- 5. For each raw-material leaf, record CONSUME_OUT ---
        List<SalesOutBomReverseItemRespDTO> itemRespList = new ArrayList<>();

        for (BomExplosionRespDTO leaf : leaves) {
            SalesOutBomReverseItemRespDTO itemResp = deductComponent(
                    leaf, req, recipeId, recipeVersion);
            itemRespList.add(itemResp);
        }

        // --- 5b. For each SEMI_FINISHED node, record CONSUME_OUT ---
        // SEMI nodes have children (so they are not leaves) but their own stock
        // must also be deducted. Uses the same CONSUME_OUT event type, null
        // parent_event_id, and same traceability fields as raw-material leaves.
        List<BomExplosionRespDTO> semiNodes = new ArrayList<>();
        collectSemiNodes(explosionTree, semiNodes);

        for (BomExplosionRespDTO semiNode : semiNodes) {
            SalesOutBomReverseItemRespDTO itemResp = deductComponent(
                    semiNode, req, recipeId, recipeVersion);
            itemRespList.add(itemResp);
        }

        // --- 6. Build response ---
        SalesOutBomReverseRespDTO resp = new SalesOutBomReverseRespDTO();
        resp.setTenantId(req.getTenantId());
        resp.setProductId(productId);
        resp.setSkuCode(product.getSkuCode());
        resp.setQuantity(req.getQuantity());
        resp.setRecipeId(recipeId);
        resp.setRecipeVersion(recipeVersion);
        resp.setItems(itemRespList);
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesReverseRestoreRespDTO salesReverseRestore(SalesReverseRestoreReqDTO req) {
        validateRestoreRequest(req);

        List<StockEventDO> originals = findOriginalConsumeOutEvents(req);
        if (originals.isEmpty()) {
            rejectLineScopedRestoreForHistoricalNullSourceOrderItemId(req);
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING,
                    "no original CONSUME_OUT events found for sourceRecordId=" + req.getSourceRecordId());
        }

        List<StockEventDO> existingRestores = findExistingRestoreEvents(req);
        if (!existingRestores.isEmpty()) {
            return handleExistingRestores(req, originals, existingRestores);
        }

        List<SalesReverseRestoreItemRespDTO> items = new ArrayList<>();
        for (StockEventDO original : originals) {
            String restoreClientRequestId = restoreClientRequestId(req.getClientRequestId(), original.getId());

            StockEventReqDTO eventReq = new StockEventReqDTO();
            eventReq.setTenantId(req.getTenantId());
            eventReq.setEventTime(LocalDateTime.now());
            eventReq.setBusinessDate(LocalDate.now());
            // G2-02Q 已将 RETURN_IN 纳入全局枚举表 V2（12 值），语义正确：销售退货还原 = 退料入库
            eventReq.setEventType(StockEventTypeEnum.RETURN_IN.getCode());
            eventReq.setDirection(StockDirectionEnum.IN.getCode());
            eventReq.setStockItemId(original.getStockItemId());
            eventReq.setSkuCode(original.getSkuCode());
            eventReq.setLocationId(original.getLocationId());
            eventReq.setQuantity(original.getQuantity());
            eventReq.setUnit(original.getUnit());
            eventReq.setSourceModule(original.getSourceModule());
            eventReq.setSourceRecordId(original.getSourceRecordId());
            eventReq.setReferenceNo(original.getReferenceNo());
            eventReq.setClientRequestId(restoreClientRequestId);
            eventReq.setOperatorUserId(req.getOperatorUserId());
            eventReq.setParentEventId(null);
            eventReq.setSourceOrderItemId(original.getSourceOrderItemId());
            eventReq.setRecipeId(original.getRecipeId());
            eventReq.setRecipeVersion(original.getRecipeVersion());

            Long restoreEventId = stockEventService.recordEvent(eventReq);
            items.add(buildRestoreItem(original, restoreEventId));
        }

        SalesReverseRestoreRespDTO resp = new SalesReverseRestoreRespDTO();
        resp.setRestoredItemCount(items.size());
        resp.setItems(items);
        return resp;
    }

    // --- Helper methods ---

    private void validateRequest(SalesOutBomReverseReqDTO req) {
        Objects.requireNonNull(req, "request must not be null");
        if (req.getTenantId() == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "tenantId");
        }
        if (req.getProductId() == null && (req.getSkuCode() == null || req.getSkuCode().isBlank())) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING,
                    "either productId or skuCode must be provided");
        }
        if (req.getQuantity() == null || req.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new StockBusinessException(StockErrorCodeConstants.QUANTITY_MUST_BE_POSITIVE);
        }
        if (req.getLocationId() == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "locationId");
        }
        if (req.getSourceModule() == null || req.getSourceModule().isBlank()) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "sourceModule");
        }
        if (req.getSourceRecordId() == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "sourceRecordId");
        }
        if (req.getOperatorUserId() == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "operatorUserId");
        }
        if (req.getClientRequestId() == null || req.getClientRequestId().isBlank()) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "clientRequestId");
        }
    }

    private void validateRestoreRequest(SalesReverseRestoreReqDTO req) {
        Objects.requireNonNull(req, "request must not be null");
        if (req.getTenantId() == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "tenantId");
        }
        if (req.getSourceModule() == null || req.getSourceModule().isBlank()) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "sourceModule");
        }
        if (req.getSourceRecordId() == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "sourceRecordId");
        }
        if (req.getOperatorUserId() == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "operatorUserId");
        }
        if (req.getClientRequestId() == null || req.getClientRequestId().isBlank()) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "clientRequestId");
        }
    }

    private List<StockEventDO> findOriginalConsumeOutEvents(SalesReverseRestoreReqDTO req) {
        LambdaQueryWrapper<StockEventDO> wrapper = new LambdaQueryWrapper<StockEventDO>()
                .eq(StockEventDO::getTenantId, req.getTenantId())
                .eq(StockEventDO::getSourceModule, req.getSourceModule())
                .eq(StockEventDO::getSourceRecordId, req.getSourceRecordId())
                .eq(StockEventDO::getEventType, StockEventTypeEnum.CONSUME_OUT.getCode())
                .orderByAsc(StockEventDO::getId);
        if (req.getReferenceNo() != null) {
            wrapper.eq(StockEventDO::getReferenceNo, req.getReferenceNo());
        }
        if (hasSourceOrderItemScope(req)) {
            wrapper.in(StockEventDO::getSourceOrderItemId, req.getSourceOrderItemIds());
        }
        return stockEventMapper.selectList(wrapper);
    }

    private List<StockEventDO> findOriginalConsumeOutEventsWithoutLineScope(SalesReverseRestoreReqDTO req) {
        LambdaQueryWrapper<StockEventDO> wrapper = new LambdaQueryWrapper<StockEventDO>()
                .eq(StockEventDO::getTenantId, req.getTenantId())
                .eq(StockEventDO::getSourceModule, req.getSourceModule())
                .eq(StockEventDO::getSourceRecordId, req.getSourceRecordId())
                .eq(StockEventDO::getEventType, StockEventTypeEnum.CONSUME_OUT.getCode())
                .orderByAsc(StockEventDO::getId);
        if (req.getReferenceNo() != null) {
            wrapper.eq(StockEventDO::getReferenceNo, req.getReferenceNo());
        }
        return stockEventMapper.selectList(wrapper);
    }

    private void rejectLineScopedRestoreForHistoricalNullSourceOrderItemId(SalesReverseRestoreReqDTO req) {
        if (!hasSourceOrderItemScope(req)) {
            return;
        }
        List<StockEventDO> unscopedOriginals = findOriginalConsumeOutEventsWithoutLineScope(req);
        boolean hasHistoricalNullLine = unscopedOriginals.stream()
                .anyMatch(event -> event.getSourceOrderItemId() == null);
        if (hasHistoricalNullLine) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING,
                    "source_order_item_id missing for line-scoped restore; historical CONSUME_OUT cannot be safely restored for sourceRecordId="
                            + req.getSourceRecordId());
        }
    }

    private List<StockEventDO> findExistingRestoreEvents(SalesReverseRestoreReqDTO req) {
        LambdaQueryWrapper<StockEventDO> wrapper = new LambdaQueryWrapper<StockEventDO>()
                .eq(StockEventDO::getTenantId, req.getTenantId())
                .eq(StockEventDO::getSourceModule, req.getSourceModule())
                .eq(StockEventDO::getSourceRecordId, req.getSourceRecordId())
                .in(StockEventDO::getEventType,
                        StockEventTypeEnum.PURCHASE_IN.getCode(),
                        StockEventTypeEnum.RETURN_IN.getCode())
                .orderByAsc(StockEventDO::getId);
        if (req.getReferenceNo() != null) {
            wrapper.eq(StockEventDO::getReferenceNo, req.getReferenceNo());
        }
        if (hasSourceOrderItemScope(req)) {
            wrapper.in(StockEventDO::getSourceOrderItemId, req.getSourceOrderItemIds());
        }
        return stockEventMapper.selectList(wrapper);
    }

    private boolean hasSourceOrderItemScope(SalesReverseRestoreReqDTO req) {
        return req.getSourceOrderItemIds() != null && !req.getSourceOrderItemIds().isEmpty();
    }

    private SalesReverseRestoreRespDTO handleExistingRestores(SalesReverseRestoreReqDTO req,
                                                              List<StockEventDO> originals,
                                                              List<StockEventDO> existingRestores) {
        String prefix = req.getClientRequestId() + "::restore::";
        boolean sameRequest = existingRestores.stream()
                .allMatch(event -> event.getClientRequestId() != null
                        && event.getClientRequestId().startsWith(prefix));
        if (!sameRequest) {
            throw new StockBusinessException(StockErrorCodeConstants.CLIENT_REQUEST_ID_CONFLICT,
                    "source already restored with a different clientRequestId");
        }

        List<SalesReverseRestoreItemRespDTO> items = new ArrayList<>();
        for (StockEventDO original : originals) {
            String expectedClientRequestId = restoreClientRequestId(req.getClientRequestId(), original.getId());
            StockEventDO restore = existingRestores.stream()
                    .filter(event -> expectedClientRequestId.equals(event.getClientRequestId()))
                    .findFirst()
                    .orElseThrow(() -> new StockBusinessException(StockErrorCodeConstants.CLIENT_REQUEST_ID_CONFLICT,
                            "partial restore exists for sourceRecordId=" + req.getSourceRecordId()));
            items.add(buildRestoreItem(original, restore.getId()));
        }

        SalesReverseRestoreRespDTO resp = new SalesReverseRestoreRespDTO();
        resp.setRestoredItemCount(items.size());
        resp.setItems(items);
        return resp;
    }

    private String restoreClientRequestId(String clientRequestId, Long originalEventId) {
        return clientRequestId + "::restore::" + originalEventId;
    }

    private SalesReverseRestoreItemRespDTO buildRestoreItem(StockEventDO original, Long restoreEventId) {
        SalesReverseRestoreItemRespDTO item = new SalesReverseRestoreItemRespDTO();
        item.setOriginalEventId(original.getId());
        item.setRestoreEventId(restoreEventId);
        item.setStockItemId(original.getStockItemId());
        item.setLocationId(original.getLocationId());
        item.setQuantity(original.getQuantity());
        item.setUnit(original.getUnit());
        item.setRecipeId(original.getRecipeId());
        item.setRecipeVersion(original.getRecipeVersion());
        return item;
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

    /**
     * Recursively collect all SEMI_FINISHED nodes from the explosion tree.
     * These are intermediate nodes that have children but are themselves
     * semi-finished products whose stock should also be deducted.
     */
    private void collectSemiNodes(List<BomExplosionRespDTO> nodes, List<BomExplosionRespDTO> semiNodes) {
        for (BomExplosionRespDTO node : nodes) {
            if ("SEMI_FINISHED".equals(node.getComponentType())) {
                semiNodes.add(node);
            }
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                collectSemiNodes(node.getChildren(), semiNodes);
            }
        }
    }

    /**
     * Deduct one component (raw-material leaf or SEMI_FINISHED node)
     * via StockEventService.recordEvent.
     */
    private SalesOutBomReverseItemRespDTO deductComponent(
            BomExplosionRespDTO leaf,
            SalesOutBomReverseReqDTO req,
            Long recipeId, Integer recipeVersion) {

        Long componentProductId = leaf.getProductId();

        // Resolve component product master for skuCode and unit
        ProductMasterDO componentProduct = productMasterService.getById(componentProductId, req.getTenantId());
        if (componentProduct == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING,
                    "component product not found: " + componentProductId);
        }

        String skuCode = componentProduct.getSkuCode();
        if (skuCode == null || skuCode.isBlank()) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING,
                    "component product has no skuCode: " + componentProductId);
        }

        String unit = leaf.getUnit() != null ? leaf.getUnit() : componentProduct.getUnit();

        // Resolve stock item by skuCode
        StockItemDO stockItem = stockItemService.getBySkuCode(skuCode, req.getTenantId());
        if (stockItem == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING,
                    "stock item not found for skuCode: " + skuCode);
        }

        // Per-component idempotency key
        String perComponentClientRequestId = req.getClientRequestId() + "::" + componentProductId;

        // Build StockEventReqDTO
        StockEventReqDTO eventReq = new StockEventReqDTO();
        eventReq.setTenantId(req.getTenantId());
        eventReq.setEventTime(LocalDateTime.now());
        eventReq.setBusinessDate(LocalDate.now());
        eventReq.setEventType(StockEventTypeEnum.CONSUME_OUT.getCode());
        eventReq.setDirection(StockDirectionEnum.OUT.getCode());
        eventReq.setStockItemId(stockItem.getId());
        eventReq.setSkuCode(skuCode);
        eventReq.setLocationId(req.getLocationId());
        eventReq.setQuantity(leaf.getQuantity());
        eventReq.setUnit(unit);
        eventReq.setSourceModule(req.getSourceModule());
        eventReq.setSourceRecordId(req.getSourceRecordId());
        eventReq.setSourceOrderItemId(req.getSourceOrderItemId());
        eventReq.setReferenceNo(req.getReferenceNo());
        eventReq.setClientRequestId(perComponentClientRequestId);
        eventReq.setOperatorUserId(req.getOperatorUserId());
        // Explicitly null — no parent stock_event
        eventReq.setParentEventId(null);
        // Recipe snapshot
        eventReq.setRecipeId(recipeId);
        eventReq.setRecipeVersion(recipeVersion);

        Long eventId = stockEventService.recordEvent(eventReq);

        // Build item response
        SalesOutBomReverseItemRespDTO itemResp = new SalesOutBomReverseItemRespDTO();
        itemResp.setComponentProductId(componentProductId);
        itemResp.setSkuCode(skuCode);
        itemResp.setUnit(unit);
        itemResp.setStockItemId(stockItem.getId());
        itemResp.setQuantity(leaf.getQuantity());
        itemResp.setEventId(eventId);
        itemResp.setClientRequestId(perComponentClientRequestId);
        itemResp.setRecipeId(recipeId);
        itemResp.setRecipeVersion(recipeVersion);
        return itemResp;
    }
}
