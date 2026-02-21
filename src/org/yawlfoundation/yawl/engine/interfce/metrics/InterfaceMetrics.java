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

package org.yawlfoundation.yawl.engine.interfce.metrics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;

/**
 * Central metrics provider for all YAWL interfaces.
 *
 * <h2>Overview</h2>
 * <p>This class provides OpenTelemetry-based metrics for all YAWL interfaces:</p>
 * <ul>
 *   <li><b>Interface A</b>: Design/management interface - specification uploads, account management</li>
 *   <li><b>Interface B</b>: Work item interface - client interactions with the engine</li>
 *   <li><b>Interface E</b>: Event/query interface - log queries and process analytics</li>
 *   <li><b>Interface X</b>: Exception handling interface - event notifications and retries</li>
 * </ul>
 *
 * <h2>Metrics Exposed</h2>
 * <table border="1">
 *   <tr><th>Interface</th><th>Metric</th><th>Type</th><th>Description</th></tr>
 *   <tr><td>A</td><td>yawl.interface.a.requests.total</td><td>Counter</td><td>Total requests to Interface A</td></tr>
 *   <tr><td>A</td><td>yawl.interface.a.latency</td><td>Histogram</td><td>Request latency in seconds</td></tr>
 *   <tr><td>B</td><td>yawl.interface.b.requests.total</td><td>Counter</td><td>Total requests to Interface B</td></tr>
 *   <tr><td>B</td><td>yawl.interface.b.latency</td><td>Histogram</td><td>Request latency in seconds</td></tr>
 *   <tr><td>B</td><td>yawl.interface.b.workitems.processed</td><td>Counter</td><td>Work items processed</td></tr>
 *   <tr><td>B</td><td>yawl.interface.b.connection_pool.wait_time</td><td>Histogram</td><td>Connection pool wait time in ms</td></tr>
 *   <tr><td>E</td><td>yawl.interface.e.queries.total</td><td>Counter</td><td>Total queries to Interface E</td></tr>
 *   <tr><td>E</td><td>yawl.interface.e.query.latency</td><td>Histogram</td><td>Query latency in seconds</td></tr>
 *   <tr><td>E</td><td>yawl.interface.e.result_size</td><td>Histogram</td><td>Query result size in bytes</td></tr>
 *   <tr><td>X</td><td>yawl.interface.x.notifications.total</td><td>Counter</td><td>Event notifications sent</td></tr>
 *   <tr><td>X</td><td>yawl.interface.x.retries.total</td><td>Counter</td><td>Notification retry attempts</td></tr>
 *   <tr><td>X</td><td>yawl.interface.x.failures.total</td><td>Counter</td><td>Notification failures</td></tr>
 *   <tr><td>X</td><td>yawl.interface.x.dead_letters.total</td><td>Counter</td><td>Dead letter queue entries</td></tr>
 * </table>
 *
 * @author YAWL Foundation
 * @version 6.0
 * @since 6.0
 */
public final class InterfaceMetrics {

    private static final Logger _logger = LogManager.getLogger(InterfaceMetrics.class);

    private static final String INSTRUMENTATION_NAME = "org.yawlfoundation.yawl.interfaces";
    private static final double[] LATENCY_BUCKETS =
            {0.001, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0};

    private static volatile InterfaceMetrics _instance;
    private static final ReentrantLock _lock = new ReentrantLock();

    private final OpenTelemetry openTelemetry;
    private final Meter meter;

    private final LongCounter interfaceARequestsCounter;
    private final LongHistogram interfaceALatencyHistogram;
    private final LongCounter interfaceBRequestsCounter;
    private final LongHistogram interfaceBLatencyHistogram;
    private final LongCounter interfaceBWorkItemsCounter;
    private final LongHistogram interfaceBConnectionPoolWaitTimeHistogram;
    private final LongCounter interfaceEQueriesCounter;
    private final LongHistogram interfaceELatencyHistogram;
    private final LongHistogram interfaceEResultSizeHistogram;
    private final LongCounter interfaceXNotificationsCounter;
    private final LongCounter interfaceXRetriesCounter;
    private final LongCounter interfaceXFailuresCounter;
    private final LongCounter interfaceXDeadLettersCounter;

