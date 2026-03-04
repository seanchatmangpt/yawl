/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 */

package org.yawlfoundation.yawl.ml.dspy;

/**
 * Exception thrown when DSPy operations fail.
 */
public final class DspyException extends RuntimeException {

    private final String errorCode;

    public DspyException(String message) {
        super(message);
        this.errorCode = "UNKNOWN";
    }

    public DspyException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "UNKNOWN";
    }

    public DspyException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public DspyException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
