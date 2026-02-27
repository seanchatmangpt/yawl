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

package org.yawlfoundation.yawl.pi.adaptive;

import org.yawlfoundation.yawl.integration.adaptation.AdaptationResult;
import org.yawlfoundation.yawl.integration.adaptation.AdaptationRule;
import org.yawlfoundation.yawl.integration.adaptation.EventDrivenAdaptationEngine;
import org.yawlfoundation.yawl.integration.adaptation.ProcessEvent;
import org.yawlfoundation.yawl.pi.predictive.PredictiveModelRegistry;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Pre-wired enterprise AutoML integration patterns for four verticals: insurance,
 * healthcare, financial services, and operations.
 *
 * <h2>Diátaxis — Tutorial</h2>
 *
 * <p>EnterpriseAutoMlPatterns is the <em>tutorial entry point</em>.  Each static
 * factory method returns a fully configured {@link PredictiveProcessObserver} wired
 * to a domain-specific {@link EventDrivenAdaptationEngine}.  Start here; customise
 * later via {@link PredictiveAdaptationRules}.</p>
 *
 * <h2>Why co-location matters for these patterns</h2>
 *
 * <p>Traditional enterprise deployments treat ML and workflow as separate systems,
 * forcing a batch cycle:</p>
 *
 * <ol>
 *   <li>Nightly ETL job exports workflow events to data warehouse</li>
 *   <li>Weekly ML training job retrains models on stale data</li>
 *   <li>Prediction REST service deployed separately (HTTP round-trip per case)</li>
 *   <li>Routing decisions imported back via another batch job</li>
 * </ol>
 *
 * <p>Total lag: hours to days.  Model reflects last week's process, not today's.</p>
 *
 * <p>Because YAWL and the ONNX runtime share the same JVM, these patterns achieve:</p>
 *
 * <ul>
 *   <li><strong>Zero ETL lag</strong> — training data is extracted directly from
 *       {@link org.yawlfoundation.yawl.pi.predictive.ProcessMiningTrainingDataExtractor}
 *       against the live {@link org.yawlfoundation.yawl.integration.eventsourcing.WorkflowEventStore}</li>
 *   <li><strong>Microsecond inference</strong> — ONNX Runtime runs in-process;
 *       no HTTP round-trip, no serialisation</li>
 *   <li><strong>Synchronous adaptation</strong> — adaptation actions are returned in
 *       the same engine callback, before the case routes to its next task</li>
 *   <li><strong>Continuous learning</strong> — models can be retrained on any case
 *       milestone (e.g. every 100 completions) without stopping the engine</li>
 * </ul>
 *
 * <h2>Usage pattern</h2>
 *
 * <pre>{@code
 * // 1. Load trained models (from ProcessMiningAutoMl.autoTrain*())
 * PredictiveModelRegistry registry = new PredictiveModelRegistry(
 *     Path.of("/var/yawl/models/insurance"));
 *
 * // 2. Define how your application handles adaptation actions
 * Consumer<AdaptationResult> handler = result -> {
 *     if (result.adapted()) {
 *         myWorkflowService.executeAction(result.executedAction(), result.event());
 *     }
 * };
 *
 * // 3. Create the pre-wired pattern (one line)
 * PredictiveProcessObserver observer = EnterpriseAutoMlPatterns.forInsuranceClaimsTriage(
 *     registry, handler);
 *
 * // 4. Register with the YAWL engine
 * YEngine.getInstance().getAnnouncer().registerInterfaceBObserverGateway(observer);
 * }</pre>
 *
 * <h2>Training models for these patterns</h2>
 *
 * <pre>{@code
 * // Train all four task types once (then models are loaded automatically by registry)
 * ProcessMiningAutoMl.autoTrainCaseOutcome(specId, eventStore, registry,
 *     Tpot2Config.forCaseOutcome(), modelDir);
 * ProcessMiningAutoMl.autoTrainAnomalyDetection(specId, eventStore, registry,
 *     Tpot2Config.forAnomalyDetection(), modelDir);
 * ProcessMiningAutoMl.autoTrainRemainingTime(specId, eventStore, registry,
 *     Tpot2Config.forRemainingTime(), modelDir);
 * ProcessMiningAutoMl.autoTrainNextActivity(specId, eventStore, registry,
 *     Tpot2Config.forNextActivity(), modelDir);
 * }</pre>
 *
 * @author YAWL Foundation
 * @since 6.0
 * @see PredictiveProcessObserver
 * @see PredictiveAdaptationRules
 */
