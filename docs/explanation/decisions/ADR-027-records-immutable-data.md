# ADR-027: Records for Immutable Data Structures

## Status

**ACCEPTED**

## Date

2026-02-20

## Context

YAWL's event and data transfer objects are currently defined as mutable classes with post-construction setters:

```java
// Current: Mutable YEvent hierarchy
public abstract class YEvent {
    private Instant _timeStamp;
    private YSpecificationID _specID;      // Set after construction
    private YWorkItem _item;               // Set after construction
    private Document _dataDoc;             // Set after construction
    private int _engineNbr;                // Set after construction

    public void setSpecID(YSpecificationID specID) { this._specID = specID; }
    public void setWorkItem(YWorkItem item) { this._item = item; }
    // ... more setters
}
```

**Problems:**

1. **Race Conditions**: Event can be modified while being dispatched to listeners
2. **Boilerplate**: Manual equals/hashCode/toString (100+ lines per class)
3. **Testing**: Builders or complex setup to create test objects
4. **Contracts**: No enforced immutability; readers cannot assume thread-safety
5. **Nullability**: Mutable fields can become null unexpectedly

Java 25 provides **Records** (JEP 440, finalized), which provide:
- Automatic immutability (all fields final)
- Auto-generated equals/hashCode/toString
- Compact constructors for validation
- Component accessors
- Pattern matching support

## Decision

**YAWL v6.0.0 adopts records for all immutable data structures: events, DTOs, API responses, and workflow artifacts.**

### 1. Event Records

**File**: `org.yawlfoundation.yawl.stateless.listener.event`

```java
// Sealed interface for all events (see ADR-026)
public sealed interface YWorkflowEvent
    permits YCaseLifecycleEvent, YWorkItemLifecycleEvent, /* ... */ {}

// Case-level events
public record YCaseLifecycleEvent(
    Instant timestamp,
    YEventType type,
    YIdentifier caseID,
    YSpecificationID specID,
    int engineNbr
) implements YWorkflowEvent {
    // Compact constructor for validation
    public YCaseLifecycleEvent {
        Objects.requireNonNull(timestamp, "timestamp required");
        Objects.requireNonNull(type, "type required");
        Objects.requireNonNull(caseID, "caseID required");
        Objects.requireNonNull(specID, "specID required");
        if (engineNbr < 0) throw new IllegalArgumentException("engineNbr must be >= 0");
    }
}

// Work item events
public record YWorkItemLifecycleEvent(
    Instant timestamp,
    YEventType type,
    YIdentifier caseID,
    YSpecificationID specID,
    YWorkItem workItem,
    @Nullable Document data,
    int engineNbr
) implements YWorkflowEvent {
    public YWorkItemLifecycleEvent {
        Objects.requireNonNull(timestamp, "timestamp required");
        Objects.requireNonNull(type, "type required");
        Objects.requireNonNull(caseID, "caseID required");
        Objects.requireNonNull(specID, "specID required");
        Objects.requireNonNull(workItem, "workItem required");
        if (engineNbr < 0) throw new IllegalArgumentException("engineNbr must be >= 0");
    }
}

// Timer events
public record YTimerEvent(
    Instant timestamp,
    YEventType type,
    YIdentifier caseID,
    YSpecificationID specID,
    String taskID,
    int engineNbr
) implements YWorkflowEvent {}

// Exception events
public record YExceptionEvent(
    Instant timestamp,
    YIdentifier caseID,
    YException exception,
    ExceptionLevel level
) implements YWorkflowEvent {}
```

**Benefits:**
- Immutability guaranteed by type system
- No race conditions during event dispatch
- Thread-safe; no synchronization needed
- Clear data contracts

---

### 2. Data Transfer Objects (DTOs)

**File**: `org.yawlfoundation.yawl.engine.interfce.interfaceB`

```java
// API request DTOs
public record LaunchCaseRequest(
    YSpecificationID specID,
    @Nullable String caseParams,
    @Nullable String logData
) {}

public record CompleteWorkItemRequest(
    String workItemID,
    Document outputData
) {
    public CompleteWorkItemRequest {
        Objects.requireNonNull(workItemID, "workItemID required");
        Objects.requireNonNull(outputData, "outputData required");
    }
}

// API response DTOs
public record LaunchCaseResponse(
    String caseID,
    long createdTimestamp,
    YSpecificationID specID
) {}

public record WorkItemResponse(
    String id,
    String taskID,
    YWorkItemStatus status,
    Instant enabledTime,
    @Nullable Instant startTime,
    Document data
) {}

// Query result DTOs
public record AvailableWorkItemsResponse(
    List<WorkItemResponse> items,
    int totalCount,
    long queryTimestamp
) {
    public AvailableWorkItemsResponse {
        Objects.requireNonNull(items, "items required");
        if (totalCount < 0) throw new IllegalArgumentException("totalCount >= 0");
    }
}
```

**Testing:**

