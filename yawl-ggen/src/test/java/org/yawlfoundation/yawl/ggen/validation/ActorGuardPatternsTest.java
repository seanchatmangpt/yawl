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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for actor-specific guard patterns:
 * - H_ACTOR_LEAK: Memory leak detection in actor code
 * - H_ACTOR_DEADLOCK: Deadlock risk detection in actor code
 */
public class ActorGuardPatternsTest {

    private HyperStandardsValidator validator;

    @BeforeEach
    void setUp() {
        validator = new HyperStandardsValidator();
    }

    @Test
    void testActorLeakViolations() throws IOException {
        Path testFile = Paths.get("src/test/resources/fixtures/actor/violation-h-actor-leak.java");
        GuardReceipt receipt = validator.validateEmitDir(testFile.getParent());
        
        // Should find actor leak violations
        assertTrue(receipt.getViolations().stream()
            .anyMatch(v -> v.getPattern().equals("H_ACTOR_LEAK")));
        
        // Check specific violations
        List<GuardViolation> actorLeaks = receipt.getViolations().stream()
            .filter(v -> v.getPattern().equals("H_ACTOR_LEAK"))
            .toList();
        
        assertEquals(3, actorLeaks.size(), "Should detect 3 actor leak patterns");
        
        // Verify fix guidance is specific to actor leaks
        actorLeaks.forEach(v -> {
            assertTrue(v.getFixGuidance().contains("actor lifecycle management"));
            assertTrue(v.getFixGuidance().contains("cleanup"));
        });
    }

    @Test
    void testActorDeadlockViolations() throws IOException {
        Path testFile = Paths.get("src/test/resources/fixtures/actor/violation-h-actor-deadlock.java");
        GuardReceipt receipt = validator.validateEmitDir(testFile.getParent());
        
        // Should find actor deadlock violations
        assertTrue(receipt.getViolations().stream()
            .anyMatch(v -> v.getPattern().equals("H_ACTOR_DEADLOCK")));
        
        // Check specific violations
        List<GuardViolation> actorDeadlocks = receipt.getViolations().stream()
            .filter(v -> v.getPattern().equals("H_ACTOR_DEADLOCK"))
            .toList();
        
        // Should detect multiple deadlock patterns
        assertTrue(actorDeadlocks.size() >= 4, "Should detect at least 4 deadlock patterns");
        
        // Verify fix guidance is specific to deadlocks
        actorDeadlocks.forEach(v -> {
            assertTrue(v.getFixGuidance().contains("async messaging"));
            assertTrue(v.getFixGuidance().contains("timeout mechanisms"));
        });
    }

    @Test
    void testCleanActorCodePasses() throws IOException {
        Path testFile = Paths.get("src/test/resources/fixtures/actor/clean-actor-code.java");
        GuardReceipt receipt = validator.validateEmitDir(testFile.getParent());
        
        // Clean code should pass all actor guard checks
        assertTrue(receipt.getStatus().equals("GREEN") || 
                  receipt.getViolations().stream()
                     .filter(v -> v.getPattern().equals("H_ACTOR_LEAK") || 
                                 v.getPattern().equals("H_ACTOR_DEADLOCK"))
                     .count() == 0);
    }

    @Test
    void testActorPatternSummary() throws IOException {
        Path testFile = Paths.get("src/test/resources/fixtures/actor/violation-h-actor-leak.java");
        validator.validateEmitDir(testFile.getParent());
        
        GuardReceipt receipt = validator.getReceipt();
        var summary = receipt.getSummary();
        
        // Check that actor patterns are included in summary
        assertTrue(summary.asMap().containsKey("h_actor_leak_count"));
        assertTrue(summary.asMap().containsKey("h_actor_deadlock_count"));
        
        // Actor leak count should be non-zero
        assertTrue(summary.getH_actor_leak_count() > 0);
        
        // Total violations should include actor patterns
        int totalViolations = summary.getTotalViolations();
        assertTrue(totalViolations >= summary.getH_actor_leak_count());
    }

