package org.yawlfoundation.yawl.stateless.monitor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.stateless.monitor.YCaseMonitoringService.CaseStatistics;
import org.yawlfoundation.yawl.stateless.monitor.YCaseMonitoringService.TaskMetrics;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for YCaseMonitoringService.
 *
 * @author YAWL Development Team
 * @since 5.2
 */
public class TestYCaseMonitoringService {

    private YCaseMonitor caseMonitor;
    private YCaseMonitoringService monitoringService;

    @BeforeEach
    void setUp() {
        caseMonitor = new YCaseMonitor(0);
        monitoringService = new YCaseMonitoringService(caseMonitor);
    }

    /**
     * Test creating service with null monitor throws exception.
     */
    @Test
    void testNullMonitorThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> new YCaseMonitoringService(null),
                "Should throw IllegalArgumentException for null monitor");
    }

    /**
     * Test getting statistics for empty monitor.
     */
    @Test
    void testGetStatisticsEmpty() {
        CaseStatistics stats = monitoringService.getCaseStatistics();

        assertNotNull(stats, "Statistics should not be null");
        assertEquals(0, stats.totalCases, "Total cases should be 0");
        assertEquals(0, stats.activeCases, "Active cases should be 0");
        assertEquals(0, stats.completedCases, "Completed cases should be 0");
        assertTrue(stats.timestamp > 0, "Timestamp should be set");
    }

    /**
     * Test getting work item distribution for empty monitor.
     */
    @Test
    void testGetWorkItemDistributionEmpty() {
        Map<String, Integer> distribution = monitoringService.getWorkItemDistribution();

        assertNotNull(distribution, "Distribution should not be null");
        assertTrue(distribution.isEmpty(), "Distribution should be empty");
    }

    /**
     * Test getting task performance for empty monitor.
     */
    @Test
    void testGetTaskPerformanceEmpty() {
        Map<String, TaskMetrics> performance = monitoringService.getTaskPerformance();

        assertNotNull(performance, "Performance map should not be null");
        assertTrue(performance.isEmpty(), "Performance map should be empty");
    }

    /**
     * Test TaskMetrics class.
     */
    @Test
    void testTaskMetrics() {
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
    @Test
    void testTaskMetricsWithDurations() {
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
    @Test
    void testTaskMetricsToString() {
        TaskMetrics metrics = new TaskMetrics("task-1");
        metrics.addDuration(150L);
        metrics.incrementCount();

        String str = metrics.toString();

        assertTrue(str.contains("task-1"), "String should contain task ID");
        assertTrue(str.contains("count=1"), "String should contain count");
    }

    /**
     * Test CaseStatistics toString.
     */
    @Test
    void testCaseStatisticsToString() {
        CaseStatistics stats = new CaseStatistics();
        stats.totalCases = 10;
        stats.activeCases = 5;
        stats.completedCases = 4;
        stats.cancelledCases = 1;
        stats.avgCompletionTimeMs = 1500.5;

        String str = stats.toString();

        assertTrue(str.contains("total=10"), "String should contain total");
        assertTrue(str.contains("active=5"), "String should contain active");
        assertTrue(str.contains("completed=4"), "String should contain completed");
        assertTrue(str.contains("cancelled=1"), "String should contain cancelled");
    }

    /**
     * Test clearing metrics cache.
     */
    @Test
    void testClearCache() {
        monitoringService.clearCache();
        CaseStatistics stats = monitoringService.getCaseStatistics();
        assertNotNull(stats, "Statistics should still be available after cache clear");
    }

    /**
     * Test getting slowest cases with no cases.
     */
    @Test
    void testGetSlowestCasesEmpty() {
        var slowest = monitoringService.getSlowestCases(5);

        assertNotNull(slowest, "Result should not be null");
        assertTrue(slowest.isEmpty(), "Result should be empty");
    }

    /**
     * Test getting slowest cases with limit.
     */
    @Test
    void testGetSlowestCasesWithLimit() {
        var slowest = monitoringService.getSlowestCases(10);

        assertNotNull(slowest, "Result should not be null");
        assertTrue(slowest.size() <= 10, "Result size should be <= limit");
    }
}
