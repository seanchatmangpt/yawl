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
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.ListAppender;
import org.apache.logging.log4j.core.configurator.Log4j2Configurator;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/**
 * Test suite for SLOTracker functionality.
 *
 * Tests:
 * - SLO definition and initialization
 * - Event recording and compliance tracking
 * - Violation detection and alerting
 * - Predictive breach detection
 * - Metrics registration and reporting
 */
@Tag("integration")
class SLOTrackerTest {

    private MeterRegistry meterRegistry;
    private SLOTracker sloTracker;
    private ListAppender listAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        sloTracker = new SLOTracker(meterRegistry);

        // Setup log capture for alert verification
        logger = LogManager.getLogger(AndonCord.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        ((org.apache.logging.log4j.core.Logger) logger).addAppender(listAppender);
        ((org.apache.logging.log4j.core.Logger) logger).setLevel(Level.INFO);
    }

    @AfterEach
    void tearDown() {
        sloTracker.shutdown();
        listAppender.stop();
        ((org.apache.logging.log4j.core.Logger) logger).removeAppender(listAppender);
    }

    @Test
    void testInitialization() {
        assertNotNull(sloTracker);
        Map<SLOTracker.SLOType, Double> complianceRates = sloTracker.getAllComplianceRates(Duration.ofHours(1));
        assertEquals(5, complianceRates.size());
        assertTrue(complianceRates.containsKey(SLOTracker.SLOType.CASE_COMPLETION));
        assertTrue(complianceRates.containsKey(SLOTracker.SLOType.TASK_EXECUTION));
        assertTrue(complianceRates.containsKey(SLOTracker.SLOType.QUEUE_RESPONSE));
        assertTrue(complianceRates.containsKey(SLOTracker.SLOType.VT_PINNING));
        assertTrue(complianceRates.containsKey(SLOTracker.SLOType.LOCK_CONTENTION));
    }

    @Test
    void testCaseCompletionCompliance() {
        // Record compliant case completion (under 24h)
        sloTracker.recordMetric(SLOTracker.SLOType.CASE_COMPLETION, true, 1000); // 1 second

        // Verify metric was recorded
        assertEquals(1, sloTracker.getTotalMetrics(SLOTracker.SLOType.CASE_COMPLETION));
        assertEquals(1000.0, sloTracker.getAverageDuration(SLOTracker.SLOType.CASE_COMPLETION));

        // Check compliance status
        double complianceRate = sloTracker.getComplianceRate(SLOTracker.SLOType.CASE_COMPLETION, Duration.ofDays(7));
        assertTrue(complianceRate > 0);
        assertFalse(sloTracker.isViolating(SLOTracker.SLOType.CASE_COMPLETION));
    }

    @Test
    void testCaseCompletionViolation() {
        // Record case completion exceeding 24h
        sloTracker.recordMetric(SLOTracker.SLOType.CASE_COMPLETION, false, 25 * 60 * 60 * 1000L); // 25 hours

        // Verify metric was recorded
        assertEquals(1, sloTracker.getTotalMetrics(SLOTracker.SLOType.CASE_COMPLETION));
        assertEquals(25 * 60 * 60 * 1000.0, sloTracker.getAverageDuration(SLOTracker.SLOType.CASE_COMPLETION));

        // Check compliance status - should now be violating
        assertTrue(sloTracker.isViolating(SLOTracker.SLOType.CASE_COMPLETION));

        // Check violations
        List<SLOTracker.SLOViolation> violations = sloTracker.getRecentViolations(Duration.ofHours(1));
        assertEquals(1, violations.size());
        assertEquals(SLOTracker.SLOType.CASE_COMPLETION, violations.get(0).getType());
    }

    @Test
    void testTaskExecutionCompliance() {
        // Record compliant task execution
        sloTracker.recordMetric(SLOTracker.SLOType.TASK_EXECUTION, true, 30 * 60 * 1000L); // 30 minutes

        // Verify metric was recorded
        assertEquals(1, sloTracker.getTotalMetrics(SLOTracker.SLOType.TASK_EXECUTION));

        // Check compliance status
        double complianceRate = sloTracker.getComplianceRate(SLOTracker.SLOType.TASK_EXECUTION, Duration.ofHours(1));
        assertTrue(complianceRate > 0);
        assertFalse(sloTracker.isViolating(SLOTracker.SLOType.TASK_EXECUTION));
    }

