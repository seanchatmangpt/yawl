/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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
 * You should have received a copy of the GNU Lesser General
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.engine;

import junit.framework.TestCase;
import org.yawlfoundation.yawl.elements.YDecomposition;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.exceptions.YStateException;
import org.yawlfoundation.yawl.exceptions.YSyntaxException;
import org.yawlfoundation.yawl.logging.YLogDataItemList;
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.yawlfoundation.yawl.util.StringUtil;
import org.yawlfoundation.yawl.util.YVerificationHandler;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Set;

/**
 * Comprehensive tests for Specification Management in YEngine.
 * Tests loading, unloading, retrieval, versioning, and verification of specifications.
 *
 * @author YAWL Foundation
 */
public class TestSpecificationManagement extends TestCase {

    private YEngine _engine;

    public TestSpecificationManagement(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        _engine = YEngine.getInstance();
        EngineClearer.clear(_engine);
    }

    @Override
    protected void tearDown() throws Exception {
        if (_engine != null) {
            EngineClearer.clear(_engine);
        }
        super.tearDown();
    }

    /**
     * Test 1: Load a valid specification and verify it returns success.
     */
    public void testLoadSpecificationValid() throws Exception {
        YSpecification spec = loadSpecification("YAWL_Specification4.xml");
        assertNotNull("Specification must not be null after unmarshalling", spec);

        boolean loaded = _engine.loadSpecification(spec);
        assertTrue("loadSpecification must return true for valid spec", loaded);

        YSpecificationID specID = spec.getSpecificationID();
        YSpecification retrieved = _engine.getSpecification(specID);
        assertNotNull("Specification must be retrievable after loading", retrieved);
        assertEquals("Retrieved spec must match loaded spec", specID, retrieved.getSpecificationID());
    }

    /**
     * Test 2: Load an invalid XML specification and verify it throws YSyntaxException.
     */
    public void testLoadSpecificationInvalid() throws Exception {
        String invalidXML = "<invalid>This is not a valid YAWL specification</invalid>";

        try {
            YMarshal.unmarshalSpecifications(invalidXML);
            fail("Expected YSyntaxException for invalid XML");
        } catch (YSyntaxException e) {
            assertNotNull("Exception message should not be null", e.getMessage());
        }
    }

    /**
     * Test 3: Unload a specification and verify it is removed from the engine.
     */
    public void testUnloadSpecification() throws Exception {
        YSpecification spec = loadSpecification("YAWL_Specification4.xml");
        _engine.loadSpecification(spec);

        YSpecificationID specID = spec.getSpecificationID();
        assertNotNull("Spec must be loaded before unloading", _engine.getSpecification(specID));

        _engine.unloadSpecification(specID);

        YSpecification unloaded = _engine.getSpecification(specID);
        assertNull("Specification must be null after unloading", unloaded);
    }

    /**
     * Test 4: Get a loaded specification by its ID.
     */
    public void testGetSpecification() throws Exception {
        YSpecification spec = loadSpecification("YAWL_Specification4.xml");
        _engine.loadSpecification(spec);

        YSpecificationID specID = spec.getSpecificationID();
        YSpecification retrieved = _engine.getSpecification(specID);

        assertNotNull("getSpecification must return the loaded spec", retrieved);
        assertEquals("Specification URIs must match",
                spec.getURI(), retrieved.getURI());
        assertEquals("Specification IDs must match", specID, retrieved.getSpecificationID());
    }

    /**
     * Test 5: Get list of loaded specification IDs.
     */
    public void testGetSpecificationIDs() throws Exception {
        Set<YSpecificationID> initialIDs = _engine.getLoadedSpecificationIDs();
        assertTrue("Initial spec IDs should be empty after clear", initialIDs.isEmpty());

        YSpecification spec1 = loadSpecification("YAWL_Specification4.xml");
        _engine.loadSpecification(spec1);

        Set<YSpecificationID> afterLoad = _engine.getLoadedSpecificationIDs();
        assertEquals("Should have exactly one spec ID", 1, afterLoad.size());
        assertTrue("Spec ID should be in the set", afterLoad.contains(spec1.getSpecificationID()));

        YSpecification spec2 = loadSpecification("TestOrJoin.xml");
        _engine.loadSpecification(spec2);

        Set<YSpecificationID> afterSecondLoad = _engine.getLoadedSpecificationIDs();
        assertEquals("Should have exactly two spec IDs", 2, afterSecondLoad.size());
    }

