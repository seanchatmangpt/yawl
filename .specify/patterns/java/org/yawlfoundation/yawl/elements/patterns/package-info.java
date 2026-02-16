/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
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
 * YAWL Advanced Workflow Patterns Package.
 *
 * <p>This package contains implementations of advanced workflow control patterns
 * for the YAWL workflow engine. These patterns extend the base YAWL functionality
 * with modern workflow capabilities required for distributed systems, event-driven
 * architectures, and complex business processes.</p>
 *
 * <h2>Pattern Summary</h2>
 *
 * <table border="1">
 *   <tr>
 *     <th>Pattern</th>
 *     <th>WCP Ref</th>
 *     <th>Description</th>
 *     <th>Main Class</th>
 *   </tr>
 *   <tr>
 *     <td>Saga Orchestration</td>
 *     <td>N/A</td>
 *     <td>Distributed transactions with compensating actions</td>
 *     <td>{@link org.yawlfoundation.yawl.elements.patterns.YSagaOrchestrationTask}</td>
 *   </tr>
 *   <tr>
 *     <td>Structured Discriminator</td>
 *     <td>WCP-9</td>
 *     <td>First token passes, rest discarded</td>
 *     <td>{@link org.yawlfoundation.yawl.elements.patterns.YDiscriminatorTask}</td>
 *   </tr>
 *   <tr>
 *     <td>Milestone</td>
 *     <td>WCP-18</td>
 *     <td>Context-dependent enablement based on state</td>
 *     <td>{@link org.yawlfoundation.yawl.elements.patterns.YMilestoneCondition}</td>
 *   </tr>
 *   <tr>
 *     <td>Interleaved Parallel Routing</td>
 *     <td>WCP-17</td>
 *     <td>Concurrent enabling with mutex execution</td>
 *     <td>{@link org.yawlfoundation.yawl.elements.patterns.YInterleavedRouterTask}</td>
 *   </tr>
 *   <tr>
 *     <td>Event-Based Deferred Choice</td>
 *     <td>WCP-16</td>
 *     <td>External event triggers workflow path</td>
 *     <td>{@link org.yawlfoundation.yawl.elements.patterns.YDeferredChoiceTask}</td>
 *   </tr>
 * </table>
 *
 * <h2>Integration with YAWL</h2>
 *
 * <p>These patterns integrate with the existing YAWL architecture through:</p>
 * <ul>
 *   <li>Extension of {@link org.yawlfoundation.yawl.elements.YTask} for task-based patterns</li>
 *   <li>Extension of {@link org.yawlfoundation.yawl.elements.YCondition} for milestone conditions</li>
 *   <li>New join/split type constants added to YTask</li>
 *   <li>Integration with YNetRunner for event coordination</li>
 *   <li>Persistence through YPersistenceManager</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 *
 * <p>All pattern implementations are designed to be thread-safe:</p>
 * <ul>
 *   <li>State transitions are synchronized</li>
 *   <li>Atomic operations for token management</li>
 *   <li>Concurrent-safe data structures for event handling</li>
 * </ul>
 *
 * <h2>Persistence</h2>
 *
 * <p>Pattern state is persisted using the standard YAWL persistence mechanism:</p>
 * <ul>
 *   <li>All state objects implement persistence callbacks</li>
 *   <li>Recovery from crash is supported</li>
 *   <li>Transaction boundaries are respected</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @since 5.3
 * @see org.yawlfoundation.yawl.elements.YTask
 * @see org.yawlfoundation.yawl.elements.YCondition
 */
package org.yawlfoundation.yawl.elements.patterns;
