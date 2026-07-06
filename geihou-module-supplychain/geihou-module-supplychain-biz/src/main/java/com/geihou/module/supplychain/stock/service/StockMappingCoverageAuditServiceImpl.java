package com.geihou.module.supplychain.stock.service;

import com.geihou.module.supplychain.api.stock.dto.StockCoverageDecisionRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockCoverageObserveReqDTO;
import com.geihou.module.supplychain.api.stock.enums.StockCoverageModeEnum;
import com.geihou.module.supplychain.stock.dal.dataobject.StockMappingCoverageAuditDO;
import com.geihou.module.supplychain.stock.dal.mapper.StockMappingCoverageAuditMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class StockMappingCoverageAuditServiceImpl implements StockMappingCoverageAuditService {

    private final StockMappingCoverageAuditMapper mapper;
    private final String modeProperty;

    public StockMappingCoverageAuditServiceImpl(StockMappingCoverageAuditMapper mapper,
                                                 @Value("${geihou.stock.mapping.coverage.mode:AUDIT_ONLY}") String modeProperty) {
        this.mapper = mapper;
        this.modeProperty = modeProperty;
    }

    @Override
    public StockCoverageDecisionRespDTO observeMissingMapping(StockCoverageObserveReqDTO req) {
        validate(req);
        StockCoverageModeEnum mode = resolveMode();
        LocalDateTime now = LocalDateTime.now();
        String coverageType = req.getCoverageType().getCode();
        String idempotentKey = req.getIdempotentKey() == null || req.getIdempotentKey().isBlank()
                ? buildFallbackKey(req)
                : req.getIdempotentKey();

        StockMappingCoverageAuditDO existing = mapper.selectLogical(req.getTenantId(), coverageType,
                req.getSourceModule(), req.getSourceRecordId(), idempotentKey);
        if (existing == null) {
            StockMappingCoverageAuditDO row = new StockMappingCoverageAuditDO();
            row.setTenantId(req.getTenantId());
            row.setCoverageType(coverageType);
            row.setSkuId(req.getSkuId());
            row.setSkuCode(req.getSkuCode());
            row.setStoreId(req.getStoreId());
            row.setLocationType(req.getLocationType());
            row.setSourceModule(req.getSourceModule());
            row.setSourceRecordId(req.getSourceRecordId());
            row.setIdempotentKey(idempotentKey);
            row.setMode(mode.getCode());
            row.setFirstSeenTime(now);
            row.setLastSeenTime(now);
            row.setSeenCount(1);
            row.setCreator("system");
            row.setCreateTime(now);
            row.setUpdater("system");
            row.setUpdateTime(now);
            row.setDeleted(false);
            mapper.insert(row);
        } else {
            mapper.incrementSeen(existing.getId(), req.getTenantId(), mode.getCode(), now, "system", now);
        }

        return new StockCoverageDecisionRespDTO(mode, mode == StockCoverageModeEnum.ENFORCE);
    }

    @Override
    public List<StockMappingCoverageAuditDO> listRecent(Long tenantId, int limit) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return mapper.selectRecentByTenant(tenantId, safeLimit);
    }

    private void validate(StockCoverageObserveReqDTO req) {
        Objects.requireNonNull(req, "req must not be null");
        Objects.requireNonNull(req.getTenantId(), "tenantId must not be null");
        Objects.requireNonNull(req.getCoverageType(), "coverageType must not be null");
        Objects.requireNonNull(req.getSourceModule(), "sourceModule must not be null");
        Objects.requireNonNull(req.getSourceRecordId(), "sourceRecordId must not be null");
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

    private String buildFallbackKey(StockCoverageObserveReqDTO req) {
        return req.getCoverageType().getCode() + ":skuId=" + req.getSkuId()
                + ":skuCode=" + req.getSkuCode()
                + ":storeId=" + req.getStoreId()
                + ":locationType=" + req.getLocationType();
    }
}
