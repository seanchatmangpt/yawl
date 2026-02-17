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
import org.yawlfoundation.yawl.engine.YWorkItemRepository;

/**
 * Health indicator for YAWL Engine state and capacity.
 *
 * This indicator reports on:
 * - Engine running status
 * - Active workflow cases
 * - Work item queue depth
 * - Engine capacity and load
 *
 * @author YAWL Foundation
 * @version 5.2
 */
@Component
public class YEngineHealthIndicator implements HealthIndicator {


    private static final Logger logger = LogManager.getLogger(YEngineHealthIndicator.class);
    private static final Logger _logger = LogManager.getLogger(YEngineHealthIndicator.class);

    private static final int MAX_ACTIVE_CASES = 10000;
    private static final int MAX_WORK_ITEMS = 50000;
    private static final double CRITICAL_LOAD_THRESHOLD = 0.9;
    private static final double WARNING_LOAD_THRESHOLD = 0.75;

    private final YEngine engine;
    private final YWorkItemRepository workItemRepository;

    public YEngineHealthIndicator() {
        this.engine = YEngine.getInstance();
        this.workItemRepository = YWorkItemRepository.getInstance();
    }

    @Override
    public Health health() {
        try {
            YEngine.Status engineStatus = engine.getEngineStatus();

            if (engineStatus == null || engineStatus == YEngine.Status.Dormant) {
                return Health.down()
                    .withDetail("status", "dormant")
                    .withDetail("reason", "Engine is not initialized")
                    .build();
            }

            if (engineStatus == YEngine.Status.Terminating) {
                return Health.down()
                    .withDetail("status", "terminating")
                    .withDetail("reason", "Engine is shutting down")
                    .build();
            }

            if (engineStatus == YEngine.Status.Initialising) {
                return Health.up()
                    .withDetail("status", "initializing")
                    .withDetail("ready", false)
                    .withDetail("reason", "Engine is starting up")
                    .build();
            }

            int activeCases = getActiveCaseCount();
            int workItemCount = getWorkItemCount();
            int loadedSpecifications = getLoadedSpecificationCount();

            double caseLoad = (double) activeCases / MAX_ACTIVE_CASES;
            double workItemLoad = (double) workItemCount / MAX_WORK_ITEMS;
            double overallLoad = Math.max(caseLoad, workItemLoad);

            Health.Builder healthBuilder = Health.up()
                .withDetail("status", engineStatus.toString().toLowerCase())
                .withDetail("ready", true)
                .withDetail("activeCases", activeCases)
                .withDetail("maxActiveCases", MAX_ACTIVE_CASES)
                .withDetail("workItems", workItemCount)
                .withDetail("maxWorkItems", MAX_WORK_ITEMS)
                .withDetail("loadedSpecifications", loadedSpecifications)
                .withDetail("caseLoad", "%.2f%%".formatted(caseLoad * 100))
                .withDetail("workItemLoad", "%.2f%%".formatted(workItemLoad * 100))
                .withDetail("overallLoad", "%.2f%%".formatted(overallLoad * 100));

            if (overallLoad >= CRITICAL_LOAD_THRESHOLD) {
                healthBuilder.down()
                    .withDetail("reason", "Critical load threshold exceeded");
            } else if (overallLoad >= WARNING_LOAD_THRESHOLD) {
                healthBuilder.status("WARNING")
                    .withDetail("warning", "Load approaching capacity");
            }

            return healthBuilder.build();

        } catch (Exception e) {
            _logger.error("Error checking engine health", e);
            return Health.down()
                .withDetail("error", e.getClass().getName())
                .withDetail("message", e.getMessage())
                .build();
        }
    }

    private int getActiveCaseCount() {
        try {
            return engine.getRunningCaseCount();
        } catch (Exception e) {
            _logger.warn("Failed to get active case count", e);
            return -1;
        }
    }

    private int getWorkItemCount() {
        try {
            return workItemRepository.getWorkItemCount();
        } catch (Exception e) {
            _logger.warn("Failed to get work item count", e);
            return -1;
        }
    }

    private int getLoadedSpecificationCount() {
        try {
            return engine.getLoadedSpecificationCount();
        } catch (Exception e) {
            _logger.warn("Failed to get loaded specification count", e);
            return -1;
        }
    }
}
