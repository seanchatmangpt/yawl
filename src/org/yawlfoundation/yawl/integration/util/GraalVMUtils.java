package org.yawlfoundation.yawl.integration.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Utility class for checking GraalVM/GraalPy availability and handling related exceptions.
 *
 * This class provides a centralized way to:
 * <ul>
 *   <li>Check if GraalVM/GraalPy is available at runtime</li>
 *   <li>Detect if an exception is due to GraalVM unavailability</li>
 *   <li>Provide fallback path guidance</li>
 *   <li>Cache availability check result for performance</li>
 * </ul>
 *
 * <h2>Usage Examples:</h2>
 * <pre>{@code
 * // Check availability
 * if (GraalVMUtils.isAvailable()) {
 *     // Use GraalPy features
 * } else {
 *     // Use fallback implementation
 *     String guidance = GraalVMUtils.getFallbackGuidance();
 * }
 *
 * // Check exception type
 * try {
 *     // GraalPy operation
 * } catch (Exception e) {
 *     if (GraalVMUtils.isUnavailableException(e)) {
 *         // Handle GraalVM unavailability
 *     }
 * }
 * }</pre>
 *
 * @since YAWL v6.0.0
 */
public final class GraalVMUtils {

    // Cached availability flag
    private static final AtomicBoolean availabilityCached = new AtomicBoolean(false);
    private static final AtomicReference<Boolean> isAvailable = new AtomicReference<>(null);

    // Known error kinds that indicate GraalVM unavailability
    private static final Set<String> UNAVAILABLE_ERROR_KINDS = Collections.unmodifiableSet(new HashSet<>(Set.of(
        "RUNTIME_NOT_AVAILABLE",
        "CONTEXT_CREATION_FAILED",
        "POLYGLOT_NOT_FOUND",
        "GRAALPY_NOT_FOUND",
        "NO_GRAALVM_HOME",
        "UNSATISFIED_LINK_ERROR",
        "JNI_ERR",
        "CLASS_NOT_FOUND"
    )));

    // Common error patterns in exception messages
    private static final Set<String> ERROR_PATTERNS = Collections.unmodifiableSet(new HashSet<>(Set.of(
        "org.graalvm.polyglot",
        "polyglot",
        "graalvm",
        "NoClassDefFoundError",
        "NoSuchMethodError",
        "UnsatisfiedLinkError"
    )));

    // Private constructor to enforce utility class pattern
    private GraalVMUtils() {}

    /**
     * Checks if GraalVM/GraalPy is available at runtime.
     *
     * This method:
     * <ul>
     *   <li>First checks the cache if available</li>
     *   <li>If not cached, attempts to load the GraalPy execution engine class</li>
     *   <li>Caches the result for subsequent calls</li>
     *   <li>Returns true if GraalVM is available, false otherwise</li>
     * </ul>
     *
     * @return true if GraalVM/GraalPy is available, false otherwise
     */
    public static boolean isAvailable() {
        if (availabilityCached.get()) {
            return Boolean.TRUE.equals(isAvailable.get());
        }
        return checkAvailability();
    }

    /**
     * Performs the actual availability check.
     *
     * This method attempts to load the PythonExecutionEngine class which should
     * be available when GraalVM is properly configured.
     *
     * @return true if GraalVM is available, false otherwise
     */
    private static synchronized boolean checkAvailability() {
        if (availabilityCached.get()) {
            return Boolean.TRUE.equals(isAvailable.get());
        }

        boolean available = false;
        try {
            // Try to create a minimal GraalPy context by loading the engine class
            Class<?> engineClass = Class.forName(
                "org.yawlfoundation.yawl.graalpy.PythonExecutionEngine"
            );
            available = engineClass != null;
        } catch (ClassNotFoundException e) {
            // GraalPy execution engine not found
            available = false;
        } catch (NoClassDefFoundError e) {
            // GraalVM polyglot classes not found
            available = false;
        } catch (ExceptionInInitializerError e) {
            // Error during class initialization (e.g., missing native libraries)
            available = false;
        } catch (SecurityException e) {
            // Security manager prevents class loading
            available = false;
        } catch (LinkageError e) {
            // Native library issues
            available = false;
        }

        isAvailable.set(available);
        availabilityCached.set(true);
        return available;
    }

