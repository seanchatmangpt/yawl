# SAFe Agent Testing Patterns & Architecture

## Executive Summary

This document describes the testing architecture for SAFe agent simulation in YAWL v6.0 using **Chicago TDD** (Detroit School) principles.

## Core Philosophy

### Detroit School (Chicago TDD)
- Test **complete workflows** end-to-end, not isolated units
- Use **real implementations**, not mocks or stubs
- Test **behavior**, not implementation details
- Verify **state changes** through assertions
- Test **integration points** between components

### No Mocks, Only Reality
```
DON'T: mock(ProductOwnerAgent).acceptStory(story)
DO:    executor.executeStoryCompletionFlow(data, dev, arch, po)
       assertTrue(result.poAccepted())
```

## Test Structure

### Arrange-Act-Assert Pattern

Every test follows the same 3-phase structure:

```java
@Test
void testCeremonyScenario() {
    // ========== ARRANGE ==========
    // Setup test data, agents, workflow
    SAFeCeremonyExecutor.AgentParticipant po =
        new SAFeCeremonyExecutor.AgentParticipant("po-1", "PO", "PO");
    UserStory story = new UserStory(...);
    SAFeCeremonyData.StoryCompletionData data =
        new SAFeCeremonyData.StoryCompletionData(story, ...);

    // ========== ACT ==========
    // Execute the real workflow
    SAFeCeremonyData.StoryCompletionResult result =
        executor.executeStoryCompletionFlow(data, dev, arch, po);

    // ========== ASSERT ==========
    // Verify behavior and state changes
    assertTrue(result.poAccepted(), "PO should accept story");
    assertEquals("done", result.finalStatus());
}
```

### Why This Works
1. **Clear Intent**: Each phase has a single purpose
2. **Testability**: State changes visible through assertions
3. **Maintainability**: Easy to understand test flow
4. **Debuggability**: Failures point to exact phase

## Ceremony Test Patterns

### Pattern 1: Multi-Agent Coordination

**Used for**: PI Planning, Sprint Planning, Retrospectives

```
Setup:
  1. Create agents for each role
  2. Create ceremony input data
  3. Record agent IDs for later verification

Execute:
  1. Call executor.executeCeremony(data, agents)
  2. Each agent participates via message passing

Assert:
  1. All agents participatedInCeremony()
  2. Messages follow expected ordering
  3. Output contains all expected decisions
```

Example: testPIPlanningCeremony()
```java
// 5 agents coordinate
List<AgentParticipant> agents = List.of(rte, arch, po, sm, dev);
SAFeCeremonyData.PIPlanning result =
    executor.executePIPlanningCeremony(piData, agents);

// All participate
agents.forEach(agent ->
    assertTrue(agent.participatedInCeremony())
);

// Dependencies registered
assertTrue(result.registeredDependencies().size() > 0);
```

### Pattern 2: Sequential Workflow

**Used for**: Story Completion Flow

```
Setup:
  Story A depends on Story B
  Story B is already completed

Execute:
  1. Developer marks story complete
  2. Architect reviews dependencies
  3. PO reviews acceptance criteria
  4. System updates story state

Assert:
  State transitions: ready → (arch) → ready-for-po → (po) → done
  Dependencies checked before PO review
  All acceptance criteria evaluated
```

Example: testStoryCompletionFlow()
```java
// B is done, A depends on B
UserStory storyA = new UserStory(..., List.of("story-b"), ...);
UserStory storyB = new UserStory(..., "done", ...);

// Execute workflow
result = executor.executeStoryCompletionFlow(data, dev, arch, po);

// Verify state transitions
assertTrue(result.architectReviewedDependencies());
assertTrue(result.noDependencyBlockers());  // B is done
assertEquals("done", result.finalStatus());  // PO accepted
```

### Pattern 3: Concurrent Execution

**Used for**: Race condition testing

```
Setup:
  10 independent stories (no dependencies)

Execute:
  parallelize:
    for each story:
      executeStoryCompletionFlow(story, dev, arch, po)

Assert:
  All stories reach "done" state
  No race conditions in acceptance
  Completion times are monotonic (no temporal reversals)
```

Example: testConcurrentStoryCompletions()
```java
// 10 independent stories
List<UserStory> stories = IntStream.range(0, 10)
    .mapToObj(i -> new UserStory(..., List.of(), ...))
    .toList();

// Execute in parallel
List<Result> results = stories.parallelStream()
    .map(story -> executor.executeStoryCompletionFlow(...))
    .toList();

// All should complete
assertEquals(10, results.size());
results.forEach(r -> assertEquals("done", r.finalStatus()));
```

### Pattern 4: Decision Consistency

**Used for**: Agent role verification

```
Setup:
  Same agent (e.g., PO) evaluates 3 identical stories

Execute:
  for each story:
    execute story completion
    record PO decision

Assert:
  All 3 decisions are identical
  Agent applies consistent business rules
```