public final class EnterpriseAutoMlPatterns {

    private EnterpriseAutoMlPatterns() {
        throw new UnsupportedOperationException(
                "EnterpriseAutoMlPatterns is a static factory; do not instantiate");
    }

    // ── Insurance ─────────────────────────────────────────────────────────────

    /**
     * Insurance claims triage — detects fraud, routes complex claims to specialists,
     * and alerts on process anomalies.
     *
     * <h3>What it does in real-time</h3>
     * <ul>
     *   <li><strong>Case intake:</strong> predicts claim complexity at first touch;
     *       routes high-confidence complex claims to specialist adjusters
     *       ({@link PredictiveAdaptationRules#complexityRouter})</li>
     *   <li><strong>During processing:</strong> anomaly score above 0.85 triggers
     *       immediate rejection (fraud detected);
     *       score 0.55–0.85 notifies SIU team</li>
     *   <li><strong>Timer breach:</strong> escalates to manual review immediately</li>
     * </ul>
     *
     * <h3>Models required</h3>
     * <p>{@code case_outcome} (complexity) and {@code anomaly_detection} (fraud)</p>
     *
     * <h3>Traditional alternative (batch)</h3>
     * <p>Fraud scoring runs nightly; complex claims identified weekly via actuarial
     * reports; no real-time routing.  Fraudulent claims process for days before
     * detection.</p>
     *
     * @param registry model registry with {@code case_outcome} and
     *                 {@code anomaly_detection} models loaded
     * @param handler  receives {@link AdaptationResult} for each prediction event
     * @return observer ready to register with the YAWL engine
     */
    public static PredictiveProcessObserver forInsuranceClaimsTriage(
            PredictiveModelRegistry registry,
            Consumer<AdaptationResult> handler) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(handler, "handler");

        List<AdaptationRule> rules = PredictiveAdaptationRules.insuranceClaimsRuleSet(0.85, 0.75);
        EventDrivenAdaptationEngine adaptationEngine = new EventDrivenAdaptationEngine(rules);

