# Claude Code DX Quick Reference (80/20 Edition)

**Fastest paths to accomplishing common developer tasks.** Eliminates unnecessary steps.

---

## 🚀 **Common Workflows** (Copy-Paste Ready)

### MCP Development Loop (15s)
```bash
bash scripts/mcp-quick.sh          # compile + test MCP module only
bash scripts/mcp-quick.sh --watch  # auto-rerun on file changes
bash scripts/mcp-quick.sh --full   # compile all + test MCP
```

### Session Status (2s)
```bash
bash scripts/session-info.sh       # show: branch, changes, commits
bash scripts/session-info.sh --quick  # one-line summary
```

### Are Facts Stale? (1s)
```bash
bash scripts/facts-quick-check.sh          # check age
bash scripts/facts-quick-check.sh --refresh # auto-refresh if needed
```

### Full Build (2-5m)
```bash
bash scripts/dx.sh all          # compile + test ALL modules
bash scripts/dx.sh compile all  # compile only (faster)
```

### Changed Modules Only (10-30s)
```bash
bash scripts/dx.sh              # compile + test CHANGED modules
bash scripts/dx.sh compile      # compile changed only
bash scripts/dx.sh test         # test changed only
```

### Specific Module
```bash
bash scripts/dx.sh -pl yawl-engine              # one module
bash scripts/dx.sh -pl yawl-engine,yawl-stateless  # multiple
```

### Git Commit & Push (30s)
```bash
git status                # see what changed
git diff                  # review changes
git add <files>           # stage specific files (NOT git add .)
git commit -m "message"   # commit with message
git push -u origin <branch>  # push to feature branch
```

### Make a Feature Branch
```bash
git checkout -b claude/my-feature-$(date +%s)  # auto-timestamped
bash scripts/session-info.sh                    # verify branch name
git push -u origin HEAD                         # push empty branch
```

---

## 📋 **File Locations** (Don't Grep - Know Where It Is)

| What | Where |
|------|-------|
| Build config | `pom.xml`, `yawl-*/pom.xml` |
| MCP code | `src/.../integration/mcp/**` |
| MCP tests | `test/.../integration/mcp/**` |
| Engine | `src/.../engine/YEngine.java` |
| Rules | `.claude/rules/` |
| Facts | `docs/v6/latest/facts/` |
| Build script | `scripts/dx.sh` |
| Session helpers | `scripts/session-info.sh` |
| MCP quick loop | `scripts/mcp-quick.sh` |

---

## 🔧 **Most Common Fixes** (Pattern Matching)

### "Can't find class X"
→ Check `pom.xml` for missing dependency or module exclusion
→ Look in `yawl-integration/pom.xml` line 267+ for excludes

### "Hook blocked write/edit"
→ Run: `cat .claude/HYPER_STANDARDS.md` (see guard patterns)
→ Fix: replace TODO/FIXME/mock/stub with real implementation or `throw UnsupportedOperationException`

### "Module won't compile"
→ Try: `bash scripts/dx.sh compile -pl <module>`
→ Show errors: `bash scripts/dx.sh -pl <module>` (with verbose)
→ Check: are facts stale? `bash scripts/facts-quick-check.sh`

### "Tests failing"
→ Check: `bash scripts/dx.sh test -pl <module>`
→ Coverage: `bash scripts/dx.sh test all` then check `target/jacoco-reports/`
→ Single test: `mvn test -pl <module> -Dtest=YourTest`

### "Git branch issues"
→ Check: `bash scripts/session-info.sh` (shows commits ahead/behind)
→ Sync: `git fetch origin && git rebase origin/main` (never force-push!)

---

## ⚡ **Speed Tips** (Saved Seconds = Saved Hours)

1. **Use `mcp-quick.sh` for MCP work** (compile only = 5s vs full build = 5m)
2. **Check `session-info.sh` before pushing** (avoid "wrong branch" mistakes)
3. **Use `dx.sh compile` for syntax errors** (faster than `dx.sh` which includes tests)
4. **Facts are 1KB = 50 tokens saved** (use facts, don't grep!)
5. **Stage specific files** (`git add file.java` not `git add .`)

---

## 📚 **Deep Dives** (When You Need More)

- **Build Performance**: `.claude/BUILD-PERFORMANCE.md`
- **MCP Architecture**: `.claude/rules/integration/mcp-a2a-conventions.md`
- **Design Patterns**: `.claude/ARCHITECTURE-PATTERNS-JAVA25.md`
- **Standards**: `.claude/HYPER_STANDARDS.md`
- **Rules for your path**: `.claude/rules/` (auto-loaded by scope)

---

## 🎯 **The 80/20 Rule for YAWL Dev**

**20% of knowledge that gives 80% of productivity:**

1. Know where files are (above ⬆️)
2. Use fast build scripts (mcp-quick.sh, session-info.sh)
3. Never force-push; only commit on `claude/*` branches
4. Read facts instead of grepping (100x faster)
5. Use rules as your guardrails (guards + invariants = 0 rewrites)

**That's it.** Everything else is details.

---

## 🆘 **Quick Help**

```bash
bash scripts/dx.sh -h                      # build options
bash scripts/mcp-quick.sh -h               # MCP quick loop
bash scripts/session-info.sh -h            # session status
bash scripts/facts-quick-check.sh -h       # fact checking
grep -r "FIXME\|TODO" src/ --include="*.java" | head  # find incomplete code
```

**Last updated:** 2026-02-20
**For detailed help:** See CLAUDE.md (top of repo)