Example: testPODecisionConsistency()
```java
List<Boolean> decisions = new ArrayList<>();

// Same PO evaluates 3 identical stories
for (int i = 0; i < 3; i++) {
    UserStory story = new UserStory(...,
        List.of("C1", "C2"), ...);
    // All criteria met for all stories
    List<Boolean> evals = List.of(true, true);

    result = executor.executeStoryCompletionFlow(
        new StoryCompletionData(story, List.of(), evals, ...),
        dev, arch, po
    );

    decisions.add(result.poAccepted());
}

// All decisions consistent
assertTrue(decisions.stream().allMatch(Boolean::booleanValue));
```

## Message Ordering Patterns

### Ordering Assertion Pattern

For ceremonies with expected message sequences:

```java
// Execute ceremony
executor.executePIPlanningCeremony(data, agents);

// Extract message sequence
List<String> sequence = agents.stream()
    .map(AgentParticipant::lastMessageType)
    .filter(Objects::nonNull)
    .toList();

// Verify ordering
int announcementIdx = sequence.indexOf("ANNOUNCEMENT");
int roadmapIdx = sequence.indexOf("ROADMAP");
int commitmentIdx = sequence.indexOf("COMMITMENT");

// ANNOUNCEMENT must come before ROADMAP
assertTrue(announcementIdx < roadmapIdx,
    "ANNOUNCEMENT should precede ROADMAP");

// ROADMAP must come before COMMITMENT
assertTrue(roadmapIdx < commitmentIdx,
    "ROADMAP should precede COMMITMENT");
```

## Dependency Validation Patterns

### Circular Dependency Detection

```java
// Build dependency graph and detect cycles
boolean hasCycle = hasCyclicDependency("story-a", allStories);
assertTrue(hasCycle, "Should detect circular dependency");
```

**Algorithm**: Depth-First Search (DFS)
```java
private boolean hasCyclicDependency(String storyId,
                                   List<UserStory> allStories,
                                   Set<String> visited) {
    if (visited.contains(storyId)) {
        return true;  // Cycle detected
    }

    visited.add(storyId);

    // Recursively check dependencies
    for (String dep : findStory(storyId).dependsOn()) {
        if (hasCyclicDependency(dep, allStories,
                               new HashSet<>(visited))) {
            return true;
        }
    }

    return false;
}
```

### Blocking Dependency Detection

```java
// Story A is blocked if any dependency not completed
List<String> completedStories = List.of("s1", "s2");
boolean isBlocked = story.isBlocked(completedStories);

// Story A depends on S2 (completed), S3 (not completed)
assertTrue(isBlocked, "Story is blocked on S3");
```

## State Machine Testing

### Story State Transitions

```
Expected sequence:
    backlog
      ↓
    in-progress (developer working)
      ↓
    ready-for-review (developer finished)
      ↓
    ready-for-po-review (architect approved)
      ↓
    done (PO accepted)
```

**Test verification**:
```java
List<String> stateTransitions = new ArrayList<>();

// Track state changes
stateTransitions.add(story.status());  // "backlog"

// Developer marks ready
story = story.withStatus("ready-for-review");
stateTransitions.add(story.status());

// Architect reviews
result = executor.executeStoryCompletionFlow(...);
stateTransitions.add(result.storyStatus());  // ready-for-po-review

// PO accepts
stateTransitions.add(result.finalStatus());  // done

// Verify sequence
assertEquals(List.of("backlog", "ready-for-review",
                    "ready-for-po-review", "done"),
             stateTransitions);
```

## Parametrized Testing Pattern

**When**: Testing the same logic with different inputs

```java
@ParameterizedTest(name = "Team size: {0} teams, {1} members")
@CsvSource({
    "1, 5",   // Small team
    "3, 5",   // 3 agile teams
    "5, 4",   // Large release train
})
void testPIPlanningWithVaryingTeamSizes(int teamCount,
                                        int membersPerTeam) {
    // Create teams dynamically
    List<AgentParticipant> agents = new ArrayList<>();

    for (int i = 0; i < teamCount; i++) {
        agents.add(new AgentParticipant("sm-" + i, "SM", "SM"));

        for (int j = 0; j < membersPerTeam; j++) {
            agents.add(new AgentParticipant(
                "dev-" + i + "-" + j, "Dev", "DEV"));
        }
    }

    // Execute with team configuration
    SAFeCeremonyData.PIPlanning result =
        executor.executePIPlanningCeremony(piData, agents);

    // Verify scales correctly
    assertEquals(teamCount, result.teamCommitments().size());
    assertTrue(result.plannedStories().size() > 0);
}
```

## Edge Case Testing Patterns

### Boundary Value Analysis

