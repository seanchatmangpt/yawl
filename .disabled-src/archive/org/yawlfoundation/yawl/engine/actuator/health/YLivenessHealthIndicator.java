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

/**
 * Liveness health indicator for Kubernetes/Cloud Run liveness probes.
 *
 * This indicator reports whether the YAWL engine process is alive and not deadlocked.
 * Liveness failures should result in container restart.
 *
 * Reports DOWN only if:
 * - Engine is in a terminated/deadlocked state
 * - Critical internal failure detected
 *
 * @author YAWL Foundation
 * @version 5.2
 */
@Component("livenessHealthIndicator")
public class YLivenessHealthIndicator implements HealthIndicator {


    private static final Logger logger = LogManager.getLogger(YLivenessHealthIndicator.class);
    private static final Logger _logger = LogManager.getLogger(YLivenessHealthIndicator.class);

    private final YEngine engine;
    private volatile long lastSuccessfulCheck;
    private static final long DEADLOCK_THRESHOLD_MS = 60000; // 1 minute

    public YLivenessHealthIndicator() {
        this.engine = YEngine.getInstance();
        this.lastSuccessfulCheck = System.currentTimeMillis();
    }

    @Override
    public Health health() {
        try {
            YEngine.Status engineStatus = engine.getEngineStatus();

            if (engineStatus == null) {
                _logger.error("Engine status is null - possible initialization failure");
                return Health.down()
                    .withDetail("status", "null")
                    .withDetail("reason", "Engine not initialized")
                    .build();
            }

            if (engineStatus == YEngine.Status.Terminating) {
                return Health.down()
                    .withDetail("status", "terminating")
                    .withDetail("reason", "Engine is shutting down")
                    .build();
            }

            long currentTime = System.currentTimeMillis();
            long timeSinceLastCheck = currentTime - lastSuccessfulCheck;

            if (timeSinceLastCheck > DEADLOCK_THRESHOLD_MS) {
                _logger.warn("Possible deadlock detected - {} ms since last successful check",
                    timeSinceLastCheck);
                return Health.down()
                    .withDetail("status", engineStatus.toString().toLowerCase())
                    .withDetail("reason", "Possible deadlock detected")
                    .withDetail("timeSinceLastCheck", timeSinceLastCheck + "ms")
                    .build();
            }

            lastSuccessfulCheck = currentTime;

            return Health.up()
                .withDetail("status", engineStatus.toString().toLowerCase())
                .withDetail("alive", true)
                .withDetail("uptime", getEngineUptime())
                .build();

        } catch (Exception e) {
            _logger.error("Critical error in liveness check", e);
            return Health.down()
                .withDetail("error", e.getClass().getName())
                .withDetail("message", e.getMessage())
                .withDetail("reason", "Critical internal failure")
                .build();
        }
    }

    private String getEngineUptime() {
        try {
            long startTime = engine.getStartTime();
            if (startTime > 0) {
                long uptimeMs = System.currentTimeMillis() - startTime;
                long hours = uptimeMs / 3600000;
                long minutes = (uptimeMs % 3600000) / 60000;
                long seconds = (uptimeMs % 60000) / 1000;
                return String.format("%dh %dm %ds", hours, minutes, seconds);
            }
        } catch (Exception e) {
            _logger.debug("Could not retrieve engine uptime", e);
        }
        return "unknown";
    }
}
