package com.geihou.module.finance.cart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.api.cart.enums.CartEventTypeEnum;
import com.geihou.module.finance.api.cart.enums.CartItemStateEnum;
import com.geihou.module.finance.api.cart.enums.CartStatusEnum;
import com.geihou.module.finance.api.order.enums.OrderChannelEnum;
import com.geihou.module.finance.api.product.ProductApi;
import com.geihou.module.finance.api.product.dto.SkuRespDTO;
import com.geihou.module.finance.cart.controller.app.customer.vo.CartAddItemReqVO;
import com.geihou.module.finance.cart.controller.app.customer.vo.CartVO;
import com.geihou.module.finance.cart.convert.CartConvert;
import com.geihou.module.finance.cart.dal.dataobject.CartDO;
import com.geihou.module.finance.cart.dal.dataobject.CartEventLogDO;
import com.geihou.module.finance.cart.dal.dataobject.CartItemDO;
import com.geihou.module.finance.cart.dal.mapper.CartEventLogMapper;
import com.geihou.module.finance.cart.dal.mapper.CartItemMapper;
import com.geihou.module.finance.cart.dal.mapper.CartMapper;
import com.geihou.module.finance.cart.framework.CartBusinessException;
import com.geihou.module.finance.cart.framework.CartErrorCodeConstants;
import com.geihou.module.finance.order.service.BusinessDateCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Cart service implementation for customer cart operations.
 *
 * <p>Tenant isolation via TenantContextHolder + MyBatis-Plus TenantLineInnerInterceptor.
 * All money fields use BigDecimal (never double/float).
 * All mutations are @Transactional with triple-write (cart + cart_item + cart_event_log)
 * per Cart root-cause 3 (fire-and-forget defense).
 *
 * <p>CG-5 degradation: No stock check, only ProductApi.getSku() for SKU existence/status.
 * item_state defaults to NORMAL. Not auto-set to SOLD_OUT (StockApi not implemented).
 */
@Service
public class CartServiceImpl implements CartService {

    private static final Logger log = LoggerFactory.getLogger(CartServiceImpl.class);

    private static final int ABANDONMENT_THRESHOLD_HOURS = 24;
    private static final Long SYSTEM_OPERATOR_USER_ID = 0L;
    private static final String SYSTEM_OPERATOR_ROLE = "SYSTEM";

    private final CartMapper cartMapper;
    private final CartItemMapper cartItemMapper;
    private final CartEventLogMapper cartEventLogMapper;
    private final ProductApi productApi;
    private final BusinessDateCalculator businessDateCalculator;
    private final CartService self;

    public CartServiceImpl(CartMapper cartMapper,
                           CartItemMapper cartItemMapper,
                           CartEventLogMapper cartEventLogMapper,
                           ProductApi productApi,
                           BusinessDateCalculator businessDateCalculator,
                           @Lazy CartService self) {
        this.cartMapper = cartMapper;
        this.cartItemMapper = cartItemMapper;
        this.cartEventLogMapper = cartEventLogMapper;
        this.productApi = productApi;
        this.businessDateCalculator = businessDateCalculator;
        this.self = self;
    }

    @Override
    public CartVO getCurrentCart(Long customerUserId, Long shopId, String channel) {
        CartDO cart = getOrCreateActiveCart(customerUserId, shopId, channel);
        List<CartItemDO> items = getCartItems(cart.getId());
        return CartConvert.convert(cart, items);
    }

