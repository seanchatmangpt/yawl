package org.yawlfoundation.yawl.integration.util;

/**
 * Utility class for detecting and handling GraalVM availability issues.
 */
public final class GraalVMUtils {

    private GraalVMUtils() {}

    public static boolean isAvailable() {
        try {
            Class.forName("org.graalvm.polyglot.Context");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static boolean isUnavailableException(Throwable exception) {
        if (exception == null) {
            return false;
        }

        String message = exception.getMessage();
        String className = exception.getClass().getName();

        return className.contains("org.graalvm") ||
               className.contains("polyglot") ||
               (message != null &&
                (message.contains("GraalVM") ||
                 message.contains("native image") ||
                 message.contains("RUNTIME_NOT_AVAILABLE") ||
                 message.contains("CONTEXT_CREATION_FAILED") ||
                 message.contains("POLYGLOT_NOT_FOUND")));
    }

    public static String getFallbackGuidance() {
        return "Use fallback PatternBasedSynthesizer and OllamaCandidateSampler when GraalVM is unavailable. " +
               "Ensure JDK 24.1+ and polyglot libraries are properly installed for native image support.";
    }
}