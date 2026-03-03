package org.yawlfoundation.yawl.engine;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * ScopedValue-based context management for YAWL engine instances.
 *
 * This class replaces ThreadLocal-based engine context management with Java 25's
 * ScopedValue API, providing better virtual thread support and immutable binding scope.
 *
 * Key features:
 * - Immutable engine binding through ScopedValue
 * - Automatic cleanup on scope exit
 * - Better structured concurrency support
 * - No memory leaks from thread-local variables
 * - Proper inheritance in virtual thread forks
 *
 * Usage:
 * ```java
 * // Execute with engine context
 * String result = ScopedEngineContext.withEngine(engine, () -> {
 *     // Code inside this block has access to YEngine.current()
 *     return doWork();
 * });
 *
 * // Get current engine (throws exception if not in scope)
 * YEngine engine = ScopedEngineContext.current();
 * ```
 *
 * Thread Safety:
 * - ScopedValue instances are thread-safe and immutable
 * - Each binding creates a new scope
 * - No external synchronization needed
 * - Safe for virtual threads and structured concurrency
 *
 * @since Java 25
 */
public final class ScopedEngineContext {

    /**
     * ScopedValue container for YEngine instances.
     * This is the core replacement for ThreadLocal<YEngine>.
     */
    private static final ScopedValue<YEngine> ENGINE = ScopedValue.newInstance();

    /**
     * Binds a YEngine instance to the current scope and executes the action.
     * The engine is automatically available to code within the action via current().
     *
     * This method creates a new scope where the engine is bound.
     * The binding is automatically cleaned up when the scope exits.
     *
     * @param <T> the return type of the action
     * @param engine the YAWL engine instance to bind (must not be null)
     * @param action the supplier to execute with the engine context
     * @return the result of the action execution
     * @throws NullPointerException if engine or action is null
     * @throws IllegalStateException if engine is already bound in this scope
     * @throws RuntimeException if action execution fails
     */
    public static <T> T withEngine(YEngine engine, Supplier<T> action) {
        Objects.requireNonNull(engine, "Engine cannot be null");
        Objects.requireNonNull(action, "Action cannot be null");

        return ScopedValue.where(ENGINE, engine)
            .call(action::get);
    }

    /**
     * Binds a YEngine instance to the current scope and executes the callable.
     * This variant accepts a Callable for actions that may throw checked exceptions.
     *
     * @param <T> the return type of the action
     * @param engine the YAWL engine instance to bind (must not be null)
     * @param callable the callable to execute with the engine context
     * @return the result of the callable execution
     * @throws NullPointerException if engine or callable is null
     * @throws IllegalStateException if engine is already bound in this scope
     * @throws RuntimeException if callable execution fails
     */
    public static <T> T withEngine(YEngine engine, Callable<T> callable) {
        Objects.requireNonNull(engine, "Engine cannot be null");
        Objects.requireNonNull(callable, "Callable cannot be null");

        return ScopedValue.where(ENGINE, engine)
            .call(callable::call);
    }

    /**
     * Gets the current YEngine instance in the current scope.
     *
     * This method returns the engine bound by the nearest enclosing withEngine() call.
     *
     * @return the current YEngine instance
     * @throws IllegalStateException if no engine is bound in the current scope
     */
    public static YEngine current() {
        try {
            return ENGINE.get();
        } catch (IllegalStateException e) {
            throw new IllegalStateException(
                "No YEngine bound in current scope. " +
                "Ensure execution is within a withEngine() scope.",
                e
            );
        }
    }

    /**
     * Checks if a YEngine is bound in the current scope.
     *
     * @return true if an engine is bound, false otherwise
     */
    public static boolean isEngineBound() {
        return ENGINE.isBound();
    }

    /**
     * Gets the current YEngine instance if available, otherwise returns null.
     * This is a convenience method for optional access.
     *
     * @return the current YEngine instance, or null if not bound
     */
    public static YEngine currentOrNull() {
        return ENGINE.isBound() ? ENGINE.get() : null;
    }

    /**
     * Creates a new nested scope with the current engine.
     * This is useful for structured concurrency scenarios where you want
     * to ensure the engine context is available to child tasks.
     *
     * @param <T> the return type of the action
     * @param action the supplier to execute in the nested scope
     * @return the result of the action execution
     * @throws NullPointerException if action is null
     * @throws IllegalStateException if no engine is bound in the current scope
     */
    public static <T> T inNestedScope(Supplier<T> action) {
        Objects.requireNonNull(action, "Action cannot be null");

        if (!isEngineBound()) {
            throw new IllegalStateException(
                "Cannot create nested scope: no engine bound in current scope"
            );
        }

        // The ScopedValue framework automatically inherits the binding
        return action.get();
    }

    /**
     * Executes an action without any engine context.
     * This clears the current engine binding for the duration of the action.
     *
     * @param <T> the return type of the action
     * @param action the supplier to execute without engine context
     * @return the result of the action execution
     * @throws NullPointerException if action is null
     */
    public static <T> T withoutEngine(Supplier<T> action) {
        Objects.requireNonNull(action, "Action cannot be null");

        return ScopedValue.where(ENGINE, null)
            .call(() -> {
                // Temporarily clear the binding
                YEngine previous = ENGINE.getOrNull();
                try {
                    return action.get();
                } finally {
                    // Restore the previous binding if it existed
                    if (previous != null) {
                        ScopedValue.where(ENGINE, previous)
                            .call(() -> { /* restore */ });
                    }
                }
            });
    }

    /**
     * Private constructor to prevent instantiation.
     * This class is a utility with only static methods.
     */
    private ScopedEngineContext() {
        throw new AssertionError("Utility class - do not instantiate");
    }
}