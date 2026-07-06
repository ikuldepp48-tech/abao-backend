package com.geihou.module.finance.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.geihou.common.pojo.PageResult;
import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.api.order.enums.OrderChannelEnum;
import com.geihou.module.finance.api.order.enums.OrderStatusEnum;
import com.geihou.module.finance.api.product.ProductApi;
import com.geihou.module.finance.api.product.dto.SkuRespDTO;
import com.geihou.module.finance.api.product.dto.SpuRespDTO;
import com.geihou.module.finance.cart.dal.dataobject.CartDO;
import com.geihou.module.finance.cart.dal.dataobject.CartItemDO;
import com.geihou.module.finance.cart.dal.mapper.CartItemMapper;
import com.geihou.module.finance.cart.dal.mapper.CartMapper;
import com.geihou.module.finance.cart.framework.CartBusinessException;
import com.geihou.module.finance.cart.framework.CartErrorCodeConstants;
import com.geihou.module.finance.checkout.dal.dataobject.CheckoutSessionDO;
import com.geihou.module.finance.checkout.dal.mapper.CheckoutSessionMapper;
import com.geihou.module.finance.checkout.framework.CheckoutStatusEnum;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderCreateReqVO;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderCreateRespVO;
import com.geihou.module.finance.order.controller.app.customer.vo.OrderItemReqVO;
import com.geihou.module.finance.order.dal.dataobject.OrderDO;
import com.geihou.module.finance.order.dal.dataobject.OrderEventLogDO;
import com.geihou.module.finance.order.dal.dataobject.OrderItemDO;
import com.geihou.module.finance.order.dal.dataobject.OrderPaymentDO;
import com.geihou.module.finance.order.dal.dataobject.OrderRefundDO;
import com.geihou.module.finance.order.dal.dataobject.OrderTableSessionDO;
import com.geihou.module.finance.order.dal.mapper.OrderEventLogMapper;
import com.geihou.module.finance.order.dal.mapper.OrderIdempotentMapper;
import com.geihou.module.finance.order.dal.mapper.OrderItemMapper;
import com.geihou.module.finance.order.dal.mapper.OrderMapper;
import com.geihou.module.finance.order.enums.OrderEventTypeEnum;
import com.geihou.module.finance.order.enums.OrderItemTypeEnum;
import com.geihou.module.finance.order.enums.PaymentMethodEnum;
import com.geihou.module.finance.order.framework.OrderBusinessException;
import com.geihou.module.finance.order.framework.OrderErrorCodeConstants;
import com.geihou.module.finance.order.service.payment.OrderPaymentService;
import com.geihou.module.finance.order.service.refund.RefundService;
import com.geihou.module.finance.order.service.statemachine.OrderStateMachineService;
import com.geihou.module.finance.order.service.tablesession.TableSessionService;
import com.geihou.module.finance.stock.StockIntegrationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Order service implementation for customer order creation, query, and list.
 *
 * <p>Tenant isolation via TenantContextHolder + MyBatis-Plus TenantLineInnerInterceptor.
 * All money fields use BigDecimal (never double/float).
 * Order creation flow:
 * 1. Idempotent check
 * 2. Validate request (channel, items)
 * 3. Fetch SKU snapshots via ProductApi.batchGetSkus
 * 4. Fetch SPU data via ProductApi.getSpu for missing fields
 * 5. Compute total_amount and business_date
 * 6. Insert order + order_items + event_log in single transaction
 * 7. Mark idempotent SUCCESS
 */
