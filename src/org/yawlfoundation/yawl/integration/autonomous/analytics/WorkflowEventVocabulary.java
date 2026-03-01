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

/**
 * RDF vocabulary constants for the YAWL workflow analytics ontology.
 *
 * <p>All IRIs use the {@value #NS} namespace. Two RDF classes are defined:
 * {@link #CASE_EXECUTION} for workflow case instances and {@link #TASK_EXECUTION}
 * for individual task executions within a case.</p>
 *
 * <p>IRI patterns for subject resources:</p>
 * <ul>
 *   <li>{@code <http://yawlfoundation.org/yawl/analytics#case/{caseId}>} → CaseExecution</li>
 *   <li>{@code <http://yawlfoundation.org/yawl/analytics#task/{caseId}/{taskId}/{seqNr}>}
 *       → TaskExecution</li>
 * </ul>
 *
 * @since YAWL 6.0
 */
public final class WorkflowEventVocabulary {

    /** Base namespace for all analytics terms. */
    public static final String NS = "http://yawlfoundation.org/yawl/analytics#";

    /** Prefix string for Turtle {@code @prefix wf: <NS> .} declarations. */
    public static final String PREFIX_DECL = "@prefix wf: <" + NS + "> .\n"
            + "@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n\n";

    // -------------------------------------------------------------------------
    // RDF classes
    // -------------------------------------------------------------------------

    /** IRI of the CaseExecution class: a running or completed workflow case. */
    public static final String CASE_EXECUTION = NS + "CaseExecution";

    /** IRI of the TaskExecution class: one execution of a task within a case. */
    public static final String TASK_EXECUTION = NS + "TaskExecution";

    // -------------------------------------------------------------------------
    // Case properties
    // -------------------------------------------------------------------------

    /** {@code xsd:string} — the case identifier (e.g. {@code "42"}). */
    public static final String CASE_ID = NS + "caseId";

    /** {@code xsd:string} — the specification (process definition) identifier. */
    public static final String SPEC_ID = NS + "specId";

    /** {@code xsd:string} — current case status: starting|executing|completed|cancelled. */
    public static final String CASE_STATUS = NS + "caseStatus";

    /** {@code xsd:dateTime} — ISO-8601 instant when the case started. */
    public static final String CASE_START_TIME = NS + "caseStartTime";

    /** {@code xsd:dateTime} — ISO-8601 instant when the case ended (if finished). */
    public static final String CASE_END_TIME = NS + "caseEndTime";

    /** {@code xsd:long} — elapsed milliseconds from case start to case end. */
    public static final String CASE_DURATION_MS = NS + "caseDurationMs";

    // -------------------------------------------------------------------------
    // Task properties
    // -------------------------------------------------------------------------

    /** {@code xsd:string} — the task identifier (YAWL task name). */
    public static final String TASK_ID = NS + "taskId";

    /** {@code xsd:string} — the case that contains this task execution. */
    public static final String TASK_CASE_ID = NS + "taskCaseId";

    /** {@code xsd:string} — current task status: enabled|executing|completed|cancelled|failed. */
    public static final String TASK_STATUS = NS + "taskStatus";

    /** {@code xsd:dateTime} — ISO-8601 instant when this task execution started. */
    public static final String TASK_START_TIME = NS + "taskStartTime";

    /** {@code xsd:dateTime} — ISO-8601 instant when this task execution ended. */
    public static final String TASK_END_TIME = NS + "taskEndTime";

    /** {@code xsd:long} — elapsed milliseconds for this task execution. */
    public static final String TASK_DURATION_MS = NS + "taskDurationMs";

    /**
     * {@code wf:TaskExecution} — the task that immediately preceded this one in the same case.
     * Enables SPARQL property path queries for full case trace (e.g. {@code wf:precededBy+}).
     */
    public static final String PRECEDED_BY = NS + "precededBy";

    // -------------------------------------------------------------------------
    // IRI factories
    // -------------------------------------------------------------------------

    /**
     * Returns the IRI for a CaseExecution subject.
     *
     * @param caseId the YAWL case identifier
     * @return full IRI string
     */
    public static String caseIri(String caseId) {
        return NS + "case/" + encode(caseId);
    }

    /**
     * Returns the IRI for a TaskExecution subject.
     *
     * @param caseId YAWL case identifier
     * @param taskId YAWL task identifier
     * @param seqNr  sequence number distinguishing repeated task executions in the same case
     * @return full IRI string
     */
    public static String taskExecIri(String caseId, String taskId, long seqNr) {
        return NS + "task/" + encode(caseId) + "/" + encode(taskId) + "/" + seqNr;
    }

    // -------------------------------------------------------------------------
    // Turtle helpers
    // -------------------------------------------------------------------------

    /**
     * Wraps an IRI in angle brackets for inline Turtle use.
     *
     * @param iri the full IRI
     * @return {@code <iri>}
     */
    public static String iri(String iri) {
        return "<" + iri + ">";
    }

    /**
     * Formats a string literal for Turtle ({@code "value"^^xsd:string}).
     *
     * @param value the string value (must not be null)
     * @return Turtle literal
     */
    public static String lit(String value) {
        return "\"" + escapeTurtleString(value) + "\"^^<http://www.w3.org/2001/XMLSchema#string>";
    }

    /**
     * Formats a dateTime literal for Turtle ({@code "value"^^xsd:dateTime}).
     *
     * @param isoInstant ISO-8601 instant string (e.g. {@code "2026-03-01T12:00:00Z"})
     * @return Turtle literal
     */
    public static String litDateTime(String isoInstant) {
        return "\"" + isoInstant + "\"^^<http://www.w3.org/2001/XMLSchema#dateTime>";
    }

    /**
     * Formats a long integer literal for Turtle ({@code "value"^^xsd:long}).
     *
     * @param value the long value
     * @return Turtle literal
     */
    public static String litLong(long value) {
        return "\"" + value + "\"^^<http://www.w3.org/2001/XMLSchema#long>";
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private WorkflowEventVocabulary() {
        throw new UnsupportedOperationException("Vocabulary constant class");
    }

    /** URL-encodes a path segment (replaces '/' and ':' to prevent IRI collision). */
    private static String encode(String s) {
        return s.replace(":", "_").replace("/", "_").replace(" ", "_");
    }

    /** Escapes backslash and double-quote characters in Turtle string literals. */
    private static String escapeTurtleString(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
