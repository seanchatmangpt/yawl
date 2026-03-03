package org.yawlfoundation.yawl.qlever;

import javax.annotation.Nonnull;

/**
 * Enum representing supported SPARQL result media types for QLever.
 */
public enum QLeverMediaType {
    /**
     * Turtle format.
     */
    TURTLE("text/turtle"),

    /**
     * JSON format (SPARQL Results JSON).
     */
    JSON("application/sparql-results+json"),

    /**
     * XML format (SPARQL Results XML).
     */
    XML("application/sparql-results+xml"),

    /**
     * CSV format.
     */
    CSV("text/csv");

    private final @Nonnull String contentType;

    QLeverMediaType(@Nonnull String contentType) {
        this.contentType = contentType;
    }

    /**
     * Gets the content type string for this media type.
     */
    @Nonnull
    public String getContentType() {
        return contentType;
    }

    /**
     * Parses a content type string to a QLeverMediaType.
     * Returns null if not supported.
     */
    @javax.annotation.Nullable
    public static QLeverMediaType fromContentType(@Nonnull String contentType) {
        for (QLeverMediaType type : values()) {
            if (type.contentType.equalsIgnoreCase(contentType)) {
                return type;
            }
        }
        return null;
    }
}