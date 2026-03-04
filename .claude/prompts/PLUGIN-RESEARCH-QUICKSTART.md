# Plugin Research — Quick Start (Next Session)

**Time Budget**: ~4-6 hours
**Scope**: Discover all plugins, identify top 5 for adaptation
**Output**: Inventory + 1 working adapter proof-of-concept

---

## 60-Second Setup

```bash
# Go to project root
cd /home/user/yawl

# Verify environment
bash scripts/dx.sh compile  # Check build is GREEN

# Check what we built last session
cat .claude/receipts/team-activation-receipt.json | jq '.status'
```

---

## 3 Big Questions

1. **What plugins exist?** → Inventory them
2. **Which 5 matter most?** → Tier them
3. **How do we adapt one?** → Build adapter proof-of-concept

---

## Work Phases (in order)

### Phase 1: Discovery (1h)
Find all plugins using bash scripts:
```bash
# Plugin candidates
find . -name "*.java" | xargs grep -l "Plugin\|@Plugin"

# Config files
find . -name "*plugin*" -type f | grep -E "\.(xml|toml|properties)"

# Endpoints/servers
grep -r "MCP\|A2A\|ServerRegister" --include="*.java" | grep -i plugin
```

Output: `.claude/audit/plugin-inventory.md` (list all plugins found)

### Phase 2: Analysis (2h)
For each plugin:
- What does it do?
- Where is the code?
- What does it input/output?
- Can it be autonomous? (Y/N)

Output: `.claude/audit/plugin-analysis-summary.md` (all plugins analyzed)

### Phase 3: Tier Classification (1h)
Rank plugins by adaptation priority:
- **Tier 1** (must adapt): Security, validation, build
- **Tier 2** (should adapt): Monitoring, quality, docs
- **Tier 3** (nice-to-have): External integrations

Output: `.claude/audit/tier-classification.md`

### Phase 4: Adapt 1 Plugin (1.5h)
Pick easiest Tier 1 plugin and create:
1. Adapter wrapper (`.claude/adapters/plugin-<name>-adapter.sh`)
2. Test it with mock data
3. Document the pattern

Output: Working adapter + test suite

### Phase 5: Document Pattern (0.5h)
Create `.claude/doc/PLUGIN-ADAPTATION-GUIDE.md`:
- Step-by-step how to adapt a plugin
- Code examples
- Common pitfalls

---

## Quick Commands

```bash
# Search for plugins
grep -r "class.*Plugin\|interface.*Plugin" --include="*.java" | wc -l

# Find where plugins are registered
grep -r "register\|add\|factory" --include="*.java" | grep -i plugin | head -10

# List all .toml config files
find . -name "*.toml" | head -20

# Check plugin docs
find . -name "*.md" | xargs grep -l "plugin\|extension\|adapter" | head -10
```

---

## Expected Outputs

By end of session, you should have created:

```
.claude/
├── audit/
│   ├── plugin-inventory.md                    # All plugins listed
│   ├── plugin-analysis-summary.md             # Analysis of each
│   └── tier-classification.md                 # Tier 1/2/3 ranking
├── adapters/
│   ├── plugin-<chosen-name>-adapter.sh        # PROOF OF CONCEPT
│   └── plugin-<chosen-name>-adapter.test      # Passing test
└── doc/
    └── PLUGIN-ADAPTATION-GUIDE.md             # How to replicate

+ 1 commit showing all research
```

---

## Success Metrics

✅ **Session is a success if you can answer**:

1. How many plugins exist? (number)
2. Which 5 matter most? (names + why)
3. Which one did you adapt? (name + working test)
4. How would you adapt the next one? (step-by-step)

If you can answer all 4, you've succeeded.

---

## Helpful Context

**Last session built**:
- `analyze-errors.sh` — error detection pipeline
- `remediate-violations.sh` — auto-fix framework
- `decision-engine.sh` — intelligent routing
- `activate-permanent-team.sh` — 15 agents, 3 teams

**This session should**:
- Find all plugins
- See which ones can use those tools
- Adapt 1 to prove the pattern works

---

## Estimated Timeline

| Phase | Time | Output |
|-------|------|--------|
| Discovery | 1h | Inventory |
| Analysis | 2h | Plugin details |
| Classification | 1h | Tier ranking |
| Adaptation | 1.5h | Working adapter |
| Documentation | 0.5h | Guide |
| **Total** | **6h** | **All outputs** |

---

## Pro Tips

1. **Start with smallest plugin** → Easiest to understand
2. **Look for existing tests** → Shows expected input/output
3. **Check config files** → Reveals integration patterns
4. **Search for "register"** → Finds where plugins are loaded
5. **Build incrementally** → Test adapter with mock data first

---

**You'll need access to**:
- Full codebase (`/home/user/yawl`)
- Previous session's scripts (`.claude/scripts/`)
- Previous session's team structure (`.claude/.team-state/`)

All ready. Good luck! 🚀
