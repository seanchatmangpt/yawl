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
 * You should have received a copy of the GNU Lesser General
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import javax.sql.DataSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

/**
 * Real-time database performance monitor for YAWL workflow engine.
 *
 * <p>Collects and reports on:
 * <ul>
 *   <li>Connection pool health and utilization</li>
 *   <li>Query execution times and slow query detection</li>
 *   <li>Table sizes and growth rates</li>
 *   <li>Transaction statistics</li>
 *   <li>Cache hit rates</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 5.2
 * @since 2026-02-18
 */
public class DatabasePerformanceMonitor {

    private static final Logger _log = LogManager.getLogger(DatabasePerformanceMonitor.class);

    private static final long SLOW_QUERY_THRESHOLD_MS = 1000;
    private static final long CRITICAL_QUERY_THRESHOLD_MS = 5000;

    private final DataSource dataSource;
    private final SessionFactory sessionFactory;
    private final ScheduledExecutorService scheduler;
    private final Map<String, QueryStats> queryStatsMap = new ConcurrentHashMap<>();
    private final AtomicLong totalQueries = new AtomicLong();
    private final AtomicLong slowQueries = new AtomicLong();
    private final Instant startTime = Instant.now();
    private final LatencyHistogram queryLatencyHistogram = new LatencyHistogram(1000);

