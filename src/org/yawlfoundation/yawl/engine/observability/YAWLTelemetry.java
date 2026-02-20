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

package org.yawlfoundation.yawl.engine.observability;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.yawlfoundation.yawl.observability.AndonCord;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.metrics.*;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

/**
 * Central telemetry provider for YAWL Engine observability.
 * Provides access to OpenTelemetry tracing and metrics for YAWL components.
 *
 * <p>This class works in conjunction with the OpenTelemetry Java agent for
 * zero-code instrumentation, but also provides APIs for deeper custom
 * instrumentation of YAWL-specific operations.
 *
 * <h2>Metrics Exposed</h2>
 *
 * <h3>Case Metrics</h3>
 * <ul>
 *   <li>{@code yawl.case.started} - Counter of cases started</li>
 *   <li>{@code yawl.case.completed} - Counter of cases completed</li>
 *   <li>{@code yawl.case.cancelled} - Counter of cases cancelled</li>
 *   <li>{@code yawl.case.failed} - Counter of cases failed</li>
 *   <li>{@code yawl.case.duration} - Histogram of case execution time</li>
 *   <li>{@code yawl.cases.active} - Gauge of currently active cases</li>
 * </ul>
 *
 * <h3>Work Item Metrics</h3>
 * <ul>
 *   <li>{@code yawl.workitem.created} - Counter of work items created</li>
 *   <li>{@code yawl.workitem.started} - Counter of work items started</li>
 *   <li>{@code yawl.workitem.completed} - Counter of work items completed</li>
 *   <li>{@code yawl.workitem.failed} - Counter of work items failed</li>
 *   <li>{@code yawl.workitem.duration} - Histogram of work item execution time</li>
 *   <li>{@code yawl.workitems.active} - Gauge of currently active work items</li>
 * </ul>
 *
 * <h3>Deadlock Metrics</h3>
 * <ul>
 *   <li>{@code yawl.deadlock.detected.total} - Counter of total deadlock events detected</li>
 *   <li>{@code yawl.deadlock.events} - Counter of deadlock events detected (legacy)</li>
 *   <li>{@code yawl.deadlock.task_count} - Distribution of tasks involved in deadlocks</li>
 *   <li>{@code yawl.deadlock.tasks_count} - Gauge of currently deadlocked tasks (legacy)</li>
 *   <li>{@code yawl.deadlock.resolution.duration} - Histogram of time to resolve deadlocks</li>
 * </ul>
 *
 * <h3>Error Metrics</h3>
 * <ul>
 *   <li>{@code yawl.error.validation} - Counter of YDataStateException errors</li>
 *   <li>{@code yawl.error.lock_wait_timeout} - Counter of lock wait timeout errors</li>
 *   <li>{@code yawl.error.jwks_refresh} - Counter of JWKS refresh failures</li>
 *   <li>{@code yawl.error.case_cancellation} - Counter of case cancellation failures</li>
 *   <li>{@code yawl.error.interface_delivery} - Counter of Interface X delivery failures</li>
 *   <li>{@code yawl.error.retry_exhausted} - Counter of exhausted retry attempts</li>
 *   <li>{@code yawl.error.fallback_used} - Counter of fallback invocations</li>
 * </ul>
 *
 * <h3>Lock Contention Metrics</h3>
 * <ul>
 *   <li>{@code yawl.lock.contention.count} - Counter of lock contention events</li>
 *   <li>{@code yawl.lock.contention.wait_time_ms} - Distribution of lock wait times</li>
 *   <li>{@code yawl.lock.contention.threshold} - Gauge for alert threshold (500ms)</li>
 *   <li>{@code yawl.lock.contention.duration} - Histogram of lock wait times (legacy)</li>
 * </ul>
 *
 * <h3>Engine Metrics</h3>
 * <ul>
 *   <li>{@code yawl.engine.operation.duration} - Histogram of engine operation times</li>
 *   <li>{@code yawl.netrunner.execution.duration} - Histogram of net runner execution times</li>
 *   <li>{@code yawl.tasks.enabled} - Gauge of enabled tasks</li>
 *   <li>{@code yawl.tasks.busy} - Gauge of busy tasks</li>
 * </ul>
 *
 * @author YAWL Development Team
 * @version 6.0.0
 * @since 6.0.0
 */
public class YAWLTelemetry {


    private static final Logger logger = LogManager.getLogger(YAWLTelemetry.class);
    private static final Logger _logger = LogManager.getLogger(YAWLTelemetry.class);

    // Log markers for structured logging
    private static final Marker DEADLOCK_MARKER = MarkerManager.getMarker("DEADLOCK");
    private static final Marker LOCK_CONTENTION_MARKER = MarkerManager.getMarker("LOCK_CONTENTION");

    private static final String INSTRUMENTATION_NAME = "org.yawlfoundation.yawl";
    private static final String INSTRUMENTATION_VERSION = "6.0";

    // Lock contention alert threshold in milliseconds
    private static final long LOCK_CONTENTION_THRESHOLD_MS = 500;

