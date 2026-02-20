# YAWL v6.0 — 5-Minute Quickstart

> Get a YAWL workflow running in 5 minutes. No deep knowledge required. ⚡

## What is YAWL?

**YAWL** = Yet Another Workflow Language. A rigorous Petri net-based BPM engine for enterprise workflows.

In simple terms: Define workflows as diagrams → YAWL executes them with proven correctness.

```
┌─────────────────┐
│  Specification  │  (YAWL XML defining workflow)
│  (workflow.yawl)│
└────────┬────────┘
         │
         ↓
┌─────────────────┐
│  YAWL Engine    │  (Executes workflow rules)
│  (YEngine)      │
└────────┬────────┘
         │
         ↓
┌─────────────────┐
│  Work Items     │  (Tasks for humans/systems)
│  (to-do list)   │
└─────────────────┘
```

---

## Start Here: 5 Minute Tutorial

### Step 1: Create a Simple Workflow (1 min)

Create file `hello-workflow.yawl`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<specification xmlns="http://www.yawlfoundation.org/yawlschema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://www.yawlfoundation.org/yawlschema http://www.yawlfoundation.org/yawlschema/YAWL_Schema2.3.xsd" name="HelloWorkflow" version="1.0">
  <decomposition id="root" isRootNet="true">
    <processControlElements>
      <inputCondition id="InputCondition"/>
      <task id="SayHello">
        <name>Say Hello</name>
        <flowsInto>
          <nextElementRef id="OutputCondition"/>
        </flowsInto>
      </task>
      <outputCondition id="OutputCondition"/>
      <flow source="InputCondition" target="SayHello"/>
      <flow source="SayHello" target="OutputCondition"/>
    </processControlElements>
  </decomposition>
</specification>
```

### Step 2: Load and Execute (2 min)

```bash
# In Java code (or use REST API):
YStatelessEngine engine = YStatelessEngine.getInstance();
YSpecification spec = engine.getSpecification("hello-workflow.yawl");
YIdentifier caseID = engine.createCase(spec);

// Get work items (tasks waiting for execution)
Set<YWorkItem> workItems = engine.getWorkItems(caseID);
System.out.println("Work items: " + workItems.size()); // → 1 (SayHello task)

// Complete the task
for (YWorkItem item : workItems) {
    engine.completeWorkItem(item, null, null, null);
}

// Case now complete
System.out.println("Case complete!");
```

### Step 3: Verify (1 min)

```bash
# Check execution:
YCase caseData = engine.getCaseMonitor().getCase(caseID);
System.out.println("State: " + caseData.getState()); // → COMPLETE
System.out.println("Work items: " + caseData.getWorkItems().size()); // → 0
```

### Step 4: Add Another Task (1 min)

Edit `hello-workflow.yawl`, add second task after SayHello:

```xml
<task id="SayGoodbye">
  <name>Say Goodbye</name>
  <flowsInto>
    <nextElementRef id="OutputCondition"/>
  </flowsInto>
</task>

<!-- Update flows -->
<flow source="SayHello" target="SayGoodbye"/>
<flow source="SayGoodbye" target="OutputCondition"/>
```

Reload: `engine.getSpecification("hello-workflow.yawl")` (now has 2 tasks)

---

## Common Patterns

### Pattern 1: Sequential Tasks (A → B → C)

```xml
<flow source="TaskA" target="TaskB"/>
<flow source="TaskB" target="TaskC"/>
```

### Pattern 2: Parallel Tasks (A → [B, C] → D)

```xml
<task id="Split">
  <flowsInto>
    <nextElementRef id="TaskB"/>
    <nextElementRef id="TaskC"/>
  </flowsInto>
</task>
<task id="TaskB">
  <flowsInto>
    <nextElementRef id="Join"/>
  </flowsInto>
</task>
<task id="TaskC">
  <flowsInto>
    <nextElementRef id="Join"/>
  </flowsInto>
</task>
<condition id="Join"/>
<flow source="Split" target="TaskB"/>
<flow source="Split" target="TaskC"/>
<flow source="TaskB" target="Join"/>
<flow source="TaskC" target="Join"/>
```

### Pattern 3: Decision (IF condition THEN B ELSE C)

```xml
<task id="Decide">
  <flowsInto>
    <nextElementRef id="ThenTask"/>
    <nextElementRef id="ElseTask"/>
  </flowsInto>
  <!-- Add predicate conditions here -->
</task>
```

---

## Next Steps

| Goal | Read | Time |
|------|------|------|
| Understand architecture | `.claude/CLAUDE.md` | 10 min |
| Learn workflow patterns | `.claude/rules/engine/workflow-patterns.md` | 15 min |
| See full examples | `exampleSpecs/` directory | 20 min |
| Set up IDE | Project pom.xml + README.md | 10 min |
| Write real workflow | Tutorial in `docs/guides/` | 30 min |

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| "Specification not found" | Check file path and format (must be valid YAWL XML) |
| "Task never completes" | Check flows — task must have output flow to some condition |
| "Case stuck in limbo" | Check for cycles or missing flows from tasks |
| "No work items generated" | Check if task has correct flowsInto element |

---

## Key Concepts (2-minute summary)

| Concept | Means | Example |
|---------|-------|---------|
| **Specification** | Workflow definition (XML file) | `hello-workflow.yawl` |
| **Case** | Instance of workflow | `CaseID-001` |
| **Task** | Unit of work | `SayHello`, `ProcessPayment` |
| **Condition** | Decision/merge point | `Approved` (holds tokens) |
| **Work Item** | Task assigned to person/system | "User Bob: Please review" |
| **Token** | Marks position in workflow | Flows through tasks → conditions |

---

## Pro Tips ⚡

1. **Stateless execution** → Each run is independent (no persistent DB required)
2. **Composable workflows** → Embed sub-workflows via decompositions
3. **Multi-instance tasks** → Run same task for multiple data items
4. **Event listeners** → React to case/task events in real-time
5. **REST API** → Control workflows over HTTP (see integration docs)

---

**You now know enough to build basic workflows!** 🚀

For deep dives, see:
- `docs/v6/architecture/` — System design
- `.claude/rules/engine/` — Workflow semantics
- `exampleSpecs/` — Real-world patterns
- `README.md` — Build & deploy

---

Last updated: 2026-02-20
Questions? Check `.claude/.dx-cheatsheet.md` for quick answers.
