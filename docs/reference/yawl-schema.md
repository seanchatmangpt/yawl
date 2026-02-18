# YAWL XML Specification Format Reference

**Audience**: AI coding agents writing or modifying `.yawl` workflow specification files.
**Schema**: `schema/YAWL_Schema4.0.xsd` — validate with `xmllint --schema schema/YAWL_Schema4.0.xsd spec.yawl`
**Namespace**: `http://www.yawlfoundation.org/yawlschema`

This document is a lookup reference. Read it when you need the name, attributes, child elements, or constraints for any element in a `.yawl` file.

---

## Top-Level File Structure

A `.yawl` file is an XML document whose root element is `<specificationSet>`. The file contains one or more `<specification>` elements (each a distinct workflow) plus an optional `<layout>` element for visual positioning data (ignored by the engine).

```xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet
    xmlns="http://www.yawlfoundation.org/yawlschema"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    version="4.0"
    xsi:schemaLocation="http://www.yawlfoundation.org/yawlschema
                        http://www.yawlfoundation.org/yawlschema/YAWL_Schema4.0.xsd">
  <specification uri="MyWorkflow">
    ...
  </specification>
  <layout> ... </layout>   <!-- optional; engine strips this on load -->
</specificationSet>
```

---

## Element Reference

### `<specificationSet>`

Root element. Container for all specifications in the file.

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `version` | string, fixed `"4.0"` | yes | Schema version. Must be exactly `"4.0"`. |
| `xmlns` | URI | yes | Must be `http://www.yawlfoundation.org/yawlschema` |
| `xmlns:xsi` | URI | yes | Must be `http://www.w3.org/2001/XMLSchema-instance` |
| `xsi:schemaLocation` | string | recommended | Points validator to the XSD file. |

**Children**: one or more `<specification>`, zero or one `<layout>`.

Each `uri` attribute across `<specification>` children must be unique within the set.

---

### `<specification>`

One workflow process definition. Maps to `YSpecification` in the engine.

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `uri` | anyURI | yes | Unique identifier for the specification. Used when launching cases and referencing the spec by ID. Must be unique within the `<specificationSet>`. |

**Child elements** (in order):

| Element | Required | Description |
|---------|----------|-------------|
| `<name>` | no | Human-readable name (non-empty string). |
| `<documentation>` | no | Free-text description of the specification. |
| `<metaData>` | yes | Dublin Core metadata block (see below). |
| `<xs:schema>` | no | Embedded XML Schema defining custom data types used by variables. Namespace `http://www.w3.org/2001/XMLSchema`. |
| `<decomposition>` | one or more | Net or task decomposition definitions. At least one must be a root net. |
| `<importedNet>` | zero or more | URI references to external net definitions. |

**Example**:
```xml
<specification uri="OrderProcessing">
  <name>Order Processing</name>
  <documentation>Handles order fulfilment through payment.</documentation>
  <metaData>
    <title>Order Processing</title>
    <creator>jsmith</creator>
    <version>1.3</version>
    <persistent>false</persistent>
    <identifier>UID_a1b2c3d4-e5f6-7890-abcd-ef1234567890</identifier>
  </metaData>
  <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" />
  <decomposition id="OrderProcessing" isRootNet="true" xsi:type="NetFactsType">
    ...
  </decomposition>
  <decomposition id="FillOrder" xsi:type="WebServiceGatewayFactsType">
    ...
  </decomposition>
</specification>
```

---

### `<metaData>`

Dublin Core metadata. All child elements are optional.

| Element | Type | Description |
|---------|------|-------------|
| `<title>` | normalizedString | Display title. |
| `<creator>` | string (repeatable) | Author(s). |
| `<subject>` | string (repeatable) | Subject keywords. |
| `<description>` | normalizedString | Long description. |
| `<contributor>` | string (repeatable) | Additional contributors. |
| `<coverage>` | string | Version of YAWL editor used. |
| `<validFrom>` | date (xs:date) | Effective start date. |
| `<validUntil>` | date (xs:date) | Effective end date. |
| `<created>` | date (xs:date) | Creation date. |
| `<version>` | decimal (min 0.1) | Specification version number. |
| `<status>` | string | Status descriptor (e.g., "Active", "Draft"). |
| `<persistent>` | boolean | Whether case state is persisted across engine restarts. `true` = persist. |
| `<identifier>` | xs:NCName | Unique identifier (typically a UUID prefixed with `UID_`). |

