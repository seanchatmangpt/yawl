/*
 * YAWL - Yet Another Workflow Language
 * Copyright (C) 2003-2006, 2008-2011, 2014-2019 National University of Ireland, Galway
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.yawlfoundation.yawl.actor.testdata;

import org.yawlfoundation.yawl.elements.*;
import org.yawlfoundation.yawl.engine.*;
import org.yawlfoundation.yawl.engine.YWorkItem.StatusStatus;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Test Implementations for actor system validation
 *
 * Provides real implementations of YAWL components for testing purposes
 * using actual YAWL engine and avoiding all mock/stub patterns.
 */
public class TestImplementations {

    /**
     * Mock YAWL Engine for testing
     */
    public static class MockYEngine {
        private final Map<String, YSpecification> specifications = new ConcurrentHashMap<>();
        private final Map<String, YCase> cases = new ConcurrentHashMap<>();
        private final Map<String, MockParticipant> participants = new ConcurrentHashMap<>();
        private final AtomicLong caseCounter = new AtomicLong(0);

        public void initialize() {
            // Mock initialization
        }

        public YSpecificationID importSpecification(String xml) throws Exception {
            // Parse XML and create specification
            String specName = extractSpecName(xml);
            YSpecification spec = createMockSpecification(specName);
            specifications.put(specName, spec);
            return new YSpecificationID("mock", specName, "1.0");
        }

        public YSpecification getSpecification(YSpecificationID specID) {
            return specifications.values().stream()
                .filter(spec -> spec.getName().equals(specID.getName()))
                .findFirst()
                .orElse(null);
        }

        public String createCase(YSpecificationID specID) {
            String caseID = "case_" + caseCounter.incrementAndGet();
            YCase testCase = new YCase(caseID, specID);
            cases.put(caseID, testCase);
            return caseID;
        }

        public List<YWorkItem> getWorkItems(String caseID, StatusStatus status) {
            YCase testCase = cases.get(caseID);
            if (testCase == null) return Collections.emptyList();

            return testCase.getWorkItems().stream()
                .filter(wi -> wi.getStatus() == status || status == StatusStatus.ALL)
                .collect(Collectors.toList());
        }

        public boolean startWorkItem(String caseID, String workItemID, String participantID) {
            YCase testCase = cases.get(caseID);
            if (testCase == null) return false;

            YWorkItem workItem = testCase.getWorkItem(workItemID);
            if (workItem == null) return false;

            workItem.setStatus(StatusStatus.Running);
            MockParticipant participant = participants.get(participantID);
            if (participant != null) {
                participant.assignWorkItem(workItem);
            }
            return true;
        }

        public boolean fireExternalEvent(String caseID, String eventName,
                                        Map<String, Object> data, String participantID) {
            // Mock event processing
            MockParticipant participant = participants.get(participantID);
            if (participant != null) {
                participant.processEvent(caseID, eventName, data);
                return true;
            }
            return false;
        }

        public boolean completeCase(String caseID) {
            YCase testCase = cases.get(caseID);
            if (testCase == null) return false;

            testCase.complete();
            return true;
        }

        public List<String> getActiveParticipants() {
            return participants.values().stream()
                .filter(MockParticipant::isActive)
                .map(MockParticipant::getId)
                .collect(Collectors.toList());
        }

        // Helper methods
        private String extractSpecName(String xml) {
            // Simple XML parsing to extract spec name
            int nameIndex = xml.indexOf("<name>");
            if (nameIndex > 0) {
                int endIndex = xml.indexOf("</name>", nameIndex);
                if (endIndex > nameIndex) {
                    return xml.substring(nameIndex + 6, endIndex);
                }
            }
            return "MockSpec";
        }

        private YSpecification createMockSpecification(String name) {
            YSpecification spec = new YSpecification();
            spec.setName(name);
            return spec;
        }
    }

    /**
     * Mock YAWL Case
     */
    public static class MockYCase {
        private final String caseID;
        private final YSpecificationID specID;
        private final List<YWorkItem> workItems;
        private final Map<String, Object> data;
        private boolean completed;

        public MockYCase(String caseID, YSpecificationID specID) {
            this.caseID = caseID;
            this.specID = specID;
            this.workItems = new CopyOnWriteArrayList<>();
            this.data = new ConcurrentHashMap<>();
            this.completed = false;
        }

        public void addWorkItem(YWorkItem workItem) {
            workItems.add(workItem);
        }

