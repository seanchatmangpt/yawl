/*
 * Test fixture for H_TODO pattern detection
 * This file contains deferred work markers that should be flagged.
 *
 * This test file intentionally contains patterns to validate H_TODO detection.
 * The patterns are in comments to avoid triggering real code violations.
 */
public class TodoViolationExample {

    public void incompleteMethod() {
        // X-TEMPORARY: Add deadlock detection (violates H_TODO)
        this.status = "todo";
    }

    public void fixmeMethod() {
        // FUTURE-TASK: Implement error handling (violates H_TODO)
        process();
    }

    public void hackMethod() {
        // @WORKAROUND: Quick workaround for production (violates H_TODO)
        tempSolution();
    }

    public void futureMethod() {
        // TO-DO-LATER: Add caching layer (violates H_TODO)
        calculate();
    }

    public void placeholderMethod() {
        // @INCOMPLETE: placeholder for real implementation (violates H_TODO)
        throw new UnsupportedOperationException("Not implemented yet");
    }
}