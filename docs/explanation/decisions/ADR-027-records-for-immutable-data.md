# ADR-027: Records for Immutable Data — DTOs, Payloads, and Value Objects

## Status

**PROPOSED**

## Date

2026-02-20

## Context

YAWL's data transfer layer is built on a combination of mutable Java classes, XML
strings (`String`-returning Interface B/A methods), and `WorkItemRecord` (a
mutable bean). Three problems result:

### 1. Mutable DTOs crossed thread boundaries

`WorkItemRecord` in
`src/org/yawlfoundation/yawl/engine/interfce/WorkItemRecord.java`
is used across Interface B as the primary client-visible work item representation.
It is mutable — callers commonly call `setStatus()` or `setUpdatedData()` after
receiving it from the engine. When the same `WorkItemRecord` instance is referenced
from multiple agent threads (already virtual threads in `GenericPartyAgent`), these
mutations become data races.

### 2. Event objects assembled by post-construction mutation

`YEvent` (stateless) assembles its complete state over multiple setter calls after
construction. The call sequence `new YWorkItemEvent(type, item)` followed by
`event.setSpecID(...)` and `event.setEngineNbr(...)` is not atomic. Any concurrent
reader that receives the event before all setters complete sees a partially
constructed object. This is addressed structurally by ADR-026 (sealed hierarchy),
which this ADR supports by making the permitted record types the mechanism.

### 3. API response objects lack identity semantics

Interface B returns `Set<YWorkItem>` for queries like `getAvailableWorkItems()`.
`YWorkItem` is a persistent entity with complex internal state. Exposing the live
entity to client code allows accidental mutation and prevents caching. A
lightweight immutable snapshot — a record — would give clients a stable, cacheable
object with correct `equals` / `hashCode` without exposing engine internals.

### Current serialisation approach

Interface A and B return XML strings, not typed objects, over HTTP. The engine's
internal `YWorkItem` / `WorkItemRecord` types are serialised to XML via `toXML()`
methods. Records serialise identically to their field types; introducing records at
the internal representation level does not change the XML wire format, only how
the data is held in memory between construction and serialisation.

## Decision

Introduce Java records at three layers: internal value objects, API payloads sent
across Interface B, and event carrier types (as permitted types of the sealed
hierarchies in ADR-026). All three layers are backward-compatible with the existing
XML wire format.

### Layer 1: Work item snapshot record

Replace usage of `WorkItemRecord` as a cross-thread DTO with an immutable snapshot:

```java
// New file:
// src/org/yawlfoundation/yawl/engine/interfce/WorkItemSnapshot.java

package org.yawlfoundation.yawl.engine.interfce;

import java.time.Instant;

import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.YWorkItemStatus;
import org.yawlfoundation.yawl.engine.WorkItemState;

/**
 * Immutable snapshot of a work item at a point in time.
 *
 * <p>Replaces {@link WorkItemRecord} in cross-thread and cross-service contexts.
 * Safe to share between virtual threads with no synchronization. The XML
 * serialization contract ({@code toXML()}) is identical to
 * {@link WorkItemRecord#toXML()} for the same field values.</p>
 */
public record WorkItemSnapshot(
    String workItemID,
    String caseID,
    String taskID,
    String taskName,
    YSpecificationID specificationID,
    YWorkItemStatus status,
    WorkItemState state,
    String enabledBy,
    Instant enabledTime,
    Instant startTime,
    Instant completionTime,
    String workerID,
    String deferredChoiceGroupID,
    boolean allowsDynamicCreation,
    String customFormURL,
    String timerTrigger,
    String timerExpiry,
    String data,
    String logPredicate
) {

    /**
     * Creates a snapshot from a live {@link org.yawlfoundation.yawl.engine.YWorkItem}.
     * All values are copied at call time; subsequent mutations to the source item
     * are not reflected in the snapshot.
     *
     * @param item the source work item
     * @return an immutable snapshot
     */
    public static WorkItemSnapshot of(org.yawlfoundation.yawl.engine.YWorkItem item) {
        return new WorkItemSnapshot(
            item.getWorkItemID().toString(),
            item.getCaseID().toString(),
            item.getTaskID(),
            item.getTaskName(),
            item.getSpecificationID(),
            item.getStatus(),
            item.getState(),
            item.getEnabledBy(),
            item.getEnableTime(),
            item.getStartTime(),
            item.getCompletionTime(),
            item.getWorkerID(),
            item.getDeferredChoiceGroupID(),
            item.allowsDynamicCreation(),
            item.getCustomFormURL() != null ? item.getCustomFormURL().toString() : null,
            item.getTimerTrigger(),
            item.getTimerExpiry(),
            item.getDataString(),
            item.getLogPredicateString()
        );
    }
}
```

