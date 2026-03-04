/**
 * NativeHandle<T> - Generic Typed Handle for Native Resources
 *
 * This class provides a typed wrapper around native resources with automatic
 * memory management using Panama Arena.
 *
 * @param <T> The type of resource this handle manages
 */
public final class NativeHandle<T> {

    private final T resource;
    private final jdk.incubator.foreign.Arena arena;
    private final boolean shouldClose;

    private boolean closed = false;

    /**
     * Creates a new NativeHandle that will manage the resource and arena
     */
    public static <T> NativeHandle<T> create(T resource, jdk.incubator.foreign.Arena arena) {
        return new NativeHandle<>(resource, arena, true);
    }

    /**
     * Creates a new NativeHandle for a resource that doesn't need arena management
     */
    public static <T> NativeHandle<T> create(T resource) {
        return new NativeHandle<>(resource, null, false);
    }

    /**
     * Creates a new NativeHandle with explicit arena management
     */
    public static <T> NativeHandle<T> create(T resource, jdk.incubator.foreign.Arena arena, boolean shouldClose) {
        return new NativeHandle<>(resource, arena, shouldClose);
    }

    private NativeHandle(T resource, jdk.incubator.foreign.Arena arena, boolean shouldClose) {
        if (resource == null) {
            throw new IllegalArgumentException("Resource cannot be null");
        }
        this.resource = resource;
        this.arena = arena;
        this.shouldClose = shouldClose;
    }

    /**
     * Gets the wrapped resource
     */
    public T get() {
        if (closed) {
            throw new IllegalStateException("Native handle has been closed");
        }
        return resource;
    }

    /**
     * Gets the arena associated with this handle
     */
    public jdk.incubator.foreign.Arena getArena() {
        if (closed) {
            throw new IllegalStateException("Native handle has been closed");
        }
        return arena;
    }

    /**
     * Checks if the handle is closed
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Closes the native resources if they need to be closed
     */
    public void close() {
        if (closed) {
            return;
        }

        try {
            // For resources that need explicit closing (like native handles)
            if (shouldClose && resource instanceof AutoCloseable) {
                ((AutoCloseable) resource).close();
            }
        } catch (Exception e) {
            // Log the error but don't prevent closing
            System.err.println("Error while closing native resource: " + e.getMessage());
        }

        closed = true;
    }

    /**
     * Ensures resources are closed when the handle is no longer needed
     */
    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    /**
     * Executes a function with this handle, automatically closing it afterwards
     * if it's not global or shared
     */
    public <R> R withFunction(Function<T, R> function, boolean autoClose) {
        if (closed) {
            throw new IllegalStateException("Native handle has been closed");
        }

        try {
            return function.apply(resource);
        } finally {
            if (autoClose && shouldClose && arena != null) {
                close();
            }
        }
    }

    /**
     * Executes a consumer with this handle
     */
    public void withConsumer(Consumer<T> consumer, boolean autoClose) {
        if (closed) {
            throw new IllegalStateException("Native handle has been closed");
        }

        try {
            consumer.accept(resource);
        } finally {
            if (autoClose && shouldClose && arena != null) {
                close();
            }
        }
    }

    /**
     * Creates a global arena handle that never closes automatically
     */
    public static <T> NativeHandle<T> createGlobal(T resource) {
        return new NativeHandle<>(resource, jdk.incubator.foreign.Arena.global(), false);
    }

    /**
     * Creates a confined arena handle that closes with the resource
     */
    public static <T> NativeHandle<T> createConfined(T resource) {
        jdk.incubator.foreign.Arena arena = jdk.incubator.foreign.Arena.ofConfined();
        return new NativeHandle<>(resource, arena, true);
    }

    /**
     * Creates a shared arena handle that can be shared across threads
     */
    public static <T> NativeHandle<T> createShared(T resource) {
        jdk.incubator.foreign.Arena arena = jdk.incubator.foreign.Arena.ofShared();
        return new NativeHandle<>(resource, arena, true);
    }
}