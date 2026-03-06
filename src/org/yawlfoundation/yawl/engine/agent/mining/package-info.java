/**
 * Process mining API inspired by Rust4PM — event logs, process discovery,
 * and conformance checking for YAWL workflow models.
 *
 * <p>Core types:
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.engine.agent.mining.ProcessEvent} — atomic event</li>
 *   <li>{@link org.yawlfoundation.yawl.engine.agent.mining.Trace} — case execution sequence</li>
 *   <li>{@link org.yawlfoundation.yawl.engine.agent.mining.EventLog} — collection of events/traces</li>
 *   <li>{@link org.yawlfoundation.yawl.engine.agent.mining.AlphaDiscovery} — process discovery</li>
 *   <li>{@link org.yawlfoundation.yawl.engine.agent.mining.ConformanceChecker} — conformance checking</li>
 *   <li>{@link org.yawlfoundation.yawl.engine.agent.mining.ProcessMiningPipeline} — fluent pipeline</li>
 * </ul>
 *
 * @since Java 25
 */
package org.yawlfoundation.yawl.engine.agent.mining;