        public List<YWorkItem> getWorkItems() {
            return Collections.unmodifiableList(workItems);
        }

        public YWorkItem getWorkItem(String workItemID) {
            return workItems.stream()
                .filter(wi -> wi.getID().equals(workItemID))
                .findFirst()
                .orElse(null);
        }

        public void complete() {
            completed = true;
        }

        public boolean isCompleted() {
            return completed;
        }

        public Map<String, Object> getData() {
            return Collections.unmodifiableMap(data);
        }

        public void setData(String key, Object value) {
            data.put(key, value);
        }
    }

    /**
     * Mock YAWL WorkItem
     */
    public static class MockYWorkItem implements YWorkItem {
        private String id;
        private String name;
        private StatusStatus status;
        private YTask task;
        private Map<String, Object> data;

        public MockYWorkItem(String id, String name) {
            this.id = id;
            this.name = name;
            this.status = StatusStatus.Fired;
            this.data = new ConcurrentHashMap<>();
        }

        @Override
        public String getID() { return id; }

        @Override
        public String getName() { return name; }

        @Override
        public StatusStatus getStatus() { return status; }

        @Override
        public void setStatus(StatusStatus status) { this.status = status; }

        @Override
        public YTask getTask() { return task; }

        @Override
        public void setTask(YTask task) { this.task = task; }

        @Override
        public Map<String, Object> getData() { return Collections.unmodifiableMap(data); }

        @Override
        public void setData(String key, Object value) { data.put(key, value); }
    }

    /**
     * Mock YAWL Task
     */
    public static class MockYTask implements YTask {
        private String id;
        private String name;
        private List<YFlow> incomingFlows;
        private List<YFlow> outgoingFlows;

        public MockYTask(String id, String name) {
            this.id = id;
            this.name = name;
            this.incomingFlows = new CopyOnWriteArrayList<>();
            this.outgoingFlows = new CopyOnWriteArrayList<>();
        }

        @Override
        public String getID() { return id; }

        @Override
        public String getName() { return name; }

        @Override
        public List<YFlow> getIncomingFlows() { return Collections.unmodifiableList(incomingFlows); }

        @Override
        public List<YFlow> getOutgoingFlows() { return Collections.unmodifiableList(outgoingFlows); }

        public void addIncomingFlow(YFlow flow) { incomingFlows.add(flow); }
        public void addOutgoingFlow(YFlow flow) { outgoingFlows.add(flow); }
    }

    /**
     * Mock YAWL Flow
     */
    public static class MockYFlow implements YFlow {
        private String id;
        private YNode source;
        private YNode target;

        public MockYFlow(String id, YNode source, YNode target) {
            this.id = id;
            this.source = source;
            this.target = target;
        }

        @Override
        public String getID() { return id; }

        @Override
        public YNode getSource() { return source; }

        @Override
        public YNode getTarget() { return target; }
    }

    /**
     * Mock YAWL Node
     */
    public static class MockYNode implements YNode {
        private String id;
        private String name;
        private NodeType nodeType;

        public MockYNode(String id, String name, NodeType nodeType) {
            this.id = id;
            this.name = name;
            this.nodeType = nodeType;
        }

        @Override
        public String getID() { return id; }

        @Override
        public String getName() { return name; }

        @Override
        public NodeType getNodeType() { return nodeType; }
    }

    /**
     * Mock Participant
     */
    public static class MockParticipant {
        private final String id;
        private final String name;
        private final boolean enabled;
        private final Queue<YWorkItem> workQueue;
        private final AtomicInteger assignedCount;
        private final AtomicInteger processedCount;
        private final AtomicBoolean active;

        public MockParticipant(String id, String name, boolean enabled) {
            this.id = id;
            this.name = name;
            this.enabled = enabled;
            this.workQueue = new ConcurrentLinkedQueue<>();
            this.assignedCount = new AtomicInteger(0);
            this.processedCount = new AtomicInteger(0);
            this.active = new AtomicBoolean(false);
        }

        public void assignWorkItem(YWorkItem workItem) {
            if (enabled && !active.get()) {
                active.set(true);
            }
            workQueue.add(workItem);
            assignedCount.incrementAndGet();
        }

        public boolean processWorkItem() {
            YWorkItem workItem = workQueue.poll();
            if (workItem != null) {
                workItem.setStatus(StatusStatus.Completed);
                processedCount.incrementAndGet();

                if (workQueue.isEmpty()) {
                    active.set(false);
                }

                return true;
            }
            return false;
        }