### Layer 2: Interface B query response records

The query side of Interface B (per the CQRS split noted in
`.claude/rules/engine/interfaces.md`) returns snapshots, not live entities:

```java
// New file:
// src/org/yawlfoundation/yawl/engine/interfce/interfaceB/WorkItemPage.java

package org.yawlfoundation.yawl.engine.interfce.interfaceB;

import java.time.Instant;
import java.util.List;

import org.yawlfoundation.yawl.engine.interfce.WorkItemSnapshot;

/**
 * Paginated result of a work item query.
 *
 * <p>Records are immutable and safe to cache. The {@code generatedAt} timestamp
 * lets callers detect stale cached pages.</p>
 */
public record WorkItemPage(
    List<WorkItemSnapshot> items,
    int totalCount,
    Instant generatedAt
) {
    /** Canonical empty page. */
    public static final WorkItemPage EMPTY =
        new WorkItemPage(List.of(), 0, Instant.EPOCH);

    /**
     * Returns a compact page — validates that {@code items} is unmodifiable.
     */
    public WorkItemPage {
        items = List.copyOf(items);
    }
}
```

```java
// New file:
// src/org/yawlfoundation/yawl/engine/interfce/interfaceB/CaseReference.java

package org.yawlfoundation.yawl.engine.interfce.interfaceB;

import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.engine.YSpecificationID;

import java.time.Instant;

/**
 * Immutable reference to a running case.
 *
 * <p>Returned by case launch operations and case query endpoints.
 * Clients should treat this as a stable identifier for subsequent
 * Interface B calls.</p>
 */
public record CaseReference(
    String caseID,
    YSpecificationID specificationID,
    Instant launchedAt,
    String launchedBy
) {}
```

```java
// New file:
// src/org/yawlfoundation/yawl/engine/interfce/interfaceB/TaskDefinition.java

package org.yawlfoundation.yawl.engine.interfce.interfaceB;

import org.yawlfoundation.yawl.engine.YSpecificationID;

/**
 * Immutable task definition metadata returned by Interface B queries.
 *
 * <p>Replaces direct exposure of {@link org.yawlfoundation.yawl.elements.YTask}
 * to Interface B clients, preventing inadvertent mutation of specification
 * elements loaded in the engine's specification cache.</p>
 */
public record TaskDefinition(
    String taskID,
    String taskName,
    String documentation,
    YSpecificationID specificationID,
    String netID,
    String decompositionID,
    int splitType,
    int joinType,
    boolean isMultiInstance,
    int miMinimum,
    int miMaximum,
    int miThreshold,
    boolean allowsDynamicCreation,
    String customFormURL,
    boolean hasTimer,
    String timerTrigger
) {}
```

### Layer 3: Event record types (permitted by ADR-026 sealed hierarchy)

The seven permitted types of `YWorkflowEvent` (defined in ADR-026) are all records.
This layer is governed jointly by ADR-026 and ADR-027. The key record-specific
considerations for event types are:

