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

package org.yawlfoundation.yawl.dspy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.yawlfoundation.yawl.graalpy.PythonExecutionEngine;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Unit tests for PythonDspyBridge.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Basic execution of DSPy programs with hardcoded results</li>
 *   <li>Compilation caching (same program cached, different program not)</li>
 *   <li>Result marshalling (Python dict to Java Map)</li>
 *   <li>Exception handling (invalid programs)</li>
 *   <li>Context pooling and reuse</li>
 * </ul>
 * </p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@DisplayName("PythonDspyBridge Tests")
public class PythonDspyBridgeTest {

    private PythonDspyBridge bridge;
    private PythonExecutionEngine engine;

    @BeforeEach
    void setUp() {
        // Use permissive sandbox for testing; no restrictions
        engine = PythonExecutionEngine.builder()
                .contextPoolSize(2)
                .sandboxed(false)
                .build();
        bridge = new PythonDspyBridge(engine);
    }

    @Test
    @DisplayName("Should execute hardcoded DSPy program successfully")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testExecuteHardcodedDspyProgram() {
        // Arrange: Simple DSPy program that returns hardcoded dict
        String programSource = """
            import dspy
            class SentimentAnalyzer(dspy.Module):
                def __init__(self):
                    self.predictor = dspy.ChainOfThought("text -> sentiment")
                def forward(self, text):
                    # Return hardcoded result (normally would call LLM)
                    return {"sentiment": "positive", "confidence": 0.95}
            """;

        DspyProgram program = DspyProgram.builder()
                .name("sentiment-analyzer")
                .source(programSource)
                .description("Sentiment analyzer for testing")
                .build();

        Map<String, Object> inputs = Map.of("text", "YAWL is fantastic!");

        // Act
        DspyExecutionResult result = bridge.execute(program, inputs);

        // Assert
        assertThat(result, notNullValue());
        assertThat(result.output(), notNullValue());
        assertThat(result.output().size(), greaterThan(0));
        assertThat(result.metrics(), notNullValue());
        assertThat(result.metrics().executionTimeMs(), greaterThanOrEqualTo(0L));
    }

    @Test
    @DisplayName("Should cache compiled programs by name + source hash")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testCompilationCaching() {
        // Arrange: Same program executed twice
        String programSource = """
            import dspy
            class CachedModule(dspy.Module):
                def __init__(self):
                    pass
                def forward(self, x):
                    return {"cached": True}
            """;

        DspyProgram program1 = DspyProgram.builder()
                .name("cached-test")
                .source(programSource)
                .build();

        Map<String, Object> inputs = Map.of("x", 42);

        // Act: First execution
        DspyExecutionResult result1 = bridge.execute(program1, inputs);
        boolean firstCacheHit = result1.metrics().cacheHit();
        long firstCompilationTime = result1.metrics().compilationTimeMs();

        // Second execution (same program)
        DspyExecutionResult result2 = bridge.execute(program1, inputs);
        boolean secondCacheHit = result2.metrics().cacheHit();
        long secondCompilationTime = result2.metrics().compilationTimeMs();

        // Assert
        assertThat(firstCacheHit, is(false));  // First execution is cache miss
        assertThat(secondCacheHit, is(true));   // Second execution is cache hit
        assertThat(secondCompilationTime, lessThanOrEqualTo(firstCompilationTime));
    }

    @Test
    @DisplayName("Should not reuse cache for programs with same name but different source")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testCacheKeyDifferentiatesSource() {
        // Arrange: Two programs with same name but different source
        String source1 = """
            import dspy
            class SameNameModule(dspy.Module):
                def forward(self, x):
                    return {"version": 1}
            """;

        String source2 = """
            import dspy
            class SameNameModule(dspy.Module):
                def forward(self, x):
                    return {"version": 2}
            """;

        DspyProgram program1 = DspyProgram.builder()
                .name("same-name")
                .source(source1)
                .build();

        DspyProgram program2 = DspyProgram.builder()
                .name("same-name")
                .source(source2)
                .build();

        Map<String, Object> inputs = Map.of("x", 1);

        // Act
        DspyExecutionResult result1 = bridge.execute(program1, inputs);
        DspyExecutionResult result2 = bridge.execute(program2, inputs);

        // Assert: Different source hashes mean different cache entries
        assertThat(program1.sourceHash(), not(equalTo(program2.sourceHash())));
        assertThat(result1.metrics().cacheHit(), is(false));
        assertThat(result2.metrics().cacheHit(), is(false));  // Different program
    }

