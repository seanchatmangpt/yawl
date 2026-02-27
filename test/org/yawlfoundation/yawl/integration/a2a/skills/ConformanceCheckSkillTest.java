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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for ConformanceCheckSkill.
 * Uses real FootprintExtractor + FootprintScorer — no mocks.
 *
 * @since YAWL 6.0
 */
class ConformanceCheckSkillTest {

    // Minimal valid POWL JSON — SEQUENCE A → B
    private static final String SEQUENCE_AB = """
        {"type":"SEQUENCE","id":"root","children":[
          {"type":"ACTIVITY","id":"a1","label":"A"},
          {"type":"ACTIVITY","id":"a2","label":"B"}
        ]}
        """;

    // SEQUENCE A → B → C
    private static final String SEQUENCE_ABC = """
        {"type":"SEQUENCE","id":"root","children":[
          {"type":"ACTIVITY","id":"a1","label":"A"},
          {"type":"ACTIVITY","id":"a2","label":"B"},
          {"type":"ACTIVITY","id":"a3","label":"C"}
        ]}
        """;

    // PARALLEL A ‖ B
    private static final String PARALLEL_AB = """
        {"type":"PARALLEL","id":"root","children":[
          {"type":"ACTIVITY","id":"a1","label":"A"},
          {"type":"ACTIVITY","id":"a2","label":"B"}
        ]}
        """;

    // XOR A ⊕ B
    private static final String XOR_AB = """
        {"type":"XOR","id":"root","children":[
          {"type":"ACTIVITY","id":"a1","label":"A"},
          {"type":"ACTIVITY","id":"a2","label":"B"}
        ]}
        """;

    private ConformanceCheckSkill skill;

    @BeforeEach
    void setUp() {
        skill = new ConformanceCheckSkill();
    }

    // =========================================================================
    // Metadata Tests
    // =========================================================================

    @Test
    void testSkillId() {
        assertEquals("conformance_check", skill.getId());
    }

    @Test
    void testSkillName() {
        assertNotNull(skill.getName());
        assertFalse(skill.getName().isBlank());
    }

    @Test
    void testSkillDescription() {
        String desc = skill.getDescription();
        assertNotNull(desc);
        assertTrue(desc.contains("conformance") || desc.contains("footprint") || desc.contains("POWL"),
            "Description must mention conformance, footprint, or POWL: " + desc);
    }

    @Test
    void testRequiredPermissions() {
        Set<String> perms = skill.getRequiredPermissions();
        assertTrue(perms.contains("workflow:analyze"),
            "Must require workflow:analyze permission");
    }

    @Test
    void testTags() {
        List<String> tags = skill.getTags();
        assertNotNull(tags);
        assertTrue(tags.contains("no-llm"), "Must be tagged no-llm");
        assertTrue(tags.contains("conformance") || tags.contains("footprint"),
            "Must have domain tags (conformance or footprint)");
    }

    @Test
    void testCanExecuteWithPermission() {
        assertTrue(skill.canExecute(Set.of("workflow:analyze")));
    }

    @Test
    void testCannotExecuteWithoutPermission() {
        assertFalse(skill.canExecute(Set.of("workflow:read")));
    }

    // =========================================================================
    // Parameter Validation
    // =========================================================================

    @Test
    void testMissingAllParamsReturnsError() {
        SkillRequest request = SkillRequest.builder("conformance_check").build();
        SkillResult result = skill.execute(request);
        assertTrue(result.isError(), "Missing all params must return error");
        String error = result.getError();
        assertTrue(error.contains("powlModelJson") || error.contains("referenceModelJson"),
            "Error must mention required parameters: " + error);
    }

    @Test
    void testMissingCandidateOnlyReturnsError() {
        SkillRequest request = SkillRequest.builder("conformance_check")
            .parameter("referenceModelJson", SEQUENCE_AB)
            .build();
        SkillResult result = skill.execute(request);
        assertTrue(result.isError(),
            "Providing only referenceModelJson without candidateModelJson must return error");
    }

