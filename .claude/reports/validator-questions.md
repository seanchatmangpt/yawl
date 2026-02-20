# Questions for Validator Teammate

**From**: ENGINEER (Validation Team - Audit Session)
**To**: VALIDATOR (Code Review & Semantics)
**Date**: 2026-02-20
**Hook Under Review**: `/home/user/yawl/.claude/hooks/team-recommendation.sh`

This document contains 5 critical questions that require domain expertise to answer. The hook's correctness depends on your decisions.

---

## Q1: "workflow" Keyword Disambiguation

### Background
The hook's engine quantum pattern includes the word "workflow":
```bash
# Line 29:
if [[ $desc_lower =~ (engine|ynetrunner|workflow|deadlock|state|task.completion) ]]; then
```

This causes a false positive on schema-only tasks that mention "workflow":
- Input: "Update workflow type definition in XSD"
- Expected: Schema quantum only (N=1)
- Actual: Engine + Schema (N=2) → incorrectly recommends team

### The Problem
"workflow" is ambiguous:
- **Engine context**: "Fix workflow execution deadlock" (engine-specific)
- **Schema context**: "Update workflow type definition" (schema-specific)
- **Generic**: "Improve workflow reliability" (could be either)

### Test Results
```
FAILED: "Update workflow type definition in XSD"
  Expected: 1 quantum (schema) → exit 2
  Actual:   2 quantums (engine+schema) → exit 0 (team recommended)

Output shows both detected:
  ◆ Engine Semantic (yawl/engine/**)
  ◆ Schema Definition (schema/**, exampleSpecs/**)
```

### Your Decision Required

**Option A: Remove "workflow" from engine pattern**
- Fix: Line 29 becomes `(engine|ynetrunner|deadlock|state|execution|task.completion)`
- Pro: Eliminates false positive on schema-only tasks
- Con: May miss legitimate engine tasks that say "workflow execution"
- Implementation: 1-line change

**Option B: Keep "workflow" but add context-aware matching**
- Fix: Line 29 becomes `(engine|ynetrunner|deadlock|state|execution|task.completion|workflow.execution|workflow.engine)`
- Pro: Catches legitimate engine+workflow tasks
- Con: Still matches generic "improve workflow" tasks ambiguously
- Implementation: More complex regex

**Option C: Separate "workflow" into its own quantum**
- Fix: Add new quantum category just for workflow-related tasks
- Pro: Explicit categorization
- Con: May not align with current YAWL module taxonomy
- Implementation: Requires changes to multiple patterns

### Validation Checklist
Please provide:
1. Which option best reflects YAWL's task semantics?
2. Example engine tasks involving "workflow" (if any)
3. Example schema tasks involving "workflow" (if any)
4. Preferred exit behavior: strict (reject ambiguous) or permissive (allow ambiguous)?

---

## Q2: Resourcing Keyword Canonicalization

### Background
The hook's resourcing quantum pattern doesn't match the word "resourcing":
```bash
# Line 44:
if [[ $desc_lower =~ (resource|allocation|pool|workqueue|queue) ]]; then
```

This causes test failures for N ≥ 6 boundary conditions:
- Input: "Fix engine, modify schema, add integration, improve resourcing, write tests, and add security"
- Expected: 6 quantums (because "resourcing" is present)
- Actual: 5 quantums (resourcing not matched)

### The Problem
"resourcing" (gerund/noun form) ≠ "resource" (pattern match) in regex:
```bash
[[ "improve resourcing" =~ (resource|allocation|pool|workqueue|queue) ]]
# Result: NO MATCH (because "resourc" is not in the list)

[[ "improve resource allocation" =~ (resource|allocation|pool|workqueue|queue) ]]
# Result: MATCH (because "resource" is in the list)
```

