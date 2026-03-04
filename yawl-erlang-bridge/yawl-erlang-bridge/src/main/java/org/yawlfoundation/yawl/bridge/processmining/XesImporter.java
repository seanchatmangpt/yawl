package org.yawlfoundation.yawl.bridge.processmining;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JNI wrapper for XES event log import functionality.
 * Loads the native Rust process mining library and provides Java interface.
 */
public class XesImporter {

    static {
        System.loadLibrary("yawl_process_mining");
    }

    private native long importXes(String path);

    /**
     * Imports an XES event log file.
     *
     * @param path Path to the XES file to import
     * @return Event log handle for further processing
     * @throws ProcessMiningException if the file cannot be imported
     * @throws IllegalArgumentException if the file doesn't exist
     */
    public EventLogHandle importXesFile(Path path) {
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("File not found: " + path);
        }

        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Path is not a file: " + path);
        }

        if (!path.toString().toLowerCase().endsWith(".xes")) {
            throw new IllegalArgumentException("File must have .xes extension: " + path);
        }

        long handle = importXes(path.toString());
        if (handle == 0) {
            throw new ProcessMiningException("Failed to import XES file: " + path);
        }

        return new EventLogHandle(handle);
    }

    /**
     * Validates that a file is a valid XES file before importing.
     *
     * @param path Path to validate
     * @throws IllegalArgumentException if the file is not a valid XES file
     */
    public void validateXesFile(Path path) {
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("XES file does not exist: " + path);
        }

        if (!Files.isReadable(path)) {
            throw new IllegalArgumentException("XES file is not readable: " + path);
        }

        if (!path.toString().toLowerCase().endsWith(".xes")) {
            throw new IllegalArgumentException("File must have .xes extension: " + path);
        }

        long fileSize = path.toFile().length();
        if (fileSize == 0) {
            throw new IllegalArgumentException("XES file is empty: " + path);
        }

        if (fileSize > 100 * 1024 * 1024) { // 100MB limit
            throw new IllegalArgumentException("XES file too large (max 100MB): " + path);
        }
    }
}