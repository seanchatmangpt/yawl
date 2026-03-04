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

import org.yawlfoundation.yawl.dspy.signature.Example;
import org.yawlfoundation.yawl.dspy.signature.Signature;
import org.yawlfoundation.yawl.dspy.signature.SignatureResult;

import java.util.List;
import java.util.Map;

/**
 * Base sealed interface for DSPy modules.
 *
 * <p>DSPy modules are the building blocks of LLM programs. Each module:
 * <ul>
 *   <li>Has a signature defining its input/output contract</li>
 *   <li>Can be composed with other modules into pipelines</li>
 *   <li>Can be optimized by teleprompters</li>
 *   <li>Can be compiled to efficient inference programs</li>
 * </ul>
 *
 * <h2>Module Composition:</h2>
 * {@snippet :
 * // Sequential pipeline
 * var pipeline = Module.sequence(
 *     new Predict<>(sig1, llm),
 *     new Predict<>(sig2, llm),
 *     new ChainOfThought<>(sig3, llm)
 * );
 *
 * // Run pipeline
 * var result = pipeline.run(inputs);
 * }
 *
 * <h2>Optimization:</h2>
 * {@snippet :
 * // Bootstrap few-shot examples
 * var optimizer = new BootstrapFewShot<>(metric, maxExamples);
 * var optimized = optimizer.compile(module, trainset);
 *
 * // Run optimized module
 * var result = optimized.run(inputs);
 * }
 *
 * @param <T> the module type for composition
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public sealed interface Module<T extends Module<T>>
    permits Predict, ChainOfThought, ReAct, Module.Sequential, Module.Parallel {

    /**
     * Get this module's signature.
     */
    Signature signature();

    /**
     * Run the module with the given inputs.
     *
     * @param inputs the input values matching the signature
     * @return the structured result
     * @throws ModuleException if execution fails
     */
    SignatureResult run(Map<String, Object> inputs);

    /**
     * Get the module's name for tracing and debugging.
     */
    default String name() {
        return signature().id();
    }

    /**
     * Get examples currently configured for this module.
     */
    List<Example> examples();

    /**
     * Return a new module with updated examples.
     */
    T withExamples(List<Example> examples);

    /**
     * Return a new module with an additional example.
     */
    default T addExample(Example example) {
        var newExamples = new java.util.ArrayList<>(examples());
        newExamples.add(example);
        return withExamples(List.copyOf(newExamples));
    }

    /**
     * Compile this module to a reusable program.
     */
    CompiledModule compile();

    // ── Composition ─────────────────────────────────────────────────────────────

    /**
     * Create a sequential pipeline of modules.
     */
    static Module<?> sequential(List<Module<?>> modules) {
        return new Sequential(modules);
    }

    /**
     * Create a parallel composition of modules.
     */
    static Module<?> parallel(List<Module<?>> modules) {
        return new Parallel(modules);
    }

    /**
     * Sequential pipeline - outputs of one module feed into the next.
     */
    record Sequential(List<Module<?>> modules) implements Module<Sequential> {

        public Sequential {
            if (modules == null || modules.isEmpty()) {
                throw new IllegalArgumentException("modules must not be empty");
            }
            modules = List.copyOf(modules);
        }

        @Override
        public Signature signature() {
            // First module's inputs, last module's outputs
            return new Signature.Impl(
                "Sequential pipeline",
                modules.getFirst().signature().inputs(),
                modules.getLast().signature().outputs(),
                "",
                List.of()
            );
        }

        @Override
        public SignatureResult run(Map<String, Object> inputs) {
            Map<String, Object> currentInputs = inputs;
            SignatureResult result = null;

            for (Module<?> module : modules) {
                result = module.run(currentInputs);
                currentInputs = result.values();
            }

            return result;
        }

        @Override
        public List<Example> examples() {
            return List.of();
        }

        @Override
        public Sequential withExamples(List<Example> examples) {
            return this; // Sequential doesn't hold examples directly
        }

        @Override
        public CompiledModule compile() {
            List<CompiledModule> compiled = modules.stream()
                .map(Module::compile)
                .toList();
            return new CompiledModule.Pipeline(signature(), compiled);
        }
    }

    /**
     * Parallel composition - modules run independently and results merged.
     */
    record Parallel(List<Module<?>> modules) implements Module<Parallel> {

        public Parallel {
            if (modules == null || modules.isEmpty()) {
                throw new IllegalArgumentException("modules must not be empty");
            }
            modules = List.copyOf(modules);
        }

        @Override
        public Signature signature() {
            // Merge all inputs and outputs
            var allInputs = modules.stream()
                .flatMap(m -> m.signature().inputs().stream())
                .toList();
            var allOutputs = modules.stream()
                .flatMap(m -> m.signature().outputs().stream())
                .toList();

            return new Signature.Impl(
                "Parallel composition",
                allInputs,
                allOutputs,
                "",
                List.of()
            );
        }

        @Override
        public SignatureResult run(Map<String, Object> inputs) {
            // Run all modules in parallel using virtual threads
            var futures = modules.stream()
                .map(module -> java.util.concurrent.CompletableFuture.supplyAsync(
                    () -> module.run(inputs),
                    java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()
                ))
                .toList();

            // Combine all results
            var combinedValues = new java.util.LinkedHashMap<String, Object>();
            for (var future : futures) {
                combinedValues.putAll(future.join().values());
            }

            return new SignatureResult(combinedValues, "", signature());
        }

        @Override
        public List<Example> examples() {
            return List.of();
        }

        @Override
        public Parallel withExamples(List<Example> examples) {
            return this;
        }

        @Override
        public CompiledModule compile() {
            List<CompiledModule> compiled = modules.stream()
                .map(Module::compile)
                .toList();
            return new CompiledModule.ParallelCompiled(signature(), compiled);
        }
    }
}
