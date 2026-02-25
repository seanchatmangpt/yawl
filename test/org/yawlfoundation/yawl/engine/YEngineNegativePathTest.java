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
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.engine;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.jdom2.Element;
import org.yawlfoundation.yawl.authentication.YExternalClient;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.exceptions.*;
import org.yawlfoundation.yawl.logging.YLogDataItemList;
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.yawlfoundation.yawl.util.StringUtil;

import java.io.File;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Negative path (error handling) tests for {@link YEngine}.
 *
 * <p>Chicago TDD approach: tests use real engine, real YAWL_Specification2.xml,
 * and real exception types. Tests validate that invalid inputs are rejected
 * appropriately with expected exceptions. No mocks or stubs.
 *
 * <p>Error scenarios covered:
 * <ol>
 *   <li>Null specification load - throws exception</li>
 *   <li>Case start with unknown spec - throws YEngineStateException</li>
 *   <li>Fetch unknown work item - returns null (graceful)</li>
 *   <li>Start null work item - throws exception</li>
 *   <li>Complete enabled work item - throws YStateException (wrong state)</li>
 *   <li>Unload spec with running case - throws YStateException</li>
 *   <li>Start case with null spec ID - throws exception</li>
 *   <li>Cancel unknown case ID - no exception (idempotent)</li>
 *   <li>Complete work item with null data - throws exception</li>
 *   <li>Load-unload-reload spec - succeeds (no exceptions)</li>
 * </ol>
 *
 * @author YAWL Foundation
 * @see YEngine#loadSpecification(YSpecification)
 * @see YEngine#startCase(YSpecificationID, String, java.net.URI, String, YLogDataItemList, String, boolean)
 * @see YEngine#completeWorkItem(YWorkItem, String, String, WorkItemCompletion)
 */
@Tag("unit")
@Execution(ExecutionMode.SAME_THREAD)
@DisplayName("YEngine Negative Path & Error Handling Tests")
class YEngineNegativePathTest {

    private YEngine _engine;
    private YSpecification _spec;

    @BeforeEach
    void setUp() throws Exception {
        _engine = YEngine.getInstance();
        EngineClearer.clear(_engine);
        _spec = loadSpec("YAWL_Specification2.xml");
    }

    @AfterEach
    void tearDown() throws Exception {
        EngineClearer.clear(_engine);
    }

    // -------------------------------------------------------------------------
    // Test 1: Load null specification throws exception
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Loading null specification returns false (graceful handling)")
    void testLoadSpecification_null_returnsFalse() {
        // Attempt to load null spec - engine gracefully returns false instead of throwing
        boolean result = _engine.loadSpecification(null);
        assertFalse(result, "Loading null specification should return false");
    }

    // -------------------------------------------------------------------------
    // Test 2: Start case with unknown spec ID throws YEngineStateException
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Starting case with unknown specification ID throws YStateException")
    void testStartCase_specNotLoaded_throwsYStateException() throws Exception {
        // Create unknown spec ID (not loaded in engine)
        YSpecificationID unknownSpecId = new YSpecificationID("urn:unknown:spec", "1.0", "unknown");

        // Attempt to start case with unknown spec - throws YStateException not YEngineStateException
        assertThrows(YStateException.class, () -> {
            _engine.startCase(unknownSpecId, null, null, null,
                    new YLogDataItemList(), null, false);
        }, "Starting case with unknown spec ID must throw YStateException");
    }

    // -------------------------------------------------------------------------
    // Test 3: Get unknown work item returns null (no exception)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Getting unknown work item returns null gracefully")
    void testGetWorkItem_unknownId_returnsNull() throws Exception {
        // Load spec (so engine has something)
        _engine.loadSpecification(_spec);

        // Attempt to get non-existent work item ID
        YWorkItem item = _engine.getWorkItem("nonexistent:task:12345");
        assertNull(item, "getWorkItem() with unknown ID must return null, not throw");
    }

    // -------------------------------------------------------------------------
    // Test 4: Start null work item throws exception
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Starting null work item throws exception")
    void testStartWorkItem_nullItem_throwsException() throws Exception {
        // Attempt to start null work item - should throw YStateException
        assertThrows(YStateException.class, () -> {
            _engine.startWorkItem(null, null);
        }, "Starting null work item must throw an exception");
    }

