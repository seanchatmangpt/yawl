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

package org.yawlfoundation.yawl.engine.actuator.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

/**
 * Spring Boot Actuator configuration for YAWL engine.
 *
 * Configures:
 * - Health endpoints with liveness and readiness groups
 * - Prometheus metrics registry
 * - Custom endpoint paths for Kubernetes compatibility
 * - Security and access controls
 *
 * Note: HealthEndpointGroups is auto-configured by Spring Boot Actuator.
 * Configure groups via application.properties:
 * - management.endpoint.health.group.liveness.include=livenessProbe
 * - management.endpoint.health.group.readiness.include=readinessProbe
 *
 * @author YAWL Foundation
 * @version 5.2
 */
@Configuration
public class ActuatorConfiguration {


    private static final Logger logger = LogManager.getLogger(ActuatorConfiguration.class);
    private static final Logger _logger = LogManager.getLogger(ActuatorConfiguration.class);

    @Bean
    public PrometheusMeterRegistry prometheusMeterRegistry() {
        _logger.info("Configuring Prometheus metrics registry for YAWL");
        return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    }
}
