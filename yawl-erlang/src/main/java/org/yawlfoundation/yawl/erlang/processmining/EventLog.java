/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * Java API that mirrors the Rust process_mining library API exactly.
 * See: https://docs.rs/process_mining/latest/process_mining/
 */
package org.yawlfoundation.yawl.erlang.processmining;

import org.yawlfoundation.yawl.erlang.error.ErlangRpcException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Traditional (case-centric) event log representation.
 *
 * <p>This class mirrors the Rust {@code EventLog} struct from the process_mining crate.
 * A Java developer can read the Rust docs and use the equivalent Java API:
 *
 * <h2>Rust → Java API Mapping</h2>
 * <pre>{@code
 * // ═══════════════════════════════════════════════════════════════
 * // RUST (from process_mining crate docs)
 * // ═══════════════════════════════════════════════════════════════
 * use process_mining::{EventLog, Importable};
 *
 * let log = EventLog::import_from_path(&path)?;
 * println!("Traces: {}", log.traces.len());
 * let total: usize = log.traces.iter().map(|t| t.events.len()).sum();
 * println!("Total events: {}", total);
 * let avg = total as f64 / log.traces.len() as f64;
 * println!("Avg events per trace: {:.2}", avg);
 *
 * // ═══════════════════════════════════════════════════════════════
 * // JAVA (equivalent - same method names, same behavior)
 * // ═══════════════════════════════════════════════════════════════
 * import org.yawlfoundation.yawl.erlang.processmining.EventLog;
 *
 * EventLog log = EventLog.importFromPath(path);
 * System.out.println("Traces: " + log.traces().size());
 * int total = log.traces().stream().mapToInt(t -> t.events().size()).sum();
 * System.out.println("Total events: " + total);
 * double avg = (double) total / log.traces().size();
 * System.out.printf("Avg events per trace: %.2f%n", avg);
 * }</pre>
 *
 * @see <a href="https://docs.rs/process_mining/latest/process_mining/struct.EventLog.html">Rust EventLog docs</a>
 */
public final class EventLog {

    private final List<Trace> traces;
    private final ProcessMining pm;

    /**
     * Creates an EventLog from trace data.
     */
    EventLog(ProcessMining pm, List<Trace> traces) {
        this.pm = Objects.requireNonNull(pm);
        this.traces = Objects.requireNonNull(traces);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STATIC FACTORY METHODS (mirror Rust EventLog::import_from_path, etc.)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Imports an event log from a file path (XES format).
     *
     * <p><b>Rust equivalent:</b>
     * <pre>{@code let log = EventLog::import_from_path(&path)?;}</pre>
     *
     * @param path path to the XES file
     * @return the imported event log
     * @throws IOException if the file cannot be read
     * @throws ProcessMiningException if parsing fails
     */
    public static EventLog importFromPath(Path path) throws IOException, ProcessMiningException {
        // XES parsing would be implemented here
        throw new UnsupportedOperationException(
            "XES import not yet implemented. Use fromTraces() for trace-based logs."
        );
    }

    /**
     * Creates an event log from a list of traces (activity sequences).
     *
     * <p>Each trace is a list of activity names representing one case.
     *
     * <p><b>Rust equivalent:</b> Creating EventLog from traces manually
     *
     * <pre>{@code
     * // Java
     * EventLog log = EventLog.fromTraces(List.of(
     *     List.of("a", "b", "c", "d"),
     *     List.of("a", "b", "c", "e"),
     *     List.of("a", "b", "d", "c")
     * ));
     * }</pre>
     *
     * @param traces list of traces, each trace is a list of activity names
     * @return the event log
     */
    public static EventLog fromTraces(List<List<String>> traces) {
        Objects.requireNonNull(traces);
        List<Trace> traceList = new ArrayList<>();
        for (List<String> activities : traces) {
            List<Event> events = new ArrayList<>();
            for (String activity : activities) {
                events.add(new Event(activity, null, List.of()));
            }
            traceList.add(new Trace(events, List.of()));
        }
        return new EventLog(ProcessMining.getDefault(), traceList);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FIELD ACCESSORS (mirror Rust log.traces, etc.)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Returns the list of traces in this log.
     *
     * <p><b>Rust equivalent:</b>
     * <pre>{@code log.traces}</pre>
     * <p><b>Rust type:</b> {@code Vec<Trace>}
     *
     * @return list of traces
     */
    public List<Trace> traces() {
        return traces;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONVENIENCE METHODS (mirror common Rust patterns like log.traces.len())
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Returns the number of traces in this log.
     *
     * <p><b>Rust equivalent:</b>
     * <pre>{@code log.traces.len()}</pre>
     */
    public int traceCount() {
        return traces.size();
    }

    /**
     * Returns the total number of events across all traces.
     *
     * <p><b>Rust equivalent:</b>
     * <pre>{@code log.traces.iter().map(|t| t.events.len()).sum()}</pre>
     */
    public int totalEvents() {
        return traces.stream().mapToInt(t -> t.events().size()).sum();
    }

    /**
     * Returns the average number of events per trace.
     *
     * <p><b>Rust equivalent:</b>
     * <pre>{@code total as f64 / log.traces.len() as f64}</pre>
     */
    public double avgEventsPerTrace() {
        if (traces.isEmpty()) return 0.0;
        return (double) totalEvents() / traceCount();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PROCESS DISCOVERY (mirror Rust discover_dfg, alphappp_discover_petri_net)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Discovers a Directly-Follows Graph from this event log.
     *
     * <p><b>Rust equivalent:</b>
     * <pre>{@code let dfg = discover_dfg(&log);}</pre>
     *
     * @return the discovered DFG
     */
    public DFG discoverDFG() throws ProcessMiningException {
        List<List<String>> traceActivities = new ArrayList<>();
        for (Trace trace : traces) {
            List<String> activities = new ArrayList<>();
            for (Event event : trace.events()) {
                activities.add(event.name());
            }
            traceActivities.add(activities);
        }
        return pm.discoverDfgFromTraces(traceActivities);
    }

    /**
     * Discovers a Petri net using Alpha+++ algorithm.
     *
     * <p><b>Rust equivalent:</b>
     * <pre>{@code
     * let projection = EventLogActivityProjection::from(&log);
     * let petri_net = alphappp_discover_petri_net(&projection, AlphaPPPConfig::default());
     * }</pre>
     *
     * @return the discovered Petri net
     */
    public PetriNet discoverAlphaPPP() throws ProcessMiningException {
        return pm.discoverAlphaPPP(this);
    }

    /**
     * Computes token replay conformance for this log against a Petri net.
     *
     * <p><b>Rust equivalent:</b>
     * <pre>{@code let fitness = token_replay_conformance(&log, &net);}</pre>
     *
     * @param pnml PNML string defining the Petri net
     * @return fitness score (0.0 to 1.0)
     */
    public double tokenReplayConformance(String pnml) throws ProcessMiningException {
        return pm.tokenReplayConformance(this, pnml);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NESTED TYPES (mirror Rust Trace, Event)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * A single trace (case) in an event log.
     *
     * <p><b>Rust equivalent:</b> {@code Trace}
     */
    public record Trace(
        List<Event> events,
        List<Attribute> attributes
    ) {}

    /**
     * A single event in a trace.
     *
     * <p><b>Rust equivalent:</b> {@code Event}
     */
    public record Event(
        String name,
        String timestamp,
        List<Attribute> attributes
    ) {}

    /**
     * An attribute on a trace or event.
     *
     * <p><b>Rust equivalent:</b> {@code Attribute}
     */
    public record Attribute(
        String key,
        Object value
    ) {}
}
