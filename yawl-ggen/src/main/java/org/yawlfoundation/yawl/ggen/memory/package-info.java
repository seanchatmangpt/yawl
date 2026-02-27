/*
 * Copyright 2004-2026 YAWL Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
 * OpenSage Memory System
 *
 * Implements the OpenSage intelligent memory system for pattern recognition and
 * workflow optimization using JUNG graph algorithms and Java 25 modern features.
 * This package provides pattern-based memory management for workflow generation:
 *
 * <h3>Key Features:</h3>
 * <ul>
 *   <li>JUNG graph integration for pattern node relationships</li>
 *   <li>Pattern memory loop with learning capabilities</li>
 *   <li>Virtual threads for concurrent pattern processing</li>
 *   <li>Sealed classes for type-safe pattern definitions</li>
 *   <li>Records for immutable pattern artifacts</li>
 *   <li>Structured concurrency for memory operations</li>
 * </ul>
 *
 * <h3>Architecture Overview:</h3>
 * <pre>{@code
 * ┌─────────────────────────────────────────────────────┐
 * │               OpenSage Memory System                 │
 * │                                                     │
 * │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  │
 * │  │ Pattern     │  │ Memory     │  │ Graph       │  │
 * │  │ Nodes       │  │ Store      │  │ Manager     │  │
 * │  └─────────────┘  └─────────────┘  └─────────────┘  │
 * │                                                     │
 * │  ┌─────────────────────────────────────────────────┐  │
 * │  │            Pattern Memory Loop                   │  │
 * │  │  (Virtual Thread Pool - Concurrent Learning)      │  │
 * │  └─────────────────────────────────────────────────┘  │
 * └─────────────────────────────────────────────────────┘
 * }</pre>
 *
 * <h3>Example Usage:</h3>
 * <pre>{@code
 * // Using virtual threads for pattern memory operations
 * ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
 * MemorySystem memorySystem = new MemorySystem();
 *
 * // Pattern learning with virtual threads
 * var patternFuture = virtualExecutor.submit(() -> {
 *     PatternNode node = new PatternNode(
 *         "sequence",
 *         List.of("task1", "task2"),
 *         Map.of("frequency", 42, "optimization", true)
 *     );
 *     return memorySystem.learnPattern(node);
 * });
 *
 * // Pattern retrieval
 * var retrievalFuture = virtualExecutor.submit(() -> {
 *     return memorySystem.findSimilarPatterns("parallel");
 * });
 *
 * // Using records for immutable pattern artifacts
 * record PatternArtifact(String id, String type, Map<String, Object> metadata) {
 *     public PatternArtifact {
 *         metadata = Map.copyOf(metadata); // Defensive copy
 *     }
 * }
 *
 * try {
 *     var learnedPattern = patternFuture.get(5, TimeUnit.SECONDS);
 *     var similarPatterns = retrievalFuture.get(5, TimeUnit.SECONDS);
 *
 *     // Process results with structured concurrency
 *     try (var scope = new StructuredTaskScope<>()) {
 *         var task = scope.fork(() -> processPatterns(similarPatterns));
 *         scope.join();
 *         var processed = task.get();
 *     }
 * } catch (TimeoutException e) {
 *     memorySystem.cancelPendingOperations();
 * }
 * }</pre>
 *
 * <h3>Core Components:</h3>
 * <ul>
 *   <li>{@code PatternNode} - Individual pattern with metadata</li>
 *   <li>{@code MemoryStore} - Persistent pattern storage</li>
 *   <li>{@code GraphManager} - JUNG graph operations</li>
 *   <li>{@code PatternMemoryLoop} - Learning and optimization loop</li>
 *   <li>{@code VirtualThreadProcessor} - Concurrent pattern processing</li>
 * </ul>
 *
 * <h3>Memory Loop Process:</h3>
 * <ol>
 *   <li>Pattern ingestion from workflow analysis</li>
 *   <li>Graph integration with JUNG algorithms</li>
 *   <li>Similarity matching and pattern evolution</li>
 *   <li>Optimization feedback to workflow generation</li>
 *   <li>Memory persistence and indexing</li>
 * </ol>
 *
 * @since 6.0.0-GA
 * @see <a href="https://github.com/jrtom/jung">JUNG (Java Universal Network/Graph Framework)</a>
 * @see <a href="https://openjdk.org/jeps/444">Virtual Threads (Project Loom)</a>
 * @see org.yawlfoundation.yawl.ggen
 * @see org.yawlfoundation.yawl.ggen.api
 */
package org.yawlfoundation.yawl.ggen.memory;