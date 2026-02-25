# Ω Git Phase — Atomic Commit Protocol

**Status**: ACTIVE
**Position in pipeline**: Final phase after Q (Invariants) passes GREEN
**Scope**: Commit integrity, channel control, and branch discipline for YAWL v6.0.0

---

## Overview

The Ω (Git) phase governs how changes move from validated local state into the permanent
record. It enforces three invariants that survive all future refactors:

1. **Atomicity** — one logical change per commit, always buildable
2. **Channel discipline** — emit channels vs. protected channels, ask before crossing
3. **Hygiene** — no force-push, no amended pushed commits, no glob staging

---

## 1. Branch Naming Convention

```
claude/<description>-<sessionId>
```

| Component | Format | Example |
|-----------|--------|---------|
| `claude/` | literal prefix | `claude/` |
| `<description>` | kebab-case summary | `add-virtual-thread-netrunner` |
| `<sessionId>` | last 5 chars of session ID | `iDs6b` |

Full example: `claude/add-virtual-thread-netrunner-iDs6b`

**Rule**: Never push to `master` or any non-`claude/` branch without explicit user instruction.

---

## 2. Channel Map

Channels control which paths Claude may write to autonomously vs. which require user confirmation.

### Emit Channels (Write Freely)

```
src/          — Java source files
test/         — Test source files
schema/       — XSD and specification schemas
.claude/      — Agent instructions, rules, hooks, phases
```

### Protected Channels ⊗ (Ask First)

```
root          — pom.xml, .gitignore, .mvn/, Dockerfile, docker-compose*.yml
docs/         — Published documentation directories
*.md          — All Markdown files at repo root level
```

**When blocked**: State the proposed change and ask: _"This touches `<protected path>`. Proceed?"_

---

## 3. Staging Rules

### Required: Stage Specific Files

```bash
# CORRECT — name every file
git add src/org/yawlfoundation/yawl/engine/YNetRunner.java
git add test/org/yawlfoundation/yawl/engine/NetRunnerBehavioralTest.java

# FORBIDDEN — never glob-stage
git add .
git add -A
git add src/
```

**Why**: `git add .` risks committing IDE artifacts, generated files, or secrets that happen
to exist in the working tree. The hook (`pre-commit-validation.sh`) enforces this check.

---

## 4. Commit Message Format

```
<imperative verb> <what changed>

<optional body: why this change, not what>

https://claude.ai/code/session_<sessionId>
```

### Examples

```
Refactor YNetRunner to use virtual thread per case

Eliminates platform thread blocking during OR-join evaluation.
Each case now runs on a dedicated virtual thread created via
Thread.ofVirtual().name("case-" + caseId).start(runnable).

https://claude.ai/code/session_01CqDn3kzHhBaigTG1bcMLqJ
```

```
Add SHACL Q-phase validation for real_impl ∨ throw invariant

https://claude.ai/code/session_01CqDn3kzHhBaigTG1bcMLqJ
```

### Rules

- Imperative mood: "Add", "Fix", "Refactor", "Remove" — not "Added", "Fixing", "Removes"
- First line ≤ 72 characters
- Always append the session URL on the last line
- No body needed for trivial changes

---

## 5. Safety Rules

| Rule | Rationale |
|------|-----------|
| Never `--force` | Rewrites history that teammates may have fetched |
| Never amend a pushed commit | History rewrite on shared branch |
| Never `--no-verify` | Bypasses hyper-validate.sh + pre-commit hook |
| Never `git add .` | Risks committing unintended files |

---

## 6. Push Protocol

```bash
# First push (creates tracking)
git push -u origin claude/<desc>-<sessionId>

# Subsequent pushes
git push origin claude/<desc>-<sessionId>
```

**Network failure retry** (exponential backoff):

```bash
for delay in 2 4 8 16; do
    git push -u origin "$BRANCH" && break
    echo "Push failed, retrying in ${delay}s..."
    sleep $delay
done
```

---

## 7. Ω in the GODSPEED Pipeline

```
Ψ (Observatory)     Facts green ✓
    ↓
Λ (Build)           bash scripts/dx.sh all → green ✓
    ↓
H (Guards)          hyper-validate.sh → no violations ✓
    ↓
Q (Invariants)      q-phase-invariants.sh → green ✓
    ↓
Ω (Git)             ← YOU ARE HERE
    ├─ Stage specific files
    ├─ Write atomic commit message + session URL
    ├─ git push -u origin claude/<desc>-<sessionId>
    └─ DONE
```

---

## 8. Multi-Module Commits

When a feature spans multiple modules (e.g., `yawl-elements` + `yawl-engine`):

```bash
# Stage only the changed files from each module
git add yawl-elements/src/org/yawlfoundation/yawl/elements/YTask.java
git add yawl-engine/src/org/yawlfoundation/yawl/engine/YNetRunner.java
git add yawl-engine/test/org/yawlfoundation/yawl/engine/NetRunnerBehavioralTest.java

# One commit for the cross-module change
git commit -m "$(cat <<'EOF'
Add split/join type to YTask, update NetRunner routing

YTask.setSplitType()/getJoinType() now validate against the sealed
SplitType/JoinType enums. YNetRunner uses pattern matching on the
new sealed type to select the correct firing rule.

https://claude.ai/code/session_01CqDn3kzHhBaigTG1bcMLqJ
EOF
)"
```

**Anti-pattern**: Do NOT split a single logical change into separate commits per module.
Bisect must always find the repo in a buildable state.

---

## 9. Shared-Source Awareness

Before committing files in shared source (see `docs/v6/latest/facts/shared-src.json`):

```bash
# Check which modules compile a file
cat docs/v6/latest/facts/shared-src.json | jq '.shared[] | select(.file | contains("YEngine"))'
```

Shared files are compiled by multiple modules. A change in `../src/engine/YEngine.java`
affects `yawl-elements`, `yawl-engine`, and `yawl-stateless`. Run `dx.sh all` (not just
the owning module) before committing.

---

## 10. Teams (τ) Commit Protocol

When a team lead consolidates teammate work:

1. **Pull all teammate commits** into lead's branch
2. **Run `dx.sh all`** — full build must be green before consolidation commit
3. **Hyper-validate combined edits** — hook checks all modified files together
4. **Single atomic commit** — all teammate work lands as one logical unit
5. **Tag the consolidation**: `git tag -a τ-consolidated-<teamId> -m "Team consolidation"`

If `dx.sh all` fails after teammate contributions:
- Identify failing module
- Assign fix to responsible teammate
- Re-run consolidation only after green

---

## 11. Receipt

The Ω phase does not emit a JSON receipt (it is a gate, not a validator). Success is
confirmed by the git push exit code. The commit SHA is the receipt.

```bash
# Verify commit landed
git log --oneline -1
# Output: abc1234 Add SHACL Q-phase validation for real_impl ∨ throw invariant
```

---

## Quick Reference

```bash
# Full Ω workflow
bash scripts/dx.sh all                          # Λ gate
.claude/hooks/q-phase-invariants.sh src/        # Q gate (if applicable)
git add <specific files>                        # Stage
git commit -m "$(cat <<'EOF'
<message>

https://claude.ai/code/session_<id>
EOF
)"
git push -u origin claude/<desc>-<sessionId>    # Push
```

---

**See also**:
- `CLAUDE.md` — Ω GIT axioms
- `.claude/hooks/pre-commit-validation.sh` — Pre-commit hook enforcement
- `docs/v6/latest/facts/shared-src.json` — Shared source map
- `.claude/phases/README.md` — Full pipeline overview
