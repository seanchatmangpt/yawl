# YAWL V6 Specification Guide

**Version:** 6.0.0
**Document Date:** 2026-02-20
**Audience:** Workflow developers, system integrators, agents generating YAWL specifications
**Status:** Current — reflects YAWL_Schema4.0.xsd

---

## Table of Contents

1. [Overview](#overview)
2. [Getting Started](#getting-started)
3. [XML Structure](#xml-structure)
4. [Petri Net Semantics](#petri-net-semantics)
5. [Element Reference](#element-reference)
6. [Validation & Compliance](#validation--compliance)
7. [Real Workflow Examples](#real-workflow-examples)
8. [Common Patterns](#common-patterns)
9. [Best Practices](#best-practices)
10. [Troubleshooting](#troubleshooting)

---

## Overview

### What is a YAWL Specification?

A YAWL specification is an XML document that describes a workflow process using Petri net semantics. YAWL (Yet Another Workflow Language) combines:

- **Formal semantics** — grounded in Petri nets with mathematical verification
- **Workflow expressiveness** — supports all 43 ISO workflow patterns
- **Integration capability** — connects to external services, databases, and agents
- **Resource management** — role-based task assignment and resource allocation

### V6.0.0 Key Features

| Feature | Description | References |
|---------|-------------|------------|
| **Petri Net Foundation** | All control flow is expressed as places, transitions, and tokens | `/docs/explanation/petri-net-foundations.md` |
| **OR-Join Support** | Correctly handles choice convergence without deadlock or livelock | `/docs/explanation/or-join-semantics.md` |
| **Multi-Instance Tasks** | Dynamic task spawning with configurable join thresholds | `WCP-13` to `WCP-15` patterns |
| **Cancellation Regions** | Structured exception handling via token removal sets | `WCP-19` to `WCP-20` patterns |
| **Data Flow Integration** | XPath expressions for data routing and transformation | `xs:schema` embedded metadata |
| **Resource Binding** | Role-based allocation via offer/allocate/start semantics | Interface C documentation |

### Schema Version Reference

| Version | Year | Java Package | Namespace |
|---------|------|--------------|-----------|
| YAWL 4.0 | 2013 | yawl-engine v6.0+ | `http://www.yawlfoundation.org/yawlschema` |
| YAWL 3.0 | 2010 | yawl-engine v5.x | `http://www.yawlfoundation.org/yawlschema` |
| YAWL 2.2 | 2009 | yawl-engine v4.x | `http://www.citi.qut.edu.au/yawl` |
| YAWL Beta | 2003-2008 | yawl-engine v1-3 | Legacy, deprecated |

**Current Production Schema:** `schema/YAWL_Schema4.0.xsd` (47 KB, 700+ element definitions)

---

## Getting Started

### Minimum Specification

Every YAWL specification must include:

1. **Declaration** — XML header with UTF-8 encoding
2. **Root element** — `<specificationSet>` with version and namespace
3. **Specification** — One or more named `<specification>` blocks
4. **Decomposition** — At least one root net defining the control flow
5. **Entry/Exit** — Input and output conditions

```xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet
    version="4.0"
    xmlns="http://www.yawlfoundation.org/yawlschema"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.yawlfoundation.org/yawlschema
                        http://www.yawlfoundation.org/yawlschema/YAWL_Schema4.0.xsd">

  <specification uri="MyWorkflow">
    <name>Human-Readable Name</name>
    <documentation>Description of the workflow.</documentation>
    <metaData>
      <title>My Workflow</title>
      <creator>user@example.com</creator>
      <version>1.0</version>
    </metaData>

    <decomposition id="MainNet" isRootNet="true" xsi:type="NetFactsType">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="FirstTask"/></flowsInto>
        </inputCondition>
        <task id="FirstTask">
          <join code="xor"/><split code="and"/>
          <flowsInto><nextElementRef id="end"/></flowsInto>
        </task>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>

  </specification>

</specificationSet>
```

### Validation Command

```bash
xmllint --noout --schema schema/YAWL_Schema4.0.xsd spec.xml
```

**Output on success:** `spec.xml validates`

---

## XML Structure

### Root: `<specificationSet>`

Container for all specifications in a file. Multiple specifications in one file share the layout but are logically independent.

```xml
<specificationSet
    version="4.0"
    xmlns="http://www.yawlfoundation.org/yawlschema"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.yawlfoundation.org/yawlschema
                        http://www.yawlfoundation.org/yawlschema/YAWL_Schema4.0.xsd">
```

| Attribute | Required | Value |
|-----------|----------|-------|
| `version` | yes | Fixed string `"4.0"` |
| `xmlns` | yes | Fixed URI `http://www.yawlfoundation.org/yawlschema` |
| `xmlns:xsi` | yes | Fixed URI `http://www.w3.org/2001/XMLSchema-instance` |
| `xsi:schemaLocation` | recommended | Points to the XSD file for validation |

### Specification: `<specification>`

Defines a single workflow process.

| Attribute | Required | Type | Description |
|-----------|----------|------|-------------|
| `uri` | yes | anyURI | Unique identifier. Used by the engine and API to reference this workflow. Example: `"OrderProcessing"`, `"urn:example.com:workflow:v1.0"` |

**Child elements (in order):**

```xml
<specification uri="OrderProcessing">
  <name>Order Processing Workflow</name>
  <documentation>Handles order creation through fulfillment.</documentation>
  <metaData>
    <title>...</title>
    <creator>...</creator>
    <version>1.0</version>
    <persistent>false</persistent>
    <identifier>urn:uuid:...</identifier>
  </metaData>

  <!-- Custom data types (optional) -->
  <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
    <xs:element name="orderData">...</xs:element>
  </xs:schema>

  <!-- Net definitions (required: at least one with isRootNet="true") -->
  <decomposition id="MainNet" isRootNet="true" xsi:type="NetFactsType">
    ...
  </decomposition>

  <!-- Sub-net definitions (optional) -->
  <decomposition id="ApprovalSubnet" xsi:type="NetFactsType">
    ...
  </decomposition>

  <!-- External service definitions (optional) -->
  <decomposition id="EmailService" xsi:type="WebServiceGatewayFactsType">
    ...
  </decomposition>

  <!-- Imported net references (optional) -->
  <importedNet id="SharedTasks" uri="http://example.com/shared-tasks.yawl"/>
</specification>
```

### Decomposition: `<decomposition>`

Defines either a **net** (control flow) or a **task** (service/gateway).

#### Net Decomposition (xsi:type="NetFactsType")

```xml
<decomposition id="OrderNet" isRootNet="true" xsi:type="NetFactsType">
  <name>Order Processing Net</name>
  <documentation>Main workflow net.</documentation>

  <!-- Variable declarations -->
  <yawlData>
    <data id="orderID" type="string">
      <initialValue>ORD-00000</initialValue>
    </data>
    <data id="totalAmount" type="double">
      <initialValue>0.0</initialValue>
    </data>
  </yawlData>

  <!-- Control flow elements -->
  <processControlElements>
    <!-- Input condition (entry point) -->
    <inputCondition id="start">
      <flowsInto><nextElementRef id="CreateOrder"/></flowsInto>
    </inputCondition>

    <!-- Tasks and conditions -->
    <task id="CreateOrder">
      <name>Create Order</name>
      <join code="xor"/><split code="and"/>
      <flowsInto><nextElementRef id="ApproveOrder"/></flowsInto>
    </task>

    <!-- Output condition (exit point) -->
    <outputCondition id="end"/>
  </processControlElements>
</decomposition>
```

**Key attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `id` | ID | Unique within the specification. Used to reference this net. |
| `isRootNet` | boolean | If `true`, this is the entry point for the workflow. Exactly one must be `true`. |
| `xsi:type` | fixed string | Must be `"NetFactsType"` for nets. |

---

## Petri Net Semantics

### Core Concepts

**YAWL execution is token flow through a Petri net.** Every workflow construct (task, condition, choice, loop) maps to a Petri net element.

#### Places → Conditions

A `YCondition` holds **tokens** (identifiers representing case instances). A condition with a token means "this case is at this point in the workflow."

```
InputCondition(start)  —— token ——▶  Task1  —— token ——▶  OutputCondition(end)
```

#### Transitions → Tasks

A `YTask` **consumes tokens** from input conditions (according to its **join** semantic) and **produces tokens** in output conditions (according to its **split** semantic).

#### Token Creation

When a case starts:
1. Engine creates a unique `YIdentifier` (case ID)
2. Deposits the identifier as a token in the input condition
3. Calls `continueIfPossible()` to begin execution

#### Task Firing

For each task that becomes **enabled** (its join condition is met):

```
1. ENABLE: Check if all preset conditions have tokens (join semantics)
2. FIRE: Remove tokens from preset conditions, create task instances
3. EXECUTE: Task runs (external service, human work item, composite net)
4. EXIT: Add tokens to postset conditions (split semantics)
5. CONTINUE: Recursively enable downstream tasks
```

### Join Semantics

How a task decides when it can fire.

#### XOR-Join (Exclusive Or)

Fires when **at least one** input condition has a token.

**Use case:** After a choice (only one branch executes at runtime).

```xml
<join code="xor"/>
```

**Petri net:** Place—transition-place bipartite graph. Transition requires ≥1 token in preset.

#### AND-Join (Synchronisation)

Fires when **all** input conditions have tokens.

**Use case:** Merging parallel branches; waits for completion.

```xml
<join code="and"/>
```

**Petri net:** Transition requires 1+ token in each preset place.

#### OR-Join (Conditional Synchronisation)

Fires when **some** input conditions have tokens **AND** no more tokens will arrive from other branches.

**Use case:** Merging conditional paths without deadlock or duplicate execution.

```xml
<join code="or"/>
```

**Petri net:** Uses the E2WFOJ algorithm to detect when waiting is futile.

### Split Semantics

How a task distributes tokens to output conditions.

#### AND-Split (Parallel)

Copies the token to **all** output conditions simultaneously.

**Use case:** Start parallel work; all branches execute concurrently.

```xml
<split code="and"/>
```

**Effect:** If task has 2 output flows, both receive a token. Case now has 2 parallel threads.

#### XOR-Split (Exclusive Choice)

Routes the token to **exactly one** output condition, selected by guard predicates.

**Use case:** If/then/else routing based on case data.

```xml
<split code="xor"/>
  <flowsInto>
    <nextElementRef id="ApprovalPath" label="amount &gt; 1000"/>
    <nextElementRef id="StandardPath" label="else"/>
  </flowsInto>
```

Predicates are evaluated left-to-right; the first true predicate wins. If none match, the `else` (default) flows.

#### OR-Split (Multi-Choice)

Routes the token to **one or more** output conditions, independently selected by predicates.

**Use case:** Notify multiple departments based on case data.

```xml
<split code="or"/>
  <flowsInto>
    <nextElementRef id="FinancePath" label="requiresApproval = true()"/>
    <nextElementRef id="CISPath" label="requiresSecurityReview = true()"/>
    <nextElementRef id="CompliancePath" label="requiresCompliance = true()"/>
  </flowsInto>
```

All predicates are evaluated; token goes to every condition whose predicate is true.

---

## Element Reference

### Conditions

#### Input Condition

Entry point of a net. Exactly one per net. Marked with a token when the case starts.

```xml
<inputCondition id="start">
  <name>Start</name>
  <flowsInto>
    <nextElementRef id="FirstTask"/>
  </flowsInto>
</inputCondition>
```

#### Output Condition

Exit point of a net. When a token reaches it, the net completes.

```xml
<outputCondition id="end">
  <name>End</name>
</outputCondition>
```

#### Condition

Intermediate place for explicit routing.

```xml
<condition id="c1">
  <name>Order Approved</name>
  <flowsInto>
    <nextElementRef id="ShipOrder"/>
  </flowsInto>
</condition>
```

### Tasks

#### Atomic Task

Single work unit, typically delegated to a human or external service.

```xml
<task id="ReviewOrder">
  <name>Review Purchase Order</name>
  <join code="xor"/>
  <split code="and"/>
  <flowsInto>
    <nextElementRef id="ApprovalApproved" label="approved = true()"/>
    <nextElementRef id="ApprovalRejected" label="else"/>
  </flowsInto>
  <decomposesTo id="ReviewOrderDecomposition"/>
</task>
```

#### Composite Task

Decomposes into a sub-net. Task exits only when sub-net completes.

```xml
<task id="ProcessOrder" isComposite="true">
  <name>Process Order (Sub-process)</name>
  <join code="xor"/>
  <split code="and"/>
  <flowsInto>
    <nextElementRef id="ShipOrder"/>
  </flowsInto>
  <decomposesTo id="OrderProcessingSubnet"/>
</task>
```

#### Multi-Instance Task

Single task definition that spawns multiple instances dynamically.

```xml
<task id="ReviewLineItems" multiinstance="true">
  <name>Review Line Item (multi-instance)</name>
  <join code="xor"/>
  <split code="and"/>
  <flowsInto>
    <nextElementRef id="FinalizeOrder"/>
  </flowsInto>
  <multiinstanceAttributes>
    <creationMode code="parallel"/>
    <miDataType>xs:integer</miDataType>
    <counterVariable>itemCount</counterVariable>
    <minInstances>1</minInstances>
    <maxInstances>999</maxInstances>
    <threshold>100</threshold>
  </multiinstanceAttributes>
</task>
```

**Parameters:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `creationMode` | `parallel` or `sequential` | When instances are created |
| `threshold` | integer | Task exits when this many instances complete (≤ total instances) |

---

## Validation & Compliance

### Schema Validation

**Requirement:** Every specification must validate against `schema/YAWL_Schema4.0.xsd`.

```bash
xmllint --noout --schema schema/YAWL_Schema4.0.xsd myspec.xml
```

**Common validation errors:**

| Error | Cause | Fix |
|-------|-------|-----|
| `Element 'foo': This element is not expected.` | Element in wrong position or wrong namespace | Check element order; verify namespace is `http://www.yawlfoundation.org/yawlschema` |
| `Attribute 'uri': required but missing` | `<specification>` or `<decomposition>` without `uri` attribute | Add `uri="UniqueIdentifier"` |
| `xsi:type is not allowed` | Missing XSD import | Ensure `xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"` in root |
| `Unexpected end of document` | Missing closing tags | Count opening/closing tag pairs; use XML editor with validation |

### Compliance Checklist

Before deploying a specification:

- [ ] Specification has unique `uri` attribute
- [ ] Exactly one `<decomposition>` has `isRootNet="true"`
- [ ] Every task has a `<join>` and `<split>` element
- [ ] Every condition flows to at least one task or output condition (no dead ends)
- [ ] Every task is reachable from the input condition
- [ ] Output condition is reachable from all tasks
- [ ] XPath predicates in splits are syntactically valid JEXL
- [ ] Variable names referenced in XPath exist in `<yawlData>`
- [ ] Specification validates against schema: `xmllint --schema schema/YAWL_Schema4.0.xsd spec.xml`

---

## Real Workflow Examples

### Example 1: Simple Purchase Order (Sequential)

**File:** `/exampleSpecs/SimplePurchaseOrder.xml`

**Pattern:** WCP-01 Sequence

**Control flow:**
```
Start → CreatePO → ApprovePO → End
```

**Features:**
- XOR joins (single input path)
- AND splits (single output path)
- No data routing

**Validation:**
```bash
xmllint --schema schema/YAWL_Schema4.0.xsd exampleSpecs/SimplePurchaseOrder.xml
```

**Lines of interest:**
- Line 7: Root net declaration
- Line 10-15: Input condition
- Line 16-23: First task with XOR join, AND split
- Line 24-31: Second task
- Line 32-34: Output condition

### Example 2: Document Processing (Conditional Routing)

**File:** `/exampleSpecs/DocumentProcessing.xml`

**Pattern:** WCP-04 Exclusive Choice + WCP-05 Simple Merge

**Control flow:**
```
Start → ReceiveDoc → ReviewDoc
                       ├─→ ApprovalApproved → End
                       └─→ ApprovalRejected → End
```

**Features:**
- Conditional split (if/else based on reviewResult)
- Merging of parallel conditional paths

**Validation:**
```bash
xmllint --schema schema/YAWL_Schema4.0.xsd exampleSpecs/DocumentProcessing.xml
```

### Example 3: Parallel Processing (Synchronisation)

**File:** `/exampleSpecs/ParallelProcessing.xml`

**Pattern:** WCP-02 Parallel Split + WCP-03 Synchronisation

**Control flow:**
```
Start → Initialize → ┬→ ParallelTask1 →┐
                     ├→ ParallelTask2 →┼→ Synchronize → Complete → End
                     └→ ParallelTask3 →┘
```

**Features:**
- AND split: Creates 3 parallel branches
- AND join: Waits for all 3 branches to complete
- Demonstration of multi-instance token flow

**Validation:**
```bash
xmllint --schema schema/YAWL_Schema4.0.xsd exampleSpecs/ParallelProcessing.xml
```

---

## Common Patterns

### Pattern 1: Sequential Tasks (WCP-01)

```xml
<task id="Task1">
  <join code="xor"/><split code="and"/>
  <flowsInto><nextElementRef id="Task2"/></flowsInto>
</task>
<task id="Task2">
  <join code="xor"/><split code="and"/>
  <flowsInto><nextElementRef id="end"/></flowsInto>
</task>
```

**When to use:** Simple workflows with no branching.

### Pattern 2: If/Then/Else (WCP-04 + WCP-05)

```xml
<task id="CheckAmount">
  <join code="xor"/><split code="xor"/>
  <flowsInto>
    <nextElementRef id="HighValuePath" label="amount > 10000"/>
    <nextElementRef id="StandardPath" label="else"/>
  </flowsInto>
</task>
<condition id="HighValuePath"/>
<condition id="StandardPath"/>
<task id="ApproveHighValue">
  <join code="xor"/><split code="and"/>
  <flowsInto><nextElementRef id="Proceed"/></flowsInto>
</task>
<task id="ApproveStandard">
  <join code="xor"/><split code="and"/>
  <flowsInto><nextElementRef id="Proceed"/></flowsInto>
</task>
<condition id="Proceed"/>
```

**When to use:** Binary decisions based on case data.

### Pattern 3: Parallel Work (WCP-02 + WCP-03)

```xml
<task id="SplitWork">
  <join code="xor"/><split code="and"/>
  <flowsInto>
    <nextElementRef id="Work1"/>
    <nextElementRef id="Work2"/>
    <nextElementRef id="Work3"/>
  </flowsInto>
</task>
<task id="Work1">
  <join code="xor"/><split code="and"/>
  <flowsInto><nextElementRef id="Sync"/></flowsInto>
</task>
<!-- Work2, Work3 similar -->
<condition id="Sync"/>
<task id="JoinWork">
  <join code="and"/><split code="and"/>
  <flowsInto><nextElementRef id="end"/></flowsInto>
</task>
```

**When to use:** Multiple tasks can execute concurrently and must all complete before proceeding.

### Pattern 4: Multi-Instance (WCP-13 to WCP-15)

```xml
<task id="ReviewItems" multiinstance="true">
  <join code="xor"/><split code="and"/>
  <flowsInto><nextElementRef id="Finalize"/></flowsInto>
  <multiinstanceAttributes>
    <creationMode code="parallel"/>
    <threshold>100</threshold>
  </multiinstanceAttributes>
</task>
```

**When to use:** Single task that runs multiple times (once per item in a collection).

---

## Best Practices

### 1. Naming Conventions

**IDs** (XML attributes): Use camelCase, no spaces.
- Good: `createOrder`, `sendInvoice`, `checkCredit`
- Bad: `Create Order`, `send-invoice`, `check_credit`

**Names** (display strings): Use Title Case with spaces.
- Good: `Create Order`, `Send Invoice`, `Check Credit`
- Bad: `createOrder`, `SEND_INVOICE`, `checkCredit`

### 2. Data Flow

Always declare variables in `<yawlData>` before referencing them in XPath predicates:

```xml
<yawlData>
  <data id="orderAmount" type="double">
    <initialValue>0.0</initialValue>
  </data>
</yawlData>

<!-- Later, in a split: -->
<flowsInto>
  <nextElementRef id="HighValuePath" label="orderAmount > 10000"/>
</flowsInto>
```

### 3. Guard Predicates

Use JEXL (Java Expression Language) for guards. Common patterns:

```
// Comparison
amount > 100
status = 'approved'
count >= minRequired

// Boolean operators
approved = true() and amount > 1000
isPriority = true() or isExpress = true()

// NOT
not(isReady = true())
```

### 4. Error Handling

Use cancellation sets for exception handling:

```xml
<task id="ProcessPayment">
  <!-- ... -->
  <removesTokens id="RefundCustomer" id="RollbackInventory"/>
  <!-- If ProcessPayment completes, it cancels RefundCustomer and RollbackInventory tasks -->
</task>
```

### 5. Documentation

Add `<documentation>` to every workflow and complex task:

```xml
<specification uri="OrderProcessing">
  <documentation>
    Handles order receipt through shipment. Supports:
    - Credit card and purchase order payment
    - Standard and express shipping
    - Backordered items with automated reorder
  </documentation>
</specification>
```

---

## Troubleshooting

### Issue: "No element could be enabled"

**Symptom:** Workflow gets stuck; no tasks fire.

**Cause:** Likely a logic error in join semantics or guards that prevent any task from becoming enabled.

**Debug:**
1. Check all joins: at least one task from input condition should have AND-join requiring all inputs
2. Check guards: use default/else labels to catch unmatched conditions
3. Use trace logging: enable `YNetRunner` debug output

### Issue: "Deadlock: output condition never marked"

**Symptom:** Case runs forever or gets stuck before completion.

**Cause:** Task flow doesn't properly reach output condition.

**Debug:**
1. Visually trace path from input to output
2. Verify every task has outgoing flowsInto
3. Check OR-join semantics if present

### Issue: "XPath predicate evaluates to non-boolean"

**Symptom:** Validation error on guard label.

**Cause:** JEXL expression doesn't return true/false.

**Fix:**
- `amount > 100` ✓ (returns boolean)
- `amount` ✗ (returns double, not boolean)
- `count` ✗ (returns integer, not boolean)

---

## References

- **YAWL Foundation:** https://www.yawlfoundation.org
- **Schema:** `/schema/YAWL_Schema4.0.xsd`
- **Example Specifications:** `/exampleSpecs/`
- **Petri Net Theory:** `/docs/explanation/petri-net-foundations.md`
- **Workflow Patterns:** `/docs/reference/workflow-patterns.md`
- **Validation:** `https://www.yawlfoundation.org/tools/`

---

**Maintained by:** YAWL V6 Development Team
**License:** Apache 2.0
**Last Updated:** 2026-02-20
