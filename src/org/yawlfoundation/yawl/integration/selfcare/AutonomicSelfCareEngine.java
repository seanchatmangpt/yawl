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

package org.yawlfoundation.yawl.integration.selfcare;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MAPE-K engine that autonomically schedules and monitors self-care actions without
 * requiring user deliberation.
 *
 * <p>Guiding principle: <em>"You can act your way into right action but you can't think
 * your way into right actions."</em> The engine generates plans and triggers actions
 * automatically — people do not need to decide before starting.</p>
 *
 * <h2>MAPE-K Cycle (default period: 60 seconds)</h2>
 * <ol>
 *   <li><strong>Monitor</strong> — poll scheduled action completion status</li>
 *   <li><strong>Analyse</strong> — detect overdue or missed actions</li>
 *   <li><strong>Plan</strong> — reschedule missed actions at reduced duration</li>
 *   <li><strong>Execute</strong> — trigger next action via scheduled callback</li>
 * </ol>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * GregverseSearchClient search = new GregverseSearchClient(gregverseUrl);
 * AutonomicSelfCareEngine engine = new AutonomicSelfCareEngine(search);
 * engine.start();
 *
 * BehavioralActivationPlan plan = engine.generatePlan(OTDomain.SELF_CARE, 3);
 * engine.scheduleActions(plan);
 *
 * // Actions trigger automatically — no further deliberation required
 * EngineStatus status = engine.getStatus();
 * engine.stop();
 * }</pre>
 *
 * @since YAWL 6.0
 */
