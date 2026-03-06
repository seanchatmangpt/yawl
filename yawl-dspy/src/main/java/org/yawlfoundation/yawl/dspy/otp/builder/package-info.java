/*
 * Copyright (c) 2025 YAWL Foundation. All rights reserved.
 * This source code is licensed under the Apache License 2.0.
 */

/**
 * Fluent builder API for constructing DSPy RPC calls.
 *
 * <h2>Primary Class</h2>
 *
 * {@link org.yawlfoundation.yawl.dspy.otp.builder.DspyOtpProgramBuilder} provides
 * a fluent interface for specifying program name, inputs, and timeout before execution.
 *
 * <h2>Typical Flow</h2>
 *
 * <pre>{@code
 * bridge.program("sentiment-analyzer")      // Create builder
 *     .input("text", userInput)              // Add input
 *     .input("lang", "en")                   // Add more inputs
 *     .execute()                             // Start async RPC
 *     .await(5, TimeUnit.SECONDS);           // Wait for result
 * }</pre>
 *
 * <h2>Input Validation</h2>
 *
 * Builders validate that at least one input is provided. Full schema validation
 * (if a schema is available) happens on the Erlang side to avoid network overhead
 * of duplicate validation logic.
 *
 * @see org.yawlfoundation.yawl.dspy.otp.builder.DspyOtpProgramBuilder
 */
package org.yawlfoundation.yawl.dspy.otp.builder;
