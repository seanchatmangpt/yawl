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
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.benchmark;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic test to verify benchmark setup
 */
public class BenchmarkTest {

    @Test
    void testDataGeneratorCreation() {
        TestDataGenerator generator = new TestDataGenerator();
        assertNotNull(generator, "TestDataGenerator should be created successfully");
    }
    
    @Test
    void testWorkflowSpecificationsGeneration() {
        TestDataGenerator generator = new TestDataGenerator();
        var specs = generator.generateWorkflowSpecifications();
        
        assertFalse(specs.isEmpty(), "Should generate workflow specifications");
        assertTrue(specs.containsKey("sequential"), "Should contain sequential workflow");
        assertTrue(specs.containsKey("parallel"), "Should contain parallel workflow");
        assertTrue(specs.containsKey("multiChoice"), "Should contain multi-choice workflow");
    }
    
    @Test
    void testWorkItemsGeneration() {
        TestDataGenerator generator = new TestDataGenerator();
        var workItems = generator.generateWorkItems(10);
        
        assertEquals(10, workItems.size(), "Should generate specified number of work items");
        
        var firstWorkItem = workItems.get(0);
        assertTrue(firstWorkItem.containsKey("id"), "Work item should have ID");
        assertTrue(firstWorkItem.containsKey("caseId"), "Work item should have case ID");
        assertTrue(firstWorkItem.containsKey("taskId"), "Work item should have task ID");
        assertTrue(firstWorkItem.containsKey("status"), "Work item should have status");
    }
    
    @Test
    void testCaseDataGeneration() {
        TestDataGenerator generator = new TestDataGenerator();
        var cases = generator.generateCaseData(5);
        
        assertEquals(5, cases.size(), "Should generate specified number of cases");
        
        var testCase = cases.get(0);
        assertTrue(testCase.containsKey("caseId"), "Case should have case ID");
        assertTrue(testCase.containsKey("specId"), "Case should have spec ID");
        assertTrue(testCase.containsKey("status"), "Case should have status");
        assertTrue(testCase.containsKey("createdTime"), "Case should have creation time");
    }
    
    @Test
    void testPerformanceScenariosGeneration() {
        TestDataGenerator generator = new TestDataGenerator();
        var scenarios = generator.generatePerformanceScenarios();
        
        assertFalse(scenarios.isEmpty(), "Should generate performance scenarios");
        
        var firstScenario = scenarios.get(0);
        assertTrue(firstScenario.containsKey("id"), "Scenario should have ID");
        assertTrue(firstScenario.containsKey("caseCount"), "Scenario should have case count");
        assertTrue(firstScenario.containsKey("concurrency"), "Scenario should have concurrency level");
        assertTrue(firstScenario.containsKey("description"), "Scenario should have description");
    }
}
