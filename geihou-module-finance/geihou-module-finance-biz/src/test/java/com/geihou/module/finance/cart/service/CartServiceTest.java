package com.geihou.module.finance.cart.service;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.api.cart.enums.CartStatusEnum;
import com.geihou.module.finance.cart.CartTestConfig;
import com.geihou.module.finance.cart.CartTestSchemaInitializer;
import com.geihou.module.finance.cart.controller.app.customer.vo.CartAddItemReqVO;
import com.geihou.module.finance.cart.controller.app.customer.vo.CartVO;
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
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cart service integration test.
 *
 * <p>Tests add item happy path, amount recalculation, auto cart creation,
 * active cart reuse, update quantity, remove item, clear cart, event log writing.
 */
@SpringBootTest(
        classes = CartTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:cart_service_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class CartServiceTest {

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
    void addItemCreatesCartAndWritesEventLog() {
        CartAddItemReqVO req = new CartAddItemReqVO();
        req.setSkuId(1001L);
        req.setQuantity(2);

        CartVO vo = cartService.addItem(5001L, 1L, "DINE_IN", req);

        assertThat(vo.getId()).isNotNull();
        assertThat(vo.getStatus()).isEqualTo(CartStatusEnum.ACTIVE.getCode());
        assertThat(vo.getChannel()).isEqualTo("DINE_IN");
        assertThat(vo.getItemCount()).isEqualTo(1);
        assertThat(vo.getTotalQuantity()).isEqualTo(2);
        assertThat(vo.getSubtotalAmount()).isEqualByComparingTo(new BigDecimal("24.00")); // 12.00 * 2
        assertThat(vo.getTotalAmount()).isEqualByComparingTo(new BigDecimal("24.00"));
        assertThat(vo.getItems()).hasSize(1);
        assertThat(vo.getItems().get(0).getSkuId()).isEqualTo(1001L);
        assertThat(vo.getItems().get(0).getSkuNameSnapshot()).isEqualTo("Beef Burger");
        assertThat(vo.getItems().get(0).getUnitPriceSnapshot()).isEqualByComparingTo(new BigDecimal("12.00"));
        assertThat(vo.getItems().get(0).getItemState()).isEqualTo("NORMAL");

        // Verify event log
        List<CartEventLogDO> logs = cartEventLogMapper.selectList();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getEventType()).isEqualTo("ITEM_ADDED");
        assertThat(logs.get(0).getSkuId()).isEqualTo(1001L);
        assertThat(logs.get(0).getQuantityAfter()).isEqualTo(2);
    }

    @Test
    void addItemReusesExistingActiveCart() {
        CartAddItemReqVO req1 = new CartAddItemReqVO();
        req1.setSkuId(1001L);
        req1.setQuantity(1);
        cartService.addItem(5002L, 1L, "DINE_IN", req1);

        CartAddItemReqVO req2 = new CartAddItemReqVO();
        req2.setSkuId(1002L);
        req2.setQuantity(1);
        CartVO vo = cartService.addItem(5002L, 1L, "DINE_IN", req2);

        // Should have 1 cart with 2 items
        List<CartDO> carts = cartMapper.selectList();
        assertThat(carts).hasSize(1);
        assertThat(vo.getItemCount()).isEqualTo(2);
    }

    @Test
    void addItemMergesSameSkuQuantity() {
        CartAddItemReqVO req1 = new CartAddItemReqVO();
        req1.setSkuId(1001L);
        req1.setQuantity(2);
        cartService.addItem(5003L, 1L, "DINE_IN", req1);

        CartAddItemReqVO req2 = new CartAddItemReqVO();
        req2.setSkuId(1001L);
        req2.setQuantity(3);
        CartVO vo = cartService.addItem(5003L, 1L, "DINE_IN", req2);

        assertThat(vo.getItemCount()).isEqualTo(1); // same SKU merged
        assertThat(vo.getTotalQuantity()).isEqualTo(5); // 2 + 3
        assertThat(vo.getSubtotalAmount()).isEqualByComparingTo(new BigDecimal("60.00")); // 12.00 * 5
    }

    @Test
    void addItemRejectsUnavailableSku() {
        CartAddItemReqVO req = new CartAddItemReqVO();
        req.setSkuId(3001L); // PAUSED SKU
        req.setQuantity(1);

        assertThatThrownBy(() -> cartService.addItem(5004L, 1L, "DINE_IN", req))
                .isInstanceOf(CartBusinessException.class);
    }

    @Test
    void updateQuantityRecalculatesAmount() {
        CartAddItemReqVO req = new CartAddItemReqVO();
        req.setSkuId(1001L);
        req.setQuantity(2);
        CartVO vo = cartService.addItem(5005L, 1L, "DINE_IN", req);
        Long itemId = vo.getItems().get(0).getId();

        CartVO updated = cartService.updateQuantity(5005L, 1L, itemId, 5);

        assertThat(updated.getTotalQuantity()).isEqualTo(5);
        assertThat(updated.getSubtotalAmount()).isEqualByComparingTo(new BigDecimal("60.00")); // 12.00 * 5

        // Verify event log
        List<CartEventLogDO> logs = cartEventLogMapper.selectList();
        assertThat(logs).hasSize(2); // ITEM_ADDED + ITEM_QUANTITY_CHANGED
        assertThat(logs.get(1).getEventType()).isEqualTo("ITEM_QUANTITY_CHANGED");
        assertThat(logs.get(1).getQuantityBefore()).isEqualTo(2);
        assertThat(logs.get(1).getQuantityAfter()).isEqualTo(5);
    }

    @Test
    void removeItemRecalculatesCart() {
        CartAddItemReqVO req1 = new CartAddItemReqVO();
        req1.setSkuId(1001L);
        req1.setQuantity(2);
        cartService.addItem(5006L, 1L, "DINE_IN", req1);

        CartAddItemReqVO req2 = new CartAddItemReqVO();
        req2.setSkuId(1002L);
        req2.setQuantity(1);
        CartVO vo = cartService.addItem(5006L, 1L, "DINE_IN", req2);
        Long itemIdToRemove = vo.getItems().stream()
                .filter(i -> i.getSkuId() == 1001L).findFirst().orElseThrow().getId();

        CartVO updated = cartService.removeItem(5006L, 1L, itemIdToRemove);

        assertThat(updated.getItemCount()).isEqualTo(1);
        assertThat(updated.getTotalQuantity()).isEqualTo(1);
        assertThat(updated.getSubtotalAmount()).isEqualByComparingTo(new BigDecimal("10.00")); // only chicken burger left

        // Verify event log
        List<CartEventLogDO> logs = cartEventLogMapper.selectList();
        assertThat(logs).hasSize(3); // ADD + ADD + REMOVE
        assertThat(logs.get(2).getEventType()).isEqualTo("ITEM_REMOVED");
    }

    @Test
    void clearCartResetsAllTotals() {
        CartAddItemReqVO req = new CartAddItemReqVO();
        req.setSkuId(1001L);
        req.setQuantity(2);
        cartService.addItem(5007L, 1L, "DINE_IN", req);

        CartVO vo = cartService.clearCart(5007L, 1L);

        assertThat(vo.getItemCount()).isEqualTo(0);
        assertThat(vo.getTotalQuantity()).isEqualTo(0);
        assertThat(vo.getSubtotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(vo.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(vo.getItems()).isEmpty();

        // Verify event log
        List<CartEventLogDO> logs = cartEventLogMapper.selectList();
        assertThat(logs).hasSize(2); // ADD + CLEAR
        assertThat(logs.get(1).getEventType()).isEqualTo("CART_CLEARED");
    }

    @Test
    void getCurrentCartCreatesCartIfNoneExists() {
        CartVO vo = cartService.getCurrentCart(5008L, 1L, "SELF_PICKUP");

        assertThat(vo.getId()).isNotNull();
        assertThat(vo.getStatus()).isEqualTo(CartStatusEnum.ACTIVE.getCode());
        assertThat(vo.getItemCount()).isEqualTo(0);
        assertThat(vo.getSubtotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(vo.getItems()).isEmpty();
    }

    // --- MF-1 fix: shopId-scoped active cart lookup ---

    @Test
    void sameCustomerCanHaveSeparateActiveCartsForDifferentShops() {
        // Add item to shop A (shopId=1)
        CartAddItemReqVO reqA = new CartAddItemReqVO();
        reqA.setSkuId(1001L);
        reqA.setQuantity(1);
        CartVO voA = cartService.addItem(5009L, 1L, "DINE_IN", reqA);

        // Add item to shop B (shopId=2)
        CartAddItemReqVO reqB = new CartAddItemReqVO();
        reqB.setSkuId(1002L);
        reqB.setQuantity(1);
        CartVO voB = cartService.addItem(5009L, 2L, "DINE_IN", reqB);

        // Two separate carts exist for the same customer, different shops
        assertThat(voA.getId()).isNotEqualTo(voB.getId());
        assertThat(voA.getShopId()).isEqualTo(1L);
        assertThat(voB.getShopId()).isEqualTo(2L);

        // Shop A cart has only sku 1001, shop B cart has only sku 1002
        assertThat(voA.getItems()).hasSize(1);
        assertThat(voA.getItems().get(0).getSkuId()).isEqualTo(1001L);
        assertThat(voB.getItems()).hasSize(1);
        assertThat(voB.getItems().get(0).getSkuId()).isEqualTo(1002L);
    }

    @Test
    void addItemToShopBDoesNotReuseShopACart() {
        // Create cart in shop A
        CartAddItemReqVO reqA = new CartAddItemReqVO();
        reqA.setSkuId(1001L);
        reqA.setQuantity(2);
        cartService.addItem(5010L, 1L, "DINE_IN", reqA);

        // Add item to shop B — must NOT reuse shop A cart
        CartAddItemReqVO reqB = new CartAddItemReqVO();
        reqB.setSkuId(1002L);
        reqB.setQuantity(1);
        CartVO voB = cartService.addItem(5010L, 2L, "DINE_IN", reqB);

        // The returned cart must belong to shop B, not shop A
        assertThat(voB.getShopId()).isEqualTo(2L);

        // Verify two carts exist in DB for this customer
        List<CartDO> carts = cartMapper.selectList();
        assertThat(carts).hasSize(2);
        assertThat(carts).anyMatch(c -> c.getShopId().equals(1L));
        assertThat(carts).anyMatch(c -> c.getShopId().equals(2L));
    }

    @Test
    void sameCustomerSameShopReusesOneActiveCart() {
        // First add to shop 1
        CartAddItemReqVO req1 = new CartAddItemReqVO();
        req1.setSkuId(1001L);
        req1.setQuantity(1);
        CartVO vo1 = cartService.addItem(5011L, 1L, "DINE_IN", req1);

        // Second add to same shop 1 — must reuse same cart
        CartAddItemReqVO req2 = new CartAddItemReqVO();
        req2.setSkuId(1002L);
        req2.setQuantity(1);
        CartVO vo2 = cartService.addItem(5011L, 1L, "DINE_IN", req2);

        assertThat(vo1.getId()).isEqualTo(vo2.getId());
        assertThat(vo2.getItemCount()).isEqualTo(2);

        List<CartDO> carts = cartMapper.selectList();
        assertThat(carts).hasSize(1);
    }

    // --- G1-04A follow-up: shopId validation on updateQuantity/removeItem ---

    @Test
    void updateQuantityFailsWhenShopIdMismatches() {
        // Add item to shop 1
        CartAddItemReqVO req = new CartAddItemReqVO();
        req.setSkuId(1001L);
        req.setQuantity(2);
        CartVO vo = cartService.addItem(5012L, 1L, "DINE_IN", req);
        Long itemId = vo.getItems().get(0).getId();

        // Attempt update with shop 2 — must fail
        assertThatThrownBy(() -> cartService.updateQuantity(5012L, 2L, itemId, 5))
                .isInstanceOf(CartBusinessException.class);

        // Verify quantity unchanged
        List<CartItemDO> items = cartItemMapper.selectList();
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getQuantity()).isEqualTo(2);
    }

    @Test
    void updateQuantitySucceedsWhenShopIdMatches() {
        // Add item to shop 1
        CartAddItemReqVO req = new CartAddItemReqVO();
        req.setSkuId(1001L);
        req.setQuantity(2);
        CartVO vo = cartService.addItem(5013L, 1L, "DINE_IN", req);
        Long itemId = vo.getItems().get(0).getId();

        // Update with matching shopId — must succeed
        CartVO updated = cartService.updateQuantity(5013L, 1L, itemId, 5);
        assertThat(updated.getTotalQuantity()).isEqualTo(5);
        assertThat(updated.getSubtotalAmount()).isEqualByComparingTo(new BigDecimal("60.00"));
    }

    @Test
    void removeItemFailsWhenShopIdMismatches() {
        // Add item to shop 1
        CartAddItemReqVO req = new CartAddItemReqVO();
        req.setSkuId(1001L);
        req.setQuantity(1);
        CartVO vo = cartService.addItem(5014L, 1L, "DINE_IN", req);
        Long itemId = vo.getItems().get(0).getId();

        // Attempt remove with shop 2 — must fail
        assertThatThrownBy(() -> cartService.removeItem(5014L, 2L, itemId))
                .isInstanceOf(CartBusinessException.class);

        // Verify item still exists (not removed)
        List<CartItemDO> items = cartItemMapper.selectList();
        assertThat(items).hasSize(1);
    }

    @Test
    void removeItemSucceedsWhenShopIdMatches() {
        // Add item to shop 1
        CartAddItemReqVO req = new CartAddItemReqVO();
        req.setSkuId(1001L);
        req.setQuantity(1);
        CartVO vo = cartService.addItem(5015L, 1L, "DINE_IN", req);
        Long itemId = vo.getItems().get(0).getId();

        // Remove with matching shopId — must succeed
        CartVO updated = cartService.removeItem(5015L, 1L, itemId);
        assertThat(updated.getItemCount()).isEqualTo(0);
        assertThat(updated.getItems()).isEmpty();
    }
}
