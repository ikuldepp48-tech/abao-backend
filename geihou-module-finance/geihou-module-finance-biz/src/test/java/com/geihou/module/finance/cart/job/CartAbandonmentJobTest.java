package com.geihou.module.finance.cart.job;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.api.cart.enums.CartEventTypeEnum;
import com.geihou.module.finance.api.cart.enums.CartStatusEnum;
import com.geihou.module.finance.cart.CartTestConfig;
import com.geihou.module.finance.cart.CartTestSchemaInitializer;
import com.geihou.module.finance.cart.controller.app.customer.vo.CartAddItemReqVO;
import com.geihou.module.finance.cart.dal.dataobject.CartDO;
import com.geihou.module.finance.cart.dal.dataobject.CartEventLogDO;
import com.geihou.module.finance.cart.dal.dataobject.CartItemDO;
import com.geihou.module.finance.cart.dal.mapper.CartEventLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geihou.module.finance.cart.dal.mapper.CartItemMapper;
import com.geihou.module.finance.cart.dal.mapper.CartMapper;
import com.geihou.module.finance.cart.service.CartService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cart abandonment job test.
 *
 * <p>G1-04E: Tests that ACTIVE carts with last_activity_time older than 24 hours
 * are transitioned to ABANDONED by the scheduled job.
 *
 * <p>Tests call job methods directly (not via @Scheduled).
 */
