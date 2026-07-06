package com.geihou.module.finance.checkout.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.api.cart.enums.CartEventTypeEnum;
import com.geihou.module.finance.api.cart.enums.CartStatusEnum;
import com.geihou.module.finance.cart.dal.dataobject.CartDO;
import com.geihou.module.finance.cart.dal.dataobject.CartEventLogDO;
import com.geihou.module.finance.cart.dal.mapper.CartEventLogMapper;
import com.geihou.module.finance.cart.dal.mapper.CartMapper;
import com.geihou.module.finance.cart.framework.CartBusinessException;
import com.geihou.module.finance.cart.framework.CartErrorCodeConstants;
import com.geihou.module.finance.checkout.controller.app.customer.vo.CheckoutInitiateReqVO;
import com.geihou.module.finance.checkout.controller.app.customer.vo.CheckoutPayReqVO;
import com.geihou.module.finance.checkout.controller.app.customer.vo.CheckoutSessionVO;
import com.geihou.module.finance.checkout.convert.CheckoutConvert;
import com.geihou.module.finance.checkout.dal.dataobject.CheckoutIdempotentDO;
import com.geihou.module.finance.checkout.dal.dataobject.CheckoutSessionDO;
import com.geihou.module.finance.checkout.dal.mapper.CheckoutIdempotentMapper;
import com.geihou.module.finance.checkout.dal.mapper.CheckoutSessionMapper;
import com.geihou.module.finance.checkout.framework.CheckoutStatusEnum;
import com.geihou.module.finance.order.service.BusinessDateCalculator;
import com.geihou.module.finance.order.service.OrderService;
import com.geihou.module.finance.stock.StockIntegrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Checkout service implementation for customer checkout operations.
 *
 * <p>Tenant isolation via TenantContextHolder + MyBatis-Plus TenantLineInnerInterceptor.
 * All money fields use BigDecimal (never double/float).
 * All mutations are @Transactional with triple-write (session + cart + event log)
 * per Cart root-cause 3 (fire-and-forget defense).
 *
 * <p>G1-04D: Auto-conversion is always on. After simulated payment commits (PAID),
 *   {@link #convertToOrder(String)} is called in a separate transaction. If conversion
 *   fails, session remains PAID with orderId = null (保金不丢). Client retries via /convert.
 * <p>CG-8 StockApi integrated (G2-01B2): reserve on checkout initiate, release on cancel/expire/pay-fail, commit on order convert.
 *   Oversell protection is ACTIVE for SKUs with stock_item + stock_location mapping configured.
 *   SKUs without mapping are SKIPPED (no reserve) — oversell risk remains for unmapped SKUs.
 * <p>CG-9 Simulated local payment bridge: NOT real WeChat Pay, no signature verification.
 *   Production payment requires real payment gateway SDK.
 * <p>CG-11 PromotionApi missing: Reject couponIds with COUPON_INVALID, do not silently ignore.
 * <p>Session expiry: 5 min per PRD §4.1/§4.2. Lazy check on query/pay + scheduled job (G1-04E).
 */
@Service
public class CheckoutServiceImpl implements CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutServiceImpl.class);

    private static final int SESSION_EXPIRE_MINUTES = 5;
    private static final int IDEMPOTENT_EXPIRE_HOURS = 24;

    private final CheckoutSessionMapper checkoutSessionMapper;
    private final CheckoutIdempotentMapper checkoutIdempotentMapper;
    private final CartMapper cartMapper;
    private final CartEventLogMapper cartEventLogMapper;
    private final BusinessDateCalculator businessDateCalculator;
    private final OrderService orderService;
    private final CheckoutService self;
    private final StockIntegrationService stockIntegrationService;

    public CheckoutServiceImpl(CheckoutSessionMapper checkoutSessionMapper,
                               CheckoutIdempotentMapper checkoutIdempotentMapper,
                               CartMapper cartMapper,
                               CartEventLogMapper cartEventLogMapper,
                               BusinessDateCalculator businessDateCalculator,
                               @Lazy OrderService orderService,
                               @Lazy CheckoutService self,
                               StockIntegrationService stockIntegrationService) {
        this.checkoutSessionMapper = checkoutSessionMapper;
        this.checkoutIdempotentMapper = checkoutIdempotentMapper;
        this.cartMapper = cartMapper;
        this.cartEventLogMapper = cartEventLogMapper;
        this.businessDateCalculator = businessDateCalculator;
        this.orderService = orderService;
        this.self = self;
        this.stockIntegrationService = stockIntegrationService;
    }

    @Override
    @Transactional
    public CheckoutSessionVO initiateCheckout(CheckoutInitiateReqVO reqVO) {
        return doInitiateCheckout(reqVO.getCustomerUserId(), "CUSTOMER", reqVO);
    }

    @Override
    @Transactional
    public CheckoutSessionVO staffInitiateCheckout(Long staffUserId, CheckoutInitiateReqVO reqVO) {
        return doInitiateCheckout(staffUserId, "STAFF", reqVO);
    }

    /**
     * Core initiate checkout logic shared by customer and staff paths.
     *
     * @param operatorUserId the user id to attribute as creator/updater and event log operator
     * @param operatorRole   "CUSTOMER" or "STAFF"
     * @param reqVO          initiate request
     */
    private CheckoutSessionVO doInitiateCheckout(Long operatorUserId, String operatorRole,
                                                  CheckoutInitiateReqVO reqVO) {
        Long tenantId = TenantContextHolder.getTenantId();
        LocalDateTime now = LocalDateTime.now();

        // CG-11: Reject non-empty couponIds with COUPON_INVALID
        if (reqVO.getCouponIds() != null && !reqVO.getCouponIds().isEmpty()) {
            throw new CartBusinessException(CartErrorCodeConstants.COUPON_INVALID,
                    "PromotionApi not available, coupon validation unavailable, please remove coupon and retry");
        }

        // Idempotency check: if same idempotent key exists, return same session
        if (reqVO.getIdempotentKey() != null && !reqVO.getIdempotentKey().isBlank()) {
            CheckoutIdempotentDO existing = checkoutIdempotentMapper.selectOne(
                    new LambdaQueryWrapper<CheckoutIdempotentDO>()
                            .eq(CheckoutIdempotentDO::getTenantId, tenantId)
                            .eq(CheckoutIdempotentDO::getIdempotentKey, reqVO.getIdempotentKey())
                            .last("LIMIT 1"));
            if (existing != null) {
                // Return existing session
                CheckoutSessionDO session = checkoutSessionMapper.selectById(existing.getSessionId());
                if (session != null) {
                    return CheckoutConvert.convert(session);
                }
            }
        }

        // Find the customer's ACTIVE cart
        CartDO cart = findActiveCart(reqVO.getCustomerUserId(), reqVO.getShopId());
        if (cart == null) {
            // Check if there's a CHECKOUT-status cart (already in checkout)
            CartDO checkoutCart = findCheckoutCart(reqVO.getCustomerUserId(), reqVO.getShopId());
            if (checkoutCart != null) {
                // Check for duplicate checkout: same cart with INITIATED session
                CheckoutSessionDO existingSession = findInitiatedSessionByCartId(checkoutCart.getId());
                if (existingSession != null) {
                    throw new CartBusinessException(CartErrorCodeConstants.CHECKOUT_DUPLICATE,
                            "cartId=" + checkoutCart.getId() + " already has an INITIATED checkout session");
                }
            }
            throw new CartBusinessException(CartErrorCodeConstants.CART_NOT_FOUND,
                    "customerUserId=" + reqVO.getCustomerUserId() + ", shopId=" + reqVO.getShopId());
        }

        // Check for duplicate checkout: same cart with INITIATED session
        CheckoutSessionDO existingSession = findInitiatedSessionByCartId(cart.getId());
        if (existingSession != null) {
            throw new CartBusinessException(CartErrorCodeConstants.CHECKOUT_DUPLICATE,
                    "cartId=" + cart.getId() + " already has an INITIATED checkout session");
        }

        // Lock cart: ACTIVE → CHECKOUT
        cart.setStatus(CartStatusEnum.CHECKOUT.getCode());
        cart.setLastActivityTime(now);
        cart.setUpdater(String.valueOf(operatorUserId));
        cart.setUpdateTime(now);
        cartMapper.updateById(cart);

        // Create checkout session
        CheckoutSessionDO session = new CheckoutSessionDO();
        session.setTenantId(tenantId);
        session.setCartId(cart.getId());
        session.setCustomerUserId(cart.getCustomerUserId());
        session.setShopId(cart.getShopId());
        // AC-6: Server-generated unique token, never client-provided/static/mock
        session.setSessionToken(generateSessionToken());
        session.setStatus(CheckoutStatusEnum.INITIATED.getCode());
        session.setSubtotalAmount(cart.getSubtotalAmount());
        session.setDiscountAmount(cart.getDiscountAmount());
        // CG-11: locked_discount always 0
        session.setLockedDiscount(BigDecimal.ZERO);
        session.setTotalAmount(cart.getTotalAmount());
        // CG-11: applied_promotions/applied_coupon_ids always null
        session.setAppliedPromotions(null);
        session.setAppliedCouponIds(null);
        // CG-7: order_id always null in G1-04B
        session.setOrderId(null);
        session.setBusinessDate(businessDateCalculator.computeBusinessDate(now));
        session.setExpireTime(now.plusMinutes(SESSION_EXPIRE_MINUTES));
        session.setChannel(cart.getChannel());
        session.setRemark(reqVO.getRemark());
        session.setCreator(String.valueOf(operatorUserId));
        session.setCreateTime(now);
        session.setUpdater(String.valueOf(operatorUserId));
        session.setUpdateTime(now);
        session.setDeleted(false);
        checkoutSessionMapper.insert(session);

        // G2-01B2: Reserve stock for all mapped SKUs in this checkout session.
        // If reserve fails (e.g. INSUFFICIENT_AVAILABLE_STOCK), exception propagates
        // and rolls back the entire @Transactional (checkout_session INSERT, cart lock, etc.)
        stockIntegrationService.reserveForCheckout(
                tenantId,
                session.getId(),
                cart.getId(),
                session.getShopId(),
                operatorUserId);

        // Write CHECKOUT_STARTED event log (same transaction — no fire-and-forget)
        writeCartEventLog(cart, CartEventTypeEnum.CHECKOUT_STARTED, operatorUserId,
                operatorRole, cart.getTotalAmount(), cart.getTotalAmount(), now);

        // Write idempotent record
        if (reqVO.getIdempotentKey() != null && !reqVO.getIdempotentKey().isBlank()) {
            CheckoutIdempotentDO idempotent = new CheckoutIdempotentDO();
            idempotent.setTenantId(tenantId);
            idempotent.setIdempotentKey(reqVO.getIdempotentKey());
            idempotent.setSessionId(session.getId());
            idempotent.setSessionToken(session.getSessionToken());
            idempotent.setStatus("SUCCESS");
            idempotent.setExpireTime(now.plusHours(IDEMPOTENT_EXPIRE_HOURS));
            idempotent.setCreateTime(now);
            checkoutIdempotentMapper.insert(idempotent);
        }

        return CheckoutConvert.convert(session);
    }

    @Override
    @Transactional
    public CheckoutSessionVO querySession(String sessionToken) {
        return doQuerySession(null, null, sessionToken);
    }

    @Override
    @Transactional
    public CheckoutSessionVO staffQuerySession(Long staffUserId, String sessionToken) {
        return doQuerySession(staffUserId, "STAFF", sessionToken);
    }

    /**
     * Core query session logic shared by customer and staff paths.
     *
     * <p>For customer path, lazy expiry uses the session's customerUserId and "CUSTOMER" role.
     * For staff path, lazy expiry uses the staffUserId and "STAFF" role.
     *
     * @param operatorUserId if non-null, used for lazy-expiry attribution; if null, customer is used
     * @param operatorRole   if non-null, used for lazy-expiry attribution; if null, "CUSTOMER" is used
     * @param sessionToken   server-generated session token
     */
    private CheckoutSessionVO doQuerySession(Long operatorUserId, String operatorRole, String sessionToken) {
        CheckoutSessionDO session = findByToken(sessionToken);
        if (session == null) {
            throw new CartBusinessException(CartErrorCodeConstants.CART_NOT_FOUND,
                    "checkout session not found for token=" + sessionToken);
        }

        // Lazy expiry check: if INITIATED and expired, transition to EXPIRED
        if (CheckoutStatusEnum.INITIATED.getCode().equals(session.getStatus())
                && session.getExpireTime() != null
                && session.getExpireTime().isBefore(LocalDateTime.now())) {
            Long expiryOperatorUserId = operatorUserId != null ? operatorUserId : session.getCustomerUserId();
            String expiryOperatorRole = operatorRole != null ? operatorRole : "CUSTOMER";
            expireSession(session, expiryOperatorRole, expiryOperatorUserId);
            session = checkoutSessionMapper.selectById(session.getId());
        }

        return CheckoutConvert.convert(session);
    }

    @Override
    @Transactional
    public CheckoutSessionVO cancelSession(String sessionToken) {
        return doCancelSession(null, null, sessionToken);
    }

    @Override
    @Transactional
    public CheckoutSessionVO staffCancelSession(Long staffUserId, String sessionToken) {
        return doCancelSession(staffUserId, "STAFF", sessionToken);
    }

    /**
     * Core cancel session logic shared by customer and staff paths.
     *
     * @param operatorUserId if non-null, used for updater and event log operator; if null, customer is used
     * @param operatorRole   if non-null, used for event log operator role; if null, "CUSTOMER" is used
     * @param sessionToken   server-generated session token
     */
    private CheckoutSessionVO doCancelSession(Long operatorUserId, String operatorRole, String sessionToken) {
        CheckoutSessionDO session = findByToken(sessionToken);
        if (session == null) {
            throw new CartBusinessException(CartErrorCodeConstants.CART_NOT_FOUND,
                    "checkout session not found for token=" + sessionToken);
        }

        // Idempotent: if already in terminal state, return current state
        if (CheckoutStatusEnum.ABANDONED.getCode().equals(session.getStatus())
                || CheckoutStatusEnum.EXPIRED.getCode().equals(session.getStatus())
                || CheckoutStatusEnum.PAID.getCode().equals(session.getStatus())
                || CheckoutStatusEnum.FAILED.getCode().equals(session.getStatus())) {
            return CheckoutConvert.convert(session);
        }

        Long effectiveOperatorUserId = operatorUserId != null ? operatorUserId : session.getCustomerUserId();
        String effectiveOperatorRole = operatorRole != null ? operatorRole : "CUSTOMER";

        // Only INITIATED sessions can be cancelled
        LocalDateTime now = LocalDateTime.now();
        session.setStatus(CheckoutStatusEnum.ABANDONED.getCode());
        session.setUpdater(String.valueOf(effectiveOperatorUserId));
        session.setUpdateTime(now);
        checkoutSessionMapper.updateById(session);

        // G2-01B2: Release stock reservations for this checkout session.
        // Release failure does NOT block terminal state — logged as risk.
        stockIntegrationService.releaseByCheckoutSession(
                session.getTenantId(),
                session.getId(),
                effectiveOperatorUserId);

        // Unlock cart: CHECKOUT → ACTIVE
        unlockCart(session.getCartId(), effectiveOperatorUserId, now);

        // Write CHECKOUT_ABANDONED event log
        CartDO cart = cartMapper.selectById(session.getCartId());
        if (cart != null) {
            writeCartEventLog(cart, CartEventTypeEnum.CHECKOUT_ABANDONED, effectiveOperatorUserId,
                    effectiveOperatorRole, session.getTotalAmount(), session.getTotalAmount(), now);
        }

        return CheckoutConvert.convert(session);
    }

    @Override
    public CheckoutSessionVO paySession(String sessionToken, CheckoutPayReqVO reqVO) {
        CheckoutSessionDO session = findByToken(sessionToken);
        if (session == null) {
            throw new CartBusinessException(CartErrorCodeConstants.CART_NOT_FOUND,
                    "checkout session not found for token=" + sessionToken);
        }

        // G1-04M: Pre-transaction lazy-expiry check — mirrors staffPaySession.
        // If session is INITIATED and expired, persist expiry in its own transaction
        // via querySession before entering paySessionInternal. This prevents the
        // expiry transition from being rolled back when CHECKOUT_EXPIRED is thrown
        // inside paySessionInternal's transaction.
        if (CheckoutStatusEnum.INITIATED.getCode().equals(session.getStatus())
                && session.getExpireTime() != null
                && session.getExpireTime().isBefore(LocalDateTime.now())) {
            self.querySession(sessionToken);
            throw new CartBusinessException(CartErrorCodeConstants.CHECKOUT_EXPIRED,
                    "session token=" + sessionToken + " has expired");
        }

        // G1-04D: Idempotent re-pay — if already PAID, return existing session
        if (CheckoutStatusEnum.PAID.getCode().equals(session.getStatus())) {
            // If orderId is already set (previous conversion succeeded), return as-is
            if (session.getOrderId() != null) {
                return CheckoutConvert.convert(session);
            }
            // PAID but orderId is null — previous auto-conversion failed.
            // Retry conversion now (idempotent — won't create duplicate order).
            return autoConvertAfterPay(CheckoutConvert.convert(session));
        }

        // Only INITIATED sessions can be paid (non-PAID, non-INITIATED → reject)
        if (!CheckoutStatusEnum.INITIATED.getCode().equals(session.getStatus())) {
            throw new CartBusinessException(CartErrorCodeConstants.CHECKOUT_DUPLICATE,
                    "session is not in INITIATED status, current status=" + session.getStatus());
        }

        // Delegate to transactional pay method via self-injection proxy.
        // G1-04D Decision 1: pay commits PAID first, then conversion runs in separate transaction.
        CheckoutSessionVO paidResult = self.paySessionInternal(sessionToken, reqVO);

        // If pay failed (simulated failure), return the FAILED result — no conversion.
        if (CheckoutStatusEnum.FAILED.getCode().equals(paidResult.getStatus())) {
            return paidResult;
        }

        // Pay succeeded (PAID). Now auto-convert in a separate transaction.
        return autoConvertAfterPay(paidResult);
    }

    /**
     * G1-04D: Auto-convert a PAID session to an order.
     *
     * <p>Decision 2: If conversion fails, session remains PAID with orderId = null.
     * The pay response returns HTTP 200 with PAID + orderId null.
     * The client detects this and retries via POST /convert.
     */
    private CheckoutSessionVO autoConvertAfterPay(CheckoutSessionVO paidResult) {
        try {
            return convertToOrder(paidResult.getSessionToken());
        } catch (RuntimeException e) {
            // G1-04D Decision 2: Conversion failure does NOT roll back PAID.
            // Log at ERROR level for operational alerting (PRD §4.3: 严重故障 + 告警 + 人工介入).
            log.error("G1-04D auto-conversion failed for session token={}, sessionId={}. "
                    + "Session is PAID but orderId is null. Client should retry via /convert. Error: {}",
                    paidResult.getSessionToken(), paidResult.getId(), e.getMessage(), e);
            // Re-query to return the current PAID + null orderId state
            CheckoutSessionDO session = checkoutSessionMapper.selectById(paidResult.getId());
            return CheckoutConvert.convert(session);
        }
    }

    /**
     * G1-04D: Internal transactional pay method. Called by {@link #paySession} via self-injection proxy.
     *
     * <p>This method commits the PAID state (or FAILED state) in its own transaction.
     * After commit, {@link #paySession} calls {@link #convertToOrder} in a separate transaction.
     */
    @Override
    @Transactional
    public CheckoutSessionVO paySessionInternal(String sessionToken, CheckoutPayReqVO reqVO) {
        CheckoutSessionDO session = findByToken(sessionToken);
        if (session == null) {
            throw new CartBusinessException(CartErrorCodeConstants.CART_NOT_FOUND,
                    "checkout session not found for token=" + sessionToken);
        }
        return doPaySessionInternal(session.getCustomerUserId(), "CUSTOMER", session, reqVO);
    }

    /**
     * Core pay-internal logic shared by customer and staff paths.
     *
     * <p>G1-04L: Refactored from the original paySessionInternal to accept operator
     * id/role so that staff pay attributes session/cart updates and event logs to staff.
     *
     * @param operatorUserId the user id to attribute as updater and event log operator
     * @param operatorRole   "CUSTOMER" or "STAFF"
     * @param session        the checkout session (already fetched by caller)
     * @param reqVO          pay request
     * @return CheckoutSessionVO with PAID or FAILED status (orderId is null — conversion happens later)
     */
    private CheckoutSessionVO doPaySessionInternal(Long operatorUserId, String operatorRole,
                                                   CheckoutSessionDO session, CheckoutPayReqVO reqVO) {
        // Only INITIATED sessions can be paid
        if (!CheckoutStatusEnum.INITIATED.getCode().equals(session.getStatus())) {
            throw new CartBusinessException(CartErrorCodeConstants.CHECKOUT_DUPLICATE,
                    "session is not in INITIATED status, current status=" + session.getStatus());
        }

        // Lazy expiry check on pay: if expired, reject with CHECKOUT_EXPIRED
        if (session.getExpireTime() != null
                && session.getExpireTime().isBefore(LocalDateTime.now())) {
            expireSession(session, operatorRole, operatorUserId);
            throw new CartBusinessException(CartErrorCodeConstants.CHECKOUT_EXPIRED,
                    "session token=" + session.getSessionToken() + " has expired");
        }

        LocalDateTime now = LocalDateTime.now();
        CartDO cart = cartMapper.selectById(session.getCartId());

        // CG-9: Simulated local payment bridge
        // NOT real WeChat Pay. No signature verification.
        // Simulate failure if simulateFail is set
        if (reqVO.getSimulateFail() != null && "SIMULATE_FAIL".equals(reqVO.getSimulateFail())) {
            // Payment failure path — persist FAILED status, unlock cart, write event log
            session.setStatus(CheckoutStatusEnum.FAILED.getCode());
            session.setPaymentMethod(reqVO.getPaymentMethod());
            session.setUpdater(String.valueOf(operatorUserId));
            session.setUpdateTime(now);
            checkoutSessionMapper.updateById(session);

            // G2-01B2: Release stock reservations for pay-fail.
            // Release failure does NOT block FAILED terminal state — logged as risk.
            stockIntegrationService.releaseByCheckoutSession(
                    session.getTenantId(),
                    session.getId(),
                    operatorUserId);

            // Unlock cart: CHECKOUT → ACTIVE
            unlockCart(session.getCartId(), operatorUserId, now);

            // Write CHECKOUT_ABANDONED event log (FAILED → cart unlocked)
            if (cart != null) {
                writeCartEventLog(cart, CartEventTypeEnum.CHECKOUT_ABANDONED, operatorUserId,
                        operatorRole, session.getTotalAmount(), session.getTotalAmount(), now);
            }

            return CheckoutConvert.convert(session);
        }

        // Simulated payment success path
        session.setStatus(CheckoutStatusEnum.PAID.getCode());
        session.setPaymentMethod(reqVO.getPaymentMethod());
        session.setPaymentTime(now);
        // Generate simulated trade number
        session.setPaymentTradeNo("SIM" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase());
        // G1-04D: order_id will be set by auto-conversion after this transaction commits
        session.setOrderId(null);
        session.setUpdater(String.valueOf(operatorUserId));
        session.setUpdateTime(now);
        checkoutSessionMapper.updateById(session);

        // Cart: CHECKOUT → CONVERTED
        if (cart != null) {
            cart.setStatus(CartStatusEnum.CONVERTED.getCode());
            cart.setLastActivityTime(now);
            cart.setUpdater(String.valueOf(operatorUserId));
            cart.setUpdateTime(now);
            cartMapper.updateById(cart);
        }

        return CheckoutConvert.convert(session);
    }

    // --- G1-04L: Staff checkout pay + convert ---

    @Override
    public CheckoutSessionVO staffPaySession(Long staffUserId, String sessionToken, CheckoutPayReqVO reqVO) {
        CheckoutSessionDO session = findByToken(sessionToken);
        if (session == null) {
            throw new CartBusinessException(CartErrorCodeConstants.CART_NOT_FOUND,
                    "checkout session not found for token=" + sessionToken);
        }

        // G1-04L: Lazy expiry check on staff pay — attributed to staff.
        // Done in the non-transactional method via staffQuerySession (which runs
        // in its own transaction) so the expiry persists even though pay is rejected.
        if (CheckoutStatusEnum.INITIATED.getCode().equals(session.getStatus())
                && session.getExpireTime() != null
                && session.getExpireTime().isBefore(LocalDateTime.now())) {
            self.staffQuerySession(staffUserId, sessionToken);
            throw new CartBusinessException(CartErrorCodeConstants.CHECKOUT_EXPIRED,
                    "session token=" + sessionToken + " has expired");
        }

        // G1-04L: Idempotent re-pay — if already PAID, return existing session
        if (CheckoutStatusEnum.PAID.getCode().equals(session.getStatus())) {
            // If orderId is already set (previous conversion succeeded), return as-is
            if (session.getOrderId() != null) {
                return CheckoutConvert.convert(session);
            }
            // PAID but orderId is null — previous auto-conversion failed.
            // Retry staff conversion now (idempotent).
            return autoConvertAfterStaffPay(staffUserId, CheckoutConvert.convert(session));
        }

        // Only INITIATED sessions can be paid (non-PAID, non-INITIATED → reject)
        if (!CheckoutStatusEnum.INITIATED.getCode().equals(session.getStatus())) {
            throw new CartBusinessException(CartErrorCodeConstants.CHECKOUT_DUPLICATE,
                    "session is not in INITIATED status, current status=" + session.getStatus());
        }

        // Delegate to transactional pay method via self-injection proxy.
        // G1-04L Decision 1: pay commits PAID first, then conversion runs in separate transaction.
        CheckoutSessionVO paidResult = self.staffPaySessionInternal(staffUserId, sessionToken, reqVO);

        // If pay failed (simulated failure), return the FAILED result — no conversion.
        if (CheckoutStatusEnum.FAILED.getCode().equals(paidResult.getStatus())) {
            return paidResult;
        }

        // Pay succeeded (PAID). Now auto-convert in a separate transaction using staff-aware path.
        return autoConvertAfterStaffPay(staffUserId, paidResult);
    }

    @Override
    @Transactional
    public CheckoutSessionVO staffPaySessionInternal(Long staffUserId, String sessionToken, CheckoutPayReqVO reqVO) {
        CheckoutSessionDO session = findByToken(sessionToken);
        if (session == null) {
            throw new CartBusinessException(CartErrorCodeConstants.CART_NOT_FOUND,
                    "checkout session not found for token=" + sessionToken);
        }
        return doPaySessionInternal(staffUserId, "STAFF", session, reqVO);
    }

    /**
     * G1-04L: Auto-convert a PAID session to an order via the staff-aware convert path.
     *
     * <p>Decision 2: If conversion fails, session remains PAID with orderId = null.
     * The pay response returns HTTP 200 with PAID + orderId null.
     * The client detects this and retries via POST /convert.
     */
    private CheckoutSessionVO autoConvertAfterStaffPay(Long staffUserId, CheckoutSessionVO paidResult) {
        try {
            return staffConvertToOrder(staffUserId, paidResult.getSessionToken());
        } catch (RuntimeException e) {
            // G1-04L Decision 2: Conversion failure does NOT roll back PAID.
            log.error("G1-04L staff auto-conversion failed for session token={}, sessionId={}. "
                    + "Session is PAID but orderId is null. Client should retry via /convert. Error: {}",
                    paidResult.getSessionToken(), paidResult.getId(), e.getMessage(), e);
            // Re-query to return the current PAID + null orderId state
            CheckoutSessionDO session = checkoutSessionMapper.selectById(paidResult.getId());
            return CheckoutConvert.convert(session);
        }
    }

    @Override
    public CheckoutSessionVO staffConvertToOrder(Long staffUserId, String sessionToken) {
        // Delegate to existing convertToOrder — no order attribution change in this slice.
        // OrderService.createFromCheckout is not modified.
        return convertToOrder(sessionToken);
    }

    // --- G1-04C: Checkout-to-Order Conversion ---

    @Override
    public CheckoutSessionVO convertToOrder(String sessionToken) {
        CheckoutSessionDO session = findByToken(sessionToken);
        if (session == null) {
            throw new CartBusinessException(CartErrorCodeConstants.CART_NOT_FOUND,
                    "checkout session not found for token=" + sessionToken);
        }

        // Idempotent: if already linked, return existing session with orderId
        if (session.getOrderId() != null) {
            return CheckoutConvert.convert(session);
        }

        // Validate: must be PAID to convert
        if (!CheckoutStatusEnum.PAID.getCode().equals(session.getStatus())) {
            throw new CartBusinessException(CartErrorCodeConstants.CHECKOUT_DUPLICATE,
                    "checkout session is not PAID, cannot convert to order, current status=" + session.getStatus());
        }

        // Delegate to OrderService.createFromCheckout (single @Transactional boundary)
        try {
            orderService.createFromCheckout(session.getId());
        } catch (RuntimeException e) {
            // Race condition or other error — re-check if another thread linked an order
            CheckoutSessionDO reloaded = checkoutSessionMapper.selectById(session.getId());
            if (reloaded != null && reloaded.getOrderId() != null) {
                return CheckoutConvert.convert(reloaded);
            }
            throw e; // Real error, rethrow
        }

        // Re-query session to get the updated orderId
        CheckoutSessionDO updated = checkoutSessionMapper.selectById(session.getId());
        return CheckoutConvert.convert(updated);
    }

    // --- G1-04E: Scheduled expiry support ---

    @Override
    public void expireOverdueSessions() {
        Long savedTenantId = TenantContextHolder.getTenantId();
        boolean savedIgnore = TenantContextHolder.isIgnore();
        try {
            // Bypass tenant SQL filter to scan all tenants
            TenantContextHolder.clear();
            TenantContextHolder.setIgnore(true);

            List<CheckoutSessionDO> overdueSessions = checkoutSessionMapper.selectList(
                    new LambdaQueryWrapper<CheckoutSessionDO>()
                            .eq(CheckoutSessionDO::getStatus, CheckoutStatusEnum.INITIATED.getCode())
                            .lt(CheckoutSessionDO::getExpireTime, LocalDateTime.now())
                            .eq(CheckoutSessionDO::getDeleted, false));

            for (CheckoutSessionDO session : overdueSessions) {
                try {
                    TenantContextHolder.setIgnore(false);
                    TenantContextHolder.setTenantId(session.getTenantId());
                    self.expireOverdueSession(session.getId());
                    log.info("Expired overdue checkout session {} (tenant {}, expireTime {})",
                            session.getId(), session.getTenantId(), session.getExpireTime());
                } catch (Exception e) {
                    log.warn("Failed to expire checkout session {}: {}", session.getId(), e.getMessage());
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
    public void expireOverdueSession(Long sessionId) {
        CheckoutSessionDO session = checkoutSessionMapper.selectById(sessionId);
        if (session == null) {
            return; // Already deleted or not found
        }
        // Validate: only INITIATED sessions can be expired
        if (!CheckoutStatusEnum.INITIATED.getCode().equals(session.getStatus())) {
            return; // Already transitioned (PAID, EXPIRED, etc.)
        }
        // Validate: session must be overdue
        if (session.getExpireTime() == null
                || !session.getExpireTime().isBefore(LocalDateTime.now())) {
            return; // Not yet expired
        }
        // Expire with SYSTEM operator
        expireSession(session, "SYSTEM", 0L);
    }

    // --- Private helpers ---

    private CheckoutSessionDO findByToken(String sessionToken) {
        Long tenantId = TenantContextHolder.getTenantId();
        return checkoutSessionMapper.selectOne(new LambdaQueryWrapper<CheckoutSessionDO>()
                .eq(CheckoutSessionDO::getTenantId, tenantId)
                .eq(CheckoutSessionDO::getSessionToken, sessionToken)
                .eq(CheckoutSessionDO::getDeleted, false)
                .last("LIMIT 1"));
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

    private CartDO findCheckoutCart(Long customerUserId, Long shopId) {
        Long tenantId = TenantContextHolder.getTenantId();
        return cartMapper.selectOne(new LambdaQueryWrapper<CartDO>()
                .eq(CartDO::getTenantId, tenantId)
                .eq(CartDO::getCustomerUserId, customerUserId)
                .eq(CartDO::getShopId, shopId)
                .eq(CartDO::getStatus, CartStatusEnum.CHECKOUT.getCode())
                .eq(CartDO::getDeleted, false)
                .last("LIMIT 1"));
    }

    private CheckoutSessionDO findInitiatedSessionByCartId(Long cartId) {
        Long tenantId = TenantContextHolder.getTenantId();
        return checkoutSessionMapper.selectOne(new LambdaQueryWrapper<CheckoutSessionDO>()
                .eq(CheckoutSessionDO::getTenantId, tenantId)
                .eq(CheckoutSessionDO::getCartId, cartId)
                .eq(CheckoutSessionDO::getStatus, CheckoutStatusEnum.INITIATED.getCode())
                .eq(CheckoutSessionDO::getDeleted, false)
                .last("LIMIT 1"));
    }

    private void expireSession(CheckoutSessionDO session, String operatorRole, Long operatorUserId) {
        LocalDateTime now = LocalDateTime.now();
        session.setStatus(CheckoutStatusEnum.EXPIRED.getCode());
        session.setUpdater(String.valueOf(operatorUserId));
        session.setUpdateTime(now);
        checkoutSessionMapper.updateById(session);

        // G2-01B2: Release stock reservations for expired checkout session.
        // Release failure does NOT block terminal state — logged as risk.
        stockIntegrationService.releaseByCheckoutSession(
                session.getTenantId(),
                session.getId(),
                operatorUserId);

        // Unlock cart: CHECKOUT → ACTIVE
        unlockCart(session.getCartId(), operatorUserId, now);

        // Write CHECKOUT_ABANDONED event log
        CartDO cart = cartMapper.selectById(session.getCartId());
        if (cart != null) {
            writeCartEventLog(cart, CartEventTypeEnum.CHECKOUT_ABANDONED, operatorUserId, operatorRole,
                    session.getTotalAmount(), session.getTotalAmount(), now);
        }
    }

    private void unlockCart(Long cartId, Long customerUserId, LocalDateTime now) {
        CartDO cart = cartMapper.selectById(cartId);
        if (cart != null && CartStatusEnum.CHECKOUT.getCode().equals(cart.getStatus())) {
            cart.setStatus(CartStatusEnum.ACTIVE.getCode());
            cart.setLastActivityTime(now);
            cart.setUpdater(String.valueOf(customerUserId));
            cart.setUpdateTime(now);
            cartMapper.updateById(cart);
        }
    }

    private void writeCartEventLog(CartDO cart, CartEventTypeEnum eventType, Long operatorUserId,
                                   String operatorRole, BigDecimal amountBefore, BigDecimal amountAfter,
                                   LocalDateTime now) {
        CartEventLogDO eventLog = new CartEventLogDO();
        eventLog.setTenantId(cart.getTenantId());
        eventLog.setCartId(cart.getId());
        eventLog.setEventType(eventType.getCode());
        eventLog.setEventTime(now);
        eventLog.setOperatorUserId(operatorUserId);
        eventLog.setOperatorRole(operatorRole);
        eventLog.setAmountBefore(amountBefore);
        eventLog.setAmountAfter(amountAfter);
        eventLog.setCreateTime(now);
        cartEventLogMapper.insert(eventLog);
    }

    /**
     * AC-6: Server-generated unique session token.
     * Uses UUID.randomUUID() — never client-provided, never static/mock/debug.
     */
    private String generateSessionToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
