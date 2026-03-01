package org.yawlfoundation.yawl.datamodelling.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.yawlfoundation.yawl.datamodelling.DataModellingException;

/**
 * Thin Jackson wrapper for JSON encode/decode in the service layer.
 */
public final class Json {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Json() {}

    public static String encode(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            throw new DataModellingException("JSON encode failed: " + e.getMessage(), e);
        }
    }

    public static <T> T decode(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new DataModellingException("JSON decode failed for " + type.getSimpleName() + ": " + e.getMessage(), e);
        }
    }
}
