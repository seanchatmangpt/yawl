/*
 * Copyright 2024 YAWL Foundation
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

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.provider.Arguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.elements.YAWLServiceReference;
import org.yawlfoundation.yawl.graalpy.PythonExecutionEngine;
import org.yawlfoundation.yawl.graalpy.PythonSandboxConfig;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Abstract base class for all YAWL Java-Python validation tests.
 * Provides common setup, teardown, and utility methods for testing GraalPy integration.
 *
 * <p>This class implements the Chicago TDD methodology with real YAWL engine instances.
 * All tests use real integrations without mocks.</p>
 *
 * @see <a href="https://github.com/yawlfoundation/yawl-graalpy">YAWL GraalPy Integration</a>
 * @since 6.0.0
 */
@DisplayName("Validation Test Base")
public abstract class ValidationTestBase {

    protected static final Logger logger = LoggerFactory.getLogger(ValidationTestBase.class);

    protected Context pythonEngine;
    protected PythonExecutionEngine yawlEngine;
    protected TestInfo testInfo;

    // Performance measurement thresholds
    protected static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    protected static final Duration LONG_TIMEOUT = Duration.ofMinutes(5);

    // Test data constants
    protected static final String[] SIMPLE_TYPES = {"string", "integer", "boolean", "double", "list", "map"};
    protected static final String[] COMPLEX_TYPES = {"yawl_workflow", "yawl_case", "yawl_task", "yawl_data"};

    /**
     * Sets up the test environment before each test.
     * Initializes GraalPy engine with YAWL integration.
     *
     * @param testInfo the current test information
     * @throws Exception if initialization fails
     */
    @BeforeEach
    void setUp(TestInfo testInfo) throws Exception {
        this.testInfo = testInfo;
        logger.info("Setting up test: {}", testInfo.getDisplayName());

        // Create Python engine with sandbox configuration
        pythonEngine = createPythonEngine();

        // Initialize YAWL GraalPy engine
        yawlEngine = new PythonExecutionEngine(pythonEngine);
        yawlEngine.initialize();

        logger.debug("Test setup completed for: {}", testInfo.getDisplayName());
    }

    /**
     * Tears down the test environment after each test.
     * Cleans up resources and ensures proper shutdown.
     */
    @AfterEach
    void tearDown() {
        logger.info("Tearing down test: {}", testInfo.getDisplayName());

        try {
            if (yawlEngine != null) {
                yawlEngine.shutdown();
                yawlEngine = null;
            }

            if (pythonEngine != null) {
                pythonEngine.close();
                pythonEngine = null;
            }

            logger.debug("Test teardown completed for: {}", testInfo.getDisplayName());
        } catch (Exception e) {
            logger.error("Error during teardown for test {}: {}",
                testInfo.getDisplayName(), e.getMessage(), e);
        }
    }

    /**
     * Creates and configures a new GraalPy engine instance with sandbox settings.
     *
     * @return configured Context instance
     */
    protected Context createPythonEngine() {
        return Context.newBuilder("python")
            .allowExperimentalOptions(true)
            .option("python.Executable", "python3")
            .option("python.ForceImportSite", "true")
            .option("python.Home", System.getProperty("python.home", "/usr/bin"))
            .option("python.Platform", "linux")
            .option("python.PythonVersion", "3.10")
            .option("sandbox.enabled", "true")
            .option("sandbox.allowFileAccess", "false")
            .option("sandbox.allowNetworkAccess", "false")
            .option("sandbox.allowNativeAccess", "false")
            .option("sandbox.allowHostClassLoading", "false")
            .option("sandbox.allowCreateThread", "false")
            .option("sandbox.allowCreateProcess", "false")
            .build();
    }

    /**
     * Executes Python code in the GraalPy engine and returns the result.
     *
     * @param pythonCode the Python code to execute
     * @return the result as a Polyglot Value
     * @throws PolyglotException if execution fails
     */
    protected Value executePython(String pythonCode) throws PolyglotException {
        return pythonEngine.eval(Source.create("python", pythonCode));
    }

