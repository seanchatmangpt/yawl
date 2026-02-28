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

package org.yawlfoundation.yawl.dspy.persistence;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Configuration of a single DSPy predictor from a saved program.
 *
 * <p>Each predictor contains:</p>
 * <ul>
 *   <li>Signature with instructions, input/output fields</li>
 *   <li>Learned few-shot examples from optimization</li>
 *   <li>Optimized prompt instructions</li>
 * </ul>
 *
 * @param signature          the DSPy signature configuration
 * @param fewShotExamples    few-shot examples from BootstrapFewShot optimization
 * @param learnedInstructions optimized prompt instructions (may be null)
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record DspyPredictorConfig(
        @JsonProperty("signature")
        DspySignatureConfig signature,

        @JsonProperty("demos")
        List<Map<String, Object>> fewShotExamples,

        @JsonProperty("learned_instructions")
        @Nullable String learnedInstructions
) {

    /**
     * Returns the instruction text for this predictor.
     *
     * <p>Prefers learned instructions from optimization, falls back to
     * signature instructions.</p>
     *
     * @return instruction text
     * @throws IllegalStateException if no instructions are available
     */
    public String getEffectiveInstructions() {
        if (learnedInstructions != null && !learnedInstructions.isBlank()) {
            return learnedInstructions;
        }
        if (signature != null && signature.instructions() != null) {
            return signature.instructions();
        }
        throw new IllegalStateException(
            "No instructions available for predictor. " +
            "Ensure DSPy program was properly serialized with signature instructions."
        );
    }

    /**
     * Returns the input field names from the signature.
     *
     * @return list of input field names
     * @throws IllegalStateException if signature is not configured
     */
    public List<String> getInputFieldNames() {
        if (signature == null) {
            throw new IllegalStateException(
                "Signature not configured. Ensure predictor was properly deserialized."
            );
        }
        if (signature.inputFields() == null) {
            return Collections.unmodifiableList(new ArrayList<>());
        }
        return signature.inputFields().stream()
                .map(DspyFieldConfig::name)
                .toList();
    }

    /**
     * Returns the output field names from the signature.
     *
     * @return list of output field names
     * @throws IllegalStateException if signature is not configured
     */
    public List<String> getOutputFieldNames() {
        if (signature == null) {
            throw new IllegalStateException(
                "Signature not configured. Ensure predictor was properly deserialized."
            );
        }
        if (signature.outputFields() == null) {
            return Collections.unmodifiableList(new ArrayList<>());
        }
        return signature.outputFields().stream()
                .map(DspyFieldConfig::name)
                .toList();
    }

    /**
     * Returns the number of few-shot examples from BootstrapFewShot optimization.
     *
     * @return example count (0 if none)
     */
    public int fewShotExampleCount() {
        return fewShotExamples != null ? fewShotExamples.size() : 0;
    }
}
