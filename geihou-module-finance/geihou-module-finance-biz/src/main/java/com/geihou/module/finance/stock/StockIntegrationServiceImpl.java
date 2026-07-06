package com.geihou.module.finance.stock;

import com.geihou.module.finance.api.product.ProductApi;
import com.geihou.module.finance.api.product.dto.SkuRespDTO;
import com.geihou.module.finance.cart.dal.dataobject.CartItemDO;
import com.geihou.module.finance.cart.dal.mapper.CartItemMapper;
import com.geihou.module.finance.checkout.dal.dataobject.CheckoutSessionDO;
import com.geihou.module.finance.checkout.dal.mapper.CheckoutSessionMapper;
import com.geihou.module.supplychain.api.bom.BomApi;
import com.geihou.module.supplychain.api.bom.dto.BomRecipeRespDTO;
import com.geihou.module.supplychain.api.stock.StockApi;
import com.geihou.module.supplychain.api.stock.StockCoverageApi;
import com.geihou.module.supplychain.api.stock.StockEventApi;
import com.geihou.module.supplychain.api.stock.StockQueryApi;
import com.geihou.module.supplychain.api.stock.dto.SalesOutBomReverseReqDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesOutBomReverseRespDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesReverseRestoreReqDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesReverseRestoreRespDTO;
import com.geihou.module.supplychain.api.stock.dto.SkuQuantityDTO;
import com.geihou.module.supplychain.api.stock.dto.StockCheckRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockCoverageDecisionRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockCoverageObserveReqDTO;
import com.geihou.module.supplychain.api.stock.dto.StockCommitReqDTO;
import com.geihou.module.supplychain.api.stock.dto.StockItemQueryRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockLocationQueryRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockReleaseReqDTO;
import com.geihou.module.supplychain.api.stock.dto.StockReserveReqDTO;
import com.geihou.module.supplychain.api.stock.enums.StockCoverageTypeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of {@link StockIntegrationService}.
 *
 * <p>Depends only on supplychain-api contracts (StockEventApi + StockQueryApi + BomApi + StockApi) and
 * finance-internal mappers (CheckoutSessionMapper, CartItemMapper) + ProductApi.
 * Does NOT depend on supplychain-biz services. Does NOT query supplychain tables directly.
 *
 * <p>CG-8 Boundary:
 * - Mapped SKUs (stock_item + stock_location configured): reserve/release/commit active.
 * - Unmapped SKUs: SKIP (log.info), no stock operations.
 *
 * <p>G2-02H-3 active-BOM wiring:
 * - Reserve: active-BOM SKUs skip finished-SKU reserve and use StockApi.checkStock preflight (fail closed).
 * - Commit: active-BOM SKUs skip finished-SKU commitStock and call StockApi.salesOutWithBomReverse.
 * - Refund restore: calls StockApi.salesReverseRestore; no-op when no original CONSUME_OUT exists.
 *
 * <p>Idempotent key pattern: checkout-{sessionId}-sku-{skuId}
 *
 * <p>Source: TASK-G2-01B2 Section 10, TASK-G2-02H-3.
 */