    /**
     * Executes Python code from a file resource.
     *
     * @param resourceName the path to the Python resource file
     * @return the result as a Polyglot Value
     * @throws IOException if resource cannot be loaded
     * @throws PolyglotException if execution fails
     */
    protected Value executePythonResource(String resourceName) throws IOException, PolyglotException {
        String pythonCode = loadTestResource(resourceName);
        return executePython(pythonCode);
    }

    /**
     * Asserts that Java and Python objects are equivalent for testing purposes.
     * Performs deep comparison considering the differences between Java and Python data types.
     *
     * @param javaObject the Java object to compare
     * @param pythonValue the Python value to compare
     * @throws AssertionError if objects are not equivalent
     */
    protected void assertEquivalent(Object javaObject, Value pythonValue) throws AssertionError {
        if (javaObject == null && pythonValue.isNull()) {
            return;
        }

        if (javaObject == null || pythonValue.isNull()) {
            throw new AssertionError(String.format(
                "Java-Python mismatch: java=%s, python=%s",
                javaObject, pythonValue)
            );
        }

        // Convert Python value to Java object for comparison
        Object pythonObject = pythonValue.as(Object.class);

        if (!objectsEquivalent(javaObject, pythonObject)) {
            throw new AssertionError(String.format(
                "Java-Python equivalence failed:\n  Java: %s (%s)\n  Python: %s (%s)",
                javaObject, javaObject.getClass().getSimpleName(),
                pythonObject, getPythonTypeName(pythonValue)
            ));
        }
    }

    /**
     * Performs deep comparison between Java and Python objects.
     *
     * @param java the Java object
     * @param python the Python object (converted from Value)
     * @return true if equivalent, false otherwise
     */
    private boolean objectsEquivalent(Object java, Object python) {
        if (java == null && python == null) return true;
        if (java == null || python == null) return false;

        // Handle primitives and their boxed equivalents
        if (java instanceof Number && python instanceof Number) {
            return ((Number) java).doubleValue() == ((Number) python).doubleValue();
        }

        if (java instanceof Boolean && python instanceof Boolean) {
            return java.equals(python);
        }

        if (java instanceof String && python instanceof String) {
            return java.equals(python);
        }

        // Handle collections
        if (java instanceof Collection && python instanceof Collection) {
            return collectionsEquivalent((Collection<?>) java, (Collection<?>) python);
        }

        // Handle maps
        if (java instanceof Map && python instanceof Map) {
            return mapsEquivalent((Map<?, ?>) java, (Map<?, ?>) python);
        }

        // Handle arrays
        if (java.getClass().isArray() && python.getClass().isArray()) {
            return arraysEquivalent(java, python);
        }

        // Default object equality
        return java.equals(python);
    }

