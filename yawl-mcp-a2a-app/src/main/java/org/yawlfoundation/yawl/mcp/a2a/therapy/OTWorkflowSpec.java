/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.therapy;

/**
 * YAWL workflow specification for the Occupational Therapy Lifestyle Redesign Swarm.
 *
 * <p>Encodes the 8-phase OT workflow as a YAML specification compatible with
 * {@link org.yawlfoundation.yawl.mcp.a2a.example.ExtendedYamlConverter}.
 * The workflow implements the AGT-5 multi-agent orchestration pattern adapted
 * for the clinical occupational therapy lifecycle.</p>
 *
 * <h2>Workflow Structure</h2>
 * <p>Sequential phases with one adaptive feedback loop:</p>
 * <pre>
 * PatientIntake → OccupationalAssessment → GoalSetting → InterventionPlanning
 *   → SessionScheduling → ProgressMonitoring
 *     ├─ [progress ≥ 85%] → OutcomeEvaluation → end
 *     └─ [progress < 85%] → PlanAdaptation → InterventionPlanning (loop)
 * </pre>
 *
 * <h2>Workflow Variables</h2>
 * <ul>
 *   <li>{@code patientId}: Patient identifier (xs:string)</li>
 *   <li>{@code progressScore}: Goal attainment scaling score 0-100 (xs:double)</li>
 *   <li>{@code adaptationCycle}: Current plan adaptation iteration (xs:integer)</li>
 *   <li>{@code caseStartTime}: ISO 8601 timestamp when case was launched (xs:string)</li>
 * </ul>
 *
 * <h2>Decision Rule</h2>
 * <p>At ProgressMonitoring, the workflow branches based on progressScore:
 * If progressScore ≥ 85, proceed to OutcomeEvaluation (successful conclusion).
 * If progressScore < 85, proceed to PlanAdaptation (iterate on therapy plan).
 * Adaptation loops back to InterventionPlanning for a new iteration.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class OTWorkflowSpec {

    /**
     * Resource path for the YAML workflow spec on the classpath.
     *
     * <p>The YAML file is loaded by {@link org.yawlfoundation.yawl.mcp.a2a.example.ExtendedYamlConverter}
     * to produce YAWL XML suitable for engine execution.</p>
     */
    public static final String RESOURCE_PATH = "patterns/therapy/ot-lifestyle-redesign-swarm.yaml";

    /**
     * Inline YAML workflow specification (fallback if resource is unavailable).
     *
     * <p>This YAML adheres to the ExtendedYamlConverter format:
     * - Uses extended syntax for XPath-based decisions
     * - Task IDs map to YAWL workflow task identifiers
     * - Conditions use XPath expressions on workflow variables</p>
     */
    public static final String THERAPY_WORKFLOW_YAML = """
        name: OccupationalTherapyLifestyleRedesign
        uri: OTSwarm.xml
        first: PatientIntake

        variables:
          - name: patientId
            type: xs:string
          - name: progressScore
            type: xs:double
            default: "0.0"
          - name: adaptationCycle
            type: xs:integer
            default: "0"
          - name: caseStartTime
            type: xs:string
            default: ""

        tasks:
          - id: PatientIntake
            flows: [OccupationalAssessment]
            split: xor
            join: xor
            description: "Agent 1: Patient intake and clinical risk stratification"

          - id: OccupationalAssessment
            flows: [GoalSetting]
            split: xor
            join: xor
            description: "Agent 2: COPM occupational performance assessment"

          - id: GoalSetting
            flows: [InterventionPlanning]
            split: xor
            join: xor
            description: "Agent 3: Collaborative SMART lifestyle goal identification"

          - id: InterventionPlanning
            flows: [SessionScheduling]
            split: xor
            join: xor
            description: "Agent 4: Evidence-based intervention selection"

          - id: SessionScheduling
            flows: [ProgressMonitoring]
            split: xor
            join: xor
            description: "Agent 5: Therapy session scheduling and resource allocation"

          - id: ProgressMonitoring
            flows: [OutcomeEvaluation, PlanAdaptation]
            condition: "/root/progressScore >= 85.0 -> OutcomeEvaluation"
            default: PlanAdaptation
            split: xor
            join: xor
            description: "Agent 6: Goal attainment scaling and progress evaluation"

          - id: PlanAdaptation
            flows: [InterventionPlanning]
            split: xor
            join: xor
            description: "Agent 7: Dynamic therapy plan adaptation"

          - id: OutcomeEvaluation
            flows: [end]
            split: xor
            join: xor
            description: "Agent 8: Final COPM re-assessment and discharge planning"
        """;

    private OTWorkflowSpec() {
        throw new UnsupportedOperationException("OTWorkflowSpec is a utility class and must not be instantiated");
    }
}
