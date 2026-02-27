package org.yawlfoundation.yawl.integration.wizard.patterns;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Formal Petri net structure for a workflow pattern.
 *
 * <p>Captures the structural topology of places, transitions, and arcs
 * enabling formal analysis including soundness verification.
 *
 * <p>Follows van der Aalst's workflow net theory: a net is sound if it is
 * free from deadlock, livelock, and implicit termination violations.
 *
 * @param pattern the workflow pattern
 * @param placeCount the number of places in the net
 * @param transitionCount the number of transitions in the net
 * @param arcCount the number of directed arcs
 * @param isFreeChoice whether the net satisfies the free-choice property
 * @param isWorkflowNet whether the net is a valid workflow net (single source/sink place)
 * @param isSound whether the net satisfies soundness criterion
 * @param places immutable list of place names
 * @param transitions immutable list of transition names
 * @param petriNotation compact textual Petri net representation
 */
public record PatternStructure(
    WorkflowPattern pattern,
    int placeCount,
    int transitionCount,
    int arcCount,
    boolean isFreeChoice,
    boolean isWorkflowNet,
    boolean isSound,
    List<String> places,
    List<String> transitions,
    String petriNotation
) {
    /**
     * Compact constructor ensures immutability of lists.
     */
    public PatternStructure {
        Objects.requireNonNull(pattern, "pattern cannot be null");
        Objects.requireNonNull(places, "places cannot be null");
        Objects.requireNonNull(transitions, "transitions cannot be null");
        Objects.requireNonNull(petriNotation, "petriNotation cannot be null");

        places = Collections.unmodifiableList(List.copyOf(places));
        transitions = Collections.unmodifiableList(List.copyOf(transitions));
    }

    /**
     * Factory method: creates a PatternStructure for a given workflow pattern.
     *
     * <p>Each pattern has a predefined formal Petri net structure based on
     * van der Aalst's pattern definitions.
     *
     * @param pattern the workflow pattern
     * @return the Petri net structure for that pattern
     * @throws NullPointerException if pattern is null
     */
    public static PatternStructure forPattern(WorkflowPattern pattern) {
        Objects.requireNonNull(pattern, "pattern cannot be null");

        return switch (pattern) {
            case SEQUENCE -> sequenceStructure();
            case PARALLEL_SPLIT -> parallelSplitStructure();
            case SYNCHRONIZATION -> synchronizationStructure();
            case EXCLUSIVE_CHOICE -> exclusiveChoiceStructure();
            case SIMPLE_MERGE -> simpleMergeStructure();
            case MULTI_CHOICE -> multiChoiceStructure();
            case STRUCTURED_SYNC_MERGE -> structuredSyncMergeStructure();
            case MULTI_MERGE -> multiMergeStructure();
            case STRUCTURED_DISCRIMINATOR -> structuredDiscriminatorStructure();
            case ARBITRARY_CYCLES -> arbitraryCyclesStructure();
            case IMPLICIT_TERMINATION -> implicitTerminationStructure();
            case MI_WITHOUT_SYNC -> miWithoutSyncStructure();
            case MI_WITH_APRIORI_DESIGN -> miWithAprioriDesignStructure();
            case MI_WITH_APRIORI_RUNTIME -> miWithAprioriRuntimeStructure();
            case MI_WITHOUT_APRIORI -> miWithoutAprioriStructure();
            case DEFERRED_CHOICE -> deferredChoiceStructure();
            case INTERLEAVED_PARALLEL -> interleavedParallelStructure();
            case MILESTONE -> milestoneStructure();
            case CANCEL_TASK -> cancelTaskStructure();
            case CANCEL_CASE -> cancelCaseStructure();
        };
    }

    private static PatternStructure sequenceStructure() {
        return new PatternStructure(
            WorkflowPattern.SEQUENCE,
            4,  // p_start, p_task1_complete, p_task2_complete, p_end
            2,  // t_task1, t_task2
            4,  // arcs
            true,  // free-choice
            true,  // workflow net
            true,  // sound
            List.of("p_start", "p_task1_complete", "p_task2_complete", "p_end"),
            List.of("t_task1", "t_task2"),
            "p_start → t_task1 → p_task1_complete → t_task2 → p_end"
        );
    }

    private static PatternStructure parallelSplitStructure() {
        return new PatternStructure(
            WorkflowPattern.PARALLEL_SPLIT,
            4,  // p_start, p_branch1, p_branch2, p_join_point
            3,  // t_split, t_branch1, t_branch2
            5,
            true,  // free-choice
            true,  // workflow net
            true,  // sound
            List.of("p_start", "p_branch1", "p_branch2", "p_join_point"),
            List.of("t_split", "t_branch1_work", "t_branch2_work"),
            "p_start → t_split → {p_branch1, p_branch2}"
        );
    }

    private static PatternStructure synchronizationStructure() {
        return new PatternStructure(
            WorkflowPattern.SYNCHRONIZATION,
            4,  // p_branch1, p_branch2, p_sync_input, p_end
            2,  // t_work1, t_work2, t_join
            5,
            true,  // free-choice
            true,  // workflow net
            true,  // sound
            List.of("p_branch1", "p_branch2", "p_sync_input", "p_end"),
            List.of("t_work1", "t_work2", "t_join"),
            "{p_branch1, p_branch2} → t_join → p_end"
        );
    }

    private static PatternStructure exclusiveChoiceStructure() {
        return new PatternStructure(
            WorkflowPattern.EXCLUSIVE_CHOICE,
            4,  // p_start, p_branch1, p_branch2, p_merge
            3,  // t_choice, t_branch1_work, t_branch2_work
            5,
            false,  // not free-choice (guards create dependencies)
            true,  // workflow net
            true,  // sound
            List.of("p_start", "p_branch1", "p_branch2", "p_merge"),
            List.of("t_choice[g1|g2]", "t_branch1_work", "t_branch2_work"),
            "p_start → t_choice[guard_1|guard_2] → {p_branch1, p_branch2}"
        );
    }

    private static PatternStructure simpleMergeStructure() {
        return new PatternStructure(
            WorkflowPattern.SIMPLE_MERGE,
            4,  // p_branch1, p_branch2, p_merge_input, p_end
            3,  // t_work1, t_work2, t_merge
            5,
            true,  // free-choice
            true,  // workflow net
            true,  // sound
            List.of("p_branch1", "p_branch2", "p_merge_input", "p_end"),
            List.of("t_work1", "t_work2", "t_merge"),
            "{p_branch1, p_branch2} → t_merge[non-exclusive] → p_end"
        );
    }

    private static PatternStructure multiChoiceStructure() {
        return new PatternStructure(
            WorkflowPattern.MULTI_CHOICE,
            5,  // p_start, p_branch1, p_branch2, p_branch3, p_merge
            4,  // t_multichoice, t_work1, t_work2, t_work3
            7,
            false,  // not free-choice (overlapping branches)
            true,  // workflow net
            true,  // sound (with appropriate join)
            List.of("p_start", "p_branch1", "p_branch2", "p_branch3", "p_merge"),
            List.of("t_multichoice[g1,g2,g3]", "t_work1", "t_work2", "t_work3"),
            "p_start → t_multichoice[g1∧g2 | g1∧g3 | g2∧g3 | ...] → {p_branch_i}"
        );
    }

    private static PatternStructure structuredSyncMergeStructure() {
        return new PatternStructure(
            WorkflowPattern.STRUCTURED_SYNC_MERGE,
            6,  // p_branch1, p_branch2, p_branch3, p_merge, p_tracking, p_end
            4,  // t_work1, t_work2, t_work3, t_structured_sync
            8,
            false,  // not free-choice
            true,  // workflow net
            true,  // sound
            List.of("p_branch1", "p_branch2", "p_branch3", "p_tracking", "p_merge", "p_end"),
            List.of("t_work1", "t_work2", "t_work3", "t_structured_sync"),
            "{p_branch_i} + tracking → t_structured_sync[cardinality=N] → p_end"
        );
    }

    private static PatternStructure multiMergeStructure() {
        return new PatternStructure(
            WorkflowPattern.MULTI_MERGE,
            4,  // p_branch1, p_branch2, p_task, p_end
            3,  // t_work1, t_work2, t_merge_task
            5,
            false,  // not free-choice
            true,  // workflow net
            false,  // not sound (may cause multiple executions per branch)
            List.of("p_branch1", "p_branch2", "p_task", "p_end"),
            List.of("t_work1", "t_work2", "t_merge_task"),
            "{p_branch_i} → t_merge_task[fire per input] (no join semantics)"
        );
    }

    private static PatternStructure structuredDiscriminatorStructure() {
        return new PatternStructure(
            WorkflowPattern.STRUCTURED_DISCRIMINATOR,
            5,  // p_branch1, p_branch2, p_tracking, p_winner, p_end
            4,  // t_work1, t_work2, t_discriminate, t_reset
            7,
            false,  // not free-choice
            true,  // workflow net
            true,  // sound (with reset)
            List.of("p_branch1", "p_branch2", "p_tracking", "p_winner", "p_end"),
            List.of("t_work1", "t_work2", "t_discriminate", "t_reset_losers"),
            "{p_branch_i} + tracking → t_discriminate[first_wins] → p_end; t_reset[cancel others]"
        );
    }

    private static PatternStructure arbitraryCyclesStructure() {
        return new PatternStructure(
            WorkflowPattern.ARBITRARY_CYCLES,
            5,  // p_start, p_loop_body, p_test, p_exit, p_end
            4,  // t_enter_loop, t_body, t_loop_back, t_exit
            7,
            true,  // free-choice (simple loops)
            true,  // workflow net
            false,  // may not be sound without proper guards
            List.of("p_start", "p_loop_body", "p_test", "p_exit", "p_end"),
            List.of("t_enter_loop", "t_body", "t_loop_back", "t_exit"),
            "p_start → t_enter → {p_loop_body → t_body → p_test → t_loop_back → p_loop_body | t_exit → p_end}"
        );
    }

    private static PatternStructure implicitTerminationStructure() {
        return new PatternStructure(
            WorkflowPattern.IMPLICIT_TERMINATION,
            4,  // p_start, p_task1, p_task2, p_end
            3,  // t_start, t_task1, t_task2
            4,
            true,  // free-choice
            true,  // workflow net
            true,  // sound (deadlock-free)
            List.of("p_start", "p_task1", "p_task2", "p_end"),
            List.of("t_start", "t_task1", "t_task2"),
            "p_start → {t_task1 → p_end, t_task2 → p_end} (no explicit termination)"
        );
    }

    private static PatternStructure miWithoutSyncStructure() {
        return new PatternStructure(
            WorkflowPattern.MI_WITHOUT_SYNC,
            3,  // p_mi_start, p_instance, p_end
            2,  // t_spawn, t_execute
            4,
            true,  // free-choice
            true,  // workflow net (in a generalized sense)
            false,  // not sound (unbounded execution)
            List.of("p_mi_start", "p_instance_i", "p_end"),
            List.of("t_spawn[N]", "t_execute_i"),
            "p_mi_start →* {t_instance_1, t_instance_2, ..., t_instance_N} →* p_end (no join)"
        );
    }

    private static PatternStructure miWithAprioriDesignStructure() {
        return new PatternStructure(
            WorkflowPattern.MI_WITH_APRIORI_DESIGN,
            4,  // p_start, p_instance_i, p_join_gate, p_end
            3,  // t_spawn, t_instance_i, t_join
            5,
            true,  // free-choice
            true,  // workflow net
            true,  // sound (known number of instances)
            List.of("p_start", "p_instance_i[1..N]", "p_join_gate", "p_end"),
            List.of("t_spawn[N at design-time]", "t_instance_execute_i", "t_join[N-fold]"),
            "p_start → t_spawn[N] →* {t_instance_1...t_instance_N} → t_join[N-fold] → p_end"
        );
    }

    private static PatternStructure miWithAprioriRuntimeStructure() {
        return new PatternStructure(
            WorkflowPattern.MI_WITH_APRIORI_RUNTIME,
            5,  // p_start, p_count_decision, p_instance_i, p_join_gate, p_end
            4,  // t_count, t_spawn, t_instance_i, t_join
            6,
            true,  // free-choice
            true,  // workflow net
            true,  // sound (count determined before instantiation)
            List.of("p_start", "p_count_data", "p_instance_i", "p_join_gate", "p_end"),
            List.of("t_count_decision[N from data]", "t_spawn[N]", "t_instance_execute_i", "t_join[N-fold]"),
            "p_start → t_count_decision[N from data] → t_spawn[N] →* {t_instance_i} → t_join → p_end"
        );
    }

    private static PatternStructure miWithoutAprioriStructure() {
        return new PatternStructure(
            WorkflowPattern.MI_WITHOUT_APRIORI,
            4,  // p_start, p_loop, p_instance, p_end
            4,  // t_check, t_spawn, t_execute, t_exit
            6,
            false,  // not free-choice
            true,  // workflow net
            false,  // not sound (unbounded, count unknown)
            List.of("p_start", "p_loop_check", "p_instance_i", "p_end"),
            List.of("t_check[more?]", "t_spawn", "t_execute_i", "t_exit[no_more]"),
            "p_start → loop{ t_check[more?] → t_spawn → t_execute_i } → t_exit → p_end (count unknown)"
        );
    }

    private static PatternStructure deferredChoiceStructure() {
        return new PatternStructure(
            WorkflowPattern.DEFERRED_CHOICE,
            4,  // p_path1, p_path2, p_chosen, p_end
            3,  // t_message1, t_message2, t_receive
            5,
            false,  // not free-choice (external choice)
            true,  // workflow net
            true,  // sound
            List.of("p_path1", "p_path2", "p_chosen", "p_end"),
            List.of("t_message1[external]", "t_message2[external]", "t_continue"),
            "{p_path_i} → {t_message_i[external]} (first wins, others cancelled)"
        );
    }

    private static PatternStructure interleavedParallelStructure() {
        return new PatternStructure(
            WorkflowPattern.INTERLEAVED_PARALLEL,
            5,  // p_active_threads, p_thread1, p_thread2, p_thread3, p_end
            5,  // t_open, t_close, t_work1, t_work2, t_work3
            8,
            false,  // not free-choice
            true,  // workflow net
            false,  // not sound (dynamic topology)
            List.of("p_active_threads", "p_thread1", "p_thread2", "p_thread3", "p_end"),
            List.of("t_open[dynamic]", "t_work_i", "t_close[dynamic]"),
            "{p_active_threads} → {t_open, t_close}* (dynamic parallelism)"
        );
    }

    private static PatternStructure milestoneStructure() {
        return new PatternStructure(
            WorkflowPattern.MILESTONE,
            4,  // p_milestone, p_task_enabled, p_end
            2,  // t_reach_milestone, t_task
            4,
            true,  // free-choice
            true,  // workflow net
            true,  // sound
            List.of("p_start", "p_milestone", "p_task_enabled", "p_end"),
            List.of("t_reach_milestone", "t_task[enabled_by_milestone]"),
            "p_start → t_reach_milestone → p_milestone; p_milestone →* p_task_enabled → t_task"
        );
    }

    private static PatternStructure cancelTaskStructure() {
        return new PatternStructure(
            WorkflowPattern.CANCEL_TASK,
            4,  // p_task_running, p_task_cancelled, p_cleanup, p_end
            3,  // t_run, t_cancel_event, t_cleanup
            5,
            true,  // free-choice
            true,  // workflow net
            true,  // sound
            List.of("p_task_running", "p_task_cancelled", "p_cleanup", "p_end"),
            List.of("t_run", "t_cancel_event[external]", "t_cleanup"),
            "p_task_running → {t_cancel_event | t_normal_complete}; t_cancel_event → p_cleanup → p_end"
        );
    }

    private static PatternStructure cancelCaseStructure() {
        return new PatternStructure(
            WorkflowPattern.CANCEL_CASE,
            4,  // p_active_tasks, p_case_cancelled, p_cleanup, p_end
            3,  // t_run, t_cancel_event, t_cleanup_all
            5,
            true,  // free-choice (at abort point)
            true,  // workflow net
            true,  // sound (cancellation reaches terminal)
            List.of("p_active_tasks", "p_case_cancelled", "p_cleanup_all", "p_case_end"),
            List.of("t_tasks[active]", "t_cancel_case[external]", "t_cleanup_all"),
            "{p_active_tasks} → t_cancel_case → p_cleanup_all → p_case_end (terminal)"
        );
    }

    /**
     * Checks if this Petri net is sound (free from deadlock, livelock, and improper termination).
     *
     * @return true if the net satisfies soundness criterion
     */
    public boolean isSound() {
        return isSound;
    }

    /**
     * Gets a human-readable summary of the structure.
     *
     * @return summary string
     */
    @Override
    public String toString() {
        return String.format(
            "%s (%s): %d places, %d transitions, %d arcs | Free-choice: %s, Workflow net: %s, Sound: %s",
            pattern.getLabel(),
            pattern.getCode(),
            placeCount,
            transitionCount,
            arcCount,
            isFreeChoice,
            isWorkflowNet,
            isSound
        );
    }
}
