package org.yawlfoundation.yawl.bridge.processmining;

/**
 * Factory class for creating process mining JNI components.
 * Provides convenient access to all process mining functionality.
 */
public class ProcessMiningFactory {

    private static volatile XesImporter xesImporter;
    private static volatile AlphaMiner alphaMiner;
    private static volatile ConformanceChecker conformanceChecker;

    /**
     * Gets or creates an XesImporter instance.
     *
     * @return XesImporter instance
     */
    public static XesImporter getXesImporter() {
        if (xesImporter == null) {
            synchronized (ProcessMiningFactory.class) {
                if (xesImporter == null) {
                    xesImporter = new XesImporter();
                }
            }
        }
        return xesImporter;
    }

    /**
     * Gets or creates an AlphaMiner instance.
     *
     * @return AlphaMiner instance
     */
    public static AlphaMiner getAlphaMiner() {
        if (alphaMiner == null) {
            synchronized (ProcessMiningFactory.class) {
                if (alphaMiner == null) {
                    alphaMiner = new AlphaMiner();
                }
            }
        }
        return alphaMiner;
    }

    /**
     * Gets or creates a ConformanceChecker instance.
     *
     * @return ConformanceChecker instance
     */
    public static ConformanceChecker getConformanceChecker() {
        if (conformanceChecker == null) {
            synchronized (ProcessMiningFactory.class) {
                if (conformanceChecker == null) {
                    conformanceChecker = new ConformanceChecker();
                }
            }
        }
        return conformanceChecker;
    }

    /**
     * Validates that the native library is properly loaded.
     *
     * @throws ProcessMiningException if the library is not loaded
     */
    public static void validateNativeLibrary() {
        try {
            // Try to access any static native class to verify library is loaded
            Class.forName("org.yawlfoundation.yawl.bridge.processmining.XesImporter");
        } catch (ClassNotFoundException e) {
            throw new ProcessMiningException("Process mining JNI components not found", e);
        }
    }
}