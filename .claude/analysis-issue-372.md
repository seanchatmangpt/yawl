# Issue #372 Complete Analysis — First Principles

## Observed Symptom
The Stop hook (stop-hook-git-check.sh) was failing with "CLAUDE_PROJECT_DIR unbound variable" error when invoked by Claude Code's hook system.

## Root Cause Chain (From Deepest to Surface)

### Layer 1: Architectural Issue (Deepest)
**Location**: `.claude/settings.json` lines 83, 89, 108

The three validation hooks use **inconsistent quoted JSON syntax**:

```json
# Lines 83, 89, 108: WITH escaped quotes (WRONG)
"command": "\"$CLAUDE_PROJECT_DIR\"/.claude/hooks/hyper-validate.sh"
"command": "\"$CLAUDE_PROJECT_DIR\"/.claude/hooks/verify-ttl-write.sh"
"command": "\"$CLAUDE_PROJECT_DIR\"/.claude/hooks/stop-hook-git-check.sh"

# Other hooks: WITHOUT quotes (CORRECT)
"command": "$CLAUDE_PROJECT_DIR/.claude/hooks/session-start.sh"
"command": "$CLAUDE_PROJECT_DIR/.claude/hooks/run-yawl-hooks.sh"
```

**Why This Matters**:
- JSON parser unescapes `\"` → `"`
- Shell then sees literal quote characters: `"/home/user/yawl"/.claude/hooks/...`
- While bash actually *handles* this correctly in string context, it's:
  - Syntactically inconsistent with other hooks
  - Violates the principle of least surprise
  - Created by accident in commits a87184f/b2471d1 (rogue AI experiments)

### Layer 2: Script-Level Vulnerability (Middle)
**Location**: `.claude/hooks/stop-hook-git-check.sh` (now fixed in HEAD)

Original code used `set -euo pipefail`:
```bash
set -euo pipefail          # -u flag causes unbound variable error

CLAUDE_PROJECT_DIR="${CLAUDE_PROJECT_DIR}"  # No default!
cd "$CLAUDE_PROJECT_DIR" || exit 0
```

When CLAUDE_PROJECT_DIR was not set by the Rust hook system, this line failed with:
```
stop-hook-git-check.sh: line 14: CLAUDE_PROJECT_DIR: unbound variable
```

**Why This Triggered**:
- Rust hook system may not set CLAUDE_PROJECT_DIR in all execution contexts
- The escaped quotes in settings.json somehow caused the variable to not be substituted
- OR the Rust system passed a malformed command path and the script failed before it could even check

### Layer 3: Similar Vulnerabilities (Surface)
**Files**: `.claude/hooks/hyper-validate.sh`, `.claude/hooks/verify-ttl-write.sh`

Both still have `set -euo pipefail` BUT:
- They don't reference CLAUDE_PROJECT_DIR, so they won't hit this specific failure
- They're still vulnerable to OTHER unbound variables if referenced

## Fix Applied (Commit f46e1fe)

**File**: `.claude/hooks/stop-hook-git-check.sh`

Changed from:
```bash
set -euo pipefail
cd "$CLAUDE_PROJECT_DIR" || exit 0
```

To:
```bash
set -eo pipefail  # Removed -u (unbound variable check)
PROJECT_DIR="${CLAUDE_PROJECT_DIR:-.}"  # Default to current directory
cd "$PROJECT_DIR" || exit 0
```

**Impact**: Issue #372 is resolved at the symptom level.

## Unresolved Issues (Not Fixed)

### Issue 1: Inconsistent Hook Configuration
The escaped quotes in settings.json remain unfixed. While they don't break execution (bash handles it), they:
- Violate consistency with other hooks
- Are confusing to future maintainers
- Originated from incomplete V7 self-play experiments (commits a87184f, b2471d1)

**Lines to fix**:
- `.claude/settings.json:83` — hyper-validate.sh
- `.claude/settings.json:89` — verify-ttl-write.sh
- `.claude/settings.json:108` — stop-hook-git-check.sh

**Change**: Remove escaped quotes to match other hooks:
```json
# Before
"command": "\"$CLAUDE_PROJECT_DIR\"/.claude/hooks/stop-hook-git-check.sh"

# After
"command": "$CLAUDE_PROJECT_DIR/.claude/hooks/stop-hook-git-check.sh"
```

### Issue 2: Similar Vulnerability in Other Hooks
Both `hyper-validate.sh` and `verify-ttl-write.sh` still use `set -euo pipefail`. If they ever reference an unbound environment variable, they will fail the same way.

**Recommendation**: Proactively update both to use `set -eo pipefail` (remove `-u`) for consistency and safety.

### Issue 3: Missing Fallback Pattern
Only `stop-hook-git-check.sh` and `yawl-state.sh` use the defensive pattern `${VAR:-.}` or `${VAR:-default}`. The other hooks should follow this pattern.

## Verification Checklist

- [x] Issue #372 fixed at symptom level (stop-hook-git-check.sh runs without unbound variable error)
- [x] Fix deployed in commit f46e1fe
- [x] Branch claude/fix-stop-hook-372o5 is up to date with origin
- [ ] Settings.json JSON syntax made consistent (escaped quotes removed) — **NOT FIXED**
- [ ] Proactive fix applied to hyper-validate.sh and verify-ttl-write.sh — **NOT FIXED**
- [ ] All hooks follow defensive env var pattern — **PARTIALLY DONE**

## Recommended Next Steps (NOT BLOCKING)

1. **Optional**: Fix settings.json to use consistent unquoted syntax on lines 83, 89, 108
2. **Optional**: Update hyper-validate.sh and verify-ttl-write.sh to use `set -eo pipefail` instead of `set -euo pipefail`
3. **Nice to have**: Add default handling to all hooks that reference env vars

## Summary

Issue #372 is **functionally resolved** by commit f46e1fe. The immediate cause (unbound variable with `set -u`) is fixed. However, the **root architectural issue** (escaped quotes in settings.json) and **latent vulnerabilities** in other hooks remain unaddressed. These are low-risk since the hooks don't trigger the specific failure condition, but they should be addressed for consistency and future resilience.
