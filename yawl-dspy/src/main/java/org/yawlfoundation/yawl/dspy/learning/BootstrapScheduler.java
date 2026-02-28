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

package org.yawlfoundation.yawl.dspy.learning;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Objects;

/**
 * Schedules incremental bootstrap of DSPy programs when new cases complete.
 *
 * <p>Monitors case completion events and triggers DSPy BootstrapFewShot
 * compilation when a threshold of new completed cases is reached. Uses
 * a counter-based trigger mechanism to batch compilations for efficiency.</p>
 *
 * <h2>Bootstrap Trigger</h2>
 * <p>When the number of new completed cases reaches the threshold (default: 10),
 * a bootstrap compilation is triggered. This retrains the DSPy program with
 * the latest historical examples to improve quality over time.</p>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe. The completion counter is atomic, and the
 * lock protects the bootstrap operation itself.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class BootstrapScheduler {

    private static final Logger log = LogManager.getLogger(BootstrapScheduler.class);

    private static final int DEFAULT_BOOTSTRAP_THRESHOLD = 10;

    private final CaseLearningBootstrapper bootstrapper;
    private final int bootstrapThreshold;
    private final AtomicLong completedCasesSinceBootstrap;
    private final ReentrantLock bootstrapLock = new ReentrantLock();

    /**
     * Creates a new bootstrap scheduler with default threshold (10 cases).
     *
     * @param bootstrapper the CaseLearningBootstrapper to invoke; must not be null
     * @throws NullPointerException if bootstrapper is null
     */
    public BootstrapScheduler(CaseLearningBootstrapper bootstrapper) {
        this(bootstrapper, DEFAULT_BOOTSTRAP_THRESHOLD);
    }

    /**
     * Creates a new bootstrap scheduler with custom threshold.
     *
     * @param bootstrapper        the CaseLearningBootstrapper to invoke; must not be null
     * @param bootstrapThreshold  number of cases before triggering bootstrap; must be > 0
     * @throws NullPointerException     if bootstrapper is null
     * @throws IllegalArgumentException if bootstrapThreshold <= 0
     */
    public BootstrapScheduler(CaseLearningBootstrapper bootstrapper, int bootstrapThreshold) {
        this.bootstrapper = Objects.requireNonNull(bootstrapper, "bootstrapper must not be null");
        if (bootstrapThreshold <= 0) {
            throw new IllegalArgumentException("bootstrapThreshold must be > 0, got: " + bootstrapThreshold);
        }
        this.bootstrapThreshold = bootstrapThreshold;
        this.completedCasesSinceBootstrap = new AtomicLong(0);
        log.info("BootstrapScheduler initialized: threshold={} cases", bootstrapThreshold);
    }

    /**
     * Records a case completion and triggers bootstrap if threshold is reached.
     *
     * <p>Called by YWorkItemEventListener when a case completes. Increments the
     * completion counter and initiates bootstrap if threshold is exceeded.</p>
     *
     * @throws Exception if bootstrap execution fails
     */
    public void onCaseCompleted() throws Exception {
        long newCount = completedCasesSinceBootstrap.incrementAndGet();

        if (newCount >= bootstrapThreshold) {
            triggerBootstrap();
        }
    }

    /**
     * Manually triggers a bootstrap compilation immediately.
     *
     * <p>Acquires a lock to ensure only one bootstrap runs at a time.
     * Resets the completion counter after successful bootstrap.</p>
     *
     * @throws Exception if bootstrap execution fails
     */
    public void triggerBootstrap() throws Exception {
        bootstrapLock.lock();
        try {
            long currentCount = completedCasesSinceBootstrap.get();
            log.info("Triggering bootstrap: {} cases completed since last bootstrap", currentCount);

            bootstrapper.bootstrap(currentCount);

            // Reset counter after successful bootstrap
            completedCasesSinceBootstrap.set(0);
            log.info("Bootstrap completed successfully, counter reset");

        } finally {
            bootstrapLock.unlock();
        }
    }

    /**
     * Returns the current count of completed cases since last bootstrap.
     *
     * @return the number of completed cases
     */
    public long getCompletedCasesSinceBootstrap() {
        return completedCasesSinceBootstrap.get();
    }

    /**
     * Returns the bootstrap threshold.
     *
     * @return the number of cases required to trigger bootstrap
     */
    public int getBootstrapThreshold() {
        return bootstrapThreshold;
    }

    /**
     * Resets the completion counter (e.g., after manual bootstrap).
     */
    public void resetCounter() {
        long previous = completedCasesSinceBootstrap.getAndSet(0);
        log.debug("Bootstrap counter reset from {} to 0", previous);
    }
}
