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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Automated backup manager for YAWL database.
 *
 * @author YAWL Foundation
 * @version 5.2
 * @since 2026-02-18
 */
public class DatabaseBackupManager {

    private static final Logger _log = LogManager.getLogger(DatabaseBackupManager.class);
    private static final int DAILY_RETENTION_DAYS = 7;
    private static final int WEEKLY_RETENTION_WEEKS = 4;
    private static final int MONTHLY_RETENTION_MONTHS = 12;

    private final DataSource dataSource;
    private final Properties dbProperties;
    private final Path backupDirectory;
    private final ScheduledExecutorService scheduler;
    private final Map<String, BackupRecord> backupHistory = new ConcurrentHashMap<>();

    private volatile boolean enabled = true;
    private volatile int dailyRetentionDays = DAILY_RETENTION_DAYS;
    private volatile int weeklyRetentionWeeks = WEEKLY_RETENTION_WEEKS;
    private volatile int monthlyRetentionMonths = MONTHLY_RETENTION_MONTHS;

    public DatabaseBackupManager(DataSource dataSource, Properties dbProperties, String backupPath) {
        this.dataSource = dataSource;
        this.dbProperties = dbProperties;
        this.backupDirectory = Paths.get(backupPath);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "db-backup-manager");
            t.setDaemon(true);
            return t;
        });

        try {
            Files.createDirectories(backupDirectory);
        } catch (IOException e) {
            _log.error("Failed to create backup directory: {}", backupDirectory, e);
        }
    }

    public void scheduleDailyBackup(int hourOfDay) {
        long initialDelay = calculateInitialDelay(hourOfDay);
        scheduler.scheduleAtFixedRate(
                () -> createBackup("daily"),
                initialDelay,
                TimeUnit.HOURS.toMillis(24),
                TimeUnit.MILLISECONDS
        );
        _log.info("Daily backup scheduled at hour {}", hourOfDay);
    }

    public void scheduleWeeklyBackup(int dayOfWeek, int hourOfDay) {
        scheduler.scheduleAtFixedRate(
                () -> {
                    int today = LocalDateTime.now().getDayOfWeek().getValue();
                    if (today == dayOfWeek) {
                        createBackup("weekly");
                    }
                },
                calculateInitialDelay(hourOfDay),
                TimeUnit.HOURS.toMillis(24),
                TimeUnit.MILLISECONDS
        );
        _log.info("Weekly backup scheduled on day {} at hour {}", dayOfWeek, hourOfDay);
    }

    public BackupRecord createBackup(String backupType) {
        if (!enabled) {
            _log.info("Backup is disabled");
            return null;
        }

        _log.info("Starting {} backup", backupType);
        Instant startTime = Instant.now();

        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now());
        String backupFileName = String.format("yawl_backup_%s_%s.sql", backupType, timestamp);
        Path backupPath = backupDirectory.resolve(backupFileName);

        BackupRecord record = new BackupRecord();
        record.setBackupType(backupType);
        record.setFileName(backupFileName);
        record.setPath(backupPath.toString());
        record.setStartTime(startTime);

        try {
            String dbType = detectDatabaseType();
            boolean success = switch (dbType) {
                case "PostgreSQL" -> executePostgresBackup(backupPath);
                case "MySQL" -> executeMySQLBackup(backupPath);
                case "H2" -> executeH2Backup(backupPath);
                default -> {
                    _log.error("Unsupported database type: {}", dbType);
                    yield false;
                }
            };

            if (success) {
                record.setSuccessful(true);
                record.setEndTime(Instant.now());
                record.setSizeBytes(Files.size(backupPath));
                record.setChecksum(calculateChecksum(backupPath));
                backupHistory.put(backupFileName, record);
                enforceRetentionPolicy();

                Duration duration = Duration.between(startTime, Instant.now());
                _log.info("Backup completed: {} ({} seconds, {} bytes)",
                        backupFileName, duration.getSeconds(), record.getSizeBytes());
            }

        } catch (Exception e) {
            record.setSuccessful(false);
            record.setError(e.getMessage());
            record.setEndTime(Instant.now());
            _log.error("Backup failed", e);
        }

        return record;
    }

    private boolean executePostgresBackup(Path backupPath) {
        ProcessBuilder pb = new ProcessBuilder("pg_dump", "-f", backupPath.toString());
        return executeProcess(pb, "PostgreSQL backup");
    }

    private boolean executeMySQLBackup(Path backupPath) {
        ProcessBuilder pb = new ProcessBuilder("mysqldump", "--result-file=" + backupPath);
        return executeProcess(pb, "MySQL backup");
    }

    private boolean executeH2Backup(Path backupPath) {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute(String.format("SCRIPT TO '%s'", backupPath));
            return true;
        } catch (Exception e) {
            _log.error("H2 backup failed", e);
            return false;
        }
    }

    private boolean executeProcess(ProcessBuilder pb, String operation) {
        try {
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        _log.error("{} error: {}", operation, line);
                    }
                }
                return false;
            }
            return true;
        } catch (IOException | InterruptedException e) {
            _log.error("{} failed", operation, e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    private String calculateChecksum(Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] fileBytes = Files.readAllBytes(path);
        byte[] hash = md.digest(fileBytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String detectDatabaseType() {
        String url = dbProperties.getProperty("hibernate.connection.url", "");
        if (url.contains("postgresql")) return "PostgreSQL";
        if (url.contains("mysql")) return "MySQL";
        if (url.contains("h2")) return "H2";
        return "Unknown";
    }

    private void enforceRetentionPolicy() {
        Instant now = Instant.now();
        Instant dailyCutoff = now.minus(dailyRetentionDays, TimeUnit.DAYS.toChronoUnit());
        Instant weeklyCutoff = now.minus(weeklyRetentionWeeks * 7L, TimeUnit.DAYS.toChronoUnit());

        backupHistory.entrySet().removeIf(entry -> {
            BackupRecord record = entry.getValue();
            if (record.getStartTime() == null) return false;

            Instant backupTime = record.getStartTime();
            boolean shouldDelete = switch (record.getBackupType()) {
                case "daily" -> backupTime.isBefore(dailyCutoff);
                case "weekly" -> backupTime.isBefore(weeklyCutoff);
                default -> false;
            };

            if (shouldDelete) {
                try {
                    Files.deleteIfExists(Paths.get(record.getPath()));
                    _log.info("Deleted old backup: {}", entry.getKey());
                } catch (IOException e) {
                    _log.warn("Failed to delete old backup: {}", entry.getKey(), e);
                }
            }
            return shouldDelete;
        });
    }

    private long calculateInitialDelay(int targetHour) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = now.withHour(targetHour).withMinute(0).withSecond(0);
        if (nextRun.isBefore(now)) {
            nextRun = nextRun.plusDays(1);
        }
        return Duration.between(now, nextRun).toMillis();
    }

    public List<BackupRecord> listBackups() { return new ArrayList<>(backupHistory.values()); }
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

    public static class BackupRecord {
        private String backupType;
        private String fileName;
        private String path;
        private Instant startTime;
        private Instant endTime;
        private long sizeBytes;
        private String checksum;
        private boolean successful;
        private String error;

        public String getBackupType() { return backupType; }
        public void setBackupType(String backupType) { this.backupType = backupType; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public Instant getStartTime() { return startTime; }
        public void setStartTime(Instant startTime) { this.startTime = startTime; }
        public Instant getEndTime() { return endTime; }
        public void setEndTime(Instant endTime) { this.endTime = endTime; }
        public long getSizeBytes() { return sizeBytes; }
        public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
        public String getChecksum() { return checksum; }
        public void setChecksum(String checksum) { this.checksum = checksum; }
        public boolean isSuccessful() { return successful; }
        public void setSuccessful(boolean successful) { this.successful = successful; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }
}
