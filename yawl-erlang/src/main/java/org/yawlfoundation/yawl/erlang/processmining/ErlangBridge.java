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
package org.yawlfoundation.yawl.erlang.processmining;

import org.yawlfoundation.yawl.erlang.bridge.ErlangNode;
import org.yawlfoundation.yawl.erlang.capability.Capability;
import org.yawlfoundation.yawl.erlang.capability.MapsToCapability;
import org.yawlfoundation.yawl.erlang.error.ErlangConnectionException;
import org.yawlfoundation.yawl.erlang.error.ErlangRpcException;
import org.yawlfoundation.yawl.erlang.resilience.OtpCircuitBreaker;
import org.yawlfoundation.yawl.erlang.term.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Layer 3 domain API for YAWL workflow and process mining operations.
 *
 * <p>Zero FFI types at call site: all method signatures use only standard Java types
 * (String, List, Map, records) and never expose Panama MemorySegment, Arena, or ErlTerm
 * in public method signatures.</p>
 *
 * <p>Internally delegates to {@link ErlangNode} (Layer 2) which uses erl_interface
 * to call Erlang gen_servers:
 * <ul>
 *   <li>{@code yawl_workflow} — case lifecycle (launch_case/1)</li>
 *   <li>{@code yawl_process_mining} — DFG discovery and token replay conformance</li>
 *   <li>{@code yawl_event_relay} — event subscription via gen_event</li>
 * </ul>
 *
 * <p>Thread-safe: internally uses {@code ErlangNode} lock semantics. May be called
 * from multiple threads concurrently.</p>
 *
 * <p>Usage:
 * <pre>
 *   try (ErlangBridge bridge = ErlangBridge.connect("yawl_erl@localhost", "secret")) {
 *       String caseId = bridge.launchCase("Process_123");
 *       List&lt;Map&lt;String,Object&gt;&gt; log = List.of(
 *           Map.of("activity", "Task_A", "timestamp", "2024-01-01T10:00:00Z"),
 *           Map.of("activity", "Task_B", "timestamp", "2024-01-01T11:00:00Z")
 *       );
 *       ConformanceResult result = bridge.checkConformance(log);
 *       System.out.println("Fitness: " + result.fitness());
 *   }
 * </pre>
 *
 * @see ConformanceResult
 */
@MapsToCapability(value = Capability.LAUNCH_CASE, layer = "L3")
@MapsToCapability(value = Capability.CHECK_CONFORMANCE, layer = "L3")
@MapsToCapability(value = Capability.SUBSCRIBE_TO_EVENTS, layer = "L3")
@MapsToCapability(value = Capability.RELOAD_MODULE, layer = "L3")
@MapsToCapability(value = Capability.AS_RPC_CALLABLE, layer = "L3")
public final class ErlangBridge implements AutoCloseable {

    private final ErlangNode node;
    private volatile boolean running = false;

    /**
     * Constructs an ErlangBridge with an already-connected ErlangNode.
     * For internal use; applications should use {@link #connect}.
     *
     * @param node connected ErlangNode
     */
    private ErlangBridge(ErlangNode node) {
        this.node = node;
    }

    /**
     * Connects to an Erlang node and returns a ready bridge.
     *
     * <p>Establishes a connection to the target Erlang node via EPMD,
     * validates the connection is alive, and returns a bridge ready for RPC calls.
     * Automatically starts the internal keepalive tick loop.</p>
     *
     * @param erlangNodeName target Erlang node name (e.g., {@code "yawl_erl@localhost"})
     * @param cookie         distribution cookie (must match Erlang node's cookie)
     * @return connected ErlangBridge
     * @throws ErlangConnectionException if libei.so is unavailable, EPMD unreachable,
     *                                    or the connection cannot be established
     */
    public static ErlangBridge connect(String erlangNodeName, String cookie)
            throws ErlangConnectionException {
        if (erlangNodeName == null || erlangNodeName.isBlank()) {
            throw new IllegalArgumentException("erlangNodeName must be non-blank");
        }
        if (cookie == null || cookie.isBlank()) {
            throw new IllegalArgumentException("cookie must be non-blank");
        }

        ErlangNode node = new ErlangNode("yawl_java@localhost");
        try {
            node.connect(erlangNodeName, cookie);
            return new ErlangBridge(node);
        } catch (ErlangConnectionException e) {
            try {
                node.close();
            } catch (Exception ignored) {
            }
            throw e;
        }
    }