        return new PredictiveProcessObserver(registry, event ->
                handler.accept(adaptationEngine.process(event)));
    }

    /**
     * Insurance claims triage with a custom feature extractor for richer predictions
     * (e.g. claim amount, policy type, claimant history encoded as floats).
     *
     * @param registry         model registry
     * @param handler          receives adaptation results
     * @param featureExtractor domain feature extractor calibrated to insurance schema
     */
    public static PredictiveProcessObserver forInsuranceClaimsTriage(
            PredictiveModelRegistry registry,
            Consumer<AdaptationResult> handler,
            PredictiveProcessObserver.FeatureExtractor featureExtractor) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(handler, "handler");
        Objects.requireNonNull(featureExtractor, "featureExtractor");

        List<AdaptationRule> rules = PredictiveAdaptationRules.insuranceClaimsRuleSet(0.85, 0.75);
        EventDrivenAdaptationEngine adaptationEngine = new EventDrivenAdaptationEngine(rules);

        return new PredictiveProcessObserver(registry,
                event -> handler.accept(adaptationEngine.process(event)),
                featureExtractor);
    }

    // ── Healthcare ────────────────────────────────────────────────────────────

    /**
     * Healthcare case management — surfaces high-risk patient pathways, predicts
     * SLA breaches for triage queues, and recommends next clinical activities.
     *
     * <h3>What it does in real-time</h3>
     * <ul>
     *   <li><strong>Patient intake:</strong> outcome model identifies high-risk
     *       pathways; escalates to senior clinician
     *       ({@link PredictiveAdaptationRules#highRiskEscalation})</li>
     *   <li><strong>Queue monitoring:</strong> remaining-time model predicts
     *       wait time; escalates cases approaching 4-hour emergency target</li>
     *   <li><strong>After each task:</strong> next-activity model boosts priority
     *       of cases with a clear, confident care pathway</li>
     * </ul>
     *
     * <h3>Models required</h3>
     * <p>{@code case_outcome}, {@code remaining_time}, {@code next_activity}</p>
     *
     * <h3>Traditional alternative (batch)</h3>
     * <p>Risk stratification done at admission only via static scoring tools;
     * no intra-episode re-scoring; SLA breaches discovered retrospectively.</p>
     *
     * @param registry            model registry with all three models loaded
     * @param handler             receives adaptation results
     * @param slaThresholdMinutes remaining-time threshold in minutes (e.g. 60 for
     *                            1-hour-before-4h-target)
     * @param riskThreshold       outcome score above which case is high-risk (e.g. 0.70)
     * @return observer ready to register with the YAWL engine
     */
    public static PredictiveProcessObserver forHealthcareCaseManagement(
            PredictiveModelRegistry registry,
            Consumer<AdaptationResult> handler,
            int slaThresholdMinutes,
            double riskThreshold) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(handler, "handler");

        List<AdaptationRule> rules = PredictiveAdaptationRules.healthcareRuleSet(
                slaThresholdMinutes, riskThreshold);
        EventDrivenAdaptationEngine adaptationEngine = new EventDrivenAdaptationEngine(rules);

        return new PredictiveProcessObserver(registry,
                event -> handler.accept(adaptationEngine.process(event)));
    }

    /**
     * Healthcare case management with sensible defaults: SLA = 60 min,
     * risk threshold = 0.70.
     *
     * @param registry model registry
     * @param handler  receives adaptation results
     */
    public static PredictiveProcessObserver forHealthcareCaseManagement(
            PredictiveModelRegistry registry,
            Consumer<AdaptationResult> handler) {
        return forHealthcareCaseManagement(registry, handler, 60, 0.70);
    }

    // ── Financial Services ────────────────────────────────────────────────────

    /**
     * Financial risk monitoring — real-time fraud rejection, high-risk escalation,
     * and broad anomaly notification across transaction approval workflows.
     *
     * <h3>What it does in real-time</h3>
     * <ul>
     *   <li><strong>Transaction intake:</strong> anomaly score &gt; 0.90 triggers
     *       immediate rejection before any fulfilment step executes</li>
     *   <li><strong>During approval workflow:</strong> outcome score &gt; 0.75
     *       escalates to senior risk officer</li>
     *   <li><strong>Broad monitoring:</strong> anomaly score &gt; 0.60 notifies
     *       the risk operations team without blocking</li>
     * </ul>
     *
     * <h3>Models required</h3>
     * <p>{@code case_outcome} and {@code anomaly_detection}</p>
     *
     * <h3>Traditional alternative (batch)</h3>
     * <p>Rule-based fraud engine at intake only; ML model run as nightly batch;
     * fraudulent transactions may complete before detection; no intra-workflow
     * risk re-scoring.</p>
     *
     * @param registry       model registry with {@code case_outcome} and
     *                       {@code anomaly_detection} models loaded
     * @param handler        receives adaptation results
     * @param fraudThreshold anomaly score for immediate rejection (e.g. 0.90)
     * @param riskThreshold  outcome score for senior escalation (e.g. 0.75)
     * @return observer ready to register with the YAWL engine
     */
    public static PredictiveProcessObserver forFinancialRiskMonitoring(
            PredictiveModelRegistry registry,
            Consumer<AdaptationResult> handler,
            double fraudThreshold,
            double riskThreshold) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(handler, "handler");

        List<AdaptationRule> rules = PredictiveAdaptationRules.financialRiskRuleSet(
                fraudThreshold, riskThreshold);
        EventDrivenAdaptationEngine adaptationEngine = new EventDrivenAdaptationEngine(rules);

        return new PredictiveProcessObserver(registry,
                event -> handler.accept(adaptationEngine.process(event)));
    }

    /**
     * Financial risk monitoring with conservative defaults: fraud = 0.90,
     * escalation risk = 0.75.
     *
     * @param registry model registry
     * @param handler  receives adaptation results
     */
    public static PredictiveProcessObserver forFinancialRiskMonitoring(
            PredictiveModelRegistry registry,
            Consumer<AdaptationResult> handler) {
        return forFinancialRiskMonitoring(registry, handler, 0.90, 0.75);
    }

    // ── Operations ────────────────────────────────────────────────────────────

    /**
     * Operations SLA guardian — two-tier remaining-time monitoring with critical
     * escalation and early-warning notification, plus anomaly alerting.
     *
     * <h3>What it does in real-time</h3>
     * <ul>
     *   <li><strong>Every task transition:</strong> remaining-time model predicts
     *       minutes to completion</li>
     *   <li><strong>Warning tier (&lt; {@code warningMinutes}):</strong> notifies
     *       operations team so they can plan resource reallocation</li>
     *   <li><strong>Critical tier (&lt; {@code criticalMinutes}):</strong> escalates
     *       to manual review; supervisor assigned immediately</li>
     *   <li><strong>Timer breach:</strong> critical escalation regardless of model</li>
     *   <li><strong>Anomaly detection:</strong> unusual cases notified at 0.55 threshold</li>
     * </ul>
     *
     * <h3>Models required</h3>
     * <p>{@code remaining_time} (required), {@code anomaly_detection} (optional)</p>
     *
     * <h3>Traditional alternative (batch)</h3>
     * <p>SLA dashboards updated every 15 minutes via polling; breach notifications
     * sent only after breach occurs; no proactive intervention possible.</p>
     *
     * @param registry        model registry with {@code remaining_time} model loaded
     * @param handler         receives adaptation results
     * @param criticalMinutes remaining-time threshold for mandatory escalation
     * @param warningMinutes  remaining-time threshold for advance notification
     * @return observer ready to register with the YAWL engine
     */
    public static PredictiveProcessObserver forOperationsSlaGuardian(
            PredictiveModelRegistry registry,
            Consumer<AdaptationResult> handler,
            int criticalMinutes,
            int warningMinutes) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(handler, "handler");

        if (criticalMinutes >= warningMinutes) {
            throw new IllegalArgumentException(
                    "criticalMinutes (" + criticalMinutes + ") must be less than "
                            + "warningMinutes (" + warningMinutes + ")");
        }

        List<AdaptationRule> rules = PredictiveAdaptationRules.operationsSlaRuleSet(
                criticalMinutes, warningMinutes);
        EventDrivenAdaptationEngine adaptationEngine = new EventDrivenAdaptationEngine(rules);

        return new PredictiveProcessObserver(registry,
                event -> handler.accept(adaptationEngine.process(event)));
    }

    /**
     * Operations SLA guardian with defaults: critical = 30 min, warning = 120 min.
     *
     * @param registry model registry
     * @param handler  receives adaptation results
     */
    public static PredictiveProcessObserver forOperationsSlaGuardian(
            PredictiveModelRegistry registry,
            Consumer<AdaptationResult> handler) {
        return forOperationsSlaGuardian(registry, handler, 30, 120);
    }

    // ── Sink adapters ─────────────────────────────────────────────────────────

    /**
     * Creates an event sink {@link Consumer}{@code <ProcessEvent>} that routes every
     * prediction event through a supplied adaptation engine and passes results to a
     * result handler.
     *
     * <p>Use this when you want to share one engine across multiple observers:</p>
     *
     * <pre>{@code
     * EventDrivenAdaptationEngine engine = new EventDrivenAdaptationEngine(myRules);
     * Consumer<ProcessEvent> sink = EnterpriseAutoMlPatterns.adaptationSink(
     *     engine, result -> myActionHandler.handle(result));
     *
     * PredictiveProcessObserver o1 = new PredictiveProcessObserver(registry1, sink);
     * PredictiveProcessObserver o2 = new PredictiveProcessObserver(registry2, sink);
     * }</pre>
     *
     * @param engine  adaptation engine to process events through
     * @param handler receives the adaptation result for each event
     * @return a thread-safe event sink consumer
     */
    public static Consumer<ProcessEvent> adaptationSink(
            EventDrivenAdaptationEngine engine,
            Consumer<AdaptationResult> handler) {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(handler, "handler");
        return event -> handler.accept(engine.process(event));
    }

    /**
     * Creates a logging-only event sink that records every prediction event at INFO
     * level.  Useful during model validation before wiring in real adaptation actions.
     *
     * @param logger the Log4j logger to write to
     * @return a consumer that logs each {@link ProcessEvent}'s type, severity, and payload
     */
    public static Consumer<ProcessEvent> loggingSink(
            org.apache.logging.log4j.Logger logger) {
        Objects.requireNonNull(logger, "logger");
        return event -> logger.info("PI event: type={} severity={} payload={}",
                event.eventType(), event.severity(), event.payload());
    }
}
