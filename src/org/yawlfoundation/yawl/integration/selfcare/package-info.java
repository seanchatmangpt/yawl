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
 * Autonomic self-care actions: OT-aligned workflow discovery and behavioral activation
 * scheduling via Gregverse marketplace.
 *
 * <p>Guiding principle: <em>"You can act your way into right action but you can't think
 * your way into right actions."</em> — the system takes action autonomically so people
 * don't need to deliberate before starting.</p>
 *
 * <h2>Core Components</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.selfcare.OTDomain} — Occupational Therapy
 *       performance areas per AOTA Practice Framework (Self-Care, Productivity, Leisure)</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.selfcare.SelfCareAction} — OT-aligned
 *       sealed action types (DailyLiving, Physical, Cognitive, Social)</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.selfcare.BehavioralActivationPlan} —
 *       Immutable action plan ordered easiest-first; {@code nextAction()} is the immediate
 *       "act now" entry point</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.selfcare.GregverseSearchClient} — Queries
 *       Gregverse SPARQL endpoint for OT workflow specifications</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.selfcare.AutonomicSelfCareEngine} — MAPE-K
 *       engine that schedules and monitors care actions without user deliberation</li>
 * </ul>
 *
 * <h2>MCP Integration</h2>
 * <p>Two MCP tools are exposed via {@code YawlMcpServer}:</p>
 * <ul>
 *   <li>{@code yawl_selfcare_search} — Search Gregverse for OT workflow specs by domain</li>
 *   <li>{@code yawl_selfcare_recommend} — Generate a behavioral activation plan for a domain</li>
 * </ul>
 *
 * @since YAWL 6.0
 */
package org.yawlfoundation.yawl.integration.selfcare;
