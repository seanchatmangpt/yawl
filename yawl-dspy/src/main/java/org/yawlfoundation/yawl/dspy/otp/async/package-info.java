/*
 * Copyright (c) 2025 YAWL Foundation. All rights reserved.
 * This source code is licensed under the Apache License 2.0.
 */

/**
 * Virtual thread-based async execution for DSPy RPC calls.
 *
 * <h2>Primary Class</h2>
 *
 * {@link org.yawlfoundation.yawl.dspy.otp.async.DspyOtpAsyncExecutor} manages
 * a pool of virtual threads, one per RPC execution. This enables millions of
 * concurrent DSPy operations without traditional thread pool constraints.
 *
 * <h2>Execution Model</h2>
 *
 * Each {@code execute().await()} call:
 * <ol>
 *   <li>Spawns a new virtual thread</li>
 *   <li>Marshals inputs to Erlang terms</li>
 *   <li>Issues RPC to dspy_bridge:predict on Erlang node</li>
 *   <li>Blocks virtual thread (parks cleanly on I/O, not spinning)</li>
 *   <li>Unmarshals result back to Java Map</li>
 *   <li>Returns DspyOtpResult</li>
 *   <li>Virtual thread completes and is recycled</li>
 * </ol>
 *
 * <h2>Timeout Handling</h2>
 *
 * Timeout is enforced at the CompletableFuture level using
 * {@link java.util.concurrent.CompletableFuture#orTimeout(long, java.util.concurrent.TimeUnit)}.
 *
 * If RPC exceeds timeout:
 * <ol>
 *   <li>Future is cancelled</li>
 *   <li>Virtual thread is interrupted</li>
 *   <li>DspyOtpTimeoutException is thrown</li>
 * </ol>
 *
 * @see org.yawlfoundation.yawl.dspy.otp.async.DspyOtpAsyncExecutor
 */
package org.yawlfoundation.yawl.dspy.otp.async;
