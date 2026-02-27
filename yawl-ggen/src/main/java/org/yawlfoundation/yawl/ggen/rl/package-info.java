/**
 * Copyright 2004-2026 YAWL Foundation
 *
 * This package provides the GRPO (Graph-based Reinforcement Process Optimization) RL engine
 * for automating YAWL workflow optimization through reinforcement learning.
 *
 * <p>The RL package leverages Java 25 features including:
 * <ul>
 *   <li><strong>Virtual Threads</strong>: For scalable concurrent execution of reinforcement
 *       learning agents with non-blocking I/O operations</li>
 *   <li><strong>Structured Concurrency</strong>: Coordinated execution of parallel training
 *       episodes with error propagation</li>
 *   <li><strong>Sealed Interfaces for Strategies</strong>: Type-safe RL algorithm implementations</li>
 * </ul>
 *
 * <p>Key components:
 * <ul>
 *   <li>Candidate sampling for workflow pattern optimization</li>
 *   <li>Reward scoring mechanisms for performance evaluation</li>
 *   <li>Group advantage calculations for multi-objective optimization</li>
 *   <li>Policy gradient learning for workflow refinement</li>
 * </ul>
 *
 * <p>Since: 6.0.0-GA
 */
package org.yawlfoundation.yawl.ggen.rl;