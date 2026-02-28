/**
 * Q-Invariants Violation: Q3 Pattern - Silent catch-and-return
 *
 * This fixture violates Q invariant by catching exceptions and returning fake data
 * instead of propagating the error or throwing UnsupportedOperationException.
 * Silent fallback (catch and return fake) is forbidden per Q rules.
 *
 * Expected: FAIL with pattern Q3 on catch blocks with silent returns
 */
package org.yawlfoundation.yawl.test.invariants;

public class ViolationQ3 {

    /**
     * This method catches exceptions and returns empty list instead of propagating.
     */
    public java.util.List<String> loadConfiguration(String configPath) {
        try {
            return loadFromFile(configPath);
        } catch (Exception e) {
            // VIOLATION: Silent fallback instead of throwing
            return java.util.Collections.emptyList();
        }
    }

    /**
     * This method catches and returns false instead of propagating.
     */
    public boolean validateWorkflow(String workflowXml) {
        try {
            return performValidation(workflowXml);
        } catch (RuntimeException e) {
            // VIOLATION: Silent fallback
            return false;
        }
    }

    /**
     * This method catches and returns null instead of throwing.
     */
    public java.util.Map<String, Object> parseWorkflowData(String jsonData) {
        try {
            return parseJson(jsonData);
        } catch (Exception e) {
            // VIOLATION: Silent fallback with null
            return null;
        }
    }

    private java.util.List<String> loadFromFile(String path) throws Exception {
        throw new Exception("Not implemented");
    }

    private boolean performValidation(String xml) throws RuntimeException {
        throw new RuntimeException("Not implemented");
    }

    private java.util.Map<String, Object> parseJson(String json) throws Exception {
        throw new Exception("Not implemented");
    }
}
