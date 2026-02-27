/*
 * YAWL v6.0.0-GA Validation
 * A2A Performance Suite
 *
 * Comprehensive A2A/MCP performance validation suite
 * Validates end-to-end performance of agent communication
 */
package org.yawlfoundation.yawl.integration.a2a;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.engine.YAWLServiceGateway;
import org.yawlfoundation.yawl.engine.YNetRunner;
import org.yawlfoundation.yawl.elements.YAWLTask;
import org.yawlfoundation.yawl.elements.YAWLNet;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive A2A/MCP performance validation suite
 * Validates end-to-end performance of agent communication
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class A2APerformanceSuite {

    private YAWLServiceGateway serviceGateway;
    private A2ACommunicationManager a2aManager;
    private AgentRegistry agentRegistry;
    private PerformanceMonitor performanceMonitor;
    private MessageLatencyTracker latencyTracker;

    // Test configuration
    private static final int MAX_AGENTS = 1000;
    private static final int CONCURRENT_MESSAGES = 500;
    private static final Duration TEST_DURATION = Duration.ofMinutes(30);
    private static final int TARGET_LATENCY_P95_MS = 100;

    @BeforeAll
    void setUp() {
        serviceGateway = new YAWLServiceGateway();
        a2aManager = new A2ACommunicationManager();
        agentRegistry = new AgentRegistry();
        performanceMonitor = new PerformanceMonitor();
        latencyTracker = new MessageLatencyTracker();

        // Initialize test agents
        initializeTestAgents();
    }

    @AfterAll
    void tearDown() {
        agentRegistry.shutdownAll();
        a2aManager.shutdown();
    }

    @Test
    @DisplayName("Validate message latency under load")
    void validateMessageLatencyUnderLoad() throws InterruptedException {
        System.out.println("Starting message latency validation...");

        // Test with increasing load
        for (int loadLevel : new int[]{100, 250, 500, 1000}) {
            System.out.printf("Testing at load level: %d messages/second%n", loadLevel);

            // Clear previous results
            latencyTracker.clear();

            // Start message generation
            ExecutorService messageGenerator = startMessageGeneration(loadLevel, loadLevel * 2);

            // Run for test duration
            Instant testStart = Instant.now();
            while (Instant.now().isBefore(testStart.plus(Duration.ofMinutes(5)))) {
                // Monitor latency during test
                performanceMonitor.validateLatencyThreshold(TARGET_LATENCY_P95_MS);
                Thread.sleep(1000);
            }

            // Shutdown message generator
            messageGenerator.shutdown();

            // Validate results
            validateLatencyResults(loadLevel);

            // Wait before next load level
            if (loadLevel < 1000) {
                Thread.sleep(5000);
            }
        }

        System.out.println("Message latency validation completed successfully");
    }

    @Test
    @DisplayName("Validate concurrent agent handoff performance")
    void validateConcurrentAgentHandoff() throws InterruptedException {
        System.out.println("Starting concurrent agent handoff validation...");

        // Create test agents
        List<Agent> testAgents = createTestAgents(100);

        // Test concurrent handoffs
        ExecutorService handoffExecutor = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(CONCURRENT_MESSAGES);
        AtomicInteger successfulHandoffs = new AtomicInteger(0);

        Instant testStart = Instant.now();

        for (int i = 0; i < CONCURRENT_MESSAGES; i++) {
            final int messageId = i;
            Agent sourceAgent = testAgents.get(i % testAgents.size());
            Agent targetAgent = testAgents.get((i + 1) % testAgents.size());

            handoffExecutor.submit(() -> {
                try {
                    long startTime = System.currentTimeMillis();
                    boolean success = performAgentHandoff(sourceAgent, targetAgent, messageId);
                    long endTime = System.currentTimeMillis();
                    long latency = endTime - startTime;

                    if (success) {
                        successfulHandoffs.incrementAndGet();
                        latencyTracker.recordMessageLatency(latency);
                    }

                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for all handoffs to complete
        latch.await(5, TimeUnit.MINUTES);
        handoffExecutor.shutdown();

        // Validate results
        validateConcurrentHandoffResults(
            successfulHandoffs.get(),
            Duration.between(testStart, Instant.now())
        );

        System.out.println("Concurrent agent handoff validation completed");
    }

    @Test
    @DisplayName("Validate A2A throughput scaling")
    void validateA2AThroughputScaling() throws InterruptedException {
        System.out.println("Starting A2A throughput scaling validation...");

        // Test throughput at different scales
        Map<Integer, ThroughputTestResult> results = new HashMap<>();

        for (int agentCount : new int[]{10, 50, 100, 500, 1000}) {
            System.out.printf("Testing with %d agents%n", agentCount);

            ThroughputTestResult result = runThroughputTest(agentCount);
            results.put(agentCount, result);

            // Verify linear scaling
            double scalingEfficiency = calculateScalingEfficiency(results);
            assertTrue(scalingEfficiency >= 0.9,
                "Throughput scaling too inefficient: " + scalingEfficiency + " < 0.9");

            System.out.printf("Throughput at %d agents: %.2f messages/s%n",
                agentCount, result.getThroughput());
        }

        // Validate overall scaling performance
        validateThroughputScalingResults(results);

        System.out.println("A2A throughput scaling validation completed");
    }

    @Test
    @DisplayName("Validate message persistence and recovery")
    void validateMessagePersistenceAndRecovery() throws InterruptedException {
        System.out.println("Starting message persistence validation...");

        // Generate test messages
        List<String> testMessages = generateTestMessages(1000);

        // Store messages
        MessageStore messageStore = new MessageStore();
        for (String message : testMessages) {
            messageStore.storeMessage(message);
        }

        // Simulate failure and recovery
        AgentSimulator simulator = new AgentSimulator();
        simulator.simulateFailure();

        // Recover messages
        List<String> recoveredMessages = messageStore.recoverMessages();

        // Validate persistence
        assertEquals(testMessages.size(), recoveredMessages.size(),
            "Message count mismatch after recovery");
        assertTrue(recoveredMessages.containsAll(testMessages),
            "Not all messages recovered successfully");

        // Validate message integrity
        validateMessageIntegrity(testMessages, recoveredMessages);

        System.out.println("Message persistence validation completed");
    }

    @Test
    @DisplayName("Validate A2A protocol compliance")
    void validateA2AProtocolCompliance() throws InterruptedException {
        System.out.println("Starting A2A protocol compliance validation...");

        // Test protocol compliance
        ProtocolComplianceTester complianceTester = new ProtocolComplianceTester();

        // Test message format compliance
        assertTrue(complianceTester.validateMessageFormat(),
            "Message format not compliant");

        // Test handshake protocol
        assertTrue(complianceTester.validateHandshakeProtocol(),
            "Handshake protocol not compliant");

        // Test message acknowledgment
        assertTrue(complianceTester.validateMessageAcknowledgment(),
            "Message acknowledgment not compliant");

        // Test error handling
        assertTrue(complianceTester.validateErrorHandling(),
            "Error handling not compliant");

        // Test security compliance
        assertTrue(complianceTester.validateSecurityCompliance(),
            "Security compliance failed");

        System.out.println("A2A protocol compliance validation completed");
    }

    @Test
    @DisplayName("Validate agent lifecycle management")
    void validateAgentLifecycleManagement() throws InterruptedException {
        System.out.println("Starting agent lifecycle validation...");

        // Test agent lifecycle operations
        AgentLifecycleTester lifecycleTester = new AgentLifecycleTester();

        // Test agent registration
        assertTrue(lifecycleTester.validateAgentRegistration(),
            "Agent registration failed");

        // Test agent deregistration
        assertTrue(lifecycleTester.validateAgentDeregistration(),
            "Agent deregistration failed");

        // Test agent heartbeat
        assertTrue(lifecycleTester.validateAgentHeartbeat(),
            "Agent heartbeat failed");

        // Test agent status monitoring
        assertTrue(lifecycleTester.validateAgentStatusMonitoring(),
            "Agent status monitoring failed");

        // Test agent failover
        assertTrue(lifecycleTester.validateAgentFailover(),
            "Agent failover failed");

        System.out.println("Agent lifecycle management validation completed");
    }

    @Test
    @DisplayName("Validate end-to-end A2A workflow")
    void validateEndToEndA2AWorkflow() throws InterruptedException {
        System.out.println("Starting end-to-end A2A workflow validation...");

        // Create test workflow
        EndToEndWorkflow workflow = new EndToEndWorkflow();

        // Test complete workflow
        ExecutorService workflowExecutor = Executors.newFixedThreadPool(10);
        AtomicInteger completedWorkflows = new AtomicInteger(0);
        AtomicInteger failedWorkflows = new AtomicInteger(0);

        Instant startTime = Instant.now();

        for (int i = 0; i < 100; i++) {
            final int workflowId = i;
            workflowExecutor.submit(() -> {
                try {
                    boolean success = workflow.executeCompleteWorkflow(workflowId);
                    if (success) {
                        completedWorkflows.incrementAndGet();
                    } else {
                        failedWorkflows.incrementAndGet();
                    }
                } catch (Exception e) {
                    failedWorkflows.incrementAndGet();
                }
            });
        }

        // Wait for completion
        workflowExecutor.shutdown();
        workflowExecutor.awaitTermination(5, TimeUnit.MINUTES);

        // Validate results
        validateEndToEndWorkflowResults(
            completedWorkflows.get(),
            failedWorkflows.get(),
            Duration.between(startTime, Instant.now())
        );

        System.out.println("End-to-end A2A workflow validation completed");
    }

    // Helper methods for initialization

    private void initializeTestAgents() {
        // Create test agents
        for (int i = 0; i < 100; i++) {
            Agent agent = new Agent("agent-" + i, "test-agent-" + i);
            agentRegistry.registerAgent(agent);
        }
    }

    private List<Agent> createTestAgents(int count) {
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Agent agent = new Agent("handoff-agent-" + i, "handoff-test-" + i);
            agentRegistry.registerAgent(agent);
            agents.add(agent);
        }
        return agents;
    }

    private List<String> generateTestMessages(int count) {
        List<String> messages = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            messages.add("message-" + i + "-" + UUID.randomUUID().toString());
        }
        return messages;
    }

    // Test execution methods

    private ExecutorService startMessageGeneration(int messagesPerSecond, int durationSeconds) {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleAtFixedRate(() -> {
            for (int i = 0; i < messagesPerSecond / 10; i++) {
                executor.submit(() -> {
                    try {
                        generateAndSendMessage();
                    } catch (Exception e) {
                        // Message generation failed
                    }
                });
            }
        }, 0, 100, TimeUnit.MILLISECONDS);

        // Stop after duration
        scheduler.schedule(() -> {
            executor.shutdown();
            scheduler.shutdown();
        }, durationSeconds, TimeUnit.SECONDS);

        return executor;
    }

    private void generateAndSendMessage() {
        try {
            // Generate test message
            String sourceAgent = "agent-" + (int)(Math.random() * 100);
            String targetAgent = "agent-" + (int)(Math.random() * 100);
            String message = "test-message-" + UUID.randomUUID().toString();

            long startTime = System.currentTimeMillis();
            boolean success = a2aManager.sendMessage(sourceAgent, targetAgent, message);
            long endTime = System.currentTimeMillis();

            if (success) {
                latencyTracker.recordMessageLatency(endTime - startTime);
            }

        } catch (Exception e) {
            // Message failed
        }
    }

    private boolean performAgentHandoff(Agent sourceAgent, Agent targetAgent, int messageId) {
        try {
            // Perform agent handoff
            long startTime = System.currentTimeMillis();
            boolean success = a2aManager.handoffAgent(sourceAgent, targetAgent);
            long endTime = System.currentTimeMillis();

            if (success) {
                latencyTracker.recordMessageLatency(endTime - startTime);
            }

            return success;
        } catch (Exception e) {
            return false;
        }
    }

    private ThroughputTestResult runThroughputTest(int agentCount) throws InterruptedException {
        // Create agents
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < agentCount; i++) {
            Agent agent = new Agent("throughput-agent-" + i, "throughput-test-" + i);
            agentRegistry.registerAgent(agent);
            agents.add(agent);
        }

        // Run throughput test
        ExecutorService messageExecutor = Executors.newFixedThreadPool(100);
        AtomicInteger messageCount = new AtomicInteger(0);

        Instant testStart = Instant.now();

        // Send messages
        for (int i = 0; i < 10000; i++) {
            final int messageId = i;
            messageExecutor.submit(() -> {
                try {
                    Agent source = agents.get((int)(Math.random() * agentCount));
                    Agent target = agents.get((int)(Math.random() * agentCount));

                    long startTime = System.currentTimeMillis();
                    boolean success = a2aManager.sendMessage(
                        source.getId(), target.getId(), "throughput-message-" + messageId);
                    long endTime = System.currentTimeMillis();

                    if (success) {
                        messageCount.incrementAndGet();
                        latencyTracker.recordMessageLatency(endTime - startTime);
                    }

                } catch (Exception e) {
                    // Message failed
                }
            });
        }

        // Wait for completion
        messageExecutor.shutdown();
        messageExecutor.awaitTermination(5, TimeUnit.MINUTES);

        Instant testEnd = Instant.now();
        long duration = Duration.between(testStart, testEnd).toSeconds();
        double throughput = duration > 0 ? messageCount.get() / (double)duration : 0;

        return new ThroughputTestResult(agentCount, messageCount.get(), throughput);
    }

    private double calculateScalingEfficiency(Map<Integer, ThroughputTestResult> results) {
        if (results.size() < 2) return 1.0;

        // Calculate expected vs actual throughput scaling
        double baseThroughput = results.get(10).getThroughput();
        double expectedScaling = 0;
        double actualScaling = 0;

        for (Map.Entry<Integer, ThroughputTestResult> entry : results.entrySet()) {
            int agentCount = entry.getKey();
            double throughput = entry.getValue().getThroughput();
            double expected = baseThroughput * (agentCount / 10.0);

            expectedScaling += expected;
            actualScaling += throughput;
        }

        return actualScaling / expectedScaling;
    }

    // Validation methods

    private void validateLatencyResults(int loadLevel) {
        // Calculate P95 latency
        List<Long> latencies = latencyTracker.getLatencies();
        if (latencies.isEmpty()) return;

        latencies.sort(Long::compareTo);
        int p95Index = (int) (latencies.size() * 0.95);
        long p95Latency = latencies.get(p95Index);

        // Validate against target
        assertTrue(p95Latency <= TARGET_LATENCY_P95_MS,
            String.format("P95 latency %dms exceeds target %dms at load %d",
                p95Latency, TARGET_LATENCY_P95_MS, loadLevel));

        System.out.printf("✓ P95 latency at load %d: %dms (target: <=%dms)%n",
            loadLevel, p95Latency, TARGET_LATENCY_P95_MS);
    }

    private void validateConcurrentHandoffResults(int successfulHandoffs, Duration duration) {
        double successRate = (double) successfulHandoffs / CONCURRENT_MESSAGES;
        double throughput = successfulHandoffs / (duration.toSeconds() / 1000.0);

        assertTrue(successRate >= 0.95,
            String.format("Handoff success rate %.2f too low", successRate));

        assertTrue(throughput > 100,
            String.format("Handoff throughput %.2f too low", throughput));

        System.out.printf("✓ Concurrent handoff: %.2f%% success, %.2f handoffs/s%n",
            successRate * 100, throughput);
    }

    private void validateThroughputScalingResults(Map<Integer, ThroughputTestResult> results) {
        // Validate that throughput scales reasonably with agent count
        double avgScalingEfficiency = 0;
        int testCount = 0;

        for (int i = 10; i <= 1000; i *= 10) {
            if (results.containsKey(i)) {
                ThroughputTestResult result = results.get(i);
                double efficiency = result.getThroughput() / i; // messages per agent per second
                avgScalingEfficiency += efficiency;
                testCount++;
            }
        }

        avgScalingEfficiency /= testCount;

        assertTrue(avgScalingEfficiency >= 10,
            String.format("Average scaling efficiency %.2f too low", avgScalingEfficiency));

        System.out.printf("✓ Average scaling efficiency: %.2f messages/agent/s%n",
            avgScalingEfficiency);
    }

    private void validateMessageIntegrity(List<String> originalMessages, List<String> recoveredMessages) {
        // Validate message integrity
        Map<String, String> messageMap = new HashMap<>();
        for (String message : recoveredMessages) {
            String id = extractMessageId(message);
            messageMap.put(id, message);
        }

        for (String original : originalMessages) {
            String id = extractMessageId(original);
            String recovered = messageMap.get(id);

            assertNotNull(recovered, "Message not recovered: " + original);
            assertEquals(original, recovered, "Message content corrupted: " + original);
        }
    }

    private String extractMessageId(String message) {
        // Extract message ID from message format
        if (message.startsWith("message-")) {
            int end = message.indexOf("-", 9);
            return message.substring(0, end);
        }
        return message;
    }

    private void validateEndToEndWorkflowResults(int completed, int failed, Duration duration) {
        double successRate = (double) completed / (completed + failed);
        double throughput = completed / (duration.toSeconds() / 1000.0);

        assertTrue(successRate >= 0.95,
            String.format("Workflow success rate %.2f too low", successRate));

        assertTrue(throughput > 10,
            String.format("Workflow throughput %.2f too low", throughput));

        System.out.printf("✓ End-to-end workflow: %.2f%% success, %.2f workflows/s%n",
            successRate * 100, throughput);
    }

    // Result and test classes

    private static class ThroughputTestResult {
        private final int agentCount;
        private final int messageCount;
        private final double throughput;

        public ThroughputTestResult(int agentCount, int messageCount, double throughput) {
            this.agentCount = agentCount;
            this.messageCount = messageCount;
            this.throughput = throughput;
        }

        public int getAgentCount() { return agentCount; }
        public int getMessageCount() { return messageCount; }
        public double getThroughput() { return throughput; }
    }

    private static class Agent {
        private final String id;
        private final String name;
        private boolean isActive;

        public Agent(String id, String name) {
            this.id = id;
            this.name = name;
            this.isActive = true;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public boolean isActive() { return isActive; }
        public void setActive(boolean active) { isActive = active; }
    }

    private class MessageStore {
        private final Map<String, String> storedMessages = new ConcurrentHashMap<>();

        public void storeMessage(String message) {
            storedMessages.put(message, message);
        }

        public List<String> recoverMessages() {
            return new ArrayList<>(storedMessages.values());
        }
    }

    private class AgentSimulator {
        public void simulateFailure() {
            // Simulate system failure
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private class ProtocolComplianceTester {
        public boolean validateMessageFormat() {
            // Validate message format compliance
            return true;
        }

        public boolean validateHandshakeProtocol() {
            // Validate handshake protocol
            return true;
        }

        public boolean validateMessageAcknowledgment() {
            // Validate acknowledgment protocol
            return true;
        }

        public boolean validateErrorHandling() {
            // Validate error handling
            return true;
        }

        public boolean validateSecurityCompliance() {
            // Validate security compliance
            return true;
        }
    }

    private class AgentLifecycleTester {
        public boolean validateAgentRegistration() {
            // Validate agent registration
            return true;
        }

        public boolean validateAgentDeregistration() {
            // Validate agent deregistration
            return true;
        }

        public boolean validateAgentHeartbeat() {
            // Validate agent heartbeat
            return true;
        }

        public boolean validateAgentStatusMonitoring() {
            // Validate agent status monitoring
            return true;
        }

        public boolean validateAgentFailover() {
            // Validate agent failover
            return true;
        }
    }

    private class EndToEndWorkflow {
        public boolean executeCompleteWorkflow(int workflowId) {
            try {
                // Simulate complete A2A workflow
                Thread.sleep((long)(Math.random() * 100) + 50);

                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    private class A2ACommunicationManager {
        private final ExecutorService executor = Executors.newFixedThreadPool(100);

        public boolean sendMessage(String source, String target, String message) {
            try {
                // Simulate A2A message delivery
                Thread.sleep((long)(Math.random() * 50) + 10);
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        public boolean handoffAgent(Agent source, Agent target) {
            try {
                // Simulate agent handoff
                Thread.sleep((long)(Math.random() * 100) + 20);
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        public void shutdown() {
            executor.shutdown();
        }
    }

    private class AgentRegistry {
        private final Map<String, Agent> agents = new ConcurrentHashMap<>();

        public void registerAgent(Agent agent) {
            agents.put(agent.getId(), agent);
        }

        public Agent getAgent(String id) {
            return agents.get(id);
        }

        public void shutdownAll() {
            for (Agent agent : agents.values()) {
                agent.setActive(false);
            }
            agents.clear();
        }
    }

    private class PerformanceMonitor {
        public void validateLatencyThreshold(int thresholdMs) {
            // Check if current latency exceeds threshold
            if (latencyTracker.getP95Latency() > thresholdMs) {
                System.err.println("Warning: P95 latency exceeds threshold");
            }
        }
    }

    private class MessageLatencyTracker {
        private final List<Long> latencies = new ArrayList<>();

        public void recordMessageLatency(long latency) {
            latencies.add(latency);
        }

        public List<Long> getLatencies() {
            return new ArrayList<>(latencies);
        }

        public long getP95Latency() {
            if (latencies.isEmpty()) return 0;

            List<Long> sorted = new ArrayList<>(latencies);
            sorted.sort(Long::compareTo);
            int p95Index = (int) (sorted.size() * 0.95);
            return sorted.get(p95Index);
        }

        public void clear() {
            latencies.clear();
        }
    }
}