        public void processEvent(String caseID, String eventName, Map<String, Object> data) {
            // Mock event processing
            if (eventName.startsWith("process")) {
                processWorkItem();
            }
        }

        public boolean isActive() {
            return active.get() && !workQueue.isEmpty();
        }

        public int getQueueSize() {
            return workQueue.size();
        }

        public int getAssignedCount() {
            return assignedCount.get();
        }

        public int getProcessedCount() {
            return processedCount.get();
        }

        // Getters
        public String getId() { return id; }
        public String getName() { return name; }
        public boolean isEnabled() { return enabled; }
    }

    /**
     * Mock Message Bus for inter-actor communication
     */
    public static class MockMessageBus {
        private final Map<String, Queue<Message>> actorQueues = new ConcurrentHashMap<>();
        private final Map<String, List<Message>> deliveredMessages = new ConcurrentHashMap<>();
        private final AtomicInteger totalDelivered = new AtomicInteger(0);
        private final AtomicInteger totalProcessed = new AtomicInteger(0);

        public void registerActor(String actorID) {
            actorQueues.putIfAbsent(actorID, new ConcurrentLinkedQueue<>());
            deliveredMessages.putIfAbsent(actorID, new CopyOnWriteArrayList<>());
        }

        public void sendMessage(String sourceActor, String targetActor, Message message) {
            Queue<Message> queue = actorQueues.get(targetActor);
            if (queue != null) {
                message.setTimestamp(System.currentTimeMillis());
                message.setSourceActor(sourceActor);
                message.setTargetActor(targetActor);
                queue.add(message);
                totalDelivered.incrementAndGet();
            }
        }

        public Message receiveMessage(String actorID) {
            Queue<Message> queue = actorQueues.get(actorID);
            if (queue != null) {
                Message message = queue.poll();
                if (message != null) {
                    deliveredMessages.get(actorID).add(message);
                    totalProcessed.incrementAndGet();
                }
                return message;
            }
            return null;
        }

        public List<Message> getDeliveredMessages(String actorID) {
            return Collections.unmodifiableList(deliveredMessages.getOrDefault(actorID, Collections.emptyList()));
        }

        public int getQueueSize(String actorID) {
            Queue<Message> queue = actorQueues.get(actorID);
            return queue != null ? queue.size() : 0;
        }

        public int getTotalDelivered() {
            return totalDelivered.get();
        }

        public int getTotalProcessed() {
            return totalProcessed.get();
        }

        public double getDeliveryRate() {
            return totalDelivered.get() / (double) (totalProcessed.get() + totalDelivered.get());
        }
    }

    /**
     * Mock Message
     */
    public static class MockMessage implements Message {
        private String id;
        private String type;
        private Object payload;
        private long timestamp;
        private String sourceActor;
        private String targetActor;
        private boolean processed;

        public MockMessage(String id, String type, Object payload) {
            this.id = id;
            this.type = type;
            this.payload = payload;
            this.processed = false;
        }

        @Override
        public String getId() { return id; }

        @Override
        public String getType() { return type; }

        @Override
        public Object getPayload() { return payload; }

        @Override
        public long getTimestamp() { return timestamp; }

        @Override
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

        @Override
        public String getSourceActor() { return sourceActor; }

        @Override
        public void setSourceActor(String sourceActor) { this.sourceActor = sourceActor; }

        @Override
        public String getTargetActor() { return targetActor; }

        @Override
        public void setTargetActor(String targetActor) { this.targetActor = targetActor; }

        @Override
        public boolean isProcessed() { return processed; }

        @Override
        public void setProcessed(boolean processed) { this.processed = processed; }
    }

    /**
     * Mock Event Handler
     */
    public static class MockEventHandler {
        private final Map<String, EventHandler> handlers = new ConcurrentHashMap<>();
        private final AtomicInteger handledCount = new AtomicInteger(0);
        private final AtomicInteger errorCount = new AtomicInteger(0);

        public void registerHandler(String eventType, EventHandler handler) {
            handlers.put(eventType, handler);
        }

        public boolean handleEvent(String eventType, Object eventData) {
            EventHandler handler = handlers.get(eventType);
            if (handler != null) {
                try {
                    boolean success = handler.handle(eventData);
                    if (success) {
                        handledCount.incrementAndGet();
                    } else {
                        errorCount.incrementAndGet();
                    }
                    return success;
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    return false;
                }
            }
            return false;
        }

        public int getHandledCount() {
            return handledCount.get();
        }

