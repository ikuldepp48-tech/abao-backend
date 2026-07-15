package com.geihou.module.supplychain.stock.command;

import com.geihou.module.supplychain.api.stock.dto.SalesOutBomReverseItemRespDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesOutBomReverseRespDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesReverseRestoreItemRespDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesReverseRestoreRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockCoverageDecisionRespDTO;
import com.geihou.module.supplychain.api.stock.enums.StockCoverageModeEnum;
import com.geihou.module.supplychain.api.stock.enums.SupplychainCommandOperationEnum;
import java.time.LocalDateTime;

/**
 * Package-private stateless static adapter bridging {@link SnapshotV1.Root}
 * (persistence boundary) to two independent consumers:
 *
 * <ol>
 *   <li>{@code adaptToBusiness} - used by {@link CommandExecutorImpl} to
 *       reconstruct the business result type R (Long, DTO, etc.) from a
 *       deserialized snapshot. Replaces the executor's former private
 *       adapt methods (lines 234-302).</li>
 *   <li>{@code adaptToResult} - used by {@code CommandStatusQueryService}
 *       to build a {@link CommandStatusResult} from a deserialized snapshot
 *       + journal metadata.</li>
 * </ol>
 *
 * <p>This class is NOT a Spring bean. It has no state and no constructor
 * parameters. The executor's 6-arg constructor is unchanged.
 *
 * <p>{@link SnapshotV1.Root} does not enter the public return type or cross
 * the query-domain boundary. It appears as a method parameter inside this
 * adapter and as a local variable inside the executor/query service
 * (deserialization intermediate), consistent with the frozen constraint at
 * CommandResult.java:7-8.
 */
final class CommandSnapshotAdapter {

    private CommandSnapshotAdapter() {
    }

    // --- adaptToBusiness: snapshot -> executor's business result type R ---

    @SuppressWarnings("unchecked")
    static <R> R adaptToBusiness(
            SupplychainCommandOperationEnum operation,
            SnapshotV1.Root root) {
        Object result = switch (operation) {
            case RESERVE -> Long.valueOf(((SnapshotV1.Reserve) root).reserveId());
            case RELEASE -> null;
            case COMMIT -> Long.valueOf(((SnapshotV1.Commit) root).consumeOutEventId());
            case SALES_OUT_BOM_REVERSE ->
                    adaptSalesOutBomReverseToDto((SnapshotV1.SalesOutBomReverse) root);
            case SALES_REVERSE_RESTORE ->
                    adaptSalesReverseRestoreToDto((SnapshotV1.SalesReverseRestore) root);
            case OBSERVE_MISSING_MAPPING -> {
                SnapshotV1.Observe snap = (SnapshotV1.Observe) root;
                yield new StockCoverageDecisionRespDTO(
                        StockCoverageModeEnum.fromCode(snap.mode()), snap.enforce());
            }
        };
        return (R) result;
    }

    // --- adaptToResult: snapshot -> CommandStatusResult for query service ---

    static CommandStatusResult adaptToResult(
            SupplychainCommandOperationEnum operation,
            String businessCommandId,
            SnapshotV1.Root root,
            LocalDateTime executedAt,
            LocalDateTime checkedAt) {
        return switch (operation) {
            case RESERVE -> new CommandStatusResult.ReserveSucceeded(
                    businessCommandId, executedAt, checkedAt,
                    new CommandStatusResult.ReserveResult(
                            ((SnapshotV1.Reserve) root).reserveId()));
            case RELEASE -> new CommandStatusResult.ReleaseSucceeded(
                    businessCommandId, executedAt, checkedAt,
                    new CommandStatusResult.ReleaseResult());
            case COMMIT -> new CommandStatusResult.CommitSucceeded(
                    businessCommandId, executedAt, checkedAt,
                    new CommandStatusResult.CommitResult(
                            ((SnapshotV1.Commit) root).consumeOutEventId()));
            case SALES_OUT_BOM_REVERSE -> new CommandStatusResult.SalesOutBomReverseSucceeded(
                    businessCommandId, executedAt, checkedAt,
                    adaptSalesOutBomReverseToResult((SnapshotV1.SalesOutBomReverse) root));
            case SALES_REVERSE_RESTORE -> new CommandStatusResult.SalesReverseRestoreSucceeded(
                    businessCommandId, executedAt, checkedAt,
                    adaptSalesReverseRestoreToResult((SnapshotV1.SalesReverseRestore) root));
            case OBSERVE_MISSING_MAPPING -> new CommandStatusResult.ObserveSucceeded(
                    businessCommandId, executedAt, checkedAt,
                    adaptObserveToResult((SnapshotV1.Observe) root));
        };
    }

    // --- private: adaptToBusiness helpers (existing executor behavior, unchanged) ---

