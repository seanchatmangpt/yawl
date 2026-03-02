package org.yawlfoundation.yawl.datamodelling.bridge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared assertion and fixture helpers for L4 JTBD tests.
 *
 * <p>Two static methods:
 * <ul>
 *   <li>{@link #assertPlausible(String, String...)} — domain-plausibility check on JSON output</li>
 *   <li>{@link #loadFixture(String)} — loads a JSON fixture from {@code /fixtures/<name>.json}</li>
 * </ul>
 *
 * <p>Plausibility is not a schema validator — it encodes domain-expert knowledge
 * about what constitutes a valid, actionable response:
 * <ul>
 *   <li>Not null</li>
 *   <li>Starts with {@code {} or {@code [} (valid JSON object or array)</li>
 *   <li>Does not contain an error envelope ({@code "error"} + {@code "message"})</li>
 *   <li>Contains all required field names</li>
 * </ul>
 */
final class JTBDTestSupport {

    private JTBDTestSupport() {}

    /**
     * Asserts that {@code json} is a plausible, non-error response containing
     * all {@code requiredFields}.
     *
     * @param json           the JSON string returned by an L3 call
     * @param requiredFields field names that must appear in the JSON
     */
    static void assertPlausible(String json, String... requiredFields) {
        assertNotNull(json, "Response must not be null");
        String prefix = json.substring(0, Math.min(50, json.length()));
        assertTrue(json.startsWith("{") || json.startsWith("["),
            "Response must be a JSON object or array, got: " + prefix);
        assertFalse(json.contains("\"error\"") && json.contains("\"message\""),
            "Response must not be an error envelope: " + json.substring(0, Math.min(100, json.length())));
        for (String field : requiredFields) {
            assertTrue(json.contains("\"" + field + "\""),
                "Expected field '" + field + "' not found in: "
                    + json.substring(0, Math.min(200, json.length())));
        }
    }

    /**
     * Loads a JSON fixture from the test classpath at {@code /fixtures/<name>.json}.
     *
     * @param name fixture name without extension (e.g. {@code "odcs-schema-simple"})
     * @return fixture content as a UTF-8 string
     * @throws IOException if the fixture file cannot be read
     */
    static String loadFixture(String name) throws IOException {
        String path = "/fixtures/" + name + ".json";
        InputStream is = JTBDTestSupport.class.getResourceAsStream(path);
        assertNotNull(is, "Fixture not found on classpath: " + path);
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
}
