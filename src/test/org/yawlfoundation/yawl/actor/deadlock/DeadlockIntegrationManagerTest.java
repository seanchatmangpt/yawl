package org.yawlfoundation.yawl.actor.deadlock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for DeadlockIntegrationManager
 *
 * @author Deadlock Detection Team
 * @version 1.0
 */
@Execution(ExecutionMode.CONCURRENT)
public class DeadlockIntegrationManagerTest {

    private DeadlockIntegrationManager manager;
    private TestEventBus eventBus;
    private RealMetricsCollector metrics;
    private AdaptiveThresholdManager thresholds;
    private TestNotificationService notifications;

    @BeforeEach
    void setUp() {
        manager = new DeadlockIntegrationManager();
        eventBus = new TestEventBus();
        metrics = new RealMetricsCollector();
        thresholds = new AdaptiveThresholdManager();
        notifications = new TestNotificationService();

        manager.setEventBus(eventBus);
        manager.setMetricsCollector(metrics);
        manager.setThresholdManager(thresholds);
        manager.setNotificationService(notifications);
        manager.initialize();
    }

    @Test
    @DisplayName("Coordinates between detection and recovery systems")
    void testDetectionRecoveryCoordination() {
        // Create deadlock event
        DeadlockEvent detectionEvent = new DeadlockEvent(
            "DEADLOCK_DETECTED",
            Instant.now(),
            "Circular dependency detected",
            Set.of("actor-A", "actor-B", "actor-C")
        );

        // Send event to manager
        manager.processEvent(detectionEvent);

        // Verify coordination
        assertTrue(eventBus.eventsProcessed > 0, "Should process detection events");
        assertEquals(1, metrics.getDetectionCount(), "Should track detection count");
        assertTrue(notifications.notificationsSent > 0, "Should send notifications");
    }

    @Test
    @DisplayName("Manages system load balancing during high traffic")
    void testLoadBalancing() {
        // Simulate high traffic scenario
        for (int i = 0; i < 100; i++) {
            DeadlockEvent event = new DeadlockEvent(
                "ACTIVITY_TRACK",
                Instant.now(),
                "Activity track event",
                Set.of("actor-" + i % 10)
            );
            manager.processEvent(event);
        }

        // Verify load balancing
        assertTrue(metrics.getAverageProcessingTime() < 1000,
                   "Should maintain reasonable processing times");
        assertTrue(eventBus.eventsProcessed == 100, "Should process all events");
        assertFalse(metrics.isSystemOverloaded(), "System should not be overloaded");
    }

    @Test
    @DisplayName("Adapts thresholds based on system behavior")
    void testAdaptiveThresholds() {
        // Initially low threshold
        assertEquals(10, thresholds.getThreshold("DEADLOCK_DETECTED"));

        // Process high volume of events
        for (int i = 0; i < 50; i++) {
            DeadlockEvent event = new DeadlockEvent(
                "DEADLOCK_DETECTED",
                Instant.now(),
                "Test deadlock",
                Set.of("actor-A")
            );
            manager.processEvent(event);
        }

        // Verify threshold adaptation
        assertTrue(thresholds.getThreshold("DEADLOCK_DETECTED") > 10,
                   "Should adapt threshold based on load");
    }

    @Test
    @DisplayName("Integrates with existing YAWL components")
    void testYawlIntegration() {
        // Test YAWL-specific event processing
        DeadlockEvent yawlEvent = new DeadlockEvent(
            "YAWL_WORKFLOW_EVENT",
            Instant.now(),
            "Workflow execution event",
            Set.of("workflow-123")
        );

        manager.processEvent(yawlEvent);

        // Verify integration
        assertTrue(metrics.getEventCount("YAWL_WORKFLOW_EVENT") > 0,
                   "Should track YAWL-specific events");
        assertEquals(1, notifications.notificationsSent,
                   "Should send notifications for YAWL events");
    }

    @Test
    @DisplayName("Handles event ordering and deduplication")
    void testEventOrdering() {
        // Create multiple events for same deadlock
        DeadlockEvent event1 = new DeadlockEvent(
            "DEADLOCK_DETECTED",
            Instant.now(),
            "First detection",
            Set.of("actor-A", "actor-B")
        );

        DeadlockEvent event2 = new DeadlockEvent(
            "DEADLOCK_DETECTED",
            Instant.now().plusMillis(100),
            "Duplicate detection",
            Set.of("actor-A", "actor-B")
        );

        // Process events
        manager.processEvent(event1);
        manager.processEvent(event2);

        // Verify deduplication
        assertEquals(1, metrics.getUniqueDeadlockCount(),
                   "Should deduplicate identical deadlocks");
        assertTrue(eventBus.eventsProcessed >= 1, "Should process at least one event");
    }

    @Test
    @DisplayName("Provides performance monitoring and metrics")
    void testPerformanceMonitoring() {
        // Process test events
        for (int i = 0; i < 20; i++) {
            DeadlockEvent event = new DeadlockEvent(
                "ACTIVITY_TRACK",
                Instant.now(),
                "Performance test event",
                Set.of("actor-A")
            );
            manager.processEvent(event);
        }

        // Verify metrics collection
        assertTrue(metrics.getAverageProcessingTime() > 0,
                   "Should track processing time");
        assertTrue(metrics.getThroughput() > 0, "Should calculate throughput");
        assertTrue(metrics.getErrorRate() >= 0, "Should track error rate");
    }

