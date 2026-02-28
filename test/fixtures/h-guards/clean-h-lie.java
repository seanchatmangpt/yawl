/**
 * H-Guards Clean: H_LIE Pattern - PASS
 *
 * This fixture has documentation that matches implementation.
 * Expected: PASS (no H_LIE violations)
 */
package org.yawlfoundation.yawl.test.guards;

public class CleanHLie {

    /**
     * Returns the work item ID if available.
     *
     * @return the ID, or null if not set
     */
    public String getWorkItemId() {
        return null;  // Documentation says "or null if not set" - matches implementation
    }

    /**
     * Validates the input.
     *
     * @param input the input to validate
     * @throws IllegalArgumentException if input is invalid
     */
    public void validateInput(String input) throws IllegalArgumentException {
        // Implementation matches documentation: throws exception if invalid
        if (input == null) {
            throw new IllegalArgumentException("Input cannot be null");
        }
    }

    /**
     * Processes data synchronously and returns result.
     *
     * @param data the data to process
     * @return the processing result
     * @throws UnsupportedOperationException if not implemented
     */
    public String processData(String data) throws UnsupportedOperationException {
        // Implementation matches documentation: synchronous with immediate return
        throw new UnsupportedOperationException(
            "processData requires real implementation"
        );
    }

    /**
     * Retrieves items from cache (fast operation).
     *
     * @throws UnsupportedOperationException if not implemented
     */
    public void retrieveItems() throws UnsupportedOperationException {
        // Implementation matches documentation: no network calls
        throw new UnsupportedOperationException(
            "retrieveItems requires cache implementation"
        );
    }
}
