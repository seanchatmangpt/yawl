/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it terms of the GNU Lesser
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

package org.yawlfoundation.yawl.nativebridge.erlang;

import java.util.List;
import java.util.Map;

/**
 * Domain API for process mining operations.
 * Provides a clean interface for Java applications to interact with Erlang-based process mining systems.
 */
public interface ProcessMiningClient {

    /**
     * Discovers a process model from event log data.
     *
     * @param eventLog The event log as a list of events
     * @return The discovered process model as an Erlang term
     * @throws ErlangException if discovery fails
     */
    ErlTerm discoverProcessModel(List<EventLogEntry> eventLog) throws ErlangException;

    /**
     * Conformance checking between a process model and event log.
     *
     * @param processModel The process model
     * @param eventLog The event log
     * @return Conformance analysis results
     * @throws ErlangException if conformance checking fails
     */
    ConformanceResult conformanceCheck(ErlTerm processModel, List<EventLogEntry> eventLog) throws ErlangException;

    /**
     * Performance analysis of a process model.
     *
     * @param processModel The process model
     * @param eventLog The event log
     * @return Performance analysis results
     * @throws ErlangException if performance analysis fails
     */
    PerformanceResult analyzePerformance(ErlTerm processModel, List<EventLogEntry> eventLog) throws ErlangException;

    /**
     * Gets statistics about a specific process instance.
     *
     * @param processInstanceId The process instance ID
     * @return Process instance statistics
     * @throws ErlangException if statistics retrieval fails
     */
    ProcessInstanceStats getProcessInstanceStats(String processInstanceId) throws ErlangException;

    /**
     * Queries for all available process models.
     *
     * @return List of available process model IDs
     * @throws ErlangException if query fails
     */
    List<String> listProcessModels() throws ErlangException;

    /**
     * Validates a process model against standard patterns.
     *
     * @param processModel The process model to validate
     * @return Validation results
     * @throws ErlangException if validation fails
     */
    ValidationResult validateProcessModel(ErlTerm processModel) throws ErlangException;

    /**
     * Executes a process mining query.
     *
     * @param query The query string
     * @param parameters Query parameters
     * @return Query results
     * @throws ErlangException if query execution fails
     */
    ErlTerm executeQuery(String query, Map<String, ErlTerm> parameters) throws ErlangException;
}

/**
 * Represents a single event log entry.
 */
class EventLogEntry {
    private final String caseId;
    private final String activity;
    private final long timestamp;
    private final Map<String, String> attributes;

    public EventLogEntry(String caseId, String activity, long timestamp, Map<String, String> attributes) {
        this.caseId = caseId;
        this.activity = activity;
        this.timestamp = timestamp;
        this.attributes = attributes;
    }

    // Getters...
    public String getCaseId() { return caseId; }
    public String getActivity() { return activity; }
    public long getTimestamp() { return timestamp; }
    public Map<String, String> getAttributes() { return attributes; }
}

/**
 * Represents conformance checking results.
 */
class ConformanceResult {
    private final double fitness;
    private final double precision;
    private final List<ErlTerm> deviations;

    public ConformanceResult(double fitness, double precision, List<ErlTerm> deviations) {
        this.fitness = fitness;
        this.precision = precision;
        this.deviations = deviations;
    }

    // Getters...
    public double getFitness() { return fitness; }
    public double getPrecision() { return precision; }
    public List<ErlTerm> getDeviations() { return deviations; }
}

/**
 * Represents performance analysis results.
 */
class PerformanceResult {
    private final double averageCycleTime;
    private final double throughput;
    private final Map<String, Double> activityDurations;

    public PerformanceResult(double averageCycleTime, double throughput, Map<String, Double> activityDurations) {
        this.averageCycleTime = averageCycleTime;
        this.throughput = throughput;
        this.activityDurations = activityDurations;
    }

    // Getters...
    public double getAverageCycleTime() { return averageCycleTime; }
    public double getThroughput() { return throughput; }
    public Map<String, Double> getActivityDurations() { return activityDurations; }
}

/**
 * Represents process instance statistics.
 */
class ProcessInstanceStats {
    private final String instanceId;
    private final int totalActivities;
    private final long duration;
    private final long startTime;
    private final long endTime;

    public ProcessInstanceStats(String instanceId, int totalActivities, long duration, long startTime, long endTime) {
        this.instanceId = instanceId;
        this.totalActivities = totalActivities;
        this.duration = duration;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    // Getters...
    public String getInstanceId() { return instanceId; }
    public int getTotalActivities() { return totalActivities; }
    public long getDuration() { return duration; }
    public long getStartTime() { return startTime; }
    public long getEndTime() { return endTime; }
}

/**
 * Represents validation results.
 */
class ValidationResult {
    private final boolean isValid;
    private final List<String> warnings;
    private final List<String> errors;

    public ValidationResult(boolean isValid, List<String> warnings, List<String> errors) {
        this.isValid = isValid;
        this.warnings = warnings;
        this.errors = errors;
    }

    // Getters...
    public boolean isValid() { return isValid; }
    public List<String> getWarnings() { return warnings; }
    public List<String> getErrors() { return errors; }
}