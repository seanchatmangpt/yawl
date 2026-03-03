/*
 * Native Handle with Arena Lifecycle Management
 *
 * Generic typed native handle with automatic resource management using Panama Arena.
 * Implements HYPER_STANDARDS: no mocks, real impl or throw UnsupportedOperationException.
 */

package org.yawlfoundation.yawl.bridge.qlever;

import jdk.incubator.foreign.Arena;
import jdk.incubator.foreign.MemoryAddress;

/**
 * Generic typed handle for native resources with automatic lifecycle management.
 * Provides type safety and proper resource cleanup using Panama Arena.
 *
 * @param <T> Type of the native resource this handle represents
 */
public final class NativeHandle<T> implements AutoCloseable {

    private final MemoryAddress address;
    private final Arena arena;
    private final T type;
    private boolean closed = false;

    /**
     * Create a new native handle with arena ownership
     *
     * @param address Native memory address
     * @param arena Arena that owns this resource
     * @param type Type token for type safety
     */
    private NativeHandle(MemoryAddress address, Arena arena, T type) {
        this.address = address;
        this.arena = arena;
        this.type = type;
    }

    /**
     * Create a native handle that takes ownership of the arena
     *
     * @param address Native memory address
     * @param arena Arena to own the resource
     * @param type Type token for type safety
     * @return New native handle
     */
    public static <T> NativeHandle<T> of(MemoryAddress address, Arena arena, T type) {
        Objects.requireNonNull(address, "Memory address cannot be null");
        Objects.requireNonNull(arena, "Arena cannot be null");
        Objects.requireNonNull(type, "Type token cannot be null");

        return new NativeHandle<>(address, arena, type);
    }

    /**
     * Create a native handle that borrows from a shared arena
     * Resource cleanup is caller's responsibility
     *
     * @param address Native memory address
     * @param type Type token for type safety
     * @return Borrowed native handle
     */
    public static <T> NativeHandle<T> borrow(MemoryAddress address, T type) {
        Objects.requireNonNull(address, "Memory address cannot be null");
        Objects.requireNonNull(type, "Type token cannot be null");

        return new NativeHandle<>(address, null, type);
    }

    /**
     * Get the native memory address
     */
    public MemoryAddress address() {
        checkNotClosed();
        return address;
    }

    /**
     * Get the owning arena (may be null for borrowed handles)
     */
    public Arena arena() {
        return arena;
    }

    /**
     * Get the type token for type safety
     */
    public T type() {
        return type;
    }

    /**
     * Check if this handle is closed
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Check if this handle owns its resources
     */
    public boolean isOwner() {
        return arena != null;
    }

    /**
     * Check if this handle borrows resources
     */
    public boolean isBorrowed() {
        return arena == null;
    }

    /**
     * Close the handle and release native resources
     * Only has effect for owner handles
     */
    @Override
    public void close() {
        if (!closed && isOwner()) {
            try {
                arena.close();
            } finally {
                closed = true;
            }
        }
    }

    /**
     * Force close without throwing if already closed
     */
    public void closeSilently() {
        try {
            close();
        } catch (Exception e) {
            // Ignore close exceptions
        }
    }

    /**
     * Convert to a pointer value for legacy APIs
     */
    public long toPointer() {
        checkNotClosed();
        return address.toRawLongValue();
    }

    /**
     * Check if this handle points to null
     */
    public isNull() {
        checkNotClosed();
        return address.equals(MemoryAddress.NULL);
    }

    /**
     * Validate that the handle is not closed
     */
    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("Native handle is closed");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NativeHandle<?> that = (NativeHandle<?>) o;
        return Objects.equals(address, that.address) &&
               Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, type);
    }

    @Override
    public String toString() {
        if (closed) {
            return "NativeHandle{closed}";
        }
        return "NativeHandle{" +
               "address=" + address +
               ", type=" + type +
               ", owner=" + isOwner() +
               '}';
    }
}