# A2A Wiring for Buried Engines in YAWL

This implementation provides complete Agent-to-Agent (A2A) communication capabilities for buried engines in the YAWL workflow system. It enables seamless integration between buried workflow engines and agents through async message routing, virtual thread support, and efficient peer-to-peer communication.

## Architecture Overview

### Core Components

1. **BuriedEngineA2AAdapter**
   - Enables buried workflow engines to participate in A2A communication
   - Handles message routing through peer network via gossip protocol
   - Provides async message delivery with virtual threads
   - Supports acknowledgments and message correlation

2. **AgentA2AIntegration**
   - Integration layer connecting YAWL Agent class with A2A messaging
   - Provides backward compatibility with existing Agent patterns
   - Supports event subscriptions and message routing
   - Enables bidirectional communication with buried engines

3. **A2AMessage**
   - Structured message type for A2A communication
   - Supports typed messages with metadata and correlation
   - Provides builder pattern for easy message creation
   - Includes validation and tracking capabilities

4. **Peer Registry**
   - Manages peer agent connections
   - Provides health monitoring and failover
   - Supports dynamic registration and discovery

## Key Features

### 1. Agent Communication
- **Direct Communication**: Agents can send messages directly to specific engines
- **Group Communication**: Engines can broadcast messages to all agents in a workflow group
- **Event Subscriptions**: Agents can subscribe to specific event types
- **Acknowledgments**: Support for request/response patterns with acknowledgment tracking

### 2. Async Message Delivery
- **Virtual Threads**: Efficient async operations using Java virtual threads
- **Non-blocking**: Message delivery doesn't block workflow execution
- **Concurrent Processing**: Multiple messages processed concurrently
- **Timeout Management**: Configurable timeouts for message delivery

### 3. Message Routing
- **Gossip Protocol**: Messages propagated through peer network
- **Anti-Entropy**: Prevents duplicate processing with seen message tracking
- **TTL Management**: Messages expire after configurable duration
- **Load Balancing**: Distributes messages across available peers

### 4. Integration Features
- **Backward Compatibility**: Works with existing YAWL Agent infrastructure
- **Event-Driven**: Supports event-driven architecture patterns
- **State Synchronization**: Enables state sharing between peers
- **Health Monitoring**: Tracks peer availability and connection health

## Usage Examples

### Basic Engine Setup

```java
// Create peer registry
PeerRegistry peerRegistry = new PeerRegistry();

// Create buried engine adapter
BuriedEngineA2AAdapter engineAdapter = new BuriedEngineA2AAdapter(
    "engine-123",
    "workflow-group",
    peerRegistry,
    Duration.ofSeconds(30),
    50
);

// Set up message handler
engineAdapter.setMessageHandler(message -> {
    System.out.println("Engine received: " + message.type());
    // Handle workflow events, state updates, etc.
});

// Start the adapter
engineAdapter.start();

// Register as peer
peerRegistry.registerPeer("engine-123",
    new PeerConnection.InMemoryPeerConnection("engine-123", engineAdapter));
```

### Basic Agent Setup

```java
// Create agent integration
AgentA2AIntegration agentIntegration = new AgentA2AIntegration(
    "agent-456",
    "workflow-group",
    peerRegistry,
    Duration.ofSeconds(30)
);

// Subscribe to workflow events
agentIntegration.subscribeToAllEvents(message -> {
    System.out.println("Agent received: " + message.type());
    // Process workflow events, handle responses, etc.
});

// Start integration
agentIntegration.start();

// Register as peer
peerRegistry.registerPeer("agent-456",
    new PeerConnection.InMemoryPeerConnection("agent-456", agentIntegration));
```

### Agent to Engine Communication

```java
// Send message to engine
boolean sent = agentIntegration.sendToEngine("engine-123", "workflow-event",
    Map.of(
        "caseId", "case-789",
        "event", "task-completed",
        "timestamp", Instant.now()
    ));

// Send with acknowledgment
boolean ackReceived = agentIntegration.sendWithAck("engine-123", "request",
    Map.of("requireResponse", true),
    Duration.ofSeconds(10));
```

### Engine to Agent Communication

```java
// Send message to agent
A2AMessage response = A2AMessage.builder()
    .type("engine-response")
    .targetAgent("agent-456")
    .payload(Map.of(
        "result", "success",
        "details", "Task completed"
    ))
    .build();

engineAdapter.sendToAgent("agent-456", response);

// Broadcast to all agents in group
int broadcastCount = engineAdapter.broadcastToGroup(
    A2AMessage.builder()
        .type("broadcast-event")
        .payload(Map.of("systemUpdate", true))
        .build()
);
```

### Event Subscriptions

```java
// Subscribe to specific event types
agentIntegration.subscribeToEvents("workflow-event", message -> {
    // Handle workflow events only
});

// Subscribe to engine events
agentIntegration.subscribeToEngineEvents(message -> {
    // Handle events from engines
});

// Subscribe to system events (acks, timeouts, etc.)
agentIntegration.subscribeToSystemEvents(message -> {
    // Handle system notifications
});
```

## Message Types

