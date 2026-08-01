package com.geihou.module.finance.stock.plan.classifier;

import com.geihou.module.finance.product.enums.StockStrategyEnum;
import com.geihou.module.finance.stock.plan.enums.CheckoutClassificationReason;
import com.geihou.module.finance.stock.plan.enums.CheckoutStockClassification;

import java.util.Objects;

/**
 * Immutable result of {@link CheckoutClassifier#classify}.
 *
 * <p>G0-04H185 FIN-CONSISTENCY slice 2C-2C-A1.
 *
 * <p>Carries the frozen classification decision plus all reference IDs
 * needed for plan write and (future) durable RESERVE command snapshot.
 *
 * <p>Field semantics (authoritative: 00-全局接口契约汇总表.md §5.8.2):
 * <ul>
 *   <li>{@code classification} + {@code reason} map to plan.classification /
 *       plan.classification_reason. {@code reason} is non-null iff
 *       {@code classification=UNMAPPED}; null for BOM and NON_BOM.</li>
 *   <li>{@code skuCode} / {@code stockStrategy} are normalized source values:
 *       blank skuCode -> null; invalid stockStrategy (not TRACK_STOCK/UNLIMITED)
 *       -> null. Both may be null simultaneously (step 1a+1b both fail with
 *       reason=INVALID_SKU_CODE). NULL is NOT bound to {@code reason}; it
 *       always mirrors source-value validity.</li>
 *   <li>{@code bomProductId} non-null only when classification=BOM
 *       (skuCode resolved to exactly 1 active FINISHED product with active recipe).</li>
 *   <li>{@code stockItemId} non-null only when classification=NON_BOM
 *       AND stockStrategy=TRACK_STOCK.</li>
 *   <li>{@code locationId} non-null when classification=BOM
 *       or classification=NON_BOM+TRACK_STOCK.</li>
 *   <li>{@code stockItemUnit} non-null only when classification=NON_BOM
 *       AND stockStrategy=TRACK_STOCK. <b>Must NOT be persisted to
 *       checkout_cart_item_plan</b> (no seventh plan field); caller writes
 *       it to the durable RESERVE command snapshot only. Re-querying
 *       StockQueryApi for unit after classify returns is forbidden.</li>
 * </ul>
 *
 * <p>The compact constructor enforces the full four-row classification matrix
 * (BOM / NON_BOM+UNLIMITED / NON_BOM+TRACK_STOCK / UNMAPPED) in a fail-closed
 * manner. Any field combination that does not match one of these four rows
 * throws {@link IllegalArgumentException}. This prevents construction of
 * logically impossible results regardless of caller.
 *
 * <p>Matrix (authoritative: 00-全局接口契约汇总表.md §5.8.2):
 * <table border="1">
 * <caption>Valid field combinations</caption>
 * <tr><th>classification</th><th>reason</th><th>skuCode</th><th>stockStrategy</th>
 *     <th>bomProductId</th><th>stockItemId</th><th>locationId</th><th>stockItemUnit</th></tr>
 * <tr><td>BOM</td><td>null</td><td>non-null</td><td>TRACK_STOCK</td>
 *     <td>&gt;0</td><td>null</td><td>&gt;0</td><td>null</td></tr>
 * <tr><td>NON_BOM</td><td>null</td><td>non-null</td><td>UNLIMITED</td>
 *     <td>null</td><td>null</td><td>null</td><td>null</td></tr>
 * <tr><td>NON_BOM</td><td>null</td><td>non-null</td><td>TRACK_STOCK</td>
 *     <td>null</td><td>&gt;0</td><td>&gt;0</td><td>non-blank</td></tr>
 * <tr><td>UNMAPPED</td><td>non-null</td><td>null or non-blank</td>
 *     <td>null or TRACK_STOCK or UNLIMITED</td>
 *     <td>null</td><td>null</td><td>null</td><td>null</td></tr>
 * </table>
 */
