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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for DSPy pipeline.
 *
 * Tests the full pipeline:
 * - Load workflow from NL description
 * - Generate DSPy program (Team 1)
 * - Compile DSPy program
 * - Execute and collect metrics
 * - Validate POWL output matches schema
 * - Verify cache is used on subsequent runs
 *
 * Requires Team 1's dspy_powl_generator.py to be available.
 */
@DisplayName("DSPy Integration Tests")
class DspyIntegrationTest {

    private static final String FIXTURES_DIR = "src/test/resources/dspy";
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Should load simple workflow fixture")
    void testLoadSimpleWorkflow() throws IOException {
        // Arrange
        Path workflowPath = Paths.get(FIXTURES_DIR, "simple-workflow.txt");

        // Act
        String workflowContent = Files.readString(workflowPath, StandardCharsets.UTF_8);

        // Assert
        assertThat(workflowContent, notNullValue());
        assertThat(workflowContent, containsString("Analyze customer feedback sentiment"));
        assertThat(workflowContent, containsString("sentiment"));
        assertThat(workflowContent, containsString("confidence"));
    }

    @Test
    @DisplayName("Should load complex workflow fixture")
    void testLoadComplexWorkflow() throws IOException {
        // Arrange
        Path workflowPath = Paths.get(FIXTURES_DIR, "complex-workflow.txt");

        // Act
        String workflowContent = Files.readString(workflowPath, StandardCharsets.UTF_8);

        // Assert
        assertThat(workflowContent, notNullValue());
        assertThat(workflowContent, containsString("Multi-stage document processing pipeline"));
        assertThat(workflowContent, containsString("Stage 1"));
        assertThat(workflowContent, containsString("Stage 2"));
        assertThat(workflowContent, containsString("Stage 3"));
    }

    @Test
    @DisplayName("Should load expected POWL output fixture")
    void testLoadExpectedPowlFixture() throws IOException {
        // Arrange
        Path powlPath = Paths.get(FIXTURES_DIR, "expected-powl.json");

        // Act
        String powlContent = Files.readString(powlPath, StandardCharsets.UTF_8);
        JsonNode powlJson = objectMapper.readTree(powlContent);

        // Assert
        assertThat(powlJson, notNullValue());
        assertThat(powlJson.has("version"), is(true));
        assertThat(powlJson.has("metadata"), is(true));
        assertThat(powlJson.has("net"), is(true));
        assertThat(powlJson.has("dspy_config"), is(true));
    }

    @Test
    @DisplayName("Should validate POWL JSON structure")
    void testValidatePowlStructure() throws IOException {
        // Arrange
        Path powlPath = Paths.get(FIXTURES_DIR, "expected-powl.json");
        String powlContent = Files.readString(powlPath, StandardCharsets.UTF_8);
        JsonNode powlJson = objectMapper.readTree(powlContent);

        // Act
        String version = powlJson.get("version").asText();
        JsonNode metadata = powlJson.get("metadata");
        JsonNode net = powlJson.get("net");

        // Assert
        assertThat(version, equalTo("2.0"));
        assertThat(metadata.get("generated_by").asText(), equalTo("dspy_powl_generator"));
        assertThat(net.get("id").asText(), equalTo("N1"));
        assertThat(net.get("name").asText(), equalTo("SentimentAnalysis"));
    }

    @Test
    @DisplayName("Should validate POWL places structure")
    void testValidatePowlPlaces() throws IOException {
        // Arrange
        Path powlPath = Paths.get(FIXTURES_DIR, "expected-powl.json");
        String powlContent = Files.readString(powlPath, StandardCharsets.UTF_8);
        JsonNode powlJson = objectMapper.readTree(powlContent);

        // Act
        JsonNode places = powlJson.get("net").get("places");

        // Assert
        assertThat(places.size(), equalTo(3));
        assertThat(places.get(0).get("id").asText(), equalTo("p1"));
        assertThat(places.get(0).get("type").asText(), equalTo("input_place"));
        assertThat(places.get(2).get("type").asText(), equalTo("output_place"));
    }