    @Override
    @Transactional
    public CartVO addItem(Long customerUserId, Long shopId, String channel, CartAddItemReqVO reqVO) {
        // Step 1: Validate SKU existence and status via ProductApi (CG-5 degradation)
        SkuRespDTO sku = productApi.getSku(reqVO.getSkuId());
        if (sku == null) {
            throw new CartBusinessException(CartErrorCodeConstants.SKU_NOT_FOUND,
                    "skuId=" + reqVO.getSkuId());
        }
        // Check SKU status — only ACTIVE SKUs are sellable
        if (!isSkuAvailable(sku)) {
            throw new CartBusinessException(CartErrorCodeConstants.SKU_NOT_AVAILABLE,
                    "skuId=" + reqVO.getSkuId() + ", status=" + sku.getStatus());
        }

        // Step 2: @Transactional triple-write
        CartDO cart = getOrCreateActiveCart(customerUserId, shopId, channel);
        BigDecimal amountBefore = cart.getTotalAmount();

        // 2.1: Check if same SKU already in cart — if so, merge quantity
        CartItemDO existingItem = findCartItemBySku(cart.getId(), reqVO.getSkuId());

        Integer quantityBefore = null;
        if (existingItem != null) {
            quantityBefore = existingItem.getQuantity();
            // Update existing item quantity
            existingItem.setQuantity(existingItem.getQuantity() + reqVO.getQuantity());
            recalculateItem(existingItem, reqVO.getOptionsExtraPrice());
            cartItemMapper.updateById(existingItem);
        } else {
            // 2.2: Create new cart item with snapshot
            existingItem = createCartItemFromSku(cart, sku, reqVO);
            cartItemMapper.insert(existingItem);
        }

        // 2.3: Recalculate cart totals
        recalculateCart(cart);
        cart.setLastActivityTime(LocalDateTime.now());
        cartMapper.updateById(cart);

        // 2.4: Write event log (ITEM_ADDED) — root-cause 3: await, not fire-and-forget
        CartEventLogDO eventLog = new CartEventLogDO();
        eventLog.setTenantId(cart.getTenantId());
        eventLog.setCartId(cart.getId());
        eventLog.setEventType(CartEventTypeEnum.ITEM_ADDED.getCode());
        eventLog.setEventTime(LocalDateTime.now());
        eventLog.setOperatorUserId(customerUserId);
        eventLog.setOperatorRole("CUSTOMER");
        eventLog.setSkuId(reqVO.getSkuId());
        eventLog.setQuantityBefore(quantityBefore);
        eventLog.setQuantityAfter(existingItem.getQuantity());
        eventLog.setAmountBefore(amountBefore);
        eventLog.setAmountAfter(cart.getTotalAmount());
        eventLog.setCreateTime(LocalDateTime.now());
        cartEventLogMapper.insert(eventLog); // await — if this fails, transaction rolls back

        // Return updated cart
        List<CartItemDO> items = getCartItems(cart.getId());
        return CartConvert.convert(cart, items);
    }

    @Override
    @Transactional
    public CartVO updateQuantity(Long customerUserId, Long shopId, Long itemId, Integer quantity) {
        CartItemDO item = cartItemMapper.selectById(itemId);
        if (item == null || Boolean.TRUE.equals(item.getDeleted())) {
            throw new CartBusinessException(CartErrorCodeConstants.CART_ITEM_NOT_FOUND,
                    "itemId=" + itemId);
        }

        CartDO cart = cartMapper.selectById(item.getCartId());
        if (cart == null
                || !Objects.equals(cart.getCustomerUserId(), customerUserId)
                || !Objects.equals(cart.getShopId(), shopId)) {
            throw new CartBusinessException(CartErrorCodeConstants.CART_ITEM_NOT_FOUND,
                    "itemId=" + itemId);
        }

        BigDecimal amountBefore = cart.getTotalAmount();
        Integer quantityBefore = item.getQuantity();

        // Update item quantity and recalculate
        item.setQuantity(quantity);
        recalculateItem(item, item.getOptionsExtraPrice());
        cartItemMapper.updateById(item);

        // Recalculate cart
        recalculateCart(cart);
        cart.setLastActivityTime(LocalDateTime.now());
        cartMapper.updateById(cart);

        // Write event log (ITEM_QUANTITY_CHANGED)
        CartEventLogDO eventLog = new CartEventLogDO();
        eventLog.setTenantId(cart.getTenantId());
        eventLog.setCartId(cart.getId());
        eventLog.setEventType(CartEventTypeEnum.ITEM_QUANTITY_CHANGED.getCode());
        eventLog.setEventTime(LocalDateTime.now());
        eventLog.setOperatorUserId(customerUserId);
        eventLog.setOperatorRole("CUSTOMER");
        eventLog.setSkuId(item.getSkuId());
        eventLog.setQuantityBefore(quantityBefore);
        eventLog.setQuantityAfter(quantity);
        eventLog.setAmountBefore(amountBefore);
        eventLog.setAmountAfter(cart.getTotalAmount());
        eventLog.setCreateTime(LocalDateTime.now());
        cartEventLogMapper.insert(eventLog);

        List<CartItemDO> items = getCartItems(cart.getId());
        return CartConvert.convert(cart, items);
    }

