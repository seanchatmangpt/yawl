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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Blue-ocean race-condition tests for the cancel-before-fire TOCTOU window in
 * {@link WorkflowScheduler}.
 *
 * <h2>The race</h2>
 * <ol>
 *   <li>{@code scheduleCase()} registers a {@link ScheduledCase} in {@code pendingCases}
 *       and stores a {@link java.util.concurrent.ScheduledFuture} in {@code futures}.</li>
 *   <li>When the timer fires, {@code fireCase()} spawns a virtual thread that calls
 *       {@code launchCallback} and then sets status to {@code FIRED}.</li>
 *   <li>{@code cancelCase()} calls {@link java.util.concurrent.ScheduledFuture#cancel(boolean)
 *       cancel(false)} (non-interrupting) and then unconditionally sets status to
 *       {@code CANCELLED}.</li>
 * </ol>
 *
 * <p>When the timer task has already started before {@code cancel(false)} is called,
 * {@code cancel(false)} returns {@code false} but the virtual fire-thread continues
 * running. Both threads then race to write the {@code volatile} status field: the
 * virtual thread writes {@code FIRED} after calling the callback, while the cancel
 * thread writes {@code CANCELLED}. The result is that a cancelled case can still
 * invoke the launch callback — a safety invariant violation.
 *
 * <h2>Invariant under test</h2>
 * <pre>
 *   A ScheduledCase is either CANCELLED or FIRED, never both,
 *   and the launch callback must never be invoked for a case that was
 *   successfully cancelled (i.e. cancelCase returned true AND cancel(false)
 *   returned true, meaning the timer had not yet started).
 * </pre>
 *
 * <p>No mocks, stubs, or fakes — all tests use the real {@link WorkflowScheduler}.
 *
 * @since YAWL 6.0
 */
@Tag("unit")
@DisplayName("WorkflowScheduler cancel-fire TOCTOU race invariants")
class SchedulerCancelFireRaceTest {

    /**
     * Exercises the tight cancel/fire race window 100 times.
     *
     * <p>A case is scheduled 1 ms in the future so that the cancel attempt and the
     * timer fire compete. In each iteration exactly one of the following must be true:
     * <ul>
     *   <li>Cancel won: {@code cancelCase} returned {@code true} AND
     *       the launch callback was NOT invoked.</li>
     *   <li>Timer won: {@code cancelCase} returned {@code false} (or {@code true} but
     *       the cancel arrived after the virtual thread had already called the callback)
     *       AND the callback was invoked exactly once.</li>
     * </ul>
     *
     * <p>The callback must never be invoked more than once, and a case whose cancel
     * call both returned {@code true} and prevented the timer from dispatching (i.e.
     * {@code future.cancel(false)} returned {@code true} inside the scheduler) must
     * not invoke the callback at all. Because the scheduler's internal
     * {@code future.cancel(false)} result is not exposed to callers, the observable
     * contract tested here is the weaker but still critical property:
     * {@code launched <= 1} always, and {@code cancelled && launched > 0} must never
     * occur when the cancel genuinely pre-empted the fire.
     */
    @RepeatedTest(100)
    @DisplayName("cancel and fire are mutually exclusive: callback invoked at most once, never after a pre-emptive cancel")
    void cancelBeforeFireIsMutuallyExclusiveWithLaunch() throws InterruptedException {
        AtomicInteger launchCount = new AtomicInteger(0);
        WorkflowScheduler scheduler = new WorkflowScheduler(
                (specId, caseData) -> launchCount.incrementAndGet());

        ScheduledCase sc = null;
        try {
            sc = scheduler.scheduleCase("spec-race", "<data/>", Instant.now().plusMillis(1));
        } catch (SchedulingException e) {
            // scheduledAt was computed in the past by the time scheduleCase ran;
            // treat as a timer-already-won scenario with 0 launches expected.
            scheduler.shutdown();
            assertEquals(0, launchCount.get(),
                    "No launch expected when scheduling itself failed");
            return;
        }

        boolean cancelled = scheduler.cancelCase(sc.getId());

        // Give the timer thread (and the virtual fire-thread it may have spawned) time
        // to complete. 10 ms is sufficient: the timer delay was 1 ms and virtual thread
        // dispatch is sub-millisecond on any modern JVM.
        Thread.sleep(10);

        int launched = launchCount.get();

        // Invariant 1: callback invoked at most once, regardless of race outcome.
        assertTrue(launched <= 1,
                "Case fired multiple times — exactly-once guarantee violated; launched=" + launched);

        // Invariant 2: if cancel pre-empted the fire (the only case where the scheduler's
        // internal future.cancel returned true is when the timer task had not yet started),
        // the callback must not have been invoked.  The only observable proxy we have is
        // whether cancelCase returned true AND launched > 0, which indicates the scheduler
        // set CANCELLED but the virtual thread also ran — a definitive invariant breach.
        assertFalse(cancelled && launched > 0,
                "Cancelled case was still launched — TOCTOU race detected; "
                + "cancelled=" + cancelled + ", launched=" + launched);

        scheduler.shutdown();
    }

