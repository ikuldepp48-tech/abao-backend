package com.geihou.module.supplychain.stock.service;

import com.geihou.module.supplychain.api.stock.dto.StockReleaseReqDTO;
import com.geihou.module.supplychain.api.stock.enums.StockReserveStatusEnum;
import com.geihou.module.supplychain.stock.dal.dataobject.StockReserveDO;
import com.geihou.module.supplychain.stock.dal.mapper.StockReserveMapper;
import com.geihou.module.supplychain.stock.framework.StockBusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of {@link StockReserveCompensationService}.
 *
 * <p>Scans for timed-out RESERVED stock_reserve records and releases them
 * by delegating to {@link StockReserveService#releaseStock}.
 *
 * <p>Conservative compensation:
 * <ul>
 *   <li>Only scans status = RESERVED, deleted = false, create_time &lt; cutoff</li>
 *   <li>Release uses the same CAS-based releaseStock path as the main chain</li>
 *   <li>releaseStock is idempotent: if the record was already released/committed,
 *       it returns silently (RELEASED) or throws (COMMITTED); both are caught and skipped</li>
 *   <li>Single record failure is logged as WARN and does not abort the batch</li>
 * </ul>
 *
 * <p>Configuration:
 * <ul>
 *   <li>{@code stock.compensation.timeout-minutes} — default 30, min 5</li>
 *   <li>{@code stock.compensation.batch-size} — default 500, max 2000</li>
 *   <li>{@code stock.compensation.enabled} — default false (job controls execution)</li>
 * </ul>
 *
 * <p>Source: TASK-G2-01B4.
 */
@Service
public class StockReserveCompensationServiceImpl implements StockReserveCompensationService {

    private static final Logger log = LoggerFactory.getLogger(StockReserveCompensationServiceImpl.class);

    private static final int MIN_TIMEOUT_MINUTES = 5;
    private static final int DEFAULT_TIMEOUT_MINUTES = 30;
    private static final int DEFAULT_BATCH_SIZE = 500;
    private static final int MAX_BATCH_SIZE = 2000;

    private static final long COMPENSATION_OPERATOR_USER_ID = 0L; // system operator

    @Autowired
    private StockReserveMapper stockReserveMapper;

    @Autowired
    private StockReserveService stockReserveService;

    @Value("${stock.compensation.timeout-minutes:" + DEFAULT_TIMEOUT_MINUTES + "}")
    private int timeoutMinutes;

    @Value("${stock.compensation.batch-size:" + DEFAULT_BATCH_SIZE + "}")
    private int batchSize;

    @Value("${stock.compensation.enabled:false}")
    private boolean enabled;

    /**
     * Clamp timeout to minimum 5 minutes.
     */
    int getEffectiveTimeoutMinutes() {
        return Math.max(timeoutMinutes, MIN_TIMEOUT_MINUTES);
    }

    /**
     * Clamp batch size to maximum 2000.
     */
    int getEffectiveBatchSize() {
        return Math.min(Math.max(batchSize, 1), MAX_BATCH_SIZE);
    }

    boolean isEnabled() {
        return enabled;
    }

    @Override
    public CompensationResult executeCompensation() {
        if (!enabled) {
            log.info("Stock reserve compensation is disabled, skipping scan");
            return new CompensationResult(0, 0, 0, 0, List.of());
        }

        int effectiveTimeout = getEffectiveTimeoutMinutes();
        int effectiveBatch = getEffectiveBatchSize();
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(effectiveTimeout);

        log.info("Starting stock reserve compensation scan: cutoff={}, batchSize={}", cutoff, effectiveBatch);

        List<StockReserveDO> candidates = stockReserveMapper.selectTimeoutReserved(
                null, // all tenants
                cutoff,
                effectiveBatch
        );

        if (candidates == null || candidates.isEmpty()) {
            log.info("Stock reserve compensation: no timed-out RESERVED records found");
            return new CompensationResult(0, 0, 0, 0, List.of());
        }

        int released = 0;
        int skipped = 0;
        int failed = 0;
        List<String> failureDetails = new ArrayList<>();

        for (StockReserveDO reserve : candidates) {
            try {
                // Double-check status before release (belt-and-suspenders)
                // releaseStock itself has CAS, but this avoids unnecessary exception overhead
                if (!StockReserveStatusEnum.RESERVED.getCode().equals(reserve.getStatus())) {
                    log.debug("Skipping reserve id={} status={} (not RESERVED)",
                            reserve.getId(), reserve.getStatus());
                    skipped++;
                    continue;
                }

                // Build release request using the original idempotent key
                StockReleaseReqDTO releaseReq = new StockReleaseReqDTO();
                releaseReq.setTenantId(reserve.getTenantId());
                releaseReq.setReserveId(reserve.getId());
                releaseReq.setIdempotentKey(reserve.getIdempotentKey());
                releaseReq.setOperatorUserId(COMPENSATION_OPERATOR_USER_ID);

                stockReserveService.releaseStock(releaseReq);
                released++;
                log.info("Compensation released reserve id={} tenant={} idempotentKey={} quantity={}",
                        reserve.getId(), reserve.getTenantId(), reserve.getIdempotentKey(),
                        reserve.getQuantity());

            } catch (StockBusinessException e) {
                // releaseStock throws if: record already COMMITTED, not found, or CAS conflict
                // These are expected during compensation — log WARN and continue
                log.warn("Compensation release failed for reserve id={} tenant={} status={}: {}",
                        reserve.getId(), reserve.getTenantId(), reserve.getStatus(), e.getMessage());
                skipped++;
            } catch (Exception e) {
                log.warn("Compensation unexpected error for reserve id={} tenant={}: {}",
                        reserve.getId(), reserve.getTenantId(), e.getMessage(), e);
                failed++;
                failureDetails.add("reserveId=" + reserve.getId() + ": " + e.getMessage());
            }
        }

        log.info("Stock reserve compensation complete: scanned={}, released={}, skipped={}, failed={}",
                candidates.size(), released, skipped, failed);

        return new CompensationResult(candidates.size(), released, skipped, failed, failureDetails);
    }
}
