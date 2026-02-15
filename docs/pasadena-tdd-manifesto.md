# Pasadena TDD Manifesto

**Version:** 1.0
**Last Updated:** 2026-02-14

---

## 1. WHY: LLMs Cannot Do Traditional TDD

Test-Driven Development as practiced by humans comes in two major schools. Both require iterative human judgment that LLMs cannot replicate.

### 1.1 London School TDD (Mockist/Outside-In)

London TDD requires imagining interfaces that don't exist yet. The practitioner writes a test against a mock, then implements to satisfy the mock's contract.

**Why LLMs fail:**
- LLMs hallucinate plausible but wrong contracts
- Mock design requires understanding future behavior
- The feedback loop needs human judgment at each step
- Interface contracts drift from reality without human oversight

### 1.2 Chicago School TDD (Classicist/Inside-Out)

Chicago TDD builds from the domain outward, using real objects and state verification. The practitioner reasons about state transformations.

**Why LLMs fail:**
- Inside-out state reasoning requires maintaining mental model across iterations
- LLMs lose track of state mutations across multiple red-green-refactor cycles
- Refactoring decisions need human intuition about code smell
- The "simplest thing that works" is subjective and context-dependent

### 1.3 The Fundamental Problem

Red-green-refactor is not a mechanical process. Each step requires:

| Step | Human Activity | LLM Limitation |
|------|----------------|----------------|
| **Red** | Design the test interface | Hallucinates wrong contracts |
| **Green** | Choose simplest implementation | Cannot judge "simplest" |
| **Refactor** | Identify code smell | No aesthetic judgment |
| **Repeat** | Know when to stop | No stopping criterion |

---

## 2. WHAT: Pasadena TDD

**Definition:** Pasadena TDD is a testing methodology where tests are written AFTER working code exists, capturing VERIFIED behavior.

### 2.1 Core Principles

| Principle | Description |
|-----------|-------------|
| **Implementation First** | Write working code before tests |
| **Tests Document Reality** | Tests capture actual behavior, not desired behavior |
| **Regression Guards** | Tests protect against future breakage |
| **Manual Verification** | Human confirms correctness before test capture |

### 2.2 The Pasadena Contract

```
┌─────────────────────────────────────────────────────────────┐
│                    PASADENA CONTRACT                        │
├─────────────────────────────────────────────────────────────┤
│  A test is valid if and only if:                            │
│                                                             │
│  1. The implementation EXISTS and WORKS                     │
│  2. A human has VERIFIED the correct behavior               │
│  3. The test CAPTURES the verified behavior                 │
│  4. The test will FAIL if behavior changes                  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 2.3 What Pasadena TDD Is NOT

- **NOT** a design methodology (tests don't drive design)
- **NOT** a specification tool (tests don't specify requirements)
- **NOT** a replacement for manual verification
- **NOT** applicable when code doesn't exist yet

---

## 3. HOW: The Pasadena Workflow

### 3.1 The Four Steps

```
Step 1: IMPLEMENT     Write working implementation
           │
           v
Step 2: VERIFY        Manually confirm correctness
           │              (debugger, logging, inspection)
           v
Step 3: CAPTURE       Write test against verified behavior
           │
           v
Step 4: PROTECT       Test now guards against regression
```

### 3.2 YAWL Example: WorkItem Completion

**Step 1: Implement**

```java
// YWorkItemRunner.java - Implementation written first
public class YWorkItemRunner {

    private final YNetRunner netRunner;
    private final String workItemID;
    private YWorkItemStatus status;

    public YWorkItemRunner(YNetRunner netRunner, String workItemID) {
        this.netRunner = netRunner;
        this.workItemID = workItemID;
        this.status = YWorkItemStatus.enabled;
    }

    public synchronized YWorkItem complete(Map<String, Object> outputData)
            throws YStateException, YDataStateException {

        if (status != YWorkItemStatus.executing) {
            throw new YStateException(
                "Cannot complete work item in state: " + status
            );
        }

        if (outputData == null) {
            outputData = new HashMap<>();
        }

        // Validate output against task's output parameters
        YTask task = netRunner.getTask(workItemID);
        validateOutputData(task, outputData);

        // Update case data with output
        netRunner.updateCaseData(workItemID, outputData);

        // Transition state
        status = YWorkItemStatus.complete;

        // Trigger net runner to check enabling conditions
        netRunner.notifyWorkItemCompletion(this);

        return getWorkItem();
    }

    private void validateOutputData(YTask task, Map<String, Object> data)
            throws YDataStateException {
        // Implementation validates against XSD typing
        for (YParameter param : task.getOutputParams()) {
            if (param.isRequired() && !data.containsKey(param.getName())) {
                throw new YDataStateException(
                    "Missing required output parameter: " + param.getName()
                );
            }
        }
    }
}
```

**Step 2: Verify**

Manual verification through inspection and debugging:

```bash
# Start YAWL engine with debug logging
export YAWL_LOG_LEVEL=DEBUG
./catalina.sh run

