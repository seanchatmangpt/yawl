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

package org.yawlfoundation.yawl.integration.verification;

/**
 * Enumeration of 7 core deadlock patterns in Petri nets.
 * Each pattern is paired with a SPARQL query for detection and
 * remediation guidance.
 *
 * <p>Patterns are detected via graph reachability analysis on
 * the Petri net structure (places, transitions, arcs).
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public enum DeadlockPattern {
    /**
     * UNREACHABLE_TASK: A task has no incoming arc from any reachable place.
     * Tokens can never arrive at the task's input place.
     *
     * <p>Detection: Forward reachability from start place does not reach
     * the task's input place.
     *
     * <p>SPARQL query variables: ?task (the unreachable task), ?net (the net),
     * ?place (the task's unreachable input place).
     */
    UNREACHABLE_TASK(
        "Unreachable Task",
        """
        SELECT ?task ?place WHERE {
          ?net a :PetriNet ;
               :hasTransition ?task ;
               :hasPlace ?place ;
               :isInputOf ?task ?place .
          FILTER NOT EXISTS { ?reachPlace :canReach ?place }
        }
        """,
        "Add an arc from a reachable place to this task's input place. "
        + "Alternatively, verify the task should exist in the workflow."
    ),

    /**
     * DEAD_TRANSITION: A task can never fire due to its precondition being
     * permanently unsatisfiable (e.g., AND-join waiting for tokens that
     * never arrive, or always-false guard).
     *
     * <p>Detection: All input places to the task have no incoming arcs,
     * or form a cycle with no external entry.
     */
    DEAD_TRANSITION(
        "Dead Transition",
        """
        SELECT ?task WHERE {
          ?net a :PetriNet ;
               :hasTransition ?task .
          FILTER NOT EXISTS {
            ?task :inputPlace ?place .
            ?place :inputTransition ?sourceTransition .
            ?sourceTransition :canFire true .
          }
        }
        """,
        "Verify the task's precondition is reachable. Add missing source transitions "
        + "or remove impossible guard conditions."
    ),

    /**
     * IMPLICIT_DEADLOCK: An AND-join task waits for tokens from multiple
     * places, but at least one place can never produce a token.
     *
     * <p>Detection: AND-join with unreachable input place(s).
     */
    IMPLICIT_DEADLOCK(
        "Implicit Deadlock",
        """
        SELECT ?task ?place WHERE {
          ?net a :PetriNet ;
               :hasTransition ?task ;
               :isAndJoin ?task true ;
               :inputPlace ?place .
          FILTER NOT EXISTS {
            ?place :producingTransition ?producer .
            ?producer :canFire true .
          }
        }
        """,
        "Verify all branches feeding the AND-join are reachable. "
        + "Either enable the missing branch or convert AND-join to OR-join."
    ),

    /**
     * MISSING_OR_JOIN: Multiple execution paths converge without a merge
     * transition, leading to token loss or duplication.
     *
     * <p>Detection: Two or more transitions output to the same place
     * without a join transition consolidating them.
     */
    MISSING_OR_JOIN(
        "Missing Or Join",
        """
        SELECT ?place WHERE {
          ?net a :PetriNet ;
               :hasPlace ?place ;
               :inputTransition ?t1 ;
               :inputTransition ?t2 .
          FILTER (?t1 != ?t2)
          FILTER NOT EXISTS {
            ?place :isOutputOf ?joinTask .
            ?joinTask :isOrJoin true .
          }
        }
        """,
        "Add an OR-join (merge) transition to consolidate multiple input paths "
        + "into a single place."
    ),

    /**
     * ORPHANED_PLACE: A place has no outgoing transitions, creating a token
     * trap where tokens accumulate and never leave.
     *
     * <p>Detection: Place with no transitions in its post-set.
     */
    ORPHANED_PLACE(
        "Orphaned Place",
        """
        SELECT ?place WHERE {
          ?net a :PetriNet ;
               :hasPlace ?place .
          FILTER NOT EXISTS {
            ?place :outputTransition ?transition .
          }
        }
        """,
        "Add transitions to consume tokens from this place. "
        + "Verify the place is not meant to be a final output place."
    ),

    /**
     * LIVELOCK: A cycle exists in the net with no exit, causing the workflow
     * to loop indefinitely and never progress to completion.
     *
     * <p>Detection: Strongly connected component (SCC) that does not lead to
     * the end place.
     */
    LIVELOCK(
        "Livelock",
        """
        SELECT ?task1 ?task2 WHERE {
          ?net a :PetriNet ;
               :hasTransition ?task1 ;
               :hasTransition ?task2 .
          ?task1 :canReach ?task2 .
          ?task2 :canReach ?task1 .
          FILTER NOT EXISTS {
            ?task2 :canReach ?endPlace .
            ?endPlace :isEnd true .
          }
        }
        """,
        "Break the cycle by adding an exit condition or guard. "
        + "Ensure at least one path from the cycle leads to the end place."
    ),

    /**
     * IMPROPER_TERMINATION: Multiple tokens are possible at the end place,
     * violating the requirement that exactly one token marks successful completion.
     *
     * <p>Detection: Multiple transitions without consolidation producing to
     * the end place.
     */
    IMPROPER_TERMINATION(
        "Improper Termination",
        """
        SELECT ?t1 ?t2 WHERE {
          ?net a :PetriNet ;
               :endPlace ?end ;
               :hasTransition ?t1 ;
               :hasTransition ?t2 ;
               :outputOf ?end ?t1 ;
               :outputOf ?end ?t2 .
          FILTER (?t1 != ?t2)
          FILTER NOT EXISTS {
            ?t1 :isSynchronizer true .
            ?t2 :isSynchronizer true .
          }
        }
        """,
        "Ensure all paths converging to the end place use a synchronizer "
        + "(merge) transition to guarantee exactly one final token."
    );

    private final String displayName;
    private final String sparqlQuery;
    private final String remediationAdvice;

    DeadlockPattern(String displayName, String sparqlQuery, String remediationAdvice) {
        this.displayName = displayName;
        this.sparqlQuery = sparqlQuery;
        this.remediationAdvice = remediationAdvice;
    }

    /**
     * Returns the human-readable name of this deadlock pattern.
     *
     * @return pattern name (e.g., "Unreachable Task")
     */
    public String displayName() {
        return displayName;
    }

    /**
     * Returns the SPARQL SELECT query to detect this pattern in a Petri net.
     *
     * <p>Query variables include:
     * <ul>
     *   <li>?task - the task entity involved in the pattern</li>
     *   <li>?net - the Petri net being queried</li>
     *   <li>?place - the place entity involved (if applicable)</li>
     * </ul>
     *
     * @return SPARQL SELECT query as a string
     */
    public String sparqlQuery() {
        return sparqlQuery;
    }

    /**
     * Returns actionable remediation advice for fixing this pattern.
     *
     * @return remediation guidance string
     */
    public String remediation() {
        return remediationAdvice;
    }
}
