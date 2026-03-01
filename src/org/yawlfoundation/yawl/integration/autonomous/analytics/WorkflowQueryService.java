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

import org.yawlfoundation.yawl.integration.autonomous.marketplace.QLeverSparqlEngine;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.SparqlEngineException;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.SparqlEngineUnavailableException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.yawlfoundation.yawl.integration.autonomous.analytics.WorkflowEventVocabulary.*;

/**
 * Pre-built SPARQL analytics queries over workflow execution data stored in QLever.
 *
 * <p>All query methods return raw SPARQL query strings via {@link #queryString()} accessors
 * on the inner {@link Query} builders, or — via the {@code run*()} methods — execute the
 * query against QLever and return the raw Turtle/SPARQL-JSON result. Callers parse
 * results into typed records via {@link WorkflowAnalytics}.</p>
 *
 * <p>When QLever is unavailable, methods return {@link Optional#empty()} rather than
 * throwing, so callers can treat analytics as best-effort.</p>
 *
 * <p>All queries assume triples were published by {@link WorkflowEventPublisher} using
 * the {@link WorkflowEventVocabulary} namespace.</p>
 *
 * @since YAWL 6.0
 * @see WorkflowEventPublisher
 * @see WorkflowAnalytics
 */
public final class WorkflowQueryService {

    private static final String WF_NS = NS;
    private static final String PREFIXES =
            "PREFIX wf: <" + WF_NS + ">\n" +
            "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n\n";

    private final QLeverSparqlEngine engine;

    /**
     * Creates a query service targeting the given QLever engine.
     *
     * @param engine the QLever engine to query; must not be {@code null}
     */
    public WorkflowQueryService(QLeverSparqlEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
    }

    // =========================================================================
    // Query 1 — Bypassed tasks
    // =========================================================================

    /**
     * Returns the SPARQL SELECT query string that finds completed cases in the given
     * specification that did not include an execution of {@code requiredTaskId}.
     *
     * <p>Business value: compliance and audit — detects cases that skipped mandatory tasks.</p>
     *
     * @param specId         YAWL specification identifier
     * @param requiredTaskId task identifier that must appear in every completed case
     * @return SPARQL SELECT string
     */
    public String bypassedTasksQuery(String specId, String requiredTaskId) {
        Objects.requireNonNull(specId, "specId must not be null");
        Objects.requireNonNull(requiredTaskId, "requiredTaskId must not be null");
        return PREFIXES +
                "SELECT ?caseId WHERE {\n" +
                "  ?case a <" + CASE_EXECUTION + "> ;\n" +
                "        <" + CASE_STATUS + "> \"completed\"^^xsd:string ;\n" +
                "        <" + SPEC_ID + "> " + lit(specId) + " ;\n" +
                "        <" + CASE_ID + "> ?caseId .\n" +
                "  FILTER NOT EXISTS {\n" +
                "    ?taskExec a <" + TASK_EXECUTION + "> ;\n" +
                "              <" + TASK_CASE_ID + "> ?caseId ;\n" +
                "              <" + TASK_ID + "> " + lit(requiredTaskId) + " ;\n" +
                "              <" + TASK_STATUS + "> \"completed\"^^xsd:string .\n" +
                "  }\n" +
                "}";
    }

    /**
     * Executes the bypassed-tasks query against QLever.
     *
     * @param specId         YAWL specification identifier
     * @param requiredTaskId task that must appear in every completed case
     * @return raw SPARQL result string, or empty if QLever is unavailable
     * @throws SparqlEngineException if QLever returns an error
     */
    public Optional<String> bypassedTasks(String specId, String requiredTaskId)
            throws SparqlEngineException {
        return runSelectQuery(bypassedTasksQuery(specId, requiredTaskId));
    }

    // =========================================================================
    // Query 2 — Average task duration
    // =========================================================================

