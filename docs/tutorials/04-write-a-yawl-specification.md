# Tutorial: Write a YAWL Specification

By the end of this tutorial you will have authored a minimal YAWL workflow specification from scratch in XML, validated it against the YAWL schema, loaded it into the engine, and confirmed the engine accepted it. You will understand every element in the file and why it is there.

This tutorial assumes you have completed [Tutorial 1: Build YAWL from Source](01-build-yawl.md). To load the spec into the engine you also need a running engine (see Tutorial 1, Step 5 of the companion deployment guide). You can complete the authoring and validation steps without a running engine.

---

## What a YAWL specification contains

A YAWL specification file is an XML document that declares one or more workflow nets inside a root `specificationSet` element. Each net maps to a Petri-net structure with exactly one input condition, exactly one output condition, and any number of tasks and intermediate conditions wired by flow arcs.

The minimal structure you need for a two-task sequential process is:

```
specificationSet
  specification (uri="MySpec")
    metaData
    schema                         ← empty XSD, required by the file format
    decomposition (isRootNet, NetFactsType)
      inputCondition → task1 → task2 → outputCondition
    decomposition (task1 gateway, WebServiceGatewayFactsType)
    decomposition (task2 gateway, WebServiceGatewayFactsType)
```

Each task has two decomposition entries: one inside the net that describes its flow topology, and one at the specification level that describes whether it is manual or automated. They share the same `id` value and are linked by a `decomposesTo` reference.

---

## Step 1: Understand the schema version

The repository ships two schema files:

- `schema/YAWL_Schema4.0.xsd` — current version, requires `version="4.0"` on `specificationSet`
- Sample files in `build/workletService/samples/` use `version="3.0"` with a 3.0 schema URL

This tutorial targets the current 4.0 schema. Your spec must declare `version="4.0"` on the `specificationSet` root element or validation will fail with an enumeration constraint error.

Check that the schema file exists in the repository:

```bash
ls schema/YAWL_Schema4.0.xsd
```

Expected: the path prints without error.

---

## Step 2: Write the minimal specification

Create a file named `my-first-spec.yawl` in a working directory. The complete content is shown below — copy it exactly, then examine each section using the annotations that follow.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet
    xmlns="http://www.yawlfoundation.org/yawlschema"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    version="4.0"
    xsi:schemaLocation="http://www.yawlfoundation.org/yawlschema
                        http://www.yawlfoundation.org/yawlschema/YAWL_Schema4.0.xsd">

  <specification uri="MyFirstSpec">
    <name>My First Specification</name>
    <documentation>A minimal two-task sequential workflow</documentation>

    <metaData>
      <title>My First Specification</title>
      <version>0.1</version>
      <persistent>false</persistent>
      <identifier>UID_00000000-0000-0000-0000-000000000001</identifier>
    </metaData>

    <!--
      The xs:schema element is required by the YAWL file format.
      It declares any custom types used by net variables.
      For a spec with no custom types, leave it empty.
    -->
    <schema xmlns="http://www.w3.org/2001/XMLSchema"/>

    <!--
      The root net decomposition. isRootNet="true" marks this as the
      top-level net that is instantiated when a case is launched.
      xsi:type="NetFactsType" tells the parser to use the net schema type.
    -->
    <decomposition id="MyFirstSpec" isRootNet="true"
                   xsi:type="NetFactsType">
      <processControlElements>

        <!--
          Every net has exactly one inputCondition.
          It has one flowsInto arc pointing to the first task.
        -->
        <inputCondition id="InputCondition">
          <flowsInto>
            <nextElementRef id="ApproveRequest"/>
          </flowsInto>
        </inputCondition>

        <!--
          Task 1: ApproveRequest.
          join/split control types apply when multiple arcs converge or diverge.
          For a simple sequence, xor join and and split are the conventional defaults.
          decomposesTo links this task node to its gateway decomposition below.
        -->
        <task id="ApproveRequest">
          <name>Approve Request</name>
          <flowsInto>
            <nextElementRef id="ProcessApproval"/>
          </flowsInto>
          <join code="xor"/>
          <split code="and"/>
          <decomposesTo id="ApproveRequestGateway"/>
        </task>

        <!--
          Task 2: ProcessApproval.
          Its flowsInto points to the output condition, completing the case.
        -->
        <task id="ProcessApproval">
          <name>Process Approval</name>
          <flowsInto>
            <nextElementRef id="OutputCondition"/>
          </flowsInto>
          <join code="xor"/>
          <split code="and"/>
          <decomposesTo id="ProcessApprovalGateway"/>
        </task>

        <!--
          Every net has exactly one outputCondition.
          It has no flowsInto — it is the terminal element.
        -->
        <outputCondition id="OutputCondition"/>

      </processControlElements>
    </decomposition>

    <!--
      Gateway decomposition for ApproveRequest.
      xsi:type="WebServiceGatewayFactsType" marks this as a task gateway.
      externalInteraction="manual" means a human completes this task.
      externalInteraction="automated" would mean a service completes it.
    -->
    <decomposition id="ApproveRequestGateway"
                   xsi:type="WebServiceGatewayFactsType">
      <externalInteraction>manual</externalInteraction>
    </decomposition>

    <!--
      Gateway decomposition for ProcessApproval.
    -->
    <decomposition id="ProcessApprovalGateway"
                   xsi:type="WebServiceGatewayFactsType">
      <externalInteraction>manual</externalInteraction>
    </decomposition>

  </specification>
