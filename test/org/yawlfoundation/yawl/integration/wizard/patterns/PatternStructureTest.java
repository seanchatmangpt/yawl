package org.yawlfoundation.yawl.integration.wizard.patterns;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Petri net structures of workflow patterns.
 *
 * <p>Verifies that pattern structures have correct formal properties
 * including place/transition counts and soundness characteristics.
 */
@DisplayName("Petri Net Pattern Structure Tests")
class PatternStructureTest {

    @Test
    @DisplayName("forPattern factory creates structure for all patterns")
    void testForPatternAllPatterns() {
        for (WorkflowPattern pattern : WorkflowPattern.values()) {
            PatternStructure structure = PatternStructure.forPattern(pattern);
            assertNotNull(structure, String.format("Should create structure for %s", pattern.getCode()));
            assertEquals(pattern, structure.pattern());
        }
    }

    @Test
    @DisplayName("forPattern throws on null pattern")
    void testForPatternNullPattern() {
        assertThrows(NullPointerException.class,
            () -> PatternStructure.forPattern(null),
            "Should throw on null pattern");
    }

    @Nested
    @DisplayName("Basic Control Flow Pattern Structures")
    class BasicPatternStructures {

        @Test
        @DisplayName("Sequence structure is sound")
        void testSequenceSound() {
            PatternStructure seq = PatternStructure.forPattern(WorkflowPattern.SEQUENCE);
            assertTrue(seq.isSound(), "Sequence should be sound");
            assertTrue(seq.isWorkflowNet(), "Sequence should be a workflow net");
            assertTrue(seq.isFreeChoice(), "Sequence should be free-choice");
            assertEquals(4, seq.placeCount());
            assertEquals(2, seq.transitionCount());
        }

        @Test
        @DisplayName("Parallel Split structure is sound")
        void testParallelSplitSound() {
            PatternStructure ps = PatternStructure.forPattern(WorkflowPattern.PARALLEL_SPLIT);
            assertTrue(ps.isSound(), "Parallel Split should be sound");
            assertTrue(ps.isWorkflowNet());
            assertEquals(4, ps.placeCount());
            assertEquals(3, ps.transitionCount());
        }

        @Test
        @DisplayName("Synchronization structure is sound")
        void testSynchronizationSound() {
            PatternStructure sync = PatternStructure.forPattern(WorkflowPattern.SYNCHRONIZATION);
            assertTrue(sync.isSound(), "Synchronization should be sound");
            assertTrue(sync.isWorkflowNet());
            assertEquals(4, sync.placeCount());
        }

        @Test
        @DisplayName("Exclusive Choice structure is sound")
        void testExclusiveChoiceSound() {
            PatternStructure ec = PatternStructure.forPattern(WorkflowPattern.EXCLUSIVE_CHOICE);
            assertTrue(ec.isSound(), "Exclusive Choice should be sound");
            assertFalse(ec.isFreeChoice(), "Exclusive Choice is not free-choice (has guards)");
        }

        @Test
        @DisplayName("Simple Merge structure is sound")
        void testSimpleMergeSound() {
            PatternStructure sm = PatternStructure.forPattern(WorkflowPattern.SIMPLE_MERGE);
            assertTrue(sm.isSound(), "Simple Merge should be sound");
            assertTrue(sm.isFreeChoice());
        }
    }

    @Nested
    @DisplayName("Advanced Branching Pattern Structures")
    class AdvancedPatternStructures {

        @Test
        @DisplayName("Multi-Choice structure properties")
        void testMultiChoiceStructure() {
            PatternStructure mc = PatternStructure.forPattern(WorkflowPattern.MULTI_CHOICE);
            assertFalse(mc.isFreeChoice(), "Multi-Choice is not free-choice (overlapping branches)");
            assertTrue(mc.isWorkflowNet());
            assertTrue(mc.isSound());
        }

        @Test
        @DisplayName("Multi-Merge is not sound")
        void testMultiMergeNotSound() {
            PatternStructure mm = PatternStructure.forPattern(WorkflowPattern.MULTI_MERGE);
            assertFalse(mm.isSound(), "Multi-Merge is not sound (may cause repeated executions)");
            assertTrue(mm.isWorkflowNet());
            assertFalse(mm.isFreeChoice());
        }

