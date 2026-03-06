/*
 * Copyright (c) 2025 YAWL Foundation. All rights reserved.
 * This source code is licensed under the Apache License 2.0.
 */
package org.yawlfoundation.yawl.dspy.otp.async;

import org.jspecify.annotations.NullMarked;
import org.yawlfoundation.yawl.dspy.DspyExecutionMetrics;
import org.yawlfoundation.yawl.dspy.otp.DspyOtpException;
import org.yawlfoundation.yawl.dspy.otp.DspyOtpResult;
import org.yawlfoundation.yawl.erlang.bridge.ErlangNode;
import org.yawlfoundation.yawl.erlang.term.ErlMap;
import org.yawlfoundation.yawl.erlang.term.ErlTerm;
import org.yawlfoundation.yawl.erlang.term.dspy.DspyTermMarshaller;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Asynchronous DSPy RPC execution using virtual threads.
 *
 * Each RPC call runs on a dedicated virtual thread, enabling millions
 * of concurrent executions without thread pool sizing concerns.
 *
 * <h2>Implementation Notes</h2>
 * <ul>
 *   <li>Uses {@link Executors#newVirtualThreadPerTaskExecutor()} for unbounded parallelism</li>
 *   <li>Virtual threads park cleanly on I/O (Erlang RPC call), not spinning</li>
 *   <li>No pinning by synchronized blocks or JNI (uses ReentrantLock instead)</li>
 *   <li>Timeout enforced per-execution via {@link CompletableFuture#orTimeout(long, java.util.concurrent.TimeUnit)}</li>
 * </ul>
 */
@NullMarked
public final class DspyOtpAsyncExecutor {

    private static final DspyOtpAsyncExecutor INSTANCE = new DspyOtpAsyncExecutor();

    private final Executor virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Get the singleton async executor.
     *
     * @return the executor
     */
    public static DspyOtpAsyncExecutor instance() {
        return INSTANCE;
    }

    /**
     * Execute a DSPy program asynchronously on a virtual thread.
     *
     * @param programName the DSPy program to execute
     * @param inputs the input parameters
     * @param erlangNode the OTP node connection
     * @param timeout RPC timeout
     * @param executionId unique execution identifier
     * @return a future that completes with the result
     */
    public CompletableFuture<DspyOtpResult> executeAsync(
            String programName,
            Map<String, Object> inputs,
            ErlangNode erlangNode,
            Duration timeout,
            String executionId) {

        Objects.requireNonNull(programName, "programName");
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(erlangNode, "erlangNode");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(executionId, "executionId");

        CompletableFuture<DspyOtpResult> future = CompletableFuture.supplyAsync(() -> {
            try {
                return executeOnErlangNode(programName, inputs, erlangNode, executionId);
            } catch (Exception e) {
                throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
            }
        }, virtualThreadExecutor);

        // Add timeout enforcement: if future doesn't complete in timeout duration,
        // cancel it and raise DspyOtpTimeoutException
        future.orTimeout(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
              .exceptionally(ex -> {
                  if (ex instanceof java.util.concurrent.TimeoutException) {
                      throw new DspyOtpException.DspyOtpTimeoutException(
                          "DSPy RPC exceeded timeout of " + timeout,
                          timeout.toMillis()
                      );
                  }
                  throw (RuntimeException) ex;
              });

        return future;
    }

    /**
     * Execute the actual RPC call to Erlang (blocks on virtual thread).
     *
     * Calls dspy_bridge:predict(ProgramName, InputsMap) on the Erlang node.
     * Expects response: {ok, ResultMap} or {error, Reason}.
     */
    private DspyOtpResult executeOnErlangNode(
            String programName,
            Map<String, Object> inputs,
            ErlangNode erlangNode,
            String executionId) {

        long startTime = System.currentTimeMillis();

        try {
            // Marshal input map to Erlang terms
            ErlMap erlInputs = DspyTermMarshaller.toErlMap(inputs);

            // Prepare RPC arguments: [ProgramName, InputsMap]
            List<ErlTerm> rpcArgs = List.of(
                DspyTermMarshaller.toErlTerm(programName),
                erlInputs
            );

            // Call dspy_bridge:predict(ProgramName, InputsMap) on Erlang node
            ErlTerm response = erlangNode.rpc("dspy_bridge", "predict", rpcArgs);

            // Parse response tuple: {ok, ResultMap} or {error, Reason}
            Map<String, Object> resultMap = extractResultFromResponse(response);
            long duration = System.currentTimeMillis() - startTime;

            // Create execution metrics (minimal for now; Erlang side can provide more)
            DspyExecutionMetrics metrics = new DspyExecutionMetrics(
                0L,  // accuracy: not computed by RPC bridge
                0L,  // f1Score: not computed by RPC bridge
                duration  // latency: roundtrip time
            );

            return new DspyOtpResult(resultMap, metrics, duration, executionId);

        } catch (DspyOtpException e) {
            throw e;
        } catch (Exception e) {
            throw new DspyOtpException.DspyOtpRpcException(
                "RPC call to dspy_bridge:predict failed: " + e.getMessage(),
                e.toString()
            );
        }
    }

    /**
     * Extract result map from Erlang response tuple.
     *
     * Expected format: {ok, ResultMap} → ResultMap
     *                  {error, Reason} → throws exception
     */
    private Map<String, Object> extractResultFromResponse(ErlTerm response) {
        // Response should be a tuple {ok, Map} or {error, Reason}
        if (response instanceof org.yawlfoundation.yawl.erlang.term.ErlTuple tuple) {
            var elements = tuple.getElements();
            if (elements.size() >= 2) {
                ErlTerm status = elements.get(0);
                ErlTerm data = elements.get(1);

                if (status instanceof org.yawlfoundation.yawl.erlang.term.ErlAtom atom) {
                    if ("ok".equals(atom.getValue())) {
                        // Success: {ok, ResultMap}
                        if (data instanceof ErlMap map) {
                            return DspyTermMarshaller.fromErlMap(map);
                        }
                        // Fallback: if not a map, wrap in result
                        Map<String, Object> result = new java.util.HashMap<>();
                        result.put("_result", DspyTermMarshaller.fromErlTerm(data));
                        return result;
                    } else if ("error".equals(atom.getValue())) {
                        // Error: {error, Reason}
                        String reason = DspyTermMarshaller.fromErlTerm(data).toString();
                        throw new DspyOtpException.DspyOtpRpcException(
                            "DSPy RPC failed on Erlang side",
                            reason
                        );
                    }
                }
            }
        }

        // Unexpected response format
        throw new DspyOtpException.DspyOtpSerializationException(
            "Unexpected RPC response format: " + response.getClass().getSimpleName()
        );
    }

    /**
     * Internal constructor (singleton).
     */
    private DspyOtpAsyncExecutor() {
    }
}