```java
@Test
void testLaunchCase() {
    // Simple record construction, no builders
    LaunchCaseRequest request = new LaunchCaseRequest(
        new YSpecificationID("Process", "1.0"),
        """
        <data>
            <customer>John Doe</customer>
            <amount>1000</amount>
        </data>
        """,
        null
    );

    LaunchCaseResponse response = engine.launchCase(request);

    assertEquals("Process", response.specID().getProcessName());
    assertNotNull(response.caseID());
    assertTrue(response.createdTimestamp() > 0);
}
```

---

### 3. Workflow Artifacts

**File**: `org.yawlfoundation.yawl.specification`

```java
// Flow edges
public record YFlow(
    String id,
    YElement source,
    YElement target,
    @Nullable XPathExpression predicate
) {
    public YFlow {
        Objects.requireNonNull(source, "source required");
        Objects.requireNonNull(target, "target required");
    }
}

// Task parameters
public record TaskParameter(
    String name,
    YDataType type,
    boolean mandatory,
    @Nullable String documentation
) {}

// Decomposition references
public record DecompositionReference(
    String id,
    DecompositionType type,
    String specificationID,
    String specificationVersion
) {}

// Markup rules
public record MarkupRule(
    String groupID,
    List<String> rules,
    MarkupBehavior behavior
) {}
```

---

### 4. Persisted Events (Event Sourcing)

**File**: `org.yawlfoundation.yawl.observability.event`

```java
// Immutable event log entries
public record WorkflowEventLogEntry(
    long eventID,
    Instant timestamp,
    YIdentifier caseID,
    String eventType,
    Map<String, Object> payload,
    String sourceSystem,
    @Nullable String correlationID
) {
    public WorkflowEventLogEntry {
        Objects.requireNonNull(timestamp, "timestamp required");
        Objects.requireNonNull(caseID, "caseID required");
        Objects.requireNonNull(eventType, "eventType required");
        if (eventID <= 0) throw new IllegalArgumentException("eventID > 0");
    }
}

// With Hibernate @Embeddable support
@Embeddable
public record EventMetadata(
    String sourceAgent,
    String userId,
    @Nullable String sessionID
) {}
```

---

## Pattern: Record Validation

Use compact constructors for validation (no penalty for record construction):

```java
public record YWorkItem(
    String id,
    String taskID,
    Instant enabledTime,
    @Nullable Instant startTime,
    @Nullable Instant completionTime,
    Document data,
    WorkItemState state
) {
    // Compact constructor - validation before field assignment
    public YWorkItem {
        Objects.requireNonNull(id, "id required");
        Objects.requireNonNull(taskID, "taskID required");
        Objects.requireNonNull(enabledTime, "enabledTime required");
        Objects.requireNonNull(data, "data required");
        Objects.requireNonNull(state, "state required");

        // Temporal ordering validation
        if (startTime != null && startTime.isBefore(enabledTime)) {
            throw new IllegalArgumentException("startTime must be after enabledTime");
        }
        if (completionTime != null && completionTime.isBefore(enabledTime)) {
            throw new IllegalArgumentException("completionTime must be after enabledTime");
        }
        if (startTime != null && completionTime != null
            && completionTime.isBefore(startTime)) {
            throw new IllegalArgumentException("completionTime must be after startTime");
        }
    }

    // Custom accessor methods (if needed beyond component accessors)
    public long getAgeDays() {
        return ChronoUnit.DAYS.between(enabledTime, Instant.now());
    }

    public boolean isTerminal() {
        return state instanceof WorkItemState.TerminalState;
    }
}
```

---

## Pattern: Nullable Records with @Nullable

Use `@Nullable` annotation from `javax.annotation` for optional fields:

```java
public record CaseEvent(
    Instant timestamp,
    YIdentifier caseID,
    @Nullable String parentCaseID,        // Parent case (if subnet)
    @Nullable Document caseData,          // May not always be populated
    int engineNbr
) {}

// Usage
CaseEvent event = ...;
if (event.parentCaseID() != null) {
    String parent = event.parentCaseID();  // Understood to be non-null here
}
```

---

## Hibernate/JSON Serialization

### Hibernate Entity with Records

```java
@Entity
@Table(name = "WORKFLOW_EVENTS")
public class WorkflowEventEntity {
    @Id
    @GeneratedValue
    private long id;

    @Embedded
    private EventRecord event;

    @Embeddable
    public record EventRecord(
        String caseID,
        String eventType,
        LocalDateTime timestamp
    ) {}

    // Convert to immutable record
    public YWorkflowEvent toDomainEvent() {
        return switch (event.eventType()) {
            case "CASE_STARTED" -> new YCaseLifecycleEvent(
                event.timestamp().toInstant(ZoneOffset.UTC),
                YEventType.CASE_STARTED,
                new YIdentifier(event.caseID()),
                /* ... */
            );
            default -> throw new IllegalStateException("Unknown event type");
        };
    }
}
```

### Jackson JSON Serialization

```java
// Auto-detection works; records are fully serializable
ObjectMapper mapper = new ObjectMapper();

YCaseLifecycleEvent event = new YCaseLifecycleEvent(
    Instant.now(),
    YEventType.CASE_STARTED,
    caseID,
    specID,
    1
);

// Serialize
String json = mapper.writeValueAsString(event);

// Deserialize
YCaseLifecycleEvent restored = mapper.readValue(json, YCaseLifecycleEvent.class);
assertEquals(event, restored);  // Records have auto-generated equals!
```

