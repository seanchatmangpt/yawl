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

package org.yawlfoundation.yawl.mcp.a2a.therapy.agents;

import org.yawlfoundation.yawl.mcp.a2a.therapy.OTSwarmAgent;
import org.yawlfoundation.yawl.mcp.a2a.therapy.SwarmPhase;
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.OTPatient;
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.SwarmTaskResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 1: Patient intake and clinical risk stratification.
 *
 * <p>Performs initial patient intake for occupational therapy referral.
 * Validates patient data, stratifies clinical risk, identifies contraindications,
 * and determines the appropriate OT programme pathway.</p>
 *
 * <h2>Risk Stratification</h2>
 * <ul>
 *   <li>HIGH: geriatric patient with neurological condition or falls risk</li>
 *   <li>MEDIUM: working-age patient with mental health or chronic condition</li>
 *   <li>LOW: paediatric or young adult with developmental goals</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class OTIntakeAgent implements OTSwarmAgent {

    private static final List<String> HIGH_RISK_CONDITIONS = List.of(
        "stroke", "traumatic brain injury", "spinal cord injury", "dementia",
        "parkinson", "multiple sclerosis", "hip fracture", "severe depression"
    );

    private static final List<String> INPATIENT_CONDITIONS = List.of(
        "stroke", "traumatic brain injury", "spinal cord injury", "hip fracture", "post-surgical"
    );

    /**
     * Returns the swarm phase this agent handles.
     *
     * @return SwarmPhase.INTAKE
     */
    @Override
    public SwarmPhase phase() {
        return SwarmPhase.INTAKE;
    }

    /**
     * Executes intake assessment for the given patient.
     *
     * <p>Validates patient data, performs clinical risk stratification, identifies
     * contraindications, and determines the appropriate occupational therapy programme.
     * Populates context with intake metadata for downstream agents.</p>
     *
     * @param patient the OTPatient to assess (non-null)
     * @param context mutable context map populated by this agent
     * @return SwarmTaskResult.success() with intake metadata, or SwarmTaskResult.failure()
     *         if validation fails
     */
    @Override
    public SwarmTaskResult execute(OTPatient patient, Map<String, Object> context) {
        if (patient == null) {
            return SwarmTaskResult.failure(
                agentId(), phase(),
                "Patient cannot be null"
            );
        }

        var riskLevel = stratifyRisk(patient);
        var programme = determineProgramme(patient);
        var contraindications = identifyContraindications(patient);

        var notes = buildIntakeNotes(patient, riskLevel, programme, contraindications);

        var data = new HashMap<String, Object>();
        data.put("intake_complete", true);
        data.put("risk_level", riskLevel);
        data.put("programme_type", programme);
        data.put("contraindications", contraindications);
        data.put("intake_notes", notes);

        var output = String.format(
            "Intake complete for %s (age %d, %s). Risk: %s. Programme: %s. Contraindications: %s.",
            patient.name(), patient.age(), patient.condition(),
            riskLevel, programme,
            contraindications.isEmpty() ? "none" : String.join(", ", contraindications)
        );

        return SwarmTaskResult.success(agentId(), phase(), output, data);
    }

    /**
     * Stratifies the clinical risk level based on patient age and condition.
     *
     * <p>Applies heuristics to determine HIGH, MEDIUM-HIGH, MEDIUM, LOW-MEDIUM, or LOW risk.
     * Geriatric patients with high-risk conditions are scored most severe.</p>
     *
     * @param patient the patient to assess
     * @return risk level as a string
     */
    private String stratifyRisk(OTPatient patient) {
        String conditionLower = patient.condition().toLowerCase();
        boolean isHighRiskCondition = HIGH_RISK_CONDITIONS.stream()
            .anyMatch(conditionLower::contains);

        if (isHighRiskCondition && patient.isGeriatric()) return "HIGH";
        if (isHighRiskCondition) return "MEDIUM-HIGH";
        if (patient.isGeriatric()) return "MEDIUM";
        if (patient.isPaediatric()) return "LOW-MEDIUM";
        return "MEDIUM";
    }

    /**
     * Determines the appropriate occupational therapy programme pathway.
     *
     * <p>Returns INPATIENT for acute/post-surgical conditions, COMMUNITY for geriatric
     * patients, and OUTPATIENT for others.</p>
     *
     * @param patient the patient to assess
     * @return programme type (INPATIENT, COMMUNITY, or OUTPATIENT)
     */
    private String determineProgramme(OTPatient patient) {
        String conditionLower = patient.condition().toLowerCase();
        if (INPATIENT_CONDITIONS.stream().anyMatch(conditionLower::contains)) {
            return "INPATIENT";
        }
        if (patient.isGeriatric()) return "COMMUNITY";
        return "OUTPATIENT";
    }

    /**
     * Identifies clinical contraindications based on patient condition.
     *
     * <p>Extracts keywords from the condition to flag precautions (weight-bearing,
     * cardiac monitoring, seizure precautions, etc.).</p>
     *
     * @param patient the patient to assess
     * @return list of contraindication strings (possibly empty)
     */
    private List<String> identifyContraindications(OTPatient patient) {
        var contraindications = new ArrayList<String>();
        String conditionLower = patient.condition().toLowerCase();

        if (conditionLower.contains("fracture") || conditionLower.contains("post-surgical")) {
            contraindications.add("weight-bearing restrictions apply");
        }
        if (conditionLower.contains("cardiac") || conditionLower.contains("heart")) {
            contraindications.add("monitor cardiac response during activity");
        }
        if (conditionLower.contains("seizure") || conditionLower.contains("epilepsy")) {
            contraindications.add("seizure precautions required");
        }
        return contraindications;
    }

    /**
     * Builds a structured intake assessment note as a narrative document.
     *
     * <p>Combines patient demographics, referral information, clinical risk assessment,
     * programme type, and identified contraindications into a clinical note.</p>
     *
     * @param patient the patient being assessed
     * @param riskLevel the computed risk level
     * @param programme the determined programme type
     * @param contraindications list of identified contraindications
     * @return formatted intake notes
     */
    private String buildIntakeNotes(OTPatient patient, String riskLevel,
                                     String programme, List<String> contraindications) {
        return String.format(
            """
            OT Intake Assessment
            Patient: %s (ID: %s)
            Age: %d | Condition: %s
            Referral reason: %s
            Patient goal: "%s"
            Risk level: %s | Programme: %s
            Contraindications: %s""",
            patient.name(), patient.id(),
            patient.age(), patient.condition(),
            patient.referralReason(),
            patient.functionalGoal(),
            riskLevel, programme,
            contraindications.isEmpty() ? "None identified" : String.join("; ", contraindications)
        );
    }
}
