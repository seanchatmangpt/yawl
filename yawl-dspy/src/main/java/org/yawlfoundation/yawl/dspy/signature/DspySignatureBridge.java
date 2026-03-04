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

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Bridges Java Signatures to Python DSPy programs.
 *
 * <p>Generates Python DSPy code from Java Signature definitions and
 * parses Python DSPy output back to Java SignatureResult.
 *
 * <h2>Usage with Groq:</h2>
 * {@snippet :
 * // Create bridge with Groq model
 * var bridge = DspySignatureBridge.withGroq("gpt-oss-20b");
 *
 * // Define signature
 * var sig = Signature.builder()
 *     .description("Predict case outcome")
 *     .input("events", "case events", String.class)
 *     .output("outcome", "predicted outcome", String.class)
 *     .build();
 *
 * // Execute
 * var result = bridge.execute(sig, Map.of("events", eventLog));
 * }
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class DspySignatureBridge {

    private static final Logger log = LoggerFactory.getLogger(DspySignatureBridge.class);
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-z_][a-z0-9_]*$");

    private final String model;
    private final @Nullable String apiKey;
    private final boolean useCache;

    private DspySignatureBridge(Builder builder) {
        this.model = builder.model != null ? builder.model : "groq/gpt-oss-20b";
        this.apiKey = builder.apiKey;
        this.useCache = builder.useCache;
    }

    /**
     * Create bridge with default Groq model (gpt-oss-20b).
     */
    public static DspySignatureBridge withGroq() {
        return builder().model("groq/gpt-oss-20b").build();
    }

    /**
     * Create bridge with specific Groq model.
     */
    public static DspySignatureBridge withGroq(String model) {
        return builder().model("groq/" + model).build();
    }

    /**
     * Generate Python DSPy program source from a Java Signature.
     */
    public String generatePythonSource(Signature signature, List<Example> examples) {
        StringBuilder sb = new StringBuilder();

        // DSPy import and LM configuration
        sb.append("import dspy\n\n");
        sb.append("# Configure Groq LM\n");
        sb.append("lm = dspy.LM(\n");
        sb.append("    model=\"").append(model).append("\",\n");
        sb.append("    api_key=").append(apiKey != null ? "\"" + apiKey + "\"" : "None");
        sb.append("  # pulled from GROQ_API_KEY env if None\n");
        sb.append(")\n");
        sb.append("dspy.configure(lm=lm)\n\n");

        // Generate signature
        sb.append(generateSignatureClass(signature));
        sb.append("\n");

        // Generate module
        sb.append(generateModuleClass(signature, examples));

        return sb.toString();
    }

    private String generateSignatureClass(Signature sig) {
        StringBuilder sb = new StringBuilder();

        sb.append("# Signature: ").append(sig.description()).append("\n");
        sb.append("class ").append(toClassName(sig)).append("(dspy.Signature):\n");
        sb.append("    \"\"\"").append(sig.description()).append("\"\"\"\n\n");

        // Input fields
        for (InputField in : sig.inputs()) {
            sb.append("    ").append(in.name()).append(" = dspy.InputField(desc=\"")
                .append(escapePython(in.description())).append("\"");
            if (in.optional()) {
                sb.append(", optional=True");
            }
            sb.append(")\n");
        }

        sb.append("\n");

        // Output fields
        for (OutputField out : sig.outputs()) {
            sb.append("    ").append(out.name()).append(" = dspy.OutputField(desc=\"")
                .append(escapePython(out.description())).append("\"");
            if (out.reasoning()) {
                sb.append(", prefix=\"Reasoning: Let's think step by step.\"");
            }
            sb.append(")\n");
        }

        return sb.toString();
    }

    private String generateModuleClass(Signature sig, List<Example> examples) {
        StringBuilder sb = new StringBuilder();
        String className = toClassName(sig);
        String moduleName = className + "Module";

        sb.append("class ").append(moduleName).append("(dspy.Module):\n");
        sb.append("    def __init__(self):\n");
        sb.append("        super().__init__()\n");
        sb.append("        self.predict = dspy.Predict(").append(className).append(")\n\n");

        sb.append("    def forward(self");
        for (InputField in : sig.inputs()) {
            sb.append(", ").append(in.name());
        }
        sb.append("):\n");
        sb.append("        return self.predict(");
        sb.append(String.join(", ", sig.inputs().stream().map(InputField::name).toList()));
        sb.append(")\n\n");

        // Create instance
        sb.append("# Module instance\n");
        sb.append("_module = ").append(moduleName).append("()\n");

        // Add examples if provided
        if (!examples.isEmpty()) {
            sb.append("\n# Few-shot examples\n");
            sb.append("_module.predict.demos = [\n");
            for (Example ex : examples) {
                sb.append("    dspy.Example(");
                sb.append(formatPythonKwargs(ex.inputs()));
                sb.append(", ").append(formatPythonKwargs(ex.outputs()));
                sb.append(").with_inputs(");
                sb.append(String.join(", ", ex.inputs().keySet().stream()
                    .map(k -> "\"" + k + "\"").toList()));
                sb.append("),\n");
            }
            sb.append("]\n");
        }

        return sb.toString();
    }

    /**
     * Generate Python code to execute the signature with given inputs.
     */
    public String generateExecutionCode(Signature signature, Map<String, Object> inputs) {
        StringBuilder sb = new StringBuilder();

        sb.append("# Execute\n");
        sb.append("result = _module(");
        sb.append(formatPythonKwargs(inputs));
        sb.append(")\n\n");

        sb.append("# Extract outputs as dict\n");
        sb.append("import json\n");
        sb.append("output = {\n");
        for (OutputField out : signature.outputs()) {
            sb.append("    \"").append(out.name()).append("\": getattr(result, \"")
                .append(out.name()).append("\", None),\n");
        }
        sb.append("}\n");
        sb.append("print(json.dumps(output))\n");

        return sb.toString();
    }

    /**
     * Parse Python JSON output to SignatureResult.
     */
    public SignatureResult parseOutput(String jsonOutput, Signature signature) {
        return SignatureResultParser.parse(signature, jsonOutput);
    }

    /**
     * Get the full Python program for execution.
     */
    public String generateFullProgram(Signature signature, List<Example> examples, Map<String, Object> inputs) {
        StringBuilder sb = new StringBuilder();
        sb.append(generatePythonSource(signature, examples));
        sb.append("\n");
        sb.append(generateExecutionCode(signature, inputs));
        return sb.toString();
    }

    private String toClassName(Signature sig) {
        // Convert description to class name
        String name = sig.description()
            .replaceAll("[^a-zA-Z0-9]+", "_")
            .replaceAll("^_|_$", "");
        if (name.isEmpty()) name = "Signature";

        // Capitalize first letter of each word
        StringBuilder className = new StringBuilder();
        for (String part : name.split("_")) {
            if (!part.isEmpty()) {
                className.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1).toLowerCase());
            }
        }
        return className.toString();
    }

    private String escapePython(String s) {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
    }

    private String formatPythonKwargs(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (var entry : map.entrySet()) {
            if (!first) sb.append(", ");
            sb.append(entry.getKey()).append("=");
            sb.append(formatPythonValue(entry.getValue()));
            first = false;
        }
        return sb.toString();
    }

    private String formatPythonValue(Object value) {
        if (value == null) return "None";
        if (value instanceof String s) {
            return "\"" + escapePython(s) + "\"";
        }
        if (value instanceof Boolean b) {
            return b ? "True" : "False";
        }
        if (value instanceof Number) {
            return value.toString();
        }
        if (value instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(", ");
                sb.append(formatPythonValue(item));
                first = false;
            }
            sb.append("]");
            return sb.toString();
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (var entry : map.entrySet()) {
                if (!first) sb.append(", ");
                sb.append(formatPythonValue(entry.getKey())).append(": ");
                sb.append(formatPythonValue(entry.getValue()));
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }
        // Fallback: convert to string
        return "\"" + escapePython(value.toString()) + "\"";
    }

    // ── Builder ─────────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String model = "groq/gpt-oss-20b";
        private @Nullable String apiKey;
        private boolean useCache = true;

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder useCache(boolean useCache) {
            this.useCache = useCache;
            return this;
        }

        public DspySignatureBridge build() {
            return new DspySignatureBridge(this);
        }
    }
}
