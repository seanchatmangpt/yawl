# YAWL Refactoring Patterns for ggen Automation

**Document Purpose**: Catalog refactoring patterns that ggen can systematically apply to the YAWL codebase based on specifications.

**Target Codebase**: `/Users/sac/cre/vendors/yawl/src/`

**Generated**: 2026-02-16

---

## Pattern 1: Extract Interface from Implementation

### Description
Extract interfaces from concrete classes that have multiple implementations or could benefit from contract-based design.

### Current State
Many classes implement behavior directly without interface contracts, making testing difficult and limiting extensibility.

### Example: YEngine Multi-Interface Implementation

**File**: `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/engine/YEngine.java`

```java
// Current: YEngine implements 4 interfaces directly
public class YEngine implements InterfaceADesign,
                                InterfaceAManagement,
                                InterfaceBClient,
                                InterfaceBInterop {
    // 1800+ lines of mixed concerns
}
```

### Refactoring Template
```java
// Step 1: Extract unified interface
public interface YWorkflowEngine extends
        InterfaceADesign, InterfaceAManagement,
        InterfaceBClient, InterfaceBInterop {

    // Core lifecycle operations
    void initialise() throws YAWLException;
    void shutdown();

    // Case management
    String launchCase(YSpecificationID specID, String caseParams,
                      URI completionObserver, YLogDataItemList logData)
            throws YStateException, YDataStateException;

    // Work item operations
    YWorkItem startWorkItem(YWorkItem workItem, YClient client);
    void completeWorkItem(YWorkItem workItem, String data,
                          String logPredicate, WorkItemCompletion flag);
}

// Step 2: Separate stateful from stateless implementations
public class StatefulYEngine implements YWorkflowEngine {
    // Full persistence-aware implementation
}

public class StatelessYEngine implements YWorkflowEngine {
    // No persistence, pure workflow execution
}
```

### ggen Specification
```yaml
pattern: extract-interface
source: YEngine.java
target-interface: YWorkflowEngine
extract-methods:
  - public methods matching "^(launch|cancel|start|complete|get|set).*$"
exclude-methods:
  - private.*$
  - protected.*$
generate-delegate: true
```

### Benefits
- Enables mock injection for testing
- Allows alternative implementations (stateful vs stateless)
- Clear contract for integration points

---

## Pattern 2: Immutable Value Object Conversion

### Description
Convert mutable data-holding classes to immutable value objects using builder pattern.

### Current State
Value objects have setters allowing mutation, leading to thread-safety concerns and defensive copying.

### Example: YWorkItemID - Value Object Candidate

**File**: `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/engine/YWorkItemID.java`

```java
// Current: Mutable with getter/setter pattern
public class YWorkItemID {
    private YIdentifier _caseID;
    private String _taskID;
    private char[] _uniqueID;

    public YWorkItemID(YIdentifier caseID, String taskID) {
        _caseID = caseID;
        _taskID = taskID;
        _uniqueID = _uniqifier.clone();
    }

    // Mutable state allows modification
    public void setCaseID(YIdentifier id) { _caseID = id; }
}
```

### Refactoring Template
```java
// After: Immutable with builder
@Immutable
public final class YWorkItemID {
    private final YIdentifier caseID;
    private final String taskID;
    private final String uniqueID;

    private YWorkItemID(Builder builder) {
        this.caseID = Objects.requireNonNull(builder.caseID);
        this.taskID = Objects.requireNonNull(builder.taskID);
        this.uniqueID = builder.uniqueID != null
            ? builder.uniqueID
            : UniqueIDGenerator.next();
    }

    // Only getters - no setters
    public YIdentifier getCaseID() { return caseID; }
    public String getTaskID() { return taskID; }
    public String getUniqueID() { return uniqueID; }

    // Builder for construction
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private YIdentifier caseID;
        private String taskID;
        private String uniqueID;

        public Builder caseID(YIdentifier caseID) {
            this.caseID = caseID;
            return this;
        }

        public Builder taskID(String taskID) {
            this.taskID = taskID;
            return this;
        }

        public Builder uniqueID(String uniqueID) {
            this.uniqueID = uniqueID;
            return this;
        }

        public YWorkItemID build() {
            return new YWorkItemID(this);
        }
    }

    // Proper equals/hashCode for value semantics
    @Override
    public boolean equals(Object o) { /* ... */ }

    @Override
    public int hashCode() { /* ... */ }
}
```

### ggen Specification
```yaml
pattern: immutable-value-object
source: YWorkItemID.java
fields:
  - name: caseID
    type: YIdentifier
    nullable: false
  - name: taskID
    type: String
    nullable: false
  - name: uniqueID
    type: String
    nullable: true
    default: UniqueIDGenerator.next()
generate:
  - builder
  - equals-hashCode
  - toString
annotations:
  - "@Immutable"
```

