/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * This file is part of YAWL. YAWL is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License.
 */
package org.yawlfoundation.yawl.ggen.polyglot;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import org.yawlfoundation.yawl.ggen.powl.PowlActivity;
import org.yawlfoundation.yawl.ggen.powl.PowlModel;
import org.yawlfoundation.yawl.ggen.powl.PowlNode;
import org.yawlfoundation.yawl.ggen.powl.PowlOperatorNode;
import org.yawlfoundation.yawl.ggen.powl.PowlOperatorType;
import org.yawlfoundation.yawl.ggen.rl.PowlParseException;

import java.util.ArrayList;
import java.util.List;

/**
 * Marshals POWL models to and from the canonical JSON wire format used by
 * {@code powl_generator.py} and related Python components.
 *
 * <h2>Wire format</h2>
 * <pre>
 * Activity:  {"type":"ACTIVITY",  "id":"act_foo",  "label":"Foo"}
 * Operator:  {"type":"SEQUENCE",  "id":"root",     "children":[...]}
 *            {"type":"XOR",       "id":"xor_1",    "children":[...]}
 *            {"type":"PARALLEL",  "id":"par_1",    "children":[...]}
 *            {"type":"LOOP",      "id":"loop_1",   "children":[do, redo]}
 * </pre>
 *
 * <p>This class contains only static utility methods. It is not instantiable.</p>
 */
public final class PowlJsonMarshaller {

    private static final Gson GSON = new Gson();

    private PowlJsonMarshaller() {
        throw new UnsupportedOperationException("PowlJsonMarshaller is a utility class");
    }

    /**
     * Parses a POWL JSON string into a {@link PowlModel}.
     *
     * @param json    the JSON string from {@code powl_generator.py}; must not be null
     * @param modelId identifier for the resulting model
     * @return a non-null {@link PowlModel}
     * @throws PowlParseException if the JSON is malformed or structurally invalid
     */
    public static PowlModel fromJson(String json, String modelId) throws PowlParseException {
        if (json == null || json.isBlank()) {
            throw new PowlParseException("POWL JSON must not be blank", "");
        }
        try {
            JsonObject obj = GSON.fromJson(json.trim(), JsonObject.class);
            PowlNode root = parseNode(obj);
            return PowlModel.of(modelId, root);
        } catch (JsonSyntaxException e) {
            throw new PowlParseException("Malformed POWL JSON: " + e.getMessage(), json, e);
        }
    }

    /**
     * Serializes a {@link PowlModel}'s root node to canonical JSON.
     *
     * @param model the model to serialize; must not be null
     * @return JSON string representation of the root node
     */
    public static String toJson(PowlModel model) {
        if (model == null) {
            throw new IllegalArgumentException("model must not be null");
        }
        return GSON.toJson(nodeToJson(model.root()));
    }

    // ─── private helpers ───────────────────────────────────────────────────

    private static PowlNode parseNode(JsonObject obj) throws PowlParseException {
        String type = requireString(obj, "type");
        String id   = requireString(obj, "id");

        if ("ACTIVITY".equals(type)) {
            String label = requireString(obj, "label");
            return new PowlActivity(id, label);
        }

        PowlOperatorType opType;
        try {
            opType = PowlOperatorType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new PowlParseException("Unknown POWL operator type: " + type, obj.toString());
        }

        JsonArray childrenArr = obj.getAsJsonArray("children");
        if (childrenArr == null || childrenArr.isEmpty()) {
            throw new PowlParseException("Operator node '" + id + "' has no children", obj.toString());
        }
        List<PowlNode> children = new ArrayList<>(childrenArr.size());
        for (JsonElement el : childrenArr) {
            if (!el.isJsonObject()) {
                throw new PowlParseException(
                        "Child element is not a JSON object in node: " + id, el.toString());
            }
            children.add(parseNode(el.getAsJsonObject()));
        }
        return new PowlOperatorNode(id, opType, children);
    }

    private static String requireString(JsonObject obj, String field) throws PowlParseException {
        JsonElement el = obj.get(field);
        if (el == null || !el.isJsonPrimitive()) {
            throw new PowlParseException(
                    "Missing or non-string field '" + field + "' in: " + obj, obj.toString());
        }
        String value = el.getAsString();
        if (value.isBlank()) {
            throw new PowlParseException(
                    "Field '" + field + "' must not be blank in: " + obj, obj.toString());
        }
        return value;
    }

    private static JsonObject nodeToJson(PowlNode node) {
        return switch (node) {
            case PowlActivity a -> {
                JsonObject obj = new JsonObject();
                obj.addProperty("type", "ACTIVITY");
                obj.addProperty("id", a.id());
                obj.addProperty("label", a.label());
                yield obj;
            }
            case PowlOperatorNode op -> {
                JsonObject obj = new JsonObject();
                obj.addProperty("type", op.type().name());
                obj.addProperty("id", op.id());
                JsonArray children = new JsonArray();
                for (PowlNode child : op.children()) {
                    children.add(nodeToJson(child));
                }
                obj.add("children", children);
                yield obj;
            }
        };
    }
}
