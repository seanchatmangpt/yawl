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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Captures outcomes and extracts patterns for continuous improvement of upgrades.
 *
 * <p>This component analyzes upgrade history to identify success patterns, failure
 * patterns, and agent performance metrics. Patterns are persisted to JSON files
 * and used to guide future upgrade decisions.</p>
 *
 * <h2>Pattern Types</h2>
 * <ul>
 *   <li><strong>Success Patterns</strong>: Sequences of phases that consistently succeed</li>
 *   <li><strong>Failure Patterns</strong>: Common failure modes with root causes</li>
 *   <li><strong>Agent Metrics</strong>: Per-agent success rates and timing statistics</li>
 * </ul>
 *
 * <h2>Learning Algorithm</h2>
 * <p>Patterns are extracted using frequency analysis:</p>
 * <ol>
 *   <li>Group records by outcome type</li>
 *   <li>Extract phase sequences and timing patterns</li>
 *   <li>Calculate occurrence frequency and success correlation</li>
 *   <li>Persist patterns with confidence scores</li>
 * </ol>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class LearningCapture {

    private static final Logger log = LoggerFactory.getLogger(LearningCapture.class);
    private static final String SUCCESS_PATTERNS_FILE = "success_patterns.json";
    private static final String FAILURE_PATTERNS_FILE = "failure_patterns.json";
    private static final String AGENT_METRICS_FILE = "agent_metrics.json";

    private final Path memoryDirectory;
    private final ObjectMapper objectMapper;
    private final UpgradeMemoryStore memoryStore;
    private final ConcurrentHashMap<String, SuccessPattern> successPatterns;
    private final ConcurrentHashMap<String, FailurePattern> failurePatterns;
    private final ConcurrentHashMap<String, AgentMetric> agentMetrics;

    /**
     * Record representing a success pattern extracted from upgrade history.
     *
     * @param id unique pattern identifier
     * @param name human-readable pattern name
     * @param phaseSequence ordered list of phases in the pattern
     * @param occurrenceCount how many times this pattern occurred
     * @param successRate success rate when this pattern was used (0.0-1.0)
     * @param avgDurationMillis average duration for upgrades using this pattern
     * @param lastSeen when this pattern was last observed
     * @param tags categorization tags for filtering
     */
    public record SuccessPattern(
            String id,
            String name,
            List<String> phaseSequence,
            int occurrenceCount,
            double successRate,
            long avgDurationMillis,
            Instant lastSeen,
            Set<String> tags
    ) {
        public SuccessPattern {
            Objects.requireNonNull(id, "id cannot be null");
            Objects.requireNonNull(name, "name cannot be null");
            Objects.requireNonNull(phaseSequence, "phaseSequence cannot be null");
            Objects.requireNonNull(lastSeen, "lastSeen cannot be null");
            Objects.requireNonNull(tags, "tags cannot be null");
            phaseSequence = List.copyOf(phaseSequence);
            tags = Set.copyOf(tags);
            if (occurrenceCount < 0) {
                throw new IllegalArgumentException("occurrenceCount must be non-negative");
            }
            if (successRate < 0.0 || successRate > 1.0) {
                throw new IllegalArgumentException("successRate must be between 0.0 and 1.0");
            }
        }

        /**
         * Returns a new pattern with incremented occurrence count and updated statistics.
         */
        public SuccessPattern incrementObservation(boolean wasSuccessful, long durationMillis) {
            int newCount = occurrenceCount + 1;
            int newSuccesses = (int) Math.round(occurrenceCount * successRate) + (wasSuccessful ? 1 : 0);
            double newRate = (double) newSuccesses / newCount;
            long newAvgDuration = ((avgDurationMillis * occurrenceCount) + durationMillis) / newCount;

            return new SuccessPattern(
                    id, name, phaseSequence, newCount, newRate, newAvgDuration, Instant.now(), tags
            );
        }

        /**
         * Creates a builder for constructing success patterns.
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Builder for SuccessPattern.
         */
        public static final class Builder {
            private String id = UUID.randomUUID().toString();
            private String name;
            private final List<String> phaseSequence = new ArrayList<>();
            private int occurrenceCount = 1;
            private double successRate = 1.0;
            private long avgDurationMillis = 0;
            private Instant lastSeen = Instant.now();
            private final Set<String> tags = new java.util.HashSet<>();

            public Builder id(String id) {
                this.id = Objects.requireNonNull(id);
                return this;
            }

            public Builder name(String name) {
                this.name = Objects.requireNonNull(name);
                return this;
            }

            public Builder addPhase(String phase) {
                this.phaseSequence.add(Objects.requireNonNull(phase));
                return this;
            }

            public Builder phaseSequence(List<String> phases) {
                this.phaseSequence.clear();
                this.phaseSequence.addAll(Objects.requireNonNull(phases));
                return this;
            }

            public Builder occurrenceCount(int count) {
                this.occurrenceCount = count;
                return this;
            }

            public Builder successRate(double rate) {
                this.successRate = rate;
                return this;
            }

            public Builder avgDurationMillis(long duration) {
                this.avgDurationMillis = duration;
                return this;
            }

            public Builder lastSeen(Instant lastSeen) {
                this.lastSeen = Objects.requireNonNull(lastSeen);
                return this;
            }

            public Builder addTag(String tag) {
                this.tags.add(Objects.requireNonNull(tag));
                return this;
            }

            public SuccessPattern build() {
                return new SuccessPattern(
                        id,
                        name != null ? name : "pattern-" + id.substring(0, 8),
                        phaseSequence,
                        occurrenceCount,
                        successRate,
                        avgDurationMillis,
                        lastSeen,
                        tags
                );
            }
        }
    }

    /**
     * Record representing a failure pattern extracted from upgrade history.
     *
     * @param id unique pattern identifier
     * @param name human-readable pattern name
     * @param failureType category of the failure (compile, test, validation, etc.)
     * @param errorPattern regex pattern to match similar errors
     * @param rootCause suspected root cause description
     * @param resolution suggested resolution steps
     * @param occurrenceCount how many times this failure occurred
     * @param lastSeen when this failure was last observed
     * @param affectedPhases phases where this failure typically occurs
     */
    public record FailurePattern(
            String id,
            String name,
            String failureType,
            String errorPattern,
            String rootCause,
            List<String> resolution,
            int occurrenceCount,
            Instant lastSeen,
            Set<String> affectedPhases
    ) {
        public FailurePattern {
            Objects.requireNonNull(id, "id cannot be null");
            Objects.requireNonNull(name, "name cannot be null");
            Objects.requireNonNull(failureType, "failureType cannot be null");
            Objects.requireNonNull(lastSeen, "lastSeen cannot be null");
            resolution = resolution != null ? List.copyOf(resolution) : List.of();
            affectedPhases = affectedPhases != null ? Set.copyOf(affectedPhases) : Set.of();
            if (occurrenceCount < 0) {
                throw new IllegalArgumentException("occurrenceCount must be non-negative");
            }
        }

        /**
         * Returns a new pattern with incremented occurrence count.
         */
        public FailurePattern incrementObservation() {
            return new FailurePattern(
                    id, name, failureType, errorPattern, rootCause, resolution,
                    occurrenceCount + 1, Instant.now(), affectedPhases
            );
        }

        /**
         * Checks if an error message matches this failure pattern.
         */
        public boolean matches(String errorMessage) {
            if (errorMessage == null || errorPattern == null) {
                return false;
            }
            return errorMessage.contains(errorPattern) ||
                   java.util.regex.Pattern.compile(errorPattern, java.util.regex.Pattern.CASE_INSENSITIVE)
                           .matcher(errorMessage).find();
        }

        /**
         * Creates a builder for constructing failure patterns.
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Builder for FailurePattern.
         */
        public static final class Builder {
            private String id = UUID.randomUUID().toString();
            private String name;
            private String failureType = "unknown";
            private String errorPattern;
            private String rootCause = "";
            private final List<String> resolution = new ArrayList<>();
            private int occurrenceCount = 1;
            private Instant lastSeen = Instant.now();
            private final Set<String> affectedPhases = new java.util.HashSet<>();

            public Builder id(String id) {
                this.id = Objects.requireNonNull(id);
                return this;
            }

            public Builder name(String name) {
                this.name = Objects.requireNonNull(name);
                return this;
            }

            public Builder failureType(String type) {
                this.failureType = Objects.requireNonNull(type);
                return this;
            }

            public Builder errorPattern(String pattern) {
                this.errorPattern = pattern;
                return this;
            }

            public Builder rootCause(String cause) {
                this.rootCause = Objects.requireNonNullElse(cause, "");
                return this;
            }

            public Builder addResolutionStep(String step) {
                this.resolution.add(Objects.requireNonNull(step));
                return this;
            }

            public Builder resolution(List<String> steps) {
                this.resolution.clear();
                this.resolution.addAll(Objects.requireNonNull(steps));
                return this;
            }

            public Builder occurrenceCount(int count) {
                this.occurrenceCount = count;
                return this;
            }

            public Builder lastSeen(Instant lastSeen) {
                this.lastSeen = Objects.requireNonNull(lastSeen);
                return this;
            }

            public Builder addAffectedPhase(String phase) {
                this.affectedPhases.add(Objects.requireNonNull(phase));
                return this;
            }

            public FailurePattern build() {
                return new FailurePattern(
                        id,
                        name != null ? name : "failure-" + id.substring(0, 8),
                        failureType,
                        errorPattern,
                        rootCause,
                        resolution,
                        occurrenceCount,
                        lastSeen,
                        affectedPhases
                );
            }
        }
    }

    /**
     * Record representing performance metrics for a specific agent.
     *
     * @param agentId unique agent identifier
     * @param agentType type/category of the agent
     * @param totalTasks total number of tasks assigned
     * @param successfulTasks number of successfully completed tasks
     * @param failedTasks number of failed tasks
     * @param avgTaskDurationMillis average task duration
     * @param totalDurationMillis cumulative task duration
     * @param lastActive when the agent was last active
     * @param specializations set of task types this agent handles
     */
    public record AgentMetric(
            String agentId,
            String agentType,
            int totalTasks,
            int successfulTasks,
            int failedTasks,
            long avgTaskDurationMillis,
            long totalDurationMillis,
            Instant lastActive,
            Set<String> specializations
    ) {
        public AgentMetric {
            Objects.requireNonNull(agentId, "agentId cannot be null");
            Objects.requireNonNull(agentType, "agentType cannot be null");
            Objects.requireNonNull(lastActive, "lastActive cannot be null");
            specializations = specializations != null ? Set.copyOf(specializations) : Set.of();
            if (totalTasks < 0 || successfulTasks < 0 || failedTasks < 0) {
                throw new IllegalArgumentException("task counts must be non-negative");
            }
        }

        /**
         * Returns the success rate as a percentage (0-100).
         */
        public double successRate() {
            if (totalTasks == 0) return 0.0;
            return (successfulTasks * 100.0) / totalTasks;
        }

        /**
         * Records a new task completion for this agent.
         */
        public AgentMetric recordTask(boolean successful, long durationMillis) {
            int newTotal = totalTasks + 1;
            int newSuccesses = successfulTasks + (successful ? 1 : 0);
            int newFailures = failedTasks + (successful ? 0 : 1);
            long newTotalDuration = totalDurationMillis + durationMillis;
            long newAvgDuration = newTotalDuration / newTotal;

            return new AgentMetric(
                    agentId, agentType, newTotal, newSuccesses, newFailures,
                    newAvgDuration, newTotalDuration, Instant.now(), specializations
            );
        }

        /**
         * Creates an initial metric for an agent.
         */
        public static AgentMetric initial(String agentId, String agentType) {
            return new AgentMetric(
                    agentId, agentType, 0, 0, 0, 0, 0, Instant.now(), Set.of()
            );
        }
    }

    /**
     * Container for all pattern data.
     */
    public record PatternData(
            Instant lastUpdated,
            List<SuccessPattern> successPatterns,
            List<FailurePattern> failurePatterns
    ) {
        public PatternData {
            Objects.requireNonNull(lastUpdated, "lastUpdated cannot be null");
            successPatterns = successPatterns != null ? List.copyOf(successPatterns) : List.of();
            failurePatterns = failurePatterns != null ? List.copyOf(failurePatterns) : List.of();
        }

        public static PatternData empty() {
            return new PatternData(Instant.now(), List.of(), List.of());
        }
    }

    /**
     * Container for agent metrics data.
     */
    public record AgentMetricsData(
            Instant lastUpdated,
            List<AgentMetric> metrics
    ) {
        public AgentMetricsData {
            Objects.requireNonNull(lastUpdated, "lastUpdated cannot be null");
            metrics = metrics != null ? List.copyOf(metrics) : List.of();
        }

        public static AgentMetricsData empty() {
            return new AgentMetricsData(Instant.now(), List.of());
        }
    }

    /**
     * Creates a new LearningCapture with the specified memory store.
     *
     * @param memoryStore the upgrade memory store to analyze
     */
    public LearningCapture(UpgradeMemoryStore memoryStore) {
        this.memoryStore = Objects.requireNonNull(memoryStore, "memoryStore cannot be null");
        this.memoryDirectory = memoryStore.getMemoryDirectory();
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);
        this.successPatterns = new ConcurrentHashMap<>();
        this.failurePatterns = new ConcurrentHashMap<>();
        this.agentMetrics = new ConcurrentHashMap<>();

        loadPatterns();
    }

    /**
     * Creates a new LearningCapture with the default memory store.
     *
     * @return a new LearningCapture instance
     */
    public static LearningCapture createDefault() {
        return new LearningCapture(UpgradeMemoryStore.createDefault());
    }

    private void loadPatterns() {
        loadSuccessPatterns();
        loadFailurePatterns();
        loadAgentMetrics();
    }

    private void loadSuccessPatterns() {
        try {
            Path path = memoryDirectory.resolve(SUCCESS_PATTERNS_FILE);
            if (Files.exists(path)) {
                String content = Files.readString(path, StandardCharsets.UTF_8);
                PatternData data = objectMapper.readValue(content, PatternData.class);
                for (SuccessPattern pattern : data.successPatterns()) {
                    successPatterns.put(pattern.id(), pattern);
                }
                log.info("Loaded {} success patterns", successPatterns.size());
            }
        } catch (IOException e) {
            log.warn("Could not load success patterns, starting fresh: {}", e.getMessage());
        }
    }

    private void loadFailurePatterns() {
        try {
            Path path = memoryDirectory.resolve(FAILURE_PATTERNS_FILE);
            if (Files.exists(path)) {
                String content = Files.readString(path, StandardCharsets.UTF_8);
                PatternData data = objectMapper.readValue(content, PatternData.class);
                for (FailurePattern pattern : data.failurePatterns()) {
                    failurePatterns.put(pattern.id(), pattern);
                }
                log.info("Loaded {} failure patterns", failurePatterns.size());
            }
        } catch (IOException e) {
            log.warn("Could not load failure patterns, starting fresh: {}", e.getMessage());
        }
    }

    private void loadAgentMetrics() {
        try {
            Path path = memoryDirectory.resolve(AGENT_METRICS_FILE);
            if (Files.exists(path)) {
                String content = Files.readString(path, StandardCharsets.UTF_8);
                AgentMetricsData data = objectMapper.readValue(content, AgentMetricsData.class);
                for (AgentMetric metric : data.metrics()) {
                    agentMetrics.put(metric.agentId(), metric);
                }
                log.info("Loaded {} agent metrics", agentMetrics.size());
            }
        } catch (IOException e) {
            log.warn("Could not load agent metrics, starting fresh: {}", e.getMessage());
        }
    }

    /**
     * Analyzes all records in the memory store and extracts patterns.
     * This is a comprehensive analysis that updates all pattern types.
     *
     * @return analysis results summary
     */
    public AnalysisResult analyzeAll() {
        List<UpgradeMemoryStore.UpgradeRecord> records = memoryStore.retrieveAll();

        int successPatternsExtracted = extractSuccessPatterns(records);
        int failurePatternsExtracted = extractFailurePatterns(records);
        int agentMetricsUpdated = updateAgentMetrics(records);

        persistPatterns();

        return new AnalysisResult(
                records.size(),
                successPatternsExtracted,
                failurePatternsExtracted,
                agentMetricsUpdated,
                Instant.now()
        );
    }

    /**
     * Result of an analysis operation.
     *
     * @param recordsAnalyzed number of records analyzed
     * @param successPatternsExtracted number of success patterns found
     * @param failurePatternsExtracted number of failure patterns found
     * @param agentMetricsUpdated number of agent metrics updated
     * @param analyzedAt timestamp of analysis
     */
    public record AnalysisResult(
            int recordsAnalyzed,
            int successPatternsExtracted,
            int failurePatternsExtracted,
            int agentMetricsUpdated,
            Instant analyzedAt
    ) {}

    private int extractSuccessPatterns(List<UpgradeMemoryStore.UpgradeRecord> records) {
        // Group successful records by their phase sequence
        Map<String, List<UpgradeMemoryStore.UpgradeRecord>> byPhaseSequence = records.stream()
                .filter(UpgradeMemoryStore.UpgradeRecord::isSuccessful)
                .collect(Collectors.groupingBy(this::extractPhaseSignature));

        int extracted = 0;
        for (Map.Entry<String, List<UpgradeMemoryStore.UpgradeRecord>> entry : byPhaseSequence.entrySet()) {
            String signature = entry.getKey();
            List<UpgradeMemoryStore.UpgradeRecord> matching = entry.getValue();

            if (matching.size() >= 1) {
                UpgradeMemoryStore.UpgradeRecord exemplar = matching.get(0);
                List<String> phases = exemplar.phases().stream()
                        .map(UpgradeMemoryStore.PhaseResult::phaseName)
                        .collect(Collectors.toList());

                double successRate = (double) matching.size() / records.stream()
                        .filter(r -> extractPhaseSignature(r).equals(signature))
                        .count();

                long avgDuration = (long) matching.stream()
                        .mapToLong(UpgradeMemoryStore.UpgradeRecord::durationMillis)
                        .filter(d -> d > 0)
                        .average()
                        .orElse(0.0);

                String patternId = "sp-" + signature.hashCode();
                SuccessPattern pattern = new SuccessPattern.Builder()
                        .id(patternId)
                        .name("Pattern: " + String.join(" -> ", phases))
                        .phaseSequence(phases)
                        .occurrenceCount(matching.size())
                        .successRate(successRate)
                        .avgDurationMillis(avgDuration)
                        .addTag("auto-extracted")
                        .build();

                successPatterns.merge(patternId, pattern, (existing, newP) ->
                        new SuccessPattern(
                                existing.id(),
                                existing.name(),
                                existing.phaseSequence(),
                                existing.occurrenceCount() + newP.occurrenceCount(),
                                (existing.successRate() + newP.successRate()) / 2,
                                (existing.avgDurationMillis() + newP.avgDurationMillis()) / 2,
                                Instant.now(),
                                existing.tags()
                        ));
                extracted++;
            }
        }

        log.info("Extracted {} success patterns", extracted);
        return extracted;
    }

    private int extractFailurePatterns(List<UpgradeMemoryStore.UpgradeRecord> records) {
        // Group failed records by failure type and error message patterns
        List<UpgradeMemoryStore.UpgradeRecord> failures = records.stream()
                .filter(r -> r.outcome() instanceof UpgradeMemoryStore.Failure)
                .collect(Collectors.toList());

        int extracted = 0;
        Map<String, List<UpgradeMemoryStore.UpgradeRecord>> byErrorType = new HashMap<>();

        for (UpgradeMemoryStore.UpgradeRecord record : failures) {
            if (record.outcome() instanceof UpgradeMemoryStore.Failure failure) {
                String errorType = failure.errorType();
                byErrorType.computeIfAbsent(errorType, k -> new ArrayList<>()).add(record);
            }
        }

        for (Map.Entry<String, List<UpgradeMemoryStore.UpgradeRecord>> entry : byErrorType.entrySet()) {
            String errorType = entry.getKey();
            List<UpgradeMemoryStore.UpgradeRecord> matching = entry.getValue();

            if (matching.size() >= 1) {
                UpgradeMemoryStore.UpgradeRecord exemplar = matching.get(0);
                Set<String> affectedPhases = exemplar.phases().stream()
                        .filter(p -> !p.isSuccessful())
                        .map(UpgradeMemoryStore.PhaseResult::phaseName)
                        .collect(Collectors.toSet());

                String failureMessage = "";
                if (exemplar.outcome() instanceof UpgradeMemoryStore.Failure f) {
                    failureMessage = f.errorMessage();
                }

                String patternId = "fp-" + errorType.toLowerCase().replace(" ", "-");
                String errorPattern = extractErrorPattern(failureMessage);

                FailurePattern pattern = new FailurePattern.Builder()
                        .id(patternId)
                        .name(errorType + " Failure")
                        .failureType(errorType)
                        .errorPattern(errorPattern)
                        .rootCause("Auto-detected from " + matching.size() + " occurrences")
                        .addResolutionStep("Review error logs for details")
                        .addResolutionStep("Check configuration and dependencies")
                        .occurrenceCount(matching.size())
                        .addAffectedPhase(affectedPhases.isEmpty() ? "unknown" : affectedPhases.iterator().next())
                        .build();

                failurePatterns.merge(patternId, pattern, (existing, newP) ->
                        new FailurePattern(
                                existing.id(),
                                existing.name(),
                                existing.failureType(),
                                existing.errorPattern(),
                                existing.rootCause(),
                                existing.resolution(),
                                existing.occurrenceCount() + newP.occurrenceCount(),
                                Instant.now(),
                                existing.affectedPhases()
                        ));
                extracted++;
            }
        }

        log.info("Extracted {} failure patterns", extracted);
        return extracted;
    }

    private int updateAgentMetrics(List<UpgradeMemoryStore.UpgradeRecord> records) {
        int updated = 0;

        for (UpgradeMemoryStore.UpgradeRecord record : records) {
            for (Map.Entry<String, String> agent : record.agents().entrySet()) {
                String agentId = agent.getKey();
                String task = agent.getValue();

                AgentMetric existing = agentMetrics.getOrDefault(
                        agentId, AgentMetric.initial(agentId, "unknown"));

                boolean successful = record.isSuccessful();
                long duration = record.durationMillis();

                agentMetrics.put(agentId, existing.recordTask(successful, duration > 0 ? duration : 0));
                updated++;
            }
        }

        log.info("Updated {} agent metric entries", updated);
        return updated;
    }

    private String extractPhaseSignature(UpgradeMemoryStore.UpgradeRecord record) {
        return record.phases().stream()
                .map(UpgradeMemoryStore.PhaseResult::phaseName)
                .collect(Collectors.joining("|"));
    }

    private String extractErrorPattern(String errorMessage) {
        if (errorMessage == null || errorMessage.isEmpty()) {
            return ".*";
        }
        // Extract key terms from error message
        String[] words = errorMessage.split("\\s+");
        StringBuilder pattern = new StringBuilder();
        for (int i = 0; i < Math.min(5, words.length); i++) {
            String word = words[i].replaceAll("[^a-zA-Z0-9]", "");
            if (word.length() > 3) {
                if (pattern.length() > 0) pattern.append(".*");
                pattern.append(word);
            }
        }
        return pattern.length() > 0 ? pattern.toString() : ".*";
    }

    private void persistPatterns() {
        persistSuccessPatterns();
        persistFailurePatterns();
        persistAgentMetrics();
    }

    private void persistSuccessPatterns() {
        try {
            Path path = memoryDirectory.resolve(SUCCESS_PATTERNS_FILE);
            Path tempPath = memoryDirectory.resolve(SUCCESS_PATTERNS_FILE + ".tmp");

            PatternData data = new PatternData(
                    Instant.now(),
                    new ArrayList<>(successPatterns.values()),
                    List.of()
            );
            String json = objectMapper.writeValueAsString(data);

            Files.writeString(tempPath, json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            Files.move(tempPath, path,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

            log.debug("Persisted {} success patterns", successPatterns.size());
        } catch (IOException e) {
            log.error("Failed to persist success patterns: {}", e.getMessage());
            throw new LearningCaptureException("Failed to persist success patterns", e);
        }
    }

    private void persistFailurePatterns() {
        try {
            Path path = memoryDirectory.resolve(FAILURE_PATTERNS_FILE);
            Path tempPath = memoryDirectory.resolve(FAILURE_PATTERNS_FILE + ".tmp");

            PatternData data = new PatternData(
                    Instant.now(),
                    List.of(),
                    new ArrayList<>(failurePatterns.values())
            );
            String json = objectMapper.writeValueAsString(data);

            Files.writeString(tempPath, json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            Files.move(tempPath, path,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

            log.debug("Persisted {} failure patterns", failurePatterns.size());
        } catch (IOException e) {
            log.error("Failed to persist failure patterns: {}", e.getMessage());
            throw new LearningCaptureException("Failed to persist failure patterns", e);
        }
    }

    private void persistAgentMetrics() {
        try {
            Path path = memoryDirectory.resolve(AGENT_METRICS_FILE);
            Path tempPath = memoryDirectory.resolve(AGENT_METRICS_FILE + ".tmp");

            AgentMetricsData data = new AgentMetricsData(
                    Instant.now(),
                    new ArrayList<>(agentMetrics.values())
            );
            String json = objectMapper.writeValueAsString(data);

            Files.writeString(tempPath, json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            Files.move(tempPath, path,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

            log.debug("Persisted {} agent metrics", agentMetrics.size());
        } catch (IOException e) {
            log.error("Failed to persist agent metrics: {}", e.getMessage());
            throw new LearningCaptureException("Failed to persist agent metrics", e);
        }
    }

    /**
     * Captures an outcome from a completed upgrade record.
     * Updates patterns and metrics based on the outcome.
     *
     * @param record the completed upgrade record to capture
     */
    public void captureOutcome(UpgradeMemoryStore.UpgradeRecord record) {
        Objects.requireNonNull(record, "record cannot be null");

        // Update success/failure patterns
        if (record.isSuccessful()) {
            updateSuccessPatternFromRecord(record);
        } else if (record.outcome() instanceof UpgradeMemoryStore.Failure failure) {
            updateFailurePatternFromRecord(record, failure);
        }

        // Update agent metrics
        for (Map.Entry<String, String> agent : record.agents().entrySet()) {
            updateAgentMetricFromRecord(agent.getKey(), record);
        }

        persistPatterns();
        log.info("Captured outcome for record: id={}, outcome={}",
                record.id(), record.outcome().description());
    }

    private void updateSuccessPatternFromRecord(UpgradeMemoryStore.UpgradeRecord record) {
        String signature = extractPhaseSignature(record);
        String patternId = "sp-" + signature.hashCode();

        List<String> phases = record.phases().stream()
                .map(UpgradeMemoryStore.PhaseResult::phaseName)
                .collect(Collectors.toList());

        successPatterns.compute(patternId, (id, existing) -> {
            if (existing == null) {
                return new SuccessPattern.Builder()
                        .id(patternId)
                        .name("Pattern: " + String.join(" -> ", phases))
                        .phaseSequence(phases)
                        .occurrenceCount(1)
                        .successRate(1.0)
                        .avgDurationMillis(record.durationMillis() > 0 ? record.durationMillis() : 0)
                        .build();
            } else {
                return existing.incrementObservation(true, record.durationMillis());
            }
        });
    }

    private void updateFailurePatternFromRecord(
            UpgradeMemoryStore.UpgradeRecord record,
            UpgradeMemoryStore.Failure failure) {

        String errorType = failure.errorType();
        String patternId = "fp-" + errorType.toLowerCase().replace(" ", "-");

        Set<String> affectedPhases = record.phases().stream()
                .filter(p -> !p.isSuccessful())
                .map(UpgradeMemoryStore.PhaseResult::phaseName)
                .collect(Collectors.toSet());

        failurePatterns.compute(patternId, (id, existing) -> {
            if (existing == null) {
                return new FailurePattern.Builder()
                        .id(patternId)
                        .name(errorType + " Failure")
                        .failureType(errorType)
                        .errorPattern(extractErrorPattern(failure.errorMessage()))
                        .rootCause("Detected from failure")
                        .addResolutionStep("Review logs for: " + failure.errorMessage())
                        .addAffectedPhase(affectedPhases.isEmpty() ? "unknown" : affectedPhases.iterator().next())
                        .build();
            } else {
                return existing.incrementObservation();
            }
        });
    }

    private void updateAgentMetricFromRecord(String agentId, UpgradeMemoryStore.UpgradeRecord record) {
        AgentMetric existing = agentMetrics.getOrDefault(
                agentId, AgentMetric.initial(agentId, "unknown"));

        long duration = record.durationMillis();
        agentMetrics.put(agentId, existing.recordTask(record.isSuccessful(), duration > 0 ? duration : 0));
    }

    /**
     * Retrieves all success patterns, sorted by occurrence count (descending).
     *
     * @return list of success patterns
     */
    public List<SuccessPattern> getSuccessPatterns() {
        return successPatterns.values().stream()
                .sorted(Comparator.comparingInt(SuccessPattern::occurrenceCount).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Retrieves success patterns matching a filter.
     *
     * @param filter predicate to filter patterns
     * @return filtered list of success patterns
     */
    public List<SuccessPattern> getSuccessPatterns(Predicate<SuccessPattern> filter) {
        Objects.requireNonNull(filter, "filter cannot be null");
        return successPatterns.values().stream()
                .filter(filter)
                .sorted(Comparator.comparingInt(SuccessPattern::occurrenceCount).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Retrieves all failure patterns, sorted by occurrence count (descending).
     *
     * @return list of failure patterns
     */
    public List<FailurePattern> getFailurePatterns() {
        return failurePatterns.values().stream()
                .sorted(Comparator.comparingInt(FailurePattern::occurrenceCount).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Retrieves failure patterns matching a filter.
     *
     * @param filter predicate to filter patterns
     * @return filtered list of failure patterns
     */
    public List<FailurePattern> getFailurePatterns(Predicate<FailurePattern> filter) {
        Objects.requireNonNull(filter, "filter cannot be null");
        return failurePatterns.values().stream()
                .filter(filter)
                .sorted(Comparator.comparingInt(FailurePattern::occurrenceCount).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Finds failure patterns that match an error message.
     *
     * @param errorMessage the error message to match against
     * @return list of matching failure patterns with suggested resolutions
     */
    public List<FailurePattern> findMatchingFailurePatterns(String errorMessage) {
        Objects.requireNonNull(errorMessage, "errorMessage cannot be null");
        return failurePatterns.values().stream()
                .filter(p -> p.matches(errorMessage))
                .sorted(Comparator.comparingInt(FailurePattern::occurrenceCount).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Retrieves all agent metrics, sorted by total tasks (descending).
     *
     * @return list of agent metrics
     */
    public List<AgentMetric> getAgentMetrics() {
        return agentMetrics.values().stream()
                .sorted(Comparator.comparingInt(AgentMetric::totalTasks).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Retrieves metrics for a specific agent.
     *
     * @param agentId the agent identifier
     * @return Optional containing the agent metric, or empty if not found
     */
    public Optional<AgentMetric> getAgentMetric(String agentId) {
        Objects.requireNonNull(agentId, "agentId cannot be null");
        return Optional.ofNullable(agentMetrics.get(agentId));
    }

    /**
     * Returns the top performing agents by success rate.
     *
     * @param limit maximum number of agents to return
     * @return list of top performing agents
     */
    public List<AgentMetric> getTopPerformingAgents(int limit) {
        return agentMetrics.values().stream()
                .filter(m -> m.totalTasks() >= 3) // Require minimum tasks for statistical significance
                .sorted(Comparator.comparingDouble(AgentMetric::successRate).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Returns the most common success patterns.
     *
     * @param limit maximum number of patterns to return
     * @return list of most common success patterns
     */
    public List<SuccessPattern> getMostCommonSuccessPatterns(int limit) {
        return successPatterns.values().stream()
                .sorted(Comparator.comparingInt(SuccessPattern::occurrenceCount).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Returns the most common failure patterns.
     *
     * @param limit maximum number of patterns to return
     * @return list of most common failure patterns
     */
    public List<FailurePattern> getMostCommonFailurePatterns(int limit) {
        return failurePatterns.values().stream()
                .sorted(Comparator.comparingInt(FailurePattern::occurrenceCount).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Clears all learned patterns.
     */
    public void clearPatterns() {
        successPatterns.clear();
        failurePatterns.clear();
        agentMetrics.clear();
        persistPatterns();
        log.info("Cleared all learned patterns");
    }

    /**
     * Returns the underlying memory store.
     *
     * @return the upgrade memory store
     */
    public UpgradeMemoryStore getMemoryStore() {
        return memoryStore;
    }

    /**
     * Exception thrown when learning capture operations fail.
     */
    public static final class LearningCaptureException extends RuntimeException {
        public LearningCaptureException(String message) {
            super(message);
        }

        public LearningCaptureException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
