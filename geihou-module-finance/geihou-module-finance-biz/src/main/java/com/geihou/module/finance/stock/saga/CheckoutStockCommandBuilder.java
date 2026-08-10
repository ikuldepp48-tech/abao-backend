package com.geihou.module.finance.stock.saga;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.geihou.module.finance.cart.dal.dataobject.CartItemDO;
import com.geihou.module.finance.product.enums.StockStrategyEnum;
import com.geihou.module.finance.stock.plan.dal.dataobject.CheckoutCartItemPlanDO;
import com.geihou.module.finance.stock.plan.enums.CheckoutStockClassification;
import com.geihou.module.finance.stock.saga.enums.FinanceStockSagaType;
import com.geihou.module.finance.stock.saga.enums.FinanceStockTransportMode;
import com.geihou.module.supplychain.api.stock.dto.StockItemQueryRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockReserveReqDTO;
import com.geihou.module.supplychain.api.stock.enums.SupplychainCommandOperationEnum;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Builds the immutable {@link FinanceStockCommandCreate} for a checkout
 * RESERVE command from the frozen cart-item plan and live stock lookup
 * (G0-04H185 FIN-CONSISTENCY slice 2C-2D).
 *
 * <p>Only {@code NON_BOM + TRACK_STOCK} cart items produce a RESERVE
 * command. {@code BOM} (handled by SALES_OUT_BOM_REVERSE),
 * {@code UNMAPPED} (OBSERVE_MISSING_MAPPING) and {@code NON_BOM+UNLIMITED}
 * (no stock reservation) are rejected before any serialization.
 *
 * <p>Frozen command identity produced by {@link #build}:
 * <ul>
 *   <li>{@code sagaType = CHECKOUT}, {@code sagaId = checkoutSessionId}</li>
 *   <li>{@code stepKey = cartItemId} (String)</li>
 *   <li>{@code parentCommandId = null}</li>
 *   <li>{@code operation = SupplychainCommandOperationEnum.RESERVE.getCode()}</li>
 *   <li>{@code businessCommandId = "checkout-{checkoutSessionId}-item-{cartItemId}"}</li>
 *   <li>{@code requestSchemaVersion = 1}</li>
 *   <li>{@code requestBody} = UTF-8 JSON bytes of the frozen
 *       {@link StockReserveReqDTO} (nulls preserved, e.g. {@code referenceNo:null},
 *       map entries NOT sorted), {@code requestBodySha256} = SHA-256 of those
 *       exact bytes.</li>
 * </ul>
 *
 * <p>Validation is fail-closed: any invalid input (non-positive ids,
 * blank {@code skuCode}, missing {@code stockItemId}/{@code locationId},
 * classification/strategy mismatch, {@code LOCAL_API_V1 + c0=true},
 * serialization failure) throws before a half-built command is returned.
 *
 * <p>This is a plain stateless class (not a Spring bean). Tests use
 * {@code new CheckoutStockCommandBuilder().build(...)}.
 */
public class CheckoutStockCommandBuilder {

    private static final int REQUEST_SCHEMA_VERSION = 1;
    private static final String SOURCE_MODULE = "checkout";
    private static final String SHA_256 = "SHA-256";
    private static final ObjectMapper DEFAULT_OBJECT_MAPPER = defaultObjectMapper();

    private final ObjectMapper objectMapper;

    public CheckoutStockCommandBuilder() {
        this(DEFAULT_OBJECT_MAPPER);
    }

    /**
     * Package-private for tests that inject a failing {@link ObjectMapper}
     * to prove serialization failure is fail-closed.
     */
    CheckoutStockCommandBuilder(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * Build the durable RESERVE command for one NON_BOM+TRACK_STOCK cart item.
     *
     * @param cartItem              the cart item (quantity, sku identity)
     * @param plan                  the frozen classification plan
     * @param queryResult           live stock-item lookup (unit, sku mapping)
     * @param checkoutSessionId     the checkout session id (also the sagaId)
     * @param operatorUserId        operator (customer or staff), must be > 0
     * @param transportMode         publish fence (LOCAL_API_V1 / HMAC_RPC_V1)
     * @param c0JournalAvailable    T_c0 gate; forbidden with LOCAL_API_V1
     * @param maxDispatchAttempts   dispatch retry cap, must be > 0
     * @param maxResolutionAttempts UNKNOWN resolution cap, must be > 0
     * @return a fully frozen {@link FinanceStockCommandCreate}
     */
    public FinanceStockCommandCreate build(
            CartItemDO cartItem,
            CheckoutCartItemPlanDO plan,
            StockItemQueryRespDTO queryResult,
            long checkoutSessionId,
            long operatorUserId,
            FinanceStockTransportMode transportMode,
            boolean c0JournalAvailable,
            int maxDispatchAttempts,
            int maxResolutionAttempts) {
        validateInputs(cartItem, plan, queryResult, checkoutSessionId, operatorUserId,
                transportMode, c0JournalAvailable, maxDispatchAttempts,
                maxResolutionAttempts);

        String businessCommandId = businessCommandId(checkoutSessionId, cartItem.getId());
        StockReserveReqDTO reserveReq = buildReserveRequest(
                cartItem, plan, queryResult, checkoutSessionId, operatorUserId);
        byte[] body = serialize(reserveReq);

        return new FinanceStockCommandCreate(
                plan.getTenantId(),
                FinanceStockSagaType.CHECKOUT,
                checkoutSessionId,
                String.valueOf(cartItem.getId()),
                null,
                SupplychainCommandOperationEnum.RESERVE.getCode(),
                businessCommandId,
                transportMode,
                c0JournalAvailable,
                REQUEST_SCHEMA_VERSION,
                body,
                sha256Hex(body),
                maxDispatchAttempts,
                maxResolutionAttempts
        );
    }

    // ==================== Validation ====================

    private static void validateInputs(CartItemDO cartItem, CheckoutCartItemPlanDO plan,
                                       StockItemQueryRespDTO queryResult,
                                       long checkoutSessionId, long operatorUserId,
                                       FinanceStockTransportMode transportMode,
                                       boolean c0JournalAvailable,
                                       int maxDispatchAttempts, int maxResolutionAttempts) {
        if (cartItem == null) {
            throw new IllegalArgumentException("cartItem must not be null");
        }
        if (plan == null) {
            throw new IllegalArgumentException("plan must not be null");
        }
        if (queryResult == null) {
            throw new IllegalArgumentException("queryResult must not be null");
        }
        if (transportMode == null) {
            throw new IllegalArgumentException("transportMode must not be null");
        }
        if (cartItem.getId() == null || cartItem.getId() <= 0) {
            throw new IllegalArgumentException("cartItem.id must be positive");
        }
        if (cartItem.getTenantId() == null || cartItem.getTenantId() <= 0) {
            throw new IllegalArgumentException("cartItem.tenantId must be positive");
        }
        if (cartItem.getSkuId() == null || cartItem.getSkuId() <= 0) {
            throw new IllegalArgumentException("cartItem.skuId must be positive");
        }
        if (cartItem.getQuantity() == null || cartItem.getQuantity() <= 0) {
            throw new IllegalArgumentException("cartItem.quantity must be positive");
        }
        if (checkoutSessionId <= 0) {
            throw new IllegalArgumentException("checkoutSessionId must be positive");
        }
        if (operatorUserId <= 0) {
            throw new IllegalArgumentException("operatorUserId must be positive");
        }
        if (maxDispatchAttempts <= 0) {
            throw new IllegalArgumentException("maxDispatchAttempts must be positive");
        }
        if (maxResolutionAttempts <= 0) {
            throw new IllegalArgumentException("maxResolutionAttempts must be positive");
        }
        if (transportMode == FinanceStockTransportMode.LOCAL_API_V1 && c0JournalAvailable) {
            throw new IllegalStateException(
                    "LOCAL_API_V1 + c0_journal_available=true is forbidden");
        }

        // Only NON_BOM + TRACK_STOCK produces a RESERVE command
        if (plan.getSkuCode() == null || plan.getSkuCode().isBlank()) {
            throw new IllegalArgumentException("plan.skuCode must not be null or blank");
        }
        CheckoutStockClassification classification;
        try {
            classification = CheckoutStockClassification.valueOf(plan.getClassification());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException(
                    "plan.classification must be a valid CheckoutStockClassification, got: "
                            + plan.getClassification(), e);
        }
        if (classification != CheckoutStockClassification.NON_BOM) {
            throw new IllegalStateException(
                    "RESERVE requires NON_BOM classification, got: " + classification);
        }
        if (!StockStrategyEnum.TRACK_STOCK.getCode().equals(plan.getStockStrategy())) {
            throw new IllegalStateException(
                    "RESERVE requires NON_BOM+TRACK_STOCK, got stockStrategy: "
                            + plan.getStockStrategy());
        }
        if (plan.getStockItemId() == null || plan.getStockItemId() <= 0) {
            throw new IllegalStateException(
                    "RESERVE requires a positive stockItemId for NON_BOM+TRACK_STOCK");
        }
        if (plan.getLocationId() == null || plan.getLocationId() <= 0) {
            throw new IllegalStateException(
                    "RESERVE requires a positive locationId for NON_BOM+TRACK_STOCK");
        }

        // plan <-> cartItem consistency
        requireEquals(plan.getTenantId(), cartItem.getTenantId(), "plan.tenantId", "cartItem.tenantId");
        requireEquals(plan.getCheckoutSessionId(), checkoutSessionId,
                "plan.checkoutSessionId", "checkoutSessionId");
        requireEquals(plan.getCartItemId(), cartItem.getId(), "plan.cartItemId", "cartItem.id");
        requireEquals(plan.getSkuId(), cartItem.getSkuId(), "plan.skuId", "cartItem.skuId");

        // queryResult <-> plan consistency
        if (!ObjectsEqual(plan.getSkuCode(), queryResult.getSkuCode())) {
            throw new IllegalArgumentException(
                    "plan.skuCode mismatch with queryResult.skuCode: plan="
                            + plan.getSkuCode() + ", query=" + queryResult.getSkuCode());
        }
        requireEquals(plan.getTenantId(), queryResult.getTenantId(), "plan.tenantId", "queryResult.tenantId");
        requireEquals(plan.getStockItemId(), queryResult.getId(), "plan.stockItemId", "queryResult.id");
        if (queryResult.getUnit() == null || queryResult.getUnit().isBlank()) {
            throw new IllegalArgumentException("queryResult.unit must not be null or blank");
        }
    }

    // ==================== Request assembly ====================

    private static StockReserveReqDTO buildReserveRequest(CartItemDO cartItem,
                                                          CheckoutCartItemPlanDO plan,
                                                          StockItemQueryRespDTO queryResult,
                                                          long checkoutSessionId,
                                                          long operatorUserId) {
        String businessCommandId = businessCommandId(checkoutSessionId, cartItem.getId());
        StockReserveReqDTO req = new StockReserveReqDTO();
        req.setTenantId(plan.getTenantId());
        req.setStockItemId(plan.getStockItemId());
        req.setLocationId(plan.getLocationId());
        req.setSkuCode(plan.getSkuCode());
        req.setQuantity(BigDecimal.valueOf(cartItem.getQuantity().longValue()));
        req.setUnit(queryResult.getUnit());
        req.setSourceModule(SOURCE_MODULE);
        req.setSourceRecordId(checkoutSessionId);
        req.setReferenceNo(null);
        req.setIdempotentKey(businessCommandId);
        req.setOperatorUserId(operatorUserId);
        return req;
    }

    private static String businessCommandId(long checkoutSessionId, long cartItemId) {
        return "checkout-" + checkoutSessionId + "-item-" + cartItemId;
    }

    // ==================== Serialization (fail-closed) ====================

    private byte[] serialize(StockReserveReqDTO req) {
        try {
            return objectMapper.writeValueAsBytes(req);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to serialize RESERVE request body", e);
        }
    }

    private static ObjectMapper defaultObjectMapper() {
        ObjectMapper om = new ObjectMapper();
        om.setSerializationInclusion(JsonInclude.Include.ALWAYS);
        om.disable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        return om;
    }

    private static String sha256Hex(byte[] body) {
        try {
            MessageDigest md = MessageDigest.getInstance(SHA_256);
            return HexFormat.of().formatHex(md.digest(body));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // ==================== Small helpers ====================

    private static void requireEquals(Long existing, Long provided, String existingName, String providedName) {
        if (!ObjectsEqual(existing, provided)) {
            throw new IllegalArgumentException(
                    existingName + "=" + existing + " mismatch with " + providedName + "=" + provided);
        }
    }

    private static boolean ObjectsEqual(Object a, Object b) {
        return java.util.Objects.equals(a, b);
    }
}
