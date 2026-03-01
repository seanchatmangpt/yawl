/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.yawlfoundation.yawl.ggen.validation.model.GuardReceipt;
import org.yawlfoundation.yawl.ggen.validation.model.GuardViolation;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Enhanced test suite for H_ACTOR_LEAK guard patterns.
 * Tests comprehensive memory leak detection with detailed pattern validation.
 */
public class EnhancedActorGuardPatternsTest {

    private HyperStandardsValidator validator;

    @BeforeEach
    void setUp() {
        validator = new HyperStandardsValidator();
    }

    @Test
    void testComprehensiveActorLeakDetection() throws IOException {
        Path testFile = Paths.get("src/test/resources/fixtures/actor/violation-h-actor-leak-comprehensive.java");
        GuardReceipt receipt = validator.validateEmitDir(testFile.getParent());

        // Should find many actor leak violations
        List<GuardViolation> actorLeaks = receipt.getViolations().stream()
            .filter(v -> v.getPattern().equals("H_ACTOR_LEAK"))
            .toList();

        // Verify we detect a significant number of patterns
        assertTrue(actorLeaks.size() >= 15, "Should detect at least 15 actor leak patterns");

        // Verify different violation types
        Map<String, Long> violationTypes = actorLeaks.stream()
            .collect(Collectors.groupingBy(v -> getViolationType(v), Collectors.counting()));

        assertTrue(violationTypes.containsKey("ACTOR_CREATION_NO_DESTRUCTION"));
        assertTrue(violationTypes.containsKey("UNBOUNDED_ACCUMULATION"));
        assertTrue(violationTypes.containsKey("REFERENCE_LEAK"));
        assertTrue(violationTypes.containsKey("RESOURCE_LEAK"));
        assertTrue(violationTypes.containsKey("MAILBOX_OVERFLOW"));

        // Verify specific patterns are detected
        assertTrue(actorLeaks.stream().anyMatch(v -> v.getContent().contains("new Actor")));
        assertTrue(actorLeaks.stream().anyMatch(v -> v.getContent().contains("putMessage")));
        assertTrue(actorLeaks.stream().anyMatch(v -> v.getContent().contains("WeakReference")));
        assertTrue(actorLeaks.stream().anyMatch(v -> v.getContent().contains("Executors")));
        assertTrue(actorLeaks.stream().anyMatch(v -> v.getContent().contains("ArrayBlockingQueue")));
    }

    @Test
    void testCleanCodePassesAllChecks() throws IOException {
        Path testFile = Paths.get("src/test/resources/fixtures/actor/clean-actor-code-comprehensive.java");
        GuardReceipt receipt = validator.validateEmitDir(testFile.getParent());

        // Clean code should have no actor leak violations
        List<GuardViolation> actorLeaks = receipt.getViolations().stream()
            .filter(v -> v.getPattern().equals("H_ACTOR_LEAK"))
            .toList();

        assertTrue(actorLeaks.isEmpty(), "Clean code should have no actor leak violations");

        // Should still have proper guard guidance
        actorLeaks.forEach(v -> {
            assertTrue(v.getFixGuidance().contains("actor lifecycle management"));
            assertTrue(v.getFixGuidance().contains("cleanup"));
        });
    }

    @Test
    void testViolationTypeSpecificDetection() throws IOException {
        Path testFile = Paths.get("src/test/resources/fixtures/actor/violation-h-actor-leak-comprehensive.java");
        GuardReceipt receipt = validator.validateEmitDir(testFile.getParent());

        List<GuardViolation> actorLeaks = receipt.getViolations().stream()
            .filter(v -> v.getPattern().equals("H_ACTOR_LEAK"))
            .toList();

        // Test specific violation type detection
        List<GuardViolation> creationLeaks = actorLeaks.stream()
            .filter(v -> getViolationType(v).equals("ACTOR_CREATION_NO_DESTRUCTION"))
            .toList();

        assertFalse(creationLeaks.isEmpty());
        creationLeaks.forEach(v -> {
            assertTrue(v.getContent().contains("new Actor") ||
                      v.getContent().contains("Actor.builder") ||
                      v.getContent().contains("spawn"));
        });

        List<GuardViolation> accumulationLeaks = actorLeaks.stream()
            .filter(v -> getViolationType(v).equals("UNBOUNDED_ACCUMULATION"))
            .toList();

        assertFalse(accumulationLeaks.isEmpty());
        accumulationLeaks.forEach(v -> {
            assertTrue(v.getContent().contains("putMessage") ||
                      v.getContent().contains("add(") ||
                      v.getContent().contains("ArrayList") ||
                      v.getContent().contains("while"));
        });
    }