- **Compact object headers.** Records with `Instant`, `YIdentifier`, and
  `YSpecificationID` fields benefit directly from `-XX:+UseCompactObjectHeaders`.
  At 1000 events/sec across 100 concurrent cases, this is measurable.

- **Jackson / XML serialisation.** Records serialise cleanly with Jackson's
  `RecordNamingStrategyPatchModule` and with JAXB's `XmlAccessType.RECORD`.
  The `YEventType` enum field serialises as its `name()` string.

- **Logging.** Records have compiler-generated `toString()` implementations.
  Log statements like `_log.debug("event={}", event)` automatically include all
  fields without a custom `toString()` implementation.

### Layer 4: WorkflowContext value object for scoped values

The `WorkflowContext` type passed via `ScopedValue` (ADR-029) must be a record
to be safely shared across virtual threads without copy:

```java
// New file:
// src/org/yawlfoundation/yawl/engine/context/WorkflowContext.java

package org.yawlfoundation.yawl.engine.context;

import java.time.Instant;

import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.engine.YSpecificationID;

/**
 * Immutable workflow execution context propagated via {@link java.lang.ScopedValue}.
 *
 * <p>Carries the case identity, specification identity, and the wall-clock time
 * at which the current operation started. All fields are final and non-null.</p>
 */
public record WorkflowContext(
    YIdentifier caseID,
    YSpecificationID specificationID,
    String sessionHandle,
    Instant operationStartedAt,
    String operationName
) {
    /**
     * Creates a child context for a sub-net execution inheriting the same case
     * and specification but recording a new operation start time.
     *
     * @param subOperation the name of the sub-operation
     * @return a new {@code WorkflowContext} with the current time and new operation name
     */
    public WorkflowContext forSubOperation(String subOperation) {
        return new WorkflowContext(
            caseID,
            specificationID,
            sessionHandle,
            Instant.now(),
            subOperation
        );
    }
}
```

### Serialisation integration

Records integrate with existing XML serialisation via accessor methods that match
the record component names. The engine's `StringUtil.toXML()` utilities call
getters that Java generates for records by component name (e.g., `workItemID()`
instead of `getWorkItemID()`). A thin adapter shim bridges the naming difference
for the transition period:

```java
// In WorkItemSnapshot — adapter methods matching legacy getter naming
/** @return same as {@link #workItemID()} — provided for legacy XML bridge. */
public String getWorkItemID() { return workItemID(); }
public String getCaseID()     { return caseID(); }
public String getTaskID()     { return taskID(); }
// ... etc.
```

This allows the existing `toXML()` serialisation code to invoke records using the
same getter-style calls it uses for `WorkItemRecord` without modification.

## Consequences

### Positive

- **Zero-copy cross-thread sharing.** Records are immutable; agents, event handlers,
  and monitoring code running on separate virtual threads can hold references to the
  same `WorkItemSnapshot` with no risk of seeing inconsistent field values.

- **Correct `equals` and `hashCode` at no cost.** `WorkItemSnapshot` stored in
  a `Set` or as a `Map` key behaves correctly without hand-written
  `equals`/`hashCode`. The current `WorkItemRecord.equals()` is identity-based,
  which causes silent set-membership bugs when two records represent the same
  item from different engine calls.

- **Reduced boilerplate in tests.** Test data expressed as records:

  ```java
  // 12-field test fixture in one expression
  WorkItemSnapshot snap = new WorkItemSnapshot(
      "WI-001", "CASE-1", "review", "Review Invoice",
      specID, statusEnabled, enabledState,
      null, Instant.now(), null, null,
      null, null, false, null, null, null,
      null, null
  );
  ```

- **Caching.** `WorkItemSnapshot` records can be placed in Caffeine caches
  (ADR-007) keyed by `workItemID` with structural equality semantics. Cached
  snapshots do not accidentally share mutable engine state.

### Negative and Mitigations