### Benefits
- Thread-safe by design
- No defensive copying needed
- Hash-based collections work correctly
- Clear intent: value rather than entity

---

## Pattern 3: Strategy Pattern for Pluggable Behavior

### Description
Extract varying algorithms into strategy interfaces, allowing runtime selection.

### Current State
Conditional logic embedded in classes makes extension difficult.

### Example: External Data Gateway Strategy

**File**: `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/elements/data/external/ExternalDataGatewayFactory.java`

```java
// Current: Factory with reflection-based instantiation
public class ExternalDataGatewayFactory {
    public static ExternalDataGateway getInstance(String classname) {
        Class<ExternalDataGateway> gatewayClass = getClassMap().get(classname);
        return (gatewayClass != null)
            ? _loader.newInstance(gatewayClass)
            : _loader.loadInstance(ExternalDataGateway.class, classname);
    }
}
```

### Refactoring Template
```java
// Define strategy interface (already exists)
public interface ExternalDataGateway {
    String getDescription();
    Element populateTaskParameter(YTask task, YParameter param, Element caseData);
    void updateFromTaskCompletion(YTask task, String paramName,
                                   Element outputData, Element caseData);
}

// Strategy registry with type-safe enum
public enum GatewayType {
    DATABASE("database", DatabaseGateway.class),
    REST_API("rest", RestApiGateway.class),
    FILE("file", FileGateway.class),
    MESSAGE_QUEUE("mq", MessageQueueGateway.class);

    private final String code;
    private final Class<? extends ExternalDataGateway> gatewayClass;

    // Enum-based factory
    public ExternalDataGateway createGateway() {
        try {
            return gatewayClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new GatewayCreationException(code, e);
        }
    }
}

// Context class with strategy selection
public class ExternalDataContext {
    private final Map<String, ExternalDataGateway> gatewayCache = new ConcurrentHashMap<>();

    public ExternalDataGateway getGateway(String expression) {
        String gatewayCode = GatewayType.fromExpression(expression).getCode();
        return gatewayCache.computeIfAbsent(gatewayCode,
            code -> GatewayType.fromCode(code).createGateway());
    }
}
```

### ggen Specification
```yaml
pattern: strategy-extraction
source-interface: ExternalDataGateway.java
strategies:
  - name: DatabaseGateway
    code: database
  - name: RestApiGateway
    code: rest
  - name: FileGateway
    code: file
generate:
  - strategy-enum
  - strategy-context
  - strategy-factory
registration: service-loader
```

### Benefits
- Open/Closed Principle: add new gateways without modifying factory
- Type-safe strategy selection
- Easy testing with mock strategies

---

## Pattern 4: Builder Pattern for Complex Construction

### Description
Replace telescoping constructors with fluent builder pattern.

### Current State
Multiple constructors with varying parameters create confusion.

### Example: YVariable Construction

**File**: `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/elements/data/YVariable.java`

```java
// Current: Multiple constructors and setter chains
public class YVariable implements Cloneable, YVerifiable {
    protected YDecomposition _parentDecomposition;
    protected String _dataTypeName;
    protected String _name;
    protected String _elementName;
    protected String _initialValue;
    protected String _defaultValue;
    protected String _namespaceURI;
    protected boolean _isUntyped = false;
    private String _documentation;
    private boolean _mandatory;
    private YLogPredicate _logPredicate;
    private YAttributeMap _attributes = new YAttributeMap();

    // Deprecated constructor
    public YVariable(YDecomposition dec, String dataType, String name,
                     String initialValue, String namespaceURI) { ... }

    // Another constructor
    public YVariable(YDecomposition dec) { ... }

    // Setter chains required for full configuration
    variable.setDataTypeAndName(dataType, name, namespace);
    variable.setUntyped(true);
    variable.setElementName(elementName);
    variable.setInitialValue(initialValue);
    variable.setMandatory(true);
}
```

