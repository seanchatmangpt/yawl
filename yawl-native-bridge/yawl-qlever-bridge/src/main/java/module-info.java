/*
 * YAWL QLever Bridge Module
 *
 * Defines the module structure for QLever Panama FFI integration.
 * Exports the public API and requires necessary dependencies.
 */

module org.yawlfoundation.yawl.bridge.qlever {
    // Export the public API packages
    exports org.yawlfoundation.yawl.bridge.qlever;

    // Require YAWL engine
    requires org.yawlfoundation.yawl.engine;

    // Require Panama FFI for native interop
    requires jdk.incubator.foreign;

    // Optional: Log4j for logging (if used)
    requires org.apache.logging.log4j.api;

    // Use Java's try-with-resources for automatic cleanup
    uses java.lang.AutoCloseable;

    // Export jextract generated bindings to internal packages
    exports org.yawlfoundation.yawl.bridge.qlever.jextract to
        org.yawlfoundation.yawl.engine,
        org.yawlfoundation.yawl.bridge.qlever;
}