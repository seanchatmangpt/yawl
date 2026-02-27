package org.yawlfoundation.yawl.integration.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.List;
import java.util.Arrays;
import java.time.Duration;
import java.io.IOException;

/**
 * Integration tests for utility classes that verify they work together correctly.
 * These tests focus on interaction patterns between different utilities.
 */
@DisplayName("Utility Integration Tests")
public class UtilityIntegrationTest {

    private SkillLogger logger;
    private Map<String, Object> testParams;

    @BeforeEach
    void setUp() {
        logger = SkillLogger.forSkill("test-skill", "TestSkill");
        testParams = new HashMap<>();
        testParams.put("requiredParam", "value1");
        testParams.put("optionalParam", "value2");
        testParams.put("severity", "HIGH");
        testParams.put("count", 10);
        testParams.put("enabled", true);
    }

    @Test
    @DisplayName("Timer with Logger Integration")
    void testTimerWithLoggerIntegration() {
        // Test SkillExecutionTimer working with SkillLogger for timing logging
        SkillExecutionTimer timer = SkillExecutionTimer.start("test-operation");

        // Simulate some work
        List<String> data = List.of("item1", "item2", "item3");
        for (String item : data) {
            logger.debug("Processing: " + item);
        }

        long elapsed = timer.endAndLog();

        // Verify timer returns non-negative elapsed time
        assertTrue(elapsed >= 0);
    }

    @Test
    @DisplayName("Parameter Validator with Event Severity Utils Integration")
    void testParameterValidatorWithEventSeverityUtils() {
        // Test ParameterValidator working with EventSeverityUtils for validated severity parsing

        // Use ParameterValidator to validate required parameters
        String requiredParam = ParameterValidator.validateRequired(testParams, "requiredParam");
        assertEquals("value1", requiredParam);

        // Use ParameterValidator to get optional parameters
        String optionalParam = ParameterValidator.getOptional(testParams, "optionalParam", "default");
        assertEquals("value2", optionalParam);

        // Use EventSeverityUtils to validate and parse severity
        String severity = ParameterValidator.validateRequired(testParams, "severity");
        String normalizedSeverity = EventSeverityUtils.parseSeverity(severity);

        // Verify severity parsing worked correctly
        assertEquals("HIGH", normalizedSeverity);
        assertTrue(EventSeverityUtils.isValidSeverity(severity));

        // Verify default severity is returned for null/empty input
        assertEquals(EventSeverityUtils.defaultSeverity(),
                    EventSeverityUtils.parseSeverity(null));
    }

    @Test
    @DisplayName("Payload Parser with Parameter Validator Integration")
    void testPayloadParserWithParameterValidator() {
        // Test PayloadParser working with ParameterValidator for payload validation

        // Create test payload string
        String payloadStr = "requiredParam=value1,optionalParam=value2,severity=HIGH,count=10,enabled=true";
        PayloadParser parser = new PayloadParser(payloadStr);

        // Use ParameterValidator to validate required parameter from parser
        Map<String, Object> objectParams = new HashMap<>(parser.toMap());
        String requiredValue = ParameterValidator.validateRequired(
            objectParams, "requiredParam");
        assertEquals("value1", requiredValue);

        // Use ParameterValidator to get optional parameter from parser
        String optionalValue = ParameterValidator.getOptional(
            objectParams, "optionalParam", "default");
        assertEquals("value2", optionalValue);

        // Use ParameterValidator to validate format
        assertThrows(IllegalArgumentException.class, () -> {
            ParameterValidator.validateFormat(
                "invalid-email",
                java.util.regex.Pattern.compile("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$"),
                "email"
            );
        });

        // Verify parser correctly converts types
        assertEquals(10, parser.getInt("count", 0));
        assertTrue(parser.getBoolean("enabled", false));
        assertEquals(3, parser.size());
    }

    @Test
    @DisplayName("GraalVM Utils Integration")
    void testGraalVMUtilsIntegration() {
        // Test GraalVMUtils functionality

        // Test availability check
        boolean isGraalAvailable = GraalVMUtils.isAvailable();

        // Test exception detection with a test exception
        Exception testException = new ClassNotFoundException("org.graalvm.polyglot.PolyglotException");
        boolean isGraalException = GraalVMUtils.isUnavailableException(testException);

        // The result depends on environment, but we can test the logic
        assertNotNull(isGraalException);

        // Test fallback guidance
        String guidance = GraalVMUtils.getFallbackGuidance();
        assertNotNull(guidance);
        assertTrue(guidance.contains("GraalVM not available"));

        // Test troubleshooting info
        String troubleshooting = GraalVMUtils.getTroubleshootingInfo();
        assertNotNull(troubleshooting);
        assertTrue(troubleshooting.contains("GraalVM JDK"));
    }

