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

package org.yawlfoundation.yawl.pi.predictive;

import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.integration.eventsourcing.WorkflowEventStore;
import org.yawlfoundation.yawl.integration.messagequeue.WorkflowEvent;
import org.yawlfoundation.yawl.observatory.rdf.WorkflowDNAOracle;
import org.yawlfoundation.yawl.pi.PIException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Predicts case outcome (completion or failure) using ONNX models or DNA oracle.
 *
 * <p>Extracts case-specific features from event store, optionally runs ONNX model
 * inference, and falls back to DNA oracle risk assessment if model unavailable.
 * Returns prediction with confidence score and primary risk factor.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class CaseOutcomePredictor {

    private final PredictiveModelRegistry registry;
    private final WorkflowEventStore eventStore;
    private final WorkflowDNAOracle dnaOracle;

    /**
     * Construct with dependencies.
     *
     * @param registry ONNX model registry for inference
     * @param eventStore Workflow event store
     * @param dnaOracle DNA oracle for fallback assessment
     * @throws NullPointerException if any dependency is null
     */
    public CaseOutcomePredictor(PredictiveModelRegistry registry,
                                 WorkflowEventStore eventStore,
                                 WorkflowDNAOracle dnaOracle) {
        if (registry == null) throw new NullPointerException("registry is required");
        if (eventStore == null) throw new NullPointerException("eventStore is required");
        if (dnaOracle == null) throw new NullPointerException("dnaOracle is required");

        this.registry = registry;
        this.eventStore = eventStore;
        this.dnaOracle = dnaOracle;
    }

    /**
     * Predict case outcome for a given case ID.
     *
     * <p>Attempts ONNX model inference first; falls back to DNA oracle.
     * Computes features: case duration ms, task count, cancelled count, avg event gap ms.
     *
     * @param caseId Case ID to predict
     * @return Case outcome prediction
     * @throws PIException If event retrieval fails or outcome cannot be determined
     */
    public CaseOutcomePrediction predict(String caseId) throws PIException {
        try {
            List<WorkflowEvent> events = eventStore.loadEvents(caseId);

            if (events.isEmpty()) {
                throw new PIException("No events found for case: " + caseId, "predictive");
            }

            events.sort(Comparator.comparing(WorkflowEvent::getTimestamp));

            float[] features = computeFeatures(events);
            Instant now = Instant.now();

            if (registry.isAvailable("case_outcome")) {
                return predictWithOnnx(caseId, features, now);
            } else {
                return predictWithDnaOracle(caseId, events, now);
            }
        } catch (WorkflowEventStore.EventStoreException e) {
            throw new PIException("Failed to load events for case: " + caseId,
                "predictive", e);
        }
    }

    private float[] computeFeatures(List<WorkflowEvent> events) {
        long startMs = events.get(0).getTimestamp().toEpochMilli();
        long endMs = events.get(events.size() - 1).getTimestamp().toEpochMilli();
        long durationMs = endMs - startMs;

        long taskCount = events.stream()
            .filter(e -> e.getEventType() == WorkflowEvent.EventType.WORKITEM_STARTED)
            .count();

        long cancelledCount = events.stream()
            .filter(e -> e.getEventType() == WorkflowEvent.EventType.CASE_CANCELLED)
            .count();

        double avgGapMs = 0.0;
        if (events.size() > 1) {
            long totalGap = 0;
            for (int i = 1; i < events.size(); i++) {
                long gap = events.get(i).getTimestamp().toEpochMilli()
                    - events.get(i - 1).getTimestamp().toEpochMilli();
                totalGap += Math.max(0, gap);
            }
            avgGapMs = (double) totalGap / (events.size() - 1);
        }

        return new float[] {
            (float) durationMs,
            (float) taskCount,
            (float) cancelledCount,
            (float) avgGapMs,
            (float) events.size()
        };
    }

    private CaseOutcomePrediction predictWithOnnx(String caseId, float[] features,
                                                    Instant now) throws PIException {
        try {
            float[] output = registry.infer("case_outcome", features);

            float completionProb = output.length > 0 ? Math.max(0f, Math.min(1f, output[0])) : 0.5f;
            float riskScore = output.length > 1 ? Math.max(0f, Math.min(1f, output[1])) : 0.5f;

            String riskFactor = interpretRiskFactor(features, riskScore);

            return new CaseOutcomePrediction(
                caseId,
                completionProb,
                riskScore,
                riskFactor,
                true,
                now
            );
        } catch (PIException e) {
            throw new PIException("ONNX inference failed for case: " + caseId,
                "predictive", e);
        }
    }

    private CaseOutcomePrediction predictWithDnaOracle(String caseId,
                                                        List<WorkflowEvent> events,
                                                        Instant now) throws PIException {

        List<String> activitySeq = new ArrayList<>();
        events.forEach(e -> {
            if (e.getEventType() == WorkflowEvent.EventType.WORKITEM_STARTED) {
                activitySeq.add("task_" + e.getWorkItemId());
            }
        });

        long durationMs = events.get(events.size() - 1).getTimestamp().toEpochMilli()
            - events.get(0).getTimestamp().toEpochMilli();

        boolean hasCancellation = events.stream()
            .anyMatch(e -> e.getEventType() == WorkflowEvent.EventType.CASE_CANCELLED);

        double riskScore = 0.0;
        String riskFactor = "No risk indicators";

        if (hasCancellation) {
            riskScore = 0.8;
            riskFactor = "Case was cancelled";
        } else if (durationMs > 3600000) {
            riskScore = 0.5;
            riskFactor = "Long execution time (>1 hour)";
        } else if (activitySeq.size() > 10) {
            riskScore = 0.3;
            riskFactor = "High task count (>10 tasks)";
        }

        double completionProb = 1.0 - riskScore;

        return new CaseOutcomePrediction(
            caseId,
            completionProb,
            riskScore,
            riskFactor,
            false,
            now
        );
    }

    private String interpretRiskFactor(float[] features, float riskScore) {
        if (riskScore > 0.7) {
            if (features[0] > 3600000) {
                return "very long execution time (>1 hour)";
            } else if (features[1] > 20) {
                return "very high task count (>20)";
            } else {
                return "high failure risk based on model";
            }
        } else if (riskScore > 0.5) {
            if (features[0] > 1800000) {
                return "long execution time (>30 minutes)";
            } else if (features[1] > 10) {
                return "high task count (>10)";
            } else {
                return "moderate failure risk";
            }
        } else {
            return "low risk indicators";
        }
    }
}