    /**
     * Compares two collections for equivalence.
     */
    private boolean collectionsEquivalent(Collection<?> java, Collection<?> python) {
        if (java.size() != python.size()) {
            return false;
        }

        List<Object> pythonList = new ArrayList<>(python);
        for (int i = 0; i < java.size(); i++) {
            if (!objectsEquivalent(java.toArray()[i], pythonList.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compares two maps for equivalence.
     */
    private boolean mapsEquivalent(Map<?, ?> java, Map<?, ?> python) {
        if (java.size() != python.size()) {
            return false;
        }

        for (Map.Entry<?, ?> entry : java.entrySet()) {
            if (!python.containsKey(entry.getKey())) {
                return false;
            }
            if (!objectsEquivalent(entry.getValue(), python.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compares two arrays for equivalence.
     */
    private boolean arraysEquivalent(Object java, Object python) {
        int length = java.getClass().isArray() ? javaArrayLength(java) : 0;
        int pythonLength = python.getClass().isArray() ? javaArrayLength(python) : 0;

        if (length != pythonLength) {
            return false;
        }

        for (int i = 0; i < length; i++) {
            if (!objectsEquivalent(getJavaArrayElement(java, i), getJavaArrayElement(python, i))) {
                return false;
            }
        }
        return true;
    }

    private int javaArrayLength(Object array) {
        return java.lang.reflect.Array.getLength(array);
    }

    private Object getJavaArrayElement(Object array, int index) {
        return java.lang.reflect.Array.get(array, index);
    }

    /**
     * Gets the Python type name from a Polyglot Value.
     */
    private String getPythonTypeName(Value value) {
        try {
            return value.getMetaQualifiedName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Measures execution time of a task.
     *
     * @param task the task to measure
     * @return PerformanceResult with timing information
     */
    protected PerformanceResult measurePerformance(Runnable task) {
        return measurePerformance(task, DEFAULT_TIMEOUT);
    }

    /**
     * Measures execution time of a task with custom timeout.
     *
     * @param task the task to measure
     * @param timeout the maximum allowed duration
     * @return PerformanceResult with timing information
     */
    protected PerformanceResult measurePerformance(Runnable task, Duration timeout) {
        Instant start = Instant.now();

        try {
            task.run();
            Duration duration = Duration.between(start, Instant.now());

            if (duration.toMillis() > timeout.toMillis()) {
                logger.warn("Task exceeded timeout: {} > {}", duration, timeout);
            }

            return new PerformanceResult(duration, true);
        } catch (Exception e) {
            Duration duration = Duration.between(start, Instant.now());
            logger.error("Task failed after {}: {}", duration, e.getMessage());
            return new PerformanceResult(duration, false, e);
        }
    }

    /**
     * Generates test data of the specified type.
     *
     * @param type the type of data to generate
     * @param count the number of items to generate
     * @return generated test data
     */
    @SuppressWarnings("unchecked")
    protected <T> List<T> generateTestData(String type, int count) {
        return switch (type.toLowerCase()) {
            case "string" -> (List<T>) generateStrings(count);
            case "integer" -> (List<T>) generateIntegers(count);
            case "boolean" -> (List<T>) generateBooleans(count);
            case "double" -> (List<T>) generateDoubles(count);
            case "list" -> (List<T>) generateLists(count);
            case "map" -> (List<T>) generateMaps(count);
            case "yawl_workflow" -> (List<T>) generateYAWLWorkflows(count);
            case "yawl_case" -> (List<T>) generateYAWLCases(count);
            case "yawl_task" -> (List<T>) generateYAWLTasks(count);
            case "yawl_data" -> (List<T>) generateYAWLData(count);
            default -> throw new IllegalArgumentException("Unknown data type: " + type);
        };
    }

    /**
     * Loads a test resource file as a string.
     *
     * @param name the resource file name
     * @return the file content as a string
     * @throws IOException if resource cannot be loaded
     */
    protected String loadTestResource(String name) throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(name)) {
            if (stream == null) {
                throw new IOException("Resource not found: " + name);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Loads a test resource file as bytes.
     *
     * @param name the resource file name
     * @return the file content as bytes
     * @throws IOException if resource cannot be loaded
     */
    protected byte[] loadTestResourceBytes(String name) throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(name)) {
            if (stream == null) {
                throw new IOException("Resource not found: " + name);
            }
            return stream.readAllBytes();
        }
    }

    // Private data generation methods

    private List<String> generateStrings(int count) {
        return Stream.generate(() -> "test-string-" + UUID.randomUUID())
            .limit(count)
            .toList();
    }

    private List<Integer> generateIntegers(int count) {
        Random random = new Random();
        return random.ints(count, 0, 1000)
            .boxed()
            .toList();
    }

    private List<Boolean> generateBooleans(int count) {
        Random random = new Random();
        return random.ints(count, 0, 2)
            .mapToObj(i -> i == 1)
            .toList();
    }

    private List<Double> generateDoubles(int count) {
        Random random = new Random();
        return random.doubles(count, 0.0, 100.0)
            .boxed()
            .toList();
    }

    private List<List<Object>> generateLists(int count) {
        return Stream.generate(() ->
            Arrays.asList("item1", 42, true, 3.14)
        ).limit(count).toList();
    }

    private List<Map<String, Object>> generateMaps(int count) {
        return Stream.generate(() -> {
            Map<String, Object> map = new HashMap<>();
            map.put("name", "test-" + UUID.randomUUID());
            map.put("value", Math.random() * 100);
            map.put("active", true);
            return map;
        }).limit(count).toList();
    }

    private List<Map<String, Object>> generateYAWLWorkflows(int count) {
        return Stream.generate(() -> {
            Map<String, Object> workflow = new HashMap<>();
            workflow.put("id", UUID.randomUUID().toString());
            workflow.put("name", "test-workflow-" + UUID.randomUUID());
            workflow.put("uri", "http://localhost:8080/test-workflow");
            workflow.put("version", "1.0");
            workflow.put("status", "active");
            return workflow;
        }).limit(count).toList();
    }

    private List<Map<String, Object>> generateYAWLCases(int count) {
        return Stream.generate(() -> {
            Map<String, Object> caseObj = new HashMap<>();
            caseObj.put("id", UUID.randomUUID().toString());
            caseObj.put("name", "test-case-" + UUID.randomUUID());
            caseObj.put("uri", "http://localhost:8080/test-case");
            caseObj.put("status", "open");
            caseObj.put("created", System.currentTimeMillis());
            return caseObj;
        }).limit(count).toList();
    }

    private List<Map<String, Object>> generateYAWLTasks(int count) {
        return Stream.generate(() -> {
            Map<String, Object> task = new HashMap<>();
            task.put("id", UUID.randomUUID().toString());
            task.put("name", "test-task-" + UUID.randomUUID());
            task.put("uri", "http://localhost:8080/test-task");
            task.put("state", "pending");
            task.put("priority", "normal");
            return task;
        }).limit(count).toList();
    }

    private List<Map<String, Object>> generateYAWLData(int count) {
        return Stream.generate(() -> {
            Map<String, Object> data = new HashMap<>();
            data.put("id", UUID.randomUUID().toString());
            data.put("type", "yawl-data");
            data.put("timestamp", System.currentTimeMillis());
            data.put("metadata", Map.of(
                "version", "1.0",
                "source", "test-generator"
            ));
            return data;
        }).limit(count).toList();
    }

    // Nested classes

    /**
     * Performance measurement result.
     */
    public static class PerformanceResult {
        private final Duration duration;
        private final boolean success;
        private final Exception exception;

        public PerformanceResult(Duration duration, boolean success) {
            this(duration, success, null);
        }

        public PerformanceResult(Duration duration, boolean success, Exception exception) {
            this.duration = duration;
            this.success = success;
            this.exception = exception;
        }

        public Duration getDuration() {
            return duration;
        }

        public boolean isSuccess() {
            return success;
        }

        public Exception getException() {
            return exception;
        }

        public long getDurationMillis() {
            return duration.toMillis();
        }

        @Override
        public String toString() {
            return String.format("PerformanceResult{duration=%s, success=%s, exception=%s}",
                duration, success, exception);
        }
    }

    /**
     * Test data argument provider for parameterized tests.
     */
    public static class TestDataArguments {
        public static Arguments simpleType(String type) {
            return Arguments.of(type, 5);
        }

        public static Arguments complexType(String type) {
            return Arguments.of(type, 3);
        }

        public static Arguments[] allTypes() {
            List<Arguments> arguments = new ArrayList<>();

            for (String type : SIMPLE_TYPES) {
                arguments.add(simpleType(type));
            }

            for (String type : COMPLEX_TYPES) {
                arguments.add(complexType(type));
            }

            return arguments.toArray(new Arguments[0]);
        }
    }
}