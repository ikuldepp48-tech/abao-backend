package com.geihou.module.finance.stock.plan.enums;

/**
 * Stock classification for a checkout cart item (G0-04H185 FIN-CONSISTENCY
 * slice 2B).
 *
 * <ul>
 *   <li>{@code BOM} - SKU is covered by a Bill of Materials; stock impact
 *       is handled via BOM expansion in supplychain.</li>
 *   <li>{@code NON_BOM} - SKU is a simple stock item with no BOM; stock
 *       impact is a direct reservation against the SKU.</li>
 *   <li>{@code UNMAPPED} - SKU could not be classified at plan time;
 *       the plan is still frozen. Downstream stages must NOT re-derive
 *       classification, must NOT re-classify, and must refuse to commit
 *       (escalate for human classification). The freeze principle applies
 *       equally to {@code UNMAPPED}.</li>
 * </ul>
 *
 * <p>The persisted string value is the enum name (e.g. {@code "BOM"}).
 * The {@link #name()} method is used for serialization; the value is
 * write-once and never mutated after {@code createOrGet}. Downstream
 * stages must read the frozen plan and must never re-derive
 * classification, regardless of the value.
 */
public enum CheckoutStockClassification {

    BOM,
    NON_BOM,
    UNMAPPED
}
