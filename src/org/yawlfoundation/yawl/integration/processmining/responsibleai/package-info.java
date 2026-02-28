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

/**
 * Responsible Process Mining framework for fairness, bias detection, and explainability.
 *
 * <h2>Overview</h2>
 * <p>This package implements van der Aalst's "Responsible Process Mining" (2022-2024)
 * framework to ensure that discovered and deployed process models are:
 * <ul>
 *   <li><b>Fair</b> - No demographic bias in decisions (fairness analysis)</li>
 *   <li><b>Unbiased</b> - Attributes don't have spurious correlation with outcomes (bias detection)</li>
 *   <li><b>Explainable</b> - Users understand why specific decisions were made (explainability)</li>
 * </ul>
 * </p>
 *
 * <h2>Key Components</h2>
 * <ul>
 *   <li><b>FairnessAnalyzer</b> - Demographic parity and disparate impact detection (four-fifths rule)</li>
 *   <li><b>BiasDetector</b> - Categorical correlation analysis using Cramér's V</li>
 *   <li><b>ExplainabilityReport</b> - Decision rationale and rule tracing</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>
 * // Fairness analysis
 * FairnessAnalyzer.FairnessReport fairness = FairnessAnalyzer.analyze(
 *     caseAttributes,
 *     decisions,
 *     "department",      // sensitive attribute
 *     "approved"         // positive outcome
 * );
 *
 * if (!fairness.isFair()) {
 *     System.out.println("Fairness violation: " + fairness.violations());
 * }
 *
 * // Bias detection
 * BiasDetector.BiasReport bias = BiasDetector.detect(
 *     caseAttributes,
 *     decisions,
 *     "decision"         // outcome attribute
 * );
 *
 * for (String attr : bias.highBiasAttributes()) {
 *     System.out.println("High-bias attribute: " + attr);
 * }
 *
 * // Explainability
 * ExplainabilityReport report = ExplainabilityReport.builder(caseId)
 *     .withAppliedRule("budget_limit_exceeded")
 *     .withRuleRationale("budget_limit_exceeded",
 *         "Case total exceeds budget threshold of $50,000")
 *     .withDecision("Escalate")
 *     .build();
 * </pre>
 *
 * <h2>Fairness Metrics</h2>
 * <p><b>Demographic Parity:</b>
 * <ul>
 *   <li>Measures: min(rate_A) / max(rate_A) where rate_A = P(positive|group=A)</li>
 *   <li>Range: 1.0 (perfect parity) to 0.0 (maximum disparity)</li>
 *   <li>Interpretation: Decision rates should be similar across demographic groups</li>
 * </ul>
 *
 * <p><b>Disparate Impact (Four-Fifths Rule):</b>
 * <ul>
 *   <li>Measures: min(rate_A/rate_B, rate_B/rate_A)</li>
 *   <li>Threshold: ≥ 0.8 = fair (US EEOC legal standard)</li>
 *   <li>Formula violation (< 0.8) indicates illegal discrimination</li>
 * </ul>
 * </p>
 *
 * <h2>Bias Detection</h2>
 * <p>Uses Cramér's V statistic for categorical variables:
 * <ul>
 *   <li>0.0 = No association (no bias)</li>
 *   <li>1.0 = Perfect association (severe bias)</li>
 *   <li>Threshold: > 0.3 = potential bias (customizable)</li>
 * </ul>
 * </p>
 *
 * <h2>Responsible Process Mining Principles</h2>
 * <p>Van der Aalst 2022-2024:
 * <ol>
 *   <li><b>Transparency:</b> Process outcomes must be explainable</li>
 *   <li><b>Fairness:</b> No demographic discrimination in decisions</li>
 *   <li><b>Accountability:</b> Audit trails track rule applications</li>
 *   <li><b>Improvement:</b> Feedback loops to identify and fix bias</li>
 * </ol>
 * </p>
 *
 * @author YAWL Foundation (Wil van der Aalst, Responsible Process Mining)
 * @version 6.0
 */
package org.yawlfoundation.yawl.integration.processmining.responsibleai;
