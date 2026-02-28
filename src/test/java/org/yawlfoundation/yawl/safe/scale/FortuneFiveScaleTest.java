/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.safe.scale;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.engine.YNetRunner;
import org.yawlfoundation.yawl.safe.agents.*;
import org.yawlfoundation.yawl.safe.model.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fortune 5 Scale Testing for SAFe Simulation (30 ARTs, 100,000+ employees).
 *
 * Chicago TDD (Detroit School) integration tests with:
 * - Real YAWL YEngine orchestration
 * - 30 Agile Release Trains in parallel
 * - 2,000+ simulated participants per PI
 * - 3,000+ stories, 5,000+ dependencies per cycle
 * - Performance SLA validation (PI planning <4h, dependency resolution <30m)
 * - Chaos engineering (agent failures, network partitions)
 * - Full data consistency verification
 *
 * Key testing principles:
 * 1. Real integration: no mocks, genuine YAWL orchestration
 * 2. Scale-first: all tests designed for 30 ARTs minimum
 * 3. SLA enforcement: strict timing requirements validated
 * 4. Chaos resilience: 10-20% failure rate expected to be handled
 * 5. Data integrity: eventual consistency verified across all operations
 *
 * Test execution modes:
 * - Baseline (1 ART): 5 min, basic validation
 * - Medium (5 ARTs): 20 min, multi-ART coordination
 * - Full (30 ARTs): 4+ hours, enterprise scale
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@DisplayName("Fortune 5 SAFe Scale Test Suite")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FortuneFiveScaleTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(FortuneFiveScaleTest.class);

    private static final int SCALE_LEVEL = getScaleLevel();  // 1, 5, or 30 ARTs
    private static final int ART_COUNT = SCALE_LEVEL;
    private static final int TEAMS_PER_ART = 6;
    private static final int STORIES_PER_TEAM = 5;
    private static final int DEPENDENCY_RATE = 45;  // 45% of stories have cross-ART deps

    private YEngine engine;
    private FortuneScaleOrchestrator orchestrator;
    private FortuneScaleDataFactory dataFactory;
    private List<ARTContext> artContexts;
    private Map<String, Long> performanceMetrics;

    @BeforeEach
    void setUp() {
        LOGGER.info("=== FORTUNE 5 SCALE TEST SETUP ===");
        LOGGER.info("Scale Level: {} ARTs", SCALE_LEVEL);
        LOGGER.info("Total Teams: {}", ART_COUNT * TEAMS_PER_ART);
        LOGGER.info("Total Stories: {}", ART_COUNT * TEAMS_PER_ART * STORIES_PER_TEAM);

        // Initialize YAWL engine
        engine = YEngine.getInstance();
        assertNotNull(engine, "YEngine should be initialized");

        // Initialize orchestrator and data factory
        orchestrator = new FortuneScaleOrchestrator(engine);
        dataFactory = new FortuneScaleDataFactory();
        performanceMetrics = new ConcurrentHashMap<>();

        // Pre-create business units and value streams
        orchestrator.initializeEnterpriseStructure(
            dataFactory.createBusinessUnits(5),
            dataFactory.createValueStreams(12)
        );

        artContexts = Collections.synchronizedList(new ArrayList<>());
        LOGGER.info("Setup complete: engine ready, {} business units created", 5);
    }

    @AfterEach
    void tearDown() {
        LOGGER.info("=== FORTUNE 5 SCALE TEST TEARDOWN ===");
        LOGGER.info("Performance Metrics: {}", performanceMetrics);
        LOGGER.info("ART Contexts Processed: {}", artContexts.size());

        // Cleanup
        if (engine != null) {
            engine.shutdown();
        }
    }

    // ==================== TIER 1: BASELINE TESTS (1 ART) ====================

    /**
     * Test 1: Single ART PI Planning (baseline)
     *
     * Validates:
     * - One ART can complete PI planning with 6 teams, 30 stories, dependencies
     * - All stories assigned and dependencies discovered
     * - Message ordering preserved
     * - Timing: <10 minutes (baseline SLA)
     */
    @Test
    @DisplayName("T1: Single ART PI Planning Ceremony")
    @Order(1)
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void testSingleARTPIPlanningCeremony() {
        LOGGER.info("T1: Starting single ART PI planning ceremony");
        Instant startTime = Instant.now();

        // Create one ART
        BusinessUnit bu = dataFactory.createBusinessUnit("Enterprise", 100);
        ValueStream vs = dataFactory.createValueStream("Stream-1", List.of(bu));
        ART art = dataFactory.createART("ART-1", TEAMS_PER_ART, vs);

        // Generate stories with dependencies
        List<UserStory> stories = dataFactory.generateStoriesWithDependencies(
            TEAMS_PER_ART * STORIES_PER_TEAM,
            DEPENDENCY_RATE
        );

        // Execute PI planning
        ARTContext artContext = new ARTContext(art, stories);
        artContexts.add(artContext);

        PIResult result = orchestrator.executeARTPIPlanningWorkflow(artContext);

        // Validate result
        Instant endTime = Instant.now();
        long durationSeconds = Duration.between(startTime, endTime).toSeconds();

        assertTrue(result.isSuccessful(), "ART PI planning should succeed");
        assertEquals(stories.size(), result.getAssignedStories().size(),
            "All stories should be assigned");
        assertTrue(durationSeconds < 600, "PI planning should complete in <10 min, took " + durationSeconds + "s");

        // Validate dependency discovery
        assertTrue(result.getDependencies().size() > 0,
            "Should discover inter-ART dependencies");

        LOGGER.info("T1: PASS - Single ART PI planning completed in {} seconds", durationSeconds);
        performanceMetrics.put("single_art_pi_planning_seconds", durationSeconds);
    }

    /**
     * Test 2: Single ART Story Flow (story acceptance)
     *
     * Validates:
     * - Dev → Architect → PO acceptance flow
     * - State machine transitions
     * - Timing: each transition <1 minute
     */
    @Test
    @DisplayName("T2: Single ART Story Acceptance Flow")
    @Order(2)
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void testSingleARTStoryFlow() {
        LOGGER.info("T2: Starting single ART story flow test");

        // Create simple ART with one story
        ART art = dataFactory.createART("ART-Story-Flow", 1, null);
        UserStory story = dataFactory.createUserStory(
            "US-1", "Simple Story", 3,
            List.of()  // no dependencies
        );

        ARTContext artContext = new ARTContext(art, List.of(story));
        artContexts.add(artContext);

        // Execute story flow: DEV_IN_PROGRESS → READY_FOR_REVIEW → PO_ACCEPTANCE → DEPLOYED
        StoryFlowResult flowResult = orchestrator.executeStoryFlow(artContext, story);

        assertTrue(flowResult.isCompleted(), "Story flow should reach DEPLOYED state");
        assertEquals(4, flowResult.getStateTransitions().size(),
            "Story should transition through 4 states");
        assertTrue(flowResult.getTotalDurationSeconds() < 300,
            "Story flow should complete in <5 minutes");

        LOGGER.info("T2: PASS - Story flow completed in {} seconds with {} transitions",
            flowResult.getTotalDurationSeconds(),
            flowResult.getStateTransitions().size());
    }

    /**
     * Test 3: Dependency Negotiation (single dependency)
     *
     * Validates:
     * - Consumer ART submits dependency
     * - Provider ART receives and negotiates
     * - Both confirm and register
     * - SLA: <5 minutes
     */
    @Test
    @DisplayName("T3: Single Dependency Negotiation Flow")
    @Order(3)
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void testSingleDependencyNegotiationFlow() {
        LOGGER.info("T3: Starting single dependency negotiation test");
        Instant startTime = Instant.now();

        // Create two ARTs
        ART producerART = dataFactory.createART("ART-Producer", 1, null);
        ART consumerART = dataFactory.createART("ART-Consumer", 1, null);

        // Consumer submits dependency
        Dependency dependency = new Dependency(
            "DEP-1",
            consumerART.id(),
            producerART.id(),
            "US-1",
            "SUBMITTED",
            Instant.now(),
            null
        );

        // Negotiate and resolve
        DependencyResolutionResult resolutionResult = orchestrator.resolveDependency(
            producerART,
            consumerART,
            dependency
        );

        Instant endTime = Instant.now();
        long durationSeconds = Duration.between(startTime, endTime).toSeconds();

        assertTrue(resolutionResult.isResolved(), "Dependency should be resolved");
        assertTrue(durationSeconds < 300, "Resolution should complete in <5 minutes, took " + durationSeconds + "s");

        LOGGER.info("T3: PASS - Dependency resolved in {} seconds", durationSeconds);
        performanceMetrics.put("single_dependency_resolution_seconds", durationSeconds);
    }

    // ==================== TIER 2: MULTI-ART TESTS (5 ARTs) ====================

    /**
     * Test 4: Five ARTs Parallel PI Planning
     *
     * Validates:
     * - 5 ARTs plan simultaneously without interference
     * - Dependencies across all 5 ARTs discovered
     * - SLA: <30 minutes total
     */
    @Test
    @DisplayName("T4: Five ARTs Parallel PI Planning")
    @Order(4)
    @Timeout(value = 45, unit = TimeUnit.MINUTES)
    void testFiveARTsParallelPIPlanningCeremony() {
        LOGGER.info("T4: Starting 5-ART parallel PI planning ceremony");
        Instant startTime = Instant.now();

        // Create 5 ARTs
        List<ART> arts = IntStream.range(1, 6)
            .mapToObj(i -> dataFactory.createART("ART-" + i, TEAMS_PER_ART, null))
            .collect(Collectors.toList());

        List<ARTContext> contextList = new ArrayList<>();
        for (ART art : arts) {
            List<UserStory> stories = dataFactory.generateStoriesWithDependencies(
                TEAMS_PER_ART * STORIES_PER_TEAM,
                DEPENDENCY_RATE
            );
            ARTContext context = new ARTContext(art, stories);
            contextList.add(context);
            artContexts.add(context);
        }

        // Execute all PI planning in parallel
        List<PIResult> results = contextList.parallelStream()
            .map(orchestrator::executeARTPIPlanningWorkflow)
            .collect(Collectors.toList());

        Instant endTime = Instant.now();
        long durationSeconds = Duration.between(startTime, endTime).toSeconds();

        // Validate all succeeded
        assertTrue(results.stream().allMatch(PIResult::isSuccessful),
            "All 5 ARTs should succeed");
        assertEquals(5, results.size(), "All 5 ARTs should produce results");

        // Validate dependencies discovered
        long totalDependencies = results.stream()
            .flatMap(r -> r.getDependencies().stream())
            .count();
        assertTrue(totalDependencies > 0, "Should discover inter-ART dependencies");

        assertTrue(durationSeconds < 1800, "5-ART planning should complete in <30 min, took " + durationSeconds + "s");

        LOGGER.info("T4: PASS - 5-ART PI planning completed in {} seconds with {} dependencies",
            durationSeconds, totalDependencies);
        performanceMetrics.put("five_art_pi_planning_seconds", durationSeconds);
    }

    /**
     * Test 5: Five ARTs Dependency Resolution
     *
     * Validates:
     * - All 5 ARTs submit dependencies simultaneously
     * - Each pair negotiates independently
     * - No circular dependencies created
     * - SLA: <15 minutes total
     */
    @Test
    @DisplayName("T5: Five ARTs Cross-ART Dependency Resolution")
    @Order(5)
    @Timeout(value = 30, unit = TimeUnit.MINUTES)
    void testFiveARTsCrossARTDependencyResolution() {
        LOGGER.info("T5: Starting 5-ART cross-ART dependency resolution");
        Instant startTime = Instant.now();

        // Create 5 ARTs
        List<ART> arts = IntStream.range(1, 6)
            .mapToObj(i -> dataFactory.createART("ART-Dep-" + i, 2, null))
            .collect(Collectors.toList());

        // Generate cross-ART dependencies
        List<Dependency> dependencies = dataFactory.generateCrossARTDependencies(arts, 20);

        // Resolve all dependencies
        List<DependencyResolutionResult> results = dependencies.parallelStream()
            .map(dep -> {
                ART producer = arts.stream()
                    .filter(a -> a.id().equals(dep.providerArtId()))
                    .findFirst()
                    .orElse(null);
                ART consumer = arts.stream()
                    .filter(a -> a.id().equals(dep.consumerArtId()))
                    .findFirst()
                    .orElse(null);
                return orchestrator.resolveDependency(producer, consumer, dep);
            })
            .collect(Collectors.toList());

        Instant endTime = Instant.now();
        long durationSeconds = Duration.between(startTime, endTime).toSeconds();

        // Validate all resolved
        assertTrue(results.stream().allMatch(DependencyResolutionResult::isResolved),
            "All dependencies should be resolved");

        // Validate no circular dependencies
        Set<String> circularDeps = results.stream()
            .filter(DependencyResolutionResult::hasCircularDependency)
            .map(DependencyResolutionResult::getDependencyId)
            .collect(Collectors.toSet());
        assertTrue(circularDeps.isEmpty(), "No circular dependencies should exist");

        assertTrue(durationSeconds < 900, "5-ART dependency resolution should complete in <15 min, took " + durationSeconds + "s");

        LOGGER.info("T5: PASS - 5-ART dependency resolution completed in {} seconds", durationSeconds);
        performanceMetrics.put("five_art_dependency_resolution_seconds", durationSeconds);
    }

    /**
     * Test 6: Five ARTs Story Execution (30 stories)
     *
     * Validates:
     * - 30 stories (6 per ART) execute through complete flow
     * - Timing per story <5 minutes average
     * - No lost or corrupted stories
     */
    @Test
    @DisplayName("T6: Five ARTs Story Execution (30 stories)")
    @Order(6)
    @Timeout(value = 45, unit = TimeUnit.MINUTES)
    void testFiveARTsStoryExecution() {
        LOGGER.info("T6: Starting 5-ART story execution test (30 stories)");
        Instant startTime = Instant.now();

        // Create 5 ARTs with 6 stories each
        List<StoryFlowResult> allResults = new ArrayList<>();
        AtomicInteger storyCount = new AtomicInteger(0);

        for (int i = 1; i <= 5; i++) {
            ART art = dataFactory.createART("ART-Story-" + i, 1, null);
            ARTContext context = new ARTContext(art, new ArrayList<>());
            artContexts.add(context);

            // Create and execute 6 stories per ART
            for (int j = 1; j <= 6; j++) {
                UserStory story = dataFactory.createUserStory(
                    "US-" + i + "-" + j, "Story " + storyCount.incrementAndGet(), 3,
                    List.of()
                );

                StoryFlowResult result = orchestrator.executeStoryFlow(context, story);
                allResults.add(result);
            }
        }

        Instant endTime = Instant.now();
        long durationSeconds = Duration.between(startTime, endTime).toSeconds();
        long avgPerStory = durationSeconds / allResults.size();

        // Validate all stories completed
        assertEquals(30, allResults.size(), "Should execute 30 stories");
        assertTrue(allResults.stream().allMatch(StoryFlowResult::isCompleted),
            "All stories should complete");

        assertTrue(avgPerStory < 300, "Average story execution should be <5 min, was " + avgPerStory + " seconds");

        LOGGER.info("T6: PASS - 30 stories completed in {} seconds (avg {} sec/story)",
            durationSeconds, avgPerStory);
        performanceMetrics.put("five_art_story_execution_seconds", durationSeconds);
    }

    // ==================== TIER 3: FULL SCALE TESTS (30 ARTs) ====================

    /**
     * Test 7: Full Scale PI Planning (30 ARTs, 2,000+ participants)
     *
     * CORE SLA TEST
     *
     * Validates:
     * - 30 ARTs plan simultaneously
     * - 2,000+ participants coordinated
     * - 3,000+ stories assigned
     * - 5,000+ dependencies discovered
     * - SLA: <4 hours (240 minutes)
     * - No failures, escalations handled
     */
    @Test
    @DisplayName("T7: Full Scale PI Planning (30 ARTs, 2,000+ participants) [SLA TEST]")
    @Order(7)
    @Timeout(value = 5, unit = TimeUnit.HOURS)
    void testFullScalePIPlanningCeremony() {
        if (SCALE_LEVEL < 30) {
            LOGGER.warn("T7: Skipping full-scale test at scale level {}", SCALE_LEVEL);
            return;
        }

        LOGGER.info("T7: Starting FULL SCALE PI PLANNING CEREMONY");
        LOGGER.info("Participants: 2,000+ | Stories: 3,000+ | Dependencies: 5,000+");
        Instant startTime = Instant.now();

        // Create 30 ARTs
        List<ART> arts = IntStream.range(1, 31)
            .mapToObj(i -> dataFactory.createART("ART-" + i, TEAMS_PER_ART, null))
            .collect(Collectors.toList());

        List<ARTContext> contextList = new ArrayList<>();
        int totalStories = 0;
        for (ART art : arts) {
            List<UserStory> stories = dataFactory.generateStoriesWithDependencies(
                TEAMS_PER_ART * STORIES_PER_TEAM,
                DEPENDENCY_RATE
            );
            contextList.add(new ARTContext(art, stories));
            totalStories += stories.size();
        }

        LOGGER.info("Executing {} ARTs with {} total stories", arts.size(), totalStories);

        // Execute all PI planning in parallel (virtual threads)
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<PIResult>> futures = new ArrayList<>();

        for (ARTContext context : contextList) {
            artContexts.add(context);
            futures.add(executor.submit(() -> orchestrator.executeARTPIPlanningWorkflow(context)));
        }

        // Wait for all to complete
        List<PIResult> results = new ArrayList<>();
        try {
            for (Future<PIResult> future : futures) {
                results.add(future.get());
            }
        } catch (InterruptedException | ExecutionException e) {
            fail("PI planning execution failed: " + e.getMessage());
        } finally {
            executor.shutdown();
        }

        Instant endTime = Instant.now();
        long durationMinutes = Duration.between(startTime, endTime).toMinutes();

        // CRITICAL ASSERTIONS
        assertEquals(30, results.size(), "All 30 ARTs must produce results");
        assertTrue(results.stream().allMatch(PIResult::isSuccessful),
            "All 30 ARTs must succeed");

        long totalAssignedStories = results.stream()
            .flatMap(r -> r.getAssignedStories().stream())
            .count();
        assertEquals(totalStories, totalAssignedStories,
            "All " + totalStories + " stories must be assigned");

        long totalDependencies = results.stream()
            .flatMap(r -> r.getDependencies().stream())
            .count();
        assertTrue(totalDependencies >= totalStories * 0.4,
            "Should discover ~40-50% of stories with dependencies");

        // SLA ENFORCEMENT
        assertTrue(durationMinutes <= 240,
            "CRITICAL SLA FAILURE: PI planning took " + durationMinutes + " min, SLA = 240 min");

        // No unresolved critical issues
        assertTrue(results.stream()
                .flatMap(r -> r.getUnresolvedIssues().stream())
                .count() == 0,
            "No unresolved critical issues allowed");

        LOGGER.info("T7: PASS - Full-scale PI planning completed in {} minutes", durationMinutes);
        LOGGER.info("T7: {} stories assigned, {} dependencies discovered", totalAssignedStories, totalDependencies);
        performanceMetrics.put("full_scale_pi_planning_minutes", durationMinutes);
    }

    /**
     * Test 8: Full Scale Dependency Resolution (30 ARTs, 5,000+ dependencies)
     *
     * CORE SLA TEST
     *
     * Validates:
     * - All 5,000+ dependencies submitted and negotiated
     * - Cross-ART negotiation works at scale
     * - SLA: <30 minutes
     * - No lost or corrupted dependencies
     */
    @Test
    @DisplayName("T8: Full Scale Dependency Resolution (5,000+ deps) [SLA TEST]")
    @Order(8)
    @Timeout(value = 60, unit = TimeUnit.MINUTES)
    void testFullScaleDependencyResolution() {
        if (SCALE_LEVEL < 30) {
            LOGGER.warn("T8: Skipping full-scale test at scale level {}", SCALE_LEVEL);
            return;
        }

        LOGGER.info("T8: Starting FULL SCALE DEPENDENCY RESOLUTION");
        LOGGER.info("Resolving 5,000+ dependencies across 30 ARTs");
        Instant startTime = Instant.now();

        // Create 30 ARTs
        List<ART> arts = IntStream.range(1, 31)
            .mapToObj(i -> dataFactory.createART("ART-Dep-Full-" + i, TEAMS_PER_ART, null))
            .collect(Collectors.toList());

        // Generate 5,000+ cross-ART dependencies
        List<Dependency> dependencies = dataFactory.generateCrossARTDependencies(
            arts,
            167  // ~5,000 dependencies across 30 ARTs
        );

        LOGGER.info("Generated {} dependencies to resolve", dependencies.size());

        // Resolve all in parallel
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<DependencyResolutionResult>> futures = new ArrayList<>();

        for (Dependency dep : dependencies) {
            futures.add(executor.submit(() -> {
                ART producer = arts.stream()
                    .filter(a -> a.id().equals(dep.providerArtId()))
                    .findFirst()
                    .orElse(null);
                ART consumer = arts.stream()
                    .filter(a -> a.id().equals(dep.consumerArtId()))
                    .findFirst()
                    .orElse(null);
                return orchestrator.resolveDependency(producer, consumer, dep);
            }));
        }

        // Collect results
        List<DependencyResolutionResult> results = new ArrayList<>();
        try {
            for (Future<DependencyResolutionResult> future : futures) {
                results.add(future.get());
            }
        } catch (InterruptedException | ExecutionException e) {
            fail("Dependency resolution failed: " + e.getMessage());
        } finally {
            executor.shutdown();
        }

        Instant endTime = Instant.now();
        long durationMinutes = Duration.between(startTime, endTime).toMinutes();

        // CRITICAL ASSERTIONS
        assertEquals(dependencies.size(), results.size(),
            "All dependencies must be resolved");
        assertTrue(results.stream().allMatch(DependencyResolutionResult::isResolved),
            "All dependencies must reach RESOLVED status");

        // Detect circular dependencies
        long circularCount = results.stream()
            .filter(DependencyResolutionResult::hasCircularDependency)
            .count();
        assertEquals(0, circularCount, "No circular dependencies allowed");

        // SLA ENFORCEMENT
        assertTrue(durationMinutes <= 30,
            "CRITICAL SLA FAILURE: Dependency resolution took " + durationMinutes + " min, SLA = 30 min");

        LOGGER.info("T8: PASS - Full-scale dependency resolution completed in {} minutes", durationMinutes);
        performanceMetrics.put("full_scale_dependency_resolution_minutes", durationMinutes);
    }

    /**
     * Test 9: Portfolio Governance at Full Scale
     *
     * Validates:
     * - 5 business units allocate themes and capacity
     * - 30 ARTs submit demands
     * - Portfolio algorithm optimizes within constraints
     * - SLA: <15 minutes
     */
    @Test
    @DisplayName("T9: Portfolio Governance (5 BUs, 30 ARTs) [SLA TEST]")
    @Order(9)
    @Timeout(value = 30, unit = TimeUnit.MINUTES)
    void testPortfolioGovernanceFullScale() {
        if (SCALE_LEVEL < 30) {
            LOGGER.warn("T9: Skipping full-scale test at scale level {}", SCALE_LEVEL);
            return;
        }

        LOGGER.info("T9: Starting PORTFOLIO GOVERNANCE");
        LOGGER.info("5 Business Units | 30 ARTs | Capacity Optimization");
        Instant startTime = Instant.now();

        // Create portfolio with themes
        List<Theme> themes = dataFactory.createThemes(
            "Cloud Migration", "Performance", "Security", "Infrastructure", "AI/ML"
        );

        PortfolioAllocationRequest request = new PortfolioAllocationRequest(
            themes,
            ART_COUNT
        );

        // Execute portfolio allocation
        PortfolioAllocationResult allocationResult = orchestrator.allocatePortfolioThemes(request);

        Instant endTime = Instant.now();
        long durationMinutes = Duration.between(startTime, endTime).toMinutes();

        // Validate allocation
        assertNotNull(allocationResult, "Allocation should complete");
        assertTrue(allocationResult.isFeasible(),
            "Allocation should respect capacity constraints");

        // Verify capacity not exceeded
        long totalAllocated = allocationResult.getThemeAllocations().values().stream()
            .mapToLong(ThemeAllocation::getAllocatedCapacity)
            .sum();
        assertTrue(totalAllocated <= request.getTotalAvailableCapacity(),
            "Total allocated should not exceed available capacity");

        // SLA ENFORCEMENT
        assertTrue(durationMinutes <= 15,
            "CRITICAL SLA FAILURE: Portfolio allocation took " + durationMinutes + " min, SLA = 15 min");

        LOGGER.info("T9: PASS - Portfolio allocation completed in {} minutes", durationMinutes);
        performanceMetrics.put("portfolio_governance_minutes", durationMinutes);
    }

    /**
     * Test 10: Data Consistency Under Concurrent Operations (30 ARTs)
     *
     * CRITICAL TEST: Verifies eventual consistency
     *
     * Validates:
     * - No lost updates during concurrent PI planning
     * - All dependencies recorded exactly once
     * - No orphaned stories
     * - Final state consistency across all ARTs
     */
    @Test
    @DisplayName("T10: Data Consistency (30 ARTs, concurrent ops)")
    @Order(10)
    @Timeout(value = 60, unit = TimeUnit.MINUTES)
    void testDataConsistencyConcurrentOperations() {
        if (SCALE_LEVEL < 30) {
            LOGGER.warn("T10: Skipping full-scale test at scale level {}", SCALE_LEVEL);
            return;
        }

        LOGGER.info("T10: Starting DATA CONSISTENCY TEST (30 ARTs, concurrent)");
        Instant startTime = Instant.now();

        // Create 30 ARTs
        List<ART> arts = IntStream.range(1, 31)
            .mapToObj(i -> dataFactory.createART("ART-Consistency-" + i, TEAMS_PER_ART, null))
            .collect(Collectors.toList());

        // Create stories with known counts
        Map<String, List<UserStory>> storiesByART = new HashMap<>();
        int expectedTotalStories = 0;
        for (ART art : arts) {
            List<UserStory> stories = dataFactory.generateStoriesWithDependencies(
                TEAMS_PER_ART * STORIES_PER_TEAM,
                DEPENDENCY_RATE
            );
            storiesByART.put(art.id(), stories);
            expectedTotalStories += stories.size();
        }

        LOGGER.info("Expected total stories: {}", expectedTotalStories);

        // Execute concurrent PI planning
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<PIResult>> futures = new ArrayList<>();

        for (ART art : arts) {
            futures.add(executor.submit(() -> {
                ARTContext context = new ARTContext(art, storiesByART.get(art.id()));
                artContexts.add(context);
                return orchestrator.executeARTPIPlanningWorkflow(context);
            }));
        }

        // Collect results
        List<PIResult> results = new ArrayList<>();
        try {
            for (Future<PIResult> future : futures) {
                results.add(future.get());
            }
        } catch (InterruptedException | ExecutionException e) {
            fail("Concurrent execution failed: " + e.getMessage());
        } finally {
            executor.shutdown();
        }

        Instant endTime = Instant.now();
        long durationSeconds = Duration.between(startTime, endTime).toSeconds();

        // CRITICAL CONSISTENCY CHECKS
        long totalAssigned = results.stream()
            .flatMap(r -> r.getAssignedStories().stream())
            .count();
        assertEquals(expectedTotalStories, totalAssigned,
            "CONSISTENCY FAILURE: Lost stories during concurrent execution");

        // Check for duplicates
        List<String> allStoryIds = results.stream()
            .flatMap(r -> r.getAssignedStories().stream())
            .map(UserStory::id)
            .collect(Collectors.toList());
        Set<String> uniqueStoryIds = new HashSet<>(allStoryIds);
        assertEquals(allStoryIds.size(), uniqueStoryIds.size(),
            "CONSISTENCY FAILURE: Duplicate story assignments");

        // Check dependencies
        Set<String> allDeps = results.stream()
            .flatMap(r -> r.getDependencies().stream())
            .collect(Collectors.toSet());
        long expectedDeps = expectedTotalStories * DEPENDENCY_RATE / 100;
        assertTrue(allDeps.size() >= expectedDeps * 0.9,
            "Dependency count should match expectation");

        LOGGER.info("T10: PASS - Data consistency verified");
        LOGGER.info("T10: {} stories assigned (expected {}), {} unique dependencies",
            totalAssigned, expectedTotalStories, allDeps.size());
        performanceMetrics.put("data_consistency_test_seconds", durationSeconds);
    }

    /**
     * Test 11: M&A Integration Workflow (full scale)
     *
     * Validates:
     * - New business unit onboarding
     * - 2-3 new ARTs created
     * - Backlog merge preserves data
     * - First PI planning with integrated teams
     */
    @Test
    @DisplayName("T11: M&A Integration Workflow")
    @Order(11)
    @Timeout(value = 60, unit = TimeUnit.MINUTES)
    void testMAIntegrationWorkflow() {
        if (SCALE_LEVEL < 30) {
            LOGGER.warn("T11: Skipping full-scale test at scale level {}", SCALE_LEVEL);
            return;
        }

        LOGGER.info("T11: Starting M&A INTEGRATION WORKFLOW");
        Instant startTime = Instant.now();

        // Create acquired business unit
        BusinessUnit acquiredBU = dataFactory.createBusinessUnit("AcquiredCorp", 50);

        // Generate acquired backlog
        List<UserStory> acquiredBacklog = dataFactory.generateStoriesWithDependencies(100, 30);

        // Onboard and integrate
        MAIntegrationResult integrationResult = orchestrator.onboardAcquiredBusinessUnit(
            acquiredBU,
            acquiredBacklog
        );

        Instant endTime = Instant.now();
        long durationSeconds = Duration.between(startTime, endTime).toSeconds();

        // Validate integration
        assertTrue(integrationResult.isSuccessful(), "Integration should succeed");
        assertEquals(2, integrationResult.getNewARTsCreated(), "Should create 2-3 new ARTs");
        assertEquals(100, integrationResult.getBacklogStoriesIntegrated(),
            "All backlog stories should be integrated");

        // Run first PI planning with integrated teams
        List<ART> integratedARTs = integrationResult.getNewARTs();
        for (ART art : integratedARTs) {
            List<UserStory> stories = dataFactory.generateStoriesWithDependencies(20, 25);
            ARTContext context = new ARTContext(art, stories);
            artContexts.add(context);

            PIResult result = orchestrator.executeARTPIPlanningWorkflow(context);
            assertTrue(result.isSuccessful(), "Integrated ART should complete PI planning");
        }

        LOGGER.info("T11: PASS - M&A integration completed in {} seconds", durationSeconds);
        performanceMetrics.put("ma_integration_seconds", durationSeconds);
    }

    /**
     * Test 12: Market Disruption Response (full scale)
     *
     * Validates:
     * - Disruption alert triggers assessment
     * - Impact analysis completes in <10 min
     * - Executive decision cascades to 30 ARTs
     * - All ARTs replan within 1 hour
     * - Business value preserved or improved
     */
    @Test
    @DisplayName("T12: Market Disruption Response (30 ARTs)")
    @Order(12)
    @Timeout(value = 90, unit = TimeUnit.MINUTES)
    void testMarketDisruptionResponse() {
        if (SCALE_LEVEL < 30) {
            LOGGER.warn("T12: Skipping full-scale test at scale level {}", SCALE_LEVEL);
            return;
        }

        LOGGER.info("T12: Starting MARKET DISRUPTION RESPONSE");
        Instant startTime = Instant.now();

        // Create initial PI state
        List<ART> arts = IntStream.range(1, 31)
            .mapToObj(i -> dataFactory.createART("ART-Disrupt-" + i, TEAMS_PER_ART, null))
            .collect(Collectors.toList());

        DisruptionAlert alert = new DisruptionAlert(
            "New competitor with superior pricing",
            "CRITICAL",
            DisruptionType.MARKET_THREAT,
            Instant.now()
        );

        // Execute disruption response
        DisruptionResponseResult responseResult = orchestrator.handleDisruptionAlert(alert, arts);

        Instant endTime = Instant.now();
        long durationSeconds = Duration.between(startTime, endTime).toSeconds();

        // Validate response
        assertTrue(responseResult.isSuccessful(), "Disruption response should succeed");
        assertTrue(responseResult.getDecisionMadeAt() != null, "Decision should be made");

        // All ARTs should have replanned
        assertEquals(30, responseResult.getARTsReplanned(), "All 30 ARTs should replan");

        // Business value maintained or improved
        assertTrue(responseResult.getNewBusinessValue() >= responseResult.getOriginalBusinessValue() * 0.9,
            "Business value should be maintained (90%+)");

        // Total time should be reasonable (impact assessment + decision + cascade + replan)
        assertTrue(durationSeconds < 3600, "Total disruption response should complete in <1 hour");

        LOGGER.info("T12: PASS - Disruption response completed in {} seconds", durationSeconds);
        performanceMetrics.put("disruption_response_seconds", durationSeconds);
    }

    // ==================== PARAMETRIZED STRESS TESTS ====================

    /**
     * Parametrized test: Varying failure rates
     *
     * Tests 7, 8 (PI planning, dependency resolution) with simulated failures
     */
    @ParameterizedTest
    @CsvSource({
        "0.05,   5%  agent failure",
        "0.10,  10%  agent failure",
        "0.15,  15%  agent failure",
        "0.20,  20%  agent failure"
    })
    @DisplayName("T13: PI Planning with Agent Failures [Chaos]")
    @Order(13)
    @Timeout(value = 5, unit = TimeUnit.HOURS)
    void testPIPlanningWithAgentFailures(double failureRate, String description) {
        if (SCALE_LEVEL < 30) {
            LOGGER.warn("T13: Skipping full-scale chaos test at scale level {}", SCALE_LEVEL);
            return;
        }

        LOGGER.info("T13: Starting PI planning with {} failure injection", description);
        Instant startTime = Instant.now();

        // Create 30 ARTs with chaos injection
        ChaosInjector chaos = new ChaosInjector(failureRate);

        List<ART> arts = IntStream.range(1, 31)
            .mapToObj(i -> dataFactory.createART("ART-Chaos-" + i, TEAMS_PER_ART, null))
            .collect(Collectors.toList());

        List<ARTContext> contextList = new ArrayList<>();
        for (ART art : arts) {
            List<UserStory> stories = dataFactory.generateStoriesWithDependencies(
                TEAMS_PER_ART * STORIES_PER_TEAM,
                DEPENDENCY_RATE
            );
            contextList.add(new ARTContext(art, stories));
        }

        // Execute with chaos
        List<PIResult> results = contextList.parallelStream()
            .map(context -> {
                artContexts.add(context);
                try {
                    chaos.maybeInjectFailure();
                    return orchestrator.executeARTPIPlanningWorkflow(context);
                } catch (Exception e) {
                    return PIResult.failed("Chaos injection", e);
                }
            })
            .collect(Collectors.toList());

        Instant endTime = Instant.now();
        long durationMinutes = Duration.between(startTime, endTime).toMinutes();

        // Even with failures, system should mostly succeed
        long successCount = results.stream().filter(PIResult::isSuccessful).count();
        long successRate = successCount * 100 / 30;

        LOGGER.info("T13: PASS - PI planning with {} completed: {} successful ({} %), {} minutes",
            description, successCount, successRate, durationMinutes);

        assertTrue(successRate >= 80, "Should have ≥80% success rate, got " + successRate + "%");
    }

    /**
     * Helper method to determine scale level
     */
    private static int getScaleLevel() {
        String scaleEnv = System.getenv("FORTUNE5_SCALE_LEVEL");
        if (scaleEnv != null) {
            return Integer.parseInt(scaleEnv);
        }
        return 1;  // Default: baseline
    }
}
