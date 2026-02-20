# Quick Reference: Team Recommendation Hook Fixes

**For ENGINEER**: Copy-paste ready fixes with before/after

---

## FIX #1: Add "resourc" to Resourcing Pattern (CRITICAL)

**File**: `/home/user/yawl/.claude/hooks/team-recommendation.sh`
**Line**: 44
**Time**: 1 minute

### Before
```bash
if [[ $desc_lower =~ (resource|allocation|pool|workqueue|queue) ]]; then
    ((QUANTUM_COUNT++))
    DETECTED="$DETECTED  ${MAGENTA}◆${NC} Resourcing (yawl/resourcing/**)\n"
fi
```

### After
```bash
if [[ $desc_lower =~ (resource|resourc|allocation|pool|workqueue|queue) ]]; then
    ((QUANTUM_COUNT++))
    DETECTED="$DETECTED  ${MAGENTA}◆${NC} Resourcing (yawl/resourcing/**)\n"
fi
```

### Why
- "resourcing" (gerund) not in original pattern
- Current pattern only matches "resource", "allocation", "pool", etc.
- Adding "resourc" prefix catches "resourcing", "resource", "resources"

### Test
```bash
bash /home/user/yawl/.claude/tests/test-team-recommendation.sh
# After fix: Test 18 and 19 should PASS
```

---

## FIX #2: Change Exit Code for No-Quantums (CRITICAL)

**File**: `/home/user/yawl/.claude/hooks/team-recommendation.sh`
**Line**: 103
**Time**: 1 minute

### Before
```bash
else
    echo -e "${YELLOW}ℹ️  Could not detect clear quantums. Analyze manually.${NC}"
    echo ""
    exit 0
fi
```

### After
```bash
else
    echo -e "${YELLOW}⚠️  Could not detect clear quantums. Provide task with specific quantum keywords.${NC}"
    echo ""
    exit 2
fi
```

### Why
- `exit 0` makes "no quantums" indistinguishable from "empty input"
- Per GODSPEED STOP conditions, ambiguous tasks should trigger error
- `exit 2` signals user to clarify task description

### Test
```bash
bash /home/user/yawl/.claude/tests/test-team-recommendation.sh
# After fix: Test 11 should PASS
```

---

## FIX #3: Disambiguate "workflow" Keyword (CRITICAL)

**File**: `/home/user/yawl/.claude/hooks/team-recommendation.sh`
**Line**: 29
**Time**: 5-10 minutes
**Depends On**: Answer to Q1 from VALIDATOR

### Option A: Remove "workflow" (Recommended if no engine+workflow tasks exist)

#### Before
```bash
if [[ $desc_lower =~ (engine|ynetrunner|workflow|deadlock|state|task.completion) ]]; then
    ((QUANTUM_COUNT++))
    DETECTED="$DETECTED  ${MAGENTA}◆${NC} Engine Semantic (yawl/engine/**)\n"
fi
```

#### After
```bash
if [[ $desc_lower =~ (engine|ynetrunner|deadlock|state|execution|task.completion) ]]; then
    ((QUANTUM_COUNT++))
    DETECTED="$DETECTED  ${MAGENTA}◆${NC} Engine Semantic (yawl/engine/**)\n"
fi
```

#### Why
- Removes false positive on "workflow type definition"
- "execution" replaces "workflow" as execution-specific keyword
- Prevents schema-only tasks from triggering engine quantum

#### Test
```bash
bash /home/user/yawl/.claude/tests/test-team-recommendation.sh
# After fix: Test 10 should PASS
```

---

### Option B: Add Context-Aware Matching (If engine+workflow tasks exist)

#### Before
```bash
if [[ $desc_lower =~ (engine|ynetrunner|workflow|deadlock|state|task.completion) ]]; then
```

#### After
```bash
if [[ $desc_lower =~ (engine|ynetrunner|deadlock|state|execution|task.completion|workflow.execution|workflow.engine|workflow.runtime) ]]; then
```

#### Why
- Keeps "workflow" keyword but adds execution context requirements
- "workflow.execution" catches legitimate engine+workflow tasks
- Still avoids false positive on "workflow type definition"

#### Test
```bash
bash /home/user/yawl/.claude/tests/test-team-recommendation.sh
# After fix: Test 10 should PASS (but verify no new failures)
```

---

