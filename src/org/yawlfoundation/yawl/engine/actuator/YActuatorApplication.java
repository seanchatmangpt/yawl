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

package org.yawlfoundation.yawl.engine.actuator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boot application for YAWL Actuator endpoints.
 *
 * This application provides cloud-native health and metrics endpoints
 * for Kubernetes, Cloud Run, and other cloud platforms.
 *
 * Endpoints:
 * - /actuator/health - Overall system health
 * - /actuator/health/liveness - Liveness probe
 * - /actuator/health/readiness - Readiness probe
 * - /actuator/metrics - Metrics endpoint
 * - /actuator/prometheus - Prometheus metrics
 *
 * @author YAWL Foundation
 * @version 5.2
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "org.yawlfoundation.yawl.engine.actuator"
})
public class YActuatorApplication {


    private static final Logger logger = LogManager.getLogger(YActuatorApplication.class);
    private static final Logger _logger = LogManager.getLogger(YActuatorApplication.class);

    public static void main(String[] args) {
        _logger.info("Starting YAWL Actuator Application");
        SpringApplication.run(YActuatorApplication.class, args);
        _logger.info("YAWL Actuator Application started successfully");
    }
}
