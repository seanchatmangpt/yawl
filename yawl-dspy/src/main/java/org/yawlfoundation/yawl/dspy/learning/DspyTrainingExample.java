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

package org.yawlfoundation.yawl.dspy.learning;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable training example for DSPy BootstrapFewShot compilation.
 *
 * <p>Represents an input-output pair extracted from historical work items,
 * used to train DSPy programs via bootstrap compilation. Each example maps
 * a natural language workflow description to a compiled POWL JSON output.</p>
 *
 * @param input      Natural language workflow description (from YWorkItem metadata)
 * @param output     Compiled POWL JSON representation (expected or achieved output)
 * @param metadata   Additional metadata about the training example source
 * @param qualityScore Quality score between 0.0 and 1.0 for GEPA optimization
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record DspyTrainingExample(
        @JsonProperty("input")
        String input,

        @JsonProperty("output")
        Map<String, Object> output,

        @JsonProperty("metadata")
        Map<String, Object> metadata,

        @JsonProperty("qualityScore")
        double qualityScore
) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Legacy alias for the input field.
     *
     * @return The workflow description/id
     */
    public String id() {
        return input;
    }

    /**
     * Accessor for behavioral features.
     *
     * @return Map containing behavioral metrics
     */
    public Map<String, Object> behavioralFeatures() {
        return Collections.unmodifiableMap(output);
    }

    /**
     * Accessor for performance metrics.
     *
     * @return Map containing performance metrics
     */
    public Map<String, Object> performanceMetrics() {
        return Collections.unmodifiableMap(metadata);
    }

    /**
     * Legacy alias for the qualityScore field.
     *
     * @return The footprint score
     */
    public double footprintScore() {
        return qualityScore;
    }

    /**
     * Compact constructor with validation.
     */
    public DspyTrainingExample {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(output, "output must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");

        if (input.isBlank()) {
            throw new IllegalArgumentException("input must not be blank");
        }
        if (output.isEmpty()) {
            throw new IllegalArgumentException("output must not be empty");
        }
        if (metadata.isEmpty()) {
            throw new IllegalArgumentException("metadata must not be empty");
        }
        if (qualityScore < 0.0 || qualityScore > 1.0) {
            throw new IllegalArgumentException("qualityScore must be between 0.0 and 1.0, got: " + qualityScore);
        }
    }

    /**
     * Legacy constructor for backward compatibility.
     *
     * @deprecated Use the full 4-parameter constructor instead
     */
    @Deprecated
    public DspyTrainingExample(String input, Map<String, Object> output) {
        this(input, output, Map.of(), 0.0);
    }
}