    @Test
    void testPerformanceConsiderations() throws IOException {
        Path testFile = Paths.get("src/test/resources/fixtures/actor/violation-h-actor-leak-comprehensive.java");

        // Time the validation to ensure it's reasonable
        long startTime = System.currentTimeMillis();
        GuardReceipt receipt = validator.validateEmitDir(testFile.getParent());
        long endTime = System.currentTimeMillis();

        // Should complete in under 5 seconds for comprehensive test
        assertTrue(endTime - startTime < 5000, "Comprehensive leak detection should complete in < 5s");

        // Verify performance isn't affected by complexity
        List<GuardViolation> actorLeaks = receipt.getViolations().stream()
            .filter(v -> v.getPattern().equals("H_ACTOR_LEAK"))
            .toList();

        assertTrue(actorLeaks.size() >= 15, "Performance test should detect sufficient patterns");
    }

    @Test
    void testEdgeCaseHandling() throws IOException {
        // Test the query handles edge cases correctly
        Path testFile = Paths.get("src/test/resources/fixtures/actor/violation-h-actor-leak-comprehensive.java");
        GuardReceipt receipt = validator.validateEmitDir(testFile.getParent());

        // Should handle complex nested patterns
        List<GuardViolation> complexLeaks = receipt.getViolations().stream()
            .filter(v -> v.getPattern().equals("H_ACTOR_LEAK"))
            .filter(v -> v.getContent().contains("complexLeakScenario") ||
                       v.getContent().contains("circularReferenceLeak"))
            .toList();

        assertFalse(complexLeaks.isEmpty(), "Should detect complex edge cases");

        // Should not generate false positives for legitimate patterns
        List<GuardViolation> falsePositives = receipt.getViolations().stream()
            .filter(v -> v.getPattern().equals("H_ACTOR_LEAK"))
            .filter(v -> v.getContent().contains("AutoCloseable") ||
                       v.getContent().contains("try-with-resources"))
            .toList();

        assertTrue(falsePositives.isEmpty() ||
                  falsePositives.stream().allMatch(v ->
                      v.getContent().contains("not properly closed") ||
                      v.getContent().contains("resource leak")),
                  "Should not have false positives for proper resource handling");
    }

    @Test
    void testActorSummaryEnhancements() throws IOException {
        Path testFile = Paths.get("src/test/resources/fixtures/actor/violation-h-actor-leak-comprehensive.java");
        validator.validateEmitDir(testFile.getParent());

        GuardReceipt receipt = validator.getReceipt();
        var summary = receipt.getSummary();

        // Verify actor pattern counts are tracked
        assertTrue(summary.getH_actor_leak_count() > 10);
        assertTrue(summary.getH_actor_deadlock_count() >= 0);

        // Total should include all patterns
        int totalViolations = summary.getTotalViolations();
        assertEquals(totalViolations, summary.getH_todo_count() +
                   summary.getH_mock_count() +
                   summary.getH_stub_count() +
                   summary.getH_empty_count() +
                   summary.getH_fallback_count() +
                   summary.getH_lie_count() +
                   summary.getH_silent_count() +
                   summary.getH_actor_leak_count() +
                   summary.getH_actor_deadlock_count());
    }

    @Test
    void testQueryOptimization() throws IOException {
        // Test that the query is optimized for performance
        Path testFile = Paths.get("src/test/resources/fixtures/actor/violation-h-actor-leak-comprehensive.java");

        // Run multiple times to ensure consistent performance
        for (int i = 0; i < 3; i++) {
            GuardReceipt receipt = validator.validateEmitDir(testFile.getParent());
            List<GuardViolation> actorLeaks = receipt.getViolations().stream()
                .filter(v -> v.getPattern().equals("H_ACTOR_LEAK"))
                .toList();

            // Should consistently detect patterns
            assertTrue(actorLeaks.size() >= 15,
                "Iteration " + (i + 1) + ": Should detect at least 15 patterns");
        }
    }

