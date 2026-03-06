/*
 * Copyright (c) 2025 YAWL Foundation. All rights reserved.
 * This source code is licensed under the Apache License 2.0.
 */

/**
 * Erlang term marshalling for DSPy RPC communication.
 *
 * <h2>Primary Class</h2>
 *
 * {@link org.yawlfoundation.yawl.erlang.term.dspy.DspyTermMarshaller} provides
 * bidirectional conversion between Java objects and Erlang terms (ErlTerm hierarchy).
 *
 * <h2>Type Mapping (Java ↔ Erlang)</h2>
 *
 * <table>
 *   <tr><th>Java Type</th><th>Erlang Type</th></tr>
 *   <tr><td>String</td><td>ErlAtom (atoms, preferred for DSPy)</td></tr>
 *   <tr><td>Integer, Long</td><td>ErlInteger</td></tr>
 *   <tr><td>Float, Double</td><td>ErlFloat</td></tr>
 *   <tr><td>Boolean</td><td>ErlAtom (true/false)</td></tr>
 *   <tr><td>List, Collection</td><td>ErlList</td></tr>
 *   <tr><td>Map</td><td>ErlMap</td></tr>
 *   <tr><td>null</td><td>ErlNil</td></tr>
 * </table>
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * // Marshal Java Map to Erlang
 * Map<String, Object> inputs = Map.of(
 *     "text", "This is amazing!",
 *     "confidence", 0.95
 * );
 * ErlMap erlInputs = DspyTermMarshaller.toErlMap(inputs);
 *
 * // Unmarshal Erlang response back to Java
 * ErlMap erlResponse = ...; // from RPC result
 * Map<String, Object> result = DspyTermMarshaller.fromErlMap(erlResponse);
 * }</pre>
 *
 * <h2>Implementation Notes</h2>
 *
 * <ul>
 *   <li>Strings are marshalled as atoms (ErlAtom) for efficiency with DSPy text inputs</li>
 *   <li>Null values are preserved as ErlNil and unmarshal back to null</li>
 *   <li>Recursively marshals nested collections and maps</li>
 *   <li>UTF-8 encoding for string ↔ binary conversions</li>
 * </ul>
 *
 * @see org.yawlfoundation.yawl.erlang.term.dspy.DspyTermMarshaller
 */
package org.yawlfoundation.yawl.erlang.term.dspy;
