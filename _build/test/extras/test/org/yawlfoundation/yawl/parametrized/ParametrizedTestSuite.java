/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.parametrized;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Parametrized test suite for YAWL v6.0.0.
 *
 * Aggregates all parametrized test classes:
 * - {@link DatabaseBackendParametrizedTest}: same YAWL schema/DML against H2
 *   (Postgres-compat mode), H2 (MySQL-compat mode), H2 (default), and HSQLDB
 * - {@link PerformanceRegressionParametrizedTest}: throughput regression matrix
 *   covering small/medium/large operation counts and concurrent thread counts
 * - {@link JavaFeatureMatrixTest}: Java 21+ feature validation matrix covering
 *   records, sealed types, text blocks, virtual threads, sequenced collections
 *
 * Run this suite:
 * <pre>
 *   mvn test -Dtest=ParametrizedTestSuite
 * </pre>
 *
 * Run individual test class:
 * <pre>
 *   mvn test -Dtest=DatabaseBackendParametrizedTest
 *   mvn test -Dtest=PerformanceRegressionParametrizedTest
 *   mvn test -Dtest=JavaFeatureMatrixTest
 * </pre>
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-17
 */
@Suite
@SuiteDisplayName("YAWL v6 Parametrized Test Suite")
@SelectClasses({
    DatabaseBackendParametrizedTest.class,
    PerformanceRegressionParametrizedTest.class,
    JavaFeatureMatrixTest.class
})
public class ParametrizedTestSuite {
    // JUnit 5 Platform Suite — no main method required.
}
