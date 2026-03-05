# Ralph FMEA — Failure Mode and Effects Analysis

## System Overview
Ralph is an autonomous validation loop orchestrator that:
- Runs iterative validation (dx.sh all)
- Auto-spawns agent teams on failures
- Detects agent completion via git commits
- Persists state for resumption

---

## FMEA Matrix

| # | Failure Mode | Cause | Effect | Severity | Detection | Mitigation |
|---|---|---|---|---|---|---|
| **1** | **dx.sh Hangs** | Resource exhaustion, JVM lock | Loop stalled indefinitely | CRITICAL | Timeout mechanism | Add dx.sh timeout (5 min), kill on exceed |
| **2** | **Agent Spawn Fails** | Task() syntax invalid or unrecognized | Agents never start, loop waits forever | CRITICAL | Check Claude Code Task() syntax docs | Test Task() in real Claude Code session |
| **3** | **No Commit Detected** | Agents didn't commit, git log empty | Ralph thinks fix succeeded, no actual change | HIGH | Check git log for commits | Verify commits exist before continuing |
| **4** | **Git Fetch Fails** | Network issue, auth failure | State becomes stale | HIGH | Retry logic with backoff | Retry 3× before failing |
| **5** | **Loop State Corruption** | Concurrent writes, malformed JSON | Loop crashes, can't read state | MEDIUM | Validate JSON schema | Use atomic file writes, backup state |
| **6** | **Infinite Loop** | Validation RED forever | Loop runs max iterations uselessly | MEDIUM | Iteration counter | Hard limit (10 iters) + timeout (2 hrs) |
| **7** | **File Permissions** | Directory not writable | State can't persist | MEDIUM | mkdir -p with checks | Pre-check .ralph-state/ is writable |
| **8** | **Missing jq/git** | Tools not in PATH | Scripts crash | LOW | Which jq/git on startup | Explicit error message, document deps |
| **9** | **Timezone/Clock Skew** | System clock wrong | Timestamp comparisons fail | LOW | Use UTC + sequence numbers | Monotonic counters instead of timestamps |
| **10** | **Race: Multiple Agents** | Same file edited by 2+ agents | Git merge conflict | MEDIUM | Agent task separation | Assign orthogonal modules to agents |

---

## Critical Failure Modes (SEVERITY = CRITICAL)

### Failure #1: dx.sh Execution Hangs
**Scenario**: dx.sh all never completes (JVM lock, Maven deadlock)

**Current Implementation**:
```bash
check_dx_status() {
    bash scripts/dx.sh all > "${RALPH_LOGS_DIR}/dx-$(date +%s).log" 2>&1
    return $?
}
```
❌ **Problem**: No timeout — loop can hang forever

**Recommended Fix**:
```bash
check_dx_status() {
    timeout 300 bash scripts/dx.sh all > "${RALPH_LOGS_DIR}/dx-$(date +%s).log" 2>&1
    local exit_code=$?
    if [[ $exit_code -eq 124 ]]; then
        log_error "dx.sh timeout (5 min exceeded)"
        return 1  # Transient, retry
    fi
    return $exit_code
}
```

**Risk**: Without this, ralph loop hangs indefinitely on Maven issues

---

### Failure #2: Agent Spawn Task() Syntax Invalid
**Scenario**: Claude Code doesn't recognize Task() syntax in agent-spawner.sh output

**Current Implementation**:
```bash
cat <<EOF
Task("Engineer", "Fix validation failures...", "yawl-engineer")
EOF
```
❌ **Problem**: Task() syntax not validated; assumes Claude Code parser recognizes it

**Root Cause**: Task() might need to be:
- Wrapped in specific markers for Claude Code parsing
- Called via Bash function instead of output string
- Registered explicitly in Claude Code config

**Recommended Actions**:
1. **Validate** Task() syntax with real Claude Code session
2. **Test** if output is parsed correctly by running test invocation
3. **Document** exact Task() format expected by Claude Code
4. **Fallback** if syntax wrong: Use Agent tool instead

**Risk**: If Task() doesn't work, agents never spawn = system broken

---

## High-Risk Failure Modes (SEVERITY = HIGH)

### Failure #3: Agent Commits Not Detected
**Scenario**: Agents complete and commit, but git log shows no new commits

**Current Implementation**:
```bash
wait_with_timeout() {
    local timeout_secs=$1
    local check_cmd=$2
    # Check if commits exist since timestamp
    local check_cmd="git_log_since '${spawn_timestamp}' 2>&1 | grep -q '.'"
    wait_with_timeout 1800 "$check_cmd"
}
```
❌ **Problem**: Assumes git log will show commits; doesn't verify content

**Recommended Fix**:
```bash
# Verify commits actually changed source code
verify_agent_commits() {
    local pre_commit="$1"
    local post_commit
    post_commit=$(git rev-parse HEAD)

    if [[ "$pre_commit" == "$post_commit" ]]; then
        log_error "No new commits detected from agents"
        return 1
    fi

    # Check diff to ensure real changes (not just metadata)
    if ! git diff "${pre_commit}..${post_commit}" --stat | grep -q '\.java'; then
        log_error "Agent commits don't contain code changes"
        return 1
    fi

    return 0
}
```

**Risk**: Ralph thinks fix succeeded when nothing changed → infinite loop potential

---

### Failure #4: Network/Git Failures
**Scenario**: `git fetch`, `git log` fail intermittently

**Current Implementation**:
```bash
git_fetch_latest() {
    git fetch origin -q 2>/dev/null || return 1
}
```
❌ **Problem**: No retry logic — single network hiccup fails entire loop

