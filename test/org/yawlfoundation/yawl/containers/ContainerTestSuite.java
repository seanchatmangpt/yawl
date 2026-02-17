/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.containers;

import org.junit.platform.suite.api.IncludeTags;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * TestContainers integration test suite for YAWL v6.0.0.
 *
 * Aggregates all Docker-container-backed integration tests:
 * - PostgreSQL 16: full CRUD, FK constraints, transactions, bulk inserts
 * - MySQL 8.4: schema parity, batch inserts, transaction isolation
 *
 * Execution requirements:
 * - Docker daemon running and accessible
 * - Network access to docker.io and ghcr.io for image pulls (first run only)
 * - Tests tagged "containers"; skip in environments without Docker
 *
 * Run the full container suite:
 * <pre>
 *   mvn test -Dgroups=containers -Dtest=ContainerTestSuite
 * </pre>
 *
 * Run only the PostgreSQL subset:
 * <pre>
 *   mvn test -Dgroups=containers -Dtest=PostgresContainerIntegrationTest
 * </pre>
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-17
 */
@Suite
@SuiteDisplayName("YAWL v6 Container Integration Tests")
@IncludeTags("containers")
@SelectClasses({
    PostgresContainerIntegrationTest.class,
    MySQLContainerIntegrationTest.class
})
public class ContainerTestSuite {
    // JUnit 5 Platform Suite — no main method required.
}