    @Test
    @DisplayName("Should marshal Python dict results to Java Map")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testResultMarshalling() {
        // Arrange: Program that returns nested dict
        String programSource = """
            import dspy
            class NestedDictModule(dspy.Module):
                def forward(self, x):
                    return {
                        "level1": {
                            "level2": {"value": 123},
                            "list": [1, 2, 3]
                        },
                        "string": "test"
                    }
            """;

        DspyProgram program = DspyProgram.builder()
                .name("nested-dict")
                .source(programSource)
                .build();

        // Act
        DspyExecutionResult result = bridge.execute(program, Map.of("x", 0));

        // Assert
        assertThat(result.output(), notNullValue());
        assertThat(result.output().get("string"), equalTo("test"));
        assertThat(result.output().get("level1"), instanceOf(Map.class));
    }

    @Test
    @DisplayName("Should estimate tokens in inputs and outputs")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testTokenEstimation() {
        // Arrange
        String programSource = """
            import dspy
            class TokenCounterModule(dspy.Module):
                def forward(self, text):
                    return {"output": text + " processed"}
            """;

        DspyProgram program = DspyProgram.builder()
                .name("token-counter")
                .source(programSource)
                .build();

        String inputText = "A".repeat(100);  // 100 characters
        Map<String, Object> inputs = Map.of("text", inputText);

        // Act
        DspyExecutionResult result = bridge.execute(program, inputs);

        // Assert: tokens should be estimated (roughly length / 4)
        long totalTokens = result.metrics().totalTokens();
        assertThat(totalTokens, greaterThan(0L));
    }

    @Test
    @DisplayName("Should report cache hit status in metrics")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testCacheHitMetrics() {
        // Arrange
        String programSource = """
            import dspy
            class CacheMetricsModule(dspy.Module):
                def forward(self, x):
                    return {"result": x}
            """;

        DspyProgram program = DspyProgram.builder()
                .name("cache-metrics")
                .source(programSource)
                .build();

        Map<String, Object> inputs = Map.of("x", 42);

        // Act: First execution
        DspyExecutionResult result1 = bridge.execute(program, inputs);
        // Second execution
        DspyExecutionResult result2 = bridge.execute(program, inputs);

        // Assert
        assertThat(result1.metrics().cacheHit(), is(false));
        assertThat(result2.metrics().cacheHit(), is(true));
        assertThat(result1.metrics().contextReused(), is(true));  // Always reused from pool
        assertThat(result2.metrics().contextReused(), is(true));
    }

    @Test
    @DisplayName("Should report timestamp in metrics")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testMetricsTimestamp() {
        // Arrange
        String programSource = """
            import dspy
            class TimestampModule(dspy.Module):
                def forward(self, x):
                    return {"timestamp_test": True}
            """;

        DspyProgram program = DspyProgram.builder()
                .name("timestamp-test")
                .source(programSource)
                .build();

        // Act
        DspyExecutionResult result = bridge.execute(program, Map.of("x", 1));

        // Assert
        assertThat(result.metrics().timestamp(), notNullValue());
    }

