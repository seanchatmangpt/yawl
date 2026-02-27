/*
 * YAWL v6.0.0-GA A2A Communication Benchmark Agent
 *
 * Specialized benchmark agent for agent-to-agent communication performance
 * Tests autonomous agent communication, message passing, and coordination
 */

package org.yawlfoundation.yawl.benchmark.agents;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.yawlfoundation.yawl.engine.instance.CaseInstance;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.benchmark.framework.BaseBenchmarkAgent;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.instance.CaseInstance;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Agent-to-Agent Communication Benchmark Agent
 *
 * Benchmarks:
 * - Message passing efficiency between autonomous agents
 * - A2A communication protocols performance
 * - Coordination overhead analysis
 * - Message queue throughput
 * - Network latency simulation
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 10)
@Measurement(iterations = 5, time = 30)
@Fork(value = 1, jvmArgs = {
    "-Xms4g", "-Xmx8g",
    "-XX:+UseG1GC",
    "-XX:+UseCompactObjectHeaders",
    "--enable-preview",
    "-XX:+UnlockExperimentalVMOptions",
    "-XX:+UseZGC"
})
@State(Scope.Benchmark)
public class A2ACommunicationBenchmarkAgent extends BaseBenchmarkAgent {

    // Communication configuration
    private final int maxAgents;
    private final int maxMessagesPerSecond;
    private final boolean enableAsyncProcessing;
    private final boolean enableMessageCompression;

    // Message system
    private final BlockingQueue<AgentMessage> messageQueue;
    private final Map<String, Agent> agents;
    private final AtomicLong totalMessagesSent;
    private final AtomicLong totalMessagesReceived;
    private final AtomicInteger activeAgents;

    // Communication patterns
    private final List<CommunicationPattern> communicationPatterns;
    private final List<MessageType> messageTypes;

    // Benchmark state
    private Instant benchmarkStart;
    private List<AgentMessage> messageHistory;

    public A2ACommunicationBenchmarkAgent() {
        super("A2ACommunicationBenchmarkAgent", "Agent-to-Agent Communication", BaseBenchmarkAgent.defaultConfig());
        this.maxAgents = 100;
        this.maxMessagesPerSecond = 10_000;
        this.enableAsyncProcessing = true;
        this.enableMessageCompression = true;

        this.messageQueue = new LinkedBlockingQueue<>(10_000);
        this.agents = new ConcurrentHashMap<>();
        this.totalMessagesSent = new AtomicLong(0);
        this.totalMessagesReceived = new AtomicLong(0);
        this.activeAgents = new AtomicInteger(0);

        this.communicationPatterns = Arrays.asList(
            new CommunicationPattern("broadcast", "One to many"),
            new CommunicationPattern("multicast", "Selective many"),
            new CommunicationPattern("unicast", "One to one"),
            new CommunicationPattern("roundrobin", "Sequential distribution"),
            new CommunicationPattern("star", "Central hub"),
            new CommunicationPattern("mesh", "Mesh network")
        );

        this.messageTypes = Arrays.asList(
            new MessageType("taskAssignment", "High priority"),
            new MessageType("statusUpdate", "Medium priority"),
            new MessageType("errorReport", "High priority"),
            new MessageType("coordination", "Medium priority"),
            new MessageType("heartbeat", "Low priority"),
            new MessageType("dataExchange", "Medium priority")
        );

        this.messageHistory = Collections.synchronizedList(new ArrayList<>());
    }

    @Setup
    public void setup() {
        benchmarkStart = Instant.now();
        initializeAgents();
        startMessageProcessing();
    }

    private void initializeAgents() {
        for (int i = 0; i < maxAgents; i++) {
            Agent agent = new Agent("agent_" + i);
            agents.put(agent.getId(), agent);
            activeAgents.incrementAndGet();
        }
    }

    private void startMessageProcessing() {
        // Start message processor thread
        Thread processorThread = new Thread(this::processMessages);
        processorThread.setDaemon(true);
        processorThread.start();
    }

    @Override
    public void executeBenchmark(Blackhole bh) {
        try {
            // Test basic communication operations
            testBasicCommunication(bh);
        } catch (Exception e) {
            bh.consume(e);
        }
    }

    /**
     * Test message throughput
     */
    @Benchmark
    @Group("messageThroughput")
    @GroupThreads(1)
    public void testMessageThroughput_100(Blackhole bh) {
        testMessageThroughput(100, bh);
    }

    @Benchmark
    @Group("messageThroughput")
    @GroupThreads(1)
    public void testMessageThroughput_1000(Blackhole bh) {
        testMessageThroughput(1000, bh);
    }

