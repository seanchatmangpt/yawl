/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.chaos;

import org.junit.platform.suite.api.IncludeTags;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Chaos engineering test suite for YAWL v6.0.0.
 *
 * Aggregates all chaos / resilience fault-injection tests:
 *
 * - {@link NetworkDelayResilienceTest}: validates YAWL persistence layer
 *   under artificial network delays (0–600ms), timeout enforcement, partial
 *   availability patterns, and concurrent slow operation throughput.
 *
 * - {@link ServiceFailureResilienceTest}: validates YAWL behaviour when
 *   database connections are forcibly closed, transactions roll back on
 *   partial batch failure, duplicate keys arrive mid-batch, and retries
 *   succeed after transient failures.
 *
 * Chaos tests are tagged "chaos" and excluded from standard CI runs.
 * Activate with:
 * <pre>
 *   mvn test -Dgroups=chaos -Dtest=ChaosTestSuite
 * </pre>
 *
 * Individual test classes:
 * <pre>
 *   mvn test -Dgroups=chaos -Dtest=NetworkDelayResilienceTest
 *   mvn test -Dgroups=chaos -Dtest=ServiceFailureResilienceTest
 * </pre>
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-17
 */
@Suite
@SuiteDisplayName("YAWL v6 Chaos Engineering Tests")
@IncludeTags("chaos")
@SelectClasses({
    NetworkDelayResilienceTest.class,
    ServiceFailureResilienceTest.class
})
public class ChaosTestSuite {
    // JUnit 5 Platform Suite — no main method required.
}