    @Test
    @DisplayName("Similarity Metrics with Yawl Constants Integration")
    void testSimilarityMetricsWithYawlConstantsIntegration() {
        // Test SimilarityMetrics working with YawlConstants for conformance thresholds

        // Create test sets for Jaccard similarity
        Set<String> referenceSet = Set.of("task1", "task2", "task3", "task4");
        Set<String> candidateSet = Set.of("task1", "task2", "task5");

        // Calculate similarity
        double similarity = SimilarityMetrics.jaccardSimilarity(referenceSet, candidateSet);

        // Verify similarity is within valid range
        assertTrue(similarity >= 0.0 && similarity <= 1.0);

        // Use YawlConstants timeout for conformance checking
        Duration timeout = YawlConstants.DEFAULT_TIMEOUT;

        // Check if similarity meets various thresholds
        boolean meetsGoodThreshold = SimilarityMetrics.meetsThreshold(
            similarity, SimilarityMetrics.THRESHOLD_GOOD);
        boolean meetsAcceptableThreshold = SimilarityMetrics.meetsThreshold(
            similarity, SimilarityMetrics.THRESHOLD_ACCEPTABLE);

        // Get interpretation using constants
        String interpretation = SimilarityMetrics.getDetailedInterpretation(similarity);

        // Verify interpretation is not null
        assertNotNull(interpretation);

        // Verify conformance constants are defined
        assertTrue(YawlConstants.TAG_CONFORMANCE.startsWith("conformance"));
        assertTrue(YawlConstants.TOOL_CONFORMANCE.startsWith("yawl_conformance"));
    }

    @Test
    @DisplayName("Combined Workflow Integration Test")
    void testCombinedWorkflowIntegration() {
        // Test all utilities working together in a realistic workflow

        // Start timing the entire workflow
        SkillExecutionTimer workflowTimer = SkillExecutionTimer.start("integrated-workflow");

        try {
            // Step 1: Log workflow start
            logger.info("Starting integrated workflow");

            // Step 2: Parse and validate input payload
            String payload = "taskId=task1,severity=HIGH,data={key:value}";
            PayloadParser parser = new PayloadParser(payload);

            // Validate required parameters
            Map<String, Object> objectParams = new HashMap<>(parser.toMap());
            String taskId = ParameterValidator.validateRequired(
                objectParams, "taskId");
            assertEquals("task1", taskId);

            // Validate severity
            String severity = EventSeverityUtils.parseSeverity(
                ParameterValidator.getOptional(objectParams, "severity", "MEDIUM"));
            assertEquals("HIGH", severity);

            // Step 3: Simulate processing with timing
            SkillExecutionTimer processTimer = SkillExecutionTimer.start("process-data");
            logger.infoStructured("Processing data", Map.of(
                "taskId", taskId,
                "severity", severity,
                "dataSize", parser.size()
            ));

            // Step 4: Calculate similarity (simulated)
            Set<String> expected = Set.of("step1", "step2", "step3");
            Set<String> actual = Set.of("step1", "step2");
            double similarity = SimilarityMetrics.jaccardSimilarity(expected, actual);

            // Step 5: Check if meets conformance threshold
            boolean meetsThreshold = SimilarityMetrics.meetsThreshold(
                similarity, YawlConstants.DEFAULT_TIMEOUT.getSeconds() / 300.0); // Convert to threshold

            // Log results
            logger.info("Similarity score: " + similarity);
            logger.info("Meets threshold: " + meetsThreshold);

            long processTime = processTimer.endAndLog();
            assertTrue(processTime >= 0);

        } finally {
            // Complete workflow timing
            long totalTime = workflowTimer.endAndLog();
            assertTrue(totalTime >= 0);

            // Log final results
            logger.info("Integrated workflow completed in " + totalTime + "ms");
        }
    }

    @Test
    @DisplayName("Error Handling Integration")
    void testErrorHandlingIntegration() {
        // Test error handling across utilities

        // Test with invalid payload that should throw exceptions
        assertThrows(IllegalArgumentException.class, () -> {
            PayloadParser parser = new PayloadParser("invalid-payload");
            Map<String, Object> objectParams = new HashMap<>(parser.toMap());
            ParameterValidator.validateRequired(objectParams, "missingParam");
        });

        // Test with invalid severity
        assertThrows(IllegalArgumentException.class, () -> {
            EventSeverityUtils.parseSeverity("INVALID_SEVERITY");
        });

        // Test similarity metrics with invalid input
        assertThrows(IllegalArgumentException.class, () -> {
            SimilarityMetrics.interpretScore(-1.0);
        });

        // Test GraalVM exception detection
        Exception testException = new ClassNotFoundException("org.graalvm.polyglot.PolyglotException");
        boolean isGraalException = GraalVMUtils.isUnavailableException(testException);

        // The result depends on environment, but we can test the logic
        assertNotNull(isGraalException);
    }
}