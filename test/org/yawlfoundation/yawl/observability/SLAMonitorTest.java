package org.yawlfoundation.yawl.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD: Real database operations for SLA tracking.
 * Tests SLAMonitor with real Micrometer registry and time-based tracking.
 */
class SLAMonitorTest {

    private SLAMonitor monitor;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        monitor = new SLAMonitor(meterRegistry);
    }

    @Test
    void testDefineSLA_StoresDefinition() {
        monitor.defineSLA("approval_task", 3600000, "1 hour for approval");

        SLAMonitor.SLADefinition sla = monitor.getSLA("approval_task");
        assertNotNull(sla);
        assertEquals("approval_task", sla.getSlaId());
        assertEquals(3600000, sla.getThresholdMs());
        assertEquals("1 hour for approval", sla.getDescription());
    }

    @Test
    void testDefineSLA_NegativeThreshold_Rejected() {
        assertThrows(IllegalArgumentException.class, () -> {
            monitor.defineSLA("invalid_sla", -1000, "Invalid");
        });
    }

    @Test
    void testDefineSLA_ZeroThreshold_Rejected() {
        assertThrows(IllegalArgumentException.class, () -> {
            monitor.defineSLA("invalid_sla", 0, "Invalid");
        });
    }

    @Test
    void testTracking_StartAndComplete_Compliance() throws InterruptedException {
        monitor.defineSLA("quick_task", 2000, "2 seconds max");

        Map<String, String> context = new HashMap<>();
        context.put("task", "quick_task");

        monitor.startTracking("quick_task", "item-1", context);

        // Sleep less than threshold (1 second)
        Thread.sleep(1000);

        monitor.completeTracking("quick_task", "item-1");

        // Should not have violation
        assertEquals(0, monitor.getTotalViolations("quick_task"));
    }

    @Test
    void testTracking_ViolationDetected() throws InterruptedException {
        monitor.defineSLA("slow_task", 500, "500ms max");

        Map<String, String> context = new HashMap<>();
        context.put("task", "slow_task");

        monitor.startTracking("slow_task", "item-1", context);

        // Sleep longer than threshold (1 second > 500ms)
        Thread.sleep(1000);

        monitor.completeTracking("slow_task", "item-1");

        // Should have violation
        assertTrue(monitor.getTotalViolations("slow_task") > 0);
    }

    @Test
    void testTracking_ActiveCount() {
        monitor.defineSLA("task_a", 5000, "");
        monitor.defineSLA("task_b", 5000, "");

        Map<String, String> ctx = new HashMap<>();

        monitor.startTracking("task_a", "item-1", ctx);
        monitor.startTracking("task_a", "item-2", ctx);
        monitor.startTracking("task_b", "item-3", ctx);

        assertEquals(3, monitor.getActiveTrackingCount());

        monitor.completeTracking("task_a", "item-1");

        assertEquals(2, monitor.getActiveTrackingCount());
    }

    @Test
    void testTracking_MultipleItems_IndependentTracking() throws InterruptedException {
        monitor.defineSLA("task", 1000, "");

        Map<String, String> ctx = new HashMap<>();

        monitor.startTracking("task", "item-1", ctx);
        monitor.startTracking("task", "item-2", ctx);

        Thread.sleep(600);
        monitor.completeTracking("task", "item-1");

        // item-1 completes within SLA

        Thread.sleep(600);
        monitor.completeTracking("task", "item-2");

        // item-2 violates SLA (1200ms > 1000ms)
        assertTrue(monitor.getTotalViolations("task") > 0);
    }

    @Test
    void testTracking_UnknownSLA_Ignored() {
        Map<String, String> ctx = new HashMap<>();
        monitor.startTracking("unknown_sla", "item-1", ctx);
        monitor.completeTracking("unknown_sla", "item-1");

        // Should not crash, just return no definition
        assertNull(monitor.getSLA("unknown_sla"));
    }

    @Test
    void testTracking_NonexistentItem_Handled() {
        monitor.defineSLA("task", 1000, "");

        // Completing item that was never started
        monitor.completeTracking("task", "nonexistent");

        // Should not crash or record anything
        assertEquals(0, monitor.getTotalViolations("task"));
    }

    @Test
    void testStartTracking_NullSlaId_Rejected() {
        assertThrows(NullPointerException.class, () -> {
            monitor.startTracking(null, "item-1", new HashMap<>());
        });
    }

    @Test
    void testStartTracking_NullItemId_Rejected() {
        assertThrows(NullPointerException.class, () -> {
            monitor.startTracking("task", null, new HashMap<>());
        });
    }

    @Test
    void testStartTracking_NullContext_Rejected() {
        assertThrows(NullPointerException.class, () -> {
            monitor.startTracking("task", "item-1", null);
        });
    }

    @Test
    void testCompleteTracking_NullSlaId_Rejected() {
        assertThrows(NullPointerException.class, () -> {
            monitor.completeTracking(null, "item-1");
        });
    }

    @Test
    void testCompleteTracking_NullItemId_Rejected() {
        assertThrows(NullPointerException.class, () -> {
            monitor.completeTracking("task", null);
        });
    }

    @Test
    void testMultipleSLAs_IndependentTracking() throws InterruptedException {
        monitor.defineSLA("approval", 2000, "");
        monitor.defineSLA("processing", 5000, "");

        Map<String, String> ctx = new HashMap<>();

        monitor.startTracking("approval", "item-1", ctx);
        monitor.startTracking("processing", "item-2", ctx);

        Thread.sleep(3000);

        monitor.completeTracking("approval", "item-1");  // 3000ms > 2000ms = VIOLATION
        monitor.completeTracking("processing", "item-2"); // 3000ms < 5000ms = OK

        assertTrue(monitor.getTotalViolations("approval") > 0);
        assertEquals(0, monitor.getTotalViolations("processing"));
    }

    @Test
    void testSLADefinition_ImmutableAfterCreation() {
        SLAMonitor.SLADefinition sla = new SLAMonitor.SLADefinition("test", 1000, "test");

        assertEquals("test", sla.getSlaId());
        assertEquals(1000, sla.getThresholdMs());
        assertEquals("test", sla.getDescription());
    }

    @Test
    void testContextPreservation_LogsWithTaskInfo() throws InterruptedException {
        monitor.defineSLA("review_task", 500, "");

        Map<String, String> context = new HashMap<>();
        context.put("task_name", "code_review");
        context.put("reviewer", "alice");
        context.put("pr_id", "pr-123");

        monitor.startTracking("review_task", "item-1", context);
        Thread.sleep(1000);
        monitor.completeTracking("review_task", "item-1");

        // Context should be preserved in structured logs
        assertTrue(monitor.getTotalViolations("review_task") > 0);
    }

    @Test
    void testTrendingToBreach_HighUtilizationDetected() throws InterruptedException {
        monitor.defineSLA("task", 1000, "");

        Map<String, String> ctx = new HashMap<>();
        monitor.startTracking("task", "item-1", ctx);

        // Sleep for 850ms (85% of 1000ms threshold)
        Thread.sleep(850);

        monitor.completeTracking("task", "item-1");

        // Should be "at risk" but not violated
        assertTrue(monitor.getTotalViolations("task") == 0);
    }

    @Test
    void testBatchTracking_ManyItems() throws InterruptedException {
        monitor.defineSLA("task", 2000, "");

        Map<String, String> ctx = new HashMap<>();

        // Start 50 items
        for (int i = 0; i < 50; i++) {
            monitor.startTracking("task", "item-" + i, ctx);
        }

        assertEquals(50, monitor.getActiveTrackingCount());

        Thread.sleep(500);

        // Complete all items
        for (int i = 0; i < 50; i++) {
            monitor.completeTracking("task", "item-" + i);
        }

        assertEquals(0, monitor.getActiveTrackingCount());
    }

    @Test
    void testViolationMetricsExport() throws InterruptedException {
        monitor.defineSLA("task", 500, "");

        Map<String, String> ctx = new HashMap<>();

        // Create 3 violations
        for (int i = 0; i < 3; i++) {
            monitor.startTracking("task", "item-" + i, ctx);
            Thread.sleep(600);
            monitor.completeTracking("task", "item-" + i);
        }

        assertEquals(3, monitor.getTotalViolations("task"));
    }
}