    /**
     * Launches a new workflow case and returns the case ID.
     *
     * <p>Calls {@code yawl_workflow:launch_case(SpecId)} via RPC on the connected
     * Erlang node. The RPC returns the case ID as an Erlang atom, which is converted
     * to a Java String.</p>
     *
     * @param specId workflow specification ID (e.g., {@code "Process_123"})
     * @return case ID (e.g., {@code "case_1"})
     * @throws ErlangRpcException if the RPC fails, the remote module/function does not exist,
     *                            or the result cannot be decoded
     * @throws IllegalStateException if the bridge is not connected
     */
    public String launchCase(String specId) throws ErlangRpcException {
        if (specId == null || specId.isBlank()) {
            throw new IllegalArgumentException("specId must be non-blank");
        }

        if (!node.isConnected()) {
            throw new IllegalStateException("Bridge is not connected");
        }

        ErlTerm result = node.rpc("yawl_workflow", "launch_case",
            List.of(new ErlAtom(specId)));

        if (result instanceof ErlAtom(var caseId)) {
            return caseId;
        }

        throw new ErlangRpcException("yawl_workflow", "launch_case",
            "Expected ErlAtom result, got " + result.getClass().getSimpleName());
    }

    /**
     * Checks conformance of an event log against the workflow model.
     *
     * <p>Converts the event log (List of Map) to an Erlang list of tuples,
     * calls {@code yawl_process_mining:conformance(Log)} via RPC, and extracts
     * conformance metrics from the returned tuple.</p>
     *
     * <p>Each log entry is a Map with keys:
     * <ul>
     *   <li>{@code "activity"} → activity name (String)</li>
     *   <li>{@code "timestamp"} → ISO-8601 timestamp (String)</li>
     * </ul>
     * Additional keys are silently ignored.
     * </p>
     *
     * <p>The Erlang RPC returns a tuple:
     * {@code {Fitness, Precision, TotalEvents, ConformingEvents}} where all
     * values are numeric (integers or floats).
     * </p>
     *
     * @param log event log as List of Maps, each with "activity" and optional "timestamp" keys
     * @return ConformanceResult with fitness, precision, event counts, and diagnosis
     * @throws ErlangRpcException if the RPC fails, the remote module/function does not exist,
     *                            or the result tuple format is invalid
     * @throws IllegalStateException if the bridge is not connected
     * @throws IllegalArgumentException if log is null or contains entries with missing "activity" key
     */
    public ConformanceResult checkConformance(List<Map<String, Object>> log)
            throws ErlangRpcException {
        if (log == null) {
            throw new IllegalArgumentException("log must be non-null");
        }

        if (!node.isConnected()) {
            throw new IllegalStateException("Bridge is not connected");
        }

        ErlList erlLog = convertLogToErlang(log);

        ErlTerm result = node.rpc("yawl_process_mining", "conformance",
            List.of(erlLog));

        return extractConformanceResult(result);
    }

    /**
     * Subscribes to workflow events from the Erlang event relay.
     *
     * <p>Registers a handler for workflow events and starts a non-blocking
     * virtual thread listener. The handler is called whenever an event is
     * received from the Erlang event relay.</p>
     *
     * <p>Events are JSON-serialized strings representing workflow state changes,
     * case completions, workitem updates, etc.</p>
     *
     * <p>The subscription runs until {@link #close()} is called.</p>
     *
     * @param handler Consumer that processes JSON event strings
     * @throws ErlangConnectionException if the handler registration RPC fails
     * @throws IllegalStateException if the bridge is not connected
     * @throws IllegalArgumentException if handler is null
     */
    public void subscribeToEvents(Consumer<String> handler)
            throws ErlangConnectionException {
        if (handler == null) {
            throw new IllegalArgumentException("handler must be non-null");
        }

        if (!node.isConnected()) {
            throw new IllegalStateException("Bridge is not connected");
        }

        running = true;

        Thread.ofVirtual()
            .name("erlang-event-listener")
            .start(() -> listenForEvents(handler));
    }

