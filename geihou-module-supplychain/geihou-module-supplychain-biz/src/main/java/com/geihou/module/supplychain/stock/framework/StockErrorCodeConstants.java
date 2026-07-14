package com.geihou.module.supplychain.stock.framework;

import com.geihou.common.error.ErrorCode;

/**
 * Stock module error code constants.
 *
 * <p>Source: TASK-G2-01A Section 5.3, CG-13 ruling.
 * Error code range: 2_002_xxx (2002001 - 2002008).
 * Registered in global contract summary table (00-全局接口契约汇总表.md).
 */
public final class StockErrorCodeConstants {

    private StockErrorCodeConstants() {
    }

    // --- Event recording errors ---
    public static final ErrorCode EVENT_TYPE_INVALID = new ErrorCode(2002001, "Invalid stock event type");
    public static final ErrorCode DIRECTION_INVALID = new ErrorCode(2002002, "Invalid stock direction");
    public static final ErrorCode QUANTITY_MUST_BE_POSITIVE = new ErrorCode(2002003, "Quantity must be positive");
    public static final ErrorCode INSUFFICIENT_STOCK = new ErrorCode(2002004, "Insufficient stock for OUT event");

    // --- Idempotency errors ---
    public static final ErrorCode CLIENT_REQUEST_ID_CONFLICT = new ErrorCode(2002005, "Client request ID conflict");

    // --- Concurrency errors ---
    public static final ErrorCode BALANCE_CONCURRENT_CONFLICT = new ErrorCode(2002006, "Stock balance concurrent update conflict");

    // --- Validation errors ---
    public static final ErrorCode REQUIRED_FIELD_MISSING = new ErrorCode(2002007, "Required field missing in stock event");
    public static final ErrorCode INTERNAL_DIRECTION_NOT_SUPPORTED = new ErrorCode(2002008, "INTERNAL direction (count adjust) not supported in G2-01A, deferred to G2-01C");

    // --- G2-01B1: Reservation errors ---
    public static final ErrorCode RESERVE_NOT_FOUND = new ErrorCode(2002009, "Stock reservation not found");
    public static final ErrorCode RESERVE_ALREADY_RELEASED = new ErrorCode(2002010, "Stock reservation already released");
    public static final ErrorCode RESERVE_ALREADY_COMMITTED = new ErrorCode(2002011, "Stock reservation already committed");
    public static final ErrorCode INSUFFICIENT_AVAILABLE_STOCK = new ErrorCode(2002012, "Insufficient available stock for reservation");

    // --- G2-02I-3: Stock loss errors ---
    public static final ErrorCode LOSS_NOT_FOUND = new ErrorCode(2002013, "Stock loss record not found");
    public static final ErrorCode LOSS_ALREADY_APPROVED = new ErrorCode(2002014, "Stock loss already approved");
    public static final ErrorCode LOSS_ALREADY_REJECTED = new ErrorCode(2002015, "Stock loss already rejected");
    public static final ErrorCode LOSS_INVALID_STATUS = new ErrorCode(2002016, "Invalid stock loss status for operation");
    public static final ErrorCode LOSS_REASON_REMARK_REQUIRED = new ErrorCode(2002017, "Remark required for OTHER loss reason");

    // --- G2-02I-2: Stock count errors ---
    public static final ErrorCode COUNT_SESSION_NOT_FOUND = new ErrorCode(2002018, "Stock count session not found");
    public static final ErrorCode COUNT_INVALID_STATUS = new ErrorCode(2002019, "Invalid stock count status for operation");
    public static final ErrorCode COUNT_RECORD_NOT_FOUND = new ErrorCode(2002020, "Stock count record not found");
    public static final ErrorCode COUNT_DIFF_REASON_REQUIRED = new ErrorCode(2002021, "Diff reason required (>=30 chars) for any diff_qty != 0 record");
    public static final ErrorCode COUNT_ALREADY_ADJUSTED = new ErrorCode(2002022, "Stock count already adjusted");
    public static final ErrorCode COUNT_DIFF_EVIDENCE_REQUIRED = new ErrorCode(2002023, "Evidence photo required for high variance item");
    public static final ErrorCode COUNT_NOT_ALL_RECORDED = new ErrorCode(2002024, "Not all items recorded, cannot submit");
    public static final ErrorCode COUNT_VARIANCE_NEGATIVE_INSUFFICIENT = new ErrorCode(2002025, "Negative variance exceeds available stock, cannot approve");
    public static final ErrorCode ADJUSTMENT_REASON_TOO_SHORT = new ErrorCode(2002026, "Adjustment reason must be >= 30 chars for COUNT_ADJUST/ADJUSTMENT event");

