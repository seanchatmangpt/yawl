# Ralph — Autonomous YAWL Validation Loop

Ralph is an autonomous validation orchestrator for YAWL that runs iterative validation loops and automatically spawns agent teams when validation fails.

## Quick Start

```bash
# From Claude Code, invoke Ralph:
/ralph "Fix all broken tests"
/ralph "Add new workflow feature" --max-iterations 20
/ralph "Optimize performance" --timeout 180
```

Ralph will:
1. Run validation (`dx.sh all`)
2. If it fails (RED) → Auto-spawn agent team
3. Agents work in parallel, commit fixes
4. Loop resumes, validates again
5. Repeat until validation passes (GREEN)

---

## How It Works

### Iteration Flow

```
Iteration N:
  ├─ Run: dx.sh all
  ├─ If GREEN → exit loop ✓
  └─ If RED → spawn agents
       ├─ Task("Engineer", "Fix violations", "yawl-engineer")
       ├─ Task("Validator", "Validate changes", "yawl-validator")
       └─ Task("Tester", "Run test suite", "yawl-tester")
            ├─ Agents work in parallel
            ├─ Agents commit changes
            └─ git log detects commits

Iteration N+1:
  ├─ (loop resumes with agent changes)
  ├─ Run: dx.sh all
  └─ If GREEN → exit loop ✓
```

### State Machine

```
[PENDING] → [RED] → [spawn agents] → [PENDING] → [GREEN] → [DONE]
    ↑                                     ↑
    └─────────────────────────────────────┘
         (agents commit changes)
```

---

## Usage

### Basic Invocation

```bash
# Simple: use defaults (10 iterations, 2-hour timeout)
/ralph "Fix broken tests"

# Custom max iterations
/ralph "Add feature" --max-iterations 20

# Custom timeout (in minutes)
/ralph "Big refactor" --timeout 300

# Resume interrupted loop
/ralph --resume
```

### Options

| Option | Default | Description |
|--------|---------|-------------|
| `--max-iterations N` | 10 | Maximum loop iterations |
| `--timeout MINS` | 120 | Timeout in minutes (0 = no limit) |
| `--resume` | - | Resume previous loop if interrupted |

---

## Architecture

### Core Components

| Component | File | Purpose |
|-----------|------|---------|
| **CLI** | `scripts/ralph/cli.sh` | /ralph entry point, argument parsing |
| **Loop Orchestrator** | `.claude/hooks/ralph-loop.sh` | Main iteration logic, failure detection |
| **Agent Spawner** | `scripts/ralph/agent-spawner.sh` | Invoke agents via Task() syntax |
| **State Manager** | `scripts/ralph/loop-state.sh` | Persist loop progress, resumption |
| **Utilities** | `scripts/ralph/utils.sh` | Common functions (git, JSON, logging) |
| **Validator** | `.claude/hooks/validate-and-report.sh` | Wrapper for dx.sh all |

### State Storage

Ralph stores state in `.claude/.ralph-state/`:

```
.claude/.ralph-state/
├── current-loop.json   # Current loop metadata
├── results.jsonl       # Aggregated results (append-only)
└── .gitkeep
```

**current-loop.json**:
```json
{
  "loop_id": "uuid",
  "description": "Fix broken tests",
  "created_at": "2026-03-05T00:52:00Z",
  "max_iterations": 10,
  "current_iteration": 3,
  "validation_status": "RED",
  "agents_spawned": true
}
```

---

## Safety & Reliability

### Safeguards (From FMEA Analysis)

| Risk | Mitigation |
|------|-----------|
| **dx.sh hangs** | 5-minute timeout, kill on exceed |
| **Agent timeout** | 30-minute timeout per agent team |
| **Infinite loop** | Hard limit: 10 iterations + 2-hour timeout |
| **No code changes** | Verify commits contain `.java`/`.xml` files |
| **Network failures** | Git retry logic (3 attempts, exponential backoff) |
| **State corruption** | Atomic writes, JSON validation |
| **Stuck validation** | Bail out after 3 consecutive RED iterations |
| **Missing tools** | Preflight checks (git, jq, bash, timeout) |

### Pre-flight Checks

Ralph verifies before starting:
- ✓ Required tools available (git, jq, bash, timeout)
- ✓ `.ralph-state/` directory exists and is writable
- ✓ `scripts/dx.sh` exists and is executable

If any check fails, Ralph exits with error code 2.

---

## Monitoring & Debugging

### View Current Loop Status

```bash
bash scripts/ralph/loop-state.sh summary
```

Output:
```
=== Ralph Loop Summary ===
Loop ID: ralph-xyz-123
Description: Fix broken tests
Iteration: 3/10
Status: RED
Agents Spawned: true

=== Recent Events ===
agent_spawn: 2026-03-05T00:52:00Z
agent_complete: 2026-03-05T00:57:15Z
agent_spawn: 2026-03-05T00:57:30Z
```

### Check Loop State

```bash
# Get full state
bash scripts/ralph/loop-state.sh get | jq .

# Get specific field
jq -r '.validation_status' .claude/.ralph-state/current-loop.json
```

### View Logs

```bash
# Recent dx.sh output
ls -ltr .claude/.ralph-logs/dx-*.log | tail -1

# View latest validation log
tail -f .claude/.ralph-logs/dx-*.log
```

---

## Performance

### Typical Loop Timing

| Component | Time |
|-----------|------|
| dx.sh validation | 2-5 min |
| Agent task execution | 5-10 min per agent |
| Total per iteration | ~10-15 min |

