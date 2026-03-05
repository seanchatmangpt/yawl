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
 * Event Sourcing pattern implementation for YAWL workflow state management (v6.0.0).
 *
 * <p>This package implements the Event Sourcing architectural pattern where the
 * workflow event log is the authoritative source of truth for all case state.
 * The current state of any running or completed workflow case is derived by
 * replaying its ordered event sequence from the event store.
 *
 * <h2>Core Concepts</h2>
 * <ul>
 *   <li><b>Event Store</b> - {@link org.yawlfoundation.yawl.integration.eventsourcing.WorkflowEventStore}
 *       is an append-only log keyed by (specId, caseId, sequenceNumber). Events are
 *       immutable once written. The store supports JDBC and in-memory backends.</li>
 *   <li><b>Event Replay</b> - {@link org.yawlfoundation.yawl.integration.eventsourcing.EventReplayer}
 *       reconstructs case state by applying events in sequence order. Supports
 *       full replay (from event 0) and partial replay (from a snapshot + delta).</li>
 *   <li><b>Snapshots</b> - {@link org.yawlfoundation.yawl.integration.eventsourcing.CaseSnapshot}
 *       captures materialized case state at a sequence checkpoint to bound replay cost.</li>
 *   <li><b>Temporal Queries</b> - {@link org.yawlfoundation.yawl.integration.eventsourcing.TemporalCaseQuery}
 *       answers "what was the state of case X at time T?" by replaying only events
 *       with timestamp &lt;= T.</li>
 * </ul>
 *
 * <h2>Consistency Guarantees</h2>
 * <p>The event store enforces optimistic concurrency via sequence number compare-and-swap:
 * a writer supplies the expected next sequence number; if another writer has already
 * appended at that position, an {@link org.yawlfoundation.yawl.integration.eventsourcing.ConcurrentModificationException}
 * is thrown and the operation must be retried.
 *
 * <h2>JDBC Schema</h2>
 * <pre>
 * CREATE TABLE workflow_events (
 *   id              BIGINT PRIMARY KEY AUTO_INCREMENT,
 *   event_id        VARCHAR(36)  NOT NULL UNIQUE,
 *   spec_id         VARCHAR(255) NOT NULL,
 *   case_id         VARCHAR(255) NOT NULL,
 *   seq_num         BIGINT       NOT NULL,
 *   event_type      VARCHAR(64)  NOT NULL,
 *   event_timestamp TIMESTAMP(6) NOT NULL,
 *   schema_version  VARCHAR(16)  NOT NULL DEFAULT '1.0',
 *   payload_json    TEXT         NOT NULL,
 *   UNIQUE (case_id, seq_num)
 * );
 *
 * CREATE TABLE case_snapshots (
 *   case_id         VARCHAR(255) NOT NULL,
 *   seq_num         BIGINT       NOT NULL,
 *   snapshot_ts     TIMESTAMP(6) NOT NULL,
 *   state_json      TEXT         NOT NULL,
 *   PRIMARY KEY (case_id, seq_num)
 * );
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.integration.eventsourcing;
