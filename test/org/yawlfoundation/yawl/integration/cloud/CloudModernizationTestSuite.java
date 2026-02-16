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

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * Comprehensive test suite for all cloud modernization integrations.
 *
 * This suite includes:
 * - Spring AI MCP resource lifecycle tests
 * - OpenTelemetry trace and metric export tests
 * - SPIFFE identity and SVID management tests
 * - Resilience4j circuit breaker behavior tests
 * - Virtual thread scalability tests
 * - Actuator health endpoint tests
 * - Cloud platform smoke tests (GKE, EKS, AKS, Cloud Run)
 *
 * Test Coverage:
 * - 80%+ coverage of cloud integration components
 * - Real integration tests (not mocks)
 * - Multi-threaded concurrency testing
 * - Failure mode handling
 * - Performance baseline establishment
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class CloudModernizationTestSuite {

    public static Test suite() {
        TestSuite suite = new TestSuite("YAWL Cloud Modernization Integration Tests");

        // Spring AI and MCP Integration Tests
        suite.addTestSuite(SpringAIMcpResourceIntegrationTest.class);

        // OpenTelemetry Integration Tests
        suite.addTestSuite(OpenTelemetryIntegrationTest.class);

        // SPIFFE Identity Management Tests
        suite.addTestSuite(SPIFFEIdentityIntegrationTest.class);

        // Resilience4j Circuit Breaker Tests
        suite.addTestSuite(Resilience4jIntegrationTest.class);

        // Virtual Thread Scalability Tests
        suite.addTestSuite(VirtualThreadScalabilityTest.class);

        // Actuator Health Endpoint Tests
        suite.addTestSuite(ActuatorHealthEndpointTest.class);

        // Cloud Platform Smoke Tests
        suite.addTestSuite(CloudPlatformSmokeTest.class);

        return suite;
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }
}
