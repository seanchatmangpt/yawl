# Tutorial: Run Your First Workflow

By the end of this tutorial you will have loaded a workflow specification into a running YAWL engine, launched a case, claimed a work item, completed it with output data, and confirmed that the case finished. You will see every HTTP call and its expected response, giving you the full mental model of how YAWL executes a workflow end to end.

This tutorial assumes you have completed [Tutorial 1: Build YAWL from Source](01-build-yawl.md) and that the YAWL engine is deployed and running at `http://localhost:8080/yawl`.

---

## Prerequisites

- YAWL engine running at `http://localhost:8080/yawl` (deployed to Tomcat or equivalent servlet container)
- `curl` available in your shell
- A YAWL specification file to load. This tutorial uses `build/workletService/samples/worklets/BobOne.yawl` from the repository root. It contains a single-task net with one manual work item — ideal for learning the cycle.

Verify the repository file exists before continuing:

```bash
ls build/workletService/samples/worklets/BobOne.yawl
```

Expected: the path prints without error.

---

## Step 1: Verify the engine is running

The Interface B servlet responds to a `checkConnection` action. Before authentication you can send an anonymous `connect` request and observe whether the engine replies at all.

```bash
curl -s -X POST http://localhost:8080/yawl/ib \
  -d "action=connect&userid=admin&password=YAWL"
```

Expected output (the session handle value will differ):

```xml
<response><success>WFXSsKs1736...</success></response>
```

If you see `Connection refused`, the engine is not running. If you see a `<failure>` element, the credentials are wrong — the default admin account is `admin` / `YAWL` (not `admin`/`admin`).

Store the session handle in a shell variable so every subsequent step can reference it without repetition:

```bash
SESSION=$(curl -s -X POST http://localhost:8080/yawl/ib \
  -d "action=connect&userid=admin&password=YAWL" \
  | sed 's/<[^>]*>//g' | tr -d ' ')
echo "Session handle: $SESSION"
```

All subsequent steps use `$SESSION`.

---

## Step 2: Load the sample specification

Interface A accepts specification XML via a form-encoded POST. The `specXML` parameter holds the full XML content of the `.yawl` file.

```bash
curl -s -X POST http://localhost:8080/yawl/ia \
  -d "action=upload&sessionHandle=$SESSION" \
  --data-urlencode "specXML@build/workletService/samples/worklets/BobOne.yawl"
```

Expected output:

```xml
<response><success>Specification successfully loaded</success></response>
```

If you see `<failure>Specification with the same URI already loaded`, the spec is already in the engine. That is fine — continue to Step 3.

If you see `<failure>` with a parse error, confirm the file exists and is valid XML with:

```bash
xmllint --noout build/workletService/samples/worklets/BobOne.yawl && echo "XML is well-formed"
```

---

## Step 3: Confirm the specification is loaded

```bash
curl -s -X POST http://localhost:8080/yawl/ia \
  -d "action=getList&sessionHandle=$SESSION"
```

Expected output contains a `<specEntry>` element for `BobOne`:

```xml
<response>
  <specEntry>
    <specID>
      <identifier>BobOne</identifier>
      <version>0.2</version>
      <uri>BobOne</uri>
    </specID>
    <specName>Bob One</specName>
    <documentation>Worklet to enact when bob is one</documentation>
    <hasTopLevelNet>true</hasTopLevelNet>
  </specEntry>
</response>
```

The `<identifier>` value (`BobOne`) and `<version>` value (`0.2`) are what you will pass when launching a case. Note them.

---

## Step 4: Launch a case

Interface B's `launchCase` action takes three identifiers from the specification header: `specidentifier`, `specversion`, and `specuri`. Use the values observed in Step 3.

The `BobOne` specification declares one net-level input variable `SomeBob` of type `string`. Supply it in the `caseParams` field wrapped in a `<data>` element:

```bash
curl -s -X POST http://localhost:8080/yawl/ib \
  -d "action=launchCase&sessionHandle=$SESSION" \
  -d "specidentifier=BobOne" \
  -d "specversion=0.2" \
  -d "specuri=BobOne" \
  --data-urlencode "caseParams=<data><SomeBob>hello</SomeBob></data>"
```

Expected output is a plain case identifier string wrapped in a `<response>` element:

```xml
<response><success>1</success></response>
```

The number `1` is the case ID. The engine assigns sequential integers to running cases. Store it:

```bash
CASE_ID=1
```

If you see `<failure>No specification found`, the identifier or version does not match what is in the engine. Re-run Step 3 and copy the values exactly.

---

## Step 5: Poll for enabled work items

After a case launches, the engine immediately evaluates the net's input condition and enables any tasks that follow it. In `BobOne` that is the single task `Get_Bob_One`. It will appear in the live work item list with status `Enabled`.

```bash
curl -s -X POST http://localhost:8080/yawl/ib \
  -d "action=getLiveItems&sessionHandle=$SESSION"
```

Expected output contains one `<workItemRecord>`:

