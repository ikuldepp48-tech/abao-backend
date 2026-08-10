package com.geihou.module.finance.stock.saga;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geihou.module.finance.cart.dal.dataobject.CartItemDO;
import com.geihou.module.finance.product.enums.StockStrategyEnum;
import com.geihou.module.finance.stock.plan.dal.dataobject.CheckoutCartItemPlanDO;
import com.geihou.module.finance.stock.plan.enums.CheckoutStockClassification;
import com.geihou.module.finance.stock.saga.enums.FinanceStockSagaType;
import com.geihou.module.finance.stock.saga.enums.FinanceStockTransportMode;
import com.geihou.module.supplychain.api.stock.dto.StockItemQueryRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockReserveReqDTO;
import com.geihou.module.supplychain.api.stock.enums.SupplychainCommandOperationEnum;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CheckoutStockCommandBuilder} (G0-04H185
 * FIN-CONSISTENCY slice 2C-2D, RESERVE command construction).
 *
 * <p>Verifies the frozen command identity, the exact UTF-8 JSON body
 * (nulls preserved, e.g. {@code referenceNo:null}, map entries NOT sorted),
 * the SHA-256 of the exact bytes, and the fail-closed validation surface
 * including an injected serialization failure.
 */
class CheckoutStockCommandBuilderTest {

    private static final long TENANT = 1L;
    private static final long SESSION_ID = 500L;
    private static final long CART_ITEM_ID = 700L;
    private static final long OPERATOR = 9001L;

    private final CheckoutStockCommandBuilder builder = new CheckoutStockCommandBuilder();

    // ==================== Happy path ====================

    @Test
    void build_frozenCommandIdentity() throws Exception {
        FinanceStockCommandCreate command = builder.build(
                cartItem(), plan(), queryResult(), SESSION_ID,
                OPERATOR,
                FinanceStockTransportMode.HMAC_RPC_V1,
                true,
                3, 5);

        assertThat(command.tenantId()).isEqualTo(TENANT);
        assertThat(command.sagaType()).isEqualTo(FinanceStockSagaType.CHECKOUT);
        assertThat(command.sagaId()).isEqualTo(SESSION_ID);
        assertThat(command.stepKey()).isEqualTo(String.valueOf(CART_ITEM_ID));
        assertThat(command.parentCommandId()).isNull();
        assertThat(command.operation())
                .isEqualTo(SupplychainCommandOperationEnum.RESERVE.getCode());
        assertThat(command.businessCommandId())
                .isEqualTo("checkout-" + SESSION_ID + "-item-" + CART_ITEM_ID);
        assertThat(command.transportMode()).isEqualTo(FinanceStockTransportMode.HMAC_RPC_V1);
        assertThat(command.c0JournalAvailable()).isTrue();
        assertThat(command.requestSchemaVersion()).isEqualTo(1);
        assertThat(command.maxDispatchAttempts()).isEqualTo(3);
        assertThat(command.maxResolutionAttempts()).isEqualTo(5);
    }

    @Test
    void build_requestBodyJson_frozenFieldsAndNullsPreserved() throws Exception {
        FinanceStockCommandCreate command = builder.build(
                cartItem(), plan(), queryResult(), SESSION_ID,
                OPERATOR, FinanceStockTransportMode.LOCAL_API_V1, false, 3, 3);

        byte[] body = command.requestBody();
        assertThat(body).isNotNull();

        String json = new String(body, StandardCharsets.UTF_8);
        // Freeze the exact bytes: Include.ALWAYS keeps referenceNo and no
        // alphabetical/map-key sorting is applied.
        assertThat(json).isEqualTo("{\"tenantId\":1,\"stockItemId\":42,"
                + "\"locationId\":88,\"skuCode\":\"SKU-001\",\"quantity\":2,"
                + "\"unit\":\"份\",\"sourceModule\":\"checkout\","
                + "\"sourceRecordId\":500,\"referenceNo\":null,"
                + "\"idempotentKey\":\"checkout-500-item-700\","
                + "\"operatorUserId\":9001}");

        ObjectMapper mapper = new ObjectMapper();
        StockReserveReqDTO req = mapper.readValue(body, StockReserveReqDTO.class);
        assertThat(req.getTenantId()).isEqualTo(TENANT);
        assertThat(req.getStockItemId()).isEqualTo(42L);
        assertThat(req.getLocationId()).isEqualTo(88L);
        assertThat(req.getSkuCode()).isEqualTo("SKU-001");
        assertThat(req.getQuantity()).isEqualByComparingTo("2");
        assertThat(req.getUnit()).isEqualTo("份");
        assertThat(req.getSourceModule()).isEqualTo("checkout");
        assertThat(req.getSourceRecordId()).isEqualTo(SESSION_ID);
        assertThat(req.getReferenceNo()).isNull();
        assertThat(req.getIdempotentKey())
                .isEqualTo("checkout-" + SESSION_ID + "-item-" + CART_ITEM_ID);
        assertThat(req.getOperatorUserId()).isEqualTo(OPERATOR);

        assertThat(command.requestBodySha256()).isEqualTo(sha256Hex(body));
    }