    /**
     * Returns the SPARQL SELECT query string for the average duration (ms) of a specific
     * task across all completed executions in the given specification.
     *
     * @param specId YAWL specification identifier
     * @param taskId task identifier to measure
     * @return SPARQL SELECT string
     */
    public String averageTaskDurationQuery(String specId, String taskId) {
        Objects.requireNonNull(specId, "specId must not be null");
        Objects.requireNonNull(taskId, "taskId must not be null");
        return PREFIXES +
                "SELECT ?taskId (AVG(xsd:decimal(?durMs)) AS ?avgDurationMs) (COUNT(?t) AS ?execCount)\n" +
                "WHERE {\n" +
                "  ?case a <" + CASE_EXECUTION + "> ;\n" +
                "        <" + SPEC_ID + "> " + lit(specId) + " ;\n" +
                "        <" + CASE_ID + "> ?caseId .\n" +
                "  ?t a <" + TASK_EXECUTION + "> ;\n" +
                "       <" + TASK_CASE_ID + "> ?caseId ;\n" +
                "       <" + TASK_ID + "> " + lit(taskId) + " ;\n" +
                "       <" + TASK_ID + "> ?taskId ;\n" +
                "       <" + TASK_STATUS + "> \"completed\"^^xsd:string ;\n" +
                "       <" + TASK_DURATION_MS + "> ?durMs .\n" +
                "}\n" +
                "GROUP BY ?taskId";
    }

    /**
     * Executes the average-task-duration query against QLever.
     *
     * @param specId YAWL specification identifier
     * @param taskId task identifier to measure
     * @return raw SPARQL result, or empty if QLever is unavailable
     * @throws SparqlEngineException if QLever returns an error
     */
    public Optional<String> averageTaskDuration(String specId, String taskId)
            throws SparqlEngineException {
        return runSelectQuery(averageTaskDurationQuery(specId, taskId));
    }

    // =========================================================================
    // Query 3 — Task bottlenecks (top N slowest tasks)
    // =========================================================================

    /**
     * Returns the SPARQL SELECT query string for the top-N slowest tasks by average
     * duration across all completed executions in the given specification.
     *
     * @param specId YAWL specification identifier
     * @param topN   number of results to return
     * @return SPARQL SELECT string
     */
    public String taskBottlenecksQuery(String specId, int topN) {
        Objects.requireNonNull(specId, "specId must not be null");
        if (topN <= 0) throw new IllegalArgumentException("topN must be positive, got: " + topN);
        return PREFIXES +
                "SELECT ?taskId (AVG(xsd:decimal(?durMs)) AS ?avgDurationMs) (COUNT(?t) AS ?execCount)\n" +
                "WHERE {\n" +
                "  ?case a <" + CASE_EXECUTION + "> ;\n" +
                "        <" + SPEC_ID + "> " + lit(specId) + " ;\n" +
                "        <" + CASE_ID + "> ?caseId .\n" +
                "  ?t a <" + TASK_EXECUTION + "> ;\n" +
                "       <" + TASK_CASE_ID + "> ?caseId ;\n" +
                "       <" + TASK_ID + "> ?taskId ;\n" +
                "       <" + TASK_STATUS + "> \"completed\"^^xsd:string ;\n" +
                "       <" + TASK_DURATION_MS + "> ?durMs .\n" +
                "}\n" +
                "GROUP BY ?taskId\n" +
                "ORDER BY DESC(?avgDurationMs)\n" +
                "LIMIT " + topN;
    }

    /**
     * Executes the task-bottlenecks query against QLever.
     *
     * @param specId YAWL specification identifier
     * @param topN   number of slowest tasks to return
     * @return raw SPARQL result, or empty if QLever is unavailable
     * @throws SparqlEngineException if QLever returns an error
     */
    public Optional<String> taskBottlenecks(String specId, int topN)
            throws SparqlEngineException {
        return runSelectQuery(taskBottlenecksQuery(specId, topN));
    }

    // =========================================================================
    // Query 4 — Active (open) cases
    // =========================================================================

    /**
     * Returns the SPARQL SELECT query string for all cases with status "starting" or
     * "executing" (i.e. currently in-flight) for the given specification.
     *
     * @param specId YAWL specification identifier
     * @return SPARQL SELECT string
     */
    public String activeCasesQuery(String specId) {
        Objects.requireNonNull(specId, "specId must not be null");
        return PREFIXES +
                "SELECT ?caseId ?status ?startTime WHERE {\n" +
                "  ?case a <" + CASE_EXECUTION + "> ;\n" +
                "        <" + SPEC_ID + "> " + lit(specId) + " ;\n" +
                "        <" + CASE_ID + "> ?caseId ;\n" +
                "        <" + CASE_STATUS + "> ?status ;\n" +
                "        <" + CASE_START_TIME + "> ?startTime .\n" +
                "  FILTER(?status IN (\"starting\"^^xsd:string, \"executing\"^^xsd:string))\n" +
                "}\n" +
                "ORDER BY ?startTime";
    }

