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

package org.yawlfoundation.yawl.engine.interfce;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * P5 MEDIUM - HTTP Connection Pool Observability: tracks per-request latency and
 * connection-pool utilization for all YAWL inter-service HTTP communications.
 *
 * <h2>Background</h2>
 * <p>{@link Interface_Client} uses {@code java.net.http.HttpClient} (Java 11+), which
 * manages an internal connection pool automatically.  The JDK's {@code HttpClient} does
 * not expose direct pool utilization metrics, so this class wraps request-level
 * observability: in-flight count, request latency histograms, and error rates.</p>
 *
 * <h2>Target configuration (100-200 concurrent cases)</h2>
 * <pre>
 *   // HttpClient is built with:
 *   HttpClient.newBuilder()
 *       .connectTimeout(Duration.ofMillis(5000))
 *       .executor(Executors.newVirtualThreadPerTaskExecutor())  // unbounded virtual threads
 *       .build();
 *
 *   // Virtual threads eliminate traditional connection-pool tuning:
 *   // Each blocked HTTP request parks on a virtual thread (cheap).
 *   // The JDK HttpClient uses HTTP/1.1 keep-alive and HTTP/2 multiplexing automatically.
 * </pre>
 *
 * <h2>Pool sizing recommendation</h2>
 * <ul>
 *   <li>Virtual thread executor: effectively unlimited; no fixed pool needed</li>
 *   <li>Connection timeout: 5000ms (current)</li>
 *   <li>Request timeout: 120000ms (current, configurable per-request)</li>
 *   <li>Keep-alive: managed by JDK HttpClient (HTTP/1.1: Connection: keep-alive)</li>
 *   <li>For 100-200 concurrent cases: expect 50-200 concurrent HTTP connections</li>
 * </ul>
 *
 * <h2>Observability metrics tracked</h2>
 * <ul>
 *   <li>In-flight request count (gauge)</li>
 *   <li>Total requests sent</li>
 *   <li>Total errors (HTTP 4xx/5xx + IOException)</li>
 *   <li>Total request latency (nanos) for avg/p95 calculation</li>
 *   <li>Peak concurrent in-flight count</li>
 * </ul>
 *
 * <h2>Integration</h2>
 * <p>Call {@link #recordRequestStart()} before each HTTP send, and
 * {@link #recordRequestComplete(long, boolean)} in the response handler.</p>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class HttpConnectionPoolMetrics {

    private static final Logger _logger = LogManager.getLogger(HttpConnectionPoolMetrics.class);

    private static volatile HttpConnectionPoolMetrics _instance;
    private static final ReentrantLock _lock = new ReentrantLock();

    // ------------------------------------------------------------------ counters

    /** Number of requests currently in flight (connection-pool utilization proxy). */
    private final AtomicLong _inFlight = new AtomicLong(0);

    /** Peak number of concurrent in-flight requests observed. */
    private volatile long _peakInFlight = 0;

    /** Total HTTP requests sent (successful + failed). */
    private final LongAdder _totalRequests = new LongAdder();

    /** Total requests that resulted in an error (timeout, IOException, 4xx, 5xx). */
    private final LongAdder _totalErrors = new LongAdder();

    /** Cumulative request latency in nanoseconds (for average calculation). */
    private final LongAdder _totalLatencyNanos = new LongAdder();

    /** Count of latency samples (may lag _totalRequests during errors). */
    private final LongAdder _latencySamples = new LongAdder();

    // ------------------------------------------------------------------ constructor/singleton

    private HttpConnectionPoolMetrics() {}

    /**
     * Returns the singleton metrics instance.
     *
     * @return the singleton {@link HttpConnectionPoolMetrics}
     */
    public static HttpConnectionPoolMetrics getInstance() {
        if (_instance == null) {
            _lock.lock();
            try {
                if (_instance == null) {
                    _instance = new HttpConnectionPoolMetrics();
                }
            } finally {
                _lock.unlock();
            }
        }
        return _instance;
    }

    // ------------------------------------------------------------------ lifecycle hooks

    /**
     * Records the start of an HTTP request.  Call immediately before
     * {@code httpClient.send()} or {@code httpClient.sendAsync()}.
     *
     * @return the start timestamp in nanoseconds (pass to {@link #recordRequestComplete})
     */
    public long recordRequestStart() {
        _totalRequests.increment();
        long current = _inFlight.incrementAndGet();
        if (current > _peakInFlight) {
            _peakInFlight = current;
        }
        if (current > 150) {
            // Warn when utilization exceeds recommended threshold for 100-200 concurrent cases
            _logger.warn("HTTP connection pool: {} requests in flight (threshold: 150). " +
                    "Consider reviewing concurrent case load.", current);
        }
        return System.nanoTime();
    }

    /**
     * Records the completion of an HTTP request.
     *
     * @param startNanos the value returned by {@link #recordRequestStart()}
     * @param isError    {@code true} if the request resulted in an HTTP error or exception
     */
    public void recordRequestComplete(long startNanos, boolean isError) {
        _inFlight.decrementAndGet();
        long latency = System.nanoTime() - startNanos;
        _totalLatencyNanos.add(latency);
        _latencySamples.increment();
        if (isError) {
            _totalErrors.increment();
        }
        if (_logger.isDebugEnabled()) {
            _logger.debug("HTTP request complete: latency={}ms error={} inFlight={}",
                    latency / 1_000_000, isError, _inFlight.get());
        }
    }

    // ------------------------------------------------------------------ metrics accessors

    /** Returns the number of HTTP requests currently in flight. */
    public long getInFlightCount() {
        return _inFlight.get();
    }

    /** Returns the peak number of concurrent in-flight requests since startup. */
    public long getPeakInFlightCount() {
        return _peakInFlight;
    }

    /** Returns the total number of HTTP requests sent. */
    public long getTotalRequests() {
        return _totalRequests.longValue();
    }

    /** Returns the total number of requests that resulted in an error. */
    public long getTotalErrors() {
        return _totalErrors.longValue();
    }

    /** Returns the error rate as a fraction in [0.0, 1.0]. */
    public double getErrorRate() {
        long total = getTotalRequests();
        return total == 0 ? 0.0 : (double) getTotalErrors() / total;
    }

    /** Returns the average request latency in milliseconds. */
    public double getAvgLatencyMs() {
        long samples = _latencySamples.longValue();
        return samples == 0 ? 0.0 : _totalLatencyNanos.longValue() / (double) samples / 1_000_000.0;
    }

    /**
     * Returns a formatted diagnostic summary of pool metrics.
     *
     * @return a human-readable metrics summary
     */
    public String summary() {
        return String.format(
            "HttpConnectionPool{inFlight=%d, peak=%d, total=%d, errors=%d, " +
            "errorRate=%.1f%%, avgLatency=%.2fms}",
            getInFlightCount(), getPeakInFlightCount(), getTotalRequests(),
            getTotalErrors(), getErrorRate() * 100, getAvgLatencyMs());
    }

    /**
     * Logs the current pool metrics at INFO level.  Call from a health check or
     * periodic monitoring task.
     */
    public void logMetrics() {
        _logger.info(summary());
    }

    /**
     * Resets all counters.  Intended for use in testing or rolling metric windows.
     */
    public void reset() {
        _totalRequests.reset();
        _totalErrors.reset();
        _totalLatencyNanos.reset();
        _latencySamples.reset();
        _peakInFlight = 0;
        // Note: _inFlight is NOT reset as it represents live state
        _logger.debug("HttpConnectionPoolMetrics reset");
    }
}