    @Override
    @Transactional
    public CartVO removeItem(Long customerUserId, Long shopId, Long itemId) {
        CartItemDO item = cartItemMapper.selectById(itemId);
        if (item == null || Boolean.TRUE.equals(item.getDeleted())) {
            throw new CartBusinessException(CartErrorCodeConstants.CART_ITEM_NOT_FOUND,
                    "itemId=" + itemId);
        }

        CartDO cart = cartMapper.selectById(item.getCartId());
        if (cart == null
                || !Objects.equals(cart.getCustomerUserId(), customerUserId)
                || !Objects.equals(cart.getShopId(), shopId)) {
            throw new CartBusinessException(CartErrorCodeConstants.CART_ITEM_NOT_FOUND,
                    "itemId=" + itemId);
        }

        BigDecimal amountBefore = cart.getTotalAmount();
        Integer quantityBefore = item.getQuantity();

        // Soft delete the item
        cartItemMapper.deleteById(itemId);

        // Recalculate cart
        recalculateCart(cart);
        cart.setLastActivityTime(LocalDateTime.now());
        cartMapper.updateById(cart);

        // Write event log (ITEM_REMOVED)
        CartEventLogDO eventLog = new CartEventLogDO();
        eventLog.setTenantId(cart.getTenantId());
        eventLog.setCartId(cart.getId());
        eventLog.setEventType(CartEventTypeEnum.ITEM_REMOVED.getCode());
        eventLog.setEventTime(LocalDateTime.now());
        eventLog.setOperatorUserId(customerUserId);
        eventLog.setOperatorRole("CUSTOMER");
        eventLog.setSkuId(item.getSkuId());
        eventLog.setQuantityBefore(quantityBefore);
        eventLog.setQuantityAfter(0);
        eventLog.setAmountBefore(amountBefore);
        eventLog.setAmountAfter(cart.getTotalAmount());
        eventLog.setCreateTime(LocalDateTime.now());
        cartEventLogMapper.insert(eventLog);

        List<CartItemDO> items = getCartItems(cart.getId());
        return CartConvert.convert(cart, items);
    }

    @Override
    @Transactional
    public CartVO clearCart(Long customerUserId, Long shopId) {
        CartDO cart = findActiveCart(customerUserId, shopId);
        if (cart == null) {
            // No active cart — return empty
            CartVO emptyVo = new CartVO();
            emptyVo.setCustomerUserId(customerUserId);
            emptyVo.setStatus(CartStatusEnum.ACTIVE.getCode());
            emptyVo.setItemCount(0);
            emptyVo.setTotalQuantity(0);
            emptyVo.setSubtotalAmount(BigDecimal.ZERO);
            emptyVo.setDiscountAmount(BigDecimal.ZERO);
            emptyVo.setTotalAmount(BigDecimal.ZERO);
            emptyVo.setVersion(0);
            return emptyVo;
        }

        BigDecimal amountBefore = cart.getTotalAmount();
        List<CartItemDO> items = getCartItems(cart.getId());

        // Soft delete all items
        for (CartItemDO item : items) {
            cartItemMapper.deleteById(item.getId());
        }

        // Reset cart totals
        cart.setItemCount(0);
        cart.setTotalQuantity(0);
        cart.setSubtotalAmount(BigDecimal.ZERO);
        cart.setDiscountAmount(BigDecimal.ZERO);
        cart.setTotalAmount(BigDecimal.ZERO);
        cart.setLastActivityTime(LocalDateTime.now());
        cartMapper.updateById(cart);

        // Write event log (CART_CLEARED)
        CartEventLogDO eventLog = new CartEventLogDO();
        eventLog.setTenantId(cart.getTenantId());
        eventLog.setCartId(cart.getId());
        eventLog.setEventType(CartEventTypeEnum.CART_CLEARED.getCode());
        eventLog.setEventTime(LocalDateTime.now());
        eventLog.setOperatorUserId(customerUserId);
        eventLog.setOperatorRole("CUSTOMER");
        eventLog.setQuantityBefore(items.stream().mapToInt(CartItemDO::getQuantity).sum());
        eventLog.setQuantityAfter(0);
        eventLog.setAmountBefore(amountBefore);
        eventLog.setAmountAfter(BigDecimal.ZERO);
        eventLog.setCreateTime(LocalDateTime.now());
        cartEventLogMapper.insert(eventLog);

        List<CartItemDO> remainingItems = getCartItems(cart.getId());
        return CartConvert.convert(cart, remainingItems);
    }

