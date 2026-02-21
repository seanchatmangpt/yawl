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

/**
 * Process Mining Integration for YAWL v6.0.0.
 *
 * <h2>Overview</h2>
 * This package integrates YAWL's workflow engine with advanced process mining
 * capabilities via the Rust4PM crate (crates.io {@code process_mining}).
 * It enables real-time process discovery, conformance checking, and performance
 * analysis on YAWL event logs, with results exposed to autonomous agents via
 * MCP tools.
 *
 * <h2>Architecture</h2>
 *
 * <h3>Integration Flow</h3>
 * <pre>
 *   YAWL Engine                  Java Integration             Rust4PM Bridge
 *   (YEngine)                    (processmining)              (HTTP REST)
 *       |                               |                           |
 *       | YWorkItem events              |                           |
 *       | (completed, failed, etc.)     |                           |
 *       +------&gt; EventLogExporter ------&gt; XES/OCEL2 -----&gt; Rust4PmClient
 *                                        |                       |
 *                                        | &lt;--- JSON Results ---+
 *                                        |
 *                  ProcessDiscoveryResult
 *                  ConformanceAnalyzer
 *                  PerformanceAnalyzer
 *                        |
 *                        | ProcessMiningSession (durable)
 *                        |
 *                        +---&gt; MCP Tools (for autonomous agents)
 * </pre>
 *
 * <h3>Core Components</h3>
 *
 * <h4>{@link org.yawlfoundation.yawl.integration.processmining.EventLogExporter}</h4>
 * Exports YAWL workflow execution history to standard process mining formats.
 * <ul>
 *   <li><strong>Input</strong>: YSpecificationID, scope (all cases or specific case)</li>
 *   <li><strong>Output</strong>: XES (eXtensible Event Stream, IEEE 5679-2016 standard)</li>
 *   <li><strong>Integration</strong>: Uses InterfaceB (client) + InterfaceE (log gateway)
 *       to retrieve event logs from engine</li>
 *   <li><strong>Durable</strong>: Exports can be cached to disk or streamed to process
 *       mining backend</li>
 * </ul>
 *
 * <h4>{@link org.yawlfoundation.yawl.integration.processmining.ConformanceAnalyzer}</h4>
 * Computes how well actual execution traces conform to a reference model
 * (fitness) and how much of the reference model is covered (precision).
 * <ul>
 *   <li><strong>Fitness</strong>: Fraction of traces that replay without violation
 *       (token-based replay)</li>
 *   <li><strong>Precision</strong>: Fraction of directly-follows relations in model
 *       that appear in observed traces</li>
 *   <li><strong>Method</strong>: Direct comparison of observed activity sequences
 *       to expected transitions (simplified)</li>
 *   <li><strong>Result</strong>: {@code ConformanceResult} with trace metrics and
 *       deviating trace IDs</li>
 * </ul>
 *
 * <h4>{@link org.yawlfoundation.yawl.integration.processmining.PerformanceAnalyzer}</h4>
 * Extracts performance metrics from event logs:
 * <ul>
 *   <li><strong>Flow Time</strong>: End-to-end case duration (milliseconds)</li>
 *   <li><strong>Throughput</strong>: Cases per hour</li>
 *   <li><strong>Activity Frequency</strong>: Execution count per activity</li>
 *   <li><strong>Waiting Time</strong>: Average time between successive activities</li>
 *   <li><strong>Result</strong>: {@code PerformanceResult} with aggregated statistics</li>
 * </ul>
 *
 * <h4>{@link org.yawlfoundation.yawl.integration.processmining.ProcessDiscoveryResult}</h4>
 * Captures output from Rust4PM process discovery algorithms (Alpha, Heuristic, DFG).
 * <ul>
 *   <li><strong>processModelJson</strong>: Discovered Petri net or DFG as JSON graph
 *       (nodes, arcs, marking)</li>
 *   <li><strong>fitness</strong>: Conformance fitness (0.0-1.0)</li>
 *   <li><strong>precision</strong>: Conformance precision (0.0-1.0)</li>
 *   <li><strong>caseCount</strong>: Number of cases analyzed</li>
 *   <li><strong>activityCount</strong>: Distinct activities discovered</li>
 *   <li><strong>activityFrequencies</strong>: Execution frequency per activity</li>
 *   <li><strong>analyzedAt</strong>: Timestamp of analysis</li>
 *   <li><strong>Immutable</strong>: Java 25 record with natural equality/serialization</li>
 * </ul>
 *
 * <h3>Rust4PM REST Bridge (Rust4PmClient)</h3>
 * HTTP-based adapter to Rust4PM process mining service.
 * <ul>
 *   <li><strong>Protocol</strong>: HTTP/1.1 or HTTP/2 REST</li>
 *   <li><strong>Endpoints</strong>:
 *     <ul>
 *       <li>{@code POST /discover} - Run process discovery (Alpha, Heuristic, DFG)</li>
 *       <li>{@code POST /analyze} - Conformance + performance analysis</li>
 *       <li>{@code POST /export-ocel2} - Convert XES to OCEL2 (object-centric)</li>
 *       <li>{@code GET /status} - Health check</li>
 *     </ul>
 *   </li>
 *   <li><strong>Input Format</strong>: XES XML or OCEL2 JSON</li>
 *   <li><strong>Output Format</strong>: JSON (ProcessDiscoveryResult, analysis metrics)</li>
 *   <li><strong>Error Handling</strong>: Throws checked exceptions for network/parsing
 *       errors; caller responsible for retry logic</li>
 *   <li><strong>Timeout</strong>: Configurable per request (default 60s for discovery,
 *       10s for status)</li>
 * </ul>
 *
 * <h3>OCEL2 Export (Ocel2Exporter)</h3>
 * Object-Centric Event Log (OCEL2) export for analyzing object interactions.
 * <ul>
 *   <li><strong>Why OCEL2</strong>: XES is activity-centric (traces of activities);
 *       OCEL2 is object-centric (how objects interact across workflow)</li>
 *   <li><strong>Objects</strong>: Case ID, work item ID, resource ID (role), document ID</li>
 *   <li><strong>Events</strong>: Activity executions with object references</li>
 *   <li><strong>Use Case</strong>: Analyzing object flows, inter-case dependencies,
 *       resource allocation patterns</li>
 *   <li><strong>Integration</strong>: Rust4PmClient converts XES to OCEL2 via
 *       {@code POST /export-ocel2}</li>
 * </ul>
 *
 * <h3>ProcessMiningSession (Durable)</h3>
 * Persistent session object for managing long-running analysis workflows.
 * <ul>
 *   <li><strong>State</strong>: session ID, specification ID, analysis timestamp,
 *       results cache</li>
 *   <li><strong>Lifecycle</strong>:
 *     <ul>
 *       <li>Create: {@code new ProcessMiningSession(specId)}</li>
 *       <li>Export: {@code exporter.exportSpecificationToXes(specId, withData)}</li>
 *       <li>Analyze: {@code Rust4PmClient.discover(xes) / analyze(xes)}</li>
 *       <li>Store: {@code session.cacheResult(ProcessDiscoveryResult)}</li>
 *       <li>Query: {@code session.getLatestResult()} for MCP tool access</li>
 *     </ul>
 *   </li>
 *   <li><strong>Persistence</strong>: Session persisted to disk (JSON) or database
 *       for replay/audit</li>
 *   <li><strong>Concurrent Access</strong>: Thread-safe; multiple agents can query
 *       same session</li>
 * </ul>
 *
 * <h3>GregverseSimulator Orchestration</h3>
 * High-level orchestrator for automated process mining + feedback simulation.
 * <ul>
 *   <li><strong>Purpose</strong>: Run end-to-end process mining workflow with
 *       configurable discovery algorithm, conformance thresholds, and feedback rules</li>
 *   <li><strong>Algorithm</strong>:
 *     <ol>
 *       <li>Load YAWL specification (processDefinition)</li>
 *       <li>Export current execution log to XES</li>
 *       <li>Run process discovery (Alpha/Heuristic/DFG) on Rust4PM</li>
 *       <li>Compare discovered model to specification (conformance check)</li>
 *       <li>Extract performance bottlenecks (flow time, throughput)</li>
 *       <li>Generate feedback: "Model drifts from specification by {drift %}"
 *           or "Bottleneck: {activity} takes {duration}ms avg"</li>
 *       <li>Cache results to ProcessMiningSession</li>
 *       <li>Surface results to MCP tools for autonomous agent decision-making</li>
 *     </ol>
 *   </li>
 *   <li><strong>Configuration</strong>:
 *     <ul>
 *       <li>{@code discoveryAlgorithm}: ALPHA | HEURISTIC | DFG (default: HEURISTIC)</li>
 *       <li>{@code fitnessCriteria}: Minimum fitness threshold (default: 0.8)</li>
 *       <li>{@code precisionCriteria}: Minimum precision threshold (default: 0.8)</li>
 *       <li>{@code performanceThresholds}: Alert if avg flow time exceeds (ms)</li>
 *     </ul>
 *   </li>
 *   <li><strong>Feedback Loop</strong>: Results trigger MCP "suggest_optimization"
 *       tool with specific action (e.g., parallelize task, add resource, reduce
 *       data validation)</li>
 * </ul>
 *
 * <h2>MCP Integration (Autonomous Agents)</h2>
 *
 * Process mining results are exposed to AI agents via MCP tools:
 *
 * <ul>
 *   <li><strong>{@code discover_process}</strong>: Invoke process discovery on
 *       specification; returns ProcessDiscoveryResult</li>
 *   <li><strong>{@code get_conformance}</strong>: Check conformance of current log
 *       against specification; returns fitness/precision scores</li>
 *   <li><strong>{@code get_performance}</strong>: Extract performance metrics
 *       (flow time, throughput, bottlenecks); triggers optimization recommendations</li>
 *   <li><strong>{@code export_event_log}</strong>: Fetch XES or OCEL2 event log
 *       (used for external analysis tools)</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * // 1. Initialize exporter (connects to YAWL engine)
 * EventLogExporter exporter = new EventLogExporter("http://localhost:8080", "admin", "pass");
 *
 * // 2. Export XES log for specification
 * YSpecificationID specId = new YSpecificationID("MyWorkflow", "1.0", "root");
 * String xesLog = exporter.exportSpecificationToXes(specId, true);
 *
 * // 3. Send to Rust4PM for discovery
 * Rust4PmClient client = new Rust4PmClient("http://rust4pm:8000");
 * ProcessDiscoveryResult discovery = client.discover(xesLog, "HEURISTIC");
 *
 * // 4. Cache result to session
 * ProcessMiningSession session = new ProcessMiningSession(specId);
 * session.cacheResult(discovery);
 *
 * // 5. Query results for MCP tools
 * ProcessDiscoveryResult latest = session.getLatestResult();
 * System.out.println("Discovered model: " + latest.processModelJson());
 * System.out.println("Fitness: " + latest.fitness());
 * System.out.println("Precision: " + latest.precision());
 * }</pre>
 *
 * <h2>Thread Safety &amp; Concurrency</h2>
 *
 * <ul>
 *   <li><strong>EventLogExporter</strong>: Single-threaded session management
 *       (thread-safe per session, reuse exporter across threads)</li>
 *   <li><strong>Rust4PmClient</strong>: Thread-safe HTTP client; concurrent
 *       requests to different endpoints allowed</li>
 *   <li><strong>ProcessMiningSession</strong>: Virtual threads safe; results cache
 *       is immutable (ProcessDiscoveryResult is Java 25 record)</li>
 *   <li><strong>Analyzers</strong>: Stateless; safe for concurrent analysis</li>
 * </ul>
 *
 * <h2>Error Handling</h2>
 *
 * <ul>
 *   <li><strong>EventLogExporter</strong>: Throws {@code IOException} on connection
 *       or export failure; caller retries with exponential backoff</li>
 *   <li><strong>Rust4PmClient</strong>: Throws {@code IOException} for HTTP errors
 *       or timeouts; throws {@code ParseException} for malformed JSON responses</li>
 *   <li><strong>Analyzers</strong>: Log warnings on parse failure; return empty
 *       results rather than throwing (graceful degradation)</li>
 *   <li><strong>ProcessMiningSession</strong>: Validates immutable result invariants
 *       at construction; throws {@code IllegalArgumentException} if invalid</li>
 * </ul>
 *
 * <h2>Performance Considerations</h2>
 *
 * <ul>
 *   <li><strong>XES Export</strong>: O(cases × activities); large logs (1M+ events)
 *       should use pagination or OCEL2 for object filtering</li>
 *   <li><strong>Discovery Algorithms</strong>: Heuristic miner is O(n²) on unique
 *       directly-follows pairs; Alpha is O(n³) worst-case; DFG is O(n)</li>
 *   <li><strong>Caching</strong>: Cache ProcessDiscoveryResult in ProcessMiningSession
 *       to avoid re-running discovery on same log</li>
 *   <li><strong>Virtual Threads</strong>: Use {@code Executors.newVirtualThreadPerTaskExecutor()}
 *       for parallel discovery across multiple specifications</li>
 * </ul>
 *
 * <h2>Related Packages</h2>
 *
 * <ul>
 *   <li>{@code org.yawlfoundation.yawl.engine} - YEngine, YWorkItem, YSpecification</li>
 *   <li>{@code org.yawlfoundation.yawl.engine.interfce.interfaceB} - Client interface
 *       for case/work item queries</li>
 *   <li>{@code org.yawlfoundation.yawl.engine.interfce.interfaceE} - Log gateway for
 *       XES export</li>
 *   <li>{@code org.yawlfoundation.yawl.integration} - Other integration adapters
 *       (MCP, A2A, webhook)</li>
 * </ul>
 *
 * @since YAWL 6.0
 * @author YAWL Foundation
 * @version 6.0
 */
package org.yawlfoundation.yawl.integration.processmining;