    private static SalesOutBomReverseRespDTO adaptSalesOutBomReverseToDto(
            SnapshotV1.SalesOutBomReverse snap) {
        SalesOutBomReverseRespDTO dto = new SalesOutBomReverseRespDTO();
        dto.setProductId(snap.productId());
        dto.setSkuCode(snap.skuCode());
        dto.setQuantity(snap.quantity());
        dto.setRecipeId(snap.recipeId());
        dto.setRecipeVersion(snap.recipeVersion());
        dto.setItems(snap.items().stream()
                .map(CommandSnapshotAdapter::adaptSalesOutBomReverseItemToDto)
                .toList());
        return dto;
    }

    private static SalesOutBomReverseItemRespDTO adaptSalesOutBomReverseItemToDto(
            SnapshotV1.SalesOutBomReverseItem snap) {
        SalesOutBomReverseItemRespDTO dto = new SalesOutBomReverseItemRespDTO();
        dto.setComponentProductId(snap.componentProductId());
        dto.setSkuCode(snap.skuCode());
        dto.setUnit(snap.unit());
        dto.setStockItemId(snap.stockItemId());
        dto.setQuantity(snap.quantity());
        dto.setEventId(snap.eventId());
        dto.setClientRequestId(snap.clientRequestId());
        dto.setRecipeId(snap.recipeId());
        dto.setRecipeVersion(snap.recipeVersion());
        return dto;
    }

    private static SalesReverseRestoreRespDTO adaptSalesReverseRestoreToDto(
            SnapshotV1.SalesReverseRestore snap) {
        SalesReverseRestoreRespDTO dto = new SalesReverseRestoreRespDTO();
        dto.setRestoredItemCount(snap.restoredItemCount());
        dto.setItems(snap.items().stream()
                .map(CommandSnapshotAdapter::adaptSalesReverseRestoreItemToDto)
                .toList());
        return dto;
    }

    private static SalesReverseRestoreItemRespDTO adaptSalesReverseRestoreItemToDto(
            SnapshotV1.SalesReverseRestoreItem snap) {
        SalesReverseRestoreItemRespDTO dto = new SalesReverseRestoreItemRespDTO();
        dto.setOriginalEventId(snap.originalEventId());
        dto.setRestoreEventId(snap.restoreEventId());
        dto.setStockItemId(snap.stockItemId());
        dto.setLocationId(snap.locationId());
        dto.setQuantity(snap.quantity());
        dto.setUnit(snap.unit());
        dto.setRecipeId(snap.recipeId());
        dto.setRecipeVersion(snap.recipeVersion());
        return dto;
    }

    // --- private: adaptToResult helpers (new query service behavior) ---

    private static CommandStatusResult.SalesOutBomReverseResult adaptSalesOutBomReverseToResult(
            SnapshotV1.SalesOutBomReverse snap) {
        return new CommandStatusResult.SalesOutBomReverseResult(
                snap.productId(),
                snap.skuCode(),
                snap.quantity(),
                snap.recipeId(),
                snap.recipeVersion(),
                snap.items().stream()
                        .map(CommandSnapshotAdapter::adaptSalesOutBomReverseItemToResult)
                        .toList()
        );
    }

    private static CommandStatusResult.SalesOutBomReverseResult.Item
            adaptSalesOutBomReverseItemToResult(SnapshotV1.SalesOutBomReverseItem snap) {
        return new CommandStatusResult.SalesOutBomReverseResult.Item(
                snap.componentProductId(),
                snap.skuCode(),
                snap.unit(),
                snap.stockItemId(),
                snap.quantity(),
                snap.eventId(),
                snap.clientRequestId(),
                snap.recipeId(),
                snap.recipeVersion()
        );
    }

    private static CommandStatusResult.SalesReverseRestoreResult adaptSalesReverseRestoreToResult(
            SnapshotV1.SalesReverseRestore snap) {
        return new CommandStatusResult.SalesReverseRestoreResult(
                snap.restoredItemCount(),
                snap.items().stream()
                        .map(CommandSnapshotAdapter::adaptSalesReverseRestoreItemToResult)
                        .toList()
        );
    }

    private static CommandStatusResult.SalesReverseRestoreResult.Item
            adaptSalesReverseRestoreItemToResult(SnapshotV1.SalesReverseRestoreItem snap) {
        return new CommandStatusResult.SalesReverseRestoreResult.Item(
                snap.originalEventId(),
                snap.restoreEventId(),
                snap.stockItemId(),
                snap.locationId(),
                snap.quantity(),
                snap.unit(),
                snap.recipeId(),
                snap.recipeVersion()
        );
    }

    private static CommandStatusResult.ObserveResult adaptObserveToResult(
            SnapshotV1.Observe snap) {
        if ("AUDIT_ONLY".equals(snap.mode())) {
            return new CommandStatusResult.AuditOnlyObserveResult();
        }
        if ("ENFORCE".equals(snap.mode())) {
            return new CommandStatusResult.EnforceObserveResult();
        }
        throw new IllegalStateException(
                "Observe oneOf violation: (AUDIT_ONLY,false) or (ENFORCE,true) only");
    }
}
