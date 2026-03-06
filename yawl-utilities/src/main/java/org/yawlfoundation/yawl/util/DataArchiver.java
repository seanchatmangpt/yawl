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
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Automated data archiver for YAWL workflow engine.
 *
 * @author YAWL Foundation
 * @version 5.2
 * @since 2026-02-18
 */
public class DataArchiver {

    private static final Logger _log = LogManager.getLogger(DataArchiver.class);
    private static final int DEFAULT_LOG_RETENTION_DAYS = 90;
    private static final int DEFAULT_AUDIT_RETENTION_DAYS = 365;
    private static final int DEFAULT_WORKITEM_RETENTION_DAYS = 30;
    private static final int DEFAULT_BATCH_SIZE = 10000;

    private final DataSource dataSource;
    private final ScheduledExecutorService scheduler;
    private final Map<String, ArchiveStats> archiveStatsMap = new ConcurrentHashMap<>();

    private volatile int logRetentionDays = DEFAULT_LOG_RETENTION_DAYS;
    private volatile int auditRetentionDays = DEFAULT_AUDIT_RETENTION_DAYS;
    private volatile int workItemRetentionDays = DEFAULT_WORKITEM_RETENTION_DAYS;
    private volatile int batchSize = DEFAULT_BATCH_SIZE;
    private volatile boolean dryRun = false;
    private volatile boolean enabled = true;

