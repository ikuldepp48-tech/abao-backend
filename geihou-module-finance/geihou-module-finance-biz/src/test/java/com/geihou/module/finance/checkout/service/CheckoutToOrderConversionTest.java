package com.geihou.module.finance.checkout.service;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.api.cart.enums.CartStatusEnum;
import com.geihou.module.finance.api.order.enums.OrderStatusEnum;
import com.geihou.module.finance.cart.controller.app.customer.vo.CartAddItemReqVO;
import com.geihou.module.finance.cart.dal.dataobject.CartDO;
import com.geihou.module.finance.cart.dal.dataobject.CartItemDO;
import com.geihou.module.finance.cart.dal.mapper.CartItemMapper;
import com.geihou.module.finance.cart.dal.mapper.CartMapper;
import com.geihou.module.finance.cart.framework.CartBusinessException;
import com.geihou.module.finance.cart.service.CartService;
import com.geihou.module.finance.checkout.CheckoutToOrderTestConfig;
import com.geihou.module.finance.checkout.CheckoutToOrderTestSchemaInitializer;
import com.geihou.module.finance.checkout.controller.app.customer.vo.CheckoutInitiateReqVO;
import com.geihou.module.finance.checkout.controller.app.customer.vo.CheckoutPayReqVO;
import com.geihou.module.finance.checkout.controller.app.customer.vo.CheckoutSessionVO;
import com.geihou.module.finance.checkout.dal.dataobject.CheckoutSessionDO;
import com.geihou.module.finance.checkout.dal.mapper.CheckoutSessionMapper;
import com.geihou.module.finance.checkout.framework.CheckoutStatusEnum;
import com.geihou.module.finance.order.dal.dataobject.OrderDO;
import com.geihou.module.finance.order.dal.dataobject.OrderEventLogDO;
import com.geihou.module.finance.order.dal.dataobject.OrderItemDO;
import com.geihou.module.finance.order.dal.dataobject.OrderPaymentDO;
import com.geihou.module.finance.order.dal.mapper.OrderEventLogMapper;
import com.geihou.module.finance.order.dal.mapper.OrderItemMapper;
import com.geihou.module.finance.order.dal.mapper.OrderMapper;
import com.geihou.module.finance.order.dal.mapper.OrderPaymentMapper;
import com.geihou.module.finance.order.enums.OrderEventTypeEnum;
import com.geihou.module.finance.order.enums.OrderItemTypeEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * G1-04C Checkout-to-Order Conversion integration tests.
 *
 * <p>Tests the 8 required cases from the G1-04C slice package §7 Test Plan:
 * <ol>
 *   <li>payThenConvertCreatesOrderAndLinksCheckout</li>
 *   <li>convertSameCheckoutTwiceDoesNotDuplicateOrder</li>
 *   <li>conversionRejectsUnpaidOrExpiredCheckout</li>
 *   <li>conversionRejectsCrossTenantSession</li>
 *   <li>conversionRejectsCrossShopOrCustomerMismatch</li>
 *   <li>conversionUsesCartSnapshots</li>
 *   <li>conversionRollbackDoesNotLeaveHalfLinkedOrder</li>
 *   <li>existingPaidUnlinkedSessionCanBeConvertedOrIsExplicitlyDeferred</li>
 * </ol>
 */
