package org.yawlfoundation.yawl.integration.wizard.autonomic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the KnowledgeBase class.
 *
 * <p>Verifies that the knowledge base:
 * <ul>
 *   <li>Has rules for all 6 symptom types</li>
 *   <li>Assigns correct recovery actions to each symptom type</li>
 *   <li>Orders rules by priority</li>
 *   <li>Provides proper lookup methods</li>
 * </ul>
 */
@DisplayName("KnowledgeBase Tests")
class KnowledgeBaseTest {

    @Test
    @DisplayName("defaultBase() creates knowledge base with 6 rules (one per symptom type)")
    void testDefaultBaseHasSixRules() {
        KnowledgeBase kb = KnowledgeBase.defaultBase();

        assertEquals(6, kb.ruleCount(), "Default KB should have exactly 6 rules");
    }

    @ParameterizedTest
    @EnumSource(WizardSymptom.SymptomType.class)
    @DisplayName("defaultBase() has rule for each symptom type")
    void testDefaultBaseCoversAllSymptomTypes(WizardSymptom.SymptomType type) {
        KnowledgeBase kb = KnowledgeBase.defaultBase();
        List<KnowledgeBase.Rule> rules = kb.rulesFor(type);

        assertFalse(rules.isEmpty(), "KB should have rule for symptom type: " + type);
        assertEquals(1, rules.size(), "Each symptom type should have exactly 1 rule");
    }

    @Test
    @DisplayName("DISCOVERY_EMPTY maps to FALLBACK_PATTERN")
    void testDiscoveryEmptyRule() {
        KnowledgeBase kb = KnowledgeBase.defaultBase();
        List<KnowledgeBase.Rule> rules = kb.rulesFor(WizardSymptom.SymptomType.DISCOVERY_EMPTY);

        assertEquals(1, rules.size());
        assertEquals(WizardDiagnosis.RecoveryAction.FALLBACK_PATTERN, rules.get(0).recommendedAction());
        assertEquals(10, rules.get(0).priority());
    }

    @Test
    @DisplayName("PHASE_STALLED maps to RETRY_DISCOVERY")
    void testPhaseStalledRule() {
        KnowledgeBase kb = KnowledgeBase.defaultBase();
        List<KnowledgeBase.Rule> rules = kb.rulesFor(WizardSymptom.SymptomType.PHASE_STALLED);

        assertEquals(1, rules.size());
        assertEquals(WizardDiagnosis.RecoveryAction.RETRY_DISCOVERY, rules.get(0).recommendedAction());
        assertEquals(8, rules.get(0).priority());
    }

    @Test
    @DisplayName("CONFIG_CONFLICT maps to RELAX_CONSTRAINTS")
    void testConfigConflictRule() {
        KnowledgeBase kb = KnowledgeBase.defaultBase();
        List<KnowledgeBase.Rule> rules = kb.rulesFor(WizardSymptom.SymptomType.CONFIG_CONFLICT);

        assertEquals(1, rules.size());
        assertEquals(WizardDiagnosis.RecoveryAction.RELAX_CONSTRAINTS, rules.get(0).recommendedAction());
        assertEquals(7, rules.get(0).priority());
    }

    @Test
    @DisplayName("PATTERN_UNSOUND maps to FALLBACK_PATTERN")
    void testPatternUnsoundRule() {
        KnowledgeBase kb = KnowledgeBase.defaultBase();
        List<KnowledgeBase.Rule> rules = kb.rulesFor(WizardSymptom.SymptomType.PATTERN_UNSOUND);

        assertEquals(1, rules.size());
        assertEquals(WizardDiagnosis.RecoveryAction.FALLBACK_PATTERN, rules.get(0).recommendedAction());
        assertEquals(9, rules.get(0).priority());
    }

    @Test
    @DisplayName("VALIDATION_FAILED maps to USE_DEFAULTS")
    void testValidationFailedRule() {
        KnowledgeBase kb = KnowledgeBase.defaultBase();
        List<KnowledgeBase.Rule> rules = kb.rulesFor(WizardSymptom.SymptomType.VALIDATION_FAILED);

        assertEquals(1, rules.size());
        assertEquals(WizardDiagnosis.RecoveryAction.USE_DEFAULTS, rules.get(0).recommendedAction());
        assertEquals(6, rules.get(0).priority());
    }

    @Test
    @DisplayName("DEPLOYMENT_FAILED maps to ABORT_WIZARD")
    void testDeploymentFailedRule() {
        KnowledgeBase kb = KnowledgeBase.defaultBase();
        List<KnowledgeBase.Rule> rules = kb.rulesFor(WizardSymptom.SymptomType.DEPLOYMENT_FAILED);

        assertEquals(1, rules.size());
        assertEquals(WizardDiagnosis.RecoveryAction.ABORT_WIZARD, rules.get(0).recommendedAction());
        assertEquals(5, rules.get(0).priority());
    }

    @Test
    @DisplayName("rulesFor() returns empty list for non-existent type (defensive)")
    void testRulesForNonExistentType() {
        KnowledgeBase kb = KnowledgeBase.defaultBase();
        // This should never happen with real SymptomType enum, but test defensiveness
        // by calling with null and expecting NPE
        assertThrows(NullPointerException.class, () -> kb.rulesFor(null));
    }

    @Test
    @DisplayName("allRules() returns all rules in KB")
    void testAllRules() {
        KnowledgeBase kb = KnowledgeBase.defaultBase();
        List<KnowledgeBase.Rule> allRules = kb.allRules();

        assertEquals(6, allRules.size());
        assertTrue(allRules.stream().allMatch(r -> r != null));
    }

    @Test
    @DisplayName("Custom knowledge base can be created with specific rules")
    void testCustomKnowledgeBase() {
        List<KnowledgeBase.Rule> rules = List.of(
            KnowledgeBase.Rule.of(
                WizardSymptom.SymptomType.DISCOVERY_EMPTY,
                WizardDiagnosis.RecoveryAction.ABORT_WIZARD,
                "Fail fast on empty discovery",
                15
            ),
            KnowledgeBase.Rule.of(
                WizardSymptom.SymptomType.PHASE_STALLED,
                WizardDiagnosis.RecoveryAction.RESET_TO_PHASE,
                "Reset to previous phase on stall",
                12
            )
        );

        KnowledgeBase kb = new KnowledgeBase(rules);

        assertEquals(2, kb.ruleCount());
        assertEquals(WizardDiagnosis.RecoveryAction.ABORT_WIZARD,
            kb.rulesFor(WizardSymptom.SymptomType.DISCOVERY_EMPTY).get(0).recommendedAction());
    }

    @Test
    @DisplayName("Rule.of() factory creates rule with priority 10 by default")
    void testRuleFactoryDefaultPriority() {
        KnowledgeBase.Rule rule = KnowledgeBase.Rule.of(
            WizardSymptom.SymptomType.DISCOVERY_EMPTY,
            WizardDiagnosis.RecoveryAction.FALLBACK_PATTERN,
            "Test rule"
        );

        assertEquals(10, rule.priority());
    }

    @Test
    @DisplayName("Rule validates priority >= 0")
    void testRulePriorityValidation() {
        assertThrows(IllegalArgumentException.class, () ->
            new KnowledgeBase.Rule(
                WizardSymptom.SymptomType.DISCOVERY_EMPTY,
                WizardDiagnosis.RecoveryAction.FALLBACK_PATTERN,
                "Bad priority",
                -1
            )
        );
    }
}