    // Attribute keys for consistent tagging
    public static final AttributeKey<String> ATTR_CASE_ID = AttributeKey.stringKey("yawl.case.id");
    public static final AttributeKey<String> ATTR_SPEC_ID = AttributeKey.stringKey("yawl.specification.id");
    public static final AttributeKey<String> ATTR_TASK_ID = AttributeKey.stringKey("yawl.task.id");
    public static final AttributeKey<String> ATTR_WORKITEM_ID = AttributeKey.stringKey("yawl.workitem.id");
    public static final AttributeKey<String> ATTR_ERROR_TYPE = AttributeKey.stringKey("yawl.error.type");
    public static final AttributeKey<String> ATTR_ERROR_MESSAGE = AttributeKey.stringKey("yawl.error.message");
    public static final AttributeKey<String> ATTR_INTERFACE_NAME = AttributeKey.stringKey("yawl.interface.name");
    public static final AttributeKey<String> ATTR_COMPONENT = AttributeKey.stringKey("yawl.component");
    public static final AttributeKey<String> ATTR_OPERATION = AttributeKey.stringKey("yawl.operation");
    public static final AttributeKey<Long> ATTR_DEADLOCK_TASKS = AttributeKey.longKey("yawl.deadlock.tasks_count");
    public static final AttributeKey<Long> ATTR_RETRY_ATTEMPT = AttributeKey.longKey("yawl.retry.attempt");
    public static final AttributeKey<String> ATTR_FALLBACK_REASON = AttributeKey.stringKey("yawl.fallback.reason");

    // Deadlock attribute keys
    public static final AttributeKey<String> ATTR_DEADLOCK_CASE_ID = AttributeKey.stringKey("yawl.deadlock.case_id");
    public static final AttributeKey<String> ATTR_DEADLOCK_SPEC_ID = AttributeKey.stringKey("yawl.deadlock.specification_id");
    public static final AttributeKey<Long> ATTR_DEADLOCK_TASK_COUNT = AttributeKey.longKey("yawl.deadlock.task_count");

    // Lock contention attribute keys
    public static final AttributeKey<String> ATTR_LOCK_CONTENTION_CASE_ID = AttributeKey.stringKey("yawl.lock.contention.case_id");
    public static final AttributeKey<Long> ATTR_LOCK_CONTENTION_WAIT_TIME_MS = AttributeKey.longKey("yawl.lock.contention.wait_time_ms");

    private static volatile YAWLTelemetry _instance;
    private static final ReentrantLock _lock = new ReentrantLock();

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

    // Lock contention metrics
    private final LongHistogram lockContentionDuration;
    private final LongCounter lockContentionCount;
    private final LongHistogram lockContentionWaitTimeHistogram;
    private final ObservableLongGauge lockContentionThresholdGauge;

    // Deadlock metrics
    private final LongCounter deadlockEventsCounter;
    private final LongCounter deadlockDetectedTotalCounter;
    private final LongHistogram deadlockResolutionDuration;
    private final LongHistogram deadlockTaskCountHistogram;
    private final AtomicLong currentDeadlockedTasks = new AtomicLong(0);
    private final ConcurrentHashMap<String, Long> deadlockStartTimes = new ConcurrentHashMap<>();

    // Deadlock tracking for statistics
    private final AtomicLong totalDeadlocksDetected = new AtomicLong(0);
    private final AtomicLong totalDeadlockedTasks = new AtomicLong(0);
    private final ConcurrentHashMap<String, DeadlockRecord> activeDeadlocks = new ConcurrentHashMap<>();
    private final List<DeadlockRecord> resolvedDeadlocks = Collections.synchronizedList(new ArrayList<>());

    // Lock contention tracking for statistics
    private final AtomicLong totalLockContentions = new AtomicLong(0);
    private final AtomicLong totalLockWaitTimeMs = new AtomicLong(0);
    private final AtomicLong maxLockWaitTimeMs = new AtomicLong(0);
    private final AtomicLong contentionsAboveThreshold = new AtomicLong(0);
    private final ConcurrentHashMap<String, LockContentionRecord> contentionByOperation = new ConcurrentHashMap<>();

    // Error metrics
    private final LongCounter validationErrorCounter;
    private final LongCounter lockWaitTimeoutCounter;
    private final LongCounter jwksRefreshFailureCounter;
    private final LongCounter caseCancellationFailureCounter;
    private final LongCounter interfaceDeliveryFailureCounter;
    private final LongCounter retryExhaustedCounter;
    private final LongCounter fallbackUsedCounter;

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

        // Initialize lock contention metrics
        this.lockContentionDuration = meter
            .histogramBuilder("yawl.lock.contention.duration")
            .setDescription("Duration of lock contention waits")
            .setUnit("ms")
            .ofLongs()
            .build();

        this.lockContentionCount = meter
            .counterBuilder("yawl.lock.contention.count")
            .setDescription("Count of lock contention events")
            .setUnit("events")
            .build();

        this.lockContentionWaitTimeHistogram = meter
            .histogramBuilder("yawl.lock.contention.wait_time_ms")
            .setDescription("Distribution of lock contention wait times in milliseconds")
            .setUnit("ms")
            .ofLongs()
            .build();

