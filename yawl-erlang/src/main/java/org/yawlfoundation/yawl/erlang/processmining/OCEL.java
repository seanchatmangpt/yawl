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
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Object-Centric Event Log (OCEL) representation.
 *
 * <p>This class mirrors the Rust {@code OCEL} struct from the process_mining crate.
 * A Java developer can read the Rust docs and use the equivalent Java API:
 *
 * <h2>Rust → Java API Mapping</h2>
 * <pre>{@code
 * // ═══════════════════════════════════════════════════════════════
 * // RUST (from process_mining crate docs)
 * // ═══════════════════════════════════════════════════════════════
 * use process_mining::{OCEL, Importable};
 *
 * let ocel = OCEL::import_from_path(&path)?;
 * println!("Events: {}", ocel.events.len());
 * println!("Objects: {}", ocel.objects.len());
 *
 * // ═══════════════════════════════════════════════════════════════
 * // JAVA (equivalent - same method names, same behavior)
 * // ═══════════════════════════════════════════════════════════════
 * import org.yawlfoundation.yawl.erlang.processmining.OCEL;
 *
 * OCEL ocel = OCEL.importFromPath(path);
 * System.out.println("Events: " + ocel.events().size());
 * System.out.println("Objects: " + ocel.objects().size());
 * }</pre>
 *
 * @see <a href="https://docs.rs/process_mining/latest/process_mining/ocel/struct.OCEL.html">Rust OCEL docs</a>
 */
public final class OCEL implements AutoCloseable {

    // Internal handle to the Erlang/NIF resource
    private final String handle;
    private final ProcessMining pm;
    private List<Event> events;
    private List<Obj> objects;
    private boolean closed = false;

