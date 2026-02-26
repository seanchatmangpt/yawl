import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.observability.SLODashboard;
import org.yawlfoundation.yawl.observability.SLOTracker;
import org.yawlfoundation.yawl.observability.AndonCord;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test to verify SLODashboard implementation works
 */
public class SLODashboardSimpleTest {

    @Test
    void testSLODashboardInitialization() {
        // Create dependencies
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        AndonCord andonCord = AndonCord.getInstance();
        SLOTracker sloTracker = new SLOTracker(meterRegistry, andonCord);

        // Test dashboard creation
        SLODashboard dashboard = new SLODashboard(sloTracker, meterRegistry);

        // Verify it's not null
        assertNotNull(dashboard);

        // Test initial snapshot
        var snapshot = dashboard.getCurrentSnapshot();
        assertNotNull(snapshot);
        assertNotNull(snapshot.timestamp());
        assertNotNull(snapshot.statusMap());
        assertFalse(snapshot.statusMap().isEmpty());

        System.out.println("SLODashboard initialized successfully!");
        System.out.println("Initial snapshot: " + snapshot.overallStatus());
        System.out.println("Timestamp: " + snapshot.timestamp());
        System.out.println("Status map size: " + snapshot.statusMap().size());
    }

    @Test
    void testDashboardStartStop() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        AndonCord andonCord = AndonCord.getInstance();
        SLOTracker sloTracker = new SLOTracker(meterRegistry, andonCord);
        SLODashboard dashboard = new SLODashboard(sloTracker, meterRegistry);

        // Test start
        dashboard.start();
        assertEquals(SLODashboard.Status.RUNNING, dashboard.getStatus());

        // Record some data
        sloTracker.recordCaseCompletion("test-case-1", 1000, Map.of("type", "test"));

        // Get snapshot
        var snapshot = dashboard.getCurrentSnapshot();
        assertNotNull(snapshot);

        // Test stop
        dashboard.stop();
        assertEquals(SLODashboard.Status.STOPPED, dashboard.getStatus());

        System.out.println("Dashboard start/stop test passed!");
    }

    @Test
    void testReportGeneration() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        AndonCord andonCord = AndonCord.getInstance();
        SLOTracker sloTracker = new SLOTracker(meterRegistry, andonCord);
        SLODashboard dashboard = new SLODashboard(sloTracker, meterRegistry);

        // Record some data first
        sloTracker.recordCaseCompletion("case-report-1", 1000, Map.of("type", "report"));

        // Test HTML report generation
        String htmlReport = dashboard.generateHtmlReport();
        assertNotNull(htmlReport);
        assertTrue(htmlReport.contains("YAWL SLO Dashboard"));
        assertTrue(htmlReport.contains("<!DOCTYPE html>"));

        // Test JSON report generation
        Instant from = Instant.now().minusHours(1);
        Instant to = Instant.now();
        String jsonReport = dashboard.generateJsonReport(from, to);
        assertNotNull(jsonReport);
        assertTrue(jsonReport.contains("\"timestamp\""));
        assertTrue(jsonReport.contains("\"current\""));

        System.out.println("HTML report length: " + htmlReport.length());
        System.out.println("JSON report length: " + jsonReport.length());
        System.out.println("Report generation test passed!");
    }

    @Test
    void testCaseRecording() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        AndonCord andonCord = AndonCord.getInstance();
        SLOTracker sloTracker = new SLOTracker(meterRegistry, andonCord);
        SLODashboard dashboard = new SLODashboard(sloTracker, meterRegistry);

        dashboard.start();

        // Record cases with different durations
        dashboard.recordCaseCompletion("case-1", 500, Map.of("priority", "high"));
        dashboard.recordCaseCompletion("case-2", 2000, Map.of("priority", "low"));
        dashboard.recordCaseCompletion("case-3", 800, Map.of("priority", "medium"));

        // Verify we can get snapshots
        var snapshot = dashboard.getCurrentSnapshot();
        assertNotNull(snapshot);
        assertEquals(SLODashboard.Status.RUNNING, dashboard.getStatus());

        dashboard.stop();

        System.out.println("Case recording test passed!");
        System.out.println("Final status: " + snapshot.overallStatus());
    }

    public static void main(String[] args) {
        SLODashboardSimpleTest test = new SLODashboardSimpleTest();

        try {
            test.testSLODashboardInitialization();
            test.testDashboardStartStop();
            test.testReportGeneration();
            test.testCaseRecording();

            System.out.println("\n🎉 All tests passed! SLODashboard implementation is working correctly.");
        } catch (Exception e) {
            System.err.println("❌ Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}