        this.lockContentionThresholdGauge = meter
            .gaugeBuilder("yawl.lock.contention.threshold")
            .setDescription("Lock contention alert threshold in milliseconds")
            .setUnit("ms")
            .ofLongs()
            .buildWithCallback(measurement ->
                measurement.record(LOCK_CONTENTION_THRESHOLD_MS));

        // Initialize deadlock metrics
        this.deadlockEventsCounter = meter
            .counterBuilder("yawl.deadlock.events")
            .setDescription("Number of deadlock events detected")
            .setUnit("events")
            .build();

        this.deadlockDetectedTotalCounter = meter
            .counterBuilder("yawl.deadlock.detected.total")
            .setDescription("Total number of deadlock events detected")
            .setUnit("deadlocks")
            .build();

        this.deadlockResolutionDuration = meter
            .histogramBuilder("yawl.deadlock.resolution.duration")
            .setDescription("Time taken to resolve deadlock conditions")
            .setUnit("ms")
            .ofLongs()
            .build();

        this.deadlockTaskCountHistogram = meter
            .histogramBuilder("yawl.deadlock.task_count")
            .setDescription("Distribution of task counts involved in deadlocks")
            .setUnit("tasks")
            .ofLongs()
            .build();

        // Initialize error metrics
        this.validationErrorCounter = meter
            .counterBuilder("yawl.error.validation")
            .setDescription("Number of data validation errors (YDataStateException)")
            .setUnit("errors")
            .build();

        this.lockWaitTimeoutCounter = meter
            .counterBuilder("yawl.error.lock_wait_timeout")
            .setDescription("Number of lock wait timeout errors")
            .setUnit("errors")
            .build();

        this.jwksRefreshFailureCounter = meter
            .counterBuilder("yawl.error.jwks_refresh")
            .setDescription("Number of JWKS refresh failures")
            .setUnit("errors")
            .build();

        this.caseCancellationFailureCounter = meter
            .counterBuilder("yawl.error.case_cancellation")
            .setDescription("Number of case cancellation failures")
            .setUnit("errors")
            .build();

        this.interfaceDeliveryFailureCounter = meter
            .counterBuilder("yawl.error.interface_delivery")
            .setDescription("Number of Interface X delivery failures")
            .setUnit("errors")
            .build();

        this.retryExhaustedCounter = meter
            .counterBuilder("yawl.error.retry_exhausted")
            .setDescription("Number of exhausted retry attempts")
            .setUnit("errors")
            .build();

