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

import org.yawlfoundation.yawl.dspy.fluent.Dspy;
import org.yawlfoundation.yawl.dspy.fluent.DspyExample;
import org.yawlfoundation.yawl.dspy.fluent.DspyModule;
import org.yawlfoundation.yawl.dspy.fluent.DspyResult;
import org.yawlfoundation.yawl.tpot2.Tpot2Config;
import org.yawlfoundation.yawl.tpot2.Tpot2Result;
import org.yawlfoundation.yawl.tpot2.Tpot2TaskType;

import java.util.List;

/**
 * DSPy-powered interpreter for TPOT2 AutoML results.
 *
 * <p>This class uses the DSPy fluent API to provide intelligent interpretation
 * of machine learning pipeline optimization results from TPOT2.
 *
 * <h2>JOR4J Meta-Layer Architecture</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  Layer 4: Interpretation (DSPy)                                 │
 * │  Tpot2DspyInterpreter → LLM → Structured Insights               │
 * └─────────────────────────────────────────────────────────────────┘
 *                          ▲
 *                          │ TPOT2 Result
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  Layer 3: AutoML (TPOT2)                                        │
 * │  Tpot2Optimizer → Genetic Programming → ONNX Pipeline           │
 * └─────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class Tpot2DspyInterpreter {

    private final Tpot2Result result;
    private final Tpot2Config config;
    private final DspyModule interpreterModule;

    /**
     * Create a DSPy interpreter for TPOT2 results.
     *
     * @param result the TPOT2 optimization result
     * @param config the TPOT2 configuration used
     * @throws IllegalStateException if DSPy is not configured
     */
    public Tpot2DspyInterpreter(Tpot2Result result, Tpot2Config config) {
        if (!Dspy.isConfigured()) {
            throw new IllegalStateException(
                "DSPy must be configured first. Call Dspy.configure() with your LLM settings.");
        }
        this.result = result;
        this.config = config;
        this.interpreterModule = createInterpreterModule();
    }

    /**
     * Interpret the TPOT2 result using DSPy.
     *
     * @return structured interpretation of the result
     */
    public Interpretation interpret() {
        String taskContext = buildTaskContext();
        String pipelineContext = buildPipelineContext();
        String metricsContext = buildMetricsContext();

        DspyResult dspyResult = interpreterModule.predict(
            "task_context", taskContext,
            "pipeline_description", pipelineContext,
            "metrics_summary", metricsContext
        );

        return new Interpretation(
            dspyResult.getString("explanation"),
            dspyResult.getString("recommendations"),
            dspyResult.getString("deployment_readiness"),
            dspyResult.getString("feature_insights"),
            result.bestScore(),
            result.trainingTimeMs()
        );
    }

    /**
     * Get a quick explanation without full interpretation.
     *
     * @return brief explanation of the result
     */
    public String quickExplanation() {
        return String.format(
            "TPOT2 found a %s pipeline with %.2f score for %s task. " +
            "Training took %d ms. %s",
            extractPipelineType(),
            result.bestScore(),
            config.taskType(),
            result.trainingTimeMs(),
            getScoreAssessment()
        );
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private DspyModule createInterpreterModule() {
        return Dspy.predict(
            "task_context, pipeline_description, metrics_summary -> " +
            "explanation, recommendations, deployment_readiness, feature_insights"
        )
        .withExamples(List.of(
            Dspy.example()
                .input("task_context", "Case outcome prediction for loan approval process")
                .input("pipeline_description", "RandomForestClassifier with StandardScaler")
                .input("metrics_summary", "Accuracy: 0.92, F1: 0.89, Training time: 45s")
                .output("explanation", "The model uses a Random Forest ensemble which works well " +
                    "for tabular process data. The 92% accuracy indicates reliable predictions.")
                .output("recommendations", "Consider adding case duration and resource features. " +
                    "Monitor for concept drift in production.")
                .output("deployment_readiness", "READY - Model exceeds 85% threshold for classification")
                .output("feature_insights", "Random Forest suggests non-linear relationships in the data. " +
                    "Feature importance analysis recommended.")
                .build(),
            Dspy.example()
                .input("task_context", "Remaining time prediction for manufacturing process")
                .input("pipeline_description", "GradientBoostingRegressor with PCA")
                .input("metrics_summary", "R²: 0.78, MAE: 12.5 minutes, Training time: 120s")
                .output("explanation", "Gradient Boosting with dimensionality reduction captures " +
                    "complex temporal patterns. R² of 0.78 shows good predictive power.")
                .output("recommendations", "Add time-since-last-event features. Consider separate " +
                    "models for different process variants.")
                .output("deployment_readiness", "CONDITIONALLY READY - R² meets minimum threshold " +
                    "but consider improving for critical decisions")
                .output("feature_insights", "PCA suggests high feature correlation. Original features " +
                    "may contain redundant information.")
                .build()
        ));
    }

    private String buildTaskContext() {
        return switch (config.taskType()) {
            case CASE_OUTCOME -> "Case outcome prediction for business process. " +
                "Predicting whether a case will have a positive or negative outcome.";
            case REMAINING_TIME -> "Remaining time prediction for running cases. " +
                "Estimating how long until case completion.";
            case NEXT_ACTIVITY -> "Next activity prediction for process execution. " +
                "Predicting the most likely next activity in a case.";
            case ANOMALY_DETECTION -> "Anomaly detection in process execution. " +
                "Identifying unusual patterns or deviations from normal behavior.";
        };
    }

    private String buildPipelineContext() {
        return String.format(
            "Pipeline: %s. Generations: %d, Population: %d. CV Folds: %d.",
            result.pipelineDescription(),
            config.generations(),
            config.populationSize(),
            config.cvFolds()
        );
    }

    private String buildMetricsContext() {
        return String.format(
            "Best Score: %.4f. Training Time: %d ms. Model Size: %d bytes.",
            result.bestScore(),
            result.trainingTimeMs(),
            result.onnxModelBytes().length
        );
    }

    private String extractPipelineType() {
        String desc = result.pipelineDescription().toLowerCase();
        if (desc.contains("randomforest")) return "Random Forest";
        if (desc.contains("gradientboosting")) return "Gradient Boosting";
        if (desc.contains("xgboost")) return "XGBoost";
        if (desc.contains("logisticregression")) return "Logistic Regression";
        if (desc.contains("svc") || desc.contains("svm")) return "Support Vector Machine";
        if (desc.contains("kneighbors")) return "K-Nearest Neighbors";
        if (desc.contains("decisiontree")) return "Decision Tree";
        return "Ensemble";
    }

    private String getScoreAssessment() {
        double score = result.bestScore();
        return switch (config.taskType()) {
            case CASE_OUTCOME, ANOMALY_DETECTION -> score >= 0.90 ? "Excellent for production." :
                score >= 0.80 ? "Good, consider improvements." : "Needs optimization.";
            case REMAINING_TIME -> score >= 0.80 ? "Excellent for production." :
                score >= 0.60 ? "Good, consider improvements." : "Needs optimization.";
            case NEXT_ACTIVITY -> score >= 0.85 ? "Excellent for production." :
                score >= 0.70 ? "Good, consider improvements." : "Needs optimization.";
        };
    }

    /**
     * Structured interpretation result.
     *
     * @param explanation natural language explanation of the result
     * @param recommendations actionable recommendations for improvement
     * @param deploymentReadiness assessment of production readiness
     * @param featureInsights insights about feature importance and data
     * @param score the optimization score
     * @param trainingTimeMs training time in milliseconds
     */
    public record Interpretation(
        String explanation,
        String recommendations,
        String deploymentReadiness,
        String featureInsights,
        double score,
        long trainingTimeMs
    ) {
        /**
         * Check if the model is ready for deployment.
         *
         * @return true if deployment ready
         */
        public boolean isDeploymentReady() {
            return deploymentReadiness != null &&
                deploymentReadiness.toLowerCase().contains("ready") &&
                !deploymentReadiness.toLowerCase().contains("not ready");
        }

        /**
         * Get formatted summary.
         *
         * @return formatted interpretation summary
         */
        public String summary() {
            return String.format("""
                === TPOT2 Result Interpretation ===

                Score: %.4f (%.1fs training)

                Explanation:
                %s

                Recommendations:
                %s

                Deployment Status: %s

                Feature Insights:
                %s
                """,
                score,
                trainingTimeMs / 1000.0,
                explanation,
                recommendations,
                deploymentReadiness,
                featureInsights
            );
        }
    }
}
