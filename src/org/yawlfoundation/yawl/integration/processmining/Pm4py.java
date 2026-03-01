/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * This file is part of YAWL. YAWL is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License.
 */
package org.yawlfoundation.yawl.integration.processmining;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.graalvm.polyglot.Value;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.graalpy.PythonException;
import org.yawlfoundation.yawl.graalpy.PythonExecutionContext;
import org.yawlfoundation.yawl.graalpy.PythonExecutionEngine;
import org.yawlfoundation.yawl.graalpy.TypeMarshaller;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * In-process pm4py client — one-to-one mirror of the pm4py Python API.
 *
 * <p>Every public method corresponds exactly to a function in the pm4py library.
 * A Java 25 developer can read the pm4py documentation and know exactly what is
 * happening. No serialisation between Java and Python: pm4py objects are held as
 * GraalPy {@link Value} references and passed directly between calls.</p>
 *
 * <h2>Context-local Values — critical constraint</h2>
 * <p>GraalPy {@link Value} objects are bound to the context that created them.
 * A Value from one pool context <strong>cannot</strong> be used in a different pool
 * context. All chained operations that pass Python objects between calls must happen
 * inside a single {@link #inContext(Function)} call:</p>
 *
 * <pre>{@code
 * // CORRECT: read + discover in one context borrow
 * PetriNetResult result = pm4py.inContext(bc -> {
 *     Pm4py.EventLog log = bc.readXes(xesXml);
 *     return bc.discoverPetriNetInductive(log);
 * });
 *
 * // WRONG: borrows different pool contexts — crashes at runtime
 * Pm4py.EventLog log = pm4py.readXes(xesXml);          // borrows context A, returns it
 * pm4py.discoverPetriNetInductive(log);                 // may borrow context B — Value is stale!
 * }</pre>
 *
 * <p>The standalone convenience methods ({@link #readXes}, {@link #discoverPetriNetAlpha}, etc.)
 * each borrow a single context independently. They are safe for string-input operations
 * ({@link #readXes}, {@link #xesToOcelJson}) and safe for single-operation calls.
 * For chains, always use {@link #inContext(Function)} with {@link BoundContext}.</p>
 *
 * <h2>Startup</h2>
 * <p>{@code pm4py_bridge.py} is loaded into all pool contexts at construction time
 * via {@link PythonExecutionEngine#evalScriptInAllContexts(Path)}. Per-call overhead
 * is an O(1) global namespace lookup followed by function execution — no script
 * re-parsing, no string embedding.</p>
 *
 * <h2>Runtime requirement</h2>
 * <p>GraalVM JDK 24.1+ at runtime. On standard JDK (Temurin), all methods throw
 * {@link PythonException} with kind {@link PythonException.ErrorKind#CONTEXT_ERROR}.</p>
 *
 * @see <a href="https://pm4py.fit.fraunhofer.de/documentation">pm4py documentation</a>
 * @author YAWL Foundation
 * @version 6.0
 */
public final class Pm4py implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(Pm4py.class);
    private static final String SCRIPT_RESOURCE = "polyglot/pm4py_bridge.py";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ─── Domain types (sealed — opaque wrappers over GraalPy Value) ─────────────────
    // Each record holds a Python object reference. Passing it back to pm4py functions
    // via BoundContext methods is zero-copy: GraalPy unwraps the Value automatically.

    /** Opaque reference to a pm4py EventLog Python object. */
    public sealed interface EventLog    permits EventLogValue {}
    /** Opaque reference to a pm4py PetriNet Python object. */
    public sealed interface PetriNet    permits PetriNetValue {}
    /** Opaque reference to a pm4py Marking Python object. */
    public sealed interface Marking     permits MarkingValue {}
    /** Opaque reference to a pm4py ProcessTree Python object. */
    public sealed interface ProcessTree permits ProcessTreeValue {}
    /** Opaque reference to a pm4py POWL Python object. */
    public sealed interface POWL        permits PowlValue {}
    /** Opaque reference to a pm4py BPMN Python object. */
    public sealed interface Bpmn        permits BpmnValue {}
    /** Opaque reference to a pm4py pandas DataFrame Python object. */
    public sealed interface DataFrame   permits DataFrameValue {}
    /** Opaque reference to a pm4py EventStream Python object. */
    public sealed interface EventStream permits EventStreamValue {}

    record EventLogValue(Value pythonObject)    implements EventLog {}
    record PetriNetValue(Value pythonObject)    implements PetriNet {}
    record MarkingValue(Value pythonObject)     implements Marking {}
    record ProcessTreeValue(Value pythonObject) implements ProcessTree {}
    record PowlValue(Value pythonObject)        implements POWL {}
    record BpmnValue(Value pythonObject)        implements Bpmn {}
    record DataFrameValue(Value pythonObject)   implements DataFrame {}
    record EventStreamValue(Value pythonObject) implements EventStream {}

    // ─── Result records ──────────────────────────────────────────────────────────────

    /**
     * Petri net discovery result — net and its initial and final markings.
     *
     * <p>Equivalent to the Python tuple {@code (PetriNet, Marking, Marking)} returned
     * by all pm4py discovery algorithms.</p>
     */
    public record PetriNetResult(PetriNet net, Marking initialMarking, Marking finalMarking) {}

    /**
     * Directly-follows graph result.
     *
     * <p>Equivalent to the Python tuple {@code (dfg, start_activities, end_activities)}
     * from {@code pm4py.discover_dfg(log)}. Edges are serialized through
     * {@code discover_dfg_as_json} to avoid GraalPy tuple-key marshalling limitations
     * on the DFG dict (pm4py uses {@code (str, str)} tuple keys).</p>
     *
     * @param edges           directed edges: each map has {@code source}, {@code target}, {@code count}
     * @param startActivities start activity name → occurrence count
     * @param endActivities   end activity name → occurrence count
     */
    public record DfgResult(
        List<Map<String, Object>> edges,
        Map<String, Long> startActivities,
        Map<String, Long> endActivities
    ) {}

    // ─── Construction ────────────────────────────────────────────────────────────────

    private final PythonExecutionEngine engine;

    /**
     * Creates a Pm4py instance backed by a permissive {@link PythonExecutionEngine}
     * sized to the available processor count.
     *
     * <p>pm4py needs filesystem access for its own {@code .pyc} bytecode cache and
     * temp-file fallbacks in older versions, so {@code sandboxed(false)} is required.</p>
     *
     * <p>Blocks until {@code pm4py_bridge.py} is loaded into all pool contexts.</p>
     */
    public Pm4py() {
        this(PythonExecutionEngine.builder()
                .sandboxed(false)
                .contextPoolSize(Math.max(1, Runtime.getRuntime().availableProcessors()))
                .build());
    }

    /**
     * Package-private constructor for test injection.
     *
     * @param engine pre-built engine; must not be null
     */
    Pm4py(PythonExecutionEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
        preloadScript();
    }

    // ─── Context-bound multi-step execution ─────────────────────────────────────────

    /**
     * Executes a multi-step pm4py operation within a single borrowed GraalPy context.
     *
     * <p>Use this for any chain of operations that passes Python objects between calls.
     * All operations inside the lambda share one borrowed context, making it safe to
     * pass {@link EventLog}, {@link PetriNet}, {@link Marking}, etc. between calls:</p>
     *
     * <pre>{@code
     * PetriNetResult result = pm4py.inContext(bc -> {
     *     Pm4py.EventLog log = bc.readXes(xesXml);          // create in this context
     *     return bc.discoverPetriNetInductive(log);          // use in same context — safe
     * });
     * }</pre>
     *
     * <p><strong>Do not</strong> store a {@link BoundContext} or return pm4py domain
     * types beyond the lambda — they are context-local and become invalid when the
     * borrow is returned.</p>
     *
     * @param fn  function receiving a {@link BoundContext}; must not be null
     * @param <T> return type
     * @return the value returned by {@code fn}
     * @throws PythonException if GraalPy is unavailable or Python execution fails
     */
    public <T> T inContext(Function<BoundContext, T> fn) {
        Objects.requireNonNull(fn, "fn must not be null");
        return engine.getContextPool().execute(ctx -> fn.apply(new BoundContext(ctx)));
    }

    // ─── Reading / Writing ───────────────────────────────────────────────────────────

    /**
     * Parse an XES event log from a string.
     *
     * <p>Uses {@code io.StringIO} stream parsing (no temp file) with fallback to a
     * temp file for older pm4py versions that require a file path.</p>
     *
     * <p>Equivalent to: {@code pm4py.read_xes(path)}</p>
     *
     * @param xesXml  complete XES XML string; must not be null or blank
     * @return EventLog Python object reference (context-local; use within {@link #inContext})
     * @throws IllegalArgumentException if xesXml is null or blank
     * @throws PythonException if GraalPy is unavailable or XES parsing fails
     * @see <a href="https://pm4py.fit.fraunhofer.de/documentation#reading">pm4py.read_xes</a>
     */
    public EventLog readXes(String xesXml) {
        requireNonBlank(xesXml, "xesXml");
        return inContext(bc -> bc.readXes(xesXml));
    }

    /**
     * Write a pm4py EventLog to an XES file at the given path.
     *
     * <p>Equivalent to: {@code pm4py.write_xes(log, path)}</p>
     *
     * <p><strong>Context constraint:</strong> {@code log} must have been created
     * in the current call to this method, or via {@link #inContext} where this
     * method is also called.</p>
     *
     * @param log   EventLog to write; must not be null
     * @param path  filesystem path for the output XES file; must not be null
     * @throws PythonException if writing fails
     * @see <a href="https://pm4py.fit.fraunhofer.de/documentation#writing">pm4py.write_xes</a>
     */
    public void writeXes(EventLog log, String path) {
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(path, "path");
        engine.getContextPool().execute(ctx -> {
            ctx.getBindings().getMember("write_xes").execute(pythonValue(log), path);
            return null;
        });
    }

    /**
     * Parse a PNML XML string into a Petri net, initial marking, and final marking.
     *
     * <p>Uses {@code io.StringIO} stream parsing where supported; falls back to a temp
     * file for older pm4py versions.</p>
     *
     * @param pnmlXml  PNML XML string; must not be null
     * @return PetriNetResult with net and both markings (context-local Values)
     * @throws PythonException if parsing fails
     */
    public PetriNetResult readPnmlString(String pnmlXml) {
        Objects.requireNonNull(pnmlXml, "pnmlXml");
        return inContext(bc -> bc.readPnmlString(pnmlXml));
    }

    /**
     * Serialize a pm4py PetriNet to PNML XML string.
     *
     * <p>Equivalent to {@code pm4py.write_pnml(net, im, fm, path)} but returns the
     * XML as a string without writing to disk.</p>
     *
     * @param result  PetriNetResult to serialize; must not be null
     * @return PNML XML string
     * @throws PythonException if serialization fails
     */
    public String exportPetriNetToPnmlString(PetriNetResult result) {
        Objects.requireNonNull(result, "result");
        return engine.getContextPool().execute(ctx -> {
            Value fn = ctx.getBindings().getMember("export_petri_net_to_pnml_string");
            Value pnml = fn.execute(
                pythonValue(result.net()),
                pythonValue(result.initialMarking()),
                pythonValue(result.finalMarking()));
            return TypeMarshaller.toString(pnml);
        });
    }

    // ─── Discovery ───────────────────────────────────────────────────────────────────

    /**
     * Alpha algorithm Petri net discovery.
     *
     * <p>Equivalent to: {@code pm4py.discover_petri_net_alpha(log)}</p>
     *
     * @param log  EventLog to mine; must not be null
     * @return PetriNetResult (net, initial marking, final marking)
     * @see <a href="https://pm4py.fit.fraunhofer.de/documentation#discovery">pm4py discovery</a>
     */
    public PetriNetResult discoverPetriNetAlpha(EventLog log) {
        Objects.requireNonNull(log, "log");
        return inContext(bc -> bc.discoverPetriNetAlpha(log));
    }

    /**
     * Alpha+ algorithm Petri net discovery.
     *
     * <p>Equivalent to: {@code pm4py.discover_petri_net_alpha_plus(log)}</p>
     */
    public PetriNetResult discoverPetriNetAlphaPlus(EventLog log) {
        Objects.requireNonNull(log, "log");
        return inContext(bc -> bc.discoverPetriNetAlphaPlus(log));
    }

    /**
     * Alpha+++ algorithm Petri net discovery.
     *
     * <p>Equivalent to: {@code pm4py.discover_petri_net_alpha_plus_plus(log)}</p>
     */
    public PetriNetResult discoverPetriNetAlphaPlusPlus(EventLog log) {
        Objects.requireNonNull(log, "log");
        return inContext(bc -> bc.discoverPetriNetAlphaPlusPlus(log));
    }

    /**
     * Inductive miner Petri net discovery.
     *
     * <p>Equivalent to: {@code pm4py.discover_petri_net_inductive(log)}</p>
     */
    public PetriNetResult discoverPetriNetInductive(EventLog log) {
        Objects.requireNonNull(log, "log");
        return inContext(bc -> bc.discoverPetriNetInductive(log));
    }

    /**
     * Heuristics miner Petri net discovery.
     *
     * <p>Equivalent to: {@code pm4py.discover_petri_net_heuristics(log)}</p>
     */
    public PetriNetResult discoverPetriNetHeuristics(EventLog log) {
        Objects.requireNonNull(log, "log");
        return inContext(bc -> bc.discoverPetriNetHeuristics(log));
    }

    /**
     * Inductive process tree discovery.
     *
     * <p>Equivalent to: {@code pm4py.discover_process_tree_inductive(log)}</p>
     */
    public ProcessTree discoverProcessTreeInductive(EventLog log) {
        Objects.requireNonNull(log, "log");
        return inContext(bc -> bc.discoverProcessTreeInductive(log));
    }

    /**
     * BPMN inductive miner discovery.
     *
     * <p>Equivalent to: {@code pm4py.discover_bpmn_inductive(log)}</p>
     */
    public Bpmn discoverBpmnInductive(EventLog log) {
        Objects.requireNonNull(log, "log");
        return inContext(bc -> bc.discoverBpmnInductive(log));
    }

    /**
     * POWL model discovery.
     *
     * <p>Equivalent to: {@code pm4py.discover_powl(log)}</p>
     */
    public POWL discoverPowl(EventLog log) {
        Objects.requireNonNull(log, "log");
        return inContext(bc -> bc.discoverPowl(log));
    }

    /**
     * Discover a Directly-Follows Graph, returning typed Java result.
     *
     * <p>Equivalent to: {@code pm4py.discover_dfg(log)} — serialized through
     * {@code discover_dfg_as_json} to avoid GraalPy tuple-key marshalling
     * limitations (pm4py DFG dicts use {@code (str, str)} tuple keys).</p>
     *
     * @param log  EventLog to mine; must not be null
     * @return DfgResult with edges, start activities, and end activities
     * @see <a href="https://pm4py.fit.fraunhofer.de/documentation#discovery">pm4py.discover_dfg</a>
     */
    public DfgResult discoverDfg(EventLog log) {
        Objects.requireNonNull(log, "log");
        String json = inContext(bc -> bc.discoverDfgAsJson(log));
        return parseDfgJson(json);
    }

    /**
     * Discover a Directly-Follows Graph as a JSON string.
     *
     * <p>Returns: {@code {"edges": [{"source": str, "target": str, "count": int}],
     * "start_activities": {str: int}, "end_activities": {str: int}}}</p>
     *
     * @param log  EventLog to mine; must not be null
     * @return DFG as JSON string
     */
    public String discoverDfgAsJson(EventLog log) {
        Objects.requireNonNull(log, "log");
        return inContext(bc -> bc.discoverDfgAsJson(log));
    }

    // ─── Conformance ─────────────────────────────────────────────────────────────────

    /**
     * Token-based replay conformance diagnostics.
     *
     * <p>Equivalent to: {@code pm4py.conformance_diagnostics_token_based_replay(log, net, im, fm)}</p>
     *
     * @return list of per-trace replay result maps
     * @see <a href="https://pm4py.fit.fraunhofer.de/documentation#conformance">pm4py conformance</a>
     */
    public List<Map<String, Object>> conformanceDiagnosticsTokenBasedReplay(
            EventLog log, PetriNet net, Marking im, Marking fm) {
        requireConformanceArgs(log, net, im, fm);
        return inContext(bc -> bc.conformanceDiagnosticsTokenBasedReplay(log, net, im, fm));
    }

    /**
     * Alignment-based conformance diagnostics.
     *
     * <p>Equivalent to: {@code pm4py.conformance_diagnostics_alignments(log, net, im, fm)}</p>
     */
    public List<Map<String, Object>> conformanceDiagnosticsAlignments(
            EventLog log, PetriNet net, Marking im, Marking fm) {
        requireConformanceArgs(log, net, im, fm);
        return inContext(bc -> bc.conformanceDiagnosticsAlignments(log, net, im, fm));
    }

    /**
     * Token-based replay fitness.
     *
     * <p>Equivalent to: {@code pm4py.fitness_token_based_replay(log, net, im, fm)}</p>
     *
     * @return map with fitness metrics: {@code log_fitness}, {@code average_trace_fitness},
     *         {@code perc_fit_traces}, etc.
     */
    public Map<String, Object> fitnessTokenBasedReplay(
            EventLog log, PetriNet net, Marking im, Marking fm) {
        requireConformanceArgs(log, net, im, fm);
        return inContext(bc -> bc.fitnessTokenBasedReplay(log, net, im, fm));
    }

    /**
     * Alignment-based fitness.
     *
     * <p>Equivalent to: {@code pm4py.fitness_alignments(log, net, im, fm)}</p>
     */
    public Map<String, Object> fitnessAlignments(
            EventLog log, PetriNet net, Marking im, Marking fm) {
        requireConformanceArgs(log, net, im, fm);
        return inContext(bc -> bc.fitnessAlignments(log, net, im, fm));
    }

    /**
     * Token-based replay precision.
     *
     * <p>Equivalent to: {@code pm4py.precision_token_based_replay(log, net, im, fm)}</p>
     *
     * @return precision in [0, 1]
     */
    public double precisionTokenBasedReplay(
            EventLog log, PetriNet net, Marking im, Marking fm) {
        requireConformanceArgs(log, net, im, fm);
        return inContext(bc -> bc.precisionTokenBasedReplay(log, net, im, fm));
    }

    // ─── Statistics ──────────────────────────────────────────────────────────────────

    /**
     * Compute duration (in seconds) for each case in the log.
     *
     * <p>Equivalent to: {@code pm4py.get_all_case_durations(log)}</p>
     *
     * @return list of case durations in seconds
     * @see <a href="https://pm4py.fit.fraunhofer.de/documentation#statistics">pm4py statistics</a>
     */
    public List<Double> getAllCaseDurations(EventLog log) {
        Objects.requireNonNull(log, "log");
        return inContext(bc -> bc.getAllCaseDurations(log));
    }

    /**
     * Extract variants (unique activity sequences) and their frequencies.
     *
     * <p>Equivalent to: {@code pm4py.get_variants(log)} — normalised to
     * {@code Map<String, Long>} since pm4py uses variant tuples as dict keys.</p>
     *
     * @return map of variant string → occurrence count
     */
    public Map<String, Long> getVariants(EventLog log) {
        Objects.requireNonNull(log, "log");
        return inContext(bc -> bc.getVariants(log));
    }

    /**
     * Get start activities and their occurrence counts.
     *
     * <p>Equivalent to: {@code pm4py.get_start_activities(log)}</p>
     */
    public Map<String, Long> getStartActivities(EventLog log) {
        Objects.requireNonNull(log, "log");
        return inContext(bc -> bc.getStartActivities(log));
    }

    /**
     * Get end activities and their occurrence counts.
     *
     * <p>Equivalent to: {@code pm4py.get_end_activities(log)}</p>
     */
    public Map<String, Long> getEndActivities(EventLog log) {
        Objects.requireNonNull(log, "log");
        return inContext(bc -> bc.getEndActivities(log));
    }

    /**
     * Get all event attribute names in the log.
     *
     * <p>Equivalent to: {@code pm4py.get_event_attributes(log)}</p>
     */
    public List<String> getEventAttributes(EventLog log) {
        Objects.requireNonNull(log, "log");
        return inContext(bc -> bc.getEventAttributes(log));
    }

    // ─── Format conversion ───────────────────────────────────────────────────────────

    /**
     * Convert EventLog to a pandas DataFrame.
     *
     * <p>Equivalent to: {@code pm4py.convert_to_dataframe(log)}</p>
     */
    public DataFrame convertToDataframe(EventLog log) {
        Objects.requireNonNull(log, "log");
        return inContext(bc -> bc.convertToDataframe(log));
    }

    /**
     * Convert a pandas DataFrame to a pm4py EventLog.
     *
     * <p>Equivalent to: {@code pm4py.convert_to_event_log(df)}</p>
     */
    public EventLog convertToEventLog(DataFrame df) {
        Objects.requireNonNull(df, "df");
        return inContext(bc -> bc.convertToEventLog(df));
    }

    /**
     * Convert EventLog to an EventStream.
     *
     * <p>Equivalent to: {@code pm4py.convert_to_event_stream(log)}</p>
     */
    public EventStream convertToEventStream(EventLog log) {
        Objects.requireNonNull(log, "log");
        return inContext(bc -> bc.convertToEventStream(log));
    }

    // ─── OCEL ────────────────────────────────────────────────────────────────────────

    /**
     * Convert an XES event log to OCEL 2.0 JSON.
     *
     * <p>Wraps {@code read_xes} → {@code pm4py.convert_to_ocel} → OCEL JSON export
     * in one context borrow.</p>
     *
     * @param xesXml  XES event log string; must not be null or blank
     * @return OCEL 2.0 JSON string
     */
    public String xesToOcelJson(String xesXml) {
        requireNonBlank(xesXml, "xesXml");
        return engine.getContextPool().execute(ctx -> {
            Value fn = ctx.getBindings().getMember("xes_to_ocel_json");
            return TypeMarshaller.toString(fn.execute(xesXml));
        });
    }

    // ─── Health ──────────────────────────────────────────────────────────────────────

    /**
     * Liveness check. Returns {@code true} if pm4py is importable in GraalPy.
     *
     * <p>Returns {@code false} rather than throwing when GraalPy is unavailable
     * (standard JDK) or pm4py is not installed.</p>
     */
    public boolean ping() {
        try {
            return engine.getContextPool().execute(ctx -> {
                Value fn = ctx.getBindings().getMember("ping");
                return "ok".equals(TypeMarshaller.toString(fn.execute()));
            });
        } catch (PythonException e) {
            log.debug("pm4py ping failed: {}", e.getMessage());
            return false;
        }
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────────────────

    /** Closes the engine and all pooled GraalPy contexts. */
    @Override
    public void close() {
        engine.close();
    }

    // ─── BoundContext ────────────────────────────────────────────────────────────────

    /**
     * Provides all pm4py operations bound to a single borrowed GraalPy context.
     *
     * <p>Obtain via {@link Pm4py#inContext(Function)}. All operations share one
     * borrowed pool context, making it safe to pass Python Values between calls.</p>
     *
     * <p><strong>Do not</strong> store a BoundContext beyond the lambda passed to
     * {@code inContext} — the context is returned to the pool when the lambda exits.</p>
     */
    public final class BoundContext {

        private final PythonExecutionContext ctx;

        BoundContext(PythonExecutionContext ctx) {
            this.ctx = ctx;
        }

        /** @see Pm4py#readXes(String) */
        public EventLog readXes(String xesXml) {
            return new EventLogValue(ctx.getBindings().getMember("read_xes").execute(xesXml));
        }

        /** @see Pm4py#readPnmlString(String) */
        public PetriNetResult readPnmlString(String pnmlXml) {
            return toPetriNetResult(
                ctx.getBindings().getMember("read_pnml_string").execute(pnmlXml));
        }

        /** @see Pm4py#exportPetriNetToPnmlString(PetriNetResult) */
        public String exportPetriNetToPnmlString(PetriNetResult result) {
            Value pnml = ctx.getBindings().getMember("export_petri_net_to_pnml_string")
                .execute(pythonValue(result.net()),
                         pythonValue(result.initialMarking()),
                         pythonValue(result.finalMarking()));
            return TypeMarshaller.toString(pnml);
        }

        /** @see Pm4py#discoverPetriNetAlpha(EventLog) */
        public PetriNetResult discoverPetriNetAlpha(EventLog log) {
            return toPetriNetResult(
                ctx.getBindings().getMember("discover_petri_net_alpha").execute(pythonValue(log)));
        }

        /** @see Pm4py#discoverPetriNetAlphaPlus(EventLog) */
        public PetriNetResult discoverPetriNetAlphaPlus(EventLog log) {
            return toPetriNetResult(
                ctx.getBindings().getMember("discover_petri_net_alpha_plus").execute(pythonValue(log)));
        }

        /** @see Pm4py#discoverPetriNetAlphaPlusPlus(EventLog) */
        public PetriNetResult discoverPetriNetAlphaPlusPlus(EventLog log) {
            return toPetriNetResult(
                ctx.getBindings().getMember("discover_petri_net_alpha_plus_plus").execute(pythonValue(log)));
        }

        /** @see Pm4py#discoverPetriNetInductive(EventLog) */
        public PetriNetResult discoverPetriNetInductive(EventLog log) {
            return toPetriNetResult(
                ctx.getBindings().getMember("discover_petri_net_inductive").execute(pythonValue(log)));
        }

        /** @see Pm4py#discoverPetriNetHeuristics(EventLog) */
        public PetriNetResult discoverPetriNetHeuristics(EventLog log) {
            return toPetriNetResult(
                ctx.getBindings().getMember("discover_petri_net_heuristics").execute(pythonValue(log)));
        }

        /** @see Pm4py#discoverProcessTreeInductive(EventLog) */
        public ProcessTree discoverProcessTreeInductive(EventLog log) {
            return new ProcessTreeValue(
                ctx.getBindings().getMember("discover_process_tree_inductive").execute(pythonValue(log)));
        }

        /** @see Pm4py#discoverBpmnInductive(EventLog) */
        public Bpmn discoverBpmnInductive(EventLog log) {
            return new BpmnValue(
                ctx.getBindings().getMember("discover_bpmn_inductive").execute(pythonValue(log)));
        }

        /** @see Pm4py#discoverPowl(EventLog) */
        public POWL discoverPowl(EventLog log) {
            return new PowlValue(
                ctx.getBindings().getMember("discover_powl").execute(pythonValue(log)));
        }

        /**
         * Discover a DFG and return the result as a JSON string.
         * Avoids GraalPy tuple-key marshalling by going through {@code discover_dfg_as_json}.
         */
        public String discoverDfgAsJson(EventLog log) {
            return TypeMarshaller.toString(
                ctx.getBindings().getMember("discover_dfg_as_json").execute(pythonValue(log)));
        }

        /** @see Pm4py#conformanceDiagnosticsTokenBasedReplay */
        @SuppressWarnings("unchecked")
        public List<Map<String, Object>> conformanceDiagnosticsTokenBasedReplay(
                EventLog log, PetriNet net, Marking im, Marking fm) {
            Value result = ctx.getBindings()
                .getMember("conformance_diagnostics_token_based_replay")
                .execute(pythonValue(log), pythonValue(net), pythonValue(im), pythonValue(fm));
            return (List<Map<String, Object>>) (List<?>) TypeMarshaller.toList(result);
        }

        /** @see Pm4py#conformanceDiagnosticsAlignments */
        @SuppressWarnings("unchecked")
        public List<Map<String, Object>> conformanceDiagnosticsAlignments(
                EventLog log, PetriNet net, Marking im, Marking fm) {
            Value result = ctx.getBindings()
                .getMember("conformance_diagnostics_alignments")
                .execute(pythonValue(log), pythonValue(net), pythonValue(im), pythonValue(fm));
            return (List<Map<String, Object>>) (List<?>) TypeMarshaller.toList(result);
        }

        /** @see Pm4py#fitnessTokenBasedReplay */
        public Map<String, Object> fitnessTokenBasedReplay(
                EventLog log, PetriNet net, Marking im, Marking fm) {
            return TypeMarshaller.toMap(ctx.getBindings()
                .getMember("fitness_token_based_replay")
                .execute(pythonValue(log), pythonValue(net), pythonValue(im), pythonValue(fm)));
        }

        /** @see Pm4py#fitnessAlignments */
        public Map<String, Object> fitnessAlignments(
                EventLog log, PetriNet net, Marking im, Marking fm) {
            return TypeMarshaller.toMap(ctx.getBindings()
                .getMember("fitness_alignments")
                .execute(pythonValue(log), pythonValue(net), pythonValue(im), pythonValue(fm)));
        }

        /** @see Pm4py#precisionTokenBasedReplay */
        public double precisionTokenBasedReplay(
                EventLog log, PetriNet net, Marking im, Marking fm) {
            return TypeMarshaller.toDouble(ctx.getBindings()
                .getMember("precision_token_based_replay")
                .execute(pythonValue(log), pythonValue(net), pythonValue(im), pythonValue(fm)));
        }

        /** @see Pm4py#getAllCaseDurations(EventLog) */
        public List<Double> getAllCaseDurations(EventLog log) {
            return TypeMarshaller.toList(
                ctx.getBindings().getMember("get_all_case_durations").execute(pythonValue(log)))
                .stream().map(o -> ((Number) o).doubleValue()).toList();
        }

        /** @see Pm4py#getVariants(EventLog) */
        @SuppressWarnings("unchecked")
        public Map<String, Long> getVariants(EventLog log) {
            return toLongMap(TypeMarshaller.toMap(
                ctx.getBindings().getMember("get_variants").execute(pythonValue(log))));
        }

        /** @see Pm4py#getStartActivities(EventLog) */
        @SuppressWarnings("unchecked")
        public Map<String, Long> getStartActivities(EventLog log) {
            return toLongMap(TypeMarshaller.toMap(
                ctx.getBindings().getMember("get_start_activities").execute(pythonValue(log))));
        }

        /** @see Pm4py#getEndActivities(EventLog) */
        @SuppressWarnings("unchecked")
        public Map<String, Long> getEndActivities(EventLog log) {
            return toLongMap(TypeMarshaller.toMap(
                ctx.getBindings().getMember("get_end_activities").execute(pythonValue(log))));
        }

        /** @see Pm4py#getEventAttributes(EventLog) */
        public List<String> getEventAttributes(EventLog log) {
            return TypeMarshaller.toList(
                ctx.getBindings().getMember("get_event_attributes").execute(pythonValue(log)))
                .stream().map(Object::toString).toList();
        }

        /** @see Pm4py#convertToDataframe(EventLog) */
        public DataFrame convertToDataframe(EventLog log) {
            return new DataFrameValue(
                ctx.getBindings().getMember("convert_to_dataframe").execute(pythonValue(log)));
        }

        /** @see Pm4py#convertToEventLog(DataFrame) */
        public EventLog convertToEventLog(DataFrame df) {
            return new EventLogValue(
                ctx.getBindings().getMember("convert_to_event_log").execute(pythonValue(df)));
        }

        /** @see Pm4py#convertToEventStream(EventLog) */
        public EventStream convertToEventStream(EventLog log) {
            return new EventStreamValue(
                ctx.getBindings().getMember("convert_to_event_stream").execute(pythonValue(log)));
        }

        private PetriNetResult toPetriNetResult(Value tuple) {
            return new PetriNetResult(
                new PetriNetValue(tuple.getArrayElement(0)),
                new MarkingValue(tuple.getArrayElement(1)),
                new MarkingValue(tuple.getArrayElement(2)));
        }
    }

    // ─── Private helpers ─────────────────────────────────────────────────────────────

    /** Extracts the GraalPy Value from any pm4py domain type via pattern matching switch. */
    private static Value pythonValue(Object domainType) {
        return switch (domainType) {
            case EventLogValue v    -> v.pythonObject();
            case PetriNetValue v    -> v.pythonObject();
            case MarkingValue v     -> v.pythonObject();
            case ProcessTreeValue v -> v.pythonObject();
            case PowlValue v        -> v.pythonObject();
            case BpmnValue v        -> v.pythonObject();
            case DataFrameValue v   -> v.pythonObject();
            case EventStreamValue v -> v.pythonObject();
            default -> throw new IllegalArgumentException(
                "Unknown pm4py domain type: " + domainType.getClass().getName());
        };
    }

    /** Parses the JSON produced by {@code discover_dfg_as_json} into a typed DfgResult. */
    @SuppressWarnings("unchecked")
    private static DfgResult parseDfgJson(String json) {
        try {
            Map<String, Object> root = MAPPER.readValue(json, Map.class);
            List<Map<String, Object>> edges = (List<Map<String, Object>>) root.get("edges");
            Map<String, Object> startRaw = (Map<String, Object>) root.get("start_activities");
            Map<String, Object> endRaw = (Map<String, Object>) root.get("end_activities");
            return new DfgResult(
                edges == null ? List.of() : List.copyOf(edges),
                toLongMap(startRaw),
                toLongMap(endRaw));
        } catch (Exception e) {
            throw new PythonException(
                "Failed to parse DFG JSON from pm4py bridge: " + e.getMessage(),
                PythonException.ErrorKind.TYPE_CONVERSION_ERROR, e);
        }
    }

    private static Map<String, Long> toLongMap(@Nullable Map<String, Object> raw) {
        if (raw == null) return Map.of();
        Map<String, Long> result = new HashMap<>(raw.size());
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            result.put(entry.getKey(), ((Number) entry.getValue()).longValue());
        }
        return Map.copyOf(result);
    }

    private void preloadScript() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(SCRIPT_RESOURCE)) {
            if (is == null) {
                throw new PythonException(
                    "pm4py bridge script not found on classpath: " + SCRIPT_RESOURCE,
                    PythonException.ErrorKind.RUNTIME_ERROR);
            }
            Path tmp = Files.createTempFile("pm4py_bridge_", ".py");
            try {
                Files.write(tmp, is.readAllBytes());
                engine.evalScriptInAllContexts(tmp);
                log.info("pm4py_bridge.py loaded into all pool contexts");
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw new PythonException(
                "Failed to preload pm4py bridge script: " + e.getMessage(),
                PythonException.ErrorKind.RUNTIME_ERROR, e);
        }
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be null or blank");
        }
    }

    private static void requireConformanceArgs(EventLog log, PetriNet net, Marking im, Marking fm) {
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(net, "net");
        Objects.requireNonNull(im, "im (initial marking)");
        Objects.requireNonNull(fm, "fm (final marking)");
    }
}