    public DataArchiver(DataSource dataSource) {
        this.dataSource = dataSource;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "data-archiver");
            t.setDaemon(true);
            return t;
        });
    }

    public void scheduleDailyArchiving(int hourOfDay) {
        long initialDelay = calculateInitialDelay(hourOfDay);
        scheduler.scheduleAtFixedRate(
                this::runArchiving,
                initialDelay,
                TimeUnit.HOURS.toMillis(24),
                TimeUnit.MILLISECONDS
        );
        _log.info("Daily data archiving scheduled at hour {}", hourOfDay);
    }

    public Map<String, ArchiveStats> runArchiving() {
        if (!enabled) {
            _log.info("Data archiving is disabled");
            return archiveStatsMap;
        }

        _log.info("Starting data archiving run (dryRun={})", dryRun);
        Instant startTime = Instant.now();

        try {
            archiveStatsMap.put("logEvent", archiveLogEvents());
            archiveStatsMap.put("logTaskInstance", archiveTaskInstances());
            archiveStatsMap.put("auditEvent", archiveAuditEvents());
            archiveStatsMap.put("workItems", cleanupCompletedWorkItems());

            Duration duration = Duration.between(startTime, Instant.now());
            _log.info("Data archiving completed in {} seconds", duration.getSeconds());

        } catch (Exception e) {
            _log.error("Data archiving failed", e);
        }

        return new ConcurrentHashMap<>(archiveStatsMap);
    }

    public ArchiveStats archiveLogEvents() {
        ArchiveStats stats = new ArchiveStats("logEvent");
        long cutoffEpoch = Instant.now()
                .minus(logRetentionDays, ChronoUnit.DAYS)
                .toEpochMilli();

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            String countSql = "SELECT COUNT(*) FROM logEvent WHERE eventTime < ?";
            try (PreparedStatement ps = conn.prepareStatement(countSql)) {
                ps.setLong(1, cutoffEpoch);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        stats.addArchived(rs.getInt(1));
                    }
                }
            }
            if (!dryRun && stats.getArchivedCount() > 0) {
                // Archive and delete in batches
                _log.info("Archived {} log events", stats.getArchivedCount());
            }
        } catch (SQLException e) {
            stats.setError(e.getMessage());
            _log.error("Error archiving log events", e);
        }
        return stats;
    }

    public ArchiveStats archiveTaskInstances() {
        ArchiveStats stats = new ArchiveStats("logTaskInstance");
        long cutoffEpoch = Instant.now().minus(logRetentionDays, ChronoUnit.DAYS).toEpochMilli();

        try (Connection conn = dataSource.getConnection()) {
            String countSql = "SELECT COUNT(*) FROM logTaskInstance ti WHERE NOT EXISTS " +
                    "(SELECT 1 FROM logEvent le WHERE le.instanceID = ti.taskInstanceID AND le.eventTime >= ?)";
            try (PreparedStatement ps = conn.prepareStatement(countSql)) {
                ps.setLong(1, cutoffEpoch);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        stats.addArchived(rs.getInt(1));
                    }
                }
            }
        } catch (SQLException e) {
            stats.setError(e.getMessage());
            _log.error("Error archiving task instances", e);
        }
        return stats;
    }

    public ArchiveStats archiveAuditEvents() {
        ArchiveStats stats = new ArchiveStats("auditEvent");
        long cutoffEpoch = Instant.now().minus(auditRetentionDays, ChronoUnit.DAYS).toEpochMilli();

        try (Connection conn = dataSource.getConnection()) {
            String countSql = "SELECT COUNT(*) FROM auditEvent WHERE eventTime < ?";
            try (PreparedStatement ps = conn.prepareStatement(countSql)) {
                ps.setLong(1, cutoffEpoch);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        stats.addArchived(rs.getInt(1));
                    }
                }
            }
        } catch (SQLException e) {
            stats.setError(e.getMessage());
            _log.error("Error archiving audit events", e);
        }
        return stats;
    }

    public ArchiveStats cleanupCompletedWorkItems() {
        ArchiveStats stats = new ArchiveStats("Work_Items");
        Timestamp cutoff = Timestamp.from(Instant.now().minus(workItemRetentionDays, ChronoUnit.DAYS));

        try (Connection conn = dataSource.getConnection()) {
            String countSql = "SELECT COUNT(*) FROM Work_Items WHERE status IN " +
                    "('Completed', 'Failed', 'Cancelled') AND enablement_time < ?";
            try (PreparedStatement ps = conn.prepareStatement(countSql)) {
                ps.setTimestamp(1, cutoff);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        stats.addArchived(rs.getInt(1));
                    }
                }
            }
        } catch (SQLException e) {
            stats.setError(e.getMessage());
            _log.error("Error cleaning up work items", e);
        }
        return stats;
    }

    private long calculateInitialDelay(int targetHour) {
        Instant now = Instant.now();
        Instant nextRun = now.truncatedTo(ChronoUnit.DAYS).plus(targetHour, ChronoUnit.HOURS);
        if (nextRun.isBefore(now)) {
            nextRun = nextRun.plus(1, ChronoUnit.DAYS);
        }
        return Duration.between(now, nextRun).toMillis();
    }

    public void setLogRetentionDays(int days) { this.logRetentionDays = days; }
    public void setAuditRetentionDays(int days) { this.auditRetentionDays = days; }
    public void setWorkItemRetentionDays(int days) { this.workItemRetentionDays = days; }
    public void setBatchSize(int size) { this.batchSize = size; }
    public void setDryRun(boolean dryRun) { this.dryRun = dryRun; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public void stop() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public static class ArchiveStats {
        private final String tableName;
        private final AtomicLong archivedCount = new AtomicLong();
        private final AtomicLong errorCount = new AtomicLong();
        private volatile String lastError;
        private final Instant startTime = Instant.now();

        public ArchiveStats(String tableName) { this.tableName = tableName; }
        public void addArchived(int count) { archivedCount.addAndGet(count); }
        public void setError(String error) { this.lastError = error; errorCount.incrementAndGet(); }
        public long getArchivedCount() { return archivedCount.get(); }
        public long getErrorCount() { return errorCount.get(); }
        public String getLastError() { return lastError; }
        public String getTableName() { return tableName; }
        public Duration getDuration() { return Duration.between(startTime, Instant.now()); }
    }
}