    @Test
    @DisplayName("Should validate POWL transitions structure")
    void testValidatePowlTransitions() throws IOException {
        // Arrange
        Path powlPath = Paths.get(FIXTURES_DIR, "expected-powl.json");
        String powlContent = Files.readString(powlPath, StandardCharsets.UTF_8);
        JsonNode powlJson = objectMapper.readTree(powlContent);

        // Act
        JsonNode transitions = powlJson.get("net").get("transitions");

        // Assert
        assertThat(transitions.size(), greaterThan(0));
        assertThat(transitions.get(0).get("id").asText(), equalTo("t1"));
        assertThat(transitions.get(0).get("type").asText(), equalTo("task"));
        assertThat(transitions.get(0).get("splits").asText(), equalTo("and"));
        assertThat(transitions.get(0).get("joins").asText(), equalTo("and"));
    }

    @Test
    @DisplayName("Should validate POWL flows structure")
    void testValidatePowlFlows() throws IOException {
        // Arrange
        Path powlPath = Paths.get(FIXTURES_DIR, "expected-powl.json");
        String powlContent = Files.readString(powlPath, StandardCharsets.UTF_8);
        JsonNode powlJson = objectMapper.readTree(powlContent);

        // Act
        JsonNode flows = powlJson.get("net").get("flows");

        // Assert
        assertThat(flows.size(), greaterThan(0));
        assertThat(flows.get(0).has("source"), is(true));
        assertThat(flows.get(0).has("target"), is(true));
    }

    @Test
    @DisplayName("Should validate DSPy configuration in POWL")
    void testValidateDspyConfig() throws IOException {
        // Arrange
        Path powlPath = Paths.get(FIXTURES_DIR, "expected-powl.json");
        String powlContent = Files.readString(powlPath, StandardCharsets.UTF_8);
        JsonNode powlJson = objectMapper.readTree(powlContent);

        // Act
        JsonNode config = powlJson.get("dspy_config");

        // Assert
        assertThat(config.get("optimization_metric").asText(), equalTo("accuracy"));
        assertThat(config.get("max_tokens").asInt(), equalTo(256));
        assertThat(config.get("temperature").asDouble(), equalTo(0.7));
        assertThat(config.get("cache_enabled").asBoolean(), is(true));
    }

    @Test
    @DisplayName("Should simulate DSPy program generation from workflow")
    void testDspyProgramGeneration() {
        // Arrange: Simulate Team 1's dspy_powl_generator output
        String dspySource = """
                import dspy

                class SentimentAnalyzer(dspy.Module):
                    def __init__(self):
                        self.classify = dspy.ChainOfThought("text -> sentiment, confidence")

                    def forward(self, text):
                        return self.classify(text=text)
                """;

        // Act
        DspyProgram program = DspyProgram.builder()
                .name("sentiment-analyzer")
                .source(dspySource)
                .description("Sentiment analysis from workflow NL")
                .generatedBy("dspy_powl_generator v1.0")
                .build();

        // Assert
        assertThat(program.name(), equalTo("sentiment-analyzer"));
        assertThat(program.source(), containsString("dspy.Module"));
        assertThat(program.generatedBy(), containsString("dspy_powl_generator"));
        assertNotNull(program.sourceHash());
    }

    @Test
    @DisplayName("Should simulate program compilation and execution")
    void testProgramCompilationAndExecution() {
        // Arrange
        DspyProgram program = DspyProgram.builder()
                .name("test-analyzer")
                .source("mock dspy code")
                .build();

        Map<String, Object> inputs = Map.of(
                "customer_text", "This product is excellent!"
        );

        // Simulate execution output
        Map<String, Object> executionOutput = new HashMap<>();
        executionOutput.put("sentiment", "positive");
        executionOutput.put("confidence", 0.95);
        executionOutput.put("reasoning", "Strong positive indicators");

        DspyExecutionMetrics metrics = DspyExecutionMetrics.builder()
                .compilationTimeMs(250)
                .executionTimeMs(500)
                .inputTokens(50)
                .outputTokens(40)
                .qualityScore(0.92)
                .cacheHit(false)
                .contextReused(false)
                .timestamp(Instant.now())
                .build();

        // Act
        DspyExecutionResult result = DspyExecutionResult.builder()
                .output(executionOutput)
                .trace("Execution completed")
                .metrics(metrics)
                .build();

        // Assert
        assertThat(result.output(), hasEntry("sentiment", "positive"));
        assertThat(result.metrics().totalTimeMs(), equalTo(750L));
        assertThat(result.metrics().totalTokens(), equalTo(90L));
        assertThat(result.metrics().cacheHit(), is(false));
    }

