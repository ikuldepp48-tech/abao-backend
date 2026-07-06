package com.geihou.module.finance.order.dal;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.order.OrderTestConfig;
import com.geihou.module.finance.order.OrderTestSchemaInitializer;
import com.geihou.module.finance.order.dal.dataobject.OrderPaymentDO;
import com.geihou.module.finance.order.dal.mapper.OrderPaymentMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Order payment mapper test.
 *
 * <p>Verifies INSERT-only behavior: mapper has no update/delete methods,
 * DO has no @TableLogic / deleted field.
 */
@SpringBootTest(
        classes = OrderTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:order_payment_mapper_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class OrderPaymentMapperTest {

    @Autowired
    private OrderPaymentMapper paymentMapper;
    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        OrderTestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void insertPaymentRecordSucceeds() {
        OrderPaymentDO payment = buildPayment();
        paymentMapper.insert(payment);

        assertThat(payment.getId()).isNotNull();
        OrderPaymentDO found = paymentMapper.selectById(payment.getId());
        assertThat(found).isNotNull();
        assertThat(found.getPaymentNo()).isEqualTo("PAY-TEST-001");
        assertThat(found.getPaymentAmount()).isEqualByComparingTo(new BigDecimal("22.0000"));
    }

    @Test
    void orderPaymentDOHasNoDeletedField() {
        // Verify no deleted field exists on the DO (INSERT-only, no soft delete)
        assertThat(OrderPaymentDO.class.getDeclaredFields())
                .noneMatch(f -> f.getName().equals("deleted"));
    }

    @Test
    void orderPaymentDOHasNoTableLogicAnnotation() {
        // Verify no @TableLogic annotation on any field
        assertThat(OrderPaymentDO.class.getDeclaredFields())
                .noneMatch(f -> f.isAnnotationPresent(
                        com.baomidou.mybatisplus.annotation.TableLogic.class));
    }

    @Test
    void paymentNoUniqueConstraint() {
        OrderPaymentDO payment1 = buildPayment();
        paymentMapper.insert(payment1);

        // Try to insert another with same payment_no — should fail due to unique constraint
        OrderPaymentDO payment2 = buildPayment();
        try {
            paymentMapper.insert(payment2);
            // If no exception, unique constraint is not enforced
            assertThat(false).as("Unique constraint should prevent duplicate payment_no").isTrue();
        } catch (Exception e) {
            // Expected: unique constraint violation
            assertThat(e).isNotNull();
        }
    }

    @Test
    void paymentAmountIsBigDecimal() {
        OrderPaymentDO payment = buildPayment();
        paymentMapper.insert(payment);

        OrderPaymentDO found = paymentMapper.selectById(payment.getId());
        assertThat(found.getPaymentAmount()).isInstanceOf(BigDecimal.class);
    }

    private OrderPaymentDO buildPayment() {
        OrderPaymentDO payment = new OrderPaymentDO();
        payment.setTenantId(1L);
        payment.setOrderId(100L);
        payment.setPaymentNo("PAY-TEST-001");
        payment.setPaymentMethod("CASH");
        payment.setPaymentAmount(new BigDecimal("22.0000"));
        payment.setPaymentStatus("SUCCESS");
        LocalDateTime now = LocalDateTime.now();
        payment.setInitiatedTime(now);
        payment.setPaidTime(now);
        payment.setCreateTime(now);
        return payment;
    }
}