### Refactoring Template
```java
// After: Fluent builder
public final class YVariable implements Cloneable, YVerifiable {
    private final YDecomposition parentDecomposition;
    private final String dataTypeName;
    private final String name;
    private final String elementName;
    private final String initialValue;
    private final String defaultValue;
    private final String namespaceURI;
    private final boolean untyped;
    private final String documentation;
    private final boolean mandatory;
    private final YLogPredicate logPredicate;
    private final YAttributeMap attributes;

    private YVariable(Builder builder) {
        this.parentDecomposition = builder.parentDecomposition;
        this.dataTypeName = builder.dataTypeName;
        this.name = Objects.requireNonNull(builder.name, "name required");
        this.elementName = builder.elementName;
        this.initialValue = builder.initialValue;
        this.defaultValue = builder.defaultValue;
        this.namespaceURI = builder.namespaceURI;
        this.untyped = builder.untyped;
        this.documentation = builder.documentation;
        this.mandatory = builder.mandatory;
        this.logPredicate = builder.logPredicate;
        this.attributes = builder.attributes != null
            ? builder.attributes
            : new YAttributeMap();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private YDecomposition parentDecomposition;
        private String dataTypeName;
        private String name;
        private String elementName;
        private String initialValue;
        private String defaultValue;
        private String namespaceURI;
        private boolean untyped = false;
        private String documentation;
        private boolean mandatory = false;
        private YLogPredicate logPredicate;
        private YAttributeMap attributes;

        public Builder parentDecomposition(YDecomposition parent) {
            this.parentDecomposition = parent;
            return this;
        }

        public Builder dataType(String dataType, String namespace) {
            this.dataTypeName = dataType;
            this.namespaceURI = namespace;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder elementName(String elementName) {
            this.elementName = elementName;
            return this;
        }

        public Builder initialValue(String value) {
            this.initialValue = value;
            return this;
        }

        public Builder defaultValue(String value) {
            this.defaultValue = value;
            return this;
        }

        public Builder untyped(boolean untyped) {
            this.untyped = untyped;
            return this;
        }

        public Builder mandatory(boolean mandatory) {
            this.mandatory = mandatory;
            return this;
        }

        public Builder documentation(String documentation) {
            this.documentation = documentation;
            return this;
        }

        public Builder logPredicate(YLogPredicate predicate) {
            this.logPredicate = predicate;
            return this;
        }

        public Builder attributes(YAttributeMap attributes) {
            this.attributes = attributes;
            return this;
        }

        public YVariable build() {
            return new YVariable(this);
        }
    }
}

// Usage
YVariable variable = YVariable.builder()
    .parentDecomposition(decomp)
    .dataType("xs:string", "http://www.w3.org/2001/XMLSchema")
    .name("orderId")
    .initialValue("ORDER-001")
    .mandatory(true)
    .documentation("Unique order identifier")
    .build();
```

### ggen Specification
```yaml
pattern: fluent-builder
source: YVariable.java
fields:
  - { name: parentDecomposition, type: YDecomposition, nullable: true }
  - { name: dataTypeName, type: String, nullable: true }
  - { name: name, type: String, nullable: false }
  - { name: elementName, type: String, nullable: true }
  - { name: initialValue, type: String, nullable: true }
  - { name: defaultValue, type: String, nullable: true }
  - { name: namespaceURI, type: String, nullable: true }
  - { name: untyped, type: boolean, default: false }
  - { name: mandatory, type: boolean, default: false }
  - { name: documentation, type: String, nullable: true }
  - { name: logPredicate, type: YLogPredicate, nullable: true }
  - { name: attributes, type: YAttributeMap, default: "new YAttributeMap()" }
validation:
  - name != null
generate:
  - builder
  - getters
  - equals-hashCode
```

### Benefits
- Readable construction with named parameters
- Default values handled cleanly
- Immutability support
- IDE auto-completion friendly

---

## Pattern 5: Null Object Pattern for Optional Returns

### Description
Replace null returns with explicit Optional or Null Object implementations.

### Current State
Methods return null forcing callers to check, leading to NullPointerExceptions.

### Example: YSpecification Data Access

**File**: `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/elements/YSpecification.java`

```java
// Current: Returns null for missing data
public String getDataSchema() {
    return (_dataValidator != null) ? _dataValidator.getSchema() : null;
}

// Callers must check null
String schema = spec.getDataSchema();
if (schema != null) {
    // use schema
}
```

### Refactoring Template
```java
// Option 1: Use Java Optional
public Optional<String> getDataSchema() {
    return Optional.ofNullable(_dataValidator)
                   .map(YDataValidator::getSchema);
}

// Callers use functional style
spec.getDataSchema()
    .ifPresent(schema -> processSchema(schema));

// Option 2: Null Object Pattern
public interface DataSchema {
    String getContent();
    boolean isValid();
    boolean isEmpty();
}

public final class EmptyDataSchema implements DataSchema {
    public static final EmptyDataSchema INSTANCE = new EmptyDataSchema();

    @Override
    public String getContent() { return ""; }

    @Override
    public boolean isValid() { return false; }

    @Override
    public boolean isEmpty() { return true; }
}

public final class ValidDataSchema implements DataSchema {
    private final String content;

    public ValidDataSchema(String content) {
        this.content = Objects.requireNonNull(content);
    }

    @Override
    public String getContent() { return content; }

    @Override
    public boolean isValid() { return true; }

    @Override
    public boolean isEmpty() { return content.isEmpty(); }
}

// Usage - no null checks needed
DataSchema schema = spec.getDataSchema();
if (schema.isValid()) {
    processSchema(schema.getContent());
}
```

