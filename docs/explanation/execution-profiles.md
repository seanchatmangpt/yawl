# Execution Profiles

> An execution profile is the combination of decomposition type, `externalInteraction` mode, service reference, and codelet that together determine how the engine routes a task's work item — to a human worklist, to a named YAWL service, or to an inline automated function.

## What an Execution Profile Is

In YAWL, every atomic task has a decomposition. The decomposition is not just data-wiring: it is the primary configuration that controls what happens when a work item fires. The decomposition type and its attributes constitute the execution profile for that task.

The relevant decomposition type for atomic tasks is `YAWLServiceGateway` (`src/org/yawlfoundation/yawl/elements/YAWLServiceGateway.java`), whose XSD form is `WebServiceGatewayFactsType`. Its parent class `YDecomposition` (`src/org/yawlfoundation/yawl/elements/YDecomposition.java`) contains the two core execution profile fields:

```java
// if true, this decomposition requires resourcing decisions made at runtime
protected boolean _manualInteraction = true;

protected String _codelet;       // specified codelet to execute for automated tasks
```

These fields, plus the `YAWLServiceReference` stored in `YAWLServiceGateway._yawlServices`, are the three dimensions of an execution profile.

## Manual vs Automated Execution

The `externalInteraction` XML element (defined in XSD as `ResourcingExternalInteractionType`) has two permitted values:

```xml
<xs:simpleType name="ResourcingExternalInteractionType">
    <xs:restriction base="xs:string">
        <xs:enumeration value="manual"/>
        <xs:enumeration value="automated"/>
    </xs:restriction>
</xs:simpleType>
```

This element appears inside `WebServiceGatewayFactsType` in `schema/YAWL_Schema4.0.xsd` (line 760-761). It maps directly to `YDecomposition._manualInteraction` via `setExternalInteraction(boolean)`.

**Manual** tasks (`externalInteraction = manual`, `_manualInteraction = true`) require a human actor to perform them. At runtime, the engine creates a `YWorkItem` whose `requiresManualResourcing()` returns `true`. The engine's announcer broadcasts this work item to the Resource Service (the default worklist handler), which allocates it to a human participant according to the resourcing specification embedded in the task's `<resourcing>` element.

**Automated** tasks (`externalInteraction = automated`, `_manualInteraction = false`) are handled programmatically. No human worklist allocation occurs. Instead, one of two routing paths is followed:

1. A **codelet** is specified (`_codelet` field is non-null): the engine executes the named codelet class inline within the engine process. Codelets are implementations of a codelet interface registered with the engine.
2. A **named YAWL service** is referenced (`YAWLServiceGateway.getYawlService()` returns non-null): the work item is routed to that service's Interface B endpoint.

## The `externalInteraction` Field and Its Lifecycle

When the engine loads a specification, `YDecompositionParser` (`src/org/yawlfoundation/yawl/unmarshal/YDecompositionParser.java`, line 618) reads the `externalInteraction` element:

```java
String interactionStr = decompElem.getChildText("externalInteraction", _yawlNS);
```

The string value `"manual"` or `"automated"` is converted to a boolean and set on the decomposition via `setExternalInteraction`. The boolean field is then copied onto every `YWorkItem` created from that decomposition:

```java
// in YNetRunner.createEnabledWorkItem (src/org/yawlfoundation/yawl/engine/YNetRunner.java)
YDecomposition decomp = atomicTask.getDecompositionPrototype();
if (decomp != null) {
    workItem.setRequiresManualResourcing(decomp.requiresResourcingDecisions());
    workItem.setCodelet(decomp.getCodelet());
    workItem.setAttributes(decomp.getAttributes());
}
```

The `YWorkItem` carries `_requiresManualResourcing` and `_codelet` fields (`src/org/yawlfoundation/yawl/engine/YWorkItem.java`). These travel with the item through its entire lifecycle and are serialised in the item's XML representation, so services that receive the item know immediately what kind of handling is expected.

## Role of `yawlService` in Routing

