package org.yawlfoundation.yawl.integration.util;

/**
 * Utility class for parsing string payloads into key-value pairs.
 */
public final class PayloadParser {

    private PayloadParser() {}

    public static String parse(String payload, String key) {
        return parse(payload, key, "");
    }

    public static String parse(String payload, String key, String defaultValue) {
        if (payload == null || key == null) {
            throw new NullPointerException("Payload and key cannot be null");
        }

        String[] pairs = payload.split("[,;]");

        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].trim().equals(key.trim())) {
                return kv[1].replace("\"", "").trim();
            }
        }

        return defaultValue;
    }

    public static int getInt(String payload, String key) {
        return getInt(payload, key, 0);
    }

    public static int getInt(String payload, String key, int defaultValue) {
        String value = parse(payload, key);
        try {
            return value.isEmpty() ? defaultValue : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Invalid integer value for key '" + key + "': " + value);
        }
    }

    public static boolean getBoolean(String payload, String key) {
        return getBoolean(payload, key, false);
    }

    public static boolean getBoolean(String payload, String key, boolean defaultValue) {
        String value = parse(payload, key);
        if (value.isEmpty()) {
            return defaultValue;
        }

        value = value.toLowerCase();
        if (value.equals("true") || value.equals("1")) {
            return true;
        } else if (value.equals("false") || value.equals("0")) {
            return false;
        } else {
            throw new IllegalArgumentException("Invalid boolean value for key '" + key + "': " + value);
        }
    }
}