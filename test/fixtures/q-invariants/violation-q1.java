/**
 * Q-Invariants Violation: Q1 Pattern - Missing throw or fake return
 *
 * This fixture violates Q invariant: real_impl ∨ throw UnsupportedOperationException.
 * The method returns a fake/default value instead of either:
 *   1. Implementing real logic
 *   2. Throwing UnsupportedOperationException
 *
 * Expected: FAIL with pattern Q1 on method with missing logic and fake return
 */
package org.yawlfoundation.yawl.test.invariants;

public class ViolationQ1 {

    /**
     * This method is supposed to fetch work items, but returns empty list
     * instead of either implementing real fetching OR throwing exception.
     */
    public java.util.List<WorkItem> fetchWorkItems() {
        return java.util.Collections.emptyList();
    }

    /**
     * This method pretends to execute a task but does nothing.
     */
    public boolean executeTask(String taskId) {
        return false;
    }

    /**
     * This method pretends to get status but returns hardcoded value.
     */
    public String getTaskStatus(String taskId) {
        return "PENDING";
    }

    /**
     * Interface for test purposes.
     */
    interface WorkItem {
        String getId();
    }
}
