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

package org.yawlfoundation.yawl.integration.a2a.skills;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.ggen.mining.generators.YawlExportException;
import org.yawlfoundation.yawl.ggen.mining.generators.YawlSpecExporter;
import org.yawlfoundation.yawl.ggen.mining.model.PetriNet;
import org.yawlfoundation.yawl.ggen.polyglot.PowlPythonBridge;
import org.yawlfoundation.yawl.ggen.powl.PowlModel;
import org.yawlfoundation.yawl.ggen.powl.PowlToYawlConverter;
import org.yawlfoundation.yawl.ggen.rl.PowlParseException;
import org.yawlfoundation.yawl.graalpy.PythonException;
import org.yawlfoundation.yawl.integration.synthesis.PatternBasedSynthesizer;
import org.yawlfoundation.yawl.integration.synthesis.SynthesisResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A2A Skill for LLM-free YAWL workflow synthesis via GraalPy + pm4py.
 *
 * <p>Executes the complete GraalPy pipeline without LLM inference:
 * <ol>
 *   <li>{@link PowlPythonBridge} — runs Python pm4py inside JVM via GraalVM</li>
 *   <li>{@link PowlToYawlConverter} — converts POWL model to Petri net</li>
 *   <li>{@link YawlSpecExporter} — serialises to YAWL specificationSet XML</li>
 * </ol>
 *
 * <p><b>Parameters:</b>
 * <ul>
 *   <li>{@code description} — natural-language process description (mutually exclusive with xesContent)</li>
 *   <li>{@code xesContent} — XES event log XML for process mining (mutually exclusive with description)</li>
 * </ul>
 *
 * <p>When {@code description} is provided and GraalVM is unavailable, falls back to
 * {@link PatternBasedSynthesizer} (pure Java, WCP pattern matching).
 * When {@code xesContent} is provided and GraalVM is unavailable, returns an error.
 *
 * <p><b>Required Permission:</b> {@code workflow:synthesize}
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class GraalPySynthesisSkill implements A2ASkill, AutoCloseable {

    private static final Logger _log = LogManager.getLogger(GraalPySynthesisSkill.class);

    private static final String SKILL_ID = "graalpy_synthesize";
    private static final String SKILL_NAME = "GraalPy Workflow Synthesis";
    private static final String PERMISSION = "workflow:synthesize";

    private final PowlPythonBridge bridge;
    private final PowlToYawlConverter converter;
    private final YawlSpecExporter exporter;
    private final PatternBasedSynthesizer fallbackSynthesizer;

    /**
     * Constructs the skill with a fresh GraalPy bridge (pool of 2 contexts).
     */
    public GraalPySynthesisSkill() {
        this.bridge = new PowlPythonBridge();
        this.converter = new PowlToYawlConverter();
        this.exporter = new YawlSpecExporter();
        this.fallbackSynthesizer = new PatternBasedSynthesizer();
    }

    /**
     * Package-private constructor for testing with injected bridge.
     *
     * @param bridge the GraalPy bridge to use (non-null)
     */
    GraalPySynthesisSkill(PowlPythonBridge bridge) {
        this.bridge = Objects.requireNonNull(bridge, "bridge must not be null");
        this.converter = new PowlToYawlConverter();
        this.exporter = new YawlSpecExporter();
        this.fallbackSynthesizer = new PatternBasedSynthesizer();
    }

    @Override
    public String getId() { return SKILL_ID; }

    @Override
    public String getName() { return SKILL_NAME; }

    @Override
    public String getDescription() {
        return "Synthesise a YAWL workflow specification from a natural-language description "
            + "or XES event log using GraalPy + pm4py — no LLM, no external service. "
            + "Falls back to WCP pattern matching when GraalVM is unavailable (description only).";
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of(PERMISSION);
    }

    @Override
    public List<String> getTags() {
        return List.of("workflow", "synthesis", "graalpy", "pm4py", "no-llm");
    }

    /**
     * Executes the GraalPy synthesis pipeline.
     *
     * <p>Exactly one of {@code description} or {@code xesContent} must be present.
     *
     * @param request skill request with parameters
     * @return {@link SkillResult} containing {@code yawlXml}, {@code path}, and {@code elapsed_ms}
     */
    @Override
    public SkillResult execute(SkillRequest request) {
        String description = request.getParameter("description");
        String xesContent = request.getParameter("xesContent");

        if (description != null && !description.isBlank()) {
            return synthesizeFromDescription(description.trim());
        }
        if (xesContent != null && !xesContent.isBlank()) {
            return mineFromXes(xesContent.trim());
        }
        return SkillResult.error(
            "Either 'description' or 'xesContent' parameter is required");
    }

    @Override
    public void close() {
        bridge.close();
    }

    // =========================================================================
    // Private synthesis methods
    // =========================================================================

    private SkillResult synthesizeFromDescription(String description) {
        long start = System.currentTimeMillis();
        try {
            PowlModel model = bridge.generate(description);
            PetriNet petriNet = converter.convert(model);
            String yawlXml = exporter.export(petriNet);
            long elapsed = System.currentTimeMillis() - start;

            _log.info("GraalPy synthesis completed in {}ms for: {}", elapsed,
                description.length() > 80 ? description.substring(0, 80) + "…" : description);

            Map<String, Object> data = new HashMap<>();
            data.put("yawlXml", yawlXml);
            data.put("path", "graalpy+pm4py");
            data.put("elapsed_ms", elapsed);
            return SkillResult.success(data, elapsed);

        } catch (PythonException pe) {
            if (isGraalVmUnavailable(pe)) {
                _log.info("GraalVM unavailable — falling back to PatternBasedSynthesizer");
                return synthesizeFallback(description, System.currentTimeMillis() - start);
            }
            _log.warn("GraalPy synthesis failed: {}", pe.getMessage());
            return SkillResult.error("GraalPy synthesis failed: " + pe.getMessage());
        } catch (PowlParseException | YawlExportException | IllegalArgumentException e) {
            _log.warn("GraalPy synthesis failed: {}", e.getMessage());
            return SkillResult.error("GraalPy synthesis failed: " + e.getMessage());
        } catch (Exception e) {
            _log.error("Unexpected error during GraalPy synthesis", e);
            return SkillResult.error("Unexpected error: " + e.getMessage());
        }
    }

    private SkillResult synthesizeFallback(String description, long graalElapsed) {
        long start = System.currentTimeMillis();
        try {
            PatternBasedSynthesizer.PatternSpec spec =
                fallbackSynthesizer.parseDescription(description, List.of());
            SynthesisResult result = fallbackSynthesizer.synthesize(spec);
            long elapsed = System.currentTimeMillis() - start;

            Map<String, Object> data = new HashMap<>();
            data.put("yawlXml", result.specXml());
            data.put("path", "fallback+PatternBasedSynthesizer");
            data.put("pattern", spec.wcpId());
            data.put("elapsed_ms", graalElapsed + elapsed);
            return SkillResult.success(data, graalElapsed + elapsed);

        } catch (IllegalArgumentException e) {
            return SkillResult.error(
                "GraalVM unavailable and fallback synthesis failed: " + e.getMessage());
        }
    }

    private SkillResult mineFromXes(String xesContent) {
        long start = System.currentTimeMillis();
        try {
            PowlModel model = bridge.mineFromLog(xesContent);
            PetriNet petriNet = converter.convert(model);
            String yawlXml = exporter.export(petriNet);
            long elapsed = System.currentTimeMillis() - start;

            _log.info("Process mining completed in {}ms", elapsed);

            Map<String, Object> data = new HashMap<>();
            data.put("yawlXml", yawlXml);
            data.put("path", "graalpy+pm4py+inductive-miner");
            data.put("elapsed_ms", elapsed);
            return SkillResult.success(data, elapsed);

        } catch (PythonException pe) {
            if (isGraalVmUnavailable(pe)) {
                return SkillResult.error(
                    "GraalVM runtime not available. Process mining requires GraalVM JDK 24.1+. "
                    + "No fallback exists for XES mining. Use 'description' parameter instead, "
                    + "which falls back to WCP pattern matching.");
            }
            _log.warn("Process mining failed: {}", pe.getMessage());
            return SkillResult.error("Process mining failed: " + pe.getMessage());
        } catch (PowlParseException | YawlExportException | IllegalArgumentException e) {
            _log.warn("Process mining failed: {}", e.getMessage());
            return SkillResult.error("Process mining failed: " + e.getMessage());
        } catch (Exception e) {
            _log.error("Unexpected error during process mining", e);
            return SkillResult.error("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Returns true when the PythonException indicates GraalVM is not available at runtime.
     * On standard JDK the pool throws CONTEXT_ERROR (context init fails), not RUNTIME_NOT_AVAILABLE.
     */
    private boolean isGraalVmUnavailable(PythonException pe) {
        return pe.getErrorKind() == PythonException.ErrorKind.RUNTIME_NOT_AVAILABLE
            || pe.getErrorKind() == PythonException.ErrorKind.CONTEXT_ERROR;
    }
}
