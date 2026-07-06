package com.geihou.module.finance.cart.framework;

import com.geihou.common.error.ErrorCode;

/**
 * Cart module error code constants.
 *
 * <p>Source: PRD-G1-04 Section 5, error code range 1_004_xxx (subsystem 1, cart segment).
 * Registered in global contract summary table (00-全局接口契约汇总表.md, section IV).
 * Range: 1004001 - 1004999.
 */
public final class CartErrorCodeConstants {

    private CartErrorCodeConstants() {
    }

    // --- Cart query errors ---
    public static final ErrorCode CART_NOT_FOUND = new ErrorCode(1004020, "Cart not found");
    public static final ErrorCode CART_TENANT_MISMATCH = new ErrorCode(1004021, "Cart does not belong to current tenant");

    // --- Cart item errors ---
    public static final ErrorCode CART_ITEM_NOT_FOUND = new ErrorCode(1004030, "Cart item not found");
    public static final ErrorCode SKU_NOT_FOUND = new ErrorCode(1004001, "SKU not found");
    public static final ErrorCode SKU_NOT_AVAILABLE = new ErrorCode(1004002, "SKU is not available for purchase");

    // --- Validation errors ---
    public static final ErrorCode SKU_ID_REQUIRED = new ErrorCode(1004040, "SKU ID is required and must be a positive number");
    public static final ErrorCode QUANTITY_INVALID = new ErrorCode(1004041, "Quantity must be at least 1");
    public static final ErrorCode CHANNEL_INVALID = new ErrorCode(1004042, "Invalid order channel");

    // --- Concurrency errors ---
    public static final ErrorCode CART_CONFLICT = new ErrorCode(1004050, "Cart concurrent update conflict");

    // --- Cart status errors ---
    public static final ErrorCode CART_NOT_ACTIVE = new ErrorCode(1004060, "Cart is not in ACTIVE status");

    // --- Staff cart errors (G1-04F) ---
    public static final ErrorCode STAFF_NOT_AUTHENTICATED = new ErrorCode(1004070, "Staff not authenticated");

    // --- Checkout errors (CG-10 contract cleanup) ---
    public static final ErrorCode CHECKOUT_DUPLICATE = new ErrorCode(1004010, "Duplicate checkout");
    public static final ErrorCode CHECKOUT_EXPIRED = new ErrorCode(1004011, "Checkout session expired");
    public static final ErrorCode COUPON_INVALID = new ErrorCode(1004012, "Coupon invalid or expired");
    public static final ErrorCode PAYMENT_FAILED = new ErrorCode(1004013, "Payment failed");
    public static final ErrorCode PAYMENT_CALLBACK_VERIFY_FAILED = new ErrorCode(1004014, "Payment callback verification failed");
}
