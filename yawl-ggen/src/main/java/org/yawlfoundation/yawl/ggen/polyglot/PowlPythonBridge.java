/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * This file is part of YAWL. YAWL is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License.
 */
package org.yawlfoundation.yawl.ggen.polyglot;

import org.yawlfoundation.yawl.ggen.powl.PowlActivity;
import org.yawlfoundation.yawl.ggen.powl.PowlModel;
import org.yawlfoundation.yawl.ggen.powl.PowlNode;
import org.yawlfoundation.yawl.ggen.powl.PowlOperatorNode;
import org.yawlfoundation.yawl.ggen.powl.PowlOperatorType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Bridges POWL generation to the Python pm4py library via GraalPy.
 * Loads the powl_generator.py Python script from the classpath resource
 * polyglot/powl_generator.py and evaluates it in a GraalPy context
 * to generate POWL models from process descriptions or XES logs.
 */
public class PowlPythonBridge {

    private final GraalPyRuntime runtime;

    /**
     * Constructs a PowlPythonBridge with the given GraalPy runtime.
     *
     * @param runtime the GraalPyRuntime to use for Python evaluation
     * @throws NullPointerException if runtime is null
     */
    public PowlPythonBridge(GraalPyRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime must not be null");
    }

    /**
     * Generates a POWL model from a natural language process description.
     * Uses pm4py via GraalPy if available.
     *
     * @param processDescription natural language description of the process
     * @return PowlModel representing the described process
     * @throws IllegalArgumentException if processDescription is blank
     * @throws PolyglotException if GraalPy is unavailable or Python evaluation fails
     */
    public PowlModel generate(String processDescription) {
        if (processDescription == null || processDescription.isBlank()) {
            throw new IllegalArgumentException("processDescription must not be blank");
        }
        String script = loadScript("polyglot/powl_generator.py");
        // Inject description and call generate function
        String callScript = script + "\nimport json\nresult = generate_powl_json('''" +
            processDescription.replace("'", "\\'") + "''')\nresult";
        String jsonResult = runtime.eval(callScript);
        return parsePowlJson(jsonResult, processDescription);
    }

    /**
     * Mines a POWL model from an XES event log using pm4py's inductive miner.
     *
     * @param xesContent XES event log XML content
     * @return PowlModel discovered from the log
     * @throws IllegalArgumentException if xesContent is blank
     * @throws PolyglotException if GraalPy is unavailable or Python evaluation fails
     */
    public PowlModel mineFromLog(String xesContent) {
        if (xesContent == null || xesContent.isBlank()) {
            throw new IllegalArgumentException("xesContent must not be blank");
        }
        String script = loadScript("polyglot/powl_generator.py");
        String escapedXes = xesContent.replace("\\", "\\\\").replace("'", "\\'");
        String callScript = script + "\nimport json\nresult = mine_from_xes('''" + escapedXes + "''')\nresult";
        String jsonResult = runtime.eval(callScript);
        return parsePowlJson(jsonResult, "mined-from-xes-" + System.currentTimeMillis());
    }

    /**
     * Loads a Python script from the classpath resources.
     *
     * @param resourcePath the path to the resource (e.g., "polyglot/powl_generator.py")
     * @return the content of the script as a String
     * @throws PolyglotException if the resource cannot be found or loaded
     */
    private String loadScript(String resourcePath) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new PolyglotException("Python resource not found on classpath: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PolyglotException("Failed to load Python resource: " + resourcePath, e);
        }
    }

    /**
     * Parses POWL JSON structure and returns a PowlModel.
     * Expected format: {"type":"ACTIVITY|SEQUENCE|XOR|PARALLEL|LOOP","id":"...","label":"...","children":[...]}
     *
     * @param json    the JSON string to parse
     * @param modelId the identifier for the resulting model
     * @return a PowlModel constructed from the JSON
     * @throws PolyglotException if JSON parsing fails
     */
    private PowlModel parsePowlJson(String json, String modelId) {
        json = json.trim();
        PowlNode root = parseNode(json);
        return PowlModel.of(modelId, root);
    }

    /**
     * Recursively parses a POWL node from JSON.
     *
     * @param json the JSON string representing a single node
     * @return a PowlNode (either PowlActivity or PowlOperatorNode)
     * @throws PolyglotException if JSON parsing fails
     */
    private PowlNode parseNode(String json) {
        // Extract "type" field
        String type = extractStringField(json, "type");
        String id = extractStringField(json, "id");
        if ("ACTIVITY".equals(type)) {
            String label = extractStringField(json, "label");
            return new PowlActivity(id, label);
        }
        // Operator node: parse children array
        PowlOperatorType opType = PowlOperatorType.valueOf(type);
        List<String> childJsons = extractChildJsons(json);
        List<PowlNode> children = new ArrayList<>();
        for (String childJson : childJsons) {
            children.add(parseNode(childJson));
        }
        return new PowlOperatorNode(id, opType, children);
    }

    /**
     * Extracts a string field value from JSON.
     * Assumes format: "key":"value"
     *
     * @param json the JSON string
     * @param key  the field name
     * @return the extracted value
     * @throws PolyglotException if the field is not found or malformed
     */
    private String extractStringField(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start == -1) {
            throw new PolyglotException("Field '" + key + "' not found in JSON: "
                + json.substring(0, Math.min(json.length(), 100)));
        }
        start += search.length();
        int end = json.indexOf('"', start);
        if (end == -1) {
            throw new PolyglotException("Unterminated string for field '" + key + "'");
        }
        return json.substring(start, end);
    }

    /**
     * Extracts child node JSON objects from a parent's "children" array.
     * Uses bracket-matching to extract individual JSON objects.
     *
     * @param json the parent JSON string containing a "children" array
     * @return a list of child JSON object strings
     */
    private List<String> extractChildJsons(String json) {
        List<String> results = new ArrayList<>();
        int childrenStart = json.indexOf("\"children\":[");
        if (childrenStart == -1) return results;
        int arrayStart = json.indexOf('[', childrenStart) + 1;
        int depth = 0;
        int nodeStart = -1;
        for (int i = arrayStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                if (depth == 0) nodeStart = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && nodeStart != -1) {
                    results.add(json.substring(nodeStart, i + 1));
                    nodeStart = -1;
                }
            } else if (c == ']' && depth == 0) {
                break;
            }
        }
        return results;
    }
}
