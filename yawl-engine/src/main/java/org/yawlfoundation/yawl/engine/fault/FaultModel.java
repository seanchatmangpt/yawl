/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.engine.fault;

/**
 * Enumeration of fault types for Armstrong-style fault injection testing.
 *
 * <p>Based on Joe Armstrong's fault model from Erlang/OTP, these represent
 * the fundamental categories of failures that distributed systems must handle.
 * Each fault type models a specific failure mode that can occur in production
 * workflow engine deployments.</p>
 *
 * <h2>Armstrong's Fault Categories (Erlang/OTP Philosophy)</h2>
 * <ul>
 *   <li><b>PROCESS_CRASH</b>: Sudden process termination (analogous to Erlang process exit)</li>
 *   <li><b>MESSAGE_LOSS</b>: Network partition or message drop (analogous to lost Erlang messages)</li>
 *   <li><b>MESSAGE_CORRUPTION</b>: Data integrity failure (Byzantine failure lite)</li>
 *   <li><b>TIMING_FAILURE</b>: Timeout or slow response (analogous to network latency)</li>
 *   <li><b>BYZANTINE_FAILURE</b>: Arbitrary/malicious behavior (worst-case fault model)</li>
 * </ul>
 *
 * <h2>Application to Workflow Engines</h2>
 * <p>In the context of YAWL workflow engines, these faults map to:
 * <ul>
 *   <li>PROCESS_CRASH → Thread death, OOM, JVM crash</li>
 *   <li>MESSAGE_LOSS → Lost work items, missed state transitions</li>
 *   <li>MESSAGE_CORRUPTION → Corrupted case data, malformed XML</li>
 *   <li>TIMING_FAILURE → Database timeouts, slow external services</li>
 *   <li>BYZANTINE_FAILURE → Invalid state mutations, race conditions</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 * @see FaultInjector
 * @see CrashPoint
 */
public enum FaultModel {

    /**
     * Process crash: Sudden termination of a workflow execution thread.
     *
     * <p>Simulates:
     * <ul>
     *   <li>Thread.interrupt() followed by termination</li>
     *   <li>OutOfMemoryError in work item processing</li>
     *   <li>Sudden JVM termination (SIGKILL)</li>
     * </ul>
     *
     * <p><b>Recovery expectation</b>: Case state should be recoverable from
     * last checkpoint or persisted snapshot. Work items in-flight may be lost
     * but should not corrupt case state.
     */
    PROCESS_CRASH(
        "Process Crash",
        "Sudden thread/process termination (Erlang: process exit)",
        Severity.CRITICAL,
        RecoveryStrategy.CHECKPOINT_RESTORE
    ),

    /**
     * Message loss: Network partition or message drop.
     *
     * <p>Simulates:
     * <ul>
     *   <li>Lost work item notifications</li>
     *   <li>Missing state transition events</li>
     *   <li>Network timeout without retry</li>
     * </ul>
     *
     * <p><b>Recovery expectation</b>: System should eventually detect lost
     * messages via heartbeat or timeout and retransmit/recover.
     */
    MESSAGE_LOSS(
        "Message Loss",
        "Network partition or dropped message (Erlang: lost message)",
        Severity.HIGH,
        RecoveryStrategy.RETRANSMIT
    ),

    /**
     * Message corruption: Data integrity failure.
     *
     * <p>Simulates:
     * <ul>
     *   <li>Corrupted case data in transit</li>
     *   <li>Malformed XML payloads</li>
     *   <li>Bit flips in serialized state</li>
     * </ul>
     *
     * <p><b>Recovery expectation</b>: System should detect corruption via
     * checksums and reject invalid data with appropriate error.
     */
    MESSAGE_CORRUPTION(
        "Message Corruption",
        "Data integrity failure (Byzantine failure lite)",
        Severity.HIGH,
        RecoveryStrategy.VALIDATION_REJECT
    ),

    /**
     * Timing failure: Timeout or slow response.
     *
     * <p>Simulates:
     * <ul>
     *   <li>Database query timeout</li>
     *   <li>Slow external service calls</li>
     *   <li>GC pauses causing deadline misses</li>
     * </ul>
     *
     * <p><b>Recovery expectation</b>: System should have configurable timeouts
     * with retry logic and fallback paths.
     */
    TIMING_FAILURE(
        "Timing Failure",
        "Timeout or slow response (Erlang: network latency)",
        Severity.MEDIUM,
        RecoveryStrategy.TIMEOUT_RETRY
    ),

    /**
     * Byzantine failure: Arbitrary/malicious behavior.
     *
     * <p>Simulates:
     * <ul>
     *   <li>Invalid state mutations</li>
     *   <li>Race conditions exposing inconsistent views</li>
     *   <li>Concurrent modification without synchronization</li>
     * </ul>
     *
     * <p><b>Recovery expectation</b>: System should detect inconsistent state
     * via invariants and either repair or fail-safe.
     */
    BYZANTINE_FAILURE(
        "Byzantine Failure",
        "Arbitrary/malicious behavior (worst-case fault model)",
        Severity.CRITICAL,
        RecoveryStrategy.CONSISTENCY_CHECK
    );

    private final String displayName;
    private final String description;
    private final Severity severity;
    private final RecoveryStrategy defaultRecovery;

    FaultModel(String displayName, String description, Severity severity,
               RecoveryStrategy defaultRecovery) {
        this.displayName = displayName;
        this.description = description;
        this.severity = severity;
        this.defaultRecovery = defaultRecovery;
    }

    /**
     * Returns the human-readable display name for this fault type.
     *
     * @return Display name (e.g., "Process Crash")
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns a detailed description of this fault type and its Erlang/OTP analogy.
     *
     * @return Description with Erlang context
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the severity level of this fault type.
     *
     * @return Severity (CRITICAL, HIGH, MEDIUM, LOW)
     */
    public Severity getSeverity() {
        return severity;
    }

    /**
     * Returns the default recovery strategy for this fault type.
     *
     * @return RecoveryStrategy enum value
     */
    public RecoveryStrategy getDefaultRecovery() {
        return defaultRecovery;
    }

    /**
     * Severity levels for fault types.
     */
    public enum Severity {
        /**
         * Critical: System-wide impact, may require restart.
         */
        CRITICAL(4),

        /**
         * High: Significant impact, may affect multiple cases.
         */
        HIGH(3),

        /**
         * Medium: Moderate impact, affects single case/work item.
         */
        MEDIUM(2),

        /**
         * Low: Minor impact, easily recoverable.
         */
        LOW(1);

        private final int level;

        Severity(int level) {
            this.level = level;
        }

        /**
         * Returns the numeric severity level (1-4).
         *
         * @return Severity level
         */
        public int getLevel() {
            return level;
        }
    }

    /**
     * Recovery strategies for fault types.
     */
    public enum RecoveryStrategy {
        /**
         * Restore from last checkpoint or snapshot.
         */
        CHECKPOINT_RESTORE("Restore from checkpoint"),

        /**
         * Retransmit lost message.
         */
        RETRANSMIT("Retransmit message"),

        /**
         * Reject invalid data with validation error.
         */
        VALIDATION_REJECT("Reject invalid data"),

        /**
         * Retry with exponential backoff.
         */
        TIMEOUT_RETRY("Retry with backoff"),

        /**
         * Run consistency check and repair.
         */
        CONSISTENCY_CHECK("Verify and repair");

        private final String description;

        RecoveryStrategy(String description) {
            this.description = description;
        }

        /**
         * Returns the human-readable description of this recovery strategy.
         *
         * @return Recovery strategy description
         */
        public String getDescription() {
            return description;
        }
    }
}
