/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.chaos;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Comprehensive Chaos Engineering Test Suite for YAWL MCP-A2A MVP.
 *
 * This suite aggregates all chaos engineering tests across 6 categories:
 *
 * 1. Failure Scenarios      - Network latency, partitions, pod termination, DB failures
 * 2. Resource Chaos         - Memory pressure, CPU throttling, disk, file descriptors
 * 3. Service Resilience     - Circuit breaker, retry, fallback, graceful degradation
 * 4. Data Consistency       - Partial writes, concurrent modifications, transactions
 * 5. Edge Cases             - Extreme concurrency, oversized payloads, long-running ops
 * 6. Recovery Testing       - MTTR, data integrity, health restoration, reconnection
 *
 * Run with: mvn test -Dtest=ChaosEngineeringSuite -Dgroups=chaos
 *
 * Individual categories can be run separately:
 *   mvn test -Dtest=NetworkChaosTest -Dgroups=chaos
 *   mvn test -Dtest=ResourceChaosTest -Dgroups=chaos
 *   etc.
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-19
 */
@Suite
@SuiteDisplayName("YAWL Chaos Engineering Test Suite")
@SelectClasses({
    NetworkChaosTest.class,
    ResourceChaosTest.class,
    ServiceResilienceChaosTest.class,
    DataConsistencyChaosTest.class,
    EdgeCaseChaosTest.class,
    RecoveryChaosTest.class
})
public class ChaosEngineeringSuite {
    // JUnit Platform Suite - no code needed
}
