---
paths:
  - ".gitignore"
  - ".gitattributes"
  - "**/.git/**"
---

# Ω GIT Phase — Commit Discipline Rules

**Pre-condition**: `bash scripts/dx.sh all` must exit 0 before any commit.

---

## Branch Naming

Format: `claude/<desc>-<sessionId>`

```
claude/add-sla-tracking-iDs6b
claude/fix-ynetrunner-deadlock-RW7kN
claude/mcp-endpoint-IAQ06
```

- `<desc>`: kebab-case, 2-5 words, describes the change
- `<sessionId>`: last 5-6 chars of session URL (e.g., `IAQ06` from `session_IAQ06`)
- Never push to `master` directly — always via PR

---

## Commit Structure

### Single logical change per commit
One quantum of work = one commit. Do not batch unrelated changes.

### Commit message format
```
<type>: <imperative description> (≤72 chars)

[optional body — what and why, not how]

https://claude.ai/code/session_<sessionId>
```

Types: `feat` | `fix` | `refactor` | `test` | `docs` | `chore` | `perf`

### Examples
```
feat: add SLA deadline tracking to YWorkItem

Adds deadline field to YWorkItem and propagates through InterfaceB.
Persisted via Hibernate with migration V42_add_sla_deadline.sql.

https://claude.ai/code/session_IAQ06
```

```
fix: resolve YNetRunner deadlock on parallel split with empty join

Race condition in YNetRunner.fireTransition() when two threads
simultaneously reach an AND-join with no tokens. Fixed by acquiring
the net lock before token evaluation.

https://claude.ai/code/session_RW7kN
```

---

## Staging Rules

```bash
git add <specific-files>   # ✅ Explicit staging
git add .                  # ❌ Never — may include .env, secrets, binaries
git add -A                 # ❌ Never — same risk as above
```

Emit channels (safe to stage freely): `src/`, `test/`, `schema/`, `.claude/`
Ask-first channels (confirm before staging): root configs, `docs/`, `*.md`

---

## What NOT to Commit

| Pattern | Reason |
|---------|--------|
| `.env` files | Credentials |
| `*.class`, `*.jar` | Build artifacts (in .gitignore) |
| `/target/` | Maven output |
| `*.lastUpdated` | Maven cache poison markers |
| `settings.local.json` keys with secrets | Personal machine paths |
| Large test fixtures > 1MB | Use Git LFS or exampleSpecs/ |

---

## Hard Rules (Never Violate)

- `--force` / `--force-with-lease` on shared branches: **never**
- `git commit --amend` on pushed commits: **never** (rewrite history)
- `--no-verify` to skip hooks: **never** (fix the hook failure instead)
- Committing directly to `master`: **never** (always via PR)

---

## Pre-Push Checklist

```
[ ] bash scripts/dx.sh all → exit 0
[ ] git diff --stat shows only intended changes
[ ] No .env or credentials in staged files
[ ] Commit message includes session URL
[ ] Branch name matches claude/<desc>-<sessionId>
[ ] No merge commits (rebase onto master if behind)
```

---

## Merge Strategy

- **Squash merge** for single-task branches (clean history)
- **Merge commit** for multi-commit feature branches (preserve history)
- **Rebase** before opening PR if branch is >3 commits behind master:
  ```bash
  git fetch origin master
  git rebase origin/master
  ```
- Resolve conflicts in the rebased branch, never in master

---

## Recovery

### Committed wrong file
```bash
git reset HEAD~1            # Undo last commit (keep changes staged)
git restore --staged <file> # Unstage the wrong file
git commit -m "..."         # Re-commit without the wrong file
```

### Need to amend an unpushed commit
```bash
git commit --amend --no-edit    # Only if NOT pushed yet
```

### Branch diverged from master
```bash
git fetch origin master
git rebase origin/master        # Preferred over merge
# Fix conflicts, then:
git rebase --continue
```
