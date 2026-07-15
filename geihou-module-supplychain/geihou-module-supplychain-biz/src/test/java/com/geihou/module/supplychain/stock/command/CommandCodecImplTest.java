package com.geihou.module.supplychain.stock.command;

import org.junit.jupiter.api.Test;

import static com.geihou.module.supplychain.api.stock.enums.SupplychainCommandOperationEnum.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CommandCodecImpl}.
 *
 * <p>Covers serialize/deserialize round-trip for all 6 operations,
 * {@code validateBusinessResultType} boundary cases, error handling
 * for invalid JSON and null operation, and strict deserialization
 * ({@code FAIL_ON_MISSING_CREATOR_PROPERTIES}) for missing nested
 * required fields.
 */
class CommandCodecImplTest {

    private final CommandCodec codec = TestSupport.commandCodec();

    // --- Serialize + Deserialize round-trip ---

    @Test
    void serializeDeserialize_reserve() {
        String json = codec.serialize(RESERVE, TestSupport.sampleReserveResult());
        assertThat(json).isNotBlank();
        SnapshotV1.Root root = codec.deserialize(RESERVE, json);
        assertThat(root).isInstanceOf(SnapshotV1.Reserve.class);
        assertThat(((SnapshotV1.Reserve) root).reserveId()).isEqualTo(123L);
    }

    @Test
    void serializeDeserialize_release() {
        String json = codec.serialize(RELEASE, null);
        assertThat(json).isNotBlank();
        SnapshotV1.Root root = codec.deserialize(RELEASE, json);
        assertThat(root).isInstanceOf(SnapshotV1.Release.class);
    }

    @Test
    void serializeDeserialize_commit() {
        String json = codec.serialize(COMMIT, TestSupport.sampleCommitResult());
        assertThat(json).isNotBlank();
        SnapshotV1.Root root = codec.deserialize(COMMIT, json);
        assertThat(root).isInstanceOf(SnapshotV1.Commit.class);
        assertThat(((SnapshotV1.Commit) root).consumeOutEventId()).isEqualTo(456L);
    }

    @Test
    void serializeDeserialize_salesOutBomReverse() {
        String json = codec.serialize(SALES_OUT_BOM_REVERSE,
                TestSupport.sampleSalesOutBomReverse());
        assertThat(json).isNotBlank();
        SnapshotV1.Root root = codec.deserialize(SALES_OUT_BOM_REVERSE, json);
        assertThat(root).isInstanceOf(SnapshotV1.SalesOutBomReverse.class);
        SnapshotV1.SalesOutBomReverse snap = (SnapshotV1.SalesOutBomReverse) root;
        assertThat(snap.productId()).isEqualTo(100L);
        assertThat(snap.skuCode()).isEqualTo("SKU-FINISHED");
        assertThat(snap.quantity()).isEqualByComparingTo("5.00");
        assertThat(snap.recipeId()).isEqualTo(200L);
        assertThat(snap.recipeVersion()).isEqualTo(1);
        assertThat(snap.items()).hasSize(1);
        SnapshotV1.SalesOutBomReverseItem item = snap.items().get(0);
        assertThat(item.componentProductId()).isEqualTo(101L);
        assertThat(item.skuCode()).isEqualTo("SKU-RAW");
        assertThat(item.unit()).isEqualTo("KG");
        assertThat(item.stockItemId()).isEqualTo(1001L);
        assertThat(item.quantity()).isEqualByComparingTo("2.00");
        assertThat(item.eventId()).isEqualTo(5001L);
        assertThat(item.clientRequestId()).isEqualTo("cr-001::101");
        assertThat(item.recipeId()).isEqualTo(200L);
        assertThat(item.recipeVersion()).isEqualTo(1);
    }

    @Test
    void serializeDeserialize_salesReverseRestore() {
        String json = codec.serialize(SALES_REVERSE_RESTORE,
                TestSupport.sampleSalesReverseRestore());
        assertThat(json).isNotBlank();
        SnapshotV1.Root root = codec.deserialize(SALES_REVERSE_RESTORE, json);
        assertThat(root).isInstanceOf(SnapshotV1.SalesReverseRestore.class);
        SnapshotV1.SalesReverseRestore snap = (SnapshotV1.SalesReverseRestore) root;
        assertThat(snap.restoredItemCount()).isEqualTo(1);
        assertThat(snap.items()).hasSize(1);
        SnapshotV1.SalesReverseRestoreItem item = snap.items().get(0);
        assertThat(item.originalEventId()).isEqualTo(5001L);
        assertThat(item.restoreEventId()).isEqualTo(6001L);
        assertThat(item.stockItemId()).isEqualTo(1001L);
        assertThat(item.locationId()).isEqualTo(2001L);
        assertThat(item.quantity()).isEqualByComparingTo("2.00");
        assertThat(item.unit()).isEqualTo("KG");
        assertThat(item.recipeId()).isEqualTo(200L);
        assertThat(item.recipeVersion()).isEqualTo(1);
    }

