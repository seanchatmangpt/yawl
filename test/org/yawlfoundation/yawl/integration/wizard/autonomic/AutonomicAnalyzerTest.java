package org.yawlfoundation.yawl.integration.wizard.autonomic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.integration.wizard.core.WizardPhase;
import org.yawlfoundation.yawl.integration.wizard.core.WizardSession;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the AutonomicAnalyzer class.
 *
 * <p>Verifies that the analyzer:
 * <ul>
 *   <li>Diagnoses root causes from symptoms using the knowledge base</li>
 *   <li>Assigns confidence scores based on evidence</li>
 *   <li>Prioritizes diagnoses by severity and confidence</li>
 *   <li>Returns alternative possible actions</li>
 * </ul>
 */
@DisplayName("AutonomicAnalyzer Tests")
class AutonomicAnalyzerTest {

    private final KnowledgeBase knowledgeBase = KnowledgeBase.defaultBase();
    private final AutonomicAnalyzer analyzer = new AutonomicAnalyzer(knowledgeBase);

    @Test
    @DisplayName("diagnose() produces diagnosis with correct action for each symptom type")
    void testDiagnoseReturnsCorrectAction() {
        WizardSession session = WizardSession.newSession();

        WizardSymptom discoveryEmpty = WizardSymptom.of(
            WizardPhase.DISCOVERY,
            WizardSymptom.SymptomType.DISCOVERY_EMPTY,
            "No agents found"
        );

        WizardDiagnosis diagnosis = analyzer.diagnose(discoveryEmpty, session);

        assertEquals(WizardDiagnosis.RecoveryAction.FALLBACK_PATTERN, diagnosis.recommendation());
        assertTrue(diagnosis.confidence() >= 0.0 && diagnosis.confidence() <= 1.0);
    }

    @Test
    @DisplayName("diagnose() includes multiple possible actions")
    void testDiagnosisIncludesAlternativeActions() {
        WizardSession session = WizardSession.newSession();

        WizardSymptom symptom = WizardSymptom.of(
            WizardPhase.PATTERN_SELECTION,
            WizardSymptom.SymptomType.PHASE_STALLED,
            "Stuck in pattern selection"
        );

        WizardDiagnosis diagnosis = analyzer.diagnose(symptom, session);

        assertFalse(diagnosis.possibleActions().isEmpty());
        assertTrue(diagnosis.possibleActions().contains(diagnosis.recommendation()));
    }

    @Test
    @DisplayName("diagnose() computes confidence based on evidence")
    void testConfidenceBasedOnEvidence() {
        WizardSession session = WizardSession.newSession();

        // Symptom with no evidence
        WizardSymptom symptomNoEvidence = WizardSymptom.of(
            WizardPhase.DISCOVERY,
            WizardSymptom.SymptomType.DISCOVERY_EMPTY,
            "No agents"
        );

        // Symptom with evidence
        WizardSymptom symptomWithEvidence = WizardSymptom.of(
            WizardPhase.DISCOVERY,
            WizardSymptom.SymptomType.DISCOVERY_EMPTY,
            "No agents",
            Map.of(
                "agentCount", 0,
                "toolCount", 0,
                "phase", "DISCOVERY"
            ),
            WizardSymptom.Severity.WARNING
        );

        WizardDiagnosis diagnosis1 = analyzer.diagnose(symptomNoEvidence, session);
        WizardDiagnosis diagnosis2 = analyzer.diagnose(symptomWithEvidence, session);

        // Diagnosis with evidence should have higher confidence
        assertTrue(diagnosis2.confidence() >= diagnosis1.confidence());
    }

