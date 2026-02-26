/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * This file is part of YAWL. YAWL is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License.
 */
package org.yawlfoundation.yawl.ggen.polyglot;

import java.lang.reflect.Method;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages a GraalVM Python (GraalPy) execution context.
 * Uses reflection to load org.graalvm.polyglot.Context at runtime so the
 * class compiles on any JDK even without GraalPy on the classpath.
 * If GraalPy JARs are absent, isAvailable() returns false and all eval()
 * calls throw PolyglotException.
 */
public final class GraalPyRuntime implements AutoCloseable {

    private static final String CONTEXT_CLASS = "org.graalvm.polyglot.Context";
    private static volatile boolean availabilityChecked = false;
    private static volatile boolean graalPyAvailable = false;

    private final ReentrantLock lock = new ReentrantLock();
    private Object context; // org.graalvm.polyglot.Context instance, null until initialized
    private boolean closed = false;

    /**
     * Checks if GraalPy is available on the classpath.
     * Result is cached after first check.
     *
     * @return true if GraalPy classes can be loaded, false otherwise
     */
    public static boolean isAvailable() {
        if (!availabilityChecked) {
            try {
                Class.forName(CONTEXT_CLASS);
                graalPyAvailable = true;
            } catch (ClassNotFoundException e) {
                graalPyAvailable = false;
            }
            availabilityChecked = true;
        }
        return graalPyAvailable;
    }

    /**
     * Evaluates Python code and returns the result as a String.
     *
     * @param pythonCode the Python code to evaluate
     * @return the result of evaluation as a String
     * @throws PolyglotException if GraalPy is unavailable or evaluation fails
     */
    public String eval(String pythonCode) {
        if (!isAvailable()) {
            throw new PolyglotException(
                "GraalPy is not available. Add org.graalvm.polyglot:polyglot:25.0.2 " +
                "and org.graalvm.polyglot:python:25.0.2 to the classpath.");
        }
        lock.lock();
        try {
            if (closed) throw new PolyglotException("GraalPyRuntime has been closed");
            ensureContextInitialized();
            // Use reflection to call context.eval("python", code)
            Class<?> contextClass = Class.forName(CONTEXT_CLASS);
            Method evalMethod = contextClass.getMethod("eval", String.class, CharSequence.class);
            Object value = evalMethod.invoke(context, "python", pythonCode);
            // Convert Value to String via toString()
            return value != null ? value.toString() : "";
        } catch (PolyglotException e) {
            throw e;
        } catch (Exception e) {
            throw new PolyglotException("Python evaluation failed: " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    private void ensureContextInitialized() throws ReflectiveOperationException {
        if (context == null) {
            // Context.newBuilder("python").allowAllAccess(true).build()
            Class<?> contextClass = Class.forName(CONTEXT_CLASS);
            Class<?> builderClass = Class.forName("org.graalvm.polyglot.Context$Builder");
            Method newBuilderMethod = contextClass.getMethod("newBuilder", String[].class);
            Object builder = newBuilderMethod.invoke(null, (Object) new String[]{"python"});
            Method allowAllAccessMethod = builderClass.getMethod("allowAllAccess", boolean.class);
            builder = allowAllAccessMethod.invoke(builder, true);
            Method buildMethod = builderClass.getMethod("build");
            context = buildMethod.invoke(builder);
        }
    }

    /**
     * Closes the GraalPy runtime and releases all resources.
     * Subsequent calls to eval() will throw PolyglotException.
     * Idempotent: safe to call multiple times.
     */
    @Override
    public void close() {
        lock.lock();
        try {
            if (!closed && context != null) {
                try {
                    Class<?> contextClass = Class.forName(CONTEXT_CLASS);
                    Method closeMethod = contextClass.getMethod("close");
                    closeMethod.invoke(context);
                } catch (Exception e) {
                    // Log but don't rethrow — close() should not throw
                }
                context = null;
            }
            closed = true;
        } finally {
            lock.unlock();
        }
    }
}
