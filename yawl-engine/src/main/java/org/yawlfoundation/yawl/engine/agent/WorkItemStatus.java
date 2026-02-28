package org.yawlfoundation.yawl.engine.agent;

/**
 * Sealed hierarchy representing work item lifecycle status.
 * Uses Java 17+ sealed classes for exhaustive pattern matching.
 *
 * @since Java 17
 */
public sealed interface WorkItemStatus {

    /**
     * Work item is queued, awaiting assignment to an agent.
     */
    final class Pending implements WorkItemStatus {
        public static final Pending INSTANCE = new Pending();

        private Pending() {}

        @Override
        public String toString() {
            return "PENDING";
        }
    }

    /**
     * Work item has been assigned to an agent and is actively being processed.
     */
    final class Assigned implements WorkItemStatus {
        private final long assignmentTime;

        public Assigned() {
            this.assignmentTime = System.currentTimeMillis();
        }

        public long assignmentTime() {
            return assignmentTime;
        }

        @Override
        public String toString() {
            return "ASSIGNED";
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Assigned other)) return false;
            return assignmentTime == other.assignmentTime;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(assignmentTime);
        }
    }

    /**
     * Work item has been successfully completed by the assigned agent.
     */
    final class Completed implements WorkItemStatus {
        private final long completionTime;

        public Completed() {
            this.completionTime = System.currentTimeMillis();
        }

        public long completionTime() {
            return completionTime;
        }

        @Override
        public String toString() {
            return "COMPLETED";
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Completed other)) return false;
            return completionTime == other.completionTime;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(completionTime);
        }
    }

    /**
     * Work item processing has failed with an error.
     */
    final class Failed implements WorkItemStatus {
        private final String reason;
        private final long failureTime;

        public Failed(String reason) {
            this.reason = reason != null ? reason : "Unknown error";
            this.failureTime = System.currentTimeMillis();
        }

        public String reason() {
            return reason;
        }

        public long failureTime() {
            return failureTime;
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
                   failureTime == other.failureTime;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(reason, failureTime);
        }
    }

    /**
     * Static factory methods for creating status instances.
     */
    static WorkItemStatus pending() {
        return Pending.INSTANCE;
    }

    static WorkItemStatus assigned() {
        return new Assigned();
    }

    static WorkItemStatus completed() {
        return new Completed();
    }

    static WorkItemStatus failed(String reason) {
        return new Failed(reason);
    }
}
