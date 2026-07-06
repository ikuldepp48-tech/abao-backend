package com.geihou.module.supplychain.stock.job;

import com.geihou.module.supplychain.stock.service.StockReserveCompensationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that triggers stock reserve compensation scan.
 *
 * <p>Default disabled. To enable, set {@code stock.compensation.enabled=true} and
 * ensure {@code @EnableScheduling} is present on a configuration class.
 *
 * <p>Cron expression is configurable via {@code stock.compensation.cron},
 * defaulting to every 5 minutes ({@code 0 *​/5 * * * ?}).
 *
 * <p>The job delegates entirely to {@link StockReserveCompensationService#executeCompensation},
 * which performs the conservative scan-and-release logic.
 *
 * <p>Source: TASK-G2-01B4.
 */
@Component
public class StockReserveCompensationJob {

    private static final Logger log = LoggerFactory.getLogger(StockReserveCompensationJob.class);

    @Autowired
    private StockReserveCompensationService compensationService;

    @Value("${stock.compensation.enabled:false}")
    private boolean enabled;

    /**
     * Execute compensation scan on schedule.
     *
     * <p>The service itself also checks the enabled flag, but we short-circuit here
     * to avoid unnecessary log noise when disabled.
     */
    @Scheduled(cron = "${stock.compensation.cron:0 */5 * * * ?}")
    public void execute() {
        if (!enabled) {
            return;
        }

        try {
            log.info("StockReserveCompensationJob triggered");
            StockReserveCompensationService.CompensationResult result =
                    compensationService.executeCompensation();
            log.info("StockReserveCompensationJob completed: scanned={}, released={}, skipped={}, failed={}",
                    result.getScanned(), result.getReleased(), result.getSkipped(), result.getFailed());
        } catch (Exception e) {
            log.error("StockReserveCompensationJob failed unexpectedly", e);
        }
    }
}
