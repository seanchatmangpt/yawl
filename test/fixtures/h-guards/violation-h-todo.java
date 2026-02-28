/**
 * H-Guards Violation: H_TODO Pattern
 *
 * This fixture contains TODO/FIXME comments that should be detected by guards.
 * Expected: FAIL with pattern H_TODO on lines with deferred work markers
 */
package org.yawlfoundation.yawl.test.guards;

public class ViolationHTodo {

    // TODO: implement this method
    public void processWorkItem() {
        System.out.println("Not implemented yet");
    }

    // FIXME: add deadlock detection
    public void handleConcurrency() {
        System.out.println("Concurrency handling missing");
    }

    // @incomplete: this is just a placeholder
    public String getWorkItemStatus() {
        return "pending";
    }

    // @stub: needs proper implementation
    public void executeWorkflow() {
        System.out.println("Executing workflow");
    }
}
