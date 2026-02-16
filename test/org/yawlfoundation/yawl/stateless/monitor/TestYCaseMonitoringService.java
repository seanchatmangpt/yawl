package org.yawlfoundation.yawl.stateless.monitor;

import junit.framework.TestCase;
import org.yawlfoundation.yawl.stateless.monitor.YCaseMonitoringService.CaseStatistics;
import org.yawlfoundation.yawl.stateless.monitor.YCaseMonitoringService.TaskMetrics;

import java.util.Map;

/**
 * Test cases for YCaseMonitoringService.
 *
 * @author YAWL Development Team
 * @since 5.2
 */
public class TestYCaseMonitoringService extends TestCase {

    private YCaseMonitor caseMonitor;
    private YCaseMonitoringService monitoringService;

    public TestYCaseMonitoringService(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
        super.setUp();
        caseMonitor = new YCaseMonitor(0);
        monitoringService = new YCaseMonitoringService(caseMonitor);
    }

    /**
     * Test creating service with null monitor throws exception.
     */
    public void testNullMonitorThrowsException() {
        try {
            new YCaseMonitoringService(null);
            fail("Should throw IllegalArgumentException for null monitor");
        }
        catch (IllegalArgumentException e) {
            assertTrue("Exception message should mention null",
                      e.getMessage().contains("null"));
        }
    }

    /**
     * Test getting statistics for empty monitor.
     */
    public void testGetStatisticsEmpty() {
        CaseStatistics stats = monitoringService.getCaseStatistics();

        assertNotNull("Statistics should not be null", stats);
        assertEquals("Total cases should be 0", 0, stats.totalCases);
        assertEquals("Active cases should be 0", 0, stats.activeCases);
        assertEquals("Completed cases should be 0", 0, stats.completedCases);
        assertTrue("Timestamp should be set", stats.timestamp > 0);
    }

    /**
     * Test getting work item distribution for empty monitor.
     */
    public void testGetWorkItemDistributionEmpty() {
        Map<String, Integer> distribution = monitoringService.getWorkItemDistribution();

        assertNotNull("Distribution should not be null", distribution);
        assertTrue("Distribution should be empty", distribution.isEmpty());
    }

    /**
     * Test getting task performance for empty monitor.
     */
    public void testGetTaskPerformanceEmpty() {
        Map<String, TaskMetrics> performance = monitoringService.getTaskPerformance();

        assertNotNull("Performance map should not be null", performance);
        assertTrue("Performance map should be empty", performance.isEmpty());
    }

    /**
     * Test TaskMetrics class.
     */
    public void testTaskMetrics() {
        TaskMetrics metrics = new TaskMetrics("task-1");

        assertEquals("task-1", metrics.getTaskID());
        assertEquals(0, metrics.getExecutionCount());
        assertEquals(0L, metrics.getAverageDurationMs());
        assertEquals(0L, metrics.getMaxDurationMs());
        assertEquals(0L, metrics.getMinDurationMs());
    }

    /**
     * Test TaskMetrics with duration data.
     */
    public void testTaskMetricsWithDurations() {
        TaskMetrics metrics = new TaskMetrics("task-1");

        metrics.addDuration(100L);
        metrics.incrementCount();

        metrics.addDuration(200L);
        metrics.incrementCount();

        metrics.addDuration(300L);
        metrics.incrementCount();

        assertEquals(3, metrics.getExecutionCount());
        assertEquals(200L, metrics.getAverageDurationMs());
        assertEquals(300L, metrics.getMaxDurationMs());
        assertEquals(100L, metrics.getMinDurationMs());
    }

    /**
     * Test TaskMetrics toString.
     */
    public void testTaskMetricsToString() {
        TaskMetrics metrics = new TaskMetrics("task-1");
        metrics.addDuration(150L);
        metrics.incrementCount();

        String str = metrics.toString();

        assertTrue("String should contain task ID", str.contains("task-1"));
        assertTrue("String should contain count", str.contains("count=1"));
    }

    /**
     * Test CaseStatistics toString.
     */
    public void testCaseStatisticsToString() {
        CaseStatistics stats = new CaseStatistics();
        stats.totalCases = 10;
        stats.activeCases = 5;
        stats.completedCases = 4;
        stats.cancelledCases = 1;
        stats.avgCompletionTimeMs = 1500.5;

        String str = stats.toString();

        assertTrue("String should contain total", str.contains("total=10"));
        assertTrue("String should contain active", str.contains("active=5"));
        assertTrue("String should contain completed", str.contains("completed=4"));
        assertTrue("String should contain cancelled", str.contains("cancelled=1"));
    }

    /**
     * Test clearing metrics cache.
     */
    public void testClearCache() {
        monitoringService.clearCache();
        CaseStatistics stats = monitoringService.getCaseStatistics();
        assertNotNull("Statistics should still be available after cache clear", stats);
    }

    /**
     * Test getting slowest cases with no cases.
     */
    public void testGetSlowestCasesEmpty() {
        var slowest = monitoringService.getSlowestCases(5);

        assertNotNull("Result should not be null", slowest);
        assertTrue("Result should be empty", slowest.isEmpty());
    }

    /**
     * Test getting slowest cases with limit.
     */
    public void testGetSlowestCasesWithLimit() {
        var slowest = monitoringService.getSlowestCases(10);

        assertNotNull("Result should not be null", slowest);
        assertTrue("Result size should be <= limit", slowest.size() <= 10);
    }
}
