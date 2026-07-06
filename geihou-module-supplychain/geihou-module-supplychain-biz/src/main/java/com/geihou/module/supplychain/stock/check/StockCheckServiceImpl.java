package com.geihou.module.supplychain.stock.check;

import com.geihou.module.supplychain.api.bom.explode.dto.BomExplosionRespDTO;
import com.geihou.module.supplychain.api.stock.dto.SkuQuantityDTO;
import com.geihou.module.supplychain.api.stock.dto.StockCheckItemResultDTO;
import com.geihou.module.supplychain.api.stock.dto.StockCheckRespDTO;
import com.geihou.module.supplychain.bom.dal.dataobject.ProductMasterDO;
import com.geihou.module.supplychain.bom.dal.mapper.ProductMasterMapper;
import com.geihou.module.supplychain.bom.explode.BomExplosionService;
import com.geihou.module.supplychain.stock.dal.dataobject.StockItemDO;
import com.geihou.module.supplychain.stock.dal.mapper.StockBalanceMapper;
import com.geihou.module.supplychain.stock.dal.mapper.StockItemMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of {@link StockCheckService} (G2-02C).
 *
 * <p>Read-only BOM-aware stock sufficiency preflight check. For each input SKU:
 * <ol>
 *   <li>Resolves the finished-product in {@code product_master} by product_code (fallback: sku_code)</li>
 *   <li>Explodes the BOM tree via {@link BomExplosionService}</li>
 *   <li>Aggregates RAW_MATERIAL leaf component requirements across all input SKUs</li>
 *   <li>Maps each component to {@code stock_item} via sku_code</li>
 *   <li>Reads {@code stock_balance.available_qty} summed across locations</li>
 *   <li>Returns per-component SUFFICIENT / INSUFFICIENT / UNMAPPED status</li>
 * </ol>
 *
 * <p><b>Read-only</b>: performs zero writes to stock_event, stock_balance, or stock_reserve.
 *
 * <p>Source: TASK-G2-02C.
 */