---

### `<decomposition>`

A `<decomposition>` is the fundamental unit of net or task behaviour. Its concrete type is determined by the `xsi:type` attribute.

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | NCName | yes | Unique identifier within the specification. Matches the decomposition referenced by `<decomposesTo id="..."/>` in tasks. |
| `isRootNet` | boolean | conditional | When `true`, this decomposition is the top-level net where execution begins. Exactly one decomposition per specification must have `isRootNet="true"`. |
| `xsi:type` | string | yes | Determines the decomposition type. Values: `NetFactsType` (a net/sub-net) or `WebServiceGatewayFactsType` (a task implementation). |

**Decomposition subtypes**:

#### `xsi:type="NetFactsType"` — a workflow net

Used for the root net and any sub-nets. Inherits `<name>`, `<documentation>`, `<inputParam>`, `<outputParam>`, `<logPredicate>` from the base type, then adds:

| Element | Required | Description |
|---------|----------|-------------|
| `<localVariable>` | no (repeatable) | Variables scoped to this net, not visible to callers. |
| `<processControlElements>` | yes | Contains the net's places and transitions. |
| `<externalDataGateway>` | no | Name of a registered external data handler class. |

#### `xsi:type="WebServiceGatewayFactsType"` — a task implementation

Represents the work a task performs (manual or automated). Inherits `<name>`, `<documentation>`, `<inputParam>`, `<outputParam>` from the base type, then adds:

| Element | Required | Description |
|---------|----------|-------------|
| `<enablementParam>` | no (repeatable) | Input parameter visible only at task enablement, not during execution. |
| `<yawlService>` | no | Web service binding (id attribute required; optional WSDL location and operation name). |
| `<codelet>` | no | xs:NCName identifying a registered Java codelet class to execute automatically. |
| `<externalInteraction>` | no | `"manual"` (human performs the work) or `"automated"` (service/codelet). Default: `"manual"`. |

---

### `<processControlElements>`

Container for all net elements (places and transitions) within a `NetFactsType` decomposition.

**Required order**:
1. `<inputCondition>` — exactly one; the start place.
2. Zero or more `<task>` and `<condition>` elements in any order.
3. `<outputCondition>` — exactly one; the end place.

```xml
<processControlElements>
  <inputCondition id="InputCondition">
    <flowsInto><nextElementRef id="TaskA"/></flowsInto>
  </inputCondition>
  <task id="TaskA"> ... </task>
  <condition id="Cond1"> ... </condition>
  <outputCondition id="OutputCondition"/>
</processControlElements>
```

---

### `<inputCondition>`

The mandatory start place (input condition) of a net. Type: `ExternalConditionFactsType`.

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | NMTOKEN | yes | Element identifier. Convention: `"InputCondition"`. |

**Children**:
- `<name>` — optional label.
- `<documentation>` — optional description.
- `<flowsInto>` — one or more outgoing arc definitions (required; the input condition must connect to at least one task).

---

### `<outputCondition>`

The mandatory end place (output condition) of a net. Type: `OutputConditionFactsType`. Has no outgoing arcs.

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | NMTOKEN | yes | Element identifier. Convention: `"OutputCondition"`. |

**Children**: `<name>` (optional), `<documentation>` (optional). No `<flowsInto>`.

```xml
<outputCondition id="OutputCondition"/>
```

---

### `<condition>`

An intermediate place (condition) in the net. Equivalent to a Petri net place. Type: `ExternalConditionFactsType`.

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | NMTOKEN | yes | Unique identifier within the net. |

**Children**:
- `<name>` — optional label.
- `<documentation>` — optional description.
- `<flowsInto>` — one or more outgoing arc definitions.

```xml
<condition id="ApprovalDecision">
  <name>Awaiting Approval</name>
  <flowsInto>
    <nextElementRef id="ProcessApproval"/>
  </flowsInto>
</condition>
```

---

### `<task>`

A transition in the net. Represents a unit of work. Two variants exist in the schema: standard tasks (`ExternalTaskFactsType`) and multiple-instance tasks (`MultipleInstanceExternalTaskFactsType`).

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | NMTOKEN | yes | Unique identifier within the net. |

**Children** (standard task, in order):