    private final ConcurrentHashMap<String, AtomicLong> operationStartTimes = new ConcurrentHashMap<>();
    private final LongAdder interfaceATotalRequests = new LongAdder();
    private final LongAdder interfaceBTotalRequests = new LongAdder();
    private final LongAdder interfaceBTotalWorkItems = new LongAdder();
    private final LongAdder interfaceBTotalConnectionPoolWaitTime = new LongAdder();
    private final LongAdder interfaceETotalQueries = new LongAdder();
    private final LongAdder interfaceETotalResultSize = new LongAdder();
    private final LongAdder interfaceXTotalNotifications = new LongAdder();
    private final LongAdder interfaceXTotalRetries = new LongAdder();
    private final LongAdder interfaceXTotalFailures = new LongAdder();
    private final LongAdder interfaceXTotalDeadLetters = new LongAdder();

    private volatile boolean enabled = true;

    private InterfaceMetrics() {
        this.openTelemetry = GlobalOpenTelemetry.get();
        this.meter = openTelemetry.getMeter(INSTRUMENTATION_NAME);

        this.interfaceARequestsCounter = meter
                .counterBuilder("yawl.interface.a.requests.total")
                .setDescription("Total number of requests to Interface A (design/management)")
                .setUnit("requests")
                .build();

        this.interfaceALatencyHistogram = meter
                .histogramBuilder("yawl.interface.a.latency")
                .setDescription("Latency of Interface A requests in seconds")
                .setUnit("s")
                .ofLongs()
                .build();

        this.interfaceBRequestsCounter = meter
                .counterBuilder("yawl.interface.b.requests.total")
                .setDescription("Total number of requests to Interface B (work item client)")
                .setUnit("requests")
                .build();

        this.interfaceBLatencyHistogram = meter
                .histogramBuilder("yawl.interface.b.latency")
                .setDescription("Latency of Interface B requests in seconds")
                .setUnit("s")
                .ofLongs()
                .build();

        this.interfaceBWorkItemsCounter = meter
                .counterBuilder("yawl.interface.b.workitems.processed")
                .setDescription("Total number of work items processed through Interface B")
                .setUnit("items")
                .build();

        this.interfaceBConnectionPoolWaitTimeHistogram = meter
                .histogramBuilder("yawl.interface.b.connection_pool.wait_time")
                .setDescription("Connection pool wait time for Interface B in milliseconds")
                .setUnit("ms")
                .ofLongs()
                .build();

        this.interfaceEQueriesCounter = meter
                .counterBuilder("yawl.interface.e.queries.total")
                .setDescription("Total number of queries to Interface E (log gateway)")
                .setUnit("queries")
                .build();

        this.interfaceELatencyHistogram = meter
                .histogramBuilder("yawl.interface.e.query.latency")
                .setDescription("Latency of Interface E queries in seconds")
                .setUnit("s")
                .ofLongs()
                .build();

        this.interfaceEResultSizeHistogram = meter
                .histogramBuilder("yawl.interface.e.result_size")
                .setDescription("Result size of Interface E queries in bytes")
                .setUnit("By")
                .ofLongs()
                .build();

        this.interfaceXNotificationsCounter = meter
                .counterBuilder("yawl.interface.x.notifications.total")
                .setDescription("Total number of event notifications sent via Interface X")
                .setUnit("notifications")
                .build();

        this.interfaceXRetriesCounter = meter
                .counterBuilder("yawl.interface.x.retries.total")
                .setDescription("Total number of notification retry attempts in Interface X")
                .setUnit("retries")
                .build();

        this.interfaceXFailuresCounter = meter
                .counterBuilder("yawl.interface.x.failures.total")
                .setDescription("Total number of notification failures in Interface X")
                .setUnit("failures")
                .build();

        this.interfaceXDeadLettersCounter = meter
                .counterBuilder("yawl.interface.x.dead_letters.total")
                .setDescription("Total number of dead letter queue entries in Interface X")
                .setUnit("entries")
                .build();

        _logger.info("InterfaceMetrics initialized with OpenTelemetry instrumentation");
    }

