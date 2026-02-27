/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.processmining;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD process mining session tests.
 * Tests real ProcessMiningSession record behavior (immutability, factory methods, validation).
 *
 * @author Test Specialist
 */
class ProcessMiningSessionTest {

    /**
     * Test: start factory method generates a new session with UUID sessionId.
     */
    @Test
    void start_generatesSessionId() {
        ProcessMiningSession session = ProcessMiningSession.start("spec-test-001");

        assertNotNull(session.sessionId());
        assertFalse(session.sessionId().isEmpty());
        assertTrue(session.sessionId().length() > 0);
        // UUID format check (rough)
        assertTrue(session.sessionId().contains("-") || session.sessionId().length() > 10);
    }

    /**
     * Test: start generates unique session IDs.
     */
    @Test
    void start_generatesUniqueIds() {
        ProcessMiningSession session1 = ProcessMiningSession.start("spec-1");
        ProcessMiningSession session2 = ProcessMiningSession.start("spec-1");

        assertNotEquals(session1.sessionId(), session2.sessionId());
    }

    /**
     * Test: start sets specificationId correctly.
     */
    @Test
    void start_setsSpecificationId() {
        String specId = "my-workflow-spec";
        ProcessMiningSession session = ProcessMiningSession.start(specId);

        assertEquals(specId, session.specificationId());
    }

    /**
     * Test: start sets createdAt to approximately now.
     */
    @Test
    void start_setsCreatedAtToNow() {
        Instant before = Instant.now();
        ProcessMiningSession session = ProcessMiningSession.start("spec-1");
        Instant after = Instant.now();

        assertNotNull(session.createdAt());
        assertTrue(!session.createdAt().isBefore(before.minusSeconds(1)));
        assertTrue(!session.createdAt().isAfter(after.plusSeconds(1)));
    }

    /**
     * Test: start sets lastAnalyzedAt to null.
     */
    @Test
    void start_lastAnalyzedAtIsNull() {
        ProcessMiningSession session = ProcessMiningSession.start("spec-1");

        assertNull(session.lastAnalyzedAt());
    }

    /**
     * Test: start sets totalCasesAnalyzed to 0.
     */
    @Test
    void start_totalCasesAnalyzedIsZero() {
        ProcessMiningSession session = ProcessMiningSession.start("spec-1");

        assertEquals(0, session.totalCasesAnalyzed());
    }

    /**
     * Test: start sets all metric scores to 0.0.
     */
    @Test
    void start_allMetricScoresAreZero() {
        ProcessMiningSession session = ProcessMiningSession.start("spec-1");

        assertEquals(0.0, session.lastFitnessScore());
        assertEquals(0.0, session.lastPrecisionScore());
        assertEquals(0.0, session.lastAvgFlowTimeMs());
    }

    /**
     * Test: hasAnalyzed returns false on new session.
     */
    @Test
    void start_hasNotAnalyzed() {
        ProcessMiningSession session = ProcessMiningSession.start("spec-1");

        assertFalse(session.hasAnalyzed());
    }

    /**
     * Test: withMetrics updates all metric fields.
     */
    @Test
    void withMetrics_updatesAllFields() {
        ProcessMiningSession session = ProcessMiningSession.start("spec-1");

        ProcessMiningSession updated = session.withMetrics(10, 0.9, 0.85, 5000.0);

        assertEquals(10, updated.totalCasesAnalyzed());
        assertEquals(0.9, updated.lastFitnessScore());
        assertEquals(0.85, updated.lastPrecisionScore());
        assertEquals(5000.0, updated.lastAvgFlowTimeMs());
    }

    /**
     * Test: withMetrics preserves sessionId and specificationId.
     */
    @Test
    void withMetrics_preservesIdentifiers() {
        ProcessMiningSession session = ProcessMiningSession.start("spec-original");

        ProcessMiningSession updated = session.withMetrics(5, 0.8, 0.75, 3000.0);

        assertEquals(session.sessionId(), updated.sessionId());
        assertEquals(session.specificationId(), updated.specificationId());
    }