</specificationSet>
```

### What each section does

**`specificationSet` (root element)**
The `version="4.0"` attribute must exactly match the enumerated value in the schema. The `xmlns` declaration places all elements in the YAWL namespace. Without it every element name would fail to resolve.

**`specification`**
The `uri` attribute is the unique name the engine uses to identify this specification. It also becomes the default case identifier prefix. The `uri` value must be an XML NCName (no spaces, no colons).

**`metaData`**
The `version` child is the specification version, used together with `uri` and `identifier` to form a `YSpecificationID`. The `identifier` must be an XML NCName; using the `UID_` prefix followed by a UUID is the convention. `persistent` controls whether the engine stores case data in the database.

**`schema`**
An embedded XSD. For a spec with no custom complex types, an empty `<schema>` element satisfies the parser. If your net variables need custom types, declare them here.

**`decomposition` (net)**
`isRootNet="true"` designates this as the entry point. The `xsi:type="NetFactsType"` polymorphism is what distinguishes a net decomposition from a gateway decomposition — both use the `decomposition` element name but different content models.

**`inputCondition`**
Required. Exactly one per net. The `flowsInto`/`nextElementRef` arc connects it to the first task.

**`task`**
Each task has a `join` and `split` with a `code` attribute (`and`, `or`, `xor`). For a simple sequence, `xor` join (token passes if any input arc fires) and `and` split (all output arcs fire) are the defaults. The `decomposesTo` element references the gateway decomposition by `id`.

**`outputCondition`**
Required. Exactly one per net. Has no `flowsInto`. When a token reaches it, the case completes.

**Gateway decompositions**
`WebServiceGatewayFactsType` describes what happens when the task fires. `externalInteraction="manual"` puts the work item into a worklist for a human to claim and complete.

---

## Step 3: Validate the specification

`xmllint` checks the file against the XSD before you send it to the engine. This catches structural errors locally without a running server.

```bash
xmllint --schema schema/YAWL_Schema4.0.xsd --noout my-first-spec.yawl
```

Expected output:

```
my-first-spec.yawl validates
```

If you see validation errors, the most common causes are:

| Error message | Cause | Fix |
|---|---|---|
| `Element 'specificationSet', attribute 'version': '3.0' is not a valid value` | Used version 3.0 instead of 4.0 | Change to `version="4.0"` |
| `Element ... this element is not expected` | Element appears in wrong order | The schema enforces order: `inputCondition`, then tasks/conditions, then `outputCondition` |
| `Element 'decomposesTo', attribute 'id': ... no identity constraint` | A `decomposesTo` id has no matching decomposition | Add the gateway `decomposition` element with a matching `id` |
| `Element ... namespace 'http://www.w3.org/2001/XMLSchema'` | Missing namespace on `schema` element | Add `xmlns="http://www.w3.org/2001/XMLSchema"` to the `schema` element |

---

## Step 4: Load the specification into the engine

Interface A's `upload` action accepts the entire specification XML as a POST parameter. Use `--data-urlencode` so that the XML content is properly encoded even if it contains `&` or `+` characters.

First obtain a session handle:

```bash
SESSION=$(curl -s -X POST http://localhost:8080/yawl/ib \
  -d "action=connect&userid=admin&password=YAWL" \
  | sed 's/<[^>]*>//g' | tr -d ' ')