### Option C: Context Detection with Separate Quantum (If "workflow" is distinct quantum)

#### Before
```bash
if [[ $desc_lower =~ (engine|ynetrunner|workflow|deadlock|state|task.completion) ]]; then
```

#### After (Line 29 - Engine pattern)
```bash
if [[ $desc_lower =~ (engine|ynetrunner|deadlock|state|execution|task.completion) ]]; then
```

#### Add After Schema Pattern (Line 36)
```bash
if [[ $desc_lower =~ (workflow.definition|workflow.type|workflow.specification) ]]; then
    ((QUANTUM_COUNT++))
    DETECTED="$DETECTED  ${MAGENTA}◆${NC} Workflow Design (schema/**, yawl/elements/**)\n"
fi
```

#### Why
- Separates workflow design from engine and schema
- Allows "workflow" to be its own quantum
- Requires YAWL module taxonomy verification

---

## FIX #4: Add Prerequisite Validation (MEDIUM)

**File**: `/home/user/yawl/.claude/hooks/team-recommendation.sh`
**Lines**: 72-88 (insert before existing team recommendation message)
**Time**: 10 minutes

### Location: After Line 72

### Code to Insert
```bash
elif [[ $QUANTUM_COUNT -ge 2 && $QUANTUM_COUNT -le 5 ]]; then
    echo -e "${MAGENTA}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

    # NEW: Validate prerequisites
    if [[ -z "$CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS" ]]; then
        echo -e "${RED}✗ Error: CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS not set${NC}"
        echo -e "${CYAN}  Run: export CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=1${NC}"
        exit 1
    fi

    if [[ ! -f ".claude/facts/shared-src.json" ]]; then
        echo -e "${RED}✗ Error: Facts missing. Run:${NC}"
        echo -e "${CYAN}  bash scripts/observatory/observatory.sh${NC}"
        exit 1
    fi

    if [[ ! -f ".claude/facts/reactor.json" ]]; then
        echo -e "${RED}✗ Error: Reactor info missing. Run:${NC}"
        echo -e "${CYAN}  bash scripts/observatory/observatory.sh${NC}"
        exit 1
    fi

    echo -e "${GREEN}✓ TEAM MODE RECOMMENDED (τ)${NC}"
    echo -e "${MAGENTA}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
    # ... rest of existing code continues
```

### Why
- Prevents team formation when prerequisites missing
- Fails fast with clear error messages
- Guides user to fix setup issues

### Test (After adding)
```bash
# Should fail if env var not set:
$HOOK_PATH "Fix engine and schema" 2>&1 | grep "CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS"

# Should fail if facts missing:
rm -f .claude/facts/shared-src.json
$HOOK_PATH "Fix engine and schema" 2>&1 | grep "Facts missing"
```

---

## FIX #5: Improve Regex Word Boundaries (LOW PRIORITY)

**File**: `/home/user/yawl/.claude/hooks/team-recommendation.sh`
**Lines**: 29, 34, 49, 59
**Time**: 10 minutes
**Priority**: Low (works currently, code quality improvement)

### Before (Line 29)
```bash
if [[ $desc_lower =~ (engine|ynetrunner|workflow|deadlock|state|task.completion) ]]; then
```

### After (Line 29)
```bash
if [[ $desc_lower =~ (engine|ynetrunner|workflow|deadlock|state|task[[:space:]-]completion) ]]; then
```

### Apply to All Lines
```bash
# Line 29:
task.completion          → task[[:space:]-]completion

# Line 34:
type.definition          → type[[:space:]-]definition

# Line 49:
integration.test         → integration[[:space:]-]test

# Line 59:
case.data                → case[[:space:]-]data
```

### Why
- `.` in regex matches ANY character (not just dots)
- `[[:space:]-]` explicitly matches space or hyphen only
- Semantically correct; prevents accidental matches

### Test (No new tests needed - should still pass all)
```bash
bash /home/user/yawl/.claude/tests/test-team-recommendation.sh
# Should still see 36/36 PASS (or better)
```

---

## COMPLETE FIX ORDER

### Phase 1 (Critical - 3 minutes)
1. [ ] Fix #1: Add "resourc" to pattern (Line 44)
2. [ ] Fix #2: Change exit code (Line 103)
3. [ ] Fix #3: Resolve "workflow" (Line 29) - **After Q1 answer**

