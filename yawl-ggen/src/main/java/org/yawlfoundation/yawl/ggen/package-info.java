/*
 * Copyright 2004-2026 YAWL Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * YAWL-GGEN - Generative Workflow Engine
 *
 * The root package for YAWL-GGEN, implementing a generative workflow engine that
 * combines YAWL semantics with advanced code generation capabilities. This module
 * provides GRPO (Generative Refactoring and Process Optimization) patterns,
 * OpenSage intelligent memory system, and modern Java 25 features including:
 *
 * <h3>Key Features:</h3>
 * <ul>
 *   <li>GRPO patterns for process optimization and refactoring</li>
 *   <li>OpenSage memory system with JUNG graph integration</li>
 *   <li>Virtual threads for high-concurrency workflow generation</li>
 *   <li>Polyglot architecture supporting multiple workflow formats</li>
 *   <li>Sealed classes for type-safe workflow components</li>
 *   <li>Records for immutable workflow artifacts</li>
 * </ul>
 *
 * <h3>Architecture Overview:</h3>
 * <pre>{@code
 * ┌─────────────────────────────────────────────────────┐
 * │                YAWL-GGEN Engine                     │
 * │                                                     │
 * │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  │
 * │  │  GRPO       │  │  OpenSage   │  │  Polyglot   │  │
 * │  │  Patterns   │  │  Memory     │  │  Converter │  │
 * │  └─────────────┘  └─────────────┘  └─────────────┘  │
 * │                                                     │
 * │  ┌─────────────────────────────────────────────────┐  │
 * │  │             Virtual Threads Layer               │  │
 * │  │  (Project Loom - Structured Concurrency)       │  │
 * │  └─────────────────────────────────────────────────┘  │
 * └─────────────────────────────────────────────────────┘
 * }</pre>
 *
 * <h3>Example Usage:</h3>
 * <pre>{@code
 * // Using virtual threads for concurrent workflow generation
 * ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
 *
 * try {
 *     var future = virtualExecutor.submit(() -> {
 *         GRPOptimizer optimizer = new GRPOptimizer();
 *         return optimizer.optimize(workflow);
 *     });
 *
 *     var optimizedWorkflow = future.get(30, TimeUnit.SECONDS);
 * } finally {
 *     virtualExecutor.shutdown();
 * }
 * }</pre>
 *
 * <h3>Package Structure:</h3>
 * <ul>
 *   <li>{@code org.yawlfoundation.yawl.ggen.api} - REST API layer with servlet endpoints</li>
 *   <li>{@code org.yawlfoundation.yawl.ggen.memory} - OpenSage memory system</li>
 *   <li>{@code org.yawlfoundation.yawl.ggen.patterns} - GRPO pattern implementations</li>
 *   <li>{@code org.yawlfoundation.yawl.ggen.converter} - Polyglot workflow converters</li>
 * </ul>
 *
 * @since 6.0.0-GA
 * @see <a href="https://yawl.sourceforge.net">YAWL Foundation</a>
 * @see <a href="https://openjdk.org/jeps/444">Virtual Threads (Project Loom)</a>
 */
package org.yawlfoundation.yawl.ggen;