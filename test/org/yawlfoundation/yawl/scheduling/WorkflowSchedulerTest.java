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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD unit tests for {@link WorkflowScheduler}, {@link CronExpression},
 * {@link ScheduledCase}, and {@link RecurringSchedule}.
 *
 * <p>Uses real implementations — no mocks, no stubs.
 *
 * @since YAWL 6.0
 */
@Tag("unit")
class WorkflowSchedulerTest {

    private WorkflowScheduler scheduler;
    private List<String> firedSpecs;

    @BeforeEach
    void setUp() {
        firedSpecs = new ArrayList<>();
        scheduler  = new WorkflowScheduler((spec, data) -> {
            synchronized (firedSpecs) { firedSpecs.add(spec); }
        });
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdown();
    }

    // -------------------------------------------------------------------------
    // CronExpression
    // -------------------------------------------------------------------------

    @Test
    void cronParse_wildcardAllFields_nextAfterAdvancesOneMinute() throws Exception {
        CronExpression cron = CronExpression.parse("* * * * *");
        Instant base = Instant.parse("2026-03-01T09:00:00Z");
        Instant next = cron.nextAfter(base);
        assertEquals(Instant.parse("2026-03-01T09:01:00Z"), next);
    }

    @Test
    void cronDailyAt_correctNextOccurrence() throws Exception {
        CronExpression cron = CronExpression.dailyAt(6, 30);
        // If we're at 06:31 today, next should be 06:30 tomorrow
        Instant base = Instant.parse("2026-03-01T06:31:00Z");
        Instant next = cron.nextAfter(base);
        assertEquals(Instant.parse("2026-03-02T06:30:00Z"), next);
    }

    @Test
    void cronDailyAt_beforeTime_sameDay() throws Exception {
        CronExpression cron = CronExpression.dailyAt(9, 0);
        Instant base = Instant.parse("2026-03-01T08:00:00Z");
        Instant next = cron.nextAfter(base);
        assertEquals(Instant.parse("2026-03-01T09:00:00Z"), next);
    }

    @Test
    void cronEveryMinutes_step5() throws Exception {
        CronExpression cron = CronExpression.everyMinutes(5);
        Instant base = Instant.parse("2026-03-01T09:01:00Z");
        Instant next = cron.nextAfter(base);
        assertEquals(Instant.parse("2026-03-01T09:05:00Z"), next);
    }

    @Test
    void cronHourlyAt_next() throws Exception {
        CronExpression cron = CronExpression.hourlyAt(15);
        Instant base = Instant.parse("2026-03-01T09:16:00Z");
        Instant next = cron.nextAfter(base);
        assertEquals(Instant.parse("2026-03-01T10:15:00Z"), next);
    }

    @Test
    void cronParse_commaList_minutes() throws Exception {
        CronExpression cron = CronExpression.parse("0,30 * * * *");
        Instant base = Instant.parse("2026-03-01T09:01:00Z");
        Instant next = cron.nextAfter(base);
        assertEquals(Instant.parse("2026-03-01T09:30:00Z"), next);
    }

    @Test
    void cronParse_range_hours() throws Exception {
        CronExpression cron = CronExpression.parse("0 9-17 * * *");
        // At 17:01 → next day at 09:00
        Instant base = Instant.parse("2026-03-01T17:01:00Z");
        Instant next = cron.nextAfter(base);
        assertEquals(Instant.parse("2026-03-02T09:00:00Z"), next);
    }

    @Test
    void cronParse_invalidFieldCount_throws() {
        assertThrows(SchedulingException.class, () -> CronExpression.parse("* * * *"));
    }

    @Test
    void cronParse_outOfRangeValue_throws() {
        assertThrows(SchedulingException.class, () -> CronExpression.parse("60 * * * *"));
    }

    @Test
    void cronParse_null_throws() {
        assertThrows(SchedulingException.class, () -> CronExpression.parse(null));
    }

    @Test
    void cronEveryMinutes_outOfRange_throws() {
        assertThrows(SchedulingException.class, () -> CronExpression.everyMinutes(0));
        assertThrows(SchedulingException.class, () -> CronExpression.everyMinutes(60));
    }

    // -------------------------------------------------------------------------
    // ScheduledCase
    // -------------------------------------------------------------------------

    @Test
    void scheduledCase_initialStatusIsPending() {
        Instant future = Instant.now().plusSeconds(60);
        ScheduledCase sc = new ScheduledCase("spec-1", "<data/>", future);
        assertEquals(ScheduledCase.Status.PENDING, sc.getStatus());
        assertNotNull(sc.getId());
        assertEquals("spec-1", sc.getSpecificationId());
        assertEquals("<data/>", sc.getCaseData());
        assertEquals(future, sc.getScheduledAt());
    }

