package org.yawlfoundation.yawl.integration.wizard.patterns;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

/**
 * Van der Aalst's 43 fundamental workflow control flow patterns.
 *
 * <p>Represents all workflow patterns from the seminal papers:
 * van der Aalst, W.M.P., ter Hofstede, A.H.M., Kiepuszewski, B.,
 * Barros, A.P. (2003). "Workflow Patterns." Distributed and Parallel Databases, 14, 5–51.
 * van der Aalst, W.M.P., ter Hofstede, A.H.M. (2006). "Workflow Patterns: The Definitive Guide."
 *
 * <p>Includes:
 * <ul>
 *   <li>WP-1 to WP-20: Basic, Advanced, Structural, Multiple Instance, State-Based, Cancellation patterns</li>
 *   <li>WCP-21 to WCP-43: Extended patterns including Iteration, Triggers, Advanced Cancellation, Synchronisation</li>
 * </ul>
 *
 * <p>Each pattern is annotated with suitability scores for MCP tool orchestration
 * (0-10 scale) and A2A agent coordination, reflecting how naturally each pattern
 * maps to agent-to-agent protocol semantics.
 *
 * <p>Petri net structures are provided for each pattern, enabling soundness
 * verification and formal analysis per van der Aalst's workflow net theory.
 *
 * @see PatternCategory for pattern groupings
 * @see PatternStructure for formal Petri net representation
 */
public enum WorkflowPattern {
    // Basic Control Flow Patterns (WP-1 to WP-5)

    /**
     * WP-1: Sequence.
     * A task is followed by another task. Simplest composition operator.
     * Petri net: place-transition-place linear chain.
     */
    SEQUENCE(
        "WP-1",
        "Sequence",
        PatternCategory.BASIC,
        "A task is followed by another task in sequential order. "
            + "Simplest fundamental pattern for composing workflow steps.",
        9,  // MCP suitability: excellent (sequential tool calls)
        9,  // A2A suitability: excellent (agent handoff sequence)
        "Place-transition net with linear chain: p_start → t_task_1 → p_task_1_complete → t_task_2 → p_end"
    ),

    /**
     * WP-2: Parallel Split (AND-split).
     * One incoming branch splits into multiple parallel branches.
     * Petri net: transition with multiple outgoing arcs.
     */
    PARALLEL_SPLIT(
        "WP-2",
        "Parallel Split (AND-split)",
        PatternCategory.BASIC,
        "One incoming flow path splits into multiple branches that execute concurrently. "
            + "All outgoing branches are activated upon incoming transition firing.",
        8,  // MCP suitability: very good (parallel tool orchestration)
        8,  // A2A suitability: very good (parallel agent tasks)
        "Place-transition net: p_start → t_split → {p_branch_1, p_branch_2, ..., p_branch_n}"
    ),

    /**
     * WP-3: Synchronization (AND-join).
     * Multiple incoming branches join into a single outgoing branch.
     * Petri net: multiple input places to transition (requires all tokens).
     */
    SYNCHRONIZATION(
        "WP-3",
        "Synchronization (AND-join)",
        PatternCategory.BASIC,
        "Multiple incoming branches converge and synchronize. "
            + "Outgoing branch fires only when all incoming branches complete.",
        8,  // MCP suitability: very good (wait for all tool completions)
        8,  // A2A suitability: very good (agent coordination barrier)
        "Place-transition net: {p_branch_1, p_branch_2, ..., p_branch_n} → t_join → p_end"
    ),

    /**
     * WP-4: Exclusive Choice (XOR-split).
     * Based on a condition, exactly one of multiple branches is taken.
     * Petri net: transition with conditional guard expressions.
     */
    EXCLUSIVE_CHOICE(
        "WP-4",
        "Exclusive Choice (XOR-split)",
        PatternCategory.BASIC,
        "Based on a condition, exactly one of multiple outgoing branches is selected. "
            + "Each branch has a guard condition; only the true guard branch is taken.",
        7,  // MCP suitability: good (conditional tool selection)
        7,  // A2A suitability: good (agent routing based on conditions)
        "Place-transition net: p_start → t_choice[guard_1 | guard_2 | ...] → {p_branch_1, p_branch_2, ...}"
    ),

