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

package org.yawlfoundation.yawl.ggen.pipeline;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.dspy.fluent.Dspy;
import org.yawlfoundation.yawl.erlang.processmining.ProcessMining;
import org.yawlfoundation.yawl.ggen.model.ProcessSpec;
import org.yawlfoundation.yawl.ggen.model.ProcessGraph;
import org.yawlfoundation.yawl.ggen.model.YawlSpec;
import org.yawlfoundation.yawl.ggen.model.ValidationResult;
import org.yawlfoundation.yawl.ggen.validation.YawlValidator;

import java.util.Map;
import java.util.Objects;

/**
 * Main entry point for YAWL process model generation.
 *
 * <p>Orchestrates the 3-stage pipeline:
 * <ol>
 *   <li>Stage 1: NL → ProcessSpec (Chain-of-Thought)</li>
 *   <li>Stage 2: ProcessSpec → ProcessGraph (Chain-of-Thought)</li>
 *   <li>Stage 3: ProcessGraph → YAWL XML (Predict)</li>
 * </ol>
 *
 * <h2>Quick Start:</h2>
 * <pre>{@code
 * // Configure DSPy
 * Dspy.configureGroq();
 *
 * // Create generator
 * YawlGenerator gen = YawlGenerator.create();
 *
 * // Generate YAWL from natural language
 * YawlSpec spec = gen.generate("""
 *     Patient admission process:
 *     1. Triage assessment (OR-join from multiple entry points)
 *     2. Registration in parallel with insurance verification
 *     3. Bed assignment (cancellation region if emergency)
 *     """);
 *
 * // Validate (optional)
 * ValidationResult validation = gen.validate(spec);
 *
 * if (validation.valid()) {
 *     // Load into YEngine
 *     YEngine.getInstance().loadSpecification(spec.yawlXml());
 * }
 * }</pre>
 *
 * <h2>With ProcessMining (rust4pm validation):</h2>
 * <pre>{@code
 * try (ProcessMining pm = ProcessMining.connect("yawl_erl@localhost", "secret")) {
 *     YawlGenerator gen = YawlGenerator.create(pm);
 *
 *     YawlSpec spec = gen.generate(nlDescription)
 *         .withDomainContext("healthcare")
 *         .withSpecId("PatientAdmission")
 *         .execute();
 *
 *     ValidationResult result = gen.validate(spec);
 * }
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class YawlGenerator {

    private static final Logger log = LoggerFactory.getLogger(YawlGenerator.class);

    private final Stage1SpecGenerator stage1;
    private final Stage2GraphBuilder stage2;
    private final Stage3YawlRenderer stage3;
    private final @Nullable YawlValidator validator;

    private YawlGenerator(@Nullable ProcessMining processMining) {
        this.stage1 = new Stage1SpecGenerator();
        this.stage2 = new Stage2GraphBuilder();
        this.stage3 = new Stage3YawlRenderer();
        this.validator = processMining != null ? new YawlValidator(processMining) : null;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FACTORY METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Create a YawlGenerator without rust4pm validation.
     */
    public static YawlGenerator create() {
        return new YawlGenerator(null);
    }

    /**
     * Create a YawlGenerator with rust4pm validation.
     *
     * <p>Uses existing JOR4J infrastructure from DSPy thesis.
     */
    public static YawlGenerator create(ProcessMining processMining) {
        Objects.requireNonNull(processMining, "processMining is required");
        return new YawlGenerator(processMining);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 3-STAGE PIPELINE
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Generate YAWL specification from natural language.
     *
     * @param nlDescription Natural language process description
     * @return Generated YawlSpec
     */
    public YawlSpec generate(String nlDescription) {
        return generate(nlDescription, "", "GeneratedSpec", "1.0");
    }

    /**
     * Generate YAWL specification with all options.
     *
     * @param nlDescription Natural language process description
     * @param domainContext Domain context (e.g., "healthcare", "finance")
     * @param specId YAWL specification ID
     * @param version Specification version
     * @return Generated YawlSpec
     */
    public YawlSpec generate(String nlDescription, String domainContext,
                             String specId, String version) {
        log.info("Starting 3-stage pipeline for: {}", specId);
        long startTime = System.currentTimeMillis();

        // Stage 1: NL → ProcessSpec
        log.debug("Stage 1: NL → ProcessSpec");
        ProcessSpec spec = stage1.generate(nlDescription, domainContext);
        log.debug("Stage 1 complete: {} tasks, {} OR-joins",
            spec.taskCount(), spec.orJoins().size());

        // Stage 2: ProcessSpec → ProcessGraph
        log.debug("Stage 2: ProcessSpec → ProcessGraph");
        ProcessGraph graph = stage2.build(spec);
        log.debug("Stage 2 complete: {} nodes, {} edges",
            graph.nodeCount(), graph.edgeCount());

        // Stage 3: ProcessGraph → YAWL XML
        log.debug("Stage 3: ProcessGraph → YAWL XML");
        YawlSpec yawlSpec = stage3.render(graph, specId, version);

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("Pipeline complete in {}ms", totalTime);

        return yawlSpec;
    }

    /**
     * Start fluent generation builder.
     */
    public GenerationBuilder generateWith(String nlDescription) {
        return new GenerationBuilder(this, nlDescription);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // VALIDATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Validate YAWL specification.
     *
     * <p>Runs multi-layer validation:
     * <ol>
     *   <li>XSD schema validation</li>
     *   <li>rust4pm soundness check (deadlock, lack of sync)</li>
     *   <li>Virtual execution simulation</li>
     * </ol>
     *
     * @param spec YawlSpec to validate
     * @return ValidationResult
     */
    public ValidationResult validate(YawlSpec spec) {
        if (validator == null) {
            throw new IllegalStateException(
                "Validator not available. Create YawlGenerator with ProcessMining.");
        }
        return validator.validate(spec);
    }

    /**
     * Check if validator is available.
     */
    public boolean hasValidator() {
        return validator != null;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INDIVIDUAL STAGE ACCESS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get Stage 1 generator (for standalone use).
     */
    public Stage1SpecGenerator stage1() {
        return stage1;
    }

    /**
     * Get Stage 2 builder (for standalone use).
     */
    public Stage2GraphBuilder stage2() {
        return stage2;
    }

    /**
     * Get Stage 3 renderer (for standalone use).
     */
    public Stage3YawlRenderer stage3() {
        return stage3;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FLUENT BUILDER
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Fluent builder for YAWL generation.
     */
    public static final class GenerationBuilder {
        private final YawlGenerator generator;
        private final String nlDescription;
        private String domainContext = "";
        private String specId = "GeneratedSpec";
        private String version = "1.0";
        private boolean validate = false;

        private GenerationBuilder(YawlGenerator generator, String nlDescription) {
            this.generator = generator;
            this.nlDescription = nlDescription;
        }

        /**
         * Set domain context.
         */
        public GenerationBuilder withDomainContext(String domainContext) {
            this.domainContext = domainContext;
            return this;
        }

        /**
         * Set specification ID.
         */
        public GenerationBuilder withSpecId(String specId) {
            this.specId = specId;
            return this;
        }

        /**
         * Set specification version.
         */
        public GenerationBuilder withVersion(String version) {
            this.version = version;
            return this;
        }

        /**
         * Enable validation after generation.
         */
        public GenerationBuilder withValidation() {
            this.validate = true;
            return this;
        }

        /**
         * Execute generation.
         */
        public GenerationResult execute() {
            YawlSpec spec = generator.generate(nlDescription, domainContext, specId, version);

            ValidationResult validation = null;
            if (validate && generator.hasValidator()) {
                validation = generator.validate(spec);
            }

            return new GenerationResult(spec, validation);
        }
    }

    /**
     * Result of generation with optional validation.
     */
    public record GenerationResult(
        YawlSpec spec,
        @Nullable ValidationResult validation
    ) {
        /**
         * Check if generation was successful.
         */
        public boolean isSuccess() {
            return validation == null || validation.valid();
        }

        /**
         * Get validation status message.
         */
        public String getValidationMessage() {
            if (validation == null) {
                return "Not validated";
            }
            return validation.valid() ? "Validation passed" : validation.getSummary();
        }
    }
}