    // --- G1-04E: Scheduled cart abandonment ---

    @Override
    public void abandonOldCarts() {
        Long savedTenantId = TenantContextHolder.getTenantId();
        boolean savedIgnore = TenantContextHolder.isIgnore();
        try {
            // Bypass tenant SQL filter to scan all tenants
            TenantContextHolder.clear();
            TenantContextHolder.setIgnore(true);

            LocalDateTime cutoff = LocalDateTime.now().minusHours(ABANDONMENT_THRESHOLD_HOURS);
            List<CartDO> oldCarts = cartMapper.selectList(
                    new LambdaQueryWrapper<CartDO>()
                            .eq(CartDO::getStatus, CartStatusEnum.ACTIVE.getCode())
                            .lt(CartDO::getLastActivityTime, cutoff)
                            .eq(CartDO::getDeleted, false));

            for (CartDO cart : oldCarts) {
                try {
                    TenantContextHolder.setIgnore(false);
                    TenantContextHolder.setTenantId(cart.getTenantId());
                    self.abandonCart(cart.getId());
                    log.info("Abandoned old cart {} (tenant {}, lastActivity {})",
                            cart.getId(), cart.getTenantId(), cart.getLastActivityTime());
                } catch (Exception e) {
                    log.warn("Failed to abandon cart {}: {}", cart.getId(), e.getMessage());
                }
            }
        } finally {
            // Restore tenant context
            TenantContextHolder.setIgnore(savedIgnore);
            if (savedTenantId != null) {
                TenantContextHolder.setTenantId(savedTenantId);
            } else {
                TenantContextHolder.clear();
            }
        }
    }

    @Override
    @Transactional
    public void abandonCart(Long cartId) {
        CartDO cart = cartMapper.selectById(cartId);
        if (cart == null) {
            return; // Not found or deleted
        }
        // Validate: only ACTIVE carts can be abandoned
        if (!CartStatusEnum.ACTIVE.getCode().equals(cart.getStatus())) {
            return; // Already CHECKOUT, CONVERTED, or ABANDONED
        }
        // Validate: cart must be old enough
        LocalDateTime cutoff = LocalDateTime.now().minusHours(ABANDONMENT_THRESHOLD_HOURS);
        if (cart.getLastActivityTime() == null
                || !cart.getLastActivityTime().isBefore(cutoff)) {
            return; // Not old enough (may have been recently updated)
        }

        BigDecimal amountBefore = cart.getTotalAmount();
        LocalDateTime now = LocalDateTime.now();

        // Transition cart: ACTIVE → ABANDONED
        cart.setStatus(CartStatusEnum.ABANDONED.getCode());
        cart.setUpdater(String.valueOf(SYSTEM_OPERATOR_USER_ID));
        cart.setUpdateTime(now);
        cartMapper.updateById(cart);

        // Write CART_EXPIRED event log with SYSTEM operator
        CartEventLogDO eventLog = new CartEventLogDO();
        eventLog.setTenantId(cart.getTenantId());
        eventLog.setCartId(cart.getId());
        eventLog.setEventType(CartEventTypeEnum.CART_EXPIRED.getCode());
        eventLog.setEventTime(now);
        eventLog.setOperatorUserId(SYSTEM_OPERATOR_USER_ID);
        eventLog.setOperatorRole(SYSTEM_OPERATOR_ROLE);
        eventLog.setAmountBefore(amountBefore);
        eventLog.setAmountAfter(amountBefore);
        eventLog.setCreateTime(now);
        cartEventLogMapper.insert(eventLog);

        // Cart items are NOT modified — they remain for historical reference (PRD §2.2)
    }

    // --- Private helpers ---