    /**
     * WP-5: Simple Merge (XOR-join).
     * Multiple incoming branches converge without explicit synchronization.
     * Petri net: multiple input places, non-blocking merging.
     */
    SIMPLE_MERGE(
        "WP-5",
        "Simple Merge (XOR-join)",
        PatternCategory.BASIC,
        "Multiple incoming branches converge. "
            + "Outgoing branch fires when any one incoming branch completes (non-blocking merge).",
        7,  // MCP suitability: good (first-completing tool result)
        7,  // A2A suitability: good (non-blocking agent handoff merge)
        "Place-transition net: {p_branch_1, p_branch_2, ...} → t_merge (non-exclusive) → p_end"
    ),

    // Advanced Branching and Synchronization (WP-6 to WP-9)

    /**
     * WP-6: Multi-Choice (OR-split).
     * Based on conditions, one or more of multiple branches are taken.
     * Petri net: multiple guards per output transition; branches may overlap.
     */
    MULTI_CHOICE(
        "WP-6",
        "Multi-Choice (OR-split)",
        PatternCategory.ADVANCED_BRANCHING,
        "Based on conditions, one or more of multiple outgoing branches are selected. "
            + "Any non-exclusive combination of branches may execute in parallel.",
        6,  // MCP suitability: moderate (complex conditional branching)
        6,  // A2A suitability: moderate (multi-way agent routing)
        "Place-transition net: p_start → t_multichoice[guard_1, guard_2, ...] → {p_branch_i | guard_i}"
    ),

    /**
     * WP-7: Structured Synchronizing Merge.
     * Multiple branches that were created by a multi-choice synchronize with a join.
     * Petri net: requires correlation of instances to known source split.
     */
    STRUCTURED_SYNC_MERGE(
        "WP-7",
        "Structured Synchronizing Merge",
        PatternCategory.ADVANCED_BRANCHING,
        "Branches created by a multi-choice (WP-6) are collected and synchronized. "
            + "Requires tracking which branches originated from the same split point.",
        5,  // MCP suitability: low (requires branch correlation)
        5,  // A2A suitability: low (complex state tracking)
        "Place-transition net: {p_branch_i | guard_i} → t_structured_sync[cardinality check] → p_end"
    ),

    /**
     * WP-8: Multi-Merge.
     * Multiple branches converge in a non-blocking merge where each branch
     * causes a new execution of the merged task.
     * Petri net: transition without merge-join semantics; each input arc independently triggers.
     */
    MULTI_MERGE(
        "WP-8",
        "Multi-Merge",
        PatternCategory.ADVANCED_BRANCHING,
        "Multiple incoming branches converge without synchronization. "
            + "Each incoming branch independently triggers execution of the outgoing task.",
        5,  // MCP suitability: low (repeated tool invocation per branch)
        5,  // A2A suitability: low (repeated agent task invocation)
        "Place-transition net: {p_branch_1, p_branch_2, ...} → t_merge (no join semantics) → p_task[repeat per input]"
    ),

    /**
     * WP-9: Structured Discriminator.
     * Multiple incoming branches; once the first branch completes, remaining branches are ignored.
     * Petri net: transition with reset of other input places after first firing.
     */
    STRUCTURED_DISCRIMINATOR(
        "WP-9",
        "Structured Discriminator",
        PatternCategory.ADVANCED_BRANCHING,
        "Multiple incoming branches; after the first branch completes and fires the transition, "
            + "remaining branches are cancelled (discriminated).",
        6,  // MCP suitability: moderate (cancel losers after first result)
        6,  // A2A suitability: moderate (race with cancellation)
        "Place-transition net: {p_branch_1, p_branch_2, ...} → t_discriminator[first wins, cancel rest] → p_end"
    ),

    // Structural Patterns (WP-10 to WP-11)

    /**
     * WP-10: Arbitrary Cycles.
     * Backward edges in the workflow graph enabling loops of arbitrary complexity.
     * Petri net: cycles in the reachability graph; may be nested or overlapping.
     */
    ARBITRARY_CYCLES(
        "WP-10",
        "Arbitrary Cycles",
        PatternCategory.STRUCTURAL,
        "Arbitrary backward edges enable loops of arbitrary complexity. "
            + "Cycles may be nested, overlapping, or have multiple exit points.",
        4,  // MCP suitability: low (complex loop orchestration)
        4,  // A2A suitability: low (agent loop state tracking)
        "Place-transition net: {arbitrary cycles in reachability graph}"
    ),