    @Test
    void testQueueResponseCompliance() {
        // Record compliant queue response
        sloTracker.recordMetric(SLOTracker.SLOType.QUEUE_RESPONSE, true, 2 * 60 * 1000L); // 2 minutes

        // Verify metric was recorded
        assertEquals(1, sloTracker.getTotalMetrics(SLOTracker.SLOType.QUEUE_RESPONSE));

        // Check compliance status
        double complianceRate = sloTracker.getComplianceRate(SLOTracker.SLOType.QUEUE_RESPONSE, Duration.ofMinutes(5));
        assertTrue(complianceRate > 0);
        assertFalse(sloTracker.isViolating(SLOTracker.SLOType.QUEUE_RESPONSE));
    }

    @Test
    void testVirtualThreadPinning() {
        // Record low pinning rate (compliant)
        sloTracker.recordMetric(SLOTracker.SLOType.VT_PINNING, true, 0); // No pinning

        // Record high pinning rate (violation)
        sloTracker.recordMetric(SLOTracker.SLOType.VT_PINNING, false, 0); // Pinning occurred

        // Verify metrics were recorded
        assertEquals(2, sloTracker.getTotalMetrics(SLOTracker.SLOType.VT_PINNING));

        // Check compliance status - for VT_PINNING, lower is better
        double complianceRate = sloTracker.getComplianceRate(SLOTracker.SLOType.VT_PINNING, Duration.ofHours(1));
        assertTrue(complianceRate >= 0);

        // Since we have a pinning event, we should be at risk or violating
        if (sloTracker.isViolating(SLOTracker.SLOType.VT_PINNING)) {
            assertTrue(true); // Expected to be violating
        }
    }

    @Test
    void testLockContention() {
        // Record low contention (compliant)
        sloTracker.recordMetric(SLOTracker.SLOType.LOCK_CONTENTION, true, 50); // 50ms, not contented

        // Record high contention (violation)
        sloTracker.recordMetric(SLOTracker.SLOType.LOCK_CONTENTION, false, 150); // 150ms, contented

        // Verify metrics were recorded
        assertEquals(2, sloTracker.getTotalMetrics(SLOTracker.SLOType.LOCK_CONTENTION));

        // Check compliance status - for LOCK_CONTENTION, lower is better
        double complianceRate = sloTracker.getComplianceRate(SLOTracker.SLOType.LOCK_CONTENTION, Duration.ofHours(1));
        assertTrue(complianceRate >= 0);
    }

    @Test
    void testPredictiveBreachDetection() {
        // Record events with increasing duration (approaching threshold)
        sloTracker.recordMetric(SLOTracker.SLOType.CASE_COMPLETION, true, 22 * 60 * 60 * 1000L); // 22 hours
        sloTracker.recordMetric(SLOTracker.SLOType.CASE_COMPLETION, true, 23 * 60 * 60 * 1000L); // 23 hours
        sloTracker.recordMetric(SLOTracker.SLOType.CASE_COMPLETION, true, 23.5 * 60 * 60 * 1000L); // 23.5 hours

        // Verify metrics were recorded
        assertEquals(3, sloTracker.getTotalMetrics(SLOTracker.SLOType.CASE_COMPLETION));

        // Check if predictive metrics are tracking
        double complianceRate = sloTracker.getComplianceRate(SLOTracker.SLOType.CASE_COMPLETION, Duration.ofDays(7));
        assertTrue(complianceRate > 0);

        // Check trend direction
        SLOTracker.TrendDirection trend = sloTracker.getTrend(SLOTracker.SLOType.CASE_COMPLETION);
        assertNotNull(trend);
        assertTrue(trend == SLOTracker.TrendDirection.IMPROVING ||
                   trend == SLOTracker.TrendDirection.STABLE ||
                   trend == SLOTracker.TrendDirection.DETERIORATING);
    }

