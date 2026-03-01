/**
 * H-Guards Violation: H_FALLBACK Pattern
 *
 * This fixture contains catch blocks returning fake data instead of propagating exceptions
 * Expected: FAIL with pattern H_FALLBACK
 */
package org.yawlfoundation.yawl.test.guards;

import java.util.Collections;
import java.util.List;

public class ViolationHFallback {

    public String fetchData(String url) {
        try {
            return retrieveFromRemote(url);
        } catch (Exception e) {
            return "default value";
        }
    }

    public List<String> getWorkItems() {
        try {
            return fetchFromDatabase();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public void processWorkflow() {
        try {
            executeWorkflow();
        } catch (Exception e) {
            log.warn("Failed to execute workflow");
        }
    }

    public String getConfig(String key) {
        try {
            return loadConfiguration(key);
        } catch (Exception e) {
            return "";
        }
    }

    private String retrieveFromRemote(String url) throws Exception {
        throw new Exception("Network error");
    }

    private List<String> fetchFromDatabase() throws Exception {
        throw new Exception("Database error");
    }

    private void executeWorkflow() throws Exception {
        throw new Exception("Execution error");
    }

    private String loadConfiguration(String key) throws Exception {
        throw new Exception("Configuration error");
    }

    private static class log {
        static void warn(String msg) {
            System.out.println(msg);
        }
    }
}
