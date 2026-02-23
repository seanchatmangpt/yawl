package org.yawlfoundation.yawl.infrastructure;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for checking Docker availability in test environments.
 *
 * <p>Provides a cached check for whether Docker is available and operational,
 * combining system property configuration with actual Docker daemon health checks.
 * Use the {@link RequiresDocker} annotation to conditionally enable tests
 * based on Docker availability.
 *
 * <h2>Configuration</h2>
 * <p>Docker checks are disabled by default for reliability. Enable them by setting
 * the system property {@code test.docker.enabled=true}:
 *
 * <pre>{@code
 * mvn test -Dtest=MyDockerTest -Dtest.docker.enabled=true
 * }</pre>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Option 1: Use @RequiresDocker annotation (from RequiresDocker.java)
 * @RequiresDocker
 * class MyContainerTest {
 *     @Test
 *     void testWithDocker() {
 *         // Docker containers will be available
 *     }
 * }
 *
 * // Option 2: Manual check for conditional logic
 * if (DockerAvailabilityChecker.isDockerAvailable()) {
 *     // Docker-specific setup
 * }
 * }</pre>
 *
 * <h2>Caching Behavior</h2>
 * <p>The Docker availability check is cached after the first invocation.
 * This prevents repeated subprocess spawns during test suite execution.
 * The cache is a static field and persists for the JVM lifetime.
 *
 * @version 6.0.0
 * @since 6.0.0
 * @see RequiresDocker
 */
public final class DockerAvailabilityChecker {

    /**
     * System property name for enabling Docker checks.
     * Default value: {@code "false"}.
     */
    public static final String PROPERTY_DOCKER_ENABLED = "test.docker.enabled";

    /**
     * Timeout in seconds for the {@code docker ps} command.
     * A short timeout ensures fast test startup when Docker is unavailable.
     */
    private static final int DOCKER_CHECK_TIMEOUT_SECONDS = 5;

    /**
     * Cached result of Docker availability check.
     * Null indicates the check has not been performed yet.
     * Volatile ensures visibility across threads.
     */
    private static volatile Boolean dockerAvailable = null;

    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with only static methods.
     */
    private DockerAvailabilityChecker() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /**
     * Checks if Docker is available and operational.
     *
     * <p>The check is performed in three stages:
     * <ol>
     *   <li>Return cached result if available</li>
     *   <li>Check system property {@code test.docker.enabled} (defaults to false)</li>
     *   <li>Execute {@code docker ps} command with 5-second timeout</li>
     * </ol>
     *
     * <p>The method catches all exceptions (IOException, InterruptedException,
     * SecurityException, etc.) and returns {@code false} in error cases.
     * This ensures tests fail gracefully when Docker is not installed,
     * the daemon is not running, or security policies block subprocess execution.
     *
     * @return {@code true} if Docker is enabled, installed, and the daemon is running;
     *         {@code false} otherwise
     */
    public static boolean isDockerAvailable() {
        // Return cached result if already computed
        if (dockerAvailable != null) {
            return dockerAvailable;
        }

        // Synchronize to prevent race condition on first check
        synchronized (DockerAvailabilityChecker.class) {
            // Double-check after acquiring lock
            if (dockerAvailable != null) {
                return dockerAvailable;
            }

            dockerAvailable = checkDockerAvailability();
            return dockerAvailable;
        }
    }

    /**
     * Clears the cached Docker availability result.
     *
     * <p>This method is primarily useful for testing the checker itself,
     * allowing re-evaluation of Docker availability between test cases.
     * Normal production code should not need to call this method.
     */
    public static void clearCache() {
        synchronized (DockerAvailabilityChecker.class) {
            dockerAvailable = null;
        }
    }

    /**
     * Performs the actual Docker availability check.
     *
     * <p>Implementation notes:
     * <ul>
     *   <li>Uses {@link ProcessBuilder} with explicit timeout for reliability</li>
     *   <li>Redirects error stream to output to capture daemon error messages</li>
     *   <li>Calls {@code docker ps} as a lightweight daemon health check</li>
     * </ul>
     *
     * @return {@code true} if Docker is available; {@code false} otherwise
     */
    private static boolean checkDockerAvailability() {
        // Step 1: Check system property (defaults to false for safety)
        String enabledProperty = System.getProperty(PROPERTY_DOCKER_ENABLED, "false");
        if (!Boolean.parseBoolean(enabledProperty)) {
            return false;
        }

        // Step 2: Execute docker ps with timeout
        ProcessBuilder processBuilder = new ProcessBuilder("docker", "ps");
        processBuilder.redirectErrorStream(true);

        Process process = null;
        try {
            process = processBuilder.start();
            boolean exited = process.waitFor(DOCKER_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!exited) {
                // Process timed out - Docker daemon may be hung
                process.destroyForcibly();
                return false;
            }

            // Exit code 0 means Docker daemon is responsive
            return process.exitValue() == 0;

        } catch (IOException e) {
            // Docker executable not found or cannot be executed
            return false;
        } catch (InterruptedException e) {
            // Thread was interrupted while waiting - restore interrupt flag
            Thread.currentThread().interrupt();
            return false;
        } catch (SecurityException e) {
            // Security manager blocked subprocess execution
            return false;
        } finally {
            // Ensure process is cleaned up
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
}
