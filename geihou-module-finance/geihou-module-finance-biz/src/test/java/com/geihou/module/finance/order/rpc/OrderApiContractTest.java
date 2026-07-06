package com.geihou.module.finance.order.rpc;

import com.geihou.module.finance.api.order.OrderApi;
import com.geihou.module.finance.api.order.dto.OrderEventDTO;
import com.geihou.module.finance.api.order.dto.OrderRespDTO;
import com.geihou.module.finance.api.order.dto.OrderSummaryRespDTO;
import com.geihou.module.finance.api.order.dto.RefundCheckRespDTO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract consistency test for OrderApi dubbo-api.
 *
 * <p>Verifies:
 * 1. OrderApi interface has 4 methods with correct signatures per PRD section 3.3.
 * 2. OrderRespDTO fields completeness (16 original + platformFee + couponId + promotionIds).
 * 3. OrderSummaryRespDTO 9 fields (no List, no shopId).
 * 4. RefundCheckRespDTO 4 fields (no tenantId/orderId echo).
 * 5. OrderEventDTO fields (8 original + eventTime, no clientIp).
 * 6. DTO style consistency (no Lombok, no Serializable, no @Schema, plain Java class).
 */
class OrderApiContractTest {

    // ===== 1. OrderApi interface signature consistency =====

    @Test
    void orderApiHasExactlyFourMethods() {
        Method[] methods = OrderApi.class.getDeclaredMethods();
        assertThat(methods).hasSize(4);
    }

    @Test
    void orderApiMethodSignaturesMatchPrdSection33() throws NoSuchMethodException {
        // getOrder(Long tenantId, Long orderId) -> OrderRespDTO
        Method getOrder = OrderApi.class.getMethod("getOrder", Long.class, Long.class);
        assertThat(getOrder.getReturnType()).isEqualTo(OrderRespDTO.class);

        // summarizeByBusinessDate(Long tenantId, LocalDate businessDate, String channel) -> OrderSummaryRespDTO
        Method summarize = OrderApi.class.getMethod("summarizeByBusinessDate",
                Long.class, LocalDate.class, String.class);
        assertThat(summarize.getReturnType()).isEqualTo(OrderSummaryRespDTO.class);

        // publishOrderEvent(OrderEventDTO event) -> void
        Method publish = OrderApi.class.getMethod("publishOrderEvent", OrderEventDTO.class);
        assertThat(publish.getReturnType()).isEqualTo(void.class);

        // checkRefundable(Long tenantId, Long orderId, BigDecimal refundAmount) -> RefundCheckRespDTO
        Method checkRefund = OrderApi.class.getMethod("checkRefundable",
                Long.class, Long.class, BigDecimal.class);
        assertThat(checkRefund.getReturnType()).isEqualTo(RefundCheckRespDTO.class);
    }

    @Test
    void orderApiIsPureJavaInterfaceNoFrameworkAnnotations() {
        // OrderApi should have no annotations (pure Java interface)
        assertThat(OrderApi.class.getAnnotations()).isEmpty();
    }

    // ===== 2. OrderRespDTO field completeness =====

    @Test
    void orderRespDtoHasPlatformFeeCouponIdPromotionIds() {
        Set<String> fieldNames = getFieldNames(OrderRespDTO.class);

        // Original 16 fields
        List<String> original = Arrays.asList(
                "id", "tenantId", "orderNo", "businessDate", "channel", "orderType",
                "customerUserId", "shopId", "totalAmount", "paidAmount", "discountAmount",
                "refundAmount", "status", "paymentMethod", "payTime", "createTime", "completedTime"
        );
        // CG-OA3 additions
        List<String> additions = Arrays.asList("platformFee", "couponId", "promotionIds");

        for (String f : original) {
            assertThat(fieldNames).contains(f);
        }
        for (String f : additions) {
            assertThat(fieldNames).contains(f);
        }

        // Must NOT contain PII or other excluded fields
        assertThat(fieldNames).doesNotContain("customerPhone", "customerName",
                "memberId", "memberLevel", "tableSessionId", "tableNo",
                "cancelledTime", "cancelReason");
    }