    /**
     * WP-11: Implicit Termination.
     * A case instance terminates when there are no more enabled transitions (no explicit termination).
     * Petri net: deadlock-free nets; termination follows implicit quiescence.
     */
    IMPLICIT_TERMINATION(
        "WP-11",
        "Implicit Termination",
        PatternCategory.STRUCTURAL,
        "A case instance terminates implicitly when no more transitions are enabled, "
            + "without explicit termination events.",
        7,  // MCP suitability: good (implicit tool completion)
        7,  // A2A suitability: good (agent task completion signals termination)
        "Place-transition net: deadlock-free net; no explicit termination condition"
    ),

    // Multiple Instance Patterns (WP-12 to WP-15)

    /**
     * WP-12: Multiple Instances Without Synchronization.
     * A task is instantiated multiple times; instances run independently without waiting for completion.
     * Petri net: multiple concurrent tokens in a place; no join gate.
     */
    MI_WITHOUT_SYNC(
        "WP-12",
        "Multiple Instances Without Synchronization",
        PatternCategory.MULTIPLE_INSTANCES,
        "A task is instantiated multiple times and all instances run independently. "
            + "No synchronization barrier; instances may complete at different times.",
        6,  // MCP suitability: moderate (parallel independent tool invocations)
        6,  // A2A suitability: moderate (parallel independent agent tasks)
        "Place-transition net: p_mi_start →* {t_instance_1, t_instance_2, ..., t_instance_n} →* p_end (no join)"
    ),

    /**
     * WP-13: Multiple Instances With A Priori Design-Time Knowledge.
     * Multiple instances are created with count known at design time (static loop).
     * Petri net: fixed number of tokens generated at design time.
     */
    MI_WITH_APRIORI_DESIGN(
        "WP-13",
        "Multiple Instances with A Priori Design-Time Knowledge",
        PatternCategory.MULTIPLE_INSTANCES,
        "Multiple instances are created with the count known at design time. "
            + "Instance count is static and determined before execution.",
        7,  // MCP suitability: good (static parallel tool invocations)
        7,  // A2A suitability: good (static parallel agent tasks)
        "Place-transition net: p_mi_start →* {t_1...t_N} → t_join → p_end (N known at design time)"
    ),

    /**
     * WP-14: Multiple Instances With A Priori Runtime Knowledge.
     * Multiple instances are created with count determined at runtime (dynamic loop).
     * Petri net: token generation based on runtime data.
     */
    MI_WITH_APRIORI_RUNTIME(
        "WP-14",
        "Multiple Instances with A Priori Runtime Knowledge",
        PatternCategory.MULTIPLE_INSTANCES,
        "Multiple instances are created with the count determined at runtime. "
            + "Instance count is dynamic, based on data available before task instantiation.",
        6,  // MCP suitability: moderate (dynamic parallel tool invocations)
        6,  // A2A suitability: moderate (dynamic parallel agent tasks)
        "Place-transition net: p_mi_start → t_count_decision[N from data] →* {t_1...t_N} → t_join → p_end"
    ),

    /**
     * WP-15: Multiple Instances Without A Priori Runtime Knowledge.
     * Multiple instances are created dynamically; count may grow during execution.
     * Petri net: unbounded loop with dynamic instance creation inside loop.
     */
    MI_WITHOUT_APRIORI(
        "WP-15",
        "Multiple Instances Without A Priori Runtime Knowledge",
        PatternCategory.MULTIPLE_INSTANCES,
        "Multiple instances are created dynamically. "
            + "Instance count is not known at design or task start; may grow during execution.",
        4,  // MCP suitability: low (unbounded dynamic tool invocation)
        4,  // A2A suitability: low (unbounded dynamic agent task creation)
        "Place-transition net: unbounded loop; instances created inside loop body"
    ),

    // State-Based Patterns (WP-16 to WP-18)

    /**
     * WP-16: Deferred Choice.
     * Multiple paths are active; the first to provide a message determines the chosen path.
     * Petri net: external choice through message reception (not internal conditions).
     */
    DEFERRED_CHOICE(
        "WP-16",
        "Deferred Choice",
        PatternCategory.STATE_BASED,
        "Multiple paths are active concurrently. The first path to receive a message "
            + "determines which path is chosen; other paths are cancelled.",
        7,  // MCP suitability: good (first tool response wins)
        8,  // A2A suitability: excellent (external event-driven routing)
        "Place-transition net: {p_path_1, p_path_2, ...} → {t_message_1, t_message_2, ...} (external choice)"
    ),

