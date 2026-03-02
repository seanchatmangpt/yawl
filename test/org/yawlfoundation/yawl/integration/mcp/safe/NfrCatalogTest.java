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

package org.yawlfoundation.yawl.integration.mcp.safe;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for NfrCatalog.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class NfrCatalogTest {
    private NfrCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new NfrCatalog();
    }

    @Test
    void validate_emptyFile_noViolations() throws IOException {
        // Arrange
        Path temp = Files.createTempFile("test", ".java");
        Files.writeString(temp, "public class GoodCode {\n    public void doWork() {\n        System.out.println(\"hello\");\n    }\n}");

        try {
            // Act
            List<NfrViolation> violations = catalog.validate(temp);

            // Assert
            assertTrue(violations.isEmpty());
        } finally {
            Files.delete(temp);
        }
    }

    @Test
    void validate_hardcodedPassword_reportsSecurityViolation() throws IOException {
        // Arrange
        Path temp = Files.createTempFile("test", ".java");
        Files.writeString(temp, "public class Bad {\n    String password = \"secret123\";\n}");

        try {
            // Act
            List<NfrViolation> violations = catalog.validate(temp);

            // Assert
            assertTrue(violations.stream().anyMatch(v -> "SECURITY".equals(v.attribute())));
            assertTrue(violations.stream().anyMatch(v -> v.description().contains("hardcoded")));
        } finally {
            Files.delete(temp);
        }
    }

    @Test
    void validate_emptyExceptionCatch_reportsReliabilityViolation() throws IOException {
        // Arrange
        Path temp = Files.createTempFile("test", ".java");
        Files.writeString(temp, "public class Bad {\n    void test() {\n        try {\n        } catch (Exception e) { }\n    }\n}");

        try {
            // Act
            List<NfrViolation> violations = catalog.validate(temp);

            // Assert
            assertTrue(violations.stream().anyMatch(v -> "RELIABILITY".equals(v.attribute())));
        } finally {
            Files.delete(temp);
        }
    }

    @Test
    void getPolicy_returnsValidJson() {
        // Act
        String policy = catalog.getPolicy();

        // Assert
        assertNotNull(policy);
        assertTrue(policy.contains("\"version\""));
        assertTrue(policy.contains("\"attributes\""));
        assertTrue(policy.contains("PRIVACY"));
        assertTrue(policy.contains("FAIRNESS"));
        assertTrue(policy.contains("SECURITY"));
        assertTrue(policy.contains("RELIABILITY"));
        assertTrue(policy.contains("TRANSPARENCY"));
        assertTrue(policy.contains("ACCOUNTABILITY"));
    }

    @Test
    void version_is1_0_0() {
        // Act
        String version = catalog.version();

        // Assert
        assertEquals("1.0.0", version);
    }
}