    public DatabasePerformanceMonitor(DataSource dataSource, SessionFactory sessionFactory) {
        this.dataSource = dataSource;
        this.sessionFactory = sessionFactory;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "db-perf-monitor");
            t.setDaemon(true);
            return t;
        });

        if (sessionFactory != null && !sessionFactory.getStatistics().isStatisticsEnabled()) {
            sessionFactory.getStatistics().setStatisticsEnabled(true);
        }
    }

    public void startMonitoring(long intervalSeconds) {
        scheduler.scheduleAtFixedRate(
                this::collectMetrics,
                intervalSeconds,
                intervalSeconds,
                TimeUnit.SECONDS
        );
        _log.info("Database performance monitoring started (interval={}s)", intervalSeconds);
    }

    public void stopMonitoring() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        _log.info("Database performance monitoring stopped");
    }

    public void recordQuery(String queryName, long durationMs, int rowCount) {
        totalQueries.incrementAndGet();

        if (durationMs > SLOW_QUERY_THRESHOLD_MS) {
            slowQueries.incrementAndGet();
            if (durationMs > CRITICAL_QUERY_THRESHOLD_MS) {
                _log.warn("CRITICAL SLOW QUERY: {} took {}ms (rows={})", 
                        queryName, durationMs, rowCount);
            } else {
                _log.info("Slow query detected: {} took {}ms (rows={})", 
                        queryName, durationMs, rowCount);
            }
        }

        queryStatsMap.computeIfAbsent(queryName, k -> new QueryStats())
                .record(durationMs, rowCount);
        queryLatencyHistogram.record(durationMs);
    }

    private void collectMetrics() {
        try {
            Map<String, Object> metrics = getCurrentMetrics();
            _log.debug("DB Metrics: queries={}, slow={}, p50={}ms, p95={}ms, p99={}ms",
                    totalQueries.get(),
                    slowQueries.get(),
                    queryLatencyHistogram.getPercentile(50),
                    queryLatencyHistogram.getPercentile(95),
                    queryLatencyHistogram.getPercentile(99));
            checkAndAlert(metrics);
        } catch (Exception e) {
            _log.error("Error collecting database metrics", e);
        }
    }

    public Map<String, Object> getCurrentMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("uptime_seconds", Duration.between(startTime, Instant.now()).getSeconds());
        metrics.put("total_queries", totalQueries.get());
        metrics.put("slow_queries", slowQueries.get());
        metrics.put("slow_query_rate", 
                totalQueries.get() > 0 ? (double) slowQueries.get() / totalQueries.get() : 0.0);
        metrics.put("query_latency_p50_ms", queryLatencyHistogram.getPercentile(50));
        metrics.put("query_latency_p95_ms", queryLatencyHistogram.getPercentile(95));
        metrics.put("query_latency_p99_ms", queryLatencyHistogram.getPercentile(99));

        if (sessionFactory != null) {
            Statistics hibernateStats = sessionFactory.getStatistics();
            metrics.put("hibernate_session_opens", hibernateStats.getSessionOpenCount());
            metrics.put("hibernate_session_closes", hibernateStats.getSessionCloseCount());
            metrics.put("hibernate_transactions", hibernateStats.getTransactionCount());
            metrics.put("hibernate_query_count", hibernateStats.getQueryExecutionCount());
            metrics.put("hibernate_query_max_time_ms", hibernateStats.getQueryExecutionMaxTime());
            metrics.put("cache_hit_count", hibernateStats.getSecondLevelCacheHitCount());
            metrics.put("cache_miss_count", hibernateStats.getSecondLevelCacheMissCount());
            metrics.put("cache_put_count", hibernateStats.getSecondLevelCachePutCount());

            long totalCacheRequests = hibernateStats.getSecondLevelCacheHitCount() 
                    + hibernateStats.getSecondLevelCacheMissCount();
            if (totalCacheRequests > 0) {
                metrics.put("cache_hit_rate", 
                        (double) hibernateStats.getSecondLevelCacheHitCount() / totalCacheRequests);
            }
        }

        metrics.putAll(getConnectionPoolStats());
        metrics.put("table_stats", getTableStatistics());
        return metrics;
    }

    public Map<String, Object> getHealthStatus() {
        Map<String, Object> health = new HashMap<>();
        boolean connected = testConnection();
        health.put("connected", connected);

        if (!connected) {
            health.put("status", "DOWN");
            health.put("reason", "Cannot establish database connection");
            return health;
        }

        double p95Latency = queryLatencyHistogram.getPercentile(95);
        if (p95Latency > 100) {
            health.put("status", "WARNING");
            health.put("reason", "P95 query latency exceeds 100ms threshold");
            health.put("p95_latency_ms", p95Latency);
        } else {
            health.put("status", "UP");
        }
        return health;
    }

    public List<Map<String, Object>> getSlowestQueries(int n) {
        return queryStatsMap.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().maxTimeMs, a.getValue().maxTimeMs))
                .limit(n)
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("query_name", e.getKey());
                    m.put("max_time_ms", e.getValue().maxTimeMs);
                    m.put("avg_time_ms", e.getValue().getAverageTimeMs());
                    m.put("execution_count", e.getValue().executionCount);
                    return m;
                })
                .toList();
    }

    private boolean testConnection() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() && rs.getInt(1) == 1;
        } catch (SQLException e) {
            _log.error("Database connection test failed", e);
            return false;
        }
    }

    private Map<String, Object> getConnectionPoolStats() {
        Map<String, Object> stats = new HashMap<>();
        try {
            if (dataSource instanceof com.zaxxer.hikari.HikariDataSource hikari) {
                stats.put("pool_active_connections", hikari.getHikariPoolMXBean().getActiveConnections());
                stats.put("pool_idle_connections", hikari.getHikariPoolMXBean().getIdleConnections());
                stats.put("pool_total_connections", hikari.getHikariPoolMXBean().getTotalConnections());
                stats.put("pool_threads_awaiting", hikari.getHikariPoolMXBean().getThreadsAwaitingConnection());
            }
        } catch (Exception e) {
            _log.debug("Could not retrieve connection pool stats: {}", e.getMessage());
        }
        return stats;
    }

    private Map<String, Long> getTableStatistics() {
        Map<String, Long> tableStats = new HashMap<>();
        String[] tables = {"Work_Items", "RUNNER_STATES", "logEvent", "logTaskInstance", "auditEvent"};

        for (String table : tables) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM " + table);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    tableStats.put(table.toLowerCase() + "_count", rs.getLong(1));
                }
            } catch (SQLException e) {
                _log.debug("Could not get count for table {}: {}", table, e.getMessage());
            }
        }
        return tableStats;
    }

    private void checkAndAlert(Map<String, Object> metrics) {
        double slowQueryRate = (double) metrics.getOrDefault("slow_query_rate", 0.0);
        if (slowQueryRate > 0.05) {
            _log.warn("High slow query rate: {}%", slowQueryRate * 100);
        }

        double p95 = (double) metrics.getOrDefault("query_latency_p95_ms", 0.0);
        if (p95 > 100) {
            _log.warn("P95 query latency exceeds threshold: {}ms > 100ms", p95);
        }
    }

    private static class QueryStats {
        private final ReentrantLock lock = new ReentrantLock();
        long executionCount;
        long totalTimeMs;
        long maxTimeMs;
        long minTimeMs = Long.MAX_VALUE;
        long totalRows;

        void record(long durationMs, int rows) {
            lock.lock();
            try {
                executionCount++;
                totalTimeMs += durationMs;
                maxTimeMs = Math.max(maxTimeMs, durationMs);
                minTimeMs = Math.min(minTimeMs, durationMs);
                totalRows += rows;
            } finally {
                lock.unlock();
            }
        }

        double getAverageTimeMs() {
            return executionCount > 0 ? (double) totalTimeMs / executionCount : 0;
        }
    }

    private static class LatencyHistogram {
        private final ReentrantLock lock = new ReentrantLock();
        private final long[] samples;
        private final int capacity;
        private int position;
        private boolean filled;

        LatencyHistogram(int capacity) {
            this.capacity = capacity;
            this.samples = new long[capacity];
        }

        void record(long valueMs) {
            lock.lock();
            try {
                samples[position] = valueMs;
                position = (position + 1) % capacity;
                if (position == 0) {
                    filled = true;
                }
            } finally {
                lock.unlock();
            }
        }

        double getPercentile(int percentile) {
            lock.lock();
            try {
                int size = filled ? capacity : position;
                if (size == 0) {
                    return 0;
                }
                long[] sorted = new long[size];
                System.arraycopy(samples, 0, sorted, 0, size);
                java.util.Arrays.sort(sorted);
                int index = (int) Math.ceil(percentile / 100.0 * size) - 1;
                index = Math.max(0, Math.min(index, size - 1));
                return sorted[index];
            } finally {
                lock.unlock();
            }
        }
    }
}