    /**
     * Internal constructor - use {@link #importFromPath} or {@link #importFromJson}.
     */
    OCEL(ProcessMining pm, String handle) {
        this.pm = Objects.requireNonNull(pm);
        this.handle = Objects.requireNonNull(handle);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STATIC FACTORY METHODS (mirror Rust OCEL::import_from_path, etc.)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Imports an OCEL log from a file path.
     *
     * <p><b>Rust equivalent:</b>
     * <pre>{@code let ocel = OCEL::import_from_path(&path)?;}</pre>
     *
     * @param path path to the OCEL file (JSON or XML format)
     * @return the imported OCEL log
     * @throws IOException if the file cannot be read
     * @throws ProcessMiningException if parsing fails
     */
    public static OCEL importFromPath(Path path) throws IOException, ProcessMiningException {
        String content = Files.readString(path);
        if (path.toString().endsWith(".json")) {
            return importFromJson(content);
        } else {
            return importFromXml(content);
        }
    }

    /**
     * Imports an OCEL log from a JSON string.
     *
     * <p><b>Rust equivalent:</b> Parsing OCEL2 JSON directly
     *
     * @param json OCEL2 JSON content
     * @return the imported OCEL log
     * @throws ProcessMiningException if parsing fails
     */
    public static OCEL importFromJson(String json) throws ProcessMiningException {
        return ProcessMining.getDefault().parseOcel2(json);
    }

    /**
     * Imports an OCEL log from an XML string.
     *
     * <p><b>Rust equivalent:</b> Parsing OCEL-XML format
     *
     * @param xml OCEL-XML content
     * @return the imported OCEL log
     * @throws ProcessMiningException if parsing fails
     */
    public static OCEL importFromXml(String xml) throws ProcessMiningException {
        return ProcessMining.getDefault().parseOcel2Xml(xml);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FIELD ACCESSORS (mirror Rust ocel.events, ocel.objects, etc.)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Returns the list of events in this log.
     *
     * <p><b>Rust equivalent:</b>
     * <pre>{@code ocel.events}</pre>
     * <p><b>Rust type:</b> {@code Vec<OcelEvent>}
     *
     * @return list of events (lazy-loaded on first access)
     */
    public List<Event> events() throws ProcessMiningException {
        ensureOpen();
        if (events == null) {
            events = pm.getOcelEvents(handle);
        }
        return events;
    }

    /**
     * Returns the list of objects in this log.
     *
     * <p><b>Rust equivalent:</b>
     * <pre>{@code ocel.objects}</pre>
     * <p><b>Rust type:</b> {@code Vec<OcelObject>}
     *
     * @return list of objects (lazy-loaded on first access)
     */
    public List<Obj> objects() throws ProcessMiningException {
        ensureOpen();
        if (objects == null) {
            objects = pm.getOcelObjects(handle);
        }
        return objects;
    }

    /**
     * Returns the event types defined in this log.
     *
     * <p><b>Rust equivalent:</b>
     * <pre>{@code ocel.event_types}</pre>
     * <p><b>Rust type:</b> {@code Vec<OcelEventType>}
     */
    public List<EventType> eventTypes() throws ProcessMiningException {
        ensureOpen();
        return pm.getOcelEventTypes(handle);
    }

    /**
     * Returns the object types defined in this log.
     *
     * <p><b>Rust equivalent:</b>
     * <pre>{@code ocel.object_types}</pre>
     * <p><b>Rust type:</b> {@code Vec<OcelObjectType>}
     */
    public List<ObjectType> objectTypes() throws ProcessMiningException {
        ensureOpen();
        return pm.getOcelObjectTypes(handle);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONVENIENCE METHODS (mirror common Rust patterns like ocel.events.len())
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Returns the number of events in this log.
     *
     * <p><b>Rust equivalent:</b>
     * <pre>{@code ocel.events.len()}</pre>
     */
    public int eventCount() throws ProcessMiningException {
        ensureOpen();
        return pm.getOcelEventCount(handle);
    }

    /**
     * Returns the number of objects in this log.
     *
     * <p><b>Rust equivalent:</b>
     * <pre>{@code ocel.objects.len()}</pre>
     */
    public int objectCount() throws ProcessMiningException {
        ensureOpen();
        return pm.getOcelObjectCount(handle);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PROCESS DISCOVERY (mirror Rust discover_dfg, etc.)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Discovers a Directly-Follows Graph from this OCEL log.
     *
     * <p><b>Rust equivalent:</b>
     * <pre>{@code let dfg = discover_dfg(&ocel);}</pre>
     *
     * @return the discovered DFG
     */
    public DFG discoverDFG() throws ProcessMiningException {
        ensureOpen();
        return pm.discoverDfgFromOcel(handle);
    }

    /**
     * Checks conformance against a Petri net model using token replay.
     *
     * <p><b>Rust equivalent:</b>
     * <pre>{@code let metrics = check_conformance(&ocel, &petri_net);}</pre>
     *
     * @param pnml PNML string defining the Petri net
     * @return conformance metrics (fitness, precision, etc.)
     */
    public ConformanceMetrics checkConformance(String pnml) throws ProcessMiningException {
        ensureOpen();
        return pm.checkConformance(handle, pnml);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INTERNAL
    // ═══════════════════════════════════════════════════════════════════════════

    String handle() {
        return handle;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("OCEL log has been closed");
        }
    }

    @Override
    public void close() {
        closed = true;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NESTED TYPES (mirror Rust OcelEvent, OcelObject, etc.)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * An event in an OCEL log.
     *
     * <p><b>Rust equivalent:</b> {@code OcelEvent}
     */
    public record Event(
        String id,
        String type,
        String time,
        List<Attribute> attributes,
        List<Relationship> relationships
    ) {}

    /**
     * An object in an OCEL log.
     *
     * <p><b>Rust equivalent:</b> {@code OcelObject}
     *
     * <p>Named {@code Obj} instead of {@code Object} to avoid conflict with {@link java.lang.Object}.
     */
    public record Obj(
        String id,
        String type,
        List<Attribute> attributes
    ) {}

    /**
     * An attribute on an event or object.
     *
     * <p><b>Rust equivalent:</b> {@code OcelAttribute}
     */
    public record Attribute(
        String name,
        Object value
    ) {}

    /**
     * A relationship between an event and an object.
     *
     * <p><b>Rust equivalent:</b> {@code OcelRelationship}
     */
    public record Relationship(
        String objectId,
        String qualifier
    ) {}

    /**
     * An event type definition.
     *
     * <p><b>Rust equivalent:</b> {@code OcelEventType}
     */
    public record EventType(
        String name,
        List<String> attributes
    ) {}

    /**
     * An object type definition.
     *
     * <p><b>Rust equivalent:</b> {@code OcelObjectType}
     */
    public record ObjectType(
        String name,
        List<String> attributes
    ) {}
}
