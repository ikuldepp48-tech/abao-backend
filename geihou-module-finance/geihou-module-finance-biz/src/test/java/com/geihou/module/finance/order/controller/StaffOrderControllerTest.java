package com.geihou.module.finance.order.controller;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.api.order.enums.OrderStatusEnum;
import com.geihou.module.finance.order.OrderTestConfig;
import com.geihou.module.finance.order.OrderTestSchemaInitializer;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderCreateReqVO;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderCreateRespVO;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderItemReqVO;
import com.geihou.module.finance.order.controller.app.staff.StaffOrderController;
import com.geihou.module.finance.order.dal.dataobject.OrderDO;
import com.geihou.module.finance.order.dal.dataobject.OrderPaymentDO;
import com.geihou.module.finance.order.dal.mapper.OrderMapper;
import com.geihou.module.finance.order.service.OrderService;
import com.geihou.module.finance.order.service.payment.OrderPaymentService;
import com.geihou.module.finance.order.service.statemachine.OrderStateMachineService;
import com.geihou.common.pojo.CommonResult;
import com.geihou.common.pojo.PageResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Staff order controller test (service-level integration).
 *
 * <p>Tests staff endpoints: pending-list, accept, mark-ready, deliver, mark-paid.
 */
@SpringBootTest(
        classes = OrderTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:staff_controller_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class StaffOrderControllerTest {

    @Autowired
    private StaffOrderController staffOrderController;
    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderPaymentService paymentService;
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
    void markPaidTransitionsPendingToPaid() {
        Long orderId = createOrder();

        staffOrderController.markPaid(orderId, buildMarkPaidReq("CASH", new BigDecimal("22.00")));

        OrderDO order = orderMapper.selectById(orderId);
        assertThat(order.getStatus()).isEqualTo(OrderStatusEnum.PAID.getCode());
        assertThat(order.getPaymentMethod()).isEqualTo("CASH");
        assertThat(order.getPaidAmount()).isEqualByComparingTo(new BigDecimal("22.00"));
        assertThat(order.getPayTime()).isNotNull();
    }

    @Test
    void markPaidCreatesPaymentRecord() {
        Long orderId = createOrder();

        staffOrderController.markPaid(orderId, buildMarkPaidReq("WECHAT_PAY", new BigDecimal("22.00")));

        OrderPaymentDO payment = paymentService.getPaymentByOrderId(orderId);
        assertThat(payment).isNotNull();
        assertThat(payment.getPaymentStatus()).isEqualTo("SUCCESS");
        assertThat(payment.getPaymentMethod()).isEqualTo("WECHAT_PAY");
    }

    @Test
    void markPaidRejectsAlreadyPaidOrder() {
        Long orderId = createOrder();
        staffOrderController.markPaid(orderId, buildMarkPaidReq("CASH", new BigDecimal("22.00")));

        // Second mark-paid should fail
        CommonResult<OrderDO> result = staffOrderController.markPaid(orderId,
                buildMarkPaidReq("CASH", new BigDecimal("22.00")));
        assertThat(result.getCode()).isNotEqualTo(0);
    }

    @Test
    void markPaidRejectsAmountMismatch() {
        Long orderId = createOrder();

        CommonResult<OrderDO> result = staffOrderController.markPaid(orderId,
                buildMarkPaidReq("CASH", new BigDecimal("100.00")));
        assertThat(result.getCode()).isNotEqualTo(0);
    }

    @Test
    void acceptTransitionsPaidToPreparing() {
        Long orderId = createOrder();
        orderService.markOrderPaid(orderId, "CASH", new BigDecimal("22.00"), null);

        staffOrderController.acceptOrder(orderId);

        OrderDO order = orderMapper.selectById(orderId);
        assertThat(order.getStatus()).isEqualTo(OrderStatusEnum.PREPARING.getCode());
    }

    @Test
    void markReadyTransitionsPreparingToReady() {
        Long orderId = createOrder();
        orderService.markOrderPaid(orderId, "CASH", new BigDecimal("22.00"), null);
        staffOrderController.acceptOrder(orderId);

        staffOrderController.markReady(orderId);

        OrderDO order = orderMapper.selectById(orderId);
        assertThat(order.getStatus()).isEqualTo(OrderStatusEnum.READY.getCode());
    }

    @Test
    void deliverTransitionsReadyToDelivered() {
        Long orderId = createOrder();
        orderService.markOrderPaid(orderId, "CASH", new BigDecimal("22.00"), null);
        staffOrderController.acceptOrder(orderId);
        staffOrderController.markReady(orderId);

        staffOrderController.deliver(orderId);

        OrderDO order = orderMapper.selectById(orderId);
        assertThat(order.getStatus()).isEqualTo(OrderStatusEnum.DELIVERED.getCode());
    }

    @Test
    void pendingListReturnsPaidOrders() {
        Long orderId = createOrder();
        orderService.markOrderPaid(orderId, "CASH", new BigDecimal("22.00"), null);

        // Create another order that's still PENDING
        createOrder();

        com.geihou.module.finance.order.controller.app.staff.vo.StaffOrderPendingListReqVO req =
                new com.geihou.module.finance.order.controller.app.staff.vo.StaffOrderPendingListReqVO();
        CommonResult<PageResult<OrderDO>> result = staffOrderController.pendingList(req);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().getList()).hasSize(1);
        assertThat(result.getData().getList().get(0).getStatus()).isEqualTo("PAID");
    }

    private com.geihou.module.finance.order.controller.app.staff.vo.StaffMarkPaidReqVO buildMarkPaidReq(
            String method, BigDecimal amount) {
        var req = new com.geihou.module.finance.order.controller.app.staff.vo.StaffMarkPaidReqVO();
        req.setPaymentMethod(method);
        req.setPaymentAmount(amount);
        return req;
    }

    private Long createOrder() {
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
        OrderCreateRespVO resp = orderService.createOrder(req, UUID.randomUUID().toString());
        return resp.getOrderId();
    }
}
