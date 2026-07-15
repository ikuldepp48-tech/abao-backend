package com.geihou.module.supplychain.stock.command;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.geihou.module.supplychain.api.stock.dto.SalesOutBomReverseItemRespDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesOutBomReverseRespDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesReverseRestoreItemRespDTO;
import com.geihou.module.supplychain.api.stock.dto.SalesReverseRestoreRespDTO;
import com.geihou.module.supplychain.api.stock.dto.StockCoverageDecisionRespDTO;
import com.geihou.module.supplychain.api.stock.enums.SupplychainCommandOperationEnum;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Strict {@link CommandCodec} implementation using a copied Jackson
 * {@link ObjectMapper} with fail-closed serialization and deserialization.
 *
 * <p>No {@code @Component} - registration is via {@code CommandCodecConfig}.
 */
public class CommandCodecImpl implements CommandCodec {

    private static final Map<SupplychainCommandOperationEnum, Class<? extends SnapshotV1.Root>> SNAPSHOT_TYPE_BY_OPERATION;
    private static final Map<SupplychainCommandOperationEnum, Class<?>> BUSINESS_RETURN_TYPE_BY_OPERATION;

    static {
        EnumMap<SupplychainCommandOperationEnum, Class<? extends SnapshotV1.Root>> snapshotMap =
                new EnumMap<>(SupplychainCommandOperationEnum.class);
        snapshotMap.put(SupplychainCommandOperationEnum.RESERVE, SnapshotV1.Reserve.class);
        snapshotMap.put(SupplychainCommandOperationEnum.RELEASE, SnapshotV1.Release.class);
        snapshotMap.put(SupplychainCommandOperationEnum.COMMIT, SnapshotV1.Commit.class);
        snapshotMap.put(SupplychainCommandOperationEnum.SALES_OUT_BOM_REVERSE, SnapshotV1.SalesOutBomReverse.class);
        snapshotMap.put(SupplychainCommandOperationEnum.SALES_REVERSE_RESTORE, SnapshotV1.SalesReverseRestore.class);
        snapshotMap.put(SupplychainCommandOperationEnum.OBSERVE_MISSING_MAPPING, SnapshotV1.Observe.class);
        SNAPSHOT_TYPE_BY_OPERATION = Collections.unmodifiableMap(snapshotMap);

        EnumMap<SupplychainCommandOperationEnum, Class<?>> businessMap =
                new EnumMap<>(SupplychainCommandOperationEnum.class);
        businessMap.put(SupplychainCommandOperationEnum.RESERVE, Long.class);
        businessMap.put(SupplychainCommandOperationEnum.RELEASE, Void.class);
        businessMap.put(SupplychainCommandOperationEnum.COMMIT, Long.class);
        businessMap.put(SupplychainCommandOperationEnum.SALES_OUT_BOM_REVERSE, SalesOutBomReverseRespDTO.class);
        businessMap.put(SupplychainCommandOperationEnum.SALES_REVERSE_RESTORE, SalesReverseRestoreRespDTO.class);
        businessMap.put(SupplychainCommandOperationEnum.OBSERVE_MISSING_MAPPING, StockCoverageDecisionRespDTO.class);
        BUSINESS_RETURN_TYPE_BY_OPERATION = Collections.unmodifiableMap(businessMap);
    }

    private final ObjectMapper mapper;
    private final CommandClock clock;

    public CommandCodecImpl(ObjectMapper springBootMapper, CommandClock clock) {
        this.clock = clock;
        this.mapper = springBootMapper.copy()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .configure(SerializationFeature.WRITE_ENUMS_USING_INDEX, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
                .configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, true)
                .configure(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES, true)
                .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
                .setSerializationInclusion(JsonInclude.Include.ALWAYS);
    }

    @Override
    public String serialize(SupplychainCommandOperationEnum operation, Object businessResult) {
        validateBusinessResultType(operation, businessResult);
        SnapshotV1.Root root = toSnapshot(operation, businessResult);
        try {
            return mapper.writeValueAsString(root);
        } catch (JacksonException e) {
            throw new IllegalStateException("Snapshot serialize failed: " + e.getMessage(), e);
        }
    }

