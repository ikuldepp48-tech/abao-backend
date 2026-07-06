package com.geihou.module.finance.cart.controller;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.cart.CartTestConfig;
import com.geihou.module.finance.cart.CartTestSchemaInitializer;
import com.geihou.module.finance.cart.controller.app.customer.CustomerCartController;
import com.geihou.module.finance.cart.controller.app.customer.vo.CartVO;
import com.geihou.common.pojo.CommonResult;
import com.geihou.module.finance.cart.service.CartService;
import com.geihou.module.finance.cart.controller.app.customer.vo.CartAddItemReqVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cart controller test.
 *
 * <p>Tests the 5 customer cart endpoints through the controller layer.
 */
@SpringBootTest(
        classes = CartTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:cart_controller_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class CartControllerTest {

    @Autowired
    private CustomerCartController cartController;
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
    void getCurrentCartReturnsEmptyCart() {
        CommonResult<CartVO> result = cartController.getCurrentCart(9001L, 1L, "DINE_IN");
        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getItemCount()).isEqualTo(0);
    }

    @Test
    void addItemReturnsCartWithItem() {
        CartAddItemReqVO req = new CartAddItemReqVO();
        req.setSkuId(1001L);
        req.setQuantity(2);

        CommonResult<CartVO> result = cartController.addItem(9002L, 1L, "DINE_IN", req);
        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().getItemCount()).isEqualTo(1);
        assertThat(result.getData().getTotalQuantity()).isEqualTo(2);
    }

    @Test
    void updateQuantityReturnsUpdatedCart() {
        CartAddItemReqVO req = new CartAddItemReqVO();
        req.setSkuId(1001L);
        req.setQuantity(1);
        CommonResult<CartVO> addResult = cartController.addItem(9003L, 1L, "DINE_IN", req);
        Long itemId = addResult.getData().getItems().get(0).getId();

        var updateReq = new com.geihou.module.finance.cart.controller.app.customer.vo.CartUpdateQuantityReqVO();
        updateReq.setQuantity(5);
        CommonResult<CartVO> result = cartController.updateQuantity(9003L, 1L, itemId, updateReq);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().getTotalQuantity()).isEqualTo(5);
    }

    @Test
    void removeItemReturnsUpdatedCart() {
        CartAddItemReqVO req = new CartAddItemReqVO();
        req.setSkuId(1001L);
        req.setQuantity(1);
        CommonResult<CartVO> addResult = cartController.addItem(9004L, 1L, "DINE_IN", req);
        Long itemId = addResult.getData().getItems().get(0).getId();

        CommonResult<CartVO> result = cartController.removeItem(9004L, 1L, itemId);
        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().getItemCount()).isEqualTo(0);
    }

    @Test
    void clearCartReturnsEmptyCart() {
        CartAddItemReqVO req = new CartAddItemReqVO();
        req.setSkuId(1001L);
        req.setQuantity(3);
        cartController.addItem(9005L, 1L, "DINE_IN", req);

        CommonResult<CartVO> result = cartController.clearCart(9005L, 1L);
        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().getItemCount()).isEqualTo(0);
        assertThat(result.getData().getTotalAmount()).isEqualByComparingTo(java.math.BigDecimal.ZERO);
    }

    // --- G1-04A follow-up: shopId validation on updateQuantity/removeItem ---

    @Test
    void updateQuantityFailsWhenShopIdMismatches() {
        // Add item to shop 1
        CartAddItemReqVO req = new CartAddItemReqVO();
        req.setSkuId(1001L);
        req.setQuantity(1);
        CommonResult<CartVO> addResult = cartController.addItem(9006L, 1L, "DINE_IN", req);
        Long itemId = addResult.getData().getItems().get(0).getId();

        // Attempt update with shop 2 — must fail
        var updateReq = new com.geihou.module.finance.cart.controller.app.customer.vo.CartUpdateQuantityReqVO();
        updateReq.setQuantity(5);
        CommonResult<CartVO> result = cartController.updateQuantity(9006L, 2L, itemId, updateReq);

        assertThat(result.getCode()).isNotEqualTo(0);
    }

    @Test
    void updateQuantitySucceedsWhenShopIdMatches() {
        // Add item to shop 1
        CartAddItemReqVO req = new CartAddItemReqVO();
        req.setSkuId(1001L);
        req.setQuantity(1);
        CommonResult<CartVO> addResult = cartController.addItem(9007L, 1L, "DINE_IN", req);
        Long itemId = addResult.getData().getItems().get(0).getId();

        // Update with matching shopId — must succeed
        var updateReq = new com.geihou.module.finance.cart.controller.app.customer.vo.CartUpdateQuantityReqVO();
        updateReq.setQuantity(5);
        CommonResult<CartVO> result = cartController.updateQuantity(9007L, 1L, itemId, updateReq);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().getTotalQuantity()).isEqualTo(5);
    }

    @Test
    void removeItemFailsWhenShopIdMismatches() {
        // Add item to shop 1
        CartAddItemReqVO req = new CartAddItemReqVO();
        req.setSkuId(1001L);
        req.setQuantity(1);
        CommonResult<CartVO> addResult = cartController.addItem(9008L, 1L, "DINE_IN", req);
        Long itemId = addResult.getData().getItems().get(0).getId();

        // Attempt remove with shop 2 — must fail
        CommonResult<CartVO> result = cartController.removeItem(9008L, 2L, itemId);

        assertThat(result.getCode()).isNotEqualTo(0);
    }

    @Test
    void removeItemSucceedsWhenShopIdMatches() {
        // Add item to shop 1
        CartAddItemReqVO req = new CartAddItemReqVO();
        req.setSkuId(1001L);
        req.setQuantity(1);
        CommonResult<CartVO> addResult = cartController.addItem(9009L, 1L, "DINE_IN", req);
        Long itemId = addResult.getData().getItems().get(0).getId();

        // Remove with matching shopId — must succeed
        CommonResult<CartVO> result = cartController.removeItem(9009L, 1L, itemId);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().getItemCount()).isEqualTo(0);
    }
}