    /**
     * WP-17: Interleaved Parallel Routing.
     * Multiple parallel paths can be dynamically opened and closed within a single case instance.
     * Petri net: dynamic creation and termination of parallel threads.
     */
    INTERLEAVED_PARALLEL(
        "WP-17",
        "Interleaved Parallel Routing",
        PatternCategory.STATE_BASED,
        "Parallel paths can be dynamically created and terminated within a single case instance. "
            + "Paths may interleave and be opened/closed at runtime.",
        5,  // MCP suitability: low (dynamic parallel/serial switching)
        5,  // A2A suitability: low (complex runtime topology changes)
        "Place-transition net: {p_active_threads} → {t_open, t_close} (dynamic parallelism)"
    ),

    /**
     * WP-18: Milestone.
     * A task is enabled only after a specific milestone has been reached.
     * Petri net: explicit milestone place that gates task enablement.
     */
    MILESTONE(
        "WP-18",
        "Milestone",
        PatternCategory.STATE_BASED,
        "A task is enabled only when a specific milestone (precondition) has been reached. "
            + "Milestone serves as a gate for task activation.",
        7,  // MCP suitability: good (conditional tool enablement)
        7,  // A2A suitability: good (gate-based agent task activation)
        "Place-transition net: p_milestone →* p_task_enabled → t_task (requires milestone)"
    ),

    // Cancellation Patterns (WP-19 to WP-20)

    /**
     * WP-19: Cancel Task.
     * A task instance is cancelled as a result of some event.
     * Petri net: explicit transition removing task tokens; may trigger cleanup.
     */
    CANCEL_TASK(
        "WP-19",
        "Cancel Task",
        PatternCategory.CANCELLATION,
        "A running task instance is cancelled (terminated) as a result of an event. "
            + "May trigger cleanup or compensation logic.",
        8,  // MCP suitability: very good (abort specific tool execution)
        8,  // A2A suitability: very good (abort specific agent task)
        "Place-transition net: p_task_running → t_cancel_event → p_task_cancelled (cleanup)"
    ),

    /**
     * WP-20: Cancel Case.
     * All activities in a case instance are cancelled and the case terminates.
     * Petri net: removes all case tokens; case reaches terminal state.
     */
    CANCEL_CASE(
        "WP-20",
        "Cancel Case",
        PatternCategory.CANCELLATION,
        "An entire case instance is cancelled and all activities are terminated. "
            + "Case moves to a terminal cancelled state.",
        8,  // MCP suitability: very good (abort all tool executions)
        8,  // A2A suitability: very good (abort all agent tasks in case)
        "{p_active_tasks} → t_cancel_case → p_cleanup_all → p_case_end (terminal)"
    ),

    // Extended Patterns (WCP-21 to WCP-43)

    /**
     * WCP-21: Structured Loop.
     * A block of activities is repeated while a condition holds.
     * Petri net: backward edge with condition-controlled looping.
     */
    STRUCTURED_LOOP(
        "WCP-21",
        "Structured Loop",
        PatternCategory.ITERATION,
        "A block of activities is repeated while a condition is true. "
            + "Condition is evaluated at the beginning of each iteration.",
        7,  // MCP suitability: good (structured loop orchestration)
        6,  // A2A suitability: moderate (agent loop state tracking)
        "Place-transition net: p_loop_start → {t_block} → p_loop_condition[check] →? t_back_to_start | t_exit"
    ),

    /**
     * WCP-22: Recursion.
     * A process instance can spawn recursively nested instances of itself.
     * Petri net: recursive call enabling nested case instances.
     */
    RECURSION(
        "WCP-22",
        "Recursion",
        PatternCategory.ITERATION,
        "A workflow instance can recursively create new instances of the same workflow. "
            + "Each recursive instance maintains its own state.",
        6,  // MCP suitability: moderate (recursive tool invocation)
        6,  // A2A suitability: moderate (recursive agent spawning)
        "Place-transition net: p_task → t_spawn_recursive[n times] → {nested instances} → t_join_recursive"
    ),

    /**
     * WCP-23: Transient Trigger.
     * A trigger fires at most once when enabled; if not accepted immediately, it is lost.
     * Petri net: message passing without buffering.
     */
    TRANSIENT_TRIGGER(
        "WCP-23",
        "Transient Trigger",
        PatternCategory.TRIGGER,
        "A trigger fires at most once; if not accepted immediately upon firing, the trigger is lost. "
            + "No buffering or queueing of triggers occurs.",
        6,  // MCP suitability: moderate (non-buffered message handling)
        8,  // A2A suitability: excellent (event-driven external triggers)
        "Place-transition net: t_external → p_trigger[non-buffered] → t_action (fire-and-forget)"
    ),

