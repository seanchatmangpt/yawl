/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * This file is part of YAWL. YAWL is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License.
 */
package org.yawlfoundation.yawl.ggen.polyglot;

import org.yawlfoundation.yawl.ggen.powl.PowlModel;
import org.yawlfoundation.yawl.ggen.rl.PowlParseException;
import org.yawlfoundation.yawl.graalpy.PythonException;
import org.yawlfoundation.yawl.graalpy.PythonExecutionEngine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Bridges POWL generation to the Python pm4py library via GraalPy.
 *
 * <p>Implements the {@link PowlGenerator} interface, making the Python-backed generation
 * available as a typed, injectable component following the OpenSage "typed tool synthesis"
 * pattern: every Python function has a corresponding strongly-typed Java method.</p>
 *
 * <p>Uses the canonical {@link PythonExecutionEngine} from {@code yawl-graalpy} as the
 * single Java-Python integration pattern in YAWL. The engine's context pool is shared
 * across calls; construction is thread-safe and the engine should be reused.</p>
 *
 * <h2>Two-layer API</h2>
 * <ul>
 *   <li><strong>Raw layer</strong> ({@link PowlGenerator} interface): {@link #generatePowlJson}
 *       and {@link #mineFromXes} return the JSON string exactly as the Python script produces
 *       it — useful for testing and typed injection.</li>
 *   <li><strong>Parsed layer</strong>: {@link #generate} and {@link #mineFromLog} delegate to
 *       the raw layer and then unmarshal the JSON into {@link PowlModel} via
 *       {@link PowlJsonMarshaller}.</li>
 * </ul>
 *
 * <h2>Runtime requirement</h2>
 * <p>GraalVM JDK 24.1+ must be present at runtime. On standard JDK (e.g., Temurin),
 * all methods throw {@link PythonException} with kind
 * {@link PythonException.ErrorKind#RUNTIME_NOT_AVAILABLE}. Callers should handle
 * this by falling back to the {@code OllamaCandidateSampler} or similar Java-only path.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * try (PowlPythonBridge bridge = new PowlPythonBridge()) {
 *     PowlModel model = bridge.generate("loan application approval process");
 * } catch (PythonException e) {
 *     if (e.getErrorKind() == PythonException.ErrorKind.RUNTIME_NOT_AVAILABLE) {
 *         // fall back to OllamaCandidateSampler
 *     }
 * }
 * }</pre>
 */
public class PowlPythonBridge implements PowlGenerator, AutoCloseable {

    private static final String SCRIPT_RESOURCE = "polyglot/powl_generator.py";

    private final PythonExecutionEngine engine;

    /**
     * Constructs a PowlPythonBridge backed by a sandboxed {@link PythonExecutionEngine}
     * with a pool of 4 GraalPy contexts. Thread-safe; reuse across calls.
     */
    public PowlPythonBridge() {
        this(PythonExecutionEngine.builder()
                .sandboxed(true)
                .contextPoolSize(4)
                .build());
    }

    /**
     * Package-private constructor for testing: inject a pre-built engine.
     */
    PowlPythonBridge(PythonExecutionEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
    }

    // ─── PowlGenerator interface — raw JSON layer ─────────────────────────────

    /**
     * Returns the raw JSON string from the Python {@code generate_powl_json()} function.
     *
     * <p>This is the typed-interface layer (OpenSage "tool synthesis"): callers that need
     * the JSON directly (e.g., for forwarding to another service) use this method.
     * For a fully-parsed {@link PowlModel}, use {@link #generate(String)} instead.</p>
     *
     * @param description natural language process description; must not be blank
     * @return JSON string conforming to the POWL wire format; never null
     * @throws IllegalArgumentException if description is blank
     * @throws PythonException if GraalPy is unavailable or Python execution fails
     */
    @Override
    public String generatePowlJson(String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        String escaped = description.replace("\\", "\\\\").replace("'", "\\'");
        return engine.evalToString(loadScript() + "\ngenerate_powl_json('" + escaped + "')");
    }

    /**
     * Returns the raw JSON string from the Python {@code mine_from_xes()} function.
     *
     * @param xesContent complete XES XML string of the event log; must not be blank
     * @return JSON string conforming to the POWL wire format; never null
     * @throws IllegalArgumentException if xesContent is blank
     * @throws PythonException if GraalPy is unavailable or Python execution fails
     */
    @Override
    public String mineFromXes(String xesContent) {
        if (xesContent == null || xesContent.isBlank()) {
            throw new IllegalArgumentException("xesContent must not be blank");
        }
        String escaped = xesContent.replace("\\", "\\\\").replace("'", "\\'");
        return engine.evalToString(loadScript() + "\nmine_from_xes('" + escaped + "')");
    }

    // ─── Parsed layer ─────────────────────────────────────────────────────────

    /**
     * Generates a POWL model from a natural language process description using pm4py.
     *
     * @param processDescription natural language description of the process
     * @return PowlModel representing the described process
     * @throws IllegalArgumentException if processDescription is blank
     * @throws PythonException          if GraalPy is unavailable or Python execution fails
     * @throws PowlParseException       if the Python result cannot be parsed as a POWL model
     */
    public PowlModel generate(String processDescription) throws PowlParseException {
        String json = generatePowlJson(processDescription);
        return PowlJsonMarshaller.fromJson(json, processDescription);
    }

    /**
     * Mines a POWL model from an XES event log using pm4py's inductive miner.
     *
     * @param xesContent XES event log XML content
     * @return PowlModel discovered from the log
     * @throws IllegalArgumentException if xesContent is blank
     * @throws PythonException          if GraalPy is unavailable or Python execution fails
     * @throws PowlParseException       if the Python result cannot be parsed as a POWL model
     */
    public PowlModel mineFromLog(String xesContent) throws PowlParseException {
        String json = mineFromXes(xesContent);
        return PowlJsonMarshaller.fromJson(json, "mined-from-xes-" + System.currentTimeMillis());
    }

    @Override
    public void close() {
        engine.close();
    }

    // ─── private helpers ───────────────────────────────────────────────────

    private String loadScript() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(SCRIPT_RESOURCE)) {
            if (is == null) {
                throw new PythonException(
                        "Python resource not found on classpath: " + SCRIPT_RESOURCE,
                        PythonException.ErrorKind.RUNTIME_ERROR);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PythonException(
                    "Failed to load Python resource: " + SCRIPT_RESOURCE,
                    PythonException.ErrorKind.RUNTIME_ERROR, e);
        }
    }
}
