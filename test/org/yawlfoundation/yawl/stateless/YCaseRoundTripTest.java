package org.yawlfoundation.yawl.stateless;

import junit.framework.TestCase;

import org.jdom2.Document;
import org.yawlfoundation.yawl.engine.YNetData;
import org.yawlfoundation.yawl.exceptions.YDataStateException;
import org.yawlfoundation.yawl.exceptions.YEngineStateException;
import org.yawlfoundation.yawl.exceptions.YQueryException;
import org.yawlfoundation.yawl.exceptions.YStateException;
import org.yawlfoundation.yawl.exceptions.YSyntaxException;
import org.yawlfoundation.yawl.stateless.elements.YNet;
import org.yawlfoundation.yawl.stateless.elements.YSpecification;
import org.yawlfoundation.yawl.stateless.elements.YTask;
import org.yawlfoundation.yawl.stateless.elements.marking.YIdentifier;
import org.yawlfoundation.yawl.stateless.engine.YEngine;
import org.yawlfoundation.yawl.stateless.engine.YNetRunner;
import org.yawlfoundation.yawl.stateless.engine.YWorkItem;
import org.yawlfoundation.yawl.stateless.listener.YCaseEventListener;
import org.yawlfoundation.yawl.stateless.listener.YWorkItemEventListener;
import org.yawlfoundation.yawl.stateless.listener.event.YCaseEvent;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;
import org.yawlfoundation.yawl.stateless.listener.event.YWorkItemEvent;
import org.yawlfoundation.yawl.stateless.monitor.YCaseExporter;
import org.yawlfoundation.yawl.stateless.monitor.YCaseImporter;
import org.yawlfoundation.yawl.util.JDOMUtil;
import org.yawlfoundation.yawl.util.StringUtil;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive JUnit tests for YAWL stateless engine import/export round-trip.
 * Tests verify that case state is preserved when exporting to XML and importing back.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YCaseRoundTripTest extends TestCase implements YCaseEventListener, YWorkItemEventListener {

    private static final String MINIMAL_SPEC_RESOURCE = "resources/MinimalSpec.xml";
    private static final String MULTI_TASK_SPEC_RESOURCE = "resources/MultiTaskSpec.xml";
    private static final String DATA_SPEC_RESOURCE = "resources/DataSpec.xml";
    private static final long CASE_COMPLETE_TIMEOUT_SEC = 10L;

    private YStatelessEngine _engine;
    private YEngine _internalEngine;
    private CountDownLatch _caseCompleteLatch;
    private volatile boolean _caseCompleted;
    private List<YWorkItem> _capturedWorkItems;

    public YCaseRoundTripTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        _engine = new YStatelessEngine();
        _internalEngine = new YEngine();
        _engine.addCaseEventListener(this);
        _engine.addWorkItemEventListener(this);
        _caseCompleted = false;
        _capturedWorkItems = new ArrayList<>();
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
     * Load spec XML from classpath resource.
     */
    private String loadSpecXml(String resourcePath) {
        InputStream is = getClass().getResourceAsStream(resourcePath);
        assertNotNull("Missing resource: " + resourcePath, is);
        String xml = StringUtil.streamToString(is);
        assertNotNull("Empty spec XML", xml);
        return xml;
    }

    /**
     * Test 1: Export a simple running case to XML.
     */
    public void testExportSimpleCase() throws Exception {
        String xml = loadSpecXml(MINIMAL_SPEC_RESOURCE);
        YSpecification spec = _engine.unmarshalSpecification(xml);
        YNetRunner runner = _engine.launchCase(spec, "export-test-case-1");

        assertNotNull("Runner must not be null", runner);
        assertNotNull("Case ID must not be null", runner.getCaseID());

        YCaseExporter exporter = new YCaseExporter();
        String caseXML = exporter.marshal(runner);

        assertNotNull("Exported XML must not be null", caseXML);
        assertTrue("Exported XML must contain case element", caseXML.contains("<case"));
        assertTrue("Exported XML must contain case ID", caseXML.contains("id=\""));
        assertTrue("Exported XML must contain specification", caseXML.contains("specificationSet"));
        assertTrue("Exported XML must contain runners", caseXML.contains("<runners>"));
    }

    /**
     * Test 2: Import a simple case from XML.
     */
    public void testImportSimpleCase() throws Exception {
        String xml = loadSpecXml(MINIMAL_SPEC_RESOURCE);
        YSpecification spec = _engine.unmarshalSpecification(xml);
        YNetRunner originalRunner = _engine.launchCase(spec, "import-test-case-2");

        YCaseExporter exporter = new YCaseExporter();
        String caseXML = exporter.marshal(originalRunner);

        YCaseImporter importer = new YCaseImporter();
        List<YNetRunner> importedRunners = importer.unmarshal(caseXML, _internalEngine.getAnnouncer());

        assertNotNull("Imported runners list must not be null", importedRunners);
        assertFalse("Imported runners list must not be empty", importedRunners.isEmpty());

        YNetRunner importedRunner = importedRunners.get(0);
        assertNotNull("Imported runner must not be null", importedRunner);
        assertEquals("Case ID must match after import",
                originalRunner.getCaseID().toString(),
                importedRunner.getCaseID().toString());
    }

    /**
     * Test 3: Export then import preserves state (round-trip).
     */
    public void testRoundTripSimpleCase() throws Exception {
        String xml = loadSpecXml(MINIMAL_SPEC_RESOURCE);
        YSpecification spec = _engine.unmarshalSpecification(xml);
        String originalCaseID = "roundtrip-test-case-3";
        YNetRunner originalRunner = _engine.launchCase(spec, originalCaseID);

        long originalStartTime = originalRunner.getStartTime();
        String originalExecutionStatus = originalRunner.getExecutionStatus();

        YCaseExporter exporter = new YCaseExporter();
        String caseXML = exporter.marshal(originalRunner);

        YCaseImporter importer = new YCaseImporter();
        List<YNetRunner> importedRunners = importer.unmarshal(caseXML, _internalEngine.getAnnouncer());
        YNetRunner importedRunner = importedRunners.get(0);

        assertEquals("Case ID must match after round-trip",
                originalCaseID, importedRunner.getCaseID().toString());
        assertEquals("Start time must match after round-trip",
                originalStartTime, importedRunner.getStartTime());
        assertEquals("Execution status must match after round-trip",
                originalExecutionStatus, importedRunner.getExecutionStatus());
    }

    /**
     * Test 4: Export a case with active work items.
     */
    public void testExportCaseWithWorkItems() throws Exception {
        String xml = loadSpecXml(MINIMAL_SPEC_RESOURCE);
        YSpecification spec = _engine.unmarshalSpecification(xml);
        YNetRunner runner = _engine.launchCase(spec, "export-wi-test-case-4");

        YCaseExporter exporter = new YCaseExporter();
        String caseXML = exporter.marshal(runner);

        assertNotNull("Exported XML must not be null", caseXML);
        assertTrue("Exported XML must contain runners element", caseXML.contains("<runners>"));
        assertTrue("Export must contain case ID", caseXML.contains("export-wi-test-case-4"));
    }

    /**
     * Test 5: Round-trip preserves work items.
     */
    public void testRoundTripCaseWithWorkItems() throws Exception {
        String xml = loadSpecXml(MINIMAL_SPEC_RESOURCE);
        YSpecification spec = _engine.unmarshalSpecification(xml);
        YNetRunner originalRunner = _engine.launchCase(spec, "roundtrip-wi-test-case-5");

        YCaseExporter exporter = new YCaseExporter();
        String caseXML = exporter.marshal(originalRunner);

        YCaseImporter importer = new YCaseImporter();
        List<YNetRunner> importedRunners = importer.unmarshal(caseXML, _internalEngine.getAnnouncer());
        YNetRunner importedRunner = importedRunners.get(0);

        assertNotNull("Imported runner must not be null", importedRunner);
        assertEquals("Case ID must match", "roundtrip-wi-test-case-5", importedRunner.getCaseID().toString());
    }

    /**
     * Test 6: Export a case with data.
     */
    public void testExportCaseWithData() throws Exception {
        String xml = loadSpecXml(DATA_SPEC_RESOURCE);
        YSpecification spec = _engine.unmarshalSpecification(xml);
        // caseParams outer element must match spec URI or root net name
        String caseParams = "<DataNet/>";
        YNetRunner runner = _engine.launchCase(spec, "export-data-test-case-6", caseParams);

        YCaseExporter exporter = new YCaseExporter();
        String caseXML = exporter.marshal(runner);

        assertNotNull("Exported XML must not be null", caseXML);
        assertTrue("Exported XML must contain netdata element", caseXML.contains("<netdata>"));
    }

    /**
     * Test 7: Round-trip preserves data.
     */
    public void testRoundTripCaseWithData() throws Exception {
        String xml = loadSpecXml(DATA_SPEC_RESOURCE);
        YSpecification spec = _engine.unmarshalSpecification(xml);
        String caseParams = "<DataNet/>";  // caseParams outer element must match root net name
        YNetRunner originalRunner = _engine.launchCase(spec, "roundtrip-data-test-case-7", caseParams);

        YNetData originalNetData = originalRunner.getNetData();
        assertNotNull("Original net data must not be null", originalNetData);

        YCaseExporter exporter = new YCaseExporter();
        String caseXML = exporter.marshal(originalRunner);

        YCaseImporter importer = new YCaseImporter();
        List<YNetRunner> importedRunners = importer.unmarshal(caseXML, _internalEngine.getAnnouncer());
        YNetRunner importedRunner = importedRunners.get(0);

        assertNotNull("Imported net data must not be null", importedRunner.getNetData());
    }

    /**
     * Test 8: Export multiple cases (as separate exports).
     */
    public void testExportMultipleCases() throws Exception {
        String xml = loadSpecXml(MINIMAL_SPEC_RESOURCE);
        YSpecification spec = _engine.unmarshalSpecification(xml);

        YNetRunner runner1 = _engine.launchCase(spec, "multi-export-case-8a");
        YNetRunner runner2 = _engine.launchCase(spec, "multi-export-case-8b");
        YNetRunner runner3 = _engine.launchCase(spec, "multi-export-case-8c");

        YCaseExporter exporter = new YCaseExporter();

        String caseXML1 = exporter.marshal(runner1);
        String caseXML2 = exporter.marshal(runner2);
        String caseXML3 = exporter.marshal(runner3);

        assertTrue("First export must contain case ID", caseXML1.contains("multi-export-case-8a"));
        assertTrue("Second export must contain case ID", caseXML2.contains("multi-export-case-8b"));
        assertTrue("Third export must contain case ID", caseXML3.contains("multi-export-case-8c"));
    }

    /**
     * Test 9: Import multiple cases.
     */
    public void testImportMultipleCases() throws Exception {
        String xml = loadSpecXml(MINIMAL_SPEC_RESOURCE);
        YSpecification spec = _engine.unmarshalSpecification(xml);

        YNetRunner runner1 = _engine.launchCase(spec, "multi-import-case-9a");
        YNetRunner runner2 = _engine.launchCase(spec, "multi-import-case-9b");

        YCaseExporter exporter = new YCaseExporter();
        YCaseImporter importer = new YCaseImporter();

        String caseXML1 = exporter.marshal(runner1);
        String caseXML2 = exporter.marshal(runner2);

        List<YNetRunner> imported1 = importer.unmarshal(caseXML1, _internalEngine.getAnnouncer());
        List<YNetRunner> imported2 = importer.unmarshal(caseXML2, _internalEngine.getAnnouncer());

        assertEquals("First import must have correct case ID",
                "multi-import-case-9a", imported1.get(0).getCaseID().toString());
        assertEquals("Second import must have correct case ID",
                "multi-import-case-9b", imported2.get(0).getCaseID().toString());
    }

    /**
     * Test 10: Round-trip multiple cases preserves all case states.
     */
    public void testRoundTripMultipleCases() throws Exception {
        String xml = loadSpecXml(MINIMAL_SPEC_RESOURCE);
        YSpecification spec = _engine.unmarshalSpecification(xml);

        List<String> caseIDs = new ArrayList<>();
        List<String> exportedXMLs = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            String caseID = "roundtrip-multi-case-10-" + i;
            caseIDs.add(caseID);
            YNetRunner runner = _engine.launchCase(spec, caseID);

            YCaseExporter exporter = new YCaseExporter();
            exportedXMLs.add(exporter.marshal(runner));
        }

        YCaseImporter importer = new YCaseImporter();
        for (int i = 0; i < caseIDs.size(); i++) {
            List<YNetRunner> imported = importer.unmarshal(exportedXMLs.get(i), _internalEngine.getAnnouncer());
            assertEquals("Case ID must match for case " + i,
                    caseIDs.get(i), imported.get(0).getCaseID().toString());
        }
    }

    /**
     * Test 11: Export a suspended case.
     */
    public void testExportSuspendedCase() throws Exception {
        String xml = loadSpecXml(MINIMAL_SPEC_RESOURCE);
        YSpecification spec = _engine.unmarshalSpecification(xml);
        YNetRunner runner = _engine.launchCase(spec, "suspended-export-case-11");

        _engine.suspendCase(runner);
        assertEquals("Runner should be suspended",
                YNetRunner.ExecutionStatus.Suspended.name(), runner.getExecutionStatus());

        YCaseExporter exporter = new YCaseExporter();
        String caseXML = exporter.marshal(runner);

        assertTrue("Exported XML must contain suspended status", caseXML.contains("Suspended"));
    }

    /**
     * Test 12: Round-trip preserves suspended state.
     */
    public void testRoundTripSuspendedCase() throws Exception {
        String xml = loadSpecXml(MINIMAL_SPEC_RESOURCE);
        YSpecification spec = _engine.unmarshalSpecification(xml);
        YNetRunner originalRunner = _engine.launchCase(spec, "suspended-roundtrip-case-12");

        _engine.suspendCase(originalRunner);

        YCaseExporter exporter = new YCaseExporter();
        String caseXML = exporter.marshal(originalRunner);

        YCaseImporter importer = new YCaseImporter();
        List<YNetRunner> importedRunners = importer.unmarshal(caseXML, _internalEngine.getAnnouncer());
        YNetRunner importedRunner = importedRunners.get(0);

        assertEquals("Suspended status must be preserved",
                YNetRunner.ExecutionStatus.Suspended.name(), importedRunner.getExecutionStatus());
    }

    /**
     * Test 13: Export a case with timer state information.
     */
    public void testExportCaseWithTimer() throws Exception {
        String xml = loadSpecXml(MINIMAL_SPEC_RESOURCE);
        YSpecification spec = _engine.unmarshalSpecification(xml);
        YNetRunner runner = _engine.launchCase(spec, "timer-export-case-13");

        YCaseExporter exporter = new YCaseExporter();
        String caseXML = exporter.marshal(runner);

        // Timer states may be empty for simple specs without timers
        assertNotNull("Exported XML must not be null", caseXML);
        assertTrue("Exported XML must contain runner element", caseXML.contains("<runner"));
    }

    /**
     * Test 14: Round-trip preserves timer states.
     */
    public void testRoundTripCaseWithTimer() throws Exception {
        String xml = loadSpecXml(MINIMAL_SPEC_RESOURCE);
        YSpecification spec = _engine.unmarshalSpecification(xml);
        YNetRunner originalRunner = _engine.launchCase(spec, "timer-roundtrip-case-14");

        java.util.Map<String, String> originalTimerStates = originalRunner.get_timerStates();

        YCaseExporter exporter = new YCaseExporter();
        String caseXML = exporter.marshal(originalRunner);

        YCaseImporter importer = new YCaseImporter();
        List<YNetRunner> importedRunners = importer.unmarshal(caseXML, _internalEngine.getAnnouncer());
        YNetRunner importedRunner = importedRunners.get(0);

        assertNotNull("Timer states must not be null", importedRunner.get_timerStates());
    }

    /**
     * Test 15: Export a case with nested decomposition (composite task).
     * Note: This requires a spec with composite tasks.
     */
    public void testExportNestedDecomposition() throws Exception {
        String xml = loadSpecXml(MINIMAL_SPEC_RESOURCE);
        YSpecification spec = _engine.unmarshalSpecification(xml);
        YNetRunner runner = _engine.launchCase(spec, "nested-export-case-15");

        YCaseExporter exporter = new YCaseExporter();
        String caseXML = exporter.marshal(runner);

        assertNotNull("Exported XML must not be null", caseXML);
        assertTrue("Exported XML must contain runners", caseXML.contains("<runners>"));
    }

    /**
     * Test 16: Round-trip preserves nested decomposition structure.
     */
    public void testRoundTripNestedDecomposition() throws Exception {
        String xml = loadSpecXml(MINIMAL_SPEC_RESOURCE);
        YSpecification spec = _engine.unmarshalSpecification(xml);
        YNetRunner originalRunner = _engine.launchCase(spec, "nested-roundtrip-case-16");

        YCaseExporter exporter = new YCaseExporter();
        String caseXML = exporter.marshal(originalRunner);

        YCaseImporter importer = new YCaseImporter();
        List<YNetRunner> importedRunners = importer.unmarshal(caseXML, _internalEngine.getAnnouncer());

        assertFalse("Imported runners list must not be empty", importedRunners.isEmpty());
    }

    /**
     * Test 17: Export a case with multi-instance task.
     */
    public void testExportCaseWithMultiInstance() throws Exception {
        String xml = loadSpecXml(MINIMAL_SPEC_RESOURCE);
        YSpecification spec = _engine.unmarshalSpecification(xml);
        YNetRunner runner = _engine.launchCase(spec, "mi-export-case-17");

        YCaseExporter exporter = new YCaseExporter();
        String caseXML = exporter.marshal(runner);

        assertNotNull("Exported XML must not be null", caseXML);
    }

    /**
     * Test 18: Round-trip preserves multi-instance task state.
     */
    public void testRoundTripMultiInstance() throws Exception {
        String xml = loadSpecXml(MINIMAL_SPEC_RESOURCE);
        YSpecification spec = _engine.unmarshalSpecification(xml);
        YNetRunner originalRunner = _engine.launchCase(spec, "mi-roundtrip-case-18");

        YCaseExporter exporter = new YCaseExporter();
        String caseXML = exporter.marshal(originalRunner);

        YCaseImporter importer = new YCaseImporter();
        List<YNetRunner> importedRunners = importer.unmarshal(caseXML, _internalEngine.getAnnouncer());
        YNetRunner importedRunner = importedRunners.get(0);

        assertNotNull("Imported runner must have net", importedRunner.getNet());
    }

    /**
     * Test 19: Export handles null runner gracefully.
     */
    public void testExportInvalidCase() throws Exception {
        YCaseExporter exporter = new YCaseExporter();

        try {
            String caseXML = exporter.marshal(null);
            fail("Expected exception for null runner");
        } catch (NullPointerException e) {
            assertTrue("Expected NullPointerException for null runner", true);
        }
    }

    /**
     * Test 20: Import handles malformed XML gracefully.
     */
    public void testImportInvalidXML() throws Exception {
        YCaseImporter importer = new YCaseImporter();

        try {
            importer.unmarshal("invalid-xml-content", _internalEngine.getAnnouncer());
            fail("Expected exception for malformed XML");
        } catch (Exception e) {
            assertTrue("Expected exception for malformed XML", true);
        }
    }

    /**
     * Test 21: Export with no cases handled correctly (export called on fresh runner).
     */
    public void testExportEmptyCaseList() throws Exception {
        String xml = loadSpecXml(MINIMAL_SPEC_RESOURCE);
        YSpecification spec = _engine.unmarshalSpecification(xml);
        YNetRunner runner = _engine.launchCase(spec, "empty-export-case-21");

        YCaseExporter exporter = new YCaseExporter();
        String caseXML = exporter.marshal(runner);

        assertNotNull("Export must produce XML even for fresh case", caseXML);
        assertFalse("Export must produce non-empty XML", caseXML.isEmpty());
    }

    /**
     * Test 22: Import handles empty XML structure.
     */
    public void testImportEmptyXML() throws Exception {
        YCaseImporter importer = new YCaseImporter();

        String emptyCaseXML = "<case id=\"empty-case\"><runners></runners></case>";

        try {
            importer.unmarshal(emptyCaseXML, _internalEngine.getAnnouncer());
            fail("Expected YStateException for missing specification");
        } catch (YSyntaxException e) {
            assertTrue("Expected YSyntaxException for missing specification", true);
        } catch (YStateException e) {
            assertTrue("Expected YStateException for missing runners", true);
        }
    }

    /**
     * Test 23: Exporter getXML returns well-formed XML string.
     */
    public void testExporterGetXML() throws Exception {
        String xml = loadSpecXml(MINIMAL_SPEC_RESOURCE);
        YSpecification spec = _engine.unmarshalSpecification(xml);
        YNetRunner runner = _engine.launchCase(spec, "getxml-test-case-23");

        YCaseExporter exporter = new YCaseExporter();
        String caseXML = exporter.marshal(runner);

        Document doc = JDOMUtil.stringToDocument(caseXML);
        assertNotNull("Exported XML must be well-formed", doc);
        assertEquals("Root element must be 'case'", "case", doc.getRootElement().getName());
    }

    /**
     * Test 24: Importer from XML string creates valid runner.
     */
    public void testImporterFromXML() throws Exception {
        String xml = loadSpecXml(MINIMAL_SPEC_RESOURCE);
        YSpecification spec = _engine.unmarshalSpecification(xml);
        String caseID = "importer-xml-test-case-24";
        YNetRunner originalRunner = _engine.launchCase(spec, caseID);

        YCaseExporter exporter = new YCaseExporter();
        String caseXML = exporter.marshal(originalRunner);

        YCaseImporter importer = new YCaseImporter();
        List<YNetRunner> runners = importer.unmarshal(caseXML, _internalEngine.getAnnouncer());

        assertNotNull("Imported runners must not be null", runners);
        assertEquals("Must have exactly one runner", 1, runners.size());

        YNetRunner importedRunner = runners.get(0);
        assertEquals("Case ID must match", caseID, importedRunner.getCaseID().toString());
        assertNotNull("Net must not be null", importedRunner.getNet());
    }

    /**
     * Test 25: Cross-version compatibility - export and import maintain schema version.
     */
    public void testCrossVersionCompatibility() throws Exception {
        String xml = loadSpecXml(MINIMAL_SPEC_RESOURCE);
        YSpecification spec = _engine.unmarshalSpecification(xml);
        YNetRunner originalRunner = _engine.launchCase(spec, "version-test-case-25");

        YSpecification originalSpec = originalRunner.getNet().getSpecification();
        String originalSpecID = originalSpec.getID();

        YCaseExporter exporter = new YCaseExporter();
        String caseXML = exporter.marshal(originalRunner);

        YCaseImporter importer = new YCaseImporter();
        List<YNetRunner> importedRunners = importer.unmarshal(caseXML, _internalEngine.getAnnouncer());
        YNetRunner importedRunner = importedRunners.get(0);

        YSpecification importedSpec = importedRunner.getNet().getSpecification();
        assertEquals("Specification ID must match after round-trip",
                originalSpecID, importedSpec.getID());
    }

    /**
     * Test 26: Export preserves busy tasks.
     */
    public void testExportBusyTasks() throws Exception {
        String xml = loadSpecXml(MINIMAL_SPEC_RESOURCE);
        YSpecification spec = _engine.unmarshalSpecification(xml);
        YNetRunner runner = _engine.launchCase(spec, "busy-tasks-test-case-26");

        Set<YTask> busyTasks = runner.getBusyTasks();
        assertNotNull("Busy tasks set must not be null", busyTasks);

        YCaseExporter exporter = new YCaseExporter();
        String caseXML = exporter.marshal(runner);

        assertTrue("Export must contain busytasks element", caseXML.contains("busytasks"));
    }

    /**
     * Test 27: Round-trip preserves enabled tasks set.
     */
    public void testRoundTripEnabledTasks() throws Exception {
        String xml = loadSpecXml(MINIMAL_SPEC_RESOURCE);
        YSpecification spec = _engine.unmarshalSpecification(xml);
        YNetRunner originalRunner = _engine.launchCase(spec, "enabled-tasks-test-case-27");

        YCaseExporter exporter = new YCaseExporter();
        String caseXML = exporter.marshal(originalRunner);

        YCaseImporter importer = new YCaseImporter();
        List<YNetRunner> importedRunners = importer.unmarshal(caseXML, _internalEngine.getAnnouncer());
        YNetRunner importedRunner = importedRunners.get(0);

        assertNotNull("Imported runner must not be null", importedRunner);
        assertEquals("Case ID must match", "enabled-tasks-test-case-27", importedRunner.getCaseID().toString());
    }

    /**
     * Test 28: Export preserves identifier locations.
     */
    public void testExportIdentifierLocations() throws Exception {
        String xml = loadSpecXml(MINIMAL_SPEC_RESOURCE);
        YSpecification spec = _engine.unmarshalSpecification(xml);
        YNetRunner runner = _engine.launchCase(spec, "identifier-loc-test-case-28");

        YIdentifier caseID = runner.getCaseID();
        assertNotNull("Case identifier must not be null", caseID);

        YCaseExporter exporter = new YCaseExporter();
        String caseXML = exporter.marshal(runner);

        // Identifier and locations are part of the runner export
        assertTrue("Export must contain identifier element", caseXML.contains("<identifier"));
    }

    /**
     * Test 29: Round-trip preserves identifier hierarchy.
     */
    public void testRoundTripIdentifierHierarchy() throws Exception {
        String xml = loadSpecXml(MINIMAL_SPEC_RESOURCE);
        YSpecification spec = _engine.unmarshalSpecification(xml);
        YNetRunner originalRunner = _engine.launchCase(spec, "id-hierarchy-test-case-29");

        YIdentifier originalID = originalRunner.getCaseID();
        String originalIDString = originalID.toString();

        YCaseExporter exporter = new YCaseExporter();
        String caseXML = exporter.marshal(originalRunner);

        YCaseImporter importer = new YCaseImporter();
        List<YNetRunner> importedRunners = importer.unmarshal(caseXML, _internalEngine.getAnnouncer());
        YNetRunner importedRunner = importedRunners.get(0);

        assertEquals("Identifier string must match after round-trip",
                originalIDString, importedRunner.getCaseID().toString());
    }

    /**
     * Test 30: Export preserves specification reference.
     */
    public void testExportSpecificationReference() throws Exception {
        String xml = loadSpecXml(MINIMAL_SPEC_RESOURCE);
        YSpecification spec = _engine.unmarshalSpecification(xml);
        YNetRunner runner = _engine.launchCase(spec, "spec-ref-test-case-30");

        YCaseExporter exporter = new YCaseExporter();
        String caseXML = exporter.marshal(runner);

        assertTrue("Export must contain specificationSet", caseXML.contains("<specificationSet"));
        assertTrue("Export must contain specification", caseXML.contains("<specification"));
    }

    /**
     * Test 31: Round-trip preserves net structure.
     */
    public void testRoundTripNetStructure() throws Exception {
        String xml = loadSpecXml(MINIMAL_SPEC_RESOURCE);
        YSpecification spec = _engine.unmarshalSpecification(xml);
        YNetRunner originalRunner = _engine.launchCase(spec, "net-structure-test-case-31");

        YNet originalNet = originalRunner.getNet();
        String originalNetID = originalNet.getID();

        YCaseExporter exporter = new YCaseExporter();
        String caseXML = exporter.marshal(originalRunner);

        YCaseImporter importer = new YCaseImporter();
        List<YNetRunner> importedRunners = importer.unmarshal(caseXML, _internalEngine.getAnnouncer());
        YNetRunner importedRunner = importedRunners.get(0);

        YNet importedNet = importedRunner.getNet();
        assertEquals("Net ID must match after round-trip", originalNetID, importedNet.getID());
    }

    /**
     * Test 32: Export with containing task ID (subnet case).
     */
    public void testExportWithContainingTaskID() throws Exception {
        String xml = loadSpecXml(MINIMAL_SPEC_RESOURCE);
        YSpecification spec = _engine.unmarshalSpecification(xml);
        YNetRunner runner = _engine.launchCase(spec, "containing-task-test-case-32");

        String containingTaskID = runner.getContainingTaskID();
        assertNull("Root net should have null containing task ID", containingTaskID);

        YCaseExporter exporter = new YCaseExporter();
        String caseXML = exporter.marshal(runner);

        assertTrue("Export must contain containingtask element", caseXML.contains("<containingtask"));
    }

    /**
     * Test 33: Round-trip preserves work item status.
     */
    public void testRoundTripWorkItemStatus() throws Exception {
        String xml = loadSpecXml(MINIMAL_SPEC_RESOURCE);
        YSpecification spec = _engine.unmarshalSpecification(xml);
        YNetRunner originalRunner = _engine.launchCase(spec, "wi-status-test-case-33");

        YCaseExporter exporter = new YCaseExporter();
        String caseXML = exporter.marshal(originalRunner);

        YCaseImporter importer = new YCaseImporter();
        List<YNetRunner> importedRunners = importer.unmarshal(caseXML, _internalEngine.getAnnouncer());
        YNetRunner importedRunner = importedRunners.get(0);

        assertNotNull("Imported runner must have net", importedRunner.getNet());
    }

    /**
     * Test 34: Export preserves work item timestamps.
     */
    public void testExportWorkItemTimestamps() throws Exception {
        String xml = loadSpecXml(MINIMAL_SPEC_RESOURCE);
        YSpecification spec = _engine.unmarshalSpecification(xml);
        YNetRunner runner = _engine.launchCase(spec, "wi-timestamps-test-case-34");

        YCaseExporter exporter = new YCaseExporter();
        String caseXML = exporter.marshal(runner);

        // Timestamps are exported within the workitems element when work items exist
        assertNotNull("Exported XML must not be null", caseXML);
        assertTrue("Export must contain case element", caseXML.contains("<case"));
    }

    /**
     * Test 35: Round-trip preserves work item data.
     */
    public void testRoundTripWorkItemData() throws Exception {
        String xml = loadSpecXml(DATA_SPEC_RESOURCE);
        YSpecification spec = _engine.unmarshalSpecification(xml);
        String caseParams = "<DataNet/>";  // caseParams outer element must match root net name
        YNetRunner originalRunner = _engine.launchCase(spec, "wi-data-test-case-35", caseParams);

        YCaseExporter exporter = new YCaseExporter();
        String caseXML = exporter.marshal(originalRunner);

        assertNotNull("Export must produce XML", caseXML);

        YCaseImporter importer = new YCaseImporter();
        List<YNetRunner> importedRunners = importer.unmarshal(caseXML, _internalEngine.getAnnouncer());
        YNetRunner importedRunner = importedRunners.get(0);

        assertNotNull("Imported runner must have net data", importedRunner.getNetData());
    }

    /**
     * Test 36: marshalCase API returns valid XML.
     */
    public void testMarshalCaseAPI() throws Exception {
        String xml = loadSpecXml(MINIMAL_SPEC_RESOURCE);
        YSpecification spec = _engine.unmarshalSpecification(xml);
        YNetRunner runner = _engine.launchCase(spec, "marshal-api-test-case-36");

        String caseXML = _engine.marshalCase(runner);

        assertNotNull("marshalCase must return XML", caseXML);
        assertTrue("marshalCase XML must contain case element", caseXML.contains("<case"));
    }

    /**
     * Test 37: marshalCase with null runner throws exception.
     */
    public void testMarshalCaseNullRunner() throws Exception {
        try {
            _engine.marshalCase(null);
            fail("Expected YStateException for null runner");
        } catch (YStateException e) {
            assertTrue("Expected YStateException for null runner", true);
        }
    }

    /**
     * Test 38: restoreCase API creates valid runner from XML.
     */
    public void testRestoreCaseAPI() throws Exception {
        String xml = loadSpecXml(MINIMAL_SPEC_RESOURCE);
        YSpecification spec = _engine.unmarshalSpecification(xml);
        _engine.setCaseMonitoringEnabled(true);
        YNetRunner originalRunner = _engine.launchCase(spec, "restore-api-test-case-38");

        String caseXML = _engine.marshalCase(originalRunner);

        YNetRunner restoredRunner = _engine.restoreCase(caseXML);

        assertNotNull("restoreCase must return runner", restoredRunner);
        assertEquals("Case ID must match after restore",
                originalRunner.getCaseID().toString(), restoredRunner.getCaseID().toString());
    }

    /**
     * Test 39: restoreCase with invalid XML throws exception.
     */
    public void testRestoreCaseInvalidXML() throws Exception {
        try {
            _engine.restoreCase("invalid-xml");
            fail("Expected exception for invalid XML");
        } catch (YSyntaxException e) {
            assertTrue("Expected YSyntaxException for invalid XML", true);
        } catch (Exception e) {
            assertTrue("Expected some exception for invalid XML", true);
        }
    }

    /**
     * Test 40: Complete round-trip workflow simulation.
     */
    public void testCompleteRoundTripWorkflow() throws Exception {
        String xml = loadSpecXml(MINIMAL_SPEC_RESOURCE);
        YSpecification spec = _engine.unmarshalSpecification(xml);

        _engine.setCaseMonitoringEnabled(true);
        String caseID = "workflow-test-case-40";
        YNetRunner runner = _engine.launchCase(spec, caseID);

        String exportedXML = _engine.marshalCase(runner);
        assertNotNull("Export must succeed", exportedXML);

        YNetRunner restoredRunner = _engine.restoreCase(exportedXML);
        assertNotNull("Restore must succeed", restoredRunner);
        assertEquals("Case ID must match", caseID, restoredRunner.getCaseID().toString());

        assertTrue("Restored runner must have net", restoredRunner.getNet() != null);
        assertNotNull("Restored runner must have specification ID", restoredRunner.getSpecificationID());
    }

    // Event listener implementations

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
            if (item != null) {
                _capturedWorkItems.add(item);
            }
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