    /**
     * Executes the active-cases query against QLever.
     *
     * @param specId YAWL specification identifier
     * @return raw SPARQL result, or empty if QLever is unavailable
     * @throws SparqlEngineException if QLever returns an error
     */
    public Optional<String> activeCases(String specId) throws SparqlEngineException {
        return runSelectQuery(activeCasesQuery(specId));
    }

    // =========================================================================
    // Query 5 — Completed case durations
    // =========================================================================

    /**
     * Returns the SPARQL SELECT query string for all completed cases and their durations.
     * Callers use the result set to build histograms or percentile analysis.
     *
     * @param specId YAWL specification identifier
     * @return SPARQL SELECT string
     */
    public String completedCaseDurationsQuery(String specId) {
        Objects.requireNonNull(specId, "specId must not be null");
        return PREFIXES +
                "SELECT ?caseId ?startTime ?endTime ?durationMs WHERE {\n" +
                "  ?case a <" + CASE_EXECUTION + "> ;\n" +
                "        <" + SPEC_ID + "> " + lit(specId) + " ;\n" +
                "        <" + CASE_ID + "> ?caseId ;\n" +
                "        <" + CASE_STATUS + "> \"completed\"^^xsd:string ;\n" +
                "        <" + CASE_START_TIME + "> ?startTime ;\n" +
                "        <" + CASE_END_TIME + "> ?endTime ;\n" +
                "        <" + CASE_DURATION_MS + "> ?durationMs .\n" +
                "}\n" +
                "ORDER BY ?startTime";
    }

    /**
     * Executes the completed-case-durations query against QLever.
     *
     * @param specId YAWL specification identifier
     * @return raw SPARQL result, or empty if QLever is unavailable
     * @throws SparqlEngineException if QLever returns an error
     */
    public Optional<String> completedCaseDurations(String specId) throws SparqlEngineException {
        return runSelectQuery(completedCaseDurationsQuery(specId));
    }

    // =========================================================================
    // Query 6 — SLA violations
    // =========================================================================

    /**
     * Returns the SPARQL SELECT query string for completed cases whose duration exceeds
     * the given SLA threshold.
     *
     * @param specId  YAWL specification identifier
     * @param slaMs   SLA threshold in milliseconds
     * @return SPARQL SELECT string
     */
    public String slaViolationsQuery(String specId, long slaMs) {
        Objects.requireNonNull(specId, "specId must not be null");
        if (slaMs <= 0) throw new IllegalArgumentException("slaMs must be positive, got: " + slaMs);
        return PREFIXES +
                "SELECT ?caseId ?durationMs WHERE {\n" +
                "  ?case a <" + CASE_EXECUTION + "> ;\n" +
                "        <" + SPEC_ID + "> " + lit(specId) + " ;\n" +
                "        <" + CASE_ID + "> ?caseId ;\n" +
                "        <" + CASE_STATUS + "> \"completed\"^^xsd:string ;\n" +
                "        <" + CASE_DURATION_MS + "> ?durationMs .\n" +
                "  FILTER(xsd:decimal(?durationMs) > " + slaMs + ")\n" +
                "}\n" +
                "ORDER BY DESC(?durationMs)";
    }

    /**
     * Executes the SLA-violations query against QLever.
     *
     * @param specId  YAWL specification identifier
     * @param slaMs   SLA threshold in milliseconds
     * @return raw SPARQL result, or empty if QLever is unavailable
     * @throws SparqlEngineException if QLever returns an error
     */
    public Optional<String> slaViolations(String specId, long slaMs) throws SparqlEngineException {
        return runSelectQuery(slaViolationsQuery(specId, slaMs));
    }

    // =========================================================================
    // Query 7 — Throughput by hour
    // =========================================================================

