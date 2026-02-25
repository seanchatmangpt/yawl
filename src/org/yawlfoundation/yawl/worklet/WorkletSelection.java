/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.worklet;

/**
 * Sealed result type representing the outcome of an RDR worklet selection.
 *
 * <p>An evaluation of a {@link RdrSet} against a work-item context yields one of:
 * <ul>
 *   <li>{@link SubCaseSelection} — the RDR tree selected a standard YAWL sub-case worklet</li>
 *   <li>{@link A2AAgentSelection} — the RDR conclusion starts with {@code "a2a:"}, routing
 *       to an autonomous A2A agent via HTTP</li>
 *   <li>{@link NoSelection} — no rule matched; normal task execution continues</li>
 * </ul>
 *
 * <p>Usage example in an exhaustive switch:
 * <pre>{@code
 * switch (selection) {
 *     case SubCaseSelection scs  -> launchSubCase(scs.workletName());
 *     case A2AAgentSelection a2a -> dispatchToAgent(a2a.agentEndpoint(), a2a.skill());
 *     case NoSelection ignored   -> { /* no worklet needed *\/ }
 * }
 * }</pre>
 */
public sealed interface WorkletSelection
        permits WorkletSelection.SubCaseSelection,
                WorkletSelection.A2AAgentSelection,
                WorkletSelection.NoSelection {

    /**
     * A standard YAWL sub-case worklet was selected.
     *
     * @param workletName the name of the YAWL worklet specification to launch as a sub-case
     * @param rdrNodeId   the ID of the RDR node whose conclusion selected this worklet;
     *                    useful for audit and rule feedback
     */
    record SubCaseSelection(String workletName, int rdrNodeId) implements WorkletSelection {
        public SubCaseSelection {
            if (workletName == null || workletName.isBlank()) {
                throw new IllegalArgumentException("workletName must not be null or blank");
            }
        }
    }

    /**
     * An autonomous A2A agent was selected for this work item.
     *
     * <p>The RDR conclusion had the form {@code "a2a:<endpoint>/<skill>"}, e.g.
     * {@code "a2a:http://agent:8090/risk_assessment"}.
     *
     * @param agentEndpoint the HTTP base URL of the A2A agent (e.g. {@code "http://agent:8090"})
     * @param skill         the skill / task name to invoke on the agent
     * @param rdrNodeId     the ID of the RDR node that triggered this routing
     */
    record A2AAgentSelection(String agentEndpoint, String skill, int rdrNodeId)
            implements WorkletSelection {
        public A2AAgentSelection {
            if (agentEndpoint == null || agentEndpoint.isBlank()) {
                throw new IllegalArgumentException("agentEndpoint must not be null or blank");
            }
            if (skill == null || skill.isBlank()) {
                throw new IllegalArgumentException("skill must not be null or blank");
            }
        }
    }

    /**
     * No rule was satisfied; the work item should continue with normal YAWL execution.
     */
    record NoSelection() implements WorkletSelection {}
}