### ggen Specification
```yaml
pattern: null-object
source: YSpecification.java
methods:
  - name: getDataSchema
    return-type: DataSchema
    null-return: EmptyDataSchema.INSTANCE
  - name: getRootNet
    return-type: YNet
    null-return: Optional<YNet>
generate:
  - null-object-interface
  - empty-implementation
  - valid-implementation
optional-wrappers:
  - getDataSchema
  - getRootNet
  - getMetaData
```

### Benefits
- Eliminates NullPointerException
- Clear intent: value may be absent
- Functional composition with Optional

---

## Pattern 6: Event Listener Pattern Standardization

### Description
Standardize event listener registration and notification across the codebase.

### Current State
Multiple listener patterns exist with inconsistent naming and registration.

### Example: Disparate Listener Patterns

**Files**:
- `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/engine/interfce/interfaceB/InterfaceBClientObserver.java`
- `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/stateless/listener/YCaseEventListener.java`

```java
// Pattern 1: Observer interface
public interface InterfaceBClientObserver {
    void newWorkItem(YWorkItem item);
    void workItemStatusChanged(YWorkItem item);
    void caseCompleted(String caseID);
}

// Pattern 2: Event-based interface
public interface YCaseEventListener {
    void handleCaseEvent(YCaseEvent event);
}
```

### Refactoring Template
```java
// Standardized event bus pattern
public interface YEventListener<T extends YEvent> {
    void onEvent(T event);
    Class<T> getEventType();
}

// Base event class
public abstract class YEvent {
    private final Instant timestamp;
    private final YIdentifier caseID;
    private final YEventType eventType;

    protected YEvent(YEventType type, YIdentifier caseID) {
        this.timestamp = Instant.now();
        this.eventType = type;
        this.caseID = caseID;
    }

    // Getters...
}

// Specific event types
public final class YWorkItemEvent extends YEvent {
    private final YWorkItem workItem;
    private final YWorkItemStatus previousStatus;
    private final YWorkItemStatus newStatus;

    public YWorkItemEvent(YEventType type, YIdentifier caseID,
                          YWorkItem workItem,
                          YWorkItemStatus previousStatus,
                          YWorkItemStatus newStatus) {
        super(type, caseID);
        this.workItem = workItem;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
    }
}

// Event bus implementation
public class YEventBus {
    private final Map<Class<?>, List<YEventListener<?>>> listeners = new ConcurrentHashMap<>();

    public <T extends YEvent> void register(YEventListener<T> listener) {
        listeners.computeIfAbsent(listener.getEventType(), k -> new CopyOnWriteArrayList<>())
                 .add(listener);
    }

    public <T extends YEvent> void unregister(YEventListener<T> listener) {
        List<YEventListener<?>> eventListeners = listeners.get(listener.getEventType());
        if (eventListeners != null) {
            eventListeners.remove(listener);
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends YEvent> void publish(T event) {
        List<YEventListener<?>> eventListeners = listeners.get(event.getClass());
        if (eventListeners != null) {
            for (YEventListener<?> listener : eventListeners) {
                ((YEventListener<T>) listener).onEvent(event);
            }
        }
    }
}

// Usage
eventBus.register(new YEventListener<YWorkItemEvent>() {
    @Override
    public void onEvent(YWorkItemEvent event) {
        System.out.println("WorkItem " + event.getWorkItem().getID()
            + " changed from " + event.getPreviousStatus()
            + " to " + event.getNewStatus());
    }

    @Override
    public Class<YWorkItemEvent> getEventType() {
        return YWorkItemEvent.class;
    }
});
```

### ggen Specification
```yaml
pattern: event-listener-standardization
base-event: YEvent.java
events:
  - name: YWorkItemEvent
    fields:
      - { name: workItem, type: YWorkItem }
      - { name: previousStatus, type: YWorkItemStatus }
      - { name: newStatus, type: YWorkItemStatus }
    event-types:
      - WORK_ITEM_CREATED
      - WORK_ITEM_STATUS_CHANGED
      - WORK_ITEM_COMPLETED

  - name: YCaseEvent
    fields:
      - { name: caseID, type: YIdentifier }
      - { name: specID, type: YSpecificationID }
    event-types:
      - CASE_STARTED
      - CASE_COMPLETED
      - CASE_CANCELLED
      - CASE_IDLE_TIMEOUT

generate:
  - event-bus
  - base-event
  - specific-events
  - listener-interface
thread-safety: CopyOnWriteArrayList
```

### Benefits
- Consistent event handling across codebase
- Type-safe event dispatch
- Easy to add new event types
- Decoupled publishers and subscribers

---

## Pattern 7: Factory Method Pattern for Object Creation

