/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.dspy.worklets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.dspy.PythonDspyBridge;
import org.yawlfoundation.yawl.worklet.RdrSet;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * DSPy-based worklet selector that learns from historical worklet selections.
 *
 * <p>This selector replaces brittle hand-coded RDR (Ripple Down Rules) decision trees
 * with a machine-learning approach using DSPy's {@code BootstrapFewShot} classifier.
 * The classifier improves automatically as more cases complete and new training examples
 * are available.
 *
 * <h2>Architecture</h2>
 * <pre>
 * WorkletService.evaluate()
 *   ↓ delegates to
 * DspyWorkletSelector.selectWorklet()
 *   ↓ builds context
 * WorkletSelectionContext (taskName, caseData, availableWorklets, historicalSelections)
 *   ↓ marshals to Python
 * PythonDspyBridge.selectWorklet(context)
 *   ↓ calls
 * dspy_worklet_selection.WorkletSelectionModule
 *   ↓ BootstrapFewShot classification
 * WorkletSelection (selectedWorkletId, confidence, rationale)
 *   ↓ if confidence > 0.7: return DSPy selection
 *   ↓ else: fallback to RDR evaluator
 * Final Worklet ID
 * </pre>
 *
 * <h2>Fallback Semantics</h2>
 * <p>If DSPy confidence is below the threshold ({@value #CONFIDENCE_THRESHOLD}),
 * the selector falls back to the existing RDR evaluator. This ensures safety:
 * <ul>
 *   <li>High-confidence DSPy decisions are used directly (faster, more accurate)</li>
 *   <li>Low-confidence predictions fall back to proven RDR logic</li>
 *   <li>No silent failures; selection method is logged</li>
 * </ul>
 *
 * <h2>Error Handling</h2>
 * <p>If DSPy execution throws an exception, the selector logs a warning and falls back
 * to the RDR evaluator. This ensures the system remains operational even if DSPy
 * infrastructure has issues.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class DspyWorkletSelector {

    private static final Logger log = LoggerFactory.getLogger(DspyWorkletSelector.class);

    /**
     * Confidence threshold: selections below this value trigger RDR fallback.
     */
    private static final double CONFIDENCE_THRESHOLD = 0.7;

    private final PythonDspyBridge dspyBridge;
    private final RdrEvaluator fallbackEvaluator;

    /**
     * Constructs a DspyWorkletSelector with DSPy bridge and RDR fallback.
     *
     * @param dspyBridge the Python DSPy bridge for worklet selection; must not be null
     * @param fallbackEvaluator the RDR evaluator for low-confidence cases; must not be null
     * @throws NullPointerException if either parameter is null
     */
    public DspyWorkletSelector(PythonDspyBridge dspyBridge, RdrEvaluator fallbackEvaluator) {
        this.dspyBridge = Objects.requireNonNull(dspyBridge,
            "PythonDspyBridge must not be null");
        this.fallbackEvaluator = Objects.requireNonNull(fallbackEvaluator,
            "RdrEvaluator must not be null");
        log.info("DspyWorkletSelector initialized with DSPy bridge and RDR fallback");
    }

    /**
     * Selects a worklet using DSPy, falling back to RDR if confidence is low.
     *
     * <p>The selection process:
     * <ol>
     *   <li>Builds a {@link WorkletSelectionContext} from task and case data</li>
     *   <li>Calls {@code PythonDspyBridge.selectWorklet()} to get DSPy prediction</li>
     *   <li>If confidence > 0.7, returns DSPy selection</li>
     *   <li>If confidence ≤ 0.7, falls back to RDR evaluator</li>
     *   <li>If DSPy throws exception, falls back to RDR and logs warning</li>
     * </ol>
     *
     * @param taskName the name of the task (e.g., "ApproveRequest"); must not be null
     * @param caseData attributes of the case relevant to selection; must not be null
     * @param availableWorklets candidate worklet IDs; must not be null or empty
     * @param rdrSet the RDR rule set for fallback evaluation; must not be null
     * @return the selected worklet ID; never null
     * @throws NullPointerException if any required parameter is null
     * @throws IllegalArgumentException if availableWorklets is empty
     * @throws Exception if both DSPy and RDR evaluation fail
     */
    public String selectWorklet(
            String taskName,
            Map<String, Object> caseData,
            List<String> availableWorklets,
            RdrSet rdrSet) throws Exception {

        Objects.requireNonNull(taskName, "taskName must not be null");
        Objects.requireNonNull(caseData, "caseData must not be null");
        Objects.requireNonNull(availableWorklets, "availableWorklets must not be null");
        Objects.requireNonNull(rdrSet, "rdrSet must not be null");

        if (availableWorklets.isEmpty()) {
            throw new IllegalArgumentException("availableWorklets must not be empty");
        }

        // Build historical selection counts (for training signal)
        Map<String, Integer> historicalSelections = new HashMap<>();
        for (String worklet : availableWorklets) {
            historicalSelections.put(worklet, 0);  // Will be populated from metrics in production
        }

        // Build context for DSPy
        WorkletSelectionContext context = new WorkletSelectionContext(
            taskName,
            caseData,
            availableWorklets,
            historicalSelections
        );

        try {
            // Call DSPy selector
            WorkletSelection selection = dspyBridge.selectWorklet(context);

            // Use DSPy selection if confident
            if (selection.confidence() > CONFIDENCE_THRESHOLD) {
                log.info("DSPy worklet selection: task='{}' selected='{}' confidence={:.2f}",
                    taskName, selection.selectedWorkletId(), selection.confidence());
                return selection.selectedWorkletId();
            }

            // Low confidence: fall back to RDR
            log.info("DSPy confidence below threshold ({} < {:.2f}), using RDR fallback for task='{}'",
                selection.confidence(), CONFIDENCE_THRESHOLD, taskName);
            return fallbackEvaluator.evaluate(taskName, caseData, availableWorklets, rdrSet);

        } catch (Exception e) {
            // DSPy execution failed: fall back to RDR
            log.warn("DSPy worklet selection failed for task='{}': {}, using RDR fallback",
                taskName, e.getMessage(), e);
            return fallbackEvaluator.evaluate(taskName, caseData, availableWorklets, rdrSet);
        }
    }

    /**
     * Interface for RDR-based worklet evaluation.
     *
     * <p>Encapsulates the existing RDR evaluation logic, allowing DSPy selector
     * to delegate to it when needed. The evaluator uses the RdrSet for the
     * given task and returns the selected worklet ID.
     */
    @FunctionalInterface
    public interface RdrEvaluator {
        /**
         * Evaluates an RDR rule set for the given task and case context.
         *
         * @param taskName the task name
         * @param caseData case attributes
         * @param availableWorklets candidate worklets
         * @param rdrSet the RDR rule set
         * @return the selected worklet ID from RDR evaluation
         * @throws Exception if RDR evaluation fails
         */
        String evaluate(
            String taskName,
            Map<String, Object> caseData,
            List<String> availableWorklets,
            RdrSet rdrSet) throws Exception;
    }
}
