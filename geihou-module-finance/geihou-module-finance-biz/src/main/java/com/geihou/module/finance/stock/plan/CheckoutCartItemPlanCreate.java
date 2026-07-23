package com.geihou.module.finance.stock.plan;

import com.geihou.module.finance.product.enums.StockStrategyEnum;
import com.geihou.module.finance.stock.plan.enums.CheckoutClassificationReason;
import com.geihou.module.finance.stock.plan.enums.CheckoutStockClassification;

import java.util.Objects;
import java.util.Set;

/**
 * Immutable create parameters for
 * {@link CheckoutCartItemPlanStore#createOrGet}.
 *
 * <p>Carries the immutable identity of a cart item plan:
 * {@code (tenantId, checkoutSessionId, cartItemId)} is the unique key;
 * {@code skuId} and {@code classification} are the immutable payload
 * that must match exactly if a row already exists.
 *
 * <p>The compact constructor rejects null {@code classification} and
 * requires all four IDs ({@code tenantId}, {@code checkoutSessionId},
 * {@code cartItemId}, {@code skuId}) to be strictly positive.
 *
 * <p>G0-04H185 FIN-CONSISTENCY slice 2C-2B: the compact constructor
 * also normalizes the six new fields and enforces the frozen
 * classification matrix:
 * <ul>
 *   <li>BOM: {@code skuCode} non-null, {@code stockStrategy=TRACK_STOCK},
 *       {@code bomProductId}/{@code locationId} positive,
 *       {@code stockItemId}/{@code classificationReason} null</li>
 *   <li>NON_BOM+UNLIMITED: {@code skuCode} non-null, the three
 *       reference IDs and {@code classificationReason} all null</li>
 *   <li>NON_BOM+TRACK_STOCK: {@code skuCode} non-null,
 *       {@code stockItemId}/{@code locationId} positive,
 *       {@code bomProductId}/{@code classificationReason} null</li>
 *   <li>UNMAPPED: {@code classificationReason} required, the three
 *       reference IDs all null; sub-reason rules:
 *       {@code INVALID_SKU_CODE} (skuCode null, strategy optional),
 *       {@code INVALID_STOCK_STRATEGY} (skuCode non-null, strategy null),
 *       {@code NO_PRODUCT}/{@code AMBIGUOUS_PRODUCT}/{@code NO_STOCK_ITEM}/
 *       {@code NO_LOCATION} (skuCode non-null, strategy=TRACK_STOCK)</li>
 * </ul>
 *
 * <p>Normalization is independent per field: blank {@code skuCode}
 * becomes null; an invalid {@code stockStrategy} (not TRACK_STOCK or
 * UNLIMITED) becomes null. Both may be null simultaneously. NULL is
 * not bound to {@code classification_reason}.
 *
 * <p>Non-null reference IDs must be positive; any illegal combination
 * throws {@link IllegalArgumentException}.
 */
