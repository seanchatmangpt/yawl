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
import java.util.concurrent.locks.ReentrantLock;

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

    private static final int DEFAULT_MONITORING_INTERVAL_SECONDS = 60;
    private static final int DEFAULT_BACKUP_HOUR = 2;

    private final DataSource dataSource;
    private final SessionFactory sessionFactory;
    private final Properties dbProperties;
    private final String backupDirectory;

    private DatabasePerformanceMonitor performanceMonitor;
    private DataArchiver dataArchiver;
    private DatabaseBackupManager backupManager;

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);
    // ReentrantLock instead of synchronized — avoids pinning virtual threads during init/shutdown
    private final ReentrantLock _lifecycleLock = new ReentrantLock();

    private volatile int monitoringIntervalSeconds = DEFAULT_MONITORING_INTERVAL_SECONDS;
    private volatile int dailyBackupHour = DEFAULT_BACKUP_HOUR;
    private volatile boolean archivingEnabled = true;
    private volatile boolean backupEnabled = true;

    public DatabaseService(DataSource dataSource, SessionFactory sessionFactory,
                           Properties dbProperties, String backupDirectory) {
        this.dataSource = dataSource;
        this.sessionFactory = sessionFactory;
        this.dbProperties = dbProperties;
        this.backupDirectory = backupDirectory;
    }

    public void start() {
        _lifecycleLock.lock();
        try {
            if (!initialized.compareAndSet(false, true)) {
                _log.warn("DatabaseService already initialized");
                return;
            }

            _log.info("Starting DatabaseService...");

            performanceMonitor = new DatabasePerformanceMonitor(dataSource, sessionFactory);
            performanceMonitor.startMonitoring(monitoringIntervalSeconds);

            if (archivingEnabled) {
                dataArchiver = new DataArchiver(dataSource);
                dataArchiver.setLogRetentionDays(90);
                dataArchiver.setAuditRetentionDays(365);
                dataArchiver.setWorkItemRetentionDays(30);
                dataArchiver.scheduleDailyArchiving(4);
                _log.info("Data archiver scheduled");
            }

            if (backupEnabled && backupDirectory != null) {
                backupManager = new DatabaseBackupManager(dataSource, dbProperties, backupDirectory);
                backupManager.scheduleDailyBackup(dailyBackupHour);
                _log.info("Backup manager scheduled (daily at {}h)", dailyBackupHour);
            }

            running.set(true);
            _log.info("DatabaseService started successfully");
        } finally {
            _lifecycleLock.unlock();
        }
    }

    public void stop() {
        _lifecycleLock.lock();
        try {
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
        } finally {
            _lifecycleLock.unlock();
        }
    }

    public Map<String, Object> getPerformanceMetrics() {
        if (performanceMonitor == null) {
            return Map.of("status", "not_initialized");
        }
        return performanceMonitor.getCurrentMetrics();
    }

    public Map<String, Object> getHealthStatus() {
        Map<String, Object> health = new java.util.HashMap<>();
        health.put("connected", testConnection());

        if (performanceMonitor != null) {
            health.putAll(performanceMonitor.getHealthStatus());
        }

        if (sessionFactory != null) {
            var stats = sessionFactory.getStatistics();
            if (stats != null && stats.getStartTime() > 0) {
                Instant startTime = Instant.ofEpochMilli(stats.getStartTime());
                health.put("uptime", Duration.between(startTime, Instant.now()).toString());
            }
        }

        return health;
    }

    public Map<String, DataArchiver.ArchiveStats> runArchiving() {
        if (dataArchiver == null) {
            return Map.of();
        }
        return dataArchiver.runArchiving();
    }

    public DatabaseBackupManager.BackupRecord createBackup() {
        if (backupManager == null) {
            return null;
        }
        return backupManager.createBackup("manual");
    }

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

        return sb.toString();
    }

    public java.util.List<Map<String, Object>> getSlowestQueries(int n) {
        if (performanceMonitor == null) {
            return java.util.List.of();
        }
        return performanceMonitor.getSlowestQueries(n);
    }

    public void recordQuery(String queryName, long durationMs, int rowCount) {
        if (performanceMonitor != null) {
            performanceMonitor.recordQuery(queryName, durationMs, rowCount);
        }
    }

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

    public void setMonitoringIntervalSeconds(int seconds) {
        this.monitoringIntervalSeconds = seconds;
    }

    public void setDailyBackupHour(int hour) {
        this.dailyBackupHour = hour;
    }

    public void setArchivingEnabled(boolean enabled) {
        this.archivingEnabled = enabled;
    }

    public void setBackupEnabled(boolean enabled) {
        this.backupEnabled = enabled;
    }

    public DatabasePerformanceMonitor getPerformanceMonitor() {
        return performanceMonitor;
    }

    public DataArchiver getDataArchiver() {
        return dataArchiver;
    }

    public DatabaseBackupManager getBackupManager() {
        return backupManager;
    }
}
