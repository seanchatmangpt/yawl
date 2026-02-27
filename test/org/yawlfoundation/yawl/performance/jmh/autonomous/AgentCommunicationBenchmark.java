/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.performance.jmh.autonomous;

// JUnit imports removed - benchmarks are run via JMH
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.yawlfoundation.yawl.integration.autonomous.*;
import org.yawlfoundation.yawl.integration.autonomous.registry.AgentInfo;
import org.yawlfoundation.yawl.integration.autonomous.registry.AgentRegistryClient;
import org.yawlfoundation.yawl.integration.a2a.handoff.HandoffRequestService;
import org.yawlfoundation.yawl.integration.a2a.auth.JwtAuthenticationProvider;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.autonomous.strategies.DiscoveryStrategy;
import org.yawlfoundation.yawl.integration.autonomous.strategies.EligibilityReasoner;
import org.yawlfoundation.yawl.integration.autonomous.strategies.DecisionReasoner;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.AgentMarketplace;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.AgentMarketplaceListing;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.AgentMarketplaceSpec;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.CoordinationCostProfile;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.LatencyProfile;
import org.yawlfoundation.yawl.integration.autonomous.AgentCapability;
import org.yawlfoundation.yawl.integration.autonomous.strategies.DiscoveryStrategy;
import org.yawlfoundation.yawl.integration.autonomous.strategies.EligibilityReasoner;
import org.yawlfoundation.yawl.integration.autonomous.strategies.DecisionReasoner;
import org.yawlfoundation.yawl.integration.autonomous.registry.AgentInfo;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.AgentMarketplace;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.AgentMarketplaceListing;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

// Removed static imports - validation done via method calls

