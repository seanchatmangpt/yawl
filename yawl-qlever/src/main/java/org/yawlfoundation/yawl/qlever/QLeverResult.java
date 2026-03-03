package org.yawlfoundation.yawl.qlever;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Represents the result of a QLever SPARQL query.
 *
 * @param status   The status of the query execution
 * @param data     The query result data (may be null for some statuses)
 * @param metadata Optional metadata about the result
 */
public record QLeverResult(
    @Nonnull QLeverStatus status,
    @Nullable String data,
    @Nullable String metadata
) {

    /**
     * Creates a successful result with data.
     */
    public static QLeverResult success(@Nonnull String data, @Nullable String metadata) {
        return new QLeverResult(QLeverStatus.READY, data, metadata);
    }

    /**
     * Creates an empty result.
     */
    public static QLeverResult empty(@Nonnull QLeverStatus status) {
        return new QLeverResult(status, null, null);
    }

    /**
     * Checks if the result represents successful execution.
     */
    public boolean isSuccess() {
        return status == QLeverStatus.READY;
    }
}