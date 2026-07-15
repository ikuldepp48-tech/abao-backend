package com.geihou.module.supplychain.stock.command;

import com.geihou.module.supplychain.api.stock.enums.SupplychainCommandOperationEnum;

/**
 * Codec for serializing business results to journal snapshots and deserializing
 * journal snapshots back to {@link SnapshotV1.Root}.
 *
 * <p>The codec operates at the persistence boundary only. It adapts the real
 * business result type (e.g. {@code Long}, response DTO) to a
 * {@link SnapshotV1.Root} JSON shape for {@code result_snapshot} storage, and
 * reconstructs a {@link SnapshotV1.Root} from stored JSON on replay.
 *
 * <p>{@code schemaVersion} validation is the executor's responsibility; the
 * codec does not accept a version parameter.
 */
public interface CommandCodec {

    /**
     * Serialize a business result to canonical JSON for journal storage.
     *
     * @param operation      the operation type
     * @param businessResult the business result; may be {@code null} for RELEASE
     * @return canonical JSON string matching the operation's snapshot schema
     */
    String serialize(
            SupplychainCommandOperationEnum operation,
            Object businessResult);

    /**
     * Deserialize a journal snapshot JSON back to a {@link SnapshotV1.Root}.
     *
     * @param operation the operation type
     * @param json      the stored {@code result_snapshot} JSON
     * @return the reconstructed snapshot root
     */
    SnapshotV1.Root deserialize(
            SupplychainCommandOperationEnum operation,
            String json);

    /**
     * Validate that the business result type matches the operation's expected
     * type.
     *
     * @param operation      the operation type
     * @param businessResult the business result; {@code null} is allowed only
     *                       for RELEASE, all other operations require non-null
     *                       and exact type match
     */
    void validateBusinessResultType(
            SupplychainCommandOperationEnum operation,
            Object businessResult);
}