    @Test
    void testInvalidPowlJsonReturnsError() {
        SkillRequest request = SkillRequest.builder("conformance_check")
            .parameter("powlModelJson", "not-valid-json")
            .build();
        SkillResult result = skill.execute(request);
        assertTrue(result.isError(), "Invalid powlModelJson must return error");
    }

    @Test
    void testInvalidReferenceJsonReturnsError() {
        SkillRequest request = SkillRequest.builder("conformance_check")
            .parameter("referenceModelJson", "not-json")
            .parameter("candidateModelJson", SEQUENCE_AB)
            .build();
        SkillResult result = skill.execute(request);
        assertTrue(result.isError(), "Invalid referenceModelJson must return error");
    }

    // =========================================================================
    // Extract Mode Tests
    // =========================================================================

    @Test
    void testExtractModeSucceeds() {
        SkillRequest request = SkillRequest.builder("conformance_check")
            .parameter("powlModelJson", SEQUENCE_AB)
            .build();
        SkillResult result = skill.execute(request);

        assertTrue(result.isSuccess(), "Extract mode must succeed: " + result.getError());
        assertEquals("extract", result.get("mode"), "Mode must be 'extract'");
    }

    @Test
    void testExtractModeHasFootprintKeys() {
        SkillRequest request = SkillRequest.builder("conformance_check")
            .parameter("powlModelJson", SEQUENCE_AB)
            .build();
        SkillResult result = skill.execute(request);

        assertTrue(result.isSuccess());
        assertNotNull(result.get("directSuccession"), "directSuccession must be present");
        assertNotNull(result.get("concurrency"), "concurrency must be present");
        assertNotNull(result.get("exclusive"), "exclusive must be present");
        assertNotNull(result.get("directSuccessionCount"), "directSuccessionCount must be present");
        assertNotNull(result.get("concurrencyCount"), "concurrencyCount must be present");
        assertNotNull(result.get("exclusiveCount"), "exclusiveCount must be present");
        assertNotNull(result.get("elapsed_ms"), "elapsed_ms must be present");
    }

    @Test
    void testExtractSequenceHasDirectSuccession() {
        SkillRequest request = SkillRequest.builder("conformance_check")
            .parameter("powlModelJson", SEQUENCE_AB)
            .build();
        SkillResult result = skill.execute(request);

        assertTrue(result.isSuccess());
        int dsCount = (int) result.get("directSuccessionCount");
        assertTrue(dsCount > 0, "SEQUENCE model must have direct succession relationships");
    }

    @Test
    void testExtractParallelHasConcurrency() {
        SkillRequest request = SkillRequest.builder("conformance_check")
            .parameter("powlModelJson", PARALLEL_AB)
            .build();
        SkillResult result = skill.execute(request);

        assertTrue(result.isSuccess());
        int concCount = (int) result.get("concurrencyCount");
        assertTrue(concCount > 0, "PARALLEL model must have concurrency relationships");
    }

    @Test
    void testExtractXorHasExclusivity() {
        SkillRequest request = SkillRequest.builder("conformance_check")
            .parameter("powlModelJson", XOR_AB)
            .build();
        SkillResult result = skill.execute(request);

        assertTrue(result.isSuccess());
        int exclCount = (int) result.get("exclusiveCount");
        assertTrue(exclCount > 0, "XOR model must have exclusivity relationships");
    }

    @Test
    void testExtractElapsedMsIsNonNegative() {
        SkillRequest request = SkillRequest.builder("conformance_check")
            .parameter("powlModelJson", SEQUENCE_AB)
            .build();
        SkillResult result = skill.execute(request);

        assertTrue(result.isSuccess());
        long elapsed = (long) result.get("elapsed_ms");
        assertTrue(elapsed >= 0, "elapsed_ms must be non-negative");
    }

    // =========================================================================
    // Compare Mode Tests
    // =========================================================================