### Description
Centralize object creation logic in factory methods/classes.

### Current State
Object creation scattered throughout codebase with duplicated logic.

### Example: YNetRunner Construction

**File**: `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/engine/YNetRunner.java`

```java
// Current: Multiple constructors with complex initialization
public class YNetRunner {
    protected YNetRunner() {
        _engine = YEngine.getInstance();
        init();
    }

    public YNetRunner(YPersistenceManager pmgr, YNet netPrototype,
                      Element paramsData, String caseID)
            throws YStateException, YDataStateException {
        this();
        _caseIDForNet = new YIdentifier(caseID);
        if (pmgr != null) pmgr.storeObject(_caseIDForNet);
        // ... more initialization
    }

    public YNetRunner(YPersistenceManager pmgr, YNet netPrototype,
                      YCompositeTask container, YIdentifier caseIDForNet,
                      Element incomingData)
            throws YDataStateException {
        this();
        // ... different initialization
    }
}
```

### Refactoring Template
```java
// Factory class with clear creation methods
public final class YNetRunnerFactory {

    private final YEngine engine;

    public YNetRunnerFactory(YEngine engine) {
        this.engine = Objects.requireNonNull(engine);
    }

    /**
     * Creates a root net runner for a new case.
     */
    public YNetRunner createRootRunner(YNet netPrototype, Element paramsData,
                                        Optional<String> caseID,
                                        Optional<YPersistenceManager> pmgr)
            throws YStateException, YDataStateException {

        YIdentifier identifier = caseID
            .map(YIdentifier::new)
            .orElseGet(() -> new YIdentifier(engine.getNextCaseNbr()));

        pmgr.ifPresent(p -> p.storeObject(identifier));

        Element effectiveParams = getEffectiveParams(netPrototype, identifier, paramsData);

        return new YNetRunner(netPrototype, identifier, effectiveParams, engine);
    }

    /**
     * Creates a sub-net runner for a composite task.
     */
    public YNetRunner createSubnetRunner(YNet netPrototype,
                                          YCompositeTask container,
                                          YIdentifier caseID,
                                          Element incomingData)
            throws YDataStateException {

        return new YNetRunner(netPrototype, container, caseID, incomingData, engine);
    }

    /**
     * Creates a runner from persisted state.
     */
    public YNetRunner restoreRunner(YNet netPrototype, YIdentifier caseID,
                                     String serializedState) {
        YNetRunner runner = new YNetRunner(netPrototype, caseID, null, engine);
        runner.restoreFrom(serializedState);
        return runner;
    }

    private Element getEffectiveParams(YNet net, YIdentifier caseID, Element params) {
        Element externalData = net.getCaseDataFromExternal(caseID.toString());
        return externalData != null ? externalData : params;
    }
}

// Simplified YNetRunner with single constructor
public class YNetRunner {
    private final YNet net;
    private final YIdentifier caseID;
    private final YEngine engine;
    // ... other fields

    // Package-private - use factory
    YNetRunner(YNet net, YIdentifier caseID, Element params, YEngine engine) {
        this.net = (YNet) net.clone();
        this.caseID = caseID;
        this.engine = engine;
        initialize(params);
    }

    // ... rest of implementation
}

// Usage
YNetRunnerFactory factory = new YNetRunnerFactory(engine);
YNetRunner runner = factory.createRootRunner(net, params, Optional.empty(), Optional.of(pmgr));
```

### ggen Specification
```yaml
pattern: factory-method
source: YNetRunner.java
factory-class: YNetRunnerFactory
creation-methods:
  - name: createRootRunner
    description: Creates a root net runner for a new case
    parameters:
      - { name: netPrototype, type: YNet }
      - { name: paramsData, type: Element }
      - { name: caseID, type: "Optional<String>" }
      - { name: pmgr, type: "Optional<YPersistenceManager>" }
    exceptions:
      - YStateException
      - YDataStateException

  - name: createSubnetRunner
    description: Creates a sub-net runner for a composite task
    parameters:
      - { name: netPrototype, type: YNet }
      - { name: container, type: YCompositeTask }
      - { name: caseID, type: YIdentifier }
      - { name: incomingData, type: Element }
    exceptions:
      - YDataStateException

  - name: restoreRunner
    description: Creates a runner from persisted state
    parameters:
      - { name: netPrototype, type: YNet }
      - { name: caseID, type: YIdentifier }
      - { name: serializedState, type: String }
constructor-visibility: package-private
```

### Benefits
- Clear creation semantics
- Centralized initialization logic
- Easy to add new creation variants
- Better testability with dependency injection

---

## Pattern 8: Template Method Pattern for Verification

### Description
Extract common verification algorithm structure into abstract base class.

### Current State
Verification logic duplicated across verifiable classes.

### Example: YVerifiable Implementation