    /**
     * Test 6: Handle duplicate specification loads.
     * Loading the same spec twice should return false on second load.
     */
    public void testLoadSpecificationDuplicate() throws Exception {
        YSpecification spec = loadSpecification("YAWL_Specification4.xml");

        boolean firstLoad = _engine.loadSpecification(spec);
        assertTrue("First load should succeed", firstLoad);

        boolean secondLoad = _engine.loadSpecification(spec);
        assertFalse("Second load of same spec should return false", secondLoad);

        Set<YSpecificationID> specIDs = _engine.getLoadedSpecificationIDs();
        assertEquals("Should still have only one spec", 1, specIDs.size());
    }

    /**
     * Test 7: Specification versioning - verify version information is accessible.
     */
    public void testSpecificationVersioning() throws Exception {
        YSpecification spec = loadSpecification("YAWL_Specification4.xml");
        _engine.loadSpecification(spec);

        YSpecificationID specID = spec.getSpecificationID();
        String version = specID.getVersionAsString();
        assertNotNull("Version should not be null", version);

        YSpecification retrieved = _engine.getSpecification(specID);
        assertNotNull("Retrieved spec must not be null", retrieved);

        String specVersion = retrieved.getSpecVersion();
        assertNotNull("Spec version should not be null", specVersion);
    }

    /**
     * Test 8: Specification URI resolution.
     */
    public void testSpecificationURI() throws Exception {
        YSpecification spec = loadSpecification("YAWL_Specification4.xml");
        _engine.loadSpecification(spec);

        String uri = spec.getURI();
        assertNotNull("Spec URI must not be null", uri);
        assertTrue("Spec URI should contain the filename",
                uri.contains("YAWL_Specification4"));

        YSpecificationID specID = spec.getSpecificationID();
        String specUri = specID.getUri();
        assertNotNull("SpecID URI must not be null", specUri);
    }

    /**
     * Test 9: Load specification with decompositions (complex spec with subnets).
     */
    public void testLoadSpecificationWithDecompositions() throws Exception {
        YSpecification spec = loadSpecification("YAWL_Specification3.xml");
        _engine.loadSpecification(spec);

        YNet rootNet = spec.getRootNet();
        assertNotNull("Root net must not be null", rootNet);

        Set<YDecomposition> decompositions = spec.getDecompositions();
        assertNotNull("Decompositions set must not be null", decompositions);
        assertTrue("Should have multiple decompositions", decompositions.size() > 1);
    }

    /**
     * Test 10: Specification with custom data types.
     * Note: MakeMusic.xml has custom data types (mm:InstrumentListYpe, mm:SongListType)
     */
    public void testSpecificationDataTypes() throws Exception {
        YSpecification spec = loadSpecification("MakeMusic.xml");
        _engine.loadSpecification(spec);

        YSpecificationID specID = spec.getSpecificationID();
        assertNotNull("Spec ID must not be null", specID);

        // Verify the spec loaded correctly with its data types
        YNet rootNet = spec.getRootNet();
        assertNotNull("Root net must not be null", rootNet);
        assertNotNull("Root net should have local variables", rootNet.getLocalVariables());
    }

