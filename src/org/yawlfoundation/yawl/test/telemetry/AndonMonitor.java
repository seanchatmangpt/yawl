package org.yawlfoundation.yawl.test.telemetry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * ANDON (visual control) monitoring for multi-agent telemetry tests.
 *
 * Enforces production-style alerting:
 * - P0 CRITICAL: No LLM available → STOP immediately
 * - P1 HIGH: Agent timeout, deadlock detected → HALT agent
 * - P2 MEDIUM: SLA breaches, metric anomalies → Alert, continue
 * - P3 LOW: Informational → Log, no action
 *
 * Integrates with OpenTelemetry for metric tracking.
 */
public class AndonMonitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(AndonMonitor.class);

    private final MeterRegistry meterRegistry;
    private final List<AndonAlert> alerts = new ArrayList<>();

    // Counters per severity
    private final Counter criticalAlerts;
    private final Counter highAlerts;
    private final Counter mediumAlerts;
    private final Counter lowAlerts;
    private final Counter totalAlerts;

    public AndonMonitor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.criticalAlerts = Counter.builder("yawl.test.andon.critical")
                .description("P0 critical ANDON alerts")
                .register(meterRegistry);

        this.highAlerts = Counter.builder("yawl.test.andon.high")
                .description("P1 high priority ANDON alerts")
                .register(meterRegistry);

        this.mediumAlerts = Counter.builder("yawl.test.andon.medium")
                .description("P2 medium priority ANDON alerts")
                .register(meterRegistry);

        this.lowAlerts = Counter.builder("yawl.test.andon.low")
                .description("P3 low priority ANDON alerts")
                .register(meterRegistry);

        this.totalAlerts = Counter.builder("yawl.test.andon.total")
                .description("Total ANDON alerts fired")
                .register(meterRegistry);
    }

    /**
     * Check for LLM availability (critical gate).
     *
     * @param groqAvailable true if Groq API is accessible
     * @param openaiAvailable true if OpenAI API is accessible
     * @return true if at least one LLM available, false if both down
     * @throws IllegalStateException if no LLM available (ANDON violation)
     */
    public boolean checkLlmAvailability(boolean groqAvailable, boolean openaiAvailable)
            throws IllegalStateException {

        if (!groqAvailable && !openaiAvailable) {
            // P0 CRITICAL: Stop immediately
            fireAlert("LLM_UNAVAILABLE", "P0_CRITICAL",
                    "No LLM available (Groq + OpenAI both down). " +
                            "Cannot proceed with LLM-based tests.");
            throw new IllegalStateException(
                    "ANDON P0: No LLM available. Test execution HALTED.");
        }

        if (!groqAvailable) {
            // P1 HIGH: Groq down, OpenAI available
            fireAlert("GROQ_UNAVAILABLE", "P1_HIGH",
                    "Groq API unavailable. Using OpenAI fallback. " +
                            "Agent 5 performance tests may be skipped.");
        }

        if (!openaiAvailable) {
            // P1 HIGH: OpenAI down, Groq available
            fireAlert("OPENAI_UNAVAILABLE", "P1_HIGH",
                    "OpenAI API unavailable. Using Groq fallback. " +
                            "Agent 3 integration tests may be limited.");
        }

        return true;
    }

    /**
     * Check for violations in agent results.
     */
    public void checkAllViolations(AggregatedTestResults results) {
        LOGGER.info("Checking ANDON violations in aggregated results");

        var violations = results.getAndonViolations();
        if (!violations.isEmpty()) {
            for (var violation : violations) {
                fireAlert(violation.type, violation.severity, violation.message);
            }
        }

        // Check for test failures
        if (results.getTotalTestsFailed() > 0) {
            fireAlert("TEST_FAILURES", "P2_MEDIUM",
                    String.format("Total test failures: %d", results.getTotalTestsFailed()));
        }

        // Check pass rate
        if (results.getPassRate() < 95.0) {
            fireAlert("LOW_PASS_RATE", "P2_MEDIUM",
                    String.format("Pass rate below 95%%: %.2f%%", results.getPassRate()));
        }
    }

    /**
     * Record a violation from a specific agent.
     */
    public void recordViolation(String agentId, AgentTestResults results) {
        var violations = results.getViolations();
        for (var violation : violations) {
            fireAlert(violation.type, violation.severity,
                    String.format("[Agent %s] %s", agentId, violation.message));
        }
    }

    /**
     * Fire an ANDON alert.
     */
    private void fireAlert(String type, String severity, String message) {
        var alert = new AndonAlert(type, severity, message);
        alerts.add(alert);
        totalAlerts.increment();

        switch (severity) {
            case "P0_CRITICAL" -> {
                criticalAlerts.increment();
                LOGGER.error("ANDON P0 CRITICAL: {} - {}", type, message);
            }
            case "P1_HIGH" -> {
                highAlerts.increment();
                LOGGER.warn("ANDON P1 HIGH: {} - {}", type, message);
            }
            case "P2_MEDIUM" -> {
                mediumAlerts.increment();
                LOGGER.warn("ANDON P2 MEDIUM: {} - {}", type, message);
            }
            case "P3_LOW" -> {
                lowAlerts.increment();
                LOGGER.info("ANDON P3 LOW: {} - {}", type, message);
            }
            default -> LOGGER.info("ANDON {}: {} - {}", severity, type, message);
        }
    }

    /**
     * Check if any critical violations were fired.
     *
     * @return true if critical violations exist
     */
    public boolean hasCriticalViolations() {
        return alerts.stream()
                .anyMatch(a -> "P0_CRITICAL".equals(a.severity));
    }

    /**
     * Check if any high-priority violations were fired.
     */
    public boolean hasHighPriorityViolations() {
        return alerts.stream()
                .anyMatch(a -> a.severity.startsWith("P0") || a.severity.startsWith("P1"));
    }

    /**
     * Get all alerts.
     */
    public List<AndonAlert> getAlerts() {
        return new ArrayList<>(alerts);
    }

    /**
     * Get alerts by severity.
     */
    public List<AndonAlert> getAlertsBySeverity(String severity) {
        return alerts.stream()
                .filter(a -> severity.equals(a.severity))
                .toList();
    }

    /**
     * Get alert count by severity.
     */
    public long getAlertCount(String severity) {
        return alerts.stream()
                .filter(a -> severity.equals(a.severity))
                .count();
    }

    /**
     * Data class for ANDON alerts.
     */
    public static class AndonAlert {
        public final String type;
        public final String severity;
        public final String message;
        public final Instant timestamp;

        public AndonAlert(String type, String severity, String message) {
            this.type = type;
            this.severity = severity;
            this.message = message;
            this.timestamp = Instant.now();
        }

        @Override
        public String toString() {
            return String.format("[%s] %s: %s", severity, type, message);
        }
    }
}
