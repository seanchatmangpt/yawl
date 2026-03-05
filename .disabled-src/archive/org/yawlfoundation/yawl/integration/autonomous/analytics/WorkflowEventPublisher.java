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

package org.yawlfoundation.yawl.integration.autonomous.analytics;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.YWorkItemStatus;
import org.yawlfoundation.yawl.engine.spi.WorkflowEvent;
import org.yawlfoundation.yawl.engine.spi.WorkflowEventBus;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.SparqlEngine;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.SparqlEngineException;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import static org.yawlfoundation.yawl.integration.autonomous.analytics.WorkflowEventVocabulary.*;

/**
 * Subscribes to the YAWL {@link WorkflowEventBus} and persists workflow lifecycle events
 * as RDF triples in QLever via SPARQL 1.1 UPDATE.
 *
 * <p><strong>IMPORTANT:</strong> QLever is an embedded Java/C++ FFI bridge (NOT Docker/HTTP).
 * Use {@link org.yawlfoundation.yawl.integration.autonomous.marketplace.QLeverEmbeddedEngineAdapter}
 * to wrap {@code QLeverEmbeddedSparqlEngine} for use with this publisher.</p>
 *
 * <p>Every case start/complete/cancel and every task status transition (to Executing
 * or to a terminal state) is converted to a SPARQL {@code INSERT DATA} statement and
 * posted to the SPARQL engine. This makes the entire workflow execution history
 * queryable as a graph, enabling path analysis, bypass detection, SLA reporting,
 * and throughput queries via {@link WorkflowQueryService}.</p>
 *
 * <p>Engine unavailability never disrupts the workflow engine. All exceptions from
 * {@link SparqlEngine} are caught and logged at WARN level;
 * the engine event stream continues uninterrupted.</p>
 *
 * <p>Thread safety: all subscription handlers are invoked on virtual threads by
 * {@code FlowWorkflowEventBus}; the atomic sequence counter ensures globally unique
 * task-execution IRIs across concurrent executions.</p>
 *
 * @since YAWL 6.0
 * @see WorkflowEventVocabulary
 * @see WorkflowQueryService
 * @see org.yawlfoundation.yawl.integration.autonomous.marketplace.QLeverEmbeddedEngineAdapter
 */
public final class WorkflowEventPublisher implements AutoCloseable {

    private static final Logger log = LogManager.getLogger(WorkflowEventPublisher.class);

    /**
     * Monotonically increasing counter used to make task-execution IRIs unique even
     * when the same task fires multiple times in the same case.
     */
    private static final AtomicLong SEQ = new AtomicLong();

    private final SparqlEngine engine;

    /**
     * Creates a publisher that subscribes to the given bus and writes triples to the SPARQL engine.
     *
     * <p>Subscriptions are registered immediately in the constructor and remain active
     * for the lifetime of this object. Call {@link #close()} to release resources
     * (the bus subscriptions themselves are not individually cancellable; calling close
     * on the engine is a no-op).</p>
     *
     * @param engine the SPARQL engine to write to; must not be {@code null}.
     *               Use {@link org.yawlfoundation.yawl.integration.autonomous.marketplace.QLeverEmbeddedEngineAdapter}
     *               for embedded QLever.
     * @param bus    the workflow event bus to subscribe to; must not be {@code null}
     */
    public WorkflowEventPublisher(SparqlEngine engine, WorkflowEventBus bus) {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
        Objects.requireNonNull(bus, "bus must not be null");

        bus.subscribe(YEventType.CASE_STARTING,     this::onCaseStarting);
        bus.subscribe(YEventType.CASE_COMPLETED,    this::onCaseCompleted);
        bus.subscribe(YEventType.CASE_CANCELLED,    this::onCaseCancelled);
        bus.subscribe(YEventType.ITEM_STATUS_CHANGE, this::onItemStatusChange);
    }

    // -------------------------------------------------------------------------
    // Case event handlers
    // -------------------------------------------------------------------------

    private void onCaseStarting(WorkflowEvent event) {
        YIdentifier caseId = event.caseId();
        if (caseId == null) return;

        String caseIri = caseIri(caseId.toString());
        String now = event.timestamp().toString();

        // payload for CASE_STARTING is YSpecificationID (or null)
        String specId = extractSpecId(event.payload());

        String update = buildCaseInsert(caseIri, caseId.toString(), specId,
                "starting", now, null, -1L);
        executeUpdate(update, "CASE_STARTING", caseId.toString());
    }