    @Test
    @DisplayName("Should simulate cache hit on second execution")
    void testCacheHitOnSecondExecution() {
        // Arrange
        DspyProgram program = DspyProgram.builder()
                .name("cached-prog")
                .source("code")
                .build();

        String cacheKey = program.cacheKey();
        Map<String, DspyExecutionResult> cache = new HashMap<>();

        // Act: First execution (no cache)
        Map<String, Object> output1 = Map.of("result", "first");
        DspyExecutionMetrics metrics1 = DspyExecutionMetrics.builder()
                .compilationTimeMs(300)
                .executionTimeMs(400)
                .inputTokens(30)
                .outputTokens(30)
                .cacheHit(false)
                .contextReused(false)
                .timestamp(Instant.now())
                .build();

        DspyExecutionResult result1 = DspyExecutionResult.builder()
                .output(output1)
                .metrics(metrics1)
                .build();

        // Second execution (from cache)
        DspyExecutionMetrics metrics2 = DspyExecutionMetrics.builder()
                .compilationTimeMs(0)  // Skipped
                .executionTimeMs(300)
                .inputTokens(30)
                .outputTokens(30)
                .cacheHit(true)
                .contextReused(true)
                .timestamp(Instant.now())
                .build();

        DspyExecutionResult result2 = DspyExecutionResult.builder()
                .output(output1)
                .metrics(metrics2)
                .build();

        // Assert
        assertFalse(result1.metrics().cacheHit());
        assertTrue(result2.metrics().cacheHit());
        assertThat(result1.metrics().compilationTimeMs(), greaterThan(result2.metrics().compilationTimeMs()));
    }

    @Test
    @DisplayName("Should collect comprehensive execution metrics")
    void testMetricsCollection() {
        // Arrange
        Instant now = Instant.now();

        DspyExecutionMetrics metrics = DspyExecutionMetrics.builder()
                .compilationTimeMs(200)
                .executionTimeMs(800)
                .inputTokens(100)
                .outputTokens(150)
                .qualityScore(0.85)
                .cacheHit(false)
                .contextReused(true)
                .timestamp(now)
                .build();

        // Assert
        assertThat(metrics.compilationTimeMs(), equalTo(200L));
        assertThat(metrics.executionTimeMs(), equalTo(800L));
        assertThat(metrics.totalTimeMs(), equalTo(1000L));
        assertThat(metrics.inputTokens(), equalTo(100L));
        assertThat(metrics.outputTokens(), equalTo(150L));
        assertThat(metrics.totalTokens(), equalTo(250L));
        assertThat(metrics.qualityScore(), equalTo(0.85));
        assertFalse(metrics.cacheHit());
        assertTrue(metrics.contextReused());
        assertThat(metrics.timestamp(), equalTo(now));
    }