    @ParameterizedTest
    @MethodSource("eventScenarios")
    @DisplayName("Handles various event scenarios")
    void testEventScenarios(EventScenario scenario) {
        // Set up scenario
        scenario.setup(manager);

        // Execute scenario
        List<DeadlockEvent> events = scenario.generateEvents();
        events.forEach(manager::processEvent);

        // Verify results
        scenario.verify(manager, metrics, notifications);
    }

    @Test
    @DisplayName("Supports event replay for recovery scenarios")
    void testEventReplay() {
        // Create and process events
        List<DeadlockEvent> events = List.of(
            new DeadlockEvent("DEADLOCK_DETECTED", Instant.now(), "Event 1", Set.of("actor-A")),
            new DeadlockEvent("RECOVERY_SUCCESS", Instant.now().plusMillis(100), "Event 2", Set.of("actor-A"))
        );

        events.forEach(manager::processEvent);

        // Replay events for recovery verification
        boolean replaySuccess = manager.replayEvents(events);
        assertTrue(replaySuccess, "Event replay should succeed");
        assertEquals(2, metrics.getReplayCount(), "Should track replay count");
    }

    @Test
    @DisplayName("Manages notification preferences and routing")
    void testNotificationManagement() {
        // Set up notification preferences
        notifications.addPreference("alert-high", "email");
        notifications.addPreference("alert-medium", "slack");
        notifications.addPreference("alert-low", "dashboard");

        // Send deadlock alert
        DeadlockEvent alert = new DeadlockEvent(
            "DEADLOCK_DETECTED",
            Instant.now(),
            "High severity deadlock",
            Set.of("actor-A", "actor-B")
        );

        manager.processEvent(alert);

        // Verify notification routing
        assertTrue(notifications.deliveredTo("email"), "Should route to email");
        assertFalse(notifications.deliveredTo("sms"), "Should not route to SMS");
    }

    private static Stream<EventScenario> eventScenarios() {
        return Stream.of(
            new HighVolumeScenario(),
            new CriticalAlertScenario(),
            new PerformanceScenario(),
            new IntegrationScenario()
        );
    }
}

/**
 * Test implementation of EventBus
 */
class TestEventBus implements EventBus {
    private final BlockingQueue<DeadlockEvent> eventQueue = new LinkedBlockingQueue<>();
    public int eventsProcessed = 0;

    @Override
    public void publish(DeadlockEvent event) {
        eventQueue.add(event);
    }

    @Override
    public DeadlockEvent consume() throws InterruptedException {
        DeadlockEvent event = eventQueue.poll(1, TimeUnit.SECONDS);
        if (event != null) {
            eventsProcessed++;
        }
        return event;
    }

    @Override
    public int getQueueSize() {
        return eventQueue.size();
    }
}

/**
 * Test implementation of MetricsCollector
 */
class RealMetricsCollector implements MetricsCollector {
    private final Map<String, Integer> eventCounts = new ConcurrentHashMap<>();
    private final List<Long> processingTimes = new CopyOnWriteArrayList<>();
    private final Map<String, Integer> errorCounts = new ConcurrentHashMap<>();
    private int detectionCount = 0;
    private int replayCount = 0;

    @Override
    public void recordEvent(DeadlockEvent event) {
        eventCounts.merge(event.getType(), 1, Integer::sum);
    }

    @Override
    public void recordProcessingTime(long duration) {
        processingTimes.add(duration);
    }

    @Override
    public void recordError(String eventType, Throwable error) {
        errorCounts.merge(eventType, 1, Integer::sum);
    }

    @Override
    public double getAverageProcessingTime() {
        return processingTimes.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0);
    }

    @Override
    public double getThroughput() {
        long totalTime = processingTimes.stream().mapToLong(Long::longValue).sum();
        return totalTime > 0 ? processingTimes.size() / (totalTime / 1000.0) : 0;
    }

    @Override
    public double getErrorRate() {
        int totalEvents = eventCounts.values().stream().mapToInt(Integer::intValue).sum();
        int totalErrors = errorCounts.values().stream().mapToInt(Integer::intValue).sum();
        return totalEvents > 0 ? (double) totalErrors / totalEvents : 0;
    }

    @Override
    public void incrementDetectionCount() {
        detectionCount++;
    }

    @Override
    public void incrementReplayCount() {
        replayCount++;
    }

    public int getEventCount(String eventType) {
        return eventCounts.getOrDefault(eventType, 0);
    }

    public int getDetectionCount() {
        return detectionCount;
    }

    public int getReplayCount() {
        return replayCount;
    }

    public boolean isSystemOverloaded() {
        return getThroughput() < 10 || getErrorRate() > 0.1;
    }
}

/**
 * Test implementation of NotificationService
 */
