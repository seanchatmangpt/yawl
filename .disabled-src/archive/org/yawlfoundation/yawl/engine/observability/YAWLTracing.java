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

import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

/**
 * Utility class for adding OpenTelemetry tracing to YAWL operations.
 * Provides helper methods to create and manage spans for workflow operations.
 *
 * @author YAWL Development Team
 */
public class YAWLTracing {

    private static final Logger _logger = LogManager.getLogger(YAWLTracing.class);

    private static final YAWLTelemetry telemetry = YAWLTelemetry.getInstance();
    private static final Tracer tracer = telemetry.getTracer();

    // YAWL-specific attribute keys
    public static final AttributeKey<String> YAWL_CASE_ID = AttributeKey.stringKey("yawl.case.id");
    public static final AttributeKey<String> YAWL_SPECIFICATION_ID = AttributeKey.stringKey("yawl.specification.id");
    public static final AttributeKey<String> YAWL_TASK_ID = AttributeKey.stringKey("yawl.task.id");
    public static final AttributeKey<String> YAWL_WORKITEM_ID = AttributeKey.stringKey("yawl.workitem.id");
    public static final AttributeKey<String> YAWL_NET_ID = AttributeKey.stringKey("yawl.net.id");
    public static final AttributeKey<String> YAWL_OPERATION = AttributeKey.stringKey("yawl.operation");
    public static final AttributeKey<String> YAWL_ENGINE_STATUS = AttributeKey.stringKey("yawl.engine.status");
    public static final AttributeKey<Long> YAWL_ENABLED_TASKS = AttributeKey.longKey("yawl.enabled.tasks");
    public static final AttributeKey<Long> YAWL_BUSY_TASKS = AttributeKey.longKey("yawl.busy.tasks");
    public static final AttributeKey<String> YAWL_WORKITEM_STATUS = AttributeKey.stringKey("yawl.workitem.status");