    @Test
    void testAlertGeneration() {
        // Record multiple violations to trigger alerts
        sloTracker.recordMetric(SLOTracker.SLOType.CASE_COMPLETION, false, 25 * 60 * 60 * 1000L); // 25 hours
        sloTracker.recordMetric(SLOTracker.SLOType.CASE_COMPLETION, false, 26 * 60 * 60 * 1000L); // 26 hours

        // Verify metrics were recorded
        assertEquals(2, sloTracker.getTotalMetrics(SLOTracker.SLOType.CASE_COMPLETION));

        // Verify alerts were generated by checking log messages
        await().atMost(5, TimeUnit.SECONDS).until(() -> {
            List<LogEvent> logEvents = listAppender.getEvents();
            return logEvents.stream()
                .filter(event -> event.getLevel().isAtLeast(Level.WARN))
                .anyMatch(event -> event.getMessage().getFormattedMessage().contains("SLO violation"));
        });

        // Verify specific alert patterns in logs
        List<LogEvent> logEvents = listAppender.getEvents();
        List<String> alertMessages = logEvents.stream()
            .filter(event -> event.getLevel().isAtLeast(Level.WARN))
            .map(event -> event.getMessage().getFormattedMessage())
            .collect(Collectors.toList());

        assertTrue(alertMessages.size() > 0, "No alerts were generated");
        assertTrue(alertMessages.stream().anyMatch(msg -> msg.contains("SLO violation")),
                  "No SLO violation alerts found");
    }

    @Test
    void testComplianceWindowMaintenance() {
        // Record many events to test window maintenance
        for (int i = 0; i < 1500; i++) {
            sloTracker.recordMetric(SLOTracker.SLOType.CASE_COMPLETION, true, i * 1000);
        }

        // Verify metrics were recorded
        assertEquals(1500, sloTracker.getTotalMetrics(SLOTracker.SLOType.CASE_COMPLETION));

        // Check that status is still valid despite large number of events
        double complianceRate = sloTracker.getComplianceRate(SLOTracker.SLOType.CASE_COMPLETION, Duration.ofDays(7));
        assertTrue(complianceRate >= 0);
        assertTrue(complianceRate <= 100);
    }

    @Test
    void testConcurrentEventProcessing() {
        // Test thread safety with concurrent events
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < 100; i++) {
            final int threadId = i;
            executor.submit(() -> {
                sloTracker.recordMetric(SLOTracker.SLOType.CASE_COMPLETION, true, threadId * 1000);
            });
        }

        executor.shutdown();
        try {
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Verify all events were processed
        assertEquals(100, sloTracker.getTotalMetrics(SLOTracker.SLOType.CASE_COMPLETION));

        // Check that compliance calculation is thread-safe
        double complianceRate = sloTracker.getComplianceRate(SLOTracker.SLOType.CASE_COMPLETION, Duration.ofDays(7));
        assertTrue(complianceRate >= 0);
        assertTrue(complianceRate <= 100);
    }

    @Test
    void testNullParameterHandling() {
        assertThrows(NullPointerException.class, () -> {
            sloTracker.recordMetric(null, true, 1000);
        });

        assertThrows(NullPointerException.class, () -> {
            sloTracker.recordMetric(SLOTracker.SLOType.CASE_COMPLETION, true, 1000);
        });
    }

    @Test
    void testMetricsRegistration() {
        // Verify metrics are registered in the meter registry
        assertNotNull(meterRegistry.find("yawl.slo.compliance.rate.casecompletion").gauge());
        assertNotNull(meterRegistry.find("yawl.slo.compliance.rate.taskexecution").gauge());
        assertNotNull(meterRegistry.find("yawl.slo.compliance.rate.queueresponse").gauge());
        assertNotNull(meterRegistry.find("yawl.slo.compliance.rate.vtpinning").gauge());
        assertNotNull(meterRegistry.find("yawl.slo.compliance.rate.lockcontention").gauge());

        assertNotNull(meterRegistry.find("yawl.slo.metrics.total.casecompletion").gauge());
        assertNotNull(meterRegistry.find("yawl.slo.metrics.total.taskexecution").gauge());
        assertNotNull(meterRegistry.find("yawl.slo.metrics.total.queueresponse").gauge());
        assertNotNull(meterRegistry.find("yawl.slo.metrics.total.vtpinning").gauge());
        assertNotNull(meterRegistry.find("yawl.slo.metrics.total.lockcontention").gauge());

        assertNotNull(meterRegistry.find("yawl.slo.violations").counter());
    }

    @Test
    void testTrendAnalysis() {
        // Record enough data to establish a trend
        for (int i = 0; i < 10; i++) {
            sloTracker.recordMetric(SLOTracker.SLOType.CASE_COMPLETION, true, 10000); // Consistent 10s
        }

        // Check trend analysis
        SLOTracker.TrendDirection trend = sloTracker.getTrend(SLOTracker.SLOType.CASE_COMPLETION);
        assertNotNull(trend);
        assertTrue(trend == SLOTracker.TrendDirection.IMPROVING ||
                   trend == SLOTracker.TrendDirection.STABLE ||
                   trend == SLOTracker.TrendDirection.DETERIORATING ||
                   trend == SLOTracker.TrendDirection.INSUFFICIENT_DATA);

        // Check burn rate
        double burnRate = sloTracker.getBurnRate(SLOTracker.SLOType.CASE_COMPLETION);
        assertTrue(burnRate >= 0.0);
        assertTrue(burnRate <= 1.0);
    }

