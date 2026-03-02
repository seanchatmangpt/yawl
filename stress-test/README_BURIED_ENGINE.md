# Buried Engine Implementation

This implementation provides a buried engine for YAWL with MCP (Model Context Protocol) wiring.

## Overview

The buried engine system consists of:

1. **BuriedEngine** - Core class that embeds YEngine with resource isolation
2. **BuriedEngineMcpAdapter** - MCP adapter that exposes YAWL capabilities via MCP
3. **BuriedEngineManager** - Manages multiple buried engines with lifecycle management
4. **BuriedEngineDemo** - Demo application showing how to use the buried engine

## Architecture

### BuriedEngine

The `BuriedEngine` class provides:
- Resource isolation per engine instance
- Virtual thread lifecycle management
- Automatic connection management
- Graceful shutdown support
- Metrics collection

Key features:
- Each engine has its own YEngine instance
- Separate connection pools for Interface A and B clients
- Virtual thread management for concurrent operations
- Resource isolation prevents interference between engines

### BuriedEngineMcpAdapter

The MCP adapter provides:
- MCP tools for workflow operations
- MCP resources for data access
- Background caching
- Graceful lifecycle management

Tools provided:
- `buried_engine_status` - Get engine status and metrics
- `launch_buried_case` - Launch a workflow case
- `get_buried_case` - Get case status and details
- `complete_buried_workitem` - Complete a work item
- `list_buried_specs` - List available specifications
- `list_buried_cases` - List running cases
- `list_buried_workitems` - List work items

Resources provided:
- `buried://engine/status` - Engine status and metrics
- `buried://engine/cases` - All running cases
- `buried://engine/workitems` - All work items
- `buried://engine/specs` - All specifications

### BuriedEngineManager

The manager provides:
- Centralized management of multiple engines
- Load balancing across engines
- Health monitoring and recovery
- Automatic cleanup of idle engines
- Metrics aggregation

## Usage

### Basic Usage

```java
// Create a buried engine
BuriedEngine engine = new BuriedEngine(
    "engine-1",
    "http://localhost:8080/yawl",
    "admin",
    "YAWL",
    Duration.ofSeconds(30),
    Duration.ofMinutes(5)
);

// Start the engine
engine.start();

// Launch a case
String caseId = engine.launchCase("SimpleProcess", Map.of(
    "description", "Test case"
));

// Complete a work item
engine.completeWorkItem("WI-123", "completed", Map.of(
    "result", "Task completed"
));

// Stop the engine
engine.stop();
```

### MCP Adapter Usage

```java
// Create MCP adapter
BuriedEngineMcpAdapter adapter = new BuriedEngineMcpAdapter(engine);

// Start MCP server
adapter.start();

// The adapter now provides MCP tools and resources
// In a real implementation, this would expose HTTP endpoints
```

### Manager Usage

```java
// Create manager
BuriedEngineManager manager = new BuriedEngineManager(
    "http://localhost:8080/yawl",
    "admin",
    "YAWL",
    Duration.ofSeconds(30),
    Duration.ofMinutes(5),
    3, // max engines
    30, // health check interval (seconds)
    60  // cleanup interval (seconds)
);

// Create and start engines
BuriedEngine engine1 = manager.createEngine("engine-1");
BuriedEngine engine2 = manager.createEngine("engine-2");

// Launch cases using load balancing
BuriedEngine engine = manager.getLeastLoadedEngine();
String caseId = engine.launchCase("SimpleProcess", null);

// Get metrics
BuriedEngine.ManagerMetrics metrics = manager.getMetrics();
System.out.println("Total engines: " + metrics.totalEngines());
System.out.println("Active engines: " + metrics.activeEngines());

// Shutdown manager
manager.shutdown();
```

## Virtual Thread Management

The buried engine uses virtual threads for:
- Concurrent task execution
- Background maintenance tasks
- Health monitoring
- Cache refresh operations

Virtual threads provide:
- Better scalability for I/O-bound operations
- Lightweight threading without OS thread overhead
- Automatic parking during I/O operations

## Resource Isolation

Each buried engine instance provides:
- Separate YEngine instance
- Separate Interface A and B clients
- Separate connection pools
- Separate virtual thread pools
- Separate resource cache
- Separate metrics collection

This ensures that multiple engines can run concurrently without interference.

## Metrics

The system provides comprehensive metrics:

### Engine Metrics
- `status` - Engine status (CREATED, STARTING, RUNNING, STOPPING, TERMINATED, ERROR)
- `isRunning` - Whether the engine is running
- `activeThreads` - Number of active virtual threads
- `launchedCases` - Total cases launched
- `completedCases` - Total cases completed
- `failedCases` - Total failed cases
- `uptime` - Engine uptime
- `idleTime` - Time since last activity
- `loadedSpecs` - Number of loaded specifications

### Manager Metrics
- `totalEngines` - Total number of engines
- `activeEngines` - Number of running engines
- `totalThreads` - Total active threads across all engines
- `launchedCases` - Total cases launched across all engines
- `completedCases` - Total cases completed across all engines
- `failedCases` - Total failed cases across all engines
- `maxEngines` - Maximum allowed engines

## Error Handling

The system provides comprehensive error handling:
- Connection failures with automatic retry
- Virtual thread interruption handling
- Resource cleanup on shutdown
- Graceful degradation
- Detailed logging

## Integration

The buried engine can be integrated with:

1. **YAWL Engine** - Connects to existing YAWL engine via HTTP
2. **MCP Protocol** - Provides MCP interface for AI agents
3. **Monitoring Systems** - Exposes metrics for monitoring
4. **Load Balancers** - Can be deployed behind load balancers
5. **Containerization** - Suitable for containerized deployment

## Configuration

Key configuration parameters:

- `yawlEngineUrl` - URL of the YAWL engine to connect to
- `yawlUsername` - Username for YAWL authentication
- `yawlPassword` - Password for YAWL authentication
- `connectionTimeout` - Timeout for connections
- `maxIdleTime` - Maximum idle time before cleanup
- `maxEngines` - Maximum number of engines to manage
- `healthCheckInterval` - Health check interval
- `cleanupInterval` - Cleanup interval

## Future Enhancements

Potential enhancements:

1. **Full MCP Implementation** - Complete MCP protocol implementation
2. **Database Integration** - Direct database access instead of HTTP
3. **Clustering Support** - Multi-node deployment support
4. **Advanced Load Balancing** - Intelligent load balancing algorithms
5. **Persistence** - Case and state persistence
6. **Plugin Architecture** - Extensible plugin system
7. **Advanced Monitoring** - Real-time monitoring and alerting