    @Test
    void scheduledCase_nullSpecId_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> new ScheduledCase(null, null, Instant.now().plusSeconds(10)));
    }

    @Test
    void scheduledCase_nullScheduledAt_throws() {
        assertThrows(NullPointerException.class,
            () -> new ScheduledCase("spec", null, null));
    }

    @Test
    void scheduledCase_statusTransition_worksCorrectly() {
        ScheduledCase sc = new ScheduledCase("spec", null, Instant.now().plusSeconds(60));
        sc.setStatus(ScheduledCase.Status.FIRED);
        assertEquals(ScheduledCase.Status.FIRED, sc.getStatus());
    }

    // -------------------------------------------------------------------------
    // RecurringSchedule
    // -------------------------------------------------------------------------

    @Test
    void recurringSchedule_nextOccurrence_matchesCron() throws Exception {
        CronExpression cron = CronExpression.dailyAt(8, 0);
        RecurringSchedule rs = new RecurringSchedule("spec-r", null, cron);
        assertTrue(rs.isActive());
        assertEquals(0L, rs.getFireCount());
        assertNull(rs.getLastFiredAt());

        Instant base = Instant.parse("2026-03-01T07:00:00Z");
        Instant next = rs.nextOccurrence(base);
        assertEquals(Instant.parse("2026-03-01T08:00:00Z"), next);
    }

    @Test
    void recurringSchedule_recordFiring_incrementsCount() throws Exception {
        CronExpression cron = CronExpression.dailyAt(8, 0);
        RecurringSchedule rs = new RecurringSchedule("spec-r", null, cron);

        Instant firedAt = Instant.parse("2026-03-01T08:00:00Z");
        Instant next = rs.recordFiring(firedAt);

        assertEquals(1L, rs.getFireCount());
        assertEquals(firedAt, rs.getLastFiredAt());
        assertEquals(Instant.parse("2026-03-02T08:00:00Z"), next);
    }

    @Test
    void recurringSchedule_cancelledThrowsOnNext() throws Exception {
        CronExpression cron = CronExpression.dailyAt(8, 0);
        RecurringSchedule rs = new RecurringSchedule("spec-r", null, cron);
        rs.cancel();
        assertFalse(rs.isActive());
        assertThrows(SchedulingException.class, () -> rs.nextOccurrence(Instant.now()));
    }

    @Test
    void recurringSchedule_nullCron_throws() {
        assertThrows(NullPointerException.class,
            () -> new RecurringSchedule("spec", null, null));
    }

    // -------------------------------------------------------------------------
    // WorkflowScheduler — one-shot
    // -------------------------------------------------------------------------

    @Test
    void scheduleCase_inPast_throws() {
        Instant past = Instant.now().minusSeconds(1);
        assertThrows(SchedulingException.class,
            () -> scheduler.scheduleCase("spec", null, past));
    }

    @Test
    void scheduleCase_nullScheduledAt_throws() {
        assertThrows(NullPointerException.class,
            () -> scheduler.scheduleCase("spec", null, null));
    }

    @Test
    void scheduleCase_fires_andCallbackInvoked() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        List<String> fired = new ArrayList<>();
        WorkflowScheduler s = new WorkflowScheduler((spec, data) -> {
            fired.add(spec);
            latch.countDown();
        });
        try {
            Instant soon = Instant.now().plusMillis(150);
            ScheduledCase sc = s.scheduleCase("my-spec", "<d/>", soon);
            assertEquals(ScheduledCase.Status.PENDING, sc.getStatus());

            assertTrue(latch.await(3, TimeUnit.SECONDS), "Callback not invoked in time");
            assertEquals(List.of("my-spec"), fired);
            assertEquals(ScheduledCase.Status.FIRED, sc.getStatus());
        } finally {
            s.shutdown();
        }
    }

    @Test
    void cancelCase_beforeFiring_statusIsCancelled() throws Exception {
        Instant far = Instant.now().plusSeconds(300);
        ScheduledCase sc = scheduler.scheduleCase("spec-cancel", null, far);
        assertTrue(scheduler.cancelCase(sc.getId()));
        assertEquals(ScheduledCase.Status.CANCELLED, sc.getStatus());
        assertTrue(scheduler.getUpcoming(Instant.now(), far.plusSeconds(1)).isEmpty());
    }

    @Test
    void cancelCase_unknownId_returnsFalse() {
        assertFalse(scheduler.cancelCase("no-such-id"));
    }

    @Test
    void getUpcoming_returnsOnlyPendingInWindow() throws Exception {
        Instant t1 = Instant.now().plusSeconds(60);
        Instant t2 = Instant.now().plusSeconds(120);
        Instant t3 = Instant.now().plusSeconds(300);
        scheduler.scheduleCase("s1", null, t1);
        scheduler.scheduleCase("s2", null, t2);
        scheduler.scheduleCase("s3", null, t3);

        List<ScheduledCase> upcoming = scheduler.getUpcoming(
            Instant.now(), Instant.now().plusSeconds(200));
        assertEquals(2, upcoming.size());
    }

    // -------------------------------------------------------------------------
    // WorkflowScheduler — recurring
    // -------------------------------------------------------------------------

    @Test
    void scheduleRecurring_registersAndIsActive() throws Exception {
        CronExpression cron = CronExpression.dailyAt(3, 0);
        RecurringSchedule rs = scheduler.scheduleRecurring("spec-rec", null, cron);
        assertTrue(rs.isActive());
        assertEquals(1, scheduler.getRecurringSchedules().size());
    }

    @Test
    void cancelRecurring_removesSchedule() throws Exception {
        CronExpression cron = CronExpression.dailyAt(4, 0);
        RecurringSchedule rs = scheduler.scheduleRecurring("spec-rec", null, cron);
        assertTrue(scheduler.cancelRecurring(rs.getId()));
        assertFalse(rs.isActive());
        assertTrue(scheduler.getRecurringSchedules().isEmpty());
    }

    @Test
    void cancelRecurring_unknownId_returnsFalse() {
        assertFalse(scheduler.cancelRecurring("no-such-id"));
    }
}
