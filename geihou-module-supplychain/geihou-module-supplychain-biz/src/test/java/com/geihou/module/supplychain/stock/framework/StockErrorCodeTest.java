package com.geihou.module.supplychain.stock.framework;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Error code verification test.
 *
 * <p>Covers: AC-19 (error codes use 2_002_xxx range, 2002001-2002065).
 */
class StockErrorCodeTest {

    @Test
    void allErrorCodesInRange2002xxx() {
        Field[] fields = StockErrorCodeConstants.class.getDeclaredFields();
        List<String> violations = new ArrayList<>();

        for (Field field : fields) {
            if (field.getType() == com.geihou.common.error.ErrorCode.class) {
                try {
                    field.setAccessible(true);
                    com.geihou.common.error.ErrorCode code =
                            (com.geihou.common.error.ErrorCode) field.get(null);
                    int codeValue = code.getCode();
                    if (codeValue < 2002001 || codeValue > 2002065) {
                        violations.add(field.getName() + " = " + codeValue);
                    }
                } catch (IllegalAccessException e) {
                    violations.add(field.getName() + " (access error)");
                }
            }
        }

        assertThat(violations).as("Error codes outside 2002001-2002065 range").isEmpty();
    }

    @Test
    void errorCodeConstantsHas65Codes() {
        Field[] fields = StockErrorCodeConstants.class.getDeclaredFields();
        long count = 0;
        for (Field field : fields) {
            if (field.getType() == com.geihou.common.error.ErrorCode.class) {
                count++;
            }
        }
        assertThat(count).isEqualTo(65);
    }

    @Test
    void specificErrorCodesExist() {
        assertThat(StockErrorCodeConstants.EVENT_TYPE_INVALID.getCode()).isEqualTo(2002001);
        assertThat(StockErrorCodeConstants.DIRECTION_INVALID.getCode()).isEqualTo(2002002);
        assertThat(StockErrorCodeConstants.QUANTITY_MUST_BE_POSITIVE.getCode()).isEqualTo(2002003);
        assertThat(StockErrorCodeConstants.INSUFFICIENT_STOCK.getCode()).isEqualTo(2002004);
        assertThat(StockErrorCodeConstants.CLIENT_REQUEST_ID_CONFLICT.getCode()).isEqualTo(2002005);
        assertThat(StockErrorCodeConstants.BALANCE_CONCURRENT_CONFLICT.getCode()).isEqualTo(2002006);
        assertThat(StockErrorCodeConstants.REQUIRED_FIELD_MISSING.getCode()).isEqualTo(2002007);
        assertThat(StockErrorCodeConstants.INTERNAL_DIRECTION_NOT_SUPPORTED.getCode()).isEqualTo(2002008);
    }

    @Test
    void reserveErrorCodesExist() {
        assertThat(StockErrorCodeConstants.RESERVE_NOT_FOUND.getCode()).isEqualTo(2002009);
        assertThat(StockErrorCodeConstants.RESERVE_ALREADY_RELEASED.getCode()).isEqualTo(2002010);
        assertThat(StockErrorCodeConstants.RESERVE_ALREADY_COMMITTED.getCode()).isEqualTo(2002011);
        assertThat(StockErrorCodeConstants.INSUFFICIENT_AVAILABLE_STOCK.getCode()).isEqualTo(2002012);
    }

    @Test
    void lossErrorCodesExist() {
        assertThat(StockErrorCodeConstants.LOSS_NOT_FOUND.getCode()).isEqualTo(2002013);
        assertThat(StockErrorCodeConstants.LOSS_ALREADY_APPROVED.getCode()).isEqualTo(2002014);
        assertThat(StockErrorCodeConstants.LOSS_ALREADY_REJECTED.getCode()).isEqualTo(2002015);
        assertThat(StockErrorCodeConstants.LOSS_INVALID_STATUS.getCode()).isEqualTo(2002016);
        assertThat(StockErrorCodeConstants.LOSS_REASON_REMARK_REQUIRED.getCode()).isEqualTo(2002017);
    }

