/*
 * Copyright (c) 2025 YAWL Foundation. All rights reserved.
 * This source code is licensed under the Apache License 2.0.
 */
package org.yawlfoundation.yawl.erlang.term.dspy;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.yawlfoundation.yawl.erlang.term.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Marshals Java objects to/from Erlang terms for DSPy RPC communication.
 *
 * Provides bidirectional conversion:
 * <ul>
 *   <li>Java Map ↔ ErlMap</li>
 *   <li>Java List ↔ ErlList</li>
 *   <li>Java String ↔ ErlAtom or ErlBinary</li>
 *   <li>Java Number ↔ ErlInteger or ErlFloat</li>
 *   <li>Java Boolean ↔ ErlAtom (true/false)</li>
 *   <li>null → ErlNil</li>
 * </ul>
 *
 * <h2>Type Mapping (Java → Erlang)</h2>
 * <table>
 *   <tr><th>Java Type</th><th>Erlang Type</th></tr>
 *   <tr><td>String</td><td>ErlAtom (atom) or ErlBinary (binary)</td></tr>
 *   <tr><td>Integer, Long</td><td>ErlInteger</td></tr>
 *   <tr><td>Float, Double</td><td>ErlFloat</td></tr>
 *   <tr><td>Boolean</td><td>ErlAtom (true/false)</td></tr>
 *   <tr><td>List, Collection</td><td>ErlList</td></tr>
 *   <tr><td>Map</td><td>ErlMap</td></tr>
 *   <tr><td>null</td><td>ErlNil</td></tr>
 * </table>
 */
@NullMarked
public final class DspyTermMarshaller {

    /**
     * Convert a Java Map to an Erlang map term.
     *
     * Recursively marshals nested values.
     *
     * @param map the Java map to convert
     * @return the ErlMap representation
     */
    public static ErlMap toErlMap(Map<String, Object> map) {
        Objects.requireNonNull(map, "map");
        Map<ErlTerm, ErlTerm> erlMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            ErlTerm key = toErlTerm(entry.getKey());
            ErlTerm value = toErlTerm(entry.getValue());
            erlMap.put(key, value);
        }
        return new ErlMap(erlMap);
    }

    /**
     * Convert a Java object to an Erlang term.
     *
     * @param obj the Java object (String, Number, Boolean, List, Map, or null)
     * @return the corresponding ErlTerm
     * @throws IllegalArgumentException if type is not supported
     */
    public static ErlTerm toErlTerm(@Nullable Object obj) {
        if (obj == null) {
            return ErlNil.INSTANCE;
        }

        if (obj instanceof String str) {
            // Use atom for small strings, binary for larger ones
            // DSPy text inputs are typically < 10KB, so use atom for simplicity
            return new ErlAtom(str);
        }

        if (obj instanceof Integer i) {
            return new ErlInteger(i);
        }

        if (obj instanceof Long l) {
            return new ErlInteger(l.intValue());
        }

        if (obj instanceof Float f) {
            return new ErlFloat(f);
        }

        if (obj instanceof Double d) {
            return new ErlFloat(d);
        }

        if (obj instanceof Boolean b) {
            return new ErlAtom(b ? "true" : "false");
        }

        if (obj instanceof List<?> list) {
            return toErlList(list);
        }

        if (obj instanceof Collection<?> coll) {
            return toErlList(new ArrayList<>(coll));
        }

        if (obj instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> strMap = (Map<String, Object>) map;
            return toErlMap(strMap);
        }

        throw new IllegalArgumentException(
            "Unsupported type for marshalling to Erlang: " + obj.getClass().getSimpleName()
        );
    }

    /**
     * Convert a Java List to an Erlang list term.
     *
     * @param list the Java list to convert
     * @return the ErlList representation
     */
    public static ErlList toErlList(List<?> list) {
        Objects.requireNonNull(list, "list");
        List<ErlTerm> erlTerms = list.stream()
                .map(DspyTermMarshaller::toErlTerm)
                .collect(Collectors.toList());
        return new ErlList(erlTerms);
    }

    /**
     * Convert an Erlang term back to a Java object.
     *
     * @param term the ErlTerm to convert
     * @return the Java object (String, Number, Boolean, List, Map, or null)
     * @throws IllegalArgumentException if type is not supported
     */
    @Nullable
    public static Object fromErlTerm(ErlTerm term) {
        Objects.requireNonNull(term, "term");

        if (term instanceof ErlNil) {
            return null;
        }

        if (term instanceof ErlAtom atom) {
            String value = atom.getValue();
            if ("true".equals(value)) {
                return true;
            } else if ("false".equals(value)) {
                return false;
            }
            return value;
        }

        if (term instanceof ErlBinary binary) {
            return new String(binary.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }

        if (term instanceof ErlInteger integer) {
            return integer.getValue();
        }

        if (term instanceof ErlFloat eFloat) {
            return eFloat.getValue();
        }

        if (term instanceof ErlList eList) {
            return fromErlList(eList);
        }

        if (term instanceof ErlMap eMap) {
            return fromErlMap(eMap);
        }

        if (term instanceof ErlTuple tuple) {
            // Convert tuples to lists for JSON compatibility
            List<Object> result = new ArrayList<>();
            for (ErlTerm element : tuple.getElements()) {
                result.add(fromErlTerm(element));
            }
            return result;
        }

        throw new IllegalArgumentException(
            "Unsupported Erlang term type: " + term.getClass().getSimpleName()
        );
    }

    /**
     * Convert an ErlList to a Java List.
     *
     * @param eList the Erlang list
     * @return Java list of unmarshalled objects
     */
    public static List<Object> fromErlList(ErlList eList) {
        Objects.requireNonNull(eList, "eList");
        return eList.getElements().stream()
                .map(DspyTermMarshaller::fromErlTerm)
                .collect(Collectors.toList());
    }

    /**
     * Convert an ErlMap to a Java Map.
     *
     * All map keys must be strings (atoms) after conversion.
     * Non-string keys are converted to their string representation.
     *
     * @param eMap the Erlang map
     * @return Java map with string keys
     */
    public static Map<String, Object> fromErlMap(ErlMap eMap) {
        Objects.requireNonNull(eMap, "eMap");
        Map<String, Object> result = new HashMap<>();
        Map<ErlTerm, ErlTerm> erlMap = eMap.getMap();
        for (Map.Entry<ErlTerm, ErlTerm> entry : erlMap.entrySet()) {
            String key = keyToString(entry.getKey());
            Object value = fromErlTerm(entry.getValue());
            result.put(key, value);
        }
        return result;
    }

    /**
     * Convert an Erlang term key to a string.
     */
    private static String keyToString(ErlTerm term) {
        if (term instanceof ErlAtom atom) {
            return atom.getValue();
        }
        if (term instanceof ErlBinary binary) {
            return new String(binary.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        return term.toString();
    }
}
