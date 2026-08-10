package com.geihou.module.finance.stock.saga;

import com.geihou.module.finance.stock.saga.enums.FinanceStockTransportMode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the finance stock command creation gate (G0-04H185
 * FIN-CONSISTENCY slice 2C-2D).
 *
 * <p>Part 1 drives the static {@link FinanceStockCommandCreationGateValidator#validate}
 * directly. Part 2 uses {@link ApplicationContextRunner} with
 * {@link ConfigurationPropertiesAutoConfiguration} to prove the
 * {@code geihou.finance.stock.command.creation-gate.*} binding and the
 * fail-fast startup on {@code LOCAL_API_V1 + c0-journal-available=true}.
 */
class FinanceStockCommandCreationGateTest {

    // ==================== Direct static validate ====================

    @Test
    void validate_defaults_localApiV1C0False_ok() {
        FinanceStockCommandCreationGateProperties props =
                new FinanceStockCommandCreationGateProperties();
        assertThat(props.getTransportMode()).isEqualTo(FinanceStockTransportMode.LOCAL_API_V1);
        assertThat(props.isC0JournalAvailable()).isFalse();
        FinanceStockCommandCreationGateValidator.validate(props);
    }

    @Test
    void validate_hmacRpcV1WithC0True_ok() {
        FinanceStockCommandCreationGateProperties props =
                new FinanceStockCommandCreationGateProperties();
        props.setTransportMode(FinanceStockTransportMode.HMAC_RPC_V1);
        props.setC0JournalAvailable(true);
        FinanceStockCommandCreationGateValidator.validate(props);
    }

    @Test
    void validate_localApiV1WithC0True_throws() {
        FinanceStockCommandCreationGateProperties props =
                new FinanceStockCommandCreationGateProperties();
        props.setTransportMode(FinanceStockTransportMode.LOCAL_API_V1);
        props.setC0JournalAvailable(true);
        assertThatThrownBy(() -> FinanceStockCommandCreationGateValidator.validate(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LOCAL_API_V1");
    }

    // ==================== ApplicationContextRunner ====================

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class))
            .withBean(FinanceStockCommandCreationGateProperties.class)
            .withBean(FinanceStockCommandCreationGateValidator.class);

    @Test
    void context_creationGateDefaults_startupSucceeds() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context)
                    .hasSingleBean(FinanceStockCommandCreationGateValidator.class);
            FinanceStockCommandCreationGateProperties props =
                    context.getBean(FinanceStockCommandCreationGateProperties.class);
            assertThat(props.getTransportMode()).isEqualTo(FinanceStockTransportMode.LOCAL_API_V1);
            assertThat(props.isC0JournalAvailable()).isFalse();
        });
    }

    @Test
    void context_localApiV1C0True_startupFails() {
        runner.withPropertyValues(
                "geihou.finance.stock.command.creation-gate.transport-mode=LOCAL_API_V1",
                "geihou.finance.stock.command.creation-gate.c0-journal-available=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasStackTraceContaining("LOCAL_API_V1");
                });
    }

    @Test
    void context_localApiV1C0False_startupSucceeds() {
        runner.withPropertyValues(
                "geihou.finance.stock.command.creation-gate.transport-mode=LOCAL_API_V1",
                "geihou.finance.stock.command.creation-gate.c0-journal-available=false")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void context_hmacRpcV1C0True_startupSucceeds() {
        runner.withPropertyValues(
                "geihou.finance.stock.command.creation-gate.transport-mode=HMAC_RPC_V1",
                "geihou.finance.stock.command.creation-gate.c0-journal-available=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    FinanceStockCommandCreationGateProperties props =
                            context.getBean(FinanceStockCommandCreationGateProperties.class);
                    assertThat(props.getTransportMode())
                            .isEqualTo(FinanceStockTransportMode.HMAC_RPC_V1);
                    assertThat(props.isC0JournalAvailable()).isTrue();
                });
    }

    @Test
    void context_hmacRpcV1C0False_startupSucceeds() {
        runner.withPropertyValues(
                "geihou.finance.stock.command.creation-gate.transport-mode=HMAC_RPC_V1",
                "geihou.finance.stock.command.creation-gate.c0-journal-available=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    FinanceStockCommandCreationGateProperties props =
                            context.getBean(FinanceStockCommandCreationGateProperties.class);
                    assertThat(props.getTransportMode())
                            .isEqualTo(FinanceStockTransportMode.HMAC_RPC_V1);
                    assertThat(props.isC0JournalAvailable()).isFalse();
                });
    }

    @Test
    void context_unknownTransportMode_bindingFails() {
        runner.withPropertyValues(
                "geihou.finance.stock.command.creation-gate.transport-mode=UNKNOWN")
                .run(context -> assertThat(context).hasFailed());
    }

}
