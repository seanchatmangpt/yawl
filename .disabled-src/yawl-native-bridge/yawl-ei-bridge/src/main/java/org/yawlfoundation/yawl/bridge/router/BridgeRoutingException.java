/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it terms of the GNU Lesser
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
package org.yawlfoundation.yawl.bridge.router;

/**
 * Exception thrown when bridge routing fails.
 * Wraps routing-specific errors with contextual information.
 */
public final class BridgeRoutingException extends RuntimeException {
    private final NativeCall failedCall;
    private final CallPattern failedPattern;

    /**
     * Constructs a new BridgeRoutingException.
     *
     * @param message detail message
     */
    public BridgeRoutingException(String message) {
        this(message, null, null);
    }

    /**
     * Constructs a new BridgeRoutingException with a cause.
     *
     * @param message detail message
     * @param cause the cause
     */
    public BridgeRoutingException(String message, Throwable cause) {
        this(message, null, cause);
    }

    /**
     * Constructs a new BridgeRoutingException with contextual information.
     *
     * @param message detail message
     * @param failedCall the call that failed
     * @param cause the root cause
     */
    public BridgeRoutingException(String message, NativeCall failedCall, Throwable cause) {
        super(message, cause);
        this.failedCall = failedCall;
        this.failedPattern = failedCall != null ? failedCall.callPattern() : null;
    }

    /**
     * Gets the call that failed.
     *
     * @return the failed NativeCall, or null if not available
     */
    public NativeCall getFailedCall() {
        return failedCall;
    }

    /**
     * Gets the call pattern that failed.
     *
     * @return the CallPattern that failed, or null if not available
     */
    public CallPattern getFailedPattern() {
        return failedPattern;
    }

    /**
     * Gets a formatted error message with contextual information.
     *
     * @return detailed error message
     */
    @Override
    public String getMessage() {
        String message = super.getMessage();
        if (failedCall != null) {
            message = message + " (call: " + failedCall.toNtriple() + ")";
        }
        if (failedPattern != null) {
            message = message + " (pattern: " + failedPattern + ")";
        }
        return message;
    }

    /**
     * Gets a short error message without call details.
     *
     * @return brief error message
     */
    public String getShortMessage() {
        return super.getMessage();
    }
}