/**
 * JMH (Java Microbenchmark Harness) benchmarks for YAWL virtual thread performance analysis.
 *
 * <h2>Overview</h2>
 * This package contains comprehensive benchmarks comparing platform threads vs virtual threads
 * across all YAWL subsystems that have been migrated to use virtual threads in Java 25.
 *
 * <h2>Benchmark Classes</h2>
 * <ul>
 *   <li>{@link IOBoundBenchmark} - I/O-bound operations (database, file, network)</li>
 *   <li>{@link EventLoggerBenchmark} - Event logging and notification patterns</li>
 *   <li>{@link InterfaceBClientBenchmark} - HTTP client performance (InterfaceB)</li>
 *   <li>{@link StructuredConcurrencyBenchmark} - Structured concurrency vs CompletableFuture</li>
 *   <li>{@link MemoryUsageBenchmark} - Memory efficiency and GC pressure</li>
 *   <li>{@link WorkflowExecutionBenchmark} - Real-world workflow execution patterns</li>
 * </ul>
 *
 * <h2>Running Benchmarks</h2>
 * <pre>
 * # Run all benchmarks
 * mvn clean test-compile exec:java \
 *   -Dexec.mainClass="org.yawlfoundation.yawl.performance.jmh.AllBenchmarksRunner"
 *
 * # Run specific benchmark
 * java -jar target/benchmarks.jar IOBoundBenchmark
 *
 * # Run with custom parameters
 * java -jar target/benchmarks.jar IOBoundBenchmark \
 *   -p taskCount=1000 -p ioDelayMs=10
 * </pre>
 *
 * <h2>Expected Performance Improvements</h2>
 * <table border="1">
 *   <tr>
 *     <th>Benchmark</th>
 *     <th>Metric</th>
 *     <th>Expected Improvement</th>
 *   </tr>
 *   <tr>
 *     <td>I/O-Bound</td>
 *     <td>Throughput</td>
 *     <td>2-10x (higher with more concurrent tasks)</td>
 *   </tr>
 *   <tr>
 *     <td>Event Logger</td>
 *     <td>Throughput</td>
 *     <td>3-5x (1000+ listeners)</td>
 *   </tr>
 *   <tr>
 *     <td>InterfaceB Client</td>
 *     <td>Latency p95</td>
 *     <td>20-30% reduction</td>
 *   </tr>
 *   <tr>
 *     <td>Structured Concurrency</td>
 *     <td>Cancellation time</td>
 *     <td>50-80% faster</td>
 *   </tr>
 *   <tr>
 *     <td>Memory Usage</td>
 *     <td>Memory per thread</td>
 *     <td>100-1000x reduction (1MB → 1KB)</td>
 *   </tr>
 *   <tr>
 *     <td>Workflow Execution</td>
 *     <td>Completion time</td>
 *     <td>2-4x faster (parallel stages)</td>
 *   </tr>
 * </table>
 *
 * <h2>Result Analysis</h2>
 * Results are output in multiple formats:
 * <ul>
 *   <li>Console: Human-readable summary</li>
 *   <li>JSON: target/jmh-results.json (machine-readable)</li>
 *   <li>CSV: For spreadsheet analysis</li>
 * </ul>
 *
 * <h2>Dependencies</h2>
 * <pre>
 * &lt;dependency&gt;
 *   &lt;groupId&gt;org.openjdk.jmh&lt;/groupId&gt;
 *   &lt;artifactId&gt;jmh-core&lt;/artifactId&gt;
 *   &lt;version&gt;1.37&lt;/version&gt;
 * &lt;/dependency&gt;
 * &lt;dependency&gt;
 *   &lt;groupId&gt;org.openjdk.jmh&lt;/groupId&gt;
 *   &lt;artifactId&gt;jmh-generator-annprocess&lt;/artifactId&gt;
 *   &lt;version&gt;1.37&lt;/version&gt;
 * &lt;/dependency&gt;
 * </pre>
 *
 * @since 5.2
 * @version 2026-02-16
 */
package org.yawlfoundation.yawl.performance.jmh;