    /**
     * Hot-reloads an OTP module without stopping the node.
     *
     * <p>Invokes {@code code:purge(Module)} to remove the old version, then
     * {@code code:load_file(Module)} to load the new version from the node's
     * code path. Requires the {@code .beam} file to be accessible on the
     * Erlang node's code path.</p>
     *
     * <p>All three YAWL gen_servers implement {@code code_change/3} which OTP
     * calls automatically during hot reload to migrate state.</p>
     *
     * @param moduleName atom name of the module (e.g. {@code "yawl_workflow"})
     * @throws ErlangRpcException if purge or load fails, or if the Erlang node
     *                            returns {@code {error, Reason}}
     * @throws IllegalStateException if not connected
     */
    public void reloadModule(String moduleName) throws ErlangRpcException {
        if (moduleName == null || moduleName.isBlank()) {
            throw new IllegalArgumentException("moduleName must be non-blank");
        }

        if (!node.isConnected()) {
            throw new IllegalStateException("Bridge is not connected");
        }

        node.rpc("code", "purge", List.of(new ErlAtom(moduleName)));

        ErlTerm result = node.rpc("code", "load_file", List.of(new ErlAtom(moduleName)));

        if (result instanceof ErlTuple t
                && t.elements().size() == 2
                && t.elements().get(0) instanceof ErlAtom a
                && "error".equals(a.value())) {
            throw new ErlangRpcException("code", "load_file",
                    "Hot reload failed for '" + moduleName + "': " + t.elements().get(1));
        }
    }

    /**
     * Returns the bridge's RPC channel as a callable for use by
     * {@link org.yawlfoundation.yawl.erlang.hotreload.HotReloadServiceImpl} and other
     * components that need OTP RPC access without a hard dependency on this class.
     *
     * <p>The returned callable delegates to the underlying {@link ErlangNode#rpc}
     * and is valid as long as the bridge is connected and not closed.</p>
     *
     * @return an {@link OtpCircuitBreaker.ErlRpcCallable} backed by this bridge's node
     * @throws IllegalStateException if the bridge is not connected
     */
    @MapsToCapability(value = Capability.AS_RPC_CALLABLE, layer = "L3")
    public OtpCircuitBreaker.ErlRpcCallable asRpcCallable() {
        return (module, function, args) -> {
            if (!node.isConnected()) {
                throw new ErlangConnectionException("erlang-bridge", "Bridge is not connected");
            }
            return node.rpc(module, function, args);
        };
    }

    /**
     * Closes the bridge and releases all resources.
     *
     * <p>Stops the event listener thread (if running) and closes the underlying
     * {@link ErlangNode}, releasing the file descriptor and arena memory.</p>
     *
     * <p>After calling close(), the bridge cannot be reused.</p>
     */
    @Override
    public void close() {
        running = false;
        if (node != null) {
            node.close();
        }
    }

    /**
     * Returns whether the bridge is connected and operational.
     *
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        return node.isConnected();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Converts a List of Map event entries into an ErlList of tuples.
     * Each entry becomes a tuple {activity_atom, timestamp_atom}.
     */
    private ErlList convertLogToErlang(List<Map<String, Object>> log) {
        List<ErlTerm> erlTerms = new ArrayList<>(log.size());

        for (Map<String, Object> entry : log) {
            Object activity = entry.get("activity");
            Object timestamp = entry.get("timestamp");

            if (activity == null) {
                throw new IllegalArgumentException(
                    "Event log entry missing required 'activity' key");
            }

            String activityStr = String.valueOf(activity);
            String timestampStr = timestamp != null ? String.valueOf(timestamp) : "";

            ErlAtom activityAtom = new ErlAtom(activityStr);
            ErlAtom timestampAtom = new ErlAtom(timestampStr);

            ErlTuple tuple = new ErlTuple(List.of(activityAtom, timestampAtom));
            erlTerms.add(tuple);
        }

        return new ErlList(erlTerms);
    }

