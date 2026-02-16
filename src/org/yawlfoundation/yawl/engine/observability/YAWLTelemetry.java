/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.engine.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.*;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central telemetry provider for YAWL Engine observability.
 * Provides access to OpenTelemetry tracing and metrics for YAWL components.
 *
 * This class works in conjunction with the OpenTelemetry Java agent for
 * zero-code instrumentation, but also provides APIs for deeper custom
 * instrumentation of YAWL-specific operations.
 *
 * @author YAWL Development Team
 */
public class YAWLTelemetry {


    private static final Logger logger = LogManager.getLogger(YAWLTelemetry.class);
    private static final Logger _logger = LogManager.getLogger(YAWLTelemetry.class);

    private static final String INSTRUMENTATION_NAME = "org.yawlfoundation.yawl";
    private static final String INSTRUMENTATION_VERSION = "5.2";

    private static volatile YAWLTelemetry _instance;
    private static final Object _lock = new Object();

    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;
    private final Meter meter;

    // Metrics
    private final LongCounter caseStartedCounter;
    private final LongCounter caseCompletedCounter;
    private final LongCounter caseCancelledCounter;
    private final LongCounter caseFailedCounter;

    private final LongCounter workItemCreatedCounter;
    private final LongCounter workItemStartedCounter;
    private final LongCounter workItemCompletedCounter;
    private final LongCounter workItemFailedCounter;

    private final LongHistogram caseDurationHistogram;
    private final LongHistogram workItemDurationHistogram;
    private final LongHistogram netRunnerExecutionHistogram;

    private final ObservableLongGauge activeCasesGauge;
    private final ObservableLongGauge activeWorkItemsGauge;
    private final ObservableLongGauge enabledTasksGauge;
    private final ObservableLongGauge busyTasksGauge;

    private final DoubleHistogram engineOperationDuration;

    // Runtime state for gauges
    private final AtomicLong activeCasesCount = new AtomicLong(0);
    private final AtomicLong activeWorkItemsCount = new AtomicLong(0);
    private final AtomicLong enabledTasksCount = new AtomicLong(0);
    private final AtomicLong busyTasksCount = new AtomicLong(0);

    private final ConcurrentHashMap<String, Long> caseStartTimes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> workItemStartTimes = new ConcurrentHashMap<>();

    private boolean enabled = true;

    /**
     * Private constructor - use getInstance().
     */
    private YAWLTelemetry() {
        this.openTelemetry = GlobalOpenTelemetry.get();
        this.tracer = openTelemetry.getTracer(INSTRUMENTATION_NAME, INSTRUMENTATION_VERSION);
        this.meter = openTelemetry.getMeter(INSTRUMENTATION_NAME);

        // Initialize counters
        this.caseStartedCounter = meter
            .counterBuilder("yawl.case.started")
            .setDescription("Number of workflow cases started")
            .setUnit("cases")
            .build();

        this.caseCompletedCounter = meter
            .counterBuilder("yawl.case.completed")
            .setDescription("Number of workflow cases completed successfully")
            .setUnit("cases")
            .build();

        this.caseCancelledCounter = meter
            .counterBuilder("yawl.case.cancelled")
            .setDescription("Number of workflow cases cancelled")
            .setUnit("cases")
            .build();

        this.caseFailedCounter = meter
            .counterBuilder("yawl.case.failed")
            .setDescription("Number of workflow cases failed")
            .setUnit("cases")
            .build();

        this.workItemCreatedCounter = meter
            .counterBuilder("yawl.workitem.created")
            .setDescription("Number of work items created")
            .setUnit("items")
            .build();

        this.workItemStartedCounter = meter
            .counterBuilder("yawl.workitem.started")
            .setDescription("Number of work items started")
            .setUnit("items")
            .build();

        this.workItemCompletedCounter = meter
            .counterBuilder("yawl.workitem.completed")
            .setDescription("Number of work items completed successfully")
            .setUnit("items")
            .build();

        this.workItemFailedCounter = meter
            .counterBuilder("yawl.workitem.failed")
            .setDescription("Number of work items failed")
            .setUnit("items")
            .build();

        // Initialize histograms
        this.caseDurationHistogram = meter
            .histogramBuilder("yawl.case.duration")
            .setDescription("Duration of workflow case execution")
            .setUnit("ms")
            .ofLongs()
            .build();

        this.workItemDurationHistogram = meter
            .histogramBuilder("yawl.workitem.duration")
            .setDescription("Duration of work item execution")
            .setUnit("ms")
            .ofLongs()
            .build();

        this.netRunnerExecutionHistogram = meter
            .histogramBuilder("yawl.netrunner.execution.duration")
            .setDescription("Duration of net runner execution cycles")
            .setUnit("ms")
            .ofLongs()
            .build();

        this.engineOperationDuration = meter
            .histogramBuilder("yawl.engine.operation.duration")
            .setDescription("Duration of engine operations")
            .setUnit("ms")
            .build();

        // Initialize observable gauges
        this.activeCasesGauge = meter
            .gaugeBuilder("yawl.cases.active")
            .setDescription("Number of currently active workflow cases")
            .setUnit("cases")
            .ofLongs()
            .buildWithCallback(measurement ->
                measurement.record(activeCasesCount.get()));

        this.activeWorkItemsGauge = meter
            .gaugeBuilder("yawl.workitems.active")
            .setDescription("Number of currently active work items")
            .setUnit("items")
            .ofLongs()
            .buildWithCallback(measurement ->
                measurement.record(activeWorkItemsCount.get()));

        this.enabledTasksGauge = meter
            .gaugeBuilder("yawl.tasks.enabled")
            .setDescription("Number of currently enabled tasks")
            .setUnit("tasks")
            .ofLongs()
            .buildWithCallback(measurement ->
                measurement.record(enabledTasksCount.get()));

        this.busyTasksGauge = meter
            .gaugeBuilder("yawl.tasks.busy")
            .setDescription("Number of currently busy tasks")
            .setUnit("tasks")
            .ofLongs()
            .buildWithCallback(measurement ->
                measurement.record(busyTasksCount.get()));

        _logger.info("YAWLTelemetry initialized with OpenTelemetry instrumentation");
    }

