# YAWL Engine Module

The YAWL Engine module is the core workflow engine that provides stateful workflow execution based on YAWL (Yet Another Workflow Language) specifications. This module implements Petri net semantics with YAWL-specific extensions for advanced workflow patterns.

## Module Purpose

The YAWL Engine serves as the runtime execution environment for YAWL workflow specifications, managing the complete lifecycle of workflow cases including creation, execution, monitoring, and completion.

## Key Classes and Interfaces

### Core Workflow Classes

- **`YNet`** - The primary workflow net container implementing Petri net semantics with YAWL extensions
  - Manages the complete control flow definition of a workflow process
  - Implements E2WFOJ (Extended Workflow Net with OR-Joins) algorithm
  - Handles net cloning for independent case instances
  - Supports external data gateways for integration with external systems

- **`YInputCondition`** - Single entry point of a workflow net
  - Represents the starting condition of the workflow
  - Must have exactly one input condition per net

- **`YOutputCondition`** - Single exit point of a workflow net
  - Represents the completion condition of the workflow
  - Must have exactly one output condition per net

- **`YTask`** - Workflow activities that process work items
  - `YAtomicTask` - Basic task that executes a single workflow step
  - `YCompositeTask` - Complex task composed of nested sub-nets

- **`YCondition`** - Intermediate conditions (places in Petri net terms)
  - Hold tokens between tasks
  - Enable/disable task execution based on token availability

### Data Management Classes

- **`YNetData`** - Container for workflow data associated with tokens
  - Stores data that moves through the workflow
  - Includes ID and content fields for case-specific information

- **`YVariable`** - Net-scoped data variables
  - Case-specific data that persists throughout workflow execution
  - Can be input, output, or local to specific nets

- **`YParameter`** - Formal parameters for task interfaces
  - Define the input/output contracts of tasks
  - Used for data passing between workflow elements

### Interface Layer

- **`YHttpServlet`** - Base HTTP servlet for web interfaces
  - Provides common functionality for YAWL web services
  - Handles authentication and logging

- **`InterfaceA_EnvironmentBasedClient`** - Client interface for external systems
  - Provides programmatic access to YAWL engine functionality
  - Used for case creation, work item management, and data retrieval

- **`InterfaceB_EnvironmentBasedClient`** - Client interface for work item handling
  - Focuses on work item operations (start, complete, cancel)
  - Used by external work item consumers

### State Management

- **`YMarking`** - Represents the state of the workflow net
  - Contains collections of marked conditions (token positions)
  - Updated as workflow execution progresses

- **`YIdentifier`** - Unique identifiers for workflow elements
  - Used to reference specific conditions, tasks, and variables
  - Ensures unambiguous element referencing

### Persistence and Metrics

- **`ReceiptStore`** - Stores receipts for workflow operations
  - Provides transactional guarantees for workflow operations
  - Maintains a chain of receipts for auditability

- **`InterfaceMetrics`** - Performance metrics collection
  - Tracks execution times, work item counts, and throughput
  - Used for monitoring and optimization

## Dependencies

### Internal Dependencies
- `yawl-elements` - Provides the YAWL data model and workflow net structure
- `yawl-utilities` - Provides utility classes for string handling, XML processing, etc.

### External Dependencies
- Jakarta Servlet API - For web interface capabilities
- Log4j - For logging and monitoring
- JDOM - For XML processing and YAWL specification parsing
- Hibernate - For persistence (if configured)

## Usage Examples

### Basic Workflow Execution

```java
// Load a YAWL specification
YSpecificationID specID = new YSpecificationID("Process", "1.0", "SampleProcess");
SpecificationData specData = interfaceAClient.getSpecification(specID);

// Create a new workflow case
String caseID = interfaceAClient.createCase(specID, initialData);

// Get work items for the case
List<WorkItemRecord> workItems = interfaceBClient.getWorkItems(caseID);

// Process a work item
WorkItemRecord workItem = workItems.get(0);
interfaceBClient.startWorkItem(workItem.getID());
interfaceBClient.completeWorkItem(workItem.getID(), outputData);
```

### Monitoring Workflow Progress

```java
// Get current case state
CaseState state = interfaceAClient.getCaseState(caseID);

// Check work item status
for (WorkItemRecord workItem : state.getWorkItems()) {
    switch (workItem.getStatus()) {
        case RUNNING:
            // Handle running work items
            break;
        case OFFERED:
            // Handle offered work items
            break;
        case COMPLETED:
            // Handle completed work items
            break;
    }
}
```

## Integration Points

### Web Interface
The engine provides REST-like endpoints through servlets for integration with external systems:
- `/case` - Case management operations
- `/workitem` - Work item operations
- `/specification` - Specification management

### External Systems Integration
- **External Data Gateways** - Enable integration with external data sources
- **Authentication Providers** - Support various authentication mechanisms
- **Monitoring Systems** - Metrics collection for performance monitoring

## Performance Characteristics

- **Stateful Execution** - Maintains complete workflow state for each case
- **OR-Join Optimization** - Implements efficient OR-join evaluation algorithm
- **Token-based Flow** - Uses Petri net token passing for workflow control
- **Thread Management** - Efficient thread pool management for concurrent work items

## Error Handling

The engine provides comprehensive error handling through:
- **Validation** - Pre-execution validation of workflow specifications
- **Exception Handling** - Structured exception hierarchy for different error types
- **Recovery Mechanisms** - Support for checkpoint and recovery
- **Deadlock Detection** - Built-in deadlock detection for workflow nets

## Configuration

The engine can be configured through:
- **Configuration Files** - XML-based configuration for engine parameters
- **System Properties** - JVM system properties for runtime configuration
- **Environment Variables** - Environment-specific settings

## Best Practices

1. **Use InterfaceA** for case and specification management
2. **Use InterfaceB** for work item operations
3. **Implement proper error handling** for workflow operations
4. **Monitor metrics** for performance optimization
5. **Use external data gateways** for complex data integration
6. **Validate specifications** before deployment to production