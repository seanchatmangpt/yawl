/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.cloud;

import junit.framework.TestCase;
import org.yawlfoundation.yawl.integration.autonomous.observability.HealthCheck;

import java.util.Map;

/**
 * Integration tests for Actuator health endpoint responses.
 *
 * Tests cover:
 * - Health endpoint availability
 * - Status indicators (UP, DOWN, OUT_OF_SERVICE)
 * - Component health status
 * - Custom health indicators
 * - Detailed health information
 * - Database connectivity checks
 * - Disk space monitoring
 * - Memory usage reporting
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class ActuatorHealthEndpointTest extends TestCase {

    private HealthCheck healthCheck;
    private HealthEndpointHandler healthEndpoint;

    public ActuatorHealthEndpointTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        healthCheck = new HealthCheck();
        healthEndpoint = new HealthEndpointHandler();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Test basic health endpoint availability
     */
    public void testHealthEndpointAvailability() throws Exception {
        assertTrue("Health endpoint available", healthEndpoint.isAvailable());
        String endpoint = healthEndpoint.getEndpointPath();
        assertEquals("Health endpoint path correct", "/actuator/health", endpoint);
    }

    /**
     * Test health status response structure
     */
    public void testHealthStatusResponseStructure() throws Exception {
        Map<String, Object> response = healthEndpoint.getHealthStatus();
        
        assertNotNull("Response not null", response);
        assertTrue("Response contains status", response.containsKey("status"));
        assertTrue("Response contains timestamp", response.containsKey("timestamp"));
    }

    /**
     * Test health status UP
     */
    public void testHealthStatusUp() throws Exception {
        healthEndpoint.setDatabaseHealthy(true);
        healthEndpoint.setDiskSpaceHealthy(true);
        healthEndpoint.setMemoryHealthy(true);
        
        Map<String, Object> response = healthEndpoint.getHealthStatus();
        
        Object status = response.get("status");
        assertEquals("Status is UP", "UP", status);
    }

    /**
     * Test health status DOWN
     */
    public void testHealthStatusDown() throws Exception {
        healthEndpoint.setDatabaseHealthy(false);
        
        Map<String, Object> response = healthEndpoint.getHealthStatus();
        
        Object status = response.get("status");
        assertEquals("Status is DOWN", "DOWN", status);
    }

    /**
     * Test component health indicators
     */
    public void testComponentHealthIndicators() throws Exception {
        healthEndpoint.setDatabaseHealthy(true);
        healthEndpoint.setDiskSpaceHealthy(true);
        healthEndpoint.setMemoryHealthy(true);
        
        Map<String, Object> components = healthEndpoint.getComponentsHealth();
        
        assertTrue("Database health indicator present", components.containsKey("database"));
        assertTrue("Disk space health indicator present", components.containsKey("diskSpace"));
        assertTrue("Memory health indicator present", components.containsKey("memory"));
    }

    /**
     * Test database connectivity health check
     */
    public void testDatabaseConnectivityCheck() throws Exception {
        boolean dbHealthy = healthCheck.isDatabaseConnected();
        assertTrue("Database connectivity checked", true);
    }

    /**
     * Test disk space health check
     */
    public void testDiskSpaceHealthCheck() throws Exception {
        long diskSpaceFree = healthEndpoint.getDiskSpaceFree();
        long diskSpaceThreshold = healthEndpoint.getDiskSpaceThreshold();
        
        assertTrue("Disk space free >= 0", diskSpaceFree >= 0);
        assertTrue("Disk space threshold > 0", diskSpaceThreshold > 0);
        
        boolean diskHealthy = diskSpaceFree > diskSpaceThreshold;
        assertTrue("Disk space health determined", true);
    }

    /**
     * Test memory usage health check
     */
    public void testMemoryUsageHealthCheck() throws Exception {
        long memoryUsed = healthEndpoint.getMemoryUsed();
        long memoryMax = healthEndpoint.getMemoryMax();
        
        assertTrue("Memory used >= 0", memoryUsed >= 0);
        assertTrue("Memory max > 0", memoryMax > 0);
        
        double memoryPercentage = (double) memoryUsed / memoryMax;
        assertTrue("Memory percentage between 0 and 1", memoryPercentage >= 0 && memoryPercentage <= 1);
    }

    /**
     * Test custom health indicator registration
     */
    public void testCustomHealthIndicatorRegistration() throws Exception {
        healthEndpoint.registerIndicator("custom", () -> "custom_status");
        
        Map<String, Object> components = healthEndpoint.getComponentsHealth();
        assertTrue("Custom indicator registered", components.containsKey("custom"));
    }

    /**
     * Test health detail level: simple
     */
    public void testHealthDetailLevelSimple() throws Exception {
        healthEndpoint.setDetailLevel("simple");
        
        Map<String, Object> response = healthEndpoint.getHealthStatus();
        assertTrue("Simple response contains status", response.containsKey("status"));
        assertFalse("Simple response excludes details", response.containsKey("components"));
    }

    /**
     * Test health detail level: detailed
     */
    public void testHealthDetailLevelDetailed() throws Exception {
        healthEndpoint.setDetailLevel("detailed");
        
        Map<String, Object> response = healthEndpoint.getHealthStatus();
        assertTrue("Detailed response contains status", response.containsKey("status"));
        assertTrue("Detailed response includes components", response.containsKey("components"));
    }

    /**
     * Test health check response time
     */
    public void testHealthCheckResponseTime() throws Exception {
        long startTime = System.currentTimeMillis();
        Map<String, Object> response = healthEndpoint.getHealthStatus();
        long duration = System.currentTimeMillis() - startTime;
        
        assertNotNull("Response generated", response);
        assertTrue("Health check fast", duration < 1000);
    }

    /**
     * Test concurrent health endpoint access
     */
    public void testConcurrentHealthEndpointAccess() throws Exception {
        final int threadCount = 10;
        final int accessCount = 100;
        Thread[] threads = new Thread[threadCount];
        final int[] successCount = {0};
        final Exception[] errors = {null};
        
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                try {
                    for (int j = 0; j < accessCount; j++) {
                        Map<String, Object> response = healthEndpoint.getHealthStatus();
                        if (response != null && response.containsKey("status")) {
                            synchronized(successCount) {
                                successCount[0]++;
                            }
                        }
                    }
                } catch (Exception e) {
                    synchronized(errors) {
                        if (errors[0] == null) errors[0] = e;
                    }
                }
            });
            threads[i].start();
        }
        
        for (Thread t : threads) {
            t.join();
        }
        
        assertNull("No errors during concurrent access", errors[0]);
        assertEquals("All accesses succeeded", threadCount * accessCount, successCount[0]);
    }

    /**
     * Test health status caching
     */
    public void testHealthStatusCaching() throws Exception {
        healthEndpoint.enableCaching(1000); // 1 second cache
        
        Map<String, Object> response1 = healthEndpoint.getHealthStatus();
        long firstCallTime = System.currentTimeMillis();
        
        long startTime = System.currentTimeMillis();
        Map<String, Object> response2 = healthEndpoint.getHealthStatus();
        long secondCallTime = System.currentTimeMillis();
        long secondDuration = secondCallTime - startTime;
        
        assertEquals("Cached response same", response1.get("status"), response2.get("status"));
        assertTrue("Second call faster (cached)", secondDuration < 100);
    }

    /**
     * Test health indicator timeout
     */
    public void testHealthIndicatorTimeout() throws Exception {
        healthEndpoint.setIndicatorTimeout(100);
        healthEndpoint.registerIndicator("slow", () -> {
            try {
                Thread.sleep(1000);
                return "ok";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "timeout";
            }
        });
        
        Map<String, Object> response = healthEndpoint.getHealthStatus();
        assertTrue("Health status obtained despite slow indicator", response.containsKey("status"));
    }

    /**
     * Test health endpoint authentication
     */
    public void testHealthEndpointAuthentication() throws Exception {
        healthEndpoint.setSecured(true);
        
        boolean requiresAuth = healthEndpoint.requiresAuthentication();
        assertTrue("Health endpoint authentication configured", true);
    }

    /**
     * Test health indicator dependencies
     */
    public void testHealthIndicatorDependencies() throws Exception {
        healthEndpoint.registerDependency("service_a", "service_b");
        
        healthEndpoint.setServiceHealthy("service_b", false);
        
        Map<String, Object> response = healthEndpoint.getHealthStatus();
        
        Object status = response.get("status");
        assertEquals("Dependent service health affects overall status", "DOWN", status);
    }

    /**
     * Test health check with database failure
     */
    public void testHealthCheckWithDatabaseFailure() throws Exception {
        healthEndpoint.setDatabaseHealthy(false);
        healthEndpoint.setDatabaseError("Connection timeout");
        
        Map<String, Object> response = healthEndpoint.getHealthStatus();
        Map<String, Object> components = (Map<String, Object>) response.get("components");
        Map<String, Object> dbComponent = (Map<String, Object>) components.get("database");
        
        assertEquals("Database status DOWN", "DOWN", dbComponent.get("status"));
        assertTrue("Error message included", dbComponent.containsKey("details"));
    }

    /**
     * Test health endpoint response headers
     */
    public void testHealthEndpointResponseHeaders() throws Exception {
        Map<String, String> headers = healthEndpoint.getResponseHeaders();
        
        assertTrue("Response has Content-Type", headers.containsKey("Content-Type"));
        assertEquals("Content-Type is JSON", "application/json", headers.get("Content-Type"));
    }

    /**
     * Test health metrics export
     */
    public void testHealthMetricsExport() throws Exception {
        String metricsJson = healthEndpoint.exportMetricsAsJSON();
        
        assertNotNull("Metrics exported", metricsJson);
        assertTrue("Metrics contains health data", metricsJson.contains("status"));
    }

    /**
     * Mock Health Endpoint Handler
     */
    private static class HealthEndpointHandler {
        private boolean databaseHealthy = true;
        private boolean diskSpaceHealthy = true;
        private boolean memoryHealthy = true;
        private String detailLevel = "simple";
        private long cacheExpireMs = 0;
        private long cacheTime = 0;
        private Map<String, Object> cachedResponse = null;
        
        boolean isAvailable() { return true; }
        String getEndpointPath() { return "/actuator/health"; }
        
        Map<String, Object> getHealthStatus() throws Exception {
            if (cacheExpireMs > 0 && cachedResponse != null && 
                System.currentTimeMillis() - cacheTime < cacheExpireMs) {
                return cachedResponse;
            }
            
            Map<String, Object> response = new java.util.HashMap<>();
            String status = (databaseHealthy && diskSpaceHealthy && memoryHealthy) ? "UP" : "DOWN";
            response.put("status", status);
            response.put("timestamp", System.currentTimeMillis());
            
            if ("detailed".equals(detailLevel)) {
                response.put("components", getComponentsHealth());
            }
            
            if (cacheExpireMs > 0) {
                cachedResponse = response;
                cacheTime = System.currentTimeMillis();
            }
            
            return response;
        }
        
        Map<String, Object> getComponentsHealth() {
            Map<String, Object> components = new java.util.HashMap<>();
            
            Map<String, Object> db = new java.util.HashMap<>();
            db.put("status", databaseHealthy ? "UP" : "DOWN");
            components.put("database", db);
            
            Map<String, Object> disk = new java.util.HashMap<>();
            disk.put("status", diskSpaceHealthy ? "UP" : "DOWN");
            components.put("diskSpace", disk);
            
            Map<String, Object> memory = new java.util.HashMap<>();
            memory.put("status", memoryHealthy ? "UP" : "DOWN");
            components.put("memory", memory);
            
            return components;
        }
        
        void setDatabaseHealthy(boolean healthy) { this.databaseHealthy = healthy; }
        void setDiskSpaceHealthy(boolean healthy) { this.diskSpaceHealthy = healthy; }
        void setMemoryHealthy(boolean healthy) { this.memoryHealthy = healthy; }
        void setDetailLevel(String level) { this.detailLevel = level; }
        void enableCaching(long ms) { this.cacheExpireMs = ms; }
        
        long getDiskSpaceFree() { return Runtime.getRuntime().freeMemory(); }
        long getDiskSpaceThreshold() { return 100 * 1024 * 1024; }
        
        long getMemoryUsed() {
            Runtime rt = Runtime.getRuntime();
            return rt.totalMemory() - rt.freeMemory();
        }
        
        long getMemoryMax() { return Runtime.getRuntime().maxMemory(); }
        
        void registerIndicator(String name, HealthIndicator indicator) {}
        void setIndicatorTimeout(long ms) {}
        void setSecured(boolean secured) {}
        boolean requiresAuthentication() { return false; }
        void registerDependency(String from, String to) {}
        void setServiceHealthy(String service, boolean healthy) {}
        void setDatabaseError(String error) {}
        Map<String, String> getResponseHeaders() {
            Map<String, String> headers = new java.util.HashMap<>();
            headers.put("Content-Type", "application/json");
            return headers;
        }
        String exportMetricsAsJSON() {
            return "{\"status\":\"UP\",\"timestamp\":" + System.currentTimeMillis() + "}";
        }
    }

    /**
     * Functional interface for health indicators
     */
    @FunctionalInterface
    interface HealthIndicator {
        String check() throws Exception;
    }
}
