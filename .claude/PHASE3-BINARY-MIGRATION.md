# Phase 3: Rust Binary Migration Guide

**Status**: DESIGN (Implementation requires Rust changes)
**Scope**: Update yawl-jira and yawl-scout to use Phase State Machine

---

## Overview

Phases 1 & 2 implemented the state machine infrastructure (phases.json, task queue). Phase 3 requires updating the Rust binaries (yawl-jira, yawl-scout) to:

1. Read/write phases.json instead of individual receipt files
2. Use explicit task queueing instead of background spawning
3. Participate in the phase state machine lifecycle

---

## Requirements for Rust Binaries

### yawl-jira Requirements

**Current behavior**:
```rust
// Old: Writes to individual receipt files
receipts/intelligence-build.json
receipts/intelligence-sessions.json
```

**New behavior (Phase 3)**:
```rust
// New: Reads/writes phases.json
1. On startup: read .claude/state/phases.json
2. On pre-write: validate phase prerequisitesvia validate_phase_prerequisites()
3. On post-write: update phases.json via update_phase_status()
4. On checkpoint: flush to phases.json
```

**Changes needed**:

1. **Add phases.json reader**:
   ```rust
   fn read_phases_json(repo_root: &Path) -> Result<PhasesJson> {
       let path = repo_root.join(".claude/state/phases.json");
       let json = std::fs::read_to_string(&path)?;
       serde_json::from_str(&json)
   }
   ```

2. **Add phases.json writer (atomic)**:
   ```rust
   fn write_phases_json(repo_root: &Path, phases: &PhasesJson) -> Result<()> {
       let path = repo_root.join(".claude/state/phases.json");
       let tmp = path.with_extension("json.tmp");

       // Write to temp file
       std::fs::write(&tmp, serde_json::to_string_pretty(phases)?)?;

       // Atomic rename
       std::fs::rename(&tmp, &path)?;
       Ok(())
   }
   ```

3. **Add audit logging**:
   ```rust
   fn audit_log(repo_root: &Path, event: &str, data: &str) -> Result<()> {
       let audit_path = repo_root.join(".claude/state/audit.jsonl");
       let entry = serde_json::json!({
           "timestamp": chrono::Utc::now().to_rfc3339(),
           "event": event,
           "data": data,
           "session_id": std::env::var("CLAUDE_SESSION_ID").unwrap_or_default()
       });

       let mut file = OpenOptions::new()
           .create(true)
           .append(true)
           .open(&audit_path)?;
       writeln!(file, "{}", entry.to_string())?;
       Ok(())
   }
   ```

4. **Update command handlers**:
   ```rust
   pub fn handle_inject_prompt(&self, input: &str) -> Result<String> {
       // New: Check if phase prerequisites are met
       audit_log(&self.repo_root, "jira_inject_prompt", input)?;

       // ... existing logic ...

       // New: Update phases.json on completion
       let mut phases = read_phases_json(&self.repo_root)?;
       // Update ψ or λ status if needed
       write_phases_json(&self.repo_root, &phases)?;

       Ok(output)
   }
   ```

### yawl-scout Requirements

**Current behavior**:
```bash
# Old: Spawned in background via run-yawl-hooks.sh
yawl-scout fetch --async &
# No guarantee it completes before session ends
```

**New behavior (Phase 3)**:
```bash
# New: Enqueued via task-queue-lib.sh
enqueue_task "fetch_intelligence" "yawl-scout fetch"
execute_task "task-20260304-053849-001"
# session-end-v2.sh waits for completion
```

**Changes needed**:

1. **Argument changes**:
   ```rust
   // Old: fetch --async (spawned by caller)
   // New: fetch (called explicitly, returns when done)

   // The caller (run-yawl-hooks.sh v2.0) will:
   // 1. Enqueue task with command "yawl-scout fetch"
   // 2. session-end-v2.sh will execute and wait
   ```

2. **Return behavior**:
   ```rust
   // Must return with exit code 0 on success
   // Task queue will capture exit code and update tasks.json

   pub fn fetch(&self) -> Result<()> {
       // Existing fetch logic
       audit_log(&self.repo_root, "scout_fetch", "started")?;

       // ... fetch from sources ...

       audit_log(&self.repo_root, "scout_fetch", "completed")?;
       Ok(())
   }
   ```

---

## Migration Strategy

### Option A: Parallel Implementation (Recommended)

**Phase 3a**: Both old and new systems active (backward compatible)
- yawl-jira writes to both old receipts AND phases.json
- yawl-scout works with both task queueing and async spawning
- run-yawl-hooks.sh v2.0 calls yawl-scout via task queue
- Reduces deployment risk

**Phase 3b**: Switch to new system
- Turn off writing to old receipt files
- Update run-yawl-hooks.sh to use only task queue
- Remove async spawning fallback

### Option B: Gradual Rollout

