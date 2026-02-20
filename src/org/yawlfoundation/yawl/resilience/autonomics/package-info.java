/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and organisations
 * who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute
 * it and/or modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation.
 */

/**
 * Autonomics Framework for Self-Healing Workflows.
 *
 * <h2>Overview</h2>
 * <p>
 * This package provides automatic failure detection, recovery, and escalation
 * for YAWL workflows. Systems using autonomics can operate with minimal human
 * intervention, automatically recovering from transient failures and alerting
 * operators to critical issues.
 * </p>
 *
 * <h2>Core Components</h2>
 * <ul>
 *   <li><strong>WorkflowAutonomicsEngine</strong> — Main autonomics orchestrator</li>
 *   <li><strong>HealthMonitoring</strong> — Detect stuck/slow workflows</li>
 *   <li><strong>AutoRetry</strong> — Retry transient failures with backoff</li>
 *   <li><strong>DeadLetterQueue</strong> — Capture unrecoverable failures</li>
 *   <li><strong>Escalation</strong> — Alert operators to critical issues</li>
 * </ul>
 *
 * <h2>Self-Healing Behaviors</h2>
 *
 * <h3>Transient Failure Recovery</h3>
 * <pre>{@code
 * ConnectionException (transient)
 *   → Retry with exponential backoff (100ms, 200ms, 400ms...)
 *   → Max 3 attempts
 *   → If all fail: escalate to dead letter queue
 * }</pre>
 *
 * <h3>Stuck Workflow Detection</h3>
 * <pre>{@code
 * Workflow idle for 5+ minutes
 *   → Health monitor detects
 *   → Check for deadlocks/resource issues
 *   → Attempt auto-recovery (reset blockers)
 *   → If fails: escalate to operator
 * }</pre>
 *
 * <h3>Critical Exception Escalation</h3>
 * <pre>{@code
 * OutOfMemoryException or SecurityException (fatal)
 *   → Capture case state
 *   → Add to dead letter queue
 *   → Alert operator immediately
 *   → Block case from processing
 * }</pre>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * YStatelessEngine engine = YStatelessEngine.getInstance();
 * WorkflowAutonomicsEngine autonomics = new WorkflowAutonomicsEngine(engine);
 *
 * // Register retry policies for known transient errors
 * autonomics.registerRetryPolicy(
 *     "ConnectionException",
 *     new RetryPolicy(3, 100, 2.0, true)  // max 3 attempts, exponential backoff
 * );
 *
 * // Start health monitoring (check every 30 seconds)
 * autonomics.startHealthMonitoring(Duration.ofSeconds(30));
 *
 * // Now run workflows...
 * YIdentifier caseID = engine.createCase(spec);
 *
 * // Autonomics will automatically:
 * // - Retry failed tasks
 * // - Detect stuck cases
 * // - Escalate critical issues
 *
 * // Monitor health
 * HealthReport health = autonomics.getHealthReport();
 * System.out.println("Active cases: " + health.activeCases());
 * System.out.println("Stuck cases: " + health.stuckCases());
 *
 * // Handle unrecoverable failures
 * DeadLetterQueue dlq = autonomics.getDeadLetterQueue();
 * while (dlq.size() > 0) {
 *     StuckCase stuck = dlq.poll().orElse(null);
 *     if (stuck != null) {
 *         operatorAlert.escalate(stuck.caseID(), stuck.reason());
 *     }
 * }
 * }</pre>
 *
 * <h2>Configuration Best Practices</h2>
 *
 * <h3>Transient vs Permanent Failures</h3>
 * <table border="1">
 *   <tr>
 *     <th>Exception Type</th>
 *     <th>Transient?</th>
 *     <th>Retry Policy</th>
 *   </tr>
 *   <tr>
 *     <td>ConnectionException</td>
 *     <td>YES</td>
 *     <td>Retry 3x with backoff</td>
 *   </tr>
 *   <tr>
 *     <td>TimeoutException</td>
 *     <td>YES</td>
 *     <td>Retry 2x with backoff</td>
 *   </tr>
 *   <tr>
 *     <td>IllegalArgumentException</td>
 *     <td>NO</td>
 *     <td>No retry, escalate immediately</td>
 *   </tr>
 *   <tr>
 *     <td>OutOfMemoryException</td>
 *     <td>NO</td>
 *     <td>No retry, critical escalation</td>
 *   </tr>
 * </table>
 *
 * <h3>Backoff Strategy</h3>
 * <pre>
 * Exponential backoff prevents thundering herd:
 * Attempt 1: 100ms
 * Attempt 2: 200ms (100 * 2^1)
 * Attempt 3: 400ms (100 * 2^2)
 * Attempt 4: 800ms (100 * 2^3)
 *
 * Use multiplier 2.0 for most scenarios.
 * </pre>
 *
 * <h3>Stuck Threshold</h3>
 * <pre>
 * Recommend: 5-10 minutes for production workflows
 * Reason: Allows for legitimate long-running tasks
 * Monitor: Check health report regularly for patterns
 * </pre>
 *
 * <h2>Monitoring and Observability</h2>
 *
 * <p>
 * Autonomics integrates with OpenTelemetry for observability:
 * </p>
 *
 * <h3>Metrics Exported</h3>
 * <ul>
 *   <li>yawl_autonomics_retries_total — Total auto-retries executed</li>
 *   <li>yawl_autonomics_stuck_cases_total — Cases detected as stuck</li>
 *   <li>yawl_autonomics_dlq_size — Dead letter queue depth</li>
 *   <li>yawl_autonomics_recovery_success_total — Successful auto-recoveries</li>
 *   <li>yawl_autonomics_recovery_failure_total — Failed auto-recoveries</li>
 * </ul>
 *
 * <h3>Events Recorded</h3>
 * <ul>
 *   <li>autonomics.retry.scheduled — Auto-retry scheduled</li>
 *   <li>autonomics.case.stuck — Case detected stuck</li>
 *   <li>autonomics.recovery.attempted — Auto-recovery initiated</li>
 *   <li>autonomics.escalation.critical — Critical escalation triggered</li>
 * </ul>
 *
 * <h2>Implementation Notes</h2>
 *
 * <p>
 * <strong>Thread Safety</strong>: All classes use ConcurrentHashMap and
 * ScheduledExecutorService for safe concurrent access.
 * </p>
 *
 * <p>
 * <strong>No Mocks/Stubs</strong>: This package provides real, production-ready
 * autonomics implementations. All methods execute real recovery logic or throw
 * exceptions.
 * </p>
 *
 * <p>
 * <strong>Extensibility</strong>: Implement custom recovery strategies by
 * extending RetryCoordinator or HealthMonitor.
 * </p>
 *
 * @author Claude Code / GODSPEED Protocol
 * @version 6.0.0
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.resilience.autonomics;
