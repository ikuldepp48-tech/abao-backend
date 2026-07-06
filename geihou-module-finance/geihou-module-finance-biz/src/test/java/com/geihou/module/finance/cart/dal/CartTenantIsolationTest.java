package com.geihou.module.finance.cart.dal;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.cart.CartTestConfig;
import com.geihou.module.finance.cart.CartTestSchemaInitializer;
import com.geihou.module.finance.cart.controller.app.customer.vo.CartAddItemReqVO;
import com.geihou.module.finance.cart.controller.app.customer.vo.CartVO;
import com.geihou.module.finance.cart.dal.dataobject.CartDO;
import com.geihou.module.finance.cart.dal.mapper.CartMapper;
import com.geihou.module.finance.cart.framework.CartBusinessException;
import com.geihou.module.finance.cart.service.CartService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cart tenant isolation test (AC-1).
 *
 * <p>Verifies that tenant A cannot query or modify carts from tenant B.
 * Verifies that the same customer_user_id in different tenants creates separate carts.
 */
@SpringBootTest(
        classes = CartTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:cart_tenant_iso_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class CartTenantIsolationTest {

    @Autowired
    private CartService cartService;
    @Autowired
    private CartMapper cartMapper;
    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        CartTestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void tenantACannotQueryCartOfTenantB() {
        // Create cart as tenant A
        TenantContextHolder.setTenantId(1L);
        CartAddItemReqVO req = new CartAddItemReqVO();
        req.setSkuId(1001L);
        req.setQuantity(1);
        CartVO vo = cartService.addItem(8001L, 1L, "DINE_IN", req);
        Long cartIdA = vo.getId();

        // Switch to tenant B
        TenantContextHolder.setTenantId(2L);

        // Tenant B's customer 8001 should not see tenant A's cart
        CartVO voB = cartService.getCurrentCart(8001L, 1L, "DINE_IN");
        assertThat(voB.getId()).isNotEqualTo(cartIdA);
    }

    @Test
    void sameCustomerIdInDifferentTenantsCreatesSeparateCarts() {
        // Tenant A
        TenantContextHolder.setTenantId(1L);
        cartService.addItem(8002L, 1L, "DINE_IN", buildReq(1001L, 1));

        // Tenant B
        TenantContextHolder.setTenantId(2L);
        cartService.addItem(8002L, 1L, "DINE_IN", buildReq(1001L, 1));

        // Verify 2 carts exist for the same customer_user_id but different tenants
        // Use TenantContextHolder.setIgnore to query across tenants
        TenantContextHolder.setIgnore(true);
        List<CartDO> allCarts = cartMapper.selectList();
        TenantContextHolder.setIgnore(false);

        assertThat(allCarts).hasSize(2);
        assertThat(allCarts.stream().map(CartDO::getTenantId))
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void updateQuantityFailsForOtherTenantItem() {
        // Create cart as tenant A
        TenantContextHolder.setTenantId(1L);
        CartVO vo = cartService.addItem(8003L, 1L, "DINE_IN", buildReq(1001L, 1));
        Long itemId = vo.getItems().get(0).getId();

        // Switch to tenant B — should not be able to modify tenant A's item
        TenantContextHolder.setTenantId(2L);
        assertThatThrownBy(() -> cartService.updateQuantity(8003L, 1L, itemId, 5))
                .isInstanceOf(CartBusinessException.class);
    }

    private CartAddItemReqVO buildReq(Long skuId, Integer qty) {
        CartAddItemReqVO req = new CartAddItemReqVO();
        req.setSkuId(skuId);
        req.setQuantity(qty);
        return req;
    }
}