    /**
     * WCP-24: Persistent Trigger.
     * A trigger remains active until explicitly consumed; multiple tasks may be triggered.
     * Petri net: trigger place remains marked until consumed.
     */
    PERSISTENT_TRIGGER(
        "WCP-24",
        "Persistent Trigger",
        PatternCategory.TRIGGER,
        "A trigger remains active until explicitly consumed by a task. "
            + "Multiple tasks can observe and react to a persistent trigger.",
        6,  // MCP suitability: moderate (buffered event handling)
        8,  // A2A suitability: excellent (observable event state)
        "Place-transition net: t_external → p_trigger[buffered] → {t_action_1, t_action_2, ...}"
    ),

    /**
     * WCP-25: Cancel Region.
     * A region of the workflow can be cancelled as a single unit.
     * Petri net: atomic removal of all tokens in region upon cancellation.
     */
    CANCEL_REGION(
        "WCP-25",
        "Cancel Region",
        PatternCategory.CANCELLATION,
        "A specific region (block) of the workflow can be cancelled as a unit. "
            + "All active tasks within the region are terminated atomically.",
        7,  // MCP suitability: good (scoped cancellation)
        7,  // A2A suitability: good (scoped agent task termination)
        "Place-transition net: {p_region_tasks} → t_cancel_region → p_region_cleanup → p_resume_after_region"
    ),

    /**
     * WCP-26: Cancel Multiple Instance Activity.
     * All instances of a multiple instance activity can be cancelled.
     * Petri net: removes all instance tokens; remaining instances are discarded.
     */
    CANCEL_MI_ACTIVITY(
        "WCP-26",
        "Cancel Multiple Instance Activity",
        PatternCategory.CANCELLATION,
        "All instances of a multiple instance activity are cancelled. "
            + "Remaining instances are terminated immediately.",
        7,  // MCP suitability: good (parallel cancellation)
        7,  // A2A suitability: good (cancel all parallel agent tasks)
        "Place-transition net: {p_instance_1, ..., p_instance_N} → t_cancel_all_instances → p_post_mi"
    ),

    /**
     * WCP-27: Complete Multiple Instance Activity.
     * A multiple instance activity can be completed early without waiting for all instances.
     * Petri net: remaining instances are removed upon early completion.
     */
    COMPLETE_MI_ACTIVITY(
        "WCP-27",
        "Complete Multiple Instance Activity",
        PatternCategory.CANCELLATION,
        "A multiple instance activity can be completed early without waiting for all instances to finish. "
            + "Remaining instances are terminated and cleanup occurs.",
        7,  // MCP suitability: good (early completion of parallel activity)
        7,  // A2A suitability: good (early completion of parallel agent tasks)
        "Place-transition net: {p_instance_completed, p_instance_pending} → t_complete_mi[early] → p_post_mi"
    ),

    /**
     * WCP-28: Blocking Discriminator.
     * Multiple incoming branches; after the first completes, remaining branches wait until discarded.
     * Petri net: blocking join; remaining branches remain enabled until discriminator completes.
     */
    BLOCKING_DISCRIMINATOR(
        "WCP-28",
        "Blocking Discriminator",
        PatternCategory.ADVANCED_BRANCHING,
        "Multiple incoming branches; after the first completes, the transition fires but remaining branches "
            + "remain enabled until the discriminator's next firing (blocking behavior).",
        5,  // MCP suitability: low (complex stateful blocking)
        5,  // A2A suitability: low (complex distributed state)
        "Place-transition net: {p_branch_1, p_branch_2, ...} → t_discriminator[blocking] → p_output"
    ),

    /**
     * WCP-29: Cancelling Discriminator.
     * Multiple incoming branches; after the first completes, remaining branches are cancelled.
     * Petri net: first completion triggers cancel of other branches.
     */
    CANCELLING_DISCRIMINATOR(
        "WCP-29",
        "Cancelling Discriminator",
        PatternCategory.ADVANCED_BRANCHING,
        "Multiple incoming branches; the first to complete fires the transition and all remaining branches "
            + "are cancelled immediately.",
        6,  // MCP suitability: moderate (cancel-on-first winner)
        6,  // A2A suitability: moderate (distributed race cancellation)
        "Place-transition net: {p_branch_1, p_branch_2, ...} → t_discriminator[cancel others] → p_output"
    ),

