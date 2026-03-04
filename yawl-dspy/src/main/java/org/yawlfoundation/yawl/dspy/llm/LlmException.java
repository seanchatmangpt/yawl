/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.dspy.llm;

/**
 * Exception thrown by LLM clients.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class LlmException extends RuntimeException {

    private final ErrorKind kind;
    private final int statusCode;

    public LlmException(ErrorKind kind, String message) {
        super(message);
        this.kind = kind;
        this.statusCode = 0;
    }

    public LlmException(ErrorKind kind, String message, int statusCode) {
        super(message);
        this.kind = kind;
        this.statusCode = statusCode;
    }

    public LlmException(ErrorKind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.statusCode = 0;
    }

    public ErrorKind kind() { return kind; }
    public int statusCode() { return statusCode; }

    /**
     * Check if this error is retryable.
     */
    public boolean isRetryable() {
        return kind == ErrorKind.RATE_LIMITED || kind == ErrorKind.TIMEOUT || kind == ErrorKind.SERVICE_UNAVAILABLE;
    }

    public enum ErrorKind {
        AUTHENTICATION,
        RATE_LIMITED,
        CONTEXT_TOO_LONG,
        CONTENT_FILTERED,
        TIMEOUT,
        SERVICE_UNAVAILABLE,
        INVALID_REQUEST,
        MODEL_NOT_FOUND,
        RUNTIME_ERROR
    }
}