    @Test
    void countErrorCodesExist() {
        assertThat(StockErrorCodeConstants.COUNT_SESSION_NOT_FOUND.getCode()).isEqualTo(2002018);
        assertThat(StockErrorCodeConstants.COUNT_INVALID_STATUS.getCode()).isEqualTo(2002019);
        assertThat(StockErrorCodeConstants.COUNT_RECORD_NOT_FOUND.getCode()).isEqualTo(2002020);
        assertThat(StockErrorCodeConstants.COUNT_DIFF_REASON_REQUIRED.getCode()).isEqualTo(2002021);
        assertThat(StockErrorCodeConstants.COUNT_ALREADY_ADJUSTED.getCode()).isEqualTo(2002022);
        assertThat(StockErrorCodeConstants.COUNT_DIFF_EVIDENCE_REQUIRED.getCode()).isEqualTo(2002023);
        assertThat(StockErrorCodeConstants.COUNT_NOT_ALL_RECORDED.getCode()).isEqualTo(2002024);
        assertThat(StockErrorCodeConstants.COUNT_VARIANCE_NEGATIVE_INSUFFICIENT.getCode()).isEqualTo(2002025);
    }

    @Test
    void adjustmentReasonErrorCodeExists() {
        assertThat(StockErrorCodeConstants.ADJUSTMENT_REASON_TOO_SHORT.getCode()).isEqualTo(2002026);
    }

    @Test
    void reconcileErrorCodesExist() {
        assertThat(StockErrorCodeConstants.RECONCILE_TENANT_ID_REQUIRED.getCode()).isEqualTo(2002027);
        assertThat(StockErrorCodeConstants.RECONCILE_AMBIGUOUS_SIGN.getCode()).isEqualTo(2002028);
    }

    @Test
    void productionOrderErrorCodesExist() {
        assertThat(StockErrorCodeConstants.PRODUCTION_PRODUCT_NOT_SEMI_FINISHED.getCode()).isEqualTo(2002029);
        assertThat(StockErrorCodeConstants.PRODUCTION_RECIPE_NOT_ACTIVE.getCode()).isEqualTo(2002030);
        assertThat(StockErrorCodeConstants.PRODUCTION_LOCATION_NOT_ACTIVE.getCode()).isEqualTo(2002031);
        assertThat(StockErrorCodeConstants.PRODUCTION_PLANNED_QTY_MUST_BE_POSITIVE.getCode()).isEqualTo(2002032);
        assertThat(StockErrorCodeConstants.PRODUCTION_ORDER_NOT_FOUND.getCode()).isEqualTo(2002033);
        assertThat(StockErrorCodeConstants.PRODUCTION_INVALID_STAGE_TRANSITION.getCode()).isEqualTo(2002034);
        assertThat(StockErrorCodeConstants.PRODUCTION_UPDATE_NOT_ALLOWED.getCode()).isEqualTo(2002035);
        assertThat(StockErrorCodeConstants.PRODUCTION_ORDER_NO_CONFLICT.getCode()).isEqualTo(2002036);
        assertThat(StockErrorCodeConstants.PRODUCTION_STAGE_NOT_IMPLEMENTED.getCode()).isEqualTo(2002037);
    }

    @Test
    void productionStockEventIntegrationErrorCodesExist() {
        assertThat(StockErrorCodeConstants.PRODUCTION_STOCK_ITEM_NOT_FOUND.getCode()).isEqualTo(2002038);
        assertThat(StockErrorCodeConstants.PRODUCTION_COMPONENT_SKU_CODE_MISSING.getCode()).isEqualTo(2002039);
        assertThat(StockErrorCodeConstants.PRODUCTION_BOM_EXPLOSION_EMPTY.getCode()).isEqualTo(2002040);
        assertThat(StockErrorCodeConstants.PRODUCTION_STOCK_EVENT_INTEGRATION_FAILED.getCode()).isEqualTo(2002041);
    }

    @Test
    void qualityCheckReworkErrorCodesExist() {
        assertThat(StockErrorCodeConstants.PRODUCTION_QUALITY_CHECK_OPERATOR_REQUIRED.getCode()).isEqualTo(2002042);
        assertThat(StockErrorCodeConstants.PRODUCTION_REWORK_REASON_REQUIRED.getCode()).isEqualTo(2002043);
        assertThat(StockErrorCodeConstants.PRODUCTION_QUALITY_CHECK_SUBMIT_NOT_ALLOWED.getCode()).isEqualTo(2002044);
        assertThat(StockErrorCodeConstants.PRODUCTION_QUALITY_CHECK_PASS_NOT_ALLOWED.getCode()).isEqualTo(2002045);
        assertThat(StockErrorCodeConstants.PRODUCTION_QUALITY_CHECK_REJECT_NOT_ALLOWED.getCode()).isEqualTo(2002046);
        assertThat(StockErrorCodeConstants.PRODUCTION_REWORK_RESUME_NOT_ALLOWED.getCode()).isEqualTo(2002047);
    }

