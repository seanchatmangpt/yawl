/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.infrastructure;

import org.junit.jupiter.api.Tag;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Meta-annotation for tests that require a Docker daemon to be available.
 *
 * <p>This annotation combines multiple JUnit 5 annotations to indicate that
 * a test class or method requires Docker containers (via Testcontainers).
 * Tests annotated with this will:</p>
 *
 * <ul>
 *   <li>Be tagged with "docker" for selective test execution</li>
 *   <li>Be tagged with "integration" for integration test grouping</li>
 *   <li>Require Testcontainers extension to be active</li>
 * </ul>
 *
 * <p>Usage example:</p>
 * <pre>
 * {@code
 * @RequiresDocker
 * class MyContainerTest {
 *     @Container
 *     static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
 *
 *     @Test
 *     void testWithRealDatabase() {
 *         // test using real PostgreSQL container
 *     }
 * }
 * }
 * </pre>
 *
 * <p>Skip Docker-dependent tests in CI environments without Docker:</p>
 * <pre>
 *   mvn test -DexcludedGroups=docker
 *   mvn test -DexcludedGroups=containers
 * </pre>
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-17
 * @see Testcontainers
 * @see Tag
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Tag("docker")
@Tag("integration")
public @interface RequiresDocker {
    /**
     * Optional reason for requiring Docker, used in test reports.
     *
     * @return description of why Docker is required
     */
    String value() default "";
}
