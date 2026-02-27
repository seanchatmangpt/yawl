/**
 * Copyright 2004-2026 YAWL Foundation
 *
 * This package provides reward function implementations for reinforcement learning in YAWL
 * workflow optimization, including specialized scorers for different optimization criteria.
 *
 * <p>The scoring package leverages Java 25 features including:
 * <ul>
 *   <li><strong>Pattern Matching for switch</strong>: Exhaustive type-safe dispatch for
 *       different reward calculation strategies</li>
 *   <li><strong>Records for Reward Models</strong>: Immutable data structures for reward
 *       calculations with virtual constructor support</li>
 *   <li><strong>Virtual Threads</strong>: For concurrent execution of reward calculations</li>
 * </ul>
 *
 * <p>Key components:
 * <ul>
 *   <li>FootprintScorer: Workflow resource utilization optimization</li>
 *   <li>LlmJudgeScorer: Large language model-based workflow quality assessment</li>
 *   <li>CompositeRewardFunction: Multi-objective reward combination</li>
 *   <li>Adaptive reward scaling and normalization</li>
 * </ul>
 *
 * <p>Since: 6.0.0-GA
 */
package org.yawlfoundation.yawl.ggen.rl.scoring;