1. Update yawl-scout first (simpler: just needs exit code handling)
2. Update yawl-jira second (complex: phases.json reader/writer logic)
3. Coordinate via run-yawl-hooks.sh v2.0 (already in place)

---

## Testing Checklist (Before Deployment)

### Unit Tests

```rust
#[test]
fn test_read_phases_json() {
    // Verify reading canonical phases.json format
}

#[test]
fn test_write_phases_json_atomic() {
    // Verify atomic writes (temp file → rename)
}

#[test]
fn test_audit_log_format() {
    // Verify JSONL format with all required fields
}

#[test]
fn test_phase_prerequisite_validation() {
    // Verify validate_phase_prerequisites() works
}
```

### Integration Tests

```bash
# Test 1: yawl-scout enqueue → execute → complete
enqueue_task "fetch_intelligence" "yawl-scout fetch"
task_id=$(...)
execute_task "$task_id"
wait_for_task "$task_id" 30
# Verify: exit code 0, tasks.json shows "completed"

# Test 2: yawl-jira updates phases.json
yawl-jira --repo-root . inject prompt < input.json
# Verify: phases.json updated with current timestamp, exit code recorded

# Test 3: Session end waits for tasks
yawl-scout fetch --task-id "task-..." &
task_pid=$!
bash ./session-end-v2.sh
# Verify: Waits for $task_pid, reports final status
```

### Production Validation

```bash
# Before switching: run both systems in parallel
dx.sh all  # Uses old receipt files
# + phases.json should also be updated (dual-write)

# Verify consistency:
jq .phases.h .claude/state/phases.json
# Should match status in .claude/receipts/guard-receipt.json
```

---

## Schema Changes Required in Rust

### PhasesJson (serde struct)

```rust
use serde::{Deserialize, Serialize};
use chrono::{DateTime, Utc};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PhasesJson {
    pub version: String,
    pub session_id: String,
    pub session_start_time: DateTime<Utc>,
    pub phases: Phases,
    pub audit_trail: String,
    pub background_tasks: String,
    pub last_update: DateTime<Utc>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Phases {
    #[serde(rename = "ψ")]
    pub psi: Phase,
    #[serde(rename = "λ")]
    pub lambda: Phase,
    #[serde(rename = "h")]
    pub h: Phase,
    #[serde(rename = "q")]
    pub q: Phase,
    #[serde(rename = "ω")]
    pub omega: Phase,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Phase {
    pub name: String,
    pub status: String,  // PENDING, RUNNING, GREEN, RED, BLOCKED, SKIPPED
    pub timestamp: Option<DateTime<Utc>>,
    pub duration_ms: Option<u64>,
    pub exit_code: Option<i32>,
    #[serde(flatten)]
    pub extra: serde_json::Value,  // For phase-specific fields
}
```

### TasksJson (serde struct)

```rust
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TasksJson {
    pub version: String,
    pub tasks: Vec<Task>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Task {
    pub id: String,
    pub name: String,
    pub command: String,
    pub description: Option<String>,
    pub enqueued_at: DateTime<Utc>,
    pub status: String,  // pending, running, completed, failed
    pub pid: Option<u32>,
    pub exit_code: Option<i32>,
    pub started_at: Option<DateTime<Utc>>,
    pub completed_at: Option<DateTime<Utc>>,
    pub error: Option<String>,
}
```

---

## Deployment Plan

### Week 1: Implement Changes
- [ ] Add phases.json reader/writer to yawl-jira
- [ ] Add audit logging to yawl-jira and yawl-scout
- [ ] Update yawl-scout to support task queue
- [ ] Write unit tests

### Week 2: Integration Testing
- [ ] Test both systems in parallel (dual-write)
- [ ] Validate consistency between old and new
- [ ] Load testing (ensure no performance regression)

### Week 3: Rollout
- [ ] Deploy updated binaries to production
- [ ] Monitor for issues (keep old receipt files as fallback)
- [ ] Switch to new system (Phase 3b) after 1 week

### Week 4: Cleanup (Phase 4)
- [ ] Remove old receipt files
- [ ] Update documentation
- [ ] Archive old receipts

---

## Benefits of Phase 3 Migration

1. **Observable Intelligence**: Task queue provides visibility into background work
2. **Guaranteed Completion**: session-end-v2.sh waits for all tasks
3. **Audit Trail**: All yawl-jira operations logged to audit.jsonl
4. **Single Source of Truth**: Rust binaries read/write phases.json like shell scripts
5. **Atomic Updates**: Prevents inconsistent state (no partial writes)

---

## Fallback Plan

If Phase 3 migration encounters issues:

1. **Keep old system running** (Phase 2 is already backward compatible)
2. **Revert Rust changes** (old receipt files still work)
3. **Phase 1-2 benefits still apply** (state machine infrastructure exists)
4. **Retry Phase 3 later** (no data loss)

Phase 1-2 implementation is complete and working. Phase 3 is a value-add but optional for basic functionality.
