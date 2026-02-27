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

package org.yawlfoundation.yawl.integration.a2a.skills;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for TemporalForkSkill.
 * Uses real TemporalForkEngine with lambda constructor — no mocks.
 *
 * @since YAWL 6.0
 */
class TemporalForkSkillTest {

    private TemporalForkSkill skill;

    @BeforeEach
    void setUp() {
        skill = new TemporalForkSkill();
    }

    // =========================================================================
    // Metadata Tests
    // =========================================================================

    @Test
    void testSkillId() {
        assertEquals("temporal_fork", skill.getId());
    }

    @Test
    void testSkillName() {
        assertNotNull(skill.getName());
        assertFalse(skill.getName().isBlank());
    }

    @Test
    void testSkillDescription() {
        assertNotNull(skill.getDescription());
        assertTrue(skill.getDescription().contains("temporal") || skill.getDescription().contains("fork"),
            "Description must mention temporal or fork");
    }

    @Test
    void testRequiredPermissions() {
        assertTrue(skill.getRequiredPermissions().contains("workflow:simulate"),
            "Must require workflow:simulate");
    }

    @Test
    void testTags() {
        List<String> tags = skill.getTags();
        assertNotNull(tags);
        assertTrue(tags.contains("no-llm"), "Must be tagged no-llm");
        assertTrue(tags.contains("temporal"), "Must be tagged temporal");
    }

    @Test
    void testCanExecuteWithPermission() {
        assertTrue(skill.canExecute(java.util.Set.of("workflow:simulate")));
    }

    @Test
    void testCannotExecuteWithoutPermission() {
        assertFalse(skill.canExecute(java.util.Set.of("workflow:read")));
    }

    // =========================================================================
    // Parameter Validation
    // =========================================================================

    @Test
    void testMissingTaskNamesReturnsError() {
        SkillRequest request = SkillRequest.builder("temporal_fork").build();
        SkillResult result = skill.execute(request);
        assertTrue(result.isError(), "Missing taskNames must return error");
        assertTrue(result.getError().contains("taskNames"),
            "Error must mention taskNames parameter");
    }

    @Test
    void testBlankTaskNamesReturnsError() {
        SkillRequest request = SkillRequest.builder("temporal_fork")
            .parameter("taskNames", "   ")
            .build();
        SkillResult result = skill.execute(request);
        assertTrue(result.isError(), "Blank taskNames must return error");
    }

    @Test
    void testInvalidMaxSecondsReturnsError() {
        SkillRequest request = SkillRequest.builder("temporal_fork")
            .parameter("taskNames", "TaskA,TaskB")
            .parameter("maxSeconds", "not-a-number")
            .build();
        SkillResult result = skill.execute(request);
        assertTrue(result.isError(), "Invalid maxSeconds must return error");
        assertTrue(result.getError().contains("maxSeconds"),
            "Error must mention maxSeconds");
    }

    @Test
    void testMaxSecondsOutOfRangeReturnsError() {
        SkillRequest request = SkillRequest.builder("temporal_fork")
            .parameter("taskNames", "TaskA,TaskB")
            .parameter("maxSeconds", "9999")
            .build();
        SkillResult result = skill.execute(request);
        assertTrue(result.isError(), "Out-of-range maxSeconds must return error");
    }

    // =========================================================================
    // Fork Execution Tests
    // =========================================================================

    @Test
    void testSingleTaskForkSucceeds() {
        SkillRequest request = SkillRequest.builder("temporal_fork")
            .parameter("taskNames", "ApproveApplication")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue(result.isSuccess(), "Expected success but got: " + result.getError());
        assertNotNull(result.get("forks"), "forks must be present");
        assertNotNull(result.get("completedForks"), "completedForks must be present");
        assertNotNull(result.get("elapsed_ms"), "elapsed_ms must be present");
    }

    @Test
    void testMultipleTaskForkSucceeds() {
        SkillRequest request = SkillRequest.builder("temporal_fork")
            .parameter("taskNames", "ReviewApplication,ApproveApplication,RejectApplication")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue(result.isSuccess(), "Expected success but got: " + result.getError());
        assertEquals(3, result.get("requestedForks"), "Should have requested 3 forks");
        assertEquals(3, result.get("completedForks"), "All 3 forks must complete");
    }

    @Test
    void testForkResultContainsDominantPath() {
        SkillRequest request = SkillRequest.builder("temporal_fork")
            .parameter("taskNames", "TaskA,TaskB")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue(result.isSuccess(), "Expected success");
        assertNotNull(result.get("dominantPath"), "dominantPath must be present");
    }

    @Test
    void testAllForksCompletedFlagPresent() {
        SkillRequest request = SkillRequest.builder("temporal_fork")
            .parameter("taskNames", "TaskX")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue(result.isSuccess(), "Expected success");
        assertNotNull(result.get("allCompleted"), "allCompleted flag must be present");
    }

    @Test
    void testForksListIsNotEmpty() {
        SkillRequest request = SkillRequest.builder("temporal_fork")
            .parameter("taskNames", "Step1,Step2")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue(result.isSuccess(), "Expected success");
        @SuppressWarnings("unchecked")
        var forks = (List<?>) result.get("forks");
        assertNotNull(forks);
        assertFalse(forks.isEmpty(), "forks list must not be empty");
    }

    @Test
    void testElapsedMsIsPositive() {
        SkillRequest request = SkillRequest.builder("temporal_fork")
            .parameter("taskNames", "TaskA")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue(result.isSuccess(), "Expected success");
        long elapsed = (long) result.get("elapsed_ms");
        assertTrue(elapsed >= 0, "elapsed_ms must be non-negative");
    }

    @Test
    void testCustomMaxSecondsIsRespected() {
        SkillRequest request = SkillRequest.builder("temporal_fork")
            .parameter("taskNames", "FastTask")
            .parameter("maxSeconds", "5")
            .build();
        SkillResult result = skill.execute(request);
        // Should complete well within 5s
        assertTrue(result.isSuccess(), "Expected success with maxSeconds=5");
    }
}
