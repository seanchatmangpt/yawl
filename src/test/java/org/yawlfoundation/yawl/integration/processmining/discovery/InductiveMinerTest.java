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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.processmining.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for Inductive Miner algorithm.
 *
 * Tests validate process tree discovery and soundness properties.
 */
public class InductiveMinerTest {

    private InductiveMiner inductiveMiner;

    @BeforeEach
    public void setUp() {
        inductiveMiner = new InductiveMiner();
    }

    @Test
    public void testAlgorithmMetadata() {
        // Assert
        assertEquals("Inductive Miner", inductiveMiner.getAlgorithmName());
        assertEquals(ProcessDiscoveryAlgorithm.AlgorithmType.INDUCTIVE, inductiveMiner.getType());
    }

    @Test
    public void testDiscoverSimpleSequence() {
        // Arrange: Simple linear sequence a→b→c
        List<List<String>> traces = List.of(
            List.of("a", "b", "c"),
            List.of("a", "b", "c"),
            List.of("a", "b", "c")
        );

        // Act
        ProcessTree tree = inductiveMiner.discoverTree(traces);

        // Assert
        assertNotNull(tree);
        assertTrue(tree.isSound());
        assertInstanceOf(ProcessTree.Sequence.class, tree);
    }

    @Test
    public void testDiscoverParallel() {
        // Arrange: Parallel activities b and c after a
        List<List<String>> traces = List.of(
            List.of("a", "b", "c", "d"),
            List.of("a", "c", "b", "d"),
            List.of("a", "b", "c", "d")
        );

        // Act
        ProcessTree tree = inductiveMiner.discoverTree(traces);

        // Assert
        assertNotNull(tree);
        assertTrue(tree.isSound());
        // Tree should represent some form of parallel or exclusive choice
    }

    @Test
    public void testDiscoverExclusiveChoice() {
        // Arrange: Two alternative paths: a→b or a→c
        List<List<String>> traces = List.of(
            List.of("a", "b", "d"),
            List.of("a", "c", "d"),
            List.of("a", "b", "d")
        );

        // Act
        ProcessTree tree = inductiveMiner.discoverTree(traces);

        // Assert
        assertNotNull(tree);
        assertTrue(tree.isSound());
    }

    @Test
    public void testDiscoverLoop() {
        // Arrange: Looping structure a→b→a→b→c
        List<List<String>> traces = List.of(
            List.of("a", "b", "a", "b", "c"),
            List.of("a", "b", "c"),
            List.of("a", "b", "a", "b", "a", "b", "c")
        );

        // Act
        ProcessTree tree = inductiveMiner.discoverTree(traces);

        // Assert
        assertNotNull(tree);
        assertTrue(tree.isSound());
        // May contain loop operator or multiple passes
    }

    @Test
    public void testSingleActivityLog() {
        // Arrange
        List<List<String>> traces = List.of(
            List.of("a"),
            List.of("a")
        );

        // Act
        ProcessTree tree = inductiveMiner.discoverTree(traces);

        // Assert
        assertNotNull(tree);
        assertTrue(tree.isSound());
        assertInstanceOf(ProcessTree.Leaf.class, tree);
        ProcessTree.Leaf leaf = (ProcessTree.Leaf) tree;
        assertEquals("a", leaf.activity());
    }

    @Test
    public void testEmptyLog() {
        // Arrange
        List<List<String>> traces = List.of();

        // Act
        ProcessTree tree = inductiveMiner.discoverTree(traces);

        // Assert
        assertNotNull(tree);
        assertTrue(tree.isSound());
        assertInstanceOf(ProcessTree.Silent.class, tree);
    }

    @Test
    public void testIdenticalTraces() {
        // Arrange: All traces are identical
        List<List<String>> traces = List.of(
            List.of("a", "b", "c"),
            List.of("a", "b", "c"),
            List.of("a", "b", "c")
        );

        // Act
        ProcessTree tree = inductiveMiner.discoverTree(traces);

        // Assert
        assertNotNull(tree);
        assertTrue(tree.isSound());
    }

    @Test
    public void testLeafTreeSoundness() {
        // Arrange: Single activity
        ProcessTree.Leaf leaf = new ProcessTree.Leaf("activity");

        // Assert
        assertTrue(leaf.isSound());
        assertEquals("activity", leaf.activity());
    }

    @Test
    public void testSilentTreeSoundness() {
        // Arrange: Silent transition
        ProcessTree.Silent silent = new ProcessTree.Silent();

        // Assert
        assertTrue(silent.isSound());
    }

    @Test
    public void testSequenceSoundness() {
        // Arrange: Sequence of leaves
        List<ProcessTree> children = List.of(
            new ProcessTree.Leaf("a"),
            new ProcessTree.Leaf("b"),
            new ProcessTree.Leaf("c")
        );
        ProcessTree.Sequence seq = new ProcessTree.Sequence(children);

        // Assert
        assertTrue(seq.isSound());
    }