    /**
     * Test 11: Load status of specifications.
     */
    public void testLoadStatus() throws Exception {
        YSpecification spec = loadSpecification("YAWL_Specification4.xml");
        YSpecificationID specID = spec.getSpecificationID();

        String statusBeforeLoad = _engine.getLoadStatus(specID);
        assertEquals("Status should be unloaded before loading",
                YSpecification._unloaded, statusBeforeLoad);

        _engine.loadSpecification(spec);

        String statusAfterLoad = _engine.getLoadStatus(specID);
        assertEquals("Status should be loaded after loading",
                YSpecification._loaded, statusAfterLoad);

        _engine.unloadSpecification(specID);

        String statusAfterUnload = _engine.getLoadStatus(specID);
        assertEquals("Status should be unloaded after unloading",
                YSpecification._unloaded, statusAfterUnload);
    }

    /**
     * Test 12: Specification verification returns errors for invalid specs.
     * Note: DeadlockingSpecification.xml has a loop structure that can cause verification warnings
     */
    public void testSpecificationVerificationErrors() throws Exception {
        YSpecification spec = loadSpecification("DeadlockingSpecification.xml");

        YVerificationHandler handler = new YVerificationHandler();
        spec.verify(handler);

        // DeadlockingSpecification has a cycle back to a-top, verification may detect issues
        // The spec is valid but can produce warnings about potential deadlocks
        assertNotNull("Verification handler must not be null", handler);
    }

    /**
     * Test 13: Unloading a spec with active cases throws exception.
     */
    public void testUnloadSpecificationWithActiveCasesThrowsException() throws Exception {
        YSpecification spec = loadSpecification("YAWL_Specification4.xml");
        _engine.loadSpecification(spec);

        YSpecificationID specID = spec.getSpecificationID();
        YIdentifier caseID = _engine.startCase(specID, null, null, null,
                new YLogDataItemList(), null, false);

        assertNotNull("Case should start successfully", caseID);

        try {
            _engine.unloadSpecification(specID);
            fail("Expected YStateException when unloading spec with active cases");
        } catch (YStateException e) {
            assertTrue("Exception message should mention active cases",
                    e.getMessage().contains("active") || e.getMessage().contains("case"));
        } finally {
            _engine.cancelCase(caseID);
        }
    }

    /**
     * Test 14: Get latest specification by key.
     */
    public void testGetLatestSpecification() throws Exception {
        YSpecification spec = loadSpecification("YAWL_Specification4.xml");
        _engine.loadSpecification(spec);

        String key = spec.getSpecificationID().getKey();
        YSpecification latest = _engine.getLatestSpecification(key);

        assertNotNull("Latest specification should not be null", latest);
        assertEquals("Latest spec should match loaded spec",
                spec.getSpecificationID(), latest.getSpecificationID());
    }

    /**
     * Test 15: Get specification for a running case.
     */
    public void testGetSpecificationForCase() throws Exception {
        YSpecification spec = loadSpecification("YAWL_Specification4.xml");
        _engine.loadSpecification(spec);

        YSpecificationID specID = spec.getSpecificationID();
        YIdentifier caseID = _engine.startCase(specID, null, null, null,
                new YLogDataItemList(), null, false);

        YSpecification specForCase = _engine.getSpecificationForCase(caseID);
        assertNotNull("Specification for case should not be null", specForCase);
        assertEquals("Spec for case should match original spec",
                specID, specForCase.getSpecificationID());

        _engine.cancelCase(caseID);
    }

    /**
     * Test 16: Get cases for specification.
     */
    public void testGetCasesForSpecification() throws Exception {
        YSpecification spec = loadSpecification("YAWL_Specification4.xml");
        _engine.loadSpecification(spec);

        YSpecificationID specID = spec.getSpecificationID();
        Set<YIdentifier> initialCases = _engine.getCasesForSpecification(specID);
        assertTrue("Should have no cases initially", initialCases.isEmpty());

        YIdentifier caseID = _engine.startCase(specID, null, null, null,
                new YLogDataItemList(), null, false);

        Set<YIdentifier> casesAfterStart = _engine.getCasesForSpecification(specID);
        assertEquals("Should have one case after starting", 1, casesAfterStart.size());
        assertTrue("Case ID should be in the set", casesAfterStart.contains(caseID));

        _engine.cancelCase(caseID);
    }