| Element | Required | Description |
|---------|----------|-------------|
| `<name>` | no | Human-readable label. |
| `<documentation>` | no | Task description. |
| `<flowsInto>` | one or more | Outgoing arc(s) to next elements. |
| `<join>` | yes | Incoming arc join semantics. |
| `<split>` | yes | Outgoing arc split semantics. |
| `<defaultConfiguration>` | no | Default configuration for configurable workflows. |
| `<configuration>` | no | Active configuration for configurable workflows. |
| `<removesTokens>` | no (repeatable) | Cancellation set: IDs of elements to cancel when this task completes. |
| `<removesTokensFromFlow>` | no (repeatable) | Cancellation set targeting specific arcs. |
| `<startingMappings>` | no | XQuery mappings from net variables into task input parameters. |
| `<completedMappings>` | no | XQuery mappings from task output parameters back to net variables. |
| `<enablementMappings>` | no | XQuery mappings available at task enablement time. |
| `<timer>` | no | Timer configuration triggering task expiry. |
| `<resourcing>` | no | Resource allocation specification. |
| `<customForm>` | no | URI to a custom web form for this task. |
| `<decomposesTo>` | no | Links task to its implementation decomposition by `id`. |

**Example**:
```xml
<task id="FillOrder">
  <name>Fill Order</name>
  <flowsInto>
    <nextElementRef id="ReceivePayment"/>
  </flowsInto>
  <join code="xor"/>
  <split code="and"/>
  <startingMappings>
    <mapping>
      <expression query="&lt;Item&gt;{/Sales/Item/text()}&lt;/Item&gt;"/>
      <mapsTo>Item</mapsTo>
    </mapping>
  </startingMappings>
  <completedMappings>
    <mapping>
      <expression query="&lt;Item&gt;{/FillOrder/Item/text()}&lt;/Item&gt;"/>
      <mapsTo>Item</mapsTo>
    </mapping>
  </completedMappings>
  <resourcing>
    <offer initiator="user"/>
    <allocate initiator="user"/>
    <start initiator="user"/>
  </resourcing>
  <decomposesTo id="FillOrder"/>
</task>
```

---

### `<join>` and `<split>`

Control-flow semantics for a task's incoming and outgoing arcs.

| Attribute | Type | Required | Values |
|-----------|------|----------|--------|
| `code` | ControlTypeCodeType | yes | `"xor"`, `"and"`, `"or"` |

**Join semantics**:
- `xor` — fires when any single incoming arc has a token (exclusive). The default for most tasks.
- `and` — fires only when all incoming arcs have tokens (synchronisation barrier).
- `or` — fires when one or more incoming arcs have tokens, with OR-join semantics (requires all active paths to arrive).

**Split semantics**:
- `xor` — routes to exactly one outgoing arc based on predicates; the arc with the highest-priority satisfied predicate wins.
- `and` — places tokens on all outgoing arcs simultaneously (parallel split).
- `or` — places tokens on one or more outgoing arcs where predicates evaluate to true.

```xml
<join code="xor"/>
<split code="and"/>
```

---

### `<flowsInto>`

Defines an outgoing arc from a net element to another net element.

**Children**:

| Element | Required | Description |
|---------|----------|-------------|
| `<nextElementRef id="..."/>` | yes | NMTOKEN id of the target element. Must resolve to an element in the same net. |
| `<predicate ordering="N">expr</predicate>` | no | XPath predicate evaluated to determine if this arc is taken (for XOR/OR splits). `ordering` integer determines evaluation priority (lower = higher priority). |
| `<isDefaultFlow/>` | no | Marks this arc as the default flow taken when no predicate matches. |
| `<documentation>` | no | Arc description. |

```xml
<flowsInto>
  <nextElementRef id="PaymentTask"/>
  <predicate ordering="1">/Sales/Paid/text() = 'false'</predicate>
</flowsInto>
<flowsInto>
  <nextElementRef id="OutputCondition"/>
  <isDefaultFlow/>
</flowsInto>
```

---

### `<startingMappings>` and `<completedMappings>`

Data mappings transfer data between net-level variables and task parameters.

- `<startingMappings>` — executed when the task starts; copies net variable values into the task's input parameters.
- `<completedMappings>` — executed when the task completes; copies task output parameter values back into net variables.
- `<enablementMappings>` — executed at task enablement; used for enablement parameters only.

Each contains one or more `<mapping>` elements:

