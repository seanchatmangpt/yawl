/*
 * Copyright (c) 2025 YAWL Foundation. All rights reserved.
 * This source code is licensed under the Apache License 2.0.
 */
package org.yawlfoundation.yawl.dspy.otp.builder;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.yawlfoundation.yawl.dspy.otp.DspyOtpException;
import org.yawlfoundation.yawl.dspy.otp.DspyOtpExecution;
import org.yawlfoundation.yawl.dspy.otp.async.DspyOtpAsyncExecutor;
import org.yawlfoundation.yawl.erlang.bridge.ErlangNode;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Fluent builder for constructing and executing DSPy program calls via OTP.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * DspyOtpExecution execution = bridge.program("sentiment-analyzer")
 *     .input("text", "Amazing product!")
 *     .execute();
 *
 * DspyOtpResult result = execution.await(5, TimeUnit.SECONDS);
 * String sentiment = result.getOutput("sentiment");
 * }</pre>
 *
 * <h2>Batch Inputs</h2>
 * <pre>{@code
 * Map<String, Object> inputs = Map.of(
 *     "text", userInput,
 *     "lang", "en"
 * );
 * DspyOtpExecution execution = bridge.program("sentiment-analyzer")
 *     .inputs(inputs)
 *     .execute();
 * }</pre>
 */
@NullMarked
public final class DspyOtpProgramBuilder {

    private final String programName;
    private final ErlangNode erlangNode;
    private final String erlangNodeName;
    private final Duration defaultTimeout;
    private final Map<String, Object> inputs = new HashMap<>();

    /**
     * Create a new builder (typically called via DspyOtpBridge.program()).
     *
     * @param programName the DSPy program identifier
     * @param erlangNode the active OTP node connection
     * @param erlangNodeName the node name for error messages
     * @param defaultTimeout default RPC timeout
     */
    public DspyOtpProgramBuilder(String programName, ErlangNode erlangNode,
                                 String erlangNodeName, Duration defaultTimeout) {
        this.programName = Objects.requireNonNull(programName, "programName");
        this.erlangNode = Objects.requireNonNull(erlangNode, "erlangNode");
        this.erlangNodeName = Objects.requireNonNull(erlangNodeName, "erlangNodeName");
        this.defaultTimeout = Objects.requireNonNull(defaultTimeout, "defaultTimeout");
    }

    /**
     * Add a single input parameter.
     *
     * @param key input field name
     * @param value input value (String, int, float, Map, List, etc.)
     * @return this builder for chaining
     */
    public DspyOtpProgramBuilder input(String key, @Nullable Object value) {
        Objects.requireNonNull(key, "key");
        inputs.put(key, value);
        return this;
    }

    /**
     * Add multiple input parameters at once.
     *
     * @param inputMap all input key-value pairs
     * @return this builder for chaining
     */
    public DspyOtpProgramBuilder inputs(Map<String, Object> inputMap) {
        Objects.requireNonNull(inputMap, "inputMap");
        inputs.putAll(inputMap);
        return this;
    }

    /**
     * Execute the DSPy program with collected inputs.
     *
     * Starts an async RPC call to the Erlang DSPy bridge.
     * The actual execution happens on a virtual thread.
     *
     * @return a handle to the in-flight execution
     * @throws DspyOtpException if execution cannot be started
     */
    public DspyOtpExecution execute() {
        return execute(defaultTimeout);
    }

    /**
     * Execute with custom timeout.
     *
     * @param timeout how long to wait for RPC completion
     * @return a handle to the in-flight execution
     * @throws DspyOtpException if execution cannot be started
     */
    public DspyOtpExecution execute(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");

        String executionId = UUID.randomUUID().toString();

        // Validate inputs before RPC (fail-fast)
        validateInputs();

        // Start async execution on virtual thread
        var asyncExecutor = DspyOtpAsyncExecutor.instance();
        var future = asyncExecutor.executeAsync(
            programName,
            inputs,
            erlangNode,
            timeout,
            executionId
        );

        return new DspyOtpExecution(executionId, future, defaultTimeout);
    }

    /**
     * Validate inputs before RPC.
     * Checks that at least one input is provided. Full schema validation
     * is performed by the Erlang DSPy bridge service.
     */
    private void validateInputs() {
        if (inputs.isEmpty()) {
            throw new DspyOtpException.DspyOtpValidationException(
                "No inputs provided for program '" + programName + "'",
                "<all>", "at least one input"
            );
        }
    }

    @Override
    public String toString() {
        return "DspyOtpProgramBuilder{" +
                "programName='" + programName + '\'' +
                ", inputCount=" + inputs.size() +
                '}';
    }
}
