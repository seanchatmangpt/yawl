/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * Task scheduling and time-based workflow triggers for YAWL v6.0.0.
 *
 * <p>This package provides scheduling infrastructure for time-based workflow
 * operations including delayed case launches, recurring workflow executions,
 * and time-based task activations. Built on Java 21+ virtual threads for
 * efficient handling of large numbers of scheduled tasks.
 *
 * <h2>Core Components</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.scheduling.Constants} -
 *       Configuration constants for scheduling intervals, timeouts, and defaults</li>
 *   <li>{@link org.yawlfoundation.yawl.scheduling.Mapping} -
 *       Data mapping utilities for converting between scheduling representations</li>
 *   <li>{@link org.yawlfoundation.yawl.scheduling.SchedulingException} -
 *       Domain-specific exception for scheduling failures with contextual information</li>
 * </ul>
 *
 * <h2>Scheduling Capabilities</h2>
 * <ul>
 *   <li><b>Delayed Case Launch</b> - Schedule cases to start at a future timestamp</li>
 *   <li><b>Recurring Execution</b> - Cron-like scheduling for periodic workflow runs</li>
 *   <li><b>Timer Events</b> - Time-based triggers within workflow definitions</li>
 *   <li><b>Timeout Handling</b> - Automatic escalation when tasks exceed time limits</li>
 *   <li><b>Schedule Management</b> - Pause, resume, cancel, and query scheduled tasks</li>
 * </ul>
 *
 * <h2>Integration with YAWL Engine</h2>
 * <p>Scheduled tasks integrate with the YAWL engine through standard interfaces:
 * <pre>{@code
 * // Schedule a case for future execution
 * ScheduledCase future = scheduler.scheduleCase(
 *     specId,
 *     caseData,
 *     Instant.parse("2026-03-01T09:00:00Z")
 * );
 *
 * // Schedule recurring execution (daily at 6 AM UTC)
 * RecurringSchedule daily = scheduler.scheduleRecurring(
 *     specId,
 *     caseData,
 *     CronExpression.dailyAt(6, 0)
 * );
 *
 * // Query upcoming scheduled cases
 * List<ScheduledCase> upcoming = scheduler.getUpcoming(
 *     Instant.now(),
 *     Instant.now().plus(Duration.ofDays(7))
 * );
 * }</pre>
 *
 * <h2>Virtual Thread Architecture</h2>
 * <p>The scheduling subsystem uses virtual threads for:
 * <ul>
 *   <li>Timer management without blocking platform threads</li>
 *   <li>Concurrent execution of scheduled tasks</li>
 *   <li>Efficient waiting for time-based triggers</li>
 *   <li>Scalable handling of thousands of pending schedules</li>
 * </ul>
 *
 * <h2>Persistence</h2>
 * <p>Scheduled tasks are persisted to survive engine restarts:
 * <ul>
 *   <li>Schedule definitions stored in database with timestamps</li>
 *   <li>Recovery on startup for missed executions (configurable)</li>
 *   <li>Transaction-safe scheduling with rollback support</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.scheduling;