    private CartDO getOrCreateActiveCart(Long customerUserId, Long shopId, String channel) {
        CartDO cart = findActiveCart(customerUserId, shopId);
        if (cart != null) {
            return cart;
        }

        // Validate channel
        try {
            OrderChannelEnum.fromCode(channel);
        } catch (IllegalArgumentException e) {
            throw new CartBusinessException(CartErrorCodeConstants.CHANNEL_INVALID,
                    "channel=" + channel);
        }

        Long tenantId = TenantContextHolder.getTenantId();
        LocalDateTime now = LocalDateTime.now();

        cart = new CartDO();
        cart.setTenantId(tenantId);
        cart.setCustomerUserId(customerUserId);
        cart.setShopId(shopId);
        cart.setChannel(channel);
        cart.setStatus(CartStatusEnum.ACTIVE.getCode());
        cart.setItemCount(0);
        cart.setTotalQuantity(0);
        cart.setSubtotalAmount(BigDecimal.ZERO);
        cart.setDiscountAmount(BigDecimal.ZERO);
        cart.setTotalAmount(BigDecimal.ZERO);
        cart.setBusinessDate(businessDateCalculator.computeBusinessDate(now));
        cart.setIsStaffAssisted(false);
        cart.setVersion(0);
        cart.setLastActivityTime(now);
        cart.setCreator(String.valueOf(customerUserId));
        cart.setCreateTime(now);
        cart.setUpdater(String.valueOf(customerUserId));
        cart.setUpdateTime(now);
        cart.setDeleted(false);
        cartMapper.insert(cart);
        return cart;
    }

    private CartDO findActiveCart(Long customerUserId, Long shopId) {
        Long tenantId = TenantContextHolder.getTenantId();
        return cartMapper.selectOne(new LambdaQueryWrapper<CartDO>()
                .eq(CartDO::getTenantId, tenantId)
                .eq(CartDO::getCustomerUserId, customerUserId)
                .eq(CartDO::getShopId, shopId)
                .eq(CartDO::getStatus, CartStatusEnum.ACTIVE.getCode())
                .eq(CartDO::getDeleted, false)
                .last("LIMIT 1"));
    }

    private List<CartItemDO> getCartItems(Long cartId) {
        return cartItemMapper.selectList(new LambdaQueryWrapper<CartItemDO>()
                .eq(CartItemDO::getCartId, cartId)
                .eq(CartItemDO::getDeleted, false));
    }

    private CartItemDO findCartItemBySku(Long cartId, Long skuId) {
        return cartItemMapper.selectOne(new LambdaQueryWrapper<CartItemDO>()
                .eq(CartItemDO::getCartId, cartId)
                .eq(CartItemDO::getSkuId, skuId)
                .eq(CartItemDO::getDeleted, false)
                .last("LIMIT 1"));
    }

    private CartItemDO createCartItemFromSku(CartDO cart, SkuRespDTO sku, CartAddItemReqVO reqVO) {
        CartItemDO item = new CartItemDO();
        item.setTenantId(cart.getTenantId());
        item.setCartId(cart.getId());
        item.setSkuId(sku.getId()); // Long — root-cause 2 defense
        item.setSpuId(sku.getSpuId());
        item.setSkuNameSnapshot(sku.getSkuName());
        item.setSkuImageSnapshot(sku.getPrimaryImageUrl());
        item.setUnitPriceSnapshot(sku.getSellingPrice());
        item.setQuantity(reqVO.getQuantity());
        item.setOptions(reqVO.getOptions());
        BigDecimal extraPrice = reqVO.getOptionsExtraPrice() != null
                ? reqVO.getOptionsExtraPrice() : BigDecimal.ZERO;
        item.setOptionsExtraPrice(extraPrice);
        item.setItemDiscount(BigDecimal.ZERO);
        item.setItemState(CartItemStateEnum.NORMAL.getCode()); // CG-5: default NORMAL
        item.setCreator(String.valueOf(cart.getCustomerUserId()));
        item.setCreateTime(LocalDateTime.now());
        item.setUpdater(String.valueOf(cart.getCustomerUserId()));
        item.setUpdateTime(LocalDateTime.now());
        item.setDeleted(false);
        recalculateItem(item, extraPrice);
        return item;
    }

    private void recalculateItem(CartItemDO item, BigDecimal optionsExtraPrice) {
        BigDecimal unitPrice = item.getUnitPriceSnapshot();
        BigDecimal extra = optionsExtraPrice != null ? optionsExtraPrice : item.getOptionsExtraPrice();
        if (extra == null) {
            extra = BigDecimal.ZERO;
        }
        BigDecimal subtotal = unitPrice.add(extra).multiply(BigDecimal.valueOf(item.getQuantity()));
        item.setItemSubtotal(subtotal);
        item.setItemTotal(subtotal.subtract(item.getItemDiscount() != null ? item.getItemDiscount() : BigDecimal.ZERO));
        if (item.getOptionsExtraPrice() == null) {
            item.setOptionsExtraPrice(extra);
        }
    }

