/*
 * Copyright (c) 2004-2024 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.stateless;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import org.yawlfoundation.yawl.stateless.elements.*;
import org.yawlfoundation.yawl.stateless.elements.data.YVariable;
import org.yawlfoundation.yawl.stateless.elements.marking.YIdentifier;
import org.yawlfoundation.yawl.stateless.unmarshal.YMarshal;
import org.yawlfoundation.yawl.exceptions.YSyntaxException;
import org.yawlfoundation.yawl.util.StringUtil;
import org.yawlfoundation.yawl.util.YVerificationHandler;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for stateless YNet creation and management.
 * Tests net creation from specification, marking initialization,
 * and enabled task detection.
 *
 * @author YAWL Test Suite
 */
class TestStatelessYNet {

    private static final String MINIMAL_SPEC_RESOURCE = "resources/MinimalSpec.xml";

    private YSpecification specification;
    private YNet rootNet;


    @BeforeEach
    void setUp() throws YSyntaxException {
        InputStream is = getClass().getResourceAsStream(MINIMAL_SPEC_RESOURCE);
        assertNotNull(is, "Test specification file must exist: " + MINIMAL_SPEC_RESOURCE);
        String specXML = StringUtil.streamToString(is);
        assertNotNull(specXML, "Specification XML must not be null");

        List<YSpecification> specs = YMarshal.unmarshalSpecifications(specXML);
        assertFalse(specs.isEmpty(), "Should parse at least one specification");

        specification = specs.get(0);
        rootNet = specification.getRootNet();
        assertNotNull(rootNet, "Root net must not be null");
    }


    @Test
    @DisplayName("Test net creation from specification")
    void testNetCreationFromSpecification() {
        assertNotNull(rootNet, "Net should be created from specification");
        assertEquals("MinimalNet", rootNet.getID(), "Net ID should match specification");
        assertNotNull(rootNet.getSpecification(), "Net should reference parent specification");
        assertEquals(specification, rootNet.getSpecification(), "Net's specification should be the parent");
    }


    @Test
    @DisplayName("Test net has input condition")
    void testNetHasInputCondition() {
        YInputCondition inputCondition = rootNet.getInputCondition();
        assertNotNull(inputCondition, "Net must have an input condition");
        assertEquals("start", inputCondition.getID(), "Input condition ID should match");
    }


    @Test
    @DisplayName("Test net has output condition")
    void testNetHasOutputCondition() {
        YOutputCondition outputCondition = rootNet.getOutputCondition();
        assertNotNull(outputCondition, "Net must have an output condition");
        assertEquals("end", outputCondition.getID(), "Output condition ID should match");
    }


    @Test
    @DisplayName("Test net elements retrieval")
    void testNetElementsRetrieval() {
        Map<String, YExternalNetElement> elements = rootNet.getNetElements();
        assertNotNull(elements, "Net elements map should not be null");
        assertFalse(elements.isEmpty(), "Net should contain elements");

        assertTrue(elements.containsKey("start"), "Should contain input condition");
        assertTrue(elements.containsKey("end"), "Should contain output condition");
    }


    @Test
    @DisplayName("Test net tasks retrieval")
    void testNetTasksRetrieval() {
        List<YTask> tasks = rootNet.getNetTasks();
        assertNotNull(tasks, "Tasks list should not be null");
        assertEquals(1, tasks.size(), "Should have 1 task (task1)");

        boolean hasTask1 = tasks.stream().anyMatch(t -> "task1".equals(t.getID()));
        assertTrue(hasTask1, "Should have task task1");
    }


    @Test
    @DisplayName("Test net element by ID")
    void testNetElementById() {
        YExternalNetElement task1 = rootNet.getNetElement("task1");
        assertNotNull(task1, "Should find task task1");
        assertTrue(task1 instanceof YTask, "task1 should be a task");

        YExternalNetElement inputCond = rootNet.getNetElement("start");
        assertNotNull(inputCond, "Should find input condition");
        assertTrue(inputCond instanceof YInputCondition, "start should be input condition");
    }


