/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
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

package org.yawlfoundation.yawl.dspy.module;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.dspy.llm.LlmClient;
import org.yawlfoundation.yawl.dspy.llm.LlmException;
import org.yawlfoundation.yawl.dspy.llm.LlmRequest;
import org.yawlfoundation.yawl.dspy.llm.LlmResponse;
import org.yawlfoundation.yawl.dspy.signature.Example;
import org.yawlfoundation.yawl.dspy.signature.InputField;
import org.yawlfoundation.yawl.dspy.signature.OutputField;
import org.yawlfoundation.yawl.dspy.signature.Signature;
import org.yawlfoundation.yawl.dspy.signature.SignatureResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ReAct (Reasoning + Acting) module - interleaves thinking and tool use.
 *
 * <p>ReAct enables LLMs to use tools while reasoning. Each step consists of:
 * <ol>
 *   <li><strong>Thought</strong>: LLM reasons about what to do next</li>
 *   <li><strong>Action</strong>: LLM selects a tool to use</li>
 *   <li><strong>Observation</strong>: Tool result is fed back to LLM</li>
 * </ol>
 *
 * <h2>Defining Tools:</h2>
 * {@snippet :
 * var tools = List.of(
 *     Tool.function("search_cases", "Search for similar cases", this::searchCases),
 *     Tool.function("get_metrics", "Get process metrics", this::getMetrics)
 * );
 * }
 *
 * <h2>Usage:</h2>
 * {@snippet :
 * var sig = Signature.builder()
 *     .description("Diagnose workflow issue")
 *     .input("caseId", "the case to diagnose", String.class)
 *     .output("diagnosis", "root cause analysis", String.class)
 *     .output("recommendation", "fix recommendation", String.class)
 *     .build();
 *
 * var react = new ReAct<>(sig, llmClient, tools);
 * var result = react.run(Map.of("caseId", "case-123"));
 *
 * // Access trace of thoughts and actions
 * List<ReAct.Step> trace = result.trace();
 * }
 *
 * @param <T> self-referential type for fluent API
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class ReAct<T extends ReAct<T>> implements Module<T> {

    private static final Logger log = LoggerFactory.getLogger(ReAct.class);
    private static final Pattern THOUGHT_PATTERN = Pattern.compile("(?i)thought\\s*:\\s*(.+?)(?=action:|observation:|$)", Pattern.DOTALL);
    private static final Pattern ACTION_PATTERN = Pattern.compile("(?i)action\\s*:\\s*(\\w+)\\s*\\[([^\\]]*)\\]");
    private static final int MAX_ITERATIONS = 10;

    private final Signature signature;
    private final LlmClient llm;
    private final List<Tool> tools;
    private final List<Example> examples;
    private final int maxIterations;
    private final @Nullable Double temperature;

    /**
     * Create a new ReAct module.
     *
     * @param signature the signature defining input/output contract
     * @param llm the LLM client for reasoning
     * @param tools the tools available for action
     */
    public ReAct(Signature signature, LlmClient llm, List<Tool> tools) {
        this(signature, llm, tools, List.of(), MAX_ITERATIONS, null);
    }

    private ReAct(
        Signature signature,
        LlmClient llm,
        List<Tool> tools,
        List<Example> examples,
        int maxIterations,
        @Nullable Double temperature
    ) {
        this.signature = signature;
        this.llm = llm;
        this.tools = List.copyOf(tools);
        this.examples = examples;
        this.maxIterations = maxIterations;
        this.temperature = temperature;
    }

    @Override
    public Signature signature() {
        return signature;
    }

    /**
     * Get the tools available to this ReAct module.
     */
    public List<Tool> tools() {
        return tools;
    }

    @Override
    public List<Example> examples() {
        return examples;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T withExamples(List<Example> examples) {
        return (T) new ReAct<>(signature, llm, tools, examples, maxIterations, temperature);
    }

    /**
     * Set the maximum number of reasoning iterations.
     */
    @SuppressWarnings("unchecked")
    public T withMaxIterations(int maxIterations) {
        return (T) new ReAct<>(signature, llm, tools, examples, maxIterations, temperature);
    }

    /**
     * Set the temperature for LLM calls.
     */
    @SuppressWarnings("unchecked")
    public T withTemperature(double temperature) {
        return (T) new ReAct<>(signature, llm, tools, examples, maxIterations, temperature);
    }

    @Override
    public SignatureResult run(Map<String, Object> inputs) {
        log.debug("Running ReAct module: {}", signature.id());

        List<Step> trace = new ArrayList<>();
        Map<String, Object> context = new LinkedHashMap<>(inputs);
        StringBuilder history = new StringBuilder();

        // Build initial prompt
        String prompt = buildReActPrompt(inputs, null);
        history.append(prompt);

        for (int i = 0; i < maxIterations; i++) {
            log.debug("ReAct iteration {} of {}", i + 1, maxIterations);

            // Call LLM
            String llmOutput = callLlm(history.toString());
            history.append("\n\n").append(llmOutput);

            // Parse thought
            String thought = parseThought(llmOutput);
            if (thought != null) {
                log.debug("Thought: {}", thought);
            }

            // Parse action
            ActionCall actionCall = parseAction(llmOutput);

            if (actionCall == null) {
                // No action - try to parse final answer
                SignatureResult result = signature.parse(llmOutput);
                if (result.isComplete()) {
                    log.debug("ReAct completed after {} iterations", i + 1);
                    return withTrace(result, trace);
                }
                // Continue if incomplete
                history.append("\n\nObservation: Please provide an action or the final answer.");
                continue;
            }

            // Execute action
            String observation = executeAction(actionCall);
            log.debug("Action: {} -> {}", actionCall.toolName(), observation);

            // Record step
            trace.add(new Step(thought, actionCall.toolName(), actionCall.args(), observation));

            // Add observation to history
            history.append("\n\nObservation: ").append(observation);

            // Check if we should finish
            if (actionCall.toolName().equalsIgnoreCase("finish") ||
                actionCall.toolName().equalsIgnoreCase("submit")) {
                // Parse final answer
                SignatureResult result = signature.parse(observation);
                return withTrace(result, trace);
            }
        }

        // Max iterations reached - try to extract answer
        SignatureResult result = signature.parse(history.toString());
        return withTrace(result, trace);
    }

    private SignatureResult withTrace(SignatureResult result, List<Step> trace) {
        // Add trace to values for access
        Map<String, Object> valuesWithTrace = new LinkedHashMap<>(result.values());
        valuesWithTrace.put("_react_trace", trace);
        return new SignatureResult(valuesWithTrace, result.rawOutput(), result.signature());
    }

    /**
     * Extract the ReAct trace from a result.
     */
    @SuppressWarnings("unchecked")
    public static List<Step> getTrace(SignatureResult result) {
        Object trace = result.values().get("_react_trace");
        return trace instanceof List<?> list ? (List<Step>) list : List.of();
    }

    private String buildReActPrompt(Map<String, Object> inputs, @Nullable String additionalContext) {
        StringBuilder sb = new StringBuilder();

        sb.append("# Task\n").append(signature.description()).append("\n\n");

        sb.append("# Available Tools\n");
        for (Tool tool : tools) {
            sb.append("- ").append(tool.name()).append(": ").append(tool.description()).append("\n");
        }
        sb.append("- finish: Submit the final answer when done\n\n");

        sb.append("# Inputs\n");
        for (InputField in : signature.inputs()) {
            sb.append(in.name()).append(": ");
            if (inputs.containsKey(in.name())) {
                sb.append(inputs.get(in.name()));
            }
            sb.append("\n");
        }

        sb.append("\n# Instructions\n");
        sb.append("Use the following format:\n\n");
        sb.append("Thought: [your reasoning about what to do]\n");
        sb.append("Action: tool_name[arg1, arg2, ...]\n");
        sb.append("Observation: [tool result]\n");
        sb.append("... (repeat Thought/Action/Observation as needed)\n");
        sb.append("Thought: I now know the final answer\n");

        sb.append("\n# Final Output Format\n");
        for (OutputField out : signature.outputs()) {
            sb.append(out.name()).append(": [").append(out.description()).append("]\n");
        }

        if (additionalContext != null) {
            sb.append("\n# Additional Context\n").append(additionalContext);
        }

        return sb.toString();
    }

    private String callLlm(String prompt) {
        try {
            var requestBuilder = LlmRequest.builder().prompt(prompt);
            if (temperature != null) {
                requestBuilder.temperature(temperature);
            }
            return llm.complete(requestBuilder.build()).text();
        } catch (LlmException e) {
            throw new ModuleException(
                signature.id(),
                signature,
                ModuleException.ErrorKind.LLM_ERROR,
                "LLM call failed: " + e.getMessage(),
                e
            );
        }
    }

    private @Nullable String parseThought(String output) {
        Matcher m = THOUGHT_PATTERN.matcher(output);
        return m.find() ? m.group(1).strip() : null;
    }

    private @Nullable ActionCall parseAction(String output) {
        Matcher m = ACTION_PATTERN.matcher(output);
        if (m.find()) {
            String toolName = m.group(1);
            String argsStr = m.group(2);
            String[] args = argsStr.isEmpty() ? new String[0] : argsStr.split("\\s*,\\s*");
            return new ActionCall(toolName, List.of(args));
        }
        return null;
    }

    private String executeAction(ActionCall actionCall) {
        // Find tool
        Tool tool = tools.stream()
            .filter(t -> t.name().equalsIgnoreCase(actionCall.toolName()))
            .findFirst()
            .orElse(null);

        if (tool == null) {
            return "Error: Unknown tool '" + actionCall.toolName() + "'";
        }

        try {
            return tool.execute(actionCall.args());
        } catch (Exception e) {
            return "Error executing " + actionCall.toolName() + ": " + e.getMessage();
        }
    }

    @Override
    public CompiledModule compile() {
        String optimizedPrompt = buildReActPrompt(Map.of(), null);
        int estimatedTokens = llm.countTokens(optimizedPrompt);

        return new CompiledModule.Single(
            signature,
            optimizedPrompt,
            List.of(),
            estimatedTokens
        );
    }

    /**
     * A single reasoning step in the ReAct trace.
     */
    public record Step(
        @Nullable String thought,
        String action,
        List<String> actionArgs,
        String observation
    ) {
        public Step {
            actionArgs = actionArgs != null ? List.copyOf(actionArgs) : List.of();
        }
    }

    private record ActionCall(String toolName, List<String> args) {}
}
