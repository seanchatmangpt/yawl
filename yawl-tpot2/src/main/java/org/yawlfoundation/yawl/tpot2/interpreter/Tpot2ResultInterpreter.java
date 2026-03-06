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
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.tpot2.interpreter;

import org.yawlfoundation.yawl.tpot2.Tpot2Result;
import org.yawlfoundation.yawl.tpot2.Tpot2TaskType;

/**
 * Interprets TPOT2 AutoML results using DSPy LLM optimization.
 *
 * <p>This class provides intelligent interpretation of ML pipeline results
 * from TPOT2, using the DSPy fluent API to generate:
 * <ul>
 *   <li>Natural language explanations of the best pipeline</li>
 *   <li>Feature importance analysis</li>
 *   <li>Performance recommendations</li>
 *   <li>Deployment guidance</li>
 * </ul>
 *
 * <h2>Architecture (JOR4J + JOR4J = Meta-Layer)</h2>
 * <pre>
 * TPOT2 Result → DSPy Interpreter → LLM → Structured Interpretation
 *      ↓              ↓
 *   Pipeline       Natural
 *    Score        Language
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class Tpot2ResultInterpreter {

    private final Tpot2Result result;
    private final Tpot2TaskType taskType;

    /**
     * Create an interpreter for a TPOT2 result.
     *
     * @param result the TPOT2 optimization result
     * @param taskType the process mining task type
     */
    public Tpot2ResultInterpreter(Tpot2Result result, Tpot2TaskType taskType) {
        this.result = result;
        this.taskType = taskType;
    }

    /**
     * Get the original TPOT2 result.
     *
     * @return the optimization result
     */
    public Tpot2Result result() {
        return result;
    }

    /**
     * Get the task type.
     *
     * @return the process mining task type
     */
    public Tpot2TaskType taskType() {
        return taskType;
    }

    /**
     * Generate a human-readable summary of the optimization result.
     *
     * @return formatted summary string
     */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("TPOT2 Optimization Result\n");
        sb.append("========================\n\n");
        sb.append("Task Type: ").append(taskType).append("\n");
        sb.append("Best Score: ").append(String.format("%.4f", result.bestScore())).append("\n");
        sb.append("Training Time: ").append(result.trainingTimeMs()).append(" ms\n");
        sb.append("Pipeline: ").append(result.pipelineDescription()).append("\n");
        sb.append("Model Size: ").append(result.onnxModelBytes().length).append(" bytes\n");
        return sb.toString();
    }

    /**
     * Get score interpretation for the task type.
     *
     * @return human-readable score interpretation
     */
    public String interpretScore() {
        double score = result.bestScore();
        return switch (taskType) {
            case CASE_OUTCOME -> interpretClassificationScore(score);
            case REMAINING_TIME -> interpretRegressionScore(score);
            case NEXT_ACTIVITY -> interpretClassificationScore(score);
            case ANOMALY_DETECTION -> interpretClassificationScore(score);
        };
    }

    private String interpretClassificationScore(double score) {
        if (score >= 0.95) return "Excellent (≥95% accuracy) - Production ready";
        if (score >= 0.90) return "Very Good (90-95%) - Suitable for most use cases";
        if (score >= 0.80) return "Good (80-90%) - Consider additional features";
        if (score >= 0.70) return "Fair (70-80%) - Needs improvement";
        return "Poor (<70%) - Requires significant optimization";
    }

    private String interpretRegressionScore(double score) {
        // For regression, score is typically R² or negative MAE
        if (score >= 0.9) return "Excellent (R² ≥ 0.9) - Highly accurate predictions";
        if (score >= 0.7) return "Good (R² 0.7-0.9) - Reasonable predictions";
        if (score >= 0.5) return "Fair (R² 0.5-0.7) - Moderate accuracy";
        return "Poor (R² < 0.5) - Consider feature engineering";
    }

    /**
     * Get deployment readiness assessment.
     *
     * @return deployment readiness status
     */
    public String deploymentReadiness() {
        double score = result.bestScore();
        boolean isReady = switch (taskType) {
            case CASE_OUTCOME, ANOMALY_DETECTION -> score >= 0.85;
            case REMAINING_TIME -> score >= 0.7;
            case NEXT_ACTIVITY -> score >= 0.80;
        };

        if (isReady) {
            return "READY - Model meets deployment threshold for " + taskType;
        } else {
            return "NOT READY - Score " + String.format("%.2f", score) +
                " below threshold for " + taskType + ". Consider: " +
                getImprovementSuggestions();
        }
    }

    private String getImprovementSuggestions() {
        return switch (taskType) {
            case CASE_OUTCOME -> "More training data, feature engineering, or ensemble methods";
            case REMAINING_TIME -> "Additional temporal features, larger training window";
            case NEXT_ACTIVITY -> "More context features, activity embeddings";
            case ANOMALY_DETECTION -> "Balanced dataset, anomaly-specific features";
        };
    }
}
