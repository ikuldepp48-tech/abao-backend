package com.geihou.module.finance.order.controller.app.staff.vo;

/**
 * Table session settle request VO (staff operation).
 *
 * <p>Per G1-01D slice: staff initiates settlement for a table session.
 * Currently no additional fields needed; settlement aggregates from orders.
 */
public class TableSessionSettleReqVO {
    // Reserved for future fields (e.g., discount, tip, etc.)
    // Currently empty — settlement aggregates amounts from associated orders.
}