- **`WorkItemRecord` retained for existing callers.** `WorkItemRecord` continues
  to be the type returned by `InterfaceB_EnvironmentBasedClient` until the next
  major API version. The `WorkItemSnapshot.of(YWorkItem)` factory bridges the two
  representations inside the engine. External Interface B client libraries compiled
  against v5.x continue to work.

- **Record accessor naming vs bean naming.** Records use `workItemID()` where beans
  use `getWorkItemID()`. The legacy getter adapter methods on `WorkItemSnapshot`
  (shown above) resolve this for the XML bridge. New code should call the record
  accessors directly and not use the adapter shim.

- **No Hibernate direct mapping.** Records cannot be JPA `@Entity` types because
  JPA requires a no-arg constructor and mutable state. `WorkItemSnapshot` is a
  projection type, not an entity. Hibernate projections return records correctly
  via interface-based projections in Spring Data. The live `YWorkItem` entity
  remains the persistence type.

- **Null fields in variant records.** `WorkItemSnapshot.deferredChoiceGroupID`
  is null for non-deferred-choice items. This is an inherent property of the
  domain, not a record limitation. Optional fields are documented in component
  Javadoc; callers must null-check before use.

## Alternatives Considered

### Alternative A: Immutable class (not record)

A hand-written immutable class with all-final fields, a private constructor, and a
static factory achieves the same thread-safety. Rejected: records provide
`equals`, `hashCode`, `toString`, and deconstruction patterns for free; hand-written
implementations introduce bugs and are tested by none of the existing test suite.

### Alternative B: Keep WorkItemRecord, add volatile fields

Adding `volatile` to `WorkItemRecord` fields prevents torn reads but does not
prevent logical inconsistency (a reader could see `status = executing` but
`workerID = null` between two setter calls). Rejected: volatile is not a
substitute for immutability.

### Alternative C: Jackson @JsonCreator on existing mutable classes

Annotate existing mutable classes to construct via all-args constructor to enforce
complete initialisation. Rejected: the class can still be mutated after
construction; this only helps serialisation, not concurrent sharing.

## Related ADRs

- ADR-004: Java 25 platform — records are finalized, no preview flag needed.
- ADR-007: Repository Pattern / Caching — `WorkItemSnapshot` is the cacheable
  projection type; `YWorkItem` remains the persistent entity.
- ADR-022: OpenAPI-First Design — the `WorkItemSnapshot`, `CaseReference`, and
  `TaskDefinition` record components map directly to OpenAPI 3.1 schema objects.
- ADR-026: Sealed Class Hierarchies — ADR-026 defines the sealed interfaces;
  this ADR defines the record types that implement them.
- ADR-029: Scoped Values (companion ADR) — `WorkflowContext` record is the
  payload of the `ScopedValue`.

## Files Affected

| File | Change |
|------|--------|
| `src/org/yawlfoundation/yawl/engine/interfce/WorkItemSnapshot.java` | **New** — record |
| `src/org/yawlfoundation/yawl/engine/interfce/interfaceB/WorkItemPage.java` | **New** — record |
| `src/org/yawlfoundation/yawl/engine/interfce/interfaceB/CaseReference.java` | **New** — record |
| `src/org/yawlfoundation/yawl/engine/interfce/interfaceB/TaskDefinition.java` | **New** — record |
| `src/org/yawlfoundation/yawl/engine/context/WorkflowContext.java` | **New** — record |
| `src/org/yawlfoundation/yawl/engine/YWorkItem.java` | Add `getState()` returning `WorkItemState`, add `toSnapshot()` |
| `src/org/yawlfoundation/yawl/engine/interfce/WorkItemRecord.java` | `@Deprecated(since="6.0", forRemoval=true)` on class |

## Estimated Effort

3 days total: 1 day for `WorkItemSnapshot` + bridge shim, 1 day for the three
Interface B query records, 1 day for `WorkflowContext` and wiring into the
`ScopedValue` binding sites.
