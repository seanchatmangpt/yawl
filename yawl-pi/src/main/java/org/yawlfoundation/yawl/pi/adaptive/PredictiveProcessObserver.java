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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdom2.Document;
import org.yawlfoundation.yawl.elements.YAWLServiceReference;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.engine.ObserverGateway;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.YWorkItemStatus;
import org.yawlfoundation.yawl.engine.announcement.YAnnouncement;
import org.yawlfoundation.yawl.integration.adaptation.EventSeverity;
import org.yawlfoundation.yawl.integration.adaptation.ProcessEvent;
import org.yawlfoundation.yawl.pi.PIException;
import org.yawlfoundation.yawl.pi.automl.Tpot2TaskType;
import org.yawlfoundation.yawl.pi.predictive.PredictiveModelRegistry;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * ObserverGateway implementation that wires every YAWL engine lifecycle event into
 * real-time ONNX inference, emitting typed {@link ProcessEvent}s for downstream
 * adaptation engines.
 *
 * <h2>Diátaxis — Reference</h2>
 *
 * <p>PredictiveProcessObserver is the <em>reference implementation</em> of the
 * co-located AutoML pattern.  It bridges the engine's synchronous announcement chain
 * to {@link PredictiveModelRegistry}, turning each callback into a prediction
 * opportunity with zero ETL lag.</p>
 *
 * <h2>Co-location advantage vs. batch</h2>
 *
 * <p>Traditional enterprise ML requires: nightly ETL export → weekly model training →
 * separate REST prediction service → result import.  Total lag: hours–days.  Because
 * YAWL and the ONNX runtime share the same JVM, inference runs in the engine callback
 * before the case routes to its next task — lag is microseconds.</p>
 *
 * <h2>Model naming convention</h2>
 *
 * <p>Models are resolved by lower-case {@link Tpot2TaskType#name()}:
 * {@code case_outcome}, {@code remaining_time}, {@code next_activity},
 * {@code anomaly_detection}.  If a model is absent for a callback, that callback
 * still executes its structural logic (logging, event shape) but skips inference.</p>
 *
 * <h2>Non-blocking contract</h2>
 *
 * <p>All {@link PIException}s from inference are caught and logged; they never
 * propagate to the engine.  The {@link #eventSink} must not throw.</p>
 *
 * <h2>Event type constants</h2>
 * <ul>
 *   <li>{@link #CASE_OUTCOME_PREDICTION} — emitted on case start</li>
 *   <li>{@link #SLA_BREACH_PREDICTED} — emitted on item fire and status change</li>
 *   <li>{@link #NEXT_ACTIVITY_SUGGESTION} — emitted on item completion</li>
 *   <li>{@link #PROCESS_ANOMALY_DETECTED} — emitted on anomaly, cancellation, deadlock</li>
 *   <li>{@link #TIMER_EXPIRY_BREACH} — emitted on timer expiry (definite SLA breach)</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * PredictiveModelRegistry registry = new PredictiveModelRegistry(Path.of("/var/yawl/models"));
 * EventDrivenAdaptationEngine adaptationEngine = new EventDrivenAdaptationEngine(List.of(
 *     PredictiveAdaptationRules.slaGuardian(120),
 *     PredictiveAdaptationRules.highRiskEscalation(0.75)
 * ));
 *
 * PredictiveProcessObserver observer = new PredictiveProcessObserver(
 *     registry,
 *     event -> {
 *         AdaptationResult result = adaptationEngine.process(event);
 *         if (result.adapted()) handleAction(result.executedAction(), event);
 *     }
 * );
 * YEngine.getInstance().getAnnouncer().registerInterfaceBObserverGateway(observer);
 * }</pre>
 *
 * @author YAWL Foundation
 * @since 6.0
 * @see PredictiveAdaptationRules
 * @see EnterpriseAutoMlPatterns
 */
public final class PredictiveProcessObserver implements ObserverGateway {

    private static final Logger log = LogManager.getLogger(PredictiveProcessObserver.class);

    // ── Event type constants ────────────────────────────────────────────────

    /** Emitted on case start when a {@code case_outcome} model is loaded. */
    public static final String CASE_OUTCOME_PREDICTION = "CASE_OUTCOME_PREDICTION";

    /**
     * Emitted on work-item fire and status change when a {@code remaining_time}
     * model is loaded.
     */
    public static final String SLA_BREACH_PREDICTED = "SLA_BREACH_PREDICTED";

    /** Emitted on work-item completion when a {@code next_activity} model is loaded. */
    public static final String NEXT_ACTIVITY_SUGGESTION = "NEXT_ACTIVITY_SUGGESTION";

    /**
     * Emitted on case completion (broadcast), case cancellation, deadlock, and
     * work-item cancellation when an {@code anomaly_detection} model scores above
     * {@link #ANOMALY_THRESHOLD}.
     */
    public static final String PROCESS_ANOMALY_DETECTED = "PROCESS_ANOMALY_DETECTED";

    /**
     * Emitted on timer expiry — a definite SLA breach, no inference required.
     * Severity is always {@link EventSeverity#CRITICAL}.
     */
    public static final String TIMER_EXPIRY_BREACH = "TIMER_EXPIRY_BREACH";

    private static final String SCHEME = "predictive-pi";
    private static final String SOURCE = "predictive-process-observer";

    private static final String MODEL_CASE_OUTCOME =
            Tpot2TaskType.CASE_OUTCOME.name().toLowerCase();
    private static final String MODEL_REMAINING_TIME =
            Tpot2TaskType.REMAINING_TIME.name().toLowerCase();
    private static final String MODEL_NEXT_ACTIVITY =
            Tpot2TaskType.NEXT_ACTIVITY.name().toLowerCase();
    private static final String MODEL_ANOMALY =
            Tpot2TaskType.ANOMALY_DETECTION.name().toLowerCase();

    /** Anomaly score above which {@link #PROCESS_ANOMALY_DETECTED} is emitted. */
    private static final float ANOMALY_THRESHOLD = 0.5f;

    // ── State ────────────────────────────────────────────────────────────────

    private final PredictiveModelRegistry registry;
    private final Consumer<ProcessEvent> eventSink;
    private final FeatureExtractor featureExtractor;

    // ── Feature extractor interface ──────────────────────────────────────────

    /**
     * Extracts a model feature vector from available work-item context.
     *
     * <p>The default ({@link #IDENTITY_EXTRACTOR}) encodes four dimensions: normalised
     * hashes of caseId, taskId, specId, and work-item age in hours.  Supply a
     * domain-specific extractor — built from
     * {@link org.yawlfoundation.yawl.pi.predictive.ProcessMiningTrainingDataExtractor}
     * feature definitions — for production accuracy.</p>
     */
    @FunctionalInterface
    public interface FeatureExtractor {
        /**
         * @param caseId  case identifier string
         * @param taskId  task identifier string (empty at case-level events)
         * @param specId  specification identifier string
         * @param ageMs   work-item age in milliseconds (0 at case-level events)
         * @return feature vector for {@link PredictiveModelRegistry#infer}
         */
        float[] extract(String caseId, String taskId, String specId, long ageMs);
    }

    /**
     * Default 4-dimensional hash extractor.  Suitable for integration testing;
     * replace with a domain extractor in production.
     */
    public static final FeatureExtractor IDENTITY_EXTRACTOR =
            (caseId, taskId, specId, ageMs) -> new float[]{
                Math.abs(caseId.hashCode()) % 10_000 / 10_000.0f,
                Math.abs(taskId.hashCode()) % 10_000 / 10_000.0f,
                Math.abs(specId.hashCode()) % 10_000 / 10_000.0f,
                (float) (ageMs / 3_600_000.0)
            };

    // ── Constructors ─────────────────────────────────────────────────────────

    /**
     * Creates an observer using {@link #IDENTITY_EXTRACTOR}.
     *
     * @param registry  ONNX model registry with loaded models
     * @param eventSink receives all emitted {@link ProcessEvent}s; must not throw
     */
    public PredictiveProcessObserver(PredictiveModelRegistry registry,
                                     Consumer<ProcessEvent> eventSink) {
        this(registry, eventSink, IDENTITY_EXTRACTOR);
    }

    /**
     * Creates an observer with a custom feature extractor.
     *
     * @param registry         ONNX model registry with loaded models
     * @param eventSink        receives all emitted {@link ProcessEvent}s; must not throw
     * @param featureExtractor domain-specific feature extraction strategy
     */
    public PredictiveProcessObserver(PredictiveModelRegistry registry,
                                     Consumer<ProcessEvent> eventSink,
                                     FeatureExtractor featureExtractor) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.eventSink = Objects.requireNonNull(eventSink, "eventSink");
        this.featureExtractor = Objects.requireNonNull(featureExtractor, "featureExtractor");
    }

    // ── ObserverGateway ──────────────────────────────────────────────────────

    @Override
    public String getScheme() {
        return SCHEME;
    }

    /**
     * Case started — runs {@code case_outcome} inference and emits
     * {@link #CASE_OUTCOME_PREDICTION}.
     * Payload: {@code caseId}, {@code specId}, {@code outcomeScore} [0–1],
     * {@code confidence} [0–1].
     */
    @Override
    public void announceCaseStarted(Set<YAWLServiceReference> services,
                                    YSpecificationID specID, YIdentifier caseID,
                                    String launchingService, boolean delayed) {
        String caseStr = caseID.toString();
        String specStr = specID.getIdentifier();
        log.debug("PI case-start: case={} spec={} delayed={}", caseStr, specStr, delayed);

        if (!registry.isAvailable(MODEL_CASE_OUTCOME)) return;
        try {
            float[] features = featureExtractor.extract(caseStr, "", specStr, 0L);
            float[] result = registry.infer(MODEL_CASE_OUTCOME, features);
            float score = result.length > 0 ? result[0] : 0.5f;

            eventSink.accept(new ProcessEvent(
                    "pi-" + caseStr + "-outcome-" + System.nanoTime(),
                    CASE_OUTCOME_PREDICTION,
                    SOURCE,
                    Instant.now(),
                    Map.of(
                            "caseId", caseStr,
                            "specId", specStr,
                            "outcomeScore", (double) score,
                            "confidence", (double) Math.min(1.0f, Math.abs(score - 0.5f) * 2.0f)
                    ),
                    score > 0.7f ? EventSeverity.HIGH : EventSeverity.LOW
            ));
        } catch (PIException e) {
            log.warn("Case outcome inference failed for case {}: {}", caseStr, e.getMessage());
        }
    }

    /**
     * Work item fired to a service — runs {@code remaining_time} inference on the
     * newly fired item and emits {@link #SLA_BREACH_PREDICTED}.
     * This is the earliest point at which time-to-completion can be predicted.
     */
    @Override
    public void announceFiredWorkItem(YAnnouncement announcement) {
        YWorkItem item = announcement.getItem();
        if (item == null) {
            log.warn("PI received announceFiredWorkItem with null YWorkItem; skipping inference");
            return;
        }
        String caseStr = item.getCaseID().toString();
        String taskStr = item.getTaskID();
        String specStr = item.getSpecificationID().getIdentifier();
        long ageMs = java.time.Duration.between(item.getEnablementTime(), Instant.now()).toMillis();

        log.debug("PI work-item fired: case={} task={}", caseStr, taskStr);

        if (!registry.isAvailable(MODEL_REMAINING_TIME)) return;
        try {
            float[] features = featureExtractor.extract(caseStr, taskStr, specStr, ageMs);
            float[] result = registry.infer(MODEL_REMAINING_TIME, features);
            float remainingMinutes = result.length > 0 ? result[0] : Float.MAX_VALUE;

            eventSink.accept(new ProcessEvent(
                    "pi-" + item.getIDString() + "-fired-time-" + System.nanoTime(),
                    SLA_BREACH_PREDICTED,
                    SOURCE,
                    Instant.now(),
                    Map.of(
                            "caseId", caseStr,
                            "taskId", taskStr,
                            "remainingMinutes", (double) remainingMinutes,
                            "trigger", "ITEM_FIRED"
                    ),
                    remainingMinutes < 30 ? EventSeverity.CRITICAL
                            : remainingMinutes < 120 ? EventSeverity.HIGH
                            : EventSeverity.LOW
            ));
        } catch (PIException e) {
            log.warn("Remaining-time inference failed for fired item {}: {}",
                    item.getIDString(), e.getMessage());
        }
    }

    /**
     * Work item cancelled — runs {@code anomaly_detection} inference because
     * mid-case cancellation is a strong anomaly signal; emits
     * {@link #PROCESS_ANOMALY_DETECTED} if score exceeds threshold.
     */
    @Override
    public void announceCancelledWorkItem(YAnnouncement announcement) {
        YWorkItem item = announcement.getItem();
        if (item == null) {
            log.warn("PI received announceCancelledWorkItem with null YWorkItem; skipping");
            return;
        }
        String caseStr = item.getCaseID().toString();
        String taskStr = item.getTaskID();
        String specStr = item.getSpecificationID().getIdentifier();

        log.debug("PI work-item cancelled: case={} task={}", caseStr, taskStr);

        emitAnomalyIfDetected(caseStr, taskStr, specStr, "ITEM_CANCELLED");
    }

    /**
     * Timer expired on a work item — this is a definite SLA breach requiring no
     * inference.  Emits {@link #TIMER_EXPIRY_BREACH} at {@link EventSeverity#CRITICAL}.
     * Also triggers anomaly detection since expired timers indicate process deviation.
     */
    @Override
    public void announceTimerExpiry(YAnnouncement announcement) {
        YWorkItem item = announcement.getItem();
        if (item == null) {
            log.warn("PI received announceTimerExpiry with null YWorkItem; skipping");
            return;
        }
        String caseStr = item.getCaseID().toString();
        String taskStr = item.getTaskID();
        String specStr = item.getSpecificationID().getIdentifier();

        log.warn("PI timer expiry (definite SLA breach): case={} task={}", caseStr, taskStr);

        // Timer expiry is a definite SLA breach — no model inference needed
        eventSink.accept(new ProcessEvent(
                "pi-" + item.getIDString() + "-timer-" + System.nanoTime(),
                TIMER_EXPIRY_BREACH,
                SOURCE,
                Instant.now(),
                Map.of(
                        "caseId", caseStr,
                        "taskId", taskStr,
                        "specId", specStr,
                        "breachType", "TIMER_EXPIRY"
                ),
                EventSeverity.CRITICAL
        ));

        // Timer expiry is also an anomaly signal
        emitAnomalyIfDetected(caseStr, taskStr, specStr, "TIMER_EXPIRY");
    }

    /**
     * Single-service case completion — runs anomaly detection and emits
     * {@link #PROCESS_ANOMALY_DETECTED} if the score exceeds threshold.
     * Delegates to the same anomaly logic as the broadcast variant.
     */
    @Override
    public void announceCaseCompletion(YAWLServiceReference yawlService,
                                       YIdentifier caseID, Document caseData) {
        String caseStr = caseID.toString();
        log.debug("PI case completion (single-service): case={}", caseStr);
        emitAnomalyIfDetected(caseStr, "", "", "CASE_COMPLETE_SINGLE");
    }

    /**
     * Broadcast case completion — the primary hook for post-case anomaly detection.
     * Emits {@link #PROCESS_ANOMALY_DETECTED} if {@code anomaly_detection} score
     * exceeds {@link #ANOMALY_THRESHOLD}.
     */
    @Override
    public void announceCaseCompletion(Set<YAWLServiceReference> services,
                                       YIdentifier caseID, Document caseData) {
        String caseStr = caseID.toString();
        log.debug("PI case completion (broadcast): case={}", caseStr);
        emitAnomalyIfDetected(caseStr, "", "", "CASE_COMPLETE");
    }

    /**
     * Work-item status transition — primary hook for SLA monitoring and next-activity
     * recommendation.
     *
     * <ul>
     *   <li>{@code remaining_time} model: emits {@link #SLA_BREACH_PREDICTED} with
     *       urgency-based severity.</li>
     *   <li>{@code next_activity} model on {@code statusComplete}: emits
     *       {@link #NEXT_ACTIVITY_SUGGESTION} with prediction confidence.</li>
     * </ul>
     */
    @Override
    public void announceWorkItemStatusChange(Set<YAWLServiceReference> services,
                                             YWorkItem workItem,
                                             YWorkItemStatus oldStatus,
                                             YWorkItemStatus newStatus) {
        String caseStr = workItem.getCaseID().toString();
        String taskStr = workItem.getTaskID();
        String specStr = workItem.getSpecificationID().getIdentifier();
        long ageMs = java.time.Duration.between(workItem.getEnablementTime(),
                Instant.now()).toMillis();

        log.debug("PI status change: case={} task={} {}→{}", caseStr, taskStr,
                oldStatus, newStatus);

        // ── Remaining-time prediction ────────────────────────────────────────
        if (registry.isAvailable(MODEL_REMAINING_TIME)) {
            try {
                float[] features = featureExtractor.extract(caseStr, taskStr, specStr, ageMs);
                float[] result = registry.infer(MODEL_REMAINING_TIME, features);
                float remainingMinutes = result.length > 0 ? result[0] : Float.MAX_VALUE;

                eventSink.accept(new ProcessEvent(
                        "pi-" + workItem.getIDString() + "-time-" + System.nanoTime(),
                        SLA_BREACH_PREDICTED,
                        SOURCE,
                        Instant.now(),
                        Map.of(
                                "caseId", caseStr,
                                "taskId", taskStr,
                                "remainingMinutes", (double) remainingMinutes,
                                "statusTransition", oldStatus.name() + "->" + newStatus.name()
                        ),
                        remainingMinutes < 30 ? EventSeverity.CRITICAL
                                : remainingMinutes < 120 ? EventSeverity.HIGH
                                : EventSeverity.LOW
                ));
            } catch (PIException e) {
                log.warn("Remaining-time inference failed for {}: {}",
                        workItem.getIDString(), e.getMessage());
            }
        }

        // ── Next-activity suggestion on completion ───────────────────────────
        if (newStatus == YWorkItemStatus.statusComplete
                && registry.isAvailable(MODEL_NEXT_ACTIVITY)) {
            try {
                float[] features = featureExtractor.extract(caseStr, taskStr, specStr, 0L);
                float[] result = registry.infer(MODEL_NEXT_ACTIVITY, features);
                float confidence = result.length > 0 ? result[0] : 0.0f;

                eventSink.accept(new ProcessEvent(
                        "pi-" + workItem.getIDString() + "-next-" + System.nanoTime(),
                        NEXT_ACTIVITY_SUGGESTION,
                        SOURCE,
                        Instant.now(),
                        Map.of(
                                "caseId", caseStr,
                                "completedTask", taskStr,
                                "confidence", (double) confidence
                        ),
                        EventSeverity.MEDIUM
                ));
            } catch (PIException e) {
                log.warn("Next-activity inference failed for {}: {}",
                        workItem.getIDString(), e.getMessage());
            }
        }
    }

    /**
     * Case suspended — logged at WARN because suspension indicates a blocked or
     * stalled case requiring operator attention.
     */
    @Override
    public void announceCaseSuspended(Set<YAWLServiceReference> services,
                                      YIdentifier caseID) {
        log.warn("PI case suspended (stalled): case={}", caseID);
    }

    /**
     * Case entering suspension — logged at INFO to track the suspension lifecycle.
     */
    @Override
    public void announceCaseSuspending(Set<YAWLServiceReference> services,
                                       YIdentifier caseID) {
        log.info("PI case suspending: case={}", caseID);
    }

    /**
     * Case resumed from suspension — logged at INFO to close the suspension lifecycle.
     */
    @Override
    public void announceCaseResumption(Set<YAWLServiceReference> services,
                                       YIdentifier caseID) {
        log.info("PI case resumed: case={}", caseID);
    }

    /**
     * Engine initialised — logs the number of registered services so operators
     * can verify the predictive observer is active.
     */
    @Override
    public void announceEngineInitialised(Set<YAWLServiceReference> services,
                                          int maxWaitSeconds) {
        log.info("PI observer active: {} registered services, maxWait={}s",
                services.size(), maxWaitSeconds);
    }

    /**
     * Case cancelled — runs anomaly detection because cancellations represent
     * process deviations; emits {@link #PROCESS_ANOMALY_DETECTED} if threshold exceeded.
     */
    @Override
    public void announceCaseCancellation(Set<YAWLServiceReference> services,
                                         YIdentifier id) {
        String caseStr = id.toString();
        log.warn("PI case cancelled: case={}", caseStr);
        emitAnomalyIfDetected(caseStr, "", "", "CASE_CANCELLED");
    }

    /**
     * Deadlock detected — emits {@link #PROCESS_ANOMALY_DETECTED} at
     * {@link EventSeverity#CRITICAL} with anomaly score 1.0 (structural deadlock is
     * a certain anomaly) and logs the deadlocked task IDs.
     */
    @Override
    public void announceDeadlock(Set<YAWLServiceReference> services,
                                 YIdentifier id, Set<YTask> tasks) {
        String caseStr = id.toString();
        String taskIds = tasks.stream()
                .map(YTask::getID)
                .reduce("", (a, b) -> a.isEmpty() ? b : a + "," + b);

        log.error("PI deadlock detected: case={} tasks=[{}]", caseStr, taskIds);

        // Deadlock is a structural anomaly — score is definitionally 1.0
        eventSink.accept(new ProcessEvent(
                "pi-" + caseStr + "-deadlock-" + System.nanoTime(),
                PROCESS_ANOMALY_DETECTED,
                SOURCE,
                Instant.now(),
                Map.of(
                        "caseId", caseStr,
                        "anomalyScore", 1.0,
                        "deadlockedTasks", taskIds,
                        "anomalyType", "STRUCTURAL_DEADLOCK"
                ),
                EventSeverity.CRITICAL
        ));
    }

    /**
     * Engine shutdown — logs observer deactivation so operators can confirm clean teardown.
     */
    @Override
    public void shutdown() {
        log.info("PI PredictiveProcessObserver shut down; no further inference will occur");
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Runs anomaly_detection inference and emits {@link #PROCESS_ANOMALY_DETECTED}
     * if the score exceeds {@link #ANOMALY_THRESHOLD}.  Silently skips if the model
     * is not loaded.
     */
    private void emitAnomalyIfDetected(String caseStr, String taskStr,
                                        String specStr, String trigger) {
        if (!registry.isAvailable(MODEL_ANOMALY)) return;
        try {
            float[] features = featureExtractor.extract(caseStr, taskStr, specStr, 0L);
            float[] result = registry.infer(MODEL_ANOMALY, features);
            float score = result.length > 0 ? result[0] : 0.0f;

            if (score > ANOMALY_THRESHOLD) {
                eventSink.accept(new ProcessEvent(
                        "pi-" + caseStr + "-anomaly-" + trigger + "-" + System.nanoTime(),
                        PROCESS_ANOMALY_DETECTED,
                        SOURCE,
                        Instant.now(),
                        Map.of(
                                "caseId", caseStr,
                                "anomalyScore", (double) score,
                                "trigger", trigger
                        ),
                        score > 0.8f ? EventSeverity.CRITICAL : EventSeverity.HIGH
                ));
            }
        } catch (PIException e) {
            log.warn("Anomaly inference failed for case {} (trigger={}): {}",
                    caseStr, trigger, e.getMessage());
        }
    }
}
