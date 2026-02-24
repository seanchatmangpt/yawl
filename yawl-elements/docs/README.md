# YAWL Elements

## Overview

YAWL Elements is the core domain module containing the complete set of YAWL workflow elements and process structures. It implements YAWL's Petri net semantics, including advanced features like cancellation regions, OR-joins, and multi-instance tasks.

### Purpose

- **Petri Net Modeling**: Provides complete YAWL Petri net implementation
- **Workflow Specification**: Core data structures for YAWL process definitions
- **State Management**: Workflow state and marking management
- **Data Handling**: Process data parameters, variables, and external data integration
- **Validation**: Comprehensive specification validation framework
- **Time Management**: Timer and scheduling functionality

## Key Classes and Interfaces

### Core Specification Classes
- **`YSpecification`** - Top-level workflow specification container
  - Contains root net, decompositions, data schema, and metadata
  - Manages specification versioning and identification
  - Provides validation capabilities

- **`YNet`** - Primary workflow net container
  - Implements Petri net semantics with YAWL extensions
  - Manages input/output conditions, tasks, and flows
  - Handles state transitions and marking updates

- **`YDecomposition`** - Sub-process decomposition container
  - Supports sub-nets and YAWL service gateways
  - Manages task decomposition and composition
  - Provides modular workflow structure

### Task Classes
- **`YTask`** - Abstract base class for all workflow tasks
- **`YAtomicTask`** - Atomic task with direct service execution
- **`YCompositeTask`** - Composite task with sub-net decomposition
- **`YTimerParameters`** - Task timer configuration for deadlines and delays

### Net Elements
- **`YConditionInterface`** - Interface for all condition types
- **`YInputCondition`** - Entry conditions for workflow nets
- **`YOutputCondition`** - Exit conditions for workflow nets
- **`YCondition`** - Internal workflow conditions
- **`YFlow`** - Petri net flow connections
- **`YAWLServiceGateway`** - External service integration point
- **`YAWLServiceReference`** - Service reference and binding

### Data Management
- **`YParameter`** - Input/output parameters for tasks and nets
- **`YVariable`** - Process variables for state persistence
- **`YAttributeMap`** - Extended attribute management
- **`YMultiInstanceAttributes`** - Multi-instance task configuration

### State Management
- **`YMarking`** - Current marking of tokens in the Petri net
- **`YSetOfMarkings`** - Collection of markings for state analysis
- **`YEnabledTransitionSet`** - Set of enabled transitions
- **`YInternalCondition`** - Internal condition evaluation

### External Data Integration
- **`ExternalDataGateway`** - Interface for external data access
- **`ExternalDataGatewayFactory`** - Factory for gateway implementations
- **`SimpleExternalDataGatewayImpl`** - Simple external data implementation
- **`HibernateEngine`** - Hibernate-based data persistence

### Predicate Evaluation
- **`PredicateEvaluator`** - Expression evaluation for guard conditions
- **`PredicateEvaluatorFactory`** - Factory for creating evaluators
- **`PredicateEvaluatorCache`** - Cache for performance optimization

### Validation Framework
- **`YVerifiable`** - Interface for validation-capable elements
- **`YSpecificationValidator`** - Main specification validation logic
- **`YNetLocalVarVerifier`** - Local variable verification
- **`YVerificationHandler`** - Validation result handling

### Results and Outcomes
- **`CaseOutcome`** - Base interface for case outcomes
- **`CaseCompleted`** - Successful workflow completion
- **`CaseFailed`** - Workflow failure
- **`CaseCancelled`** - Workflow cancellation
- **`EventResult`** - Event processing results

### Time Management
- **`YTimer`** - Timer functionality for workflow scheduling
- **`YTimedObject`** - Interface for timed objects
- **`YWorkItemTimer`** - Work item specific timers
- **`YTimerVariable`** - Timer variable management

### E2WFOJ Components
- **`E2WFOJNet`** - Extended to Web Flow Object Java transformation
- **`RNet`** - Reduced net representation
- **`RPlace`**, **`RTransition`**, **`RFlow`** - Petri net components
- **`RMarking`**, **`RSetOfMarkings`** - State management

## Submodules

### `org.yawlfoundation.yawl.elements.state`
- Petri net state and marking management
- Internal condition evaluation
- State transition logic

### `org.yawlfoundation.yawl.elements.data`
- Process data handling
- Parameter and variable management
- Data type validation

### `org.yawlfoundation.yawl.elements.data.external`
- External data integration
- Gateway implementations
- Hibernate integration

### `org.yawlfoundation.yawl.elements.predicate`
- Expression evaluation
- Guard condition processing
- Performance caching

### `org.yawlfoundation.yawl.elements.results`
- Workflow result handling
- Outcome processing
- Success/failure tracking

