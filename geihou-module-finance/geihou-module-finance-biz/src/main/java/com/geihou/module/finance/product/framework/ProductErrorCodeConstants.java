package com.geihou.module.finance.product.framework;

import com.geihou.common.error.ErrorCode;

/**
 * Product module error code constants.
 *
 * <p>Source: PRD-G1-02 Section 7.3, error code range 1_002_xxx (subsystem 1, product segment).
 */
public final class ProductErrorCodeConstants {

    private ProductErrorCodeConstants() {
    }

    public static final ErrorCode SPU_CODE_DUPLICATED = new ErrorCode(1002001, "SPU code already exists");
    public static final ErrorCode SKU_MUST_HAVE_SPU = new ErrorCode(1002002, "SKU must belong to an SPU");
    public static final ErrorCode SPU_HAS_ACTIVE_SKUS = new ErrorCode(1002003, "Please deactivate all SKUs before deleting the SPU");
    public static final ErrorCode PRICE_CHANGE_REASON_REQUIRED = new ErrorCode(1002004, "Price change reason is required");
    public static final ErrorCode SELLING_PRICE_EXCEEDS_LIST = new ErrorCode(1002005, "Selling price cannot exceed 110% of list price");
    public static final ErrorCode SPU_MUST_BE_DEPRECATED_BEFORE_DELETE = new ErrorCode(1002009, "SPU must be deprecated before it can be deleted");
    public static final ErrorCode STATUS_CHANGE_REASON_REQUIRED = new ErrorCode(1002010, "Status change reason is required");
    public static final ErrorCode SKU_HAS_ORDERS = new ErrorCode(1002008, "This SKU has outstanding orders and cannot be deleted");

    public static final ErrorCode SPU_NOT_FOUND = new ErrorCode(1002090, "SPU not found");
    public static final ErrorCode SKU_NOT_FOUND = new ErrorCode(1002091, "SKU not found");
    public static final ErrorCode CATEGORY_NOT_FOUND = new ErrorCode(1002092, "Category not found");
    public static final ErrorCode INVALID_STATUS_TRANSITION = new ErrorCode(1002093, "Invalid status transition");
    public static final ErrorCode INVALID_SPU_TYPE = new ErrorCode(1002094, "Invalid SPU type");
    public static final ErrorCode INVALID_STOCK_STRATEGY = new ErrorCode(1002095, "Invalid stock strategy");
    public static final ErrorCode INVALID_PRICE_CHANGE_TYPE = new ErrorCode(1002096, "Invalid price change type");

    // --- G1-02F addon group + combo slice error codes (appended, not modifying existing) ---

    public static final ErrorCode COMBO_PRICE_UNREASONABLE = new ErrorCode(1002006, "Combo price is unreasonable (below 90% of internal SKU selling price sum)");
    public static final ErrorCode ADDON_GROUP_INVALID = new ErrorCode(1002007, "Addon group config invalid (select_min must be <= select_max)");

    public static final ErrorCode ADDON_GROUP_NOT_FOUND = new ErrorCode(1002097, "Addon group not found");
    public static final ErrorCode ADDON_OPTION_NOT_FOUND = new ErrorCode(1002098, "Addon option not found");
    public static final ErrorCode COMBO_NOT_FOUND = new ErrorCode(1002099, "Combo not found");
    public static final ErrorCode COMBO_ITEM_NOT_FOUND = new ErrorCode(1002100, "Combo item not found");
    public static final ErrorCode COMBO_ITEM_DUPLICATE = new ErrorCode(1002101, "Combo item duplicate (same item_sku_id already exists in this combo)");
    public static final ErrorCode ADDON_GROUP_CODE_DUPLICATED = new ErrorCode(1002102, "Addon group code already exists");
    public static final ErrorCode COMBO_SKU_ALREADY_LINKED = new ErrorCode(1002103, "Combo SKU already linked to another combo");
    public static final ErrorCode INVALID_ADDON_OPTION_STATUS = new ErrorCode(1002104, "Invalid addon option status");
    public static final ErrorCode INVALID_COMBO_STATUS = new ErrorCode(1002105, "Invalid combo status");

    // --- G1-02G dubbo-api + MQ producer slice error codes (appended, not modifying existing) ---

    public static final ErrorCode PRODUCT_API_NO_TENANT_CONTEXT = new ErrorCode(1002106, "ProductApi requires tenant context");
    public static final ErrorCode SPU_ADDON_GROUP_DUPLICATED = new ErrorCode(1002107, "SPU-addon group mapping already exists");
    public static final ErrorCode SPU_ADDON_GROUP_NOT_FOUND = new ErrorCode(1002108, "SPU-addon group mapping not found");

    // --- G1-02H customer menu slice error codes (appended, not modifying existing) ---

    public static final ErrorCode MENU_SPU_NOT_FOUND = new ErrorCode(1002109, "Menu SPU not found");
    public static final ErrorCode MENU_SPU_NOT_ACTIVE = new ErrorCode(1002110, "Menu SPU is not active");
    public static final ErrorCode MENU_SPU_TYPE_NOT_VISIBLE = new ErrorCode(1002111, "Menu SPU type is not customer-visible");
}
