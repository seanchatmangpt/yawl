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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a persisted DSPy program loaded from JSON.
 *
 * <p>This record captures all state from a GEPA-optimized DSPy program
 * that was serialized via {@code dspy_persistence.py}. Programs can be
 * loaded from disk and executed without recompilation.</p>
 *
 * <h2>JSON Schema</h2>
 * <pre>
 * {
 *   "name": "worklet_selector",
 *   "version": "1.0.0",
 *   "dspy_version": "2.5.0",
 *   "predictors": { ... },
 *   "metadata": { ... },
 *   "source_hash": "sha256:...",
 *   "serialized_at": "2026-02-27T10:00:00Z"
 * }
 * </pre>
 *
 * @param name           program identifier (e.g., "worklet_selector")
 * @param version        serialization format version
 * @param dspyVersion    DSPy library version used for serialization
 * @param sourceHash     SHA-256 hash of program state
 * @param predictors     map of predictor name to configuration
 * @param metadata       optimization metadata (optimizer, scores, etc.)
 * @param serializedAt   timestamp when program was saved
 * @param loadedAt       timestamp when program was loaded into memory
 * @param sourcePath     path to the JSON file (for reload)
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record DspySavedProgram(
        @JsonProperty("name")
        String name,

        @JsonProperty("version")
        String version,

        @JsonProperty("dspy_version")
        @Nullable String dspyVersion,

        @JsonProperty("source_hash")
        String sourceHash,

        @JsonProperty("predictors")
        Map<String, DspyPredictorConfig> predictors,

        @JsonProperty("metadata")
        Map<String, Object> metadata,

        @JsonProperty("serialized_at")
        @Nullable String serializedAt,

        Instant loadedAt,

        @Nullable Path sourcePath
) {

    private static final Logger log = LoggerFactory.getLogger(DspySavedProgram.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Compact constructor with validation.
     */
    public DspySavedProgram {
        Objects.requireNonNull(name, "Program name must not be null");
        Objects.requireNonNull(version, "Program version must not be null");
        Objects.requireNonNull(sourceHash, "Source hash must not be null");
        Objects.requireNonNull(loadedAt, "Loaded timestamp must not be null");

        if (name.isBlank()) {
            throw new IllegalArgumentException("Program name must not be blank");
        }
        predictors = predictors != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(predictors))
                : Collections.emptyMap();
        metadata = metadata != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(metadata))
                : Collections.emptyMap();
    }

    /**
     * Loads a DSPy program from a JSON file.
     *
     * @param path path to the JSON file
     * @return the loaded program
     * @throws IOException if the file cannot be read or parsed
     * @throws IllegalArgumentException if required fields are missing
     */
    public static DspySavedProgram loadFromJson(Path path) throws IOException {
        Objects.requireNonNull(path, "Path must not be null");
        log.debug("Loading DSPy program from: {}", path);

        String json = Files.readString(path);
        JsonNode node = MAPPER.readTree(json);

        String name = requireString(node, "name");
        String version = requireString(node, "version");
        String sourceHash = requireString(node, "source_hash");
        @Nullable String dspyVersion = optionalString(node, "dspy_version");
        @Nullable String serializedAt = optionalString(node, "serialized_at");

        // Parse predictors
        Map<String, DspyPredictorConfig> predictors = new LinkedHashMap<>();
        JsonNode predictorsNode = node.get("predictors");
        if (predictorsNode != null && predictorsNode.isObject()) {
            predictorsNode.fields().forEachRemaining(entry -> {
                try {
                    DspyPredictorConfig config = parsePredictorConfig(entry.getValue());
                    predictors.put(entry.getKey(), config);
                } catch (Exception e) {
                    log.warn("Failed to parse predictor '{}': {}", entry.getKey(), e.getMessage());
                }
            });
        }

        // Parse metadata
        Map<String, Object> metadata = new LinkedHashMap<>();
        JsonNode metadataNode = node.get("metadata");
        if (metadataNode != null && metadataNode.isObject()) {
            metadata = MAPPER.treeToValue(metadataNode, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        }

        log.info("Loaded DSPy program '{}' with {} predictors from {}",
                name, predictors.size(), path.getFileName());

        return new DspySavedProgram(
                name,
                version,
                dspyVersion,
                sourceHash,
                predictors,
                metadata,
                serializedAt,
                Instant.now(),
                path
        );
    }

    private static String requireString(JsonNode node, String field) {
        JsonNode fieldNode = node.get(field);
        if (fieldNode == null || !fieldNode.isTextual()) {
            throw new IllegalArgumentException(
                    "Required field '" + field + "' is missing or not a string");
        }
        return fieldNode.asText();
    }

    private static @Nullable String optionalString(JsonNode node, String field) {
        JsonNode fieldNode = node.get(field);
        return fieldNode != null && fieldNode.isTextual() ? fieldNode.asText() : null;
    }

    private static DspyPredictorConfig parsePredictorConfig(JsonNode node) {
        DspySignatureConfig signature = null;
        JsonNode sigNode = node.get("signature");
        if (sigNode != null) {
            signature = parseSignatureConfig(sigNode);
        }

        java.util.List<Map<String, Object>> fewShotExamples = new java.util.ArrayList<>();
        JsonNode fewShotNode = node.get("demos");
        if (fewShotNode != null && fewShotNode.isArray()) {
            for (JsonNode example : fewShotNode) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> exampleMap = MAPPER.treeToValue(example, Map.class);
                    fewShotExamples.add(exampleMap);
                } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                    // Skip malformed example
                }
            }
        }

        @Nullable String learnedInstructions = optionalString(node, "learned_instructions");

        return new DspyPredictorConfig(signature, fewShotExamples, learnedInstructions);
    }

    private static DspySignatureConfig parseSignatureConfig(JsonNode node) {
        @Nullable String instructions = optionalString(node, "instructions");

        java.util.List<DspyFieldConfig> inputFields = new java.util.ArrayList<>();
        JsonNode inputNode = node.get("input_fields");
        if (inputNode != null && inputNode.isArray()) {
            inputNode.forEach(field -> inputFields.add(parseFieldConfig(field)));
        }

        java.util.List<DspyFieldConfig> outputFields = new java.util.ArrayList<>();
        JsonNode outputNode = node.get("output_fields");
        if (outputNode != null && outputNode.isArray()) {
            outputNode.forEach(field -> outputFields.add(parseFieldConfig(field)));
        }

        return new DspySignatureConfig(instructions, inputFields, outputFields);
    }

    private static DspyFieldConfig parseFieldConfig(JsonNode node) {
        String name = node.has("name") ? node.get("name").asText() : "unknown";
        @Nullable String desc = node.has("desc") ? node.get("desc").asText() : null;
        return new DspyFieldConfig(name, desc);
    }

    /**
     * Returns the number of predictors in this program.
     *
     * @return predictor count
     */
    public int predictorCount() {
        return predictors.size();
    }

    /**
     * Returns a predictor configuration by name.
     *
     * @param name predictor name
     * @return predictor configuration
     * @throws IllegalArgumentException if predictor not found
     */
    public DspyPredictorConfig getPredictor(String name) {
        DspyPredictorConfig config = predictors.get(name);
        if (config == null) {
            throw new IllegalArgumentException(
                    "Predictor '" + name + "' not found in program '" + this.name + "'. " +
                    "Available predictors: " + predictors.keySet());
        }
        return config;
    }

    /**
     * Returns the first predictor (convenience for single-predictor programs).
     *
     * @return first predictor configuration
     * @throws IllegalStateException if no predictors exist
     */
    public DspyPredictorConfig firstPredictor() {
        if (predictors.isEmpty()) {
            throw new IllegalStateException(
                    "No predictors available in program '" + name + "'");
        }
        return predictors.values().iterator().next();
    }

    /**
     * Returns the optimizer type from metadata.
     *
     * @return optimizer name (e.g., "GEPA", "BootstrapFewShot"), or "unknown"
     */
    public String optimizerType() {
        Object optimizer = metadata.get("optimizer");
        return optimizer != null ? optimizer.toString() : "unknown";
    }

    /**
     * Returns the validation score from optimization metadata.
     *
     * @return validation score (0.0-1.0), or 0.0 if not available
     */
    public double validationScore() {
        Object score = metadata.get("val_score");
        if (score instanceof Number n) {
            return n.doubleValue();
        }
        return 0.0;
    }

    /**
     * Returns the cache key for this program.
     *
     * <p>The cache key combines name and source hash for unique identification.</p>
     *
     * @return cache key string
     */
    public String cacheKey() {
        return name + ":" + sourceHash;
    }

    /**
     * Returns a summary string for logging.
     *
     * @return human-readable summary
     */
    public String summary() {
        return String.format("DspySavedProgram[name=%s, predictors=%d, optimizer=%s, valScore=%.2f]",
                name, predictors.size(), optimizerType(), validationScore());
    }
}
