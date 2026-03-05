package org.yawlfoundation.yawl.integration.wizard.autonomic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Shared knowledge base for the MAPE-K autonomic control loop.
 *
 * <p>The Knowledge base ('K' in MAPE-K) contains accumulated wisdom about
 * wizard failure modes and their remedies. It maps symptom types to
 * recommended recovery actions with priority and rationale.
 *
 * <p>The knowledge base is consulted by:
 * <ul>
 *   <li>{@link AutonomicAnalyzer} to diagnose root causes from symptoms</li>
 *   <li>{@link AutonomicPlanner} to select recovery actions for diagnoses</li>
 * </ul>
 *
 * <p>Create a default base with {@link #defaultBase()}, or supply custom rules
 * via the constructor for specialized autonomic policies.
 *
 * @see Rule for symptom-to-action mapping structure
 * @see AutonomicAnalyzer for consumption pattern
 */
public class KnowledgeBase {

    /**
     * A rule mapping a symptom type to a recommended recovery action.
     *
     * <p>Rules are the core knowledge atoms. Each rule encodes:
     * <ul>
     *   <li>trigger: the SymptomType that activates this rule</li>
     *   <li>recommendedAction: the RecoveryAction to take if this rule fires</li>
     *   <li>rationale: why this action is recommended for this symptom</li>
     *   <li>priority: relative importance (higher = more preferred)</li>
     * </ul>
     *
     * <p>Rules are immutable and stateless.
     */
    public record Rule(
            WizardSymptom.SymptomType trigger,
            WizardDiagnosis.RecoveryAction recommendedAction,
            String rationale,
            int priority
    ) {
        /**
         * Compact constructor validates non-null fields.
         */
        public Rule {
            Objects.requireNonNull(trigger, "trigger must not be null");
            Objects.requireNonNull(recommendedAction, "recommendedAction must not be null");
            Objects.requireNonNull(rationale, "rationale must not be null");
            if (priority < 0) {
                throw new IllegalArgumentException("priority must be non-negative");
            }
        }

        /**
         * Factory method: creates a rule with default priority (10).
         *
         * @param trigger the symptom type that activates this rule
         * @param recommendedAction the recovery action to recommend
         * @param rationale why this action is recommended
         * @return new rule with priority 10
         */
        public static Rule of(
                WizardSymptom.SymptomType trigger,
                WizardDiagnosis.RecoveryAction recommendedAction,
                String rationale
        ) {
            return new Rule(trigger, recommendedAction, rationale, 10);
        }

        /**
         * Factory method: creates a rule with explicit priority.
         *
         * @param trigger the symptom type that activates this rule
         * @param recommendedAction the recovery action to recommend
         * @param rationale why this action is recommended
         * @param priority higher = more preferred when multiple rules apply
         * @return new rule
         */
        public static Rule of(
                WizardSymptom.SymptomType trigger,
                WizardDiagnosis.RecoveryAction recommendedAction,
                String rationale,
                int priority
        ) {
            return new Rule(trigger, recommendedAction, rationale, priority);
        }
    }

    private final List<Rule> rules;

    /**
     * Constructor: creates a knowledge base with the given rules.
     *
     * @param rules immutable list of rules (defensive copy made)
     * @throws NullPointerException if rules is null
     */
    public KnowledgeBase(List<Rule> rules) {
        Objects.requireNonNull(rules, "rules must not be null");
        this.rules = List.copyOf(rules);
    }

    /**
     * Factory method: creates the default knowledge base with built-in rules.
     *
     * <p>The default base defines recovery rules for all 6 SymptomTypes:
     * <ul>
     *   <li>DISCOVERY_EMPTY → FALLBACK_PATTERN (priority 10)</li>
     *   <li>PHASE_STALLED → RETRY_DISCOVERY (priority 8)</li>
     *   <li>CONFIG_CONFLICT → RELAX_CONSTRAINTS (priority 7)</li>
     *   <li>PATTERN_UNSOUND → FALLBACK_PATTERN (priority 9)</li>
     *   <li>VALIDATION_FAILED → USE_DEFAULTS (priority 6)</li>
     *   <li>DEPLOYMENT_FAILED → ABORT_WIZARD (priority 5)</li>
     * </ul>
     *
     * <p>The priorities are chosen to prefer lighter-weight recovery actions
     * (e.g., USE_DEFAULTS at priority 6) over heavier ones (e.g., RESET_TO_PHASE).
     *
     * @return default knowledge base with built-in heuristics
     */
    public static KnowledgeBase defaultBase() {
        List<Rule> rules = new ArrayList<>();

        // DISCOVERY_EMPTY: no agents/tools found
        // Use fallback pattern (Sequence) which doesn't require any agents
        rules.add(Rule.of(
            WizardSymptom.SymptomType.DISCOVERY_EMPTY,
            WizardDiagnosis.RecoveryAction.FALLBACK_PATTERN,
            "Discovery found no agents/tools; use simple Sequence pattern that requires no external discovery",
            10
        ));

        // PHASE_STALLED: wizard stuck in same phase too long
        // Retry discovery in case resources became available or phase logic had transient issue
        rules.add(Rule.of(
            WizardSymptom.SymptomType.PHASE_STALLED,
            WizardDiagnosis.RecoveryAction.RETRY_DISCOVERY,
            "Session stalled in current phase; retrying discovery may unlock progress",
            8
        ));

        // CONFIG_CONFLICT: MCP and A2A configs incompatible
        // Relax constraints to allow partial configurations
        rules.add(Rule.of(
            WizardSymptom.SymptomType.CONFIG_CONFLICT,
            WizardDiagnosis.RecoveryAction.RELAX_CONSTRAINTS,
            "Configuration requirements conflicting; relax constraints to allow partial config",
            7
        ));

        // PATTERN_UNSOUND: pattern failed Petri net validation
        // Fall back to guaranteed-sound Sequence pattern
        rules.add(Rule.of(
            WizardSymptom.SymptomType.PATTERN_UNSOUND,
            WizardDiagnosis.RecoveryAction.FALLBACK_PATTERN,
            "Chosen pattern is unsound; fall back to guaranteed-sound Sequence pattern",
            9
        ));

        // VALIDATION_FAILED: overall validation failed
        // Use sensible defaults to fill gaps in configuration
        rules.add(Rule.of(
            WizardSymptom.SymptomType.VALIDATION_FAILED,
            WizardDiagnosis.RecoveryAction.USE_DEFAULTS,
            "Validation failed due to incomplete config; populate missing fields with defaults",
            6
        ));

        // DEPLOYMENT_FAILED: deployment to engine failed
        // No recovery possible; abort wizard
        rules.add(Rule.of(
            WizardSymptom.SymptomType.DEPLOYMENT_FAILED,
            WizardDiagnosis.RecoveryAction.ABORT_WIZARD,
            "Deployment failed; cannot proceed without engine connection",
            5
        ));

        return new KnowledgeBase(rules);
    }

    /**
     * Retrieves all rules triggered by a specific symptom type.
     *
     * <p>Returns rules sorted by priority (highest first).
     *
     * @param type the symptom type to look up
     * @return list of matching rules sorted by priority descending
     * @throws NullPointerException if type is null
     */
    public List<Rule> rulesFor(WizardSymptom.SymptomType type) {
        Objects.requireNonNull(type, "type must not be null");
        return rules.stream()
            .filter(r -> r.trigger() == type)
            .sorted(Comparator.comparingInt(Rule::priority).reversed())
            .toList();
    }

    /**
     * Returns all rules in the knowledge base.
     *
     * @return unmodifiable list of all rules
     */
    public List<Rule> allRules() {
        return rules;
    }

    /**
     * Returns the number of rules in the knowledge base.
     *
     * @return count of rules
     */
    public int ruleCount() {
        return rules.size();
    }
}