    @Test
    @DisplayName("Should support concurrent execution (context pooling)")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testConcurrentExecution() throws InterruptedException {
        // Arrange
        String programSource = """
            import dspy
            class ConcurrentModule(dspy.Module):
                def forward(self, id):
                    return {"id": id}
            """;

        DspyProgram program = DspyProgram.builder()
                .name("concurrent")
                .source(programSource)
                .build();

        // Act: Execute from 3 concurrent threads
        Thread[] threads = new Thread[3];
        DspyExecutionResult[] results = new DspyExecutionResult[3];

        for (int i = 0; i < 3; i++) {
            final int taskId = i;
            threads[i] = new Thread(() -> {
                results[taskId] = bridge.execute(program, Map.of("id", taskId));
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // Assert: All executions succeeded
        for (DspyExecutionResult result : results) {
            assertThat(result, notNullValue());
            assertThat(result.output(), notNullValue());
        }
    }

    @Test
    @DisplayName("Should return cache statistics")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testCacheStats() {
        // Arrange
        String programSource = """
            import dspy
            class StatsModule(dspy.Module):
                def forward(self, x):
                    return {"x": x}
            """;

        DspyProgram program = DspyProgram.builder()
                .name("stats-test")
                .source(programSource)
                .build();

        // Act: Execute once to populate cache
        bridge.execute(program, Map.of("x", 1));
        Map<String, Object> stats = bridge.getCacheStats();

        // Assert
        assertThat(stats, notNullValue());
        assertThat(stats.get("cacheSize"), notNullValue());
        assertThat(stats.get("cacheMaxSize"), notNullValue());
    }

    @Test
    @DisplayName("Should clear cache successfully")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testClearCache() {
        // Arrange
        String programSource = """
            import dspy
            class ClearCacheModule(dspy.Module):
                def forward(self, x):
                    return {"x": x}
            """;

        DspyProgram program = DspyProgram.builder()
                .name("clear-cache-test")
                .source(programSource)
                .build();

        // Act: Execute to populate cache
        bridge.execute(program, Map.of("x", 1));
        Map<String, Object> statsBefore = bridge.getCacheStats();

        bridge.clearCache();
        Map<String, Object> statsAfter = bridge.getCacheStats();

        // Act: Execute again (should be cache miss since cleared)
        DspyExecutionResult result = bridge.execute(program, Map.of("x", 1));

        // Assert
        int cacheSizeBefore = ((Number) statsBefore.get("cacheSize")).intValue();
        int cacheSizeAfter = ((Number) statsAfter.get("cacheSize")).intValue();
        assertThat(cacheSizeBefore, greaterThan(0));
        assertThat(cacheSizeAfter, equalTo(0));
        assertThat(result.metrics().cacheHit(), is(false));  // Not in cache
    }

    @Test
    @DisplayName("Should reject null program")
    void testNullProgramRejection() {
        // Act & Assert
        try {
            bridge.execute(null, Map.of("x", 1));
            throw new AssertionError("Expected NullPointerException");
        } catch (NullPointerException e) {
            assertThat(e.getMessage(), containsString("DspyProgram"));
        }
    }

    @Test
    @DisplayName("Should reject null inputs map")
    void testNullInputsRejection() {
        // Arrange
        DspyProgram program = DspyProgram.builder()
                .name("test")
                .source("import dspy\nclass T(dspy.Module):\n  def forward(self, x): return {}")
                .build();

        // Act & Assert
        try {
            bridge.execute(program, null);
            throw new AssertionError("Expected NullPointerException");
        } catch (NullPointerException e) {
            assertThat(e.getMessage(), containsString("Inputs"));
        }
    }

    @Test
    @DisplayName("Should reject null PythonExecutionEngine in constructor")
    void testNullEngineRejection() {
        // Act & Assert
        try {
            new PythonDspyBridge(null);
            throw new AssertionError("Expected NullPointerException");
        } catch (NullPointerException e) {
            assertThat(e.getMessage(), containsString("PythonExecutionEngine"));
        }
    }

    @Test
    @DisplayName("Should return empty map if no module class found")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testInvalidSourceHandling() {
        // Arrange: Source without dspy.Module class
        String invalidSource = """
            # Just a comment, no module class
            x = 42
            """;

        DspyProgram program = DspyProgram.builder()
                .name("invalid")
                .source(invalidSource)
                .build();

        // Act & Assert: Should throw PythonException
        try {
            bridge.execute(program, Map.of("x", 1));
            throw new AssertionError("Expected PythonException");
        } catch (Exception e) {
            // Verify it's some form of Python/execution error
            assertThat(e.getClass().getSimpleName(), containsString("Exception"));
        }
    }

    @Test
    @DisplayName("Should handle multiple programs concurrently with proper isolation")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testMultipleProgramsConcurrent() throws InterruptedException {
        // Arrange: Two different programs
        String source1 = """
            import dspy
            class Program1(dspy.Module):
                def forward(self, x):
                    return {"program": 1, "x": x}
            """;

        String source2 = """
            import dspy
            class Program2(dspy.Module):
                def forward(self, y):
                    return {"program": 2, "y": y}
            """;

        DspyProgram prog1 = DspyProgram.builder().name("prog1").source(source1).build();
        DspyProgram prog2 = DspyProgram.builder().name("prog2").source(source2).build();

        // Act: Execute both concurrently
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 3; i++) {
                bridge.execute(prog1, Map.of("x", i));
            }
        });

        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 3; i++) {
                bridge.execute(prog2, Map.of("y", i));
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        // Assert: Both completed without error (verified by no exception thrown)
        Map<String, Object> stats = bridge.getCacheStats();
        int cacheSize = ((Number) stats.get("cacheSize")).intValue();
        assertThat(cacheSize, equalTo(2));  // Two distinct programs
    }

    @Test
    @DisplayName("Should compute correct total time and token metrics")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testMetricsCalculations() {
        // Arrange
        String programSource = """
            import dspy
            class MetricsModule(dspy.Module):
                def forward(self, text):
                    return {"output": text}
            """;

        DspyProgram program = DspyProgram.builder()
                .name("metrics-calc")
                .source(programSource)
                .build();

        // Act
        DspyExecutionResult result = bridge.execute(program, Map.of("text", "test"));

        // Assert
        long compilationTime = result.metrics().compilationTimeMs();
        long executionTime = result.metrics().executionTimeMs();
        long totalTime = result.metrics().totalTimeMs();
        long inputTokens = result.metrics().inputTokens();
        long outputTokens = result.metrics().outputTokens();
        long totalTokens = result.metrics().totalTokens();

        assertThat(totalTime, equalTo(compilationTime + executionTime));
        assertThat(totalTokens, equalTo(inputTokens + outputTokens));
        assertThat(compilationTime, greaterThanOrEqualTo(0L));
        assertThat(executionTime, greaterThanOrEqualTo(0L));
    }
}
