/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 */

package org.yawlfoundation.yawl.ml.tpot2;

/**
 * Exception thrown when TPOT2 operations fail.
 */
public final class Tpot2Exception extends Exception {

    private final String errorCode;

    public Tpot2Exception(String message) {
        super(message);
        this.errorCode = "UNKNOWN";
    }

    public Tpot2Exception(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "UNKNOWN";
    }

    public Tpot2Exception(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public Tpot2Exception(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
