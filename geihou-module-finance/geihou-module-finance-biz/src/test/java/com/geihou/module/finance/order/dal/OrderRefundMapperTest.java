package com.geihou.module.finance.order.dal;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.api.order.enums.RefundStatusEnum;
import com.geihou.module.finance.order.OrderTestConfig;
import com.geihou.module.finance.order.OrderTestSchemaInitializer;
import com.geihou.module.finance.order.dal.dataobject.OrderRefundDO;
import com.geihou.module.finance.order.dal.mapper.OrderRefundMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Order refund mapper test (G1-01C).
 *
 * <p>Tests insert, select, update, soft delete, and tenant isolation.
 */
@SpringBootTest(
        classes = OrderTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:refund_mapper_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class OrderRefundMapperTest {

    @Autowired
    private OrderRefundMapper refundMapper;
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
    void insertAndSelectById() {
        OrderRefundDO refund = buildRefund(1L, "RFD001", new BigDecimal("50.00"));
        refundMapper.insert(refund);

        OrderRefundDO found = refundMapper.selectById(refund.getId());
        assertThat(found).isNotNull();
        assertThat(found.getRefundNo()).isEqualTo("RFD001");
        assertThat(found.getRefundAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    void updateByIdChangesStatus() {
        OrderRefundDO refund = buildRefund(1L, "RFD002", new BigDecimal("30.00"));
        refundMapper.insert(refund);

        refund.setStatus(RefundStatusEnum.REFUNDING.getCode());
        refund.setUpdater("OWNER");
        refund.setUpdateTime(LocalDateTime.now());
        refundMapper.updateById(refund);

        OrderRefundDO found = refundMapper.selectById(refund.getId());
        assertThat(found.getStatus()).isEqualTo(RefundStatusEnum.REFUNDING.getCode());
    }

    @Test
    void selectListByOrderId() {
        refundMapper.insert(buildRefund(1L, "RFD003", new BigDecimal("10.00")));
        refundMapper.insert(buildRefund(1L, "RFD004", new BigDecimal("20.00")));

        List<OrderRefundDO> refunds = refundMapper.selectList(
                OrderRefundDO::getOriginalOrderId, 1L,
                OrderRefundDO::getTenantId, 1L
        );
        assertThat(refunds).hasSize(2);
    }

    @Test
    void softDeleteHidesRecord() {
        OrderRefundDO refund = buildRefund(1L, "RFD005", new BigDecimal("15.00"));
        refundMapper.insert(refund);

        // Soft delete
        refundMapper.deleteById(refund.getId());

        // selectById should not find it (MyBatis-Plus @TableLogic filters deleted)
        OrderRefundDO found = refundMapper.selectById(refund.getId());
        assertThat(found).isNull();
    }

    @Test
    void tenantIsolationFiltersRecords() {
        TenantContextHolder.setTenantId(1L);
        refundMapper.insert(buildRefund(1L, "RFD_T1", new BigDecimal("10.00")));

        TenantContextHolder.setTenantId(2L);
        List<OrderRefundDO> refunds = refundMapper.selectList(
                OrderRefundDO::getOriginalOrderId, 1L
        );
        assertThat(refunds).isEmpty();
    }

    @Test
    void refundAmountUsesBigDecimalPrecision() {
        OrderRefundDO refund = buildRefund(1L, "RFD_PRECISION", new BigDecimal("99.9999"));
        refundMapper.insert(refund);

        OrderRefundDO found = refundMapper.selectById(refund.getId());
        assertThat(found.getRefundAmount()).isEqualByComparingTo(new BigDecimal("99.9999"));
    }

    private OrderRefundDO buildRefund(Long orderId, String refundNo, BigDecimal amount) {
        LocalDateTime now = LocalDateTime.now();
        OrderRefundDO refund = new OrderRefundDO();
        refund.setTenantId(1L);
        refund.setRefundNo(refundNo);
        refund.setOriginalOrderId(orderId);
        refund.setOriginalPaymentId(100L);
        refund.setRefundAmount(amount);
        refund.setRefundType("FULL");
        refund.setReasonType("CUSTOMER_REQUEST");
        refund.setReasonDetail("Test refund");
        refund.setStatus(RefundStatusEnum.PENDING_REVIEW.getCode());
        refund.setCreator("TEST");
        refund.setCreateTime(now);
        refund.setUpdater("TEST");
        refund.setUpdateTime(now);
        refund.setDeleted(false);
        return refund;
    }
}