### Optimization Tips

1. **Reduce max-iterations** if you know fix is simple:
   ```bash
   /ralph "Small fix" --max-iterations 5
   ```

2. **Set tight timeout** if you need to bail quickly:
   ```bash
   /ralph "Experimental" --timeout 60
   ```

3. **Monitor agent progress** via git commits:
   ```bash
   git log --oneline -20
   ```

---

## Troubleshooting

### Loop Won't Start

**Check preflight:**
```bash
bash .claude/hooks/ralph-loop.sh "test" 2>&1 | head -5
```

**Error: "Cannot create directory"**
- Fix: `mkdir -p .claude/.ralph-state && chmod 755 .claude/.ralph-state`

**Error: "Required tool not found: jq"**
- Fix: Install jq (`apt-get install jq` or `brew install jq`)

### Loop Hangs

**Symptom**: Loop doesn't complete after 10+ minutes

**Diagnosis**:
1. Check if dx.sh is stuck: `ps aux | grep dx.sh`
2. Check loop state: `bash scripts/ralph/loop-state.sh get | jq '.validation_status'`

**Fix**:
- dx.sh will timeout after 5 minutes (automatic)
- If stuck: Manual interrupt (`Ctrl+C`), then `/ralph --resume`

### Agents Don't Spawn

**Symptom**: Loop shows RED but no agents appear

**Root cause**: Task() syntax not recognized by Claude Code

**Check**:
1. Verify Claude Code task output: Look for `Task("Engineer", ...` in output
2. Test manually: `echo 'Task("Test", "desc", "yawl-engineer")'`

**Fix**:
- May need to adjust Task() syntax based on Claude Code version
- See `.claude/RALPH-FMEA.md` item #2 for details

### Validation Stuck on RED

**Symptom**: Loop keeps failing after 3+ iterations

**Root cause**: Agents can't fix the issue, or validation test is flaky

**Check**:
1. Review dx.sh output: `tail -20 .claude/.ralph-logs/dx-*.log`
2. Check agent commits: `git log --oneline -10`
3. Manual validation: `bash scripts/dx.sh compile`

**Fix**:
- Loop exits after 3 consecutive failures (by design)
- Manual intervention needed: Fix root cause, then `/ralph --resume`

---

## For Developers

### Adding Custom Validation

To use Ralph with custom validation instead of dx.sh all:

1. Create custom validator: `my-validator.sh`
2. Modify `validate_and_report.sh` to call your validator
3. Return exit codes: 0 (GREEN), 2 (RED), 1 (transient)

### Extending Ralph

Ralph is modular. Each component can be extended:

| Component | Extension Point |
|-----------|-----------------|
| **CLI** | Add new `--` options in `scripts/ralph/cli.sh` |
| **Loop Logic** | Modify iteration logic in `.claude/hooks/ralph-loop.sh` |
| **Agent Spawning** | Change Task() syntax in `scripts/ralph/agent-spawner.sh` |
| **State Persistence** | Enhance JSON schema in `scripts/ralph/loop-state.sh` |

### Testing Ralph

Minimal test (no actual compilation):
```bash
# Create test validator
cat > /tmp/test-validator.sh <<'EOF'
#!/bin/bash
# Simple test: alternate between GREEN and RED
[[ -f /tmp/ralph-test-pass ]] && { rm /tmp/ralph-test-pass; exit 0; } || { touch /tmp/ralph-test-pass; exit 2; }
EOF
chmod +x /tmp/test-validator.sh
```

Full test:
```bash
# Clean state
rm -rf .claude/.ralph-state/*

# Run loop (will try 10 iterations)
/ralph "Test loop" --max-iterations 10

# Check result
bash scripts/ralph/loop-state.sh summary
```

---

## Known Limitations

1. **Task() Syntax** — Assumes Claude Code recognizes Task() output syntax
   - Workaround: Validate with real Claude Code session

2. **Single-Session Only** — Teams exist only in current session
   - Workaround: Use `/ralph --resume` to continue after breaks

3. **No Agent Coordination** — Agents must work on orthogonal modules
   - Ensured by: CLAUDE.md "no teammate overlap" rule

4. **No Multi-Quantum Teams** — Only one agent team at a time
   - Design choice: Simplicity > parallelism

---

## Reference

### Files Modified/Created

**Created:**
- `scripts/ralph/` — Core modules (4 files)
- `.claude/hooks/ralph-loop.sh` — Orchestrator
- `.claude/hooks/validate-and-report.sh` — Validation wrapper
- `.claude/.ralph-state/` — State directory
- `.claude/RALPH-FMEA.md` — Risk analysis
- `.claude/RALPH-README.md` — This file

**Integration:**
- `/ralph` skill — Automatically available via CLI

### Commands

```bash
# Run loop
/ralph "description" [options]

# View state
bash scripts/ralph/loop-state.sh get|summary

# Manage state
bash scripts/ralph/loop-state.sh status|spawn|complete|cleanup

# Direct invocation (testing)
bash .claude/hooks/ralph-loop.sh "description" 10 120
```

---

## Support

**For issues or questions:**
1. Check `.claude/RALPH-FMEA.md` for known risks
2. Review `.claude/.ralph-logs/` for error logs
3. Check `git log` for agent commits
4. Verify state: `bash scripts/ralph/loop-state.sh get`

**For bugs:**
1. Reproduce with minimal example
2. Capture logs: `cp .claude/.ralph-logs/*.log /tmp/`
3. Document: iteration count, failure mode, dx.sh output
