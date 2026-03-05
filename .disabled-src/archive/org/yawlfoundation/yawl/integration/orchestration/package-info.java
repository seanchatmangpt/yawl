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
 */

/**
 * <h2>YAWL Blue Ocean: Autonomous Agent Orchestration (Phase 1)</h2>
 *
 * <p>
 * Strategic pivot transforming YAWL into the "Trustworthy Agent Orchestration Platform"
 * for the AI era. This package provides the foundation for intelligent task delegation
 * to external agents (LLMs, autonomous systems) with full OpenTelemetry observability.
 * </p>
 *
 * <h3>Phase 1: Foundation (80/20 Rule)</h3>
 *
 * <ul>
 *   <li><strong>AgentOrchestrationService</strong> - Core routing and result tracking</li>
 *   <li><strong>Integration Points</strong> - MCP, A2A, OTEL readiness</li>
 *   <li><strong>Observability Hooks</strong> - Tracing infrastructure for agent decisions</li>
 * </ul>
 *
 * <h3>Usage</h3>
 *
 * <pre>
 * AgentOrchestrationService orchestrator = AgentOrchestrationService.getInstance();
 * AgentOrchestrationService.OrchestrationResult result =
 *     orchestrator.routeWorkItem(workItem, taskName);
 *
 * if (result.isEscalated()) {
 *     // Route to human or retry
 * }
 * </pre>
 *
 * <h3>Future Phases</h3>
 *
 * <ul>
 *   <li><strong>Phase 2</strong> - AgentRegistry integration, capability matching, resilience</li>
 *   <li><strong>Phase 3</strong> - Cost optimization, formal verification, trust layer</li>
 *   <li><strong>Phase 4+</strong> - Advanced features, compliance, scaling</li>
 * </ul>
 *
 * <h3>Key Design Principles</h3>
 *
 * <ul>
 *   <li>Real implementations only - no mocks, stubs, or TODOs (HYPER_STANDARDS)</li>
 *   <li>80/20 effort-to-impact ratio - strategic instrumentation, not bloat</li>
 *   <li>Observability first - every decision traced for debugging</li>
 *   <li>Petri net semantics - leverage formal workflow theory for agent reasoning</li>
 * </ul>
 *
 * @author YAWL Development Team
 * @version 5.2.1
 * @see AgentOrchestrationService
 */
package org.yawlfoundation.yawl.integration.orchestration;