    private void onCaseCompleted(WorkflowEvent event) {
        YIdentifier caseId = event.caseId();
        if (caseId == null) return;

        String caseIri = caseIri(caseId.toString());
        String startTime = null; // not available directly from event
        String endTime = event.timestamp().toString();

        String update = buildCaseStatusUpdate(caseIri, "completed", endTime);
        executeUpdate(update, "CASE_COMPLETED", caseId.toString());
    }

    private void onCaseCancelled(WorkflowEvent event) {
        YIdentifier caseId = event.caseId();
        if (caseId == null) return;

        String caseIri = caseIri(caseId.toString());
        String endTime = event.timestamp().toString();

        String update = buildCaseStatusUpdate(caseIri, "cancelled", endTime);
        executeUpdate(update, "CASE_CANCELLED", caseId.toString());
    }

    // -------------------------------------------------------------------------
    // Item status-change handler
    // -------------------------------------------------------------------------

    private void onItemStatusChange(WorkflowEvent event) {
        if (!(event.payload() instanceof YWorkItem item)) return;

        YWorkItemStatus status = item.getStatus();
        String caseIdStr = item.getCaseID().toString();
        String taskId = item.getTaskID();
        Instant now = event.timestamp();

        if (status == YWorkItemStatus.statusExecuting) {
            // Task started executing — insert a new TaskExecution resource
            long seqNr = SEQ.incrementAndGet();
            String taskExecIri = taskExecIri(caseIdStr, taskId, seqNr);
            String startTime = now.toString();
            String update = buildTaskInsert(taskExecIri, caseIdStr, taskId, "executing", startTime);
            executeUpdate(update, "ITEM_STATUS_CHANGE→executing", caseIdStr + "/" + taskId);

        } else if (isTerminal(status)) {
            // Task finished — update status and compute duration
            String endTime = now.toString();
            Instant startInstant = item.getStartTime();
            long durationMs = (startInstant != null)
                    ? now.toEpochMilli() - startInstant.toEpochMilli()
                    : -1L;

            // Use work item ID as a stable identifier for the terminal update
            String stableId = item.getCaseID().toString() + "_" + taskId;
            String update = buildTaskTerminalUpdate(stableId, caseIdStr, taskId,
                    statusLabel(status), endTime, durationMs);
            executeUpdate(update, "ITEM_STATUS_CHANGE→" + status.name(), caseIdStr + "/" + taskId);
        }
    }

    // -------------------------------------------------------------------------
    // SPARQL UPDATE builders
    // -------------------------------------------------------------------------