    public static InterfaceMetrics getInstance() {
        if (_instance == null) {
            _lock.lock();
            try {
                if (_instance == null) {
                    _instance = new InterfaceMetrics();
                }
            } finally {
                _lock.unlock();
            }
        }
        return _instance;
    }

    public long recordInterfaceARequestStart(String operation) {
        if (!enabled) {
            return 0;
        }
        interfaceATotalRequests.increment();
        long startTime = System.nanoTime();
        operationStartTimes.put("A:" + operation, new AtomicLong(startTime));
        return startTime;
    }

    public void recordInterfaceARequestComplete(String operation, long startNanos, boolean success) {
        if (!enabled || startNanos == 0) {
            return;
        }
        long latencyMillis = (System.nanoTime() - startNanos) / 1_000_000;
        Attributes attributes = Attributes.builder()
                .put("yawl.interface", "A")
                .put("yawl.operation", operation)
                .put("yawl.success", success)
                .build();
        interfaceARequestsCounter.add(1, attributes);
        interfaceALatencyHistogram.record(latencyMillis, attributes);
        operationStartTimes.remove("A:" + operation);
        if (_logger.isDebugEnabled()) {
            _logger.debug("Interface A request complete: operation={} latency={}ms success={}",
                    operation, latencyMillis, success);
        }
    }

    public long recordInterfaceBRequestStart(String operation) {
        if (!enabled) {
            return 0;
        }
        interfaceBTotalRequests.increment();
        long startTime = System.nanoTime();
        operationStartTimes.put("B:" + operation, new AtomicLong(startTime));
        return startTime;
    }

    public void recordInterfaceBRequestComplete(String operation, long startNanos, boolean success) {
        if (!enabled || startNanos == 0) {
            return;
        }
        long latencyMillis = (System.nanoTime() - startNanos) / 1_000_000;
        Attributes attributes = Attributes.builder()
                .put("yawl.interface", "B")
                .put("yawl.operation", operation)
                .put("yawl.success", success)
                .build();
        interfaceBRequestsCounter.add(1, attributes);
        interfaceBLatencyHistogram.record(latencyMillis, attributes);
        operationStartTimes.remove("B:" + operation);
        if (_logger.isDebugEnabled()) {
            _logger.debug("Interface B request complete: operation={} latency={}ms success={}",
                    operation, latencyMillis, success);
        }
    }

    public void recordInterfaceBWorkItemProcessed(String workItemId, String caseId, String operation) {
        if (!enabled) {
            return;
        }
        interfaceBTotalWorkItems.increment();
        Attributes attributes = Attributes.builder()
                .put("yawl.interface", "B")
                .put("yawl.workitem.id", workItemId != null ? workItemId : "unknown")
                .put("yawl.case.id", caseId != null ? caseId : "unknown")
                .put("yawl.operation", operation)
                .build();
        interfaceBWorkItemsCounter.add(1, attributes);
    }

    public void recordInterfaceBConnectionPoolWaitTime(long waitTimeMs, String operation) {
        if (!enabled) {
            return;
        }
        interfaceBTotalConnectionPoolWaitTime.add(waitTimeMs);
        Attributes attributes = Attributes.builder()
                .put("yawl.interface", "B")
                .put("yawl.operation", operation)
                .build();
        interfaceBConnectionPoolWaitTimeHistogram.record(waitTimeMs, attributes);
        if (_logger.isDebugEnabled()) {
            _logger.debug("Interface B connection pool wait: operation={} waitTime={}ms",
                    operation, waitTimeMs);
        }
    }

    public long recordInterfaceEQueryStart(String queryType) {
        if (!enabled) {
            return 0;
        }
        interfaceETotalQueries.increment();
        long startTime = System.nanoTime();
        operationStartTimes.put("E:" + queryType, new AtomicLong(startTime));
        return startTime;
    }

