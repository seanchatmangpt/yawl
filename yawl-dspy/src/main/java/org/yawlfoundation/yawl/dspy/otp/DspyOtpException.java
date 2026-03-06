/*
 * Copyright (c) 2025 YAWL Foundation. All rights reserved.
 * This source code is licensed under the Apache License 2.0.
 */
package org.yawlfoundation.yawl.dspy.otp;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Base exception for all DSPy OTP bridge errors.
 *
 * Subclasses provide specific error contexts:
 * - {@link DspyOtpConnectionException}: network/OTP node issues
 * - {@link DspyOtpRpcException}: remote procedure call failures
 * - {@link DspyOtpTimeoutException}: RPC exceeded timeout
 * - {@link DspyOtpSerializationException}: term marshalling failed
 * - {@link DspyOtpValidationException}: input schema validation failed
 */
@NullMarked
public class DspyOtpException extends RuntimeException {

    /**
     * Create exception with message.
     */
    public DspyOtpException(String message) {
        super(message);
    }

    /**
     * Create exception with message and cause.
     */
    public DspyOtpException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }

    /**
     * Create exception with cause only.
     */
    public DspyOtpException(@Nullable Throwable cause) {
        super(cause);
    }

    /**
     * Connection error: OTP node unavailable, network partition, etc.
     */
    @NullMarked
    public static final class DspyOtpConnectionException extends DspyOtpException {
        public DspyOtpConnectionException(String message) {
            super(message);
        }

        public DspyOtpConnectionException(String message, @Nullable Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * RPC error: remote DSPy execution failed.
     * The Erlang side returned an error tuple.
     */
    @NullMarked
    public static final class DspyOtpRpcException extends DspyOtpException {
        private final String remoteCause;

        public DspyOtpRpcException(String message, String remoteCause) {
            super(message);
            this.remoteCause = remoteCause;
        }

        public String getRemoteCause() {
            return remoteCause;
        }
    }

    /**
     * Timeout error: RPC call exceeded the specified duration.
     * May indicate slow Erlang node, network latency, or slow Python execution.
     */
    @NullMarked
    public static final class DspyOtpTimeoutException extends DspyOtpException {
        private final long elapsedMillis;

        public DspyOtpTimeoutException(String message, long elapsedMillis) {
            super(message);
            this.elapsedMillis = elapsedMillis;
        }

        public long getElapsedMillis() {
            return elapsedMillis;
        }
    }

    /**
     * Serialization error: failed to marshal Java objects to Erlang terms.
     * Indicates a data type incompatibility or marshaller bug.
     */
    @NullMarked
    public static final class DspyOtpSerializationException extends DspyOtpException {
        public DspyOtpSerializationException(String message) {
            super(message);
        }

        public DspyOtpSerializationException(String message, @Nullable Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Validation error: input does not match DSPy program's schema.
     * Fail-fast before RPC to avoid network round-trips.
     */
    @NullMarked
    public static final class DspyOtpValidationException extends DspyOtpException {
        private final String fieldName;
        private final String expectedType;

        public DspyOtpValidationException(String message, String fieldName, String expectedType) {
            super(message);
            this.fieldName = fieldName;
            this.expectedType = expectedType;
        }

        public String getFieldName() {
            return fieldName;
        }

        public String getExpectedType() {
            return expectedType;
        }
    }
}