    @Test
    @DisplayName("Test marking initialization with identifier")
    void testMarkingInitialization() {
        YIdentifier caseId = new YIdentifier("test-case-001");
        assertNotNull(caseId, "Identifier should be created");
        assertEquals("test-case-001", caseId.toString(), "Identifier string should match");
    }


    @Test
    @DisplayName("Test identifier location management")
    void testIdentifierLocationManagement() {
        YIdentifier caseId = new YIdentifier("test-case-002");
        YInputCondition inputCondition = rootNet.getInputCondition();

        caseId.addLocation(inputCondition);
        List<org.yawlfoundation.yawl.elements.YNetElement> locations = caseId.getLocations();
        assertFalse(locations.isEmpty(), "Identifier should have location");
        assertTrue(locations.contains(inputCondition), "Should contain input condition");

        caseId.removeLocation(inputCondition);
        locations = caseId.getLocations();
        assertTrue(locations.isEmpty(), "Location should be removed");
    }


    @Test
    @DisplayName("Test enabled task detection")
    void testEnabledTaskDetection() {
        YIdentifier caseId = new YIdentifier("test-case-003");

        Set<YTask> enabledTasks = rootNet.getEnabledTasks(caseId);
        assertNotNull(enabledTasks, "Enabled tasks set should not be null");
    }


    @Test
    @DisplayName("Test net verification")
    void testNetVerification() {
        YVerificationHandler handler = new YVerificationHandler();
        rootNet.verify(handler);

        assertFalse(handler.hasErrors(), "Valid net should have no verification errors: " +
                handler.getMessages());
    }


    @Test
    @DisplayName("Test net local variables")
    void testNetLocalVariables() {
        Map<String, YVariable> localVars = rootNet.getLocalVariables();
        assertNotNull(localVars, "Local variables map should not be null");
        // MinimalSpec has no local variables
    }


    @Test
    @DisplayName("Test net clone")
    void testNetClone() {
        YNet clonedNet = (YNet) rootNet.clone();
        assertNotNull(clonedNet, "Cloned net should not be null");
        assertEquals(rootNet.getID(), clonedNet.getID(), "Cloned net ID should match");
        assertNotSame(rootNet, clonedNet, "Cloned net should be different object");
    }


    @Test
    @DisplayName("Test specification decompositions")
    void testSpecificationDecompositions() {
        Set<YDecomposition> decompositions = specification.getDecompositions();
        assertNotNull(decompositions, "Decompositions should not be null");
        assertFalse(decompositions.isEmpty(), "Should have decompositions");

        YDecomposition minimalNet = specification.getDecomposition("MinimalNet");
        assertNotNull(minimalNet, "Should find decomposition MinimalNet");
    }


    @Test
    @DisplayName("Test specification ID generation")
    void testSpecificationIdGeneration() {
        org.yawlfoundation.yawl.engine.YSpecificationID specId = specification.getSpecificationID();
        assertNotNull(specId, "Specification ID should not be null");
        assertNotNull(specId.getUri(), "Specification URI should not be null");
    }


    @Test
    @DisplayName("Test task preset and postset")
    void testTaskPresetPostset() {
        YTask task1 = (YTask) rootNet.getNetElement("task1");

        Set<YExternalNetElement> preset = task1.getPresetElements();
        assertNotNull(preset, "Preset should not be null");
        assertFalse(preset.isEmpty(), "Task should have preset elements");

        Set<YExternalNetElement> postset = task1.getPostsetElements();
        assertNotNull(postset, "Postset should not be null");
        assertFalse(postset.isEmpty(), "Task should have postset elements");
    }


    @Test
    @DisplayName("Test task join and split types")
    void testTaskJoinSplitTypes() {
        YTask task1 = (YTask) rootNet.getNetElement("task1");

        int joinType = task1.getJoinType();
        assertTrue(joinType == YTask._XOR || joinType == YTask._AND || joinType == YTask._OR,
                "Join type should be valid");

        int splitType = task1.getSplitType();
        assertTrue(splitType == YTask._XOR || splitType == YTask._AND || splitType == YTask._OR,
                "Split type should be valid");
    }
}