    @Test
    @DisplayName("diagnoseAll() sorts by symptom severity (CRITICAL first)")
    void testDiagnoseAllSortsBySeverity() {
        WizardSession session = WizardSession.newSession();

        List<WizardSymptom> symptoms = List.of(
            WizardSymptom.of(
                WizardPhase.DISCOVERY,
                WizardSymptom.SymptomType.DISCOVERY_EMPTY,
                "No agents",
                Map.of(),
                WizardSymptom.Severity.INFO
            ),
            WizardSymptom.of(
                WizardPhase.VALIDATION,
                WizardSymptom.SymptomType.VALIDATION_FAILED,
                "Validation failed",
                Map.of(),
                WizardSymptom.Severity.CRITICAL
            ),
            WizardSymptom.of(
                WizardPhase.PATTERN_SELECTION,
                WizardSymptom.SymptomType.PHASE_STALLED,
                "Phase stalled",
                Map.of(),
                WizardSymptom.Severity.WARNING
            )
        );

        List<WizardDiagnosis> diagnoses = analyzer.diagnoseAll(symptoms, session);

        // First diagnosis should be CRITICAL severity
        assertEquals(WizardSymptom.Severity.CRITICAL, diagnoses.get(0).symptom().severity());
    }

    @Test
    @DisplayName("diagnoseAll() with empty symptom list returns empty diagnoses")
    void testDiagnoseAllEmptySymptoms() {
        WizardSession session = WizardSession.newSession();

        List<WizardDiagnosis> diagnoses = analyzer.diagnoseAll(List.of(), session);

        assertTrue(diagnoses.isEmpty());
    }

    @Test
    @DisplayName("diagnose() throws if symptom type has no rules (defensive)")
    void testDiagnoseThrowsIfNoRules() {
        WizardSession session = WizardSession.newSession();

        // Create KB with no rules for a symptom type
        KnowledgeBase emptyKB = new KnowledgeBase(List.of(
            KnowledgeBase.Rule.of(
                WizardSymptom.SymptomType.DISCOVERY_EMPTY,
                WizardDiagnosis.RecoveryAction.FALLBACK_PATTERN,
                "Only one rule"
            )
        ));

        AutonomicAnalyzer analyzerWithLimitedKB = new AutonomicAnalyzer(emptyKB);

        WizardSymptom symptom = WizardSymptom.of(
            WizardPhase.VALIDATION,
            WizardSymptom.SymptomType.VALIDATION_FAILED,
            "Validation failed"
        );

        assertThrows(IllegalStateException.class, () -> analyzerWithLimitedKB.diagnose(symptom, session));
    }

    @Test
    @DisplayName("diagnosis includes human-readable root cause explanation")
    void testDiagnosisHasRootCauseExplanation() {
        WizardSession session = WizardSession.newSession();

        WizardSymptom symptom = WizardSymptom.of(
            WizardPhase.DISCOVERY,
            WizardSymptom.SymptomType.DISCOVERY_EMPTY,
            "No agents"
        );

        WizardDiagnosis diagnosis = analyzer.diagnose(symptom, session);

        assertNotNull(diagnosis.rootCause());
        assertFalse(diagnosis.rootCause().isEmpty());
        assertTrue(diagnosis.rootCause().length() > 10, "Root cause should be descriptive");
    }

    @Test
    @DisplayName("analyzer requires non-null knowledge base")
    void testAnalyzerRequiresNonNullKnowledgeBase() {
        assertThrows(NullPointerException.class, () -> new AutonomicAnalyzer(null));
    }

    @Test
    @DisplayName("diagnosisAll() preserves all diagnoses even with duplicates")
    void testDiagnoseAllPreservesDuplicateSymptoms() {
        WizardSession session = WizardSession.newSession();

        // Two instances of the same symptom type
        List<WizardSymptom> symptoms = List.of(
            WizardSymptom.of(WizardPhase.DISCOVERY, WizardSymptom.SymptomType.DISCOVERY_EMPTY, "No agents 1"),
            WizardSymptom.of(WizardPhase.DISCOVERY, WizardSymptom.SymptomType.DISCOVERY_EMPTY, "No agents 2")
        );

        List<WizardDiagnosis> diagnoses = analyzer.diagnoseAll(symptoms, session);

        // Both should be diagnosed
        assertEquals(2, diagnoses.size());
    }
}