**File**: `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/elements/YVerifiable.java`

```java
// Current: Interface only
public interface YVerifiable {
    void verify(YVerificationHandler verificationHandler);
}

// Each implementation repeats patterns
public class YSpecification implements YVerifiable {
    public void verify(YVerificationHandler handler) {
        if (_rootNet == null) {
            handler.error(this, "Root net must not be null");
        }
        if (_specURI == null || _specURI.isEmpty()) {
            handler.error(this, "Specification URI must be set");
        }
        // ... more checks
        _rootNet.verify(handler);
    }
}
```

### Refactoring Template
```java
// Abstract base with template method
public abstract class AbstractVerifiable implements YVerifiable {

    @Override
    public final void verify(YVerificationHandler handler) {
        verifyPreconditions(handler);
        if (!handler.hasErrors()) {
            verifyState(handler);
            verifyReferences(handler);
            verifyChildren(handler);
        }
        verifyPostconditions(handler);
    }

    /**
     * Verify preconditions before main verification.
     */
    protected void verifyPreconditions(YVerificationHandler handler) {
        // Default: no preconditions
    }

    /**
     * Verify internal state consistency.
     */
    protected abstract void verifyState(YVerificationHandler handler);

    /**
     * Verify references to other objects.
     */
    protected void verifyReferences(YVerificationHandler handler) {
        // Default: no references
    }

    /**
     * Verify child elements.
     */
    protected void verifyChildren(YVerificationHandler handler) {
        // Default: no children
    }

    /**
     * Verify postconditions after main verification.
     */
    protected void verifyPostconditions(YVerificationHandler handler) {
        // Default: no postconditions
    }

    // Utility methods for subclasses
    protected void requireNonNull(YVerificationHandler handler,
                                   Object value, String fieldName) {
        if (value == null) {
            handler.error(this, fieldName + " must not be null");
        }
    }

    protected void requireNonEmpty(YVerificationHandler handler,
                                    String value, String fieldName) {
        if (value == null || value.isEmpty()) {
            handler.error(this, fieldName + " must not be empty");
        }
    }

    protected void requireCondition(YVerificationHandler handler,
                                     boolean condition, String message) {
        if (!condition) {
            handler.error(this, message);
        }
    }
}

// Concrete implementation
public final class YSpecification extends AbstractVerifiable {

    @Override
    protected void verifyState(YVerificationHandler handler) {
        requireNonNull(handler, _rootNet, "rootNet");
        requireNonEmpty(handler, _specURI, "specificationURI");
        requireCondition(handler,
            _version != null,
            "schema version must be set");
    }

    @Override
    protected void verifyChildren(YVerificationHandler handler) {
        if (_rootNet != null) {
            _rootNet.verify(handler);
        }
        for (YDecomposition decomp : _decompositions.values()) {
            decomp.verify(handler);
        }
    }
}

// Another implementation
public final class YTask extends AbstractVerifiable {

    @Override
    protected void verifyState(YVerificationHandler handler) {
        requireNonNull(handler, _id, "id");
        requireCondition(handler,
            _splitType == _AND || _splitType == _OR || _splitType == _XOR,
            "split type must be AND, OR, or XOR");
        requireCondition(handler,
            _joinType == _AND || _joinType == _OR || _joinType == _XOR,
            "join type must be AND, OR, or XOR");
    }

    @Override
    protected void verifyReferences(YVerificationHandler handler) {
        if (_decompositionPrototype != null) {
            _decompositionPrototype.verify(handler);
        }
    }
}
```

### ggen Specification
```yaml
pattern: template-method
base-class: AbstractVerifiable
interface: YVerifiable
template-methods:
  - name: verifyPreconditions
    phase: pre
    default: empty
  - name: verifyState
    phase: main
    abstract: true
  - name: verifyReferences
    phase: main
    default: empty
  - name: verifyChildren
    phase: main
    default: empty
  - name: verifyPostconditions
    phase: post
    default: empty
utility-methods:
  - requireNonNull
  - requireNonEmpty
  - requireCondition
  - requireInRange
implementations:
  - YSpecification
  - YTask
  - YDecomposition
  - YNet
  - YVariable
```

### Benefits
- DRY verification logic
- Clear extension points
- Consistent error reporting
- Easy to add new verification steps

---

## Pattern 9: State Pattern for Workflow Status

### Description
Replace enum-based status with State pattern for complex status-dependent behavior.

### Current State
Status is an enum with switch statements scattered throughout.

### Example: YWorkItemStatus

**File**: `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/engine/YWorkItemStatus.java`

