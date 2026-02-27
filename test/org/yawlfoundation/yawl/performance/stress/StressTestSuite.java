/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.performance.stress;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Comprehensive Stress Test Suite for YAWL v6.0.0-GA.
 *
 * Aggregates all stress testing components:
 *
 * - {@link ProductionWorkloadStressTest}: Production-like workload simulation
 *   with realistic workflow mix (60% simple, 30% complex, 10% priority)
 *
 * - {@link MemoryLeakDetectionTest}: Memory leak detection during sustained
 *   load with trend analysis and threshold monitoring
 *
 * - {@link MultiTenantStressTest}: Multi-tenant isolation testing with 100
 *   concurrent tenants and resource sharing validation
 *
 * - {@link LongRunningStressTest}: Extended operation testing with 24-hour
 *   continuous operation and system recovery validation
 *
 * Usage:
 *   mvn test -Dtest=StressTestSuite
 *   mvn test -Dgroups=stress -Dtest=ProductionWorkloadStressTest
 *
 * @author YAWL Performance Team
 * @version 6.0.0-GA
 * @since 2026-02-26
 */
@Suite
@SuiteDisplayName("YAWL v6.0.0-GA Comprehensive Stress Tests")
@SelectClasses({
    ProductionWorkloadStressTest.class,
    MemoryLeakDetectionTest.class,
    MultiTenantStressTest.class,
    LongRunningStressTest.class
})
public class StressTestSuite {
    // JUnit 5 Platform Suite — all tests run with optimal configuration
    // for production-like stress testing scenarios
}