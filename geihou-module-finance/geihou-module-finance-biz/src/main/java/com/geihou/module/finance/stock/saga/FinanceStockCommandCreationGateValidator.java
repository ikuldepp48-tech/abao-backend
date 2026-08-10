package com.geihou.module.finance.stock.saga;

import com.geihou.module.finance.stock.saga.enums.FinanceStockTransportMode;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Fail-fast validator for the finance stock command creation gate
 * (G0-04H185 FIN-CONSISTENCY slice 2C-2D).
 *
 * <p>Runs once all singleton beans are instantiated and refuses application
 * startup when the gate configuration is invalid. The only currently invalid
 * combination is {@code transportMode = LOCAL_API_V1} with
 * {@code c0-journal-available = true}: a LOCAL_API_V1 transport is not durable,
 * so it must never claim C0 journal availability.
 *
 * <p>The static {@link #validate} is also exercised directly by
 * {@code FinanceStockCommandCreationGateTest}.
 */
@Component
public class FinanceStockCommandCreationGateValidator implements InitializingBean {

    private final FinanceStockCommandCreationGateProperties properties;

    public FinanceStockCommandCreationGateValidator(
            FinanceStockCommandCreationGateProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        validate(properties);
    }

    static void validate(FinanceStockCommandCreationGateProperties properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        Objects.requireNonNull(properties.getTransportMode(), "transportMode must not be null");
        if (properties.getTransportMode() == FinanceStockTransportMode.LOCAL_API_V1
                && properties.isC0JournalAvailable()) {
            throw new IllegalStateException(
                    "Invalid creation gate: transportMode=LOCAL_API_V1 cannot be combined "
                            + "with c0-journal-available=true (LOCAL_API_V1 is not durable)");
        }
    }
}