```java
// Current: Simple enum
public enum YWorkItemStatus {
    statusEnabled, statusFired, statusExecuting, statusComplete,
    statusIsParent, statusDeadlocked, statusDeleted, statusWithdrawn,
    statusForcedComplete, statusFailed, statusSuspended,
    statusCancelledByCase, statusDiscarded;
}

// Switch statements in YWorkItem
public boolean isEnabled() {
    switch (_status) {
        case statusEnabled: return true;
        default: return false;
    }
}

public boolean canBeStarted() {
    switch (_status) {
        case statusEnabled:
        case statusFired:
            return true;
        default:
            return false;
    }
}
```

### Refactoring Template
```java
// State interface
public interface WorkItemState {
    boolean canBeStarted();
    boolean canBeCompleted();
    boolean canBeSuspended();
    boolean canBeCancelled();
    boolean isActive();
    WorkItemState transitionTo(WorkItemEvent event);
    String getName();
}

// Concrete states
public final class EnabledState implements WorkItemState {
    public static final EnabledState INSTANCE = new EnabledState();

    @Override
    public boolean canBeStarted() { return true; }

    @Override
    public boolean canBeCompleted() { return false; }

    @Override
    public boolean canBeSuspended() { return false; }

    @Override
    public boolean canBeCancelled() { return true; }

    @Override
    public boolean isActive() { return true; }

    @Override
    public WorkItemState transitionTo(WorkItemEvent event) {
        switch (event) {
            case FIRE: return FiredState.INSTANCE;
            case CANCEL: return CancelledState.INSTANCE;
            case DEADLOCK: return DeadlockedState.INSTANCE;
            default: throw new IllegalStateTransition(this, event);
        }
    }

    @Override
    public String getName() { return "Enabled"; }
}

public final class FiredState implements WorkItemState {
    public static final FiredState INSTANCE = new FiredState();

    @Override
    public boolean canBeStarted() { return true; }

    @Override
    public boolean canBeCompleted() { return true; }

    @Override
    public boolean canBeSuspended() { return true; }

    @Override
    public boolean canBeCancelled() { return true; }

    @Override
    public boolean isActive() { return true; }

    @Override
    public WorkItemState transitionTo(WorkItemEvent event) {
        switch (event) {
            case START: return ExecutingState.INSTANCE;
            case CANCEL: return CancelledState.INSTANCE;
            case SUSPEND: return SuspendedState.INSTANCE;
            default: throw new IllegalStateTransition(this, event);
        }
    }

    @Override
    public String getName() { return "Fired"; }
}

// Context class
public class YWorkItem {
    private WorkItemState state = EnabledState.INSTANCE;

    public boolean canBeStarted() {
        return state.canBeStarted();
    }

    public void start() {
        this.state = state.transitionTo(WorkItemEvent.START);
    }

    public void complete() {
        this.state = state.transitionTo(WorkItemEvent.COMPLETE);
    }

    public WorkItemState getState() {
        return state;
    }
}

// State machine definition
public enum WorkItemEvent {
    FIRE, START, COMPLETE, SUSPEND, RESUME, CANCEL, FAIL, DEADLOCK
}
```

### ggen Specification
```yaml
pattern: state-pattern
source-enum: YWorkItemStatus
state-interface: WorkItemState
states:
  - name: Enabled
    transitions:
      - { event: FIRE, target: Fired }
      - { event: CANCEL, target: Cancelled }
      - { event: DEADLOCK, target: Deadlocked }
    capabilities:
      canBeStarted: true
      canBeCompleted: false

  - name: Fired
    transitions:
      - { event: START, target: Executing }
      - { event: CANCEL, target: Cancelled }
      - { event: SUSPEND, target: Suspended }
    capabilities:
      canBeStarted: true
      canBeCompleted: true
      canBeSuspended: true

  - name: Executing
    transitions:
      - { event: COMPLETE, target: Complete }
      - { event: SUSPEND, target: Suspended }
      - { event: FAIL, target: Failed }
    capabilities:
      canBeCompleted: true
      canBeSuspended: true

  - name: Suspended
    transitions:
      - { event: RESUME, target: Executing }
      - { event: CANCEL, target: Cancelled }
    capabilities:
      canBeCancelled: true

generate:
  - state-interface
  - concrete-states
  - context-class-updates
  - state-machine-diagram
```

### Benefits
- Eliminates switch statements
- Encapsulates state-specific behavior
- Clear state transition rules
- Easy to add new states

---

## Pattern 10: Composite Pattern for Net Elements

### Description
Unify treatment of individual and composite net elements.

### Current State
Different handling for conditions vs tasks vs composite tasks.

### Example: YNet Element Hierarchy

**Files**:
- `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/elements/YExternalNetElement.java`
- `/Users/sac/cre/vendors/yawl/src/org/yawlfoundation/yawl/elements/YTask.java`

