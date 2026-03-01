/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

/**
 * Object-Centric Process Mining (OCPM) discovery and analysis.
 *
 * <h2>Overview</h2>
 * <p>This package implements van der Aalst's object-centric process mining (OCPM)
 * techniques for analyzing OCEL 2.0 (Object-Centric Event Log v2.0) logs.</p>
 *
 * <h2>Key Components</h2>
 * <ul>
 *   <li><b>OcpmInput</b> - OCEL 2.0 data model with events and objects</li>
 *   <li><b>ObjectCentricDFG</b> - Per-object-type directly-follows graphs</li>
 *   <li><b>OcpmDiscovery</b> - Discovers object-centric Petri nets (OCPN)</li>
 *   <li><b>OcpmConformanceChecker</b> - Token replay adapted for OCPN</li>
 *   <li><b>OcpmPerformanceAnalyzer</b> - Cycle time, throughput, wait time analysis</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>
 * // Parse OCEL 2.0 log
 * OcpmInput input = OcpmInput.fromOcel2Json(ocel2JsonString);
 *
 * // Discover OCPN
 * OcpmDiscovery discovery = new OcpmDiscovery();
 * OcpmDiscovery.OcpmResult model = discovery.discover(input);
 *
 * // Check conformance
 * OcpmConformanceChecker.OcpmConformanceResult conformance =
 *     OcpmConformanceChecker.check(input, model);
 *
 * // Analyze performance
 * OcpmPerformanceAnalyzer.OcpmPerformanceResult performance =
 *     OcpmPerformanceAnalyzer.analyze(input);
 * </pre>
 *
 * <h2>OCEL 2.0 Data Model</h2>
 * <p>OCEL 2.0 distinguishes between events and objects:
 * <ul>
 *   <li><b>Events</b> - low-level activities with timestamps and attribute values</li>
 *   <li><b>Objects</b> - domain entities (cases, items, resources) with type and attributes</li>
 *   <li><b>Object map (omap)</b> - links events to the objects they operate on</li>
 * </ul>
 *
 * <p>This enables analysis of complex processes where multiple object types
 * interact (e.g., cases with line items, resources, and suppliers).</p>
 *
 * <h2>Algorithm Overview</h2>
 * <p><b>Discovery:</b>
 * <ol>
 *   <li>For each object type, extract its event sequence (per object)</li>
 *   <li>Build directly-follows graph (DFG) for each object type</li>
 *   <li>Convert DFGs to per-type Petri nets (Alpha-like)</li>
 *   <li>Share transitions (activities) across object types</li>
 * </ol>
 *
 * <p><b>Conformance:</b>
 * <ol>
 *   <li>For each object type, replay each object's trace against its Petri net</li>
 *   <li>Compute token-based fitness per object</li>
 *   <li>Average fitness per object type and overall</li>
 * </ol>
 *
 * <p><b>Performance:</b>
 * <ol>
 *   <li>Compute cycle time = time(first event) to time(last event) per object</li>
 *   <li>Compute throughput = (object count) / (log timespan)</li>
 *   <li>Compute wait times between consecutive activities</li>
 * </ol>
 *
 * @author YAWL Foundation (Wil van der Aalst, Object-Centric PM)
 * @version 6.0
 */
package org.yawlfoundation.yawl.integration.processmining.ocpm;
