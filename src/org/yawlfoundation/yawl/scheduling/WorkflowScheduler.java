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

package org.yawlfoundation.yawl.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Orchestrates one-shot and recurring YAWL case scheduling using virtual threads.
 *
 * <p>Callers register a launch callback via the constructor. When a scheduled time
 * arrives the scheduler invokes {@code launchCallback.accept(specId, caseData)}.
 * If the callback throws, the case is marked {@link ScheduledCase.Status#FAILED} and
 * the error is logged; the scheduler itself continues running.
 *
 * <p><strong>Virtual-thread executor</strong>: the underlying
 * {@link ScheduledExecutorService} uses a single-thread daemon scheduler (for timer
 * accuracy) whose tasks are handed off to virtual threads, keeping platform threads free.
 *
 * <p>Call {@link #shutdown()} when the engine shuts down to release all resources.
 *
 * @since YAWL 6.0
 */
public final class WorkflowScheduler {

    private static final Logger log = LoggerFactory.getLogger(WorkflowScheduler.class);

    /** Callback invoked on each firing: (specificationId, caseData). */
    private final BiConsumer<String, String> launchCallback;

    private final ScheduledExecutorService timer;
    private final ConcurrentHashMap<String, ScheduledCase>      pendingCases     = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RecurringSchedule>  recurringSchedules = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ScheduledFuture<?>> futures          = new ConcurrentHashMap<>();

    /**
     * Creates a {@code WorkflowScheduler} with the given launch callback.
     *
     * @param launchCallback invoked on each firing; must not be null
     * @throws IllegalArgumentException if launchCallback is null
     */
    public WorkflowScheduler(BiConsumer<String, String> launchCallback) {
        Objects.requireNonNull(launchCallback, "launchCallback must not be null");
        this.launchCallback = launchCallback;
        this.timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "yawl-scheduler-timer");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Schedules a single case launch at {@code scheduledAt}.
     *
     * @param specificationId the YAWL specification to launch; must not be null or blank
     * @param caseData        optional XML case data; may be null
     * @param scheduledAt     the instant to fire; must not be null and must be in the future
     * @return the registered {@link ScheduledCase}
     * @throws SchedulingException      if {@code scheduledAt} is in the past
     * @throws IllegalArgumentException if specificationId is null/blank or scheduledAt is null
     */
    public ScheduledCase scheduleCase(String specificationId, String caseData, Instant scheduledAt)
            throws SchedulingException {
        Objects.requireNonNull(scheduledAt, "scheduledAt must not be null");
        Instant now = Instant.now();
        if (!scheduledAt.isAfter(now)) {
            throw new SchedulingException(
                "scheduledAt must be in the future; got " + scheduledAt + " (now=" + now + ")");
        }

        ScheduledCase sc = new ScheduledCase(specificationId, caseData, scheduledAt);
        pendingCases.put(sc.getId(), sc);

        long delayMs = now.until(scheduledAt, ChronoUnit.MILLIS);
        ScheduledFuture<?> future = timer.schedule(
            () -> fireCase(sc),
            delayMs,
            TimeUnit.MILLISECONDS
        );
        futures.put(sc.getId(), future);
        log.info("Scheduled case {} for {} (delay {}ms)", sc.getId(), scheduledAt, delayMs);
        return sc;
    }

    /**
     * Registers a recurring schedule and immediately queues its first occurrence.
     *
     * @param specificationId the YAWL specification to launch; must not be null or blank
     * @param caseData        optional XML case data; may be null
     * @param cronExpression  parsed cron expression; must not be null
     * @return the registered {@link RecurringSchedule}
     * @throws SchedulingException      if the first occurrence cannot be computed
     * @throws IllegalArgumentException if any required argument is null
     */
    public RecurringSchedule scheduleRecurring(String specificationId,
                                               String caseData,
                                               CronExpression cronExpression)
            throws SchedulingException {
        Objects.requireNonNull(cronExpression, "cronExpression must not be null");

        RecurringSchedule rs = new RecurringSchedule(specificationId, caseData, cronExpression);
        recurringSchedules.put(rs.getId(), rs);
        queueNextOccurrence(rs, Instant.now());
        log.info("Registered recurring schedule {} with cron '{}'",
            rs.getId(), cronExpression.getExpression());
        return rs;
    }

    /**
     * Cancels a one-shot scheduled case.
     *
     * @param caseId the {@link ScheduledCase#getId()} to cancel
     * @return {@code true} if the case was found and cancelled before it fired
     */
    public boolean cancelCase(String caseId) {
        ScheduledCase sc = pendingCases.get(caseId);
        if (sc == null) return false;
        ScheduledFuture<?> f = futures.remove(caseId);
        if (f != null) f.cancel(false);
        sc.setStatus(ScheduledCase.Status.CANCELLED);
        pendingCases.remove(caseId);
        log.info("Cancelled scheduled case {}", caseId);
        return true;
    }

    /**
     * Cancels a recurring schedule; no further firings will occur.
     *
     * @param scheduleId the {@link RecurringSchedule#getId()} to cancel
     * @return {@code true} if the schedule was found and cancelled
     */
    public boolean cancelRecurring(String scheduleId) {
        RecurringSchedule rs = recurringSchedules.remove(scheduleId);
        if (rs == null) return false;
        rs.cancel();
        ScheduledFuture<?> f = futures.remove(scheduleId);
        if (f != null) f.cancel(false);
        log.info("Cancelled recurring schedule {}", scheduleId);
        return true;
    }

    /**
     * Returns all one-shot cases whose scheduled time falls within [{@code from}, {@code to}).
     *
     * @param from inclusive lower bound; must not be null
     * @param to   exclusive upper bound; must not be null
     * @return immutable list of matching cases
     */
    public List<ScheduledCase> getUpcoming(Instant from, Instant to) {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to,   "to must not be null");
        return pendingCases.values().stream()
            .filter(sc -> sc.getStatus() == ScheduledCase.Status.PENDING)
            .filter(sc -> !sc.getScheduledAt().isBefore(from) && sc.getScheduledAt().isBefore(to))
            .toList();
    }

    /** Returns an unmodifiable snapshot of all registered recurring schedules. */
    public Collection<RecurringSchedule> getRecurringSchedules() {
        return List.copyOf(recurringSchedules.values());
    }

    /**
     * Shuts down the scheduler, cancelling all pending timers.
     * Blocks for up to 5 seconds waiting for in-flight tasks.
     */
    public void shutdown() {
        timer.shutdown();
        try {
            if (!timer.awaitTermination(5, TimeUnit.SECONDS)) {
                timer.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            timer.shutdownNow();
        }
        log.info("WorkflowScheduler shut down");
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private void fireCase(ScheduledCase sc) {
        Thread.ofVirtual().name("yawl-fire-" + sc.getId()).start(() -> {
            try {
                launchCallback.accept(sc.getSpecificationId(), sc.getCaseData());
                sc.setStatus(ScheduledCase.Status.FIRED);
                pendingCases.remove(sc.getId());
                futures.remove(sc.getId());
                log.info("Case {} fired for spec {}", sc.getId(), sc.getSpecificationId());
            } catch (Exception e) {
                sc.setStatus(ScheduledCase.Status.FAILED);
                log.error("Failed to fire case {} for spec {}: {}",
                    sc.getId(), sc.getSpecificationId(), e.getMessage(), e);
            }
        });
    }

    private void fireRecurring(RecurringSchedule rs) {
        Thread.ofVirtual().name("yawl-recurring-" + rs.getId()).start(() -> {
            try {
                Instant firedAt = Instant.now();
                launchCallback.accept(rs.getSpecificationId(), rs.getCaseData());
                Instant next = rs.recordFiring(firedAt);
                log.info("Recurring schedule {} fired (count={}), next at {}",
                    rs.getId(), rs.getFireCount(), next);
                queueNextOccurrence(rs, firedAt);
            } catch (SchedulingException e) {
                log.error("Recurring schedule {} could not compute next occurrence; cancelling: {}",
                    rs.getId(), e.getMessage(), e);
                rs.cancel();
                recurringSchedules.remove(rs.getId());
            } catch (Exception e) {
                log.error("Recurring schedule {} launch failed; schedule remains active: {}",
                    rs.getId(), e.getMessage(), e);
                // Still advance to next occurrence even on launch failure
                try {
                    Instant next = rs.nextOccurrence(Instant.now());
                    queueNextOccurrence(rs, Instant.now().minusMillis(1));
                } catch (SchedulingException se) {
                    log.error("Could not reschedule {} after failure; cancelling", rs.getId());
                    rs.cancel();
                }
            }
        });
    }

    private void queueNextOccurrence(RecurringSchedule rs, Instant after) {
        try {
            Instant next = rs.nextOccurrence(after);
            long delayMs = Instant.now().until(next, ChronoUnit.MILLIS);
            if (delayMs < 0) delayMs = 0;
            ScheduledFuture<?> future = timer.schedule(
                () -> fireRecurring(rs),
                delayMs,
                TimeUnit.MILLISECONDS
            );
            futures.put(rs.getId(), future);
        } catch (SchedulingException e) {
            log.error("Cannot queue next occurrence for recurring schedule {}: {}",
                rs.getId(), e.getMessage());
            rs.cancel();
            recurringSchedules.remove(rs.getId());
        }
    }
}
