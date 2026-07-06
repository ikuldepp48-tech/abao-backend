package com.geihou.module.finance.cart.service;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.api.cart.enums.CartStatusEnum;
import com.geihou.module.finance.cart.CartTestConfig;
import com.geihou.module.finance.cart.CartTestSchemaInitializer;
import com.geihou.module.finance.cart.controller.app.customer.vo.CartAddItemReqVO;
import com.geihou.module.finance.cart.controller.app.customer.vo.CartVO;
import com.geihou.module.finance.cart.dal.dataobject.CartDO;
import com.geihou.module.finance.cart.dal.dataobject.CartEventLogDO;
import com.geihou.module.finance.cart.dal.mapper.CartEventLogMapper;
import com.geihou.module.finance.cart.dal.mapper.CartMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Staff cart service integration test (G1-04F).
 *
 * <p>Verifies that staff operations:
 * <ul>
 *   <li>Set {@code isStaffAssisted = true} and {@code assistedByUserId = staffUserId} on the cart.</li>
 *   <li>Write event logs with {@code operatorRole = "STAFF"} and {@code operatorUserId = staffUserId}.</li>
 *   <li>Reuse existing active carts and mark them as staff-assisted.</li>
 *   <li>Maintain existing cart invariants (amounts, quantities, event logs).</li>
 * </ul>
 */
