/*
 * Copyright (c) 2025 YAWL Foundation. All rights reserved.
 * This source code is licensed under the Apache License 2.0.
 */
package org.yawlfoundation.yawl.dspy.otp;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Callback interface for asynchronous DSPy execution completion.
 *
 * Exactly one of (result, error) will be non-null.
 * Used with {@link DspyOtpExecution#awaitAsync(DspyOtpCallback)}.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * execution.awaitAsync((result, error) -> {
 *     if (error != null) {
 *         logger.error("DSPy failed", error);
 *         workItem.fail();
 *     } else {
 *         String sentiment = result.getOutput("sentiment");
 *         workItem.setOutput("sentiment", sentiment);
 *     }
 * });
 * }</pre>
 */
@NullMarked
@FunctionalInterface
public interface DspyOtpCallback {

    /**
     * Invoked when DSPy execution completes or fails.
     *
     * @param result non-null if execution succeeded
     * @param error non-null if execution failed
     */
    void accept(@Nullable DspyOtpResult result, @Nullable Throwable error);
}