@SpringBootTest(
        classes = CheckoutToOrderTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:checkout_to_order_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class CheckoutToOrderConversionTest {

    @Autowired
    private CheckoutService checkoutService;
    @Autowired
    private CartService cartService;
    @Autowired
    private CartMapper cartMapper;
    @Autowired
    private CartItemMapper cartItemMapper;
    @Autowired
    private CheckoutSessionMapper checkoutSessionMapper;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderItemMapper orderItemMapper;
    @Autowired
    private OrderEventLogMapper orderEventLogMapper;
    @Autowired
    private OrderPaymentMapper orderPaymentMapper;
    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        CheckoutToOrderTestSchemaInitializer.initialize(dataSource);
        TenantContextHolder.clear();
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // --- Helpers ---

    private CartDO createCartWithItems(Long customerUserId, Long shopId) {
        CartAddItemReqVO req = new CartAddItemReqVO();
        req.setSkuId(1001L);
        req.setQuantity(2);
        cartService.addItem(customerUserId, shopId, "DINE_IN", req);
        return cartMapper.selectList().stream()
                .filter(c -> c.getCustomerUserId().equals(customerUserId))
                .findFirst().orElseThrow();
    }

    private CheckoutInitiateReqVO buildInitiateReq(Long customerUserId, Long shopId, String idempotentKey) {
        CheckoutInitiateReqVO req = new CheckoutInitiateReqVO();
        req.setCustomerUserId(customerUserId);
        req.setShopId(shopId);
        req.setChannel("DINE_IN");
        req.setIdempotentKey(idempotentKey);
        return req;
    }

    private CheckoutSessionVO paySession(Long customerUserId, Long shopId, String idempotentKey) {
        createCartWithItems(customerUserId, shopId);
        CheckoutSessionVO initiated = checkoutService.initiateCheckout(
                buildInitiateReq(customerUserId, shopId, idempotentKey));
        CheckoutPayReqVO payReq = new CheckoutPayReqVO();
        payReq.setPaymentMethod("WECHAT_PAY");
        return checkoutService.paySession(initiated.getSessionToken(), payReq);
    }

    /**
     * G1-04D helper: Reset a session's order_id to null and delete the linked order,
     * simulating the G1-04B state (PAID + null orderId) for backfill/retry tests.
     */
    private void resetSessionToPaidUnlinked(Long sessionId, Long orderId) {
        // Delete the order and its items/event logs/payments
        if (orderId != null) {
            orderItemMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OrderItemDO>()
                    .eq(OrderItemDO::getOrderId, orderId));
            orderEventLogMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OrderEventLogDO>()
                    .eq(OrderEventLogDO::getOrderId, orderId));
            OrderPaymentDO payment = orderPaymentMapper.selectOne(OrderPaymentDO::getOrderId, orderId);
            if (payment != null) {
                orderPaymentMapper.deleteById(payment.getId());
            }
            orderMapper.deleteById(orderId);
        }
        // Reset checkout_session.order_id to null using LambdaUpdateWrapper
        // (MyBatis-Plus updateById does not update null fields by default)
        checkoutSessionMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<CheckoutSessionDO>()
                        .eq(CheckoutSessionDO::getId, sessionId)
                        .set(CheckoutSessionDO::getOrderId, null));
    }

    // --- Test 1: payThenConvertCreatesOrderAndLinksCheckout ---

    @Test
    void payThenConvertCreatesOrderAndLinksCheckout() {
        CheckoutSessionVO paid = paySession(8001L, 1L, "idem-conv-8001");

        // G1-04D: pay auto-converts — orderId is non-null
        assertThat(paid.getStatus()).isEqualTo(CheckoutStatusEnum.PAID.getCode());
        assertThat(paid.getOrderId()).isNotNull();

        // Explicit convert is idempotent — returns same orderId
        CheckoutSessionVO result = checkoutService.convertToOrder(paid.getSessionToken());

        // Order is created and linked
        assertThat(result.getOrderId()).isEqualTo(paid.getOrderId());
        assertThat(result.getStatus()).isEqualTo(CheckoutStatusEnum.PAID.getCode());

        // Verify order in DB
        OrderDO order = orderMapper.selectById(result.getOrderId());
        assertThat(order).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatusEnum.PAID.getCode());
        assertThat(order.getTotalAmount()).isEqualByComparingTo(new BigDecimal("24.00")); // 12.00 * 2
        assertThat(order.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(order.getPaidAmount()).isEqualByComparingTo(new BigDecimal("24.00"));
        assertThat(order.getPaymentMethod()).isEqualTo("WECHAT_PAY");
        assertThat(order.getPayTime()).isNotNull();
        // G1-04C final-review: order.payTime must preserve checkout_session.paymentTime
        CheckoutSessionDO paidSession = checkoutSessionMapper.selectById(paid.getId());
        assertThat(order.getPayTime()).isEqualTo(paidSession.getPaymentTime());
        assertThat(order.getChannel()).isEqualTo("DINE_IN");
        assertThat(order.getShopId()).isEqualTo(1L);
        assertThat(order.getCustomerUserId()).isEqualTo(8001L);

        // Verify order items
        List<OrderItemDO> items = orderItemMapper.selectList(OrderItemDO::getOrderId, order.getId());
        assertThat(items).hasSize(1);
        OrderItemDO item = items.get(0);
        assertThat(item.getSkuId()).isEqualTo(1001L);
        assertThat(item.getSkuName()).isEqualTo("Beef Burger"); // from cart snapshot
        assertThat(item.getUnitPrice()).isEqualByComparingTo(new BigDecimal("12.00")); // from cart snapshot
        assertThat(item.getQuantity()).isEqualByComparingTo(new BigDecimal("2"));
        assertThat(item.getItemTotal()).isEqualByComparingTo(new BigDecimal("24.00")); // itemSubtotal
        assertThat(item.getItemDiscount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(item.getItemPaid()).isEqualByComparingTo(new BigDecimal("24.00")); // cart itemTotal
        assertThat(item.getSkuCode()).isEqualTo("SKU_BEEF"); // from ProductApi metadata
        assertThat(item.getSpuName()).isEqualTo("Burger"); // from ProductApi metadata
        assertThat(item.getCategoryId()).isEqualTo(10L); // from ProductApi metadata
        assertThat(item.getItemStatus()).isEqualTo(OrderItemTypeEnum.PENDING.getCode());

        // Verify event logs: CREATE + PAY
        List<OrderEventLogDO> eventLogs = orderEventLogMapper.selectList(
                OrderEventLogDO::getOrderId, order.getId());
        assertThat(eventLogs).hasSize(2);
        assertThat(eventLogs.stream().map(OrderEventLogDO::getEventType))
                .contains(OrderEventTypeEnum.CREATE.getCode(), OrderEventTypeEnum.PAY.getCode());

        // Verify payment record
        OrderPaymentDO payment = orderPaymentMapper.selectOne(
                OrderPaymentDO::getOrderId, order.getId());
        assertThat(payment).isNotNull();
        assertThat(payment.getPaymentMethod()).isEqualTo("WECHAT_PAY");
        assertThat(payment.getPaymentAmount()).isEqualByComparingTo(new BigDecimal("24.00"));
        // G1-04C final-review: order_payment audit fields must preserve checkout_session.paymentTime
        assertThat(payment.getInitiatedTime()).isEqualTo(paidSession.getPaymentTime());
        assertThat(payment.getPaidTime()).isEqualTo(paidSession.getPaymentTime());

        // Verify checkout_session.order_id is linked
        CheckoutSessionDO session = checkoutSessionMapper.selectById(paid.getId());
        assertThat(session.getOrderId()).isEqualTo(order.getId());
    }

    // --- Test 2: convertSameCheckoutTwiceDoesNotDuplicateOrder ---

    @Test
    void convertSameCheckoutTwiceDoesNotDuplicateOrder() {
        CheckoutSessionVO paid = paySession(8002L, 1L, "idem-conv-8002");

        // G1-04D: pay already auto-converted — orderId is set
        assertThat(paid.getOrderId()).isNotNull();

        // Explicit convert (idempotent) — returns same orderId
        CheckoutSessionVO first = checkoutService.convertToOrder(paid.getSessionToken());
        assertThat(first.getOrderId()).isEqualTo(paid.getOrderId());

        // Second explicit convert — still same orderId
        CheckoutSessionVO second = checkoutService.convertToOrder(paid.getSessionToken());
        assertThat(second.getOrderId()).isEqualTo(first.getOrderId());

        // No duplicate order
        Long orderCount = orderMapper.selectCount(
                com.geihou.module.finance.order.dal.dataobject.OrderDO::getCustomerUserId, 8002L);
        assertThat(orderCount).isEqualTo(1);
    }

    // --- Test 3: conversionRejectsUnpaidOrExpiredCheckout ---

    @Test
    void conversionRejectsUnpaidOrExpiredCheckout() {
        // Unpaid (INITIATED) session
        createCartWithItems(8003L, 1L);
        CheckoutSessionVO initiated = checkoutService.initiateCheckout(
                buildInitiateReq(8003L, 1L, "idem-conv-8003"));

        assertThatThrownBy(() -> checkoutService.convertToOrder(initiated.getSessionToken()))
                .isInstanceOf(CartBusinessException.class)
                .hasMessageContaining("not PAID");

        // Expired session
        createCartWithItems(8004L, 1L);
        CheckoutSessionVO initiated2 = checkoutService.initiateCheckout(
                buildInitiateReq(8004L, 1L, "idem-conv-8004"));
        CheckoutSessionDO session = checkoutSessionMapper.selectById(initiated2.getId());
        session.setExpireTime(LocalDateTime.now().minusMinutes(1));
        checkoutSessionMapper.updateById(session);
        checkoutService.querySession(initiated2.getSessionToken()); // trigger lazy expiry

        assertThatThrownBy(() -> checkoutService.convertToOrder(initiated2.getSessionToken()))
                .isInstanceOf(CartBusinessException.class);
    }

    // --- Test 4: conversionRejectsCrossTenantSession ---

    @Test
    void conversionRejectsCrossTenantSession() {
        // G1-04D: pay auto-converts — session is PAID + orderId set
        CheckoutSessionVO paid = paySession(8005L, 1L, "idem-conv-8005");

        // Switch to different tenant
        TenantContextHolder.setTenantId(2L);

        // Cross-tenant convert should fail with CART_NOT_FOUND
        assertThatThrownBy(() -> checkoutService.convertToOrder(paid.getSessionToken()))
                .isInstanceOf(CartBusinessException.class);
    }

    // --- Test 5: conversionRejectsCrossShopOrCustomerMismatch ---

    @Test
    void conversionRejectsCrossShopOrCustomerMismatch() {
        // G1-04D: pay auto-converts — need to reset to PAID + null orderId for this test
        CheckoutSessionVO paid = paySession(8006L, 1L, "idem-conv-8006");
        resetSessionToPaidUnlinked(paid.getId(), paid.getOrderId());

        // Tamper with cart's shopId to create a mismatch
        CheckoutSessionDO session = checkoutSessionMapper.selectById(paid.getId());
        CartDO cart = cartMapper.selectById(session.getCartId());
        cart.setShopId(999L); // different shop
        cartMapper.updateById(cart);

        assertThatThrownBy(() -> checkoutService.convertToOrder(paid.getSessionToken()))
                .isInstanceOf(CartBusinessException.class)
                .hasMessageContaining("does not match session shopId");

        // No order should be created
        assertThat(orderMapper.selectList()).isEmpty();
    }

    // --- Test 5b: conversionRejectsCustomerMismatch ---

    @Test
    void conversionRejectsCustomerMismatch() {
        // G1-04D: pay auto-converts — need to reset to PAID + null orderId for this test
        CheckoutSessionVO paid = paySession(8010L, 1L, "idem-conv-8010");
        resetSessionToPaidUnlinked(paid.getId(), paid.getOrderId());

        // Tamper with cart's customerUserId to create a mismatch
        CheckoutSessionDO session = checkoutSessionMapper.selectById(paid.getId());
        CartDO cart = cartMapper.selectById(session.getCartId());
        cart.setCustomerUserId(88888L); // different customer
        cartMapper.updateById(cart);

        assertThatThrownBy(() -> checkoutService.convertToOrder(paid.getSessionToken()))
                .isInstanceOf(CartBusinessException.class)
                .hasMessageContaining("does not match session customerUserId");

        // No order should be created
        assertThat(orderMapper.selectList()).isEmpty();
    }

    // --- Test 6: conversionUsesCartSnapshots ---

    @Test
    void conversionUsesCartSnapshots() {
        // G1-04D: pay auto-converts with original snapshot, then we reset + tamper + re-convert
        createCartWithItems(8007L, 1L);
        CheckoutSessionVO initiated = checkoutService.initiateCheckout(
                buildInitiateReq(8007L, 1L, "idem-conv-8007"));
        CheckoutPayReqVO payReq = new CheckoutPayReqVO();
        payReq.setPaymentMethod("WECHAT_PAY");
        CheckoutSessionVO paid = checkoutService.paySession(initiated.getSessionToken(), payReq);

        // Reset to PAID + null orderId so we can re-convert with tampered snapshots
        resetSessionToPaidUnlinked(paid.getId(), paid.getOrderId());

        // Tamper with cart item snapshot to prove conversion uses snapshot, not ProductApi
        CheckoutSessionDO session = checkoutSessionMapper.selectById(paid.getId());
        List<CartItemDO> cartItems = cartItemMapper.selectList(
                CartItemDO::getCartId, session.getCartId());
        CartItemDO cartItem = cartItems.get(0);
        // Change snapshot price to a value different from ProductApi's 12.00
        cartItem.setUnitPriceSnapshot(new BigDecimal("9.99"));
        cartItem.setSkuNameSnapshot("Modified Name");
        cartItem.setItemSubtotal(new BigDecimal("19.98")); // 9.99 * 2
        cartItem.setItemTotal(new BigDecimal("19.98")); // after-discount (no discount)
        cartItemMapper.updateById(cartItem);

        // Convert — should use the modified cart snapshot, NOT ProductApi's 12.00
        CheckoutSessionVO result = checkoutService.convertToOrder(paid.getSessionToken());
        assertThat(result.getOrderId()).isNotNull();

        OrderDO order = orderMapper.selectById(result.getOrderId());
        // Order totalAmount = session.subtotalAmount (original cart subtotal, unchanged)
        // But item-level should reflect the tampered snapshot
        List<OrderItemDO> items = orderItemMapper.selectList(OrderItemDO::getOrderId, order.getId());
        assertThat(items).hasSize(1);
        OrderItemDO item = items.get(0);

        // unitPrice from cart snapshot (9.99), NOT ProductApi (12.00)
        assertThat(item.getUnitPrice()).isEqualByComparingTo(new BigDecimal("9.99"));
        // skuName from cart snapshot
        assertThat(item.getSkuName()).isEqualTo("Modified Name");
        // itemTotal = cart itemSubtotal (pre-discount)
        assertThat(item.getItemTotal()).isEqualByComparingTo(new BigDecimal("19.98"));
        // itemPaid = cart itemTotal (after-discount)
        assertThat(item.getItemPaid()).isEqualByComparingTo(new BigDecimal("19.98"));
        // skuCode still from ProductApi metadata
        assertThat(item.getSkuCode()).isEqualTo("SKU_BEEF");
        // spuName still from ProductApi metadata
        assertThat(item.getSpuName()).isEqualTo("Burger");
    }

    // --- Test 7: conversionRollbackDoesNotLeaveHalfLinkedOrder ---

    @Test
    void conversionRollbackDoesNotLeaveHalfLinkedOrder() {
        // G1-04D: pay auto-converts — need to reset to PAID + null orderId for this test
        CheckoutSessionVO paid = paySession(8008L, 1L, "idem-conv-8008");
        resetSessionToPaidUnlinked(paid.getId(), paid.getOrderId());

        // Sabotage the payment method to cause markOrderPaid to fail
        // (PaymentMethodEnum.fromCode will throw for unknown method)
        CheckoutSessionDO session = checkoutSessionMapper.selectById(paid.getId());
        session.setPaymentMethod("INVALID_METHOD");
        checkoutSessionMapper.updateById(session);

        // Conversion should fail
        assertThatThrownBy(() -> checkoutService.convertToOrder(paid.getSessionToken()))
                .isInstanceOf(RuntimeException.class);

        // No order should exist (rollback)
        assertThat(orderMapper.selectList()).isEmpty();

        // checkout_session.order_id should still be null (no half-link)
        CheckoutSessionDO reloaded = checkoutSessionMapper.selectById(paid.getId());
        assertThat(reloaded.getOrderId()).isNull();
    }

    // --- Test 8: existingPaidUnlinkedSessionCanBeConvertedOrIsExplicitlyDeferred ---

    @Test
    void existingPaidUnlinkedSessionCanBeConvertedOrIsExplicitlyDeferred() {
        // G1-04D: pay auto-converts — orderId is non-null
        CheckoutSessionVO paid = paySession(8009L, 1L, "idem-conv-8009");
        assertThat(paid.getStatus()).isEqualTo(CheckoutStatusEnum.PAID.getCode());
        assertThat(paid.getOrderId()).isNotNull();

        // Simulate G1-04B state: reset to PAID + null orderId (backfill scenario)
        resetSessionToPaidUnlinked(paid.getId(), paid.getOrderId());

        // Verify reset worked: PAID but order_id is null
        CheckoutSessionDO resetSession = checkoutSessionMapper.selectById(paid.getId());
        assertThat(resetSession.getStatus()).isEqualTo(CheckoutStatusEnum.PAID.getCode());
        assertThat(resetSession.getOrderId()).isNull();

        // Backfill: convert the existing PAID + unlinked session
        CheckoutSessionVO result = checkoutService.convertToOrder(paid.getSessionToken());

        // Order is created and linked — backfill succeeds
        assertThat(result.getOrderId()).isNotNull();
        assertThat(result.getStatus()).isEqualTo(CheckoutStatusEnum.PAID.getCode());

        OrderDO order = orderMapper.selectById(result.getOrderId());
        assertThat(order).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatusEnum.PAID.getCode());

        // checkout_session.order_id is now non-null
        CheckoutSessionDO session = checkoutSessionMapper.selectById(paid.getId());
        assertThat(session.getOrderId()).isEqualTo(order.getId());
    }

    // --- Test 9: autoConversionFailure_sessionStateIsCorrect ---

    /**
     * G1-04D: Prove that when auto-conversion fails after pay commits PAID,
     * the session/response remains PAID with orderId null (保金不丢) and no order is linked.
     *
     * <p>Strategy: Create a cart with a SKU (9999) that does not exist in MockProductApi.
     * The pay flow ({@code paySessionInternal}) succeeds and persists PAID, but the
     * subsequent auto-conversion ({@code createFromCheckout}) fails because
     * {@code productApi.batchGetSkus} returns no match, triggering SKU_NOT_FOUND.
     * The {@code autoConvertAfterPay} catch block re-queries the session and returns
     * PAID + orderId null, proving the 保金不丢 invariant.
     */
    @Test
    void autoConversionFailure_sessionStateIsCorrect() {
        // 1. Directly insert a cart with a non-existent SKU (9999), bypassing CartService
        //    validation so that checkout can initiate but conversion will fail.
        Long customerUserId = 8011L;
        Long shopId = 1L;
        LocalDateTime now = LocalDateTime.now();

        CartDO cart = new CartDO();
        cart.setTenantId(1L);
        cart.setCustomerUserId(customerUserId);
        cart.setShopId(shopId);
        cart.setChannel("DINE_IN");
        cart.setStatus(CartStatusEnum.ACTIVE.getCode());
        cart.setItemCount(1);
        cart.setTotalQuantity(2);
        cart.setSubtotalAmount(new BigDecimal("24.00"));
        cart.setDiscountAmount(BigDecimal.ZERO);
        cart.setTotalAmount(new BigDecimal("24.00"));
        cart.setBusinessDate(java.time.LocalDate.now());
        cart.setIsStaffAssisted(false);
        cart.setVersion(0);
        cart.setLastActivityTime(now);
        cart.setCreator(String.valueOf(customerUserId));
        cart.setCreateTime(now);
        cart.setUpdater(String.valueOf(customerUserId));
        cart.setUpdateTime(now);
        cart.setDeleted(false);
        cartMapper.insert(cart);

        CartItemDO cartItem = new CartItemDO();
        cartItem.setTenantId(1L);
        cartItem.setCartId(cart.getId());
        cartItem.setSkuId(9999L); // NOT in MockProductApi — will cause conversion failure
        cartItem.setSpuId(999L);  // NOT in MockProductApi either
        cartItem.setSkuNameSnapshot("Phantom Item");
        cartItem.setUnitPriceSnapshot(new BigDecimal("12.00"));
        cartItem.setQuantity(2);
        cartItem.setOptionsExtraPrice(BigDecimal.ZERO);
        cartItem.setItemSubtotal(new BigDecimal("24.00"));
        cartItem.setItemDiscount(BigDecimal.ZERO);
        cartItem.setItemTotal(new BigDecimal("24.00"));
        cartItem.setItemState("NORMAL");
        cartItem.setCreator(String.valueOf(customerUserId));
        cartItem.setCreateTime(now);
        cartItem.setUpdater(String.valueOf(customerUserId));
        cartItem.setUpdateTime(now);
        cartItem.setDeleted(false);
        cartItemMapper.insert(cartItem);

        // 2. Initiate checkout — reads from cart, does not need ProductApi
        CheckoutSessionVO initiated = checkoutService.initiateCheckout(
                buildInitiateReq(customerUserId, shopId, "idem-conv-auto-fail"));
        assertThat(initiated.getStatus()).isEqualTo(CheckoutStatusEnum.INITIATED.getCode());

        // 3. Pay — paySessionInternal commits PAID, then autoConvertAfterPay fails
        CheckoutPayReqVO payReq = new CheckoutPayReqVO();
        payReq.setPaymentMethod("WECHAT_PAY");
        CheckoutSessionVO payResult = checkoutService.paySession(initiated.getSessionToken(), payReq);

        // 4. Response must be PAID with orderId null (保金不丢)
        assertThat(payResult.getStatus()).isEqualTo(CheckoutStatusEnum.PAID.getCode());
        assertThat(payResult.getOrderId()).isNull();
        assertThat(payResult.getPaymentMethod()).isEqualTo("WECHAT_PAY");
        assertThat(payResult.getPaymentTime()).isNotNull();
        assertThat(payResult.getPaymentTradeNo()).isNotNull();

        // 5. DB session must be PAID with orderId null
        CheckoutSessionDO dbSession = checkoutSessionMapper.selectById(payResult.getId());
        assertThat(dbSession.getStatus()).isEqualTo(CheckoutStatusEnum.PAID.getCode());
        assertThat(dbSession.getOrderId()).isNull();

        // 6. No order should exist (conversion rolled back)
        assertThat(orderMapper.selectList()).isEmpty();
        assertThat(orderItemMapper.selectList()).isEmpty();
        assertThat(orderPaymentMapper.selectList()).isEmpty();

        // 7. Cart should be CONVERTED (pay committed this transition)
        CartDO updatedCart = cartMapper.selectById(cart.getId());
        assertThat(updatedCart.getStatus()).isEqualTo(CartStatusEnum.CONVERTED.getCode());

        // 8. Retry via explicit /convert — still fails because SKU is still missing,
        //    proving the session is recoverable via retry (idempotent failure path)
        assertThatThrownBy(() -> checkoutService.convertToOrder(initiated.getSessionToken()))
                .isInstanceOf(RuntimeException.class);

        // Session remains PAID + orderId null after failed retry
        CheckoutSessionDO afterRetry = checkoutSessionMapper.selectById(payResult.getId());
        assertThat(afterRetry.getStatus()).isEqualTo(CheckoutStatusEnum.PAID.getCode());
        assertThat(afterRetry.getOrderId()).isNull();
    }
}
