/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it terms of the GNU Lesser
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
package org.yawlfoundation.yawl.nativebridge.router;

import org.yawlfoundation.yawl.nativebridge.erlang.ErlTerm;
import org.yawlfoundation.yawl.nativebridge.erlang.ErlangException;
import org.yawlfoundation.yawl.nativebridge.qlever.NativeHandle;
import org.yawlfoundation.yawl.nativebridge.qlever.QLeverEngine;
import org.yawlfoundation.yawl.nativebridge.qlever.QLeverException;

import java.util.List;
import java.util.Map;

/**
 * Routes NativeCall triples to appropriate bridge layers
 * based on callPattern registry requirements.
 *
 * <p>This is the central routing component that enforces the
 * Three-Domain Native Bridge Pattern. It analyzes each call
 * and routes it to the correct domain based on the callPattern.</p>
 */
public final class BridgeRouter {

    private final NativeHandle<QLeverEngine> qleverHandle;
    private final ErlangNode erlangNode;

    public BridgeRouter() throws QLeverException, ErlangException {
        // Initialize bridges for all domains
        this.qleverHandle = NativeHandle.create();
        this.erlangNode = new ErlangNode("yawl@localhost");
        this.erlangNode.connect("erlang@localhost", 5671, "yawl_secret");
    }

    /**
     * Routes a NativeCall to the appropriate domain.
     *
     * @param call the native call to route
     * @return the result from the target domain
     * @throws BridgeException if routing or execution fails
     */
    public ErlTerm routeCall(NativeCall call) throws BridgeException {
        return switch (call.callPattern()) {
            case "jvm" -> routeToJvm(call);
            case "beam" -> routeToBeam(call);
            case "direct" -> routeDirect(call);
            default -> throw new BridgeException("Unknown call pattern: " + call.callPattern());
        };
    }

    /**
     * Routes calls to JVM domain (sub-10ns in-process Panama FFM).
     */
    private ErlTerm routeToJvm(NativeCall call) throws BridgeException {
        try {
            QLeverEngine engine = new QLeverEngineImpl(qleverHandle);

            return switch (call.function()) {
                case "qlever_ask" -> {
                    boolean result = engine.ask(call.arguments().get("sparql"));
                    yield ErlTerm.ErlAtom.of(String.valueOf(result));
                }
                case "qlever_select" -> {
                    List<Map<String, String>> results = engine.select(call.arguments().get("sparql"));
                    // Convert results to Erlang list of maps
                    ErlTerm.ErlList resultList = convertSelectResults(results);
                    yield resultList;
                }
                case "qlever_construct" -> {
                    List<Triple> triples = engine.construct(call.arguments().get("sparql"));
                    // Convert triples to Erlang list of tuples
                    ErlTerm.ErlList tripleList = convertConstructResults(triples);
                    yield tripleList;
                }
                default -> throw new BridgeException("Unknown JVM function: " + call.function());
            };
        } catch (QLeverException e) {
            throw new BridgeException("JVM domain call failed", e);
        }
    }

    /**
     * Routes calls to BEAM domain (crosses Boundary A).
     */
    private ErlTerm routeToBeam(NativeCall call) throws BridgeException {
        try {
            // Convert Java arguments to ErlTerm
            List<ErlTerm> erlArgs = call.arguments().values().stream()
                .map(this::convertToErlangTerm)
                .toList();

            // Route to appropriate Erlang module/function
            String module = call.module();
            String function = call.function();

            return erlangNode.rpc(module, function, erlArgs);
        } catch (ErlangException e) {
            throw new BridgeException("BEAM domain call failed", e);
        }
    }

    /**
     * Routes calls directly (architectural escape valve, never used).
     */
    private ErlTerm routeDirect(NativeCall call) throws BridgeException {
        throw new BridgeException(
            "Direct routing disabled. This architectural escape valve " +
            "prevents accidental exposure of JVM to rust4pm faults."
        );
    }

