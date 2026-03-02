package org.yawlfoundation.yawl.qlever;

/**
 * Enum representing the status of the QLever engine or query operation.
 */
public enum QLeverStatus {
    /**
     * Engine is ready and operational.
     */
    READY,

    /**
     * Engine is currently loading data.
     */
    LOADING,

    /**
     * An error has occurred.
     */
    ERROR,

    /**
     * Engine is closed and cannot be used.
     */
    CLOSED;

    /**
     * Checks if the status indicates the engine is operational.
     */
    public boolean isOperational() {
        return this == READY;
    }

    /**
     * Checks if the status indicates an error condition.
     */
    public boolean isError() {
        return this == ERROR;
    }
}