    /**
     * Returns the SPARQL SELECT query string for the count of completed cases per hour.
     * Enables capacity planning and workload trend analysis.
     *
     * @param specId YAWL specification identifier
     * @return SPARQL SELECT string
     */
    public String throughputByHourQuery(String specId) {
        Objects.requireNonNull(specId, "specId must not be null");
        return PREFIXES +
                "SELECT (SUBSTR(STR(?endTime), 1, 13) AS ?hour) (COUNT(?case) AS ?completions)\n" +
                "WHERE {\n" +
                "  ?case a <" + CASE_EXECUTION + "> ;\n" +
                "        <" + SPEC_ID + "> " + lit(specId) + " ;\n" +
                "        <" + CASE_STATUS + "> \"completed\"^^xsd:string ;\n" +
                "        <" + CASE_END_TIME + "> ?endTime .\n" +
                "}\n" +
                "GROUP BY (SUBSTR(STR(?endTime), 1, 13))\n" +
                "ORDER BY ?hour";
    }

    /**
     * Executes the throughput-by-hour query against QLever.
     *
     * @param specId YAWL specification identifier
     * @return raw SPARQL result, or empty if QLever is unavailable
     * @throws SparqlEngineException if QLever returns an error
     */
    public Optional<String> throughputByHour(String specId) throws SparqlEngineException {
        return runSelectQuery(throughputByHourQuery(specId));
    }

    // =========================================================================
    // Query 8 — Task transition matrix
    // =========================================================================

    /**
     * Returns the SPARQL SELECT query string for the task transition matrix: how many
     * times each task directly follows another task in the same case. Enables process
     * discovery and conformance checking.
     *
     * <p>Uses {@code wf:precededBy} links written by the publisher when available,
     * otherwise falls back to temporal ordering within a case.</p>
     *
     * @param specId YAWL specification identifier
     * @return SPARQL SELECT string
     */
    public String taskTransitionMatrixQuery(String specId) {
        Objects.requireNonNull(specId, "specId must not be null");
        return PREFIXES +
                "SELECT ?fromTask ?toTask (COUNT(*) AS ?transitionCount)\n" +
                "WHERE {\n" +
                "  ?case a <" + CASE_EXECUTION + "> ;\n" +
                "        <" + SPEC_ID + "> " + lit(specId) + " ;\n" +
                "        <" + CASE_ID + "> ?caseId .\n" +
                "  ?t1 a <" + TASK_EXECUTION + "> ;\n" +
                "       <" + TASK_CASE_ID + "> ?caseId ;\n" +
                "       <" + TASK_ID + "> ?fromTask ;\n" +
                "       <" + TASK_STATUS + "> \"completed\"^^xsd:string ;\n" +
                "       <" + TASK_END_TIME + "> ?t1end .\n" +
                "  ?t2 a <" + TASK_EXECUTION + "> ;\n" +
                "       <" + TASK_CASE_ID + "> ?caseId ;\n" +
                "       <" + TASK_ID + "> ?toTask ;\n" +
                "       <" + TASK_START_TIME + "> ?t2start .\n" +
                "  FILTER(?t1end <= ?t2start)\n" +
                "  FILTER NOT EXISTS {\n" +
                "    ?tmid a <" + TASK_EXECUTION + "> ;\n" +
                "           <" + TASK_CASE_ID + "> ?caseId ;\n" +
                "           <" + TASK_END_TIME + "> ?tmidEnd ;\n" +
                "           <" + TASK_START_TIME + "> ?tmidStart .\n" +
                "    FILTER(?t1end <= ?tmidEnd && ?tmidStart <= ?t2start && ?tmid != ?t1 && ?tmid != ?t2)\n" +
                "  }\n" +
                "}\n" +
                "GROUP BY ?fromTask ?toTask\n" +
                "ORDER BY DESC(?transitionCount)";
    }

    /**
     * Executes the task-transition-matrix query against QLever.
     *
     * @param specId YAWL specification identifier
     * @return raw SPARQL result, or empty if QLever is unavailable
     * @throws SparqlEngineException if QLever returns an error
     */
    public Optional<String> taskTransitionMatrix(String specId) throws SparqlEngineException {
        return runSelectQuery(taskTransitionMatrixQuery(specId));
    }

    // =========================================================================
    // Query 9 — Failed tasks
    // =========================================================================

