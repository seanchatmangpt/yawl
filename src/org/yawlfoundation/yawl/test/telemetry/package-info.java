/**
 * Multi-Agent Telemetry Test Framework for YAWL v6.0.0.
 *
 * <h2>Overview</h2>
 * <p>
 * Coordinates 5 independent test agents executing different test suites in parallel,
 * with comprehensive telemetry collection (token counts, concurrency, latency) and
 * production-style ANDON monitoring (P0-P3 alert levels).
 * </p>
 *
 * <h2>5 Test Agents</h2>
 * <ol>
 *   <li><strong>EngineTestAgent</strong> - Core YAWL engine tests (YNetRunner, state machine)</li>
 *   <li><strong>StatelessTestAgent</strong> - Stateless execution with H2 snapshot isolation</li>
 *   <li><strong>IntegrationTestAgent</strong> - Cross-module integration tests (MCP/A2A)</li>
 *   <li><strong>A2ATestAgent</strong> - Agent-to-Agent protocol tests</li>
 *   <li><strong>AutonomousAgentTestAgent</strong> - Autonomous agent pattern tests</li>
 * </ol>
 *
 * <h2>LLM Integration</h2>
 * <ul>
 *   <li><strong>Groq API</strong> - For test scenario generation (Agents 1, 2, 5)</li>
 *   <li><strong>OpenAI gpt-oss-20b</strong> - For integration test validation (Agent 3)</li>
 *   <li><strong>ANDON Gate</strong> - Checks LLM availability before test execution</li>
 *   <li><strong>Token Tracking</strong> - Records all token counts per LLM</li>
 * </ul>
 *
 * <h2>Telemetry Metrics</h2>
 * <p>
 * OpenTelemetry (OTEL) integration tracks:
 * </p>
 * <ul>
 *   <li>Token counts (input + output) per LLM</li>
 *   <li>Concurrency: active agents, virtual threads, queue depth</li>
 *   <li>Latency: p50, p95, p99 percentiles (milliseconds)</li>
 *   <li>Throughput: tests/second per agent</li>
 *   <li>Test counts: run, passed, failed, skipped</li>
 * </ul>
 *
 * <h2>ANDON Monitoring (Production Alerts)</h2>
 * <table border="1">
 *   <tr><th>Severity</th><th>Trigger</th><th>Action</th></tr>
 *   <tr><td>P0 CRITICAL</td><td>No LLM available</td><td>STOP immediately</td></tr>
 *   <tr><td>P1 HIGH</td><td>Agent timeout, deadlock</td><td>HALT agent</td></tr>
 *   <tr><td>P2 MEDIUM</td><td>SLA breach, anomaly</td><td>Alert, continue</td></tr>
 *   <tr><td>P3 LOW</td><td>Informational</td><td>Log only</td></tr>
 * </table>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create orchestrator
 * MeterRegistry meterRegistry = new SimpleMeterRegistry();
 * var orchestrator = new MultiAgentTestOrchestrator.Builder()
 *     .withMeterRegistry(meterRegistry)
 *     .build();
 *
 * // Execute all 5 agents in parallel
 * AggregatedTestResults results = orchestrator.executeAllAgents();
 *
 * // Check results
 * System.out.println("Status: " + results.getStatus());
 * System.out.println("Tests: " + results.getTotalTests());
 * System.out.println("Pass Rate: " + results.getPassRate() + "%");
 * System.out.println("Tokens (Groq): " + results.getTotalTokensGroq());
 * System.out.println("Tokens (OpenAI): " + results.getTotalTokensOpenAI());
 *
 * // Check ANDON violations
 * if (orchestrator.getAndonMonitor().hasCriticalViolations()) {
 *     System.err.println("ANDON P0 CRITICAL - test execution failed");
 * }
 *
 * orchestrator.shutdown();
 * }</pre>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link MultiAgentTestOrchestrator} - Orchestrator spawning 5 agents</li>
 *   <li>{@link TestAgent} - Base class for test agents</li>
 *   <li>{@link AgentTestResults} - Results from individual agent</li>
 *   <li>{@link TelemetryCollector} - OpenTelemetry aggregator</li>
 *   <li>{@link AndonMonitor} - ANDON alert gate</li>
 *   <li>{@link AggregatedTestResults} - Final aggregated results</li>
 * </ul>
 *
 * <h2>Performance Characteristics</h2>
 * <ul>
 *   <li>5 agents execute in parallel (virtual threads, ~5s wall-clock time expected)</li>
 *   <li>Metric aggregation: ~100ms</li>
 *   <li>Report generation: ~200ms</li>
 *   <li>Total execution: 5-7 seconds (depending on network latency for LLM calls)</li>
 * </ul>
 *
 * <h2>ANDON Violations</h2>
 * <p>
 * No LLM available (both Groq and OpenAI down) triggers P0 CRITICAL alert.
 * This is enforced as a hard gate before test execution begins.
 * </p>
 *
 * @author YAWL Development Team
 * @version 6.0.0
 */
package org.yawlfoundation.yawl.test.telemetry;