    /**
     * Checks if an exception indicates GraalVM unavailability.
     *
     * This method analyzes the exception to determine if it's related to
     * GraalVM/GraalPy not being available. It checks:
     * <ul>
     *   <li>Known error kinds in exception messages</li>
     *   <li>Common error patterns in exception class names and messages</li>
     *   <li>Cause chain for GraalVM-related exceptions</li>
     * </ul>
     *
     * @param e the exception to check (can be null)
     * @return true if the exception indicates GraalVM unavailability, false otherwise
     */
    public static boolean isUnavailableException(Throwable e) {
        if (e == null) return false;

        // Check the main exception
        if (matchesErrorPattern(e)) return true;

        // Check the cause chain
        Throwable cause = e.getCause();
        while (cause != null && cause != e) {
            if (matchesErrorPattern(cause)) return true;
            cause = cause.getCause();
        }

        return false;
    }

    /**
     * Helper method to check if an exception matches GraalVM error patterns.
     */
    private static boolean matchesErrorPattern(Throwable e) {
        String message = e.getMessage();
        String className = e.getClass().getName();

        // Check for known error kinds in message
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            if (UNAVAILABLE_ERROR_KINDS.stream().anyMatch(
                kind -> lowerMessage.contains(kind.toLowerCase())
            )) {
                return true;
            }

            // Check for common patterns in message
            if (ERROR_PATTERNS.stream().anyMatch(
                pattern -> lowerMessage.contains(pattern.toLowerCase())
            )) {
                return true;
            }
        }

        // Check for GraalVM-related class names
        if (className.contains("org.graalvm") ||
            className.contains("com.oracle.graal") ||
            className.contains("polyglot")) {
            return true;
        }

        // Check for specific exception types that indicate class loading issues
        if (e instanceof NoClassDefFoundError ||
            e instanceof ClassNotFoundException ||
            e instanceof UnsatisfiedLinkError ||
            e instanceof ExceptionInInitializerError) {
            return true;
        }

        return false;
    }

    /**
     * Provides guidance for fallback paths when GraalVM is unavailable.
     *
     * This method returns a helpful message suggesting alternative approaches
     * when GraalVM cannot be used.
     *
     * @return guidance message for fallback paths
     */
    public static String getFallbackGuidance() {
        return "GraalVM not available. Use PatternBasedSynthesizer or " +
               "OllamaCandidateSampler as fallback. " +
               "To enable GraalVM, run with GraalVM JDK 24.1+ and ensure " +
               "YAWL_GraalPy integration is properly configured.";
    }

    /**
     * Gets detailed troubleshooting information for GraalVM issues.
     *
     * @return troubleshooting information
     */
    public static String getTroubleshootingInfo() {
        return "Troubleshooting GraalVM availability:\n" +
               "1. Verify you're using GraalVM JDK 24.1+\n" +
               "2. Ensure GRAALVM_HOME is set correctly\n" +
               "3. Check that polyglot libraries are in classpath\n" +
               "4. Verify native libraries are accessible\n" +
               "5. Check security policy if SandboxedSecurityManager is enabled\n" +
               "6. For Docker containers, ensure base image includes GraalVM";
    }

    /**
     * Resets the availability cache.
     *
     * This method is primarily useful for testing scenarios where you want
     * to simulate different availability states.
     *
     * <h3>Example Usage (Testing):</h3>
     * <pre>{@code
     * // Test with available state
     * GraalVMUtils.resetCache();
     * assertTrue(GraalVMUtils.isAvailable());
     *
     * // Simulate unavailable state by mocking class loading
     * GraalVMUtils.resetCache();
     * }</pre>
     */
    public static void resetCache() {
        availabilityCached.set(false);
        isAvailable.set(null);
    }

    /**
     * Forces a fresh availability check, bypassing the cache.
     *
     * @return current availability status
     */
    public static boolean checkAvailabilityFresh() {
        resetCache();
        return checkAvailability();
    }
}