---

## Migration Strategy

### Phase 1: Events (Priority: HIGH)

1. Convert `YEvent` abstract class to sealed interface
2. Create record implementations for each event type
3. Update `MultiThreadEventNotifier` to dispatch to records
4. Update all event listeners to use pattern matching
5. Run full test suite

**Effort**: 2-3 days | **Risk**: Low (events are observable)

### Phase 2: DTOs & API Responses (Priority: MEDIUM)

1. Identify all request/response classes in `interfaceB`
2. Convert to records
3. Update REST controllers
4. Update Swagger/OpenAPI schema
5. Test API integration

**Effort**: 1-2 days | **Risk**: Low

### Phase 3: Workflow Artifacts (Priority: MEDIUM)

1. Convert specification objects to records (`YFlow`, `YTask` properties)
2. Update loaders/serializers
3. Test YAWL specification parsing

**Effort**: 2-3 days | **Risk**: Medium (complex object graphs)

### Phase 4: Event Sourcing (Priority: LOW)

1. Add immutable event log records
2. Update persistence layer
3. Add JSON serialization

**Effort**: 1-2 days | **Risk**: Low

---

## Consequences

### Positive

1. **Thread Safety**: Immutability eliminates races
2. **Cleaner Tests**: No builders, factories, or complex setup
3. **Less Boilerplate**: Records eliminate 100+ lines per DTO
4. **Pattern Matching**: Sealed records enable powerful destructuring
5. **Performance**: JVM can optimize immutable objects better
6. **Explicit Contracts**: Type system enforces what can/cannot be null

### Negative

1. **Reflection Changes**: Some libraries may expect mutable beans
   - Mitigation: Jackson/Hibernate work well with records
2. **Learning Curve**: Team unfamiliar with records
   - Mitigation: Records are simpler than alternatives
3. **Validation Overhead**: Compact constructors run every time
   - Mitigation: Negligible cost; safety trade-off is worthwhile

### Risks

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| ORM compatibility | LOW | MEDIUM | Test Hibernate mappings early |
| JSON serialization | LOW | MEDIUM | Test Jackson integration |
| Third-party library issues | MEDIUM | LOW | Use adapter pattern if needed |

---

## Testing

### Unit Test Example

```java
@Test
void testRecordImmutability() {
    YCaseLifecycleEvent event1 = new YCaseLifecycleEvent(
        Instant.now(),
        YEventType.CASE_STARTED,
        caseID,
        specID,
        1
    );

    YCaseLifecycleEvent event2 = new YCaseLifecycleEvent(
        event1.timestamp(),
        event1.type(),
        event1.caseID(),
        event1.specID(),
        event1.engineNbr()
    );

    // Records auto-implement equals based on fields
    assertEquals(event1, event2);
    assertEquals(event1.hashCode(), event2.hashCode());
}

@Test
void testRecordValidation() {
    // Null check in compact constructor
    assertThrows(NullPointerException.class, () ->
        new YCaseLifecycleEvent(null, YEventType.CASE_STARTED, caseID, specID, 1)
    );
}

@Test
void testRecordPatternMatching() {
    YWorkflowEvent event = new YCaseLifecycleEvent(/* ... */);

    String desc = switch (event) {
        case YCaseLifecycleEvent e when e.type() == YEventType.CASE_STARTED ->
            "Case started: " + e.caseID();
        case YCaseLifecycleEvent e ->
            "Case event: " + e.type();
        case YWorkItemLifecycleEvent e ->
            "Work item event: " + e.workItem().getId();
        case _ -> "Unknown";
    };

    assertEquals("Case started: test-case", desc);
}
```

---

## Alternatives Considered

### Alternative 1: Keep Mutable Classes

**Rejected.** Loses thread-safety and testing benefits.

### Alternative 2: Use Lombok @Data

```java
@Data
public class YEvent {
    private Instant timestamp;
    private YSpecificationID specID;
}
```

**Rejected.** Records are native; avoid external annotation dependency.

### Alternative 3: Manual Immutable Classes

```java
public final class YCaseEvent {
    private final Instant timestamp;
    // Manual equals, hashCode, toString
}
```

**Rejected.** Too much boilerplate; records are better.

---

## Related ADRs

- **ADR-026**: Sealed Classes for Domain Hierarchies
- **ADR-030**: Scoped Values Context Management
- **ARCHITECTURE-PATTERNS-JAVA25.md**: Pattern 5 (Records for Events)

---

## References

- JEP 440: Records (finalized Java 16, Java 25)
- JEP 395: Records in Switch Patterns
- "Records come to Java" (Inside.java blog): https://inside.java/2020/03/09/records/
- "Sealed Classes: A Deep Dive": https://inside.java/2021/09/27/patterns-3-sealed-classes/

---

## Approval

**Approved by:** YAWL Architecture Team
**Date:** 2026-02-20
**Implementation Status:** PLANNED (v6.0.0)
**Review Date:** 2026-08-20

---

**Revision History:**
- 2026-02-20: Initial ADR
