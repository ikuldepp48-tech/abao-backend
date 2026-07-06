package com.geihou.module.finance.order.controller;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.order.OrderTestConfig;
import com.geihou.module.finance.order.OrderTestSchemaInitializer;
import com.geihou.module.finance.order.controller.admin.OrderController;
import com.geihou.module.finance.order.controller.admin.vo.OrderDailySummaryRespVO;
import com.geihou.module.finance.order.controller.admin.vo.OrderPageReqVO;
import com.geihou.module.finance.order.controller.admin.vo.OrderRespVO;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderCreateReqVO;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderCreateRespVO;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderItemReqVO;
import com.geihou.module.finance.order.service.OrderService;
import com.geihou.common.pojo.CommonResult;
import com.geihou.common.pojo.PageResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Admin order controller test (service-level integration).
 *
 * <p>Tests admin endpoints: page query with filters, business daily summary.
 */
@SpringBootTest(
        classes = OrderTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:admin_controller_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class AdminOrderControllerTest {

    @Autowired
    private OrderController adminOrderController;
    @Autowired
    private OrderService orderService;
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
    void pageReturnsAllOrders() {
        createOrder();
        createOrder();

        OrderPageReqVO req = new OrderPageReqVO();
        CommonResult<PageResult<OrderRespVO>> result = adminOrderController.page(req);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().getList()).hasSize(2);
        assertThat(result.getData().getTotal()).isEqualTo(2L);
    }

    @Test
    void pageFiltersByStatus() {
        createOrder(); // PENDING
        OrderCreateRespVO resp = createOrder();
        orderService.markOrderPaid(resp.getOrderId(), "CASH", new BigDecimal("22.00"), null);

        OrderPageReqVO req = new OrderPageReqVO();
        req.setStatus("PAID");
        CommonResult<PageResult<OrderRespVO>> result = adminOrderController.page(req);

        assertThat(result.getData().getList()).hasSize(1);
        assertThat(result.getData().getList().get(0).getStatus()).isEqualTo("PAID");
    }

    @Test
    void pageFiltersByChannel() {
        createOrder(); // SELF_PICKUP

        OrderPageReqVO req = new OrderPageReqVO();
        req.setChannel("SELF_PICKUP");
        CommonResult<PageResult<OrderRespVO>> result = adminOrderController.page(req);

        assertThat(result.getData().getList()).hasSize(1);
        assertThat(result.getData().getList().get(0).getChannel()).isEqualTo("SELF_PICKUP");
    }

    @Test
    void pageFiltersByBusinessDate() {
        OrderCreateRespVO resp = createOrder();

        OrderPageReqVO req = new OrderPageReqVO();
        req.setBusinessDate(resp.getBusinessDate());
        CommonResult<PageResult<OrderRespVO>> result = adminOrderController.page(req);

        assertThat(result.getData().getList()).hasSize(1);
    }

    @Test
    void businessDailySummaryReturnsAggregates() {
        createOrder();
        OrderCreateRespVO resp = createOrder();
        orderService.markOrderPaid(resp.getOrderId(), "CASH", new BigDecimal("22.00"), null);

        CommonResult<OrderDailySummaryRespVO> result =
                adminOrderController.businessDailySummary(resp.getBusinessDate(), null);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().getOrderCount()).isEqualTo(2);
        assertThat(result.getData().getTotalAmount()).isEqualByComparingTo(new BigDecimal("44.0000"));
        assertThat(result.getData().getPaidAmount()).isEqualByComparingTo(new BigDecimal("22.0000"));
        // G1-01G: enhanced fields
        assertThat(result.getData().getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getData().getRefundAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getData().getPlatformFee()).isEqualByComparingTo(BigDecimal.ZERO);
        // netRevenue = paidAmount - refundAmount - platformFee
        assertThat(result.getData().getNetRevenue()).isEqualByComparingTo(new BigDecimal("22.0000"));
        assertThat(result.getData().getChannelSummary()).isNotEmpty();
        assertThat(result.getData().getStatusSummary()).isNotEmpty();
    }

    @Test
    void businessDailySummaryWithNoOrdersReturnsZeros() {
        CommonResult<OrderDailySummaryRespVO> result =
                adminOrderController.businessDailySummary(LocalDate.of(2020, 1, 1), null);

        assertThat(result.getData().getOrderCount()).isEqualTo(0);
        assertThat(result.getData().getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        // G1-01G: enhanced zero-checks
        assertThat(result.getData().getPaidAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getData().getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getData().getRefundAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getData().getPlatformFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getData().getNetRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getData().getChannelSummary()).isEmpty();
        assertThat(result.getData().getStatusSummary()).isEmpty();
    }

    private OrderCreateRespVO createOrder() {
        OrderCreateReqVO req = new OrderCreateReqVO();
        req.setShopId(1L);
        req.setChannel("SELF_PICKUP");

        OrderItemReqVO item1 = new OrderItemReqVO();
        item1.setSkuId(1001L);
        item1.setQuantity(new BigDecimal("1"));

        OrderItemReqVO item2 = new OrderItemReqVO();
        item2.setSkuId(1002L);
        item2.setQuantity(new BigDecimal("1"));

        req.setItems(List.of(item1, item2));
        return orderService.createOrder(req, UUID.randomUUID().toString());
    }
}