    @Test
    void testCompareModeSucceeds() {
        SkillRequest request = SkillRequest.builder("conformance_check")
            .parameter("referenceModelJson", SEQUENCE_AB)
            .parameter("candidateModelJson", SEQUENCE_AB)
            .build();
        SkillResult result = skill.execute(request);

        assertTrue(result.isSuccess(), "Compare mode must succeed: " + result.getError());
        assertEquals("compare", result.get("mode"), "Mode must be 'compare'");
    }

    @Test
    void testCompareModeHasRequiredKeys() {
        SkillRequest request = SkillRequest.builder("conformance_check")
            .parameter("referenceModelJson", SEQUENCE_AB)
            .parameter("candidateModelJson", SEQUENCE_AB)
            .build();
        SkillResult result = skill.execute(request);

        assertTrue(result.isSuccess());
        assertNotNull(result.get("score"), "score must be present");
        assertNotNull(result.get("directSuccessionJaccard"), "directSuccessionJaccard must be present");
        assertNotNull(result.get("concurrencyJaccard"), "concurrencyJaccard must be present");
        assertNotNull(result.get("exclusiveJaccard"), "exclusiveJaccard must be present");
        assertNotNull(result.get("interpretation"), "interpretation must be present");
        assertNotNull(result.get("elapsed_ms"), "elapsed_ms must be present");
    }

    @Test
    void testIdenticalModelsScoreIsOne() {
        SkillRequest request = SkillRequest.builder("conformance_check")
            .parameter("referenceModelJson", SEQUENCE_AB)
            .parameter("candidateModelJson", SEQUENCE_AB)
            .build();
        SkillResult result = skill.execute(request);

        assertTrue(result.isSuccess());
        double score = (double) result.get("score");
        assertEquals(1.0, score, 0.001, "Identical models must have score 1.0");
    }

    @Test
    void testIdenticalModelsInterpretationIsHigh() {
        SkillRequest request = SkillRequest.builder("conformance_check")
            .parameter("referenceModelJson", SEQUENCE_AB)
            .parameter("candidateModelJson", SEQUENCE_AB)
            .build();
        SkillResult result = skill.execute(request);

        assertTrue(result.isSuccess());
        assertEquals("HIGH", result.get("interpretation"),
            "Identical models must be interpreted as HIGH conformance");
    }

    @Test
    void testScoreIsInValidRange() {
        SkillRequest request = SkillRequest.builder("conformance_check")
            .parameter("referenceModelJson", SEQUENCE_AB)
            .parameter("candidateModelJson", SEQUENCE_ABC)
            .build();
        SkillResult result = skill.execute(request);

        assertTrue(result.isSuccess());
        double score = (double) result.get("score");
        assertTrue(score >= 0.0 && score <= 1.0,
            "Score must be in [0.0, 1.0] but was: " + score);
    }

    @Test
    void testInterpretationValidValues() {
        SkillRequest request = SkillRequest.builder("conformance_check")
            .parameter("referenceModelJson", SEQUENCE_AB)
            .parameter("candidateModelJson", XOR_AB)
            .build();
        SkillResult result = skill.execute(request);

        assertTrue(result.isSuccess());
        String interp = (String) result.get("interpretation");
        assertTrue("HIGH".equals(interp) || "MEDIUM".equals(interp) || "LOW".equals(interp),
            "Interpretation must be HIGH, MEDIUM, or LOW: " + interp);
    }

    @Test
    void testExtractModePriorityOverCompare() {
        // When powlModelJson is present alongside reference/candidate, extract mode is used
        SkillRequest request = SkillRequest.builder("conformance_check")
            .parameter("powlModelJson", SEQUENCE_AB)
            .parameter("referenceModelJson", SEQUENCE_AB)
            .parameter("candidateModelJson", SEQUENCE_AB)
            .build();
        SkillResult result = skill.execute(request);

        assertTrue(result.isSuccess());
        assertEquals("extract", result.get("mode"),
            "powlModelJson must take priority and trigger extract mode");
    }
}