    /**
     * WCP-30: Structured Partial Join.
     * A subset of incoming branches (M out of N) must complete; the join completes when M conditions are met.
     * Petri net: join with M-of-N firing semantics (known statically).
     */
    STRUCTURED_PARTIAL_JOIN(
        "WCP-30",
        "Structured Partial Join",
        PatternCategory.ADVANCED_BRANCHING,
        "A join waits for a specific number (M) of incoming branches to complete, where M < N. "
            + "M is known at design time.",
        5,  // MCP suitability: low (cardinality tracking)
        5,  // A2A suitability: low (distributed quorum)
        "Place-transition net: {p_branch_1, ..., p_branch_N} → t_partial_join[M of N] → p_output"
    ),

    /**
     * WCP-31: Blocking Partial Join.
     * Like WCP-30 but remaining branches do not fire after M completions; they remain blocked.
     * Petri net: partial join with blocking semantics.
     */
    BLOCKING_PARTIAL_JOIN(
        "WCP-31",
        "Blocking Partial Join",
        PatternCategory.ADVANCED_BRANCHING,
        "A join waits for M of N branches; after M complete, remaining branches are blocked "
            + "and do not proceed until re-enabled.",
        5,  // MCP suitability: low (blocking quorum)
        5,  // A2A suitability: low (blocking distributed state)
        "Place-transition net: {p_branch_1, ..., p_branch_N} → t_partial_join[M of N, blocking] → p_output"
    ),

    /**
     * WCP-32: Cancelling Partial Join.
     * Like WCP-30 but after M branches complete, remaining branches are cancelled.
     * Petri net: partial join with cancellation semantics.
     */
    CANCELLING_PARTIAL_JOIN(
        "WCP-32",
        "Cancelling Partial Join",
        PatternCategory.ADVANCED_BRANCHING,
        "A join waits for M of N branches; after M complete, the join fires and remaining branches "
            + "are cancelled.",
        6,  // MCP suitability: moderate (quorum with cancellation)
        6,  // A2A suitability: moderate (quorum with distributed cancellation)
        "Place-transition net: {p_branch_1, ..., p_branch_N} → t_partial_join[M of N, cancel] → p_output"
    ),

    /**
     * WCP-33: Generalised AND-Join.
     * A join that synchronizes based on a dynamic predicate over input cardinalities.
     * Petri net: join with arbitrary cardinality predicate.
     */
    GENERALISED_AND_JOIN(
        "WCP-33",
        "Generalised AND-Join",
        PatternCategory.SYNCHRONISATION,
        "A join that synchronizes based on a predicate evaluated over the cardinality of incoming branches. "
            + "Predicate can be arbitrary logical expression.",
        5,  // MCP suitability: low (complex predicate evaluation)
        5,  // A2A suitability: low (complex distributed condition)
        "Place-transition net: {p_branch_1, ..., p_branch_N} → t_join[predicate(card)] → p_output"
    ),

    /**
     * WCP-34: Static Partial Join for Multiple Instances.
     * Multiple instances of a task await M of N completions for a static (design-time) count.
     * Petri net: partial join applied across multiple instances.
     */
    STATIC_PARTIAL_JOIN_MI(
        "WCP-34",
        "Static Partial Join for Multiple Instances",
        PatternCategory.ADVANCED_BRANCHING,
        "Multiple instances of a task await completion of M of N instances, where N is statically known. "
            + "Join completes when M instances finish.",
        6,  // MCP suitability: moderate (static quorum on instances)
        6,  // A2A suitability: moderate (static quorum on agents)
        "Place-transition net: {p_instance_1, ..., p_instance_N} → t_partial_join_mi[M of N] → p_post_mi"
    ),

    /**
     * WCP-35: Cancelling Partial Join for Multiple Instances.
     * Like WCP-34 but after M completions, remaining instances are cancelled.
     * Petri net: partial join on multiple instances with cancellation.
     */
    CANCELLING_PARTIAL_JOIN_MI(
        "WCP-35",
        "Cancelling Partial Join for Multiple Instances",
        PatternCategory.ADVANCED_BRANCHING,
        "Multiple instances await M of N completions (N known statically); after M complete, "
            + "remaining instances are cancelled.",
        6,  // MCP suitability: moderate (quorum with instance cancellation)
        6,  // A2A suitability: moderate (quorum with agent instance cancellation)
        "Place-transition net: {p_instance_1, ..., p_instance_N} → t_partial_join_mi[M cancel] → p_post_mi"
    ),

