/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * This file is part of YAWL. YAWL is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License.
 */
package org.yawlfoundation.yawl.ggen.polyglot;

/**
 * Thrown when GraalPy/polyglot operations fail.
 * Wraps underlying polyglot errors as unchecked exceptions.
 */
public class PolyglotException extends RuntimeException {

    /**
     * Constructs a PolyglotException with a detail message.
     *
     * @param message the detail message
     */
    public PolyglotException(String message) {
        super(message);
    }

    /**
     * Constructs a PolyglotException with a detail message and cause.
     *
     * @param message the detail message
     * @param cause   the cause
     */
    public PolyglotException(String message, Throwable cause) {
        super(message, cause);
    }
}