    // --- G2-02J: Reconciliation errors ---
    public static final ErrorCode RECONCILE_TENANT_ID_REQUIRED = new ErrorCode(2002027, "Tenant ID is required for reconciliation");
    public static final ErrorCode RECONCILE_AMBIGUOUS_SIGN = new ErrorCode(2002028, "Ambiguous adjustment sign inferred from balance_after");

    // --- G2-02K: Production order errors ---

    /** 产品类型不是 SEMI_FINISHED */
    public static final ErrorCode PRODUCTION_PRODUCT_NOT_SEMI_FINISHED =
        new ErrorCode(2002029, "Product type must be SEMI_FINISHED for production order");

    /** 配方不存在或非 ACTIVE 状态 */
    public static final ErrorCode PRODUCTION_RECIPE_NOT_ACTIVE =
        new ErrorCode(2002030, "Recipe not found or not in ACTIVE status");

    /** 库位不存在或未激活 */
    public static final ErrorCode PRODUCTION_LOCATION_NOT_ACTIVE =
        new ErrorCode(2002031, "Stock location not found or not active");

    /** 计划数量必须为正数 */
    public static final ErrorCode PRODUCTION_PLANNED_QTY_MUST_BE_POSITIVE =
        new ErrorCode(2002032, "Planned quantity must be positive");

    /** 生产工单不存在 */
    public static final ErrorCode PRODUCTION_ORDER_NOT_FOUND =
        new ErrorCode(2002033, "Production order not found");

    /** 非法状态转换 */
    public static final ErrorCode PRODUCTION_INVALID_STAGE_TRANSITION =
        new ErrorCode(2002034, "Invalid production stage transition");

    /** 非 CREATED 状态不允许更新 */
    public static final ErrorCode PRODUCTION_UPDATE_NOT_ALLOWED =
        new ErrorCode(2002035, "Production order can only be updated in CREATED stage");

    /** 工单号生成冲突（重试后仍冲突） */
    public static final ErrorCode PRODUCTION_ORDER_NO_CONFLICT =
        new ErrorCode(2002036, "Production order number conflict after retry");

    /** 状态流转未实现（QUALITY_CHECK / REWORK 后续切片实现） */
    public static final ErrorCode PRODUCTION_STAGE_NOT_IMPLEMENTED =
        new ErrorCode(2002037, "Production stage transition not implemented in this slice");

    // --- G2-02L: Production stock event integration errors ---

    /** product_master.skuCode → stock_item 映射失败 */
    public static final ErrorCode PRODUCTION_STOCK_ITEM_NOT_FOUND =
        new ErrorCode(2002038, "Stock item not found for product skuCode in production order");

    /** product_master.skuCode 为空 */
    public static final ErrorCode PRODUCTION_COMPONENT_SKU_CODE_MISSING =
        new ErrorCode(2002039, "Component product has no skuCode for production BOM mapping");

    /** BOM 展开结果为空（无原料叶子节点） */
    public static final ErrorCode PRODUCTION_BOM_EXPLOSION_EMPTY =
        new ErrorCode(2002040, "BOM explosion produced no raw-material leaves for production order");

    /** 生产库存事件集成失败（保留不用，StockBusinessException 直接传播） */
    public static final ErrorCode PRODUCTION_STOCK_EVENT_INTEGRATION_FAILED =
        new ErrorCode(2002041, "Production stock event integration failed");

    // --- G2-02M: Quality check / rework errors ---

    /** 质检操作人不能为空 */
    public static final ErrorCode PRODUCTION_QUALITY_CHECK_OPERATOR_REQUIRED =
        new ErrorCode(2002042, "Quality check operator must not be null");

    /** 质检驳回 / 返工原因不能为空 */
    public static final ErrorCode PRODUCTION_REWORK_REASON_REQUIRED =
        new ErrorCode(2002043, "Rework reason (remark) must not be blank for QUALITY_CHECK to REWORK");

    /** 当前状态不允许提交质检 */
    public static final ErrorCode PRODUCTION_QUALITY_CHECK_SUBMIT_NOT_ALLOWED =
        new ErrorCode(2002044, "Current stage does not allow submitting to QUALITY_CHECK");

    /** 当前状态不允许质检通过完工 */
    public static final ErrorCode PRODUCTION_QUALITY_CHECK_PASS_NOT_ALLOWED =
        new ErrorCode(2002045, "Current stage does not allow quality check pass to COMPLETED");

    /** 当前状态不允许质检驳回返工 */
    public static final ErrorCode PRODUCTION_QUALITY_CHECK_REJECT_NOT_ALLOWED =
        new ErrorCode(2002046, "Current stage does not allow quality check reject to REWORK");