    /**
     * Test: withMetrics updates lastAnalyzedAt to approximately now.
     */
    @Test
    void withMetrics_setsLastAnalyzedAt() {
        ProcessMiningSession session = ProcessMiningSession.start("spec-1");

        Instant before = Instant.now();
        ProcessMiningSession updated = session.withMetrics(1, 0.5, 0.5, 1000.0);
        Instant after = Instant.now();

        assertNotNull(updated.lastAnalyzedAt());
        assertTrue(!updated.lastAnalyzedAt().isBefore(before.minusSeconds(1)));
        assertTrue(!updated.lastAnalyzedAt().isAfter(after.plusSeconds(1)));
    }

    /**
     * Test: withMetrics returns a new instance (immutability).
     */
    @Test
    void withMetrics_returnNewInstance() {
        ProcessMiningSession session = ProcessMiningSession.start("spec-1");

        ProcessMiningSession updated = session.withMetrics(5, 0.9, 0.8, 2000.0);

        assertNotSame(session, updated);
        assertEquals(0, session.totalCasesAnalyzed());
        assertEquals(5, updated.totalCasesAnalyzed());
    }

    /**
     * Test: hasAnalyzed returns true after withMetrics.
     */
    @Test
    void withMetrics_hasAnalyzedTrue() {
        ProcessMiningSession session = ProcessMiningSession.start("spec-1");

        ProcessMiningSession updated = session.withMetrics(3, 0.7, 0.6, 1500.0);

        assertTrue(updated.hasAnalyzed());
    }

    /**
     * Test: hasAnalyzed returns false if totalCasesAnalyzed is 0 even if lastAnalyzedAt is set.
     */
    @Test
    void hasAnalyzed_requiresBothConditions() {
        ProcessMiningSession session = ProcessMiningSession.start("spec-1");
        ProcessMiningSession updated = session.withMetrics(0, 1.0, 1.0, 0.0);

        // lastAnalyzedAt is set, but totalCasesAnalyzed is 0
        assertFalse(updated.hasAnalyzed());
    }

    /**
     * Test: toSummary returns non-null, non-empty string.
     */
    @Test
    void toSummary_returnsString() {
        ProcessMiningSession session = ProcessMiningSession.start("spec-1");

        String summary = session.toSummary();

        assertNotNull(summary);
        assertFalse(summary.isEmpty());
    }

    /**
     * Test: toSummary contains specification ID.
     */
    @Test
    void toSummary_containsSpecId() {
        String specId = "my-unique-spec-id";
        ProcessMiningSession session = ProcessMiningSession.start(specId);

        String summary = session.toSummary();

        assertTrue(summary.contains(specId));
    }

    /**
     * Test: toSummary contains session ID.
     */
    @Test
    void toSummary_containsSessionId() {
        ProcessMiningSession session = ProcessMiningSession.start("spec-1");

        String summary = session.toSummary();

        assertTrue(summary.contains(session.sessionId()));
    }

    /**
     * Test: toSummary contains metrics.
     */
    @Test
    void toSummary_containsMetrics() {
        ProcessMiningSession session = ProcessMiningSession.start("spec-1");
        ProcessMiningSession updated = session.withMetrics(25, 0.95, 0.87, 4500.0);

        String summary = updated.toSummary();

        assertTrue(summary.contains("25"));  // totalCasesAnalyzed
        assertTrue(summary.contains("0.95"));  // fitness
        assertTrue(summary.contains("0.87"));  // precision
        assertTrue(summary.contains("4500"));  // flow time
    }

    /**
     * Test: toSummary contains "never" when lastAnalyzedAt is null.
     */
    @Test
    void toSummary_showsNeverWhenNotAnalyzed() {
        ProcessMiningSession session = ProcessMiningSession.start("spec-1");

        String summary = session.toSummary();

        assertTrue(summary.contains("never"));
    }

    /**
     * Test: toSummary is multi-line (formatted nicely).
     */
    @Test
    void toSummary_isFormattedMultiline() {
        ProcessMiningSession session = ProcessMiningSession.start("spec-1");

        String summary = session.toSummary();

        assertTrue(summary.contains("\n"));
        assertTrue(summary.contains("{"));
        assertTrue(summary.contains("}"));
    }

