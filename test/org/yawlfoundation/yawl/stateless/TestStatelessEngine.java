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
import org.yawlfoundation.yawl.util.StringUtil;

import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * JUnit tests for the stateless YAWL engine (YStatelessEngine).
 * Covers launchCase, startWorkItem, completeWorkItem, and unmarshal.
 */
public class TestStatelessEngine extends TestCase implements YCaseEventListener, YWorkItemEventListener {

    private static final String MINIMAL_SPEC_RESOURCE = "resources/MinimalSpec.xml";
    private static final long CASE_COMPLETE_TIMEOUT_SEC = 10L;

    private YStatelessEngine _engine;
    private CountDownLatch _caseCompleteLatch;
    private volatile boolean _caseCompleted;

    public TestStatelessEngine(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        _engine = new YStatelessEngine();
        _engine.addCaseEventListener(this);
        _engine.addWorkItemEventListener(this);
        _caseCompleted = false;
    }

    @Override
    protected void tearDown() throws Exception {
        if (_engine != null) {
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
     * Test: unmarshal specification returns non-null spec with root net and one task.
     */
    public void testUnmarshalSpecification() throws YSyntaxException {
        String xml = loadMinimalSpecXml();
        YSpecification spec = _engine.unmarshalSpecification(xml);
        assertNotNull(spec);
        assertNotNull(spec.getRootNet());
        assertEquals("MinimalSpec", spec.getID());
        assertEquals(1, spec.getDecompositions().size());
        assertNotNull(spec.getRootNet().getNetElement("task1"));
        assertNotNull(spec.getRootNet().getInputCondition());
        assertNotNull(spec.getRootNet().getOutputCondition());
    }

    /**
     * Test: launchCase(spec, caseID) returns runner with matching case ID.
     */
    public void testLaunchCaseWithExplicitCaseID() throws Exception {
        String xml = loadMinimalSpecXml();
        YSpecification spec = _engine.unmarshalSpecification(xml);
        String caseID = "test-case-1";
        YNetRunner runner = _engine.launchCase(spec, caseID);
        assertNotNull(runner);
        assertNotNull(runner.getCaseID());
        assertEquals(caseID, runner.getCaseID().toString());
    }

    /**
     * Test: launch one case, drive work item (start then complete), assert case completes.
     */
    public void testLaunchAndCompleteOneCase() throws Exception {
        String xml = loadMinimalSpecXml();
        YSpecification spec = _engine.unmarshalSpecification(xml);
        _caseCompleteLatch = new CountDownLatch(1);
        _caseCompleted = false;

        _engine.launchCase(spec);

        boolean completed = _caseCompleteLatch.await(CASE_COMPLETE_TIMEOUT_SEC, TimeUnit.SECONDS);
        assertTrue("Case did not complete within " + CASE_COMPLETE_TIMEOUT_SEC + "s", completed);
        assertTrue("Case completion flag not set", _caseCompleted);
    }

    @Override
    public void handleCaseEvent(YCaseEvent event) {
        if (event.getEventType() == YEventType.CASE_COMPLETED) {
            _caseCompleted = true;
            if (_caseCompleteLatch != null) {
                _caseCompleteLatch.countDown();
            }
        }
    }

    @Override
    public void handleWorkItemEvent(YWorkItemEvent event) {
        try {
            YWorkItem item = event.getWorkItem();
            if (event.getEventType() == YEventType.ITEM_ENABLED) {
                _engine.startWorkItem(item);
            } else if (event.getEventType() == YEventType.ITEM_STARTED) {
                if (!item.hasCompletedStatus()) {
                    _engine.completeWorkItem(item, "<data/>", null);
                }
            }
        } catch (YStateException | YDataStateException | YQueryException | YEngineStateException e) {
            throw new RuntimeException(e);
        }
    }
}