### `org.yawlfoundation.yawl.elements.e2wfoj`
- Extended to Web Flow Object Java transformation
- Reduced net representations
- Optimization components

## Dependencies

### Core Dependencies
- **yawl-utilities**: Exceptions and utility functions
- **JDOM2**: XML processing and marshalling
- **Jakarta XML Bind**: XML schema support
- **Jakarta Servlet API**: Web integration (provided scope)

### Processing Libraries
- **Jaxen**: XPath expression evaluation
- **Apache Commons**: Lang3, Collections4 for utilities

### Testing Dependencies
- **JUnit 5**: Unit testing framework
- **XMLUnit**: XML validation testing

## Usage Examples

### Basic Specification Creation
```java
// Create a new specification
YSpecification spec = new YSpecification();
spec.setSchemaVersion(YSchemaVersion.LATEST);

// Create root net
YNet rootNet = new YNet(spec, "RootNet");
spec.setRootNet(rootNet);

// Add input condition
YInputCondition inputCond = new YInputCondition("Input");
rootNet.addCondition(inputCond);

// Create atomic task
YAtomicTask task = new YAtomicTask("Task1");
rootNet.addTask(task);

// Connect elements
rootNet.addFlow(inputCond, task);
```

### Validation Example
```java
// Validate specification
YSpecificationValidator validator = new YSpecificationValidator();
YVerificationHandler handler = new YVerificationHandler();

boolean isValid = spec.validate(handler);
if (!isValid) {
    for (YVerificationHandler.ValidationError error : handler.getErrors()) {
        System.out.println(error.getMessage());
    }
}
```

### External Data Integration
```java
// Configure external data gateway
ExternalDataGateway gateway = ExternalDataGatewayFactory.newInstance("hibernate");
gateway.initialize("jdbc:mysql://localhost/yawl", "user", "password");

// Use in task
YVariable var = new YVariable("customerData");
var.setExternalDataSource("SELECT * FROM customers WHERE id = ?");
var.setExternalDataGateway(gateway);
```

### Time-based Workflow
```java
// Configure timer for task
YTimerParameters timer = new YTimerParameters();
timer.setTimerType(YTimerParameters.TimerType.DEADLINE);
timer.setTriggerType(YTimerParameters.TriggerType.TIMEOUT);
timer.setTimeDuration("PT30M"); // 30 minutes

task.setTimerParameters(timer);
```

## Architecture

### Layered Architecture
1. **Domain Layer**: Core YAWL elements and business logic
2. **State Management Layer**: Petri net state and marking management
3. **Data Layer**: Process data and external integration
4. **Validation Layer**: Specification and workflow validation
5. **Time Layer**: Timer and scheduling functionality

### Design Patterns
- **Builder Pattern**: Specification and net construction
- **Strategy Pattern**: Different evaluation and gateway strategies
- **Observer Pattern**: State change notifications
- **Factory Pattern**: Gateway and evaluator creation
- **Command Pattern**: Workflow event handling

### Key Relationships
- **Composition**: Specifications contain nets, nets contain elements
- **Aggregation**: Multiple tasks, conditions, and flows
- **Dependency**: External data and timer integrations
- **State**: Marking-based Petri net state transitions

## Performance Considerations

### Optimization Techniques
- **Caching**: Predicate evaluator caching for performance
- **Lazy Loading**: On-demand element loading
- **State Optimization**: Reduced marking representations
- **XML Processing**: Efficient XML marshalling/unmarshalling

### Scalability
- **Large Specifications**: Efficient processing of complex workflows
- **Multi-threading**: Thread-safe operations for concurrent access
- **Memory Management**: Proper cleanup and resource management

## Integration Points

### Engine Integration
- **YEngine**: Runtime engine integration
- **YPersistenceManager**: Data persistence
- **YWorkItemStatus**: Work item status management

### Schema Integration
- **YDataValidator**: Schema validation
- **YSchemaVersion**: Schema versioning
- **XSD Schema Integration**: XML schema processing

### Unmarshal Integration
- **YMarshal**: XML marshalling/unmarshalling
- **YMetaData**: Metadata handling
- **JDOMUtil**: XML processing utilities

## Development Notes

### Code Organization
- Package structure reflects logical grouping of related functionality
- Clear separation between core elements and extensions
- Comprehensive validation framework with detailed error reporting

### Testing Strategy
- Unit tests for core element functionality
- Integration tests for XML processing and validation
- Performance tests for large specifications

### Extensibility
- Plugin architecture for external data gateways
- Extensible validation framework
- Custom timer and predicate evaluation plugins

## Future Enhancements

- Enhanced validation rules and performance optimization
- Additional external data gateway implementations
- Advanced scheduling and timer features
- Integration with workflow monitoring tools
- Performance profiling and optimization tools