    /**
     * Test: constructor with null sessionId throws exception.
     */
    @Test
    void constructor_nullSessionId_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            new ProcessMiningSession(
                null,
                "spec-1",
                Instant.now(),
                null,
                0,
                0.0,
                0.0,
                0.0
            )
        );
    }

    /**
     * Test: constructor with null specificationId throws exception.
     */
    @Test
    void constructor_nullSpecId_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            new ProcessMiningSession(
                "session-1",
                null,
                Instant.now(),
                null,
                0,
                0.0,
                0.0,
                0.0
            )
        );
    }

    /**
     * Test: constructor with null createdAt throws exception.
     */
    @Test
    void constructor_nullCreatedAt_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            new ProcessMiningSession(
                "session-1",
                "spec-1",
                null,
                null,
                0,
                0.0,
                0.0,
                0.0
            )
        );
    }

    /**
     * Test: constructor with fitness score < 0.0 throws exception.
     */
    @Test
    void constructor_fitnessNegative_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            new ProcessMiningSession(
                "session-1",
                "spec-1",
                Instant.now(),
                null,
                0,
                -0.1,
                0.0,
                0.0
            )
        );
    }

    /**
     * Test: constructor with fitness score > 1.0 throws exception.
     */
    @Test
    void constructor_fitnessOverOne_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            new ProcessMiningSession(
                "session-1",
                "spec-1",
                Instant.now(),
                null,
                0,
                1.1,
                0.0,
                0.0
            )
        );
    }

    /**
     * Test: constructor with precision score > 1.0 throws exception.
     */
    @Test
    void constructor_precisionOverOne_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            new ProcessMiningSession(
                "session-1",
                "spec-1",
                Instant.now(),
                null,
                0,
                0.5,
                1.1,
                0.0
            )
        );
    }

    /**
     * Test: constructor with negative totalCasesAnalyzed throws exception.
     */
    @Test
    void constructor_negativeCases_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            new ProcessMiningSession(
                "session-1",
                "spec-1",
                Instant.now(),
                null,
                -1,
                0.0,
                0.0,
                0.0
            )
        );
    }

    /**
     * Test: constructor with negative avgFlowTimeMs throws exception.
     */
    @Test
    void constructor_negativeFlowTime_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            new ProcessMiningSession(
                "session-1",
                "spec-1",
                Instant.now(),
                null,
                0,
                0.0,
                0.0,
                -100.0
            )
        );
    }

    /**
     * Test: valid constructor with boundary scores (0.0 and 1.0) succeeds.
     */
    @Test
    void constructor_boundaryScores_valid() {
        assertDoesNotThrow(() ->
            new ProcessMiningSession(
                "session-1",
                "spec-1",
                Instant.now(),
                null,
                10,
                0.0,
                1.0,
                0.0
            )
        );

        assertDoesNotThrow(() ->
            new ProcessMiningSession(
                "session-1",
                "spec-1",
                Instant.now(),
                null,
                10,
                1.0,
                0.0,
                1000.0
            )
        );
    }

    /**
     * Test: record equality (same field values).
     */
    @Test
    void equality_sameValues() {
        Instant createdAt = Instant.now();
        Instant analyzedAt = Instant.now();

        ProcessMiningSession s1 = new ProcessMiningSession(
            "session-1",
            "spec-1",
            createdAt,
            analyzedAt,
            10,
            0.9,
            0.8,
            5000.0
        );

        ProcessMiningSession s2 = new ProcessMiningSession(
            "session-1",
            "spec-1",
            createdAt,
            analyzedAt,
            10,
            0.9,
            0.8,
            5000.0
        );

        assertEquals(s1, s2);
    }

    /**
     * Test: record toString is non-empty.
     */
    @Test
    void toString_nonEmpty() {
        ProcessMiningSession session = ProcessMiningSession.start("spec-1");

        String str = session.toString();

        assertNotNull(str);
        assertFalse(str.isEmpty());
        assertTrue(str.contains("session") || str.contains("ProcessMiningSession"));
    }
}
