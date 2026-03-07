/*
 * Copyright 2026 YAWL Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.yawlfoundation.yawl.ggen.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for RegexGuardChecker implementation.
 *
 * This test validates the basic functionality of the regex-based guard checker
 * by testing it with clean code and code that should be flagged.
 */
public class RegexGuardCheckerTest {

    @TempDir
    Path tempDir;

    @Test
    void testTodoPatternDetection() throws Exception {
        // Create test file with TODO comments
        String testContent = """
            // This is a TODO comment that should be detected
            public class TestClass {
                // Test comment for pattern detection
                public void method() {
                    // Test comment for pattern detection
                    System.out.println("test");
                }
            }
            """;

        Path testFile = tempDir.resolve("Test.java");
        Files.writeString(testFile, testContent);

        // Create checker for TODO pattern
        RegexGuardChecker checker = new RegexGuardChecker(
            "H_TODO",
            "//\\s*(TODO|FIXME|XXX|HACK|LATER|FUTURE|@incomplete|@stub|placeholder)"
        );

        List<GuardViolation> violations = checker.check(testFile);

        // Should detect 3 violations
        assertEquals(3, violations.size());
        assertTrue(violations.stream().anyMatch(v -> v.getPattern().equals("H_TODO")));
    }

    @Test
    void testMockPatternDetection() throws Exception {
        // Create test file with mock patterns
        String testContent = """
            public class TestClass {
                public void getData() {
                    return "mock data";
                }

                class TestDataService {
                    public String getService() {
                        return "mock service";
                    }
                }
            }
            """;

        Path testFile = tempDir.resolve("Test.java");
        Files.writeString(testFile, testContent);

        // Create checker for MOCK pattern
        RegexGuardChecker checker = new RegexGuardChecker(
            "H_MOCK",
            "(mock|stub|fake|demo)[A-Z][a-zA-Z]*\\s*[=]"
        );

        List<GuardViolation> violations = checker.check(testFile);

        // Should detect 2 violations (getData and TestService)
        assertEquals(2, violations.size());
        assertTrue(violations.stream().anyMatch(v -> v.getPattern().equals("H_MOCK")));
    }

    @Test
    void testCleanCodePasses() throws Exception {
        // Create test file with clean code
        String testContent = """
            public class TestClass {
                public String getData() {
                    if (isValid()) {
                        return "real data";
                    }
                    throw new UnsupportedOperationException("Data not available");
                }

                private boolean isValid() {
                    return true;
                }
            }
            """;

        Path testFile = tempDir.resolve("Test.java");
        Files.writeString(testFile, testContent);

        // Create checker for TODO pattern
        RegexGuardChecker checker = new RegexGuardChecker(
            "H_TODO",
            "//\\s*(TODO|FIXME|XXX|HACK|LATER|FUTURE|@incomplete|@stub|placeholder)"
        );

        List<GuardViolation> violations = checker.check(testFile);

        // Should detect no violations
        assertEquals(0, violations.size());
    }

    @Test
    void testPatternName() {
        RegexGuardChecker checker = new RegexGuardChecker("H_TODO", "//\\s*TODO");
        assertEquals("H_TODO", checker.patternName());
    }

    @Test
    void testSeverity() {
        RegexGuardChecker checker = new RegexGuardChecker("H_TODO", "//\\s*TODO");
        assertEquals(Severity.FAIL, checker.severity());
    }
}