@Service
public class StockCheckServiceImpl implements StockCheckService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    @Autowired
    private ProductMasterMapper productMasterMapper;
    @Autowired
    private BomExplosionService bomExplosionService;
    @Autowired
    private StockItemMapper stockItemMapper;
    @Autowired
    private StockBalanceMapper stockBalanceMapper;

    @Override
    public StockCheckRespDTO checkStock(Long tenantId, List<SkuQuantityDTO> items) {
        StockCheckRespDTO resp = new StockCheckRespDTO();
        resp.setTenantId(tenantId);
        resp.setAllSufficient(true);

        if (items == null || items.isEmpty()) {
            return resp;
        }

        // Aggregated requirements keyed by productCode (raw material leaf)
        Map<String, BigDecimal> requiredMap = new LinkedHashMap<>();
        Map<String, String> productNameMap = new HashMap<>();
        Map<String, Long> productIdMap = new HashMap<>();

        for (SkuQuantityDTO sq : items) {
            if (sq == null || sq.getSkuCode() == null || sq.getSkuCode().isBlank()) {
                continue; // SKIP invalid entry
            }

            // 1. Resolve finished product: try product_code first, then sku_code
            ProductMasterDO product = productMasterMapper.selectByTenantProductCode(tenantId, sq.getSkuCode());
            if (product == null) {
                product = resolveBySkuCode(tenantId, sq.getSkuCode());
            }
            if (product == null) {
                // SKIP — cannot resolve SKU to a product_master
                continue;
            }

            BigDecimal qty = sq.getQuantity();
            if (qty == null || qty.compareTo(ZERO) <= 0) {
                qty = BigDecimal.ONE;
            }

            // 2. Explode BOM
            List<BomExplosionRespDTO> explosion = bomExplosionService.explode(
                    product.getId(), null, qty, tenantId);

            // 3. Flatten tree and collect RAW_MATERIAL nodes
            collectRawMaterials(explosion, requiredMap, productNameMap, productIdMap);
        }

        // 4. Build per-component results
        boolean allSufficient = true;
        List<StockCheckItemResultDTO> results = new ArrayList<>();
        List<StockCheckItemResultDTO> unmappedItems = new ArrayList<>();

        for (Map.Entry<String, BigDecimal> entry : requiredMap.entrySet()) {
            String productCode = entry.getKey();
            BigDecimal requiredQty = entry.getValue().setScale(4, java.math.RoundingMode.HALF_UP);

            StockCheckItemResultDTO result = new StockCheckItemResultDTO();
            result.setProductCode(productCode);
            result.setProductName(productNameMap.get(productCode));
            result.setRequiredQty(requiredQty);

            // Set productId from BOM explosion node
            Long componentProductId = productIdMap.get(productCode);
            result.setProductId(componentProductId);

            // Resolve skuCode from product_master (sku_code, or product_code fallback)
            String skuCode = productCode; // fallback
            if (componentProductId != null) {
                ProductMasterDO compProduct = productMasterMapper.selectByIdAndTenant(componentProductId, tenantId);
                if (compProduct != null && compProduct.getSkuCode() != null && !compProduct.getSkuCode().isBlank()) {
                    skuCode = compProduct.getSkuCode();
                }
            }
            result.setSkuCode(skuCode);

            // 5. Map to stock_item
            StockItemDO stockItem = stockItemMapper.selectActiveByTenantSkuCode(tenantId, skuCode);
            if (stockItem == null) {
                result.setAvailableQty(ZERO);
                result.setStatus("UNMAPPED");
                result.setStockItemId(null);
                allSufficient = false;
                unmappedItems.add(result);
            } else {
                result.setStockItemId(stockItem.getId());
                // 6. Sum available_qty across all locations
                BigDecimal availableQty = stockBalanceMapper.sumAvailableQtyByTenantItem(tenantId, stockItem.getId());
                if (availableQty == null) {
                    availableQty = ZERO;
                }
                result.setAvailableQty(availableQty);
                if (availableQty.compareTo(requiredQty) >= 0) {
                    result.setStatus("SUFFICIENT");
                } else {
                    result.setStatus("INSUFFICIENT");
                    allSufficient = false;
                }
            }

            results.add(result);
        }

        resp.setItems(results);
        resp.setUnmappedItems(unmappedItems);
        resp.setAllSufficient(allSufficient);
        return resp;
    }

    /**
     * Recursively traverse the BOM explosion tree and collect all RAW_MATERIAL nodes,
     * aggregating their quantities by productCode.
     */
    private void collectRawMaterials(List<BomExplosionRespDTO> nodes,
                                      Map<String, BigDecimal> requiredMap,
                                      Map<String, String> productNameMap,
                                      Map<String, Long> productIdMap) {
        if (nodes == null) return;
        for (BomExplosionRespDTO node : nodes) {
            if ("RAW_MATERIAL".equals(node.getComponentType())) {
                String key = node.getProductCode() != null
                        ? node.getProductCode()
                        : String.valueOf(node.getProductId());
                BigDecimal qty = node.getQuantity() != null ? node.getQuantity() : ZERO;
                requiredMap.merge(key, qty, BigDecimal::add);
                productNameMap.putIfAbsent(key, node.getProductName());
                productIdMap.putIfAbsent(key, node.getProductId());
            }
            // Recurse into children (multi-level BOM)
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                collectRawMaterials(node.getChildren(), requiredMap, productNameMap, productIdMap);
            }
        }
    }

    /**
     * Fallback: resolve product by sku_code via listByTenant + Java filter.
     * (ProductMasterMapper does not have a dedicated selectByTenantSkuCode method.)
     */
    private ProductMasterDO resolveBySkuCode(Long tenantId, String skuCode) {
        List<ProductMasterDO> all = productMasterMapper.listByTenant(tenantId);
        if (all == null) return null;
        for (ProductMasterDO p : all) {
            if (skuCode.equals(p.getSkuCode())) {
                return p;
            }
        }
        return null;
    }
}
