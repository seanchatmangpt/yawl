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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.stateless.elements;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test for YTask class that works with available API.
 */
class SimpleYTaskTest {

    private YSpecification testSpec;
    private YNet testNet;
    private YAtomicTask testTask;

    @BeforeEach
    void setUp() {
        testSpec = new YSpecification("test://uri");
        testNet = new YNet("testNet", testSpec);
        testTask = new YAtomicTask("task1", YTask._AND, YTask._XOR, testNet);
        testNet.addNetElement(testTask);
    }

    @Test
    @DisplayName("Task creation with valid join and split types")
    void taskCreation() {
        assertNotNull(testTask);
        assertEquals("task1", testTask.getID());
        assertEquals(YTask._AND, testTask.getJoinType());
        assertEquals(YTask._XOR, testTask.getSplitType());
    }

    @Test
    @DisplayName("Task has proper ID")
    void taskProperID() {
        String properID = testTask.getProperID();
        assertTrue(properID.contains("task1"));
        assertTrue(properID.contains("test://uri"));
    }

    @Test
    @DisplayName("Task name can be set and retrieved")
    void taskName() {
        testTask.setName("Test Task");
        assertEquals("Test Task", testTask.getName());
    }

    @Test
    @DisplayName("Task documentation can be set and retrieved")
    void taskDocumentation() {
        String doc = "Test documentation";
        testTask.setDocumentation(doc);
        assertEquals(doc, testTask.getDocumentation());
    }

    @Test
    @DisplayName("Task is in net")
    void taskInNet() {
        assertTrue(testNet.getNetElements().contains(testTask));
    }

    @Test
    @DisplayName("Task can be retrieved by ID")
    void taskRetrieval() {
        YExternalNetElement retrieved = testNet.getNetElement("task1");
        assertNotNull(retrieved);
        assertEquals(testTask, retrieved);
    }

    @Test
    @DisplayName("Task XML generation")
    void taskXMLGeneration() {
        String xml = testTask.toXML();
        assertTrue(xml.contains("<task id=\"task1\">"));
        assertTrue(xml.contains("<join code=\"and\"/>"));
        assertTrue(xml.contains("<split code=\"xor\"/>"));
        assertTrue(xml.contains("</task>"));
    }

    @Test
    @DisplayName("Task verification")
    void taskVerification() {
        // Should not throw exception
        assertDoesNotThrow(() -> testTask.verify(new org.yawlfoundation.yawl.util.YVerificationHandler()));
    }
}