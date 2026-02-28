/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
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

package org.yawlfoundation.yawl.datamodelling.llm;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable request for LLM-enhanced schema refinement.
 *
 * <p>Encapsulates all inputs needed for the refinement phase, including:
 * schema to refine, sample data, context documentation, and refinement objectives.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record LlmRefinementRequest(
        /** The ODCS schema to refine (JSON or YAML). */
        String schema,
        /** Sample data records for context. */
        String[] samples,
        /** Refinement objectives (e.g., "improve naming", "detect constraints"). */
        String[] objectives,
        /** Optional documentation or context for the LLM. */
        String context,
        /** LLM configuration for this refinement request. */
        LlmConfig config) {

    /**
     * Constructs an LlmRefinementRequest.
     *
     * @param schema     the schema to refine; must not be null or blank
     * @param samples    sample data records; may be empty
     * @param objectives refinement objectives; may be empty
     * @param context    optional context documentation; may be null
     * @param config     LLM configuration; must not be null
     * @throws IllegalArgumentException if schema is blank or config is null
     */
    public LlmRefinementRequest {
        Objects.requireNonNull(schema, "schema must not be null");
        if (schema.isBlank()) {
            throw new IllegalArgumentException("schema must not be blank");
        }
        Objects.requireNonNull(config, "config must not be null");
        if (samples == null) {
            samples = new String[0];
        }
        if (objectives == null) {
            objectives = new String[0];
        }
    }

    /**
     * Creates a builder for LlmRefinementRequest.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for LlmRefinementRequest.
     */
    public static final class Builder {
        private String schema;
        private String[] samples = new String[0];
        private String[] objectives = new String[0];
        private String context;
        private LlmConfig config;

        public Builder schema(String schema) {
            this.schema = Objects.requireNonNull(schema, "schema must not be null");
            return this;
        }

        public Builder samples(String... samples) {
            this.samples = samples != null ? samples : new String[0];
            return this;
        }

        public Builder objectives(String... objectives) {
            this.objectives = objectives != null ? objectives : new String[0];
            return this;
        }

        public Builder context(String context) {
            this.context = context;
            return this;
        }

        public Builder config(LlmConfig config) {
            this.config = Objects.requireNonNull(config, "config must not be null");
            return this;
        }

        public LlmRefinementRequest build() {
            return new LlmRefinementRequest(schema, samples, objectives, context, config);
        }
    }

    @Override
    public String toString() {
        return "LlmRefinementRequest{"
                + "schemaLength=" + schema.length()
                + ", samples=" + Arrays.length(samples)
                + ", objectives=" + Arrays.length(objectives)
                + ", context=" + (context != null ? "[set]" : "null")
                + ", config=" + config
                + '}';
    }
}
