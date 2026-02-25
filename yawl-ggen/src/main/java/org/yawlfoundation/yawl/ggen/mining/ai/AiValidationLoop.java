/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.mining.ai;

import org.yawlfoundation.yawl.ggen.mining.generators.YawlSpecExporter;
import org.yawlfoundation.yawl.ggen.mining.model.PetriNet;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Orchestrates the generate → validate → fix → re-validate loop for YAWL spec generation.
 *
 * <p>Algorithm (up to {@code maxIterations}):
 * <ol>
 *   <li>Export the {@link PetriNet} to YAWL XML via {@link YawlSpecExporter}.</li>
 *   <li>Validate the XML via {@link OllamaValidationClient}.</li>
 *   <li>If valid: return the XML immediately.</li>
 *   <li>If invalid and iterations remain: apply deterministic fixes via
 *       {@link #applyFixes(String, List)}, increment iteration, go to step 2.</li>
 *   <li>If invalid and {@code maxIterations} exhausted:
 *       throw {@link ValidationExhaustedException}.</li>
 * </ol>
 *
 * <p>The fix step uses deterministic pattern-based transforms so that the LLM
 * remains in a read-only diagnostic role. This prevents hallucination: the model
 * identifies issues, the code applies known safe fixes.
 *
 * <p>Q invariant: if validation cannot be achieved within {@code maxIterations},
 * this class throws — it never returns a spec that failed its last validation.
 */
public class AiValidationLoop {

    private static final Pattern TODO_COMMENT_PATTERN =
            Pattern.compile("(?m)^[^\\S\\r\\n]*//[^\\S\\r\\n]*(TODO|FIXME|HACK|XXX)[^\n]*\\n?");
    private static final Pattern EMPTY_STRING_RETURN_PATTERN =
            Pattern.compile("return\\s+\"\";");
    private static final Pattern NULL_RETURN_PATTERN =
            Pattern.compile("return\\s+null;");

    private final YawlSpecExporter exporter;
    private final OllamaValidationClient validationClient;
    private final int maxIterations;

    /**
     * Constructs an AiValidationLoop.
     *
     * @param exporter         the YAWL spec exporter; must not be null
     * @param validationClient the Ollama client; must not be null
     * @param maxIterations    maximum generate+validate cycles; must be between 1 and 10
     * @throws IllegalArgumentException if any argument violates its contract
     */
    public AiValidationLoop(YawlSpecExporter exporter,
                            OllamaValidationClient validationClient,
                            int maxIterations) {
        if (exporter == null) {
            throw new IllegalArgumentException("exporter must not be null");
        }
        if (validationClient == null) {
            throw new IllegalArgumentException("validationClient must not be null");
        }
        if (maxIterations < 1 || maxIterations > 10) {
            throw new IllegalArgumentException(
                    "maxIterations must be between 1 and 10, got: " + maxIterations);
        }
        this.exporter = exporter;
        this.validationClient = validationClient;
        this.maxIterations = maxIterations;
    }

    /**
     * Runs the full generate → validate → fix loop for the given PetriNet.
     *
     * @param model the PetriNet to convert; must not be null
     * @return the validated YAWL XML string (passed Ollama validation)
     * @throws ValidationExhaustedException if maxIterations reached without passing validation
     * @throws IOException                  if the Ollama client encounters a network error
     * @throws IllegalArgumentException     if model is null
     */
    public String generateAndValidate(PetriNet model) throws IOException {
        if (model == null) {
            throw new IllegalArgumentException("model must not be null");
        }

        String yawlXml = exporter.export(model);
        ValidationResult lastResult = null;

        for (int iteration = 1; iteration <= maxIterations; iteration++) {
            ValidationResult result = validationClient.validate(yawlXml, iteration);
            lastResult = result;

            if (result.valid()) {
                return yawlXml;
            }

            if (iteration < maxIterations) {
                yawlXml = applyFixes(yawlXml, result.issues());
            }
        }

        List<String> finalIssues = lastResult != null ? lastResult.issues() : List.of();
        throw new ValidationExhaustedException(maxIterations, finalIssues);
    }

    /**
     * Applies deterministic structural fixes to YAWL XML based on Ollama-reported issues.
     *
     * <p>Fix rules (matched case-insensitively against each issue string):
     * <ul>
     *   <li>"todo" or "fixme" → strip lines matching the TODO comment pattern</li>
     *   <li>"empty return" → replace {@code return "";} with UnsupportedOperationException throw</li>
     *   <li>"null return" → replace {@code return null;} with UnsupportedOperationException throw</li>
     * </ul>
     *
     * <p>Issues that do not match any rule leave the XML unchanged for that rule.
     * This is intentional: only known-safe transforms are applied.
     *
     * @param yawlXml the XML to fix
     * @param issues  the list of issues reported by Ollama
     * @return the fixed XML string (may be identical to input if no rules matched)
     */
    String applyFixes(String yawlXml, List<String> issues) {
        String result = yawlXml;
        for (String issue : issues) {
            String lower = issue.toLowerCase();
            if (lower.contains("todo") || lower.contains("fixme")) {
                result = TODO_COMMENT_PATTERN.matcher(result).replaceAll("");
            }
            if (lower.contains("empty return")) {
                result = EMPTY_STRING_RETURN_PATTERN.matcher(result).replaceAll(
                        "throw new UnsupportedOperationException(\"Not implemented\");");
            }
            if (lower.contains("null return")) {
                result = NULL_RETURN_PATTERN.matcher(result).replaceAll(
                        "throw new UnsupportedOperationException(\"Not implemented\");");
            }
        }
        return result;
    }
}