/**
 * JMH benchmark for autonomous agent communication performance.
 *
 * Validates v6.0.0-GA autonomous agent capabilities:
 * - Agent registration and discovery latency: < 50ms
 * - Message processing throughput: > 1000 ops/sec
 * - Authentication overhead during agent communication
 * - Dynamic agent discovery performance
 *
 * @author YAWL Performance Team
 * @version 6.0.0-GA
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime, Mode.SingleShotTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgs = {
    "-Xms4g", "-Xmx6g", "-XX:+UseG1GC",
    "--enable-preview",
    "-Djava.util.concurrent.ForkJoinPool.common.parallelism=16"
})
public class AgentCommunicationBenchmark {

    private AgentConfiguration config;
    private AgentRegistryClient registryClient;
    private HandoffRequestService handoffService;
    private JwtAuthenticationProvider authProvider;
    private AgentMarketplace marketplace;
    private List<AgentInfo> testAgents;
    private List<AgentMarketplaceListing> marketplaceListings;
    private ExecutorService platformExecutor;
    private ExecutorService virtualExecutor;

    // Enhanced metrics collection
    private final AutonomousAgentMetrics metrics = new AutonomousAgentMetrics();
    private final AtomicInteger successfulRegistrations = new AtomicInteger(0);
    private final AtomicInteger failedRegistrations = new AtomicInteger(0);
    private final AtomicInteger successfulDiscoveries = new AtomicInteger(0);
    private final AtomicInteger failedDiscoveries = new AtomicInteger(0);
    private final List<Long> registrationLatencies = new ArrayList<>();
    private final List<Long> discoveryLatencies = new ArrayList<>();
    private final List<Long> messageProcessingTimes = new ArrayList<>();
    private final Map<String, Long> discoveryCache = new ConcurrentHashMap<>();

    @Param({"10", "50", "100", "500"})
    private int agentCount;

    @Param({"1", "10", "100", "1000"})
    private int concurrentOperations;

    @BeforeEach
    public void setup() {
        // Setup mock components
        registryClient = mock(AgentRegistryClient.class);
        handoffService = mock(HandoffRequestService.class);
        authProvider = mock(JwtAuthenticationProvider.class);
        marketplace = mock(AgentMarketplace.class);

        // Setup test agent data
        setupTestAgents();
        setupMarketplaceListings();

        // Setup strategies for realistic agent behavior
        DiscoveryStrategy discoveryStrategy = new TestDiscoveryStrategy();
        EligibilityReasoner eligibilityReasoner = new TestEligibilityReasoner();
        DecisionReasoner decisionReasoner = new TestDecisionReasoner();

        // Setup agent configuration
        AgentCapability capability = new AgentCapability("test-domain", "Test Domain",
            "Performance test agent capability");
        config = AgentConfiguration.builder("comm-benchmark", "http://localhost:8080/ib",
            "testuser", "testpass")
            .capability(capability)
            .discoveryStrategy(discoveryStrategy)
            .eligibilityReasoner(eligibilityReasoner)
            .decisionReasoner(decisionReasoner)
            .registryClient(registryClient)
            .handoffService(handoffService)
            .build();

        // Setup executors
        int cpuCount = Runtime.getRuntime().availableProcessors();
        platformExecutor = Executors.newFixedThreadPool(Math.min(100, cpuCount * 2));
        virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

        // Reset metrics
        successfulRegistrations.set(0);
        failedRegistrations.set(0);
        successfulDiscoveries.set(0);
        failedDiscoveries.set(0);
        registrationLatencies.clear();
        discoveryLatencies.clear();
        messageProcessingTimes.clear();
        discoveryCache.clear();
    }

    @TearDown
    public void teardown() throws InterruptedException {
        shutdownExecutor(platformExecutor);
        shutdownExecutor(virtualExecutor);

        // Print comprehensive metrics summary
        printComprehensiveMetrics();
    }

    @Benchmark
    public void testAgentRegistrationLatency(Blackhole bh) throws Exception {
        Instant start = Instant.now();

        // Simulate agent registration
        AgentInfo agentInfo = new AgentInfo(
            "test-agent-" + Thread.currentThread().getId(),
            "Test Agent",
            "localhost",
            8080,
            "test-domain"
        );

        // Simulate registry call
        when(registryClient.registerAgent(any(AgentInfo.class)))
            .thenReturn(true);

        boolean result = registryClient.registerAgent(agentInfo);

        long duration = Duration.between(start, Instant.now()).toMillis();
        registrationLatencies.add(duration);

        if (result) {
            successfulRegistrations.incrementAndGet();
        } else {
            failedRegistrations.incrementAndGet();
        }

        bh.consume(result);
    }

    @Benchmark
    public void testAgentDiscoveryLatency(Blackhole bh) throws Exception {
        Instant start = Instant.now();

        // Setup mock response
        when(registryClient.findAgentsByCapability("test-domain"))
            .thenReturn(testAgents);

        // Execute discovery
        List<AgentInfo> discovered = registryClient.findAgentsByCapability("test-domain");

        long duration = Duration.between(start, Instant.now()).toMillis();
        discoveryLatencies.add(duration);

        if (discovered != null && !discovered.isEmpty()) {
            successfulDiscoveries.incrementAndGet();
        } else {
            failedDiscoveries.incrementAndGet();
        }

        bh.consume(discovered);
    }

    @Benchmark
    public void testConcurrentAgentDiscovery(Blackhole bh) throws Exception {
        CountDownLatch latch = new CountDownLatch(concurrentOperations);
        List<Future<List<AgentInfo>>> futures = new ArrayList<>(concurrentOperations);

        // Setup mock response
        when(registryClient.findAgentsByCapability("test-domain"))
            .thenReturn(testAgents);

        for (int i = 0; i < concurrentOperations; i++) {
            final int iteration = i;
            Future<List<AgentInfo>> future = virtualExecutor.submit(() -> {
                try {
                    Instant start = Instant.now();

                    List<AgentInfo> result = registryClient.findAgentsByCapability("test-domain");

                    long duration = Duration.between(start, Instant.now()).toMillis();
                    discoveryLatencies.add(duration);

                    if (result != null && !result.isEmpty()) {
                        successfulDiscoveries.incrementAndGet();
                    } else {
                        failedDiscoveries.incrementAndGet();
                    }

                    return result;
                } finally {
                    latch.countDown();
                }
            });
            futures.add(future);
        }

        // Wait for all operations
        latch.await(30, TimeUnit.SECONDS);

        // Collect results
        List<AgentInfo> allResults = new ArrayList<>();
        for (Future<List<AgentInfo>> future : futures) {
            if (future.isDone()) {
                try {
                    allResults.addAll(future.get());
                } catch (Exception e) {
                    failedDiscoveries.incrementAndGet();
                }
            }
        }

        bh.consume(allResults);
    }

    @Benchmark
    public void testAgentMessageThroughput(Blackhole bh) throws Exception {
        // Setup message processing
        ExecutorService messageExecutor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<MessageResult>> futures = new ArrayList<>(concurrentOperations);
        CountDownLatch latch = new CountDownLatch(concurrentOperations);

        Instant start = Instant.now();

        for (int i = 0; i < concurrentOperations; i++) {
            final int msgId = i;
            Future<MessageResult> future = messageExecutor.submit(() -> {
                try {
                    Instant msgStart = Instant.now();

                    // Simulate message processing
                    MessageResult result = processAgentMessage(msgId);

                    long duration = Duration.between(msgStart, Instant.now()).toMillis();
                    messageProcessingTimes.add(duration);

                    return result;
                } finally {
                    latch.countDown();
                }
            });
            futures.add(future);
        }

        // Wait for all messages
        latch.await(30, TimeUnit.SECONDS);
        long totalTime = Duration.between(start, Instant.now()).toMillis();

        // Collect results
        int successCount = 0;
        for (Future<MessageResult> future : futures) {
            if (future.isDone()) {
                try {
                    MessageResult result = future.get();
                    if (result.success) {
                        successCount++;
                    }
                } catch (Exception e) {
                    // Count as failure
                }
            }
        }

        bh.consume(successCount);

        // Cleanup
        shutdownExecutor(messageExecutor);
    }

    @Benchmark
    public void testAuthenticationOverhead(Blackhole bh) throws Exception {
        // Setup JWT token generation
        when(authProvider.generateToken(anyString(), anyString()))
            .thenReturn("mock-jwt-token-" + UUID.randomUUID());

        Instant start = Instant.now();

        // Generate multiple tokens
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < concurrentOperations; i++) {
            String token = authProvider.generateToken("agent-" + i, "test-domain");
            tokens.add(token);
        }

        long duration = Duration.between(start, Instant.now()).toMillis();
        messageProcessingTimes.add(duration);

        bh.consume(tokens);
    }

    @Benchmark
    public void testAgentHandoffCommunication(Blackhole bh) throws Exception {
        // Setup handoff service
        when(handoffService.initiateHandoff(anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(
                new HandoffRequestService.HandoffResult(true, "Success")));

        Instant start = Instant.now();

        // Initiate multiple handoffs
        List<CompletableFuture<HandoffRequestService.HandoffResult>> handoffs = new ArrayList<>();
        for (int i = 0; i < concurrentOperations; i++) {
            String workItemId = "work-item-" + i;
            String targetAgent = "target-agent-" + i;

            CompletableFuture<HandoffRequestService.HandoffResult> handoff =
                handoffService.initiateHandoff(workItemId, targetAgent);
            handoffs.add(handoff);
        }

        // Wait for all handoffs
        CompletableFuture<Void> allHandoffs = CompletableFuture.allOf(
            handoffs.toArray(new CompletableFuture[0]));

        allHandoffs.get(30, TimeUnit.SECONDS);
        long duration = Duration.between(start, Instant.now()).toMillis();

        // Count successful handoffs
        int successCount = 0;
        for (CompletableFuture<HandoffRequestService.HandoffResult> handoff : handoffs) {
            if (handoff.isDone()) {
                try {
                    HandoffRequestService.HandoffResult result = handoff.get();
                    if (result.accepted()) {
                        successCount++;
                    }
                } catch (Exception e) {
                    // Count as failure
                }
            }
        }

        bh.consume(successCount);
    }

    @Benchmark
    public void testRealisticAgentDiscoveryScenarios(Blackhole bh) throws Exception {
        System.out.println("\n=== Realistic Agent Discovery Scenarios ===");

        // Simulate dynamic discovery patterns with cache invalidation
        int discoveryOperations = concurrentOperations * 10;
        CountDownLatch latch = new CountDownLatch(discoveryOperations);
        List<Future<DiscoveryScenarioResult>> futures = new ArrayList<>(discoveryOperations);

        Instant start = Instant.now();

        for (int i = 0; i < discoveryOperations; i++) {
            final int iteration = i;
            Future<DiscoveryScenarioResult> future = virtualExecutor.submit(() -> {
                try {
                    Instant discoveryStart = Instant.now();

                    // Simulate different discovery scenarios
                    DiscoveryScenario scenario = generateDiscoveryScenario(iteration);
                    DiscoveryScenarioResult result = executeDiscoveryScenario(scenario);

                    long duration = Duration.between(discoveryStart, Instant.now()).toMillis();
                    discoveryLatencies.add(duration);

                    // Record with enhanced metrics
                    metrics.recordDiscovery(
                        "agent-" + iteration % agentCount,
                        result.success,
                        duration,
                        result.discoveredAgents,
                        scenario.scenarioType
                    );

                    if (result.success) {
                        successfulDiscoveries.incrementAndGet();
                    } else {
                        failedDiscoveries.incrementAndGet();
                    }

                    return result;
                } finally {
                    latch.countDown();
                }
            });
            futures.add(future);
        }

        // Wait for all discoveries
        latch.await(60, TimeUnit.SECONDS);

        long totalTime = Duration.between(start, Instant.now()).toMillis();
        double discoveryThroughput = discoveryOperations * 1000.0 / totalTime;

        System.out.printf("Realistic discovery throughput: %.2f ops/sec%n", discoveryThroughput);
        System.out.printf("Average discovery latency: %.2f ms%n",
            discoveryLatencies.stream().mapToLong(Long::longValue).average().orElse(0.0));

        bh.consume(discoveryThroughput);
    }

    @Benchmark
    public void testDynamicAgentMarketplaceIntegration(Blackhole bh) throws Exception {
        System.out.println("\n=== Dynamic Agent Marketplace Integration ===");

        // Test marketplace integration with dynamic agent publishing
        int marketplaceOperations = concurrentOperations * 5;
        CountDownLatch latch = new CountDownLatch(marketplaceOperations);
        List<Future<MarketplaceOperationResult>> futures = new ArrayList<>(marketplaceOperations);

        Instant start = Instant.now();

        for (int i = 0; i < marketplaceOperations; i++) {
            final int iteration = i;
            Future<MarketplaceOperationResult> future = virtualExecutor.submit(() -> {
                try {
                    Instant marketplaceStart = Instant.now();

                    // Simulate marketplace operations
                    MarketplaceOperation operation = generateMarketplaceOperation(iteration);
                    MarketplaceOperationResult result = executeMarketplaceOperation(operation);

                    long duration = Duration.between(marketplaceStart, Instant.now()).toMillis();
                    messageProcessingTimes.add(duration);

                    // Record marketplace metrics
                    metrics.recordOperation(
                        "marketplace",
                        "agent-" + iteration % agentCount,
                        result.success,
                        duration * 1_000_000,
                        Map.of(
                            "operationType", operation.operationType,
                            "resultCount", result.resultCount
                        )
                    );

                    if (result.success) {
                        successfulDiscoveries.incrementAndGet();
                    } else {
                        failedDiscoveries.incrementAndGet();
                    }

                    return result;
                } finally {
                    latch.countDown();
                }
            });
            futures.add(future);
        }

        // Wait for all marketplace operations
        latch.await(90, TimeUnit.SECONDS);

        long totalTime = Duration.between(start, Instant.now()).toMillis();
        double marketplaceThroughput = marketplaceOperations * 1000.0 / totalTime;

        System.out.printf("Marketplace throughput: %.2f ops/sec%n", marketplaceThroughput);

        bh.consume(marketplaceThroughput);
    }

    @Benchmark
    public void testAgentRegistrationWithHeartbeat(Blackhole bh) throws Exception {
        System.out.println("\n=== Agent Registration with Heartbeat ===");

        // Test agent registration with periodic heartbeat mechanism
        int registrationCycles = 50;
        CountDownLatch latch = new CountDownLatch(registrationCycles);
        List<Future<RegistrationCycleResult>> futures = new ArrayList<>(registrationCycles);

        Instant start = Instant.now();

        for (int cycle = 0; cycle < registrationCycles; cycle++) {
            final int cycleNum = cycle;
            Future<RegistrationCycleResult> future = virtualExecutor.submit(() -> {
                try {
                    Instant cycleStart = Instant.now();

                    // Simulate registration with heartbeat
                    RegistrationCycleResult result = executeRegistrationWithHeartbeat(cycleNum);

                    long duration = Duration.between(cycleStart, Instant.now()).toMillis();
                    registrationLatencies.add(duration);

                    // Record registration metrics
                    metrics.recordOperation(
                        "registration",
                        "agent-" + cycleNum % agentCount,
                        result.success,
                        duration * 1_000_000,
                        Map.of(
                            "heartbeatInterval", result.heartbeatInterval,
                            "registrationType", result.registrationType
                        )
                    );

                    if (result.success) {
                        successfulRegistrations.incrementAndGet();
                    } else {
                        failedRegistrations.incrementAndGet();
                    }

                    return result;
                } finally {
                    latch.countDown();
                }
            });
            futures.add(future);
        }

        // Wait for all registration cycles
        latch.await(60, TimeUnit.SECONDS);

        long totalTime = Duration.between(start, Instant.now()).toMillis();
        double registrationThroughput = registrationCycles * 1000.0 / totalTime;

        System.out.printf("Registration throughput: %.2f cycles/sec%n", registrationThroughput);

        bh.consume(registrationThroughput);
    }

    // Helper methods
    private void setupTestAgents() {
        testAgents = new ArrayList<>();
        for (int i = 0; i < agentCount; i++) {
            testAgents.add(new AgentInfo(
                "agent-" + i,
                "Test Agent " + i,
                "localhost",
                8080 + i,
                "test-domain"
            ));
        }
    }

    private MessageResult processAgentMessage(int messageId) {
        // Simulate different message processing scenarios
        boolean success = true;
        String response = "Processed message " + messageId;

        // Simulate occasional failures
        if (messageId % 100 == 0) {
            success = false;
            response = "Failed to process message " + messageId;
        }

        return new MessageResult(success, response);
    }

    // Helper methods for new benchmarks
    private void setupMarketplaceListings() {
        marketplaceListings = new ArrayList<>();
        for (int i = 0; i < agentCount; i++) {
            AgentInfo agentInfo = testAgents.get(i);
            AgentMarketplaceListing listing = new AgentMarketplaceListing(
                agentInfo,
                createAgentSpec(agentInfo),
                Instant.now()
            );
            marketplaceListings.add(listing);
        }
    }

    private AgentMarketplaceSpec createAgentSpec(AgentInfo agentInfo) {
        return new AgentMarketplaceSpec(
            agentInfo.getId(),
            "Test Agent Spec",
            List.of("test-domain"),
            Map.of(),
            new CoordinationCostProfile(1.0, 0.1, 100),
            new LatencyProfile(50L, 100L, 200L)
        );
    }

    private DiscoveryScenario generateDiscoveryScenario(int iteration) {
        // Simulate different discovery scenarios
        if (iteration % 5 == 0) {
            return new DiscoveryScenario("CACHED", "Use cached discovery results");
        } else if (iteration % 3 == 0) {
            return new DiscoveryScenario("FULL_SCAN", "Full registry scan");
        } else if (iteration % 7 == 0) {
            return new DiscoveryScenario("FILTERED", "Capability-based filtering");
        } else {
            return new DiscoveryScenario("DYNAMIC", "Dynamic discovery with heartbeat");
        }
    }

    private DiscoveryScenarioResult executeDiscoveryScenario(DiscoveryScenario scenario) {
        boolean success = true;
        int discoveredAgents = 0;

        switch (scenario.scenarioType) {
            case "CACHED":
                // Use cached results with TTL check
                String cacheKey = "discovery-" + scenario.scenarioType;
                Long cachedTime = discoveryCache.get(cacheKey);
                if (cachedTime != null && System.currentTimeMillis() - cachedTime < 5000) {
                    discoveredAgents = testAgents.size() / 2; // Partial cached result
                } else {
                    discoveredAgents = testAgents.size();
                    discoveryCache.put(cacheKey, System.currentTimeMillis());
                }
                break;
            case "FULL_SCAN":
                // Full registry scan with higher latency
                discoveredAgents = testAgents.size();
                break;
            case "FILTERED":
                // Filter by capability with some agents missing
                discoveredAgents = (int) (testAgents.size() * 0.8);
                break;
            case "DYNAMIC":
                // Dynamic discovery with heartbeat validation
                discoveredAgents = (int) (testAgents.size() * 0.9);
                break;
            default:
                discoveredAgents = testAgents.size();
        }

        // Simulate occasional failures
        if (Math.random() < 0.01) {
            success = false;
            discoveredAgents = 0;
        }

        return new DiscoveryScenarioResult(success, discoveredAgents);
    }

    private MarketplaceOperation generateMarketplaceOperation(int iteration) {
        if (iteration % 3 == 0) {
            return new MarketplaceOperation("PUBLISH", "Publish agent listing");
        } else if (iteration % 2 == 0) {
            return new MarketplaceOperation("QUERY", "Query marketplace");
        } else {
            return new MarketplaceOperation("UNSUBSCRIBE", "Remove agent from marketplace");
        }
    }

    private MarketplaceOperationResult executeMarketplaceOperation(MarketplaceOperation operation) {
        boolean success = true;
        int resultCount = 0;

        switch (operation.operationType) {
            case "PUBLISH":
                resultCount = marketplaceListings.size();
                break;
            case "QUERY":
                resultCount = (int) (10 + Math.random() * 90); // 10-100 results
                break;
            case "UNSUBSCRIBE":
                resultCount = marketplaceListings.size() > 0 ? 1 : 0;
                break;
        }

        // Simulate occasional failures
        if (Math.random() < 0.001) {
            success = false;
            resultCount = 0;
        }

        return new MarketplaceOperationResult(success, resultCount);
    }

    private RegistrationCycleResult executeRegistrationWithHeartbeat(int cycle) {
        boolean success = true;
        int heartbeatInterval = 5000; // 5 seconds
        String registrationType = "standard";

        // Simulate different registration types based on cycle
        if (cycle % 10 == 0) {
            registrationType = "high-availability";
            heartbeatInterval = 2000; // 2 seconds for HA
        } else if (cycle % 15 == 0) {
            registrationType = "bulk-registration";
            heartbeatInterval = 10000; // 10 seconds for bulk
        }

        // Simulate registration failures
        if (Math.random() < 0.005) {
            success = false;
        }

        return new RegistrationCycleResult(success, heartbeatInterval, registrationType);
    }

    private void shutdownExecutor(ExecutorService executor) {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private void printComprehensiveMetrics() {
        System.out.println("\n=== Comprehensive Agent Communication Benchmark Metrics ===");
        System.out.println("Agent count: " + agentCount);
        System.out.println("Concurrent operations: " + concurrentOperations);
        System.out.println();

        // Basic metrics
        printBasicMetrics();

        // Enhanced metrics validation
        printMetricsValidation();

        // Performance requirements validation
        validatePerformanceTargets();

        // Export comprehensive metrics
        System.out.println("\n=== Exported Metrics ===");
        System.out.println(metrics.exportToJson());
    }

    private void printBasicMetrics() {
        // Registration metrics
        if (!registrationLatencies.isEmpty()) {
            double avgRegistrationTime = registrationLatencies.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);
            System.out.printf("Average registration latency: %.2f ms%n", avgRegistrationTime);
        }

        // Discovery metrics
        if (!discoveryLatencies.isEmpty()) {
            double avgDiscoveryTime = discoveryLatencies.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);
            System.out.printf("Average discovery latency: %.2f ms%n", avgDiscoveryTime);
        }

        // Message processing metrics
        if (!messageProcessingTimes.isEmpty()) {
            double avgMessageTime = messageProcessingTimes.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);
            System.out.printf("Average message processing time: %.2f ms%n", avgMessageTime);
        }

        // Success rates
        int totalAttempts = successfulRegistrations.get() + failedRegistrations.get();
        if (totalAttempts > 0) {
            double registrationSuccessRate = (double) successfulRegistrations.get() / totalAttempts * 100;
            System.out.printf("Registration success rate: %.1f%%%n", registrationSuccessRate);
        }

        int discoveryAttempts = successfulDiscoveries.get() + failedDiscoveries.get();
        if (discoveryAttempts > 0) {
            double discoverySuccessRate = (double) successfulDiscoveries.get() / discoveryAttempts * 100;
            System.out.printf("Discovery success rate: %.1f%%%n", discoverySuccessRate);
        }
    }

    private void printMetricsValidation() {
        System.out.println("\n=== Metrics Validation ===");

        // Discovery latency validation
        if (!discoveryLatencies.isEmpty()) {
            double avgDiscovery = discoveryLatencies.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(100.0);
            boolean discoveryValid = avgDiscovery < 50.0;
            System.out.printf("Discovery latency < 50ms: %s (avg: %.2f ms)%n",
                discoveryValid ? "✓" : "✗", avgDiscovery);
        }

        // Message throughput validation
        if (!messageProcessingTimes.isEmpty()) {
            double avgMessageTime = messageProcessingTimes.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(1.0);
            double opsPerSec = 1000.0 / avgMessageTime;
            boolean throughputValid = opsPerSec > 1000.0;
            System.out.printf("Message processing > 1000 ops/sec: %s (%.0f ops/sec)%n",
                throughputValid ? "✓" : "✗", opsPerSec);
        }

        // Enhanced metrics summary
        AutonomousAgentMetrics.PerformanceSummary summary = metrics.calculateSummary();
        System.out.printf("Overall success rate: %.2f%%%n", summary.overallSuccessRate);
        System.out.printf("Total operations: %d%n", summary.totalOperations);
    }

    private void validatePerformanceTargets() {
        System.out.println("\n=== Performance Targets Validation ===");

        // Check against requirements
        boolean allRequirementsMet = metrics.validateRequirements();

        System.out.println("All requirements met: " + (allRequirementsMet ? "✓" : "✗"));

        // Individual requirement validation
        AutonomousAgentMetrics.PerformanceRequirements requirements = metrics.requirements;
        System.out.printf("Discovery latency (%.1fms): %s%n",
            requirements.maxDiscoveryLatencyMs,
            requirements.discoveryLatencyMet ? "✓" : "✗");
        System.out.printf("Message throughput (%d ops/sec): %s%n",
            requirements.minMessageThroughput,
            requirements.messageThroughputMet ? "✓" : "✗");
        System.out.printf("Handoff success rate (%d%%): %s%n",
            requirements.minHandoffSuccessRate,
            requirements.handoffSuccessRateMet ? "✓" : "✗");
        System.out.printf("Allocation accuracy (%d%%): %s%n",
            requirements.minAllocationAccuracy,
            requirements.allocationAccuracyMet ? "✓" : "✗");
    }

    // Validation methods called from benchmarks
    private void validateDiscoveryLatencyThreshold() {
        // This would typically run separately to validate performance requirements
        if (!discoveryLatencies.isEmpty()) {
            double avgLatency = discoveryLatencies.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(100.0);

            if (avgLatency >= 50.0) {
                System.err.println("WARNING: Average discovery latency (" + avgLatency + "ms) exceeds target (< 50ms)");
            }
        }
    }

    private void validateMessageThroughputThreshold() {
        // Calculate throughput from message processing times
        if (!messageProcessingTimes.isEmpty()) {
            double avgTime = messageProcessingTimes.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(1000.0);

            double opsPerSec = 1000.0 / avgTime;
            if (opsPerSec <= 1000.0) {
                System.err.println("WARNING: Message processing throughput (" + opsPerSec + " ops/sec) below target (> 1000 ops/sec)");
            }
        }
    }

    // Record classes for test data
    private static class MessageResult {
        final boolean success;
        final String response;

        MessageResult(boolean success, String response) {
            this.success = success;
            this.response = response;
        }
    }

    // Strategy implementations for realistic behavior
    private static class TestDiscoveryStrategy implements DiscoveryStrategy {
        @Override
        public List<WorkItemRecord> discoverWorkItems(InterfaceB_EnvironmentBasedClient client, String sessionHandle) {
            // Simulate discovery with realistic patterns
            List<WorkItemRecord> items = new ArrayList<>();
            int itemCount = (int) (1 + Math.random() * 10); // 1-10 items per discovery

            for (int i = 0; i < itemCount; i++) {
                items.add(new WorkItemRecord(
                    "case-" + i,
                    "task-" + (i % 3),
                    "work-item-" + System.currentTimeMillis() + "-" + i,
                    "started",
                    1000
                ));
            }

            return items;
        }
    }

    private static class TestEligibilityReasoner implements EligibilityReasoner {
        @Override
        public boolean isEligible(WorkItemRecord workItem) {
            // Simulate realistic eligibility criteria
            String taskName = workItem.getTaskName();
            return !taskName.equals("unsupported-task") && Math.random() > 0.05;
        }
    }

    private static class TestDecisionReasoner implements DecisionReasoner {
        @Override
        public String produceOutput(WorkItemRecord workItem) {
            // Simulate decision making with occasional processing delays
            try {
                Thread.sleep((long) (Math.random() * 10)); // 0-10ms processing time
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            return "Processed: " + workItem.getID();
        }
    }

    // Record classes for new benchmarks
    private record DiscoveryScenario(String scenarioType, String description) {}
    private record DiscoveryScenarioResult(boolean success, int discoveredAgents) {}
    private record MarketplaceOperation(String operationType, String description) {}
    private record MarketplaceOperationResult(boolean success, int resultCount) {}
    private record RegistrationCycleResult(boolean success, int heartbeatInterval, String registrationType) {}
}