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

package org.yawlfoundation.yawl.verification.ltl;

import java.util.Objects;

/**
 * Predefined temporal properties for workflow verification.
 *
 * <p>Implements van der Aalst's soundness properties as LTL formulas,
 * plus additional properties for workflow reliability verification.</p>
 *
 * <h2>Van der Aalst's Soundness Properties (1997)</h2>
 * <ol>
 *   <li><b>Option to complete</b>: Every task can potentially complete</li>
 *   <li><b>Proper completion</b>: Only one token in output condition at completion</li>
 *   <li><b>No dead tasks</b>: Every task can be enabled</li>
 *   <li><b>No deadlock</b>: No state where progress is impossible</li>
 * </ol>
 *
 * <h2>LTL Formulations</h2>
 * <table border="1">
 *   <tr><th>Property</th><th>LTL Formula</th></tr>
 *   <tr><td>Eventually terminates</td><td>◇(output_condition)</td></tr>
 *   <tr><td>Always progress</td><td>□(enabled_items → ◇(completion))</td></tr>
 *   <tr><td>Single final token</td><td>□(¬multiple_tokens_at_end)</td></tr>
 *   <tr><td>Always can terminate</td><td>□(◇(output_condition))</td></tr>
 *   <tr><td>Option to complete</td><td>(enabled) → ◇(completed)</td></tr>
 * </table>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 * @see LtlFormula
 * @see LtlModelChecker
 */
public enum TemporalProperty {

    // =========================================================================
    // Van der Aalst's Soundness Properties
    // =========================================================================

    /**
     * Eventually reaches output condition (termination).
     *
     * <p><b>LTL</b>: ◇(output_condition)</p>
     *
     * <p><b>Meaning</b>: The workflow will eventually reach its output condition,
     * indicating successful completion. This is a core soundness property.</p>
     *
     * <p><b>Van der Aalst</b>: Corresponds to "proper completion" - every case
     * should eventually terminate.</p>
     */
    EVENTUALLY_TERMINATES(
        "Eventually Terminates",
        "◇(output_condition)",
        LtlFormula.finally_(LtlFormula.atomic("output_condition")),
        "Workflow eventually reaches output condition (proper completion)"
    ),

    /**
     * Always has progress (no deadlock).
     *
     * <p><b>LTL</b>: □(enabled_items → ◇(completion))</p>
     *
     * <p><b>Meaning</b>: Whenever there are enabled work items, eventually at least
     * one will complete. This ensures no deadlock states where enabled items can
     * never progress.</p>
     *
     * <p><b>Van der Aalst</b>: Corresponds to "no deadlock" property.</p>
     */
    ALWAYS_PROGRESS(
        "Always Progress",
        "□(enabled_items → ◇(completion))",
        LtlFormula.globally(
            LtlFormula.implies(
                LtlFormula.atomic("enabled_items"),
                LtlFormula.finally_(LtlFormula.atomic("completion"))
            )
        ),
        "Whenever enabled items exist, completion eventually occurs (no deadlock)"
    ),

    /**
     * Single final token (proper completion).
     *
     * <p><b>LTL</b>: □(¬multiple_tokens_at_end)</p>
     *
     * <p><b>Meaning</b>: At no point should there be multiple tokens in the output
     * condition. This ensures proper synchronization at workflow completion.</p>
     *
     * <p><b>Van der Aalst</b>: Corresponds to "proper completion" - only one token
     * in output condition at completion.</p>
     */
    SINGLE_FINAL_TOKEN(
        "Single Final Token",
        "□(¬multiple_tokens_at_end)",
        LtlFormula.globally(
            LtlFormula.not(LtlFormula.atomic("multiple_tokens_at_end"))
        ),
        "Never more than one token at output condition (proper completion)"
    ),

    /**
     * Always can terminate (no livelock).
     *
     * <p><b>LTL</b>: □(◇(output_condition))</p>
     *
     * <p><b>Meaning</b>: From every reachable state, the output condition is
     * eventually reachable. This ensures no livelock where the workflow cycles
     * forever without completion.</p>
     *
     * <p><b>Van der Aalst</b>: Corresponds to "option to complete" extended to
     * all reachable states.</p>
     */
    ALWAYS_CAN_TERMINATE(
        "Always Can Terminate",
        "□(◇(output_condition))",
        LtlFormula.globally(
            LtlFormula.finally_(LtlFormula.atomic("output_condition"))
        ),
        "From every state, completion is reachable (no livelock)"
    ),

    /**
     * Option to complete (enabled implies completable).
     *
     * <p><b>LTL</b>: (enabled) → ◇(completed)</p>
     *
     * <p><b>Meaning</b>: If a work item becomes enabled, it can eventually complete.
     * This ensures no dead tasks.</p>
     *
     * <p><b>Van der Aalst</b>: Corresponds to "no dead tasks" - every task can fire.</p>
     */
    OPTION_TO_COMPLETE(
        "Option to Complete",
        "(enabled) → ◇(completed)",
        LtlFormula.implies(
            LtlFormula.atomic("enabled"),
            LtlFormula.finally_(LtlFormula.atomic("completed"))
        ),
        "Enabled work items can eventually complete (no dead tasks)"
    ),

