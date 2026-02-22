/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.reporting;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Test reporting and analytics test suite for YAWL v6.0.0.
 *
 * Aggregates all tests for the JUnit XML analytics pipeline:
 *
 * - {@link TestAnalyticsPipelineTest}: validates the {@link JUnitXmlAnalyzer}
 *   class — XML parsing, directory scanning, aggregate report construction,
 *   slowest-test ranking, pass-rate calculation, and report formatting.
 *
 * - {@link FlakyTestDetectorTest}: validates the {@link FlakyTestDetector}
 *   class — flaky test classification across 2 and 3 CI runs, flakiness
 *   score calculation, always-failing vs flaky distinction, Markdown report
 *   generation.
 *
 * Run this suite:
 * <pre>
 *   mvn test -Dtest=ReportingTestSuite
 * </pre>
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-17
 */
@Suite
@SuiteDisplayName("YAWL v6 Test Reporting and Analytics")
@SelectClasses({
    TestAnalyticsPipelineTest.class,
    FlakyTestDetectorTest.class
})
public class ReportingTestSuite {
    // JUnit 5 Platform Suite — no main method required.
}