```xml
<response>
  <workItemRecord>
    <taskid>Get_Bob_One</taskid>
    <caseid>1</caseid>
    <specidentifier>BobOne</specidentifier>
    <specversion>0.2</specversion>
    <status>Enabled</status>
    <enablementTime>2026-02-18 09:00:00.000</enablementTime>
  </workItemRecord>
</response>
```

The work item ID used in subsequent steps is formed by joining the case ID and task ID with a colon: `1:Get_Bob_One`. Confirm this by reading the `<id>` element if present, or construct it from `<caseid>` and `<taskid>`:

```bash
ITEM_ID="1:Get_Bob_One"
```

If the list is empty, the case may not have started yet. Wait one second and retry. If it remains empty, the engine may have routed the case to the default resource service; check the engine log in Tomcat's `logs/catalina.out`.

---

## Step 6: Check out the work item

Checking out a work item (also called "starting") moves it from status `Enabled` to status `Executing`. This signals that a participant or system has claimed the work and begun processing it.

```bash
curl -s -X POST http://localhost:8080/yawl/ib \
  -d "action=checkout&sessionHandle=$SESSION" \
  -d "workItemID=$ITEM_ID"
```

Expected output is the work item record in its new `Executing` state, including the current data values:

```xml
<response>
  <workItemRecord>
    <taskid>Get_Bob_One</taskid>
    <caseid>1</caseid>
    <status>Executing</status>
    <data>
      <SomeBob>hello</SomeBob>
    </data>
  </workItemRecord>
</response>
```

The `<data>` block contains the variable values that were mapped from the case-level `SomeBob` variable to the task-level `SomeBob` parameter via the `<startingMappings>` in the specification.

---

## Step 7: Complete the work item with output data

Completing a work item (called "checking in" in YAWL's Interface B) writes the task output back to the net and allows the net runner to continue. Supply the updated variable value in the `data` parameter, using the task's output variable name as the element tag.

```bash
curl -s -X POST http://localhost:8080/yawl/ib \
  -d "action=checkin&sessionHandle=$SESSION" \
  -d "workItemID=$ITEM_ID" \
  --data-urlencode "data=<Get_Bob_One><SomeBob>hello from step 7</SomeBob></Get_Bob_One>"
```

Expected output:

```xml
<response><success>Work item successfully completed</success></response>
```

The data element's root tag must match the task's decomposition ID (`Get_Bob_One`), not the generic `data`. Inside it, each output variable appears as a child element. The engine's `<completedMappings>` in the spec copies `Get_Bob_One/SomeBob` back to the net's `SomeBob` variable.

---

## Step 8: Verify the case has completed

Once all tasks in the net complete and the output condition is reached, the case is no longer in the live items list and no longer appears in the running cases list.

```bash
curl -s -X POST http://localhost:8080/yawl/ib \
  -d "action=getAllRunningCases&sessionHandle=$SESSION"
```

Expected output when no cases remain running:

```xml
<response></response>
```

If case `1` is still present:

```xml
<response><case id="1" specID="BobOne"/></response>
```

...then the net runner has not yet received the completion event. Give it one second and poll again. In a healthy engine this completes within milliseconds.

Optionally, confirm the case ID is absent from the response with:

```bash
curl -s -X POST http://localhost:8080/yawl/ib \
  -d "action=getAllRunningCases&sessionHandle=$SESSION" \
  | grep -c "case id=\"$CASE_ID\""
```

Expected: `0` (the case ID does not appear in the response).

---

## Step 9: Disconnect

```bash
curl -s -X POST http://localhost:8080/yawl/ib \
  -d "action=disconnect&sessionHandle=$SESSION"
```

Expected output:

```xml
<response><success>Session disconnected</success></response>
```

---

## What happened

The engine received the specification, unmarshalled it from XML into an in-memory `YSpecification` object and a corresponding `YNet`, then registered the net's `YNetRunner` in its active case table.

When you issued `launchCase`, the engine created a `YNetRunner` for case `1`, fired a token into the input condition `InputCondition`, and evaluated the firing rule for `Get_Bob_One`. Because the task's join is `xor` and the input condition has a flow into it, the task immediately became `Enabled`.

When you issued `checkout`, the engine promoted the work item from `Enabled` to `Executing` and mapped the net variable `SomeBob` into the task's input parameter space.

When you issued `checkin`, the engine received the output data, ran the task's `<completedMappings>` XQuery expressions to write `SomeBob` back to the net, moved a token from `Get_Bob_One` to `OutputCondition`, and recognised that the output condition had been reached. It then removed the case from the active runner table and emitted a case-completion event to any registered observers.

---

## What next

- [Tutorial 4: Write a YAWL Specification](04-write-a-yawl-specification.md) — learn how to author the XML that defines a net from scratch.
- **API Reference** (`docs/API-REFERENCE.md`) — full reference for all Interface A, B, E, and X operations.
- **How to use the Resource Service** — when tasks have `<resourcing>` sections with `initiator="user"`, they are offered through the resource service rather than becoming directly visible in the engine's work item list.