public record CheckoutClassificationResult(
        CheckoutStockClassification classification,
        CheckoutClassificationReason reason,
        String skuCode,
        String stockStrategy,
        Long bomProductId,
        Long stockItemId,
        Long locationId,
        String stockItemUnit
) {
    private static final String TRACK_STOCK = StockStrategyEnum.TRACK_STOCK.getCode();
    private static final String UNLIMITED = StockStrategyEnum.UNLIMITED.getCode();

    public CheckoutClassificationResult {
        Objects.requireNonNull(classification, "classification must not be null");

        switch (classification) {
            case BOM -> validateBom(reason, skuCode, stockStrategy,
                    bomProductId, stockItemId, locationId, stockItemUnit);
            case NON_BOM -> validateNonBom(reason, skuCode, stockStrategy,
                    bomProductId, stockItemId, locationId, stockItemUnit);
            case UNMAPPED -> validateUnmapped(reason, skuCode, stockStrategy,
                    bomProductId, stockItemId, locationId, stockItemUnit);
            default -> throw new IllegalArgumentException(
                    "Unknown classification: " + classification);
        }
    }

    /**
     * BOM row: reason=null, skuCode non-null, stockStrategy=TRACK_STOCK,
     * bomProductId>0, stockItemId=null, locationId>0, stockItemUnit=null.
     */
    private static void validateBom(CheckoutClassificationReason reason, String skuCode,
                                     String stockStrategy, Long bomProductId, Long stockItemId,
                                     Long locationId, String stockItemUnit) {
        requireNull(reason, "reason must be null for BOM");
        requireNonBlankSkuCode(skuCode);
        requireStrategy(stockStrategy, TRACK_STOCK, "BOM");
        requirePositiveId(bomProductId, "bomProductId must be > 0 for BOM");
        requireNull(stockItemId, "stockItemId must be null for BOM");
        requirePositiveId(locationId, "locationId must be > 0 for BOM");
        requireNull(stockItemUnit, "stockItemUnit must be null for BOM");
    }

    /**
     * NON_BOM row: splits on stockStrategy (UNLIMITED vs TRACK_STOCK).
     */
    private static void validateNonBom(CheckoutClassificationReason reason, String skuCode,
                                        String stockStrategy, Long bomProductId, Long stockItemId,
                                        Long locationId, String stockItemUnit) {
        requireNull(reason, "reason must be null for NON_BOM");
        requireNonBlankSkuCode(skuCode);
        requireNull(bomProductId, "bomProductId must be null for NON_BOM");

        if (UNLIMITED.equals(stockStrategy)) {
            // NON_BOM + UNLIMITED: no stock queries, all IDs/unit null
            requireNull(stockItemId, "stockItemId must be null for NON_BOM+UNLIMITED");
            requireNull(locationId, "locationId must be null for NON_BOM+UNLIMITED");
            requireNull(stockItemUnit, "stockItemUnit must be null for NON_BOM+UNLIMITED");
        } else if (TRACK_STOCK.equals(stockStrategy)) {
            // NON_BOM + TRACK_STOCK: stockItem + location + unit all required
            requireNull(bomProductId, "bomProductId must be null for NON_BOM+TRACK_STOCK");
            requirePositiveId(stockItemId, "stockItemId must be > 0 for NON_BOM+TRACK_STOCK");
            requirePositiveId(locationId, "locationId must be > 0 for NON_BOM+TRACK_STOCK");
            requireNonBlankUnit(stockItemUnit);
        } else {
            throw new IllegalArgumentException(
                    "NON_BOM requires stockStrategy=TRACK_STOCK or UNLIMITED, got: " + stockStrategy);
        }
    }

    /**
     * UNMAPPED row: reason non-null, all reference IDs/unit null.
     * Reason binds skuCode/stockStrategy to specific field conditions
     * (authoritative: 00-全局接口契约汇总表.md §5.8.2, mirrors
     * {@code CheckoutCartItemPlanCreate.validateUnmapped} reason sub-matrix):
     * <ul>
     *   <li>INVALID_SKU_CODE: skuCode must be null (stockStrategy normalized:
     *       null, TRACK_STOCK, or UNLIMITED).</li>
     *   <li>INVALID_STOCK_STRATEGY: skuCode must be non-null,
     *       stockStrategy must be null.</li>
     *   <li>NO_PRODUCT / AMBIGUOUS_PRODUCT / NO_STOCK_ITEM / NO_LOCATION:
     *       skuCode must be non-null, stockStrategy must be TRACK_STOCK.</li>
     * </ul>
     */
    private static void validateUnmapped(CheckoutClassificationReason reason, String skuCode,
                                          String stockStrategy, Long bomProductId, Long stockItemId,
                                          Long locationId, String stockItemUnit) {
        if (reason == null) {
            throw new IllegalArgumentException("reason must not be null for UNMAPPED");
        }
        requireNull(bomProductId, "bomProductId must be null for UNMAPPED");
        requireNull(stockItemId, "stockItemId must be null for UNMAPPED");
        requireNull(locationId, "locationId must be null for UNMAPPED");
        requireNull(stockItemUnit, "stockItemUnit must be null for UNMAPPED");
        requireStrategyNormalized(stockStrategy);
        requireSkuCodeNormalized(skuCode);
        switch (reason) {
            case INVALID_SKU_CODE ->
                requireNull(skuCode, "skuCode must be null for INVALID_SKU_CODE");
            case INVALID_STOCK_STRATEGY -> {
                requireNonNull(skuCode, "skuCode must be non-null for INVALID_STOCK_STRATEGY");
                requireNull(stockStrategy, "stockStrategy must be null for INVALID_STOCK_STRATEGY");
            }
            case NO_PRODUCT, AMBIGUOUS_PRODUCT, NO_STOCK_ITEM, NO_LOCATION -> {
                requireNonNull(skuCode, "skuCode must be non-null for " + reason.name());
                requireStrategy(stockStrategy, TRACK_STOCK, reason.name());
            }
            default -> throw new IllegalArgumentException("Unknown reason: " + reason);
        }
    }

    // ==================== Helpers ====================

    private static void requireNull(Object value, String message) {
        if (value != null) {
            throw new IllegalArgumentException(message + ", got: " + value);
        }
    }

    private static void requireNonNull(Object value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requirePositiveId(Long id, String message) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(message + ", got: " + id);
        }
    }

    private static void requireNonBlankSkuCode(String skuCode) {
        if (skuCode == null || skuCode.isBlank()) {
            throw new IllegalArgumentException(
                    "skuCode must be non-blank for this classification, got: " + skuCode);
        }
    }

    private static void requireStrategy(String actual, String expected, String label) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException(
                    "stockStrategy must be " + expected + " for " + label + ", got: " + actual);
        }
    }

    private static void requireStrategyNormalized(String stockStrategy) {
        // null allowed (step 1b), but non-null must be TRACK_STOCK or UNLIMITED
        if (stockStrategy != null
                && !TRACK_STOCK.equals(stockStrategy)
                && !UNLIMITED.equals(stockStrategy)) {
            throw new IllegalArgumentException(
                    "stockStrategy must be null, TRACK_STOCK, or UNLIMITED for UNMAPPED, got: "
                            + stockStrategy);
        }
    }

    private static void requireSkuCodeNormalized(String skuCode) {
        // null allowed (step 1a); non-null must be non-blank. Blank must NOT
        // silently persist (caller is responsible for normalization; the
        // record is the frozen output, not a normalizer).
        if (skuCode != null && skuCode.isBlank()) {
            throw new IllegalArgumentException(
                    "skuCode must be null or non-blank for UNMAPPED, got: '" + skuCode + "'");
        }
    }

    private static void requireNonBlankUnit(String unit) {
        if (unit == null || unit.isBlank()) {
            throw new IllegalArgumentException(
                    "stockItemUnit must be non-blank for NON_BOM+TRACK_STOCK, got: " + unit);
        }
    }
}