@SpringBootTest(
        classes = CartTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:staff_cart_service_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class StaffCartServiceTest {

    private static final Long STAFF_USER_ID = 8001L;

    @Autowired
    private CartService cartService;
    @Autowired
    private CartMapper cartMapper;
    @Autowired
    private CartEventLogMapper cartEventLogMapper;
    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        CartTestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void staffGetCurrentCartWithNoActiveCartDoesNotCreateCart() {
        CartVO vo = cartService.staffGetCurrentCart(STAFF_USER_ID, 6001L, 1L, "DINE_IN");

        // Should return an empty VO, not create a cart
        assertThat(vo.getId()).isNull();
        assertThat(vo.getStatus()).isEqualTo(CartStatusEnum.ACTIVE.getCode());
        assertThat(vo.getItemCount()).isEqualTo(0);
        assertThat(vo.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(vo.getIsStaffAssisted()).isFalse();
        assertThat(vo.getAssistedByUserId()).isNull();

        // Verify no cart was created in DB
        List<CartDO> carts = cartMapper.selectList();
        assertThat(carts).isEmpty();
    }

    @Test
    void staffGetCurrentCartWithNoActiveCartDoesNotCreateEventLogs() {
        cartService.staffGetCurrentCart(STAFF_USER_ID, 6010L, 1L, "DINE_IN");

        List<CartEventLogDO> logs = cartEventLogMapper.selectList();
        assertThat(logs).isEmpty();
    }

    @Test
    void staffGetCurrentCartOnExistingCustomerCartDoesNotMutateStaffAssisted() {
        // Create a customer cart (not staff-assisted) with an item
        CartAddItemReqVO req = new CartAddItemReqVO();
        req.setSkuId(1001L);
        req.setQuantity(2);
        cartService.addItem(6011L, 1L, "DINE_IN", req);

        // Verify initial state
        List<CartDO> cartsBefore = cartMapper.selectList();
        assertThat(cartsBefore).hasSize(1);
        assertThat(cartsBefore.get(0).getIsStaffAssisted()).isFalse();
        assertThat(cartsBefore.get(0).getAssistedByUserId()).isNull();

        // Staff GET — should be readonly
        CartVO vo = cartService.staffGetCurrentCart(STAFF_USER_ID, 6011L, 1L, "DINE_IN");

        assertThat(vo.getId()).isNotNull();
        assertThat(vo.getItemCount()).isEqualTo(1);
        assertThat(vo.getIsStaffAssisted()).isFalse();
        assertThat(vo.getAssistedByUserId()).isNull();

        // Verify cart row was not mutated
        List<CartDO> cartsAfter = cartMapper.selectList();
        assertThat(cartsAfter).hasSize(1);
        assertThat(cartsAfter.get(0).getIsStaffAssisted()).isFalse();
        assertThat(cartsAfter.get(0).getAssistedByUserId()).isNull();
    }

    @Test
    void staffGetCurrentCartOnExistingCartDoesNotCreateEventLogs() {
        // Create a customer cart with an item (this writes 1 event log)
        CartAddItemReqVO req = new CartAddItemReqVO();
        req.setSkuId(1001L);
        req.setQuantity(1);
        cartService.addItem(6012L, 1L, "DINE_IN", req);

        // Staff GET — should not write any event log
        cartService.staffGetCurrentCart(STAFF_USER_ID, 6012L, 1L, "DINE_IN");

        List<CartEventLogDO> logs = cartEventLogMapper.selectList();
        assertThat(logs).hasSize(1); // Only the customer addItem log
        assertThat(logs.get(0).getOperatorRole()).isEqualTo("CUSTOMER");
    }

    @Test
    void staffAddItemCreatesStaffAssistedCartAndWritesStaffEventLog() {
        CartAddItemReqVO req = new CartAddItemReqVO();
        req.setSkuId(1001L);
        req.setQuantity(2);

        CartVO vo = cartService.staffAddItem(STAFF_USER_ID, 6002L, 1L, "DINE_IN", req);

        assertThat(vo.getItemCount()).isEqualTo(1);
        assertThat(vo.getTotalQuantity()).isEqualTo(2);
        assertThat(vo.getIsStaffAssisted()).isTrue();
        assertThat(vo.getAssistedByUserId()).isEqualTo(STAFF_USER_ID);

        // Verify event log
        List<CartEventLogDO> logs = cartEventLogMapper.selectList();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getEventType()).isEqualTo("ITEM_ADDED");
        assertThat(logs.get(0).getOperatorUserId()).isEqualTo(STAFF_USER_ID);
        assertThat(logs.get(0).getOperatorRole()).isEqualTo("STAFF");
    }

    @Test
    void staffAddItemMarksExistingCustomerCartAsStaffAssisted() {
        // First, create a customer cart (not staff-assisted)
        CartAddItemReqVO req1 = new CartAddItemReqVO();
        req1.setSkuId(1001L);
        req1.setQuantity(1);
        cartService.addItem(6003L, 1L, "DINE_IN", req1);

        // Verify cart is not staff-assisted
        List<CartDO> carts = cartMapper.selectList();
        assertThat(carts).hasSize(1);
        assertThat(carts.get(0).getIsStaffAssisted()).isFalse();
        assertThat(carts.get(0).getAssistedByUserId()).isNull();

        // Staff adds another item to the same customer's cart
        CartAddItemReqVO req2 = new CartAddItemReqVO();
        req2.setSkuId(1002L);
        req2.setQuantity(1);
        CartVO vo = cartService.staffAddItem(STAFF_USER_ID, 6003L, 1L, "DINE_IN", req2);

        assertThat(vo.getIsStaffAssisted()).isTrue();
        assertThat(vo.getAssistedByUserId()).isEqualTo(STAFF_USER_ID);
        assertThat(vo.getItemCount()).isEqualTo(2);

        // Verify in DB
        carts = cartMapper.selectList();
        assertThat(carts).hasSize(1);
        assertThat(carts.get(0).getIsStaffAssisted()).isTrue();
        assertThat(carts.get(0).getAssistedByUserId()).isEqualTo(STAFF_USER_ID);

        // Verify event logs — first is CUSTOMER, second is STAFF
        List<CartEventLogDO> logs = cartEventLogMapper.selectList();
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).getOperatorRole()).isEqualTo("CUSTOMER");
        assertThat(logs.get(1).getOperatorRole()).isEqualTo("STAFF");
        assertThat(logs.get(1).getOperatorUserId()).isEqualTo(STAFF_USER_ID);
    }

    @Test
    void staffUpdateQuantityWritesStaffEventLog() {
        // Setup: staff adds item
        CartAddItemReqVO req = new CartAddItemReqVO();
        req.setSkuId(1001L);
        req.setQuantity(2);
        CartVO vo = cartService.staffAddItem(STAFF_USER_ID, 6004L, 1L, "DINE_IN", req);
        Long itemId = vo.getItems().get(0).getId();

        // Act: staff updates quantity
        CartVO updated = cartService.staffUpdateQuantity(STAFF_USER_ID, 6004L, 1L, itemId, 5);

        assertThat(updated.getTotalQuantity()).isEqualTo(5);
        assertThat(updated.getIsStaffAssisted()).isTrue();
        assertThat(updated.getAssistedByUserId()).isEqualTo(STAFF_USER_ID);

        // Verify event log
        List<CartEventLogDO> logs = cartEventLogMapper.selectList();
        assertThat(logs).hasSize(2); // ITEM_ADDED + ITEM_QUANTITY_CHANGED
        assertThat(logs.get(1).getEventType()).isEqualTo("ITEM_QUANTITY_CHANGED");
        assertThat(logs.get(1).getOperatorUserId()).isEqualTo(STAFF_USER_ID);
        assertThat(logs.get(1).getOperatorRole()).isEqualTo("STAFF");
        assertThat(logs.get(1).getQuantityBefore()).isEqualTo(2);
        assertThat(logs.get(1).getQuantityAfter()).isEqualTo(5);
    }

    @Test
    void staffRemoveItemWritesStaffEventLog() {
        // Setup: staff adds item
        CartAddItemReqVO req = new CartAddItemReqVO();
        req.setSkuId(1001L);
        req.setQuantity(2);
        CartVO vo = cartService.staffAddItem(STAFF_USER_ID, 6005L, 1L, "DINE_IN", req);
        Long itemId = vo.getItems().get(0).getId();

        // Act: staff removes item
        CartVO updated = cartService.staffRemoveItem(STAFF_USER_ID, 6005L, 1L, itemId);

        assertThat(updated.getItemCount()).isEqualTo(0);
        assertThat(updated.getIsStaffAssisted()).isTrue();

        // Verify event log
        List<CartEventLogDO> logs = cartEventLogMapper.selectList();
        assertThat(logs).hasSize(2); // ITEM_ADDED + ITEM_REMOVED
        assertThat(logs.get(1).getEventType()).isEqualTo("ITEM_REMOVED");
        assertThat(logs.get(1).getOperatorUserId()).isEqualTo(STAFF_USER_ID);
        assertThat(logs.get(1).getOperatorRole()).isEqualTo("STAFF");
    }

    @Test
    void staffClearCartWritesStaffEventLog() {
        // Setup: staff adds item
        CartAddItemReqVO req = new CartAddItemReqVO();
        req.setSkuId(1001L);
        req.setQuantity(3);
        cartService.staffAddItem(STAFF_USER_ID, 6006L, 1L, "DINE_IN", req);

        // Act: staff clears cart
        CartVO updated = cartService.staffClearCart(STAFF_USER_ID, 6006L, 1L);

        assertThat(updated.getItemCount()).isEqualTo(0);
        assertThat(updated.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(updated.getIsStaffAssisted()).isTrue();
        assertThat(updated.getAssistedByUserId()).isEqualTo(STAFF_USER_ID);

        // Verify event log
        List<CartEventLogDO> logs = cartEventLogMapper.selectList();
        assertThat(logs).hasSize(2); // ITEM_ADDED + CART_CLEARED
        assertThat(logs.get(1).getEventType()).isEqualTo("CART_CLEARED");
        assertThat(logs.get(1).getOperatorUserId()).isEqualTo(STAFF_USER_ID);
        assertThat(logs.get(1).getOperatorRole()).isEqualTo("STAFF");
    }

    @Test
    void staffClearCartWithNoExistingCartReturnsStaffAssistedEmptyVo() {
        CartVO vo = cartService.staffClearCart(STAFF_USER_ID, 6099L, 1L);

        assertThat(vo.getItemCount()).isEqualTo(0);
        assertThat(vo.getIsStaffAssisted()).isTrue();
        assertThat(vo.getAssistedByUserId()).isEqualTo(STAFF_USER_ID);
    }

    @Test
    void customerPathRemainsCustomerOperatorRole() {
        // Verify customer path still uses CUSTOMER operator role
        CartAddItemReqVO req = new CartAddItemReqVO();
        req.setSkuId(1001L);
        req.setQuantity(1);
        cartService.addItem(6007L, 1L, "DINE_IN", req);

        List<CartEventLogDO> logs = cartEventLogMapper.selectList();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getOperatorRole()).isEqualTo("CUSTOMER");
        assertThat(logs.get(0).getOperatorUserId()).isEqualTo(6007L);

        // Verify cart is not staff-assisted
        List<CartDO> carts = cartMapper.selectList();
        assertThat(carts.get(0).getIsStaffAssisted()).isFalse();
    }
}
