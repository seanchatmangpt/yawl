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

package org.yawlfoundation.yawl.engine;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.SessionFactory;

import org.yawlfoundation.yawl.util.DataArchiver;
import org.yawlfoundation.yawl.util.DatabaseBackupManager;
import org.yawlfoundation.yawl.util.DatabasePerformanceMonitor;

/**
 * Centralized database service for YAWL workflow engine.
 *
 * <p>Provides a unified interface for:
 * <ul>
 *   <li>Performance monitoring and alerting</li>
 *   <li>Automated data archiving</li>
 *   <li>Backup scheduling and verification</li>
 *   <li>Health checking and diagnostics</li>
 * </ul>
 *
 * <p>Target metrics:
 * <ul>
 *   <li>Query response: P95 < 50ms, P99 < 100ms</li>
 *   <li>Uptime: 99.99%</li>
 *   <li>Cache hit rate: > 95%</li>
 *   <li>Connection pool utilization: < 80%</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 5.2
 * @since 2026-02-18
 */
public class DatabaseService {

    private static final Logger _log = LogManager.getLogger(DatabaseService.class);

    // Default configuration
    private static final int DEFAULT_MONITORING_INTERVAL_SECONDS = 60;
    private static final int DEFAULT_ARCHIVE_INTERVAL_HOURS = 24;
    private static final int DEFAULT_BACKUP_HOUR = 2; // 2 AM
    private static final int DEFAULT_WEEKLY_BACKUP_DAY = 7; // Sunday

    private final DataSource dataSource;
    private final SessionFactory sessionFactory;
    private final Properties dbProperties;
    private final String backupDirectory;

