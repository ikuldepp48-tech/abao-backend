package com.geihou.module.supplychain.stock.service;

import com.geihou.module.supplychain.api.stock.dto.StockCoverageDetailRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockCoverageGateResultRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockCoverageReportRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockCoverageTypeSummaryRespDTO;
import com.geihou.module.supplychain.api.stock.enums.StockCoverageGateStatusEnum;
import com.geihou.module.supplychain.api.stock.enums.StockCoverageModeEnum;
import com.geihou.module.supplychain.api.stock.enums.StockCoverageTypeEnum;
import com.geihou.module.supplychain.stock.dal.dataobject.StockItemDO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockLocationDO;
import com.geihou.module.supplychain.stock.dal.dataobject.StockMappingCoverageAuditDO;
import com.geihou.module.supplychain.stock.dal.mapper.StockItemMapper;
import com.geihou.module.supplychain.stock.dal.mapper.StockLocationMapper;
import com.geihou.module.supplychain.stock.dal.mapper.StockMappingCoverageAuditMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Read-only implementation of {@link StockMappingCoverageReportService} (G2-01B3C).
 *
 * <p>Groups B3B audit rows by coverage_type, counts total and unresolved observations,
 * lists capped unresolved details, and evaluates the readiness gate from the report.
 *
 * <p>Resolution check (conservative, no resolved_at column):
 * <ul>
 *   <li>STOCK_ITEM_MISSING → resolved if active stock_item exists for tenant + sku_code</li>
 *   <li>LOCATION_MISSING → resolved if active stock_location exists for tenant + store_id + location_type</li>
 *   <li>SKU_CODE_MISSING → always unresolved (no SKU-code remediation data yet)</li>
 * </ul>
 */
@Service
public class StockMappingCoverageReportServiceImpl implements StockMappingCoverageReportService {

    private final StockMappingCoverageAuditMapper auditMapper;
    private final StockItemMapper stockItemMapper;
    private final StockLocationMapper stockLocationMapper;
    private final String modeProperty;
    private final int maxDetails;

    public StockMappingCoverageReportServiceImpl(StockMappingCoverageAuditMapper auditMapper,
                                                   StockItemMapper stockItemMapper,
                                                   StockLocationMapper stockLocationMapper,
                                                   @Value("${geihou.stock.mapping.coverage.mode:AUDIT_ONLY}") String modeProperty,
                                                   @Value("${geihou.stock.mapping.coverage.report.max-details:100}") int maxDetails) {
        this.auditMapper = auditMapper;
        this.stockItemMapper = stockItemMapper;
        this.stockLocationMapper = stockLocationMapper;
        this.modeProperty = modeProperty;
        this.maxDetails = Math.max(1, maxDetails);
    }

    @Override
    public StockCoverageReportRespDTO generateReport(Long tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        StockCoverageModeEnum mode = resolveMode();
        List<StockMappingCoverageAuditDO> allRows = auditMapper.selectAllByTenant(tenantId);

        // Per-type counters
        Map<String, int[]> typeCounts = new HashMap<>(); // [total, unresolved]
        for (StockCoverageTypeEnum t : StockCoverageTypeEnum.values()) {
            typeCounts.put(t.getCode(), new int[]{0, 0});
        }

        List<StockCoverageDetailRespDTO> unresolvedDetails = new ArrayList<>();
        int totalObservations = 0;
        int totalUnresolved = 0;

        for (StockMappingCoverageAuditDO row : allRows) {
            totalObservations++;
            int[] counts = typeCounts.computeIfAbsent(row.getCoverageType(), k -> new int[]{0, 0});
            counts[0]++;

            boolean resolved = isResolved(tenantId, row);
            if (!resolved) {
                totalUnresolved++;
                counts[1]++;
                if (unresolvedDetails.size() < maxDetails) {
                    unresolvedDetails.add(toDetailDTO(row));
                }
            }
        }

        // Build per-type summaries (in enum order)
        List<StockCoverageTypeSummaryRespDTO> summaries = new ArrayList<>();
        for (StockCoverageTypeEnum t : StockCoverageTypeEnum.values()) {
            int[] counts = typeCounts.get(t.getCode());
            summaries.add(new StockCoverageTypeSummaryRespDTO(t.getCode(), counts[0], counts[1]));
        }

        StockCoverageReportRespDTO report = new StockCoverageReportRespDTO();
        report.setTenantId(tenantId);
        report.setGeneratedTime(LocalDateTime.now());
        report.setMode(mode);
        report.setTotalObservations(totalObservations);
        report.setTotalUnresolved(totalUnresolved);
        report.setTypeSummaries(summaries);
        report.setUnresolvedDetails(unresolvedDetails);
        return report;
    }

