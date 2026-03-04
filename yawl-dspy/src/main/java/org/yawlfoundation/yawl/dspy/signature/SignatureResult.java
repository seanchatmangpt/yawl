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

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Immutable result of parsing LLM output against a signature.
 *
 * @param values     parsed field values
 * @param rawOutput  original LLM output
 * @param signature  the signature used for parsing
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record SignatureResult(
    Map<String, Object> values,
    String rawOutput,
    Signature signature
) {

    public SignatureResult {
        if (values == null) values = Map.of();
        if (rawOutput == null) rawOutput = "";
        values = Map.copyOf(values);
    }

    /**
     * Get a field value by name.
     *
     * @throws NoSuchElementException if field not present
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String fieldName) {
        if (!values.containsKey(fieldName)) {
            throw new NoSuchElementException("Field not found: " + fieldName);
        }
        return (T) values.get(fieldName);
    }

    /**
     * Get a field value, returning Optional.
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getOptional(String fieldName) {
        return Optional.ofNullable((T) values.get(fieldName));
    }

    /**
     * Get a string field.
     */
    public String getString(String fieldName) {
        return get(fieldName);
    }

    /**
     * Get an integer field.
     */
    public int getInt(String fieldName) {
        Object val = get(fieldName);
        if (val instanceof Number n) return n.intValue();
        return Integer.parseInt(val.toString());
    }

    /**
     * Get a double field.
     */
    public double getDouble(String fieldName) {
        Object val = get(fieldName);
        if (val instanceof Number n) return n.doubleValue();
        return Double.parseDouble(val.toString());
    }

    /**
     * Get a boolean field.
     */
    public boolean getBoolean(String fieldName) {
        Object val = get(fieldName);
        if (val instanceof Boolean b) return b;
        return Boolean.parseBoolean(val.toString());
    }

    /**
     * Check if all required output fields are present.
     */
    public boolean isComplete() {
        return signature.outputs().stream()
            .allMatch(out -> values.containsKey(out.name()));
    }

    /**
     * Get missing output field names.
     */
    public java.util.Set<String> missingFields() {
        return signature.outputs().stream()
            .map(OutputField::name)
            .filter(name -> !values.containsKey(name))
            .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Empty result for failed parsing.
     */
    public static SignatureResult empty(Signature signature, String rawOutput) {
        return new SignatureResult(Map.of(), rawOutput, signature);
    }
}