| Element | Required | Description |
|---------|----------|-------------|
| `<expression query="..."/>` | yes | XQuery expression producing the XML fragment to pass. The `query` attribute contains the full XQuery. |
| `<mapsTo>varName</mapsTo>` | yes | Name of the target variable or parameter (NMTOKEN). |

**XQuery pattern**: The standard pattern wraps a value extracted from the net's data document:
```xml
<expression query="&lt;ParamName&gt;{/NetName/VarName/text()}&lt;/ParamName&gt;"/>
```
Where `&lt;` and `&gt;` are XML-escaped `<` and `>`. At runtime this produces `<ParamName>value</ParamName>`.

For `completedMappings`, the data root is the task ID, not the net ID:
```xml
<expression query="&lt;NetVar&gt;{/TaskID/TaskParam/text()}&lt;/NetVar&gt;"/>
```

---

### `<multiInstance>` (Multiple-Instance Tasks)

When a task element uses `MultipleInstanceExternalTaskFactsType`, these additional elements are required:

| Element | Required | Type | Description |
|---------|----------|------|-------------|
| `<minimum>` | yes | XQuery | Minimum number of instances to create (evaluated at runtime). |
| `<maximum>` | yes | XQuery | Maximum number of instances allowed. |
| `<threshold>` | yes | XQuery | Minimum number of instances that must complete before the task can proceed. |
| `<creationMode code="..."/>` | yes | `"static"` or `"dynamic"` | `static` = all instances created at start; `dynamic` = instances can be added while task is executing. |
| `<miDataInput>` | yes | complex | How to split input data across instances. |
| `<miDataOutput>` | no | complex | How to aggregate output data from all instances. |

**`<miDataInput>` children**:
- `<expression query="..."/>` — XQuery selecting the collection to split.
- `<splittingExpression query="..."/>` — XQuery that splits the collection into per-instance data.
- `<formalInputParam>paramName</formalInputParam>` — name of the parameter receiving each instance's data.

**`<miDataOutput>` children**:
- `<formalOutputExpression query="..."/>` — XQuery extracting the output from each instance.
- `<outputJoiningExpression query="..."/>` — XQuery joining all instance outputs into a single result.
- `<resultAppliedToLocalVariable>varName</resultAppliedToLocalVariable>` — net variable to receive the joined result.

---

### `<decomposesTo>`

Links a task to its implementation. Must reference an existing `<decomposition id="...">` within the same `<specification>`.

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | DecompositionIDType (NCName) | yes | Must match the `id` attribute of a `<decomposition>` in the same specification. |

```xml
<decomposesTo id="FillOrder"/>
```