    // -------------------------------------------------------------------------
    // Test 5: Complete enabled work item (wrong state) throws YStateException
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Completing enabled work item throws YStateException (wrong state)")
    void testCompleteWorkItem_wrongState_throwsException() throws Exception {
        // Load spec and start a case
        _engine.loadSpecification(_spec);
        YIdentifier caseId = _engine.startCase(
                _spec.getSpecificationID(), null, null, null,
                new YLogDataItemList(), null, false);
        assertNotNull(caseId, "Case must start successfully");

        // Get an enabled work item (not an executing child)
        YWorkItem enabledItem = _engine.getWorkItem(caseId.toString() + ":a-top");
        assertNotNull(enabledItem, "Enabled work item must be found");

        // Attempt to complete the enabled item (should be in statusEnabled, not executing)
        assertThrows(YStateException.class, () -> {
            _engine.completeWorkItem(enabledItem, "<data/>", null, WorkItemCompletion.Normal);
        }, "Completing enabled work item must throw YStateException");
    }

    // -------------------------------------------------------------------------
    // Test 6: Unload specification with running case throws YStateException
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Unloading specification with running case throws YStateException")
    void testUnloadSpecification_withRunningCase_throwsYStateException() throws Exception {
        // Load spec and start a case
        _engine.loadSpecification(_spec);
        YIdentifier caseId = _engine.startCase(
                _spec.getSpecificationID(), null, null, null,
                new YLogDataItemList(), null, false);
        assertNotNull(caseId, "Case must start successfully");

        // Attempt to unload spec while case is running
        assertThrows(YStateException.class, () -> {
            _engine.unloadSpecification(_spec.getSpecificationID());
        }, "Unloading spec with running case must throw YStateException");
    }

    // -------------------------------------------------------------------------
    // Test 7: Start case with null specification ID throws exception
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Starting case with null specification ID throws exception")
    void testStartCase_nullSpecId_throwsException() {
        // Attempt to start case with null spec ID
        assertThrows(Exception.class, () -> {
            _engine.startCase(null, null, null, null,
                    new YLogDataItemList(), null, false);
        }, "Starting case with null spec ID must throw an exception");
    }

    // -------------------------------------------------------------------------
    // Test 8: Cancel unknown case ID succeeds without exception (idempotent)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Cancelling unknown case ID does not throw exception")
    void testCancelCase_unknownCaseId_noException() throws Exception {
        // Create unknown case ID
        YIdentifier unknownCaseId = new YIdentifier("unknownCase:12345");

        // Attempt to cancel unknown case - should not throw
        assertDoesNotThrow(() -> {
            _engine.cancelCase(unknownCaseId);
        }, "Cancelling unknown case ID must not throw exception");
    }

    // -------------------------------------------------------------------------
    // Test 9: Complete work item with null data throws exception
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Completing work item with null data throws exception")
    void testCompleteWorkItem_nullData_throwsException() throws Exception {
        // Load spec and start a case
        _engine.loadSpecification(_spec);
        YIdentifier caseId = _engine.startCase(
                _spec.getSpecificationID(), null, null, null,
                new YLogDataItemList(), null, false);
        assertNotNull(caseId, "Case must start successfully");

        // Get an enabled work item and start it (make it executing)
        YWorkItem enabledItem = _engine.getWorkItem(caseId.toString() + ":a-top");
        assertNotNull(enabledItem, "Enabled work item must exist");

        YExternalClient adminClient = _engine.getExternalClient("admin");
        YWorkItem executingItem = _engine.startWorkItem(enabledItem, adminClient);
        assertNotNull(executingItem, "Work item must start successfully and be executing");

        // Attempt to complete executing item with null data
        assertThrows(Exception.class, () -> {
            _engine.completeWorkItem(executingItem, null, null, WorkItemCompletion.Normal);
        }, "Completing work item with null data must throw an exception");
    }

    // -------------------------------------------------------------------------
    // Test 10: Load, unload, reload same specification succeeds
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Load-unload-reload specification cycle succeeds")
    void testLoadSameSpecTwice_secondLoadSucceeds() throws Exception {
        // Load spec
        _engine.loadSpecification(_spec);
        YSpecification loaded1 = _engine.getSpecification(_spec.getSpecificationID());
        assertNotNull(loaded1, "Spec must be loaded first time");

        // Unload spec
        _engine.unloadSpecification(_spec.getSpecificationID());
        YSpecification afterUnload = _engine.getSpecification(_spec.getSpecificationID());
        assertNull(afterUnload, "Spec must be unloaded");

        // Reload same spec - must not throw
        assertDoesNotThrow(() -> {
            _engine.loadSpecification(_spec);
        }, "Reloading same spec after unload must not throw exception");

        // Verify it's available again
        YSpecification loaded2 = _engine.getSpecification(_spec.getSpecificationID());
        assertNotNull(loaded2, "Spec must be available after reload");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private YSpecification loadSpec(String resourceName) throws Exception {
        URL url = getClass().getResource(resourceName);
        assertNotNull(url, "Test resource not found: " + resourceName);
        return YMarshal.unmarshalSpecifications(
                StringUtil.fileToString(new File(url.getFile()).getAbsolutePath()), false).get(0);
    }
}
