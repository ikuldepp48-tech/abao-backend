package com.geihou.module.supplychain.stock.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geihou.module.supplychain.api.bom.preview.dto.BomCostPreviewRespDTO;
import com.geihou.module.supplychain.api.stock.dto.ProductCurrentCostRespDTO;
import com.geihou.module.supplychain.bom.preview.BomCostPreviewService;
import com.geihou.module.supplychain.stock.dal.dataobject.StockBalanceDO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockItemDO;
import com.geihou.module.supplychain.stock.dal.mapper.StockBalanceMapper;
import com.geihou.module.supplychain.stock.dal.mapper.StockItemMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Read-only current cost estimate based on active BOM and stock balance cost.
 *
 * <p>Source: TASK-G2-02G.
 */
@Service
public class ProductCurrentCostServiceImpl implements ProductCurrentCostService {

    private static final BigDecimal DEFAULT_QUANTITY = BigDecimal.ONE;
    private static final int SCALE = 6;

    private final BomCostPreviewService bomCostPreviewService;
    private final StockItemMapper stockItemMapper;
    private final StockBalanceMapper stockBalanceMapper;

    public ProductCurrentCostServiceImpl(BomCostPreviewService bomCostPreviewService,
                                         StockItemMapper stockItemMapper,
                                         StockBalanceMapper stockBalanceMapper) {
        this.bomCostPreviewService = bomCostPreviewService;
        this.stockItemMapper = stockItemMapper;
        this.stockBalanceMapper = stockBalanceMapper;
    }

    @Override
    public ProductCurrentCostRespDTO getProductCurrentCost(Long tenantId, Long productId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(productId, "productId must not be null");

        List<BomCostPreviewRespDTO> previewRows = bomCostPreviewService.previewCost(
                productId, null, DEFAULT_QUANTITY, tenantId);

        ProductCurrentCostRespDTO resp = new ProductCurrentCostRespDTO();
        resp.setTenantId(tenantId);
        resp.setProductId(productId);
        resp.setRequestedQuantity(DEFAULT_QUANTITY);
        resp.setCostSource("STOCK_BALANCE_AVG_UNIT_COST");
        resp.setComponents(new ArrayList<>());
        resp.setComponentCount(previewRows.size());

        BigDecimal total = BigDecimal.ZERO;
        int costedCount = 0;
        for (BomCostPreviewRespDTO row : previewRows) {
            ProductCurrentCostRespDTO.ComponentCost component = new ProductCurrentCostRespDTO.ComponentCost();
            component.setProductId(row.getProductId());
            component.setProductCode(row.getProductCode());
            component.setProductName(row.getProductName());
            component.setQuantity(defaultZero(row.getTotalQuantity()));
            component.setUnit(row.getUnit());

            BigDecimal unitCost = findWeightedAvgUnitCost(tenantId, row.getProductCode());
            component.setUnitCost(unitCost);
            component.setCostAvailable(unitCost != null);
            if (unitCost != null) {
                BigDecimal componentCost = component.getQuantity().multiply(unitCost)
                        .setScale(SCALE, RoundingMode.HALF_UP);
                component.setTotalCost(componentCost);
                total = total.add(componentCost);
                costedCount++;
            }
            resp.getComponents().add(component);
        }

        resp.setCostedComponentCount(costedCount);
        resp.setCurrentCost(costedCount == 0 ? null : total.setScale(SCALE, RoundingMode.HALF_UP));
        if (previewRows.isEmpty()) {
            resp.setCalculationMode("NO_ACTIVE_BOM");
        } else if (costedCount == 0) {
            resp.setCalculationMode("NO_COST_DATA");
        } else if (costedCount < previewRows.size()) {
            resp.setCalculationMode("PARTIAL_ESTIMATE");
        } else {
            resp.setCalculationMode("BOM_STOCK_BALANCE_ESTIMATE");
        }
        return resp;
    }

    private BigDecimal findWeightedAvgUnitCost(Long tenantId, String productCode) {
        if (productCode == null || productCode.isBlank()) {
            return null;
        }
        StockItemDO stockItem = stockItemMapper.selectActiveByTenantSkuCode(tenantId, productCode);
        if (stockItem == null) {
            return null;
        }
        List<StockBalanceDO> balances = stockBalanceMapper.selectList(
                new LambdaQueryWrapper<StockBalanceDO>()
                        .eq(StockBalanceDO::getTenantId, tenantId)
                        .eq(StockBalanceDO::getStockItemId, stockItem.getId())
                        .eq(StockBalanceDO::getDeleted, false));
        BigDecimal weightedCost = BigDecimal.ZERO;
        BigDecimal weightedQty = BigDecimal.ZERO;
        BigDecimal fallbackCost = null;
        for (StockBalanceDO balance : balances) {
            BigDecimal avgUnitCost = balance.getAvgUnitCost();
            if (avgUnitCost == null) {
                continue;
            }
            fallbackCost = avgUnitCost;
            BigDecimal qty = defaultZero(balance.getTotalQty());
            if (qty.compareTo(BigDecimal.ZERO) > 0) {
                weightedCost = weightedCost.add(avgUnitCost.multiply(qty));
                weightedQty = weightedQty.add(qty);
            }
        }
        if (weightedQty.compareTo(BigDecimal.ZERO) > 0) {
            return weightedCost.divide(weightedQty, SCALE, RoundingMode.HALF_UP);
        }
        return fallbackCost;
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