**Recommended Fix**:
```bash
git_fetch_latest() {
    local retries=3
    local backoff=2

    for attempt in $(seq 1 $retries); do
        if git fetch origin -q 2>/dev/null; then
            return 0
        fi

        if [[ $attempt -lt $retries ]]; then
            log_debug "git fetch failed, retry in ${backoff}s"
            sleep $backoff
            backoff=$((backoff * 2))
        fi
    done

    log_error "git fetch failed after ${retries} attempts"
    return 1
}
```

**Risk**: Transient network issues cause false loop failures

---

## Medium-Risk Failure Modes (SEVERITY = MEDIUM)

### Failure #5: Loop State Corruption
**Current Implementation**:
```bash
json_set() {
    local file="$1" key="$2" value="$3"
    jq ".${key} = \"${value}\"" "$file" > "${file}.tmp"
    mv "${file}.tmp" "$file"
}
```
⚠️ **Problem**: Not atomic on all filesystems; concurrent writes possible

**Recommended Fix**:
```bash
json_set() {
    local file="$1" key="$2" value="$3"
    local tmpfile="${file}.$$.tmp"
    local lockfile="${file}.lock"

    # Acquire lock
    exec 9>"$lockfile"
    flock -x 9 || return 1

    # Write atomically
    jq ".${key} = \"${value}\"" "$file" > "$tmpfile" || {
        flock -u 9
        return 1
    }

    mv "$tmpfile" "$file"
    flock -u 9
}
```

**Risk**: Corrupted state JSON → loop crash

---

### Failure #6: Infinite Loop on Stuck Validation
**Current Implementation**:
```bash
if [[ $current -ge $max ]]; then
    return 1
fi
```
✓ Good: Max iteration limit exists (default 10)
⚠️ Concern: What if agents keep failing? 10 cycles × 5 min each = 50 min of wasted computation

**Recommended Enhancement**:
```bash
# Track consecutive failures
consecutive_failures=0
max_consecutive=3

if [[ "$status" == "RED" ]]; then
    consecutive_failures=$((consecutive_failures + 1))

    if [[ $consecutive_failures -ge $max_consecutive ]]; then
        log_error "Too many consecutive failures (${consecutive_failures}). Bailing out."
        return 1
    fi
else
    consecutive_failures=0
fi
```

**Risk**: Loop consumes resources on stuck validation

---

### Failure #7: File Permission Issues
**Current Implementation**:
```bash
mkdir -p "${RALPH_STATE_DIR}"
```
❌ **Problem**: No check if directory is writable

**Recommended Fix**:
```bash
# At startup
if ! [[ -w "${RALPH_STATE_DIR}" ]]; then
    log_error "Cannot write to ${RALPH_STATE_DIR}"
    exit 2
fi
```

**Risk**: State can't persist → loop can't resume

---

## Low-Risk Failure Modes (SEVERITY = LOW)

### Failure #8: Missing Dependencies (jq, git, bash)
**Current**: Scripts assume jq, git, bash available

**Mitigation**:
- Already okay for YAWL environment (git, bash standard)
- jq is common on all Unix systems
- Add startup check if needed

### Failure #9: Timezone/Clock Skew
**Current**: Uses `date` for timestamps

**Better**: Use monotonic sequence numbers instead of timestamps for ordering

### Failure #10: Race Conditions Between Agents
**Current**: No coordination between agents

**Mitigation**: Tasks assign orthogonal modules (CLAUDE.md: "No teammate overlap on same file")

---

## Recommendations (Priority Order)

### 🔴 CRITICAL (Must Fix)
1. **Add dx.sh timeout** (300 sec) — prevents hanging
2. **Validate Task() syntax** — test in real Claude Code session
3. **Verify agent commits exist** — check git diff contains .java files

### 🟠 HIGH (Should Fix)
4. **Add git retry logic** — exponential backoff on network failures
5. **Atomic state writes** — use flock for concurrent access
6. **Consecutive failure limit** — bail out after 3 RED iterations

### 🟡 MEDIUM (Nice to Have)
7. **Pre-flight checks** — validate .ralph-state/ is writable
8. **Sequence-based ordering** — replace timestamps with counters
9. **Enhanced logging** — detailed debug output for troubleshooting

---

## Testing Strategy

| Test | Purpose | Status |
|------|---------|--------|
| **1. Task() Syntax** | Verify Claude Code recognizes Task() calls | ❌ TODO |
| **2. dx.sh Timeout** | Confirm timeout kills hung processes | ❌ TODO |
| **3. Agent Commit Detection** | Verify commits are found and validated | ❌ TODO |
| **4. Network Failure Recovery** | Simulate git failure, verify retry | ❌ TODO |
| **5. State Persistence** | Verify state survives corruption attempt | ❌ TODO |
| **6. Infinite Loop Prevention** | Confirm loop exits after max iterations | ✅ READY |
| **7. Permissions Check** | Verify pre-flight check catches unwritable dir | ❌ TODO |

---

## Risk Summary

| Risk Level | Count | Criticality |
|---|---|---|
| CRITICAL | 2 | Must validate Task() + dx.sh timeout |
| HIGH | 3 | Git retries, state atomicity, commit verification |
| MEDIUM | 5 | Consecutive failures, permissions, etc. |
| LOW | 2 | Clock skew, missing tools |

**Overall System Risk**: **HIGH** — Task() syntax and dx.sh timeout are showstoppers. Need to validate before production use.

---

## Immediate Action Items

1. ✅ **Test Task() syntax** in Claude Code console
   ```bash
   # Quick test: does Claude Code recognize this output?
   echo 'Task("Test", "description", "yawl-engineer")'
   ```

2. ✅ **Add dx.sh timeout** to ralph-loop.sh

3. ✅ **Test agent spawning** with minimal example

4. ✅ **Verify git commit detection** logic

5. ✅ **Add pre-flight checks** (writable state dir, required tools)
