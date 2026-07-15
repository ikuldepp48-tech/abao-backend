package com.geihou.module.supplychain.stock.command;

import java.math.BigDecimal;
import java.util.List;

/**
 * Version 1 result snapshot types for the supplychain command journal.
 *
 * <p>Final class wrapping a sealed {@link Root} interface that captures the
 * canonical JSON shape persisted to {@code supplychain_command_journal.result_snapshot}
 * for each of the 6 finance&rarr;supplychain write operations. Fields mirror the
 * corresponding response DTOs but exclude {@code tenantId} (cross-tenant replay
 * safety).
 *
 * <p>Only the 6 root records implement {@link Root}; the two item records are
 * nested detail types that do NOT implement {@link Root} and never appear as a
 * top-level journal snapshot.
 *
 * <p>Schema version 1 is the only version supported by FIN-CONSISTENCY.
 * Future schema evolution must introduce {@code SnapshotV2} and bump
 * {@code result_schema_version}.
 *
 * <p>Serialization intentionally includes {@code null} fields (no
 * {@code @JsonInclude(NON_NULL)}) so that strict deserialization with
 * {@code FAIL_ON_MISSING_CREATOR_PROPERTIES} succeeds on round-trip.
 *
 * <p>All required numeric fields use primitive {@code long}/{@code int} (not
 * nullable {@code Long}/{@code Integer}). Compact constructors enforce
 * fail-closed validation: {@code null}/blank inputs throw rather than being
 * silently replaced. All validation failures throw {@link IllegalStateException}
 * with REV7-frozen message text.
 */
public final class SnapshotV1 {

    /** Sealed root interface for the 6 operation result snapshots. */
    public sealed interface Root permits
            Reserve, Release, Commit,
            SalesOutBomReverse, SalesReverseRestore, Observe {
    }

    /**
     * RESERVE result snapshot.
     *
     * <p>JSON field is {@code reserveId} (not {@code eventId}) per frozen
     * OpenAPI contract. Business result type: {@code Long}.
     */
    public record Reserve(long reserveId) implements Root {
        public Reserve {
            if (reserveId <= 0) {
                throw new IllegalStateException("reserveId must be positive");
            }
        }
    }

    /** RELEASE result snapshot. Business result type: {@code void/null}. */
    public record Release() implements Root {}

    /**
     * COMMIT result snapshot.
     *
     * <p>JSON field is {@code consumeOutEventId} (not generic {@code eventId})
     * per frozen OpenAPI contract. Business result type: {@code Long}.
     */
    public record Commit(long consumeOutEventId) implements Root {
        public Commit {
            if (consumeOutEventId <= 0) {
                throw new IllegalStateException("consumeOutEventId must be positive");
            }
        }
    }

    /**
     * SALES_OUT_BOM_REVERSE result snapshot (tenantId excluded).
     * Business result type: {@code SalesOutBomReverseRespDTO}.
     */
    public record SalesOutBomReverse(
            long productId,
            String skuCode,
            BigDecimal quantity,
            long recipeId,
            int recipeVersion,
            List<SalesOutBomReverseItem> items
    ) implements Root {
        public SalesOutBomReverse {
            requireNonBlank(skuCode, "skuCode");
            if (quantity == null || quantity.signum() <= 0) {
                throw new IllegalStateException("quantity must be positive");
            }
            requireNonNull(items, "items");
            for (SalesOutBomReverseItem item : items) {
                if (item == null) {
                    throw new IllegalStateException("items contains null element");
                }
            }
            items = List.copyOf(items);
        }
    }

    /** SALES_OUT_BOM_REVERSE per-component item snapshot (not a Root). */
    public record SalesOutBomReverseItem(
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
        public SalesOutBomReverseItem {
            requireNonBlank(skuCode, "skuCode");
            requireNonBlank(unit, "unit");
            if (quantity == null || quantity.signum() <= 0) {
                throw new IllegalStateException("quantity must be positive");
            }
            requireNonBlank(clientRequestId, "clientRequestId");
        }
    }

    /**
     * SALES_REVERSE_RESTORE result snapshot.
     * Business result type: {@code SalesReverseRestoreRespDTO}.
     */
    public record SalesReverseRestore(
            int restoredItemCount,
            List<SalesReverseRestoreItem> items
    ) implements Root {
        public SalesReverseRestore {
            if (restoredItemCount < 0) {
                throw new IllegalStateException("restoredItemCount must be non-negative");
            }
            requireNonNull(items, "items");
            for (SalesReverseRestoreItem item : items) {
                if (item == null) {
                    throw new IllegalStateException("items contains null element");
                }
            }
            items = List.copyOf(items);
        }
    }

    /** SALES_REVERSE_RESTORE per-item snapshot (not a Root). */
    public record SalesReverseRestoreItem(
            long originalEventId,
            long restoreEventId,
            long stockItemId,
            long locationId,
            BigDecimal quantity,
            String unit,
            long recipeId,
            int recipeVersion
    ) {
        public SalesReverseRestoreItem {
            requireNonBlank(unit, "unit");
            if (quantity == null || quantity.signum() <= 0) {
                throw new IllegalStateException("quantity must be positive");
            }
        }
    }

    /**
     * OBSERVE_MISSING_MAPPING result snapshot. {@code mode} is the enum code
     * string. Business result type: {@code StockCoverageDecisionRespDTO}.
     *
     * <p>Only two combinations are valid: ({@code AUDIT_ONLY}, {@code false})
     * and ({@code ENFORCE}, {@code true}).
     */
    public record Observe(
            String mode,
            boolean enforce
    ) implements Root {
        public Observe {
            requireNonBlank(mode, "mode");
            if ("AUDIT_ONLY".equals(mode)) {
                if (enforce) {
                    throw new IllegalStateException(
                            "Observe oneOf violation: (AUDIT_ONLY,false) or (ENFORCE,true) only");
                }
            } else if ("ENFORCE".equals(mode)) {
                if (!enforce) {
                    throw new IllegalStateException(
                            "Observe oneOf violation: (AUDIT_ONLY,false) or (ENFORCE,true) only");
                }
            } else {
                throw new IllegalStateException(
                        "Observe oneOf violation: (AUDIT_ONLY,false) or (ENFORCE,true) only");
            }
        }
    }

    // --- Validation helpers ---

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

    private SnapshotV1() {}
}