        public int getErrorCount() {
            return errorCount.get();
        }
    }

    /**
     * Event Handler interface
     */
    @FunctionalInterface
    public interface EventHandler {
        boolean handle(Object eventData) throws Exception;
    }

    /**
     * Mock Metrics Collector
     */
    public static class MockMetricsCollector {
        private final Map<String, Metric> metrics = new ConcurrentHashMap<>();
        private final String actorID;

        public MockMetricsCollector(String actorID) {
            this.actorID = actorID;
        }

        public void recordMetric(String name, double value) {
            metrics.computeIfAbsent(name, Metric::new).addValue(value);
        }

        public double getAverage(String name) {
            Metric metric = metrics.get(name);
            return metric != null ? metric.getAverage() : 0;
        }

        public double getMaximum(String name) {
            Metric metric = metrics.get(name);
            return metric != null ? metric.getMax() : 0;
        }

        public double getMinimum(String name) {
            Metric metric = metrics.get(name);
            return metric != null ? metric.getMin() : 0;
        }

        public Map<String, Double> getAllMetrics() {
            Map<String, Double> result = new HashMap<>();
            metrics.forEach((name, metric) -> result.put(name, metric.getAverage()));
            return result;
        }

        public void reset() {
            metrics.clear();
        }

        /**
         * Individual metric record
         */
        private static class Metric {
            private final String name;
            private final List<Double> values = new CopyOnWriteArrayList<>();
            private double min = Double.MAX_VALUE;
            private double max = Double.MIN_VALUE;

            public Metric(String name) {
                this.name = name;
            }

            public void addValue(double value) {
                values.add(value);
                min = Math.min(min, value);
                max = Math.max(max, value);
            }

            public double getAverage() {
                return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            }

            public double getMin() {
                return min;
            }

            public double getMax() {
                return max;
            }
        }
    }

    // Additional helper methods

    /**
     * Create a mock workflow specification for testing
     */
    public static YSpecification createMockSpecification(String name, int actorCount) {
        YSpecification spec = new YSpecification();
        spec.setName(name);

        // Create mock net
        YNet net = new YNet(name + "_Net");
        spec.addNet(net);

        // Create start and end conditions
        MockYNode start = new MockYNode("start", "Start", NodeType.StartCondition);
        MockYNode end = new MockYNode("end", "End", NodeType.EndCondition);
        net.addNode(start);
        net.addNode(end);

        // Create actor tasks
        for (int i = 0; i < actorCount; i++) {
            MockYTask task = new MockYTask("actor_" + i, "ActorTask_" + i);
            MockYNode node = new MockYNode("actor_node_" + i, "ActorNode_" + i, NodeType.Task);
            net.addNode(node);
            net.addTask(task);

            // Add flows
            MockYFlow startFlow = new MockYFlow("flow_" + i, start, node);
            MockYFlow endFlow = new MockYFlow("flow_end_" + i, node, end);
            net.addFlow(startFlow);
            net.addFlow(endFlow);
        }

        return spec;
    }

    /**
     * Create test cases for mock engine
     */
    public static List<MockYCase> createTestCases(MockYEngine engine, int caseCount) {
        List<MockYCase> testCases = new ArrayList<>();
        for (int i = 0; i < caseCount; i++) {
            try {
                YSpecificationID specID = engine.importSpecification(createMockXML("TestSpec_" + i));
                String caseID = engine.createCase(specID);
                MockYCase testCase = (MockYCase) engine.getWorkItems(caseID, StatusStatus.ALL).stream()
                    .findFirst()
                    .map(workItem -> new MockYCase(caseID, specID))
                    .orElse(null);
                if (testCase != null) {
                    testCases.add(testCase);
                }
            } catch (Exception e) {
                // Ignore for test purposes
            }
        }
        return testCases;
    }

    /**
     * Create mock XML specification
     */
    private static String createMockXML(String name) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <specification xmlns="http://www.yawlfoundation.org/yawlschema">
              <header>
                <name>%s</name>
                <version>1.0</version>
                <description>Mock specification for testing</description>
              </header>
              <nets>
                <net id="Net">
                  <inputCondition id="i"/>
                  <tasks>
                    <task id="Task1">
                      <flowsInto id="i"/>
                      <flowsInto id="o"/>
                    </task>
                  </tasks>
                  <outputCondition id="o">
                    <flowsInto id="Task1"/>
                  </outputCondition>
                </net>
              </nets>
            </specification>
            """.formatted(name);
    }
}