# In another terminal, launch a test case
curl -X POST http://localhost:8080/ib/api/cases \
  -H "Content-Type: application/json" \
  -d '{"specIdentifier": "SimpleApproval", "specVersion": "1.0"}'

# Check logs for work item state transitions
# Verify in database:
psql -d yawl -c "SELECT id, status FROM workitems WHERE case_id = '...'"
```

Debugger inspection confirms:
- State transition: `executing` -> `complete`
- Output data merged into case data
- Exception thrown for invalid state

**Step 3: Capture**

```java
// YWorkItemRunnerTest.java - Test captures verified behavior
public class YWorkItemRunnerTest {

    private YNetRunner mockNetRunner;
    private YWorkItemRunner runner;
    private static final String WORK_ITEM_ID = "task_001";

    @Before
    public void setUp() {
        mockNetRunner = mock(YNetRunner.class);
        runner = new YWorkItemRunner(mockNetRunner, WORK_ITEM_ID);
    }

    @Test
    public void testComplete_WhenExecuting_TransitionsToComplete()
            throws YStateException, YDataStateException {

        // Arrange: Set up verified preconditions
        when(mockNetRunner.getTask(WORK_ITEM_ID))
            .thenReturn(createTaskWithNoRequiredOutputs());
        runner.setStatus(YWorkItemStatus.executing);

        Map<String, Object> outputData = new HashMap<>();
        outputData.put("approved", true);

        // Act: Execute the verified behavior
        YWorkItem result = runner.complete(outputData);

        // Assert: Capture the verified outcomes
        assertEquals(YWorkItemStatus.complete, result.getStatus());
        verify(mockNetRunner).updateCaseData(WORK_ITEM_ID, outputData);
        verify(mockNetRunner).notifyWorkItemCompletion(runner);
    }

    @Test(expected = YStateException.class)
    public void testComplete_WhenNotExecuting_ThrowsStateException()
            throws YStateException, YDataStateException {

        // Arrange: Verified precondition - wrong state
        runner.setStatus(YWorkItemStatus.enabled);  // NOT executing

        // Act: Should throw (verified behavior)
        runner.complete(new HashMap<>());
    }

    @Test(expected = YDataStateException.class)
    public void testComplete_WhenMissingRequiredOutput_ThrowsDataStateException()
            throws YStateException, YDataStateException {

        // Arrange: Task requires 'approved' output
        YTask task = createTaskWithRequiredOutput("approved", Boolean.class);
        when(mockNetRunner.getTask(WORK_ITEM_ID)).thenReturn(task);
        runner.setStatus(YWorkItemStatus.executing);

        // Act: Empty output should throw (verified behavior)
        runner.complete(new HashMap<>());
    }
}
```

**Step 4: Protect**

The tests now guard against regression. Future changes that break these behaviors will fail the test suite.

```bash
# Run tests to verify capture
mvn test -Dtest=YWorkItemRunnerTest

# T E S T S
# -------------------------------------------------------
# Running org.yawlfoundation.yawl.engine.YWorkItemRunnerTest
# Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
```

---

## 4. Comparison: TDD Schools

| Aspect | London TDD | Chicago TDD | Pasadena TDD |
|--------|-----------|-------------|--------------|
| **Test timing** | Before code | Before code | After working code |
| **Design driver** | Test drives interface | Test drives implementation | Implementation drives test |
| **Mock usage** | Heavy | Light/None | Captured behavior |
| **State verification** | Behavior only | Real state | Verified state |
| **LLM compatible** | No | No | **Yes** |
| **Human judgment** | Every iteration | Every iteration | Once per feature |
| **Regression value** | High | High | High |
| **Design value** | High | Medium | Low |

### 4.1 When to Use Pasadena TDD

| Scenario | Recommended |
|----------|-------------|
| LLM-assisted development | **Pasadena** |
| Greenfield with unclear requirements | London or Chicago |
| Adding tests to legacy code | **Pasadena** |
| Exploratory prototyping | **Pasadena** |
| Team with strong TDD discipline | London or Chicago |
| Rapid iteration with changing requirements | **Pasadena** |

---

## 5. Pasadena TDD and LLMs

### 5.1 Why Pasadena Works With LLMs

```
┌──────────────────────────────────────────────────────────────┐
│                  LLM + PASADENA FLOW                         │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│   Human: "Implement work item completion"                    │
│      │                                                       │
│      v                                                       │
│   LLM: [Generates implementation]                            │
│      │                                                       │
│      v                                                       │
│   Human: [Verifies correctness manually]                     │
│      │                                                       │
│      v                                                       │
│   Human: "Write tests for the verified behavior"             │
│      │                                                       │
│      v                                                       │
│   LLM: [Generates tests capturing behavior]                  │
│      │                                                       │
│      v                                                       │
│   Human: [Reviews and confirms tests]                        │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 5.2 The Human-LLM Contract