    @Benchmark
    @Group("messageThroughput")
    @GroupThreads(1)
    public void testMessageThroughput_10000(Blackhole bh) {
        testMessageThroughput(10000, bh);
    }

    private void testMessageThroughput(int messageCount, Blackhole bh) {
        try {
            Instant start = Instant.now();
            AtomicInteger successCount = new AtomicInteger(0);

            // Send messages in parallel
            List<Future<Void>> futures = new ArrayList<>();

            for (int i = 0; i < messageCount; i++) {
                final int messageId = i;
                Future<Void> future = virtualThreadExecutor.submit(() -> {
                    try {
                        AgentMessage message = createMessage("agent_" + (i % maxAgents),
                            "agent_" + ((i + 1) % maxAgents), "taskAssignment");

                        if (sendMessage(message)) {
                            successCount.incrementAndGet();
                        }
                        return null;
                    } catch (Exception e) {
                        recordError(e, "message_send_" + messageId);
                        return null;
                    }
                });
                futures.add(future);
            }

            // Wait for all messages to be sent
            for (Future<Void> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }

            // Wait for message processing (simulating async processing)
            Thread.sleep(1000);

            Instant end = Instant.now();
            Duration duration = Duration.between(start, end);

            // Record metrics
            performanceMonitor.recordOperation(messageCount, duration.toMillis(),
                successCount.get(), messageCount - successCount.get());

            bh.consume(successCount.get());

        } catch (Exception e) {
            bh.consume(e);
        }
    }

    /**
     * Test communication patterns
     */
    @Benchmark
    @Group("communicationPatterns")
    @GroupThreads(1)
    public void testCommunicationPatterns(Blackhole bh) {
        try {
            testPatternPerformance("broadcast", 100, bh);
            testPatternPerformance("multicast", 100, bh);
            testPatternPerformance("unicast", 100, bh);
            testPatternPerformance("roundrobin", 100, bh);
            testPatternPerformance("star", 100, bh);
            testPatternPerformance("mesh", 100, bh);
        } catch (Exception e) {
            bh.consume(e);
        }
    }

    /**
     * Test message types performance
     */
    @Benchmark
    @Group("messageTypes")
    public void testMessageTypes(Blackhole bh) {
        try {
            testMessageTypePerformance("taskAssignment", 100, bh);
            testMessageTypePerformance("statusUpdate", 100, bh);
            testMessageTypePerformance("errorReport", 100, bh);
            testMessageTypePerformance("coordination", 100, bh);
            testMessageTypePerformance("heartbeat", 100, bh);
            testMessageTypePerformance("dataExchange", 100, bh);
        } catch (Exception e) {
            bh.consume(e);
        }
    }

    /**
     * Test coordination overhead
     */
    @Benchmark
    @Group("coordinationOverhead")
    @GroupThreads(1)
    public void testCoordinationOverhead(Blackhole bh) {
        try {
            testCoordinationPerformance(10, 100, bh);
        } catch (Exception e) {
            bh.consume(e);
        }
    }