    @Test
    void scanPickOutputErrorCodesExist() {
        assertThat(StockErrorCodeConstants.PRODUCTION_SCAN_NOT_ALLOWED.getCode()).isEqualTo(2002048);
        assertThat(StockErrorCodeConstants.PRODUCTION_SCAN_NOT_ALLOWED.getMsg())
                .isEqualTo("Current stage does not allow scan-pick or scan-output");

        assertThat(StockErrorCodeConstants.PRODUCTION_PICK_INSUFFICIENT_FOR_COMPLETION.getCode()).isEqualTo(2002049);
        assertThat(StockErrorCodeConstants.PRODUCTION_PICK_INSUFFICIENT_FOR_COMPLETION.getMsg())
                .isEqualTo("Insufficient pick quantity for completion: cumulative actual_qty < planned consumption");

        assertThat(StockErrorCodeConstants.PRODUCTION_CANCEL_BLOCKED_BY_CONSUMPTION.getCode()).isEqualTo(2002050);
        assertThat(StockErrorCodeConstants.PRODUCTION_CANCEL_BLOCKED_BY_CONSUMPTION.getMsg())
                .isEqualTo("Production order has consumption records, please return materials before cancelling");

        assertThat(StockErrorCodeConstants.PRODUCTION_PICK_COMPONENT_NOT_IN_BOM.getCode()).isEqualTo(2002051);
        assertThat(StockErrorCodeConstants.PRODUCTION_PICK_COMPONENT_NOT_IN_BOM.getMsg())
                .isEqualTo("Pick component is not in the production order BOM recipe");

        assertThat(StockErrorCodeConstants.PRODUCTION_OUTPUT_SKU_MISMATCH.getCode()).isEqualTo(2002052);
        assertThat(StockErrorCodeConstants.PRODUCTION_OUTPUT_SKU_MISMATCH.getMsg())
                .isEqualTo("Output SKU does not match the production order product");

        assertThat(StockErrorCodeConstants.PRODUCTION_SEQ_ALREADY_EXISTS.getCode()).isEqualTo(2002053);
        assertThat(StockErrorCodeConstants.PRODUCTION_SEQ_ALREADY_EXISTS.getMsg())
                .isEqualTo("Pick or output sequence number already exists");
    }

    @Test
    void transferOrderErrorCodesExist() {
        assertThat(StockErrorCodeConstants.TRANSFER_FROM_TO_SAME.getCode()).isEqualTo(2002054);
        assertThat(StockErrorCodeConstants.TRANSFER_FROM_LOCATION_NOT_ACTIVE.getCode()).isEqualTo(2002055);
        assertThat(StockErrorCodeConstants.TRANSFER_TO_LOCATION_NOT_ACTIVE.getCode()).isEqualTo(2002056);
        assertThat(StockErrorCodeConstants.TRANSFER_QTY_MUST_BE_POSITIVE.getCode()).isEqualTo(2002057);
        assertThat(StockErrorCodeConstants.TRANSFER_STOCK_ITEM_NOT_FOUND.getCode()).isEqualTo(2002058);
        assertThat(StockErrorCodeConstants.TRANSFER_ORDER_NOT_FOUND.getCode()).isEqualTo(2002059);
        assertThat(StockErrorCodeConstants.TRANSFER_INVALID_STATUS.getCode()).isEqualTo(2002060);
        assertThat(StockErrorCodeConstants.INSUFFICIENT_FOR_TRANSFER.getCode()).isEqualTo(2002061);
        assertThat(StockErrorCodeConstants.TRANSFER_CANCEL_NOT_ALLOWED.getCode()).isEqualTo(2002062);
        assertThat(StockErrorCodeConstants.TRANSFER_ITEMS_EMPTY.getCode()).isEqualTo(2002063);
    }

    @Test
    void c4ConsistencyErrorCodesExist() {
        // G0-04H185: C4 command consistency errors (codes 2002064-2002065)
        // msg must match frozen RpcErrorResponse.msg exactly (pure symbol, no descriptive text)
        assertThat(StockErrorCodeConstants.IDEMPOTENT_CONFLICT.getCode()).isEqualTo(2002064);
        assertThat(StockErrorCodeConstants.IDEMPOTENT_CONFLICT.getMsg()).isEqualTo("IDEMPOTENT_CONFLICT");

        assertThat(StockErrorCodeConstants.CONSISTENCY_INTERNAL_ERROR.getCode()).isEqualTo(2002065);
        assertThat(StockErrorCodeConstants.CONSISTENCY_INTERNAL_ERROR.getMsg()).isEqualTo("CONSISTENCY_INTERNAL_ERROR");
    }
}
