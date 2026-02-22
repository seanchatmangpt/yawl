package org.yawlfoundation.yawl.integration.wizard.autonomic;

import org.yawlfoundation.yawl.integration.wizard.core.WizardSession;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Autonomic analyzer for the wizard system.
 *
 * <p>The analyzer is the 'A' in MAPE-K. It diagnoses root causes from
 * observed symptoms by consulting the knowledge base.
 *
 * <p>For each symptom, the analyzer:
 * <ul>
 *   <li>Looks up rules in the KnowledgeBase for the symptom type</li>
 *   <li>Selects the highest-priority rule</li>
 *   <li>Computes confidence based on evidence matching</li>
 *   <li>Produces a WizardDiagnosis with recommended recovery action</li>
 * </ul>
 *
 * <p>Confidence scoring: confidence = (matching evidence fields) / (total expected fields).
 * Higher confidence means more evidence supports the diagnosis.
 *
 * <p>Implementations are stateless and thread-safe.
 *
 * @see KnowledgeBase for diagnosis rules
 * @see WizardDiagnosis for output structure
 * @see AutonomicPlanner for downstream action planning
 */
public class AutonomicAnalyzer {

    private final KnowledgeBase knowledgeBase;

    /**
     * Creates a new autonomic analyzer with the given knowledge base.
     *
     * @param knowledgeBase the knowledge base to consult for rule lookups
     * @throws NullPointerException if knowledgeBase is null
     */
    public AutonomicAnalyzer(KnowledgeBase knowledgeBase) {
        this.knowledgeBase = Objects.requireNonNull(knowledgeBase, "knowledgeBase must not be null");
    }

    /**
     * Produce a diagnosis for a detected symptom.
     *
     * <p>Looks up rules in the knowledge base for the symptom type,
     * selects the highest-priority rule, and produces a diagnosis
     * with confidence based on available evidence.
     *
     * @param symptom the detected symptom to diagnose
     * @param session the wizard session (used for context)
     * @return a diagnosis with recommended recovery action and confidence
     * @throws NullPointerException if symptom or session is null
     * @throws IllegalStateException if no rules exist for symptom type
     */
    public WizardDiagnosis diagnose(WizardSymptom symptom, WizardSession session) {
        Objects.requireNonNull(symptom, "symptom must not be null");
        Objects.requireNonNull(session, "session must not be null");

        // Look up rules for this symptom type
        List<KnowledgeBase.Rule> applicableRules = knowledgeBase.rulesFor(symptom.type());

        if (applicableRules.isEmpty()) {
            throw new IllegalStateException(
                "No rules in knowledge base for symptom type: " + symptom.type()
            );
        }

        // Select highest-priority rule
        KnowledgeBase.Rule selectedRule = applicableRules.get(0);

        // Compute confidence: basic heuristic based on evidence count
        // Confidence = (evidence fields present) / (expected fields)
        // For now, use a simple formula: confidence = min(1.0, 0.5 + 0.5 * evidence.size() / 3)
        double confidence = computeConfidence(symptom);

        // Build list of possible actions (the selected rule + alternatives of lower priority)
        List<WizardDiagnosis.RecoveryAction> possibleActions = new ArrayList<>();
        possibleActions.add(selectedRule.recommendedAction());
        for (int i = 1; i < Math.min(3, applicableRules.size()); i++) {
            KnowledgeBase.Rule altRule = applicableRules.get(i);
            if (!possibleActions.contains(altRule.recommendedAction())) {
                possibleActions.add(altRule.recommendedAction());
            }
        }

        // Construct root cause explanation
        String rootCause = buildRootCauseExplanation(symptom, selectedRule);

        return WizardDiagnosis.of(
            symptom,
            rootCause,
            selectedRule.recommendedAction(),
            confidence,
            possibleActions
        );
    }

    /**
     * Diagnose all symptoms and return prioritized list.
     *
     * <p>Produces a diagnosis for each symptom, then sorts by:
     * <ul>
     *   <li>Symptom severity (CRITICAL > WARNING > INFO)</li>
     *   <li>Diagnosis confidence (higher first)</li>
     * </ul>
     *
     * <p>This ordering ensures critical diagnoses are addressed first.
     *
     * @param symptoms list of detected symptoms
     * @param session the wizard session (used for context)
     * @return list of diagnoses sorted by priority (critical first)
     * @throws NullPointerException if symptoms or session is null
     * @throws IllegalStateException if any symptom has no matching rules
     */
    public List<WizardDiagnosis> diagnoseAll(List<WizardSymptom> symptoms, WizardSession session) {
        Objects.requireNonNull(symptoms, "symptoms must not be null");
        Objects.requireNonNull(session, "session must not be null");

        return symptoms.stream()
            .map(symptom -> diagnose(symptom, session))
            .sorted(Comparator
                // Sort by symptom severity (CRITICAL first)
                .comparing((WizardDiagnosis d) -> d.symptom().severity(),
                    Comparator.reverseOrder())
                // Then by confidence (higher first)
                .thenComparing(WizardDiagnosis::confidence,
                    Comparator.reverseOrder())
            )
            .toList();
    }

    /**
     * Compute confidence score for a symptom diagnosis.
     *
     * <p>Confidence is based on the amount and quality of evidence.
     * Simple heuristic: 0.5 base + 0.5 scaled by evidence count (max 3 fields).
     *
     * @param symptom the symptom to score
     * @return confidence in range [0.0, 1.0]
     */
    private double computeConfidence(WizardSymptom symptom) {
        // Base confidence is 0.5 (moderate belief before seeing evidence)
        double baseConfidence = 0.5;

        // Add incremental confidence based on evidence count
        // Each evidence field adds 0.166 (max 3 fields = +0.5)
        int evidenceCount = symptom.evidence().size();
        double evidenceBonus = Math.min(0.5, (evidenceCount / 3.0) * 0.5);

        return Math.min(1.0, baseConfidence + evidenceBonus);
    }

    /**
     * Build a human-readable explanation of the root cause.
     *
     * <p>Combines the symptom description with the rule's rationale
     * to produce a meaningful explanation of why the symptom occurred.
     *
     * @param symptom the detected symptom
     * @param rule the selected knowledge base rule
     * @return root cause explanation text
     */
    private String buildRootCauseExplanation(WizardSymptom symptom, KnowledgeBase.Rule rule) {
        return switch (symptom.type()) {
            case DISCOVERY_EMPTY ->
                "No MCP tools or A2A agents were discovered during the discovery phase. " +
                "This may indicate the discovery service is unavailable or no resources are configured.";

            case PHASE_STALLED ->
                "The wizard has been stuck in the " + symptom.phase() + " phase for multiple events. " +
                "This suggests the step is blocked or requires external input that is not available.";

            case CONFIG_CONFLICT ->
                "The MCP tool configuration and A2A agent configuration have incompatible requirements. " +
                "The number or type of tools does not match the number or type of agents.";

            case PATTERN_UNSOUND ->
                "The selected workflow pattern (" + symptom.evidence().getOrDefault("pattern", "unknown") + ") " +
                "failed Petri net soundness validation. The pattern may have deadlocks or unreachable states.";

            case VALIDATION_FAILED ->
                "The configuration validation check failed. " +
                "Reason: " + symptom.evidence().getOrDefault("error", "validation constraints not met");

            case DEPLOYMENT_FAILED ->
                "The deployment phase could not apply the configuration to the runtime engine. " +
                "Reason: " + symptom.evidence().getOrDefault("deploymentError", "engine not available");
        };
    }
}
