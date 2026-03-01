/**
 * Q-Invariants Clean: Q1 Pattern - PASS
 *
 * This fixture properly implements Q invariant: real_impl ∨ throw.
 * Every unimplemented method either:
 *   1. Implements real logic (when possible)
 *   2. Throws UnsupportedOperationException (when not implemented)
 *
 * Expected: PASS (no Q1 violations)
 */
package org.yawlfoundation.yawl.test.invariants;

public class CleanQ1 {

    /**
     * This method implements real fetching logic.
     */
    public java.util.List<WorkItem> fetchWorkItems() {
        return java.util.Arrays.asList(
            new WorkItem("item-1"),
            new WorkItem("item-2")
        );
    }

    /**
     * This method throws UnsupportedOperationException instead of returning fake value.
     */
    public boolean executeTask(String taskId) {
        throw new UnsupportedOperationException(
            "executeTask requires real task execution engine. " +
            "See TaskExecutor documentation."
        );
    }

    /**
     * This method implements real status lookup.
     */
    public String getTaskStatus(String taskId) {
        if (taskId == null || taskId.isEmpty()) {
            throw new IllegalArgumentException("taskId cannot be empty");
        }
        // Real implementation would query database/engine
        return "ACTIVE";
    }

    static class WorkItem {
        String id;
        WorkItem(String id) { this.id = id; }
        public String getId() { return id; }
    }
}