    private void recalculateCart(CartDO cart) {
        List<CartItemDO> items = getCartItems(cart.getId());
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        int totalQuantity = 0;

        for (CartItemDO item : items) {
            subtotal = subtotal.add(item.getItemSubtotal());
            discount = discount.add(item.getItemDiscount() != null ? item.getItemDiscount() : BigDecimal.ZERO);
            totalQuantity += item.getQuantity();
        }

        cart.setItemCount(items.size());
        cart.setTotalQuantity(totalQuantity);
        cart.setSubtotalAmount(subtotal);
        cart.setDiscountAmount(discount);
        cart.setTotalAmount(subtotal.subtract(discount));
    }

    /**
     * CG-5 degradation: Check SKU availability via ProductApi status only.
     * No StockApi check. SKU is available if status is ACTIVE.
     */
    private boolean isSkuAvailable(SkuRespDTO sku) {
        return "ACTIVE".equalsIgnoreCase(sku.getStatus());
    }

    // --- G1-04F: Staff-assisted cart operations ---

    private static final String STAFF_OPERATOR_ROLE = "STAFF";

    @Override
    public CartVO staffGetCurrentCart(Long staffUserId, Long customerUserId, Long shopId, String channel) {
        // Readonly lookup: never create a cart, never mark staff-assisted, never write event log
        CartDO cart = findActiveCart(customerUserId, shopId);
        if (cart == null) {
            CartVO emptyVo = new CartVO();
            emptyVo.setCustomerUserId(customerUserId);
            emptyVo.setShopId(shopId);
            emptyVo.setStatus(CartStatusEnum.ACTIVE.getCode());
            emptyVo.setItemCount(0);
            emptyVo.setTotalQuantity(0);
            emptyVo.setSubtotalAmount(BigDecimal.ZERO);
            emptyVo.setDiscountAmount(BigDecimal.ZERO);
            emptyVo.setTotalAmount(BigDecimal.ZERO);
            emptyVo.setVersion(0);
            emptyVo.setIsStaffAssisted(false);
            emptyVo.setAssistedByUserId(null);
            return emptyVo;
        }
        List<CartItemDO> items = getCartItems(cart.getId());
        return CartConvert.convert(cart, items);
    }

    @Override
    @Transactional
    public CartVO staffAddItem(Long staffUserId, Long customerUserId, Long shopId, String channel, CartAddItemReqVO reqVO) {
        SkuRespDTO sku = productApi.getSku(reqVO.getSkuId());
        if (sku == null) {
            throw new CartBusinessException(CartErrorCodeConstants.SKU_NOT_FOUND,
                    "skuId=" + reqVO.getSkuId());
        }
        if (!isSkuAvailable(sku)) {
            throw new CartBusinessException(CartErrorCodeConstants.SKU_NOT_AVAILABLE,
                    "skuId=" + reqVO.getSkuId() + ", status=" + sku.getStatus());
        }

        CartDO cart = getOrCreateActiveStaffCart(staffUserId, customerUserId, shopId, channel);
        BigDecimal amountBefore = cart.getTotalAmount();

        CartItemDO existingItem = findCartItemBySku(cart.getId(), reqVO.getSkuId());
        Integer quantityBefore = null;
        if (existingItem != null) {
            quantityBefore = existingItem.getQuantity();
            existingItem.setQuantity(existingItem.getQuantity() + reqVO.getQuantity());
            recalculateItem(existingItem, reqVO.getOptionsExtraPrice());
            cartItemMapper.updateById(existingItem);
        } else {
            existingItem = createCartItemFromSku(cart, sku, reqVO);
            cartItemMapper.insert(existingItem);
        }

        recalculateCart(cart);
        cart.setLastActivityTime(LocalDateTime.now());
        cartMapper.updateById(cart);

        writeEventLog(cart, CartEventTypeEnum.ITEM_ADDED.getCode(),
                staffUserId, STAFF_OPERATOR_ROLE,
                reqVO.getSkuId(), quantityBefore, existingItem.getQuantity(),
                amountBefore, cart.getTotalAmount());

        List<CartItemDO> items = getCartItems(cart.getId());
        return CartConvert.convert(cart, items);
    }

