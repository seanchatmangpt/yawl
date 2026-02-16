/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.authentication;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages session timeouts using a scheduled executor service with virtual threads.
 * <p>
 * This class provides thread-safe timeout management for sessions, replacing the
 * legacy {@link java.util.Timer} approach with modern {@link ScheduledExecutorService}
 * that supports virtual threads (Java 25+).
 * </p>
 *
 * <h2>Thread Safety</h2>
 * <ul>
 *   <li>All public methods are thread-safe</li>
 *   <li>Uses ConcurrentHashMap for task tracking</li>
 *   <li>Atomic task cancellation and rescheduling</li>
 * </ul>
 *
 * @author Michael Adams
 * @since 2.1
 */
public class YSessionTimer {

    private static final Logger LOGGER = Logger.getLogger(YSessionTimer.class.getName());

    /** Shared scheduler using virtual threads for lightweight timeout handling */
    private static final ScheduledExecutorService SHARED_SCHEDULER =
            Executors.newScheduledThreadPool(
                    Runtime.getRuntime().availableProcessors(),
                    Thread.ofVirtual().factory()
            );

    /** Map of sessions to their scheduled timeout tasks */
    private final ConcurrentHashMap<YAbstractSession, ScheduledFuture<?>> sessionTasks;

    /** Reference to the session cache for timeout callbacks */
    private final ISessionCache cache;

    /**
     * Creates a new session timer backed by the given cache.
     *
     * @param cache the session cache to notify on timeouts
     * @throws NullPointerException if cache is null
     */
    public YSessionTimer(ISessionCache cache) {
        this.cache = Objects.requireNonNull(cache, "Cache cannot be null");
        this.sessionTasks = new ConcurrentHashMap<>();
    }

    /**
     * Gets the session cache associated with this timer.
     *
     * @return the session cache
     */
    public ISessionCache getCache() {
        return cache;
    }

    /**
     * Schedules a timeout task for the given session.
     * <p>
     * If the session has a negative interval, no timeout is scheduled
     * (session never expires).
     * </p>
     *
     * @param session the session to schedule a timeout for
     * @return true if a timeout was scheduled, false if session is null or has no timeout
     */
    public boolean add(YAbstractSession session) {
        if (session == null) {
            return false;
        }

        long intervalMs = session.getInterval();

        // Negative interval means never timeout
        if (intervalMs <= 0) {
            return true;
        }

        String handle = session.getHandle();

        ScheduledFuture<?> future = SHARED_SCHEDULER.schedule(
                () -> {
                    try {
                        cache.expire(handle);
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING,
                                "Error expiring session: " + handle, e);
                    }
                },
                intervalMs,
                TimeUnit.MILLISECONDS
        );

        ScheduledFuture<?> existing = sessionTasks.put(session, future);

        // Cancel any previously scheduled task (shouldn't happen in normal use)
        if (existing != null && !existing.isDone()) {
            existing.cancel(false);
        }

        return true;
    }

    /**
     * Resets the timeout for a session by cancelling the existing task
     * and scheduling a new one.
     *
     * @param session the session to reset the timeout for
     * @return true if reset successfully, false if session is null
     */
    public boolean reset(YAbstractSession session) {
        if (session == null) {
            return false;
        }

        expire(session);
        return add(session);
    }

    /**
     * Cancels the timeout task for a session without expiring the session.
     *
     * @param session the session to cancel the timeout for
     * @return true if a task was cancelled, false if session is null or had no task
     */
    public boolean expire(YAbstractSession session) {
        if (session == null) {
            return false;
        }

        ScheduledFuture<?> task = sessionTasks.remove(session);

        if (task != null && !task.isDone()) {
            task.cancel(false);
            return true;
        }

        return false;
    }

    /**
     * Shuts down the shared scheduler.
     * <p>
     * <strong>Warning:</strong> This affects all YSessionTimer instances since
     * they share the same scheduler. Only call this during application shutdown.
     * </p>
     */
    public void shutdown() {
        LOGGER.info("Shutting down session timer scheduler...");

        // Cancel all pending tasks
        sessionTasks.values().forEach(task -> {
            if (!task.isDone()) {
                task.cancel(false);
            }
        });
        sessionTasks.clear();

        // Shutdown shared scheduler
        SHARED_SCHEDULER.shutdown();
        try {
            if (!SHARED_SCHEDULER.awaitTermination(5, TimeUnit.SECONDS)) {
                SHARED_SCHEDULER.shutdownNow();
            }
        } catch (InterruptedException e) {
            SHARED_SCHEDULER.shutdownNow();
            Thread.currentThread().interrupt();
        }

        LOGGER.info("Session timer scheduler shutdown complete");
    }

    /**
     * Returns the number of sessions with active timeout tasks.
     *
     * @return count of sessions with pending timeouts
     */
    public int getActiveTimeoutCount() {
        return sessionTasks.size();
    }
}
