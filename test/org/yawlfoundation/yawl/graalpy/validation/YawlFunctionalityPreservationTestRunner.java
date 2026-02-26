/*
 * Copyright 2026 YAWL Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.yawlfoundation.yawl.graalpy.validation;

import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.util.Arrays;
import java.util.List;

/**
 * Test runner for YAWL functionality preservation tests.
 *
 * This class provides a simple way to run the functionality preservation tests
 * and collect results.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class YawlFunctionalityPreservationTestRunner {

    /**
     * Main method to run the functionality preservation tests.
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        System.out.println("Running YAWL Functionality Preservation Tests...");
        System.out.println("=============================================");

        // Set up classpath if needed
        setupClasspath();

        // Run specific test methods
        runTestMethods();

        System.out.println("=============================================");
        System.out.println("Test execution completed.");
    }

    private static void setupClasspath() {
        // Add necessary JARs to classpath
        List<String> requiredJars = Arrays.asList(
            "lib/yawl-core.jar",
            "lib/yawl-engine.jar",
            "lib/junit-jupiter-api.jar",
            "lib/junit-jupiter-engine.jar"
        );

        for (String jar : requiredJars) {
            File jarFile = new File(jar);
            if (!jarFile.exists()) {
                System.err.println("Warning: Required JAR not found: " + jar);
            }
        }
    }

    private static void runTestMethods() {
        System.out.println("\n1. Testing workflow execution...");
        runTestMethod("testWorkflowExecution");

        System.out.println("\n2. Testing state management...");
        runTestMethod("testStateManagement");

        System.out.println("\n3. Testing work item lifecycle...");
        runTestMethod("testWorkItemLifecycle");

        System.out.println("\n4. Testing specification handling...");
        runTestMethod("testSpecificationHandling");

        System.out.println("\n5. Testing error handling...");
        runTestMethod("testErrorHandling");
    }

    private static void runTestMethod(String methodName) {
        try {
            System.out.println("   Running: " + methodName);

            // Create test instance
            YawlFunctionalityPreservationTest test = new YawlFunctionalityPreservationTest();

            // Run the test method using reflection
            java.lang.reflect.Method method = YawlFunctionalityPreservationTest.class
                .getMethod(methodName);
            method.invoke(test);

            System.out.println("   PASSED: " + methodName);

        } catch (Exception e) {
            System.out.println("   FAILED: " + methodName);
            System.out.println("   Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}