    /** 当前状态不允许返工后重新制作 */
    public static final ErrorCode PRODUCTION_REWORK_RESUME_NOT_ALLOWED =
        new ErrorCode(2002047, "Current stage does not allow resuming from REWORK to IN_PROGRESS");

    // --- G2-02N: scan-pick / scan-output errors ---

    /** 当前状态不允许领料 / 产出登记（状态前置不满足） */
    public static final ErrorCode PRODUCTION_SCAN_NOT_ALLOWED =
        new ErrorCode(2002048, "Current stage does not allow scan-pick or scan-output");

    /** 领料不足，禁止完工（累计领料量 < 计划消耗量） */
    public static final ErrorCode PRODUCTION_PICK_INSUFFICIENT_FOR_COMPLETION =
        new ErrorCode(2002049, "Insufficient pick quantity for completion: cumulative actual_qty < planned consumption");

    /** 工单已有领料记录，请先退料再取消 */
    public static final ErrorCode PRODUCTION_CANCEL_BLOCKED_BY_CONSUMPTION =
        new ErrorCode(2002050, "Production order has consumption records, please return materials before cancelling");

    /** 领料组件不在工单配方 BOM 中 */
    public static final ErrorCode PRODUCTION_PICK_COMPONENT_NOT_IN_BOM =
        new ErrorCode(2002051, "Pick component is not in the production order BOM recipe");

    /** 产出 SKU 与工单产品不匹配 */
    public static final ErrorCode PRODUCTION_OUTPUT_SKU_MISMATCH =
        new ErrorCode(2002052, "Output SKU does not match the production order product");

    /** 领料 / 产出序号已存在（幂等冲突） */
    public static final ErrorCode PRODUCTION_SEQ_ALREADY_EXISTS =
        new ErrorCode(2002053, "Pick or output sequence number already exists");

    // --- G2-02S: Transfer order errors ---

    /** 源库位与目标库位相同 */
    public static final ErrorCode TRANSFER_FROM_TO_SAME =
        new ErrorCode(2002054, "From location and to location must not be the same");

    /** 源库位不存在或未激活 */
    public static final ErrorCode TRANSFER_FROM_LOCATION_NOT_ACTIVE =
        new ErrorCode(2002055, "From stock location not found or not active");

    /** 目标库位不存在或未激活 */
    public static final ErrorCode TRANSFER_TO_LOCATION_NOT_ACTIVE =
        new ErrorCode(2002056, "To stock location not found or not active");

    /** 调拨数量必须为正数 */
    public static final ErrorCode TRANSFER_QTY_MUST_BE_POSITIVE =
        new ErrorCode(2002057, "Transfer quantity must be positive");

    /** 库存品项不存在 */
    public static final ErrorCode TRANSFER_STOCK_ITEM_NOT_FOUND =
        new ErrorCode(2002058, "Stock item not found for transfer order item");

    /** 调拨单不存在 */
    public static final ErrorCode TRANSFER_ORDER_NOT_FOUND =
        new ErrorCode(2002059, "Transfer order not found");

    /** 调拨单状态不合法 */
    public static final ErrorCode TRANSFER_INVALID_STATUS =
        new ErrorCode(2002060, "Invalid transfer order status for operation");

    /** 库存不足，无法发货 */
    public static final ErrorCode INSUFFICIENT_FOR_TRANSFER =
        new ErrorCode(2002061, "Insufficient stock for transfer (available_qty < transfer_qty)");

    /** 当前状态不允许取消（SENT/RECEIVED/CANCELLED 不可取消） */
    public static final ErrorCode TRANSFER_CANCEL_NOT_ALLOWED =
        new ErrorCode(2002062, "Transfer order cannot be cancelled in current status (only PENDING can be cancelled)");

    /** 调拨单明细为空 */
    public static final ErrorCode TRANSFER_ITEMS_EMPTY =
        new ErrorCode(2002063, "Transfer order items must not be empty");

    // --- G0-04H185: C4 command consistency errors (2_002_xxx, codes 2002064-2002065) ---

    /**
     * 幂等冲突：同一 business_command_id 但 request_body_sha256 不同 (HTTP 409)。
     * 仅在 6 个写端点重放时返回。msg 与 C4 冻结的 RpcErrorResponse.msg 一致。
     */
    public static final ErrorCode IDEMPOTENT_CONFLICT =
        new ErrorCode(2002064, "IDEMPOTENT_CONFLICT");

    /**
     * 一致性内部错误：CommandExecutor T2 有界重试耗尽 (HTTP 500)。
     * 仅在 6 个写端点执行时返回。msg 与 C4 冻结的 RpcErrorResponse.msg 一致。
     */
    public static final ErrorCode CONSISTENCY_INTERNAL_ERROR =
        new ErrorCode(2002065, "CONSISTENCY_INTERNAL_ERROR");
}
