/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * N-dimensional marketplace topology for autonomous OT service delivery.
 *
 * <p>This package provides the core infrastructure for matching patients with optimal
 * occupational therapy providers through a 6-dimensional marketplace topology.</p>
 *
 * <h2>Architecture Overview</h2>
 *
 * <h3>6-Dimensional Marketplace Topology</h3>
 * <table border="1">
 *   <tr><th>Dimension</th><th>Purpose</th><th>Workflow Pattern Integration</th></tr>
 *   <tr>
 *     <td><b>Service Type</b></td>
 *     <td>Core OT services (assessment, intervention, scheduling)</td>
 *     <td>WCP-4 (Exclusive Choice) for routing</td>
 *   </tr>
 *   <tr>
 *     <td><b>Specialization</b></td>
 *     <td>Clinical expertise areas (pediatric, geriatric, mental health, physical rehab)</td>
 *     <td>WCP-4 (Exclusive Choice) for specialist selection</td>
 *   </tr>
 *   <tr>
 *     <td><b>Delivery Mode</b></td>
 *     <td>Service delivery methods (telehealth, in-person, hybrid)</td>
 *     <td>WCP-21 (Deferred Choice) for preference handling</td>
 *   </tr>
 *   <tr>
 *     <td><b>Urgency</b></td>
 *     <td>Response time requirements (routine, urgent, emergency)</td>
 *     <td>WCP-4 (Exclusive Choice) for priority routing</td>
 *   </tr>
 *   <tr>
 *     <td><b>Price Range</b></td>
 *     <td>Cost tiers (budget, standard, premium)</td>
 *     <td>WCP-6 (Multi-Choice) for bundle pricing</td>
 *   </tr>
 *   <tr>
 *     <td><b>Rating Threshold</b></td>
 *     <td>Quality filters (3+, 4+, 4.5+)</td>
 *     <td>WCP-4 (Exclusive Choice) for quality filtering</td>
 *   </tr>
 * </table>
 *
 * <h3>Workflow Pattern Integration</h3>
 *
 * <h4>WCP-4: Exclusive Choice Pattern</h4>
 * <ul>
 *   <li><b>Constraint Filtering</b>: Eliminates providers that don't meet hard constraints
 *       (rating minimum, distance, insurance compatibility)</li>
 *   <li><b>Specialized Routing</b>: Routes patients to appropriate specialists based on
 *       demographics and condition</li>
 *   <li><b>Urgency Handling</b>: Prioritizes emergency cases with accelerated processing</li>
 *   <li><b>Quality Control</b>: Filters providers based on minimum rating thresholds</li>
 * </ul>
 *
 * <h4>WCP-6: Multi-Choice Pattern</h4>
 * <ul>
 *   <li><b>Multi-Provider Selection</b>: Ranks and selects multiple qualified providers</li>
 *   <li><b>Bundle Creation</b>: Creates optimal service bundles for complex needs</li>
 *   <li><b>Pricing Optimization</b> : Calculates bundle pricing with discounts</li>
 *   <li><b>Capacity Management</b>: Balances load across provider network</li>
 * </ul>
 *
 * <h4>WCP-21: Deferred Choice Pattern</h4>
 * <ul>
 *   <li><b>Preference Integration</b>: Processes flexible patient preferences after
 *       constraint filtering</li>
 *   <li><b>Delivery Mode Selection</b>: Handles patient delivery preferences</li>
 *   <li><b>Response Time Handling</b>: Processes flexible urgency requirements</li>
 *   <li><b>Custom Matching</b>: Incorporates patient-specific preferences</li>
 * </ul>
 *
 * <h3>Pricing Model</h3>
 *
 * The marketplace implements dynamic pricing based on multiple factors:
 * <ul>
 *   <li><b>Base Rates</b>: assessment ($150/hr), intervention ($175/hr), scheduling ($100/hr)</li>
 *   <li><b>Specialization Multipliers</b>: pediatric (1.2x), geriatric (1.15x),
 *       mental health (1.25x), physical rehab (1.1x)</li>
 *   <li><b>Delivery Mode Adjustments</b>: telehealth (0.9x), in-person (1.0x), hybrid (0.95x)</li>
 *   <li><b>Urgency Multipliers</b>: routine (1.0x), urgent (1.3x), emergency (1.8x)</li>
 *   <li><b>Bundle Discounts</b>: 5-15% discount for multi-provider combinations</li>
 * </ul>
 *
 * <h3>Routing Algorithm</h3>
 *
 * The routing algorithm operates in stages:
 * <ol>
 *   <li><b>Constraint Filtering (WCP-4)</b>: Apply hard constraints to eliminate incompatible providers</li>
 *   <li><b>Specialized Routing (WCP-4)</b>: Route based on service type and specialization</li>
 *   <li><b>Multi-Provider Selection (WCP-6)</b>: Rank providers using weighted scoring</li>
 *   <li><b>Preference Integration (WCP-21)</b>: Apply patient preferences and flexible constraints</li>
 *   <li><b>Bundle Optimization</b>: Create optimal service bundles for complex needs</li>
 * </ol>
 *
 * <h3>Core Classes</h3>
 *
 * <ul>
 *   <li><b>{@link MarketplaceDimension}</b> - Enum defining all 6 marketplace dimensions and their values</li>
 *   <li><b>{@link EnhancedNDimensionalCoordinate}</b> - Represents provider/patient coordinates in 6D space</li>
 *   <li><b>{@link MatchingConstraints}</b> - Defines matching boundaries and preferences</li>
 *   <li><b>{@link OTMarketplaceRouter}</b> - Core routing algorithm implementing WCP patterns</li>
 *   <li><b>{@link OTMarketplaceWorkflow}</b> - Orchestrates complete matching workflow</li>
 *   <li><b>{@link OTMarketplaceExample}</b> - Demonstrations of all marketplace scenarios</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 *
 * <pre>{@code
 * // Create workflow
 * OTMarketplaceWorkflow workflow = new OTMarketplaceWorkflow();
 *
 * // Define patient needs
 * OTMarketplaceWorkflow.PatientData patient = new OTMarketplaceWorkflow.PatientData(
 *     "patient-123",
 *     "Jane Doe",
 *     35,
 *     "anxiety",
 *     "intervention",
 *     "telehealth",
 *     "routine",
 *     "standard",
 *     "4+",
 *     List.of("english"),
 *     List.of("Blue Cross"),
 *     false, false,
 *     MatchingConstraints.defaults(),
 *     Map.of(),
 *     List.of(),
 *     Double.POSITIVE_INFINITY
 * );
 *
 * // Execute matching
 * OTMarketplaceWorkflow.WorkflowExecutionResult result = workflow.executeWorkflow(patient);
 *
 * // Process results
 * if (result.success()) {
 *     Map<String, Object> data = result.data();
 *     // Match patient with optimal providers
 * }
 * }</pre>
 *
 * <h3>Performance Characteristics</h3>
 *
 * <ul>
 *   <li><b>Time Complexity</b>: O(n log n) for provider ranking, where n is number of providers</li>
 *   <li><b>Space Complexity</b>: O(n) for provider storage and intermediate structures</li>
 *   <li><b>Scalability</b>: Designed to handle thousands of providers efficiently</li>
 *   <li><b>Response Time</b>: <100ms for typical queries, <500ms for complex bundle matching</li>
 * </ul>
 *
 * <h3>Integration Points</h3>
 *
 * <ul>
 *   <li><b>YAWL Engine</b>: Integrates with YAWL workflow patterns for orchestration</li>
 *   <li><b>GregVerse Agent System</b>: Coordinates with autonomous OT agents</li>
 *   <li><b>MCP/A2A Protocol</b>: Supports agent-to-agent service discovery</li>
 *   <li><b>Observability</b>: Provides metrics and tracing for marketplace performance</li>
 * </ul>
 *
 * @since 6.0.0
 * @version 6.0.0
 */
package org.yawlfoundation.yawl.mcp.a2a.gregverse.matching;