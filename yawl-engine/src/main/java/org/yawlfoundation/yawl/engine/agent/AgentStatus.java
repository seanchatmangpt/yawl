package org.yawlfoundation.yawl.engine.agent;

/**
 * Sealed hierarchy representing agent operational status.
 * Uses Java 17+ sealed classes for exhaustive pattern matching.
 *
 * @since Java 17
 */
public sealed interface AgentStatus {

    /**
     * Agent is actively processing work items.
     */
    final class Running implements AgentStatus {
        public static final Running INSTANCE = new Running();

        private Running() {}

        @Override
        public String toString() {
            return "RUNNING";
        }
    }

    /**
     * Agent is idle, waiting for work.
     */
    final class Idle implements AgentStatus {
        public static final Idle INSTANCE = new Idle();

        private Idle() {}

        @Override
        public String toString() {
            return "IDLE";
        }
    }

    /**
     * Agent has encountered a fatal error.
     * Heartbeat renewal has failed or critical exception occurred.
     */
    final class Failed implements AgentStatus {
        private final String reason;
        private final long failureTimestamp;

        public Failed(String reason) {
            this.reason = reason != null ? reason : "Unknown error";
            this.failureTimestamp = System.currentTimeMillis();
        }

        public String reason() {
            return reason;
        }

        public long failureTimestamp() {
            return failureTimestamp;
        }

        @Override
        public String toString() {
            return "FAILED(" + reason + ")";
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Failed other)) return false;
            return reason.equals(other.reason) &&
                   failureTimestamp == other.failureTimestamp;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(reason, failureTimestamp);
        }
    }

    /**
     * Static factory methods for creating status instances.
     */
    static AgentStatus running() {
        return Running.INSTANCE;
    }

    static AgentStatus idle() {
        return Idle.INSTANCE;
    }

    static AgentStatus failed(String reason) {
        return new Failed(reason);
    }
}
