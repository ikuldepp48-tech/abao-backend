package com.geihou.module.finance.cart.service;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.cart.CartTestConfig;
import com.geihou.module.finance.cart.CartTestSchemaInitializer;
import com.geihou.module.finance.cart.controller.app.customer.vo.CartAddItemReqVO;
import com.geihou.module.finance.cart.dal.dataobject.CartDO;
import com.geihou.module.finance.cart.dal.dataobject.CartEventLogDO;
import com.geihou.module.finance.cart.dal.dataobject.CartItemDO;
import com.geihou.module.finance.cart.dal.mapper.CartEventLogMapper;
import com.geihou.module.finance.cart.dal.mapper.CartItemMapper;
import com.geihou.module.finance.cart.dal.mapper.CartMapper;
import com.geihou.module.finance.cart.framework.CartBusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cart event log transaction test (Cart root-cause 3).
 *
 * <p>Verifies that when cart_event_log write fails, the entire transaction
 * rolls back — no partial writes to cart or cart_item (triple-write atomicity).
 */
@SpringBootTest(
        classes = CartTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:cart_event_tx_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class CartEventLogTransactionTest {

    @Autowired
    private CartService cartService;
    @Autowired
    private CartMapper cartMapper;
    @Autowired
    private CartItemMapper cartItemMapper;
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
    void addItemMustWaitForEventLogWrite() {
        // Normal flow — event log is written atomically with cart + cart_item
        CartAddItemReqVO req = new CartAddItemReqVO();
        req.setSkuId(1001L);
        req.setQuantity(1);

        cartService.addItem(6001L, 1L, "DINE_IN", req);

        // All three tables should have exactly 1 record each
        List<CartDO> carts = cartMapper.selectList();
        List<CartItemDO> items = cartItemMapper.selectList();
        List<CartEventLogDO> logs = cartEventLogMapper.selectList();

        assertThat(carts).hasSize(1);
        assertThat(items).hasSize(1);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getEventType()).isEqualTo("ITEM_ADDED");
    }

    @Test
    void eventLogFailureRollsBackEntireTransaction() throws Exception {
        // First, add an item normally
        CartAddItemReqVO req = new CartAddItemReqVO();
        req.setSkuId(1001L);
        req.setQuantity(1);
        cartService.addItem(6002L, 1L, "DINE_IN", req);

        // Drop the cart_event_log table to force failure on next operation
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE cart_event_log");
        }

        // Attempt update quantity — should fail because event log table is gone
        CartDO cart = cartMapper.selectList().stream()
                .filter(c -> c.getCustomerUserId() == 6002L)
                .findFirst().orElseThrow();
        CartItemDO item = cartItemMapper.selectList().stream()
                .filter(i -> i.getCartId().equals(cart.getId()))
                .findFirst().orElseThrow();

        // The transaction should fail — no partial update to cart_item quantity
        try {
            cartService.updateQuantity(6002L, 1L, item.getId(), 5);
        } catch (Exception e) {
            // Expected — event log table doesn't exist
        }

        // Recreate table for verification
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE cart_event_log (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        cart_id BIGINT NOT NULL,
                        event_type VARCHAR(32) NOT NULL,
                        event_time DATETIME NOT NULL,
                        operator_user_id BIGINT NOT NULL,
                        operator_role VARCHAR(32) NOT NULL,
                        sku_id BIGINT,
                        quantity_before INT,
                        quantity_after INT,
                        amount_before DECIMAL(18,4),
                        amount_after DECIMAL(18,4),
                        extra CLOB,
                        client_ip VARCHAR(64),
                        user_agent VARCHAR(512),
                        device_id VARCHAR(64),
                        create_time DATETIME NOT NULL
                    )
                    """);
        }

        // Verify cart_item quantity was NOT updated (rolled back)
        CartItemDO itemAfter = cartItemMapper.selectById(item.getId());
        assertThat(itemAfter.getQuantity()).isEqualTo(1); // original quantity, not 5

        // Verify cart total was NOT updated (rolled back)
        CartDO cartAfter = cartMapper.selectById(cart.getId());
        // Cart should still have the original total
        assertThat(cartAfter.getTotalQuantity()).isEqualTo(1);
    }
}
