/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

/**
 * Comprehensive Chaos Engineering Test Suite for YAWL MCP-A2A MVP.
 *
 * <h2>Overview</h2>
 * This package contains chaos engineering tests that validate YAWL's resilience
 * under various failure conditions. All tests use synthetic chaos injection
 * without requiring external tools like Chaos Mesh or LitmusChaos.
 *
 * <h2>Test Categories</h2>
 *
 * <h3>1. Failure Scenarios ({@link NetworkChaosTest})</h3>
 * <ul>
 *   <li>Network latency injection (100ms - 5s delays)</li>
 *   <li>Network partition simulation (split-brain scenarios)</li>
 *   <li>Database connection failures</li>
 *   <li>YAWL engine unavailability</li>
 * </ul>
 *
 * <h3>2. Resource Chaos ({@link ResourceChaosTest})</h3>
 * <ul>
 *   <li>Memory pressure testing</li>
 *   <li>CPU throttling (50-100% utilization)</li>
 *   <li>Disk space exhaustion</li>
 *   <li>File descriptor limits</li>
 *   <li>Thread pool exhaustion</li>
 * </ul>
 *
 * <h3>3. Service Resilience ({@link ServiceResilienceChaosTest})</h3>
 * <ul>
 *   <li>Circuit breaker effectiveness validation</li>
 *   <li>Retry mechanism testing</li>
 *   <li>Fallback behavior verification</li>
 *   <li>Graceful degradation testing</li>
 *   <li>Self-healing capabilities</li>
 * </ul>
 *
 * <h3>4. Data Consistency ({@link DataConsistencyChaosTest})</h3>
 * <ul>
 *   <li>Partial write scenarios</li>
 *   <li>Concurrent modification conflicts</li>
 *   <li>Transaction rollback testing</li>
 *   <li>Cache invalidation chaos</li>
 *   <li>State synchronization issues</li>
 * </ul>
 *
 * <h3>5. Edge Cases ({@link EdgeCaseChaosTest})</h3>
 * <ul>
 *   <li>Extreme concurrency (50,000+ requests)</li>
 *   <li>Oversized payload handling</li>
 *   <li>Long-running operations (simulated 1+ hours)</li>
 *   <li>Rapid service restarts</li>
 *   <li>Clock skew scenarios</li>
 * </ul>
 *
 * <h3>6. Recovery Testing ({@link RecoveryChaosTest})</h3>
 * <ul>
 *   <li>Time to recovery (MTTR) measurements</li>
 *   <li>Data integrity after recovery</li>
 *   <li>Service health restoration</li>
 *   <li>Client reconnection effectiveness</li>
 *   <li>System stability after failures</li>
 * </ul>
 *
 * <h2>Running the Tests</h2>
 *
 * <h3>Run Full Suite</h3>
 * <pre>
 * mvn test -Dtest=ChaosEngineeringSuite -Dgroups=chaos
 * </pre>
 *
 * <h3>Run Individual Categories</h3>
 * <pre>
 * mvn test -Dtest=NetworkChaosTest -Dgroups=chaos
 * mvn test -Dtest=ResourceChaosTest -Dgroups=chaos
 * mvn test -Dtest=ServiceResilienceChaosTest -Dgroups=chaos
 * mvn test -Dtest=DataConsistencyChaosTest -Dgroups=chaos
 * mvn test -Dtest=EdgeCaseChaosTest -Dgroups=chaos
 * mvn test -Dtest=RecoveryChaosTest -Dgroups=chaos
 * </pre>
 *
 * <h3>Run Existing Chaos Tests</h3>
 * <pre>
 * mvn test -Dtest=NetworkDelayResilienceTest -Dgroups=chaos
 * mvn test -Dtest=ServiceFailureResilienceTest -Dgroups=chaos
 * </pre>
 *
 * <h2>Design Principles</h2>
 *
 * <h3>Chicago TDD</h3>
 * All tests follow Chicago School TDD principles:
 * <ul>
 *   <li>Real database operations (H2 in-memory)</li>
 *   <li>Real circuit breakers and retry policies</li>
 *   <li>Real concurrent execution</li>
 *   <li>No mocks, stubs, or placeholder implementations</li>
 * </ul>
 *
 * <h3>Chaos Injection Patterns</h3>
 * <ul>
 *   <li>Latency injection via Thread.sleep()</li>
 *   <li>Failure injection via exception throwing</li>
 *   <li>Resource pressure via allocation and computation</li>
 *   <li>Partition simulation via connection management</li>
 * </ul>
 *
 * <h3>Blast Radius Control</h3>
 * All chaos experiments have controlled blast radius:
 * <ul>
 *   <li>Tests run in isolated H2 databases</li>
 *   <li>Virtual threads prevent thread exhaustion</li>
 *   <li>Timeouts prevent infinite hangs</li>
 *   <li>Assertions verify controlled failure rates</li>
 * </ul>
 *
 * <h2>Observability</h2>
 *
 * Tests output detailed metrics including:
 * <ul>
 *   <li>Success/failure rates</li>
 *   <li>Throughput (ops/sec)</li>
 *   <li>Latency percentiles</li>
 *   <li>Recovery times (MTTR)</li>
 * </ul>
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-19
 * @see ChaosEngineeringSuite
 * @see ChaosTestSupport
 */
package org.yawlfoundation.yawl.chaos;