    @Test
    void build_sameSnapshot_producesIdenticalBodyAndSha() {
        FinanceStockCommandCreate first = builder.build(
                cartItem(), plan(), queryResult(), SESSION_ID,
                OPERATOR, FinanceStockTransportMode.HMAC_RPC_V1, true, 3, 5);
        FinanceStockCommandCreate second = builder.build(
                cartItem(), plan(), queryResult(), SESSION_ID,
                OPERATOR, FinanceStockTransportMode.HMAC_RPC_V1, true, 3, 5);

        assertThat(second.requestBody()).isEqualTo(first.requestBody());
        assertThat(second.requestBodySha256()).isEqualTo(first.requestBodySha256());
    }

    // ==================== Fail-closed validation ====================

    @Test
    void build_nullInputs_throws() {
        assertThatThrownBy(() -> builder.build(null, plan(), queryResult(), SESSION_ID,
                OPERATOR, FinanceStockTransportMode.LOCAL_API_V1, false, 3, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cartItem");
        assertThatThrownBy(() -> builder.build(cartItem(), null, queryResult(), SESSION_ID,
                OPERATOR, FinanceStockTransportMode.LOCAL_API_V1, false, 3, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plan");
        assertThatThrownBy(() -> builder.build(cartItem(), plan(), null, SESSION_ID,
                OPERATOR, FinanceStockTransportMode.LOCAL_API_V1, false, 3, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("queryResult");
        assertThatThrownBy(() -> builder.build(cartItem(), plan(), queryResult(), SESSION_ID,
                OPERATOR, null, false, 3, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transportMode");
    }

    @Test
    void build_nonPositiveCartItemIds_throws() {
        CartItemDO badId = cartItem();
        badId.setId(0L);
        assertThatThrownBy(() -> builder.build(badId, plan(), queryResult(), SESSION_ID,
                OPERATOR, FinanceStockTransportMode.LOCAL_API_V1, false, 3, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cartItem.id");

        CartItemDO badTenant = cartItem();
        badTenant.setTenantId(0L);
        assertThatThrownBy(() -> builder.build(badTenant, plan(), queryResult(), SESSION_ID,
                OPERATOR, FinanceStockTransportMode.LOCAL_API_V1, false, 3, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cartItem.tenantId");

        CartItemDO badSku = cartItem();
        badSku.setSkuId(0L);
        assertThatThrownBy(() -> builder.build(badSku, plan(), queryResult(), SESSION_ID,
                OPERATOR, FinanceStockTransportMode.LOCAL_API_V1, false, 3, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cartItem.skuId");
    }

    @Test
    void build_nonPositiveQuantity_throws() {
        CartItemDO badQty = cartItem();
        badQty.setQuantity(0);
        assertThatThrownBy(() -> builder.build(badQty, plan(), queryResult(), SESSION_ID,
                OPERATOR, FinanceStockTransportMode.LOCAL_API_V1, false, 3, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity");
    }

    @Test
    void build_nonPositiveSessionId_throws() {
        assertThatThrownBy(() -> builder.build(cartItem(), plan(), queryResult(), 0L,
                OPERATOR, FinanceStockTransportMode.LOCAL_API_V1, false, 3, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("checkoutSessionId");
    }

    @Test
    void build_nonPositiveOperatorAndAttempts_throws() {
        assertThatThrownBy(() -> builder.build(cartItem(), plan(), queryResult(), SESSION_ID,
                0L, FinanceStockTransportMode.LOCAL_API_V1, false, 3, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operatorUserId");
        assertThatThrownBy(() -> builder.build(cartItem(), plan(), queryResult(), SESSION_ID,
                OPERATOR, FinanceStockTransportMode.LOCAL_API_V1, false, 0, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxDispatchAttempts");
        assertThatThrownBy(() -> builder.build(cartItem(), plan(), queryResult(), SESSION_ID,
                OPERATOR, FinanceStockTransportMode.LOCAL_API_V1, false, 3, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxResolutionAttempts");
    }

    @Test
    void build_localApiV1WithC0True_throws() {
        assertThatThrownBy(() -> builder.build(cartItem(), plan(), queryResult(), SESSION_ID,
                OPERATOR, FinanceStockTransportMode.LOCAL_API_V1, true, 3, 3))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LOCAL_API_V1");
    }

    @Test
    void build_bomClassification_throws() {
        CheckoutCartItemPlanDO bom = plan();
        bom.setClassification(CheckoutStockClassification.BOM.name());
        bom.setStockItemId(null);
        bom.setLocationId(null);
        assertThatThrownBy(() -> builder.build(cartItem(), bom, queryResult(), SESSION_ID,
                OPERATOR, FinanceStockTransportMode.LOCAL_API_V1, false, 3, 3))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NON_BOM");
    }

    @Test
    void build_unmappedClassification_throws() {
        CheckoutCartItemPlanDO unmapped = plan();
        unmapped.setClassification(CheckoutStockClassification.UNMAPPED.name());
        assertThatThrownBy(() -> builder.build(cartItem(), unmapped, queryResult(), SESSION_ID,
                OPERATOR, FinanceStockTransportMode.LOCAL_API_V1, false, 3, 3))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NON_BOM");
    }

    @Test
    void build_unlimitedStockStrategy_throws() {
        CheckoutCartItemPlanDO unlimited = plan();
        unlimited.setStockStrategy(StockStrategyEnum.UNLIMITED.getCode());
        unlimited.setStockItemId(null);
        unlimited.setLocationId(null);
        assertThatThrownBy(() -> builder.build(cartItem(), unlimited, queryResult(), SESSION_ID,
                OPERATOR, FinanceStockTransportMode.LOCAL_API_V1, false, 3, 3))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TRACK_STOCK");
    }

    @Test
    void build_blankSkuCode_throws() {
        CheckoutCartItemPlanDO blank = plan();
        blank.setSkuCode("   ");
        assertThatThrownBy(() -> builder.build(cartItem(), blank, queryResult(), SESSION_ID,
                OPERATOR, FinanceStockTransportMode.LOCAL_API_V1, false, 3, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("skuCode");
    }

    @Test
    void build_missingStockItemOrLocation_throws() {
        CheckoutCartItemPlanDO noStockItem = plan();
        noStockItem.setStockItemId(null);
        assertThatThrownBy(() -> builder.build(cartItem(), noStockItem, queryResult(), SESSION_ID,
                OPERATOR, FinanceStockTransportMode.LOCAL_API_V1, false, 3, 3))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stockItemId");

        CheckoutCartItemPlanDO noLocation = plan();
        noLocation.setLocationId(null);
        assertThatThrownBy(() -> builder.build(cartItem(), noLocation, queryResult(), SESSION_ID,
                OPERATOR, FinanceStockTransportMode.LOCAL_API_V1, false, 3, 3))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("locationId");
    }

    @Test
    void build_skuCodeMismatchWithQueryResult_throws() {
        StockItemQueryRespDTO mismatched = queryResult();
        mismatched.setSkuCode("SKU-OTHER");
        assertThatThrownBy(() -> builder.build(cartItem(), plan(), mismatched, SESSION_ID,
                OPERATOR, FinanceStockTransportMode.LOCAL_API_V1, false, 3, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("skuCode");
    }

    @Test
    void build_stockItemIdMismatchWithQueryResult_throws() {
        StockItemQueryRespDTO mismatched = queryResult();
        mismatched.setId(43L);
        assertThatThrownBy(() -> builder.build(cartItem(), plan(), mismatched, SESSION_ID,
                OPERATOR, FinanceStockTransportMode.LOCAL_API_V1, false, 3, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stockItemId");
    }

    @Test
    void build_blankUnit_throws() {
        StockItemQueryRespDTO blankUnit = queryResult();
        blankUnit.setUnit("   ");
        assertThatThrownBy(() -> builder.build(cartItem(), plan(), blankUnit, SESSION_ID,
                OPERATOR, FinanceStockTransportMode.LOCAL_API_V1, false, 3, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unit");
    }

    @Test
    void build_planAndQueryIdentityMismatches_throw() {
        CheckoutCartItemPlanDO tenantMismatch = plan();
        tenantMismatch.setTenantId(TENANT + 1L);
        assertThatThrownBy(() -> builder.build(cartItem(), tenantMismatch, queryResult(), SESSION_ID,
                OPERATOR, FinanceStockTransportMode.LOCAL_API_V1, false, 3, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        CheckoutCartItemPlanDO skuMismatch = plan();
        skuMismatch.setSkuId(901L);
        assertThatThrownBy(() -> builder.build(cartItem(), skuMismatch, queryResult(), SESSION_ID,
                OPERATOR, FinanceStockTransportMode.LOCAL_API_V1, false, 3, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("skuId");

        CheckoutCartItemPlanDO sessionMismatch = plan();
        sessionMismatch.setCheckoutSessionId(SESSION_ID + 1L);
        assertThatThrownBy(() -> builder.build(cartItem(), sessionMismatch, queryResult(), SESSION_ID,
                OPERATOR, FinanceStockTransportMode.LOCAL_API_V1, false, 3, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("checkoutSessionId");

        StockItemQueryRespDTO queryTenantMismatch = queryResult();
        queryTenantMismatch.setTenantId(TENANT + 1L);
        assertThatThrownBy(() -> builder.build(cartItem(), plan(), queryTenantMismatch, SESSION_ID,
                OPERATOR, FinanceStockTransportMode.LOCAL_API_V1, false, 3, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
    }

    @Test
    void build_planCartItemMismatch_throws() {
        CheckoutCartItemPlanDO otherPlan = plan();
        otherPlan.setCartItemId(CART_ITEM_ID + 1L);
        assertThatThrownBy(() -> builder.build(cartItem(), otherPlan, queryResult(), SESSION_ID,
                OPERATOR, FinanceStockTransportMode.LOCAL_API_V1, false, 3, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cartItemId");
    }

    @Test
    void build_serializationFailure_failClosed() {
        CheckoutStockCommandBuilder failing = new CheckoutStockCommandBuilder(
                new ObjectMapper() {
                    @Override
                    public byte[] writeValueAsBytes(Object value) throws JsonProcessingException {
                        throw new JsonProcessingException("boom") { };
                    }
                });
        assertThatThrownBy(() -> failing.build(cartItem(), plan(), queryResult(), SESSION_ID,
                OPERATOR, FinanceStockTransportMode.LOCAL_API_V1, false, 3, 3))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("serialize");
    }

    // ==================== Fixtures ====================

    private CartItemDO cartItem() {
        CartItemDO item = new CartItemDO();
        item.setId(CART_ITEM_ID);
        item.setTenantId(TENANT);
        item.setCartId(300L);
        item.setSkuId(900L);
        item.setQuantity(2);
        return item;
    }

    private CheckoutCartItemPlanDO plan() {
        CheckoutCartItemPlanDO plan = new CheckoutCartItemPlanDO();
        plan.setTenantId(TENANT);
        plan.setCheckoutSessionId(SESSION_ID);
        plan.setCartItemId(CART_ITEM_ID);
        plan.setSkuId(900L);
        plan.setClassification(CheckoutStockClassification.NON_BOM.name());
        plan.setSkuCode("SKU-001");
        plan.setStockStrategy(StockStrategyEnum.TRACK_STOCK.getCode());
        plan.setBomProductId(null);
        plan.setStockItemId(42L);
        plan.setLocationId(88L);
        plan.setClassificationReason(null);
        return plan;
    }

    private StockItemQueryRespDTO queryResult() {
        return new StockItemQueryRespDTO(42L, "SKU-001", TENANT, "份");
    }

    private static String sha256Hex(byte[] body) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