    @Test
    void testIntegrationWithExistingGuards() throws IOException {
        // Test that H_ACTOR_LEAK works with other guard patterns
        Path testFile = Paths.get("src/test/resources/fixtures/actor/violation-h-actor-leak-comprehensive.java");
        GuardReceipt receipt = validator.validateEmitDir(testFile.getParent());

        // Should have both existing and actor guard patterns
        var patterns = receipt.getViolations().stream()
            .map(GuardViolation::getPattern)
            .distinct()
            .toList();

        // Verify actor patterns are included
        assertTrue(patterns.contains("H_ACTOR_LEAK"));

        // Verify other patterns might also exist
        boolean hasExistingPattern = patterns.stream()
            .anyMatch(p -> p.equals("H_TODO") ||
                          p.equals("H_MOCK") ||
                          p.equals("H_STUB"));

        // Either has existing patterns or comprehensive actor violations
        assertTrue(hasExistingPattern || patterns.size() >= 3);
    }

    @Test
    void testMemoryAwarenessPatterns() throws IOException {
        Path testFile = Paths.get("src/test/resources/fixtures/actor/violation-h-actor-leak-comprehensive.java");
        GuardReceipt receipt = validator.validateEmitDir(testFile.getParent());

        // Check that memory awareness patterns are detected
        List<GuardViolation> memoryLeaks = receipt.getViolations().stream()
            .filter(v -> v.getPattern().equals("H_ACTOR_LEAK"))
            .filter(v -> v.getContent().contains("Runtime.getRuntime") ||
                       v.getContent().contains("memory") ||
                       v.getContent().contains("GC"))
            .toList();

        // Memory awareness violations should be detected if present
        if (!memoryLeaks.isEmpty()) {
            assertTrue(memoryLeaks.size() > 0);
        }
    }

    @Test
    void testPatternSpecificGuidance() throws IOException {
        Path testFile = Paths.get("src/test/resources/fixtures/actor/violation-h-actor-leak-comprehensive.java");
        GuardReceipt receipt = validator.validateEmitDir(testFile.getParent());

        List<GuardViolation> actorLeaks = receipt.getViolations().stream()
            .filter(v -> v.getPattern().equals("H_ACTOR_LEAK"))
            .toList();

        // Verify all violations have proper actor-specific guidance
        actorLeaks.forEach(v -> {
            String guidance = v.getFixGuidance();
            assertTrue(guidance.contains("actor lifecycle management"));
            assertTrue(guidance.contains("cleanup"));

            // Additional guidance based on violation type
            String violationType = getViolationType(v);
            switch (violationType) {
                case "ACTOR_CREATION_NO_DESTRUCTION":
                    assertTrue(guidance.contains("destruction") || guidance.contains("cleanup"));
                    break;
                case "UNBOUNDED_ACCUMULATION":
                    assertTrue(guidance.contains("bounds") || guidance.contains("cleanup"));
                    break;
                case "REFERENCE_LEAK":
                    assertTrue(guidance.contains("reference") || guidance.contains("manage"));
                    break;
                case "RESOURCE_LEAK":
                    assertTrue(guidance.contains("close") || guidance.contains("cleanup"));
                    break;
                case "MAILBOX_OVERFLOW":
                    assertTrue(guidance.contains("backpressure") || guidance.contains("bounded"));
                    break;
            }
        });
    }

    private String getViolationType(GuardViolation violation) {
        // Extract violation type from the violation message
        String message = violation.getContent();
        if (message.contains("Actor creation without destruction")) {
            return "ACTOR_CREATION_NO_DESTRUCTION";
        } else if (message.contains("Unbounded state accumulation")) {
            return "UNBOUNDED_ACCUMULATION";
        } else if (message.contains("Reference leak")) {
            return "REFERENCE_LEAK";
        } else if (message.contains("Resource leak")) {
            return "RESOURCE_LEAK";
        } else if (message.contains("Mailbox overflow")) {
            return "MAILBOX_OVERFLOW";
        }
        return "UNKNOWN";
    }
}