    /**
     * Test structured concurrency for communication
     */
    @Benchmark
    public void testStructuredConcurrencyCommunication(Blackhole bh) throws InterruptedException {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            List<Future<Void>> futures = new ArrayList<>();

            // Multiple communication operations concurrently
            for (int i = 0; i < 5; i++) {
                final int operationId = i;
                Future<Void> future = scope.fork(() -> {
                    try {
                        // Execute communication operation
                        switch (operationId % 3) {
                            case 0:
                                // Message broadcast
                                broadcastMessage("test", "all_agents", "broadcast_test");
                                break;
                            case 1:
                                // Point-to-point communication
                                sendPointToPoint("agent_0", "agent_1", "direct_test");
                                break;
                            case 2:
                                // Coordination message
                                sendCoordinationMessage("coordination_test");
                                break;
                        }
                        return null;
                    } catch (Exception e) {
                        recordError(e, "structured_comm_" + operationId);
                        throw e;
                    }
                });
                futures.add(future);
            }

            scope.join();

            // Verify results
            for (Future<Void> future : futures) {
                future.resultNow();
            }

        } catch (Exception e) {
            bh.consume(e);
        }
    }

    @Override
    protected CaseInstance runSingleIteration(int iterationId) throws Exception {
        // Simulate A2A communication in a YAWL case context
        CaseInstance testCase = new CaseInstance(null, "a2a_case_" + iterationId);

        // Create communication context
        AgentMessage message = createMessage("source_agent", "target_agent", "taskAssignment");
        // testCase.setData("messageId", message.getId());
        // testCase.setData("source", message.getSource());
        // testCase.setData("target", message.getTarget());
        // testCase.setData("messageType", message.getType());

        // Process message
        processMessage(message);

        return testCase;
    }

    // Communication helper methods
    private AgentMessage createMessage(String source, String target, String type) {
        String messageId = "msg_" + UUID.randomUUID().toString();
        Instant timestamp = Instant.now();

        AgentMessage message = new AgentMessage(messageId, source, target, type, timestamp);
        message.setData("content", "Test message from " + source + " to " + target);
        message.setData("priority", calculatePriority(type));

        return message;
    }

    private int calculatePriority(String messageType) {
        switch (messageType) {
            case "taskAssignment":
            case "errorReport":
                return 3; // High priority
            case "statusUpdate":
            case "coordination":
            case "dataExchange":
                return 2; // Medium priority
            case "heartbeat":
                return 1; // Low priority
            default:
                return 2;
        }
    }

    private boolean sendMessage(AgentMessage message) {
        try {
            messageQueue.put(message);
            totalMessagesSent.incrementAndGet();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void processMessages() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                AgentMessage message = messageQueue.take();
                processMessage(message);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // Log processing error
                recordError(e, "message_processing");
            }
        }
    }

    private void processMessage(AgentMessage message) {
        // Record message processing
        Instant start = Instant.now();

        // Simulate message processing
        try {
            Thread.sleep(1); // Simulate processing time

            // Update agent state
            Agent agent = agents.get(message.getTarget());
            if (agent != null) {
                agent.receiveMessage(message);
            }

            totalMessagesReceived.incrementAndGet();
            messageHistory.add(message);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            recordError(e, "message_processing_" + message.getId());
        }

        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        performanceMonitor.recordOperation(1, duration.toMillis(), 1, 0);
    }

    private void broadcastMessage(String source, String target, String type) {
        String messageId = "broadcast_" + UUID.randomUUID();
        Instant timestamp = Instant.now();

        AgentMessage message = new AgentMessage(messageId, source, target, type, timestamp);

        // Send to all agents
        for (Agent agent : agents.values()) {
            if (!agent.getId().equals(source)) {
                AgentMessage copy = message.copy();
                copy.setTarget(agent.getId());
                sendMessage(copy);
            }
        }
    }

    private void sendPointToPoint(String source, String target, String type) {
        AgentMessage message = createMessage(source, target, type);
        sendMessage(message);
    }

    private void sendCoordinationMessage(String type) {
        // Send coordination message to all agents
        for (int i = 1; i < maxAgents; i++) {
            sendPointToPoint("coordinator", "agent_" + i, type);
        }
    }

    private void testPatternPerformance(String patternName, int operations, Blackhole bh) {
        try {
            Instant start = Instant.now();

            switch (patternName) {
                case "broadcast":
                    testBroadcastPattern(operations, bh);
                    break;
                case "multicast":
                    testMulticastPattern(operations, bh);
                    break;
                case "unicast":
                    testUnicastPattern(operations, bh);
                    break;
                case "roundrobin":
                    testRoundRobinPattern(operations, bh);
                    break;
                case "star":
                    testStarPattern(operations, bh);
                    break;
                case "mesh":
                    testMeshPattern(operations, bh);
                    break;
            }

            Instant end = Instant.now();
            Duration duration = Duration.between(start, end);

            performanceMonitor.recordOperation(operations, duration.toMillis(), operations, 0);

        } catch (Exception e) {
            bh.consume(e);
        }
    }

    private void testBroadcastPattern(int operations, Blackhole bh) {
        for (int i = 0; i < operations; i++) {
            broadcastMessage("source_" + i, "all", "test");
        }
    }

    private void testMulticastPattern(int operations, Blackhole bh) {
        // Multicast to subset of agents
        int subsetSize = maxAgents / 10;
        for (int i = 0; i < operations; i++) {
            String source = "source_" + i;
            for (int j = 0; j < subsetSize; j++) {
                sendPointToPoint(source, "target_" + j, "multicast");
            }
        }
    }

    private void testUnicastPattern(int operations, Blackhole bh) {
        for (int i = 0; i < operations; i++) {
            sendPointToPoint("source_" + i, "target_" + (i % maxAgents), "unicast");
        }
    }

    private void testRoundRobinPattern(int operations, Blackhole bh) {
        int currentTarget = 0;
        for (int i = 0; i < operations; i++) {
            sendPointToPoint("source_" + i, "target_" + currentTarget, "roundrobin");
            currentTarget = (currentTarget + 1) % maxAgents;
        }
    }

    private void testStarPattern(int operations, Blackhole bh) {
        // Central hub communication
        for (int i = 0; i < operations; i++) {
            String source = "source_" + i;
            String target = "hub";
            sendPointToPoint(source, target, "star");
            sendPointToPoint(target, source, "star_reply");
        }
    }

    private void testMeshPattern(int operations, Blackhole bh) {
        // Mesh network communication
        for (int i = 0; i < Math.min(operations, maxAgents); i++) {
            for (int j = i + 1; j < Math.min(operations, maxAgents); j++) {
                sendPointToPoint("agent_" + i, "agent_" + j, "mesh");
                sendPointToPoint("agent_" + j, "agent_" + i, "mesh_reply");
            }
        }
    }

    private void testMessageTypePerformance(String type, int operations, Blackhole bh) {
        try {
            Instant start = Instant.now();

            for (int i = 0; i < operations; i++) {
                String source = "source_" + i;
                String target = "target_" + (i % maxAgents);
                sendPointToPoint(source, target, type);
            }

            Instant end = Instant.now();
            Duration duration = Duration.between(start, end);

            performanceMonitor.recordOperation(operations, duration.toMillis(), operations, 0);

        } catch (Exception e) {
            bh.consume(e);
        }
    }

    private void testCoordinationPerformance(int agents, int operations, Blackhole bh) {
        try {
            Instant start = Instant.now();

            List<Future<Void>> futures = new ArrayList<>();

            for (int i = 0; i < operations; i++) {
                final int operationId = i;
                Future<Void> future = virtualThreadExecutor.submit(() -> {
                    try {
                        // Simulate coordination with multiple agents
                        List<AgentMessage> coordinationMessages = new ArrayList<>();

                        for (int j = 0; j < agents; j++) {
                            AgentMessage message = createMessage("coordinator",
                                "agent_" + j, "coordination");
                            coordinationMessages.add(message);
                        }

                        // Send coordination messages
                        for (AgentMessage message : coordinationMessages) {
                            sendMessage(message);
                        }

                        return null;
                    } catch (Exception e) {
                        recordError(e, "coordination_" + operationId);
                        return null;
                    }
                });
                futures.add(future);
            }

            // Wait for all coordination operations
            for (Future<Void> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }

            Instant end = Instant.now();
            Duration duration = Duration.between(start, end);

            performanceMonitor.recordOperation(operations, duration.toMillis(), operations, 0);

        } catch (Exception e) {
            bh.consume(e);
        }
    }

    private void testBasicCommunication(Blackhole bh) {
        try {
            // Test basic message sending and receiving
            AgentMessage message = createMessage("agent_0", "agent_1", "taskAssignment");
            boolean sent = sendMessage(message);
            boolean processed = messageHistory.size() > 0;

            bh.consume(sent);
            bh.consume(processed);

        } catch (Exception e) {
            bh.consume(e);
        }
    }

    @Override
    public void close() {
        // Shutdown message processing
        Thread.currentThread().interrupt();

        super.close();

        // Generate final report
        var report = generateFinalReport();
        System.out.println("A2A Communication Benchmark Report: " + report);
    }

    // Inner classes for communication system
    public static class Agent {
        private final String id;
        private final List<AgentMessage> receivedMessages;
        private final Instant created;

        public Agent(String id) {
            this.id = id;
            this.receivedMessages = new ArrayList<>();
            this.created = Instant.now();
        }

        public String getId() { return id; }
        public List<AgentMessage> getReceivedMessages() { return receivedMessages; }
        public Instant getCreated() { return created; }

        public void receiveMessage(AgentMessage message) {
            receivedMessages.add(message);
        }
    }

    public static class AgentMessage {
        private final String id;
        private String source;
        private String target;
        private final String type;
        private final Instant timestamp;
        private final Map<String, Object> data;

        public AgentMessage(String id, String source, String target, String type, Instant timestamp) {
            this.id = id;
            this.source = source;
            this.target = target;
            this.type = type;
            this.timestamp = timestamp;
            this.data = new HashMap<>();
        }

        // Getters and setters
        public String getId() { return id; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public String getTarget() { return target; }
        public void setTarget(String target) { this.target = target; }
        public String getType() { return type; }
        public Instant getTimestamp() { return timestamp; }
        public Map<String, Object> getData() { return data; }
        public void setData(String key, Object value) { data.put(key, value); }

        public AgentMessage copy() {
            AgentMessage copy = new AgentMessage(id + "_copy", source, target, type, timestamp);
            copy.data.putAll(data);
            return copy;
        }
    }

    public static class CommunicationPattern {
        private final String name;
        private final String description;

        public CommunicationPattern(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
    }

    public static class MessageType {
        private final String name;
        private final String description;

        public MessageType(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
    }
}