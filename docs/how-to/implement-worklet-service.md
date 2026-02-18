# How to Implement the Worklet Service

## Prerequisites

- YAWL engine (stateful `YEngine`) deployed and running
- Worklet service WAR deployed at `http://localhost:8080/workletService`
- A "parent" specification that has one or more atomic tasks you want to replace
  dynamically with sub-workflows (worklets)
- One or more "worklet" specifications — regular YAWL specifications that implement the
  substituted behaviour

## Background

The Worklet Service implements Ripple Down Rule (RDR) based exception handling and
dynamic task substitution for the stateful YAWL engine. When a task fires, the Worklet
Service evaluates an RDR rule tree against the current work item data. If a rule
matches, the Worklet Service:

1. Removes the original work item from the worklist.
2. Launches a worklet case (a separate YAWL specification) in its place.
3. When the worklet case completes, maps its output data back to the parent case and
   resumes execution from the original task's completion point.

The worklet directory at `build/workletService/samples/` provides working examples:
`OrganiseConcert` (parent) with worklets `CancelShow`, `ChangeToLargerVenue`, etc.

## Steps

### 1. Create Worklet Specifications

A worklet is a standard YAWL specification. The only requirement is that its input/output
variable names match what the parent specification passes in and expects back. Use YAWL
Editor or write the XML directly:

```xml
<!-- CancelOrder.yawl — worklet for order cancellation path -->
<specificationSet xmlns="http://www.yawlfoundation.org/yawlschema" version="4.0">
  <specification uri="CancelOrder">
    <name>Cancel Order</name>
    <metaData>
      <version>1.0</version>
      <persistent>false</persistent>
    </metaData>
    <decomposition id="CancelOrder" isRootNet="true" xsi:type="NetFactsType">
      <!-- net variables must match parent task variable names -->
      <localVariable>
        <name>orderID</name>
        <type>string</type>
        <namespace>http://www.w3.org/2001/XMLSchema</namespace>
      </localVariable>
      <!-- ... tasks, conditions, flows ... -->
    </decomposition>
  </specification>
</specificationSet>
```

Place the worklet `.yawl` files in the worklet service's repository directory. The
default location is the `worklets/` subdirectory of the Worklet Service deployment
(configured in `workletService/web.xml` as the `WorkletDirectory` context param).

### 2. Upload Worklet Specifications to the Engine

Before the Worklet Service can launch a worklet, the specification must be loaded into
the engine:

```bash
# Authenticate with the engine
SESSION=$(curl -s "http://localhost:8080/yawl/ib?action=connect&userid=admin&password=YAWL")

# Upload worklet spec to the engine
curl -s -X POST "http://localhost:8080/yawl/ib" \
  -d "action=uploadSpecification" \
  -d "specXML=$(cat CancelOrder.yawl)" \
  -d "sessionHandle=$SESSION"
```

Repeat for each worklet specification. The engine returns the specification identifier
(`uri`) to use in the RDR rule tree.

### 3. Define the RDR Rule Tree

The Worklet Service uses an RDR (Ripple Down Rule) rule tree to decide which worklet to
substitute for a given task at runtime. The rule tree is stored as XML and evaluated
against the work item's data element.

Each rule node has:
- A condition (XPath expression evaluated against the work item data)
- A conclusion (worklet URI to launch if the condition is true)
- Optional child `exception` and `else` nodes for refinement

```xml
<!-- rules/ProcessOrder.xrs — RDR rule set for task ProcessOrder in spec SalesProcess -->
<ruleSet taskID="ProcessOrder" specID="SalesProcess" specVersion="1.0">

  <rdrNode id="1" type="ItemSelection">
    <!-- Root node — always evaluated first -->
    <condition>true()</condition>
    <conclusion>
      <!-- Default worklet when no rule matches -->
      <worklet>StandardProcess</worklet>
    </conclusion>
    <exception>
      <!-- More specific rule: if order value > 10000, use the approval worklet -->
      <rdrNode id="2" type="ItemSelection">
        <condition>
          //orderValue &gt; 10000
        </condition>
        <conclusion>
          <worklet>HighValueApproval</worklet>
        </conclusion>
        <exception>
          <!-- Even more specific: high value + international = senior approval -->
          <rdrNode id="3" type="ItemSelection">
            <condition>
              //orderValue &gt; 10000 and //destinationCountry != 'AU'
            </condition>
            <conclusion>
              <worklet>SeniorApprovalInternational</worklet>
            </conclusion>
          </rdrNode>
        </exception>
      </rdrNode>
    </exception>
    <else>
      <!-- Low value orders: use quick-approval worklet -->
      <rdrNode id="4" type="ItemSelection">
        <condition>//orderValue &lt; 500</condition>
        <conclusion>
          <worklet>QuickApproval</worklet>
        </conclusion>
      </rdrNode>
    </else>
  </rdrNode>

</ruleSet>
```

Rule file naming convention: `<specID>.xrs` placed in the `rules/` subdirectory of the
Worklet Service deployment.

### 4. Register the Worklet Service with the Engine

The Worklet Service connects to the engine via Interface B (for work item events) and
Interface X (for exception announcements). Configure the connection in
`workletService/web.xml`:

