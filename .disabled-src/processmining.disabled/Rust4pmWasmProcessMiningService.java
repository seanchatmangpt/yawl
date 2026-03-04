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

package org.yawlfoundation.yawl.integration.processmining;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.graalwasm.Rust4pmBridge;
import org.yawlfoundation.yawl.graalwasm.WasmException;

import java.io.IOException;

/**
 * WASM-based implementation of {@link ProcessMiningService} using {@link Rust4pmBridge}
 * for all process mining operations (discovery, conformance, performance analysis).
 *
 * <p><strong>Architecture</strong>:</p>
 * <ul>
 *   <li><strong>OCEL2 conversion</strong>: WASM via @aarkue/process_mining_wasm</li>
 *   <li><strong>Process mining operations</strong>: Java implementations wrapped via WASM bridge</li>
 *   <li><strong>No external service required</strong>: Fully embedded, no HTTP transport</li>
 * </ul>

 * <p>Replaces the previous HTTP-based clients ({@code Rust4PmClient}, {@code ProcessMiningServiceClient})
 * with a pure WASM+Java implementation that requires no separate microservice.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * try (Rust4pmWasmProcessMiningService service = new Rust4pmWasmProcessMiningService()) {
 *     String dfgJson = service.discoverDfg(xesXmlString);
 *     String ocel2Json = service.xesToOcel(xesXmlString);
 *     String fitness = service.tokenReplay(pnmlXml, xesXml);
 * }
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see Rust4pmBridge
 * @see ProcessMiningService
 */