The `yawlService` child element of `WebServiceGatewayFactsType` identifies which registered YAWL custom service receives the work item. At spec load time this becomes a `YAWLServiceReference` (`src/org/yawlfoundation/yawl/elements/YAWLServiceReference.java`), which wraps a URI string (the service's Interface B endpoint). This URI is matched against services registered with `YEngine`.

When `YAnnouncer.createAnnouncement` is called for a work item:

```java
// src/org/yawlfoundation/yawl/engine/YAnnouncer.java
protected YAnnouncement createAnnouncement(YWorkItem item, YEngineEvent event) {
    YTask task = item.getTask();
    if ((task != null) && (task.getDecompositionPrototype() != null)) {
        YAWLServiceGateway wsgw = (YAWLServiceGateway) task.getDecompositionPrototype();
        if (wsgw != null) {
            return createAnnouncement(wsgw.getYawlService(), item, event);
        }
    }
    return null;
}
```

If `getYawlService()` returns a non-null reference, the announcement targets that specific service. If null, the work item falls back to the engine's default worklist (the Resource Service).

## How the Resource Service Fits In

The Resource Service is the default worklist handler. It is registered as the engine's `defaultWorklist` via Interface A at startup. When:

- `requiresManualResourcing()` returns `true`, AND
- no named custom service is referenced in the task decomposition

then the engine's announcer routes the work item to the Resource Service for human allocation. The Resource Service then manages participant selection, work item presentation in user inboxes, and eventually calls Interface B to complete the item once the human finishes.

When a custom service is named (a custom service URI in `yawlService`), the engine sends the item directly to that service, bypassing the Resource Service's allocation logic entirely. The custom service is responsible for completing the item via Interface B. This is how automated services like the Worklet Service and the Web Service Invoker operate.

## XSD Reference: Elements That Define an Execution Profile

The full execution profile is encoded in two places in a YAWL specification XML:

**In the decomposition element (`WebServiceGatewayFactsType`):**

```xml
<decomposition xsi:type="WebServiceGatewayFactsType" id="MyTaskGateway">
  <!-- input/output parameters -->
  <yawlService id="http://localhost:8080/myService/ib"/>
  <codelet>org.yawlfoundation.yawl.engine.codelet.MyCodelet</codelet>
  <externalInteraction>automated</externalInteraction>
</decomposition>
```

**In the task element (`ExternalTaskFactsType`), which references the decomposition:**

```xml
<task id="myTask">
  <join code="xor"/>
  <split code="and"/>
  <decomposesTo id="MyTaskGateway"/>
  <resourcing>...</resourcing>
</task>
```

The `<resourcing>` element only matters when `externalInteraction` is `manual`. For automated tasks, the resourcing block is ignored at runtime.

## What a Coding Agent Must Configure for a New Task Type

When implementing a new automated task type (for example, a new custom service integration):

1. **Create a `WebServiceGatewayFactsType` decomposition.** Set `<externalInteraction>automated</externalInteraction>`. This ensures `YDecomposition._manualInteraction = false`, so the Resource Service does not attempt to allocate the work item to a human participant.

2. **Set the `yawlService` reference.** The `id` attribute must match the URI of a service registered with the engine. At runtime, the engine verifies the URI against its registered service map via `YEngine.getRegisteredYawlService(uri)` (called from `YAWLServiceReference.verify`). If the service is not registered at spec-load time, a verification warning is issued.

3. **Do not specify both `codelet` and `yawlService`.** They are mutually exclusive execution paths. If both are set, the engine's behaviour depends on which is checked first in the announcer; in practice, the `yawlService` route takes precedence because `createAnnouncement` checks the gateway's service reference.

4. **For inline automated execution, use a codelet.** The `<codelet>` element takes a fully-qualified class name. The codelet class must implement the codelet interface and be on the engine's classpath. No network call is made; execution is synchronous in the engine thread.

5. **For manual tasks, configure `<resourcing>`.** The `<resourcing>` block inside the task element (XSD type `ResourcingFactsType`) specifies the `<offer>`, `<allocate>`, and `<start>` initiator phases. Without a valid resourcing block, the Resource Service cannot determine how to allocate the item, and the work item will remain in an `Enabled` state indefinitely.

## Java Field Reference

| XSD element | Java field | Class |
|---|---|---|
| `externalInteraction` | `_manualInteraction` | `YDecomposition` |
| `codelet` | `_codelet` | `YDecomposition` |
| `yawlService/@id` | `_yawlServiceID` | `YAWLServiceReference` |
| `requiresManualResourcing` (runtime) | `_requiresManualResourcing` | `YWorkItem` |
| `codelet` (runtime) | `_codelet` | `YWorkItem` |
