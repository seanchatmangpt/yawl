/**
 * Fluent API for composing OTP-style actor systems with process mining pipelines.
 *
 * <p>This package provides two top-level builders:</p>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.engine.agent.fluent.ActorSystem} —
 *       Fluent builder for supervised, named actor hierarchies.
 *       Wraps {@link org.yawlfoundation.yawl.engine.agent.core.ActorRuntime}
 *       and {@link org.yawlfoundation.yawl.engine.agent.core.Supervisor}.</li>
 *   <li>{@link org.yawlfoundation.yawl.engine.agent.fluent.ProcessMiningPipeline} —
 *       Fluent builder for actor-based process mining pipelines.
 *       Each stage is a supervised actor; data flows stage-to-stage via mailbox.</li>
 * </ul>
 *
 * <p>Backend-agnostic: stage functions can delegate to Rust4PM (native FFM),
 * Erlang/OTP (ErlangBridge), or pure Java implementations.</p>
 *
 * <p>All classes are thread-safe after construction.</p>
 *
 * @see org.yawlfoundation.yawl.engine.agent.core
 * @see org.yawlfoundation.yawl.engine.agent.patterns
 */
package org.yawlfoundation.yawl.engine.agent.fluent;