    @Test
    void testGuardCheckerRegistration() {
        List<GuardChecker> checkers = validator.getCheckers();
        
        // Verify that actor guard checkers are registered
        assertTrue(checkers.stream().anyMatch(c -> c.patternName().equals("H_ACTOR_LEAK")));
        assertTrue(checkers.stream().anyMatch(c -> c.patternName().equals("H_ACTOR_DEADLOCK")));
        
        // Verify severity is FAIL for actor patterns
        checkers.stream()
            .filter(c -> c.patternName().startsWith("H_ACTOR_"))
            .forEach(c -> assertEquals(GuardChecker.Severity.FAIL, c.severity()));
    }

    @Test
    void testActorLeakSpecificPatterns() throws IOException {
        // Test specific actor leak patterns
        String[] leakPatterns = {
            "new\\s+\\w+Actor",      // Creating actors
            "actor\\.put\\(",        // Accumulating state
            "WeakReference"          // Unmanaged references
        };
        
        Path testFile = Paths.get("src/test/resources/fixtures/actor/violation-h-actor-leak.java");
        GuardReceipt receipt = validator.validateEmitDir(testFile.getParent());
        
        // Verify that specific leak patterns are detected
        List<GuardViolation> leakViolations = receipt.getViolations().stream()
            .filter(v -> v.getPattern().equals("H_ACTOR_LEAK"))
            .toList();
        
        assertEquals(3, leakViolations.size(), "Should detect all 3 leak patterns");
        
        // Check for specific violation content
        assertTrue(leakViolations.stream().anyMatch(v -> v.getContent().contains("new Actor")));
        assertTrue(leakViolations.stream().anyMatch(v -> v.getContent().contains("putMessage")));
        assertTrue(leakViolations.stream().anyMatch(v -> v.getContent().contains("WeakReference")));
    }

    @Test
    void testActorDeadlockSpecificPatterns() throws IOException {
        // Test specific actor deadlock patterns
        Path testFile = Paths.get("src/test/resources/fixtures/actor/violation-h-actor-deadlock.java");
        GuardReceipt receipt = validator.validateEmitDir(testFile.getParent());
        
        List<GuardViolation> deadlockViolations = receipt.getViolations().stream()
            .filter(v -> v.getPattern().equals("H_ACTOR_DEADLOCK"))
            .toList();
        
        // Should detect various deadlock patterns
        assertTrue(deadlockViolations.size() >= 4, "Should detect multiple deadlock patterns");
        
        // Check for specific violation content
        assertTrue(deadlockViolations.stream().anyMatch(v -> v.getContent().contains("synchronized")));
        assertTrue(deadlockViolations.stream().anyMatch(v -> v.getContent().contains("wait")));
        assertTrue(deadlockViolations.stream().anyMatch(v -> v.getContent().contains("poll()")));
        assertTrue(deadlockViolations.stream().anyMatch(v -> v.getContent().contains("lock().lock()")));
    }

    @Test
    void testIntegrationWithExistingGuards() throws IOException {
        // Test that actor guards work alongside existing guard patterns
        Path testFile = Paths.get("src/test/resources/fixtures/actor/violation-h-actor-leak.java");
        GuardReceipt receipt = validator.validateEmitDir(testFile.getParent());
        
        // Should have both existing and new guard patterns
        var patterns = receipt.getViolations().stream()
            .map(GuardViolation::getPattern)
            .distinct()
            .toList();
        
        // Check for existing patterns
        assertTrue(patterns.contains("H_TODO") || patterns.contains("H_MOCK") || 
                  patterns.contains("H_STUB"));
        
        // Check for new actor patterns
        assertTrue(patterns.contains("H_ACTOR_LEAK"));
        assertTrue(patterns.contains("H_ACTOR_DEADLOCK"));
    }
}
