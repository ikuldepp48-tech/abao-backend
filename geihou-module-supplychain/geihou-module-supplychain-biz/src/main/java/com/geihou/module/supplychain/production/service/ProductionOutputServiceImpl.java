package com.geihou.module.supplychain.production.service;

import com.geihou.module.supplychain.production.controller.admin.vo.ScanOutputReqVO;
import com.geihou.module.supplychain.production.dal.dataobject.ProductionOrderDO;
import com.geihou.module.supplychain.production.dal.dataobject.ProductionOrderOutputDO;
import com.geihou.module.supplychain.production.dal.mapper.ProductionOrderMapper;
import com.geihou.module.supplychain.production.dal.mapper.ProductionOrderOutputMapper;
import com.geihou.module.supplychain.stock.framework.StockBusinessException;
import com.geihou.module.supplychain.stock.framework.StockErrorCodeConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Implementation of {@link ProductionOutputService}.
 *
 * <p>Handles scan-output (产出登记): validates order state and output SKU,
 * writes PRODUCTION_IN stock_event via {@link ProductionStockEventIntegrationService},
 * then inserts output record (INSERT-only).
 *
 * <p>Key constraints (TASK-G2-02N):
 * <ul>
 *   <li>Only IN_PROGRESS stage allows scan-output</li>
 *   <li>Output SKU must match the order's productId</li>
 *   <li>outputSeq must be unique per (tenant, order)</li>
 *   <li>output record is INSERT-only — no update/delete</li>
 *   <li>StockBusinessException propagates directly</li>
 * </ul>
 *
 * <p>Source: TASK-G2-02N.
 */
@Service
public class ProductionOutputServiceImpl implements ProductionOutputService {

    private static final String STAGE_IN_PROGRESS = "IN_PROGRESS";

    @Autowired
    private ProductionOrderMapper productionOrderMapper;

    @Autowired
    private ProductionOrderOutputMapper outputMapper;

    @Autowired
    private ProductionStockEventIntegrationService stockEventIntegrationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductionOrderOutputDO scanOutput(ScanOutputReqVO req) {
        Objects.requireNonNull(req, "request must not be null");
        if (req.getTenantId() == null) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "tenantId");
        }
        if (req.getActualOutputQty() == null || req.getActualOutputQty().compareTo(BigDecimal.ZERO) <= 0) {
            throw new StockBusinessException(StockErrorCodeConstants.QUANTITY_MUST_BE_POSITIVE);
        }
        if (req.getOutputSeq() == null || req.getOutputSeq() <= 0) {
            throw new StockBusinessException(StockErrorCodeConstants.REQUIRED_FIELD_MISSING, "outputSeq");
        }

        // 1. Find existing order
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

        // 3. Validate output SKU matches order product
        // If outputProductId is provided, it must match the order's productId (semi-finished).
        // If not provided, default to the order's productId (backward compatible).
        Long effectiveOutputProductId = req.getOutputProductId() != null
                ? req.getOutputProductId() : existing.getProductId();
        if (!effectiveOutputProductId.equals(existing.getProductId())) {
            throw new StockBusinessException(StockErrorCodeConstants.PRODUCTION_OUTPUT_SKU_MISMATCH,
                    "outputProductId " + effectiveOutputProductId + " does not match order productId " + existing.getProductId());
        }

        // 4. Check outputSeq uniqueness (幂等冲突)
        List<ProductionOrderOutputDO> existingOutputs = outputMapper.listByOrder(
                req.getTenantId(), existing.getId());
        for (ProductionOrderOutputDO o : existingOutputs) {
            if (o.getOutputSeq().equals(req.getOutputSeq())) {
                throw new StockBusinessException(StockErrorCodeConstants.PRODUCTION_SEQ_ALREADY_EXISTS,
                        "outputSeq " + req.getOutputSeq() + " already exists");
            }
        }

        // 5. Write PRODUCTION_IN stock_event (reuses mapping chain)
        String clientRequestId = "PROD-OUTPUT-" + existing.getId() + "-" + req.getOutputSeq();
        Long stockEventId = stockEventIntegrationService.recordProductionIn(
                existing, req.getActualOutputQty(), clientRequestId);

        // 6. Insert output record (INSERT-only)
        ProductionOrderOutputDO output = new ProductionOrderOutputDO();
        output.setTenantId(req.getTenantId());
        output.setProductionOrderId(existing.getId());
        output.setOutputSkuId(effectiveOutputProductId); // always the order's semi-finished product
        output.setOutputSeq(req.getOutputSeq());
        output.setActualOutputQty(req.getActualOutputQty());
        output.setStockEventId(stockEventId);
        output.setBatchNo(req.getBatchNo());
        output.setProducedTime(req.getProducedTime() != null ? req.getProducedTime() : LocalDateTime.now());
        // expireTime left null (optional, G2-03)
        String operator = req.getOperatorUserId() != null
                ? String.valueOf(req.getOperatorUserId()) : "system";
        output.setCreator(operator);
        output.setCreateTime(LocalDateTime.now());
        output.setUpdater(operator);
        output.setUpdateTime(LocalDateTime.now());
        output.setDeleted(false);

        outputMapper.insert(output);

        return output;
    }

    @Override
    public List<ProductionOrderOutputDO> listOutputs(Long orderId, Long tenantId) {
        return outputMapper.listByOrder(tenantId, orderId);
    }
}