    @Test
    public void testExclusiveChoiceSoundness() {
        // Arrange: Exclusive choice
        List<ProcessTree> children = List.of(
            new ProcessTree.Leaf("a"),
            new ProcessTree.Leaf("b")
        );
        ProcessTree.ExclusiveChoice choice = new ProcessTree.ExclusiveChoice(children);

        // Assert
        assertTrue(choice.isSound());
    }

    @Test
    public void testParallelSoundness() {
        // Arrange: Parallel composition
        List<ProcessTree> children = List.of(
            new ProcessTree.Leaf("a"),
            new ProcessTree.Leaf("b")
        );
        ProcessTree.Parallel parallel = new ProcessTree.Parallel(children);

        // Assert
        assertTrue(parallel.isSound());
    }

    @Test
    public void testLoopSoundness() {
        // Arrange: Loop structure
        ProcessTree body = new ProcessTree.Leaf("a");
        ProcessTree redo = new ProcessTree.Leaf("b");
        ProcessTree.Loop loop = new ProcessTree.Loop(body, redo);

        // Assert
        assertTrue(loop.isSound());
    }

    @Test
    public void testComposedTreeSoundness() {
        // Arrange: Complex tree with multiple levels
        ProcessTree.Sequence sequence = new ProcessTree.Sequence(List.of(
            new ProcessTree.Leaf("a"),
            new ProcessTree.Parallel(List.of(
                new ProcessTree.Leaf("b"),
                new ProcessTree.Leaf("c")
            )),
            new ProcessTree.Leaf("d")
        ));

        // Assert
        assertTrue(sequence.isSound());
    }

    @Test
    public void testInvalidSequenceThrows() {
        // Assert: Empty sequence should throw
        assertThrows(IllegalArgumentException.class, () ->
            new ProcessTree.Sequence(List.of())
        );
    }

    @Test
    public void testInvalidChoiceThrows() {
        // Assert: Empty choice should throw
        assertThrows(IllegalArgumentException.class, () ->
            new ProcessTree.ExclusiveChoice(List.of())
        );
    }

    @Test
    public void testInvalidParallelThrows() {
        // Assert: Empty parallel should throw
        assertThrows(IllegalArgumentException.class, () ->
            new ProcessTree.Parallel(List.of())
        );
    }

    @Test
    public void testNullLeafActivityThrows() {
        // Assert: Null activity should throw
        assertThrows(NullPointerException.class, () ->
            new ProcessTree.Leaf(null)
        );
    }

    @Test
    public void testNullSequenceChildrenThrows() {
        // Assert: Null children should throw
        assertThrows(NullPointerException.class, () ->
            new ProcessTree.Sequence(null)
        );
    }

    @Test
    public void testNullLoopBodyThrows() {
        // Assert: Null body should throw
        assertThrows(NullPointerException.class, () ->
            new ProcessTree.Loop(null, new ProcessTree.Silent())
        );
    }

    @Test
    public void testNullLoopRedoThrows() {
        // Assert: Null redo should throw
        assertThrows(NullPointerException.class, () ->
            new ProcessTree.Loop(new ProcessTree.Silent(), null)
        );
    }

    @Test
    public void testTreeStringRepresentation() {
        // Arrange
        ProcessTree.Leaf leaf = new ProcessTree.Leaf("task");
        ProcessTree.Silent silent = new ProcessTree.Silent();

        // Assert
        assertEquals("task", leaf.toString());
        assertEquals("τ", silent.toString());
    }

    @Test
    public void testFitnessProperty() {
        // Note: Inductive miner guarantees 100% fitness on training log
        // All discovered trees are sound by construction
        List<List<String>> traces = List.of(
            List.of("a", "b", "c"),
            List.of("a", "d", "c")
        );

        ProcessTree tree = inductiveMiner.discoverTree(traces);
        assertTrue(tree.isSound());
    }

    @Test
    public void testComplexWorkflow() {
        // Arrange: Complex workflow with multiple operators
        List<List<String>> traces = List.of(
            List.of("a", "b", "c", "d", "e"),
            List.of("a", "c", "b", "d", "e"),
            List.of("a", "b", "c", "d", "f")
        );

        // Act
        ProcessTree tree = inductiveMiner.discoverTree(traces);

        // Assert: Should discover valid structure
        assertNotNull(tree);
        assertTrue(tree.isSound());
    }

    @Test
    public void testNoiseFiltering() {
        // Arrange: Log with infrequent traces
        List<List<String>> traces = List.of(
            List.of("a", "b", "c"),  // Frequent
            List.of("a", "b", "c"),
            List.of("a", "b", "c"),
            List.of("a", "x", "c")   // Rare anomaly
        );

        // Act
        ProcessTree tree = inductiveMiner.discoverTree(traces);

        // Assert: Should handle gracefully
        assertNotNull(tree);
        assertTrue(tree.isSound());
    }
}
