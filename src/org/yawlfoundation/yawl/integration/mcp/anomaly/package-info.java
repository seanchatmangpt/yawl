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
 * Temporal Anomaly Detection for YAWL Workflow Execution.
 *
 * This package provides real-time SLA risk detection and anomaly analysis for
 * YAWL workflow cases and work items.
 *
 * <h2>Overview</h2>
 *
 * The Temporal Anomaly Sentinel analyzes running cases to detect:
 * <ul>
 *   <li>Tasks dramatically over their expected completion time (e.g., 10x baseline)</li>
 *   <li>Cases stalled in specific tasks (resource bottlenecks)</li>
 *   <li>Patterns suggesting imminent SLA violations</li>
 *   <li>Queued work items waiting too long for resource allocation</li>
 * </ul>
 *
 * <h2>Algorithm</h2>
 *
 * The sentinel uses cross-case comparison to establish baselines:
 * <ol>
 *   <li>Groups completed work items by (specId, taskId) across all cases</li>
 *   <li>Computes mean and standard deviation for each task group</li>
 *   <li>Analyzes live (enabled, executing) items for deviation from baseline</li>
 *   <li>Flags items deviating >3x mean (or 2x for fine-grained alerts)</li>
 *   <li>For single-sample tasks, uses configurable default threshold (e.g., 60 min)</li>
 * </ol>
 *
 * <h2>Risk Scoring</h2>
 *
 * Risk scores (0-100) reflect urgency of intervention:
 * <ul>
 *   <li><strong>90-100:</strong> CRITICAL — SLA breach imminent (>10x baseline)</li>
 *   <li><strong>70-89:</strong> WARNING — Resource bottleneck detected (5x-10x baseline)</li>
 *   <li><strong>40-69:</strong> CAUTION — Monitor closely (2x-5x baseline)</li>
 *   <li><strong>0-39:</strong> LOW — On track or minor delay</li>
 * </ul>
 *
 * <h2>Usage</h2>
 *
 * The sentinel is exposed as an MCP tool:
 * <pre>
 * Tool: yawl_temporal_anomaly_sentinel
 * Parameters:
 *   - defaultTimeoutMinutes (optional, default 60): Expected duration for unknown tasks
 * Returns:
 *   - Formatted ASCII report with detected anomalies and risk assessments
 * </pre>
 *
 * <h2>Example Output</h2>
 *
 * <pre>
 * ╔═════════════════════════════════════════════╗
 * ║    TEMPORAL ANOMALY SENTINEL               ║
 * ║ Real-time SLA Risk Detection — 2026-02-24  ║
 * ╚═════════════════════════════════════════════╝
 *
 * RISK ASSESSMENT: 3 running cases, 2 anomalies detected
 * ─────────────────────────────────────────────────────
 *
 * 🔴 CRITICAL ANOMALY
 *    Case #42 | OrderProcessing v1.0
 *    Work Item: ManagerApproval [WI-42:ManagerApproval]
 *    Status: Executing
 *    Elapsed: 3h 27m | Benchmark: 12m | Deviation: +17.3x expected
 *    Risk Score: 97/100 — SLA BREACH IMMINENT
 *    Action: Escalate immediately or reassign resource
 *
 * ✅ HEALTHY
 *    2 cases on track, no anomalies detected.
 *
 * SENTINEL METRICS
 * ────────────────
 * Monitored cases: 3 | Anomalies: 2 (CRITICAL:1 WARN:1)
 * Overall health: DEGRADED — 67% of cases at risk
 * </pre>
 *
 * <h2>Timing Data Sources</h2>
 *
 * The sentinel uses work item timing records:
 * <ul>
 *   <li><strong>enablementTimeMs:</strong> When task became available (timestamp in ms)</li>
 *   <li><strong>startTimeMs:</strong> When task was claimed/started (timestamp in ms)</li>
 *   <li><strong>completionTimeMs:</strong> When task completed (timestamp in ms)</li>
 * </ul>
 *
 * For live items, elapsed time = now - enablementTimeMs
 * For completed items (baseline), duration = completionTimeMs - enablementTimeMs
 *
 * <h2>Classes</h2>
 *
 * <dl>
 *   <dt>{@link org.yawlfoundation.yawl.integration.mcp.anomaly.TemporalAnomalySentinel}</dt>
 *   <dd>Core anomaly detection engine and report generator</dd>
 *   <dt>{@link org.yawlfoundation.yawl.integration.mcp.anomaly.TemporalAnomalySentinel.AnomalyRecord}</dt>
 *   <dd>Immutable record of a detected anomaly</dd>
 * </dl>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.integration.mcp.anomaly;