```java
// Current: Flat hierarchy with instanceof checks
public void processElement(YExternalNetElement element) {
    if (element instanceof YTask) {
        YTask task = (YTask) element;
        // task-specific logic
    } else if (element instanceof YCondition) {
        YCondition condition = (YCondition) element;
        // condition-specific logic
    }
}
```

### Refactoring Template
```java
// Component interface
public interface YNetElementComponent {
    String getID();
    void accept(YNetElementVisitor visitor);
    YNet getNet();
    Set<YExternalNetElement> getPresetElements();
    Set<YExternalNetElement> getPostsetElements();
}

// Leaf components
public final class YCondition implements YNetElementComponent {
    private final String id;
    private final YNet net;

    @Override
    public void accept(YNetElementVisitor visitor) {
        visitor.visit(this);
    }

    // ... other methods
}

// Composite component
public abstract class YCompositeElement implements YNetElementComponent {
    protected final Map<String, YNetElementComponent> children = new HashMap<>();

    public void addChild(YNetElementComponent component) {
        children.put(component.getID(), component);
    }

    public void removeChild(String id) {
        children.remove(id);
    }

    public YNetElementComponent getChild(String id) {
        return children.get(id);
    }

    public Collection<YNetElementComponent> getChildren() {
        return children.values();
    }

    @Override
    public void accept(YNetElementVisitor visitor) {
        visitor.visit(this);
        for (YNetElementComponent child : children.values()) {
            child.accept(visitor);
        }
    }
}

// Visitor interface
public interface YNetElementVisitor {
    void visit(YCondition condition);
    void visit(YTask task);
    void visit(YCompositeTask compositeTask);
    void visit(YInputCondition inputCondition);
    void visit(YOutputCondition outputCondition);
}

// Concrete visitor
public class NetElementValidator implements YNetElementVisitor {
    private final List<String> errors = new ArrayList<>();

    @Override
    public void visit(YCondition condition) {
        validateCondition(condition);
    }

    @Override
    public void visit(YTask task) {
        validateTask(task);
    }

    @Override
    public void visit(YCompositeTask compositeTask) {
        validateCompositeTask(compositeTask);
    }

    private void validateCondition(YCondition condition) {
        if (condition.getPresetElements().isEmpty() &&
            !(condition instanceof YInputCondition)) {
            errors.add("Condition " + condition.getID() + " has no preset");
        }
    }

    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }
}

// Usage
YNetElementValidator validator = new NetElementValidator();
net.getRootNet().accept(validator);
if (!validator.getErrors().isEmpty()) {
    throw new ValidationException(validator.getErrors());
}
```

### ggen Specification
```yaml
pattern: composite-visitor
component-interface: YNetElementComponent
leaf-classes:
  - YCondition
  - YInputCondition
  - YOutputCondition
composite-classes:
  - YTask
  - YCompositeTask
  - YNet
visitor-interface: YNetElementVisitor
visitor-methods:
  - visit(YCondition)
  - visit(YTask)
  - visit(YCompositeTask)
  - visit(YInputCondition)
  - visit(YOutputCondition)
generate:
  - component-interface
  - composite-base
  - visitor-interface
  - default-visitors:
      - validator
      - serializer
      - pretty-printer
```

### Benefits
- Unified element handling
- Easy to add new operations via visitors
- Recursive structures handled naturally
- Type-safe element processing

---

## Summary Table

| Pattern | Source File(s) | Complexity | Automation Level |
|---------|---------------|------------|------------------|
| Extract Interface | YEngine.java | Medium | High |
| Immutable Value Object | YWorkItemID.java, YVariable.java | Low | High |
| Strategy Pattern | ExternalDataGatewayFactory.java | Medium | High |
| Builder Pattern | YVariable.java, YWorkItem.java | Low | High |
| Null Object Pattern | YSpecification.java | Low | Medium |
| Event Listener | InterfaceBClientObserver.java, YCaseEventListener.java | Medium | High |
| Factory Method | YNetRunner.java | Medium | High |
| Template Method | YVerifiable.java | Low | High |
| State Pattern | YWorkItemStatus.java | High | Medium |
| Composite Pattern | YNet, YTask hierarchy | High | Medium |

---

## ggen Implementation Roadmap

### Phase 1: High Automation Patterns (Week 1-2)
1. Immutable Value Object generator
2. Builder Pattern generator
3. Template Method generator

### Phase 2: Medium Automation Patterns (Week 3-4)
4. Strategy Pattern extractor
5. Factory Method generator
6. Event Listener standardizer

### Phase 3: Complex Patterns (Week 5-6)
7. Extract Interface analyzer
8. Null Object Pattern transformer
9. State Pattern generator
10. Composite Pattern refactoring

### Validation Strategy
Each generated refactoring must:
1. Pass existing unit tests
2. Maintain API compatibility
3. Generate appropriate documentation
4. Include migration guide for breaking changes