### Test Results
```
FAILED: Task with 6 quantums
  Input: "Fix engine, modify schema, add integration, improve resourcing, write tests, and add security"
  Expected: 6 quantums → exit 2 (too many, reject)
  Actual:   5 quantums → exit 0 (team recommended)

Pattern analysis:
  ✓ Engine matched ("engine")
  ✓ Schema matched ("schema")
  ✓ Integration matched ("integration")
  ✗ Resourcing NOT matched ("resourcing" not in pattern)
  ✓ Testing matched ("tests")
  ✓ Security matched ("security")
  ✗ Stateless not matched (N/A)
```

### Your Decision Required

**Option A: Add "resourc" prefix to pattern**
- Fix: Line 44 becomes `(resource|resourc|allocation|pool|workqueue|queue)`
- Pro: Catches "resourcing", "resource", "resources", "resourceful"
- Con: Loose matching (could catch unrelated words)
- Implementation: 1 keyword addition

**Option B: Add full "resourcing" word**
- Fix: Line 44 becomes `(resource|resourcing|allocation|pool|workqueue|queue)`
- Pro: Exact match for canonical form
- Con: Misses other forms like "resource scheduling"
- Implementation: 1 keyword addition

**Option C: Expand to all variants**
- Fix: Line 44 becomes `(resource|resourc|allocation|pool|workqueue|queue|assign|schedule)`
- Pro: Comprehensive coverage
- Con: Potential false positives from "assign"
- Implementation: Multiple keyword additions

