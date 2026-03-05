/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * Exception for process mining operations.
 */
package org.yawlfoundation.yawl.erlang.processmining;

/**
 * Exception thrown when process mining operations fail.
 *
 * <p>This exception wraps underlying errors from the Erlang/NIF layer,
 * providing a clean exception API for Java developers.</p>
 */
public final class ProcessMiningException extends Exception {

    /**
     * Creates a new ProcessMiningException with a message.
     *
     * @param message error description
     */
    public ProcessMiningException(String message) {
        super(message);
    }

    /**
     * Creates a new ProcessMiningException with a message and cause.
     *
     * @param message error description
     * @param cause underlying exception
     */
    public ProcessMiningException(String message, Throwable cause) {
        super(message, cause);
    }
}