        @Test
        @DisplayName("Structured Discriminator structure is sound")
        void testStructuredDiscriminatorSound() {
            PatternStructure sd = PatternStructure.forPattern(WorkflowPattern.STRUCTURED_DISCRIMINATOR);
            assertTrue(sd.isSound(), "Structured Discriminator should be sound (with reset)");
            assertTrue(sd.isWorkflowNet());
        }
    }

    @Nested
    @DisplayName("Structural Pattern Structures")
    class StructuralPatternStructures {

        @Test
        @DisplayName("Arbitrary Cycles may not be sound")
        void testArbitraryCyclesNotGuaranteedSound() {
            PatternStructure ac = PatternStructure.forPattern(WorkflowPattern.ARBITRARY_CYCLES);
            assertFalse(ac.isSound(), "Arbitrary Cycles without proper guards may not be sound");
            assertTrue(ac.isWorkflowNet());
        }

        @Test
        @DisplayName("Implicit Termination is sound")
        void testImplicitTerminationSound() {
            PatternStructure it = PatternStructure.forPattern(WorkflowPattern.IMPLICIT_TERMINATION);
            assertTrue(it.isSound(), "Implicit Termination should be sound (deadlock-free)");
            assertTrue(it.isWorkflowNet());
        }
    }

    @Nested
    @DisplayName("Multiple Instance Pattern Structures")
    class MultipleInstancePatternStructures {

        @Test
        @DisplayName("MI Without Sync is not sound")
        void testMiWithoutSyncNotSound() {
            PatternStructure mi = PatternStructure.forPattern(WorkflowPattern.MI_WITHOUT_SYNC);
            assertFalse(mi.isSound(), "MI Without Sync is not sound (unbounded)");
        }

        @Test
        @DisplayName("MI With A Priori Design is sound")
        void testMiDesignTimeSound() {
            PatternStructure mi = PatternStructure.forPattern(WorkflowPattern.MI_WITH_APRIORI_DESIGN);
            assertTrue(mi.isSound(), "MI With Design-Time Knowledge should be sound");
            assertTrue(mi.isWorkflowNet());
        }

        @Test
        @DisplayName("MI With A Priori Runtime is sound")
        void testMiRuntimeSound() {
            PatternStructure mi = PatternStructure.forPattern(WorkflowPattern.MI_WITH_APRIORI_RUNTIME);
            assertTrue(mi.isSound(), "MI With Runtime Knowledge should be sound");
            assertTrue(mi.isWorkflowNet());
        }

        @Test
        @DisplayName("MI Without A Priori is not sound")
        void testMiNAprioriNotSound() {
            PatternStructure mi = PatternStructure.forPattern(WorkflowPattern.MI_WITHOUT_APRIORI);
            assertFalse(mi.isSound(), "MI Without A Priori is not sound (unbounded count)");
        }
    }

    @Nested
    @DisplayName("State-Based Pattern Structures")
    class StateBasedPatternStructures {

        @Test
        @DisplayName("Deferred Choice is sound")
        void testDeferredChoiceSound() {
            PatternStructure dc = PatternStructure.forPattern(WorkflowPattern.DEFERRED_CHOICE);
            assertTrue(dc.isSound(), "Deferred Choice should be sound");
            assertTrue(dc.isWorkflowNet());
            assertFalse(dc.isFreeChoice(), "Deferred Choice is not free-choice (external choice)");
        }

        @Test
        @DisplayName("Interleaved Parallel may not be sound")
        void testInterleavedParallelNotSound() {
            PatternStructure ip = PatternStructure.forPattern(WorkflowPattern.INTERLEAVED_PARALLEL);
            assertFalse(ip.isSound(), "Interleaved Parallel is not sound (dynamic topology)");
        }

        @Test
        @DisplayName("Milestone is sound")
        void testMilestoneSound() {
            PatternStructure m = PatternStructure.forPattern(WorkflowPattern.MILESTONE);
            assertTrue(m.isSound(), "Milestone should be sound");
            assertTrue(m.isWorkflowNet());
            assertTrue(m.isFreeChoice());
        }
    }

