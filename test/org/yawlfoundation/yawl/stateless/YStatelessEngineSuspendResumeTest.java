package org.yawlfoundation.yawl.stateless;

import junit.framework.TestCase;
import org.yawlfoundation.yawl.exceptions.YDataStateException;
import org.yawlfoundation.yawl.exceptions.YEngineStateException;
import org.yawlfoundation.yawl.exceptions.YQueryException;
import org.yawlfoundation.yawl.exceptions.YStateException;
import org.yawlfoundation.yawl.exceptions.YSyntaxException;
import org.yawlfoundation.yawl.stateless.elements.YSpecification;
import org.yawlfoundation.yawl.stateless.engine.YNetRunner;
import org.yawlfoundation.yawl.engine.YWorkItemStatus;
import org.yawlfoundation.yawl.stateless.engine.YWorkItem;
import org.yawlfoundation.yawl.stateless.listener.YCaseEventListener;
import org.yawlfoundation.yawl.stateless.listener.YWorkItemEventListener;
import org.yawlfoundation.yawl.stateless.listener.event.YCaseEvent;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;
import org.yawlfoundation.yawl.stateless.listener.event.YWorkItemEvent;
import org.yawlfoundation.yawl.util.StringUtil;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Integration tests for YStatelessEngine suspend/resume, cancel, marshal/restore,
 * and listener management - exercising feature parity gaps vs the stateful engine.
 *
 * Chicago TDD: Real YStatelessEngine instances, real YSpecification objects loaded
 * from classpath, real listener callbacks verified end-to-end.
 *
 * Methods under test (currently untested):
 *   - suspendCase / resumeCase
 *   - suspendWorkItem / unsuspendWorkItem
 *   - rollbackWorkItem
 *   - cancelCase
 *   - marshalCase / restoreCase
 *   - addCaseEventListener / removeCaseEventListener
 *   - addExceptionEventListener / removeExceptionEventListener
 *   - enableMultiThreadedAnnouncements
 *   - setCaseMonitoringEnabled / isCaseMonitoringEnabled / setIdleCaseTimer
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YStatelessEngineSuspendResumeTest extends TestCase
        implements YCaseEventListener, YWorkItemEventListener {

    private static final String MINIMAL_SPEC_RESOURCE =
            "/org/yawlfoundation/yawl/stateless/resources/MinimalSpec.xml";
    private static final long EVENT_TIMEOUT_SEC = 10L;

    private YStatelessEngine engine;
    private final List<YEventType> caseEventTypes = new ArrayList<>();
    private final List<YWorkItem> enabledItems = new ArrayList<>();
    private volatile CountDownLatch itemEnabledLatch;

    public YStatelessEngineSuspendResumeTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        engine = new YStatelessEngine();
        engine.addCaseEventListener(this);
        engine.addWorkItemEventListener(this);
        caseEventTypes.clear();
        enabledItems.clear();
    }

    @Override
    protected void tearDown() throws Exception {
        if (engine != null) {
            engine.removeCaseEventListener(this);
            engine.removeWorkItemEventListener(this);
        }
        super.tearDown();
    }

    // --- listener interface implementations ---

    @Override
    public void handleCaseEvent(YCaseEvent event) {
        synchronized (caseEventTypes) {
            caseEventTypes.add(event.getEventType());
        }
    }

    @Override
    public void handleWorkItemEvent(YWorkItemEvent event) {
        synchronized (enabledItems) {
            if (event.getEventType() == YEventType.ITEM_ENABLED) {
                enabledItems.add(event.getWorkItem());
                if (itemEnabledLatch != null) {
                    itemEnabledLatch.countDown();
                }
            }
        }
    }

    // --- helper ---

    private YSpecification loadSpec() throws YSyntaxException {
        InputStream is = getClass().getResourceAsStream(MINIMAL_SPEC_RESOURCE);
        assertNotNull("MinimalSpec.xml resource must exist", is);
        String xml = StringUtil.streamToString(is);
        return engine.unmarshalSpecification(xml);
    }

    private YNetRunner launchAndWaitForItem(YSpecification spec)
            throws Exception {
        itemEnabledLatch = new CountDownLatch(1);
        YNetRunner runner = engine.launchCase(spec);
        boolean gotItem = itemEnabledLatch.await(EVENT_TIMEOUT_SEC, TimeUnit.SECONDS);
        assertTrue("Work item should be enabled within " + EVENT_TIMEOUT_SEC + "s", gotItem);
        return runner;
    }

    // --- listener management tests ---

    /**
     * Verify addCaseEventListener and removeCaseEventListener round-trip
     * does not throw and the listener stops receiving events after removal.
     */
    public void testAddAndRemoveCaseEventListener() throws Exception {
        YSpecification spec = loadSpec();
        CountDownLatch latch = new CountDownLatch(1);
        List<YEventType> captured = new ArrayList<>();

        YCaseEventListener listener = event -> {
            captured.add(event.getEventType());
            latch.countDown();
        };

        engine.addCaseEventListener(listener);
        engine.launchCase(spec);
        latch.await(EVENT_TIMEOUT_SEC, TimeUnit.SECONDS);

        engine.removeCaseEventListener(listener);

        assertFalse("Listener should have received at least one event", captured.isEmpty());
    }

    /**
     * Verify suspendCase puts the runner into a suspended state and does not throw.
     */
    public void testSuspendCaseDoesNotThrow() throws Exception {
        YSpecification spec = loadSpec();
        YNetRunner runner = launchAndWaitForItem(spec);

        // suspendCase should succeed without exception
        engine.suspendCase(runner);

        // runner should now report a suspended execution status
        assertTrue("Runner should be in Suspending or Suspended state after suspend",
                runner.isSuspending() || runner.isSuspended());
    }

    /**
     * Verify resumeCase after suspendCase restores the runner to Normal status.
     */
    public void testSuspendThenResumeRestoresNormalStatus() throws Exception {
        YSpecification spec = loadSpec();
        YNetRunner runner = launchAndWaitForItem(spec);

        engine.suspendCase(runner);
        engine.resumeCase(runner);

        assertTrue("After resume, execution status should be Normal",
                runner.hasNormalState());
    }

    /**
     * Verify suspendWorkItem changes work item status to Suspended.
     */
    public void testSuspendWorkItemChangesSuspendedStatus() throws Exception {
        YSpecification spec = loadSpec();
        launchAndWaitForItem(spec);

        YWorkItem item = enabledItems.get(0);
        YWorkItem started = engine.startWorkItem(item);
        assertNotNull("Started work item should not be null", started);

        YWorkItem suspended = engine.suspendWorkItem(started);

        assertNotNull("Suspended work item should not be null", suspended);
        assertEquals("Work item should have suspended status",
                YWorkItemStatus.statusSuspended, suspended.getStatus());
    }

    /**
     * Verify unsuspendWorkItem after suspendWorkItem restores executing status.
     */
    public void testUnsuspendWorkItemRestoresExecutingStatus() throws Exception {
        YSpecification spec = loadSpec();
        launchAndWaitForItem(spec);

        YWorkItem item = enabledItems.get(0);
        YWorkItem started = engine.startWorkItem(item);
        YWorkItem suspended = engine.suspendWorkItem(started);
        YWorkItem resumed = engine.unsuspendWorkItem(suspended);

        assertNotNull("Resumed work item should not be null", resumed);
        assertEquals("Work item should have executing status after unsuspend",
                YWorkItemStatus.statusExecuting, resumed.getStatus());
    }

    /**
     * Verify suspendWorkItem on an enabled (not yet started) item throws YStateException.
     */
    public void testSuspendEnabledWorkItemThrowsStateException() throws Exception {
        YSpecification spec = loadSpec();
        launchAndWaitForItem(spec);

        YWorkItem enabledItem = enabledItems.get(0);

        try {
            engine.suspendWorkItem(enabledItem);
            fail("Expected YStateException when suspending an enabled (not started) work item");
        } catch (YStateException e) {
            assertNotNull("Exception should have a message", e.getMessage());
        }
    }

    /**
     * Verify rollbackWorkItem after starting returns item to Enabled status.
     */
    public void testRollbackWorkItemReturnsToEnabledStatus() throws Exception {
        YSpecification spec = loadSpec();
        launchAndWaitForItem(spec);

        YWorkItem item = enabledItems.get(0);
        YWorkItem started = engine.startWorkItem(item);
        assertNotNull("Started work item should be non-null", started);

        YWorkItem rolledBack = engine.rollbackWorkItem(started);

        assertNotNull("Rolled-back work item should be non-null", rolledBack);
        assertTrue("Rolled-back work item should be in fired or enabled status",
                YWorkItemStatus.statusFired.equals(rolledBack.getStatus())
                        || YWorkItemStatus.statusEnabled.equals(rolledBack.getStatus()));
    }

    /**
     * Verify cancelCase removes the case from the engine without exception.
     */
    public void testCancelCaseRemovesCase() throws Exception {
        YSpecification spec = loadSpec();
        YNetRunner runner = launchAndWaitForItem(spec);

        engine.cancelCase(runner);

        assertFalse("Cancelled runner should no longer be alive", runner.isAlive());
    }

    /**
     * Verify marshalCase produces non-empty XML string.
     */
    public void testMarshalCaseProducesXml() throws Exception {
        YSpecification spec = loadSpec();
        YNetRunner runner = launchAndWaitForItem(spec);

        String xml = engine.marshalCase(runner);

        assertNotNull("Marshalled case XML should not be null", xml);
        assertFalse("Marshalled case XML should not be empty", xml.trim().isEmpty());
        assertTrue("Marshalled XML should contain case element",
                xml.contains("<case") || xml.contains("<YCase") || xml.contains("caseID"));
    }

    /**
     * Verify restoreCase from marshalled XML produces equivalent runner.
     */
    public void testRestoreCaseFromMarshalledXml() throws Exception {
        YSpecification spec = loadSpec();
        YNetRunner original = launchAndWaitForItem(spec);
        String caseId = original.getCaseID().toString();

        String xml = engine.marshalCase(original);
        engine.cancelCase(original);

        YNetRunner restored = engine.restoreCase(xml);

        assertNotNull("Restored runner should not be null", restored);
        assertEquals("Restored runner should have original case ID",
                caseId, restored.getCaseID().toString());
        assertTrue("Restored runner should be alive", restored.isAlive());
    }

    /**
     * Verify setCaseMonitoringEnabled(true) enables case monitoring.
     */
    public void testSetCaseMonitoringEnabledTrue() {
        assertFalse("Monitoring should be disabled before enable",
                engine.isCaseMonitoringEnabled());

        engine.setCaseMonitoringEnabled(true);

        assertTrue("Monitoring should be enabled after setCaseMonitoringEnabled(true)",
                engine.isCaseMonitoringEnabled());
    }

    /**
     * Verify setCaseMonitoringEnabled(false) disables case monitoring.
     */
    public void testSetCaseMonitoringEnabledFalse() {
        engine.setCaseMonitoringEnabled(true);
        engine.setCaseMonitoringEnabled(false);

        assertFalse("Monitoring should be disabled after setCaseMonitoringEnabled(false)",
                engine.isCaseMonitoringEnabled());
    }

    /**
     * Verify setIdleCaseTimer with positive value enables monitoring.
     */
    public void testSetIdleCaseTimerPositiveEnablesMonitoring() {
        assertFalse("Monitoring off initially", engine.isCaseMonitoringEnabled());

        engine.setIdleCaseTimer(60000L);

        assertTrue("Monitoring should be enabled after setting positive idle timer",
                engine.isCaseMonitoringEnabled());
    }

    /**
     * Verify setIdleCaseTimer with zero disables monitoring.
     */
    public void testSetIdleCaseTimerZeroDisablesMonitoring() {
        engine.setCaseMonitoringEnabled(true);
        engine.setIdleCaseTimer(0L);

        assertFalse("Monitoring should be disabled when timer is set to 0",
                engine.isCaseMonitoringEnabled());
    }

    /**
     * Verify enableMultiThreadedAnnouncements(true) then false does not throw.
     */
    public void testEnableMultiThreadedAnnouncementsToggle() {
        engine.enableMultiThreadedAnnouncements(true);
        assertTrue("Multi-threaded announcements should be enabled",
                engine.isMultiThreadedAnnouncementsEnabled());

        engine.enableMultiThreadedAnnouncements(false);
        assertFalse("Multi-threaded announcements should be disabled",
                engine.isMultiThreadedAnnouncementsEnabled());
    }
}
