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

package org.yawlfoundation.yawl.dspy.signature;

import java.util.List;
import java.util.Map;

/**
 * DSPy-style signature for Java 25 - declares input/output contract of an LLM call.
 *
 * <p>Signatures separate WHAT you want from HOW to prompt for it. The DSPy runtime
 * generates optimized prompts from signatures, enabling:
 * <ul>
 *   <li>Automatic prompt optimization via teleprompters</li>
 *   <li>Compilation to efficient inference programs</li>
 *   <li>Type-safe LLM integration with MCP/A2A</li>
 * </ul>
 *
 * <h2>Example:</h2>
 * {@snippet :
 * // Define signature
 * var sig = Signature.builder()
 *     .description("Predict workflow case outcome")
 *     .input("caseEvents", "list of case events", List.class)
 *     .input("caseDuration", "duration in ms", Long.class)
 *     .output("outcome", "predicted outcome: completed or failed", String.class)
 *     .output("confidence", "confidence score 0-1", Double.class)
 *     .build();
 *
 * // Use with DSPy module
 * var predictor = new Predict<>(sig, llmClient);
 * var result = predictor.run(Map.of(
 *     "caseEvents", events,
 *     "caseDuration", 15000L
 * ));
 * }
 *
 * @param inputs  input fields - what the LLM receives
 * @param outputs output fields - what the LLM produces
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see Predict
 * @see ChainOfThought
 * @see SignatureTemplate
 */
public sealed interface Signature permits Signature.Impl {

    /**
     * Human-readable description of what this signature does.
     * Becomes the task description in the generated prompt.
     */
    String description();

    /**
     * Input fields - what the LLM receives.
     */
    List<InputField> inputs();

    /**
     * Output fields - what the LLM produces.
     */
    List<OutputField> outputs();

    /**
     * Instructions appended to the prompt (optional).
     */
    String instructions();

    /**
     * Examples for few-shot prompting (optional).
     */
    List<Example> examples();

    /**
     * Generate the full prompt for this signature with inputs.
     */
    String toPrompt(Map<String, Object> inputValues);

    /**
     * Parse LLM output into structured result.
     */
    SignatureResult parse(String llmOutput);

    /**
     * Get the signature's unique identifier (for caching).
     */
    default String id() {
        return "%s(%s)->(%s)".formatted(
            description().replaceAll("\\s+", "_").toLowerCase(),
            String.join(",", inputs().stream().map(InputField::name).toList()),
            String.join(",", outputs().stream().map(OutputField::name).toList())
        );
    }

    // ── Factory methods ──────────────────────────────────────────────────────

    static Signature of(String description, InputField input, OutputField output) {
        return builder().description(description).input(input).output(output).build();
    }

    static Signature of(String description, List<InputField> inputs, List<OutputField> outputs) {
        return new Impl(description, inputs, outputs, "", List.of());
    }

    static Builder builder() {
        return new Builder();
    }

    static Signature fromTemplate(Class<? extends SignatureTemplate> templateClass) {
        return SignatureCompiler.compile(templateClass);
    }

    // ── Implementation ──────────────────────────────────────────────────────

    record Impl(
        String description,
        List<InputField> inputs,
        List<OutputField> outputs,
        String instructions,
        List<Example> examples
    ) implements Signature {

        public Impl {
            if (description == null || description.isBlank()) {
                throw new IllegalArgumentException("description is required");
            }
            if (inputs == null || inputs.isEmpty()) {
                throw new IllegalArgumentException("at least one input required");
            }
            if (outputs == null || outputs.isEmpty()) {
                throw new IllegalArgumentException("at least one output required");
            }
            inputs = List.copyOf(inputs);
            outputs = List.copyOf(outputs);
            examples = examples != null ? List.copyOf(examples) : List.of();
            instructions = instructions != null ? instructions : "";
        }

        @Override
        public String toPrompt(Map<String, Object> inputValues) {
            StringBuilder sb = new StringBuilder();

            // Task description
            sb.append("# Task\n").append(description()).append("\n\n");

            // Instructions (if any)
            if (!instructions.isBlank()) {
                sb.append("# Instructions\n").append(instructions).append("\n\n");
            }

            // Input/output format
            sb.append("# Format\n");
            sb.append("Given the following inputs, produce the corresponding outputs.\n\n");

            sb.append("## Inputs\n");
            for (InputField in : inputs()) {
                sb.append("- ").append(in.name()).append(": ").append(in.description());
                if (inputValues.containsKey(in.name())) {
                    sb.append("\n  Value: ").append(formatValue(inputValues.get(in.name())));
                }
                sb.append("\n");
            }

            sb.append("\n## Outputs\n");
            sb.append("Respond with EXACTLY these fields, one per line:\n");
            for (OutputField out : outputs()) {
                sb.append("- ").append(out.name()).append(": ").append(out.description()).append("\n");
            }

            // Few-shot examples
            if (!examples.isEmpty()) {
                sb.append("\n# Examples\n");
                for (Example ex : examples) {
                    sb.append("\n## Example\n");
                    sb.append("Inputs:\n");
                    ex.inputs().forEach((k, v) -> sb.append("  ").append(k).append(": ").append(v).append("\n"));
                    sb.append("Outputs:\n");
                    ex.outputs().forEach((k, v) -> sb.append("  ").append(k).append(": ").append(v).append("\n"));
                }
            }

            sb.append("\n# Response\n");
            sb.append("Provide ONLY the output fields, one per line, in the format:\n");
            sb.append("field_name: value\n");

            return sb.toString();
        }

        @Override
        public SignatureResult parse(String llmOutput) {
            return SignatureResultParser.parse(this, llmOutput);
        }

        private String formatValue(Object value) {
            if (value == null) return "null";
            if (value instanceof List<?> list) {
                return "[%d items]".formatted(list.size());
            }
            if (value instanceof String s && s.length() > 100) {
                return s.substring(0, 100) + "...";
            }
            return value.toString();
        }
    }

    // ── Builder ──────────────────────────────────────────────────────────────

    final class Builder {
        private String description;
        private final List<InputField> inputs = new java.util.ArrayList<>();
        private final List<OutputField> outputs = new java.util.ArrayList<>();
        private String instructions = "";
        private final List<Example> examples = new java.util.ArrayList<>();

        public Builder description(String desc) {
            this.description = desc;
            return this;
        }

        public Builder input(String name, String desc, Class<?> type) {
            inputs.add(InputField.of(name, desc, type));
            return this;
        }

        public Builder input(InputField field) {
            inputs.add(field);
            return this;
        }

        public Builder output(String name, String desc, Class<?> type) {
            outputs.add(OutputField.of(name, desc, type));
            return this;
        }

        public Builder output(OutputField field) {
            outputs.add(field);
            return this;
        }

        public Builder instructions(String instr) {
            this.instructions = instr;
            return this;
        }

        public Builder example(Map<String, Object> in, Map<String, Object> out) {
            examples.add(new Example(in, out));
            return this;
        }

        public Signature build() {
            return new Impl(description, inputs, outputs, instructions, examples);
        }
    }
}