```xml
<context-param>
    <param-name>InterfaceBBackend</param-name>
    <param-value>http://localhost:8080/yawl/ib</param-value>
</context-param>
<context-param>
    <param-name>EngineLogonName</param-name>
    <param-value>workletService</param-value>
</context-param>
<context-param>
    <param-name>EngineLogonPassword</param-name>
    <param-value>worklet</param-value>
</context-param>
<context-param>
    <param-name>WorkletServiceURL</param-name>
    <param-value>http://localhost:8080/workletService/ixe</param-value>
</context-param>
```

On startup, the Worklet Service registers itself as an exception observer with the
engine by calling `setExceptionObserver`. The engine then forwards `checkWorkItemConstraints`
announcements to the Worklet Service for each enabled work item.

### 5. Annotate Parent Tasks to Use Worklet Selection

In the parent specification, tasks that should be subject to worklet selection must
carry a custom service URI pointing to the Worklet Service:

```xml
<task id="ProcessOrder">
  <name>Process Order</name>
  <!-- Route this task's work item to the Worklet Service for selection -->
  <customForm>
    <uri>http://localhost:8080/workletService/worklist</uri>
  </customForm>
  <!-- OR: set decomposition to the workletService service -->
  <decomposesTo id="workletService"/>
</task>
```

Alternatively, specify the worklet selection at the resourcing level by pointing the
allocator at the Worklet Service. The exact mechanism depends on the YAWL version; in
5.2, the interface X exception observer approach is the canonical path.

### 6. Test a Rule Fires Correctly

Launch a parent case with data that matches one of your rules:

```bash
# Launch the parent case with order data that should trigger HighValueApproval
curl -s -X POST "http://localhost:8080/yawl/ib" \
  -d "action=launchCase" \
  -d "specID=SalesProcess" \
  -d "specVersion=1.0" \
  -d "caseParams=<SalesProcess><orderValue>15000</orderValue><destinationCountry>AU</destinationCountry></SalesProcess>" \
  -d "sessionHandle=$SESSION"
```

Check the Worklet Service log for rule evaluation output:

```bash
tail -f logs/workletService.log | grep -E "rule|worklet|match"
```

Expected log entries (order matters):
1. `checkWorkItemConstraints received for task ProcessOrder`
2. `Evaluating rdrNode 1: condition=true() → matched`
3. `Evaluating rdrNode 2: condition=//orderValue > 10000 → matched`
4. `Launching worklet: HighValueApproval for case <parentCaseID>`

### 7. Handle Worklet Completion and Data Mapping

When the launched worklet case completes, the Worklet Service maps output data back to
the parent task. Define the mapping in the rule node's `conclusion`:

```xml
<conclusion>
  <worklet>HighValueApproval</worklet>
  <outputMappings>
    <!-- Map worklet output variable 'approvalDecision' to parent variable 'status' -->
    <mapping>
      <workletVar>approvalDecision</workletVar>
      <parentVar>status</parentVar>
    </mapping>
  </outputMappings>
</conclusion>
```

If no output mappings are defined, the Worklet Service uses name-matching: worklet
output variables with the same name as parent variables are mapped automatically.

## Verify

1. Deploy both the parent spec and all worklet specs to the engine.
2. Launch a parent case with data that matches a specific rule.
3. In the Worklet Service admin UI (`http://localhost:8080/workletService`), navigate to
   "Running Worklets." The launched worklet case should appear there.
4. Complete the worklet case. Return to the parent case — the parent task should now be
   complete and the case should have progressed to the next task.

```bash
# Confirm the worklet case was launched
curl -s "http://localhost:8080/yawl/ib?action=getActiveCaseIDs&sessionHandle=$SESSION"
# Expect two case IDs: the parent and the worklet

# After worklet completion, parent should progress
curl -s "http://localhost:8080/yawl/ib?action=getWorkItemsForCase&caseID=<parentID>&sessionHandle=$SESSION"
```

## Troubleshooting

**No worklet launched despite data matching the rule condition**
Verify the rule file name matches `<specID>.xrs` exactly (case-sensitive) and is placed
in the Worklet Service's `rules/` directory. Also confirm the task ID in `taskID=` matches
the task's `id` attribute in the specification XML, not its name.

**`YStateException: worklet specification not found`**
The worklet URI in the rule's `<worklet>` element must exactly match the `uri` attribute
in the worklet specification's `<specification>` tag. Upload the worklet to the engine
first (step 2) and verify the URI with `getSpecificationList`.

**Parent case stalls after worklet completes**
The Worklet Service receives the worklet's `CASE_COMPLETED` event and then calls
`completeWorkItem` on the parent's original work item. If the parent work item has been
cancelled or timed out in the interim, this call fails silently. Enable DEBUG logging on
`WorkletService` to see the completion attempt and its result.

**RDR evaluation order unexpected**
Rules are evaluated depth-first, `exception` branch before `else` branch. If two rules
can match the same data, the deeper (more specific) rule in the `exception` chain takes
priority. Restructure the tree so more specific conditions are always in the `exception`
subtree of their parent.
