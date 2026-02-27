package org.yawlfoundation.yawl.integration.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * A utility class for parsing key=value payload strings across A2A skills and MCP tools.
 *
 * Supports various formats:
 * - Simple: "key1=value1,key2=value2"
 * - Mixed separators: "key1=value1;key2=value2"
 * - Quoted values: name="John Doe", city="New York"
 * - Numeric values: count=42, price=3.14
 * - Boolean values: enabled=true, active=false
 *
 * Example usage:
 * <pre>
 * PayloadParser parser = new PayloadParser("name=John,age=30,active=true");
 * String name = parser.getString("name", "unknown");
 * int age = parser.getInt("age", 0);
 * boolean isActive = parser.getBoolean("active", false);
 * </pre>
 */
public final class PayloadParser {

    private final Map<String, String> data;

    /**
     * Creates a PayloadParser from the given payload string.
     *
     * @param payloadStr the payload string to parse (e.g., "key1=value1,key2=value2")
     */
    public PayloadParser(String payloadStr) {
        this.data = parse(payloadStr);
    }

    /**
     * Parses a payload string into a key-value map.
     *
     * @param payloadStr the payload string to parse
     * @return a map containing the parsed key-value pairs, empty if null or blank
     */
    public static Map<String, String> parse(String payloadStr) {
        Map<String, String> result = new HashMap<>();
        if (payloadStr == null || payloadStr.isBlank()) {
            return result;
        }

        // Split on comma or semicolon
        String[] pairs = payloadStr.split("[,;]");
        for (String pair : pairs) {
            String trimmedPair = pair.trim();
            if (trimmedPair.isEmpty()) {
                continue;
            }

            String[] kv = trimmedPair.split("=", 2);
            if (kv.length == 2) {
                String key = kv[0].trim();
                String value = kv[1].trim();

                // Remove quotes if present
                if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                    value = value.substring(1, value.length() - 1);
                }

                // Only add non-empty keys
                if (!key.isEmpty()) {
                    result.put(key, value);
                }
            }
        }
        return result;
    }

    /**
     * Gets the string value for the given key.
     *
     * @param key the key to retrieve
     * @return the string value, or null if key not found
     */
    public String getString(String key) {
        return data.get(key);
    }

    /**
     * Gets the string value for the given key with a default value.
     *
     * @param key the key to retrieve
     * @param defaultValue the default value to return if key not found
     * @return the string value, or defaultValue if key not found
     */
    public String getString(String key, String defaultValue) {
        String value = data.get(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Gets the integer value for the given key.
     *
     * @param key the key to retrieve
     * @param defaultValue the default value to return if key not found or invalid
     * @return the integer value, or defaultValue if key not found or cannot be parsed
     */
    public int getInt(String key, int defaultValue) {
        String value = data.get(key);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                // Fall through to default
            }
        }
        return defaultValue;
    }

    /**
     * Gets the long value for the given key.
     *
     * @param key the key to retrieve
     * @param defaultValue the default value to return if key not found or invalid
     * @return the long value, or defaultValue if key not found or cannot be parsed
     */
    public long getLong(String key, long defaultValue) {
        String value = data.get(key);
        if (value != null) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                // Fall through to default
            }
        }
        return defaultValue;
    }

    /**
     * Gets the boolean value for the given key.
     *
     * @param key the key to retrieve
     * @param defaultValue the default value to return if key not found
     * @return the boolean value, or defaultValue if key not found
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = data.get(key);
        if (value != null) {
            return parseBoolean(value);
        }
        return defaultValue;
    }

    /**
     * Gets the double value for the given key.
     *
     * @param key the key to retrieve
     * @param defaultValue the default value to return if key not found or invalid
     * @return the double value, or defaultValue if key not found or cannot be parsed
     */
    public double getDouble(String key, double defaultValue) {
        String value = data.get(key);
        if (value != null) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                // Fall through to default
            }
        }
        return defaultValue;
    }

    /**
     * Checks if the given key exists in the parsed data.
     *
     * @param key the key to check
     * @return true if the key exists, false otherwise
     */
    public boolean hasKey(String key) {
        return data.containsKey(key);
    }

    /**
     * Gets all keys in the parsed data.
     *
     * @return an unmodifiable set of all keys
     */
    public Set<String> keys() {
        return new HashSet<>(data.keySet());
    }

    /**
     * Gets all key-value pairs as a new map.
     *
     * @return a copy of the internal map
     */
    public Map<String, String> toMap() {
        return new HashMap<>(data);
    }

    /**
     * Returns the number of key-value pairs.
     *
     * @return the size of the map
     */
    public int size() {
        return data.size();
    }

    /**
     * Checks if there are any key-value pairs.
     *
     * @return true if empty, false otherwise
     */
    public boolean isEmpty() {
        return data.isEmpty();
    }

    /**
     * Parses a string into a boolean value.
     * Recognizes true/false, yes/no, on/off, 1/0 (case insensitive).
     *
     * @param value the string to parse
     * @return the boolean value
     */
    private static boolean parseBoolean(String value) {
        if (value == null) {
            return false;
        }

        String lowerValue = value.toLowerCase();
        return lowerValue.equals("true") ||
               lowerValue.equals("yes") ||
               lowerValue.equals("on") ||
               lowerValue.equals("1");
    }

    /**
     * Returns a string representation of the parsed data.
     *
     * @return string representation of the map
     */
    @Override
    public String toString() {
        return data.toString();
    }
}