@SpringBootTest(
        classes = CartTestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:cart_abandonment_job_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
class CartAbandonmentJobTest {

    @Autowired
    private CartAbandonmentJob abandonmentJob;
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

    // --- Helpers ---

    private CartDO createActiveCartWithItems(Long customerUserId) {
        CartAddItemReqVO req = new CartAddItemReqVO();
        req.setSkuId(1001L);
        req.setQuantity(2);
        cartService.addItem(customerUserId, 1L, "DINE_IN", req);
        return cartMapper.selectList().stream()
                .filter(c -> c.getCustomerUserId().equals(customerUserId))
                .findFirst().orElseThrow();
    }

    private void setLastActivityTimeToPast(Long cartId, int hoursAgo) {
        CartDO cart = cartMapper.selectById(cartId);
        cart.setLastActivityTime(LocalDateTime.now().minusHours(hoursAgo));
        cartMapper.updateById(cart);
    }

    private CartDO createCartWithStatus(Long customerUserId, String status) {
        CartDO cart = new CartDO();
        cart.setTenantId(TenantContextHolder.getTenantId());
        cart.setCustomerUserId(customerUserId);
        cart.setShopId(1L);
        cart.setChannel("DINE_IN");
        cart.setStatus(status);
        cart.setItemCount(0);
        cart.setTotalQuantity(0);
        cart.setSubtotalAmount(BigDecimal.ZERO);
        cart.setDiscountAmount(BigDecimal.ZERO);
        cart.setTotalAmount(BigDecimal.ZERO);
        cart.setBusinessDate(java.time.LocalDate.now());
        cart.setIsStaffAssisted(false);
        cart.setVersion(0);
        cart.setLastActivityTime(LocalDateTime.now().minusHours(25));
        cart.setCreator(String.valueOf(customerUserId));
        cart.setCreateTime(LocalDateTime.now().minusHours(25));
        cart.setUpdater(String.valueOf(customerUserId));
        cart.setUpdateTime(LocalDateTime.now().minusHours(25));
        cart.setDeleted(false);
        cartMapper.insert(cart);
        return cart;
    }

    // --- Tests ---

    @Test
    void abandonOldCart_transitionsToAbandoned() {
        CartDO cart = createActiveCartWithItems(4001L);
        setLastActivityTimeToPast(cart.getId(), 25);

        abandonmentJob.execute();

        CartDO updated = cartMapper.selectById(cart.getId());
        assertThat(updated.getStatus()).isEqualTo(CartStatusEnum.ABANDONED.getCode());
    }

    @Test
    void abandonOldCart_writesCartExpiredEventLog() {
        CartDO cart = createActiveCartWithItems(4002L);
        setLastActivityTimeToPast(cart.getId(), 25);

        abandonmentJob.execute();

        List<CartEventLogDO> logs = cartEventLogMapper.selectList();
        CartEventLogDO expiredLog = logs.stream()
                .filter(l -> CartEventTypeEnum.CART_EXPIRED.getCode().equals(l.getEventType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No CART_EXPIRED event log found"));
        assertThat(expiredLog.getOperatorUserId()).isEqualTo(0L);
        assertThat(expiredLog.getOperatorRole()).isEqualTo("SYSTEM");
        assertThat(expiredLog.getCartId()).isEqualTo(cart.getId());
    }

    @Test
    void doNotAbandonRecentCart() {
        CartDO cart = createActiveCartWithItems(4003L);
        // lastActivityTime is recent (just created)

        abandonmentJob.execute();

        CartDO updated = cartMapper.selectById(cart.getId());
        assertThat(updated.getStatus()).isEqualTo(CartStatusEnum.ACTIVE.getCode());
    }

    @Test
    void doNotAbandonCheckoutCart() {
        CartDO cart = createCartWithStatus(4004L, CartStatusEnum.CHECKOUT.getCode());
        // lastActivityTime is 25h ago, but status is CHECKOUT

        abandonmentJob.execute();

        CartDO updated = cartMapper.selectById(cart.getId());
        assertThat(updated.getStatus()).isEqualTo(CartStatusEnum.CHECKOUT.getCode());
    }

    @Test
    void doNotAbandonConvertedCart() {
        CartDO cart = createCartWithStatus(4005L, CartStatusEnum.CONVERTED.getCode());
        // lastActivityTime is 25h ago, but status is CONVERTED

        abandonmentJob.execute();

        CartDO updated = cartMapper.selectById(cart.getId());
        assertThat(updated.getStatus()).isEqualTo(CartStatusEnum.CONVERTED.getCode());
    }

    @Test
    void abandonedCart_itemsPreserved() {
        CartDO cart = createActiveCartWithItems(4006L);
        setLastActivityTimeToPast(cart.getId(), 25);

        // Verify cart has items before abandonment
        List<CartItemDO> itemsBefore = cartItemMapper.selectList(
                new LambdaQueryWrapper<CartItemDO>()
                        .eq(CartItemDO::getCartId, cart.getId())
                        .eq(CartItemDO::getDeleted, false));
        assertThat(itemsBefore).isNotEmpty();

        abandonmentJob.execute();

        // Cart items should still exist (not soft-deleted)
        List<CartItemDO> itemsAfter = cartItemMapper.selectList(
                new LambdaQueryWrapper<CartItemDO>()
                        .eq(CartItemDO::getCartId, cart.getId())
                        .eq(CartItemDO::getDeleted, false));
        assertThat(itemsAfter).hasSize(itemsBefore.size());
    }

    @Test
    void crossTenantIsolation_jobScansAllTenants() {
        // Create old cart in tenant 1
        TenantContextHolder.setTenantId(1L);
        CartDO cart1 = createActiveCartWithItems(4007L);
        setLastActivityTimeToPast(cart1.getId(), 25);

        // Create old cart in tenant 2
        TenantContextHolder.setTenantId(2L);
        CartDO cart2 = createActiveCartWithItems(4008L);
        setLastActivityTimeToPast(cart2.getId(), 25);

        // Run job without tenant context (simulates scheduled job)
        TenantContextHolder.clear();

        abandonmentJob.execute();

        // Both tenants' carts should be abandoned
        TenantContextHolder.setTenantId(1L);
        CartDO result1 = cartMapper.selectById(cart1.getId());
        assertThat(result1.getStatus()).isEqualTo(CartStatusEnum.ABANDONED.getCode());

        TenantContextHolder.setTenantId(2L);
        CartDO result2 = cartMapper.selectById(cart2.getId());
        assertThat(result2.getStatus()).isEqualTo(CartStatusEnum.ABANDONED.getCode());
    }
}
