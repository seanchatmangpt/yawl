/**
 * H-Guards Violation: H_LIE Pattern
 *
 * This fixture contains documentation that doesn't match implementation
 * Expected: FAIL with pattern H_LIE (when semantic checks are enabled)
 */
package org.yawlfoundation.yawl.test.guards;

public class ViolationHLie {

    /**
     * Returns the work item ID.
     *
     * @return never null
     */
    public String getWorkItemId() {
        return null;  // Violates contract: claimed never null, but returns null
    }

    /**
     * Validates the input and throws exception if invalid.
     *
     * @throws IllegalArgumentException if input is invalid
     */
    public void validateInput(String input) {
        // Violates contract: claims to throw, but silently returns
        if (input == null) {
            return;
        }
    }

    /**
     * Processes data synchronously and returns result.
     *
     * @return processing result
     */
    public String processData(String data) {
        // Violates contract: claims synchronous, but returns async future
        new Thread(() -> System.out.println("Processing...")).start();
        return null;
    }

    /**
     * Retrieves all items from cache (fast operation).
     */
    public void retrieveItems() {
        // Violates contract: claimed to be fast cache operation, makes network call
        makeNetworkRequest();
    }

    private void makeNetworkRequest() {
        System.out.println("Making network request...");
    }
}
