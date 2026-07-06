package com.geihou.module.finance.cart.dal;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.cart.CartTestConfig;
import com.geihou.module.finance.cart.CartTestSchemaInitializer;
import com.geihou.module.finance.cart.controller.app.customer.vo.CartAddItemReqVO;
import com.geihou.module.finance.cart.controller.app.customer.vo.CartVO;
import com.geihou.module.finance.cart.dal.dataobject.CartDO;
import com.geihou.module.finance.cart.dal.mapper.CartMapper;
import com.geihou.module.finance.cart.service.CartService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cart optimistic lock test (AC-7).
 *
 * <p>Verifies that cart has a version field for optimistic locking
 * and that version increments on each update.
 */
@SpringBootTest(
        classes = CartTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:cart_opt_lock_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class CartOptimisticLockTest {

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
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void cartHasVersionFieldThatIncrements() {
        CartAddItemReqVO req = new CartAddItemReqVO();
        req.setSkuId(1001L);
        req.setQuantity(1);

        // First add — cart created and updated (version increments from 0 to 1)
        CartVO vo1 = cartService.addItem(10001L, 1L, "DINE_IN", req);
        CartDO cart1 = cartMapper.selectById(vo1.getId());
        assertThat(cart1.getVersion()).isGreaterThanOrEqualTo(0);

        // Second add — cart updated again, version should increment
        req.setSkuId(1002L);
        req.setQuantity(1);
        CartVO vo2 = cartService.addItem(10001L, 1L, "DINE_IN", req);
        CartDO cart2 = cartMapper.selectById(vo2.getId());
        assertThat(cart2.getVersion()).isGreaterThan(cart1.getVersion());
    }

    @Test
    void versionIncrementsOnQuantityUpdate() {
        CartAddItemReqVO req = new CartAddItemReqVO();
        req.setSkuId(1001L);
        req.setQuantity(1);
        CartVO vo = cartService.addItem(10002L, 1L, "DINE_IN", req);

        CartDO beforeUpdate = cartMapper.selectById(vo.getId());
        int versionBefore = beforeUpdate.getVersion();

        cartService.updateQuantity(10002L, 1L, vo.getItems().get(0).getId(), 3);

        CartDO afterUpdate = cartMapper.selectById(vo.getId());
        assertThat(afterUpdate.getVersion()).isGreaterThan(versionBefore);
    }

    @Test
    void versionIncrementsOnClearCart() {
        CartAddItemReqVO req = new CartAddItemReqVO();
        req.setSkuId(1001L);
        req.setQuantity(1);
        CartVO vo = cartService.addItem(10003L, 1L, "DINE_IN", req);

        CartDO beforeClear = cartMapper.selectById(vo.getId());
        int versionBefore = beforeClear.getVersion();

        cartService.clearCart(10003L, 1L);

        CartDO afterClear = cartMapper.selectById(vo.getId());
        assertThat(afterClear.getVersion()).isGreaterThan(versionBefore);
    }
}