    /**
     * Get the singleton instance of YAWLTelemetry.
     *
     * @return the YAWLTelemetry instance
     */
    public static YAWLTelemetry getInstance() {
        if (_instance == null) {
            synchronized (_lock) {
                if (_instance == null) {
                    _instance = new YAWLTelemetry();
                }
            }
        }
        return _instance;
    }

    /**
     * Get the OpenTelemetry tracer for creating spans.
     *
     * @return the OpenTelemetry Tracer
     */
    public Tracer getTracer() {
        return tracer;
    }

    /**
     * Get the OpenTelemetry meter for metrics.
     *
     * @return the OpenTelemetry Meter
     */
    public Meter getMeter() {
        return meter;
    }

    /**
     * Get the OpenTelemetry instance.
     *
     * @return the OpenTelemetry instance
     */
    public OpenTelemetry getOpenTelemetry() {
        return openTelemetry;
    }

    /**
     * Record a case start event.
     *
     * @param caseId the case identifier
     * @param specificationId the specification identifier
     */
    public void recordCaseStarted(String caseId, String specificationId) {
        if (!enabled) return;

        Attributes attributes = Attributes.builder()
            .put("yawl.case.id", caseId)
            .put("yawl.specification.id", specificationId)
            .build();

        caseStartedCounter.add(1, attributes);
        activeCasesCount.incrementAndGet();
        caseStartTimes.put(caseId, System.currentTimeMillis());
    }