If omitted, the task has no implementation (it is an atomic task that must be handled by the engine's default behaviour or a registered service).

---

### `<timer>`

Configures a timer on a task. When the timer fires, the task is cancelled and the workflow takes the appropriate exception path.

**Trigger values**:
- `"OnEnabled"` — timer starts when the task becomes enabled.
- `"OnExecuting"` — timer starts when the task begins execution.

**Duration specification** (choose one):
- `<expiry>milliseconds</expiry>` — absolute Unix epoch time in milliseconds.
- `<duration>PTnHnMnS</duration>` — ISO 8601 duration (xs:duration).
- `<durationparams>` with `<ticks>N</ticks>` and `<interval>UNIT</interval>` where UNIT is `YEAR`, `MONTH`, `WEEK`, `DAY`, `HOUR`, `MIN`, `SEC`, or `MSEC`.

Alternatively, `<netparam>variableName</netparam>` reads the timer configuration from a net variable at runtime.

Optional `<workdays>true</workdays>` restricts timer counting to working days only.

```xml
<!-- 5 second timer starting on execution -->
<timer>
  <trigger>OnExecuting</trigger>
  <duration>PT5S</duration>
</timer>

<!-- Timer from net variable -->
<timer>
  <netparam>timeoutDuration</netparam>
</timer>

<!-- 30 minute timer using duration params -->
<timer>
  <trigger>OnEnabled</trigger>
  <durationparams>
    <ticks>30</ticks>
    <interval>MIN</interval>
  </durationparams>
</timer>
```

---

### `<resourcing>`

Specifies how human resources are allocated to a task.

**Top-level children** (all required except `<secondary>` and `<privileges>`):

| Element | Required | Description |
|---------|----------|-------------|
| `<offer initiator="..."/>` | yes | Who offers the task. `system` = engine allocates directly; `user` = resource service distributes. |
| `<allocate initiator="..."/>` | yes | Who allocates. `system` or `user`. |
| `<start initiator="..."/>` | yes | Who starts the task. `system` or `user`. |
| `<secondary>` | no | Secondary (non-primary) resources. |
| `<privileges>` | no | Per-task privilege grants. |

**`<offer>` with `distributionSet`** (when `initiator="system"`):
```xml
<offer initiator="system">
  <distributionSet>
    <initialSet>
      <role>ClaimsProcessor</role>
      <participant>jsmith</participant>
    </initialSet>
    <filters>
      <filter>
        <name>OrgFilter</name>
      </filter>
    </filters>
  </distributionSet>
</offer>
```

**Simple human task** (most common):
```xml
<resourcing>
  <offer initiator="user"/>
  <allocate initiator="user"/>
  <start initiator="user"/>
</resourcing>
```

**`<privileges>`** — up to 7 entries; each names a privilege and specifies `<allowall>true</allowall>` or a `<set>` of participants/roles:

| Privilege value | Meaning |
|----------------|---------|
| `canSuspend` | Participant can suspend the work item. |
| `canReallocateStateless` | Participant can reallocate before task starts. |
| `canReallocateStateful` | Participant can reallocate after task starts. |
| `canDeallocate` | Participant can return the item to the offer queue. |
| `canDelegate` | Participant can delegate to another user. |
| `canSkip` | Participant can skip the task. |
| `canPile` | Participant can pile (stack) the task. |

---

### Variables: `<inputParam>`, `<outputParam>`, `<localVariable>`

All three share a common base (`VariableBaseType`). Variables define the data flowing through a workflow.

**Scope**:
- `<inputParam>` in a `<decomposition>` — data flowing into the decomposition from its caller.
- `<outputParam>` in a `<decomposition>` — data flowing out of the decomposition back to its caller.
- `<localVariable>` in a `NetFactsType` decomposition — net-scoped variable not visible to callers; persists for the case lifetime.

**Common elements** (in order):

| Element | Required | Description |
|---------|----------|-------------|
| `<index>` | yes | Integer ordering for display purposes. Starts at 0. |
| `<documentation>` | no | Variable description. |
| `<name>` | yes (or `<element>`) | Variable name (xs:NCName). Must be unique within the decomposition. |
| `<type>` | yes (or `<isUntyped>`) | XML Schema primitive type name (see table below). |
| `<namespace>` | no | Namespace URI for the type. For XSD primitives: `http://www.w3.org/2001/XMLSchema`. |
| `<isUntyped/>` | alternative to `<type>` | Declares the variable without a schema type. |
| `<element>` | alternative to name+type | References a named element from the embedded schema. |

**`<localVariable>` and `<inputParam>` only**:
- `<initialValue>` — string value used when the variable is first initialised.

**`<outputParam>` only**:
- `<defaultValue>` — value used if the task produces no output.
- `<mandatory/>` — presence indicates the output is required.
- `<isCutThroughParam/>` — presence indicates the variable passes through unchanged.

**`<logPredicate>`** (on `<inputParam>` and `<outputParam>`): optional logging specification with `<start>` and `<completion>` child elements.

**Supported XSD types** (value of `<type>` with namespace `http://www.w3.org/2001/XMLSchema`):

| Type | Example value |
|------|---------------|
| `string` | `Hello` |
| `boolean` | `true` or `false` |
| `integer` | `42` |
| `long` | `9876543210` |
| `double` | `3.14159` |
| `float` | `1.5` |
| `decimal` | `99.95` |
| `date` | `2026-02-18` |
| `time` | `14:30:00` |
| `dateTime` | `2026-02-18T14:30:00` |
| `duration` | `P1DT4H` |
| `anyURI` | `http://example.org/data` |

Custom types defined in `<xs:schema>` within the specification may also be referenced.

```xml
<localVariable>
  <index>0</index>
  <name>OrderTotal</name>
  <type>double</type>
  <namespace>http://www.w3.org/2001/XMLSchema</namespace>
  <initialValue>0.0</initialValue>
</localVariable>

<inputParam>
  <index>0</index>
  <name>CustomerID</name>
  <type>string</type>
  <namespace>http://www.w3.org/2001/XMLSchema</namespace>
</inputParam>

<outputParam>
  <index>0</index>
  <name>ApprovalStatus</name>
  <type>boolean</type>
  <namespace>http://www.w3.org/2001/XMLSchema</namespace>
  <defaultValue>false</defaultValue>
  <mandatory/>
</outputParam>
```

---

### `<logPredicate>`

Optional logging configuration on `<decomposition>`, `<inputParam>`, and `<outputParam>`. Specifies text logged at task start and/or completion.

| Element | Required | Description |
|---------|----------|-------------|
| `<start>` | no | Text logged when the task/parameter starts. |
| `<completion>` | no | Text logged when the task/parameter completes. |

---

## Complete Minimal Example

A specification with two tasks, one sub-net, and data flow:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet
    xmlns="http://www.yawlfoundation.org/yawlschema"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    version="4.0"
    xsi:schemaLocation="http://www.yawlfoundation.org/yawlschema
                        http://www.yawlfoundation.org/yawlschema/YAWL_Schema4.0.xsd">
  <specification uri="OrderProcess">
    <name>Order Process</name>
    <metaData>
      <creator>jsmith</creator>
      <version>1.0</version>
      <persistent>false</persistent>
      <identifier>UID_00000000-0000-0000-0000-000000000001</identifier>
    </metaData>
    <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"/>
    <!-- Root net -->
    <decomposition id="OrderProcess" isRootNet="true" xsi:type="NetFactsType">
      <localVariable>
        <index>0</index>
        <name>OrderID</name>
        <type>string</type>
        <namespace>http://www.w3.org/2001/XMLSchema</namespace>
        <initialValue>ORD-001</initialValue>
      </localVariable>
      <processControlElements>
        <inputCondition id="InputCondition">
          <flowsInto><nextElementRef id="FillOrder"/></flowsInto>
        </inputCondition>
        <task id="FillOrder">
          <name>Fill Order</name>
          <flowsInto><nextElementRef id="OutputCondition"/></flowsInto>
          <join code="xor"/>
          <split code="and"/>
          <startingMappings>
            <mapping>
              <expression query="&lt;OrderID&gt;{/OrderProcess/OrderID/text()}&lt;/OrderID&gt;"/>
              <mapsTo>OrderID</mapsTo>
            </mapping>
          </startingMappings>
          <resourcing>
            <offer initiator="user"/>
            <allocate initiator="user"/>
            <start initiator="user"/>
          </resourcing>
          <decomposesTo id="FillOrder"/>
        </task>
        <outputCondition id="OutputCondition"/>
      </processControlElements>
    </decomposition>
    <!-- Task implementation -->
    <decomposition id="FillOrder" xsi:type="WebServiceGatewayFactsType">
      <inputParam>
        <index>0</index>
        <name>OrderID</name>
        <type>string</type>
        <namespace>http://www.w3.org/2001/XMLSchema</namespace>
      </inputParam>
      <externalInteraction>manual</externalInteraction>
    </decomposition>
  </specification>
</specificationSet>
```

---

## Schema Constraints Summary

| Constraint | Rule |
|-----------|------|
| Unique specification URIs | Each `<specification uri="...">` within a `<specificationSet>` must be unique. |
| Exactly one root net | Exactly one `<decomposition>` in a specification must have `isRootNet="true"`. |
| Valid `<decomposesTo>` references | Every `<decomposesTo id="...">` must reference an existing `<decomposition id="...">` in the same specification. |
| Unique variable names | `<inputParam>`, `<localVariable>` names must be unique per decomposition; `<outputParam>` names must be unique per decomposition. |
| Valid flow references | Every `<nextElementRef id="...">` must reference an existing net element within the same `<processControlElements>`. |
| Required net boundaries | Every `NetFactsType` decomposition must have exactly one `<inputCondition>` and one `<outputCondition>`. |
| Control type codes | `<join code>` and `<split code>` must be one of `and`, `or`, `xor`. |
| Timer trigger | `<trigger>` must be `OnEnabled` or `OnExecuting`. |
| Creation mode | `<creationMode code>` must be `static` or `dynamic`. |
| External interaction | `<externalInteraction>` must be `manual` or `automated`. |
| Privilege names | `<name>` inside `<privilege>` must be one of the seven defined `ResourcingPrivilegeType` values. |

---

## Validation

Validate a `.yawl` file against the schema:
```bash
xmllint --schema schema/YAWL_Schema4.0.xsd myspec.yawl --noout
```

The engine also validates on load via `YMarshal.unmarshalSpecifications()`. Schema violations produce `YSyntaxException` with the Xerces error message appended.
