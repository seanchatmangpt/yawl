package org.yawlfoundation.yawl.mcp.a2a.service;

/**
 * Represents the state of a circuit breaker for an MCP server connection.
 *
 * <p>This sealed interface models the three possible states of a circuit breaker:
 * CLOSED (normal operation), OPEN (rejecting calls), and HALF_OPEN (testing recovery).</p>
 *
 * <p>State transitions follow the circuit breaker pattern:</p>
 * <ul>
 *   <li>CLOSED -&gt; OPEN: When failure rate exceeds threshold</li>
 *   <li>OPEN -&gt; HALF_OPEN: After wait duration expires</li>
 *   <li>HALF_OPEN -&gt; CLOSED: When test calls succeed</li>
 *   <li>HALF_OPEN -&gt; OPEN: When test calls fail</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public sealed interface McpCircuitBreakerState
    permits McpCircuitBreakerState.Closed,
            McpCircuitBreakerState.Open,
            McpCircuitBreakerState.HalfOpen {

    /**
     * Gets the name of the MCP server this state belongs to.
     *
     * @return the server name
     */
    String serverName();

    /**
     * Gets the timestamp when this state was entered.
     *
     * @return the epoch millis when state was entered
     */
    long enteredAtMillis();

    /**
     * Gets the current state name as a string.
     *
     * @return state name (CLOSED, OPEN, or HALF_OPEN)
     */
    String stateName();

    /**
     * Closed state - normal operation, calls are allowed.
     */
    record Closed(
        String serverName,
        long enteredAtMillis,
        int failureCount,
        int successCount,
        int totalCalls,
        double currentFailureRate
    ) implements McpCircuitBreakerState {

        /**
         * Creates a new Closed state.
         *
         * @param serverName the server name
         * @return new Closed state instance
         */
        public static Closed create(String serverName) {
            return new Closed(serverName, System.currentTimeMillis(), 0, 0, 0, 0.0);
        }

        @Override
        public String stateName() {
            return "CLOSED";
        }

        /**
         * Records a successful call.
         *
         * @param windowSize the sliding window size
         * @return new state with updated metrics
         */
        public Closed recordSuccess(int windowSize) {
            int newSuccessCount = successCount + 1;
            int newTotalCalls = Math.min(totalCalls + 1, windowSize);
            int newFailureCount = totalCalls >= windowSize ? Math.max(0, failureCount - 1) : failureCount;
            double newFailureRate = newTotalCalls > 0 ? (double) newFailureCount / newTotalCalls * 100 : 0.0;
            return new Closed(serverName, enteredAtMillis, newFailureCount, newSuccessCount, newTotalCalls, newFailureRate);
        }

        /**
         * Records a failed call.
         *
         * @param windowSize the sliding window size
         * @return new state with updated metrics
         */
        public Closed recordFailure(int windowSize) {
            int newFailureCount = failureCount + 1;
            int newTotalCalls = Math.min(totalCalls + 1, windowSize);
            int newSuccessCount = totalCalls >= windowSize ? Math.max(0, successCount - 1) : successCount;
            double newFailureRate = newTotalCalls > 0 ? (double) newFailureCount / newTotalCalls * 100 : 0.0;
            return new Closed(serverName, enteredAtMillis, newFailureCount, newSuccessCount, newTotalCalls, newFailureRate);
        }
    }

    /**
     * Open state - calls are rejected, waiting to transition to half-open.
     */
    record Open(
        String serverName,
        long enteredAtMillis,
        long waitDurationMillis,
        int failureCountAtOpen,
        String lastFailureMessage
    ) implements McpCircuitBreakerState {

        /**
         * Creates a new Open state.
         *
         * @param serverName the server name
         * @param waitDurationMillis the wait duration before transitioning to half-open
         * @param failureCount the failure count when circuit opened
         * @param lastFailureMessage the message from the last failure
         * @return new Open state instance
         */
        public static Open create(String serverName, long waitDurationMillis,
                                  int failureCount, String lastFailureMessage) {
            return new Open(serverName, System.currentTimeMillis(), waitDurationMillis,
                           failureCount, lastFailureMessage);
        }

        @Override
        public String stateName() {
            return "OPEN";
        }

        /**
         * Checks if the wait duration has elapsed.
         *
         * @return true if ready to transition to half-open
         */
        public boolean shouldTransitionToHalfOpen() {
            return System.currentTimeMillis() >= (enteredAtMillis + waitDurationMillis);
        }

        /**
         * Gets the remaining wait time in milliseconds.
         *
         * @return remaining wait time, or 0 if elapsed
         */
        public long remainingWaitMillis() {
            long remaining = (enteredAtMillis + waitDurationMillis) - System.currentTimeMillis();
            return Math.max(0, remaining);
        }
    }

    /**
     * Half-open state - testing if the service has recovered.
     */
    record HalfOpen(
        String serverName,
        long enteredAtMillis,
        int permittedCalls,
        int successfulCalls,
        int failedCalls,
        int callsRemaining
    ) implements McpCircuitBreakerState {

        /**
         * Creates a new HalfOpen state.
         *
         * @param serverName the server name
         * @param permittedCalls the number of test calls permitted
         * @return new HalfOpen state instance
         */
        public static HalfOpen create(String serverName, int permittedCalls) {
            return new HalfOpen(serverName, System.currentTimeMillis(),
                               permittedCalls, 0, 0, permittedCalls);
        }

        @Override
        public String stateName() {
            return "HALF_OPEN";
        }

        /**
         * Records a successful test call.
         *
         * @return new state with updated metrics
         */
        public HalfOpen recordSuccess() {
            return new HalfOpen(serverName, enteredAtMillis, permittedCalls,
                               successfulCalls + 1, failedCalls, callsRemaining - 1);
        }

        /**
         * Records a failed test call.
         *
         * @return new state with updated metrics
         */
        public HalfOpen recordFailure() {
            return new HalfOpen(serverName, enteredAtMillis, permittedCalls,
                               successfulCalls, failedCalls + 1, callsRemaining - 1);
        }

        /**
         * Checks if all permitted calls have been made.
         *
         * @return true if all test calls completed
         */
        public boolean allCallsCompleted() {
            return callsRemaining <= 0;
        }

        /**
         * Checks if enough test calls succeeded to close the circuit.
         *
         * @return true if should transition to closed
         */
        public boolean shouldTransitionToClosed() {
            return allCallsCompleted() && failedCalls == 0;
        }

        /**
         * Checks if any test call failed.
         *
         * @return true if should transition back to open
         */
        public boolean shouldTransitionToOpen() {
            return failedCalls > 0;
        }
    }
}
