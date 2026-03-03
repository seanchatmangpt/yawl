package org.yawlfoundation.yawl.bridge.processmining;

/**
 * Handle to an event log in the native Rust process mining library.
 * This is a wrapper around the native pointer (long).
 */
public class EventLogHandle {

    private final long handle;

    public EventLogHandle(long handle) {
        if (handle == 0) {
            throw new IllegalArgumentException("Invalid handle: 0");
        }
        this.handle = handle;
    }

    public long getHandle() {
        return handle;
    }
}