    @Override
    @Transactional
    public CartVO staffUpdateQuantity(Long staffUserId, Long customerUserId, Long shopId, Long itemId, Integer quantity) {
        CartItemDO item = cartItemMapper.selectById(itemId);
        if (item == null || Boolean.TRUE.equals(item.getDeleted())) {
            throw new CartBusinessException(CartErrorCodeConstants.CART_ITEM_NOT_FOUND,
                    "itemId=" + itemId);
        }

        CartDO cart = cartMapper.selectById(item.getCartId());
        if (cart == null
                || !Objects.equals(cart.getCustomerUserId(), customerUserId)
                || !Objects.equals(cart.getShopId(), shopId)) {
            throw new CartBusinessException(CartErrorCodeConstants.CART_ITEM_NOT_FOUND,
                    "itemId=" + itemId);
        }

        markStaffAssisted(cart, staffUserId);

        BigDecimal amountBefore = cart.getTotalAmount();
        Integer quantityBefore = item.getQuantity();

        item.setQuantity(quantity);
        recalculateItem(item, item.getOptionsExtraPrice());
        cartItemMapper.updateById(item);

        recalculateCart(cart);
        cart.setLastActivityTime(LocalDateTime.now());
        cartMapper.updateById(cart);

        writeEventLog(cart, CartEventTypeEnum.ITEM_QUANTITY_CHANGED.getCode(),
                staffUserId, STAFF_OPERATOR_ROLE,
                item.getSkuId(), quantityBefore, quantity,
                amountBefore, cart.getTotalAmount());

        List<CartItemDO> items = getCartItems(cart.getId());
        return CartConvert.convert(cart, items);
    }

    @Override
    @Transactional
    public CartVO staffRemoveItem(Long staffUserId, Long customerUserId, Long shopId, Long itemId) {
        CartItemDO item = cartItemMapper.selectById(itemId);
        if (item == null || Boolean.TRUE.equals(item.getDeleted())) {
            throw new CartBusinessException(CartErrorCodeConstants.CART_ITEM_NOT_FOUND,
                    "itemId=" + itemId);
        }

        CartDO cart = cartMapper.selectById(item.getCartId());
        if (cart == null
                || !Objects.equals(cart.getCustomerUserId(), customerUserId)
                || !Objects.equals(cart.getShopId(), shopId)) {
            throw new CartBusinessException(CartErrorCodeConstants.CART_ITEM_NOT_FOUND,
                    "itemId=" + itemId);
        }

        markStaffAssisted(cart, staffUserId);

        BigDecimal amountBefore = cart.getTotalAmount();
        Integer quantityBefore = item.getQuantity();

        cartItemMapper.deleteById(itemId);

        recalculateCart(cart);
        cart.setLastActivityTime(LocalDateTime.now());
        cartMapper.updateById(cart);

        writeEventLog(cart, CartEventTypeEnum.ITEM_REMOVED.getCode(),
                staffUserId, STAFF_OPERATOR_ROLE,
                item.getSkuId(), quantityBefore, 0,
                amountBefore, cart.getTotalAmount());

        List<CartItemDO> items = getCartItems(cart.getId());
        return CartConvert.convert(cart, items);
    }

    @Override
    @Transactional
    public CartVO staffClearCart(Long staffUserId, Long customerUserId, Long shopId) {
        CartDO cart = findActiveCart(customerUserId, shopId);
        if (cart == null) {
            CartVO emptyVo = new CartVO();
            emptyVo.setCustomerUserId(customerUserId);
            emptyVo.setStatus(CartStatusEnum.ACTIVE.getCode());
            emptyVo.setItemCount(0);
            emptyVo.setTotalQuantity(0);
            emptyVo.setSubtotalAmount(BigDecimal.ZERO);
            emptyVo.setDiscountAmount(BigDecimal.ZERO);
            emptyVo.setTotalAmount(BigDecimal.ZERO);
            emptyVo.setVersion(0);
            emptyVo.setIsStaffAssisted(true);
            emptyVo.setAssistedByUserId(staffUserId);
            return emptyVo;
        }

        markStaffAssisted(cart, staffUserId);

        BigDecimal amountBefore = cart.getTotalAmount();
        List<CartItemDO> items = getCartItems(cart.getId());

        for (CartItemDO item : items) {
            cartItemMapper.deleteById(item.getId());
        }

        cart.setItemCount(0);
        cart.setTotalQuantity(0);
        cart.setSubtotalAmount(BigDecimal.ZERO);
        cart.setDiscountAmount(BigDecimal.ZERO);
        cart.setTotalAmount(BigDecimal.ZERO);
        cart.setLastActivityTime(LocalDateTime.now());
        cartMapper.updateById(cart);

        writeEventLog(cart, CartEventTypeEnum.CART_CLEARED.getCode(),
                staffUserId, STAFF_OPERATOR_ROLE,
                null, items.stream().mapToInt(CartItemDO::getQuantity).sum(), 0,
                amountBefore, BigDecimal.ZERO);

        List<CartItemDO> remainingItems = getCartItems(cart.getId());
        return CartConvert.convert(cart, remainingItems);
    }

