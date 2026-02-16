# YAWL .claude/ Directory Index

**Navigation guide for Claude Code agents and developers**

## 📋 Quick Start (Read These First)

1. **[CLAUDE.md](../CLAUDE.md)** - Main project instructions (mathematical notation)
2. **[README-QUICK.md](README-QUICK.md)** - 80/20 guide (30-second start)
3. **[BEST-PRACTICES-2026.md](BEST-PRACTICES-2026.md)** - Comprehensive 2026 best practices

## 📚 Documentation

### For Understanding YAWL

| File | Purpose | When to Read |
|------|---------|--------------|
| `README-QUICK.md` | 80/20 quick start | First time, getting oriented |
| `CAPABILITIES.md` | Detailed capabilities | Understanding what YAWL can do |
| `80-20-ANALYSIS.md` | Pareto principle analysis | Optimization decisions |

### For Working with Claude Code

| File | Purpose | When to Read |
|------|---------|--------------|
| `BEST-PRACTICES-2026.md` | 2026 best practices synthesis | Setting up new projects, learning patterns |
| `HYPER_STANDARDS.md` | Detailed guard examples | Understanding what's forbidden and why |
| `STANDARDS.md` | Coding standards | Writing code that passes validation |
| `ENFORCEMENT_SUMMARY.md` | How guards are enforced | Understanding hook system |

## 🤖 Agents (Specialized Roles)

Located in `.claude/agents/`

| Agent | File | Role | Model |
|-------|------|------|-------|
| **engineer** | `engineer.md` | Implement features, write code | sonnet |
| **validator** | `validator.md` | Run builds, verify tests | haiku |
| **reviewer** | `reviewer.md` | Code quality, security review | opus |

**Usage**: Invoke agents by assigning tasks in prompts:
```
ENGINEER AGENT: Implement OAuth flow
VALIDATOR AGENT: Run full test suite
REVIEWER AGENT: Security scan for SQL injection
```

## 🔧 Skills (Invocable Workflows)

Located in `.claude/skills/<skill-name>/SKILL.md`

| Skill | Command | Purpose | Time |
|-------|---------|---------|------|
| **yawl-build** | `/yawl-build [target]` | Build project (compile/buildAll) | ~18s |
| **yawl-test** | `/yawl-test` | Run unit tests | ~5s |
| **yawl-validate** | `/yawl-validate [spec]` | Validate XML specs | ~2s |

**Usage**: Type `/skill-name` in Claude Code to invoke.

## ⚙️ Hooks (Automated Verification)

Located in `.claude/hooks/`

### SessionStart Hook

**File**: `session-start.sh`
**Trigger**: When Claude Code session starts
**Actions**:
- Install Ant (if Claude Code Web)
- Configure H2 database
- Set up environment variables

### PostToolUse Hook

**File**: `hyper-validate.sh`
**Trigger**: After every Write/Edit
**Actions**:
- Validate against 14 anti-patterns (guards)
- Block if violations found (exit 2)
- Report specific violations to Claude

**Forbidden Patterns (H)**:
1. TODO/FIXME markers
2. Mock/stub method names
3. Mock/stub class names
4. Mock mode flags
5. Empty string returns
6. Null returns with stubs
7. No-op method bodies
8. Placeholder constants
9. Silent fallback patterns
10. Conditional mock behavior
11. getOrDefault with fake values
12. Early returns that skip logic
13. Log instead of throw
14. Mock framework imports in src/

### Stop Hook

**File**: `stop-hook-git-check.sh`
**Trigger**: When Claude finishes response
**Actions**:
- Check git status
- Warn if uncommitted changes
- Report clean/dirty state

## 📦 Build Scripts

Located in `.claude/`

| Script | Purpose | Usage |
|--------|---------|-------|
| `quick-start.sh` | Universal launcher | `./claude/quick-start.sh {test\|build\|run\|clean\|env}` |
| `smart-build.sh` | Auto-detecting build | `./claude/smart-build.sh` |
| `build.sh` | Direct Ant wrapper | `./claude/build.sh [target]` |
| `status.sh` | Environment status | `./claude/status.sh` |

## 🗂️ Configuration

**File**: `.claude/settings.json`

Contains:
- Project metadata
- Hook configurations
- Testing framework info
- Documentation references
- Best practices summary

## 🧠 Memory & Swarm

| Directory | Purpose | Status |
|-----------|---------|--------|
| `memory/` | Persistent memory config | Future feature |
| `swarm/` | Multi-agent topology | Future feature |

## 🎯 Workflow: How to Use This Setup

### 1. First Session

Read in order:
1. `README-QUICK.md` (2 minutes)
2. `../CLAUDE.md` (5 minutes)
3. Start working

### 2. Writing Code

1. Implement feature (engineer agent mindset)
2. PostToolUse hook validates automatically
3. If blocked: Fix violations, retry
4. Run `/yawl-test` to verify

### 3. Before Committing

1. `/yawl-build` - Verify compilation
2. `/yawl-test` - All tests pass
3. Git add/commit with session URL
4. Push to `claude/<desc>-<sessionId>` branch
5. Stop hook checks git state

### 4. Learning More

- Blocked by guards? → `HYPER_STANDARDS.md`
- Confused about architecture? → `../CLAUDE.md` Γ section
- Want to improve setup? → `BEST-PRACTICES-2026.md`

## 📊 Quick Reference Card

```
┌─────────────────────────────────────────────────────┐
│ YAWL Quick Reference                                │
├─────────────────────────────────────────────────────┤
│ Build:     ant compile (~18s)                       │
│ Test:      ant unitTest (~5s)                       │
│ Validate:  xmllint --schema ... spec.xml            │
│                                                     │
│ Guards:    14 anti-patterns blocked by hook         │
│ Agents:    engineer, validator, reviewer            │
│ Skills:    /yawl-build, /yawl-test, /yawl-validate │
│                                                     │
│ Before Commit:                                      │
│  1. ant compile && ant unitTest                     │
│  2. git add <files>                                 │
│  3. git commit -m "msg + session URL"               │
│  4. git push -u origin claude/<desc>-<id>           │
└─────────────────────────────────────────────────────┘
```

## 🔗 External References

- **YAWL Documentation**: https://yawlfoundation.github.io
- **Claude Code Docs**: https://code.claude.com/docs
- **Best Practices Source**: Official Claude Code + YAWL production patterns

## 🆘 Getting Help

1. **Build issues**: Check `session-start.sh` output
2. **Guard violations**: Read `hyper-validate.sh` error message
3. **Architecture questions**: See `../CLAUDE.md` Γ section
4. **Workflow unclear**: Read `BEST-PRACTICES-2026.md` Part 9

---

**Last Updated**: 2026-02-16
**Session**: https://claude.ai/code/session_012G4ZichzPon9aCvwkWB9Dc
