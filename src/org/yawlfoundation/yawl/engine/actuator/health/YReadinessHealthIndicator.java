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

package org.yawlfoundation.yawl.engine.actuator.health;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.engine.YPersistenceManager;

/**
 * Readiness health indicator for Kubernetes/Cloud Run readiness probes.
 *
 * This indicator reports whether the YAWL engine is ready to accept traffic.
 * Readiness failures should remove pod from load balancer but not restart it.
 *
 * Reports DOWN if:
 * - Engine is not fully initialized
 * - Engine is shutting down
 * - Database is unavailable (if persistence enabled)
 * - Engine is overloaded beyond capacity
 *
 * @author YAWL Foundation
 * @version 5.2
 */
@Component("readinessHealthIndicator")
public class YReadinessHealthIndicator implements HealthIndicator {


    private static final Logger logger = LogManager.getLogger(YReadinessHealthIndicator.class);
    private static final Logger _logger = LogManager.getLogger(YReadinessHealthIndicator.class);

    private static final int MAX_ACTIVE_CASES = 10000;
    private static final double OVERLOAD_THRESHOLD = 0.95;

    private final YEngine engine;
    private final YPersistenceManager persistenceManager;

    public YReadinessHealthIndicator() {
        this.engine = YEngine.getInstance();
        this.persistenceManager = YPersistenceManager.getInstance();
    }

    @Override
    public Health health() {
        try {
            YEngine.Status engineStatus = engine.getEngineStatus();

            if (engineStatus == null || engineStatus == YEngine.Status.Dormant) {
                return Health.down()
                    .withDetail("status", "dormant")
                    .withDetail("ready", false)
                    .withDetail("reason", "Engine not initialized")
                    .build();
            }

            if (engineStatus == YEngine.Status.Initialising) {
                return Health.down()
                    .withDetail("status", "initializing")
                    .withDetail("ready", false)
                    .withDetail("reason", "Engine still initializing")
                    .build();
            }

            if (engineStatus == YEngine.Status.Terminating) {
                return Health.down()
                    .withDetail("status", "terminating")
                    .withDetail("ready", false)
                    .withDetail("reason", "Engine is shutting down")
                    .build();
            }

            if (persistenceManager.isPersisting() && !isDatabaseReady()) {
                return Health.down()
                    .withDetail("status", engineStatus.toString().toLowerCase())
                    .withDetail("ready", false)
                    .withDetail("reason", "Database unavailable")
                    .build();
            }

            int activeCases = getActiveCaseCount();
            double load = (double) activeCases / MAX_ACTIVE_CASES;

            if (load > OVERLOAD_THRESHOLD) {
                return Health.down()
                    .withDetail("status", engineStatus.toString().toLowerCase())
                    .withDetail("ready", false)
                    .withDetail("reason", "Engine overloaded")
                    .withDetail("activeCases", activeCases)
                    .withDetail("maxCases", MAX_ACTIVE_CASES)
                    .withDetail("load", "%.2f%%".formatted(load * 100))
                    .build();
            }

            return Health.up()
                .withDetail("status", engineStatus.toString().toLowerCase())
                .withDetail("ready", true)
                .withDetail("activeCases", activeCases)
                .withDetail("load", "%.2f%%".formatted(load * 100))
                .build();

        } catch (Exception e) {
            _logger.error("Error in readiness check", e);
            return Health.down()
                .withDetail("ready", false)
                .withDetail("error", e.getClass().getName())
                .withDetail("message", e.getMessage())
                .build();
        }
    }

    private boolean isDatabaseReady() {
        try {
            return persistenceManager.getSessionFactory() != null &&
                   !persistenceManager.getSessionFactory().isClosed();
        } catch (Exception e) {
            _logger.warn("Failed to check database readiness", e);
            return false;
        }
    }

    private int getActiveCaseCount() {
        try {
            return engine.getRunningCaseCount();
        } catch (Exception e) {
            _logger.warn("Failed to get active case count", e);
            return 0;
        }
    }
}