    /**
     * Record a case completion event.
     *
     * @param caseId the case identifier
     * @param specificationId the specification identifier
     */
    public void recordCaseCompleted(String caseId, String specificationId) {
        if (!enabled) return;

        Attributes attributes = Attributes.builder()
            .put("yawl.case.id", caseId)
            .put("yawl.specification.id", specificationId)
            .build();

        caseCompletedCounter.add(1, attributes);
        activeCasesCount.decrementAndGet();

        Long startTime = caseStartTimes.remove(caseId);
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            caseDurationHistogram.record(duration, attributes);
        }
    }

    /**
     * Record a case cancellation event.
     *
     * @param caseId the case identifier
     * @param specificationId the specification identifier
     */
    public void recordCaseCancelled(String caseId, String specificationId) {
        if (!enabled) return;

        Attributes attributes = Attributes.builder()
            .put("yawl.case.id", caseId)
            .put("yawl.specification.id", specificationId)
            .build();

        caseCancelledCounter.add(1, attributes);
        activeCasesCount.decrementAndGet();
        caseStartTimes.remove(caseId);
    }

    /**
     * Record a case failure event.
     *
     * @param caseId the case identifier
     * @param specificationId the specification identifier
     * @param errorMessage the error message
     */
    public void recordCaseFailed(String caseId, String specificationId, String errorMessage) {
        if (!enabled) return;

        Attributes attributes = Attributes.builder()
            .put("yawl.case.id", caseId)
            .put("yawl.specification.id", specificationId)
            .put("error.message", errorMessage != null ? errorMessage : "unknown")
            .build();

        caseFailedCounter.add(1, attributes);
        activeCasesCount.decrementAndGet();
        caseStartTimes.remove(caseId);
    }

    /**
     * Record a work item creation event.
     *
     * @param workItemId the work item identifier
     * @param caseId the case identifier
     * @param taskId the task identifier
     */
    public void recordWorkItemCreated(String workItemId, String caseId, String taskId) {
        if (!enabled) return;

        Attributes attributes = Attributes.builder()
            .put("yawl.workitem.id", workItemId)
            .put("yawl.case.id", caseId)
            .put("yawl.task.id", taskId)
            .build();

        workItemCreatedCounter.add(1, attributes);
    }

    /**
     * Record a work item start event.
     *
     * @param workItemId the work item identifier
     * @param caseId the case identifier
     * @param taskId the task identifier
     */
    public void recordWorkItemStarted(String workItemId, String caseId, String taskId) {
        if (!enabled) return;

        Attributes attributes = Attributes.builder()
            .put("yawl.workitem.id", workItemId)
            .put("yawl.case.id", caseId)
            .put("yawl.task.id", taskId)
            .build();

        workItemStartedCounter.add(1, attributes);
        activeWorkItemsCount.incrementAndGet();
        workItemStartTimes.put(workItemId, System.currentTimeMillis());
    }

    /**
     * Record a work item completion event.
     *
     * @param workItemId the work item identifier
     * @param caseId the case identifier
     * @param taskId the task identifier
     */
    public void recordWorkItemCompleted(String workItemId, String caseId, String taskId) {
        if (!enabled) return;

        Attributes attributes = Attributes.builder()
            .put("yawl.workitem.id", workItemId)
            .put("yawl.case.id", caseId)
            .put("yawl.task.id", taskId)
            .build();

        workItemCompletedCounter.add(1, attributes);
        activeWorkItemsCount.decrementAndGet();

        Long startTime = workItemStartTimes.remove(workItemId);
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            workItemDurationHistogram.record(duration, attributes);
        }
    }

    /**
     * Record a work item failure event.
     *
     * @param workItemId the work item identifier
     * @param caseId the case identifier
     * @param taskId the task identifier
     * @param errorMessage the error message
     */
    public void recordWorkItemFailed(String workItemId, String caseId, String taskId, String errorMessage) {
        if (!enabled) return;

        Attributes attributes = Attributes.builder()
            .put("yawl.workitem.id", workItemId)
            .put("yawl.case.id", caseId)
            .put("yawl.task.id", taskId)
            .put("error.message", errorMessage != null ? errorMessage : "unknown")
            .build();

        workItemFailedCounter.add(1, attributes);
        activeWorkItemsCount.decrementAndGet();
        workItemStartTimes.remove(workItemId);
    }

    /**
     * Record net runner execution duration.
     *
     * @param durationMs the execution duration in milliseconds
     * @param caseId the case identifier
     */
    public void recordNetRunnerExecution(long durationMs, String caseId) {
        if (!enabled) return;

        Attributes attributes = Attributes.builder()
            .put("yawl.case.id", caseId)
            .build();

        netRunnerExecutionHistogram.record(durationMs, attributes);
    }

    /**
     * Record an engine operation duration.
     *
     * @param operation the operation name
     * @param durationMs the operation duration in milliseconds
     */
    public void recordEngineOperation(String operation, double durationMs) {
        if (!enabled) return;

        Attributes attributes = Attributes.builder()
            .put("yawl.operation", operation)
            .build();

        engineOperationDuration.record(durationMs, attributes);
    }

    /**
     * Update the count of enabled tasks.
     *
     * @param count the current count of enabled tasks
     */
    public void updateEnabledTasksCount(long count) {
        if (!enabled) return;
        enabledTasksCount.set(count);
    }

    /**
     * Update the count of busy tasks.
     *
     * @param count the current count of busy tasks
     */
    public void updateBusyTasksCount(long count) {
        if (!enabled) return;
        busyTasksCount.set(count);
    }

    /**
     * Enable or disable telemetry collection.
     *
     * @param enabled true to enable, false to disable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        _logger.info("YAWLTelemetry " + (enabled ? "enabled" : "disabled"));
    }

    /**
     * Check if telemetry is enabled.
     *
     * @return true if enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Get the current context.
     *
     * @return the current OpenTelemetry context
     */
    public Context getCurrentContext() {
        return Context.current();
    }
}
