# YAWL Stateless Module

## Overview

The `yawl-stateless` module provides a **stateless facade** for the YAWL workflow engine designed for modern deployment scenarios including cloud-native, containerized, and serverless architectures.

Unlike the traditional `YEngine` which maintains persistent state internally, `YStatelessEngine` is designed for environments where workflow state is managed externally through marshaling and restoration mechanisms. This architecture enables seamless scaling, horizontal deployment, and state persistence in external systems like databases or distributed caches.

## Key Features

- **Stateless Execution Model**: Engine maintains no internal state between operations
- **Case Import/Export**: Full workflow serialization/deserialization capabilities
- **Event-Driven Architecture**: Comprehensive listener support for all workflow events
- **Case Monitoring**: Optional case monitoring with idle timeout detection
- **Multi-threaded Support**: Built-in support for concurrent case processing
- **REST-Friendly**: Designed for stateless microservice architectures

## Core Components

### YStatelessEngine
The primary entry point and facade class that provides the stateless API.

**Key Responsibilities:**
- Loading and managing YAWL specifications
- Launching and restoring workflow cases
- Processing work items (start/complete)
- Case state export/import for persistence
- Event notification management
- Case monitoring coordination

### YNetRunner
Case-specific runner that handles the execution state for individual workflow instances.

**Key Responsibilities:**
- Managing case-specific execution state
- Processing workflow transitions
- Handling work item lifecycle
- Managing data flow and variable scope

### Case Management Classes

#### YCase
Represents the runtime state of a workflow case with full export/import capabilities.

#### YCaseMonitor
Optional monitoring service that tracks active cases and detects idle patterns.

#### YCaseImportExportService
Handles serialization/deserialization of case state to/from XML format.

## Event Listeners

The stateless engine supports comprehensive event monitoring through these listener interfaces:

- **YCaseEventListener**: Case lifecycle events (start, complete, cancel)
- **YWorkItemEventListener**: Work item lifecycle events (enable, start, complete, cancel)
- **YExceptionEventListener**: Exception and error handling events
- **YLogEventListener**: Audit logging and tracing events
- **YTimerEventListener**: Timer expiration and timeout events

## Architecture Patterns

### Basic Usage Pattern

```java
// Create engine with case monitoring (60s timeout)
YStatelessEngine engine = new YStatelessEngine(60000);

// Load YAWL specification from XML
YSpecification spec = engine.unmarshalSpecification(specXML);

// Launch a new workflow case
Map<String, String> initialData = new HashMap<>();
initialData.put("customer", "ACME Corp");
YNetRunner runner = engine.launchCase(spec, "case-123", initialData);

// Process enabled work items
for (YWorkItem item : runner.getEnabledWorkItems()) {
    engine.startWorkItem(item);

    // Complete work item with output data
    Map<String, String> outputData = new HashMap<>();
    outputData.put("status", "approved");
    engine.completeWorkItem(item, outputData, null);
}

// Export case state for persistence
String caseXML = engine.unloadCase(runner.getCaseID());

// Later - restore from persisted state
YNetRunner restored = engine.restoreCase(caseXML);
```

### Event-Driven Pattern

```java
// Register event listeners
engine.addCaseEventListener(new MyCaseEventHandler());
engine.addWorkItemEventListener(new MyWorkItemEventHandler());

// Listeners receive events automatically
public class MyCaseEventHandler implements YCaseEventListener {
    @Override
    public void caseEvent(YCaseEvent event) {
        System.out.println("Case " + event.getCaseID() + " event: " + event.getType());
    }
}
```

## Module Dependencies

- **yawl-engine**: Core YAWL engine functionality
- **yawl-elements**: Core YAWL workflow elements and data structures
- **yawl-util**: Utility classes for XML handling, logging, etc.

## Configuration Options

### Constructor Parameters

```java
// Engine with case monitoring enabled (timeout in milliseconds)
YStatelessEngine engine = new YStatelessEngine(60000); // 60 seconds

// Engine without case monitoring
YStatelessEngine engine = new YStatelessEngine(0); // disabled
```

### Performance Considerations

1. **Thread Safety**: All operations are thread-safe for concurrent cases
2. **Memory Usage**: Case state is kept minimal when not active
3. **Event Notification**: Supports both single-threaded and multi-threaded event delivery

## API Reference

### Key Methods

#### Case Lifecycle
- `launchCase(YSpecification spec, String caseID, Map<String, String> data)`
- `restoreCase(String caseXML)`
- `unloadCase(String caseID)`
- `isCaseActive(String caseID)`

#### Work Item Processing
- `startWorkItem(YWorkItem item)`
- `completeWorkItem(YWorkItem item, Map<String, String> output, String error)`
- `cancelWorkItem(YWorkItem item, String reason)`
- `getEnabledWorkItems(String caseID)`

#### Event Management
- `addCaseEventListener(YCaseEventListener listener)`
- `removeCaseEventListener(YCaseEventListener listener)`
- `addWorkItemEventListener(YWorkItemEventListener listener)`
- `removeWorkItemEventListener(YWorkItemEventListener listener)`

#### Data Access
- `unmarshalSpecification(String xml)`
- `marshalSpecification(YSpecification spec)`
- `exportCase(String caseID)`
- `importCase(String caseXML)`

## Error Handling

The stateless engine provides comprehensive error handling through:
- Exception propagation with detailed error information
- Event-based error notification via listeners
- XML validation for imported data
- State consistency checks

## Testing

The module includes comprehensive test coverage for:
- Case lifecycle management
- Import/export functionality
- Event system integrity
- Thread safety and concurrency
- Error conditions and edge cases
- Performance benchmarks

## Deployment Considerations

### Containerized Environments
- State is externalized to persistent storage
- Multiple instances can be deployed horizontally
- Cases can migrate between instances
- Monitoring provides visibility into active cases

### Cloud-Native Patterns
- Ideal for Kubernetes deployments
- State can be stored in databases or object stores
- Auto-scaling based on case load
- Integration with cloud logging and monitoring

### Serverless Architectures
- State restoration enables cold-start handling
- Events can be integrated with cloud event systems
- Duration-based pricing models are supported
- Minimal warm-up time

## Performance Characteristics

- **Case Launch**: < 100ms for typical workflows
- **Work Item Processing**: < 50ms per item
- **Case Export**: Depends on workflow complexity
- **Memory Usage**: Linear with workflow complexity
- **Scalability**: Limited only by external storage capacity

## Migration from YEngine

Key differences from traditional YEngine:
1. **State Management**: External vs. internal
2. **API Pattern**: Stateless methods vs. persistent state
3. **Deployment**: Multiple instances vs. single instance
4. **Event System**: Comprehensive listener support
5. **Monitoring**: Built-in idle detection

## Version Information

- **Current Version**: 6.0.0
- **Java Requirements**: Java 8+
- **Thread Safety**: Concurrent case operations
- **Serialization**: Java Serializable + XML marshaling

---

*For more detailed API documentation, see the Javadoc in the `src/org/yawlfoundation/yawl/stateless/` package.*