### Phase 2 (Medium - 10 minutes)
4. [ ] Fix #4: Add prerequisite validation (Lines 72-88) - **After Q4 answer**
5. [ ] Fix #5: Improve word boundaries (Lines 29, 34, 49, 59) - **After Q5 answer**

---

## TEST VERIFICATION

### Quick Check (1 command)
```bash
bash /home/user/yawl/.claude/tests/test-team-recommendation.sh 2>&1 | tail -20
```

### Expected Output After All Fixes
```
Total tests: 36
Passed: 36
Failed: 0

✓ All tests passed!
```

### Specific Test Verification
```bash
# After Fix #1: N=6 boundary works
bash /home/user/yawl/.claude/hooks/team-recommendation.sh \
  "Fix engine, modify schema, add integration, improve resourcing, write tests, and add security" \
  2>&1 | grep "Detected"
# Expected: "Detected 6 quantums"

# After Fix #2: No-quantums is error
bash /home/user/yawl/.claude/hooks/team-recommendation.sh \
  "Analyze performance metrics" \
  2>&1 && echo "EXIT: $?"
# Expected: EXIT code = 2

# After Fix #3: Schema-only works
bash /home/user/yawl/.claude/hooks/team-recommendation.sh \
  "Update workflow type definition in XSD" \
  2>&1 | grep "Detected"
# Expected: "Detected 1 quantum" (schema only) - depends on Q1 answer
```

---

## ROLLBACK PROCEDURE (If needed)

All changes are additions/single-line edits - easy to rollback:

```bash
# View current hook
cat /home/user/yawl/.claude/hooks/team-recommendation.sh

# Restore from git
git checkout /home/user/yawl/.claude/hooks/team-recommendation.sh

# Re-run tests
bash /home/user/yawl/.claude/tests/test-team-recommendation.sh
```

---

## DEBUGGING TIPS

### Test a Single Quantum
```bash
HOOK="/home/user/yawl/.claude/hooks/team-recommendation.sh"

# Test engine quantum
$HOOK "Fix engine deadlock" 2>&1 | grep -E "Engine|Detected"

# Test schema quantum
$HOOK "Update schema definition" 2>&1 | grep -E "Schema|Detected"

# Test resourcing quantum
$HOOK "Improve resource allocation" 2>&1 | grep -E "Resourcing|Detected"
```

### Extract Quantum Count
```bash
$HOOK "Your task" 2>&1 | grep -oP "Detected \K\d+(?= quantums)" || echo "0"
```

### Check Exit Code
```bash
$HOOK "Your task" 2>&1
echo "Exit code: $?"
```

### Inspect Pattern Matching (bash only)
```bash
desc="improve resourcing"
desc_lower="${desc,,}"

# Test resourcing pattern
if [[ $desc_lower =~ (resource|resourc|allocation|pool|workqueue|queue) ]]; then
    echo "MATCH: resourcing detected"
else
    echo "NO MATCH"
fi
```

---

## FILE LOCATIONS (Recap)

| File | Purpose |
|------|---------|
| `/home/user/yawl/.claude/hooks/team-recommendation.sh` | Hook being fixed |
| `/home/user/yawl/.claude/tests/test-team-recommendation.sh` | Test suite (36 tests) |
| `/home/user/yawl/.claude/reports/team-recommendation-audit.md` | Full audit report |
| `/home/user/yawl/.claude/reports/validator-questions.md` | Questions for VALIDATOR |
| `/home/user/yawl/.claude/reports/AUDIT-SUMMARY.md` | Executive summary |
| `/home/user/yawl/.claude/reports/quick-reference.md` | This file |

---

## COMMIT MESSAGE TEMPLATE

```
Fix team-recommendation hook: multi-quantum detection and boundary conditions

- Fix #1: Add "resourc" to resourcing pattern (line 44)
  Enables detection of "resourcing" quantum; fixes N>=6 boundary tests

- Fix #2: Change exit code for no-quantums (line 103)
  "Could not detect quantums" now returns exit 2 (error) instead of 0 (success)
  Aligns with GODSPEED STOP conditions for ambiguous tasks

- Fix #3: [Option chosen from Q1 answer]
  [Description of workflow keyword fix]

Test results: 32/36 passing → 35/36 passing (97% pass rate)

Related: Audit of team enforcement system (τ)
```

---

**END OF QUICK REFERENCE**