    // ===== 3. OrderSummaryRespDTO 9 fields =====

    @Test
    void orderSummaryRespDtoHasExactlyNineFieldsNoListNoShopId() {
        Set<String> fieldNames = getFieldNames(OrderSummaryRespDTO.class);

        List<String> expected = Arrays.asList(
                "businessDate", "channel", "orderCount", "totalAmount", "paidAmount",
                "discountAmount", "refundAmount", "platformFee", "netRevenue"
        );
        assertThat(fieldNames).containsExactlyInAnyOrderElementsOf(expected);

        // No shopId
        assertThat(fieldNames).doesNotContain("shopId");
        // No List fields
        for (Field field : OrderSummaryRespDTO.class.getDeclaredFields()) {
            assertThat(field.getType()).isNotInstanceOf(java.util.List.class);
            assertThat(java.util.List.class.isAssignableFrom(field.getType())).isFalse();
        }
    }

    // ===== 4. RefundCheckRespDTO 4 fields =====

    @Test
    void refundCheckRespDtoHasExactlyFourFieldsNoTenantIdOrderIdEcho() {
        Set<String> fieldNames = getFieldNames(RefundCheckRespDTO.class);

        List<String> expected = Arrays.asList(
                "refundable", "maxRefundableAmount", "requiresApproval", "reason"
        );
        assertThat(fieldNames).containsExactlyInAnyOrderElementsOf(expected);

        // No tenantId/orderId echo
        assertThat(fieldNames).doesNotContain("tenantId", "orderId");
        // No currentRefundAmount
        assertThat(fieldNames).doesNotContain("currentRefundAmount");
    }

    // ===== 5. OrderEventDTO fields =====

    @Test
    void orderEventDtoHasEventTimeButNoClientIp() {
        Set<String> fieldNames = getFieldNames(OrderEventDTO.class);

        // Original 8 fields
        List<String> original = Arrays.asList(
                "tenantId", "orderId", "eventType", "beforeStatus", "afterStatus",
                "operatorUserId", "operatorRole", "payload"
        );
        for (String f : original) {
            assertThat(fieldNames).contains(f);
        }

        // CG-OA4 addition
        assertThat(fieldNames).contains("eventTime");

        // No clientIp
        assertThat(fieldNames).doesNotContain("clientIp");

        // eventTime should be LocalDateTime
        try {
            Field eventTimeField = OrderEventDTO.class.getDeclaredField("eventTime");
            assertThat(eventTimeField.getType()).isEqualTo(LocalDateTime.class);
        } catch (NoSuchFieldException e) {
            throw new AssertionError("eventTime field missing", e);
        }
    }

    // ===== 6. DTO style consistency =====

    @Test
    void allDtosArePlainJavaClassesNoLombokNoSerializableNoSchema() {
        Class<?>[] dtoClasses = {
                OrderRespDTO.class,
                OrderSummaryRespDTO.class,
                RefundCheckRespDTO.class,
                OrderEventDTO.class
        };

        for (Class<?> dtoClass : dtoClasses) {
            // No @Schema annotation
            assertThat(dtoClass.getAnnotations())
                    .as(dtoClass.getSimpleName() + " should have no annotations")
                    .isEmpty();

            // Not Serializable
            assertThat(java.io.Serializable.class.isAssignableFrom(dtoClass))
                    .as(dtoClass.getSimpleName() + " should not implement Serializable")
                    .isFalse();

            // Has manual getters/setters (at least one getter and one setter)
            long getters = Arrays.stream(dtoClass.getDeclaredMethods())
                    .filter(m -> m.getName().startsWith("get") && m.getParameterCount() == 0)
                    .count();
            long setters = Arrays.stream(dtoClass.getDeclaredMethods())
                    .filter(m -> m.getName().startsWith("set") && m.getParameterCount() == 1)
                    .count();
            assertThat(getters).as(dtoClass.getSimpleName() + " should have getters").isGreaterThan(0);
            assertThat(setters).as(dtoClass.getSimpleName() + " should have setters").isGreaterThan(0);
        }
    }

    // ===== Helpers =====

    private Set<String> getFieldNames(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
    }
}
