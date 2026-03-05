package org.yawlfoundation.yawl.integration.wizard.patterns;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Formal WCP reference metadata for extended patterns (WCP-21 to WCP-43).
 *
 * <p>Provides detailed documentation and support information for the extended
 * workflow patterns defined in van der Aalst & ter Hofstede (2006).
 *
 * <p>Each pattern includes:
 * <ul>
 *   <li>WCP code (e.g., "WCP-21")</li>
 *   <li>Formal pattern name</li>
 *   <li>YAWL engine support level (FULL, PARTIAL, NONE)</li>
 *   <li>Petri net sketch description</li>
 *   <li>Alternative names used in literature</li>
 * </ul>
 *
 * @see WorkflowPattern for runtime pattern definitions
 * @see PatternCategory for pattern categorization
 */
public final class ExtendedPatternMetadata {

    /**
     * Formal workflow pattern reference metadata.
     *
     * @param wcpCode WCP identifier (e.g., "WCP-21")
     * @param name formal pattern name
     * @param yawlSupport support level in YAWL engine (FULL, PARTIAL, NONE)
     * @param petriNetSketch brief Petri net description
     * @param alternativeNames alternative names used in literature
     */
    public record PatternRef(
        String wcpCode,
        String name,
        String yawlSupport,
        String petriNetSketch,
        String[] alternativeNames
    ) {
        /**
         * Constructor ensures non-null fields.
         */
        public PatternRef {
            if (wcpCode == null || wcpCode.isBlank()) {
                throw new IllegalArgumentException("wcpCode cannot be null or blank");
            }
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name cannot be null or blank");
            }
            if (yawlSupport == null || yawlSupport.isBlank()) {
                throw new IllegalArgumentException("yawlSupport cannot be null or blank");
            }
            if (petriNetSketch == null) {
                throw new IllegalArgumentException("petriNetSketch cannot be null");
            }
            if (alternativeNames == null) {
                alternativeNames = new String[0];
            }
        }
    }

    /**
     * Catalog of extended pattern references (WCP-21 to WCP-43).
     */
    private static final List<PatternRef> EXTENDED_CATALOG = Collections.unmodifiableList(Arrays.asList(
        // WCP-21: Structured Loop
        new PatternRef(
            "WCP-21",
            "Structured Loop",
            "FULL",
            "p_loop_start → {t_block} → p_loop_condition[check] →? t_back_to_start | t_exit",
            new String[]{"While loop", "Do-while loop", "For loop"}
        ),

        // WCP-22: Recursion
        new PatternRef(
            "WCP-22",
            "Recursion",
            "PARTIAL",
            "p_task → t_spawn_recursive[n times] → {nested instances} → t_join_recursive",
            new String[]{"Recursive case instantiation", "Nested process invocation"}
        ),

        // WCP-23: Transient Trigger
        new PatternRef(
            "WCP-23",
            "Transient Trigger",
            "PARTIAL",
            "t_external → p_trigger[non-buffered] → t_action (fire-and-forget)",
            new String[]{"One-shot trigger", "Non-buffered message"}
        ),

        // WCP-24: Persistent Trigger
        new PatternRef(
            "WCP-24",
            "Persistent Trigger",
            "FULL",
            "t_external → p_trigger[buffered] → {t_action_1, t_action_2, ...}",
            new String[]{"Buffered trigger", "Observable event"}
        ),

        // WCP-25: Cancel Region
        new PatternRef(
            "WCP-25",
            "Cancel Region",
            "PARTIAL",
            "{p_region_tasks} → t_cancel_region → p_region_cleanup → p_resume_after_region",
            new String[]{"Block cancellation", "Scoped exception handling"}
        ),

        // WCP-26: Cancel Multiple Instance Activity
        new PatternRef(
            "WCP-26",
            "Cancel Multiple Instance Activity",
            "PARTIAL",
            "{p_instance_1, ..., p_instance_N} → t_cancel_all_instances → p_post_mi",
            new String[]{"MI cancellation", "Parallel instance abort"}
        ),

        // WCP-27: Complete Multiple Instance Activity
        new PatternRef(
            "WCP-27",
            "Complete Multiple Instance Activity",
            "PARTIAL",
            "{p_instance_completed, p_instance_pending} → t_complete_mi[early] → p_post_mi",
            new String[]{"Early MI termination", "Quorum completion"}
        ),

        // WCP-28: Blocking Discriminator
        new PatternRef(
            "WCP-28",
            "Blocking Discriminator",
            "NONE",
            "{p_branch_1, p_branch_2, ...} → t_discriminator[blocking] → p_output",
            new String[]{"Blocking race", "Deferred merge"}
        ),

        // WCP-29: Cancelling Discriminator
        new PatternRef(
            "WCP-29",
            "Cancelling Discriminator",
            "PARTIAL",
            "{p_branch_1, p_branch_2, ...} → t_discriminator[cancel others] → p_output",
            new String[]{"Cancelling race", "First-wins merge"}
        ),

        // WCP-30: Structured Partial Join
        new PatternRef(
            "WCP-30",
            "Structured Partial Join",
            "NONE",
            "{p_branch_1, ..., p_branch_N} → t_partial_join[M of N] → p_output",
            new String[]{"M-of-N join", "Quorum join"}
        ),

        // WCP-31: Blocking Partial Join
        new PatternRef(
            "WCP-31",
            "Blocking Partial Join",
            "NONE",
            "{p_branch_1, ..., p_branch_N} → t_partial_join[M of N, blocking] → p_output",
            new String[]{"Blocking quorum", "M-of-N blocking"}
        ),

        // WCP-32: Cancelling Partial Join
        new PatternRef(
            "WCP-32",
            "Cancelling Partial Join",
            "PARTIAL",
            "{p_branch_1, ..., p_branch_N} → t_partial_join[M of N, cancel] → p_output",
            new String[]{"Cancelling quorum", "M-of-N with cancellation"}
        ),

        // WCP-33: Generalised AND-Join
        new PatternRef(
            "WCP-33",
            "Generalised AND-Join",
            "NONE",
            "{p_branch_1, ..., p_branch_N} → t_join[predicate(card)] → p_output",
            new String[]{"Predicate-based join", "Cardinality join"}
        ),

        // WCP-34: Static Partial Join for Multiple Instances
        new PatternRef(
            "WCP-34",
            "Static Partial Join for Multiple Instances",
            "PARTIAL",
            "{p_instance_1, ..., p_instance_N} → t_partial_join_mi[M of N] → p_post_mi",
            new String[]{"Static MI quorum", "Design-time instance quorum"}
        ),

        // WCP-35: Cancelling Partial Join for Multiple Instances
        new PatternRef(
            "WCP-35",
            "Cancelling Partial Join for Multiple Instances",
            "PARTIAL",
            "{p_instance_1, ..., p_instance_N} → t_partial_join_mi[M cancel] → p_post_mi",
            new String[]{"MI quorum with cancellation", "Cancelling instance join"}
        ),

        // WCP-36: Multiple Instances Without A Priori Runtime Knowledge (dynamic join)
        new PatternRef(
            "WCP-36",
            "Multiple Instances without A Priori Runtime Knowledge (dynamic join)",
            "PARTIAL",
            "p_mi_start → t_dynamic_create[N instances] →* {t_1...t_N} → t_dynamic_join → p_end",
            new String[]{"Runtime-determined instances", "Dynamic MI"}
        ),

        // WCP-37: Interleaved Routing
        new PatternRef(
            "WCP-37",
            "Interleaved Routing",
            "NONE",
            "p_parallel → {t_block_1, t_block_2, ...} [arbitrary interleaving]",
            new String[]{"Fine-grained interleaving", "Arbitrary execution order"}
        ),

        // WCP-38: Critical Section
        new PatternRef(
            "WCP-38",
            "Critical Section",
            "PARTIAL",
            "p_mutex[1 token] → t_critical[acquire] → p_critical_section → t_release → p_mutex",
            new String[]{"Mutual exclusion", "Serialized region"}
        ),

        // WCP-39: Interleaved Parallel Routing (generalised)
        new PatternRef(
            "WCP-39",
            "Interleaved Parallel Routing (generalised)",
            "NONE",
            "{p_serial, p_parallel} ↔ t_mode_switch (dynamic)",
            new String[]{"Dynamic mode switching", "Serial-parallel composition"}
        ),

        // WCP-40: Thread Merge
        new PatternRef(
            "WCP-40",
            "Thread Merge",
            "FULL",
            "{p_thread_1, ..., p_thread_N} → t_thread_merge → p_merged",
            new String[]{"Path merge", "Thread join"}
        ),

        // WCP-41: Thread Split
        new PatternRef(
            "WCP-41",
            "Thread Split",
            "FULL",
            "p_input → t_thread_split → {p_thread_1, ..., p_thread_N}",
            new String[]{"Path split", "Thread fork"}
        ),

        // WCP-42: Explicit Termination
        new PatternRef(
            "WCP-42",
            "Explicit Termination",
            "FULL",
            "{p_active_tasks} → t_explicit_end → p_case_end (explicit)",
            new String[]{"Explicit end event", "Programmatic termination"}
        ),

        // WCP-43: Multiple Instances with A Priori Design-Time Knowledge (generalised)
        new PatternRef(
            "WCP-43",
            "Multiple Instances with A Priori Design-Time Knowledge (generalised)",
            "FULL",
            "p_mi_start →* {t_1...t_N} → t_join[generalized] → p_end (N known at design)",
            new String[]{"Generalized static MI", "Complex MI join"}
        )
    ));

    /**
     * Private constructor: utility class with static methods only.
     */
    private ExtendedPatternMetadata() {
        throw new AssertionError("Cannot instantiate ExtendedPatternMetadata");
    }

    /**
     * Gets the complete catalog of extended pattern references.
     *
     * @return immutable list of PatternRef (WCP-21 to WCP-43)
     */
    public static List<PatternRef> getExtendedCatalog() {
        return EXTENDED_CATALOG;
    }

    /**
     * Finds a pattern reference by WCP code.
     *
     * @param wcpCode the WCP code (e.g., "WCP-21")
     * @return optional containing the pattern reference if found
     * @throws NullPointerException if wcpCode is null
     */
    public static Optional<PatternRef> findByCode(String wcpCode) {
        if (wcpCode == null) {
            throw new NullPointerException("wcpCode cannot be null");
        }
        return EXTENDED_CATALOG.stream()
            .filter(p -> p.wcpCode().equalsIgnoreCase(wcpCode))
            .findFirst();
    }

    /**
     * Finds a pattern reference by formal name.
     *
     * @param name the pattern name (case-insensitive)
     * @return optional containing the pattern reference if found
     * @throws NullPointerException if name is null
     */
    public static Optional<PatternRef> findByName(String name) {
        if (name == null) {
            throw new NullPointerException("name cannot be null");
        }
        String lowerName = name.toLowerCase();
        return EXTENDED_CATALOG.stream()
            .filter(p -> p.name().toLowerCase().equals(lowerName))
            .findFirst();
    }

    /**
     * Gets all patterns with full YAWL engine support.
     *
     * @return immutable list of fully supported patterns
     */
    public static List<PatternRef> getFullySupported() {
        return EXTENDED_CATALOG.stream()
            .filter(p -> "FULL".equals(p.yawlSupport()))
            .toList();
    }

    /**
     * Gets all patterns with partial YAWL engine support.
     *
     * @return immutable list of partially supported patterns
     */
    public static List<PatternRef> getPartiallySupported() {
        return EXTENDED_CATALOG.stream()
            .filter(p -> "PARTIAL".equals(p.yawlSupport()))
            .toList();
    }

    /**
     * Gets all patterns with no YAWL engine support.
     *
     * @return immutable list of unsupported patterns
     */
    public static List<PatternRef> getUnsupported() {
        return EXTENDED_CATALOG.stream()
            .filter(p -> "NONE".equals(p.yawlSupport()))
            .toList();
    }

    /**
     * Gets the total count of extended patterns.
     *
     * @return the number of extended patterns (always 23)
     */
    public static int size() {
        return EXTENDED_CATALOG.size();
    }
}
