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

import org.yawlfoundation.yawl.dspy.signature.Signature;

import java.util.List;
import java.util.Map;

/**
 * A compiled, optimized DSPy module ready for efficient inference.
 *
 * <p>Compiled modules are the result of optimization by teleprompters.
 * They contain:
 * <ul>
 *   <li>Optimized prompts with few-shot examples</li>
 *   <li>Cached token counts and other metadata</li>
 *   <li>Potentially tuned hyperparameters</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public sealed interface CompiledModule permits CompiledModule.Single, CompiledModule.Pipeline, CompiledModule.ParallelCompiled {

    /**
     * Get the signature this module implements.
     */
    Signature signature();

    /**
     * Generate the optimized prompt for this module.
     */
    String generatePrompt(Map<String, Object> inputs);

    /**
     * Get the estimated token count for this module's prompt.
     */
    int estimatedTokens();

    /**
     * A single compiled module with optimized prompt.
     */
    record Single(
        Signature signature,
        String optimizedPrompt,
        List<Map<String, Object>> fewShotExamples,
        int estimatedTokens
    ) implements CompiledModule {

        public Single {
            fewShotExamples = fewShotExamples != null ? List.copyOf(fewShotExamples) : List.of();
        }

        @Override
        public String generatePrompt(Map<String, Object> inputs) {
            StringBuilder sb = new StringBuilder(optimizedPrompt);

            if (!fewShotExamples.isEmpty()) {
                sb.append("\n\n# Examples\n");
                for (var ex : fewShotExamples) {
                    sb.append("\n## Example\n");
                    ex.forEach((k, v) -> sb.append(k).append(": ").append(v).append("\n"));
                }
            }

            sb.append("\n\n# Input\n");
            inputs.forEach((k, v) -> sb.append(k).append(": ").append(v).append("\n"));

            return sb.toString();
        }
    }

    /**
     * A pipeline of compiled modules.
     */
    record Pipeline(
        Signature signature,
        List<CompiledModule> stages
    ) implements CompiledModule {

        public Pipeline {
            stages = List.copyOf(stages);
        }

        @Override
        public String generatePrompt(Map<String, Object> inputs) {
            return stages.getFirst().generatePrompt(inputs);
        }

        @Override
        public int estimatedTokens() {
            return stages.stream().mapToInt(CompiledModule::estimatedTokens).sum();
        }
    }

    /**
     * Parallel composition of compiled modules.
     */
    record ParallelCompiled(
        Signature signature,
        List<CompiledModule> modules
    ) implements CompiledModule {

        public ParallelCompiled {
            modules = List.copyOf(modules);
        }

        @Override
        public String generatePrompt(Map<String, Object> inputs) {
            StringBuilder sb = new StringBuilder();
            sb.append("# Parallel Execution\n\n");
            for (int i = 0; i < modules.size(); i++) {
                sb.append("## Module ").append(i + 1).append("\n");
                sb.append(modules.get(i).generatePrompt(inputs)).append("\n\n");
            }
            return sb.toString();
        }

        @Override
        public int estimatedTokens() {
            return modules.stream().mapToInt(CompiledModule::estimatedTokens).max().orElse(0);
        }
    }
}
