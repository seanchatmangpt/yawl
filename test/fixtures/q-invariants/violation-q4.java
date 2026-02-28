/**
 * Q-Invariants Violation: Q4 Pattern - Documentation doesn't match implementation
 *
 * This fixture violates Q invariant where documentation (Javadoc) claims one behavior
 * but implementation does something different. Code must match documentation.
 *
 * Expected: FAIL with pattern Q4 on documentation/implementation mismatch
 */
package org.yawlfoundation.yawl.test.invariants;

public class ViolationQ4 {

    /**
     * Returns a non-null work item or throws exception.
     *
     * @return never null
     * @throws WorkItemNotFoundException if work item not found
     */
    public WorkItem getWorkItem(String id) {
        // VIOLATION: Documentation says never null, but code returns null
        return null;
    }

    /**
     * Executes the workflow immediately and synchronously.
     *
     * @throws ExecutionException if workflow execution fails
     */
    public void executeWorkflow(String workflowId) {
        // VIOLATION: Documentation says throws ExecutionException, but no throw statement
        System.out.println("Would execute workflow " + workflowId);
    }

    /**
     * Validates that the workflow ID is not empty.
     *
     * @return true if valid, false if invalid
     * @throws IllegalArgumentException if validation fails
     */
    public boolean validateWorkflowId(String workflowId) {
        // VIOLATION: Documentation says throws exception, but returns boolean instead
        return !workflowId.isEmpty();
    }

    /**
     * Clears all cached data. After this call, cache is empty.
     */
    public void clearCache() {
        // VIOLATION: Documentation says clears cache, but doesn't do anything
    }

    static class WorkItem {
        String id;
    }

    static class WorkItemNotFoundException extends Exception {}
    static class ExecutionException extends Exception {}
}
