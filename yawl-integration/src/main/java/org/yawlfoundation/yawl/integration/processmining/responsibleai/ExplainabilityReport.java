/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.processmining.responsibleai;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Explainability report for a process decision.
 *
 * <p>Provides transparency into which rules/policies influenced a specific case's
 * outcome, enabling responsible process mining audits and fairness investigations.</p>
 *
 * <h2>Contents</h2>
 * <ul>
 *   <li>Applied rules: compliance rules that evaluated to true for this case</li>
 *   <li>Rule rationale: explanation for each rule's evaluation</li>
 *   <li>Overall decision: the resulting action/outcome</li>
 *   <li>Generation timestamp: when the report was created</li>
 * </ul>
 *
 * @param caseId case identifier
 * @param appliedRules list of rule names that triggered for this case
 * @param ruleRationale map of rule name to explanation (why it was/wasn't applied)
 * @param overallDecision the resulting process decision (e.g., "Approve", "Escalate")
 * @param generatedAt timestamp when report was created
 *
 * @author YAWL Foundation (Responsible Process Mining)
 * @version 6.0
 */
public record ExplainabilityReport(
    String caseId,
    List<String> appliedRules,
    Map<String, String> ruleRationale,
    String overallDecision,
    Instant generatedAt
) {
    /**
     * Construct an explainability report with validation.
     *
     * @param caseId case identifier (required)
     * @param appliedRules rules that triggered (required, may be empty)
     * @param ruleRationale explanations for each rule (required, may be empty)
     * @param overallDecision resulting decision (required)
     * @param generatedAt timestamp (required)
     * @throws NullPointerException if any parameter is null
     */
    public ExplainabilityReport {
        Objects.requireNonNull(caseId, "caseId is required");
        Objects.requireNonNull(appliedRules, "appliedRules is required");
        Objects.requireNonNull(ruleRationale, "ruleRationale is required");
        Objects.requireNonNull(overallDecision, "overallDecision is required");
        Objects.requireNonNull(generatedAt, "generatedAt is required");

        appliedRules = Collections.unmodifiableList(appliedRules);
        ruleRationale = Collections.unmodifiableMap(ruleRationale);
    }

    /**
     * Create a builder for constructing explainability reports.
     *
     * @param caseId case identifier
     * @return builder instance
     */
    public static Builder builder(String caseId) {
        return new Builder(caseId);
    }

    /**
     * Builder for ExplainabilityReport.
     */
    public static final class Builder {
        private final String caseId;
        private final java.util.List<String> appliedRules;
        private final java.util.Map<String, String> ruleRationale;
        private String overallDecision;
        private Instant generatedAt;

        private Builder(String caseId) {
            this.caseId = caseId;
            this.appliedRules = new java.util.ArrayList<>();
            this.ruleRationale = new java.util.HashMap<>();
            this.generatedAt = Instant.now();
        }

        /**
         * Add an applied rule.
         *
         * @param ruleName name of the rule that triggered
         * @return this builder
         */
        public Builder withAppliedRule(String ruleName) {
            this.appliedRules.add(ruleName);
            return this;
        }

        /**
         * Add rule rationale.
         *
         * @param ruleName name of the rule
         * @param rationale explanation for why it was/wasn't applied
         * @return this builder
         */
        public Builder withRuleRationale(String ruleName, String rationale) {
            this.ruleRationale.put(ruleName, rationale);
            return this;
        }

        /**
         * Set the overall decision.
         *
         * @param decision the resulting outcome
         * @return this builder
         */
        public Builder withDecision(String decision) {
            this.overallDecision = decision;
            return this;
        }

        /**
         * Set the timestamp when report was generated.
         *
         * @param timestamp generation time
         * @return this builder
         */
        public Builder withTimestamp(Instant timestamp) {
            this.generatedAt = timestamp;
            return this;
        }

        /**
         * Build the explainability report.
         *
         * @return constructed ExplainabilityReport
         * @throws IllegalStateException if overallDecision is not set
         */
        public ExplainabilityReport build() {
            if (overallDecision == null) {
                throw new IllegalStateException("overallDecision is required");
            }
            return new ExplainabilityReport(
                caseId,
                appliedRules,
                ruleRationale,
                overallDecision,
                generatedAt
            );
        }
    }
}
