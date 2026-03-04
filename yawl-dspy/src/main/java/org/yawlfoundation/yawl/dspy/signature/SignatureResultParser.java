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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses LLM output into structured SignatureResult.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
final class SignatureResultParser {

    // Pattern: field_name: value (handles multiline until next field)
    private static final Pattern FIELD_PATTERN = Pattern.compile(
        "^([a-z_][a-z0-9_]*)\\s*:\\s*(.+?)(?=\\n[a-z_][a-z0-9_]*\\s*:|\\n*$)",
        Pattern.MULTILINE | Pattern.DOTALL
    );

    // Pattern: JSON object
    private static final Pattern JSON_PATTERN = Pattern.compile(
        "^\\s*\\{[\\s\\S]*\\}\\s*$"
    );

    private SignatureResultParser() {}

    static SignatureResult parse(Signature signature, String llmOutput) {
        if (llmOutput == null || llmOutput.isBlank()) {
            return SignatureResult.empty(signature, llmOutput);
        }

        String cleaned = llmOutput.strip();

        // Try JSON first
        if (JSON_PATTERN.matcher(cleaned).matches()) {
            return parseJson(signature, cleaned);
        }

        // Try field-per-line format
        return parseFieldPerLine(signature, cleaned);
    }

    private static SignatureResult parseFieldPerLine(Signature signature, String output) {
        Map<String, Object> values = new LinkedHashMap<>();
        List<OutputField> expectedFields = signature.outputs();

        Matcher matcher = FIELD_PATTERN.matcher(output);
        while (matcher.find()) {
            String fieldName = matcher.group(1);
            String rawValue = matcher.group(2).strip();

            OutputField field = expectedFields.stream()
                .filter(f -> f.name().equals(fieldName))
                .findFirst()
                .orElse(null);

            if (field != null) {
                Object parsed = parseValue(rawValue, field.type());
                values.put(fieldName, parsed);
            } else {
                values.put(fieldName, rawValue);
            }
        }

        return new SignatureResult(values, output, signature);
    }

    private static SignatureResult parseJson(Signature signature, String json) {
        Map<String, Object> values = new LinkedHashMap<>();

        // Simple JSON parsing (production should use Jackson)
        Pattern jsonField = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"?([^,\"\\}]+)\"?");
        Matcher matcher = jsonField.matcher(json);

        while (matcher.find()) {
            String key = matcher.group(1);
            String rawValue = matcher.group(2).strip().replace("\"", "");

            OutputField field = signature.outputs().stream()
                .filter(f -> f.name().equals(key))
                .findFirst()
                .orElse(null);

            if (field != null) {
                values.put(key, parseValue(rawValue, field.type()));
            }
        }

        return new SignatureResult(values, json, signature);
    }

    private static Object parseValue(String raw, Class<?> type) {
        if (raw == null || raw.isBlank()) return null;

        String trimmed = raw.strip();

        if (type == String.class) {
            return trimmed;
        }
        if (type == Integer.class || type == int.class) {
            return Integer.parseInt(trimmed.replaceAll("[^0-9-]", ""));
        }
        if (type == Long.class || type == long.class) {
            return Long.parseLong(trimmed.replaceAll("[^0-9-]", ""));
        }
        if (type == Double.class || type == double.class) {
            return Double.parseDouble(trimmed.replaceAll("[^0-9.-]", ""));
        }
        if (type == Boolean.class || type == boolean.class) {
            return parseBoolean(trimmed);
        }
        if (type == List.class) {
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                trimmed = trimmed.substring(1, trimmed.length() - 1);
            }
            return List.of(trimmed.split("\\s*[,\\n]\\s*"));
        }

        return trimmed;
    }

    private static Boolean parseBoolean(String s) {
        return switch (s.toLowerCase()) {
            case "true", "yes", "1", "on" -> true;
            case "false", "no", "0", "off" -> false;
            default -> Boolean.parseBoolean(s);
        };
    }
}