public final class Rust4pmWasmProcessMiningService implements ProcessMiningService, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(Rust4pmWasmProcessMiningService.class);

    private final Rust4pmBridge wasmBridge;
    private volatile boolean closed = false;

    /**
     * Constructs a new WASM-based process mining service.
     *
     * <p>Initializes the {@link Rust4pmBridge} with default settings (poolSize=1).</p>
     *
     * @throws IOException if WASM bridge initialization fails
     */
    public Rust4pmWasmProcessMiningService() throws IOException {
        try {
            this.wasmBridge = new Rust4pmBridge(1);
            log.info("Rust4pmWasmProcessMiningService initialized with WASM bridge");
        } catch (Exception e) {
            throw new IOException("Failed to initialize WASM bridge", e);
        }
    }

    /**
     * Constructs a new WASM-based process mining service with a custom context pool size.
     *
     * @param poolSize number of concurrent WASM contexts (1 for most use cases)
     * @throws IOException if WASM bridge initialization fails
     * @throws IllegalArgumentException if poolSize < 1
     */
    public Rust4pmWasmProcessMiningService(int poolSize) throws IOException {
        try {
            this.wasmBridge = new Rust4pmBridge(poolSize);
            log.info("Rust4pmWasmProcessMiningService initialized with pool size: {}", poolSize);
        } catch (Exception e) {
            throw new IOException("Failed to initialize WASM bridge with pool size: " + poolSize, e);
        }
    }

    /**
     * Run token-based replay conformance check against a Petri net model and event log.
     *
     * <p>Uses WASM bridge (delegating to TokenReplayEngine) to replay tokens through the PNML model
     * according to observed event traces from the XES log.</p>
     *
     * @param pnmlXml PNML-formatted Petri net model (XML string)
     * @param xesXml XES-formatted event log (XML string)
     * @return JSON string with conformance metrics:
     *         <pre>{@code
     *         {
     *           "fitness": 0.0-1.0 (double),
     *           "produced": count of tokens produced (long),
     *           "consumed": count of tokens consumed (long),
     *           "missing": count of missing tokens (long),
     *           "remaining": count of remaining tokens (long),
     *           "deviatingCases": ["case-id-1", "case-id-2", ...]
     *         }
     *         }</pre>
     * @throws IOException if conformance checking fails
     */
    @Override
    public String tokenReplay(String pnmlXml, String xesXml) throws IOException {
        checkNotClosed();
        try {
            return wasmBridge.checkConformance(pnmlXml, xesXml);
        } catch (WasmException e) {
            log.error("Token replay conformance check failed", e);
            throw new IOException("Token replay failed: " + e.getMessage(), e);
        }
    }

    /**
     * Discover a Directly-Follows Graph (DFG) from an event log.
     *
     * <p>Uses WASM bridge (delegating to DirectlyFollowsGraph) to extract
     * activity nodes and directly-follows edges with frequency and duration metrics.</p>
     *
     * @param xesXml XES-formatted event log (XML string)
     * @return JSON string with DFG structure:
     *         <pre>{@code
     *         {
     *           "nodes": [
     *             {"id": "activity-1", "label": "Check Request", "count": 125},
     *             ...
     *           ],
     *           "edges": [
     *             {"source": "activity-1", "target": "activity-2", "count": 120, "avgDuration": 3600000},
     *             ...
     *           ]
     *         }
     *         }</pre>
     * @throws IOException if DFG discovery fails
     */
    @Override
    public String discoverDfg(String xesXml) throws IOException {
        checkNotClosed();
        try {
            return wasmBridge.discoverDfgFromXes(xesXml);
        } catch (WasmException e) {
            log.error("DFG discovery failed", e);
            throw new IOException("DFG discovery failed: " + e.getMessage(), e);
        }
    }

    /**
     * Discover a Petri net using the Alpha algorithm.
     *
     * <p>Uses WASM bridge (delegating to AlphaMiner) to infer a process model from the event log,
     * leveraging van der Aalst's Alpha algorithm (2004) with enhancements.</p>
     *
     * @param xesXml XES-formatted event log (XML string)
     * @return PNML XML string representing the discovered Petri net
     * @throws IOException if discovery fails
     */
    @Override
    public String discoverAlphaPpp(String xesXml) throws IOException {
        checkNotClosed();
        try {
            return wasmBridge.discoverProcessModelFromXes(xesXml);
        } catch (WasmException e) {
            log.error("Alpha Miner discovery failed", e);
            throw new IOException("Alpha discovery failed: " + e.getMessage(), e);
        }
    }

    /**
     * Compute performance statistics from an event log.
     *
     * <p>Analyzes the log to extract flow time, throughput, and activity-level
     * timing metrics useful for identifying bottlenecks.</p>
     *
     * @param xesXml XES-formatted event log (XML string)
     * @return JSON string with performance metrics:
     *         <pre>{@code
     *         {
     *           "traceCount": 500,
     *           "avgFlowTimeMs": 7200000.0,
     *           "throughputPerHour": 3.6,
     *           "activityStats": {
     *             "Check Request": {"count": 500, "avgDurationMs": 900000},
     *             "Approve": {"count": 450, "avgDurationMs": 1200000},
     *             ...
     *           }
     *         }
     *         }</pre>
     * @throws IOException if analysis fails
     */
    @Override
    public String performanceAnalysis(String xesXml) throws IOException {
        checkNotClosed();
        try {
            return wasmBridge.analyzePerformance(xesXml);
        } catch (WasmException e) {
            log.error("Performance analysis failed", e);
            throw new IOException("Performance analysis failed: " + e.getMessage(), e);
        }
    }

    /**
     * Convert XES event log to OCEL 2.0 (Object-Centric Event Log) format.
     *
     * <p>Uses WASM bridge to transform a traditional event log into
     * an object-centric representation where events can be linked to multiple objects.</p>
     *
     * @param xesXml XES-formatted event log (XML string)
     * @return OCEL 2.0 JSON string representing the object-centric event log
     * @throws IOException if conversion fails
     */
    @Override
    public String xesToOcel(String xesXml) throws IOException {
        checkNotClosed();
        try {
            // Use WASM bridge to convert via OCEL2 XML parsing
            return wasmBridge.parseOcel2XmlToJsonString(xesXml);
        } catch (WasmException e) {
            log.error("XES to OCEL conversion failed", e);
            throw new IOException("XES to OCEL conversion failed: " + e.getMessage(), e);
        }
    }

    /**
     * Check if the process mining service is reachable and healthy.
     *
     * <p>For the WASM-based implementation, health is true if the service is initialized
     * (no external service to check; WASM bridge is embedded).</p>
     *
     * @return true if service is initialized and not closed; false otherwise
     */
    @Override
    public boolean isHealthy() {
        return !closed;
    }

    /**
     * Closes the service and releases WASM resources.
     *
     * <p>Idempotent: subsequent calls are no-ops.</p>
     */
    @Override
    public void close() {
        if (!closed) {
            closed = true;
            wasmBridge.close();
            log.info("Rust4pmWasmProcessMiningService closed");
        }
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    /**
     * Ensures the service is not closed.
     *
     * @throws IOException if service is closed
     */
    private void checkNotClosed() throws IOException {
        if (closed) {
            throw new IOException("Rust4pmWasmProcessMiningService is closed");
        }
    }
}