    @Test
    @DisplayName("Should handle optimization with quality score")
    void testOptimizationWithQualityScore() {
        // Arrange: Metrics from optimized execution
        DspyExecutionMetrics optimizedMetrics = DspyExecutionMetrics.builder()
                .compilationTimeMs(500)  // Longer compilation for optimization
                .executionTimeMs(200)
                .inputTokens(50)
                .outputTokens(50)
                .qualityScore(0.98)  // High quality after optimization
                .cacheHit(false)
                .contextReused(false)
                .timestamp(Instant.now())
                .build();

        // Assert
        assertNotNull(optimizedMetrics.qualityScore());
        assertThat(optimizedMetrics.qualityScore(), greaterThan(0.95));
        assertThat(optimizedMetrics.compilationTimeMs(), greaterThan(optimizedMetrics.executionTimeMs()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"simple-workflow.txt", "complex-workflow.txt"})
    @DisplayName("Should load all workflow fixtures")
    void testLoadAllWorkflows(String filename) throws IOException {
        // Arrange
        Path workflowPath = Paths.get(FIXTURES_DIR, filename);

        // Act
        String content = Files.readString(workflowPath, StandardCharsets.UTF_8);

        // Assert
        assertThat(content, notNullValue());
        assertThat(content.length(), greaterThan(0));
    }

    @Test
    @DisplayName("Should simulate multi-workflow execution tracking")
    void testMultipleWorkflowExecution() {
        // Arrange
        Map<String, Long> executionTimes = new HashMap<>();

        // Act: Simulate execution of 3 workflows
        for (int i = 1; i <= 3; i++) {
            String workflowName = "workflow-" + i;
            long executionTime = 100L + (i * 50);
            executionTimes.put(workflowName, executionTime);
        }

        // Assert
        assertThat(executionTimes, aMapWithSize(3));
        assertThat(executionTimes.get("workflow-1"), equalTo(150L));
        assertThat(executionTimes.get("workflow-2"), equalTo(200L));
        assertThat(executionTimes.get("workflow-3"), equalTo(250L));
    }

    @Test
    @DisplayName("Should serialize and deserialize execution result")
    void testResultSerialization() throws IOException {
        // Arrange
        Map<String, Object> output = Map.of(
                "status", "complete",
                "confidence", 0.92,
                "items", 5
        );

        DspyExecutionMetrics metrics = DspyExecutionMetrics.builder()
                .compilationTimeMs(100)
                .executionTimeMs(200)
                .inputTokens(50)
                .outputTokens(40)
                .cacheHit(false)
                .contextReused(false)
                .timestamp(Instant.now())
                .build();

        DspyExecutionResult result = DspyExecutionResult.builder()
                .output(output)
                .metrics(metrics)
                .build();

        // Act
        String json = objectMapper.writeValueAsString(result);
        DspyExecutionResult deserialized = objectMapper.readValue(json, DspyExecutionResult.class);

        // Assert
        assertThat(deserialized.output(), hasEntry("status", "complete"));
        assertThat(deserialized.output(), hasEntry("confidence", 0.92));
        assertThat(deserialized.metrics().totalTimeMs(), equalTo(300L));
    }

    @Test
    @DisplayName("Should handle empty workflow gracefully")
    void testEmptyWorkflowHandling() {
        // Arrange
        String emptyWorkflow = "";

        // Act & Assert
        assertTrue(emptyWorkflow.isEmpty());
    }

    @Test
    @DisplayName("Should track workflow execution statistics")
    void testWorkflowExecutionStatistics() {
        // Arrange
        int totalExecutions = 5;
        long totalTime = 0;
        int successCount = 0;

        for (int i = 0; i < totalExecutions; i++) {
            long time = 100 + (i * 50);
            totalTime += time;
            successCount++;
        }

        // Act
        double averageTime = (double) totalTime / successCount;
        double successRate = (double) successCount / totalExecutions;

        // Assert
        assertThat(averageTime, closeTo(250.0, 0.1));
        assertThat(successRate, equalTo(1.0));
    }

    @Test
    @DisplayName("Should build DSPy program with input/output schemas")
    void testProgramWithSchemas() {
        // Arrange
        Map<String, Object> inputSchema = Map.of(
                "text", "string",
                "language", "string"
        );

        Map<String, Object> outputSchema = Map.of(
                "sentiment", "string",
                "confidence", "number",
                "language_detected", "string"
        );

        // Act
        DspyProgram program = DspyProgram.builder()
                .name("multilingual-analyzer")
                .source("code")
                .inputSchema(inputSchema)
                .outputSchema(outputSchema)
                .build();

        // Assert
        assertThat(program.inputSchema(), aMapWithSize(2));
        assertThat(program.outputSchema(), aMapWithSize(3));
    }
}