public final class AutonomicSelfCareEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutonomicSelfCareEngine.class);

    /** Default interval between self-care action triggers. */
    static final Duration DEFAULT_ACTION_INTERVAL = Duration.ofHours(4);

    /** MAPE-K monitoring period. */
    static final Duration MONITOR_PERIOD = Duration.ofSeconds(60);

    private final GregverseSearchClient search;
    private final ScheduledExecutorService scheduler;

    private final CopyOnWriteArrayList<ScheduledAction> scheduledActions = new CopyOnWriteArrayList<>();
    private final AtomicInteger completedCount = new AtomicInteger(0);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<Instant> lastMonitorAt = new AtomicReference<>(null);

    private ScheduledFuture<?> mapeKFuture;

    /**
     * Creates the engine with the given Gregverse search client.
     *
     * @param search search client for Gregverse workflow discovery
     */
    public AutonomicSelfCareEngine(GregverseSearchClient search) {
        this(search, Executors.newScheduledThreadPool(2,
            Thread.ofVirtual().factory()));
    }

    /**
     * Creates the engine with a custom scheduler. Use this constructor in tests to
     * inject a deterministic or manual-trigger executor.
     *
     * @param search    search client for Gregverse workflow discovery
     * @param scheduler scheduler to use for action triggers and MAPE-K loop
     */
    public AutonomicSelfCareEngine(GregverseSearchClient search,
                                    ScheduledExecutorService scheduler) {
        this.search = Objects.requireNonNull(search, "search must not be null");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Generates a behavioral activation plan for the given OT domain.
     *
     * <p>Actions are sourced from Gregverse search results where available. When
     * search returns no results (e.g. endpoint offline), built-in starter actions
     * are used so the plan is never empty.</p>
     *
     * <p>Actions are ordered easiest-first (shortest estimated duration), so
     * {@link BehavioralActivationPlan#nextAction()} is always the lowest-barrier
     * entry point.</p>
     *
     * @param domain      the OT performance area to plan for
     * @param actionCount number of actions to include (must be ≥ 1)
     * @return a ready-to-schedule behavioral activation plan
     */
    public BehavioralActivationPlan generatePlan(OTDomain domain, int actionCount) {
        Objects.requireNonNull(domain, "domain must not be null");
        if (actionCount < 1) {
            throw new IllegalArgumentException("actionCount must be at least 1, got: " + actionCount);
        }

        List<SelfCareAction> actions = buildActions(domain, actionCount);

        // Sort easiest-first (shortest estimated duration)
        actions.sort(Comparator.comparing(SelfCareAction::estimated));

        return new BehavioralActivationPlan(
            UUID.randomUUID().toString(),
            domain,
            actions,
            Instant.now(),
            "Behavioral activation plan for " + domain.description()
                + ". Start with the first action — motivation follows from doing."
        );
    }

    /**
     * Autonomically schedules all actions in the given plan at computed intervals.
     *
     * <p>Actions are spaced {@link #DEFAULT_ACTION_INTERVAL} apart. No user input
     * is required after this call — actions trigger on schedule.</p>
     *
     * @param plan the plan whose actions to schedule
     */
    public void scheduleActions(BehavioralActivationPlan plan) {
        Objects.requireNonNull(plan, "plan must not be null");

        long delaySeconds = 0;
        for (SelfCareAction action : plan.actions()) {
            long finalDelay = delaySeconds;
            ScheduledFuture<?> future = scheduler.schedule(
                () -> triggerAction(action),
                finalDelay,
                TimeUnit.SECONDS
            );
            scheduledActions.add(new ScheduledAction(action, future, Instant.now().plusSeconds(finalDelay)));
            delaySeconds += DEFAULT_ACTION_INTERVAL.toSeconds();

            LOGGER.info("Scheduled action: '{}' in {} hours",
                action.title(), TimeUnit.SECONDS.toHours(finalDelay));
        }
    }

    /**
     * Starts the MAPE-K monitoring loop. The loop runs every {@link #MONITOR_PERIOD}
     * and autonomically reschedules any overdue actions.
     *
     * <p>Idempotent: calling {@code start()} on an already-running engine is a no-op.</p>
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            mapeKFuture = scheduler.scheduleAtFixedRate(
                this::runMapeK,
                MONITOR_PERIOD.toSeconds(),
                MONITOR_PERIOD.toSeconds(),
                TimeUnit.SECONDS
            );
            LOGGER.info("AutonomicSelfCareEngine started (MAPE-K period: {}s)",
                MONITOR_PERIOD.toSeconds());
        }
    }

    /**
     * Gracefully stops the engine. Waits up to 10 seconds for in-flight callbacks to
     * complete before forcing shutdown.
     *
     * <p>Idempotent: calling {@code stop()} on an already-stopped engine is a no-op.</p>
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            if (mapeKFuture != null) {
                mapeKFuture.cancel(false);
            }
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                scheduler.shutdownNow();
            }
            LOGGER.info("AutonomicSelfCareEngine stopped");
        }
    }

    /**
     * Returns a snapshot of the engine's current operational status.
     *
     * @return status record with scheduled/completed counts and running flag
     */
    public EngineStatus getStatus() {
        return new EngineStatus(
            scheduledActions.size(),
            completedCount.get(),
            running.get(),
            lastMonitorAt.get()
        );
    }

    /**
     * Snapshot of the engine's operational status at a point in time.
     *
     * @param scheduledActions total number of actions ever scheduled
     * @param completedActions number of actions that have fired
     * @param running          whether the MAPE-K loop is active
     * @param lastMonitorAt    timestamp of the most recent MAPE-K cycle, or null if not started
     */
    public record EngineStatus(
            int scheduledActions,
            int completedActions,
            boolean running,
            Instant lastMonitorAt
    ) {
        public EngineStatus {
            if (scheduledActions < 0) {
                throw new IllegalArgumentException("scheduledActions must not be negative");
            }
            if (completedActions < 0) {
                throw new IllegalArgumentException("completedActions must not be negative");
            }
        }
    }

    // ─── Internal: action building ────────────────────────────────────────────

    private List<SelfCareAction> buildActions(OTDomain domain, int count) {
        // Prefer Gregverse results where available
        List<GregverseSearchClient.WorkflowSpecSummary> specs = search.searchByDomain(domain);

        List<SelfCareAction> actions = new ArrayList<>();
        for (GregverseSearchClient.WorkflowSpecSummary spec : specs) {
            if (actions.size() >= count) break;
            actions.add(new SelfCareAction.DailyLivingAction(
                spec.specId(),
                spec.specName(),
                spec.description(),
                Duration.ofMinutes(15), // default estimate for Gregverse-backed actions
                domain,
                spec.specId()
            ));
        }

        // Pad with built-in starter actions if Gregverse returned fewer than requested
        if (actions.size() < count) {
            actions.addAll(starterActions(domain, count - actions.size()));
        }

        return actions;
    }

    private List<SelfCareAction> starterActions(OTDomain domain, int count) {
        // Built-in starter actions per domain — always available, no external dependency
        List<SelfCareAction> starters = switch (domain) {
            case SELF_CARE -> List.of(
                new SelfCareAction.PhysicalActivity(
                    "sc-walk-5", "Take a 5-minute walk",
                    "Step outside or walk indoors for 5 minutes. Movement is the entry point.",
                    Duration.ofMinutes(5), domain, 1),
                new SelfCareAction.DailyLivingAction(
                    "sc-water", "Drink a glass of water",
                    "Hydration supports every other self-care activity. Do this first.",
                    Duration.ofMinutes(1), domain, "sc-hydration-spec"),
                new SelfCareAction.CognitiveActivity(
                    "sc-breathe", "Three slow breaths",
                    "Inhale for 4 counts, hold for 4, exhale for 6. Resets the nervous system.",
                    Duration.ofMinutes(2), domain, "parasympathetic activation")
            );
            case PRODUCTIVITY -> List.of(
                new SelfCareAction.CognitiveActivity(
                    "prod-list", "Write tomorrow's one task",
                    "Write a single most-important task for tomorrow. One is enough to start.",
                    Duration.ofMinutes(3), domain, "planning"),
                new SelfCareAction.DailyLivingAction(
                    "prod-tidy", "Tidy one surface",
                    "Clear one desk, bench, or table. Environment shapes action.",
                    Duration.ofMinutes(5), domain, "prod-environment-spec"),
                new SelfCareAction.SocialEngagement(
                    "prod-checkin", "Send one supportive message",
                    "Message someone to check in. Connection is productive.",
                    Duration.ofMinutes(3), domain, 2)
            );
            case LEISURE -> List.of(
                new SelfCareAction.SocialEngagement(
                    "lei-connect", "Call someone you enjoy",
                    "A 5-minute call with someone you like is restorative. Start dialing.",
                    Duration.ofMinutes(5), domain, 2),
                new SelfCareAction.CognitiveActivity(
                    "lei-read", "Read one page",
                    "Open a book or article and read just one page. Curiosity builds from doing.",
                    Duration.ofMinutes(5), domain, "reading fluency"),
                new SelfCareAction.PhysicalActivity(
                    "lei-stretch", "Gentle 5-minute stretch",
                    "Slowly stretch neck, shoulders, and wrists. Leisure includes your body.",
                    Duration.ofMinutes(5), domain, 1)
            );
        };

        // Return the requested count, cycling if needed
        List<SelfCareAction> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(starters.get(i % starters.size()));
        }
        return result;
    }

    // ─── Internal: MAPE-K ─────────────────────────────────────────────────────

    private void runMapeK() {
        try {
            lastMonitorAt.set(Instant.now());

            // Monitor: find overdue actions (scheduled but not yet done, past due time)
            List<ScheduledAction> overdue = scheduledActions.stream()
                .filter(sa -> !sa.future().isDone() && Instant.now().isAfter(sa.scheduledFor()))
                .toList();

            if (overdue.isEmpty()) {
                LOGGER.debug("MAPE-K: all actions on schedule ({} total, {} completed)",
                    scheduledActions.size(), completedCount.get());
                return;
            }

            // Analyse: log overdue count
            LOGGER.info("MAPE-K: {} overdue action(s) detected", overdue.size());

            // Plan + Execute: reschedule each overdue action immediately with a shorter prompt
            for (ScheduledAction sa : overdue) {
                if (!sa.future().isCancelled()) {
                    sa.future().cancel(false);
                }
                SelfCareAction shortened = shortenAction(sa.action());
                ScheduledFuture<?> newFuture = scheduler.schedule(
                    () -> triggerAction(shortened),
                    30, TimeUnit.SECONDS // brief delay to allow natural pause
                );
                scheduledActions.add(new ScheduledAction(shortened, newFuture,
                    Instant.now().plusSeconds(30)));
                LOGGER.info("MAPE-K: rescheduled overdue action '{}' as '{}'",
                    sa.action().title(), shortened.title());
            }
        } catch (Exception e) {
            LOGGER.error("MAPE-K cycle error", e);
        }
    }

    private void triggerAction(SelfCareAction action) {
        LOGGER.info("ACTION: {} — {}", action.title(), action.description());
        completedCount.incrementAndGet();
    }

    /**
     * Returns a shortened version of the action to reduce barrier on reschedule.
     * Physical actions drop one intensity level; all actions halve their estimated time.
     */
    private SelfCareAction shortenAction(SelfCareAction action) {
        Duration half = action.estimated().dividedBy(2).compareTo(Duration.ofMinutes(1)) < 0
            ? Duration.ofMinutes(1)
            : action.estimated().dividedBy(2);

        return switch (action) {
            case SelfCareAction.DailyLivingAction a ->
                new SelfCareAction.DailyLivingAction(
                    a.id() + "-short", "Quick: " + a.title(), a.description(), half, a.domain(), a.specId());
            case SelfCareAction.PhysicalActivity a ->
                new SelfCareAction.PhysicalActivity(
                    a.id() + "-short", "Quick: " + a.title(), a.description(), half, a.domain(),
                    Math.max(1, a.intensityLevel() - 1));
            case SelfCareAction.CognitiveActivity a ->
                new SelfCareAction.CognitiveActivity(
                    a.id() + "-short", "Quick: " + a.title(), a.description(), half, a.domain(), a.cognitiveTarget());
            case SelfCareAction.SocialEngagement a ->
                new SelfCareAction.SocialEngagement(
                    a.id() + "-short", "Quick: " + a.title(), a.description(), half, a.domain(), a.groupSize());
        };
    }

    // ─── Internal: tracking record ────────────────────────────────────────────

    private record ScheduledAction(
            SelfCareAction action,
            ScheduledFuture<?> future,
            Instant scheduledFor
    ) {}
}
