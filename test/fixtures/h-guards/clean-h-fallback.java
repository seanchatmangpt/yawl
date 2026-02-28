/**
 * H-Guards Clean: H_FALLBACK Pattern - PASS
 *
 * This fixture propagates exceptions instead of catching and returning fake data.
 * Expected: PASS (no H_FALLBACK violations)
 */
package org.yawlfoundation.yawl.test.guards;

public class CleanHFallback {

    public String fetchData(String url) throws Exception {
        // Propagates exception instead of catching and returning fake data
        return retrieveFromRemote(url);
    }

    public java.util.List<String> getWorkItems() throws Exception {
        // Propagates exception instead of catching and returning empty list
        return fetchFromDatabase();
    }

    public void processWorkflow() throws Exception {
        // Propagates exception instead of catching and logging
        executeWorkflow();
    }

    public String getConfig(String key) throws Exception {
        // Propagates exception instead of catching and returning empty string
        return loadConfiguration(key);
    }

    public String processDataWithRetry(String url) throws Exception {
        int maxRetries = 3;
        Exception lastException = null;

        for (int i = 0; i < maxRetries; i++) {
            try {
                return retrieveFromRemote(url);
            } catch (Exception e) {
                lastException = e;
                // Continue retry loop, don't swallow exception
            }
        }

        // Propagate exception after retries exhausted
        throw lastException;
    }

    private String retrieveFromRemote(String url) throws Exception {
        throw new Exception("Network unavailable");
    }

    private java.util.List<String> fetchFromDatabase() throws Exception {
        throw new Exception("Database unavailable");
    }

    private void executeWorkflow() throws Exception {
        throw new Exception("Workflow engine unavailable");
    }

    private String loadConfiguration(String key) throws Exception {
        throw new Exception("Configuration not found: " + key);
    }
}
