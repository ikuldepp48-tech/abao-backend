package com.geihou.module.finance.stock.plan.enums;

/**
 * Sub-reason for UNMAPPED classification (G0-04H185 FIN-CONSISTENCY slice 2C-2B).
 *
 * <p>Persisted as {@link #name()} in {@code checkout_cart_item_plan.classification_reason}
 * (VARCHAR(32)). Only set when {@code classification = UNMAPPED}; null for BOM and NON_BOM.
 *
 * <p>Routes human-fix workflows to existing controllers (no new API):
 * <ul>
 *   <li>{@code INVALID_SKU_CODE} - skuCode null/blank -> SkuController</li>
 *   <li>{@code INVALID_STOCK_STRATEGY} - stockStrategy not TRACK_STOCK/UNLIMITED -> SkuController</li>
 *   <li>{@code NO_PRODUCT} - no active FINISHED product for skuCode -> ProductMasterController</li>
 *   <li>{@code AMBIGUOUS_PRODUCT} - multiple active FINISHED products -> ProductMasterController</li>
 *   <li>{@code NO_STOCK_ITEM} - location exists but stock_item missing -> StockItemMappingController</li>
 *   <li>{@code NO_LOCATION} - location missing -> StockLocationMappingController</li>
 * </ul>
 *
 * <p>Note: {@code NO_ACTIVE_RECIPE} is a {@code BomLookupStatus} value, not a
 * classification_reason. It routes to BomRecipeController as an optional BOM fix,
 * not a mandatory UNMAPPED repair entry.
 */
public enum CheckoutClassificationReason {

    INVALID_SKU_CODE,
    INVALID_STOCK_STRATEGY,
    NO_PRODUCT,
    AMBIGUOUS_PRODUCT,
    NO_STOCK_ITEM,
    NO_LOCATION
}