### Standard Message Types

- **workflow-event**: Workflow-related events (case creation, task completion, etc.)
- **engine-event**: Events from buried engines
- **request**: Request for action or data
- **response**: Response to a request
- **broadcast**: Broadcast message to all peers
- **ack**: Message acknowledgment
- **error**: Error notification
- **heartbeat**: Health check message

### Message Structure

```java
A2AMessage message = A2AMessage.builder()
    .messageId("msg-12345")        // Unique message ID (auto-generated if not set)
    .type("workflow-event")        // Message type
    .sourceEngine("engine-123")    // Source engine ID
    .targetAgent("agent-456")      // Target agent ID
    .correlationId("corr-123")     // Correlation ID for request/response
    .payload(Map.of(...))          // Message payload
    .metadata(Map.of(...))         // Additional metadata
    .timestamp(Instant.now())      // Creation timestamp
    .build();
```

## Configuration

### Adapter Configuration

```java
// Custom configuration
BuriedEngineA2AAdapter adapter = new BuriedEngineA2AAdapter(
    engineId,                    // Unique engine ID
    engineGroup,                  // Logical group
    peerRegistry,                 // Peer registry
    Duration.ofMinutes(1),        // Message timeout
    100                          // Max concurrent messages
);
```

### Registry Configuration

```java
// Custom peer registry with health check
PeerRegistry registry = new PeerRegistry(
    Duration.ofMinutes(5),        // Peer timeout
    new CustomHealthCheck()       // Custom health check implementation
);
```

## Performance Considerations

### Virtual Threads
- Uses `Executors.newVirtualThreadPerTaskExecutor()` for efficient async operations
- No thread pool sizing needed (millions of virtual threads possible)
- Carrier thread pooling automatically managed by JVM

### Message Routing
- Gossip protocol provides epidemic message propagation
- Anti-entropy prevents duplicate processing
- TTL management prevents infinite propagation
- Load balancing distributes messages across available peers

### Memory Management
- Message queues with configurable capacity
- Automatic cleanup of timed-out messages
- Metadata size limits for long-running sessions

## Error Handling

### Common Scenarios

1. **Message Queue Full**: Messages are dropped with warning logs
2. **Peer Unavailable**: Failed delivery is logged and retried
3. **Timeout**: Acknowledgment timeouts are handled gracefully
4. **Invalid Messages**: Validation failures are logged with details

### Recovery Strategies

- **Retry Logic**: Automatic retry for transient failures
- **Circuit Breaker**: Protection from cascading failures
- **Dead Letter Queue**: Failed messages can be inspected and reprocessed
- **Health Checks**: Automatic recovery of unhealthy peers

## Testing

### Unit Tests

```bash
# Run adapter tests
mvn test -Dtest=BuriedEngineA2AAdapterTest

# Run integration tests
mvn test -Dtest=A2AIntegrationSystemTest
```

### Demo Application

```bash
# Run the demo
java -cp stress-demo/target/classes org.yawlfoundation.yawl.integration.a2a.A2ADemo
```

The demo demonstrates:
- Agent-to-engine communication
- Engine-to-agent responses
- Group broadcasting
- Event subscriptions
- System metrics

## Integration with Existing YAWL Systems

### Engine Integration

Buried engines can use the adapter to:
- Send workflow events to external agents
- Receive requests from external agents
- Participate in distributed coordination
- Monitor peer health and availability

### Agent Integration

Existing YAWL agents can be enhanced with:
- A2A communication capabilities
- Event-driven processing
- Distributed state sharing
- Peer-to-peer messaging

### Migration Path

1. **Phase 1**: Add A2A adapter to existing engines
2. **Phase 2**: Integrate agents with A2A layer
3. **Phase 3**: Enable peer-to-peer communication
4. **Phase 4**: Implement advanced features (broadcasting, events)

## Best Practices

1. **Message Design**: Keep messages small and structured
2. **Error Handling**: Implement proper error handling and logging
3. **Timeout Configuration**: Set appropriate timeouts for your use case
4. **Monitoring**: Use metrics to monitor system health
5. **Testing**: Test both happy path and failure scenarios

## Future Enhancements

- **Persistent Messages**: Message queue persistence for reliability
- **Encryption**: End-to-end encryption for sensitive messages
- **Compression**: Message compression for large payloads
- **Advanced Routing**: Complex routing rules based on message content
- **Distributed Tracing**: Integration with distributed tracing systems

## Troubleshooting

### Common Issues

1. **Messages Not Delivered**: Check peer registry and network connectivity
2. **High Memory Usage**: Reduce message queue sizes or TTL settings
3. **Slow Performance**: Monitor virtual thread usage and adjust concurrency
4. **Duplicate Messages**: Check anti-entropy configuration

### Debug Commands

```bash
# Check peer registry status
peerRegistry.getAvailablePeerCount()

# Get adapter metrics
adapter.getMetrics()

# Get integration metrics
integration.getMetrics()
```

---

This implementation provides a robust, efficient, and scalable A2A communication system for buried engines in YAWL, enabling seamless integration and collaboration between workflow engines and agents.