@Service
public class StockIntegrationServiceImpl implements StockIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(StockIntegrationServiceImpl.class);

    private static final String SOURCE_MODULE = "checkout";
    private static final String LOCATION_TYPE_STORE = "STORE";
    private static final String COVERAGE_VIOLATION_CODE = "STOCK_MAPPING_COVERAGE_VIOLATION";

    private final StockEventApi stockEventApi;
    private final StockCoverageApi stockCoverageApi;
    private final StockQueryApi stockQueryApi;
    private final StockApi stockApi;
    private final BomApi bomApi;
    private final ProductApi productApi;
    private final CheckoutSessionMapper checkoutSessionMapper;
    private final CartItemMapper cartItemMapper;

    public StockIntegrationServiceImpl(StockEventApi stockEventApi,
                                        StockCoverageApi stockCoverageApi,
                                        StockQueryApi stockQueryApi,
                                        StockApi stockApi,
                                        BomApi bomApi,
                                        ProductApi productApi,
                                        CheckoutSessionMapper checkoutSessionMapper,
                                        CartItemMapper cartItemMapper) {
        this.stockEventApi = stockEventApi;
        this.stockCoverageApi = stockCoverageApi;
        this.stockQueryApi = stockQueryApi;
        this.stockApi = stockApi;
        this.bomApi = bomApi;
        this.productApi = productApi;
        this.checkoutSessionMapper = checkoutSessionMapper;
        this.cartItemMapper = cartItemMapper;
    }

    @Override
    public void reserveForCheckout(Long tenantId, Long sessionId, Long cartId,
                                    Long shopId, Long operatorUserId) {
        // 1. Query stock_location by shopId
        StockLocationQueryRespDTO location = stockQueryApi.getStockLocationByStoreId(
                tenantId, shopId, LOCATION_TYPE_STORE);
        if (location == null) {
            observeCoverage(tenantId, sessionId, null, null, shopId,
                    LOCATION_TYPE_STORE, StockCoverageTypeEnum.LOCATION_MISSING, "checkout-" + sessionId + "-location");
            log.info("skip stock reserve for sessionId={}, no stock_location mapping for shopId={}",
                    sessionId, shopId);
            return; // No location mapping → SKIP all SKUs
        }

        // 2. Query cart items
        List<CartItemDO> cartItems = cartItemMapper.selectList(
                CartItemDO::getCartId, cartId,
                CartItemDO::getTenantId, tenantId);
        if (cartItems == null || cartItems.isEmpty()) {
            return;
        }

        // 3. Batch get SKU codes via ProductApi
        Set<Long> skuIds = cartItems.stream().map(CartItemDO::getSkuId).collect(Collectors.toSet());
        Map<Long, SkuRespDTO> skuMap = productApi.batchGetSkus(new ArrayList<>(skuIds));

        // 4. For each cart item, attempt reserve
        for (CartItemDO cartItem : cartItems) {
            SkuRespDTO sku = skuMap.get(cartItem.getSkuId());
            if (sku == null || sku.getSkuCode() == null) {
                observeCoverage(tenantId, sessionId, cartItem.getSkuId(), null, shopId,
                        LOCATION_TYPE_STORE, StockCoverageTypeEnum.SKU_CODE_MISSING,
                        buildIdempotentKey(sessionId, cartItem.getSkuId()));
                log.warn("skip stock reserve for sessionId={}, skuId={}, skuCode not found",
                        sessionId, cartItem.getSkuId());
                continue;
            }

            // 4a. Active-BOM detection — fail closed on RPC errors.
            BomRecipeRespDTO activeBom;
            try {
                activeBom = bomApi.getActiveRecipeBySkuCode(tenantId, sku.getSkuCode());
            } catch (Exception e) {
                log.error("BomApi.getActiveRecipeBySkuCode threw for tenantId={}, skuCode={}, failing closed",
                        tenantId, sku.getSkuCode(), e);
                throw new BomLookupFailureException(
                        "BOM lookup failed for skuCode=" + sku.getSkuCode() + ": " + e.getMessage(), e);
            }

            if (isActiveBom(activeBom)) {
                // Active-BOM finished SKU: do NOT reserve finished SKU.
                // Use StockApi.checkStock as a read-only BOM-aware sufficiency preflight; fail closed.
                BigDecimal quantity = new BigDecimal(cartItem.getQuantity());
                SkuQuantityDTO checkItem = new SkuQuantityDTO();
                checkItem.setSkuCode(sku.getSkuCode());
                checkItem.setQuantity(quantity);

                StockCheckRespDTO checkResp;
                try {
                    checkResp = stockApi.checkStock(tenantId, List.of(checkItem));
                } catch (Exception e) {
                    log.error("StockApi.checkStock threw for tenantId={}, skuCode={}, failing closed",
                            tenantId, sku.getSkuCode(), e);
                    throw new BomLookupFailureException(
                            "BOM stock preflight failed for skuCode=" + sku.getSkuCode()
                                    + ": " + e.getMessage(), e);
                }

                if (checkResp == null || !checkResp.isAllSufficient()) {
                    throw new BomStockInsufficientException(
                            "active-BOM stock preflight insufficient for skuCode=" + sku.getSkuCode()
                                    + ", sessionId=" + sessionId);
                }
                log.info("active-BOM skuCode={} passed checkStock preflight, skip finished reserve (sessionId={})",
                        sku.getSkuCode(), sessionId);
                continue;
            }

            // 5. Query stock_item by skuCode (non-BOM path)
            StockItemQueryRespDTO stockItem = stockQueryApi.getStockItemBySkuCode(tenantId, sku.getSkuCode());
            if (stockItem == null) {
                observeCoverage(tenantId, sessionId, cartItem.getSkuId(), sku.getSkuCode(), shopId,
                        LOCATION_TYPE_STORE, StockCoverageTypeEnum.STOCK_ITEM_MISSING,
                        buildIdempotentKey(sessionId, cartItem.getSkuId()));
                log.info("skip stock reserve for sessionId={}, skuCode={}, no stock_item mapping",
                        sessionId, sku.getSkuCode());
                continue; // SKIP — unmapped SKU
            }

            // 6. Build reserve request
            BigDecimal quantity = new BigDecimal(cartItem.getQuantity());
            String idempotentKey = buildIdempotentKey(sessionId, cartItem.getSkuId());

            StockReserveReqDTO req = new StockReserveReqDTO();
            req.setTenantId(tenantId);
            req.setStockItemId(stockItem.getId());
            req.setLocationId(location.getId());
            req.setSkuCode(sku.getSkuCode());
            req.setQuantity(quantity);
            req.setUnit(stockItem.getUnit() != null ? stockItem.getUnit() : "份");
            req.setSourceModule(SOURCE_MODULE);
            req.setSourceRecordId(sessionId);
            req.setReferenceNo(null);
            req.setIdempotentKey(idempotentKey);
            req.setOperatorUserId(operatorUserId);

            // 7. Call reserve — if fails, exception propagates and rolls back checkout transaction
            stockEventApi.reserveStock(req);
            log.info("reserved stock for sessionId={}, skuCode={}, qty={}, reserveId returned",
                    sessionId, sku.getSkuCode(), quantity);
        }
    }

    @Override
    public void releaseByCheckoutSession(Long tenantId, Long sessionId, Long operatorUserId) {
        // 1. Get cartId from checkout_session
        List<CartItemDO> cartItems = getCartItemsForSession(tenantId, sessionId);
        if (cartItems == null || cartItems.isEmpty()) {
            return;
        }

        // 2. Batch get SKU codes
        Set<Long> skuIds = cartItems.stream().map(CartItemDO::getSkuId).collect(Collectors.toSet());
        Map<Long, SkuRespDTO> skuMap = productApi.batchGetSkus(new ArrayList<>(skuIds));

        // 3. For each cart item, attempt release
        for (CartItemDO cartItem : cartItems) {
            SkuRespDTO sku = skuMap.get(cartItem.getSkuId());
            if (sku == null || sku.getSkuCode() == null) {
                continue;
            }

            // Active-BOM SKUs had no finished-SKU reserve to release — skip silently.
            BomRecipeRespDTO activeBom;
            try {
                activeBom = bomApi.getActiveRecipeBySkuCode(tenantId, sku.getSkuCode());
            } catch (Exception e) {
                // Fail closed on release too — but release is best-effort (logged, not propagated).
                log.warn("BomApi.getActiveRecipeBySkuCode threw during release for skuCode={}, skipping release: {}",
                        sku.getSkuCode(), e.getMessage(), e);
                continue;
            }
            if (isActiveBom(activeBom)) {
                log.info("skip release for active-BOM skuCode={}, no finished reserve to release (sessionId={})",
                        sku.getSkuCode(), sessionId);
                continue;
            }

            // Check if stock_item mapping exists — if not, SKIP (no reserve was made)
            StockItemQueryRespDTO stockItem = stockQueryApi.getStockItemBySkuCode(tenantId, sku.getSkuCode());
            if (stockItem == null) {
                continue; // SKIP — unmapped SKU, no reserve to release
            }

            String idempotentKey = buildIdempotentKey(sessionId, cartItem.getSkuId());

            StockReleaseReqDTO req = new StockReleaseReqDTO();
            req.setTenantId(tenantId);
            req.setReserveId(null); // idempotentKey is primary lookup
            req.setIdempotentKey(idempotentKey);
            req.setOperatorUserId(operatorUserId);

            try {
                stockEventApi.releaseStock(req);
                log.info("released stock for sessionId={}, skuCode={}", sessionId, sku.getSkuCode());
            } catch (Exception e) {
                // Release failure does NOT block terminal state — log risk
                log.warn("release failed for sessionId={}, idempotentKey={}, risk of stuck reservation: {}",
                        sessionId, idempotentKey, e.getMessage(), e);
            }
        }
    }

    @Override
    public void commitByCheckoutSession(Long tenantId, Long sessionId, Long orderId, Long operatorUserId,
                                         LocalDate businessDate, String referenceNo,
                                         Map<Long, Long> sourceOrderItemIdByCartItemId) {
        // 1. Get cart items
        List<CartItemDO> cartItems = getCartItemsForSession(tenantId, sessionId);
        if (cartItems == null || cartItems.isEmpty()) {
            return;
        }

        // 2. Resolve stock_location from the checkout session shopId — used by BOM reverse.
        CheckoutSessionDO session = checkoutSessionMapper.selectById(sessionId);
        StockLocationQueryRespDTO bomLocation = null;
        if (session != null && session.getShopId() != null) {
            bomLocation = stockQueryApi.getStockLocationByStoreId(
                    tenantId, session.getShopId(), LOCATION_TYPE_STORE);
        }
        // If no location exists, BOM reverse path will fail closed below before making the call.

        // 3. Batch get SKU codes
        Set<Long> skuIds = cartItems.stream().map(CartItemDO::getSkuId).collect(Collectors.toSet());
        Map<Long, SkuRespDTO> skuMap = productApi.batchGetSkus(new ArrayList<>(skuIds));

        // 4. For each cart item, attempt commit
        for (CartItemDO cartItem : cartItems) {
            SkuRespDTO sku = skuMap.get(cartItem.getSkuId());
            if (sku == null || sku.getSkuCode() == null) {
                continue;
            }

            // 4a. Active-BOM detection — fail closed on RPC errors.
            BomRecipeRespDTO activeBom;
            try {
                activeBom = bomApi.getActiveRecipeBySkuCode(tenantId, sku.getSkuCode());
            } catch (Exception e) {
                log.error("BomApi.getActiveRecipeBySkuCode threw during commit for tenantId={}, skuCode={}, failing closed",
                        tenantId, sku.getSkuCode(), e);
                throw new BomLookupFailureException(
                        "BOM lookup failed during commit for skuCode=" + sku.getSkuCode()
                                + ": " + e.getMessage(), e);
            }

            if (isActiveBom(activeBom)) {
                // Active-BOM finished SKU: do NOT call finished-SKU commitStock.
                // Call StockApi.salesOutWithBomReverse to deduct raw materials.
                if (bomLocation == null) {
                    throw new BomLocationMissingException(
                            "active-BOM commit requires stock_location for shopId="
                                    + (session != null ? session.getShopId() : null)
                                    + ", sessionId=" + sessionId + ", skuCode=" + sku.getSkuCode());
                }

                Long sourceOrderItemId = sourceOrderItemIdByCartItemId != null
                        ? sourceOrderItemIdByCartItemId.get(cartItem.getId()) : null;
                if (sourceOrderItemId == null) {
                    throw new OrderItemTraceMissingException(
                            "active-BOM commit requires order item trace mapping for cartItemId="
                                    + cartItem.getId() + ", sessionId=" + sessionId
                                    + ", orderId=" + orderId + ", skuCode=" + sku.getSkuCode());
                }

                BigDecimal quantity = new BigDecimal(cartItem.getQuantity());
                String clientRequestId = buildBomReverseClientRequestId(sourceOrderItemId, cartItem.getSkuId());

                SalesOutBomReverseReqDTO req = new SalesOutBomReverseReqDTO();
                req.setTenantId(tenantId);
                req.setProductId(activeBom.getProductId());
                req.setSkuCode(sku.getSkuCode());
                req.setQuantity(quantity);
                req.setLocationId(bomLocation.getId());
                req.setSourceModule(SOURCE_MODULE);
                req.setSourceRecordId(orderId);
                req.setSourceOrderItemId(sourceOrderItemId);
                req.setReferenceNo(referenceNo);
                req.setOperatorUserId(operatorUserId);
                req.setClientRequestId(clientRequestId);

                SalesOutBomReverseRespDTO resp = stockApi.salesOutWithBomReverse(req);
                log.info("BOM reverse sales-out committed for orderId={}, sessionId={}, skuCode={}, qty={}, items={}",
                        orderId, sessionId, sku.getSkuCode(), quantity,
                        resp != null && resp.getItems() != null ? resp.getItems().size() : 0);
                continue;
            }

            // 5. Non-BOM path: check if stock_item mapping exists — if not, SKIP
            StockItemQueryRespDTO stockItem = stockQueryApi.getStockItemBySkuCode(tenantId, sku.getSkuCode());
            if (stockItem == null) {
                continue; // SKIP — unmapped SKU, no reserve to commit
            }

            String idempotentKey = buildIdempotentKey(sessionId, cartItem.getSkuId());

            StockCommitReqDTO req = new StockCommitReqDTO();
            req.setTenantId(tenantId);
            req.setReserveId(null); // idempotentKey is primary lookup
            req.setIdempotentKey(idempotentKey);
            req.setOperatorUserId(operatorUserId);
            req.setEventTime(LocalDateTime.now());
            req.setBusinessDate(businessDate);
            req.setReferenceNo(referenceNo);

            // Commit failure throws exception → rolls back createFromCheckout transaction
            stockEventApi.commitStock(req);
            log.info("committed stock for sessionId={}, skuCode={}, referenceNo={}",
                    sessionId, sku.getSkuCode(), referenceNo);
        }
    }

    @Override
    public void restoreForRefund(Long tenantId, Long orderId, String orderNo,
                                 Long refundId, Long operatorUserId) {
        restoreForRefund(tenantId, orderId, orderNo, refundId, operatorUserId, null);
    }

    @Override
    public void restoreForRefund(Long tenantId, Long orderId, String orderNo,
                                 Long refundId, Long operatorUserId,
                                 List<Long> sourceOrderItemIds) {
        // Deterministic idempotency: same refund/order retry produces the same clientRequestId,
        // so supplychain deduplicates and does not create duplicate restore events.
        String clientRequestId = buildBomRestoreClientRequestId(refundId, orderId);

        SalesReverseRestoreReqDTO req = new SalesReverseRestoreReqDTO();
        req.setTenantId(tenantId);
        req.setSourceModule(SOURCE_MODULE);
        req.setSourceRecordId(orderId);
        req.setReferenceNo(orderNo);
        req.setOperatorUserId(operatorUserId);
        req.setClientRequestId(clientRequestId);
        boolean lineScopedRestore = hasSourceOrderItemScope(sourceOrderItemIds);
        if (lineScopedRestore) {
            req.setSourceOrderItemIds(sourceOrderItemIds);
        }

        SalesReverseRestoreRespDTO resp;
        try {
            resp = stockApi.salesReverseRestore(req);
        } catch (RuntimeException e) {
            // Supplychain reports "no original CONSUME_OUT events found for sourceRecordId=..."
            // when the original sale was non-BOM (no BOM reverse was ever recorded for that source).
            // The StockBusinessException type lives in supplychain-biz and cannot be referenced from
            // finance, so detect the contract by message substring. This is the only swallowed case;
            // all other conflicts (e.g. client_request_id conflict from a concurrent restore) propagate.
            if (isNoOriginalConsumeOutEvents(e)) {
                if (lineScopedRestore) {
                    log.error("line-scoped refund restore found no original CONSUME_OUT events for orderId={}, refundId={}, rethrowing",
                            orderId, refundId, e);
                    throw e;
                }
                log.info("refund restore no-op for orderId={}, refundId={}, reason: no original BOM CONSUME_OUT events",
                        orderId, refundId);
                return;
            }
            // Real conflict / failure — do not swallow.
            log.error("refund restore failed for orderId={}, refundId={}, rethrowing: {}",
                    orderId, refundId, e.getMessage(), e);
            throw e;
        }
        int restored = resp != null && resp.getItems() != null ? resp.getItems().size() : 0;
        if (restored == 0) {
            if (lineScopedRestore) {
                throw new BomRestoreFailureException(
                        "line-scoped refund restore returned 0 items for orderId=" + orderId
                                + ", refundId=" + refundId);
            }
            // Empty restore response also indicates a non-BOM order (no original events found).
            log.info("refund restore returned 0 items for orderId={}, refundId={} (non-BOM order no-op)",
                    orderId, refundId);
            return;
        }
        log.info("refund restore completed for orderId={}, refundId={}, restoredItems={}",
                orderId, refundId, restored);
    }

    /**
     * Detects the supplychain "no original CONSUME_OUT events found" signal across the API
     * boundary without depending on the supplychain-biz exception type.
     */
    private boolean isNoOriginalConsumeOutEvents(RuntimeException e) {
        if (e == null) {
            return false;
        }
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        return message.contains("no original CONSUME_OUT events found");
    }

    private boolean hasSourceOrderItemScope(List<Long> sourceOrderItemIds) {
        return sourceOrderItemIds != null && !sourceOrderItemIds.isEmpty();
    }

    // --- Private helpers ---

    private boolean isActiveBom(BomRecipeRespDTO dto) {
        return dto != null && dto.getId() != null;
    }

    private List<CartItemDO> getCartItemsForSession(Long tenantId, Long sessionId) {
        CheckoutSessionDO session = checkoutSessionMapper.selectById(sessionId);
        if (session == null || !session.getTenantId().equals(tenantId)) {
            return Collections.emptyList();
        }
        return cartItemMapper.selectList(
                CartItemDO::getCartId, session.getCartId(),
                CartItemDO::getTenantId, tenantId);
    }

    private String buildIdempotentKey(Long sessionId, Long skuId) {
        return "checkout-" + sessionId + "-sku-" + skuId;
    }

    private String buildBomReverseClientRequestId(Long sourceOrderItemId, Long skuId) {
        return "checkout-bom-oi-" + sourceOrderItemId + "-sku-" + skuId;
    }

    private String buildBomRestoreClientRequestId(Long refundId, Long orderId) {
        return "refund-" + refundId + "-order-" + orderId + "-bom-restore";
    }

    private void observeCoverage(Long tenantId, Long sessionId, Long skuId, String skuCode,
                                 Long storeId, String locationType, StockCoverageTypeEnum coverageType,
                                 String idempotentKey) {
        StockCoverageObserveReqDTO req = new StockCoverageObserveReqDTO();
        req.setTenantId(tenantId);
        req.setCoverageType(coverageType);
        req.setSkuId(skuId);
        req.setSkuCode(skuCode);
        req.setStoreId(storeId);
        req.setLocationType(locationType);
        req.setSourceModule(SOURCE_MODULE);
        req.setSourceRecordId(sessionId);
        req.setIdempotentKey(idempotentKey);

        StockCoverageDecisionRespDTO decision = stockCoverageApi.observeMissingMapping(req);
        if (decision != null && decision.isEnforce()) {
            throw new StockMappingCoverageViolationException(COVERAGE_VIOLATION_CODE + ": " + coverageType.getCode());
        }
    }

    public static class StockMappingCoverageViolationException extends RuntimeException {
        public StockMappingCoverageViolationException(String message) {
            super(message);
        }
    }

    /** Fail-closed signal for BOM lookup / preflight failures during reserve/commit. */
    public static class BomLookupFailureException extends RuntimeException {
        public BomLookupFailureException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Fail-closed signal when active-BOM stock preflight reports insufficiency. */
    public static class BomStockInsufficientException extends RuntimeException {
        public BomStockInsufficientException(String message) {
            super(message);
        }
    }

    /** Fail-closed signal when active-BOM commit cannot resolve a stock_location. */
    public static class BomLocationMissingException extends RuntimeException {
        public BomLocationMissingException(String message) {
            super(message);
        }
    }

    public static class OrderItemTraceMissingException extends RuntimeException {
        public OrderItemTraceMissingException(String message) {
            super(message);
        }
    }

    public static class BomRestoreFailureException extends RuntimeException {
        public BomRestoreFailureException(String message) {
            super(message);
        }
    }

}