    @Test
    void serializeDeserialize_observeAuditOnly() {
        String json = codec.serialize(OBSERVE_MISSING_MAPPING,
                TestSupport.sampleObserveAuditOnly());
        assertThat(json).isNotBlank();
        SnapshotV1.Root root = codec.deserialize(OBSERVE_MISSING_MAPPING, json);
        assertThat(root).isInstanceOf(SnapshotV1.Observe.class);
        SnapshotV1.Observe snap = (SnapshotV1.Observe) root;
        assertThat(snap.mode()).isEqualTo("AUDIT_ONLY");
        assertThat(snap.enforce()).isFalse();
    }

    @Test
    void serializeDeserialize_observeEnforce() {
        String json = codec.serialize(OBSERVE_MISSING_MAPPING,
                TestSupport.sampleObserveEnforce());
        assertThat(json).isNotBlank();
        SnapshotV1.Root root = codec.deserialize(OBSERVE_MISSING_MAPPING, json);
        assertThat(root).isInstanceOf(SnapshotV1.Observe.class);
        SnapshotV1.Observe snap = (SnapshotV1.Observe) root;
        assertThat(snap.mode()).isEqualTo("ENFORCE");
        assertThat(snap.enforce()).isTrue();
    }

    // --- validateBusinessResultType ---

    @Test
    void validate_reserve_acceptsLong() {
        codec.validateBusinessResultType(RESERVE, TestSupport.sampleReserveResult());
    }

    @Test
    void validate_reserve_rejectsNull() {
        assertThatThrownBy(() -> codec.validateBusinessResultType(RESERVE, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RESERVE business result must not be null");
    }

    @Test
    void validate_reserve_rejectsWrongType() {
        assertThatThrownBy(() -> codec.validateBusinessResultType(RESERVE, "wrong"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RESERVE business result type mismatch")
                .hasMessageContaining("expected java.lang.Long")
                .hasMessageContaining("got java.lang.String");
    }

    @Test
    void validate_release_acceptsNull() {
        codec.validateBusinessResultType(RELEASE, null);
    }

    @Test
    void validate_release_rejectsNonNull() {
        assertThatThrownBy(() -> codec.validateBusinessResultType(RELEASE, "not null"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RELEASE business result must be null")
                .hasMessageContaining("got: java.lang.String");
    }

    @Test
    void validate_commit_acceptsLong() {
        codec.validateBusinessResultType(COMMIT, TestSupport.sampleCommitResult());
    }

    @Test
    void validate_salesOutBomReverse_acceptsDto() {
        codec.validateBusinessResultType(SALES_OUT_BOM_REVERSE,
                TestSupport.sampleSalesOutBomReverse());
    }

    @Test
    void validate_nullOperation_throwsIllegalArgument() {
        assertThatThrownBy(() -> codec.validateBusinessResultType(null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("operation must not be null");
    }

    // --- Error handling & strict deserialization ---

    @Test
    void deserialize_invalidJson_throwsIllegalState() {
        assertThatThrownBy(() -> codec.deserialize(RESERVE, "not valid json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Snapshot validation failed");
    }

    @Test
    void deserialize_nullOperation_throwsIllegalArgument() {
        assertThatThrownBy(() -> codec.deserialize(null, "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("operation must not be null");
    }

    @Test
    void serialize_nullOperation_throwsIllegalArgument() {
        assertThatThrownBy(() -> codec.serialize(null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("operation must not be null");
    }

    @Test
    void deserialize_salesOutBomReverse_itemMissingUnit_throwsIllegalState() {
        String json = "{"
                + "\"productId\":1,"
                + "\"skuCode\":\"S\","
                + "\"quantity\":1.0,"
                + "\"recipeId\":1,"
                + "\"recipeVersion\":1,"
                + "\"items\":[{"
                + "\"componentProductId\":2,"
                + "\"skuCode\":\"S\","
                + "\"stockItemId\":5,"
                + "\"quantity\":1.0,"
                + "\"eventId\":99,"
                + "\"clientRequestId\":\"c\","
                + "\"recipeId\":1,"
                + "\"recipeVersion\":1"
                + "}]}";

        assertThatThrownBy(() ->
                codec.deserialize(SALES_OUT_BOM_REVERSE, json))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Snapshot validation failed");
    }
}