    @Override
    public SnapshotV1.Root deserialize(SupplychainCommandOperationEnum operation, String json) {
        if (operation == null) {
            throw new IllegalArgumentException("operation must not be null");
        }
        Class<? extends SnapshotV1.Root> targetClass = SNAPSHOT_TYPE_BY_OPERATION.get(operation);
        if (targetClass == null) {
            throw new IllegalStateException("No snapshot type mapping for: " + operation);
        }
        try {
            return mapper.readValue(json, targetClass);
        } catch (JacksonException e) {
            throw new IllegalStateException("Snapshot validation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void validateBusinessResultType(SupplychainCommandOperationEnum operation, Object businessResult) {
        if (operation == null) {
            throw new IllegalArgumentException("operation must not be null");
        }
        Class<?> expected = BUSINESS_RETURN_TYPE_BY_OPERATION.get(operation);
        if (expected == null) {
            throw new IllegalStateException("No business return type mapping for: " + operation);
        }
        if (expected == Void.class) {
            if (businessResult != null) {
                throw new IllegalStateException(
                        "RELEASE business result must be null, got: "
                                + businessResult.getClass().getName());
            }
            return;
        }
        if (businessResult == null) {
            throw new IllegalStateException(
                    operation + " business result must not be null");
        }
        if (!expected.isInstance(businessResult)) {
            throw new IllegalStateException(
                    operation + " business result type mismatch: expected "
                            + expected.getName() + ", got "
                            + businessResult.getClass().getName());
        }
    }

    // --- Business result to snapshot conversion ---

    private SnapshotV1.Root toSnapshot(SupplychainCommandOperationEnum operation, Object businessResult) {
        return switch (operation) {
            case RESERVE -> new SnapshotV1.Reserve((Long) businessResult);
            case RELEASE -> new SnapshotV1.Release();
            case COMMIT -> new SnapshotV1.Commit((Long) businessResult);
            case SALES_OUT_BOM_REVERSE -> toSalesOutBomReverse((SalesOutBomReverseRespDTO) businessResult);
            case SALES_REVERSE_RESTORE -> toSalesReverseRestore((SalesReverseRestoreRespDTO) businessResult);
            case OBSERVE_MISSING_MAPPING -> toObserve((StockCoverageDecisionRespDTO) businessResult);
        };
    }

    private SnapshotV1.SalesOutBomReverse toSalesOutBomReverse(SalesOutBomReverseRespDTO dto) {
        List<SnapshotV1.SalesOutBomReverseItem> items = dto.getItems() == null
                ? null
                : dto.getItems().stream().map(this::toSalesOutBomReverseItem).toList();
        return new SnapshotV1.SalesOutBomReverse(
                dto.getProductId(), dto.getSkuCode(), dto.getQuantity(),
                dto.getRecipeId(), dto.getRecipeVersion(), items);
    }

    private SnapshotV1.SalesOutBomReverseItem toSalesOutBomReverseItem(SalesOutBomReverseItemRespDTO dto) {
        return new SnapshotV1.SalesOutBomReverseItem(
                dto.getComponentProductId(), dto.getSkuCode(), dto.getUnit(),
                dto.getStockItemId(), dto.getQuantity(), dto.getEventId(),
                dto.getClientRequestId(), dto.getRecipeId(), dto.getRecipeVersion());
    }

    private SnapshotV1.SalesReverseRestore toSalesReverseRestore(SalesReverseRestoreRespDTO dto) {
        List<SnapshotV1.SalesReverseRestoreItem> items = dto.getItems() == null
                ? null
                : dto.getItems().stream().map(this::toSalesReverseRestoreItem).toList();
        return new SnapshotV1.SalesReverseRestore(dto.getRestoredItemCount(), items);
    }

    private SnapshotV1.SalesReverseRestoreItem toSalesReverseRestoreItem(SalesReverseRestoreItemRespDTO dto) {
        return new SnapshotV1.SalesReverseRestoreItem(
                dto.getOriginalEventId(), dto.getRestoreEventId(), dto.getStockItemId(),
                dto.getLocationId(), dto.getQuantity(), dto.getUnit(),
                dto.getRecipeId(), dto.getRecipeVersion());
    }

    private SnapshotV1.Observe toObserve(StockCoverageDecisionRespDTO dto) {
        return new SnapshotV1.Observe(dto.getMode().getCode(), dto.isEnforce());
    }
}
