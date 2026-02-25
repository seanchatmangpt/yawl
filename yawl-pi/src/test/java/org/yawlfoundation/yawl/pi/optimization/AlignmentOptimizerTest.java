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

package org.yawlfoundation.yawl.pi.optimization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AlignmentOptimizer.
 *
 * Tests verify that the Levenshtein distance algorithm correctly computes
 * alignment between observed and reference activity sequences.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
class AlignmentOptimizerTest {

    private AlignmentOptimizer optimizer;

    @BeforeEach
    void setUp() {
        optimizer = new AlignmentOptimizer();
    }

    @Test
    void testAlign_IdenticalSequences() {
        List<String> observed = List.of("A", "B", "C");
        List<String> reference = List.of("A", "B", "C");

        AlignmentResult result = optimizer.align(observed, reference);

        assertNotNull(result);
        assertEquals(3, result.synchronousMoves());
        assertEquals(0, result.moveOnLogMoves());
        assertEquals(0, result.moveOnModelMoves());
        assertEquals(0.0, result.alignmentCost());
        assertEquals(1.0, result.fitnessDelta(), 0.01);
    }

    @Test
    void testAlign_CompletelyDifferentSequences() {
        List<String> observed = List.of("A", "B");
        List<String> reference = List.of("X", "Y");

        AlignmentResult result = optimizer.align(observed, reference);

        assertNotNull(result);
        assertEquals(0, result.synchronousMoves());
        assertTrue(result.moveOnLogMoves() > 0 || result.moveOnModelMoves() > 0);
        assertTrue(result.alignmentCost() > 0);
    }

    @Test
    void testAlign_OneMissingActivity() {
        List<String> observed = List.of("A", "B");
        List<String> reference = List.of("A", "B", "C");

        AlignmentResult result = optimizer.align(observed, reference);

        assertNotNull(result);
        assertEquals(2, result.synchronousMoves());
        assertEquals(0, result.moveOnLogMoves());
        assertEquals(1, result.moveOnModelMoves());
        assertEquals(1.0, result.alignmentCost());
    }

    @Test
    void testAlign_OneExtraActivity() {
        List<String> observed = List.of("A", "B", "C");
        List<String> reference = List.of("A", "B");

        AlignmentResult result = optimizer.align(observed, reference);

        assertNotNull(result);
        assertEquals(2, result.synchronousMoves());
        assertEquals(1, result.moveOnLogMoves());
        assertEquals(0, result.moveOnModelMoves());
        assertEquals(1.0, result.alignmentCost());
    }

    @Test
    void testAlign_EmptyObservedSequence() {
        List<String> observed = List.of();
        List<String> reference = List.of("A", "B", "C");

        AlignmentResult result = optimizer.align(observed, reference);

        assertNotNull(result);
        assertEquals(0, result.synchronousMoves());
        assertEquals(0, result.moveOnLogMoves());
        assertEquals(3, result.moveOnModelMoves());
    }

    @Test
    void testAlign_EmptyReferenceSequence() {
        List<String> observed = List.of("A", "B", "C");
        List<String> reference = List.of();

        AlignmentResult result = optimizer.align(observed, reference);

        assertNotNull(result);
        assertEquals(0, result.synchronousMoves());
        assertEquals(3, result.moveOnLogMoves());
        assertEquals(0, result.moveOnModelMoves());
    }

    @Test
    void testAlign_BothEmptySequences() {
        List<String> observed = List.of();
        List<String> reference = List.of();

        AlignmentResult result = optimizer.align(observed, reference);

        assertNotNull(result);
        assertEquals(0, result.synchronousMoves());
        assertEquals(0, result.moveOnLogMoves());
        assertEquals(0, result.moveOnModelMoves());
        assertEquals(0.0, result.alignmentCost());
    }

    @Test
    void testAlign_NullObservedSequence() {
        AlignmentResult result = optimizer.align(null, List.of("A", "B"));

        assertNotNull(result);
        assertEquals(2, result.moveOnModelMoves());
    }

    @Test
    void testAlign_NullReferenceSequence() {
        AlignmentResult result = optimizer.align(List.of("A", "B"), null);

        assertNotNull(result);
        assertEquals(2, result.moveOnLogMoves());
    }

    @Test
    void testAlign_SubstitutionCost() {
        List<String> observed = List.of("A", "X", "C");
        List<String> reference = List.of("A", "B", "C");

        AlignmentResult result = optimizer.align(observed, reference);

        assertNotNull(result);
        // Substitution should be counted as a move
        assertTrue(result.alignmentCost() > 0);
    }

    @Test
    void testAlign_PartialOverlap() {
        List<String> observed = List.of("A", "B", "C", "D");
        List<String> reference = List.of("B", "C");

        AlignmentResult result = optimizer.align(observed, reference);

        assertNotNull(result);
        // Should have at least 2 synchronous moves (B, C)
        assertTrue(result.synchronousMoves() >= 2);
    }

    @Test
    void testAlign_ResultIncludesSequences() {
        List<String> observed = List.of("A", "B");
        List<String> reference = List.of("X", "Y");

        AlignmentResult result = optimizer.align(observed, reference);

        assertEquals(observed, result.observedActivities());
        assertEquals(reference, result.referenceActivities());
    }

    @Test
    void testAlign_FitnessDeltaRange() {
        List<String> observed = List.of("A", "B", "C");
        List<String> reference = List.of("A", "B", "C");

        AlignmentResult result = optimizer.align(observed, reference);

        assertTrue(result.fitnessDelta() >= 0.0);
        assertTrue(result.fitnessDelta() <= 1.0);
    }

    @Test
    void testAlign_LongerSequences() {
        List<String> observed = List.of("Start", "ApprovalTask", "CheckData", "SendEmail", "End");
        List<String> reference = List.of("Start", "ApprovalTask", "End");

        AlignmentResult result = optimizer.align(observed, reference);

        assertNotNull(result);
        // Start, ApprovalTask, End should align
        assertTrue(result.synchronousMoves() >= 2);
        assertTrue(result.moveOnLogMoves() > 0);  // CheckData and SendEmail
    }
}
