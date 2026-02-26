/*
 * Copyright (c) 2024-2025 YAWL Foundation
 *
 * This file is part of YAWL v6.0.0-GA.
 *
 * YAWL v6.0.0-GA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * YAWL v6.0.0-GA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL v6.0.0-GA. If not, see <http://www.gnu.org/licenses/>.
 */
package org.yawlfoundation.yawl.integration.java_python;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.yawlfoundation.yawl.test.YawlTestBase;
import org.yawlfoundation.yawl.graalpy.PythonExecutionEngine;
import org.yawlfoundation.yawl.graalpy.PythonExecutionEngine.Builder;
import org.yawlfoundation.yawl.graalpy.PythonVirtualEnvironment;
import org.yawlfoundation.yawl.graalpy.PythonVirtualEnvironmentException;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Base class for all Java-Python validation tests.
 * Provides common utilities and setup for Java-Python interoperability testing.
 *
 * @author YAWL Foundation
 * @since v6.0.0-GA
 */
public abstract class ValidationTestBase extends YawlTestBase {

    protected static PythonExecutionEngine pythonEngine;
    protected static PythonVirtualEnvironment pythonEnvironment;
    protected static boolean graalpyAvailable;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        if (pythonEngine == null) {
            initializePythonEnvironment();
        }
    }

    /**
     * Initialize Python environment for testing
     */
    private void initializePythonEnvironment() throws PythonVirtualEnvironmentException {
        try {
            // Check if GraalPy is available
            graalpyAvailable = checkGraalPyAvailability();

            if (graalpyAvailable) {
                // Create Python execution engine using builder pattern
                pythonEngine = PythonExecutionEngine.builder()
                    .contextPoolSize(2)
                    .build();

                // Create virtual environment for testing
                pythonEnvironment = PythonVirtualEnvironment.create(
                    projectRoot.resolve("test").resolve("python-venv"),
                    "graalpy"
                );
                pythonEnvironment.initialize();

                logger.info("Python environment initialized successfully for validation");
            } else {
                logger.warn("GraalPy not available - some tests will be skipped");
            }
        } catch (Exception e) {
            logger.error("Failed to initialize Python environment: " + e.getMessage(), e);
            graalpyAvailable = false;
        }
    }

    /**
     * Check if GraalPy is available in the environment
     */
    protected boolean checkGraalPyAvailability() {
        try {
            Class.forName("org.graalvm.python.Python");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Execute Python code and return the result
     */
    protected Object executePythonCode(String code) throws Exception {
        if (!graalpyAvailable) {
            throw new IllegalStateException("GraalPy not available");
        }

        return pythonEngine.eval(code);
    }

    /**
     * Execute Python code in virtual environment
     */
    protected Object executePythonInVirtualEnvironment(String code) throws Exception {
        if (!graalpyAvailable) {
            throw new IllegalStateException("GraalPy not available");
        }

        return pythonEnvironment.executePython(code);
    }

    /**
     * Compare Java and Python objects for equivalence
     */
    protected boolean areEquivalent(Object javaObject, Object pythonObject) {
        // Handle null cases
        if (javaObject == null && pythonObject == null) {
            return true;
        }
        if (javaObject == null || pythonObject == null) {
            return false;
        }

        // Handle numeric types
        if (javaObject instanceof Number && pythonObject instanceof Number) {
            return ((Number) javaObject).doubleValue() == ((Number) pythonObject).doubleValue();
        }

        // Handle string types
        if (javaObject instanceof String && pythonObject instanceof String) {
            return javaObject.equals(pythonObject);
        }

        // Handle boolean types
        if (javaObject instanceof Boolean && pythonObject instanceof Boolean) {
            return javaObject.equals(pythonObject);
        }

        // Default object comparison
        return javaObject.equals(pythonObject);
    }

    /**
     * Benchmark Python code execution
     */
    protected long benchmarkExecution(Runnable task, int iterations) {
        long totalTime = 0;

        for (int i = 0; i < iterations; i++) {
            long startTime = System.nanoTime();
            task.run();
            long endTime = System.nanoTime();
            totalTime += (endTime - startTime);
        }

        return TimeUnit.MILLISECONDS.convert(totalTime / iterations, TimeUnit.NANOSECONDS);
    }

    /**
     * Clean up resources
     */
    @AfterAll
    public static void tearDown() throws Exception {
        if (pythonEnvironment != null) {
            pythonEnvironment.shutdown();
        }
        if (pythonEngine != null) {
            pythonEngine.shutdown();
        }
    }
}