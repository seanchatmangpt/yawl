/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.processmining;

import java.io.IOException;

/**
 * Client interface for external process mining services (rust4pm, pm4py).
 * Decouples YAWL from any specific implementation, providing a clean abstraction
 * for process mining operations over event logs and Petri net models.
 *
 * All methods are designed to handle large payloads (MB-scale XES/PNML files)
 * and return JSON strings for flexible consumption by the caller.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public interface ProcessMiningService {

    /**
     * Run token-based replay conformance check against a Petri net model and event log.
     *
     * Computes fitness metrics by replaying tokens through the net according to
     * observed event traces. Higher fitness indicates better model-log alignment.
     *
     * @param pnmlXml PNML-formatted Petri net model (XML string)
     * @param xesXml XES-formatted event log (XML string)
     * @return JSON string with conformance metrics:
     *         {
     *           "fitness": 0.0-1.0 (double),
     *           "produced": count of tokens produced (long),
     *           "consumed": count of tokens consumed (long),
     *           "missing": count of missing tokens (long),
     *           "remaining": count of remaining tokens (long),
     *           "deviatingCases": ["case-id-1", "case-id-2", ...]
     *         }
     * @throws IOException if service is unavailable or request fails
     */
    String tokenReplay(String pnmlXml, String xesXml) throws IOException;

    /**
     * Discover a Directly-Follows Graph (DFG) from an event log.
     *
     * Builds a graph where nodes represent activities and edges represent
     * directly-follows relationships observed in the log. Includes frequency
     * and duration metrics for each edge.
     *
     * @param xesXml XES-formatted event log (XML string)
     * @return JSON string with DFG structure:
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
     * @throws IOException if service is unavailable or request fails
     */
    String discoverDfg(String xesXml) throws IOException;

    /**
     * Discover a Petri net using the Alpha+++ algorithm.
     *
     * Infers a process model from the event log using Alpha-family techniques
     * with enhancements for handling loops and duplicate activities.
     *
     * @param xesXml XES-formatted event log (XML string)
     * @return PNML XML string representing the discovered Petri net
     * @throws IOException if service is unavailable or request fails
     */
    String discoverAlphaPpp(String xesXml) throws IOException;

    /**
     * Compute performance statistics from an event log.
     *
     * Analyzes the log to extract flow time, throughput, and activity-level
     * timing metrics. Useful for identifying bottlenecks and process improvement areas.
     *
     * @param xesXml XES-formatted event log (XML string)
     * @return JSON string with performance metrics:
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
     * @throws IOException if service is unavailable or request fails
     */
    String performanceAnalysis(String xesXml) throws IOException;

    /**
     * Convert XES event log to OCEL 2.0 (Object-Centric Event Log) format.
     *
     * Transforms a traditional event log into an object-centric representation
     * where events can be linked to multiple objects (e.g., order, invoice, shipment).
     *
     * @param xesXml XES-formatted event log (XML string)
     * @return OCEL 2.0 JSON string representing the object-centric event log
     * @throws IOException if service is unavailable or request fails
     */
    String xesToOcel(String xesXml) throws IOException;

    /**
     * Check if the process mining service is reachable and healthy.
     *
     * Performs a synchronous health check by sending a request to the service.
     * This method blocks until the health check completes or times out.
     *
     * @return true if the service is reachable and responds with a healthy status,
     *         false if unreachable, unresponsive, or returns a non-healthy status
     */
    boolean isHealthy();
}