    /**
     * Execute a workflow operation within a traced span.
     *
     * @param operationName the name of the operation
     * @param operation the operation to execute
     * @param <T> the return type
     * @return the result of the operation
     * @throws Exception if the operation fails
     */
    public static <T> T traceOperation(String operationName, Supplier<T> operation) throws Exception {
        Span span = tracer.spanBuilder(operationName)
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute(YAWL_OPERATION, operationName)
            .startSpan();

        try (Scope scope = span.makeCurrent()) {
            T result = operation.get();
            span.setStatus(StatusCode.OK);
            return result;
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * Execute a workflow operation within a traced span (void return).
     *
     * @param operationName the name of the operation
     * @param operation the operation to execute
     * @throws Exception if the operation fails
     */
    public static void traceOperationVoid(String operationName, Runnable operation) throws Exception {
        Span span = tracer.spanBuilder(operationName)
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute(YAWL_OPERATION, operationName)
            .startSpan();

        try (Scope scope = span.makeCurrent()) {
            operation.run();
            span.setStatus(StatusCode.OK);
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * Create a span for a case execution operation.
     *
     * @param operationName the operation name
     * @param caseId the case identifier
     * @param specificationId the specification identifier
     * @return the created span
     */
    public static Span createCaseSpan(String operationName, String caseId, String specificationId) {
        Attributes attributes = Attributes.builder()
            .put(YAWL_OPERATION, operationName)
            .put(YAWL_CASE_ID, caseId)
            .put(YAWL_SPECIFICATION_ID, specificationId)
            .build();

        return tracer.spanBuilder(operationName)
            .setSpanKind(SpanKind.INTERNAL)
            .setAllAttributes(attributes)
            .startSpan();
    }

    /**
     * Create a span for a work item operation.
     *
     * @param operationName the operation name
     * @param workItemId the work item identifier
     * @param caseId the case identifier
     * @param taskId the task identifier
     * @return the created span
     */
    public static Span createWorkItemSpan(String operationName, String workItemId,
                                          String caseId, String taskId) {
        Attributes attributes = Attributes.builder()
            .put(YAWL_OPERATION, operationName)
            .put(YAWL_WORKITEM_ID, workItemId)
            .put(YAWL_CASE_ID, caseId)
            .put(YAWL_TASK_ID, taskId)
            .build();

        return tracer.spanBuilder(operationName)
            .setSpanKind(SpanKind.INTERNAL)
            .setAllAttributes(attributes)
            .startSpan();
    }

    /**
     * Create a span for a net runner operation.
     *
     * @param operationName the operation name
     * @param caseId the case identifier
     * @param netId the net identifier
     * @return the created span
     */
    public static Span createNetRunnerSpan(String operationName, String caseId, String netId) {
        Attributes attributes = Attributes.builder()
            .put(YAWL_OPERATION, operationName)
            .put(YAWL_CASE_ID, caseId)
            .put(YAWL_NET_ID, netId)
            .build();

        return tracer.spanBuilder(operationName)
            .setSpanKind(SpanKind.INTERNAL)
            .setAllAttributes(attributes)
            .startSpan();
    }

    /**
     * Create a span for an engine operation.
     *
     * @param operationName the operation name
     * @return the created span
     */
    public static Span createEngineSpan(String operationName) {
        return tracer.spanBuilder(operationName)
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute(YAWL_OPERATION, operationName)
            .startSpan();
    }

    /**
     * Add case attributes to the current span.
     *
     * @param caseId the case identifier
     * @param specificationId the specification identifier
     */
    public static void addCaseAttributes(String caseId, String specificationId) {
        Span currentSpan = Span.current();
        if (currentSpan != null && currentSpan.isRecording()) {
            currentSpan.setAttribute(YAWL_CASE_ID, caseId);
            currentSpan.setAttribute(YAWL_SPECIFICATION_ID, specificationId);
        }
    }

    /**
     * Add work item attributes to the current span.
     *
     * @param workItemId the work item identifier
     * @param taskId the task identifier
     */
    public static void addWorkItemAttributes(String workItemId, String taskId) {
        Span currentSpan = Span.current();
        if (currentSpan != null && currentSpan.isRecording()) {
            currentSpan.setAttribute(YAWL_WORKITEM_ID, workItemId);
            currentSpan.setAttribute(YAWL_TASK_ID, taskId);
        }
    }

    /**
     * Add task state attributes to the current span.
     *
     * @param enabledTasksCount the number of enabled tasks
     * @param busyTasksCount the number of busy tasks
     */
    public static void addTaskStateAttributes(long enabledTasksCount, long busyTasksCount) {
        Span currentSpan = Span.current();
        if (currentSpan != null && currentSpan.isRecording()) {
            currentSpan.setAttribute(YAWL_ENABLED_TASKS, enabledTasksCount);
            currentSpan.setAttribute(YAWL_BUSY_TASKS, busyTasksCount);
        }
    }

    /**
     * Record an exception in the current span.
     *
     * @param exception the exception to record
     */
    public static void recordException(Throwable exception) {
        Span currentSpan = Span.current();
        if (currentSpan != null && currentSpan.isRecording()) {
            currentSpan.setStatus(StatusCode.ERROR, exception.getMessage());
            currentSpan.recordException(exception);
        }
    }

    /**
     * Add an event to the current span.
     *
     * @param eventName the event name
     */
    public static void addEvent(String eventName) {
        Span currentSpan = Span.current();
        if (currentSpan != null && currentSpan.isRecording()) {
            currentSpan.addEvent(eventName);
        }
    }

    /**
     * Add an event with attributes to the current span.
     *
     * @param eventName the event name
     * @param attributes the event attributes
     */
    public static void addEvent(String eventName, Attributes attributes) {
        Span currentSpan = Span.current();
        if (currentSpan != null && currentSpan.isRecording()) {
            currentSpan.addEvent(eventName, attributes);
        }
    }

    /**
     * Get the current span.
     *
     * @return the current span
     */
    public static Span getCurrentSpan() {
        return Span.current();
    }

    /**
     * Get the OpenTelemetry tracer for custom span creation.
     *
     * @return the Tracer instance
     */
    public static Tracer getTracer() {
        return tracer;
    }

    /**
     * Get the current context.
     *
     * @return the current context
     */
    public static Context getCurrentContext() {
        return Context.current();
    }

    /**
     * Execute an operation with a custom span.
     *
     * @param span the span to use
     * @param operation the operation to execute
     * @param <T> the return type
     * @return the result of the operation
     */
    public static <T> T withSpan(Span span, Supplier<T> operation) {
        try (Scope scope = span.makeCurrent()) {
            T result = operation.get();
            span.setStatus(StatusCode.OK);
            return result;
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * Execute an operation with a custom span (void return).
     *
     * @param span the span to use
     * @param operation the operation to execute
     */
    public static void withSpanVoid(Span span, Runnable operation) {
        try (Scope scope = span.makeCurrent()) {
            operation.run();
            span.setStatus(StatusCode.OK);
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }
}