    /**
     * Converts Java SELECT results to Erlang list format.
     */
    private ErlTerm.ErlList convertSelectResults(List<Map<String, String>> results) {
        List<ErlTerm> erlResults = results.stream()
            .map(this::convertRowToErlangMap)
            .toList();
        return new ErlTerm.ErlList(erlResults);
    }

    /**
     * Converts a single row (variable bindings) to Erlang map.
     */
    private ErlTerm convertRowToErlangMap(Map<String, String> row) {
        List<ErlTerm.MapEntry> entries = row.entrySet().stream()
            .map(entry -> new ErlTerm.MapEntry(
                ErlTerm.ErlAtom.of(entry.getKey()),
                ErlTerm.ErlAtom.of(entry.getValue())
            ))
            .toList();
        return new ErlTerm.ErlMap(entries);
    }

    /**
     * Converts Java CONSTRUCT results to Erlang list format.
     */
    private ErlTerm.ErlList convertConstructResults(List<Triple> triples) {
        List<ErlTerm> erlTriples = triples.stream()
            .map(triple -> ErlTerm.ErlTuple.of(
                ErlTerm.ErlAtom.of(triple.subject()),
                ErlTerm.ErlAtom.of(triple.predicate()),
                ErlTerm.ErlAtom.of(triple.object())
            ))
            .toList();
        return new ErlTerm.ErlList(erlTriples);
    }

    /**
     * Converts Java object to Erlang term.
     */
    private ErlTerm convertToErlangTerm(Object value) {
        if (value instanceof String) {
            return ErlTerm.ErlAtom.of((String) value);
        } else if (value instanceof Integer) {
            return ErlTerm.ErlLong.of((Integer) value);
        } else if (value instanceof Long) {
            return ErlTerm.ErlLong.of((Long) value);
        } else if (value instanceof Double) {
            return ErlTerm.ErlFloat.of((Double) value);
        } else if (value instanceof Boolean) {
            return ErlTerm.ErlAtom.of((Boolean) value ? "true" : "false");
        } else if (value instanceof List) {
            List<ErlTerm> elements = ((List<?>) value).stream()
                .map(this::convertToErlangTerm)
                .toList();
            return new ErlTerm.ErlList(elements);
        } else {
            return ErlTerm.ErlAtom.of(value.toString());
        }
    }

    /**
     * Closes all bridge resources.
     */
    public void close() {
        try {
            qleverHandle.close();
        } catch (Exception e) {
            System.err.println("Error closing QLever handle: " + e.getMessage());
        }
        erlangNode.close();
    }
}

/**
 * Represents a native call triple from the bridge ontology.
 */
public record NativeCall(
    String function,
    String module,
    String callPattern,
    Map<String, Object> arguments,
    String registryKind,
    String returnRegistryKind
) {
    public NativeCall {
        if (function == null || module == null || callPattern == null || arguments == null) {
            throw new IllegalArgumentException("Call components cannot be null");
        }
    }
}

/**
 * Exception thrown by the bridge router.
 */
public final class BridgeException extends RuntimeException {
    public BridgeException(String message) {
        super(message);
    }

    public BridgeException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * QLeverEngine implementation using the native handle.
 */
class QLeverEngineImpl implements QLeverEngine {
    private final NativeHandle<QLeverEngine> handle;

    public QLeverEngineImpl(NativeHandle<QLeverEngine> handle) {
        this.handle = handle;
    }

    @Override
    public boolean ask(String sparql) throws QLeverException {
        return handle.ask(sparql);
    }

    @Override
    public List<Map<String, String>> select(String sparql) throws QLeverException {
        return handle.select(sparql);
    }

    @Override
    public List<Triple> construct(String sparql) throws QLeverException {
        return handle.construct(sparql);
    }

    @Override
    public void update(String turtle) throws QLeverException {
        handle.update(turtle);
    }

    @Override
    public QLeverStatus getStatus() {
        return handle.getLastStatus();
    }

    @Override
    public void close() {
        // Handle is closed by the BridgeRouter
    }
}