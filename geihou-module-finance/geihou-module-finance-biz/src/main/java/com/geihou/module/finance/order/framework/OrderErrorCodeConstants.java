package com.geihou.module.finance.order.framework;

import com.geihou.common.error.ErrorCode;

/**
 * Order module error code constants.
 *
 * <p>Source: PRD-G1-01 Section 5.2, error code range 1_001_xxx (subsystem 1, order segment).
 * Registered in global contract summary table (00-全局接口契约汇总表.md, section IV).
 * Range: 1001001 - 1001999.
 */
public final class OrderErrorCodeConstants {

    private OrderErrorCodeConstants() {
    }

    // --- Order creation errors ---
    public static final ErrorCode ORDER_ITEMS_EMPTY = new ErrorCode(1001001, "Order items must not be empty");
    public static final ErrorCode SKU_NOT_FOUND = new ErrorCode(1001002, "SKU not found");
    public static final ErrorCode SKU_NOT_SELLABLE = new ErrorCode(1001003, "SKU is not sellable");
    public static final ErrorCode SPU_NOT_FOUND = new ErrorCode(1001004, "SPU not found for SKU snapshot");
    public static final ErrorCode INVALID_ORDER_CHANNEL = new ErrorCode(1001005, "Invalid order channel");

    // --- Idempotency errors ---
    public static final ErrorCode IDEMPOTENT_KEY_REQUIRED = new ErrorCode(1001010, "Idempotent-Key header is required");
    public static final ErrorCode IDEMPOTENT_REQUEST_PROCESSING = new ErrorCode(1001011, "Duplicate request is still processing");

    // --- Order query errors ---
    public static final ErrorCode ORDER_NOT_FOUND = new ErrorCode(1001020, "Order not found");
    public static final ErrorCode ORDER_TENANT_MISMATCH = new ErrorCode(1001021, "Order does not belong to current tenant");

    // --- State machine errors ---
    public static final ErrorCode INVALID_STATUS_TRANSFER = new ErrorCode(1001030, "Invalid status transition");

    // --- Payment errors ---
    public static final ErrorCode ORDER_ALREADY_PAID = new ErrorCode(1001040, "Order is already paid");
    public static final ErrorCode PAYMENT_AMOUNT_MISMATCH = new ErrorCode(1001041, "Payment amount does not match order total");
    public static final ErrorCode PAYMENT_NO_DUPLICATE = new ErrorCode(1001042, "Payment number already exists");
    public static final ErrorCode INVALID_PAYMENT_METHOD = new ErrorCode(1001043, "Invalid payment method");

    // --- Concurrency errors ---
    public static final ErrorCode ORDER_CONFLICT = new ErrorCode(1001050, "Order concurrent update conflict");

    // --- Refund errors (G1-01C) ---
    public static final ErrorCode REFUND_EXCEEDS_PAID = new ErrorCode(1001060, "Refund amount exceeds paid amount");
    public static final ErrorCode REFUND_NOT_FOUND = new ErrorCode(1001061, "Refund record not found");
    public static final ErrorCode REFUND_STATUS_INVALID = new ErrorCode(1001062, "Refund status is invalid for this operation");
    public static final ErrorCode ORDER_NOT_COMPLETED = new ErrorCode(1001063, "Order is not in COMPLETED status, cannot refund");
    public static final ErrorCode REFUND_ALREADY_EXISTS = new ErrorCode(1001064, "An active refund already exists for this order");
    public static final ErrorCode REFUND_REASON_REQUIRED = new ErrorCode(1001065, "Refund reason (type and detail) is required");
    public static final ErrorCode REFUND_PAYMENT_NOT_FOUND = new ErrorCode(1001066, "Original payment record not found for order");
    public static final ErrorCode REFUND_ITEM_IDS_INVALID = new ErrorCode(1001067, "Refund item IDs are invalid or required");

    // --- Table session errors (G1-01D) ---
    public static final ErrorCode TABLE_SESSION_INVALID = new ErrorCode(1001070, "Table session is invalid for this operation");
    public static final ErrorCode TABLE_SESSION_NOT_FOUND = new ErrorCode(1001071, "Table session not found");
    public static final ErrorCode TABLE_SESSION_ALREADY_CLOSED = new ErrorCode(1001072, "Table session is already closed");
    public static final ErrorCode TABLE_SESSION_REQUIRED_FOR_DINE_IN = new ErrorCode(1001073, "Table session ID is required for DINE_IN orders");
    public static final ErrorCode TABLE_SESSION_NOT_FOR_NON_DINE_IN = new ErrorCode(1001074, "Table session ID is not allowed for non-DINE_IN orders");
    public static final ErrorCode TABLE_SESSION_SETTLE_FAILED = new ErrorCode(1001075, "Table session settlement failed");
}