| Task | Human | LLM |
|------|-------|-----|
| Design | Yes | No |
| Implement | No | Yes |
| Verify | Yes | No |
| Test Authoring | No | Yes |
| Test Review | Yes | No |

### 5.3 Anti-Patterns

**WRONG: LLM-Driven Red-Green-Refactor**

```
❌ Human: "Write a test for X, then implement, then refactor"
   LLM: [Generates test against non-existent code]
   LLM: [Implements to pass test]
   LLM: [Refactors based on... what?]
   Result: Untested behavior, drifted contracts
```

**CORRECT: Pasadena Flow**

```
✓ Human: "Implement X"
   LLM: [Generates implementation]
   Human: [Verifies correctness]
   Human: "Write tests capturing this behavior"
   LLM: [Generates tests against verified implementation]
   Result: Tests guard actual, working behavior
```

---

## 6. Adoption Guide

### 6.1 For Teams

1. **Acknowledge the reality**: If using LLMs, traditional TDD is impractical
2. **Emphasize manual verification**: Human must confirm correctness
3. **Review generated tests**: LLM tests may miss edge cases
4. **Maintain test discipline**: Tests still need to be comprehensive

### 6.2 For Code Review

When reviewing Pasadena TDD code:

- [ ] Implementation works (manual verification shown)
- [ ] Tests cover the happy path
- [ ] Tests cover error conditions
- [ ] Tests would fail if behavior changes
- [ ] No untested public methods

### 6.3 Metrics

| Metric | Target | Rationale |
|--------|--------|-----------|
| Line Coverage | 80%+ | Capture key behaviors |
| Branch Coverage | 70%+ | Cover conditionals |
| Mutation Score | 60%+ | Tests catch real bugs |

---

## 7. Theoretical Foundation

### 7.1 Pasadena TDD Axioms

1. **Axiom of Implementation Priority**: Tests can only verify what exists
2. **Axiom of Verified Capture**: Tests must capture verified behavior
3. **Axiom of LLM Incompatibility**: LLMs cannot perform iterative judgment

### 7.2 Formal Definition

Given:
- `I` = Implementation
- `T` = Test suite
- `V` = Manual verification function
- `B` = Behavior

Pasadena TDD requires:

```
∀ t ∈ T: ∃ i ∈ I : V(i) = true ∧ t captures behavior(i)
```

For all tests t, there exists an implementation i such that i is verified and t captures i's behavior.

### 7.3 Relationship to Other Methodologies

```
                    ┌─────────────────┐
                    │   Traditional   │
                    │      TDD        │
                    └────────┬────────┘
                             │
              ┌──────────────┴──────────────┐
              │                             │
    ┌─────────┴─────────┐       ┌──────────┴──────────┐
    │    London TDD     │       │    Chicago TDD      │
    │  (Mockist)        │       │  (Classicist)       │
    │  Outside-In       │       │  Inside-Out         │
    └───────────────────┘       └─────────────────────┘
              │                             │
              └──────────────┬──────────────┘
                             │
                    ┌────────┴────────┐
                    │  LLM-Assisted   │
                    │  Development    │
                    └────────┬────────┘
                             │
                    ┌────────┴────────┐
                    │  PASADENA TDD   │
                    │  (This method)  │
                    └─────────────────┘
```

---

## 8. FAQ

### Q: Isn't this just "test after"?

**A:** Yes, but with a critical difference: mandatory manual verification. "Test after" without verification produces tests that verify nothing. Pasadena requires human verification before test capture.

### Q: What about test-first for new features?

**A:** If you can do test-first effectively, do it. Pasadena is for when test-first is impractical (LLM assistance, legacy code, rapid prototyping).

### Q: Does this reduce code quality?

**A:** No. The same quality bar applies. Tests still must be comprehensive. The difference is when tests are written, not what they verify.

### Q: Can LLMs write the verification step?

**A:** No. Verification requires human judgment. LLMs cannot determine if behavior is "correct" - they can only observe what the code does.

---

## 9. Summary

```
┌─────────────────────────────────────────────────────────────┐
│                 PASADENA TDD AT A GLANCE                    │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  TRADITIONAL TDD:     Test → Code → Refactor (Human loop)   │
│  PASADENA TDD:        Code → Verify → Test (LLM + Human)    │
│                                                             │
│  PRINCIPLE:           Tests capture verified reality,       │
│                       not speculative design.               │
│                                                             │
│  FOR:                 LLM-assisted development,             │
│                       legacy code testing,                  │
│                       rapid prototyping.                    │
│                                                             │
│  NOT FOR:             Greenfield with strong TDD culture,   │
│                       design-first development.             │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 10. References

- Beck, K. (2002). *Test-Driven Development: By Example*. Addison-Wesley.
- Freeman, S., & Pryce, N. (2009). *Growing Object-Oriented Software, Guided by Tests*. Addison-Wesley.
- Fowler, M. (2018). [Mockist vs Classicist](https://martinfowler.com/articles/mocksArentStubs.html)
- YAWL Foundation. (2026). [YAWL Documentation](https://yawlfoundation.github.io)
