# Semantic Change Detection — Quick Start Guide

**TL;DR**: Use `DX_SEMANTIC_FILTER=1` to skip formatting-only changes in your build.

## For Code Agents

### Basic Usage

```bash
# Skip formatting-only changes automatically
DX_SEMANTIC_FILTER=1 bash scripts/dx.sh

# Output when no semantic changes:
# ✓ No semantic changes detected (all changes are formatting only)
# (exits with code 0 - success, no rebuild needed)

# If there ARE semantic changes:
# [SEMANTIC] yawl-engine — hash changed
# ... proceeds with normal build ...
```

### Workflow

**Before making changes:**
```bash
# Ensure semantic hashes are current (after last commit)
bash scripts/compute-semantic-hash.sh yawl-engine --cache
```

**While developing:**
```bash
# Quick compile + test loop with formatting-aware detection
DX_SEMANTIC_FILTER=1 bash scripts/dx.sh compile
DX_SEMANTIC_FILTER=1 bash scripts/dx.sh test
```

**Before committing:**
```bash
# Full validation without semantic filter (catch everything)
bash scripts/dx.sh all
```

## For Integration Engineers

### Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                                                         │
│  Git Changes Detected                                  │
│       ↓                                                │
│  detect_changed_modules()                             │
│       ↓                                                │
│  [Optional] filter_semantic_changes()                 │
│       ↓                 ↑                              │
│  ┌─────────────────────────────┐                      │
│  │ No semantic changes?        │                      │
│  │ → Exit 0 (skip build)       │                      │
│  └─────────────────────────────┘                      │
│       ↓                                                │
│  Maven compile [changed modules]                      │
│       ↓                                                │
│  Maven test                                           │
│       ↓                                                │
│  [NEW] Update semantic hashes in cache               │
│       ↓                                                │
│  Done!                                                │
│                                                        │
└─────────────────────────────────────────────────────────┘
```

### Cache Management

```bash
# Where hashes are stored
.yawl/cache/semantic-hashes/
├── .gitkeep
├── yawl-engine.json
├── yawl-elements.json
└── yawl-utilities.json

# View a module's hash
cat .yawl/cache/semantic-hashes/yawl-engine.json | jq '.'

