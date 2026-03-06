/*
 * Copyright (c) 2025 YAWL Foundation. All rights reserved.
 * This source code is licensed under the Apache License 2.0.
 */

/**
 * Layer 3 fluent API for executing DSPy programs via Erlang/OTP.
 *
 * <h2>Architecture</h2>
 *
 * This package exposes a clean, type-safe API that hides all FFM (Foreign Function Memory)
 * and Erlang term marshalling complexity. It is designed to be the primary integration point
 * for YAWL workflows that need to execute DSPy machine learning programs.
 *
 * <h2>Layer Boundary</h2>
 *
 * Public method signatures contain only these types:
 * <ul>
 *   <li>Primitive types: String, int, long, float, double, boolean</li>
 *   <li>Collections: Map, List, Duration</li>
 *   <li>Domain types: DspyOtpResult, DspyOtpExecution, DspyOtpCallback</li>
 *   <li>Exceptions: DspyOtpException and subclasses</li>
 * </ul>
 *
 * <strong>NEVER exposed in public API:</strong>
 * <ul>
 *   <li>{@link java.lang.foreign.MemorySegment}</li>
 *   <li>{@link java.lang.foreign.Arena}</li>
 *   <li>{@link org.yawlfoundation.yawl.erlang.term.ErlTerm} or subclasses</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * // Connect to Erlang node running DSPy bridge
 * try (DspyOtpBridge bridge = DspyOtpBridge.connect("yawl_dspy@localhost", "secret")) {
 *
 *     // Build and execute a program
 *     DspyOtpExecution execution = bridge.program("sentiment-analyzer")
 *         .input("text", "This product is amazing!")
 *         .input("language", "en")
 *         .execute();
 *
 *     // Wait for result (blocks current virtual thread cleanly)
 *     DspyOtpResult result = execution.await(5, TimeUnit.SECONDS);
 *
 *     // Access outputs
 *     String sentiment = result.getOutput("sentiment", String.class);
 *     double confidence = result.getOutput("confidence", Double.class);
 * }
 * }</pre>
 *
 * <h2>Asynchronous Execution</h2>
 *
 * <pre>{@code
 * execution.awaitAsync((result, error) -> {
 *     if (error != null) {
 *         logger.error("DSPy failed", error);
 *         workItem.fail();
 *     } else {
 *         workItem.setOutput("sentiment", result.getOutput("sentiment"));
 *     }
 * });
 * }</pre>
 *
 * <h2>Virtual Threads</h2>
 *
 * All RPC calls execute on dedicated virtual threads via
 * {@link java.util.concurrent.Executors#newVirtualThreadPerTaskExecutor()}.
 *
 * This enables:
 * <ul>
 *   <li>Millions of concurrent DSPy executions in a single JVM</li>
 *   <li>No thread pool sizing (one virtual thread per execution)</li>
 *   <li>Clean parking on I/O (Erlang RPC), not spinning</li>
 * </ul>
 *
 * <h2>Exception Handling</h2>
 *
 * All DSPy OTP errors are subtypes of {@link org.yawlfoundation.yawl.dspy.otp.DspyOtpException}:
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.dspy.otp.DspyOtpException.DspyOtpConnectionException}
 *       — Network or OTP node unavailable</li>
 *   <li>{@link org.yawlfoundation.yawl.dspy.otp.DspyOtpException.DspyOtpRpcException}
 *       — Remote procedure call failed on Erlang side</li>
 *   <li>{@link org.yawlfoundation.yawl.dspy.otp.DspyOtpException.DspyOtpTimeoutException}
 *       — RPC exceeded timeout</li>
 *   <li>{@link org.yawlfoundation.yawl.dspy.otp.DspyOtpException.DspyOtpSerializationException}
 *       — Term marshalling failed</li>
 *   <li>{@link org.yawlfoundation.yawl.dspy.otp.DspyOtpException.DspyOtpValidationException}
 *       — Input validation failed before RPC</li>
 * </ul>
 *
 * <h2>Related Packages</h2>
 *
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.dspy.otp.builder} — Fluent builder API</li>
 *   <li>{@link org.yawlfoundation.yawl.dspy.otp.async} — Virtual thread execution</li>
 *   <li>{@link org.yawlfoundation.yawl.dspy.otp.schema} — Input validation</li>
 *   <li>{@link org.yawlfoundation.yawl.erlang.term.dspy} — Term marshalling (internal)</li>
 * </ul>
 *
 * @see org.yawlfoundation.yawl.dspy.otp.DspyOtpBridge
 * @see org.yawlfoundation.yawl.erlang.bridge.ErlangBridge
 */
package org.yawlfoundation.yawl.dspy.otp;