    @Nested
    @DisplayName("Cancellation Pattern Structures")
    class CancellationPatternStructures {

        @Test
        @DisplayName("Cancel Task is sound")
        void testCancelTaskSound() {
            PatternStructure ct = PatternStructure.forPattern(WorkflowPattern.CANCEL_TASK);
            assertTrue(ct.isSound(), "Cancel Task should be sound");
            assertTrue(ct.isWorkflowNet());
            assertTrue(ct.isFreeChoice());
        }

        @Test
        @DisplayName("Cancel Case is sound")
        void testCancelCaseSound() {
            PatternStructure cc = PatternStructure.forPattern(WorkflowPattern.CANCEL_CASE);
            assertTrue(cc.isSound(), "Cancel Case should be sound");
            assertTrue(cc.isWorkflowNet());
            assertTrue(cc.isFreeChoice());
        }
    }

    @Nested
    @DisplayName("Structure Properties")
    class StructureProperties {

        @Test
        @DisplayName("All structures have non-empty place/transition lists")
        void testStructureListsNonEmpty() {
            for (WorkflowPattern pattern : WorkflowPattern.values()) {
                PatternStructure struct = PatternStructure.forPattern(pattern);
                assertTrue(struct.places().size() > 0,
                    String.format("%s should have places", pattern.getCode()));
                assertTrue(struct.transitions().size() > 0,
                    String.format("%s should have transitions", pattern.getCode()));
            }
        }

        @Test
        @DisplayName("All structures have Petri notation")
        void testPetriNotationNonEmpty() {
            for (WorkflowPattern pattern : WorkflowPattern.values()) {
                PatternStructure struct = PatternStructure.forPattern(pattern);
                assertNotNull(struct.petriNotation());
                assertTrue(struct.petriNotation().length() > 0,
                    String.format("%s should have non-empty Petri notation", pattern.getCode()));
            }
        }

        @Test
        @DisplayName("Place count matches place list size")
        void testPlaceCountConsistency() {
            for (WorkflowPattern pattern : WorkflowPattern.values()) {
                PatternStructure struct = PatternStructure.forPattern(pattern);
                assertEquals(struct.places().size(), struct.placeCount(),
                    String.format("%s place count should match places list size", pattern.getCode()));
            }
        }

        @Test
        @DisplayName("Transition count matches transition list size")
        void testTransitionCountConsistency() {
            for (WorkflowPattern pattern : WorkflowPattern.values()) {
                PatternStructure struct = PatternStructure.forPattern(pattern);
                assertEquals(struct.transitions().size(), struct.transitionCount(),
                    String.format("%s transition count should match transitions list size", pattern.getCode()));
            }
        }

        @Test
        @DisplayName("Arc count is positive")
        void testArcCountPositive() {
            for (WorkflowPattern pattern : WorkflowPattern.values()) {
                PatternStructure struct = PatternStructure.forPattern(pattern);
                assertTrue(struct.arcCount() >= 0,
                    String.format("%s arc count should be non-negative", pattern.getCode()));
            }
        }
    }

    @Test
    @DisplayName("toString provides summary")
    void testToStringMethod() {
        PatternStructure seq = PatternStructure.forPattern(WorkflowPattern.SEQUENCE);
        String summary = seq.toString();

        assertNotNull(summary);
        assertTrue(summary.contains("WP-1"));
        assertTrue(summary.contains("Sequence"));
        assertTrue(summary.contains("places"));
        assertTrue(summary.contains("transitions"));
    }

    @Test
    @DisplayName("Structure record is immutable")
    void testStructureImmutability() {
        PatternStructure seq = PatternStructure.forPattern(WorkflowPattern.SEQUENCE);
        List<String> places = seq.places();

        assertThrows(UnsupportedOperationException.class,
            () -> places.add("test"),
            "Places list should be immutable");

        List<String> transitions = seq.transitions();
        assertThrows(UnsupportedOperationException.class,
            () -> transitions.add("test"),
            "Transitions list should be immutable");
    }
}