    /**
     * Extracts a ConformanceResult from the Erlang RPC response tuple.
     * Expected format: {Fitness, Precision, TotalEvents, ConformingEvents}
     */
    private ConformanceResult extractConformanceResult(ErlTerm result)
            throws ErlangRpcException {
        if (!(result instanceof ErlTuple(var elements))) {
            throw new ErlangRpcException("yawl_process_mining", "conformance",
                "Expected tuple result, got " + result.getClass().getSimpleName());
        }

        if (elements.size() < 4) {
            throw new ErlangRpcException("yawl_process_mining", "conformance",
                "Expected tuple with 4 elements, got " + elements.size());
        }

        double fitness = extractDouble(elements.get(0), "fitness");
        double precision = extractDouble(elements.get(1), "precision");
        int totalEvents = extractInt(elements.get(2), "totalEvents");
        int conformingEvents = extractInt(elements.get(3), "conformingEvents");

        String diagnosis = String.format(
            "fitness=%.2f, precision=%.2f, %d/%d events conforming",
            fitness, precision, conformingEvents, totalEvents);

        return new ConformanceResult(fitness, precision, totalEvents, conformingEvents, diagnosis);
    }

    /**
     * Extracts a double value from an ErlTerm (ErlFloat or ErlInteger).
     */
    private double extractDouble(ErlTerm term, String fieldName)
            throws ErlangRpcException {
        return switch (term) {
            case ErlFloat(var d) -> d;
            case ErlInteger(var i) -> i.doubleValue();
            default -> throw new ErlangRpcException("yawl_process_mining", "conformance",
                "Field " + fieldName + " has unexpected type " +
                    term.getClass().getSimpleName() + ", expected float or integer");
        };
    }

    /**
     * Extracts an int value from an ErlTerm (ErlInteger).
     */
    private int extractInt(ErlTerm term, String fieldName)
            throws ErlangRpcException {
        if (!(term instanceof ErlInteger(var bigInt))) {
            throw new ErlangRpcException("yawl_process_mining", "conformance",
                "Field " + fieldName + " has unexpected type " +
                    term.getClass().getSimpleName() + ", expected integer");
        }
        try {
            return bigInt.intValueExact();
        } catch (ArithmeticException e) {
            throw new ErlangRpcException("yawl_process_mining", "conformance",
                "Field " + fieldName + " value " + bigInt + " exceeds int range", e);
        }
    }

    /**
     * Listens for events from the Erlang event relay in a loop.
     * Called from a virtual thread started by subscribeToEvents().
     */
    private void listenForEvents(Consumer<String> handler) {
        try {
            while (running && node.isConnected()) {
                try {
                    var message = node.receive();
                    if (running) {
                        String jsonEvent = erlTermToJsonString(message.payload());
                        handler.accept(jsonEvent);
                    }
                } catch (Exception e) {
                    if (running) {
                        System.err.println("Error receiving event: " + e.getMessage());
                    }
                }
            }
        } finally {
            running = false;
        }
    }

    /**
     * Converts an ErlTerm to a JSON string representation for event notification.
     * Provides a best-effort conversion; complex terms are toString()'d.
     */
    private String erlTermToJsonString(ErlTerm term) {
        return switch (term) {
            case ErlAtom(var value) -> "\"" + escapeJson(value) + "\"";
            case ErlInteger(var bigInt) -> bigInt.toString();
            case ErlFloat(var d) -> String.valueOf(d);
            case ErlBinary(var data) -> "\"" + escapeJson(new String(data, StandardCharsets.UTF_8)) + "\"";
            case ErlList(var elements, _) ->
                "[" + elements.stream()
                    .map(this::erlTermToJsonString)
                    .reduce((a, b) -> a + "," + b)
                    .orElse("") + "]";
            case ErlTuple(var elements) ->
                "{" + elements.stream()
                    .map(this::erlTermToJsonString)
                    .reduce((a, b) -> a + "," + b)
                    .orElse("") + "}";
            case ErlMap(var entries) ->
                "{" + entries.entrySet().stream()
                    .map(e -> erlTermToJsonString(e.getKey()) + ":" + erlTermToJsonString(e.getValue()))
                    .reduce((a, b) -> a + "," + b)
                    .orElse("") + "}";
            default -> "\"" + escapeJson(term.toString()) + "\"";
        };
    }

    /**
     * Escapes special characters for JSON string encoding.
     */
    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