```

Then upload the specification:

```bash
curl -s -X POST http://localhost:8080/yawl/ia \
  -d "action=upload&sessionHandle=$SESSION" \
  --data-urlencode "specXML@my-first-spec.yawl"
```

Expected output:

```xml
<response><success>Specification successfully loaded</success></response>
```

If you see `<failure>` with a parse message, the engine's XML parser rejected the file. Run the `xmllint` validation from Step 3 again to isolate the problem.

If you see `<failure>Specification with the same URI already loaded`, the engine already has a `MyFirstSpec` specification loaded. Unload it first (see Step 6) or change the `uri` attribute to a different value.

---

## Step 5: Verify the specification is registered

```bash
curl -s -X POST http://localhost:8080/yawl/ia \
  -d "action=getList&sessionHandle=$SESSION"
```

Expected output contains an entry for `MyFirstSpec`:

```xml
<response>
  <specEntry>
    <specID>
      <identifier>MyFirstSpec</identifier>
      <version>0.1</version>
      <uri>MyFirstSpec</uri>
    </specID>
    <specName>My First Specification</specName>
    <documentation>A minimal two-task sequential workflow</documentation>
    <hasTopLevelNet>true</hasTopLevelNet>
  </specEntry>
</response>
```

The values under `<specID>` are the three components of the engine's `YSpecificationID`: `identifier` comes from the `<identifier>` element in `metaData`, `version` comes from `<version>` in `metaData`, and `uri` comes from the `uri` attribute on `<specification>`. When you launch a case in Tutorial 3, you supply all three of these values.

---

## Step 6: Clean up (optional)

To remove the specification from the engine when you are done:

```bash
curl -s -X POST http://localhost:8080/yawl/ia \
  -d "action=unload&sessionHandle=$SESSION" \
  -d "specidentifier=MyFirstSpec" \
  -d "specversion=0.1" \
  -d "specuri=MyFirstSpec"
```

Expected output:

```xml
<response><success>Specification unloaded: MyFirstSpec</success></response>
```

Then disconnect:

```bash
curl -s -X POST http://localhost:8080/yawl/ib \
  -d "action=disconnect&sessionHandle=$SESSION"
```

---

## What happened

The engine's `EngineGatewayImpl.loadSpecification()` method parsed the XML into a `YSpecification` object, unmarshalled each `decomposition` element into either a `YNet` or a `YDecomposition`, and linked the `task` elements to their gateway decompositions by ID reference. The net's topology — `InputCondition → ApproveRequest → ProcessApproval → OutputCondition` — became an in-memory graph of `YInputCondition`, `YAtomicTask`, and `YOutputCondition` objects connected by `YFlow` arcs.

When you later launch a case from this specification (see Tutorial 3), the engine creates a `YNetRunner` that holds a token set and evaluates firing rules against this graph in real time.

---

## What next

- [Tutorial 3: Run Your First Workflow](03-run-your-first-workflow.md) — use the specification you just wrote to run a full case through the engine.
- **Add data variables** — extend the spec by adding `inputParam` and `outputParam` elements to the net decomposition, and `startingMappings`/`completedMappings` XQuery expressions to each task. The `BobOne.yawl` sample in `build/workletService/samples/worklets/` shows the complete pattern.
- **Add resourcing** — add a `<resourcing>` block inside each task in the net decomposition to specify how the work item is offered, allocated, and started. The `resourcing` element's schema type is `ResourcingFactsType`.
- **Schema reference** — `schema/YAWL_Schema4.0.xsd` is the authoritative document for every element and attribute name. Read it alongside the specs in `build/workletService/samples/` to understand the full feature set.
