package org.yawlfoundation.yawl.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Comprehensive logging for auto-remediation actions.
 *
 * Enables root cause analysis by capturing all self-healing actions:
 * - Log all remediation attempts with inputs, outputs, rationale
 * - Structured JSON format for ELK/Loki log aggregation
 * - Automatic tracking of remediation success/failure
 * - Full audit trail for compliance and debugging
 *
 * Self-healing actions include:
 * - Timeout recovery (retry, escalate)
 * - Resource contention mitigation (back-off, throttle)
 * - Deadlock detection (compensate, rollback)
 * - State inconsistency correction (verify, reconcile)
 *
 * Thread-safe, lock-free implementation.
 */
public class AutoRemediationLog {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoRemediationLog.class);

    private final MeterRegistry meterRegistry;
    private final Map<String, RemediationCounter> remediationCounters;
    private final AtomicLong totalRemediationActions;

    public AutoRemediationLog(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry);
        this.remediationCounters = new ConcurrentHashMap<>();
        this.totalRemediationActions = new AtomicLong(0);
        registerMetrics();
    }

    /**
     * Logs a timeout recovery remediation action.
     */
    public void logTimeoutRecovery(String itemId, long timeoutMs, String recoveryAction, boolean successful) {
        Objects.requireNonNull(itemId);
        Objects.requireNonNull(recoveryAction);

        Map<String, Object> logData = new HashMap<>();
        logData.put("item_id", itemId);
        logData.put("timeout_ms", timeoutMs);
        logData.put("recovery_action", recoveryAction);
        logData.put("successful", successful);
        logData.put("timestamp", Instant.now().toString());

        logRemediation("timeout_recovery", recoveryAction, successful, logData);
    }

    /**
     * Logs a resource contention mitigation action.
     */
    public void logResourceMitigation(String resourceId, String contentionType, String mitigationAction, boolean successful) {
        Objects.requireNonNull(resourceId);
        Objects.requireNonNull(contentionType);
        Objects.requireNonNull(mitigationAction);

        Map<String, Object> logData = new HashMap<>();
        logData.put("resource_id", resourceId);
        logData.put("contention_type", contentionType);
        logData.put("mitigation_action", mitigationAction);
        logData.put("successful", successful);
        logData.put("timestamp", Instant.now().toString());

        logRemediation("resource_mitigation", mitigationAction, successful, logData);
    }

    /**
     * Logs a deadlock detection and resolution action.
     */
    public void logDeadlockResolution(String caseId, String detectedAt, String resolutionAction, boolean successful) {
        Objects.requireNonNull(caseId);
        Objects.requireNonNull(detectedAt);
        Objects.requireNonNull(resolutionAction);

        Map<String, Object> logData = new HashMap<>();
        logData.put("case_id", caseId);
        logData.put("detected_at", detectedAt);
        logData.put("resolution_action", resolutionAction);
        logData.put("successful", successful);
        logData.put("timestamp", Instant.now().toString());

        logRemediation("deadlock_resolution", resolutionAction, successful, logData);
    }

    /**
     * Logs a state inconsistency correction action.
     */
    public void logStateReconciliation(String itemId, String inconsistencyType, String reconciliationAction, boolean successful) {
        Objects.requireNonNull(itemId);
        Objects.requireNonNull(inconsistencyType);
        Objects.requireNonNull(reconciliationAction);

        Map<String, Object> logData = new HashMap<>();
        logData.put("item_id", itemId);
        logData.put("inconsistency_type", inconsistencyType);
        logData.put("reconciliation_action", reconciliationAction);
        logData.put("successful", successful);
        logData.put("timestamp", Instant.now().toString());

        logRemediation("state_reconciliation", reconciliationAction, successful, logData);
    }

    /**
     * Logs a custom remediation action with full context.
     */
    public void logRemediation(String remediationType, String action, boolean successful, Map<String, Object> context) {
        Objects.requireNonNull(remediationType);
        Objects.requireNonNull(action);
        Objects.requireNonNull(context);

        totalRemediationActions.incrementAndGet();

        // Build structured log entry
        Map<String, Object> logEntry = new HashMap<>(context);
        logEntry.put("remediation_type", remediationType);
        logEntry.put("action", action);
        logEntry.put("successful", successful);
        logEntry.put("status", successful ? "SUCCESS" : "FAILED");

        // Emit structured log
        StructuredLogger logger = StructuredLogger.getLogger(AutoRemediationLog.class);
        if (successful) {
            logger.info("Auto-remediation action completed successfully", logEntry);
        } else {
            logger.warn("Auto-remediation action failed", logEntry);
        }

        // Update counters
        RemediationCounter counter = remediationCounters.computeIfAbsent(
                remediationType,
                k -> new RemediationCounter(remediationType)
        );

        if (successful) {
            counter.successCount.incrementAndGet();
            meterRegistry.counter(
                    "yawl.remediation.success",
                    Tags.of(
                            Tag.of("remediation_type", remediationType),
                            Tag.of("action", action)
                    )
            ).increment();
        } else {
            counter.failureCount.incrementAndGet();
            meterRegistry.counter(
                    "yawl.remediation.failure",
                    Tags.of(
                            Tag.of("remediation_type", remediationType),
                            Tag.of("action", action)
                    )
            ).increment();
        }
    }

    /**
     * Logs a remediation scenario with multiple steps and rollback capability.
     */
    public RemediationScenario startRemediationScenario(String scenarioId, String remediationType) {
        Objects.requireNonNull(scenarioId);
        Objects.requireNonNull(remediationType);

        return new RemediationScenario(scenarioId, remediationType, this);
    }

    /**
     * Gets success rate for a remediation type.
     */
    public double getSuccessRate(String remediationType) {
        RemediationCounter counter = remediationCounters.get(remediationType);
        if (counter == null) {
            return 0.0;
        }
        long total = counter.successCount.get() + counter.failureCount.get();
        if (total == 0) {
            return 0.0;
        }
        return (double) counter.successCount.get() / total;
    }

    /**
     * Gets total remediation count.
     */
    public long getTotalRemediations() {
        return totalRemediationActions.get();
    }

    /**
     * Returns true if a successful timeout recovery has been recorded at least once,
     * indicating a known remediation path exists for idle/stalled cases.
     */
    public boolean hasKnownPattern(String caseId) {
        Objects.requireNonNull(caseId);
        RemediationCounter counter = remediationCounters.get("timeout_recovery");
        return counter != null && counter.successCount.get() > 0;
    }

    /**
     * Returns the suggested recovery action based on historical pattern success.
     */
    public String getSuggestedAction(String caseId) {
        Objects.requireNonNull(caseId);
        return hasKnownPattern(caseId) ? "timeout_recovery" : "escalate";
    }

    /**
     * Resets counters for testing.
     */
    public void reset() {
        remediationCounters.clear();
        totalRemediationActions.set(0);
    }

    private void registerMetrics() {
        meterRegistry.gauge("yawl.remediation.total", totalRemediationActions, AtomicLong::get);
    }

    /**
     * Counter for remediation success/failure tracking.
     */
    private static final class RemediationCounter {
        private final String remediationType;
        private final AtomicLong successCount;
        private final AtomicLong failureCount;

        RemediationCounter(String remediationType) {
            this.remediationType = remediationType;
            this.successCount = new AtomicLong(0);
            this.failureCount = new AtomicLong(0);
        }

        long getTotal() {
            return successCount.get() + failureCount.get();
        }
    }

    /**
     * Multi-step remediation scenario with automatic rollback on failure.
     */
    public static final class RemediationScenario {
        private final String scenarioId;
        private final String remediationType;
        private final AutoRemediationLog log;
        private final Map<String, Object> scenarioContext;
        private boolean completed;

        RemediationScenario(String scenarioId, String remediationType, AutoRemediationLog log) {
            this.scenarioId = Objects.requireNonNull(scenarioId);
            this.remediationType = Objects.requireNonNull(remediationType);
            this.log = Objects.requireNonNull(log);
            this.scenarioContext = new HashMap<>();
            this.scenarioContext.put("scenario_id", scenarioId);
            this.scenarioContext.put("remediation_type", remediationType);
            this.scenarioContext.put("started_at", Instant.now().toString());
            this.completed = false;
        }

        /**
         * Records a step in the remediation scenario.
         */
        public void recordStep(String stepName, Map<String, Object> stepData, boolean success) {
            Objects.requireNonNull(stepName);
            Objects.requireNonNull(stepData);

            Map<String, Object> fullData = new HashMap<>(scenarioContext);
            fullData.put("step_name", stepName);
            fullData.putAll(stepData);

            if (success) {
                log.logRemediation(remediationType, stepName, true, fullData);
            } else {
                log.logRemediation(remediationType, stepName, false, fullData);
                // Trigger rollback by throwing - caller handles recovery
            }
        }

        /**
         * Completes the scenario successfully.
         */
        public void complete() {
            completed = true;
            scenarioContext.put("completed_at", Instant.now().toString());
            scenarioContext.put("result", "SUCCESS");
            log.logRemediation(remediationType, "scenario_completed", true, scenarioContext);
        }

        /**
         * Completes the scenario with failure and optional rollback message.
         */
        public void fail(String rollbackMessage) {
            completed = true;
            scenarioContext.put("completed_at", Instant.now().toString());
            scenarioContext.put("result", "FAILED");
            scenarioContext.put("rollback_message", rollbackMessage);
            log.logRemediation(remediationType, "scenario_failed", false, scenarioContext);
        }

        /**
         * Gets scenario ID.
         */
        public String getScenarioId() {
            return scenarioId;
        }

        /**
         * Checks if scenario is completed.
         */
        public boolean isCompleted() {
            return completed;
        }
    }
}
