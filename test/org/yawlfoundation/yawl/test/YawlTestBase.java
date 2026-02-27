package org.yawlfoundation.yawl.test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Base class for YAWL unit and integration tests.
 *
 * Provides:
 * - Project root resolution (replaces hard-coded paths)
 * - Common test fixtures
 * - Test resource loading utilities
 *
 * @since 6.0.0
 */
@TestInstance(Lifecycle.PER_CLASS)
public abstract class YawlTestBase {

    /** Resolved project root - never hard-code paths */
    protected Path projectRoot;

    /** Test resources directory */
    protected Path testResourcesPath;

    @BeforeEach
    void resolveProjectRoot() {
        String userDir = System.getProperty("user.dir");
        projectRoot = Paths.get(userDir).toAbsolutePath();

        // Handle multi-module project structure
        if (!projectRoot.getFileName().toString().equals("yawl-parent")
                && !projectRoot.resolve("pom.xml").toFile().exists()) {
            projectRoot = projectRoot.getParent();
        }

        testResourcesPath = projectRoot.resolve("test").resolve("resources");
    }

    /**
     * Load a test resource as a Path.
     * @param relativePath path relative to test/resources/
     * @return absolute path to resource
     */
    protected Path getTestResource(String relativePath) {
        return testResourcesPath.resolve(relativePath);
    }

    /**
     * Load a test resource as String content.
     * @param relativePath path relative to test/resources/
     * @return file contents as string
     */
    protected String loadTestResource(String relativePath) throws Exception {
        return java.nio.file.Files.readString(getTestResource(relativePath));
    }

    /**
     * Get a property from test-application.properties.
     */
    protected String getTestProperty(String key) {
        Properties props = new Properties();
        try (var is = getClass().getClassLoader()
                .getResourceAsStream("test-application.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (Exception e) {
            // Return null if properties file not found
        }
        return props.getProperty(key);
    }
}
