/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.polyglot;

/**
 * Typed Java interface for the {@code powl_generator.py} Python module.
 *
 * <p>This interface is the canonical contract between Java callers and the Python
 * pm4py-backed process generation code. It is derived from {@code powl_generator.pyi}
 * following the OpenSage "typed tool synthesis" pattern — every Python function exposed
 * to Java has a strongly-typed Java method signature here.</p>
 *
 * <p>The primary implementation is {@link PowlPythonBridge}, which executes the Python
 * functions via {@code PythonExecutionEngine.evalToString()}. On standard JDK (Temurin)
 * without GraalVM, callers should fall back to {@code OllamaCandidateSampler}.
 *
 * <h2>Wire format</h2>
 * <p>Both methods return JSON conforming to the POWL wire format defined in
 * {@code powl_generator.pyi}:
 * <pre>
 *   Activity:  {"type": "ACTIVITY",  "id": str, "label": str}
 *   Operator:  {"type": "SEQUENCE" | "XOR" | "PARALLEL" | "LOOP",
 *                "id": str, "children": [Node, ...]}
 * </pre>
 *
 * @see PowlPythonBridge
 * @see PowlJsonMarshaller
 */
public interface PowlGenerator {

    /**
     * Generates a POWL model from a natural language process description.
     *
     * @param description natural language process description; must not be blank
     * @return JSON string conforming to the POWL wire format; never null or blank
     * @throws org.yawlfoundation.yawl.graalpy.PythonException if GraalPy is unavailable
     *         or Python execution fails — callers should catch and fall back to Ollama
     */
    String generatePowlJson(String description);

    /**
     * Discovers a POWL model from an XES event log using pm4py's inductive miner.
     *
     * @param xesContent complete XES XML string of the event log; must not be blank
     * @return JSON string conforming to the POWL wire format; never null or blank
     * @throws org.yawlfoundation.yawl.graalpy.PythonException if GraalPy is unavailable
     *         or Python execution fails
     */
    String mineFromXes(String xesContent);
}
