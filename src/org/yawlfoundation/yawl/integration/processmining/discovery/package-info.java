/*
 * Copyright 2004-2026 The YAWL Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Process mining discovery package for YAWL integration.
 *
 * <p>This package provides comprehensive process mining capabilities that enable
 * discovery and analysis of workflow models from execution logs. It implements
 * advanced algorithms for process model extraction, conformance checking, and
 * performance analysis to derive insights from YAWL workflow executions.</p>
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li><strong>Process Model Discovery:</strong> Extracts process models from
 *       YAWL workflow execution logs using heuristic mining and alpha algorithms</li>
 *   <li><strong>Conformance Checking:</strong> Validates discovered models against
 *       actual executions to identify deviations and anomalies</li>
 *   <li><strong>Performance Analysis:</strong> Analyzes workflow performance
 *       metrics including cycle times, resource utilization, and bottlenecks</li>
 *   <li><strong>Log Mining:</strong> Parses and processes YAWL execution logs
 *       to extract behavioral patterns and process instances</li>
 * </ul>
 *
 * <h2>Performance Characteristics:</h2>
 * <ul>
 *   <li>Log processing optimized for large datasets (>1M events)</li>
 *   <li>Memory-efficient algorithms for process model extraction</li>
 *   <li>Parallel processing for conformance checking operations</li>
 *   <li>Incremental mining support for streaming log analysis</li>
 * </ul>
 *
 * <h2>Typical Usage:</h2>
 * <pre>{@code
 * // Discover process model from YAWL execution logs
 * ProcessDiscoveryEngine engine = new HeuristicMiningEngine();
 * ProcessModel model = engine.discoverFromLogs(executionLogs);
 *
 * // Check conformance between discovered and actual executions
 * ConformanceChecker checker = new ConformanceChecker(model);
 * ConformanceResult result = checker.checkConformance(executionTrace);
 *
 * // Analyze performance metrics
 * PerformanceAnalyzer analyzer = new PerformanceAnalyzer(executionLogs);
 * PerformanceMetrics metrics = analyzer.analyzeCycleTimes();
 * }</pre>
 *
 * @since 6.0.0-GA
 */
package org.yawlfoundation.yawl.integration.processmining.discovery;