    /**
     * Builds a SPARQL INSERT DATA for a new CaseExecution.
     * Returns a complete SPARQL 1.1 Update string.
     */
    String buildCaseInsert(String caseIri, String caseIdStr, String specIdStr,
                           String status, String startTime, String endTime, long durationMs) {
        StringBuilder sb = new StringBuilder();
        sb.append("PREFIX wf: <").append(NS).append(">\n");
        sb.append("PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n\n");
        sb.append("INSERT DATA {\n");
        sb.append("  ").append(iri(caseIri)).append(" a ").append(iri(CASE_EXECUTION)).append(" ;\n");
        sb.append("    ").append(iri(CASE_ID)).append(" ").append(lit(caseIdStr)).append(" ;\n");
        if (specIdStr != null) {
            sb.append("    ").append(iri(SPEC_ID)).append(" ").append(lit(specIdStr)).append(" ;\n");
        }
        sb.append("    ").append(iri(CASE_STATUS)).append(" ").append(lit(status)).append(" ;\n");
        sb.append("    ").append(iri(CASE_START_TIME)).append(" ").append(litDateTime(startTime)).append(" .\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Builds a SPARQL INSERT DATA to record a case status change (completed/cancelled).
     * Appends end-time and status triples to the existing case resource.
     */
    String buildCaseStatusUpdate(String caseIri, String status, String endTime) {
        StringBuilder sb = new StringBuilder();
        sb.append("PREFIX wf: <").append(NS).append(">\n");
        sb.append("PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n\n");
        sb.append("INSERT DATA {\n");
        sb.append("  ").append(iri(caseIri)).append("\n");
        sb.append("    ").append(iri(CASE_STATUS)).append(" ").append(lit(status)).append(" ;\n");
        sb.append("    ").append(iri(CASE_END_TIME)).append(" ").append(litDateTime(endTime)).append(" .\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Builds a SPARQL INSERT DATA for a new TaskExecution (task started executing).
     */
    String buildTaskInsert(String taskExecIri, String caseIdStr, String taskId,
                           String status, String startTime) {
        StringBuilder sb = new StringBuilder();
        sb.append("PREFIX wf: <").append(NS).append(">\n");
        sb.append("PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n\n");
        sb.append("INSERT DATA {\n");
        sb.append("  ").append(iri(taskExecIri)).append(" a ").append(iri(TASK_EXECUTION)).append(" ;\n");
        sb.append("    ").append(iri(TASK_CASE_ID)).append(" ").append(lit(caseIdStr)).append(" ;\n");
        sb.append("    ").append(iri(TASK_ID)).append(" ").append(lit(taskId)).append(" ;\n");
        sb.append("    ").append(iri(TASK_STATUS)).append(" ").append(lit(status)).append(" ;\n");
        sb.append("    ").append(iri(TASK_START_TIME)).append(" ").append(litDateTime(startTime)).append(" .\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Builds a SPARQL INSERT DATA to record task terminal status (completed/cancelled/failed).
     * Uses a denormalised approach: inserts a new quad with endTime and durationMs so the
     * most-recent execution can be identified by endTime ordering.
     */
    String buildTaskTerminalUpdate(String stableId, String caseIdStr, String taskId,
                                   String status, String endTime, long durationMs) {
        String taskExecIri = NS + "taskdone/" + encode(stableId);
        StringBuilder sb = new StringBuilder();
        sb.append("PREFIX wf: <").append(NS).append(">\n");
        sb.append("PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n\n");
        sb.append("INSERT DATA {\n");
        sb.append("  ").append(iri(taskExecIri)).append(" a ").append(iri(TASK_EXECUTION)).append(" ;\n");
        sb.append("    ").append(iri(TASK_CASE_ID)).append(" ").append(lit(caseIdStr)).append(" ;\n");
        sb.append("    ").append(iri(TASK_ID)).append(" ").append(lit(taskId)).append(" ;\n");
        sb.append("    ").append(iri(TASK_STATUS)).append(" ").append(lit(status)).append(" ;\n");
        sb.append("    ").append(iri(TASK_END_TIME)).append(" ").append(litDateTime(endTime));
        if (durationMs >= 0) {
            sb.append(" ;\n    ").append(iri(TASK_DURATION_MS)).append(" ").append(litLong(durationMs));
        }
        sb.append(" .\n}");
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void executeUpdate(String updateQuery, String context, String id) {
        try {
            engine.sparqlUpdate(updateQuery);
        } catch (SparqlEngineException e) {
            log.warn("WorkflowEventPublisher: failed to write {} event for {} to QLever: {}",
                    context, id, e.getMessage());
        }
    }

    private static String extractSpecId(Object payload) {
        if (payload == null) return null;
        return payload.toString();
    }

    private static boolean isTerminal(YWorkItemStatus status) {
        return status == YWorkItemStatus.statusComplete
                || status == YWorkItemStatus.statusForcedComplete
                || status == YWorkItemStatus.statusFailed
                || status == YWorkItemStatus.statusDeleted
                || status == YWorkItemStatus.statusCancelledByCase
                || status == YWorkItemStatus.statusWithdrawn;
    }

    private static String statusLabel(YWorkItemStatus status) {
        return switch (status) {
            case statusComplete, statusForcedComplete -> "completed";
            case statusFailed -> "failed";
            case statusDeleted, statusCancelledByCase, statusWithdrawn -> "cancelled";
            default -> status.name().toLowerCase();
        };
    }

    /** URL-path-safe encoding of identifiers. Mirrors {@link WorkflowEventVocabulary}. */
    private static String encode(String s) {
        return s.replace(":", "_").replace("/", "_").replace(" ", "_");
    }

    @Override
    public void close() {
        // Bus subscriptions are not individually cancellable in the current API.
        // The engine itself is managed externally.
    }
}