class TestNotificationService implements NotificationService {
    private final Map<String, String> preferences = new ConcurrentHashMap<>();
    private final List<String> deliveredNotifications = new CopyOnWriteArrayList<>();
    public int notificationsSent = 0;

    @Override
    public void sendNotification(DeadlockEvent event, String recipient) {
        notificationsSent++;
        deliveredNotifications.add(recipient);
    }

    @Override
    public void addPreference(String alertType, String channel) {
        preferences.put(alertType, channel);
    }

    @Override
    public List<String> getRecipients(DeadlockEvent event) {
        return preferences.entrySet().stream()
            .filter(entry -> event.getMessage().contains(entry.getKey()))
            .map(Map.Entry::getValue)
            .toList();
    }

    public void addPreference(String level, String channel) {
        preferences.put(level, channel);
    }

    public boolean deliveredTo(String channel) {
        return deliveredNotifications.contains(channel);
    }
}

/**
 * Test scenario implementations
 */
interface EventScenario {
    void setup(DeadlockIntegrationManager manager);
    List<DeadlockEvent> generateEvents();
    void verify(DeadlockIntegrationManager manager, MetricsCollector metrics, NotificationService notifications);
}

class HighVolumeScenario implements EventScenario {
    @Override
    public void setup(DeadlockIntegrationManager manager) {
        // Set high processing capacity
        manager.setProcessingCapacity(100);
    }

    @Override
    public List<DeadlockEvent> generateEvents() {
        return IntStream.range(0, 100)
            .mapToObj(i -> new DeadlockEvent(
                "ACTIVITY_TRACK",
                Instant.now(),
                "High volume event " + i,
                Set.of("actor-" + (i % 10))
            ))
            .collect(Collectors.toList());
    }

    @Override
    public void verify(DeadlockIntegrationManager manager, MetricsCollector metrics, NotificationService notifications) {
        assertEquals(100, metrics.getEventCount("ACTIVITY_TRACK"),
                   "Should process all high volume events");
        assertTrue(metrics.getThroughput() > 0, "Should maintain good throughput");
    }
}

class CriticalAlertScenario implements EventScenario {
    @Override
    public void setup(DeadlockIntegrationManager manager) {
        // Set critical alert threshold
        manager.setCriticalThreshold(5);
    }

    @Override
    public List<DeadlockEvent> generateEvents() {
        return List.of(
            new DeadlockEvent("DEADLOCK_DETECTED", Instant.now(), "Critical alert", Set.of("actor-A")),
            new DeadlockEvent("DEADLOCK_DETECTED", Instant.now(), "Critical alert", Set.of("actor-B")),
            new DeadlockEvent("DEADLOCK_DETECTED", Instant.now(), "Critical alert", Set.of("actor-C"))
        );
    }

    @Override
    public void verify(DeadlockIntegrationManager manager, MetricsCollector metrics, NotificationService notifications) {
        assertTrue(notifications.deliveredTo("email"), "Should deliver critical alerts via email");
        assertEquals(3, metrics.getEventCount("DEADLOCK_DETECTED"),
                   "Should track all critical alerts");
    }
}

class PerformanceScenario implements EventScenario {
    @Override
    public void setup(DeadlockIntegrationManager manager) {
        // Enable performance monitoring
        manager.enablePerformanceMonitoring(true);
    }

    @Override
    public List<DeadlockEvent> generateEvents() {
        return IntStream.range(0, 50)
            .mapToObj(i -> new DeadlockEvent(
                "PERFORMANCE_TEST",
                Instant.now(),
                "Performance test event " + i,
                Set.of("actor-A")
            ))
            .collect(Collectors.toList());
    }

    @Override
    public void verify(DeadlockIntegrationManager manager, MetricsCollector metrics, NotificationService notifications) {
        assertTrue(metrics.getAverageProcessingTime() > 0,
                   "Should track processing time");
        assertTrue(metrics.getThroughput() > 0, "Should calculate throughput");
    }
}

class IntegrationScenario implements EventScenario {
    @Override
    public void setup(DeadlockIntegrationManager manager) {
        // Enable YAWL integration
        manager.enableYawlIntegration(true);
    }

    @Override
    public List<DeadlockEvent> generateEvents() {
        return List.of(
            new DeadlockEvent("YAWL_WORKFLOW_EVENT", Instant.now(), "YAWL workflow", Set.of("workflow-123")),
            new DeadlockEvent("YAWL_NET_EVENT", Instant.now(), "YAWL net", Set.of("net-456")),
            new DeadlockEvent("YAWL_TASK_EVENT", Instant.now(), "YAWL task", Set.of("task-789"))
        );
    }

    @Override
    public void verify(DeadlockIntegrationManager manager, MetricsCollector metrics, NotificationService notifications) {
        assertEquals(1, metrics.getEventCount("YAWL_WORKFLOW_EVENT"),
                   "Should track YAWL workflow events");
        assertEquals(1, metrics.getEventCount("YAWL_NET_EVENT"),
                   "Should track YAWL net events");
        assertEquals(1, metrics.getEventCount("YAWL_TASK_EVENT"),
                   "Should track YAWL task events");
    }
}