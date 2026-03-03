# YAWL Async A2A Gossip Protocol Implementation

## Overview

This implementation provides an async gossip protocol for YAWL agents that enables epidemic message propagation across distributed agents using virtual threads.

## Components

### 1. GossipMessage<T>
- **Location**: `src/main/java/org/yawlfoundation/yawl/integration/a2a/GossipMessage.java`
- **Type**: Java record (immutable)
- **Purpose**: Represents a gossip message with payload and propagation tracking
- **Key Features**:
  - Unique message ID to prevent duplicates
  - TTL-based expiration
  - Propagation tracking to prevent loops
  - Type-safe payload using generics

### 2. GossipProtocol<T>
- **Location**: `src/main/java/org/yawlfoundation/yawl/integration/a2a/GossipProtocol.java`
- **Type**: Concrete class
- **Purpose**: Implements the async gossip protocol logic
- **Key Features**:
  - Virtual thread-based async propagation
  - Configurable propagation delay and concurrency limits
  - Anti-entropy cleanup mechanism
  - Metrics collection (received, propagated, expired messages)
  - Peer-based message routing

### 3. GossipAwareAgent<T>
- **Location**: `src/main/java/org/yawlfoundation/yawl/integration/a2a/GossipAwareAgent.java`
- **Type**: Abstract class
- **Purpose**: Base class for agents that participate in gossip
- **Key Features**:
  - Integration with GossipProtocol
  - Lifecycle management (start/stop)
  - Peer registration/unregistration
  - Abstract receive() method for message handling

### 4. PeerConnection
- **Location**: `src/main/java/org/yawlfoundation/yawl/integration/a2a/PeerConnection.java`
- **Type**: Interface
- **Purpose**: Represents communication channel to peer agents
- **Key Features**:
  - Message delivery interface
  - Availability checking
  - Health monitoring support
  - RTT tracking

### 5. PeerRegistry
- **Location**: `src/main/java/org/yawlfoundation/yawl/integration/a2a/PeerRegistry.java`
- **Type**: Concrete class
- **Purpose**: Manages available peer connections
- **Key Features**:
  - Dynamic peer registration
  - Health monitoring and cleanup
  - Peer availability tracking

## Unit Tests

### 1. GossipMessageTest
- **Location**: `src/test/java/org/yawlfoundation/yawl/integration/a2a/GossipMessageTest.java`
- **Coverage**: Message creation, validation, expiration, propagation tracking

### 2. GossipProtocolTest
- **Location**: `src/test/java/org/yawlfoundation/yawl/integration/a2a/GossipProtocolTest.java`
- **Coverage**: Async behavior, message propagation, metrics, error handling

### 3. GossipAwareAgentTest
- **Location**: `src/test/java/org/yawlfoundation/yawl/integration/a2a/GossipAwareAgentTest.java`
- **Coverage**: Agent lifecycle, message handling, peer management

### 4. GossipIntegrationTest
- **Location**: `src/test/java/org/yawlfoundation/yawl/integration/a2a/GossipIntegrationTest.java`
- **Coverage**: Multi-agent epidemic propagation, partial network failure, high load scenarios

## Usage Example

```java
// Create peer registry
PeerRegistry registry = new PeerRegistry();

// Create gossip-aware agent
MyAgent agent = new MyAgent("agent-1", registry);
agent.start();

// Send a gossip message
GossipMessage<String> message = GossipMessage.ofType(
    "workflow-update",
    "case-123:approved",
    "agent-1",
    5000  // 5 second TTL
);
agent.send(message);

// Handle received messages
public void receive(GossipMessage<String> message) {
    System.out.println("Received: " + message.payload());
    // Process message...
}
```

## Key Features

### Virtual Thread Support
- All async operations use virtual threads for efficient concurrency
- No thread pool sizing needed (millions of virtual threads possible)
- Non-blocking message propagation

### Epidemic Protocol
- Messages spread through peer network like an epidemic
- Configurable propagation strategies
- Anti-entropy mechanisms prevent duplicate processing
- TTL management prevents infinite propagation

### Error Handling
- Graceful handling of network partitions
- Automatic retry for transient failures
- Comprehensive logging for debugging
- Metrics for monitoring performance

### Integration Ready
- Extends GossipAwareAgent for easy integration
- Peer-based architecture supports various transport layers
- Compatible with existing YAWL agent patterns

## Compilation Notes

The implementation uses Java 25 features:
- Records for immutable data structures
- Virtual threads for async operations
- Modern concurrent collections

To compile, ensure you have Java 25+ and the required dependencies (SLF4J, Jackson for JSON support).

## Testing

Run the test suite with:
```bash
mvn test -Dtest=GossipMessageTest,GossipProtocolTest,GossipAwareAgentTest,GossipIntegrationTest
```

The tests cover:
- Message lifecycle management
- Async propagation behavior
- Error scenarios and recovery
- Multi-agent coordination
- Performance under load