    // =========================================================================
    // Additional Workflow Properties
    // =========================================================================

    /**
     * No orphaned work items (consistency).
     *
     * <p><b>LTL</b>: □(¬orphaned_work_items)</p>
     *
     * <p><b>Meaning</b>: At no point should there be work items without a valid
     * parent case. This ensures data integrity.</p>
     */
    NO_ORPHANED_ITEMS(
        "No Orphaned Items",
        "□(¬orphaned_work_items)",
        LtlFormula.globally(
            LtlFormula.not(LtlFormula.atomic("orphaned_work_items"))
        ),
        "No work items without valid parent case"
    ),

    /**
     * Token conservation (Petri net invariant).
     *
     * <p><b>LTL</b>: □(token_count_conserved)</p>
     *
     * <p><b>Meaning</b>: Total tokens in the net remain constant (for 1-safe nets,
     * this means boundedness is preserved).</p>
     */
    TOKEN_CONSERVATION(
        "Token Conservation",
        "□(token_count_conserved)",
        LtlFormula.globally(LtlFormula.atomic("token_count_conserved")),
        "Total tokens in net remain constant (Petri net invariant)"
    ),

    /**
     * AND-join synchronization.
     *
     * <p><b>LTL</b>: □(and_join_enabled → all_inputs_arrived)</p>
     *
     * <p><b>Meaning</b>: An AND-join only becomes enabled when all required
     * tokens have arrived from all branches.</p>
     */
    AND_JOIN_SYNCHRONIZATION(
        "AND-Join Synchronization",
        "□(and_join_enabled → all_inputs_arrived)",
        LtlFormula.globally(
            LtlFormula.implies(
                LtlFormula.atomic("and_join_enabled"),
                LtlFormula.atomic("all_inputs_arrived")
            )
        ),
        "AND-join only enabled when all inputs have arrived"
    ),

    /**
     * Work item exclusivity.
     *
     * <p><b>LTL</b>: □(¬concurrent_execution_same_item)</p>
     *
     * <p><b>Meaning</b>: At no point should the same work item be executed
     * concurrently by multiple threads.</p>
     */
    WORK_ITEM_EXCLUSIVITY(
        "Work Item Exclusivity",
        "□(¬concurrent_execution_same_item)",
        LtlFormula.globally(
            LtlFormula.not(LtlFormula.atomic("concurrent_execution_same_item"))
        ),
        "Same work item not executed concurrently"
    ),

    /**
     * Fairness (every enabled item eventually fires).
     *
     * <p><b>LTL</b>: □(enabled(item) → ◇(fired(item)))</p>
     *
     * <p><b>Meaning</b>: Every enabled work item will eventually be processed.
     * This is a strong fairness assumption.</p>
     */
    STRONG_FAIRNESS(
        "Strong Fairness",
        "□(enabled(item) → ◇(fired(item)))",
        LtlFormula.globally(
            LtlFormula.implies(
                LtlFormula.atomic("enabled(item)"),
                LtlFormula.finally_(LtlFormula.atomic("fired(item)"))
            )
        ),
        "Every enabled item eventually fires (strong fairness)"
    );

    private final String displayName;
    private final String ltlString;
    private final LtlFormula formula;
    private final String description;

    TemporalProperty(String displayName, String ltlString, LtlFormula formula,
                     String description) {
        this.displayName = displayName;
        this.ltlString = ltlString;
        this.formula = formula;
        this.description = description;
    }

    /**
     * Returns the human-readable display name.
     *
     * @return Display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the LTL formula as a string.
     *
     * @return LTL string representation
     */
    public String getLtlString() {
        return ltlString;
    }

    /**
     * Returns the LtlFormula object.
     *
     * @return Formula object
     */
    public LtlFormula getFormula() {
        return formula;
    }

    /**
     * Returns the description of this property.
     *
     * @return Description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns all van der Aalst soundness properties.
     *
     * @return Array of soundness properties
     */
    public static TemporalProperty[] soundnessProperties() {
        return new TemporalProperty[] {
            EVENTUALLY_TERMINATES,
            ALWAYS_PROGRESS,
            SINGLE_FINAL_TOKEN,
            ALWAYS_CAN_TERMINATE,
            OPTION_TO_COMPLETE
        };
    }

    /**
     * Returns all workflow integrity properties.
     *
     * @return Array of integrity properties
     */
    public static TemporalProperty[] integrityProperties() {
        return new TemporalProperty[] {
            NO_ORPHANED_ITEMS,
            TOKEN_CONSERVATION,
            AND_JOIN_SYNCHRONIZATION,
            WORK_ITEM_EXCLUSIVITY
        };
    }

    /**
     * Returns all properties (soundness + integrity).
     *
     * @return Array of all properties
     */
    public static TemporalProperty[] allProperties() {
        return values();
    }

    @Override
    public String toString() {
        return displayName + ": " + ltlString;
    }
}
