package com.geihou.module.supplychain.stock.command;

import com.geihou.module.supplychain.api.stock.enums.SupplychainCommandOperationEnum;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Transport-neutral command status result for the command-status query domain
 * (C0 section 5.6 / {@code /rpc-api/supplychain/command-status} GET).
 *
 * <p>Sealed interface with 7 permits: 6 Succeeded records (one per operation)
 * + 1 NotFound record. Every variant exposes unified operation(), status(),
 * businessCommandId(), checkedAt() accessors.
 *
 * <p>Succeeded variants additionally expose executedAt() and result().
 *
 * <p>Result value records are independent immutable records nested in this
 * file. They do NOT reuse existing API DTOs (e.g. SalesOutBomReverseRespDTO
 * which carries tenantId at line 21). They do NOT reference SnapshotV1.Root
 * (frozen constraint: CommandResult.java:7-8 - Root only exists at the
 * journal persistence boundary; it does not enter the public return type
 * or cross the query-domain boundary).
 *
 * <p>All collection fields use List.copyOf in compact constructors with
 * fail-closed null/element validation (IllegalStateException).
 */
public sealed interface CommandStatusResult permits
        CommandStatusResult.ReserveSucceeded,
        CommandStatusResult.ReleaseSucceeded,
        CommandStatusResult.CommitSucceeded,
        CommandStatusResult.SalesOutBomReverseSucceeded,
        CommandStatusResult.SalesReverseRestoreSucceeded,
        CommandStatusResult.ObserveSucceeded,
        CommandStatusResult.NotFound {

    String STATUS_SUCCEEDED = "SUCCEEDED";
    String STATUS_NOT_FOUND = "NOT_FOUND";

    SupplychainCommandOperationEnum operation();
    String status();
    String businessCommandId();
    LocalDateTime checkedAt();

    // ==================== 6 Succeeded records ====================

    record ReserveSucceeded(
            String businessCommandId,
            LocalDateTime executedAt,
            LocalDateTime checkedAt,
            ReserveResult result
    ) implements CommandStatusResult {
        public ReserveSucceeded {
            requireNonBlank(businessCommandId, "businessCommandId");
            requireNonNull(executedAt, "executedAt");
            requireNonNull(checkedAt, "checkedAt");
            requireNonNull(result, "result");
        }
        @Override
        public SupplychainCommandOperationEnum operation() {
            return SupplychainCommandOperationEnum.RESERVE;
        }
        @Override
        public String status() { return STATUS_SUCCEEDED; }
    }

    record ReleaseSucceeded(
            String businessCommandId,
            LocalDateTime executedAt,
            LocalDateTime checkedAt,
            ReleaseResult result
    ) implements CommandStatusResult {
        public ReleaseSucceeded {
            requireNonBlank(businessCommandId, "businessCommandId");
            requireNonNull(executedAt, "executedAt");
            requireNonNull(checkedAt, "checkedAt");
            requireNonNull(result, "result");
        }
        @Override
        public SupplychainCommandOperationEnum operation() {
            return SupplychainCommandOperationEnum.RELEASE;
        }
        @Override
        public String status() { return STATUS_SUCCEEDED; }
    }

    record CommitSucceeded(
            String businessCommandId,
            LocalDateTime executedAt,
            LocalDateTime checkedAt,
            CommitResult result
    ) implements CommandStatusResult {
        public CommitSucceeded {
            requireNonBlank(businessCommandId, "businessCommandId");
            requireNonNull(executedAt, "executedAt");
            requireNonNull(checkedAt, "checkedAt");
            requireNonNull(result, "result");
        }
        @Override
        public SupplychainCommandOperationEnum operation() {
            return SupplychainCommandOperationEnum.COMMIT;
        }
        @Override
        public String status() { return STATUS_SUCCEEDED; }
    }

    record SalesOutBomReverseSucceeded(
            String businessCommandId,
            LocalDateTime executedAt,
            LocalDateTime checkedAt,
            SalesOutBomReverseResult result
    ) implements CommandStatusResult {
        public SalesOutBomReverseSucceeded {
            requireNonBlank(businessCommandId, "businessCommandId");
            requireNonNull(executedAt, "executedAt");
            requireNonNull(checkedAt, "checkedAt");
            requireNonNull(result, "result");
        }
        @Override
        public SupplychainCommandOperationEnum operation() {
            return SupplychainCommandOperationEnum.SALES_OUT_BOM_REVERSE;
        }
        @Override
        public String status() { return STATUS_SUCCEEDED; }
    }

    record SalesReverseRestoreSucceeded(
            String businessCommandId,
            LocalDateTime executedAt,
            LocalDateTime checkedAt,
            SalesReverseRestoreResult result
    ) implements CommandStatusResult {
        public SalesReverseRestoreSucceeded {
            requireNonBlank(businessCommandId, "businessCommandId");
            requireNonNull(executedAt, "executedAt");
            requireNonNull(checkedAt, "checkedAt");
            requireNonNull(result, "result");
        }
        @Override
        public SupplychainCommandOperationEnum operation() {
            return SupplychainCommandOperationEnum.SALES_REVERSE_RESTORE;
        }
        @Override
        public String status() { return STATUS_SUCCEEDED; }
    }

    record ObserveSucceeded(
            String businessCommandId,
            LocalDateTime executedAt,
            LocalDateTime checkedAt,
            ObserveResult result
    ) implements CommandStatusResult {
        public ObserveSucceeded {
            requireNonBlank(businessCommandId, "businessCommandId");
            requireNonNull(executedAt, "executedAt");
            requireNonNull(checkedAt, "checkedAt");
            requireNonNull(result, "result");
        }
        @Override
        public SupplychainCommandOperationEnum operation() {
            return SupplychainCommandOperationEnum.OBSERVE_MISSING_MAPPING;
        }
        @Override
        public String status() { return STATUS_SUCCEEDED; }
    }

    // ==================== NotFound record ====================

    record NotFound(
            SupplychainCommandOperationEnum operation,
            String businessCommandId,
            LocalDateTime checkedAt
    ) implements CommandStatusResult {
        public NotFound {
            requireNonNull(operation, "operation");
            requireNonBlank(businessCommandId, "businessCommandId");
            requireNonNull(checkedAt, "checkedAt");
        }
        @Override
        public String status() { return STATUS_NOT_FOUND; }
    }

    // ==================== Result value records ====================
    // Independent immutable records. No tenantId. No reuse of API DTOs.
    // Fields mirror C0 schema (v01-base.yaml L3047-L3137).

    /** C0: ReserveResult { reserveId: int64 } */
    record ReserveResult(long reserveId) {
        public ReserveResult {
            if (reserveId <= 0) {
                throw new IllegalStateException("reserveId must be positive");
            }
        }
    }

    /** C0: ReleaseResult { properties: {} } - explicit empty result object. */
    record ReleaseResult() {
        // Empty by design. Serializes to {} per C0 L3056.
    }

    /** C0: CommitResult { consumeOutEventId: int64 } */
    record CommitResult(long consumeOutEventId) {
        public CommitResult {
            if (consumeOutEventId <= 0) {
                throw new IllegalStateException("consumeOutEventId must be positive");
            }
        }
    }

    /** C0: SalesOutBomReverseResult (L3071-L3091) */
    record SalesOutBomReverseResult(
            long productId,
            String skuCode,
            BigDecimal quantity,
            long recipeId,
            int recipeVersion,
            List<Item> items
    ) {
        public SalesOutBomReverseResult {
            requireNonBlank(skuCode, "skuCode");
            if (quantity == null || quantity.signum() <= 0) {
                throw new IllegalStateException("quantity must be positive");
            }
            requireNonNull(items, "items");
            for (Item item : items) {
                if (item == null) {
                    throw new IllegalStateException("items contains null element");
                }
            }
            items = List.copyOf(items);
        }

        record Item(
                long componentProductId,
                String skuCode,
                String unit,
                long stockItemId,
                BigDecimal quantity,
                long eventId,
                String clientRequestId,
                long recipeId,
                int recipeVersion
        ) {
            public Item {
                requireNonBlank(skuCode, "skuCode");
                requireNonBlank(unit, "unit");
                if (quantity == null || quantity.signum() <= 0) {
                    throw new IllegalStateException("quantity must be positive");
                }
                requireNonBlank(clientRequestId, "clientRequestId");
            }
        }
    }

    /** C0: SalesReverseRestoreResult (L3093-L3103) */
    record SalesReverseRestoreResult(
            int restoredItemCount,
            List<Item> items
    ) {
        public SalesReverseRestoreResult {
            if (restoredItemCount < 0) {
                throw new IllegalStateException("restoredItemCount must be non-negative");
            }
            requireNonNull(items, "items");
            for (Item item : items) {
                if (item == null) {
                    throw new IllegalStateException("items contains null element");
                }
            }
            items = List.copyOf(items);
        }

        record Item(
                long originalEventId,
                long restoreEventId,
                long stockItemId,
                long locationId,
                BigDecimal quantity,
                String unit,
                long recipeId,
                int recipeVersion
        ) {
            public Item {
                requireNonBlank(unit, "unit");
                if (quantity == null || quantity.signum() <= 0) {
                    throw new IllegalStateException("quantity must be positive");
                }
            }
        }
    }

    /**
     * C0: ObserveResult oneOf (L3105-L3137) - two sealed leaves.
     * Only (AUDIT_ONLY, false) and (ENFORCE, true) are valid.
     */
    sealed interface ObserveResult permits
            AuditOnlyObserveResult,
            EnforceObserveResult {
        String mode();
        boolean enforce();
    }

    record AuditOnlyObserveResult() implements ObserveResult {
        @Override public String mode() { return "AUDIT_ONLY"; }
        @Override public boolean enforce() { return false; }
    }

    record EnforceObserveResult() implements ObserveResult {
        @Override public String mode() { return "ENFORCE"; }
        @Override public boolean enforce() { return true; }
    }

    // ==================== Validation helpers ====================

    private static void requireNonNull(Object value, String name) {
        if (value == null) {
            throw new IllegalStateException(name + " must not be null");
        }
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must not be blank");
        }
    }
}
