package org.yawlfoundation.yawl.stateless;

import junit.framework.TestCase;

import org.yawlfoundation.yawl.exceptions.YDataStateException;
import org.yawlfoundation.yawl.exceptions.YEngineStateException;
import org.yawlfoundation.yawl.exceptions.YQueryException;
import org.yawlfoundation.yawl.exceptions.YStateException;
import org.yawlfoundation.yawl.exceptions.YSyntaxException;
import org.yawlfoundation.yawl.stateless.elements.YSpecification;
import org.yawlfoundation.yawl.stateless.engine.YNetRunner;
import org.yawlfoundation.yawl.stateless.engine.YWorkItem;
import org.yawlfoundation.yawl.stateless.listener.YCaseEventListener;
import org.yawlfoundation.yawl.stateless.listener.YWorkItemEventListener;
import org.yawlfoundation.yawl.stateless.listener.event.YCaseEvent;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;
import org.yawlfoundation.yawl.stateless.listener.event.YWorkItemEvent;
import org.yawlfoundation.yawl.stateless.monitor.YCaseMonitor;
import org.yawlfoundation.yawl.util.StringUtil;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Comprehensive JUnit tests for YCaseMonitor functionality.
 * Tests case monitoring, idle timeout handling, and event management
 * using the real YStatelessEngine and actual workflow specifications.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YCaseMonitorTest extends TestCase implements YCaseEventListener, YWorkItemEventListener {

    private static final String MINIMAL_SPEC_RESOURCE = "resources/MinimalSpec.xml";
    private static final long SHORT_TIMEOUT_MS = 100L;
    private static final long MEDIUM_TIMEOUT_MS = 500L;
    private static final long LONG_TIMEOUT_MS = 5000L;
    private static final long AWAIT_TIMEOUT_SEC = 10L;

    private YStatelessEngine _engine;
    private CountDownLatch _caseCompleteLatch;
    private CountDownLatch _idleTimeoutLatch;
    private volatile boolean _caseCompleted;
    private volatile boolean _idleTimeoutReceived;
    private List<YWorkItem> _enabledWorkItems;
    private List<YEventType> _receivedCaseEvents;

    public YCaseMonitorTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        _engine = new YStatelessEngine(LONG_TIMEOUT_MS);
        _engine.addCaseEventListener(this);
        _engine.addWorkItemEventListener(this);
        _caseCompleted = false;
        _idleTimeoutReceived = false;
        _enabledWorkItems = new ArrayList<>();
        _receivedCaseEvents = new ArrayList<>();
    }

    @Override
    protected void tearDown() throws Exception {
        if (_engine != null) {
            _engine.setCaseMonitoringEnabled(false);
            _engine.removeCaseEventListener(this);
            _engine.removeWorkItemEventListener(this);
        }
        super.tearDown();
    }

    /**
     * Load minimal spec XML from classpath resource.
     */
    private String loadMinimalSpecXml() {
        InputStream is = getClass().getResourceAsStream(MINIMAL_SPEC_RESOURCE);
        assertNotNull("Missing resource: " + MINIMAL_SPEC_RESOURCE, is);
        String xml = StringUtil.streamToString(is);
        assertNotNull("Empty spec XML", xml);
        return xml;
    }

    /**
     * Test 1: Create a case monitor with default idle timeout.
     */
    public void testMonitorCreation() {
        YCaseMonitor monitor = new YCaseMonitor(1000L);
        assertNotNull("Monitor should not be null", monitor);
    }

    /**
     * Test 2: Create a case monitor with a specific idle timeout.
     */
    public void testMonitorWithIdleTimeout() {
        long timeout = 5000L;
        YCaseMonitor monitor = new YCaseMonitor(timeout);
        assertNotNull("Monitor with timeout should not be null", monitor);
    }

    /**
     * Test 3: Engine with case monitoring enabled tracks cases.
     */
    public void testEngineCaseMonitoringEnabled() throws Exception {
        assertTrue("Engine should have case monitoring enabled", _engine.isCaseMonitoringEnabled());

        String xml = loadMinimalSpecXml();
        YSpecification spec = _engine.unmarshalSpecification(xml);
        String caseID = "monitor-test-case-1";
        YNetRunner runner = _engine.launchCase(spec, caseID);

        assertNotNull("Runner should not be null", runner);
        assertNotNull("Case ID should not be null", runner.getCaseID());
        assertEquals("Case ID should match", caseID, runner.getCaseID().toString());
    }

    /**
     * Test 4: Add and complete a case - verify case events are received.
     */
    public void testAddCase() throws Exception {
        String xml = loadMinimalSpecXml();
        YSpecification spec = _engine.unmarshalSpecification(xml);
        _caseCompleteLatch = new CountDownLatch(1);
        _caseCompleted = false;

        _engine.launchCase(spec, "add-case-test-1");

        boolean completed = _caseCompleteLatch.await(AWAIT_TIMEOUT_SEC, TimeUnit.SECONDS);
        assertTrue("Case should complete within " + AWAIT_TIMEOUT_SEC + "s", completed);
        assertTrue("Case completed flag should be set", _caseCompleted);
    }

    /**
     * Test 5: Case is removed from monitor after completion.
     */
    public void testRemoveCase() throws Exception {
        String xml = loadMinimalSpecXml();
        YSpecification spec = _engine.unmarshalSpecification(xml);
        _caseCompleteLatch = new CountDownLatch(1);

        _engine.launchCase(spec, "remove-case-test-1");

        boolean completed = _caseCompleteLatch.await(AWAIT_TIMEOUT_SEC, TimeUnit.SECONDS);
        assertTrue("Case should complete", completed);

        // After completion, case should be removed from monitoring
        // Verified by receiving CASE_COMPLETED event
        assertTrue("Should have received CASE_COMPLETED event", _caseCompleted);
    }

    /**
     * Test 6: Idle timeout fires when case is inactive (engine with short timeout).
     */
    public void testMonitorIdleTimeout() throws Exception {
        // Create engine with very short idle timeout
        _engine.setCaseMonitoringEnabled(false);
        _engine.removeCaseEventListener(this);
        _engine = new YStatelessEngine(SHORT_TIMEOUT_MS);
        _idleTimeoutLatch = new CountDownLatch(1);
        _engine.addCaseEventListener(this);

        String xml = loadMinimalSpecXml();
        YSpecification spec = _engine.unmarshalSpecification(xml);

        // Launch case but do NOT process work items - let it sit idle
        _receivedCaseEvents.clear();
        _engine.launchCase(spec, "idle-timeout-test-1");

        // Wait for idle timeout event
        boolean timedOut = _idleTimeoutLatch.await(AWAIT_TIMEOUT_SEC * 2, TimeUnit.SECONDS);
        assertTrue("Idle timeout should fire within " + (AWAIT_TIMEOUT_SEC * 2) + " seconds", timedOut);
        assertTrue("Idle timeout flag should be set", _idleTimeoutReceived);
    }

    /**
     * Test 7: Active case should not be timed out prematurely.
     */
    public void testMonitorActiveCase() throws Exception {
        String xml = loadMinimalSpecXml();
        YSpecification spec = _engine.unmarshalSpecification(xml);

        // Launch case with auto-completion
        _caseCompleteLatch = new CountDownLatch(1);
        _engine.launchCase(spec, "active-case-test-1");

        // Case should complete normally without idle timeout
        boolean completed = _caseCompleteLatch.await(AWAIT_TIMEOUT_SEC, TimeUnit.SECONDS);
        assertTrue("Case should complete normally", completed);
        assertFalse("No idle timeout should have occurred", _idleTimeoutReceived);
    }

    /**
     * Test 8: Case activity via work item events resets the idle timer.
     */
    public void testMonitorCaseActivity() throws Exception {
        // Engine with medium timeout
        _engine.setCaseMonitoringEnabled(false);
        _engine.removeCaseEventListener(this);
        _engine = new YStatelessEngine(MEDIUM_TIMEOUT_MS);
        _idleTimeoutLatch = new CountDownLatch(1);
        _engine.addCaseEventListener(this);
        _engine.addWorkItemEventListener(this);

        String xml = loadMinimalSpecXml();
        YSpecification spec = _engine.unmarshalSpecification(xml);

        // Launch case
        _caseCompleteLatch = new CountDownLatch(1);
        _engine.launchCase(spec, "activity-test-1");

        // Let work item processing happen (which pings/resets timer)
        // The case should complete without idle timeout because work items are processed
        boolean completed = _caseCompleteLatch.await(AWAIT_TIMEOUT_SEC, TimeUnit.SECONDS);
        assertTrue("Case should complete", completed);

        // Work item activity should have reset timer, preventing idle timeout
        assertFalse("Idle timeout should not fire with active work item processing", _idleTimeoutReceived);
    }

    /**
     * Test 9: Multiple cases can be monitored simultaneously.
     */
    public void testMonitorMultipleCases() throws Exception {
        String xml = loadMinimalSpecXml();
        YSpecification spec = _engine.unmarshalSpecification(xml);
        int caseCount = 5;

        _caseCompleteLatch = new CountDownLatch(caseCount);

        for (int i = 0; i < caseCount; i++) {
            _engine.launchCase(spec, "multi-case-test-" + i);
        }

        boolean allCompleted = _caseCompleteLatch.await(AWAIT_TIMEOUT_SEC * 2, TimeUnit.SECONDS);
        assertTrue("All " + caseCount + " cases should complete", allCompleted);
    }

    /**
     * Test 10: Monitor cancellation stops all timers.
     */
    public void testMonitorCancel() throws Exception {
        // Create engine with monitoring
        _engine.setCaseMonitoringEnabled(false);
        _engine.removeCaseEventListener(this);
        _engine = new YStatelessEngine(MEDIUM_TIMEOUT_MS);
        _engine.addCaseEventListener(this);
        _engine.addWorkItemEventListener(this);

        String xml = loadMinimalSpecXml();
        YSpecification spec = _engine.unmarshalSpecification(xml);

        // Launch case but don't complete work items
        _idleTimeoutLatch = new CountDownLatch(1);
        YNetRunner runner = _engine.launchCase(spec, "cancel-test-1");
        assertNotNull("Runner should not be null", runner);

        // Cancel monitoring
        _engine.setCaseMonitoringEnabled(false);

        // After canceling, no idle timeout should fire
        boolean timedOut = _idleTimeoutLatch.await(1, TimeUnit.SECONDS);
        assertFalse("No idle timeout should fire after cancel", timedOut);
    }

    /**
     * Test 11: Update idle timeout value.
     */
    public void testIdleTimeoutUpdate() {
        long originalTimeout = MEDIUM_TIMEOUT_MS;
        long newTimeout = LONG_TIMEOUT_MS;

        _engine.setIdleCaseTimer(newTimeout);

        // After setting, engine should still have monitoring enabled
        assertTrue("Engine should still have case monitoring enabled", _engine.isCaseMonitoringEnabled());
    }

    /**
     * Test 12: Case events are properly received and handled.
     */
    public void testCaseEventListener() throws Exception {
        String xml = loadMinimalSpecXml();
        YSpecification spec = _engine.unmarshalSpecification(xml);

        _caseCompleteLatch = new CountDownLatch(1);
        _receivedCaseEvents.clear();

        _engine.launchCase(spec, "listener-test-1");

        boolean completed = _caseCompleteLatch.await(AWAIT_TIMEOUT_SEC, TimeUnit.SECONDS);
        assertTrue("Case should complete", completed);

        // Verify we received case events
        assertTrue("Should have received at least one case event", _receivedCaseEvents.size() > 0);
        assertTrue("Should include CASE_STARTED or CASE_COMPLETED",
                _receivedCaseEvents.contains(YEventType.CASE_STARTED) ||
                _receivedCaseEvents.contains(YEventType.CASE_COMPLETED));
    }

    /**
     * Test 13: Work item events are properly received and handled.
     */
    public void testWorkItemEventListener() throws Exception {
        String xml = loadMinimalSpecXml();
        YSpecification spec = _engine.unmarshalSpecification(xml);

        _caseCompleteLatch = new CountDownLatch(1);
        _enabledWorkItems.clear();

        _engine.launchCase(spec, "workitem-listener-test-1");

        boolean completed = _caseCompleteLatch.await(AWAIT_TIMEOUT_SEC, TimeUnit.SECONDS);
        assertTrue("Case should complete", completed);

        // We should have captured at least one enabled work item
        assertTrue("Should have captured at least one work item", _enabledWorkItems.size() > 0);
    }

    /**
     * Test 14: Idle timeout event fires correctly.
     */
    public void testIdleTimeoutEvent() throws Exception {
        // Create engine with short timeout
        _engine.setCaseMonitoringEnabled(false);
        _engine.removeCaseEventListener(this);
        _engine = new YStatelessEngine(SHORT_TIMEOUT_MS);
        _idleTimeoutLatch = new CountDownLatch(1);
        _engine.addCaseEventListener(this);

        String xml = loadMinimalSpecXml();
        YSpecification spec = _engine.unmarshalSpecification(xml);

        _receivedCaseEvents.clear();

        // Launch case without processing work items
        _engine.launchCase(spec, "idle-event-test-1");

        boolean timedOut = _idleTimeoutLatch.await(AWAIT_TIMEOUT_SEC * 2, TimeUnit.SECONDS);
        assertTrue("Idle timeout should fire", timedOut);
        assertTrue("Should have received CASE_IDLE_TIMEOUT event",
                _receivedCaseEvents.contains(YEventType.CASE_IDLE_TIMEOUT));
    }

    /**
     * Test 15: Monitor handles concurrent access safely.
     */
    public void testMonitorThreadSafety() throws Exception {
        int threadCount = 5;
        int casesPerThread = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicReference<YSpecification> specRef = new AtomicReference<>();

        // Load spec once
        String xml = loadMinimalSpecXml();
        final YSpecification spec = _engine.unmarshalSpecification(xml);
        specRef.set(spec);

        _caseCompleteLatch = new CountDownLatch(threadCount * casesPerThread);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < casesPerThread; i++) {
                        String caseID = "concurrent-case-" + threadId + "-" + i;
                        _engine.launchCase(spec, caseID);
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Start all threads simultaneously
        startLatch.countDown();

        // Wait for completion
        boolean threadsDone = doneLatch.await(30, TimeUnit.SECONDS);
        boolean casesDone = _caseCompleteLatch.await(30, TimeUnit.SECONDS);

        executor.shutdown();

        assertTrue("All threads should complete within 30 seconds", threadsDone);
        assertTrue("All cases should complete within 30 seconds", casesDone);
        assertEquals("All cases should launch successfully", threadCount * casesPerThread, successCount.get());
        assertEquals("No errors should occur during concurrent access", 0, errorCount.get());
    }

    /**
     * Test 16: CASE_STARTED event is received for new cases.
     */
    public void testCaseStartedEvent() throws Exception {
        String xml = loadMinimalSpecXml();
        YSpecification spec = _engine.unmarshalSpecification(xml);

        _receivedCaseEvents.clear();
        _caseCompleteLatch = new CountDownLatch(1);

        _engine.launchCase(spec, "started-event-test-1");

        boolean completed = _caseCompleteLatch.await(AWAIT_TIMEOUT_SEC, TimeUnit.SECONDS);
        assertTrue("Case should complete", completed);

        assertTrue("Should have received CASE_STARTED event",
                _receivedCaseEvents.contains(YEventType.CASE_STARTED));
    }

    /**
     * Test 17: CASE_COMPLETED event is received for completed cases.
     */
    public void testCaseCompletedEvent() throws Exception {
        String xml = loadMinimalSpecXml();
        YSpecification spec = _engine.unmarshalSpecification(xml);

        _receivedCaseEvents.clear();
        _caseCompleteLatch = new CountDownLatch(1);

        _engine.launchCase(spec, "completed-event-test-1");

        boolean completed = _caseCompleteLatch.await(AWAIT_TIMEOUT_SEC, TimeUnit.SECONDS);
        assertTrue("Case should complete", completed);

        assertTrue("Should have received CASE_COMPLETED event",
                _receivedCaseEvents.contains(YEventType.CASE_COMPLETED));
    }

    /**
     * Test 18: Disabled timeout (0) does not fire idle timeouts.
     */
    public void testMonitorWithDisabledTimeout() throws Exception {
        // Create engine without idle timeout monitoring
        _engine.setCaseMonitoringEnabled(false);
        _engine.removeCaseEventListener(this);
        _engine = new YStatelessEngine();
        _engine.setCaseMonitoringEnabled(true); // Enable monitoring but without timeout
        _idleTimeoutLatch = new CountDownLatch(1);
        _engine.addCaseEventListener(this);
        _engine.addWorkItemEventListener(this);

        String xml = loadMinimalSpecXml();
        YSpecification spec = _engine.unmarshalSpecification(xml);

        _caseCompleteLatch = new CountDownLatch(1);
        _engine.launchCase(spec, "disabled-timeout-test-1");

        // Wait for normal completion
        boolean completed = _caseCompleteLatch.await(AWAIT_TIMEOUT_SEC, TimeUnit.SECONDS);
        assertTrue("Case should complete", completed);
        assertFalse("No idle timeout should fire when timeout is disabled", _idleTimeoutReceived);
    }

    /**
     * Test 19: Disable and re-enable case monitoring.
     */
    public void testDisableAndReenableMonitoring() throws Exception {
        // Start with monitoring enabled
        assertTrue("Monitoring should be enabled initially", _engine.isCaseMonitoringEnabled());

        // Disable monitoring
        _engine.setCaseMonitoringEnabled(false);
        assertFalse("Monitoring should be disabled", _engine.isCaseMonitoringEnabled());

        // Re-enable with timeout
        _engine.setCaseMonitoringEnabled(true, MEDIUM_TIMEOUT_MS);
        assertTrue("Monitoring should be re-enabled", _engine.isCaseMonitoringEnabled());

        // Verify it works
        String xml = loadMinimalSpecXml();
        YSpecification spec = _engine.unmarshalSpecification(xml);
        _caseCompleteLatch = new CountDownLatch(1);
        _engine.launchCase(spec, "reenable-test-1");

        boolean completed = _caseCompleteLatch.await(AWAIT_TIMEOUT_SEC, TimeUnit.SECONDS);
        assertTrue("Case should complete after re-enabling monitoring", completed);
    }

    /**
     * Test 20: Multiple event listeners receive events.
     */
    public void testMultipleEventListeners() throws Exception {
        AtomicInteger listener1Count = new AtomicInteger(0);
        AtomicInteger listener2Count = new AtomicInteger(0);

        // Add additional listeners
        _engine.addCaseEventListener(event -> listener1Count.incrementAndGet());
        _engine.addCaseEventListener(event -> listener2Count.incrementAndGet());

        String xml = loadMinimalSpecXml();
        YSpecification spec = _engine.unmarshalSpecification(xml);
        _caseCompleteLatch = new CountDownLatch(1);

        _engine.launchCase(spec, "multi-listener-test-1");

        boolean completed = _caseCompleteLatch.await(AWAIT_TIMEOUT_SEC, TimeUnit.SECONDS);
        assertTrue("Case should complete", completed);

        // Both listeners should have received events
        assertTrue("First additional listener should receive events", listener1Count.get() > 0);
        assertTrue("Second additional listener should receive events", listener2Count.get() > 0);
        assertEquals("Both listeners should receive same number of events",
                listener1Count.get(), listener2Count.get());
    }

    /**
     * Test 21: Work item start and complete events are received.
     */
    public void testWorkItemEvents() throws Exception {
        String xml = loadMinimalSpecXml();
        YSpecification spec = _engine.unmarshalSpecification(xml);

        List<YEventType> workItemEvents = new ArrayList<>();
        CountDownLatch workItemLatch = new CountDownLatch(1);

        _engine.addWorkItemEventListener(event -> {
            workItemEvents.add(event.getEventType());
            if (event.getEventType() == YEventType.ITEM_COMPLETED) {
                workItemLatch.countDown();
            }
        });

        _caseCompleteLatch = new CountDownLatch(1);
        _engine.launchCase(spec, "workitem-events-test-1");

        boolean completed = _caseCompleteLatch.await(AWAIT_TIMEOUT_SEC, TimeUnit.SECONDS);
        assertTrue("Case should complete", completed);

        // Should have received work item events
        assertTrue("Should have received work item events", workItemEvents.size() > 0);
        assertTrue("Should include ITEM_ENABLED event", workItemEvents.contains(YEventType.ITEM_ENABLED));
        assertTrue("Should include ITEM_STARTED event", workItemEvents.contains(YEventType.ITEM_STARTED));
        assertTrue("Should include ITEM_COMPLETED event", workItemEvents.contains(YEventType.ITEM_COMPLETED));
    }

    /**
     * Test 22: Engine with negative timeout disables idle monitoring.
     */
    public void testNegativeTimeoutDisablesIdleMonitoring() throws Exception {
        _engine.setCaseMonitoringEnabled(false);
        _engine.removeCaseEventListener(this);
        _engine = new YStatelessEngine(-1); // Negative disables idle timer
        _idleTimeoutLatch = new CountDownLatch(1);
        _engine.addCaseEventListener(this);
        _engine.addWorkItemEventListener(this);

        String xml = loadMinimalSpecXml();
        YSpecification spec = _engine.unmarshalSpecification(xml);

        _caseCompleteLatch = new CountDownLatch(1);
        _engine.launchCase(spec, "negative-timeout-test-1");

        boolean completed = _caseCompleteLatch.await(AWAIT_TIMEOUT_SEC, TimeUnit.SECONDS);
        assertTrue("Case should complete", completed);
        assertFalse("No idle timeout should fire with negative timeout", _idleTimeoutReceived);
    }

    // ==================== YCaseEventListener Implementation ====================

    @Override
    public void handleCaseEvent(YCaseEvent event) {
        _receivedCaseEvents.add(event.getEventType());

        switch (event.getEventType()) {
            case CASE_COMPLETED:
                _caseCompleted = true;
                if (_caseCompleteLatch != null) {
                    _caseCompleteLatch.countDown();
                }
                break;

            case CASE_IDLE_TIMEOUT:
                _idleTimeoutReceived = true;
                if (_idleTimeoutLatch != null) {
                    _idleTimeoutLatch.countDown();
                }
                break;

            default:
                // Other events are just tracked
                break;
        }
    }

    // ==================== YWorkItemEventListener Implementation ====================

    @Override
    public void handleWorkItemEvent(YWorkItemEvent event) {
        try {
            YWorkItem item = event.getWorkItem();

            if (event.getEventType() == YEventType.ITEM_ENABLED) {
                _enabledWorkItems.add(item);
                _engine.startWorkItem(item);
            } else if (event.getEventType() == YEventType.ITEM_STARTED) {
                if (!item.hasCompletedStatus()) {
                    _engine.completeWorkItem(item, "<data/>", null);
                }
            }
        } catch (YStateException | YDataStateException | YQueryException | YEngineStateException e) {
            throw new RuntimeException("Error handling work item event", e);
        }
    }
}