public record CheckoutCartItemPlanCreate(
        long tenantId,
        long checkoutSessionId,
        long cartItemId,
        long skuId,
        CheckoutStockClassification classification,
        String skuCode,
        String stockStrategy,
        Long bomProductId,
        Long stockItemId,
        Long locationId,
        CheckoutClassificationReason classificationReason
) {
    private static final Set<String> VALID_STOCK_STRATEGIES = Set.of(
            StockStrategyEnum.TRACK_STOCK.getCode(),
            StockStrategyEnum.UNLIMITED.getCode());

    public CheckoutCartItemPlanCreate {
        Objects.requireNonNull(classification, "classification must not be null");
        requirePositive(tenantId, "tenantId");
        requirePositive(checkoutSessionId, "checkoutSessionId");
        requirePositive(cartItemId, "cartItemId");
        requirePositive(skuId, "skuId");
        // Normalize skuCode: blank -> null (independent of reason)
        if (skuCode != null && skuCode.isBlank()) {
            skuCode = null;
        }
        // Normalize stockStrategy: invalid -> null (independent of reason)
        if (stockStrategy != null && !VALID_STOCK_STRATEGIES.contains(stockStrategy)) {
            stockStrategy = null;
        }
        // Universal: any non-null reference ID must be positive
        requirePositiveIfPresent(bomProductId, "bomProductId");
        requirePositiveIfPresent(stockItemId, "stockItemId");
        requirePositiveIfPresent(locationId, "locationId");
        // Classification matrix (pass locals: fields are unassigned in compact ctor)
        validateMatrix(classification, skuCode, stockStrategy,
                bomProductId, stockItemId, locationId, classificationReason);
    }

    private static void validateMatrix(
            CheckoutStockClassification classification,
            String skuCode, String stockStrategy,
            Long bomProductId, Long stockItemId, Long locationId,
            CheckoutClassificationReason classificationReason) {
        switch (classification) {
            case BOM -> validateBom(skuCode, stockStrategy, bomProductId, stockItemId, locationId, classificationReason);
            case NON_BOM -> validateNonBom(skuCode, stockStrategy, bomProductId, stockItemId, locationId, classificationReason);
            case UNMAPPED -> validateUnmapped(skuCode, stockStrategy, bomProductId, stockItemId, locationId, classificationReason);
        }
    }

    private static void validateBom(
            String skuCode, String stockStrategy,
            Long bomProductId, Long stockItemId, Long locationId,
            CheckoutClassificationReason classificationReason) {
        requireNonNull(skuCode, "skuCode for BOM");
        requireStrategy(stockStrategy, StockStrategyEnum.TRACK_STOCK.getCode());
        requireNonNullPositive(bomProductId, "bomProductId for BOM");
        requireNonNullPositive(locationId, "locationId for BOM");
        requireNull(stockItemId, "stockItemId for BOM");
        requireNull(classificationReason, "classificationReason for BOM");
    }

    private static void validateNonBom(
            String skuCode, String stockStrategy,
            Long bomProductId, Long stockItemId, Long locationId,
            CheckoutClassificationReason classificationReason) {
        requireNonNull(skuCode, "skuCode for NON_BOM");
        if (StockStrategyEnum.UNLIMITED.getCode().equals(stockStrategy)) {
            requireNull(bomProductId, "bomProductId for NON_BOM+UNLIMITED");
            requireNull(stockItemId, "stockItemId for NON_BOM+UNLIMITED");
            requireNull(locationId, "locationId for NON_BOM+UNLIMITED");
            requireNull(classificationReason, "classificationReason for NON_BOM+UNLIMITED");
        } else if (StockStrategyEnum.TRACK_STOCK.getCode().equals(stockStrategy)) {
            requireNull(bomProductId, "bomProductId for NON_BOM+TRACK_STOCK");
            requireNonNullPositive(stockItemId, "stockItemId for NON_BOM+TRACK_STOCK");
            requireNonNullPositive(locationId, "locationId for NON_BOM+TRACK_STOCK");
            requireNull(classificationReason, "classificationReason for NON_BOM+TRACK_STOCK");
        } else {
            throw new IllegalArgumentException(
                    "NON_BOM requires TRACK_STOCK or UNLIMITED stockStrategy, got: " + stockStrategy);
        }
    }

    private static void validateUnmapped(
            String skuCode, String stockStrategy,
            Long bomProductId, Long stockItemId, Long locationId,
            CheckoutClassificationReason classificationReason) {
        requireNonNull(classificationReason, "classificationReason for UNMAPPED");
        requireNull(bomProductId, "bomProductId for UNMAPPED");
        requireNull(stockItemId, "stockItemId for UNMAPPED");
        requireNull(locationId, "locationId for UNMAPPED");
        switch (classificationReason) {
            case INVALID_SKU_CODE -> requireNull(skuCode, "skuCode for INVALID_SKU_CODE");
            case INVALID_STOCK_STRATEGY -> {
                requireNonNull(skuCode, "skuCode for INVALID_STOCK_STRATEGY");
                requireNull(stockStrategy, "stockStrategy for INVALID_STOCK_STRATEGY");
            }
            case NO_PRODUCT, AMBIGUOUS_PRODUCT, NO_STOCK_ITEM, NO_LOCATION -> {
                requireNonNull(skuCode, "skuCode for " + classificationReason.name());
                requireStrategy(stockStrategy, StockStrategyEnum.TRACK_STOCK.getCode());
            }
        }
    }

    private static void requireStrategy(String stockStrategy, String expected) {
        if (!expected.equals(stockStrategy)) {
            throw new IllegalArgumentException(
                    "Expected stockStrategy=" + expected + " but got: " + stockStrategy);
        }
    }

    private static void requireNonNull(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
    }

    private static void requireNull(Object value, String name) {
        if (value != null) {
            throw new IllegalArgumentException(name + " must be null, got: " + value);
        }
    }

    private static void requireNonNullPositive(Long value, String name) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(name + " must be positive: " + value);
        }
    }

    private static void requirePositiveIfPresent(Long value, String name) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(name + " must be positive when present: " + value);
        }
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(
                    name + " must be positive: " + value);
        }
    }
}