    // --- Staff private helpers ---

    /**
     * Get or create an active cart for a customer, marked as staff-assisted.
     * If creating a new cart, sets isStaffAssisted = true and assistedByUserId = staffUserId.
     * If reusing an existing cart, marks it as staff-assisted.
     */
    private CartDO getOrCreateActiveStaffCart(Long staffUserId, Long customerUserId, Long shopId, String channel) {
        CartDO cart = findActiveCart(customerUserId, shopId);
        if (cart != null) {
            markStaffAssisted(cart, staffUserId);
            return cart;
        }

        try {
            OrderChannelEnum.fromCode(channel);
        } catch (IllegalArgumentException e) {
            throw new CartBusinessException(CartErrorCodeConstants.CHANNEL_INVALID,
                    "channel=" + channel);
        }

        Long tenantId = TenantContextHolder.getTenantId();
        LocalDateTime now = LocalDateTime.now();

        cart = new CartDO();
        cart.setTenantId(tenantId);
        cart.setCustomerUserId(customerUserId);
        cart.setShopId(shopId);
        cart.setChannel(channel);
        cart.setStatus(CartStatusEnum.ACTIVE.getCode());
        cart.setItemCount(0);
        cart.setTotalQuantity(0);
        cart.setSubtotalAmount(BigDecimal.ZERO);
        cart.setDiscountAmount(BigDecimal.ZERO);
        cart.setTotalAmount(BigDecimal.ZERO);
        cart.setBusinessDate(businessDateCalculator.computeBusinessDate(now));
        cart.setIsStaffAssisted(true);
        cart.setAssistedByUserId(staffUserId);
        cart.setVersion(0);
        cart.setLastActivityTime(now);
        cart.setCreator(String.valueOf(staffUserId));
        cart.setCreateTime(now);
        cart.setUpdater(String.valueOf(staffUserId));
        cart.setUpdateTime(now);
        cart.setDeleted(false);
        cartMapper.insert(cart);
        return cart;
    }

    /**
     * Mark an existing cart as staff-assisted if not already.
     */
    private void markStaffAssisted(CartDO cart, Long staffUserId) {
        boolean changed = false;
        if (!Boolean.TRUE.equals(cart.getIsStaffAssisted())) {
            cart.setIsStaffAssisted(true);
            changed = true;
        }
        if (!Objects.equals(cart.getAssistedByUserId(), staffUserId)) {
            cart.setAssistedByUserId(staffUserId);
            changed = true;
        }
        if (changed) {
            cart.setUpdater(String.valueOf(staffUserId));
            cart.setUpdateTime(LocalDateTime.now());
            cartMapper.updateById(cart);
        }
    }

    /**
     * Shared event log writer for staff operations.
     */
    private void writeEventLog(CartDO cart, String eventType,
                               Long operatorUserId, String operatorRole,
                               Long skuId, Integer quantityBefore, Integer quantityAfter,
                               BigDecimal amountBefore, BigDecimal amountAfter) {
        CartEventLogDO eventLog = new CartEventLogDO();
        eventLog.setTenantId(cart.getTenantId());
        eventLog.setCartId(cart.getId());
        eventLog.setEventType(eventType);
        eventLog.setEventTime(LocalDateTime.now());
        eventLog.setOperatorUserId(operatorUserId);
        eventLog.setOperatorRole(operatorRole);
        eventLog.setSkuId(skuId);
        eventLog.setQuantityBefore(quantityBefore);
        eventLog.setQuantityAfter(quantityAfter);
        eventLog.setAmountBefore(amountBefore);
        eventLog.setAmountAfter(amountAfter);
        eventLog.setCreateTime(LocalDateTime.now());
        cartEventLogMapper.insert(eventLog);
    }
}