    /**
     * WCP-36: Multiple Instances Without A Priori Runtime Knowledge (dynamic join).
     * Multiple instances are created dynamically; join waits for all to complete.
     * Petri net: dynamic instance creation with dynamic join.
     */
    MI_NO_APRIORI_DYNAMIC_JOIN(
        "WCP-36",
        "Multiple Instances without A Priori Runtime Knowledge (dynamic join)",
        PatternCategory.MULTIPLE_INSTANCES,
        "Multiple instances are created dynamically with count unknown at design or start time. "
            + "Join waits for all dynamically created instances to complete.",
        4,  // MCP suitability: low (unbounded dynamic join)
        4,  // A2A suitability: low (unbounded dynamic agent join)
        "Place-transition net: p_mi_start → t_dynamic_create[N instances] →* {t_1...t_N} → t_dynamic_join → p_end"
    ),

    /**
     * WCP-37: Interleaved Routing (state-based, variant).
     * Parallel paths with interleaving at fine granularity; paths can dynamically merge and re-split.
     * Petri net: arbitrary interleaving of concurrent transitions.
     */
    INTERLEAVED_ROUTING(
        "WCP-37",
        "Interleaved Routing",
        PatternCategory.STATE_BASED,
        "Multiple paths execute concurrently with fine-grained interleaving. "
            + "Paths may dynamically merge and re-split based on state conditions.",
        5,  // MCP suitability: low (fine-grained scheduling)
        5,  // A2A suitability: low (complex distributed interleaving)
        "Place-transition net: p_parallel → {t_block_1, t_block_2, ...} [arbitrary interleaving]"
    ),

    /**
     * WCP-38: Critical Section.
     * A critical section prevents concurrent execution; only one instance may execute at a time.
     * Petri net: mutual exclusion via single resource place.
     */
    CRITICAL_SECTION(
        "WCP-38",
        "Critical Section",
        PatternCategory.STATE_BASED,
        "A critical section ensures mutual exclusion; only one task/instance executes within the section at a time. "
            + "Others must wait.",
        6,  // MCP suitability: moderate (locking/serialization)
        6,  // A2A suitability: moderate (distributed mutex)
        "Place-transition net: p_mutex[1 token] → t_critical[acquire] → p_critical_section → t_release → p_mutex"
    ),

    /**
     * WCP-39: Interleaved Parallel Routing (generalised).
     * Generalization of WCP-17; allows dynamic parallel/serial composition changes.
     * Petri net: dynamic transition between parallel and serial modes.
     */
    INTERLEAVED_PARALLEL_GENERAL(
        "WCP-39",
        "Interleaved Parallel Routing (generalised)",
        PatternCategory.STATE_BASED,
        "Generalization of interleaved parallel routing allowing dynamic composition changes. "
            + "Paths can transition between parallel and serialized execution modes.",
        5,  // MCP suitability: low (dynamic mode switching)
        5,  // A2A suitability: low (dynamic topology)
        "Place-transition net: {p_serial, p_parallel} ↔ t_mode_switch (dynamic)"
    ),

    /**
     * WCP-40: Thread Merge.
     * Threads (parallel paths) are merged; single output when all threads join.
     * Petri net: synchronizing merge of thread identifiers.
     */
    THREAD_MERGE(
        "WCP-40",
        "Thread Merge",
        PatternCategory.MULTIPLE_INSTANCES,
        "Multiple parallel threads (paths) are merged into a single path. "
            + "All threads must complete before merge fires.",
        7,  // MCP suitability: good (thread pool synchronization)
        7,  // A2A suitability: good (thread merge in agents)
        "Place-transition net: {p_thread_1, ..., p_thread_N} → t_thread_merge → p_merged"
    ),

    /**
     * WCP-41: Thread Split.
     * A single path splits into multiple parallel threads.
     * Petri net: creates multiple parallel execution paths.
     */
    THREAD_SPLIT(
        "WCP-41",
        "Thread Split",
        PatternCategory.MULTIPLE_INSTANCES,
        "A single path splits into multiple parallel threads. "
            + "Each thread executes independently.",
        7,  // MCP suitability: good (parallel thread creation)
        7,  // A2A suitability: good (parallel agent thread creation)
        "Place-transition net: p_input → t_thread_split → {p_thread_1, ..., p_thread_N}"
    ),

    /**
     * WCP-42: Explicit Termination.
     * A case instance explicitly terminates via an end-of-case event.
     * Petri net: explicit transition to terminal state.
     */
    EXPLICIT_TERMINATION(
        "WCP-42",
        "Explicit Termination",
        PatternCategory.STRUCTURAL,
        "A case instance explicitly terminates upon reaching an explicit end condition or event. "
            + "Termination is not implicit but programmatically controlled.",
        7,  // MCP suitability: good (explicit completion event)
        7,  // A2A suitability: good (explicit agent case termination)
        "Place-transition net: {p_active_tasks} → t_explicit_end → p_case_end (explicit)"
    ),