### Validation Checklist
Please provide:
1. What are the canonical resourcing module keywords (from yawl/resourcing/**)?
2. Do tasks use "resourcing" or always "resource allocation"?
3. Should "resource scheduling" also trigger resourcing quantum?
4. Are there other common resourcing keywords missed?

---

## Q3: No-Quantums Exit Code Semantics

### Background
The hook returns exit 0 for two different scenarios:
```bash
# Scenario 1 (Line 15-16): No task provided
if [[ -z "$TASK_DESCRIPTION" ]]; then
    exit 0  # ← OK: no input, proceed normally
fi

# Scenario 2 (Line 100-104): No quantums detected but task provided
else
    echo -e "${YELLOW}ℹ️  Could not detect clear quantums. Analyze manually.${NC}"
    exit 0  # ← PROBLEM: ambiguous task, should this be error?
fi
```

This causes test failure:
- Input: "Analyze performance metrics"
- Expected: No quantums detected + non-empty input → error (exit 2)
- Actual: No quantums detected → success (exit 0)

### The Problem
**Semantically different conditions have same exit code:**

| Input | Meaning | Current Exit | Should Exit | Reason |
|-------|---------|--------------|-------------|--------|
| `""` | No task provided | 0 | 0 | Proceed; no work to do |
| `"Fix engine"` | Single quantum detected | 2 | 2 | Use single session; don't team |
| `"Analyze performance"` | No quantums detected | 0 | ? | Task is **ambiguous** |

### Per CLAUDE.md Stop Conditions
> "Uncertain which rule applies → **stop and re-read this stack**"
> "If uncertain → **STOP. Read facts. Re-run observatory."**

**Question**: Does "Could not detect clear quantums" violate the STOP condition?

### Test Results
```
FAILED: "Analyze performance metrics"
  Expected: No quantums + non-empty input → exit 2
  Actual:   No quantums → exit 0

The hook silently passes without error, same as if no input was provided.
```

### Your Decision Required

**Option A: Treat as error (exit 2)**
```bash
# Line 103 change:
else
    echo -e "${YELLOW}⚠️  Could not detect clear quantums. Provide task with specific quantum keywords.${NC}"
    exit 2  # ← Signal to user: rephrase task
fi
```
- Pro: Forces clarification; aligns with STOP condition
- Con: May be too strict for exploratory tasks
- Behavior: User must rephrase task to get through hook

**Option B: Keep as informational (exit 0)**
```bash
else
    echo -e "${YELLOW}ℹ️  Could not detect clear quantums. Manual analysis recommended.${NC}"
    exit 0  # ← Allow to proceed; user decides manually
fi
```
- Pro: Flexible; doesn't block vague tasks
- Con: Ambiguous tasks pass through without error signal
- Behavior: User can proceed, but no team is recommended

**Option C: Add conditional based on input length**
```bash
else
    if [[ -z "$TASK_DESCRIPTION" || ${#TASK_DESCRIPTION} -lt 20 ]]; then
        exit 0  # Empty or very short input
    else
        exit 2  # Non-empty, substantial task but no quantums
    fi
fi
```
- Pro: Distinguishes between empty and vague
- Con: Arbitrary length threshold
- Behavior: Short inputs pass; long vague inputs fail

### Validation Checklist
Please provide:
1. Does "Could not detect clear quantums" violate GODSPEED STOP conditions?
2. Should ambiguous tasks block (exit 2) or warn (exit 0)?
3. What's the expected user behavior on no-quantum detection?
4. Should hook ever refuse to make a recommendation (exit 2)?

---

## Q4: Prerequisite Validation Timing

### Background
The hook displays prerequisites but doesn't validate them:
```bash
# Lines 83-85 (informational only):
echo -e "${CYAN}Prerequisites:${NC}"
echo -e "  • ${YELLOW}CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=1${NC}"
echo -e "  • ${YELLOW}bash scripts/observatory/observatory.sh${NC}"
```

The hook should verify:
1. Environment variable `CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=1` is set
2. Observable facts files exist (`.claude/facts/shared-src.json`, `.claude/facts/reactor.json`)
3. Facts are fresh (not stale based on timestamp)

### The Problem
If a user gets a "TEAM MODE RECOMMENDED" message but:
- Hasn't set `CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=1` → teams fail later
- Hasn't run `bash scripts/observatory/observatory.sh` → facts missing
- Facts are stale → file conflicts undetected

The hook gives false confidence without validating preconditions.

### Your Decision Required

**Option A: Validate prerequisites before recommending (strict)**
```bash
# Add after line 72 (when recommending team):
if [[ -z "$CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS" ]]; then
    echo -e "${RED}✗ Set: export CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=1${NC}"
    exit 1  # Fail immediately
fi

if [[ ! -f ".claude/facts/shared-src.json" ]]; then
    echo -e "${RED}✗ Run: bash scripts/observatory/observatory.sh${NC}"
    exit 1  # Fail immediately
fi
```
- Pro: Prevents team formation without setup
- Con: Fails if env var not already set
- Behavior: User must set up before team recommendation becomes actionable

**Option B: Warn but allow (permissive)**
```bash
# Add after line 82 (in info section):
if [[ -z "$CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS" ]]; then
    echo -e "${YELLOW}⚠️  Env var not set: export CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=1${NC}"
fi

if [[ ! -f ".claude/facts/shared-src.json" ]]; then
    echo -e "${YELLOW}⚠️  Facts missing. Run: bash scripts/observatory/observatory.sh${NC}"
fi
```
- Pro: Informs without blocking
- Con: Doesn't enforce setup
- Behavior: Team recommended even if prerequisites missing

**Option C: Validate in PreToolUse gate (deferred)**
- Fix: Move validation out of this hook into `.claude/hooks/hyper-validate.sh` or session startup
- Pro: Centralized prerequisite checking
- Con: This hook becomes insufficient
- Behavior: Hook recommends; outer gate validates

### Validation Checklist
Please provide:
1. Should prerequisites be **enforced** or just **suggested**?
2. When should env var validation happen (hook or session startup)?
3. Should facts freshness be validated (timestamp-based)?
4. If facts are missing, should team mode be rejected (exit 1)?

---

## Q5: Pattern Regex Fragility & Word Boundaries

### Background
The hook uses `.` (regex wildcard) to match word separators in patterns:
```bash
# Line 29: task.completion matches task[ANY_CHAR]completion
if [[ $desc_lower =~ (task.completion) ]]; then

# Line 34: type.definition matches type[ANY_CHAR]definition
if [[ $desc_lower =~ (type.definition) ]]; then

# Line 49: integration.test matches integration[ANY_CHAR]test
if [[ $desc_lower =~ (integration.test) ]]; then

# Line 59: case.data matches case[ANY_CHAR]data
if [[ $desc_lower =~ (case.data) ]]; then
```

### The Problem
In bash regex, `.` matches ANY character, not just dots:
```bash
# This pattern:
[[ "taskXcompletion" =~ (task.completion) ]]  # MATCHES (unintended!)
[[ "task-completion" =~ (task.completion) ]]  # MATCHES (intended)
[[ "task completion" =~ (task.completion) ]]  # MATCHES (intended)

# Should really be:
[[ "task-completion" =~ (task[[:space:]-]completion) ]]  # Only task[space or hyphen]completion
```

### Current Behavior (Works by Accident)
The patterns work because:
- `.` matches spaces, hyphens, and dots in real-world inputs
- No adversarial inputs exist to break it
- Test coverage doesn't include edge cases like "taskXcompletion"

### Your Decision Required

**Option A: Keep loose matching (status quo)**
```bash
# No change: (task.completion) continues to work
```
- Pro: Works; no changes needed
- Con: Semantically wrong; fragile to edge cases
- Risk: Future maintainers may not understand intent

**Option B: Use explicit whitespace patterns (strict)**
```bash
# Replace all occurrences:
(task[[:space:]-]completion)      # task[space or hyphen]completion
(type[[:space:]-]definition)      # type[space or hyphen]definition
(integration[[:space:]-]test)     # integration[space or hyphen]test
(case[[:space:]-]data)            # case[space or hyphen]data
```
- Pro: Semantically correct; precise matching
- Con: More verbose; need to update multiple lines
- Implementation: Lines 29, 34, 49, 59

**Option C: Use looser matching but escape dots (safe loose)**
```bash
# Use literal escape:
(task\\.completion)      # Match literal dot only, or space/hyphen is ok
```
- Pro: Captures intent; more readable
- Con: Still allows other characters
- Implementation: 1 character change per pattern

### Validation Checklist
Please provide:
1. Should patterns be strict (exact word boundaries) or loose (flexible)?
2. What are canonical forms: "task-completion", "task completion", or "task.completion"?
3. Should the hook validate exact forms or accept variations?
4. Are there real-world inputs that would break current patterns?

---

## Summary Table: Decisions Needed

| Question | Impact | Depends On | Your Input |
|----------|--------|-----------|-----------|
| Q1: "workflow" keyword | Eliminates false positive on schema-only tasks | YAWL module taxonomy | Which option? |
| Q2: "resourcing" keyword | Enables N≥6 boundary detection | YAWL resourcing module | Which keywords canonical? |
| Q3: No-quantums exit code | Determines error vs. warning behavior | GODSPEED STOP conditions | Error or warning? |
| Q4: Prerequisite validation | Prevents team formation without setup | Team enforcement requirements | Strict or permissive? |
| Q5: Regex word boundaries | Fixes semantic correctness | Code quality standards | Strict or loose? |

---

## Next Steps for ENGINEER

1. **Send this document to VALIDATOR**
2. **Wait for answers** to 5 questions
3. **Implement fixes** based on VALIDATOR's responses
4. **Re-run test suite** to verify all 36 tests pass
5. **Commit changes** with VALIDATOR approval

---

## Attachment: Test Suite Location

- Full test suite: `/home/user/yawl/.claude/tests/test-team-recommendation.sh`
- Test output: `/tmp/test-results.txt`
- Audit report: `/home/user/yawl/.claude/reports/team-recommendation-audit.md`

**Current status**: 32/36 tests pass (89%) - 4 tests depend on answers above.
