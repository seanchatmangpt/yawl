/**
 * Unified simulation interface for YAWL self-play loop.
 *
 * <p>This package provides a comprehensive simulation framework that enables autonomous
 * agents to execute YAWL workflows in a controlled environment. The simulation
 * infrastructure supports continuous testing, validation, and optimization through
 * self-play loops where agents generate, execute, and refine workflows.</p>
 *
 * <h3>Key Features:</h3>
 * <ul>
 *   <li>Autonomous workflow execution with configurable strategies</li>
 *   <li>Real-time performance metrics and observability</li>
 *   <li>Integration with YAWL engine for authentic workflow processing</li>
 *   <li>Support for process mining and analysis workflows</li>
 *   <li>Exception handling for simulation-specific error conditions</li>
 * </ul>
 *
 * <h3>Core Classes:</h3>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.sim.YawlSimulator} - Main simulation orchestrator
 *       that manages workflow execution, lifecycle, and strategy application</li>
 *   <li>{@link org.yawlfoundation.yawl.sim.SimException} - Exception type for
 *       simulation-specific errors and validation failures</li>
 * </ul>
 *
 * <h3>Related Packages:</h3>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.engine} - Core YAWL engine implementation
 *       providing workflow execution semantics</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.selfplay} - Self-play integration
 *       utilities for autonomous agent collaboration</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.processmining} - Process mining
 *       integration for workflow analysis and optimization</li>
 * </ul>
 *
 * <h3>Usage Example:</h3>
 * <pre>
 * // Create a new simulator instance
 * YawlSimulator simulator = new YawlSimulator(workflow);
 *
 * // Configure execution strategy
 * simulator.setExecutionStrategy(SelfPlayStrategy.ADAPTIVE);
 *
 * // Execute simulation with real-time metrics
 * SimulationResult result = simulator.execute(
 *     Duration.ofMinutes(10),
 *     MetricsCollection.ALL
 * );
 * </pre>
 *
 * <p>The simulation package integrates with the broader YAWL ecosystem to provide
 * a comprehensive testing and validation platform for autonomous workflow systems.</p>
 *
 * @since 6.0.0
 * @see org.yawlfoundation.yawl.engine.YNetRunner
 * @see org.yawlfoundation.yawl.integration.selfplay.SelfPlayController
 * @see org.yawlfoundation.yawl.integration.processmining.ProcessMiner
 */
package org.yawlfoundation.yawl.sim;