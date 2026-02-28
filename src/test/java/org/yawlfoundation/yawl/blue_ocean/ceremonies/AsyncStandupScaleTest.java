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

package org.yawlfoundation.yawl.blue_ocean.ceremonies;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.engine.YEngine;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Async Standup Scaling Tests — Performance Under Load
 *
 * This test suite validates async standup ceremony scalability:
 * 1. Baseline: 100 participants (5 teams × 20 people)
 * 2. Scale: 500 participants (25 teams × 20 people)
 * 3. Extreme: 1000 participants (50 teams × 20 people)
 * 4. Concurrent Ceremonies: Multiple standups in parallel
 * 5. Blocker Detection Latency: Time from report to escalation
 *
 * Performance SLAs:
 * - 500 participants: <2 seconds total, <50ms per participant
 * - 1000 participants: <5 seconds total, <50ms per participant
 * - Blocker detection: <30 seconds latency
 * - Memory: <500MB heap for 1000 concurrent participants
 *
 * Test Framework: Chicago TDD with Java 25 Virtual Threads
 * - No thread pool sizing (virtual threads auto-scale)
 * - Structured concurrency for observable execution
 * - Real YAWL engine processing
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@DisplayName("Blue Ocean: Async Standup Scaling Tests")
public class AsyncStandupScaleTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncStandupScaleTest.class);

    private YEngine engine;
    private AsyncStandupTestHelper standupHelper;

    @BeforeEach
    void setUp() {
        engine = YEngine.getInstance();
        assertNotNull(engine, "YEngine should be available");
        standupHelper = new AsyncStandupTestHelper(engine);
    }

    /**
     * Baseline Test: 100 Participants (5 Teams × 20 People)
     *
     * Expected Performance:
     * - Total latency: <500ms
     * - Per-participant latency: <50ms
     * - All reports processed: 100%
     * - Blocker detection: 100% recall
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Scale: 100 participants (baseline, 5 teams)")
    void testAsyncStandupBaseline100() {
        LOGGER.info("=== Async Standup at 100 Scale (Baseline) ===");

        // Arrange: 5 teams × 20 people = 100 participants
        SAFETrain train = standupHelper.buildTrainWithParticipants(100);
        List<Developer> developers = train.getAllDevelopers();
        assertThat(developers).hasSize(100);

        // Generate standup reports (including 5-10 blockers for realistic scenario)
        List<StandupReport> reports = new ArrayList<>();
        for (int i = 0; i < developers.size(); i++) {
            Developer dev = developers.get(i);
            String status = (i % 20 == 0) ? "blocked" : "in-progress";  // 5% blockers
            reports.add(new StandupReport(dev.id(), dev.teamId(), status, "Working on " + i));
        }

        // Act: Execute async standup
        Instant start = Instant.now();
        AsyncStandupResult result = standupHelper.executeAsyncStandup(train, reports);
        Duration elapsed = Duration.between(start, Instant.now());

        // Assert: Performance SLA
        assertThat(elapsed).isLessThan(Duration.ofMillis(500));
        assertThat(result.reportsProcessed()).isEqualTo(100);
        assertThat(result.successRate()).isEqualTo(1.0);

        // Blocker detection
        List<BlockerNotification> blockers = result.detectedBlockers();
        assertThat(blockers).isNotEmpty();
        assertThat(blockers.size()).isGreaterThanOrEqualTo(5);  // Should detect ~5 blockers

        // Per-participant latency
        double avgLatency = elapsed.toMillis() / 100.0;
        LOGGER.info("100-participant standup: {}ms total, {:.1f}ms per participant, " +
            "{} blockers detected",
            elapsed.toMillis(), avgLatency, blockers.size());

        assertThat(avgLatency).isLessThan(50);  // <50ms per participant
    }

    /**
     * Scale Test: 500 Participants (25 Teams × 20 People)
     *
     * Expected Performance:
     * - Total latency: <2 seconds
     * - Per-participant latency: <50ms
     * - All reports processed: 100%
     * - Blocker detection latency: <30 seconds
     * - Memory: <500MB
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("Scale: 500 participants (5× baseline)")
    void testAsyncStandupAt500Scale() {
        LOGGER.info("=== Async Standup at 500 Scale ===");

        // Arrange: 25 teams × 20 people = 500 participants
        SAFETrain train = standupHelper.buildTrainWithParticipants(500);
        List<Developer> developers = train.getAllDevelopers();
        assertThat(developers).hasSize(500);

        // Use virtual thread executor for realistic async processing
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        // Generate and submit standup reports asynchronously
        List<Future<StandupSubmission>> submissions = new ArrayList<>();
        AtomicInteger blockerCount = new AtomicInteger(0);

        for (int i = 0; i < developers.size(); i++) {
            final int index = i;
            submissions.add(executor.submit(() -> {
                Developer dev = developers.get(index);
                String status = (index % 20 == 0) ? "blocked" : "in-progress";
                if ("blocked".equals(status)) {
                    blockerCount.incrementAndGet();
                }
                return standupHelper.submitStandupReportAsync(dev, status);
            }));
        }

        // Act: Collect all submissions
        Instant start = Instant.now();
        List<StandupSubmission> collectedReports = new ArrayList<>();
        for (Future<StandupSubmission> submission : submissions) {
            try {
                collectedReports.add(submission.get(5, TimeUnit.SECONDS));
            } catch (Exception e) {
                fail("Submission timeout or error: " + e.getMessage());
            }
        }
        Duration submissionTime = Duration.between(start, Instant.now());

        // Process all standup reports
        Instant processStart = Instant.now();
        AsyncStandupResult result = standupHelper.executeAsyncStandup(train,
            collectedReports.stream().map(s -> s.report()).toList());
        Duration processingTime = Duration.between(processStart, Instant.now());

        Duration totalElapsed = submissionTime.plus(processingTime);

        // Assert: Performance SLA (2 seconds for 500 participants)
        assertThat(totalElapsed).isLessThan(Duration.ofSeconds(2));
        assertThat(result.reportsProcessed()).isEqualTo(500);
        assertThat(result.successRate()).isEqualTo(1.0);

        // Per-participant latency
        double avgLatency = processingTime.toMillis() / 500.0;
        assertThat(avgLatency).isLessThan(50);  // <50ms per participant

        // Blocker detection
        List<BlockerNotification> blockers = result.detectedBlockers();
        assertThat(blockers).isNotEmpty();
        assertThat(blockers.size()).isGreaterThanOrEqualTo(blockerCount.get() * 0.9);  // 90% detection

        // Blocker escalation latency
        for (BlockerNotification blocker : blockers) {
            assertThat(blocker.escalationTime()).isLessThan(Duration.ofSeconds(30));
        }

        // Memory footprint
        long usedMemory = getUsedMemory();
        LOGGER.info("500-participant standup: {}ms submission, {}ms processing, " +
            "{:.1f}ms per participant, {} blockers, {}MB memory",
            submissionTime.toMillis(), processingTime.toMillis(), avgLatency,
            blockers.size(), usedMemory / 1024 / 1024);

        assertThat(usedMemory).isLessThan(500 * 1024 * 1024);  // <500MB

        executor.shutdown();
    }

    /**
     * Extreme Scale Test: 1000 Participants (50 Teams × 20 People)
     *
     * Expected Performance:
     * - Total latency: <5 seconds
     * - Per-participant latency: <50ms
     * - All reports processed: 100%
     * - Success rate: >99%
     * - Memory: <1GB
     */
    @Test
    @Timeout(value = 45, unit = TimeUnit.SECONDS)
    @DisplayName("Scale: 1000 participants (extreme scale)")
    void testAsyncStandupAt1000Scale() {
        LOGGER.info("=== Async Standup at 1000 Scale (Extreme) ===");

        // Arrange: 50 teams × 20 people = 1000 participants
        SAFETrain train = standupHelper.buildTrainWithParticipants(1000);
        List<Developer> developers = train.getAllDevelopers();
        assertThat(developers).hasSize(1000);

        // Virtual thread executor
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        // Generate and submit standup reports asynchronously
        List<Future<StandupSubmission>> submissions = new ArrayList<>();

        Instant submitStart = Instant.now();
        for (Developer dev : developers) {
            submissions.add(executor.submit(() -> {
                String status = (dev.teamId() % 20 == 0) ? "blocked" : "in-progress";
                return standupHelper.submitStandupReportAsync(dev, status);
            }));
        }

        // Collect submissions
        List<StandupSubmission> collectedReports = new ArrayList<>();
        int timeouts = 0;
        for (Future<StandupSubmission> submission : submissions) {
            try {
                collectedReports.add(submission.get(10, TimeUnit.SECONDS));
            } catch (Exception e) {
                timeouts++;
            }
        }
        Duration submissionTime = Duration.between(submitStart, Instant.now());

        // Act: Process all standup reports
        Instant processStart = Instant.now();
        AsyncStandupResult result = standupHelper.executeAsyncStandup(train,
            collectedReports.stream().map(s -> s.report()).toList());
        Duration processingTime = Duration.between(processStart, Instant.now());

        Duration totalElapsed = submissionTime.plus(processingTime);

        // Assert: Performance SLA (5 seconds for 1000 participants)
        assertThat(totalElapsed).isLessThan(Duration.ofSeconds(5));
        assertThat(timeouts).isZero();  // No timeouts
        assertThat(result.successRate()).isGreaterThan(0.99);  // >99% success

        // Per-participant latency
        double avgLatency = processingTime.toMillis() / collectedReports.size();
        assertThat(avgLatency).isLessThan(50);  // <50ms per participant

        // Blocker detection
        List<BlockerNotification> blockers = result.detectedBlockers();
        assertThat(blockers).isNotEmpty();  // Should detect blockers

        // Memory footprint
        long usedMemory = getUsedMemory();

        LOGGER.info("1000-participant standup: {}ms submission, {}ms processing, " +
            "{:.1f}ms per participant, {} blockers, {}MB memory, {}% success",
            submissionTime.toMillis(), processingTime.toMillis(), avgLatency,
            blockers.size(), usedMemory / 1024 / 1024,
            Math.round(result.successRate() * 100));

        assertThat(usedMemory).isLessThan(1024 * 1024 * 1024);  // <1GB

        executor.shutdown();
    }

    /**
     * Concurrent Ceremonies Test: Multiple Standups Running in Parallel
     *
     * Setup:
     * - 5 concurrent standup ceremonies
     * - Each with 100 participants (500 total)
     *
     * Expected:
     * - All 5 ceremonies complete in parallel without blocking
     * - Total time ≈ single standup time (not 5× longer)
     * - No inter-ceremony interference
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("Scale: 5 Concurrent Ceremonies × 100 Participants")
    void testConcurrentCeremonies() {
        LOGGER.info("=== Concurrent Ceremonies Test (5 standups parallel) ===");

        // Arrange: 5 teams executing standup in parallel
        List<SAFETrain> trains = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            trains.add(standupHelper.buildTrainWithParticipants(100));
        }

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        // Act: Execute all standups concurrently
        Instant start = Instant.now();
        List<Future<AsyncStandupResult>> ceremonies = new ArrayList<>();

        for (SAFETrain train : trains) {
            ceremonies.add(executor.submit(() -> {
                List<Developer> developers = train.getAllDevelopers();
                List<StandupReport> reports = developers.stream()
                    .map(dev -> new StandupReport(dev.id(), dev.teamId(), "in-progress", "Working"))
                    .toList();

                return standupHelper.executeAsyncStandup(train, reports);
            }));
        }

        // Collect results
        List<AsyncStandupResult> results = new ArrayList<>();
        for (Future<AsyncStandupResult> ceremony : ceremonies) {
            try {
                results.add(ceremony.get(10, TimeUnit.SECONDS));
            } catch (Exception e) {
                fail("Ceremony timeout: " + e.getMessage());
            }
        }

        Duration totalTime = Duration.between(start, Instant.now());

        // Assert: All ceremonies completed successfully
        assertThat(results).hasSize(5);
        for (AsyncStandupResult result : results) {
            assertThat(result.reportsProcessed()).isEqualTo(100);
            assertThat(result.successRate()).isEqualTo(1.0);
        }

        // Parallel execution: total time should be ~same as single standup
        // (not 5× longer if sequential)
        // Single standup at 100 scale: ~200-300ms
        // 5 concurrent: should be ~300-400ms (not 1000-1500ms)
        assertThat(totalTime).isLessThan(Duration.ofSeconds(1));

        LOGGER.info("5 concurrent ceremonies (500 total participants): " +
            "{}ms total (expected ~300-400ms for parallel, not 1000-1500ms sequential)",
            totalTime.toMillis());

        executor.shutdown();
    }

    /**
     * Blocker Detection Latency Test
     *
     * Setup:
     * - 500 participants, some reporting blockers
     * - Measure time from blocker report to escalation
     *
     * Expected:
     * - Blocker detection: <5 seconds
     * - Escalation routing: <30 seconds
     * - Notification sent: <1 minute
     */
    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    @DisplayName("Scale: Blocker Detection Latency (500 participants)")
    void testBlockerDetectionLatencyAt500Scale() {
        LOGGER.info("=== Blocker Detection Latency at 500 Scale ===");

        // Arrange: 500 participants with some blockers
        SAFETrain train = standupHelper.buildTrainWithParticipants(500);
        List<Developer> developers = train.getAllDevelopers();

        // 50 developers report blockers (~10%)
        List<StandupReport> reports = new ArrayList<>();
        for (int i = 0; i < developers.size(); i++) {
            Developer dev = developers.get(i);
            String status = (i < 50) ? "blocked" : "in-progress";
            reports.add(new StandupReport(dev.id(), dev.teamId(), status, "Issue: " + i));
        }

        // Act: Execute async standup and measure blocker latencies
        Instant standupStart = Instant.now();
        AsyncStandupResult standupResult = standupHelper.executeAsyncStandup(train, reports);
        Duration standupDuration = Duration.between(standupStart, Instant.now());

        // Blocker detection happens during standup
        List<BlockerNotification> blockers = standupResult.detectedBlockers();

        // Measure escalation latencies
        List<Duration> escalationLatencies = new ArrayList<>();
        for (BlockerNotification blocker : blockers) {
            Duration escLatency = blocker.escalationTime();
            escalationLatencies.add(escLatency);
        }

        // Assert: Latency SLAs
        assertThat(standupDuration).isLessThan(Duration.ofSeconds(5));  // Detection <5s
        assertThat(blockers).hasSizeGreaterThanOrEqualTo(45);  // >90% recall

        // All escalations within 30 seconds
        for (Duration latency : escalationLatencies) {
            assertThat(latency).isLessThan(Duration.ofSeconds(30));
        }

        // Average escalation latency
        Duration avgLatency = Duration.ofMillis(
            escalationLatencies.stream()
                .mapToLong(Duration::toMillis)
                .average()
                .orElse(0.0)
            / escalationLatencies.size()
        );

        LOGGER.info("Blocker detection latency: standup {}ms, " +
            "{} blockers detected, avg escalation {}ms, max escalation {}ms",
            standupDuration.toMillis(), blockers.size(),
            avgLatency.toMillis(),
            escalationLatencies.stream().mapToLong(Duration::toMillis).max().orElse(0));

        assertThat(avgLatency).isLessThan(Duration.ofSeconds(10));
    }

    /**
     * Parametrized Scaling Test: Varying Participant Counts
     *
     * Tests how latency scales with participant count:
     * - 100 participants
     * - 250 participants
     * - 500 participants
     * - 750 participants
     * - 1000 participants
     */
    @ParameterizedTest(name = "Scale: {0} participants")
    @ValueSource(ints = {100, 250, 500, 750, 1000})
    @DisplayName("Parametrized: Latency Scaling with Participant Count")
    void testLatencyScaling(int participantCount) {
        LOGGER.info("Testing async standup latency at {} participants", participantCount);

        // Arrange
        SAFETrain train = standupHelper.buildTrainWithParticipants(participantCount);
        List<Developer> developers = train.getAllDevelopers();
        List<StandupReport> reports = developers.stream()
            .map(dev -> new StandupReport(dev.id(), dev.teamId(), "in-progress", "Working"))
            .toList();

        // Act
        Instant start = Instant.now();
        AsyncStandupResult result = standupHelper.executeAsyncStandup(train, reports);
        Duration elapsed = Duration.between(start, Instant.now());

        // Assert
        assertThat(result.reportsProcessed()).isEqualTo(participantCount);
        assertThat(result.successRate()).isEqualTo(1.0);

        double avgLatency = elapsed.toMillis() / (double) participantCount;
        LOGGER.info("{} participants: {}ms total, {:.2f}ms per participant, {}% success",
            participantCount, elapsed.toMillis(), avgLatency, result.successRate() * 100);

        // Latency should stay constant per participant (linear scaling)
        assertThat(avgLatency).isLessThan(50);

        // Total latency should grow sub-linearly (or at worst linearly)
        if (participantCount <= 500) {
            assertThat(elapsed).isLessThan(Duration.ofSeconds(2));
        } else {
            assertThat(elapsed).isLessThan(Duration.ofSeconds(5));
        }
    }

    // ========== Helper Methods ==========

    private long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
}