# Clear all caches (forces rebuild)
rm -rf .yawl/cache/semantic-hashes/*

# See cache stats
ls -lah .yawl/cache/semantic-hashes/
```

### Hash Computation Flow

```
Java Source Files
    ↓
extract_semantic_structure()
    ├─ Parse package
    ├─ Extract imports (sorted)
    ├─ Extract class declarations
    ├─ Extract method signatures
    ├─ Extract field declarations
    └─ Extract annotations
    ↓
Canonical JSON (deterministic)
    ↓
SHA256 hash
    ↓
Compare with cached hash
    ├─ Match → No change (skip compile)
    └─ Mismatch → Change detected (rebuild)
```

## Common Scenarios

### Scenario 1: Whitespace-only changes (time saved: 95%)

```java
// Before
public void work() {
    System.out.println("OK");
}

// After (added newlines for readability)
public void work() {

    System.out.println("OK");

}
```

**With DX_SEMANTIC_FILTER=1**:
- Semantic hash: SAME
- Action: Skip compilation
- Time: 0.5s (just hash check)

**Without filter**:
- Action: Recompile
- Time: 5-10s

---

### Scenario 2: Method added (time to detect: 0.2s)

```java
// Before
public class YEngine { }

// After
public class YEngine {
    public void optimize() { }
}
```

**With DX_SEMANTIC_FILTER=1**:
- Semantic hash: DIFFERENT
- Action: Proceed with build
- Output: `[SEMANTIC] yawl-engine — hash changed`

---

### Scenario 3: Import added (time to detect: 0.2s)

```java
// Before
import java.util.*;

// After
import java.util.*;
import java.util.concurrent.*;
```

**With DX_SEMANTIC_FILTER=1**:
- Semantic hash: DIFFERENT
- Action: Proceed with build

---

### Scenario 4: Comment/string changed (time saved: 95%)

```java
// Before
public String VERSION = "1.0"; // Old version

// After
public String VERSION = "1.0"; // New version
```

**With DX_SEMANTIC_FILTER=1**:
- Semantic hash: SAME (string values not included)
- Action: Skip compilation
- Time: 0.5s

---

## Troubleshooting

### Issue: Scripts not executable

```bash
# Fix: Make scripts executable
chmod +x scripts/compute-semantic-hash.sh
chmod +x scripts/ast-differ.sh
chmod +x scripts/dx.sh
```

### Issue: Cache files not being created

```bash
# Verify cache directory exists
mkdir -p .yawl/cache/semantic-hashes

# Check permissions
ls -la .yawl/cache/

# Try manually caching a module
bash scripts/compute-semantic-hash.sh yawl-engine --cache
```

### Issue: Hash mismatch (expected same, got different)

```bash
# Clear cache and recompute
rm -rf .yawl/cache/semantic-hashes/*

# Force recompute all hashes
for module in yawl-engine yawl-elements yawl-utilities; do
    bash scripts/compute-semantic-hash.sh $module --cache
done
```

### Issue: DX_SEMANTIC_FILTER not recognized

```bash
# Verify dx.sh has been updated
grep -c "DX_SEMANTIC_FILTER" scripts/dx.sh
# Should return: 1 or more

# Show help to confirm
bash scripts/dx.sh -h | grep "SEMANTIC_FILTER"
# Should show: "DX_SEMANTIC_FILTER=1  Skip formatting..."
```

## Performance Tips

### For fastest iteration

```bash
# Enable semantic filtering + verbose output
DX_SEMANTIC_FILTER=1 DX_VERBOSE=1 bash scripts/dx.sh compile

# See which modules were cached:
# [SEMANTIC] yawl-engine — no semantic change (cache hit)
# [SEMANTIC] yawl-elements — hash changed
```

### For large modules

```bash
# Compute hash once, cache it
bash scripts/compute-semantic-hash.sh yawl-engine --cache

# Now builds use cached version (0.5s check vs 5-10s rebuild)
DX_SEMANTIC_FILTER=1 bash scripts/dx.sh
```

### For CI/CD

```bash
# Don't use semantic filter on CI (need full validation)
bash scripts/dx.sh all  # ← Always full build on CI

# Use semantic filter locally for dev feedback loop
DX_SEMANTIC_FILTER=1 bash scripts/dx.sh  # ← Dev only
```

## Advanced Usage

### Check what changed (semantically)

```bash
# See files and methods that changed
bash scripts/ast-differ.sh yawl-engine --since HEAD~1

# Output shows added/removed methods, affected tests, etc.
```

### Find affected tests

```bash
# Which tests import the changed class?
SHOW_IMPACT=1 bash scripts/ast-differ.sh yawl-engine --file yawl-engine/src/main/java/YEngine.java

# Output: List of test classes that need to run
```

### Manually compute hashes

```bash
# One-time computation (no caching)
bash scripts/compute-semantic-hash.sh yawl-engine

# Compute and save to cache
bash scripts/compute-semantic-hash.sh yawl-engine --cache

# Compare with previous cached version
bash scripts/compute-semantic-hash.sh yawl-engine --compare
```

## Integration Points

### With cache-config.sh

Semantic hashing is **orthogonal** to test result caching:

| System | What it caches | When cached | Used in dx.sh |
|--------|---|---|---|
| **Semantic hash** | Structure of source code | After compile | Change detection |
| **Test results** | Test pass/fail status | After test | Skip test re-run |

Both work together: semantic hash says "rebuild", then test cache says "skip these tests if unchanged".

### With test-impact-graph.sh

Semantic changes feed into the test impact graph:

```
Source file changed
    ↓ (detected by ast-differ.sh)
Classes/methods changed
    ↓ (looked up in impact graph)
Tests that import them
    ↓ (Phase 2 feature)
Selective test run
```

## Environment Variables

```bash
# Main switch (default: 0)
DX_SEMANTIC_FILTER=1        # Enable semantic filtering

# Debugging (default: 0)
DX_VERBOSE=1                # Show [SEMANTIC] log messages

# Related (don't change these normally)
SEMANTIC_HASH_ALGO=sha256   # Hash algorithm (internal)
SEMANTIC_CACHE_DIR=...      # Cache location (internal)
```

## FAQ

**Q: Does semantic filtering work on CI/CD?**
A: Yes, but not recommended. Use it locally for faster feedback, use full builds on CI to catch edge cases.

**Q: What if I format a file then revert it?**
A: Hash will be the same as original → no rebuild needed (even though files differ in git).

**Q: Can I use this with other build systems?**
A: Yes, the scripts work standalone. Integration is specific to dx.sh/Maven.

**Q: What about binary files, resources, config files?**
A: Only analyzes `.java` source files. Other changes always trigger rebuild.

**Q: How often should I clear the cache?**
A: Rarely. Only if hashes seem wrong or after Maven version upgrade.

**Q: Is the cache checked into git?**
A: No, `.yawl/cache/` is in `.gitignore`. It's local and per-developer.

---

## Getting Help

```bash
# Show full help for dx.sh
bash scripts/dx.sh -h

# See what a module's hash looks like
cat .yawl/cache/semantic-hashes/yawl-engine.json | jq '.'

# Run with verbose output to debug
DX_VERBOSE=1 DX_SEMANTIC_FILTER=1 bash scripts/dx.sh

# Check semantic hash for a specific module
bash scripts/compute-semantic-hash.sh yawl-engine --compare
```

---

**Last updated**: 2026-02-28
**Status**: Phase 1 complete, Phase 2 planned

https://claude.ai/code/session_01DNyAQmK3DSMsb5YJAFqsrL
