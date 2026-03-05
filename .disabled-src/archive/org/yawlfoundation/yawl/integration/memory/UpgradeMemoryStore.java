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

package org.yawlfoundation.yawl.integration.memory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Thread-safe storage and retrieval of upgrade history with JSON persistence.
 *
 * <p>This store maintains a complete history of all upgrade attempts, including
 * phases, outcomes, error messages, and agent assignments. Data is persisted
 * to JSON files in the docs/v6/latest/memory/ directory.</p>
 *
 * <h2>Storage Schema</h2>
 * <p>The main storage file ({@code upgrade_history.json}) contains:</p>
 * <ul>
 *   <li>Metadata (version, lastUpdated, totalRecords)</li>
 *   <li>Records array with individual upgrade attempts</li>
 *   <li>Summary statistics (success rate, average duration)</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>Uses {@link ReentrantReadWriteLock} for file operations and
 * {@link ConcurrentHashMap} for in-memory lookups.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class UpgradeMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(UpgradeMemoryStore.class);
    private static final String HISTORY_FILE = "upgrade_history.json";
    private static final String SCHEMA_VERSION = "1.0";

    private final Path memoryDirectory;
    private final ObjectMapper objectMapper;
    private final ReentrantReadWriteLock fileLock;
    private final ConcurrentHashMap<String, UpgradeRecord> recordsById;
    private final ExecutorService fileWriteExecutor;
    private final AtomicBoolean pendingSave;
    private volatile MemoryStoreMetadata metadata;

    /**
     * Sealed class hierarchy for upgrade outcomes.
     * Enables exhaustive pattern matching in switch expressions.
     *
     * <p>Uses Jackson polymorphic type handling with @type property.</p>
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = Success.class, name = "success"),
        @JsonSubTypes.Type(value = Failure.class, name = "failure"),
        @JsonSubTypes.Type(value = Partial.class, name = "partial"),
        @JsonSubTypes.Type(value = InProgress.class, name = "inProgress")
    })
    public sealed interface UpgradeOutcome permits
            UpgradeMemoryStore.Success,
            UpgradeMemoryStore.Failure,
            UpgradeMemoryStore.Partial,
            UpgradeMemoryStore.InProgress {

        /**
         * Returns true if the outcome represents a terminal success state.
         */
        boolean isSuccessful();

        /**
         * Returns a human-readable description of the outcome.
         */
        String description();
    }

    /** Successful completion of an upgrade phase or entire upgrade. */
    @JsonTypeName("success")
    public static final class Success implements UpgradeOutcome {
        private final String message;

        @JsonCreator
        public Success(@JsonProperty("message") String message) {
            this.message = Objects.requireNonNullElse(message, "");
        }

        @Override
        @JsonIgnore
        public boolean isSuccessful() {
            return true;
        }

        @Override
        public String description() {
            return "SUCCESS: " + message;
        }

        @JsonProperty("message")
        public String message() {
            return message;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Success success = (Success) o;
            return Objects.equals(message, success.message);
        }

        @Override
        public int hashCode() {
            return Objects.hash(message);
        }
    }

    /** Failed upgrade with error details. */
    @JsonTypeName("failure")
    public static final class Failure implements UpgradeOutcome {
        private final String errorMessage;
        private final String errorType;
        private final String stackTrace;

        @JsonCreator
        public Failure(
                @JsonProperty("errorMessage") String errorMessage,
                @JsonProperty("errorType") String errorType,
                @JsonProperty("stackTrace") String stackTrace) {
            this.errorMessage = Objects.requireNonNullElse(errorMessage, "Unknown error");
            this.errorType = Objects.requireNonNullElse(errorType, "Unknown");
            this.stackTrace = Objects.requireNonNullElse(stackTrace, "");
        }

        public static Failure fromException(Exception e) {
            String stackTrace = List.of(e.getStackTrace()).stream()
                    .map(StackTraceElement::toString)
                    .collect(Collectors.joining("\n"));
            return new Failure(e.getMessage(), e.getClass().getSimpleName(), stackTrace);
        }

        @Override
        @JsonIgnore
        public boolean isSuccessful() {
            return false;
        }

        @Override
        public String description() {
            return "FAILURE [" + errorType + "]: " + errorMessage;
        }

        @JsonProperty("errorMessage")
        public String errorMessage() {
            return errorMessage;
        }

        @JsonProperty("errorType")
        public String errorType() {
            return errorType;
        }

        @JsonProperty("stackTrace")
        public String stackTrace() {
            return stackTrace;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Failure failure = (Failure) o;
            return Objects.equals(errorMessage, failure.errorMessage)
                    && Objects.equals(errorType, failure.errorType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(errorMessage, errorType);
        }
    }

    /** Partially completed upgrade with some phases succeeding. */
    @JsonTypeName("partial")
    public static final class Partial implements UpgradeOutcome {
        private final int completedPhases;
        private final int totalPhases;
        private final String lastCompletedPhase;

        @JsonCreator
        public Partial(
                @JsonProperty("completedPhases") int completedPhases,
                @JsonProperty("totalPhases") int totalPhases,
                @JsonProperty("lastCompletedPhase") String lastCompletedPhase) {
            this.completedPhases = completedPhases;
            this.totalPhases = totalPhases;
            this.lastCompletedPhase = Objects.requireNonNullElse(lastCompletedPhase, "unknown");
        }

        @Override
        @JsonIgnore
        public boolean isSuccessful() {
            return false;
        }

        @Override
        public String description() {
            return "PARTIAL: " + completedPhases + "/" + totalPhases + " phases completed (last: " + lastCompletedPhase + ")";
        }

        @JsonProperty("completedPhases")
        public int completedPhases() {
            return completedPhases;
        }

        @JsonProperty("totalPhases")
        public int totalPhases() {
            return totalPhases;
        }

        @JsonProperty("lastCompletedPhase")
        public String lastCompletedPhase() {
            return lastCompletedPhase;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Partial partial = (Partial) o;
            return completedPhases == partial.completedPhases
                    && totalPhases == partial.totalPhases
                    && Objects.equals(lastCompletedPhase, partial.lastCompletedPhase);
        }

        @Override
        public int hashCode() {
            return Objects.hash(completedPhases, totalPhases, lastCompletedPhase);
        }
    }

    /** Upgrade currently in progress. */
    @JsonTypeName("inProgress")
    public static final class InProgress implements UpgradeOutcome {
        private final String currentPhase;
        private final double progressPercent;

        @JsonCreator
        public InProgress(
                @JsonProperty("currentPhase") String currentPhase,
                @JsonProperty("progressPercent") double progressPercent) {
            this.currentPhase = Objects.requireNonNullElse(currentPhase, "unknown");
            this.progressPercent = Math.max(0.0, Math.min(100.0, progressPercent));
        }

        @Override
        @JsonIgnore
        public boolean isSuccessful() {
            return false;
        }

        @Override
        public String description() {
            return String.format("IN_PROGRESS: %s (%.1f%%)", currentPhase, progressPercent);
        }

        @JsonProperty("currentPhase")
        public String currentPhase() {
            return currentPhase;
        }

        @JsonProperty("progressPercent")
        public double progressPercent() {
            return progressPercent;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            InProgress that = (InProgress) o;
            return Double.compare(that.progressPercent, progressPercent) == 0
                    && Objects.equals(currentPhase, that.currentPhase);
        }

        @Override
        public int hashCode() {
            return Objects.hash(currentPhase, progressPercent);
        }
    }

    /**
     * Immutable record representing a single upgrade attempt.
     *
     * @param id unique identifier for this record
     * @param sessionId session ID from the upgrade orchestration
     * @param targetVersion the version being upgraded to
     * @param sourceVersion the version being upgraded from
     * @param phases list of phase results during this upgrade
     * @param agents map of agent ID to assigned task
     * @param startTime when the upgrade started
     * @param endTime when the upgrade completed (null if in progress)
     * @param outcome the final outcome of this upgrade
     * @param metadata additional key-value metadata
     */
    public record UpgradeRecord(
            String id,
            String sessionId,
            String targetVersion,
            String sourceVersion,
            List<PhaseResult> phases,
            Map<String, String> agents,
            Instant startTime,
            Instant endTime,
            UpgradeOutcome outcome,
            Map<String, String> metadata
    ) {
        /**
         * Creates a new upgrade record with validation.
         */
        public UpgradeRecord {
            Objects.requireNonNull(id, "id cannot be null");
            Objects.requireNonNull(sessionId, "sessionId cannot be null");
            Objects.requireNonNull(targetVersion, "targetVersion cannot be null");
            Objects.requireNonNull(sourceVersion, "sourceVersion cannot be null");
            Objects.requireNonNull(phases, "phases cannot be null");
            Objects.requireNonNull(agents, "agents cannot be null");
            Objects.requireNonNull(startTime, "startTime cannot be null");
            phases = List.copyOf(phases);
            agents = Map.copyOf(agents);
            metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        }

        /**
         * Creates a builder for constructing upgrade records incrementally.
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Returns the total duration of the upgrade in milliseconds.
         * Returns -1 if the upgrade is still in progress.
         */
        public long durationMillis() {
            if (endTime == null) {
                return -1;
            }
            return endTime.toEpochMilli() - startTime.toEpochMilli();
        }

        /**
         * Returns true if this upgrade completed successfully.
         */
        public boolean isSuccessful() {
            return outcome != null && outcome.isSuccessful();
        }

        /**
         * Builder for constructing upgrade records incrementally.
         */
        public static final class Builder {
            private String id = UUID.randomUUID().toString();
            private String sessionId;
            private String targetVersion;
            private String sourceVersion;
            private final List<PhaseResult> phases = new ArrayList<>();
            private final Map<String, String> agents = new ConcurrentHashMap<>();
            private Instant startTime = Instant.now();
            private Instant endTime;
            private UpgradeOutcome outcome;
            private final Map<String, String> metadata = new ConcurrentHashMap<>();

            public Builder id(String id) {
                this.id = Objects.requireNonNull(id);
                return this;
            }

            public Builder sessionId(String sessionId) {
                this.sessionId = Objects.requireNonNull(sessionId);
                return this;
            }

            public Builder targetVersion(String targetVersion) {
                this.targetVersion = Objects.requireNonNull(targetVersion);
                return this;
            }

            public Builder sourceVersion(String sourceVersion) {
                this.sourceVersion = Objects.requireNonNull(sourceVersion);
                return this;
            }

            public Builder addPhase(PhaseResult phase) {
                this.phases.add(Objects.requireNonNull(phase));
                return this;
            }

            public Builder assignAgent(String agentId, String task) {
                this.agents.put(Objects.requireNonNull(agentId), Objects.requireNonNull(task));
                return this;
            }

            public Builder startTime(Instant startTime) {
                this.startTime = Objects.requireNonNull(startTime);
                return this;
            }

            public Builder endTime(Instant endTime) {
                this.endTime = Objects.requireNonNull(endTime);
                return this;
            }

            public Builder outcome(UpgradeOutcome outcome) {
                this.outcome = Objects.requireNonNull(outcome);
                return this;
            }

            public Builder addMetadata(String key, String value) {
                this.metadata.put(Objects.requireNonNull(key), Objects.requireNonNull(value));
                return this;
            }

            public UpgradeRecord build() {
                return new UpgradeRecord(
                        id,
                        sessionId != null ? sessionId : UUID.randomUUID().toString(),
                        targetVersion != null ? targetVersion : "unknown",
                        sourceVersion != null ? sourceVersion : "unknown",
                        phases,
                        agents,
                        startTime,
                        endTime,
                        outcome != null ? outcome : new InProgress("initialization", 0.0),
                        metadata
                );
            }
        }
    }

    /**
     * Record representing the result of a single upgrade phase.
     *
     * @param phaseName name of the phase (e.g., "compile", "test", "validate")
     * @param startTime when the phase started
     * @param endTime when the phase completed
     * @param outcome outcome of this phase
     * @param output captured output from the phase execution
     */
    public record PhaseResult(
            String phaseName,
            Instant startTime,
            Instant endTime,
            UpgradeOutcome outcome,
            String output
    ) {
        public PhaseResult {
            Objects.requireNonNull(phaseName, "phaseName cannot be null");
            Objects.requireNonNull(startTime, "startTime cannot be null");
            phaseName = phaseName.trim();
            output = output != null ? output : "";
        }

        /**
         * Returns the duration of this phase in milliseconds.
         * Returns -1 if the phase is still in progress.
         */
        public long durationMillis() {
            if (endTime == null) {
                return -1;
            }
            return endTime.toEpochMilli() - startTime.toEpochMilli();
        }

        /**
         * Returns true if this phase completed successfully.
         */
        public boolean isSuccessful() {
            return outcome != null && outcome.isSuccessful();
        }
    }

    /**
     * Metadata about the memory store.
     */
    public record MemoryStoreMetadata(
            String schemaVersion,
            Instant createdAt,
            Instant lastUpdated,
            int totalRecords
    ) {
        public MemoryStoreMetadata {
            Objects.requireNonNull(schemaVersion, "schemaVersion cannot be null");
            Objects.requireNonNull(createdAt, "createdAt cannot be null");
            Objects.requireNonNull(lastUpdated, "lastUpdated cannot be null");
        }

        public static MemoryStoreMetadata initial() {
            Instant now = Instant.now();
            return new MemoryStoreMetadata(SCHEMA_VERSION, now, now, 0);
        }

        public MemoryStoreMetadata withUpdated(int newTotal) {
            return new MemoryStoreMetadata(schemaVersion, createdAt, Instant.now(), newTotal);
        }
    }

    /**
     * Root container for JSON serialization.
     */
    public record MemoryStoreData(
            MemoryStoreMetadata metadata,
            List<UpgradeRecord> records
    ) {
        public MemoryStoreData {
            Objects.requireNonNull(metadata, "metadata cannot be null");
            Objects.requireNonNull(records, "records cannot be null");
            records = List.copyOf(records);
        }

        public static MemoryStoreData empty() {
            return new MemoryStoreData(MemoryStoreMetadata.initial(), List.of());
        }
    }

    /**
     * Creates a new UpgradeMemoryStore with the specified memory directory.
     *
     * @param memoryDirectory path to the memory storage directory
     * @throws MemoryStoreException if the directory cannot be created or accessed
     */
    public UpgradeMemoryStore(Path memoryDirectory) {
        this.memoryDirectory = Objects.requireNonNull(memoryDirectory, "memoryDirectory cannot be null");
        this.objectMapper = createObjectMapper();
        this.fileLock = new ReentrantReadWriteLock();
        this.recordsById = new ConcurrentHashMap<>();
        this.fileWriteExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "memory-store-writer");
            t.setDaemon(true);
            return t;
        });
        this.pendingSave = new AtomicBoolean(false);
        this.metadata = MemoryStoreMetadata.initial();

        initializeStore();
    }

    /**
     * Creates and configures the ObjectMapper for JSON serialization.
     * Configures polymorphic type handling for sealed interface hierarchy.
     */
    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT)
                // Don't fail on unknown properties for forward compatibility
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        // Register subtypes for polymorphic UpgradeOutcome deserialization
        // This allows Jackson to serialize/deserialize the sealed interface
        mapper.registerSubtypes(
                new NamedType(Success.class, "success"),
                new NamedType(Failure.class, "failure"),
                new NamedType(Partial.class, "partial"),
                new NamedType(InProgress.class, "inProgress")
        );

        return mapper;
    }

    /**
     * Creates a new UpgradeMemoryStore using the default memory directory.
     *
     * @return a new UpgradeMemoryStore instance
     */
    public static UpgradeMemoryStore createDefault() {
        Path defaultPath = Path.of("docs/v6/latest/memory");
        return new UpgradeMemoryStore(defaultPath);
    }

    private void initializeStore() {
        try {
            Files.createDirectories(memoryDirectory);

            Path historyPath = memoryDirectory.resolve(HISTORY_FILE);
            if (Files.exists(historyPath)) {
                loadFromDisk(historyPath);
            } else {
                saveToDisk();
            }
        } catch (IOException e) {
            throw new MemoryStoreException("Failed to initialize memory store at " + memoryDirectory, e);
        }
    }

    private void loadFromDisk(Path historyPath) throws IOException {
        fileLock.writeLock().lock();
        try {
            String content = Files.readString(historyPath, StandardCharsets.UTF_8);
            MemoryStoreData data = objectMapper.readValue(content, MemoryStoreData.class);

            this.metadata = data.metadata();
            this.recordsById.clear();
            for (UpgradeRecord record : data.records()) {
                recordsById.put(record.id(), record);
            }

            log.info("Loaded {} upgrade records from {}", recordsById.size(), historyPath);
        } finally {
            fileLock.writeLock().unlock();
        }
    }

    private void saveToDisk() throws IOException {
        fileLock.readLock().lock();
        try {
            Path historyPath = memoryDirectory.resolve(HISTORY_FILE);
            Path tempPath = memoryDirectory.resolve(HISTORY_FILE + ".tmp");

            List<UpgradeRecord> records = new ArrayList<>(recordsById.values());
            MemoryStoreData data = new MemoryStoreData(metadata, records);
            String json = objectMapper.writeValueAsString(data);

            Files.writeString(tempPath, json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.SYNC);

            Files.move(tempPath, historyPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

            pendingSave.set(false);
            log.debug("Saved {} upgrade records to {}", records.size(), historyPath);
        } finally {
            fileLock.readLock().unlock();
        }
    }

    /**
     * Schedules an asynchronous save to disk. Multiple concurrent calls are
     * coalesced into a single save operation to reduce I/O contention.
     *
     * <p>This method is designed for high-concurrency scenarios where immediate
     * persistence is not critical.</p>
     */
    private void scheduleAsyncSave() {
        if (pendingSave.compareAndSet(false, true)) {
            CompletableFuture.runAsync(() -> {
                try {
                    saveToDisk();
                } catch (IOException e) {
                    log.warn("Async save failed: {}", e.getMessage());
                    pendingSave.set(false);
                }
            }, fileWriteExecutor);
        }
    }

    /**
     * Forces an immediate synchronous save to disk.
     * Use sparingly - prefer scheduleAsyncSave() for most operations.
     *
     * @throws IOException if the save fails
     */
    private void forceSave() throws IOException {
        saveToDisk();
    }

    /**
     * Stores a new upgrade record.
     *
     * @param record the upgrade record to store
     * @throws MemoryStoreException if the record cannot be stored
     */
    public void store(UpgradeRecord record) {
        Objects.requireNonNull(record, "record cannot be null");

        recordsById.put(record.id(), record);
        // Atomic read of actual size to prevent race conditions
        // ConcurrentHashMap.size() is eventually consistent but safe for metrics
        int currentSize = recordsById.size();
        metadata = metadata.withUpdated(currentSize);

        // Use async save to reduce lock contention under high concurrency
        scheduleAsyncSave();
        log.info("Stored upgrade record: id={}, outcome={}, totalRecords={}",
                record.id(), record.outcome().description(), currentSize);
    }

    /**
     * Updates an existing upgrade record.
     * Uses replace() for atomic check-and-set to prevent race conditions.
     *
     * @param record the upgrade record with updated data
     * @throws MemoryStoreException if the record does not exist or cannot be updated
     */
    public void update(UpgradeRecord record) {
        Objects.requireNonNull(record, "record cannot be null");

        // Use atomic replace to ensure record exists and update is atomic
        // Returns null if key doesn't exist, preventing TOCTOU race condition
        UpgradeRecord previous = recordsById.replace(record.id(), record);
        if (previous == null) {
            throw new MemoryStoreException("Record not found: " + record.id());
        }

        // Use async save to reduce lock contention under high concurrency
        scheduleAsyncSave();
        log.debug("Updated upgrade record: id={}, previousOutcome={}, newOutcome={}",
                record.id(), previous.outcome().description(), record.outcome().description());
    }

    /**
     * Retrieves an upgrade record by ID.
     *
     * @param id the unique identifier of the record
     * @return an Optional containing the record, or empty if not found
     */
    public Optional<UpgradeRecord> retrieve(String id) {
        Objects.requireNonNull(id, "id cannot be null");
        return Optional.ofNullable(recordsById.get(id));
    }

    /**
     * Retrieves all upgrade records for a specific session.
     *
     * @param sessionId the session identifier
     * @return list of records for the session, may be empty
     */
    public List<UpgradeRecord> retrieveBySession(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId cannot be null");
        return recordsById.values().stream()
                .filter(r -> sessionId.equals(r.sessionId()))
                .collect(Collectors.toList());
    }

    /**
     * Retrieves all records matching the specified outcome type.
     *
     * @param outcomeClass the class of outcome to filter by
     * @param <T> the outcome type
     * @return list of records with matching outcome type
     */
    public <T extends UpgradeOutcome> List<UpgradeRecord> retrieveByOutcome(Class<T> outcomeClass) {
        Objects.requireNonNull(outcomeClass, "outcomeClass cannot be null");
        return recordsById.values().stream()
                .filter(r -> outcomeClass.isInstance(r.outcome()))
                .collect(Collectors.toList());
    }

    /**
     * Retrieves all records within a time range.
     *
     * @param start inclusive start time
     * @param end exclusive end time
     * @return list of records within the time range
     */
    public List<UpgradeRecord> retrieveByTimeRange(Instant start, Instant end) {
        Objects.requireNonNull(start, "start cannot be null");
        Objects.requireNonNull(end, "end cannot be null");

        return recordsById.values().stream()
                .filter(r -> !r.startTime().isBefore(start) && r.startTime().isBefore(end))
                .collect(Collectors.toList());
    }

    /**
     * Retrieves all upgrade records.
     *
     * @return unmodifiable list of all records
     */
    public List<UpgradeRecord> retrieveAll() {
        return Collections.unmodifiableList(new ArrayList<>(recordsById.values()));
    }

    /**
     * Returns statistics about the stored records.
     *
     * @return a RecordStats instance with summary statistics
     */
    public RecordStats getStatistics() {
        List<UpgradeRecord> records = new ArrayList<>(recordsById.values());

        int total = records.size();
        int successful = (int) records.stream().filter(UpgradeRecord::isSuccessful).count();
        int failed = (int) records.stream()
                .filter(r -> r.outcome() instanceof Failure)
                .count();
        int inProgress = (int) records.stream()
                .filter(r -> r.outcome() instanceof InProgress)
                .count();

        double avgDuration = records.stream()
                .filter(r -> r.durationMillis() > 0)
                .mapToLong(UpgradeRecord::durationMillis)
                .average()
                .orElse(0.0);

        return new RecordStats(total, successful, failed, inProgress, avgDuration);
    }

    /**
     * Statistics about stored records.
     *
     * @param total total number of records
     * @param successful number of successful upgrades
     * @param failed number of failed upgrades
     * @param inProgress number of in-progress upgrades
     * @param averageDurationMillis average duration in milliseconds
     */
    public record RecordStats(
            int total,
            int successful,
            int failed,
            int inProgress,
            double averageDurationMillis
    ) {
        /**
         * Returns the success rate as a percentage (0-100).
         */
        public double successRate() {
            if (total == 0) return 0.0;
            return (successful * 100.0) / total;
        }
    }

    /**
     * Deletes a record by ID.
     *
     * @param id the unique identifier of the record to delete
     * @return true if the record was deleted, false if it did not exist
     * @throws MemoryStoreException if the deletion cannot be persisted
     */
    public boolean delete(String id) {
        Objects.requireNonNull(id, "id cannot be null");

        UpgradeRecord removed = recordsById.remove(id);
        if (removed == null) {
            return false;
        }

        metadata = metadata.withUpdated(recordsById.size());

        try {
            saveToDisk();
            log.info("Deleted upgrade record: id={}", id);
            return true;
        } catch (IOException e) {
            // Rollback the removal
            recordsById.put(id, removed);
            throw new MemoryStoreException("Failed to delete upgrade record: " + id, e);
        }
    }

    /**
     * Clears all records from the store.
     *
     * @throws MemoryStoreException if the clear operation cannot be persisted
     */
    public void clear() {
        recordsById.clear();
        metadata = MemoryStoreMetadata.initial();

        try {
            forceSave();
            log.info("Cleared all upgrade records");
        } catch (IOException e) {
            throw new MemoryStoreException("Failed to clear upgrade records", e);
        }
    }

    /**
     * Flushes any pending saves to disk synchronously.
     * Call this before reading from disk to ensure all writes are persisted.
     *
     * @throws MemoryStoreException if the flush fails
     */
    public void flush() {
        try {
            if (pendingSave.get()) {
                forceSave();
            }
        } catch (IOException e) {
            throw new MemoryStoreException("Failed to flush pending saves", e);
        }
    }

    /**
     * Shuts down the memory store, flushing any pending saves and releasing resources.
     * After calling this method, the store should not be used.
     *
     * @param timeoutSeconds maximum time to wait for pending saves
     */
    public void shutdown(int timeoutSeconds) {
        try {
            // Flush any pending saves synchronously
            if (pendingSave.get()) {
                forceSave();
            }
        } catch (IOException e) {
            log.warn("Failed to flush pending saves during shutdown: {}", e.getMessage());
        }

        fileWriteExecutor.shutdown();
        try {
            if (!fileWriteExecutor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                fileWriteExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            fileWriteExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Memory store shut down successfully");
    }

    /**
     * Returns the path to the memory directory.
     *
     * @return the memory directory path
     */
    public Path getMemoryDirectory() {
        return memoryDirectory;
    }

    /**
     * Returns the current metadata.
     *
     * @return the store metadata
     */
    public MemoryStoreMetadata getMetadata() {
        return metadata;
    }

    /**
     * Exception thrown when memory store operations fail.
     */
    public static final class MemoryStoreException extends RuntimeException {
        public MemoryStoreException(String message) {
            super(message);
        }

        public MemoryStoreException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
