package org.yawlfoundation.yawl;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke test to verify the test infrastructure is properly configured.
 * This test does NOT test actual YAWL code - it only verifies:
 * 1. JUnit 5 is working
 * 2. The test directory structure is correct
 * 3. Java 21+ is available (project minimum requirement)
 */
@DisplayName("Test Infrastructure Smoke Test")
@Tag("integration")
class TestInfrastructureSmokeTest {

    @Test
    @DisplayName("JUnit 5 is properly configured")
    void junit5IsConfigured() {
        assertTrue(true, "JUnit 5 should be working");
    }

    @Test
    @DisplayName("Java version is 21+")
    void javaVersionIsCorrect() {
        int version = Runtime.version().feature();
        assertTrue(version >= 21, "Java version should be 21+, got: " + version);
    }

    @Test
    @DisplayName("Test class is in correct package")
    void testClassIsInCorrectPackage() {
        Package pkg = this.getClass().getPackage();
        assertNotNull(pkg, "Package should not be null");
        assertEquals("org.yawlfoundation.yawl", pkg.getName(),
            "Test class should be in org.yawlfoundation.yawl package");
    }

    @Test
    @DisplayName("Basic assertions work")
    void basicAssertionsWork() {
        assertEquals(2, 1 + 1, "Basic math should work");
        assertNotEquals("a", "b", "Strings should not be equal");
        assertNull(null, "null should be null");
        assertNotNull(new Object(), "Object should not be null");
    }
}