```java
// Capacity exactly at limit
int capacity = 40;
List<UserStory> stories = List.of(
    storyWithPoints(20),
    storyWithPoints(20)  // Total: exactly 40
);

SAFeCeremonyData.SprintPlanning result =
    executor.executeSprintPlanningCeremony(
        planData, po, sm, devs, capacity);

assertEquals(40, totalPoints(result.committedStories()));

// Capacity exceeded
stories = List.of(
    storyWithPoints(25),
    storyWithPoints(25)  // Total: 50, exceeds 40
);

result = executor.executeSprintPlanningCeremony(...);

assertTrue(totalPoints(result.committedStories()) <= capacity,
    "Should enforce capacity constraint");
```

### Error Path Testing

```java
// Partial criteria met (3 of 5)
List<Boolean> evaluations = List.of(true, false, true, false, true);

SAFeCeremonyData.StoryCompletionResult result =
    executor.executeStoryCompletionFlow(
        new StoryCompletionData(story, List.of(),
                               evaluations, ...),
        dev, arch, po);

// Story rejected
assertFalse(result.poAccepted(),
    "Story should be rejected (3/5 criteria)");
assertEquals("in-progress", result.finalStatus(),
    "Rejected story returns to in-progress");
```

## Assertion Best Practices

### 1. Descriptive Messages
```java
// BAD
assertTrue(agent.participatedInCeremony());

// GOOD
assertTrue(agent.participatedInCeremony(),
    "Agent " + agent.name() + " (" + agent.role() +
    ") should participate in " + ceremonyName);
```

### 2. State-Based Assertions
```java
// Test observable state, not implementation
assertTrue(result.poAccepted(), "Story accepted by PO");
assertEquals("done", result.finalStatus(),
    "Story status after PO acceptance");

// NOT internal calls
verify(po).acceptStory(story);  // DON'T DO THIS
```

### 3. Complete Assertion Sets
```java
// Verify all relevant state changes
assertTrue(result.architectReviewedDependencies());
assertTrue(result.noDependencyBlockers());
assertEquals("ready-for-po-review", result.storyStatus());
assertTrue(result.poAccepted());
assertEquals("done", result.finalStatus());
assertNotNull(result.acceptanceCriteriaEvaluation());
```

## Performance Considerations

### Timeout Settings
- Most tests: 15-20 seconds
- Concurrent tests: 30-60 seconds
- Retrospective calculation: 20 seconds

```java
@Test
@Timeout(value = 60, unit = TimeUnit.SECONDS)
void testConcurrentScenario() {
    // Parallel execution of 10 stories
}
```

### Parallelization
```bash
# Run tests in parallel (8 threads)
mvn -T 1.5C test -pl yawl-engine

# Each test runs independently with fresh DB
# No shared state between parallel tests
```

## Common Pitfalls & Solutions

### Pitfall 1: Test Order Dependency
**Problem**: Test A fails after Test B
```java
// BAD: Tests depend on each other
@Test void testA() { setupGlobalState(); }
@Test void testB() { assertGlobalState(); }  // Depends on A

// GOOD: Each test is independent
@BeforeEach void setUp() { createFreshState(); }
@Test void testA() { /* independent */ }
@Test void testB() { /* independent */ }
```

### Pitfall 2: Hidden Assertions
**Problem**: Test passes but condition not verified
```java
// BAD: Assumes condition without verifying
executor.executeCeremony(data, agents);
// Did agents participate? Unknown!

// GOOD: Explicit assertions
executor.executeCeremony(data, agents);
agents.forEach(agent ->
    assertTrue(agent.participatedInCeremony(),
        "Agent must participate")
);
```

### Pitfall 3: Flaky Timing Tests
**Problem**: Test fails sporadically due to timing
```java
// BAD: Relies on thread timing
Thread.sleep(100);
assertTrue(result.isReady());  // May fail if slower

// GOOD: Wait for actual state
int retries = 10;
while (!result.isReady() && retries-- > 0) {
    Thread.sleep(100);
}
assertTrue(result.isReady(), "Should reach ready state");
```

## Test Maintenance Guide

### Adding New Test
1. Choose appropriate test class (Simulation, EdgeCases, or Capability)
2. Use clear naming: `test<Behavior><Condition>` or `test<Ceremony>`
3. Follow Arrange-Act-Assert structure
4. Add descriptive assertion messages
5. Set appropriate timeout
6. Document test intent in comment

### Updating Existing Test
1. Preserve test's primary assertion
2. Update Arrange section if data changes
3. Update expected behavior in Assert
4. Maintain backward compatibility where possible

### Removing Test
1. Check if test is referenced elsewhere
2. Update related documentation
3. Consider if functionality should be preserved
4. Archive removed tests in git history

## Related Documentation

- `/home/user/yawl/src/test/java/org/yawlfoundation/yawl/safe/agents/README.md` - Test suite overview
- `/home/user/yawl/.claude/rules/TEAMS-GUIDE.md` - Team decision framework
- `/home/user/yawl/CLAUDE.md` - Root axioms and philosophy

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-02-28 | Initial testing patterns document |

---
**Last Updated**: 2026-02-28
**Maintained By**: YAWL Foundation Test Team
**License**: GNU LGPL v2.1+