    /**
     * WCP-43: Multiple Instances with A Priori Design-Time Knowledge (generalised).
     * Generalization of WCP-13; allows complex instance patterns and flexible join semantics.
     * Petri net: static instance count with generalized join.
     */
    MI_WITH_APRIORI_DESIGN_GENERAL(
        "WCP-43",
        "Multiple Instances with A Priori Design-Time Knowledge (generalised)",
        PatternCategory.MULTIPLE_INSTANCES,
        "Multiple instances are created with count known at design time (generalization of WCP-13). "
            + "Allows complex instance patterns and flexible join semantics.",
        7,  // MCP suitability: good (static parallel instances with complex join)
        7,  // A2A suitability: good (static agent instances with complex join)
        "Place-transition net: p_mi_start →* {t_1...t_N} → t_join[generalized] → p_end (N known at design)"
    );

    private final String code;
    private final String label;
    private final PatternCategory category;
    private final String description;
    private final int mcpSuitability;
    private final int a2aSuitability;
    private final String petriNotation;

    /**
     * Constructor for WorkflowPattern enum values.
     *
     * @param code the pattern code (e.g., "WP-1")
     * @param label the human-readable pattern name
     * @param category the pattern category
     * @param description a formal description of the pattern
     * @param mcpSuitability suitability for MCP orchestration (0-10)
     * @param a2aSuitability suitability for A2A coordination (0-10)
     * @param petriNotation textual Petri net structure notation
     */
    WorkflowPattern(
        String code,
        String label,
        PatternCategory category,
        String description,
        int mcpSuitability,
        int a2aSuitability,
        String petriNotation
    ) {
        this.code = code;
        this.label = label;
        this.category = category;
        this.description = description;
        this.mcpSuitability = mcpSuitability;
        this.a2aSuitability = a2aSuitability;
        this.petriNotation = petriNotation;
    }

    /**
     * Gets the pattern code (e.g., "WP-1").
     *
     * @return the pattern code
     */
    public String getCode() {
        return code;
    }

    /**
     * Gets the human-readable pattern label.
     *
     * @return the pattern label
     */
    public String getLabel() {
        return label;
    }

    /**
     * Gets the pattern category.
     *
     * @return the category
     */
    public PatternCategory getCategory() {
        return category;
    }

    /**
     * Gets the formal description of this pattern.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets the suitability score for MCP tool orchestration.
     * Scale: 0-10, where 10 is most suitable.
     *
     * @return suitability score
     */
    public int getMcpSuitability() {
        return mcpSuitability;
    }

    /**
     * Gets the suitability score for A2A agent coordination.
     * Scale: 0-10, where 10 is most suitable.
     *
     * @return suitability score
     */
    public int getA2aSuitability() {
        return a2aSuitability;
    }

    /**
     * Gets the Petri net notation for this pattern.
     *
     * @return compact textual Petri net representation
     */
    public String getPetriNotation() {
        return petriNotation;
    }

    /**
     * Checks if this pattern is suitable for MCP orchestration.
     * A pattern is considered suitable if MCP suitability >= 7.
     *
     * @return true if suitable for MCP
     */
    public boolean isSuitableForMcp() {
        return mcpSuitability >= 7;
    }

    /**
     * Checks if this pattern is suitable for A2A agent coordination.
     * A pattern is considered suitable if A2A suitability >= 7.
     *
     * @return true if suitable for A2A
     */
    public boolean isSuitableForA2a() {
        return a2aSuitability >= 7;
    }

    /**
     * Looks up a pattern by its code (e.g., "WP-1").
     *
     * @param code the pattern code
     * @return optional containing the pattern if found
     */
    public static Optional<WorkflowPattern> forCode(String code) {
        return Arrays.stream(values())
            .filter(p -> p.getCode().equalsIgnoreCase(code))
            .findFirst();
    }

    /**
     * Creates a mapping of all pattern codes to patterns for efficient lookup.
     *
     * @return immutable map of code to pattern
     */
    public static Map<String, WorkflowPattern> codeMap() {
        var map = new java.util.HashMap<String, WorkflowPattern>();
        for (WorkflowPattern pattern : values()) {
            map.put(pattern.getCode(), pattern);
        }
        return Map.copyOf(map);
    }
}