        this.fallbackUsedCounter = meter
            .counterBuilder("yawl.error.fallback_used")
            .setDescription("Number of times fallback was used")
            .setUnit("events")
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
            _lock.lock();
            try {
                if (_instance == null) {
                    _instance = new YAWLTelemetry();
                }
            } finally {
                _lock.unlock();
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
     * Record lock contention duration.
     *
     * <p>This method records lock contention events and triggers AndonCord alerts
     * when wait times exceed the 500ms threshold.
     *
     * @param waitTimeMs the wait time in milliseconds
     * @param caseId the case identifier
     * @param operation the operation that experienced contention
     */
    public void recordLockContention(long waitTimeMs, String caseId, String operation) {
        if (!enabled) return;

        String effectiveCaseId = caseId != null ? caseId : "unknown";
        String effectiveOperation = operation != null ? operation : "unknown";

        Attributes attributes = Attributes.builder()
            .put(ATTR_LOCK_CONTENTION_CASE_ID, effectiveCaseId)
            .put(ATTR_OPERATION, effectiveOperation)
            .put(ATTR_LOCK_CONTENTION_WAIT_TIME_MS, waitTimeMs)
            .build();

        // Record metrics
        lockContentionDuration.record(waitTimeMs, attributes);
        lockContentionWaitTimeHistogram.record(waitTimeMs, attributes);
        lockContentionCount.add(1, attributes);

        // Update statistics tracking
        totalLockContentions.incrementAndGet();
        totalLockWaitTimeMs.addAndGet(waitTimeMs);
        updateMaxLockWaitTime(waitTimeMs);

        // Track contention by operation
        updateContentionByOperation(effectiveOperation, waitTimeMs);

        // Check threshold and alert via AndonCord (TPS principle: make problems visible)
        if (waitTimeMs >= LOCK_CONTENTION_THRESHOLD_MS) {
            contentionsAboveThreshold.incrementAndGet();

            // Structured logging for lock contention
            _logger.warn(LOCK_CONTENTION_MARKER,
                "Lock contention above threshold: waitTimeMs={}, thresholdMs={}, caseId={}, operation={}",
                waitTimeMs, LOCK_CONTENTION_THRESHOLD_MS, effectiveCaseId, effectiveOperation);

            // Trigger AndonCord alert
            try {
                AndonCord.getInstance().lockContentionHigh(effectiveCaseId, effectiveOperation, waitTimeMs);
            } catch (Exception e) {
                _logger.error("Failed to trigger AndonCord alert for lock contention", e);
            }
        } else {
            // Log normal contention at debug level
            _logger.debug(LOCK_CONTENTION_MARKER,
                "Lock contention: waitTimeMs={}, caseId={}, operation={}",
                waitTimeMs, effectiveCaseId, effectiveOperation);
        }

        // Create span for tracing
        Span span = tracer.spanBuilder("yawl.lock.contention")
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute(ATTR_LOCK_CONTENTION_CASE_ID, effectiveCaseId)
            .setAttribute(ATTR_OPERATION, effectiveOperation)
            .setAttribute(ATTR_LOCK_CONTENTION_WAIT_TIME_MS, waitTimeMs)
            .startSpan();

        if (waitTimeMs >= LOCK_CONTENTION_THRESHOLD_MS) {
            span.setStatus(StatusCode.ERROR, "Lock contention exceeded threshold");
        } else {
            span.setStatus(StatusCode.OK);
        }
        span.end();
    }

    private void updateMaxLockWaitTime(long waitTimeMs) {
        long current;
        do {
            current = maxLockWaitTimeMs.get();
            if (waitTimeMs <= current) {
                return;
            }
        } while (!maxLockWaitTimeMs.compareAndSet(current, waitTimeMs));
    }

    private void updateContentionByOperation(String operation, long waitTimeMs) {
        contentionByOperation.compute(operation, (key, existing) -> {
            if (existing == null) {
                return new LockContentionRecord(operation, 1, waitTimeMs, waitTimeMs, Instant.now());
            }
            long newCount = existing.count() + 1;
            long newTotalWait = existing.totalWaitMs() + waitTimeMs;
            long newMaxWait = Math.max(existing.maxWaitMs(), waitTimeMs);
            return new LockContentionRecord(operation, newCount, newTotalWait, newMaxWait, Instant.now());
        });
    }

    // =========================================================================
    // Deadlock Metrics
    // =========================================================================

    /**
     * Record a deadlock event detection.
     *
     * <p>This method records when a deadlock is detected in the workflow.
     * Call {@link #recordDeadlockResolution(String)} when the deadlock is resolved.
     *
     * <p>Follows TPS principle of making problems visible by triggering AndonCord alerts
     * for immediate visibility and response.
     *
     * @param caseId the case identifier where deadlock was detected
     * @param specId the specification identifier
     * @param deadlockedTaskCount the number of tasks involved in the deadlock
     */
    public void recordDeadlock(String caseId, String specId, int deadlockedTaskCount) {
        if (!enabled) return;

        // Use fallback key for ConcurrentHashMap if caseId is null
        String effectiveCaseId = caseId != null ? caseId : "unknown-" + System.nanoTime();
        String effectiveSpecId = specId != null ? specId : "unknown";

        Attributes attributes = Attributes.builder()
            .put(ATTR_DEADLOCK_CASE_ID, caseId != null ? caseId : "unknown")
            .put(ATTR_DEADLOCK_SPEC_ID, effectiveSpecId)
            .put(ATTR_DEADLOCK_TASK_COUNT, deadlockedTaskCount)
            .build();

        // Record metrics
        deadlockEventsCounter.add(1, attributes);
        deadlockDetectedTotalCounter.add(1, attributes);
        deadlockTaskCountHistogram.record(deadlockedTaskCount, attributes);
        currentDeadlockedTasks.addAndGet(deadlockedTaskCount);
        deadlockStartTimes.put(effectiveCaseId, System.currentTimeMillis());

        // Update statistics tracking
        totalDeadlocksDetected.incrementAndGet();
        totalDeadlockedTasks.addAndGet(deadlockedTaskCount);

        // Create and store deadlock record
        DeadlockRecord record = new DeadlockRecord(
            effectiveCaseId, effectiveSpecId, deadlockedTaskCount, Instant.now(), null, false
        );
        activeDeadlocks.put(effectiveCaseId, record);

        // Structured logging for deadlock detection
        _logger.fatal(DEADLOCK_MARKER,
            "Deadlock detected: caseId={}, specId={}, deadlockedTasks={}, activeDeadlocks={}",
            caseId, specId, deadlockedTaskCount, activeDeadlocks.size());

        // Trigger AndonCord P0 alert (TPS principle: stop the line on critical issues)
        try {
            List<String> taskList = new ArrayList<>();
            taskList.add("task_count=" + deadlockedTaskCount);
            AndonCord.getInstance().deadlockDetected(effectiveCaseId, effectiveSpecId, taskList);
        } catch (Exception e) {
            _logger.error("Failed to trigger AndonCord alert for deadlock", e);
        }

        // Create a span for the deadlock event
        Span span = tracer.spanBuilder("yawl.deadlock.detected")
            .setSpanKind(SpanKind.INTERNAL)
            .setAllAttributes(attributes)
            .startSpan();
        span.setStatus(StatusCode.ERROR, "Deadlock detected: " + deadlockedTaskCount + " tasks blocked");
        span.end();
    }

    /**
     * Record a deadlock resolution event.
     *
     * <p>Call this when a deadlock has been resolved to track resolution time
     * and update the deadlocked tasks gauge.
     *
     * @param caseId the case identifier where deadlock was resolved
     */
    public void recordDeadlockResolution(String caseId) {
        if (!enabled) return;

        String effectiveCaseId = caseId != null ? caseId : "unknown";
        Long startTime = deadlockStartTimes.remove(effectiveCaseId);

        if (startTime != null) {
            long resolutionDuration = System.currentTimeMillis() - startTime;

            Attributes attributes = Attributes.builder()
                .put(ATTR_DEADLOCK_CASE_ID, effectiveCaseId)
                .build();

            deadlockResolutionDuration.record(resolutionDuration, attributes);

            // Update the active deadlock record and move to resolved
            DeadlockRecord activeRecord = activeDeadlocks.remove(effectiveCaseId);
            if (activeRecord != null) {
                currentDeadlockedTasks.addAndGet(-activeRecord.taskCount());
                DeadlockRecord resolvedRecord = new DeadlockRecord(
                    activeRecord.caseId(),
                    activeRecord.specId(),
                    activeRecord.taskCount(),
                    activeRecord.detectedAt(),
                    Instant.now(),
                    true
                );
                resolvedDeadlocks.add(resolvedRecord);

                // Limit resolved deadlocks history to prevent memory leak
                while (resolvedDeadlocks.size() > 1000) {
                    resolvedDeadlocks.remove(0);
                }
            }

            _logger.info(DEADLOCK_MARKER,
                "Deadlock resolved: caseId={}, resolutionTimeMs={}, remainingActiveDeadlocks={}",
                caseId, resolutionDuration, activeDeadlocks.size());

            // Create a span for the resolution
            Span span = tracer.spanBuilder("yawl.deadlock.resolved")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(ATTR_DEADLOCK_CASE_ID, effectiveCaseId)
                .setAttribute("yawl.deadlock.resolution_ms", resolutionDuration)
                .startSpan();
            span.setStatus(StatusCode.OK);
            span.end();
        }
    }

    /**
     * Update the count of currently deadlocked tasks.
     *
     * @param count the current count of deadlocked tasks
     */
    public void updateDeadlockedTasksCount(long count) {
        if (!enabled) return;
        currentDeadlockedTasks.set(count);
    }

    /**
     * Get the current count of deadlocked tasks.
     *
     * @return the current deadlocked tasks count
     */
    public long getDeadlockedTasksCount() {
        return currentDeadlockedTasks.get();
    }

    // =========================================================================
    // Error Metrics
    // =========================================================================

    /**
     * Record a data validation error (YDataStateException).
     *
     * @param caseId the case identifier
     * @param taskId the task identifier where the error occurred
     * @param errorType the specific error type (e.g., "query", "validation")
     * @param errorMessage the error message
     */
    public void recordValidationError(String caseId, String taskId, String errorType, String errorMessage) {
        if (!enabled) return;

        Attributes attributes = Attributes.builder()
            .put(ATTR_CASE_ID, caseId != null ? caseId : "unknown")
            .put(ATTR_TASK_ID, taskId != null ? taskId : "unknown")
            .put(ATTR_ERROR_TYPE, errorType != null ? errorType : "unknown")
            .put(ATTR_ERROR_MESSAGE, errorMessage != null ? errorMessage : "unknown")
            .build();

        validationErrorCounter.add(1, attributes);

        _logger.error("Validation error: caseId={}, taskId={}, errorType={}, message={}",
            caseId, taskId, errorType, errorMessage);
    }

    /**
     * Record a lock wait timeout error.
     *
     * @param caseId the case identifier
     * @param resourceId the resource that could not be acquired
     * @param waitTimeMs how long the wait lasted before timeout
     */
    public void recordLockWaitTimeout(String caseId, String resourceId, long waitTimeMs) {
        if (!enabled) return;

        Attributes attributes = Attributes.builder()
            .put(ATTR_CASE_ID, caseId != null ? caseId : "unknown")
            .put("yawl.resource.id", resourceId != null ? resourceId : "unknown")
            .put("yawl.lock.wait_ms", waitTimeMs)
            .build();

        lockWaitTimeoutCounter.add(1, attributes);

        _logger.error("Lock wait timeout: caseId={}, resourceId={}, waitTimeMs={}",
            caseId, resourceId, waitTimeMs);
    }

    /**
     * Record a JWKS refresh failure.
     *
     * @param jwksUri the JWKS endpoint URI that failed
     * @param errorType the exception type
     * @param errorMessage the error message
     */
    public void recordJwksRefreshFailure(String jwksUri, String errorType, String errorMessage) {
        if (!enabled) return;

        Attributes attributes = Attributes.builder()
            .put("yawl.jwks.uri", jwksUri != null ? jwksUri : "unknown")
            .put(ATTR_ERROR_TYPE, errorType != null ? errorType : "unknown")
            .put(ATTR_ERROR_MESSAGE, errorMessage != null ? errorMessage : "unknown")
            .build();

        jwksRefreshFailureCounter.add(1, attributes);

        _logger.error("JWKS refresh failure: jwksUri={}, errorType={}, message={}",
            jwksUri, errorType, errorMessage);
    }

    /**
     * Record a case cancellation failure.
     *
     * @param caseId the case identifier that could not be cancelled
     * @param reason the reason for cancellation failure
     */
    public void recordCaseCancellationFailure(String caseId, String reason) {
        if (!enabled) return;

        Attributes attributes = Attributes.builder()
            .put(ATTR_CASE_ID, caseId != null ? caseId : "unknown")
            .put("yawl.cancellation.reason", reason != null ? reason : "unknown")
            .build();

        caseCancellationFailureCounter.add(1, attributes);

        _logger.error("Case cancellation failure: caseId={}, reason={}", caseId, reason);
    }

    /**
     * Record an Interface X delivery failure.
     *
     * @param interfaceName the interface name (e.g., "InterfaceX_EngineSideClient")
     * @param commandName the command that failed to deliver
     * @param errorType the exception type
     * @param attemptNumber the attempt number that failed
     */
    public void recordInterfaceDeliveryFailure(String interfaceName, String commandName,
                                                String errorType, int attemptNumber) {
        if (!enabled) return;

        Attributes attributes = Attributes.builder()
            .put(ATTR_INTERFACE_NAME, interfaceName != null ? interfaceName : "unknown")
            .put("yawl.command.name", commandName != null ? commandName : "unknown")
            .put(ATTR_ERROR_TYPE, errorType != null ? errorType : "unknown")
            .put(ATTR_RETRY_ATTEMPT, attemptNumber)
            .build();

        interfaceDeliveryFailureCounter.add(1, attributes);

        _logger.error("Interface delivery failure: interface={}, command={}, errorType={}, attempt={}",
            interfaceName, commandName, errorType, attemptNumber);
    }

    /**
     * Record a retry exhaustion event (all retries failed).
     *
     * @param component the component that exhausted retries
     * @param operation the operation that was retried
     * @param totalAttempts the total number of attempts made
     * @param finalErrorType the final error type
     */
    public void recordRetryExhausted(String component, String operation, int totalAttempts, String finalErrorType) {
        if (!enabled) return;

        Attributes attributes = Attributes.builder()
            .put(ATTR_COMPONENT, component != null ? component : "unknown")
            .put(ATTR_OPERATION, operation != null ? operation : "unknown")
            .put("yawl.retry.total_attempts", totalAttempts)
            .put(ATTR_ERROR_TYPE, finalErrorType != null ? finalErrorType : "unknown")
            .build();

        retryExhaustedCounter.add(1, attributes);

        _logger.error("Retry exhausted: component={}, operation={}, attempts={}, errorType={}",
            component, operation, totalAttempts, finalErrorType);
    }

    /**
     * Record a fallback invocation.
     *
     * @param component the component using fallback
     * @param operation the operation using fallback
     * @param reason the reason for fallback (e.g., "circuit_open", "timeout", "retry_exhausted")
     * @param dataAgeMs the age of fallback data in milliseconds (-1 if unknown)
     */
    public void recordFallbackUsed(String component, String operation, String reason, long dataAgeMs) {
        if (!enabled) return;

        Attributes attributes = Attributes.builder()
            .put(ATTR_COMPONENT, component != null ? component : "unknown")
            .put(ATTR_OPERATION, operation != null ? operation : "unknown")
            .put(ATTR_FALLBACK_REASON, reason != null ? reason : "unknown")
            .put("yawl.fallback.data_age_ms", dataAgeMs)
            .build();

        fallbackUsedCounter.add(1, attributes);

        _logger.warn("Fallback used: component={}, operation={}, reason={}, dataAgeMs={}",
            component, operation, reason, dataAgeMs);
    }

    // =========================================================================
    // Context and Utility Methods
    // =========================================================================

    /**
     * Get the current context.
     *
     * @return the current OpenTelemetry context
     */
    public Context getCurrentContext() {
        return Context.current();
    }

    /**
     * Create a span for an operation.
     *
     * @param spanName the name of the span
     * @param caseId optional case identifier for correlation
     * @return the created span
     */
    public Span createSpan(String spanName, String caseId) {
        var builder = tracer.spanBuilder(spanName)
            .setSpanKind(SpanKind.INTERNAL);

        if (caseId != null) {
            builder.setAttribute(ATTR_CASE_ID, caseId);
        }

        return builder.startSpan();
    }

    /**
     * Create a scoped context for an operation with proper cleanup.
     *
     * @param spanName the name of the span
     * @param caseId optional case identifier
     * @return a Scope that should be closed in a try-with-resources block
     */
    public Scope createScopedSpan(String spanName, String caseId) {
        Span span = createSpan(spanName, caseId);
        return span.makeCurrent();
    }

    /**
     * Record an error on the current active span.
     *
     * @param error the error to record
     */
    public void recordErrorOnCurrentSpan(Throwable error) {
        Span currentSpan = Span.fromContext(Context.current());
        if (currentSpan != null && currentSpan.isRecording()) {
            currentSpan.recordException(error);
            currentSpan.setStatus(StatusCode.ERROR, error.getMessage());
        }
    }

    /**
     * Get deadlock statistics.
     *
     * @return a snapshot of current deadlock statistics
     */
    public DeadlockStats getDeadlockStats() {
        return new DeadlockStats(
            totalDeadlocksDetected.get(),
            totalDeadlockedTasks.get(),
            currentDeadlockedTasks.get(),
            activeDeadlocks.size(),
            resolvedDeadlocks.size(),
            Map.copyOf(new ConcurrentHashMap<>(activeDeadlocks))
        );
    }

    /**
     * Get lock contention statistics.
     *
     * @return a snapshot of current lock contention statistics
     */
    public LockContentionStats getLockContentionStats() {
        long contentions = totalLockContentions.get();
        long totalWait = totalLockWaitTimeMs.get();
        double avgWait = contentions > 0 ? (double) totalWait / contentions : 0.0;

        return new LockContentionStats(
            contentions,
            totalWait,
            avgWait,
            maxLockWaitTimeMs.get(),
            contentionsAboveThreshold.get(),
            LOCK_CONTENTION_THRESHOLD_MS,
            Map.copyOf(new ConcurrentHashMap<>(contentionByOperation))
        );
    }

    /**
     * Snapshot of deadlock statistics with comprehensive metrics.
     */
    public static final class DeadlockStats {
        private final long totalDeadlocksDetected;
        private final long totalDeadlockedTasks;
        private final long currentDeadlockedTasks;
        private final int activeDeadlockCases;
        private final int resolvedDeadlockCases;
        private final Map<String, DeadlockRecord> activeDeadlocks;

        public DeadlockStats(long totalDeadlocksDetected, long totalDeadlockedTasks,
                             long currentDeadlockedTasks, int activeDeadlockCases,
                             int resolvedDeadlockCases,
                             Map<String, DeadlockRecord> activeDeadlocks) {
            this.totalDeadlocksDetected = totalDeadlocksDetected;
            this.totalDeadlockedTasks = totalDeadlockedTasks;
            this.currentDeadlockedTasks = currentDeadlockedTasks;
            this.activeDeadlockCases = activeDeadlockCases;
            this.resolvedDeadlockCases = resolvedDeadlockCases;
            this.activeDeadlocks = activeDeadlocks;
        }

        /**
         * Get total number of deadlocks detected since startup.
         * @return total deadlock count
         */
        public long getTotalDeadlocksDetected() {
            return totalDeadlocksDetected;
        }

        /**
         * Get total number of tasks that have been involved in deadlocks.
         * @return total deadlocked tasks count
         */
        public long getTotalDeadlockedTasks() {
            return totalDeadlockedTasks;
        }

        /**
         * Get current number of tasks that are deadlocked.
         * @return current deadlocked tasks count
         */
        public long getCurrentDeadlockedTasks() {
            return currentDeadlockedTasks;
        }

        /**
         * Get number of active (unresolved) deadlock cases.
         * @return active deadlock case count
         */
        public int getActiveDeadlockCases() {
            return activeDeadlockCases;
        }

        /**
         * Get number of resolved deadlock cases.
         * @return resolved deadlock case count
         */
        public int getResolvedDeadlockCases() {
            return resolvedDeadlockCases;
        }

        /**
         * Get map of active deadlock records keyed by case ID.
         * @return unmodifiable map of active deadlocks
         */
        public Map<String, DeadlockRecord> getActiveDeadlocks() {
            return activeDeadlocks;
        }

        /**
         * Check if there are any active deadlocks.
         * @return true if there are active deadlocks
         */
        public boolean hasActiveDeadlocks() {
            return activeDeadlockCases > 0;
        }

        /**
         * Get average tasks per deadlock.
         * @return average task count per deadlock
         */
        public double getAverageTasksPerDeadlock() {
            return totalDeadlocksDetected > 0
                ? (double) totalDeadlockedTasks / totalDeadlocksDetected
                : 0.0;
        }

        @Override
        public String toString() {
            return String.format("DeadlockStats{total=%d, current=%d, activeCases=%d, resolvedCases=%d, avgTasks=%.1f}",
                totalDeadlocksDetected, currentDeadlockedTasks, activeDeadlockCases,
                resolvedDeadlockCases, getAverageTasksPerDeadlock());
        }
    }

    /**
     * Snapshot of lock contention statistics with comprehensive metrics.
     */
    public static final class LockContentionStats {
        private final long totalContentions;
        private final long totalWaitTimeMs;
        private final double averageWaitTimeMs;
        private final long maxWaitTimeMs;
        private final long contentionsAboveThreshold;
        private final long thresholdMs;
        private final Map<String, LockContentionRecord> contentionByOperation;

        public LockContentionStats(long totalContentions, long totalWaitTimeMs,
                                   double averageWaitTimeMs, long maxWaitTimeMs,
                                   long contentionsAboveThreshold, long thresholdMs,
                                   Map<String, LockContentionRecord> contentionByOperation) {
            this.totalContentions = totalContentions;
            this.totalWaitTimeMs = totalWaitTimeMs;
            this.averageWaitTimeMs = averageWaitTimeMs;
            this.maxWaitTimeMs = maxWaitTimeMs;
            this.contentionsAboveThreshold = contentionsAboveThreshold;
            this.thresholdMs = thresholdMs;
            this.contentionByOperation = contentionByOperation;
        }

        /**
         * Get total number of lock contention events.
         * @return total contention count
         */
        public long getTotalContentions() {
            return totalContentions;
        }

        /**
         * Get total wait time across all contentions in milliseconds.
         * @return total wait time in ms
         */
        public long getTotalWaitTimeMs() {
            return totalWaitTimeMs;
        }

        /**
         * Get average wait time per contention in milliseconds.
         * @return average wait time in ms
         */
        public double getAverageWaitTimeMs() {
            return averageWaitTimeMs;
        }

        /**
         * Get maximum observed wait time in milliseconds.
         * @return max wait time in ms
         */
        public long getMaxWaitTimeMs() {
            return maxWaitTimeMs;
        }

        /**
         * Get number of contentions that exceeded the alert threshold.
         * @return count of contentions above threshold
         */
        public long getContentionsAboveThreshold() {
            return contentionsAboveThreshold;
        }

        /**
         * Get the alert threshold in milliseconds.
         * @return threshold in ms
         */
        public long getThresholdMs() {
            return thresholdMs;
        }

        /**
         * Get map of contention records by operation name.
         * @return unmodifiable map of contention by operation
         */
        public Map<String, LockContentionRecord> getContentionByOperation() {
            return contentionByOperation;
        }

        /**
         * Check if there are contentions above the threshold.
         * @return true if any contentions exceeded threshold
         */
        public boolean hasHighContention() {
            return contentionsAboveThreshold > 0;
        }

        /**
         * Get percentage of contentions above threshold.
         * @return percentage (0-100) of contentions above threshold
         */
        public double getPercentageAboveThreshold() {
            return totalContentions > 0
                ? (double) contentionsAboveThreshold / totalContentions * 100.0
                : 0.0;
        }

        /**
         * Check if lock contention is healthy (below threshold).
         * @return true if contention is within acceptable levels
         */
        public boolean isHealthy() {
            return averageWaitTimeMs < thresholdMs && contentionsAboveThreshold == 0;
        }

        @Override
        public String toString() {
            return String.format("LockContentionStats{total=%d, avgWait=%.1fms, maxWait=%dms, aboveThreshold=%d (%.1f%%), healthy=%s}",
                totalContentions, averageWaitTimeMs, maxWaitTimeMs,
                contentionsAboveThreshold, getPercentageAboveThreshold(), isHealthy());
        }
    }

    /**
     * Record tracking active deadlock information for statistics.
     */
    public static final class DeadlockRecord {
        private final String caseId;
        private final String specId;
        private final int taskCount;
        private final Instant detectedAt;
        private final Instant resolvedAt;
        private final boolean resolved;

        public DeadlockRecord(String caseId, String specId, int taskCount, Instant detectedAt, String status) {
            this.caseId = caseId;
            this.specId = specId;
            this.taskCount = taskCount;
            this.detectedAt = detectedAt;
            this.resolvedAt = null;
            this.resolved = false;
        }

        public DeadlockRecord(String caseId, String specId, int taskCount, Instant detectedAt, Instant resolvedAt, boolean resolved) {
            this.caseId = caseId;
            this.specId = specId;
            this.taskCount = taskCount;
            this.detectedAt = detectedAt;
            this.resolvedAt = resolvedAt;
            this.resolved = resolved;
        }

        public String caseId() { return caseId; }
        public String specId() { return specId; }
        public int taskCount() { return taskCount; }
        public Instant detectedAt() { return detectedAt; }
        public Instant resolvedAt() { return resolvedAt; }
        public boolean resolved() { return resolved; }

        public DeadlockRecord withStatus(String newStatus) {
            return new DeadlockRecord(caseId, specId, taskCount, detectedAt, newStatus);
        }

        @Override
        public String toString() {
            return String.format("DeadlockRecord{caseId='%s', taskCount=%d, resolved=%s}",
                caseId, taskCount, resolved);
        }
    }

    /**
     * Record tracking lock contention statistics by operation.
     */
    public static final class LockContentionRecord {
        private final String operation;
        private final long count;
        private final long totalWaitMs;
        private final long maxWaitMs;
        private final Instant lastOccurrence;

        public LockContentionRecord(String operation, long count, long totalWaitMs, long maxWaitMs, Instant lastOccurrence) {
            this.operation = operation;
            this.count = count;
            this.totalWaitMs = totalWaitMs;
            this.maxWaitMs = maxWaitMs;
            this.lastOccurrence = lastOccurrence;
        }

        public String operation() { return operation; }
        public long count() { return count; }
        public long totalWaitMs() { return totalWaitMs; }
        public long maxWaitMs() { return maxWaitMs; }
        public Instant lastOccurrence() { return lastOccurrence; }

        public double averageWaitMs() {
            return count > 0 ? (double) totalWaitMs / count : 0.0;
        }

        @Override
        public String toString() {
            return String.format("LockContentionRecord{operation='%s', count=%d, avgWait=%.2fms, maxWait=%dms}",
                operation, count, averageWaitMs(), maxWaitMs);
        }
    }
}
