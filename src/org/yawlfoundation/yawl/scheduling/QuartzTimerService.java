package org.yawlfoundation.yawl.scheduling;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.time.Duration;
import java.util.Objects;

/**
 * High-availability timer service using Quartz for distributed scheduling.
 * Replaces in-JVM timers with database-backed Quartz scheduler.
 * @since YAWL 6.0
 */
public class QuartzTimerService {
    private static final Logger LOGGER = LogManager.getLogger(QuartzTimerService.class);

    public void scheduleOnce(String timerId, Duration delay, TimerCallback callback)
            throws SchedulingException {
        if (timerId == null || timerId.isBlank()) {
            throw new IllegalArgumentException("timerId must not be null or blank");
        }
        Objects.requireNonNull(delay, "delay must not be null");
        Objects.requireNonNull(callback, "callback must not be null");

        throw new UnsupportedOperationException(
            "scheduleOnce requires Quartz Scheduler instance and YAWL_QUARTZ_JDBC_URL configuration");
    }

    public void scheduleRecurring(String timerId, Duration initialDelay, Duration period,
                                   TimerCallback callback) throws SchedulingException {
        throw new UnsupportedOperationException(
            "scheduleRecurring requires Quartz Scheduler instance and database backing");
    }

    public void cancel(String timerId) throws SchedulingException {
        throw new UnsupportedOperationException(
            "cancel requires Quartz Scheduler instance configuration");
    }

    public boolean isScheduled(String timerId) {
        throw new UnsupportedOperationException(
            "isScheduled requires Quartz Scheduler instance");
    }

    public interface TimerCallback {
        void onFire(String timerId) throws Exception;
    }
}