    public void recordInterfaceEQueryComplete(String queryType, long startNanos, boolean success) {
        if (!enabled || startNanos == 0) {
            return;
        }
        long latencyMillis = (System.nanoTime() - startNanos) / 1_000_000;
        Attributes attributes = Attributes.builder()
                .put("yawl.interface", "E")
                .put("yawl.query.type", queryType)
                .put("yawl.success", success)
                .build();
        interfaceEQueriesCounter.add(1, attributes);
        interfaceELatencyHistogram.record(latencyMillis, attributes);
        operationStartTimes.remove("E:" + queryType);
        if (_logger.isDebugEnabled()) {
            _logger.debug("Interface E query complete: queryType={} latency={}ms success={}",
                    queryType, latencyMillis, success);
        }
    }

    public void recordInterfaceEQueryComplete(String queryType, long startNanos, boolean success, long resultSizeBytes) {
        if (!enabled || startNanos == 0) {
            return;
        }
        long latencyMillis = (System.nanoTime() - startNanos) / 1_000_000;
        interfaceETotalResultSize.add(resultSizeBytes);
        Attributes attributes = Attributes.builder()
                .put("yawl.interface", "E")
                .put("yawl.query.type", queryType)
                .put("yawl.success", success)
                .build();
        interfaceEQueriesCounter.add(1, attributes);
        interfaceELatencyHistogram.record(latencyMillis, attributes);
        interfaceEResultSizeHistogram.record(resultSizeBytes, attributes);
        operationStartTimes.remove("E:" + queryType);
        if (_logger.isDebugEnabled()) {
            _logger.debug("Interface E query complete: queryType={} latency={}ms success={} resultSize={}bytes",
                    queryType, latencyMillis, success, resultSizeBytes);
        }
    }

    public void recordInterfaceXNotification(String eventType, boolean success) {
        if (!enabled) {
            return;
        }
        interfaceXTotalNotifications.increment();
        Attributes attributes = Attributes.builder()
                .put("yawl.interface", "X")
                .put("yawl.event.type", eventType)
                .put("yawl.success", success)
                .build();
        interfaceXNotificationsCounter.add(1, attributes);
        if (_logger.isDebugEnabled()) {
            _logger.debug("Interface X notification: eventType={} success={}", eventType, success);
        }
    }

    public void recordInterfaceXRetry(String eventType, int retryCount) {
        if (!enabled) {
            return;
        }
        interfaceXTotalRetries.increment();
        Attributes attributes = Attributes.builder()
                .put("yawl.interface", "X")
                .put("yawl.event.type", eventType)
                .put("yawl.retry.count", retryCount)
                .build();
        interfaceXRetriesCounter.add(1, attributes);
        if (_logger.isDebugEnabled()) {
            _logger.debug("Interface X retry: eventType={} retryCount={}", eventType, retryCount);
        }
    }

    public void recordInterfaceXFailure(String eventType, String errorMessage) {
        if (!enabled) {
            return;
        }
        interfaceXTotalFailures.increment();
        Attributes attributes = Attributes.builder()
                .put("yawl.interface", "X")
                .put("yawl.event.type", eventType)
                .put("error.message", errorMessage != null ? errorMessage : "unknown")
                .build();
        interfaceXFailuresCounter.add(1, attributes);
        _logger.warn("Interface X failure recorded: eventType={} error={}", eventType, errorMessage);
    }

    public void recordInterfaceXDeadLetter(String eventType, String errorMessage) {
        if (!enabled) {
            return;
        }
        interfaceXTotalDeadLetters.increment();
        Attributes attributes = Attributes.builder()
                .put("yawl.interface", "X")
                .put("yawl.event.type", eventType)
                .put("error.message", errorMessage != null ? errorMessage : "unknown")
                .build();
        interfaceXDeadLettersCounter.add(1, attributes);
        _logger.warn("Interface X dead letter recorded: eventType={} error={}", eventType, errorMessage);
    }

    public long getInterfaceATotalRequests() {
        return interfaceATotalRequests.longValue();
    }