    private DatabasePerformanceMonitor performanceMonitor;
    private DataArchiver dataArchiver;
    private DatabaseBackupManager backupManager;

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);

    // Configuration
    private volatile int monitoringIntervalSeconds = DEFAULT_MONITORING_INTERVAL_SECONDS;
    private volatile int archiveIntervalHours = DEFAULT_ARCHIVE_INTERVAL_HOURS;
    private volatile int dailyBackupHour = DEFAULT_BACKUP_HOUR;
    private volatile int weeklyBackupDay = DEFAULT_WEEKLY_BACKUP_DAY;
    private volatile boolean archivingEnabled = true;
    private volatile boolean backupEnabled = true;

    public DatabaseService(DataSource dataSource, SessionFactory sessionFactory,
                           Properties dbProperties, String backupDirectory) {
        this.dataSource = dataSource;
        this.sessionFactory = sessionFactory;
        this.dbProperties = dbProperties;
        this.backupDirectory = backupDirectory;
    }

    /**
     * Initializes and starts all database services.
     */
    public synchronized void start() {
        if (!initialized.compareAndSet(false, true)) {
            _log.warn("DatabaseService already initialized");
            return;
        }

        _log.info("Starting DatabaseService...");

        // Initialize performance monitor
        performanceMonitor = new DatabasePerformanceMonitor(dataSource, sessionFactory);
        performanceMonitor.startMonitoring(monitoringIntervalSeconds);

        // Initialize data archiver
        if (archivingEnabled) {
            dataArchiver = new DataArchiver(dataSource);
            dataArchiver.setDryRun(false);
            dataArchiver.setLogRetentionDays(90);
            dataArchiver.setAuditRetentionDays(365);
            dataArchiver.setWorkItemRetentionDays(30);
            dataArchiver.scheduleDailyArchiving(4); // Archive at 4 AM
            _log.info("Data archiver scheduled");
        }

        // Initialize backup manager
        if (backupEnabled && backupDirectory != null) {
            backupManager = new DatabaseBackupManager(dataSource, dbProperties, backupDirectory);
            backupManager.scheduleDailyBackup(dailyBackupHour);
            backupManager.scheduleWeeklyBackup(weeklyBackupDay, dailyBackupHour);
            _log.info("Backup manager scheduled (daily at {}h, weekly on day {})",
                    dailyBackupHour, weeklyBackupDay);
        }

        running.set(true);
        _log.info("DatabaseService started successfully");
    }

    /**
     * Stops all database services.
     */
    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        _log.info("Stopping DatabaseService...");

        if (performanceMonitor != null) {
            performanceMonitor.stopMonitoring();
        }

        if (dataArchiver != null) {
            dataArchiver.stop();
        }

        if (backupManager != null) {
            backupManager.stop();
        }

        initialized.set(false);
        _log.info("DatabaseService stopped");
    }

    /**
     * Returns current performance metrics.
     *
     * @return map of metric names to values
     */
    public Map<String, Object> getPerformanceMetrics() {
        if (performanceMonitor == null) {
            return Map.of("status", "not_initialized");
        }
        return performanceMonitor.getCurrentMetrics();
    }

    /**
     * Returns database health status.
     *
     * @return health status map
     */
    public Map<String, Object> getHealthStatus() {
        Map<String, Object> health = new java.util.HashMap<>();

        // Basic connectivity
        health.put("connected", testConnection());

        // Performance monitor health
        if (performanceMonitor != null) {
            health.putAll(performanceMonitor.getHealthStatus());
        }

        // Table statistics
        health.put("table_stats", getTableStatistics());

        // Uptime
        if (sessionFactory != null) {
            var stats = sessionFactory.getStatistics();
            if (stats != null && stats.getStartTime() > 0) {
                Instant startTime = Instant.ofEpochMilli(stats.getStartTime());
                health.put("uptime", Duration.between(startTime, Instant.now()).toString());
            }
        }

        return health;
    }

    /**
     * Triggers an immediate archiving run.
     *
     * @return archiving statistics
     */
    public Map<String, DataArchiver.ArchiveStats> runArchiving() {
        if (dataArchiver == null) {
            return Map.of();
        }
        return dataArchiver.runArchiving();
    }

    /**
     * Creates a manual backup.
     *
     * @return backup record
     */
    public DatabaseBackupManager.BackupRecord createBackup() {
        if (backupManager == null) {
            return null;
        }
        return backupManager.createBackup("manual");
    }

    /**
     * Returns performance summary for the dashboard.
     *
     * @return formatted performance summary
     */
    public String getPerformanceSummary() {
        if (performanceMonitor == null) {
            return "Performance monitoring not available";
        }

        Map<String, Object> metrics = performanceMonitor.getCurrentMetrics();
        StringBuilder sb = new StringBuilder();

        sb.append("=== Database Performance Summary ===\n");
        sb.append(String.format("Total queries: %,d%n", metrics.getOrDefault("total_queries", 0)));
        sb.append(String.format("Slow queries: %,d%n", metrics.getOrDefault("slow_queries", 0)));
        sb.append(String.format("Latency P50: %.1fms%n", metrics.getOrDefault("query_latency_p50_ms", 0.0)));
        sb.append(String.format("Latency P95: %.1fms%n", metrics.getOrDefault("query_latency_p95_ms", 0.0)));
        sb.append(String.format("Latency P99: %.1fms%n", metrics.getOrDefault("query_latency_p99_ms", 0.0)));
        sb.append(String.format("Cache hit rate: %.2f%%%n",
                (double) metrics.getOrDefault("cache_hit_rate", 1.0) * 100));
        sb.append(String.format("Connection pool: %s%n", metrics.getOrDefault("pool_status", "unknown")));

        return sb.toString();
    }

    /**
     * Returns the top N slowest queries.
     *
     * @param n number of queries to return
     * @return list of slow query statistics
     */
    public java.util.List<Map<String, Object>> getSlowestQueries(int n) {
        if (performanceMonitor == null) {
            return java.util.List.of();
        }
        return performanceMonitor.getSlowestQueries(n);
    }

    /**
     * Records a query execution for performance tracking.
     *
     * @param queryName identifier for the query
     * @param durationMs execution time in milliseconds
     * @param rowCount number of rows affected/returned
     */
    public void recordQuery(String queryName, long durationMs, int rowCount) {
        if (performanceMonitor != null) {
            performanceMonitor.recordQuery(queryName, durationMs, rowCount);
        }
    }

    /**
     * Checks if the database service is healthy.
     *
     * @return true if all checks pass
     */
    public boolean isHealthy() {
        Map<String, Object> health = getHealthStatus();
        String status = (String) health.getOrDefault("status", "DOWN");
        return "UP".equals(status) || "WARNING".equals(status);
    }

    private boolean testConnection() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(5);
        } catch (SQLException e) {
            _log.error("Database connection test failed", e);
            return false;
        }
    }

    private Map<String, Long> getTableStatistics() {
        Map<String, Long> stats = new java.util.HashMap<>();
        String[] tables = {"work_items", "runner_states", "logevent", "logtaskinstance", "auditevent"};

        for (String table : tables) {
            try (Connection conn = dataSource.getConnection();
                 var stmt = conn.createStatement();
                 var rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
                if (rs.next()) {
                    stats.put(table + "_count", rs.getLong(1));
                }
            } catch (SQLException e) {
                _log.debug("Could not get count for table {}", table);
            }
        }
        return stats;
    }

    // Configuration setters

    public void setMonitoringIntervalSeconds(int seconds) {
        this.monitoringIntervalSeconds = seconds;
    }

    public void setArchiveIntervalHours(int hours) {
        this.archiveIntervalHours = hours;
    }

    public void setDailyBackupHour(int hour) {
        this.dailyBackupHour = hour;
    }

    public void setWeeklyBackupDay(int day) {
        this.weeklyBackupDay = day;
    }

    public void setArchivingEnabled(boolean enabled) {
        this.archivingEnabled = enabled;
    }

    public void setBackupEnabled(boolean enabled) {
        this.backupEnabled = enabled;
    }

    /**
     * Returns the performance monitor instance.
     */
    public DatabasePerformanceMonitor getPerformanceMonitor() {
        return performanceMonitor;
    }

    /**
     * Returns the data archiver instance.
     */
    public DataArchiver getDataArchiver() {
        return dataArchiver;
    }

    /**
     * Returns the backup manager instance.
     */
    public DatabaseBackupManager getBackupManager() {
        return backupManager;
    }
}
