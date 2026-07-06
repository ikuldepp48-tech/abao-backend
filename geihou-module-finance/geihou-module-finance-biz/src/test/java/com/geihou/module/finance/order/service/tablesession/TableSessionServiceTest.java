package com.geihou.module.finance.order.service.tablesession;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.order.OrderTestConfig;
import com.geihou.module.finance.order.OrderTestSchemaInitializer;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderCreateReqVO;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderCreateRespVO;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderItemReqVO;
import com.geihou.module.finance.order.dal.dataobject.OrderDO;
import com.geihou.module.finance.order.dal.dataobject.OrderTableSessionDO;
import com.geihou.module.finance.order.dal.mapper.OrderTableSessionMapper;
import com.geihou.module.finance.order.enums.TableSessionStatusEnum;
import com.geihou.module.finance.order.framework.OrderBusinessException;
import com.geihou.module.finance.order.service.OrderService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Table session service integration test (G1-01D).
 *
 * <p>Tests open, query, settle, close, state transitions, tenant isolation,
 * amount aggregation, business_date, session not found, closed session operations,
 * and DINE_IN validation.
 */
@SpringBootTest(
        classes = OrderTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:table_session_service_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class TableSessionServiceTest {

    @Autowired
    private TableSessionService tableSessionService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderTableSessionMapper sessionMapper;
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

    // --- AC-5: Open session creates record with status=OPEN ---

    @Test
    void openSessionCreatesRecordWithStatusOpen() {
        OrderTableSessionDO session = tableSessionService.openSession(1L, 101L, "T01", 4);

        assertThat(session.getId()).isNotNull();
        assertThat(session.getStatus()).isEqualTo(TableSessionStatusEnum.OPEN.getCode());
        assertThat(session.getSessionNo()).isNotNull().isNotEmpty();
        assertThat(session.getOpenTime()).isNotNull();
        assertThat(session.getTableNo()).isEqualTo("T01");
        assertThat(session.getTableId()).isEqualTo(101L);
        assertThat(session.getShopId()).isEqualTo(1L);
        assertThat(session.getCustomerCount()).isEqualTo(4);
    }

    // --- AC-6: business_date uses BusinessDateCalculator ---

    @Test
    void openSessionSetsBusinessDate() {
        OrderTableSessionDO session = tableSessionService.openSession(1L, 101L, "T01", null);

        assertThat(session.getBusinessDate()).isNotNull();
    }

    // --- AC-7: session_no uniqueness ---

    @Test
    void sessionNoIsUnique() {
        OrderTableSessionDO session1 = tableSessionService.openSession(1L, 101L, "T01", 4);
        OrderTableSessionDO session2 = tableSessionService.openSession(1L, 102L, "T02", 2);

        assertThat(session1.getSessionNo()).isNotEqualTo(session2.getSessionNo());
    }

    // --- AC-11: Get session by session_no ---

    @Test
    void getBySessionNoReturnsSession() {
        OrderTableSessionDO session = tableSessionService.openSession(1L, 101L, "T01", 4);

        OrderTableSessionDO found = tableSessionService.getBySessionNo(session.getSessionNo());

        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(session.getId());
        assertThat(found.getTableNo()).isEqualTo("T01");
    }

    @Test
    void getBySessionNoReturnsNullForNonExistent() {
        OrderTableSessionDO found = tableSessionService.getBySessionNo("NONEXISTENT");

        assertThat(found).isNull();
    }

    // --- AC-10: DINE_IN validation transitions OPEN→ORDERING ---

    @Test
    void validateForDineInTransitionsOpenToOrdering() {
        OrderTableSessionDO session = tableSessionService.openSession(1L, 101L, "T01", 4);

        OrderTableSessionDO validated = tableSessionService.validateForDineIn(session.getId());

        assertThat(validated.getStatus()).isEqualTo(TableSessionStatusEnum.ORDERING.getCode());
    }

    @Test
    void validateForDineInKeepsOrderingStatus() {
        OrderTableSessionDO session = tableSessionService.openSession(1L, 101L, "T01", 4);
        tableSessionService.validateForDineIn(session.getId()); // OPEN→ORDERING

        // Second validation should keep ORDERING
        OrderTableSessionDO validated2 = tableSessionService.validateForDineIn(session.getId());
        assertThat(validated2.getStatus()).isEqualTo(TableSessionStatusEnum.ORDERING.getCode());
    }

    // --- AC-8: DINE_IN without tableSessionId ---

    @Test
    void validateForDineInWithNullSessionIdThrows() {
        assertThatThrownBy(() -> tableSessionService.validateForDineIn(null))
                .isInstanceOf(OrderBusinessException.class);
    }

    @Test
    void validateForDineInWithNonExistentSessionThrows() {
        assertThatThrownBy(() -> tableSessionService.validateForDineIn(99999L))
                .isInstanceOf(OrderBusinessException.class);
    }

    // --- AC-14: Closed session cannot be operated ---

    @Test
    void validateForDineInWithClosedSessionThrows() {
        OrderTableSessionDO session = tableSessionService.openSession(1L, 101L, "T01", 4);
        tableSessionService.validateForDineIn(session.getId()); // OPEN→ORDERING
        tableSessionService.settleSession(session.getSessionNo()); // ORDERING→SETTLING
        tableSessionService.closeSession(session.getSessionNo()); // SETTLING→CLOSED

        assertThatThrownBy(() -> tableSessionService.validateForDineIn(session.getId()))
                .isInstanceOf(OrderBusinessException.class);
    }

    @Test
    void settleClosedSessionThrows() {
        OrderTableSessionDO session = tableSessionService.openSession(1L, 101L, "T01", 4);
        tableSessionService.validateForDineIn(session.getId());
        tableSessionService.settleSession(session.getSessionNo());
        tableSessionService.closeSession(session.getSessionNo());

        assertThatThrownBy(() -> tableSessionService.settleSession(session.getSessionNo()))
                .isInstanceOf(OrderBusinessException.class);
    }

    @Test
    void closeClosedSessionThrows() {
        OrderTableSessionDO session = tableSessionService.openSession(1L, 101L, "T01", 4);
        tableSessionService.validateForDineIn(session.getId());
        tableSessionService.settleSession(session.getSessionNo());
        tableSessionService.closeSession(session.getSessionNo());

        assertThatThrownBy(() -> tableSessionService.closeSession(session.getSessionNo()))
                .isInstanceOf(OrderBusinessException.class);
    }

    // --- AC-9: Non-DINE_IN with tableSessionId ---

    @Test
    void validateNonDineInWithSessionIdThrows() {
        assertThatThrownBy(() -> tableSessionService.validateNonDineIn(1L))
                .isInstanceOf(OrderBusinessException.class);
    }

    @Test
    void validateNonDineInWithNullSessionIdPasses() {
        tableSessionService.validateNonDineIn(null);
        // No exception expected
    }

    // --- AC-12: Settle transitions ORDERING→SETTLING ---

    @Test
    void settleTransitionsOrderingToSettling() {
        OrderTableSessionDO session = tableSessionService.openSession(1L, 101L, "T01", 4);
        tableSessionService.validateForDineIn(session.getId()); // OPEN→ORDERING

        OrderTableSessionDO settled = tableSessionService.settleSession(session.getSessionNo());

        assertThat(settled.getStatus()).isEqualTo(TableSessionStatusEnum.SETTLING.getCode());
        assertThat(settled.getSettleTime()).isNotNull();
    }

    // --- AC-12: Settle aggregates amounts ---

    @Test
    void settleAggregatesAmountsFromOrders() {
        OrderTableSessionDO session = tableSessionService.openSession(1L, 101L, "T01", 4);
        tableSessionService.validateForDineIn(session.getId());

        // Create an order with total 22.00
        createDineInOrder(session.getId());

        OrderTableSessionDO settled = tableSessionService.settleSession(session.getSessionNo());

        assertThat(settled.getTotalAmount()).isEqualByComparingTo(new BigDecimal("22.0000"));
        assertThat(settled.getOrderCount()).isEqualTo(1);
    }

    // --- AC-13: Close transitions SETTLING→CLOSED ---

    @Test
    void closeTransitionsSettlingToClosed() {
        OrderTableSessionDO session = tableSessionService.openSession(1L, 101L, "T01", 4);
        tableSessionService.validateForDineIn(session.getId());
        tableSessionService.settleSession(session.getSessionNo());

        OrderTableSessionDO closed = tableSessionService.closeSession(session.getSessionNo());

        assertThat(closed.getStatus()).isEqualTo(TableSessionStatusEnum.CLOSED.getCode());
        assertThat(closed.getCloseTime()).isNotNull();
    }

    // --- AC-21/AC-22: Invalid state transitions ---

    @Test
    void settleFromOpenThrows() {
        OrderTableSessionDO session = tableSessionService.openSession(1L, 101L, "T01", 4);

        assertThatThrownBy(() -> tableSessionService.settleSession(session.getSessionNo()))
                .isInstanceOf(OrderBusinessException.class);
    }

    @Test
    void closeFromOrderingThrows() {
        OrderTableSessionDO session = tableSessionService.openSession(1L, 101L, "T01", 4);
        tableSessionService.validateForDineIn(session.getId()); // OPEN→ORDERING

        assertThatThrownBy(() -> tableSessionService.closeSession(session.getSessionNo()))
                .isInstanceOf(OrderBusinessException.class);
    }

    @Test
    void settleNonExistentSessionThrows() {
        assertThatThrownBy(() -> tableSessionService.settleSession("NONEXISTENT"))
                .isInstanceOf(OrderBusinessException.class);
    }

    @Test
    void closeNonExistentSessionThrows() {
        assertThatThrownBy(() -> tableSessionService.closeSession("NONEXISTENT"))
                .isInstanceOf(OrderBusinessException.class);
    }

    // --- AC-15: Tenant isolation ---

    @Test
    void tenantIsolationPreventsCrossTenantAccess() {
        TenantContextHolder.setTenantId(1L);
        OrderTableSessionDO session = tableSessionService.openSession(1L, 101L, "T01", 4);

        // Switch to tenant 2
        TenantContextHolder.setTenantId(2L);

        OrderTableSessionDO found = tableSessionService.getBySessionNo(session.getSessionNo());
        assertThat(found).isNull();
    }

    @Test
    void tenantIsolationPreventsCrossTenantValidate() {
        TenantContextHolder.setTenantId(1L);
        OrderTableSessionDO session = tableSessionService.openSession(1L, 101L, "T01", 4);

        // Switch to tenant 2
        TenantContextHolder.setTenantId(2L);

        assertThatThrownBy(() -> tableSessionService.validateForDineIn(session.getId()))
                .isInstanceOf(OrderBusinessException.class);
    }

    // --- AC-11: Get session orders ---

    @Test
    void getSessionOrdersReturnsAssociatedOrders() {
        OrderTableSessionDO session = tableSessionService.openSession(1L, 101L, "T01", 4);
        tableSessionService.validateForDineIn(session.getId());

        createDineInOrder(session.getId());

        List<OrderDO> orders = tableSessionService.getSessionOrders(session.getId());
        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getTableSessionId()).isEqualTo(session.getId());
        assertThat(orders.get(0).getTableNo()).isEqualTo("T01");
    }

    // --- AC-20: DINE_IN order populates table_session_id and table_no from session ---

    @Test
    void dineInOrderPopulatesSessionFieldsFromSession() {
        OrderTableSessionDO session = tableSessionService.openSession(1L, 101L, "T01", 4);
        tableSessionService.validateForDineIn(session.getId()); // OPEN→ORDERING

        OrderCreateRespVO resp = createDineInOrder(session.getId());

        OrderDO order = orderService.getOrder(resp.getOrderId());
        assertThat(order.getTableSessionId()).isEqualTo(session.getId());
        assertThat(order.getTableNo()).isEqualTo("T01");
    }

    // --- AC-16: BigDecimal for money fields ---

    @Test
    void moneyFieldsAreBigDecimal() {
        OrderTableSessionDO session = tableSessionService.openSession(1L, 101L, "T01", 4);

        assertThat(session.getTotalAmount()).isInstanceOf(BigDecimal.class);
        assertThat(session.getPaidAmount()).isInstanceOf(BigDecimal.class);
        assertThat(session.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(session.getPaidAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // --- AC-17: Soft delete ---

    @Test
    void softDeleteHidesRecord() {
        OrderTableSessionDO session = tableSessionService.openSession(1L, 101L, "T01", 4);

        sessionMapper.deleteById(session.getId());

        OrderTableSessionDO found = sessionMapper.selectById(session.getId());
        assertThat(found).isNull();
    }

    // --- Helper ---

    private OrderCreateRespVO createDineInOrder(Long tableSessionId) {
        OrderCreateReqVO req = new OrderCreateReqVO();
        req.setShopId(1L);
        req.setChannel("DINE_IN");
        req.setTableSessionId(tableSessionId);

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
