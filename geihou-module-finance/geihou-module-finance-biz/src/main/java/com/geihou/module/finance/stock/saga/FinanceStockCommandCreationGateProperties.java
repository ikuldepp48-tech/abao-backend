package com.geihou.module.finance.stock.saga;

import com.geihou.module.finance.stock.saga.enums.FinanceStockTransportMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the finance stock command creation gate
 * (G0-04H185 FIN-CONSISTENCY slice 2C-2D).
 *
 * <p>Binds {@code geihou.finance.stock.command.creation-gate.*}. The validator
 * ({@link FinanceStockCommandCreationGateValidator}) fails application startup
 * when {@code transportMode = LOCAL_API_V1} is combined with
 * {@code c0-journal-available = true} - an impossible combination that would
 * silently produce non-durable RESERVE commands.
 *
 * <p>Defaults: {@code LOCAL_API_V1} transport (no C0 journaling yet) and
 * {@code c0-journal-available = false}.
 */
@ConfigurationProperties(prefix = "geihou.finance.stock.command.creation-gate")
@Component
public class FinanceStockCommandCreationGateProperties {

    /** Publish fence for the command transport. */
    private FinanceStockTransportMode transportMode = FinanceStockTransportMode.LOCAL_API_V1;

    /** Whether the C0 journal is available for the configured transport. */
    private boolean c0JournalAvailable = false;

    public FinanceStockTransportMode getTransportMode() {
        return transportMode;
    }

    public void setTransportMode(FinanceStockTransportMode transportMode) {
        this.transportMode = transportMode;
    }

    public boolean isC0JournalAvailable() {
        return c0JournalAvailable;
    }

    public void setC0JournalAvailable(boolean c0JournalAvailable) {
        this.c0JournalAvailable = c0JournalAvailable;
    }
}
