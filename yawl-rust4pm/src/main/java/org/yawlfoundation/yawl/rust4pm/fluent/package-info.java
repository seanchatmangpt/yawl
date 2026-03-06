/**
 * Fluent API for process mining pipelines with OTP-inspired supervision.
 *
 * <p>This package provides a declarative, builder-style API for composing
 * process mining operations (parse, discover, conformance check, stats)
 * with Erlang/OTP-inspired fault-tolerance patterns.
 *
 * <p>Entry point: {@link org.yawlfoundation.yawl.rust4pm.fluent.ProcessMiningPipeline}
 *
 * <p>Key concepts:
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.rust4pm.fluent.PipelineStage} — sealed hierarchy of operations</li>
 *   <li>{@link org.yawlfoundation.yawl.rust4pm.fluent.SupervisionStrategy} — fault-tolerance policy</li>
 *   <li>{@link org.yawlfoundation.yawl.rust4pm.fluent.PipelineResult} — execution results with outcomes</li>
 * </ul>
 */
package org.yawlfoundation.yawl.rust4pm.fluent;
