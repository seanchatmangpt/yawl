/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 */

package org.yawlfoundation.yawl.ml.dspy;

import java.util.Collections;
import java.util.Map;

/**
 * Few-shot example for DSPy programs.
 *
 * @param inputs example input values
 * @param outputs example output values
 */
public record Example(Map<String, Object> inputs, Map<String, Object> outputs) {

    /**
     * Create an example with single input/output.
     */
    public static Example of(String inputKey, Object inputValue,
                             String outputKey, Object outputValue) {
        return new Example(
            Map.of(inputKey, inputValue),
            Map.of(outputKey, outputValue)
        );
    }

    /**
     * Create an example from maps.
     */
    public static Example of(Map<String, Object> inputs, Map<String, Object> outputs) {
        return new Example(
            Collections.unmodifiableMap(inputs),
            Collections.unmodifiableMap(outputs)
        );
    }
}