@Service
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderEventLogMapper eventLogMapper;
    private final OrderIdempotentMapper idempotentMapper;
    private final IdempotentService idempotentService;
    private final BusinessDateCalculator businessDateCalculator;
    private final ProductApi productApi;
    private final OrderStateMachineService stateMachineService;
    private final OrderPaymentService paymentService;
    private final RefundService refundService;
    private final TableSessionService tableSessionService;
    private final CheckoutSessionMapper checkoutSessionMapper;
    private final CartMapper cartMapper;
    private final CartItemMapper cartItemMapper;
    private final StockIntegrationService stockIntegrationService;

    public OrderServiceImpl(OrderMapper orderMapper,
                            OrderItemMapper orderItemMapper,
                            OrderEventLogMapper eventLogMapper,
                            OrderIdempotentMapper idempotentMapper,
                            IdempotentService idempotentService,
                            BusinessDateCalculator businessDateCalculator,
                            ProductApi productApi,
                            OrderStateMachineService stateMachineService,
                            OrderPaymentService paymentService,
                            RefundService refundService,
                            TableSessionService tableSessionService,
                            CheckoutSessionMapper checkoutSessionMapper,
                            CartMapper cartMapper,
                            CartItemMapper cartItemMapper,
                            StockIntegrationService stockIntegrationService) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.eventLogMapper = eventLogMapper;
        this.idempotentMapper = idempotentMapper;
        this.idempotentService = idempotentService;
        this.businessDateCalculator = businessDateCalculator;
        this.productApi = productApi;
        this.stateMachineService = stateMachineService;
        this.paymentService = paymentService;
        this.refundService = refundService;
        this.tableSessionService = tableSessionService;
        this.checkoutSessionMapper = checkoutSessionMapper;
        this.cartMapper = cartMapper;
        this.cartItemMapper = cartItemMapper;
        this.stockIntegrationService = stockIntegrationService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderCreateRespVO createOrder(OrderCreateReqVO reqVO, String idempotentKey) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        // Validate idempotent key
        if (idempotentKey == null || idempotentKey.isBlank()) {
            throw new OrderBusinessException(OrderErrorCodeConstants.IDEMPOTENT_KEY_REQUIRED);
        }

        // 1. Idempotent check
        Long existingOrderId = idempotentService.tryAcquire(idempotentKey);
        if (existingOrderId != null) {
            // Return existing order with items
            OrderDO existing = orderMapper.selectById(existingOrderId);
            if (existing != null) {
                List<OrderItemDO> existingItems = orderItemMapper.selectList(
                        OrderItemDO::getOrderId, existingOrderId,
                        OrderItemDO::getTenantId, tenantId
                );
                return buildRespVO(existing, existingItems);
            }
        }

        try {
            // 2. Validate request
            validateCreateRequest(reqVO);

            // 2a. DINE_IN table session validation (CG-TS3)
            OrderTableSessionDO dineInSession = null;
            if ("DINE_IN".equals(reqVO.getChannel())) {
                dineInSession = tableSessionService.validateForDineIn(reqVO.getTableSessionId());
            } else {
                tableSessionService.validateNonDineIn(reqVO.getTableSessionId());
            }

            // 3. Fetch SKU snapshots via ProductApi.batchGetSkus
            List<OrderItemReqVO> itemReqs = reqVO.getItems();
            Set<Long> skuIds = itemReqs.stream()
                    .map(OrderItemReqVO::getSkuId)
                    .collect(Collectors.toSet());
            Map<Long, SkuRespDTO> skuMap = productApi.batchGetSkus(new ArrayList<>(skuIds));

            // 4. Build order items with frozen snapshots
            List<OrderItemDO> orderItems = new ArrayList<>();
            BigDecimal totalAmount = BigDecimal.ZERO;
            LocalDateTime now = LocalDateTime.now();

            for (OrderItemReqVO itemReq : itemReqs) {
                SkuRespDTO sku = skuMap.get(itemReq.getSkuId());
                if (sku == null) {
                    throw new OrderBusinessException(OrderErrorCodeConstants.SKU_NOT_FOUND,
                            "skuId=" + itemReq.getSkuId());
                }

                // Validate SKU is sellable (status ACTIVE)
                if (!"ACTIVE".equals(sku.getStatus())) {
                    throw new OrderBusinessException(OrderErrorCodeConstants.SKU_NOT_SELLABLE,
                            "skuId=" + itemReq.getSkuId() + " status=" + sku.getStatus());
                }

                // Fetch SPU for snapshot fields not in SkuRespDTO
                SpuRespDTO spu = productApi.getSpu(sku.getSpuId());
                if (spu == null) {
                    throw new OrderBusinessException(OrderErrorCodeConstants.SPU_NOT_FOUND,
                            "spuId=" + sku.getSpuId());
                }

                // Build immutable snapshot
                BigDecimal unitPrice = sku.getSellingPrice();
                BigDecimal quantity = itemReq.getQuantity();
                BigDecimal itemTotal = unitPrice.multiply(quantity);

                OrderItemDO item = new OrderItemDO();
                item.setTenantId(tenantId);
                item.setSkuId(sku.getId());
                item.setSkuCode(sku.getSkuCode());
                item.setSkuName(sku.getSkuName());
                item.setSpuId(sku.getSpuId());
                item.setSpuName(spu.getSpuName());
                item.setCategoryId(spu.getCategoryId());
                item.setUnitPrice(unitPrice);
                item.setQuantity(quantity);
                item.setUnit("份");
                item.setItemDiscount(BigDecimal.ZERO);
                item.setItemTotal(itemTotal);
                item.setItemPaid(itemTotal);
                item.setModifiers(itemReq.getModifiers());
                item.setRefundedQuantity(BigDecimal.ZERO);
                item.setRefundedAmount(BigDecimal.ZERO);
                item.setItemStatus(OrderItemTypeEnum.PENDING.getCode());
                item.setCreator("");
                item.setCreateTime(now);
                item.setUpdater("");
                item.setUpdateTime(now);
                item.setDeleted(false);

                orderItems.add(item);
                totalAmount = totalAmount.add(itemTotal);
            }

            // 5. Compute business_date and order_no
            LocalDate businessDate = businessDateCalculator.computeBusinessDate(now);
            String orderNo = generateOrderNo(tenantId);

            // 6. Create order
            OrderDO order = new OrderDO();
            order.setTenantId(tenantId);
            order.setOrderNo(orderNo);
            order.setBusinessDate(businessDate);
            order.setChannel(reqVO.getChannel());
            order.setOrderType("NORMAL");
            order.setCustomerUserId(null); // Anonymous by default; auth integration later
            order.setShopId(reqVO.getShopId());
            // CG-TS3: DINE_IN populates from session; non-DINE_IN keeps both null
            if (dineInSession != null) {
                order.setTableSessionId(dineInSession.getId());
                order.setTableNo(dineInSession.getTableNo());
            } else {
                order.setTableSessionId(null);
                order.setTableNo(null);
            }
            order.setTotalAmount(totalAmount);
            order.setPaidAmount(BigDecimal.ZERO);
            order.setDiscountAmount(BigDecimal.ZERO);
            order.setRefundAmount(BigDecimal.ZERO);
            order.setPlatformFee(BigDecimal.ZERO);
            order.setStatus(OrderStatusEnum.PENDING.getCode());
            order.setCouponId(reqVO.getCouponId());
            order.setCustomerRemark(reqVO.getCustomerRemark());
            order.setVersion(0);
            order.setCreator("");
            order.setCreateTime(now);
            order.setUpdater("");
            order.setUpdateTime(now);
            order.setDeleted(false);

            // Build promotion IDs string
            if (reqVO.getPromotionIds() != null && !reqVO.getPromotionIds().isEmpty()) {
                order.setPromotionIds(reqVO.getPromotionIds().stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(",")));
            }

            orderMapper.insert(order);

            // Insert order items
            for (OrderItemDO item : orderItems) {
                item.setOrderId(order.getId());
                orderItemMapper.insert(item);
            }

            // 7. Write event log (INSERT-only)
            OrderEventLogDO eventLog = new OrderEventLogDO();
            eventLog.setTenantId(tenantId);
            eventLog.setOrderId(order.getId());
            eventLog.setEventType(OrderEventTypeEnum.CREATE.getCode());
            eventLog.setBeforeStatus(null);
            eventLog.setAfterStatus(OrderStatusEnum.PENDING.getCode());
            eventLog.setOperatorUserId(null);
            eventLog.setOperatorRole("CUSTOMER");
            eventLog.setPayload("{}");
            eventLog.setEventTime(now);
            eventLog.setCreateTime(now);
            eventLogMapper.insert(eventLog);

            // 8. Mark idempotent SUCCESS
            idempotentService.markSuccess(idempotentKey, order.getId());

            return buildRespVO(order, orderItems);

        } catch (Exception e) {
            // Mark idempotent FAILED to allow retry
            idempotentService.markFailed(idempotentKey);
            throw e;
        }
    }

    @Override
    public OrderDO getOrder(Long orderId) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        OrderDO order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new OrderBusinessException(OrderErrorCodeConstants.ORDER_NOT_FOUND);
        }
        return order;
    }

    @Override
    public List<OrderItemDO> getOrderItems(Long orderId) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        return orderItemMapper.selectList(
                OrderItemDO::getOrderId, orderId,
                OrderItemDO::getTenantId, tenantId
        );
    }

    @Override
    public PageResult<OrderDO> listMyOrders(Long customerUserId, Integer pageNo, Integer pageSize) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        LambdaQueryWrapper<OrderDO> wrapper = new LambdaQueryWrapper<OrderDO>()
                .eq(OrderDO::getTenantId, tenantId)
                .orderByDesc(OrderDO::getCreateTime);

        // Handle null customerUserId — use isNull for null values, eq for non-null
        if (customerUserId != null) {
            wrapper.eq(OrderDO::getCustomerUserId, customerUserId);
        } else {
            wrapper.isNull(OrderDO::getCustomerUserId);
        }

        return orderMapper.selectPage(pageNo, pageSize, wrapper);
    }

    // --- Private helpers ---

    private void validateCreateRequest(OrderCreateReqVO reqVO) {
        if (reqVO.getShopId() == null) {
            throw new OrderBusinessException(OrderErrorCodeConstants.ORDER_ITEMS_EMPTY, "shopId is required");
        }

        // Validate channel
        if (reqVO.getChannel() == null || reqVO.getChannel().isBlank()) {
            throw new OrderBusinessException(OrderErrorCodeConstants.INVALID_ORDER_CHANNEL);
        }
        // Validate channel is a known enum value
        try {
            OrderChannelEnum.fromCode(reqVO.getChannel());
        } catch (IllegalArgumentException e) {
            throw new OrderBusinessException(OrderErrorCodeConstants.INVALID_ORDER_CHANNEL,
                    "channel=" + reqVO.getChannel());
        }

        // Validate items
        if (reqVO.getItems() == null || reqVO.getItems().isEmpty()) {
            throw new OrderBusinessException(OrderErrorCodeConstants.ORDER_ITEMS_EMPTY);
        }

        for (OrderItemReqVO item : reqVO.getItems()) {
            if (item.getSkuId() == null) {
                throw new OrderBusinessException(OrderErrorCodeConstants.SKU_NOT_FOUND, "skuId is null");
            }
            if (item.getQuantity() == null || item.getQuantity().compareTo(new BigDecimal("0.01")) < 0) {
                throw new OrderBusinessException(OrderErrorCodeConstants.ORDER_ITEMS_EMPTY,
                        "quantity must be >= 0.01 for skuId=" + item.getSkuId());
            }
        }
    }

    private String generateOrderNo(Long tenantId) {
        // Tenant prefix + timestamp + UUID suffix for uniqueness
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String uuidSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "T" + tenantId + timestamp + uuidSuffix;
    }

    private OrderCreateRespVO buildRespVO(OrderDO order) {
        OrderCreateRespVO resp = new OrderCreateRespVO();
        resp.setOrderId(order.getId());
        resp.setOrderNo(order.getOrderNo());
        resp.setTotalAmount(order.getTotalAmount());
        resp.setBusinessDate(order.getBusinessDate());
        resp.setStatus(order.getStatus());
        resp.setChannel(order.getChannel());
        resp.setCreateTime(order.getCreateTime());
        return resp;
    }

    private OrderCreateRespVO buildRespVO(OrderDO order, List<OrderItemDO> items) {
        OrderCreateRespVO resp = buildRespVO(order);
        List<OrderCreateRespVO.OrderItemRespVO> itemResps = items.stream().map(item -> {
            OrderCreateRespVO.OrderItemRespVO itemResp = new OrderCreateRespVO.OrderItemRespVO();
            itemResp.setSkuId(item.getSkuId());
            itemResp.setSkuName(item.getSkuName());
            itemResp.setUnitPrice(item.getUnitPrice());
            itemResp.setQuantity(item.getQuantity());
            itemResp.setItemTotal(item.getItemTotal());
            return itemResp;
        }).collect(Collectors.toList());
        resp.setItems(itemResps);
        return resp;
    }

    // --- G1-01B additions: cancelOrder + markOrderPaid ---

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long orderId, String cancelReason) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        // Validate order exists and is PENDING
        OrderDO order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new OrderBusinessException(OrderErrorCodeConstants.ORDER_NOT_FOUND);
        }
        if (!OrderStatusEnum.PENDING.getCode().equals(order.getStatus())) {
            throw new OrderBusinessException(OrderErrorCodeConstants.INVALID_STATUS_TRANSFER,
                    "only PENDING orders can be cancelled, current=" + order.getStatus());
        }

        // Set cancel reason on order before transition
        order.setCancelReason(cancelReason);
        order.setUpdater("CUSTOMER");
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);

        // Transition PENDING → CANCELLED via state machine
        String payload = "{\"cancelReason\":\"" + (cancelReason != null ? cancelReason : "") + "\"}";
        stateMachineService.transition(orderId, OrderStatusEnum.CANCELLED.getCode(), null, "CUSTOMER", payload);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderDO markOrderPaid(Long orderId, String paymentMethod, BigDecimal paymentAmount, String externalNo) {
        return markOrderPaid(orderId, paymentMethod, paymentAmount, externalNo, LocalDateTime.now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderDO markOrderPaid(Long orderId, String paymentMethod, BigDecimal paymentAmount,
                                 String externalNo, LocalDateTime paymentTime) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");

        // Validate payment method
        PaymentMethodEnum methodEnum = PaymentMethodEnum.fromCode(paymentMethod);

        // Validate order exists and is PENDING
        OrderDO order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new OrderBusinessException(OrderErrorCodeConstants.ORDER_NOT_FOUND);
        }
        if (!OrderStatusEnum.PENDING.getCode().equals(order.getStatus())) {
            throw new OrderBusinessException(OrderErrorCodeConstants.ORDER_ALREADY_PAID);
        }

        // Validate amount matches (total - discount)
        BigDecimal expectedAmount = order.getTotalAmount().subtract(order.getDiscountAmount());
        if (paymentAmount.compareTo(expectedAmount) != 0) {
            throw new OrderBusinessException(OrderErrorCodeConstants.PAYMENT_AMOUNT_MISMATCH,
                    "expected=" + expectedAmount + " actual=" + paymentAmount);
        }

        // (b) Write order_payment record (INSERT-only, payment_status=SUCCESS)
        // Use the provided paymentTime (preserves checkout_session.payment_time for checkout conversion)
        OrderPaymentDO payment = paymentService.createPayment(orderId, paymentMethod, paymentAmount, externalNo, paymentTime);

        // (d) Update orders.paid_amount + payment_method + pay_time
        order.setPaidAmount(paymentAmount);
        order.setPaymentMethod(methodEnum.getCode());
        order.setPayTime(paymentTime);
        order.setUpdater("STAFF");
        order.setUpdateTime(paymentTime);
        orderMapper.updateById(order);

        // (c) Transition PENDING → PAID via state machine + write event log
        String payload = "{\"paymentNo\":\"" + payment.getPaymentNo() + "\",\"paymentMethod\":\"" +
                methodEnum.getCode() + "\"}";
        OrderDO result = stateMachineService.transition(orderId, OrderStatusEnum.PAID.getCode(), null, "STAFF", payload);

        // State machine transition overwrites payTime with now() on PAID transition.
        // Restore the preserved paymentTime (from checkout_session.payment_time) so that
        // order.payTime reflects the actual payment time, not the conversion processing time.
        result.setPayTime(paymentTime);
        orderMapper.updateById(result);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderDO acceptOrder(Long orderId) {
        Objects.requireNonNull(orderId, "orderId must not be null");

        // Step 1: PAID → ACCEPTED
        stateMachineService.transition(
                orderId, OrderStatusEnum.ACCEPTED.getCode(), null, "STAFF", "{}");

        // Step 2: ACCEPTED → PREPARING (merged per D-2 decision, no KDS yet)
        // If this fails, the outer @Transactional rolls back Step 1 too.
        return stateMachineService.transition(
                orderId, OrderStatusEnum.PREPARING.getCode(), null, "STAFF", "{}");
    }

    // --- G1-01C additions: refundRequest ---

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderRefundDO refundRequest(Long orderId, String refundType, BigDecimal refundAmount,
                                       String reasonType, String reasonDetail,
                                       List<Long> refundItemIds,
                                       Long operatorUserId, String operatorRole) {
        return refundService.createRefund(orderId, refundType, refundAmount,
                reasonType, reasonDetail, refundItemIds, operatorUserId, operatorRole);
    }

    // --- G1-04C: Checkout-to-Order Conversion ---

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderDO createFromCheckout(Long checkoutSessionId) {
        Long tenantId = TenantContextHolder.getTenantId();
        Objects.requireNonNull(tenantId, "Tenant context is required");
        LocalDateTime now = LocalDateTime.now();

        // 1. SELECT checkout_session by ID
        CheckoutSessionDO session = checkoutSessionMapper.selectById(checkoutSessionId);
        if (session == null) {
            throw new CartBusinessException(CartErrorCodeConstants.CART_NOT_FOUND,
                    "checkout session not found, id=" + checkoutSessionId);
        }

        // Cross-tenant protection
        if (!session.getTenantId().equals(tenantId)) {
            throw new CartBusinessException(CartErrorCodeConstants.CART_TENANT_MISMATCH,
                    "checkout session tenantId=" + session.getTenantId()
                            + " does not match current tenantId=" + tenantId);
        }

        // 2. Validate: status == PAID, order_id IS NULL
        if (!CheckoutStatusEnum.PAID.getCode().equals(session.getStatus())) {
            throw new CartBusinessException(CartErrorCodeConstants.CHECKOUT_DUPLICATE,
                    "checkout session is not PAID, current status=" + session.getStatus());
        }
        if (session.getOrderId() != null) {
            // Already linked — return existing order (idempotent at service level)
            return orderMapper.selectById(session.getOrderId());
        }

        // 3. SELECT cart by session.cartId
        CartDO cart = cartMapper.selectById(session.getCartId());
        if (cart == null) {
            throw new CartBusinessException(CartErrorCodeConstants.CART_NOT_FOUND,
                    "cart not found, cartId=" + session.getCartId());
        }

        // Cross-shop/customer protection
        if (!cart.getShopId().equals(session.getShopId())) {
            throw new CartBusinessException(CartErrorCodeConstants.CART_NOT_FOUND,
                    "cart shopId=" + cart.getShopId()
                            + " does not match session shopId=" + session.getShopId());
        }
        if (!cart.getCustomerUserId().equals(session.getCustomerUserId())) {
            throw new CartBusinessException(CartErrorCodeConstants.CART_NOT_FOUND,
                    "cart customerUserId=" + cart.getCustomerUserId()
                            + " does not match session customerUserId=" + session.getCustomerUserId());
        }

        // 4. SELECT cart_items by cart_id (non-deleted)
        List<CartItemDO> cartItems = cartItemMapper.selectList(
                CartItemDO::getCartId, session.getCartId(),
                CartItemDO::getTenantId, tenantId
        );
        if (cartItems == null || cartItems.isEmpty()) {
            throw new CartBusinessException(CartErrorCodeConstants.CART_ITEM_NOT_FOUND,
                    "no cart items found for cartId=" + session.getCartId());
        }

        // 5. ProductApi.batchGetSkus for metadata (skuCode, spuName, categoryId — NOT pricing)
        Set<Long> skuIds = cartItems.stream()
                .map(CartItemDO::getSkuId)
                .collect(Collectors.toSet());
        Map<Long, SkuRespDTO> skuMap = productApi.batchGetSkus(new ArrayList<>(skuIds));

        // 6. INSERT orders (status = PENDING)
        // Order totalAmount = session.subtotalAmount (pre-discount receivable)
        // Order discountAmount = session.discountAmount
        // markOrderPaid validates: paymentAmount == totalAmount - discountAmount
        //   = subtotalAmount - discountAmount = session.totalAmount ✓
        OrderDO order = new OrderDO();
        order.setTenantId(tenantId);
        order.setOrderNo(generateOrderNo(tenantId));
        order.setBusinessDate(session.getBusinessDate());
        order.setChannel(session.getChannel());
        order.setOrderType("NORMAL");
        order.setCustomerUserId(session.getCustomerUserId());
        order.setShopId(session.getShopId());
        order.setTableSessionId(null);
        order.setTableNo(null);
        order.setTotalAmount(session.getSubtotalAmount());
        order.setPaidAmount(BigDecimal.ZERO);
        order.setDiscountAmount(session.getDiscountAmount());
        order.setRefundAmount(BigDecimal.ZERO);
        order.setPlatformFee(BigDecimal.ZERO);
        order.setStatus(OrderStatusEnum.PENDING.getCode());
        order.setCouponId(null);
        order.setCustomerRemark(session.getRemark());
        order.setVersion(0);
        order.setCreator(String.valueOf(session.getCustomerUserId()));
        order.setCreateTime(now);
        order.setUpdater(String.valueOf(session.getCustomerUserId()));
        order.setUpdateTime(now);
        order.setDeleted(false);
        orderMapper.insert(order);

        // 7. INSERT order_items (from cart snapshots — pricing never from ProductApi)
        Map<Long, Long> sourceOrderItemIdByCartItemId = new HashMap<>();
        for (CartItemDO cartItem : cartItems) {
            SkuRespDTO sku = skuMap.get(cartItem.getSkuId());
            if (sku == null) {
                throw new OrderBusinessException(OrderErrorCodeConstants.SKU_NOT_FOUND,
                        "skuId=" + cartItem.getSkuId());
            }
            SpuRespDTO spu = productApi.getSpu(cartItem.getSpuId());
            if (spu == null) {
                throw new OrderBusinessException(OrderErrorCodeConstants.SPU_NOT_FOUND,
                        "spuId=" + cartItem.getSpuId());
            }

            OrderItemDO item = new OrderItemDO();
            item.setTenantId(tenantId);
            item.setOrderId(order.getId());
            item.setSkuId(cartItem.getSkuId());
            item.setSkuCode(sku.getSkuCode());             // metadata from ProductApi
            item.setSkuName(cartItem.getSkuNameSnapshot()); // from cart snapshot
            item.setSpuId(cartItem.getSpuId());
            item.setSpuName(spu.getSpuName());              // metadata from ProductApi
            item.setCategoryId(spu.getCategoryId());        // metadata from ProductApi
            item.setUnitPrice(cartItem.getUnitPriceSnapshot()); // from cart snapshot, NOT ProductApi
            item.setQuantity(new BigDecimal(cartItem.getQuantity())); // Integer → BigDecimal
            item.setUnit("份");
            item.setItemDiscount(cartItem.getItemDiscount()); // from cart snapshot
            item.setItemTotal(cartItem.getItemSubtotal());    // pre-discount receivable
            item.setItemPaid(cartItem.getItemTotal());        // after-discount payable
            item.setModifiers(cartItem.getOptions());         // from cart snapshot
            item.setRefundedQuantity(BigDecimal.ZERO);
            item.setRefundedAmount(BigDecimal.ZERO);
            item.setItemStatus(OrderItemTypeEnum.PENDING.getCode());
            item.setCreator(String.valueOf(session.getCustomerUserId()));
            item.setCreateTime(now);
            item.setUpdater(String.valueOf(session.getCustomerUserId()));
            item.setUpdateTime(now);
            item.setDeleted(false);
            orderItemMapper.insert(item);
            sourceOrderItemIdByCartItemId.put(cartItem.getId(), item.getId());
        }

        // 8. INSERT order_event_log (CREATE event)
        OrderEventLogDO eventLog = new OrderEventLogDO();
        eventLog.setTenantId(tenantId);
        eventLog.setOrderId(order.getId());
        eventLog.setEventType(OrderEventTypeEnum.CREATE.getCode());
        eventLog.setBeforeStatus(null);
        eventLog.setAfterStatus(OrderStatusEnum.PENDING.getCode());
        eventLog.setOperatorUserId(session.getCustomerUserId());
        eventLog.setOperatorRole("CUSTOMER");
        eventLog.setPayload("{}");
        eventLog.setEventTime(now);
        eventLog.setCreateTime(now);
        eventLogMapper.insert(eventLog);

        // 9. Call markOrderPaid (within same transaction)
        // Creates order_payment record, transitions PENDING → PAID, writes PAY event log
        // paymentAmount = session.totalAmount = subtotalAmount - discountAmount
        // paymentTime = session.paymentTime (preserve checkout payment time for audit fields)
        markOrderPaid(order.getId(), session.getPaymentMethod(),
                session.getTotalAmount(), session.getPaymentTradeNo(), session.getPaymentTime());

        // 9b. G2-01B2 + G2-02H-3: Commit stock reservations for this checkout session.
        // For non-BOM SKUs: commit uses CONSUME_OUT event type (no new enum values).
        // For active-BOM SKUs: skip finished-SKU commit and call StockApi.salesOutWithBomReverse
        // to deduct raw-material stock. orderId is passed explicitly (not read from
        // checkout_session.order_id because that link is set AFTER stock commit in this flow).
        // If commit fails, exception propagates and rolls back the entire createFromCheckout
        // transaction: order not created, session remains PAID + orderId=null.
        stockIntegrationService.commitByCheckoutSession(
                tenantId,
                session.getId(),
                order.getId(),
                session.getCustomerUserId(),
                session.getBusinessDate(),
                order.getOrderNo(),
                sourceOrderItemIdByCartItemId);

        // 10. Conditional UPDATE checkout_session SET order_id = ? WHERE order_id IS NULL
        // This is the primary idempotency guard — "set once" semantics on order_id
        LambdaUpdateWrapper<CheckoutSessionDO> updateWrapper = new LambdaUpdateWrapper<CheckoutSessionDO>()
                .eq(CheckoutSessionDO::getId, checkoutSessionId)
                .isNull(CheckoutSessionDO::getOrderId)
                .eq(CheckoutSessionDO::getStatus, CheckoutStatusEnum.PAID.getCode())
                .eq(CheckoutSessionDO::getDeleted, false)
                .set(CheckoutSessionDO::getOrderId, order.getId())
                .set(CheckoutSessionDO::getUpdater, "CHECKOUT_CONVERT")
                .set(CheckoutSessionDO::getUpdateTime, now);
        int rows = checkoutSessionMapper.update(null, updateWrapper);

        // 11. If 0 rows → race condition detected → rollback everything
        if (rows == 0) {
            throw new RuntimeException("Checkout session order_id link failed — race condition detected, "
                    + "another thread may have already linked an order, sessionId=" + checkoutSessionId);
        }

        // Return the updated order (now PAID)
        return orderMapper.selectById(order.getId());
    }
}