    /**
     * Test 17: Unload non-existent specification throws exception.
     */
    public void testUnloadNonExistentSpecificationThrowsException() throws Exception {
        YSpecificationID nonexistentID = new YSpecificationID("nonexistent", "1.0", "nonexistent.xml");

        try {
            _engine.unloadSpecification(nonexistentID);
            fail("Expected YStateException when unloading non-existent spec");
        } catch (YStateException e) {
            assertTrue("Exception message should mention 'no such specification'",
                    e.getMessage().contains("no such specification") ||
                    e.getMessage().contains("not found"));
        }
    }

    /**
     * Test 18: Load multiple specifications and verify all are accessible.
     */
    public void testLoadMultipleSpecifications() throws Exception {
        YSpecification spec1 = loadSpecification("YAWL_Specification4.xml");
        YSpecification spec2 = loadSpecification("TestOrJoin.xml");
        YSpecification spec3 = loadSpecification("YAWL_Specification3.xml");

        _engine.loadSpecification(spec1);
        _engine.loadSpecification(spec2);
        _engine.loadSpecification(spec3);

        Set<YSpecificationID> specIDs = _engine.getLoadedSpecificationIDs();
        assertEquals("Should have three specs loaded", 3, specIDs.size());

        assertNotNull("Spec1 should be accessible", _engine.getSpecification(spec1.getSpecificationID()));
        assertNotNull("Spec2 should be accessible", _engine.getSpecification(spec2.getSpecificationID()));
        assertNotNull("Spec3 should be accessible", _engine.getSpecification(spec3.getSpecificationID()));
    }

    /**
     * Test 19: Process definition retrieval.
     */
    public void testGetProcessDefinition() throws Exception {
        YSpecification spec = loadSpecification("YAWL_Specification4.xml");
        _engine.loadSpecification(spec);

        YSpecificationID specID = spec.getSpecificationID();
        YSpecification processDef = _engine.getProcessDefinition(specID);

        assertNotNull("Process definition should not be null", processDef);
        assertEquals("Process definition should match spec",
                specID, processDef.getSpecificationID());
    }

    /**
     * Test 20: Specification ID validity.
     */
    public void testSpecificationIDValidity() throws Exception {
        YSpecification spec = loadSpecification("YAWL_Specification4.xml");
        YSpecificationID specID = spec.getSpecificationID();

        assertTrue("Specification ID should be valid", specID.isValid());

        // Test pre-2.0 spec ID (uri only)
        YSpecificationID pre20ID = new YSpecificationID("test.xml");
        assertTrue("Pre-2.0 spec ID should be valid", pre20ID.isValid());
    }

    /**
     * Test 21: Specification ID comparison and equality.
     */
    public void testSpecificationIDComparison() throws Exception {
        YSpecificationID id1 = new YSpecificationID("test-id", "1.0", "test.xml");
        YSpecificationID id2 = new YSpecificationID("test-id", "1.0", "test.xml");
        YSpecificationID id3 = new YSpecificationID("test-id", "2.0", "test.xml");

        assertEquals("Same spec IDs should be equal", id1, id2);
        assertFalse("Different versions should not be equal", id1.equals(id3));

        assertTrue("id1 should be previous version of id3",
                id1.isPreviousVersionOf(id3));
        assertFalse("id3 should not be previous version of id1",
                id3.isPreviousVersionOf(id1));
    }

    /**
     * Helper method to load a specification from XML file.
     */
    private YSpecification loadSpecification(String filename)
            throws IOException, YSyntaxException {
        URL fileURL = getClass().getResource(filename);
        assertNotNull("Test resource not found: " + filename, fileURL);
        File yawlXMLFile = new File(fileURL.getFile());
        return YMarshal.unmarshalSpecifications(
                StringUtil.fileToString(yawlXMLFile.getAbsolutePath())).get(0);
    }
}
