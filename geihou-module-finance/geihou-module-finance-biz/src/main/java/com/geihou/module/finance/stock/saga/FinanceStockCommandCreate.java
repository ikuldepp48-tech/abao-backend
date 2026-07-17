package com.geihou.module.finance.stock.saga;

import com.geihou.module.finance.stock.saga.enums.FinanceStockSagaType;
import com.geihou.module.finance.stock.saga.enums.FinanceStockTransportMode;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable create parameters for {@link FinanceStockCommandStore#createOrGet}.
 *
 * <p>Carries the full immutable identity of a durable command: remote identity
 * (operation + businessCommandId), local saga step (sagaType + sagaId +
 * stepKey), publish fence (transportMode + c0JournalAvailable), and the frozen
 * request body bytes with their SHA-256 hash.
 *
 * <p>{@code requestBody} is defensively copied in the constructor and accessor
 * so callers cannot mutate the persisted identity after creation.
 */
public record FinanceStockCommandCreate(
        long tenantId,
        FinanceStockSagaType sagaType,
        long sagaId,
        String stepKey,
        Long parentCommandId,
        String operation,
        String businessCommandId,
        FinanceStockTransportMode transportMode,
        boolean c0JournalAvailable,
        int requestSchemaVersion,
        byte[] requestBody,
        String requestBodySha256,
        int maxDispatchAttempts,
        int maxResolutionAttempts
) {
    public FinanceStockCommandCreate {
        Objects.requireNonNull(sagaType, "sagaType must not be null");
        Objects.requireNonNull(stepKey, "stepKey must not be null");
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(businessCommandId, "businessCommandId must not be null");
        Objects.requireNonNull(transportMode, "transportMode must not be null");
        Objects.requireNonNull(requestBodySha256, "requestBodySha256 must not be null");
        if (requestBody == null) {
            throw new NullPointerException("requestBody must not be null");
        }
        requestBody = requestBody.clone();
    }

    @Override
    public byte[] requestBody() {
        return requestBody.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FinanceStockCommandCreate that)) return false;
        return tenantId == that.tenantId
                && sagaId == that.sagaId
                && c0JournalAvailable == that.c0JournalAvailable
                && requestSchemaVersion == that.requestSchemaVersion
                && maxDispatchAttempts == that.maxDispatchAttempts
                && maxResolutionAttempts == that.maxResolutionAttempts
                && sagaType == that.sagaType
                && Objects.equals(stepKey, that.stepKey)
                && Objects.equals(parentCommandId, that.parentCommandId)
                && Objects.equals(operation, that.operation)
                && Objects.equals(businessCommandId, that.businessCommandId)
                && transportMode == that.transportMode
                && Objects.equals(requestBodySha256, that.requestBodySha256)
                && Arrays.equals(requestBody, that.requestBody);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(tenantId, sagaType, sagaId, stepKey, parentCommandId,
                operation, businessCommandId, transportMode, c0JournalAvailable,
                requestSchemaVersion, requestBodySha256, maxDispatchAttempts,
                maxResolutionAttempts);
        result = 31 * result + Arrays.hashCode(requestBody);
        return result;
    }
}