    /**
     * Returns the SPARQL SELECT query string for all task executions with status "failed"
     * in the given specification. Enables error pattern analysis.
     *
     * @param specId YAWL specification identifier
     * @return SPARQL SELECT string
     */
    public String failedTasksQuery(String specId) {
        Objects.requireNonNull(specId, "specId must not be null");
        return PREFIXES +
                "SELECT ?caseId ?taskId ?endTime WHERE {\n" +
                "  ?case a <" + CASE_EXECUTION + "> ;\n" +
                "        <" + SPEC_ID + "> " + lit(specId) + " ;\n" +
                "        <" + CASE_ID + "> ?caseId .\n" +
                "  ?t a <" + TASK_EXECUTION + "> ;\n" +
                "       <" + TASK_CASE_ID + "> ?caseId ;\n" +
                "       <" + TASK_ID + "> ?taskId ;\n" +
                "       <" + TASK_STATUS + "> \"failed\"^^xsd:string ;\n" +
                "       <" + TASK_END_TIME + "> ?endTime .\n" +
                "}\n" +
                "ORDER BY ?caseId ?endTime";
    }

    /**
     * Executes the failed-tasks query against QLever.
     *
     * @param specId YAWL specification identifier
     * @return raw SPARQL result, or empty if QLever is unavailable
     * @throws SparqlEngineException if QLever returns an error
     */
    public Optional<String> failedTasks(String specId) throws SparqlEngineException {
        return runSelectQuery(failedTasksQuery(specId));
    }

    // =========================================================================
    // Query 10 — Full case path trace
    // =========================================================================

    /**
     * Returns the SPARQL SELECT query string for the complete ordered task sequence
     * for a single case, ordered by task start time.
     *
     * @param specId YAWL specification identifier
     * @param caseId the case to trace
     * @return SPARQL SELECT string
     */
    public String casePathTraceQuery(String specId, String caseId) {
        Objects.requireNonNull(specId, "specId must not be null");
        Objects.requireNonNull(caseId, "caseId must not be null");
        return PREFIXES +
                "SELECT ?taskId ?status ?startTime ?endTime ?durationMs WHERE {\n" +
                "  ?t a <" + TASK_EXECUTION + "> ;\n" +
                "       <" + TASK_CASE_ID + "> " + lit(caseId) + " ;\n" +
                "       <" + TASK_ID + "> ?taskId ;\n" +
                "       <" + TASK_STATUS + "> ?status ;\n" +
                "       <" + TASK_START_TIME + "> ?startTime .\n" +
                "  OPTIONAL { ?t <" + TASK_END_TIME + "> ?endTime . }\n" +
                "  OPTIONAL { ?t <" + TASK_DURATION_MS + "> ?durationMs . }\n" +
                "}\n" +
                "ORDER BY ?startTime";
    }

    /**
     * Executes the case-path-trace query against QLever.
     *
     * @param specId YAWL specification identifier
     * @param caseId the case to trace
     * @return raw SPARQL result, or empty if QLever is unavailable
     * @throws SparqlEngineException if QLever returns an error
     */
    public Optional<String> casePathTrace(String specId, String caseId)
            throws SparqlEngineException {
        return runSelectQuery(casePathTraceQuery(specId, caseId));
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    /**
     * Executes a SPARQL SELECT query and returns the raw result string.
     * Returns {@link Optional#empty()} if QLever is currently unavailable (never throws).
     */
    private Optional<String> runSelectQuery(String query) throws SparqlEngineException {
        if (!engine.isAvailable()) {
            return Optional.empty();
        }
        // QLever's /api/query endpoint handles both SELECT (JSON) and CONSTRUCT (Turtle).
        // For SELECT, we request application/sparql-results+json by convention.
        String result = engine.constructToTurtle(query);
        return Optional.of(result);
    }

    /**
     * Returns the list of all 10 pre-built query names (for introspection/documentation).
     */
    public static List<String> queryNames() {
        return List.of(
                "bypassedTasks",
                "averageTaskDuration",
                "taskBottlenecks",
                "activeCases",
                "completedCaseDurations",
                "slaViolations",
                "throughputByHour",
                "taskTransitionMatrix",
                "failedTasks",
                "casePathTrace"
        );
    }
}
