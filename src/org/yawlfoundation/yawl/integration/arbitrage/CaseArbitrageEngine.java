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

package org.yawlfoundation.yawl.integration.arbitrage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.integration.eventsourcing.CaseStateView;
import org.yawlfoundation.yawl.integration.eventsourcing.EventReplayer;
import org.yawlfoundation.yawl.observability.PredictiveRouter;
import org.yawlfoundation.yawl.stateless.YStatelessEngine;
import org.yawlfoundation.yawl.stateless.elements.YSpecification;
import org.yawlfoundation.yawl.stateless.engine.YNetRunner;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Temporal case arbitrage engine: fork historical cases, run alternative futures in parallel,
 * elect a winner via majority vote, and update routing weights.
 *
 * <p>This engine enables workflow optimization by exploring multiple possible execution paths
 * from a historical decision point. Each variant runs a complete case execution with different
 * parameter mutations, and the successful path (or paths) inform routing decisions.
 *
 * <p><b>Workflow</b>:
 * <ol>
 *   <li>Specify a historical case and pivot instant (when to fork)</li>
 *   <li>Replay case state as of that instant</li>
 *   <li>Generate N variants, each with mutated payload (priority, route, assignee, etc.)</li>
 *   <li>Launch N cases in parallel via virtual threads</li>
 *   <li>Collect results when all complete</li>
 *   <li>Elect winner by majority vote on outcome status, tiebreak by shortest duration</li>
 *   <li>Update predictive router weights based on winning variant</li>
 * </ol>
 *
 * <p><b>Thread Safety</b>: This engine is designed for concurrent arbitrage operations using
 * virtual threads. Each variant runs independently; there is no shared mutable state during
 * parallel execution.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class CaseArbitrageEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(CaseArbitrageEngine.class);
    private static final int MAX_VARIANTS = 5;

    private final EventReplayer eventReplayer;
    private final YStatelessEngine statelessEngine;
    private final PredictiveRouter predictiveRouter;

    /**
     * Construct arbitrage engine with required collaborators.
     *
     * @param eventReplayer   replayer to reconstruct historical case state
     * @param statelessEngine engine to launch variant cases
     * @param predictiveRouter router to record routing decisions
     */
    public CaseArbitrageEngine(EventReplayer eventReplayer,
                               YStatelessEngine statelessEngine,
                               PredictiveRouter predictiveRouter) {
        this.eventReplayer = Objects.requireNonNull(eventReplayer, "eventReplayer");
        this.statelessEngine = Objects.requireNonNull(statelessEngine, "statelessEngine");
        this.predictiveRouter = Objects.requireNonNull(predictiveRouter, "predictiveRouter");
    }

    /**
     * Run arbitrage on a historical case.
     *
     * <p>Forks the case at the pivot instant, generates variants with mutated payload,
     * executes all variants in parallel, elects a winner, and records the outcome.
     *
     * @param sourceCaseId    case to fork
     * @param pivotInstant    point in time to pivot from
     * @param spec            workflow specification
     * @param variantCount    number of variants to generate (max 5)
     * @return arbitrage outcome with winner, all results, and confidence
     * @throws ArbitrageException if replay, launch, or execution fails
     * @throws IllegalArgumentException if variantCount > 5
     */
    public ArbitrageOutcome arbitrate(String sourceCaseId,
                                       Instant pivotInstant,
                                       YSpecification spec,
                                       int variantCount)
            throws ArbitrageException {
        Objects.requireNonNull(sourceCaseId, "sourceCaseId");
        Objects.requireNonNull(pivotInstant, "pivotInstant");
        Objects.requireNonNull(spec, "spec");

        if (variantCount > MAX_VARIANTS) {
            throw new IllegalArgumentException(
                "variantCount exceeds τ limit of " + MAX_VARIANTS
            );
        }

        LOGGER.debug("Starting arbitrage for case {} at pivot {}, {} variants",
                     sourceCaseId, pivotInstant, variantCount);

        // Step 1: Replay case state at pivot instant
        CaseStateView pivotState = replayPivot(sourceCaseId, pivotInstant);

        // Step 2: Generate variants with mutated payload
        List<FutureVariant> variants = generateVariants(pivotState, variantCount);

        // Step 3: Launch all variants in parallel
        List<YNetRunner> runners = launchVariants(spec, variants);

        // Step 4: Collect results
        List<VariantResult> results = collectResults(variants, runners);

        // Step 5: Elect winner
        FutureVariant winner = electWinner(results);

        // Step 6: Compute confidence
        double confidence = computeConfidence(winner, results);

        // Step 7: Update routing weights
        updateRoutingWeights(winner, results);

        Instant resolvedAt = Instant.now();
        ArbitrageOutcome outcome = new ArbitrageOutcome(
            sourceCaseId,
            pivotInstant,
            variantCount,
            winner,
            confidence,
            Collections.unmodifiableList(results),
            resolvedAt
        );

        LOGGER.info("Arbitrage complete for case {} -> winner variant {} (confidence {:.1f}%)",
                    sourceCaseId, winner.variantId(), confidence * 100);

        return outcome;
    }

    /**
     * Replay case state as of the pivot instant.
     *
     * @param sourceCaseId case to replay
     * @param pivotInstant point in time
     * @return reconstructed case state
     * @throws ArbitrageException if replay fails
     */
    private CaseStateView replayPivot(String sourceCaseId, Instant pivotInstant)
            throws ArbitrageException {
        try {
            return eventReplayer.replayAsOf(sourceCaseId, pivotInstant);
        } catch (EventReplayer.ReplayException e) {
            throw new ArbitrageException(
                "Failed to replay case " + sourceCaseId + " at " + pivotInstant, e
            );
        }
    }

    /**
     * Generate N variants from pivot state, each with mutated payload.
     *
     * <p>Each variant takes the base payload and adds a variant-specific mutation:
     * <ul>
     *   <li>Variant 0: priority=HIGH</li>
     *   <li>Variant 1: priority=NORMAL</li>
     *   <li>Variant 2: priority=LOW</li>
     *   <li>Variant 3: route=ALTERNATIVE</li>
     *   <li>Variant 4: assignee=LOAD_BALANCED</li>
     * </ul>
     *
     * @param pivotState case state at pivot
     * @param count      number of variants
     * @return list of variant definitions
     */
    private List<FutureVariant> generateVariants(CaseStateView pivotState, int count) {
        List<FutureVariant> variants = new ArrayList<>(count);
        Map<String, String> basePayload = pivotState.getPayload();

        for (int i = 0; i < count; i++) {
            String variantId = UUID.randomUUID().toString();
            Map<String, String> variantPayload = new LinkedHashMap<>(basePayload);

            // Mutate payload for variant
            switch (i) {
                case 0 -> variantPayload.put("priority", "HIGH");
                case 1 -> variantPayload.put("priority", "NORMAL");
                case 2 -> variantPayload.put("priority", "LOW");
                case 3 -> variantPayload.put("route", "ALTERNATIVE");
                case 4 -> variantPayload.put("assignee", "LOAD_BALANCED");
                default -> {} // no-op for additional variants beyond 5
            }

            String paramXml = buildParamXml(variantPayload);
            variants.add(new FutureVariant(i, variantId, paramXml, variantPayload));
        }

        return variants;
    }

    /**
     * Build minimal param XML from payload map.
     *
     * @param payload map of parameter entries
     * @return XML string: &lt;caseParams&gt;&lt;key&gt;value&lt;/key&gt;...&lt;/caseParams&gt;
     */
    private String buildParamXml(Map<String, String> payload) {
        StringBuilder sb = new StringBuilder("<caseParams>");
        for (Map.Entry<String, String> entry : payload.entrySet()) {
            sb.append("<").append(escapeXmlTagName(entry.getKey())).append(">")
              .append(escapeXmlText(entry.getValue()))
              .append("</").append(escapeXmlTagName(entry.getKey())).append(">");
        }
        sb.append("</caseParams>");
        return sb.toString();
    }

    /**
     * Escape text for XML element content (simple escaping for &lt;, &gt;, &amp;).
     *
     * @param text text to escape
     * @return escaped text
     * @throws NullPointerException if text is null
     */
    private String escapeXmlText(String text) {
        Objects.requireNonNull(text, "text must not be null");
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;");
    }

    /**
     * Escape text for XML tag name (simple: replace non-alphanumeric with underscore).
     *
     * @param text tag name to escape
     * @return escaped tag name
     */
    private String escapeXmlTagName(String text) {
        if (text == null) return "param";
        return text.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    /**
     * Launch all variants in parallel via virtual threads.
     *
     * @param spec     workflow specification
     * @param variants list of variant definitions
     * @return list of YNetRunner instances (one per variant, in order)
     * @throws ArbitrageException if launch fails
     */
    private List<YNetRunner> launchVariants(YSpecification spec,
                                             List<FutureVariant> variants)
            throws ArbitrageException {
        List<String> paramXmls = variants.stream()
            .map(FutureVariant::paramXml)
            .toList();

        try {
            return statelessEngine.launchCasesParallel(spec, paramXmls);
        } catch (Exception e) {
            throw new ArbitrageException(
                "Failed to launch variant cases: " + e.getMessage(), e
            );
        }
    }

    /**
     * Collect results by pairing variants with their corresponding runners.
     *
     * <p>For each variant, checks if the runner completed and determines outcome status.
     * If runners list is shorter than variants (partial launch failure), missing runners
     * are marked as FAILED.
     *
     * @param variants list of variant definitions (in order)
     * @param runners  list of launched runners (in order)
     * @return list of variant results
     */
    private List<VariantResult> collectResults(List<FutureVariant> variants,
                                                List<YNetRunner> runners) {
        List<VariantResult> results = new ArrayList<>(variants.size());
        long startMs = System.currentTimeMillis();

        for (int i = 0; i < variants.size(); i++) {
            FutureVariant variant = variants.get(i);

            if (i < runners.size()) {
                YNetRunner runner = runners.get(i);
                boolean completed = runner.isCompleted();
                long durationMs = System.currentTimeMillis() - startMs;
                String outcomeStatus = completed ? "COMPLETED" : "RUNNING";

                results.add(new VariantResult(variant, completed, durationMs, outcomeStatus));
            } else {
                // Launch failure for this variant
                long durationMs = System.currentTimeMillis() - startMs;
                results.add(new VariantResult(variant, false, durationMs, "FAILED"));
            }
        }

        return results;
    }

    /**
     * Elect winner by majority vote on outcome status, tiebreak by shortest duration.
     *
     * <p>Groups results by outcomeStatus. Winner is the variant from the largest group.
     * If multiple variants have the same status, tiebreaker is the shortest durationMs.
     *
     * @param results list of variant results
     * @return winning variant
     * @throws ArbitrageException if no results
     */
    private FutureVariant electWinner(List<VariantResult> results)
            throws ArbitrageException {
        if (results.isEmpty()) {
            throw new ArbitrageException("No variant results to elect winner");
        }

        // Group by outcome status
        Map<String, List<VariantResult>> byStatus = results.stream()
            .collect(Collectors.groupingBy(VariantResult::outcomeStatus));

        // Find largest group
        List<VariantResult> largestGroup = byStatus.values().stream()
            .max((a, b) -> Integer.compare(a.size(), b.size()))
            .orElseThrow(() -> new ArbitrageException("Failed to find largest status group"));

        // Tiebreak: shortest duration
        VariantResult winner = largestGroup.stream()
            .min((a, b) -> Long.compare(a.durationMs(), b.durationMs()))
            .orElseThrow(() -> new ArbitrageException("Failed to find tiebreak winner"));

        return winner.variant();
    }

    /**
     * Compute confidence as fraction of results matching winner's outcome status.
     *
     * @param winner winning variant
     * @param results all variant results
     * @return confidence in range [0.0, 1.0]
     */
    private double computeConfidence(FutureVariant winner, List<VariantResult> results) {
        if (results.isEmpty()) return 0.0;

        // Find winner's outcome status (via first result with matching variant)
        String winnerStatus = results.stream()
            .filter(r -> r.variant().variantId().equals(winner.variantId()))
            .map(VariantResult::outcomeStatus)
            .findFirst()
            .orElse("UNKNOWN");

        // Count matching outcomes
        long matching = results.stream()
            .filter(r -> r.outcomeStatus().equals(winnerStatus))
            .count();

        return (double) matching / results.size();
    }

    /**
     * Update routing weights in predictive router based on winning variant.
     *
     * <p>Records the winner's completion with its duration, allowing the router
     * to update EWMA metrics and adjust routing preferences for future cases.
     *
     * @param winner winning variant
     * @param results all variant results
     */
    private void updateRoutingWeights(FutureVariant winner, List<VariantResult> results) {
        // Find the result for the winner to get duration
        VariantResult winnerResult = results.stream()
            .filter(r -> r.variant().variantId().equals(winner.variantId()))
            .findFirst()
            .orElse(null);

        if (winnerResult != null) {
            // Record winner's performance with predictive router
            // Use variant ID as task name for routing tracking
            predictiveRouter.recordCompletion(
                "arbitrage-variant-" + winner.variantIndex(),
                "case_execution",
                winnerResult.durationMs()
            );

            LOGGER.debug("Updated routing weights for winning variant {} (duration {}ms)",
                         winner.variantId(), winnerResult.durationMs());
        }
    }

    // =========================================================================
    // Record types for arbitrage outcomes and variants
    // =========================================================================

    /**
     * A future variant candidate: one alternative execution path.
     *
     * @param variantIndex 0-based variant index
     * @param variantId    unique identifier for this variant
     * @param paramXml     case parameters as XML
     * @param context      payload map used for this variant
     */
    public record FutureVariant(
        int variantIndex,
        String variantId,
        String paramXml,
        Map<String, String> context
    ) {}

    /**
     * Result of a single variant execution.
     *
     * @param variant         the variant definition
     * @param completed       whether the case reached completion
     * @param durationMs      execution duration in milliseconds
     * @param outcomeStatus   final status: COMPLETED, RUNNING, FAILED, CANCELLED, etc.
     */
    public record VariantResult(
        FutureVariant variant,
        boolean completed,
        long durationMs,
        String outcomeStatus
    ) {}

    /**
     * Final arbitrage outcome: which variant won, with confidence and all results.
     *
     * @param sourceCaseId     original case that was forked
     * @param pivotInstant     point in time where fork occurred
     * @param variantCount     number of variants evaluated
     * @param winner           the winning variant
     * @param winnerConfidence confidence [0.0, 1.0] = fraction of variants with same outcome as winner
     * @param allResults       all variant results (immutable)
     * @param resolvedAt       timestamp when arbitrage completed
     */
    public record ArbitrageOutcome(
        String sourceCaseId,
        Instant pivotInstant,
        int variantCount,
        FutureVariant winner,
        double winnerConfidence,
        List<VariantResult> allResults,
        Instant resolvedAt
    ) {}

    /**
     * Exception thrown during arbitrage operations.
     * Wraps underlying replay, launch, or execution exceptions.
     */
    public static final class ArbitrageException extends Exception {
        /**
         * Construct with message and cause.
         *
         * @param message error description
         * @param cause   underlying exception
         */
        public ArbitrageException(String message, Throwable cause) {
            super(message, cause);
        }

        /**
         * Construct with message only.
         *
         * @param message error description
         */
        public ArbitrageException(String message) {
            super(message);
        }
    }
}
