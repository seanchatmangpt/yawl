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
import org.yawlfoundation.yawl.elements.YAWLServiceReference;
import org.yawlfoundation.yawl.engine.YEngine;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;

/**
 * Health indicator for external service connectivity (A2A agents, MCP services).
 *
 * This indicator reports on:
 * - Registered YAWL services availability
 * - Service response times
 * - Failed services count
 * - Overall external connectivity status
 *
 * @author YAWL Foundation
 * @version 5.2
 */
@Component
public class YExternalServicesHealthIndicator implements HealthIndicator {

    private static final Logger _logger = LogManager.getLogger(YExternalServicesHealthIndicator.class);

    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 5000;
    private static final int MAX_CONCURRENT_CHECKS = 10;
    private static final long HEALTH_CHECK_TIMEOUT_MS = 10000;

    private final YEngine engine;
    private final ExecutorService executorService;

    public YExternalServicesHealthIndicator() {
        this.engine = YEngine.getInstance();
        this.executorService = Executors.newFixedThreadPool(
            MAX_CONCURRENT_CHECKS,
            runnable -> {
                Thread thread = new Thread(runnable, "external-service-health-check");
                thread.setDaemon(true);
                return thread;
            }
        );
    }

    @Override
    public Health health() {
        try {
            Set<YAWLServiceReference> services = engine.getYAWLServices();

            if (services == null || services.isEmpty()) {
                return Health.up()
                    .withDetail("services", "none")
                    .withDetail("reason", "No external services registered")
                    .build();
            }

            Map<String, ServiceHealthStatus> serviceStatuses = checkAllServices(services);

            int totalServices = serviceStatuses.size();
            long healthyServices = serviceStatuses.values().stream()
                .filter(status -> status.isHealthy)
                .count();
            long unhealthyServices = totalServices - healthyServices;

            Map<String, Object> details = new HashMap<>();
            details.put("totalServices", totalServices);
            details.put("healthyServices", healthyServices);
            details.put("unhealthyServices", unhealthyServices);

            Map<String, Object> serviceDetails = new HashMap<>();
            for (Map.Entry<String, ServiceHealthStatus> entry : serviceStatuses.entrySet()) {
                ServiceHealthStatus status = entry.getValue();
                Map<String, Object> statusMap = new HashMap<>();
                statusMap.put("status", status.isHealthy ? "UP" : "DOWN");
                statusMap.put("responseTime", status.responseTime + "ms");
                if (status.errorMessage != null) {
                    statusMap.put("error", status.errorMessage);
                }
                serviceDetails.put(entry.getKey(), statusMap);
            }
            details.put("services", serviceDetails);

            if (unhealthyServices == totalServices) {
                return Health.down()
                    .withDetails(details)
                    .withDetail("reason", "All external services are unavailable")
                    .build();
            }

            if (unhealthyServices > 0) {
                double failureRate = (double) unhealthyServices / totalServices;
                if (failureRate > 0.5) {
                    return Health.down()
                        .withDetails(details)
                        .withDetail("reason", "More than 50% of services are unavailable")
                        .build();
                } else {
                    return Health.up()
                        .withDetails(details)
                        .status("WARNING")
                        .withDetail("warning", unhealthyServices + " service(s) unavailable")
                        .build();
                }
            }

            return Health.up().withDetails(details).build();

        } catch (Exception e) {
            _logger.error("Error checking external services health", e);
            return Health.down()
                .withDetail("error", e.getClass().getName())
                .withDetail("message", e.getMessage())
                .build();
        }
    }

    private Map<String, ServiceHealthStatus> checkAllServices(Set<YAWLServiceReference> services) {
        Map<String, ServiceHealthStatus> results = new ConcurrentHashMap<>();
        List<Future<Void>> futures = new ArrayList<>();

        for (YAWLServiceReference service : services) {
            Future<Void> future = executorService.submit(() -> {
                ServiceHealthStatus status = checkServiceHealth(service);
                results.put(service.getServiceName(), status);
                return null;
            });
            futures.add(future);
        }

        for (Future<Void> future : futures) {
            try {
                future.get(HEALTH_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                _logger.warn("Service health check timed out");
                future.cancel(true);
            } catch (Exception e) {
                _logger.warn("Service health check failed", e);
            }
        }

        return results;
    }

    private ServiceHealthStatus checkServiceHealth(YAWLServiceReference service) {
        long startTime = System.currentTimeMillis();

        try {
            URI serviceURI = service.getURI();
            if (serviceURI == null) {
                return new ServiceHealthStatus(false, 0, "No URI configured");
            }

            URL url = serviceURI.toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);

            int responseCode = connection.getResponseCode();
            long responseTime = System.currentTimeMillis() - startTime;

            connection.disconnect();

            if (responseCode >= 200 && responseCode < 400) {
                return new ServiceHealthStatus(true, responseTime, null);
            } else {
                return new ServiceHealthStatus(false, responseTime,
                    "HTTP " + responseCode);
            }

        } catch (IOException e) {
            long responseTime = System.currentTimeMillis() - startTime;
            return new ServiceHealthStatus(false, responseTime, e.getMessage());
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            _logger.error("Unexpected error checking service: " + service.getServiceName(), e);
            return new ServiceHealthStatus(false, responseTime,
                e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static class ServiceHealthStatus {
        final boolean isHealthy;
        final long responseTime;
        final String errorMessage;

        ServiceHealthStatus(boolean isHealthy, long responseTime, String errorMessage) {
            this.isHealthy = isHealthy;
            this.responseTime = responseTime;
            this.errorMessage = errorMessage;
        }
    }
}