    /**
     * Schedules 10 cases one second in the future and immediately cancels all of them.
     *
     * <p>A 1-second scheduling horizon gives the cancel calls ample time to complete
     * before any timer could fire. After 1.5 seconds of waiting, no case must have
     * been launched and every case must be in {@code CANCELLED} status.
     */
    @Test
    @DisplayName("confirmed cancel (1s horizon) prevents all launches")
    void confirmedCancelPreventsLaunch() throws Exception {
        AtomicInteger launchCount = new AtomicInteger(0);
        WorkflowScheduler scheduler = new WorkflowScheduler(
                (specId, caseData) -> launchCount.incrementAndGet());

        List<ScheduledCase> cases = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            cases.add(scheduler.scheduleCase(
                    "spec-cancel-" + i,
                    "<data/>",
                    Instant.now().plusSeconds(1)));
        }

        // Cancel all cases immediately — well within the 1-second window.
        for (ScheduledCase sc : cases) {
            assertTrue(scheduler.cancelCase(sc.getId()),
                    "cancelCase must return true for a case scheduled 1s ahead");
        }

        // Wait past the original scheduled time to confirm no deferred firing occurs.
        Thread.sleep(1500);

        assertEquals(0, launchCount.get(),
                "No confirmed-cancelled case should have launched; launched=" + launchCount.get());

        for (ScheduledCase sc : cases) {
            assertEquals(ScheduledCase.Status.CANCELLED, sc.getStatus(),
                    "Every confirmed-cancelled case must report CANCELLED; id=" + sc.getId());
        }

        scheduler.shutdown();
    }

    /**
     * Schedules 5 cases 50 ms in the future and waits 200 ms for them all to fire.
     *
     * <p>No cancel is issued. Each case must be launched exactly once and must report
     * {@code FIRED} status afterwards.
     */
    @Test
    @DisplayName("confirmed fire (50ms horizon, no cancel) delivers each case exactly once")
    void confirmedFireDeliversExactlyOnce() throws Exception {
        AtomicInteger launchCount = new AtomicInteger(0);
        WorkflowScheduler scheduler = new WorkflowScheduler(
                (specId, caseData) -> launchCount.incrementAndGet());

        List<ScheduledCase> cases = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            cases.add(scheduler.scheduleCase(
                    "spec-fire-" + i,
                    "<data/>",
                    Instant.now().plusMillis(50)));
        }

        // Wait well past the 50 ms fire time. 200 ms gives virtual threads plenty of
        // time to complete the callback and update the status field.
        Thread.sleep(200);

        assertEquals(5, launchCount.get(),
                "Each of the 5 cases must fire exactly once; launched=" + launchCount.get());

        for (ScheduledCase sc : cases) {
            assertEquals(ScheduledCase.Status.FIRED, sc.getStatus(),
                    "Every confirmed-fired case must report FIRED; id=" + sc.getId());
        }

        scheduler.shutdown();
    }
}