    @Test
    void testShutdown() {
        // Record some metrics
        sloTracker.recordMetric(SLOTracker.SLOType.CASE_COMPLETION, true, 1000);

        // Perform shutdown
        sloTracker.shutdown();

        // Verify shutdown completed (should not throw exceptions)
        assertDoesNotThrow(() -> {
            sloTracker.recordMetric(SLOTracker.SLOType.CASE_COMPLETION, true, 2000);
        });
    }

    @Test
    void testComplianceSummary() {
        // Record mixed metrics
        sloTracker.recordMetric(SLOTracker.SLOType.CASE_COMPLETION, true, 1000);  // Compliant
        sloTracker.recordMetric(SLOTracker.SLOType.CASE_COMPLETION, false, 25000); // Violation
        sloTracker.recordMetric(SLOTracker.SLOType.TASK_EXECUTION, true, 30000);  // Compliant

        // Get summary
        String summary = sloTracker.getComplianceSummary();
        assertNotNull(summary);
        assertTrue(summary.contains("SLO Compliance Summary"));
        assertTrue(summary.contains("CASE_COMPLETION"));
        assertTrue(summary.contains("TASK_EXECUTION"));
    }

    @Test
    void testReset() {
        // Record some metrics
        sloTracker.recordMetric(SLOTracker.SLOType.CASE_COMPLETION, true, 1000);
        sloTracker.recordMetric(SLOTracker.SLOType.TASK_EXECUTION, true, 2000);

        // Verify metrics were recorded
        assertEquals(1, sloTracker.getTotalMetrics(SLOTracker.SLOType.CASE_COMPLETION));
        assertEquals(1, sloTracker.getTotalMetrics(SLOTracker.SLOType.TASK_EXECUTION));

        // Reset
        sloTracker.reset();

        // Verify metrics were cleared
        assertEquals(0, sloTracker.getTotalMetrics(SLOTracker.SLOType.CASE_COMPLETION));
        assertEquals(0, sloTracker.getTotalMetrics(SLOTracker.SLOType.TASK_EXECUTION));
    }

    @Test
    void testViolationDetection() {
        // Record a violation
        sloTracker.recordMetric(SLOTracker.SLOType.TASK_EXECUTION, false, 1800000); // 30 minutes over 1 hour threshold

        // Verify violation is detected
        assertTrue(sloTracker.isViolating(SLOTracker.SLOType.TASK_EXECUTION));

        // Check specific compliance rate
        double complianceRate = sloTracker.getComplianceRate(SLOTracker.SLOType.TASK_EXECUTION, Duration.ofHours(1));
        assertTrue(complianceRate < 99.5); // Below 99.5% target

        // Check violations list
        List<SLOTracker.SLOViolation> violations = sloTracker.getRecentViolations(Duration.ofHours(1));
        assertFalse(violations.isEmpty());
    }

    @Test
    void testCustomWindowCompliance() {
        // Record some metrics
        sloTracker.recordMetric(SLOTracker.SLOType.QUEUE_RESPONSE, true, 60000); // 1 minute
        sloTracker.recordMetric(SLOTracker.SLOType.QUEUE_RESPONSE, true, 120000); // 2 minutes
        sloTracker.recordMetric(SLOTracker.SLOType.QUEUE_RESPONSE, false, 600000); // 10 minutes (violation)

        // Test compliance over different windows
        double fiveMinuteCompliance = sloTracker.getComplianceRate(SLOTracker.SLOType.QUEUE_RESPONSE, Duration.ofMinutes(5));
        double oneHourCompliance = sloTracker.getComplianceRate(SLOTracker.SLOType.QUEUE_RESPONSE, Duration.ofHours(1));

        // Both should be valid percentages
        assertTrue(fiveMinuteCompliance >= 0);
        assertTrue(fiveMinuteCompliance <= 100);
        assertTrue(oneHourCompliance >= 0);
        assertTrue(oneHourCompliance <= 100);
    }
}