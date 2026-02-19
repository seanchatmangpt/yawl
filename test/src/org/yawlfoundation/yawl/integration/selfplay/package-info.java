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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * Self-Play Test Integration Package for YAWL.
 *
 * <p>This package provides automated testing capabilities for YAWL workflows
 * through a complete lifecycle: XML generation → Validation → Upload → Execution →
 * Verification. The components are designed to work with Java 25 features
 * including virtual threads and structured concurrency.</p>
 *
 * <h3>Package Structure</h3>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.selfplay.SelfPlayOrchestrator} -
 *       Main orchestrator class that coordinates the entire test workflow</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.selfplay.SelfPlayConfig} -
 *       Configuration management with support for environment variables and properties</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.selfplay.ZaiWorkflowGenerator} -
 *       Integration with Z.AI for automated workflow generation</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.selfplay.SelfPlayOrchestratorTest} -
 *       Comprehensive unit tests for the orchestrator</li>
 * </ul>
 *
 * <h3>Key Features</h3>
 * <ul>
 *   <li>Multi-iteration testing with configurable parameters</li>
 *   <li>AI-powered workflow generation via Z.ai integration</li>
 *   <li>XML validation against YAWL Schema 4.0</li>
 *   <li>Metrics collection and performance tracking</li>
 *   <li>Comprehensive JSON reporting</li>
 *   <li>Virtual thread support for concurrent operations</li>
 *   <li>Graceful fallback mechanisms when dependencies are unavailable</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * // Create orchestrator with default configuration
 * SelfPlayOrchestrator orchestrator = new SelfPlayOrchestrator();
 *
 * // Run self-play test
 * orchestrator.runSelfPlayTest();
 *
 * // Generate report
 * orchestrator.generateReport();
 * }</pre>
 *
 * <h3>Integration Patterns</h3>
 * <p>The package follows established YAWL integration patterns:</p>
 * <ul>
 *   <li>Uses {@link org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient}
 *       for engine communication</li>
 *   <li>Extends YAWL's standard exception handling patterns</li>
 *   <li>Follows Java 25 conventions with virtual threads and structured concurrency</li>
 *   <li>Implements proper resource cleanup and shutdown sequences</li>
 * </ul>
 *
 * @version 5.2
 * @since 5.2
 * @see org.yawlfoundation.yawl.integration.selfplay.SelfPlayOrchestrator
 */
package org.yawlfoundation.yawl.integration.selfplay;