    @Override
    public StockCoverageGateResultRespDTO evaluateGate(Long tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        StockCoverageModeEnum mode = resolveMode();
        StockCoverageReportRespDTO report = generateReport(tenantId);

        StockCoverageGateStatusEnum status;
        List<String> blockReasons = new ArrayList<>();

        if (report.getTotalUnresolved() == 0) {
            // AC-4, AC-7: zero unresolved → READY (both modes)
            status = StockCoverageGateStatusEnum.READY;
        } else if (mode == StockCoverageModeEnum.AUDIT_ONLY) {
            // AC-3: AUDIT_ONLY with unresolved → READY_WITH_WARNINGS, never NOT_READY
            status = StockCoverageGateStatusEnum.READY_WITH_WARNINGS;
        } else {
            // ENFORCE mode with unresolved observations
            // AC-5: LOCATION_MISSING or STOCK_ITEM_MISSING unresolved → NOT_READY
            // AC-6: only SKU_CODE_MISSING unresolved → READY_WITH_WARNINGS
            boolean hasLocationMissing = false;
            boolean hasStockItemMissing = false;
            boolean hasSkuCodeMissing = false;

            for (StockCoverageTypeSummaryRespDTO summary : report.getTypeSummaries()) {
                if (summary.getUnresolved() > 0) {
                    if (StockCoverageTypeEnum.LOCATION_MISSING.getCode().equals(summary.getCoverageType())) {
                        hasLocationMissing = true;
                    } else if (StockCoverageTypeEnum.STOCK_ITEM_MISSING.getCode().equals(summary.getCoverageType())) {
                        hasStockItemMissing = true;
                    } else if (StockCoverageTypeEnum.SKU_CODE_MISSING.getCode().equals(summary.getCoverageType())) {
                        hasSkuCodeMissing = true;
                    }
                }
            }

            if (hasLocationMissing) {
                blockReasons.add("Unresolved LOCATION_MISSING observations in ENFORCE mode");
            }
            if (hasStockItemMissing) {
                blockReasons.add("Unresolved STOCK_ITEM_MISSING observations in ENFORCE mode");
            }

            if (hasLocationMissing || hasStockItemMissing) {
                status = StockCoverageGateStatusEnum.NOT_READY;
            } else {
                // Only SKU_CODE_MISSING (or unknown types) → READY_WITH_WARNINGS
                status = StockCoverageGateStatusEnum.READY_WITH_WARNINGS;
            }
        }

        StockCoverageGateResultRespDTO gate = new StockCoverageGateResultRespDTO();
        gate.setStatus(status);
        gate.setMode(mode);
        gate.setTotalUnresolved(report.getTotalUnresolved());
        gate.setBlockReasons(blockReasons);
        return gate;
    }

    // --- Private helpers ---

    private boolean isResolved(Long tenantId, StockMappingCoverageAuditDO row) {
        String type = row.getCoverageType();
        if (StockCoverageTypeEnum.STOCK_ITEM_MISSING.getCode().equals(type)) {
            // Check stock_item by tenant + sku_code, active and not deleted
            if (row.getSkuCode() == null || row.getSkuCode().isBlank()) {
                return false;
            }
            StockItemDO item = stockItemMapper.selectActiveByTenantSkuCode(tenantId, row.getSkuCode());
            return item != null;
        } else if (StockCoverageTypeEnum.LOCATION_MISSING.getCode().equals(type)) {
            // Check stock_location by tenant + store_id + location_type, active and not deleted
            if (row.getStoreId() == null || row.getLocationType() == null) {
                return false;
            }
            StockLocationDO loc = stockLocationMapper.selectActiveByTenantStoreIdType(
                    tenantId, row.getStoreId(), row.getLocationType());
            return loc != null;
        } else {
            // SKU_CODE_MISSING → always unresolved
            return false;
        }
    }

    private StockCoverageDetailRespDTO toDetailDTO(StockMappingCoverageAuditDO row) {
        StockCoverageDetailRespDTO dto = new StockCoverageDetailRespDTO();
        dto.setCoverageType(row.getCoverageType());
        dto.setSkuId(row.getSkuId());
        dto.setSkuCode(row.getSkuCode());
        dto.setStoreId(row.getStoreId());
        dto.setLocationType(row.getLocationType());
        dto.setSourceModule(row.getSourceModule());
        dto.setSourceRecordId(row.getSourceRecordId());
        dto.setIdempotentKey(row.getIdempotentKey());
        dto.setMode(row.getMode());
        dto.setFirstSeenTime(row.getFirstSeenTime());
        dto.setLastSeenTime(row.getLastSeenTime());
        dto.setSeenCount(row.getSeenCount());
        return dto;
    }

    private StockCoverageModeEnum resolveMode() {
        if (modeProperty == null || modeProperty.isBlank()) {
            return StockCoverageModeEnum.AUDIT_ONLY;
        }
        try {
            return StockCoverageModeEnum.fromCode(modeProperty.trim());
        } catch (IllegalArgumentException ignored) {
            return StockCoverageModeEnum.AUDIT_ONLY;
        }
    }
}