    public long getInterfaceBTotalRequests() {
        return interfaceBTotalRequests.longValue();
    }

    public long getInterfaceBTotalWorkItems() {
        return interfaceBTotalWorkItems.longValue();
    }

    public long getInterfaceBTotalConnectionPoolWaitTime() {
        return interfaceBTotalConnectionPoolWaitTime.longValue();
    }

    public double getInterfaceBAvgConnectionPoolWaitTime() {
        long requests = interfaceBTotalRequests.longValue();
        if (requests == 0) {
            return 0.0;
        }
        return (double) interfaceBTotalConnectionPoolWaitTime.longValue() / requests;
    }

    public long getInterfaceETotalQueries() {
        return interfaceETotalQueries.longValue();
    }

    public long getInterfaceETotalResultSize() {
        return interfaceETotalResultSize.longValue();
    }

    public double getInterfaceEAvgResultSize() {
        long queries = interfaceETotalQueries.longValue();
        if (queries == 0) {
            return 0.0;
        }
        return (double) interfaceETotalResultSize.longValue() / queries;
    }

    public long getInterfaceXTotalNotifications() {
        return interfaceXTotalNotifications.longValue();
    }

    public long getInterfaceXTotalRetries() {
        return interfaceXTotalRetries.longValue();
    }

    public long getInterfaceXTotalFailures() {
        return interfaceXTotalFailures.longValue();
    }

    public long getInterfaceXTotalDeadLetters() {
        return interfaceXTotalDeadLetters.longValue();
    }

    public double getInterfaceXDeadLetterRate() {
        long notifications = interfaceXTotalNotifications.longValue();
        if (notifications == 0) {
            return 0.0;
        }
        return (interfaceXTotalDeadLetters.longValue() * 100.0) / notifications;
    }

    public double getInterfaceXRetryRate() {
        long notifications = interfaceXTotalNotifications.longValue();
        if (notifications == 0) {
            return 0.0;
        }
        return (interfaceXTotalRetries.longValue() * 100.0) / notifications;
    }

    public double getInterfaceXFailureRate() {
        long notifications = interfaceXTotalNotifications.longValue();
        if (notifications == 0) {
            return 0.0;
        }
        return (interfaceXTotalFailures.longValue() * 100.0) / notifications;
    }

    public String summary() {
        return String.format(
                "InterfaceMetrics{A=%d, B=%d (workItems=%d, avgPoolWait=%.2fms), " +
                "E=%d (avgResultSize=%.2fbytes), X=(notifications=%d, retries=%d, failures=%d, " +
                "deadLetters=%d, retryRate=%.2f%%, failureRate=%.2f%%, deadLetterRate=%.2f%%)}",
                getInterfaceATotalRequests(),
                getInterfaceBTotalRequests(),
                getInterfaceBTotalWorkItems(),
                getInterfaceBAvgConnectionPoolWaitTime(),
                getInterfaceETotalQueries(),
                getInterfaceEAvgResultSize(),
                getInterfaceXTotalNotifications(),
                getInterfaceXTotalRetries(),
                getInterfaceXTotalFailures(),
                getInterfaceXTotalDeadLetters(),
                getInterfaceXRetryRate(),
                getInterfaceXFailureRate(),
                getInterfaceXDeadLetterRate()
        );
    }

    public void logMetrics() {
        _logger.info(summary());
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        _logger.info("InterfaceMetrics " + (enabled ? "enabled" : "disabled"));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Meter getMeter() {
        return meter;
    }

    public void reset() {
        interfaceATotalRequests.reset();
        interfaceBTotalRequests.reset();
        interfaceBTotalWorkItems.reset();
        interfaceBTotalConnectionPoolWaitTime.reset();
        interfaceETotalQueries.reset();
        interfaceETotalResultSize.reset();
        interfaceXTotalNotifications.reset();
        interfaceXTotalRetries.reset();
        interfaceXTotalFailures.reset();
        interfaceXTotalDeadLetters.reset();
        operationStartTimes.clear();
        _logger.debug("InterfaceMetrics reset");
    }
}
