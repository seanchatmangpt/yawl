# Debug YAWL with the Observatory System

**Goal**: Learn to use the Observatory—a codebase instrumentation system—to debug YAWL quickly without grepping or reading dozens of source files.

**Compression**: Observatory facts cost ~50 tokens vs grep/grep combinations costing ~5000 tokens. **100× efficiency gain.**

---

## Table of Contents

1. [Observatory Overview](#observatory-overview)
2. [Decision Tree: When to Use Observatory](#decision-tree-when-to-use-observatory)
3. [Running Observatory](#running-observatory)
4. [Fact Files Reference](#fact-files-reference)
5. [Five Core Debugging Workflows](#five-core-debugging-workflows)
   - [Workflow 1: Finding Dependency Conflicts](#workflow-1-finding-dependency-conflicts)
   - [Workflow 2: Understanding Module Relationships](#workflow-2-understanding-module-relationships)
   - [Workflow 3: Finding Duplicate Code](#workflow-3-finding-duplicate-code)
   - [Workflow 4: Analyzing Test Coverage Gaps](#workflow-4-analyzing-test-coverage-gaps)
   - [Workflow 5: Resolving Maven Hazards](#workflow-5-resolving-maven-hazards)
6. [Advanced Queries with jq](#advanced-queries-with-jq)
7. [Watermark Protocol: Fact Freshness](#watermark-protocol-fact-freshness)
8. [Observatory in Development](#observatory-in-development)
9. [Troubleshooting Observatory](#troubleshooting-observatory)

---

## Observatory Overview

### What is Observatory?

The **Observatory** (Ψ in the YAWL system) is a structured instrument for understanding your codebase without manual exploration. It generates **fact files** (JSON) that answer common questions in 50 tokens instead of 5000.

Key principle: **Observe ≺ Act ≺ Assert**

When you encounter a question like "Which modules depend on yawl-engine?" the Observatory provides:
- Direct, structured answer (1 fact file, ~50 tokens)
- Instead of: grepping 736 files, reading 150+ KB, context explosion

### Why Observatory Matters

| Problem | Without Observatory | With Observatory |
|---------|-------------------|-------------------|
| "Where do conflicts occur in deps?" | grep + mvn tree + analysis (5000 tokens) | Read `facts/deps-conflicts.json` (50 tokens) |
| "What's the build order?" | mvn dependency:tree + parsing | Read `facts/reactor.json` (75 tokens) |
| "Which tests cover module X?" | find + filter + read files (500+ tokens) | Read `facts/tests.json`, filter by module (80 tokens) |
| "Are there duplicate classes?" | find + sort + dedup (800 tokens) | Read `facts/duplicates.json` (40 tokens) |
| "Is feature X enabled?" | Read pom.xml + understand profiles (400 tokens) | Read `facts/gates.json` (60 tokens) |

**Total context saved per session: 500–2000 tokens = ~5 questions answered faster.**

### The Nine Fact Files

The Observatory generates nine machine-readable JSON fact files:

| Fact File | Size | Purpose | Use When |
|-----------|------|---------|----------|
| `modules.json` | ~8 KB | Module registry with metadata | Planning changes, understanding layers |
| `gates.json` | ~12 KB | Quality gates and CI/CD rules | Integrating with build pipeline |
| `deps-conflicts.json` | ~15 KB | Dependency conflict zones | Debugging version mismatches |
| `reactor.json` | ~6 KB | Build order and phase structure | Understanding build sequence |
| `shared-src.json` | ~18 KB | Source file ownership | Before editing shared code |
| `tests.json` | ~20 KB | Test distribution and profiles | Planning test coverage |
| `dual-family.json` | ~4 KB | Stateful ↔ stateless mapping | Editing YNetRunner, YEngine |
| `duplicates.json` | ~2 KB | Duplicate class detection | Refactoring, DRY violations |
| `maven-hazards.json` | ~5 KB | M2 cache and build hazards | Debugging build failures |

**Total**: ~90 KB of structured fact data. ~100 tokens per file = ~900 tokens for full analysis.

### Watermark-Based TTL

Facts are **stale-resistant** via watermarks:

```json
{
  "metadata": {
    "generated_at": "2026-02-28T15:00:00Z",
    "inputs_sha256": "abc123...",
    "outputs_sha256": "def456..."
  }
}
```

When you run `bash scripts/observatory/observatory.sh` again, the system:
1. Computes hash of inputs (pom.xml, source tree)
2. Compares against previous `inputs_sha256`
3. **Skips generation if unchanged** (0 cost, 0 time)
4. **Regenerates if changed** (17 seconds, 0 tokens)

---

## Decision Tree: When to Use Observatory

```
You have a question about the codebase
  ↓
Is it one of these 9 common questions?
  ├─ "What modules exist?" → facts/modules.json
  ├─ "What's the build order?" → facts/reactor.json
  ├─ "Who owns which source files?" → facts/shared-src.json
  ├─ "Are there dependency conflicts?" → facts/deps-conflicts.json
  ├─ "Which tests cover module X?" → facts/tests.json
  ├─ "Is feature Y enabled?" → facts/gates.json
  ├─ "What's the stateful/stateless split?" → facts/dual-family.json
  ├─ "Are there duplicate classes?" → facts/duplicates.json
  └─ "What Maven hazards exist?" → facts/maven-hazards.json
  ↓ YES → Read 1 fact file (50 tokens, 30 sec)
  ↓ NO → grep or read source (500–5000 tokens, 5–15 min)
  ↓
Done. If facts are stale, re-run Observatory (~17 sec).
```

### When NOT to Use Observatory

- **Very recent change** (< 5 min): Facts may be stale, re-run first
- **Esoteric question**: If the question isn't in the 9 categories above, build a new instrument
- **Deep implementation detail**: If you need the actual code, read source files directly
- **Performance profiling**: Facts don't include runtime metrics; use JFR

---

## Running Observatory

### Basic Command

Generate fresh facts:

```bash
bash scripts/observatory/observatory.sh
```

**Output** (in ~17 seconds):
- `docs/v6/latest/INDEX.md` — Full manifest
- `docs/v6/latest/facts/` — 9 JSON fact files
- `docs/v6/latest/diagrams/` — 7 Mermaid diagrams
- `docs/v6/latest/receipts/observatory.json` — Provenance receipt

### Fact-Only Mode (Faster)

If you only need facts (skip diagrams):

```bash
bash scripts/observatory/observatory.sh --facts
```

**Time**: ~13 seconds (vs 17 for full run)

### Diagrams-Only Mode

If you only need visualizations:

```bash
bash scripts/observatory/observatory.sh --diagrams
```

**Time**: ~2 seconds

### Read Cached Facts (Zero Cost)

View previously-cached facts without regeneration:

```bash
bash .claude/scripts/read-observatory.sh facts
```

This reads `docs/v6/latest/facts/` without touching pom.xml or source tree.

### Interpreting the Receipt

After running, check `docs/v6/latest/receipts/observatory.json`:

```json
{
  "status": "GREEN",
  "run_id": "obs-20260228-150000",
  "timing_ms": 17500,
  "inputs": {
    "root_pom_sha256": "abc123..."
  },
  "outputs": {
    "facts_sha256": "def456...",
    "diagrams_sha256": "ghi789...",
    "index_sha256": "jkl012..."
  },
  "refusals": [],
  "warnings": []
}
```

- **status=GREEN**: All facts generated successfully
- **status=YELLOW**: Some facts generated, some warnings
- **status=RED**: Generation failed; check `refusals[]` and `warnings[]`
- **refusals[]**: Data the Observatory couldn't produce (e.g., external API unavailable)
- **warnings[]**: Data that may be inaccurate (e.g., stale cache)

---

## Fact Files Reference

### 1. modules.json — Module Registry

**What it answers**: What modules exist? Who depends on whom?

**Structure**:
```json
{
  "modules": [
    {
      "name": "yawl-engine",
      "path": "yawl-engine/",
      "layer": 1,
      "dependencies": ["yawl-utilities", "yawl-security"],
      "dependents": ["yawl-integration", "yawl-worklet-service"],
      "is_core": true,
      "maven_coordinates": "org.yawl:yawl-engine:6.0.0"
    },
    {
      "name": "yawl-utilities",
      "path": "yawl-utilities/",
      "layer": 0,
      "dependencies": [],
      "dependents": ["yawl-engine", "yawl-security"],
      "is_core": true,
      "maven_coordinates": "org.yawl:yawl-utilities:6.0.0"
    }
  ],
  "metadata": {
    "total_modules": 12,
    "core_modules": 6,
    "generated_at": "2026-02-28T15:00:00Z"
  }
}
```

**Example jq queries**:
```bash
# List all modules
jq '.modules[] | .name' docs/v6/latest/facts/modules.json

# Find dependencies of yawl-engine
jq '.modules[] | select(.name=="yawl-engine") | .dependencies' \
  docs/v6/latest/facts/modules.json

# Find all core modules (layer 0)
jq '.modules[] | select(.layer==0)' docs/v6/latest/facts/modules.json

# Count modules by layer
jq '.modules | group_by(.layer) | map({layer: .[0].layer, count: length})' \
  docs/v6/latest/facts/modules.json
```

---

### 2. gates.json — Quality Gates

**What it answers**: What quality gates are active? When do they run?

**Structure**:
```json
{
  "gates": [
    {
      "name": "compile-green",
      "phase": "Λ",
      "trigger": "always",
      "command": "dx.sh compile",
      "modules": ["all"],
      "timeout_minutes": 15,
      "on_failure": "halt"
    },
    {
      "name": "guards-h",
      "phase": "H",
      "trigger": "generate",
      "command": "ggen validate --phase guards",
      "modules": ["yawl-generator"],
      "timeout_minutes": 5,
      "on_failure": "halt"
    },
    {
      "name": "invariants-q",
      "phase": "Q",
      "trigger": "code-change",
      "command": "mvn clean verify -P analysis",
      "modules": ["all"],
      "timeout_minutes": 30,
      "on_failure": "halt"
    }
  ],
  "metadata": {
    "total_gates": 14,
    "enabled_gates": 12,
    "generated_at": "2026-02-28T15:00:00Z"
  }
}
```

**Example jq queries**:
```bash
# List all gates
jq '.gates[] | .name' docs/v6/latest/facts/gates.json

# Find gates that run on code change
jq '.gates[] | select(.trigger=="code-change")' docs/v6/latest/facts/gates.json

# Find gates for a specific phase
jq '.gates[] | select(.phase=="H")' docs/v6/latest/facts/gates.json

# Count gates by trigger type
jq '.gates | group_by(.trigger) | map({trigger: .[0].trigger, count: length})' \
  docs/v6/latest/facts/gates.json
```

---

### 3. deps-conflicts.json — Dependency Conflicts

**What it answers**: Where are version conflicts? What versions are in conflict?

**Structure**:
```json
{
  "conflicts": [
    {
      "library": "junit",
      "versions": ["4.13.2", "4.12"],
      "resolved_to": "4.13.2",
      "modules": {
        "yawl-core": "4.13.2",
        "yawl-legacy": "4.12"
      },
      "severity": "medium"
    },
    {
      "library": "org.slf4j:slf4j-api",
      "versions": ["1.7.32", "2.0.0"],
      "resolved_to": "2.0.0",
      "modules": {
        "yawl-integration": "2.0.0",
        "yawl-security": "1.7.32"
      },
      "severity": "high"
    }
  ],
  "metadata": {
    "total_conflicts": 5,
    "high_severity": 2,
    "generated_at": "2026-02-28T15:00:00Z"
  }
}
```

**Example jq queries**:
```bash
# List all conflicts
jq '.conflicts[] | {library: .library, versions: .versions}' \
  docs/v6/latest/facts/deps-conflicts.json

# Find high-severity conflicts
jq '.conflicts[] | select(.severity=="high")' docs/v6/latest/facts/deps-conflicts.json

# Show which modules use non-standard versions
jq '.conflicts[] | .modules | to_entries[] | select(.value != .modules[])' \
  docs/v6/latest/facts/deps-conflicts.json
```

---

### 4. reactor.json — Build Order

**What it answers**: What's the build sequence? Which modules build first?

**Structure**:
```json
{
  "phases": [
    {
      "phase": 0,
      "name": "foundation",
      "modules": ["yawl-utilities", "yawl-security"],
      "can_build_in_parallel": true,
      "dependencies": []
    },
    {
      "phase": 1,
      "name": "core",
      "modules": ["yawl-engine", "yawl-elements"],
      "can_build_in_parallel": true,
      "dependencies": ["yawl-utilities", "yawl-security"]
    },
    {
      "phase": 2,
      "name": "integration",
      "modules": ["yawl-integration", "yawl-mcp"],
      "can_build_in_parallel": true,
      "dependencies": ["yawl-engine"]
    }
  ],
  "metadata": {
    "total_phases": 3,
    "critical_path_length": 3,
    "generated_at": "2026-02-28T15:00:00Z"
  }
}
```

**Example jq queries**:
```bash
# Show build phases in order
jq '.phases[] | {phase: .phase, modules: .modules}' \
  docs/v6/latest/facts/reactor.json

# Find which phase a module is in
jq '.phases[] | select(.modules | contains(["yawl-engine"]))' \
  docs/v6/latest/facts/reactor.json

# List parallel build opportunities
jq '.phases[] | select(.can_build_in_parallel==true)' \
  docs/v6/latest/facts/reactor.json
```

---

### 5. shared-src.json — Source File Ownership

**What it answers**: Which module owns which source files? Are there shared files?

**Structure**:
```json
{
  "files": [
    {
      "path": "yawl-engine/src/main/java/org/yawl/engine/YEngine.java",
      "module": "yawl-engine",
      "owner": "yawl-engine",
      "is_shared": false,
      "size_bytes": 12500,
      "last_modified": "2026-02-20T10:00:00Z"
    },
    {
      "path": "yawl-utilities/src/main/java/org/yawl/util/YAWLVersion.java",
      "module": "yawl-utilities",
      "owner": "yawl-utilities",
      "is_shared": true,
      "shared_with": ["yawl-engine", "yawl-integration", "yawl-worklet"],
      "size_bytes": 1200,
      "last_modified": "2026-02-15T14:30:00Z"
    }
  ],
  "metadata": {
    "total_files": 410,
    "shared_files": 18,
    "generated_at": "2026-02-28T15:00:00Z"
  }
}
```

**Example jq queries**:
```bash
# Find all files in a module
jq '.files[] | select(.module=="yawl-engine")' \
  docs/v6/latest/facts/shared-src.json

# List shared files
jq '.files[] | select(.is_shared==true)' \
  docs/v6/latest/facts/shared-src.json

# Find files shared with specific module
jq '.files[] | select(.shared_with | contains(["yawl-integration"]))' \
  docs/v6/latest/facts/shared-src.json

# Show which files could cause conflicts
jq '.files[] | select(.shared_with | length > 2)' \
  docs/v6/latest/facts/shared-src.json
```

---

### 6. tests.json — Test Distribution

**What it answers**: How many tests? Which modules have tests? Are there coverage gaps?

**Structure**:
```json
{
  "summary": {
    "total_test_files": 84,
    "total_test_methods": 1247,
    "junit5_count": 72,
    "junit4_count": 12,
    "coverage_percentage": 68.5
  },
  "modules": [
    {
      "name": "yawl-engine",
      "test_files": 12,
      "test_methods": 186,
      "junit_version": "5.x",
      "coverage_percentage": 78.2,
      "test_categories": {
        "unit": 120,
        "integration": 45,
        "e2e": 21
      }
    },
    {
      "name": "yawl-utilities",
      "test_files": 8,
      "test_methods": 94,
      "junit_version": "5.x",
      "coverage_percentage": 92.1,
      "test_categories": {
        "unit": 94,
        "integration": 0,
        "e2e": 0
      }
    }
  ],
  "metadata": {
    "generated_at": "2026-02-28T15:00:00Z"
  }
}
```

**Example jq queries**:
```bash
# List all modules with test coverage
jq '.modules[] | {name: .name, coverage: .coverage_percentage}' \
  docs/v6/latest/facts/tests.json

# Find modules with low coverage (< 70%)
jq '.modules[] | select(.coverage_percentage < 70)' \
  docs/v6/latest/facts/tests.json

# Show test distribution by category
jq '.modules[] | {name: .name, categories: .test_categories}' \
  docs/v6/latest/facts/tests.json

# Find modules missing integration tests
jq '.modules[] | select(.test_categories.integration == 0)' \
  docs/v6/latest/facts/tests.json
```

---

### 7. dual-family.json — Stateful ↔ Stateless Mapping

**What it answers**: Which classes have stateful and stateless variants?

**Structure**:
```json
{
  "families": [
    {
      "concept": "Net Runner",
      "stateful": {
        "class": "YNetRunner",
        "module": "yawl-engine",
        "file": "src/main/java/org/yawl/engine/YNetRunner.java"
      },
      "stateless": {
        "class": "YNetRunnerStateless",
        "module": "yawl-stateless-engine",
        "file": "src/main/java/org/yawl/stateless/YNetRunnerStateless.java"
      },
      "sync_status": "in-sync"
    },
    {
      "concept": "Work Item",
      "stateful": {
        "class": "YWorkItem",
        "module": "yawl-elements",
        "file": "src/main/java/org/yawl/elements/YWorkItem.java"
      },
      "stateless": {
        "class": "YWorkItemStateless",
        "module": "yawl-stateless-engine",
        "file": "src/main/java/org/yawl/stateless/YWorkItemStateless.java"
      },
      "sync_status": "diverged"
    }
  ],
  "metadata": {
    "total_families": 8,
    "in_sync": 6,
    "diverged": 2,
    "generated_at": "2026-02-28T15:00:00Z"
  }
}
```

**Example jq queries**:
```bash
# List all stateful/stateless families
jq '.families[] | {concept: .concept, sync: .sync_status}' \
  docs/v6/latest/facts/dual-family.json

# Find families that are diverged
jq '.families[] | select(.sync_status=="diverged")' \
  docs/v6/latest/facts/dual-family.json

# Show which modules have stateful classes
jq '.families[] | {concept: .concept, stateful_module: .stateful.module}' \
  docs/v6/latest/facts/dual-family.json
```

---

### 8. duplicates.json — Duplicate Code Detection

**What it answers**: Are there duplicate classes or functions?

**Structure**:
```json
{
  "duplicates": [
    {
      "name": "YLogPredicateFactory",
      "occurrences": 2,
      "locations": [
        {
          "module": "yawl-worklet-service",
          "file": "src/main/java/org/yawl/worklet/YLogPredicateFactory.java"
        },
        {
          "module": "yawl-integration",
          "file": "src/main/java/org/yawl/integration/YLogPredicateFactory.java"
        }
      ],
      "severity": "high",
      "remediation": "Extract to shared utility in yawl-utilities"
    }
  ],
  "metadata": {
    "total_duplicates": 4,
    "high_severity": 2,
    "generated_at": "2026-02-28T15:00:00Z"
  }
}
```

**Example jq queries**:
```bash
# List all duplicates
jq '.duplicates[] | .name' docs/v6/latest/facts/duplicates.json

# Find high-severity duplicates
jq '.duplicates[] | select(.severity=="high")' \
  docs/v6/latest/facts/duplicates.json

# Show which modules have duplicates
jq '.duplicates[] | .locations[] | .module' \
  docs/v6/latest/facts/duplicates.json | sort -u
```

---

### 9. maven-hazards.json — Maven and Build Hazards

**What it answers**: What Maven hazards exist? (version conflicts, transitive deps, M2 issues)

**Structure**:
```json
{
  "hazards": [
    {
      "type": "version_conflict",
      "severity": "high",
      "library": "org.junit.jupiter:junit-jupiter-api",
      "affected_modules": ["yawl-core", "yawl-legacy"],
      "description": "Multiple versions in dependency tree",
      "resolution": "Enforce unified version in dependencyManagement"
    },
    {
      "type": "transitive_exclusion",
      "severity": "medium",
      "library": "org.slf4j:slf4j-jcl",
      "reason": "Excluded to prevent logging bridge conflicts",
      "excluded_in_modules": ["yawl-integration"]
    },
    {
      "type": "m2_cache_hazard",
      "severity": "low",
      "description": "Stale artifacts in ~/.m2/repository/",
      "resolution": "Run 'mvn clean' or remove ~/.m2/repository/org/yawl/"
    }
  ],
  "metadata": {
    "total_hazards": 7,
    "high_severity": 2,
    "generated_at": "2026-02-28T15:00:00Z"
  }
}
```

**Example jq queries**:
```bash
# List all hazards
jq '.hazards[] | {type: .type, severity: .severity}' \
  docs/v6/latest/facts/maven-hazards.json

# Find high-severity hazards
jq '.hazards[] | select(.severity=="high")' \
  docs/v6/latest/facts/maven-hazards.json

# Show version conflicts
jq '.hazards[] | select(.type=="version_conflict")' \
  docs/v6/latest/facts/maven-hazards.json
```

---

## Five Core Debugging Workflows

Each workflow takes 5–10 minutes and solves a real problem. All use copy-paste commands.

---

### Workflow 1: Finding Dependency Conflicts

**Problem**: Build is failing with "Multiple versions in conflict" error. Where are the conflicts?

**Step 1: Run Observatory**
```bash
bash scripts/observatory/observatory.sh --facts
```

**Step 2: Check for Conflicts**
```bash
jq '.conflicts[] | {library: .library, versions: .versions, severity: .severity}' \
  docs/v6/latest/facts/deps-conflicts.json
```

**Example Output**:
```json
{
  "library": "org.slf4j:slf4j-api",
  "versions": ["1.7.32", "2.0.0"],
  "severity": "high"
}
```

**Step 3: Find Which Modules Caused It**
```bash
jq '.conflicts[] | select(.library=="org.slf4j:slf4j-api") | .modules' \
  docs/v6/latest/facts/deps-conflicts.json
```

**Example Output**:
```json
{
  "yawl-integration": "2.0.0",
  "yawl-security": "1.7.32"
}
```

**Step 4: Understand the Problem**

You now know:
- Library: `org.slf4j:slf4j-api`
- Conflict: `yawl-integration` wants 2.0.0, but `yawl-security` wants 1.7.32
- Impact: Severity is HIGH

**Step 5: Fix Pattern**

Option A: Force version in `pom.xml` dependencyManagement:
```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-api</artifactId>
      <version>2.0.0</version>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Option B: Exclude the older version:
```xml
<dependency>
  <groupId>org.yawl</groupId>
  <artifactId>yawl-security</artifactId>
  <version>6.0.0</version>
  <exclusions>
    <exclusion>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-api</artifactId>
    </exclusion>
  </exclusions>
</dependency>
```

**Step 6: Verify**

After the fix:
```bash
bash scripts/observatory/observatory.sh --facts
jq '.conflicts | length' docs/v6/latest/facts/deps-conflicts.json
```

Should show fewer conflicts.

---

### Workflow 2: Understanding Module Relationships

**Problem**: You're making changes to `yawl-engine` and need to know what depends on it. What will break?

**Step 1: List All Modules**
```bash
jq '.modules[] | {name: .name, dependencies: .dependencies, dependents: .dependents}' \
  docs/v6/latest/facts/modules.json
```

**Step 2: Find Dependents of Specific Module**
```bash
jq '.modules[] | select(.name=="yawl-engine") | .dependents' \
  docs/v6/latest/facts/modules.json
```

**Example Output**:
```json
["yawl-integration", "yawl-mcp", "yawl-worklet-service", "yawl-stateless-engine"]
```

**Step 3: Understand the Impact Chain**

Now you know that if you break the API of `yawl-engine`, you will break:
1. `yawl-integration` (direct)
2. `yawl-mcp` (direct)
3. `yawl-worklet-service` (direct)
4. `yawl-stateless-engine` (direct)

**Step 4: Plan Testing**

You must test all 4 dependents when making `yawl-engine` changes.

**Step 5: Backward Compatibility Check**

Are there other modules in the same layer?
```bash
jq '.modules[] | select(.layer==1)' docs/v6/latest/facts/modules.json | jq '.name'
```

If yes, ensure your changes maintain backward compatibility.

**Visualization**: Check `docs/v6/latest/diagrams/10-maven-reactor.mmd` for a visual DAG.

---

### Workflow 3: Finding Duplicate Code

**Problem**: You suspect there's duplicated code. Refactor it or leave it?

**Step 1: Check for Duplicates**
```bash
jq '.duplicates[] | {name: .name, severity: .severity, locations: .locations | map(.module)}' \
  docs/v6/latest/facts/duplicates.json
```

**Example Output**:
```json
{
  "name": "YLogPredicateFactory",
  "severity": "high",
  "locations": ["yawl-worklet-service", "yawl-integration"]
}
```

**Step 2: Understand the Scope**

High severity = class is large or complex. Refactoring will have high payoff.

**Step 3: Follow the Remediation**

Check the `remediation` field:
```bash
jq '.duplicates[] | select(.name=="YLogPredicateFactory") | .remediation' \
  docs/v6/latest/facts/duplicates.json
```

Example: `"Extract to shared utility in yawl-utilities"`

**Step 4: Execute the Fix**

1. Copy `YLogPredicateFactory` from `yawl-worklet-service` to `yawl-utilities/src/main/java/org/yawl/util/`
2. Add to `yawl-utilities/pom.xml` if needed
3. Update imports in both `yawl-worklet-service` and `yawl-integration`
4. Run tests:
   ```bash
   mvn clean verify -pl yawl-utilities,yawl-worklet-service,yawl-integration
   ```
5. Commit with message:
   ```
   Extract YLogPredicateFactory to shared utility (yawl-utilities)

   Previously duplicated in:
   - yawl-worklet-service
   - yawl-integration

   Now consolidated in yawl-utilities for DRY compliance.
   ```

**Step 5: Verify**

Re-run Observatory:
```bash
bash scripts/observatory/observatory.sh --facts
jq '.duplicates | map(select(.name=="YLogPredicateFactory")) | length' \
  docs/v6/latest/facts/duplicates.json
```

Should return 0 duplicates.

---

### Workflow 4: Analyzing Test Coverage Gaps

**Problem**: Build is passing but there are untested modules. Which ones?

**Step 1: Show Coverage by Module**
```bash
jq '.modules[] | {name: .name, coverage: .coverage_percentage, test_files: .test_files}' \
  docs/v6/latest/facts/tests.json
```

**Step 2: Find Low-Coverage Modules**
```bash
jq '.modules[] | select(.coverage_percentage < 75) | {name: .name, coverage: .coverage_percentage}' \
  docs/v6/latest/facts/tests.json
```

**Example Output**:
```json
{
  "name": "yawl-integration",
  "coverage": 42.5
}
```

**Step 3: Check Test Category Distribution**
```bash
jq '.modules[] | select(.name=="yawl-integration") | .test_categories' \
  docs/v6/latest/facts/tests.json
```

**Example Output**:
```json
{
  "unit": 15,
  "integration": 2,
  "e2e": 0
}
```

**Analysis**:
- Only 15 unit tests for a critical module
- Only 2 integration tests (should be more for integration module)
- 0 end-to-end tests

**Step 4: Plan Coverage Improvement**

1. Unit tests: Add 10 more (target: 25+)
2. Integration tests: Add 8 more (target: 10+)
3. E2E tests: Add 2 (target: 2+)

**Step 5: Check Which Modules Need Integration Tests**
```bash
jq '.modules[] | select(.test_categories.integration == 0) | .name' \
  docs/v6/latest/facts/tests.json
```

These are high-risk modules that integrate with external systems but have no integration tests.

**Step 6: Execute Test Plan**

Create `src/test/java/org/yawl/integration/YIntegrationModuleIT.java`:
```java
import org.junit.jupiter.api.Test;

public class YIntegrationModuleIT {
    @Test
    void shouldConnectToMCP() throws Exception {
        // Test MCP connection
    }

    @Test
    void shouldHandleA2ARequest() throws Exception {
        // Test A2A protocol
    }
}
```

Run tests:
```bash
mvn clean verify -pl yawl-integration -Dgroups=integration
```

**Step 7: Verify Improvement**

```bash
bash scripts/observatory/observatory.sh --facts
jq '.modules[] | select(.name=="yawl-integration")' docs/v6/latest/facts/tests.json
```

Coverage should improve.

---

### Workflow 5: Resolving Maven Hazards

**Problem**: Build is intermittently failing. "Stale artifact in M2 cache"?

**Step 1: Check All Hazards**
```bash
jq '.hazards[] | {type: .type, severity: .severity, description: .description}' \
  docs/v6/latest/facts/maven-hazards.json
```

**Example Output**:
```json
{
  "type": "m2_cache_hazard",
  "severity": "low",
  "description": "Stale artifacts in ~/.m2/repository/"
}
```

**Step 2: Identify Your Problem**

Match the hazard to your error message:
- **version_conflict**: "Could not find artifact" or duplicate version warnings
- **transitive_exclusion**: Import errors or missing classes
- **m2_cache_hazard**: Intermittent failures, non-deterministic behavior

**Step 3: Apply Resolution**

For M2 cache hazard:
```bash
# Option A: Clean just YAWL artifacts
rm -rf ~/.m2/repository/org/yawl/

# Option B: Full clean (nuclear option)
rm -rf ~/.m2/repository/

# Then rebuild
mvn clean install -DskipTests
```

For version conflict:
```bash
# Add to pom.xml (root)
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter-api</artifactId>
      <version>5.10.0</version>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Then rebuild:
```bash
mvn clean verify
```

For transitive exclusion issue:
```bash
# Check exclusions in pom.xml
grep -A 5 "<exclusions>" pom.xml

# If missing, add:
<dependency>
  <groupId>org.yawl</groupId>
  <artifactId>yawl-security</artifactId>
  <version>6.0.0</version>
  <exclusions>
    <exclusion>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-jcl</artifactId>
    </exclusion>
  </exclusions>
</dependency>
```

**Step 4: Verify Resolution**

```bash
bash scripts/observatory/observatory.sh --facts
jq '.hazards | length' docs/v6/latest/facts/maven-hazards.json
```

Hazard count should decrease.

**Step 5: Add to CI Prevention**

Update `.github/workflows/build.yml`:
```yaml
- name: Clean Maven cache
  run: rm -rf ~/.m2/repository/org/yawl/
  if: failure()  # Clean on failure to prevent stale artifact issues
```

---

## Advanced Queries with jq

### jq Cheat Sheet for Observatory Facts

**Basics**:
```bash
# Pretty-print JSON
jq . file.json

# Extract single field
jq '.field' file.json

# Filter by condition
jq '.items[] | select(.status=="active")' file.json

# Map (transform)
jq '.modules[] | .name' file.json

# Group by field
jq 'group_by(.status)' file.json

# Count
jq '.modules | length' file.json

# Sort
jq 'sort_by(.coverage_percentage) | reverse' file.json

# Combine multiple files
jq -s '.[0] + .[1]' facts/modules.json facts/tests.json
```

### Real-World Queries

**Query 1: Find all modules with their test coverage and dependents**

```bash
jq -s 'def mods: .[0].modules[];
       def tests: .[1].modules[];
       mods as $m |
       tests | select(.name == $m.name) |
       {name: .name, coverage: .coverage_percentage, dependents: $m.dependents}' \
  docs/v6/latest/facts/modules.json \
  docs/v6/latest/facts/tests.json
```

**Query 2: Find critical modules (many dependents + low coverage)**

```bash
jq -s 'def mods: .[0].modules[];
       def tests: .[1].modules[];
       mods | select(.dependents | length > 2) as $m |
       tests | select(.name == $m.name and .coverage_percentage < 70) |
       {name: .name, coverage: .coverage_percentage, dependents: ($m.dependents | length)}' \
  docs/v6/latest/facts/modules.json \
  docs/v6/latest/facts/tests.json | sort_by(.dependents) | reverse
```

Result: Shows high-risk modules (many dependents but low test coverage).

**Query 3: Build dependency tree as table**

```bash
jq '.modules | map({name, deps: (.dependencies | length), dependents: (.dependents | length)}) |
     sort_by(.dependents) | reverse |
     .[] | "\(.name): \(.deps) deps, \(.dependents) dependents"' \
  docs/v6/latest/facts/modules.json
```

**Query 4: List all modules that could be built in parallel**

```bash
jq '.phases[] | select(.can_build_in_parallel==true) | .modules[]' \
  docs/v6/latest/facts/reactor.json | sort -u
```

**Query 5: Hazard risk matrix (severity × count)**

```bash
jq 'group_by(.severity) | map({severity: .[0].severity, count: length})' \
  docs/v6/latest/facts/maven-hazards.json
```

### Combining Multiple Fact Files

```bash
# Find modules with low test coverage that are critical (many dependents)
jq -s 'def critical: .[0].modules[] | select(.dependents | length > 3);
       def lowcov: .[1].modules[] | select(.coverage_percentage < 70);
       [critical, lowcov] | group_by(.name) | map(select(length==2))' \
  docs/v6/latest/facts/modules.json \
  docs/v6/latest/facts/tests.json
```

### Performance: Processing Large Fact Files

For large fact files (100s of modules), use streaming:

```bash
# Instead of loading entire file:
cat docs/v6/latest/facts/modules.json | jq --stream 'select(length==2) | .[0]' | head -50

# Or use --slurpfile for multiple files without loading all at once
jq --slurpfile tests docs/v6/latest/facts/tests.json \
  '.modules[] | select(.name as $n | $tests[0].modules[].name | . == $n)' \
  docs/v6/latest/facts/modules.json
```

---

## Watermark Protocol: Fact Freshness

### How Watermarks Work

The Observatory uses SHA256 hashes to detect when facts are stale:

```json
{
  "metadata": {
    "generated_at": "2026-02-28T15:00:00Z",
    "inputs": {
      "pom_xml_sha256": "abc123...",
      "source_tree_sha256": "def456..."
    },
    "outputs": {
      "modules_sha256": "ghi789...",
      "reactor_sha256": "jkl012..."
    }
  }
}
```

**TTL-Based Refetching**:

1. User runs `bash scripts/observatory/observatory.sh`
2. System computes hash of `pom.xml` (inputs)
3. Compares against `inputs.pom_xml_sha256` from previous run
4. **If same**: Skip generation (0 time, 0 tokens)
5. **If different**: Regenerate (17 seconds, 0 tokens)

### Manual Stale Check

Check if facts are stale:

```bash
# Compute current hash of pom.xml
sha256sum pom.xml | awk '{print $1}' > /tmp/current.sha

# Compare against fact metadata
jq '.metadata.inputs.pom_xml_sha256' docs/v6/latest/facts/modules.json > /tmp/recorded.sha

# Are they different?
diff /tmp/current.sha /tmp/recorded.sha
```

If different, re-run:
```bash
bash scripts/observatory/observatory.sh --facts
```

### Content Hash Verification

Verify integrity of fact files:

```bash
# Compute current hash of modules.json
sha256sum docs/v6/latest/facts/modules.json | awk '{print $1}' > /tmp/actual.sha

# Compare against receipt
jq '.outputs.modules_sha256' docs/v6/latest/receipts/observatory.json > /tmp/expected.sha

# Should match
diff /tmp/expected.sha /tmp/actual.sha
```

If hashes differ, the fact file may have been corrupted. Regenerate:
```bash
rm -rf docs/v6/latest/facts/
bash scripts/observatory/observatory.sh --facts
```

### Preventing Stale Facts in CI/CD

Add to your CI pipeline (`.github/workflows/build.yml`):

```yaml
- name: Check Observatory freshness
  run: |
    # Compute current pom.xml hash
    CURRENT_HASH=$(sha256sum pom.xml | awk '{print $1}')

    # Get recorded hash
    RECORDED_HASH=$(jq -r '.metadata.inputs.pom_xml_sha256' docs/v6/latest/receipts/observatory.json)

    # Compare
    if [ "$CURRENT_HASH" != "$RECORDED_HASH" ]; then
      echo "Observatory facts are stale. Regenerating..."
      bash scripts/observatory/observatory.sh --facts
      git add docs/v6/latest/
      git commit -m "Update stale Observatory facts"
    fi
```

---

## Observatory in Development

### Pre-Commit Workflow

Before committing changes:

```bash
# 1. Make your code changes
# ... edit files ...

# 2. Check if facts are stale
bash scripts/observatory/observatory.sh --facts

# 3. Review changes to facts
git diff docs/v6/latest/facts/

# 4. If significant changes, update diagnostics
jq '.conflicts | length' docs/v6/latest/facts/deps-conflicts.json
jq '.duplicates | length' docs/v6/latest/facts/duplicates.json

# 5. Commit
git add -A
git commit -m "Update Observatory facts after changes"
```

### During Code Review

As a reviewer, use Observatory to assess pull requests:

**Checklist**:
```
- [ ] Run Observatory to detect new dependency conflicts
- [ ] Check if any new duplicate classes are introduced
- [ ] Verify test coverage doesn't decrease
- [ ] Confirm no modules become dangling (no dependents, no dependencies)
- [ ] Review any new Maven hazards
```

**Review commands**:
```bash
# Fetch PR branch
git fetch origin pull/123/head:pr-123
git checkout pr-123

# Run Observatory
bash scripts/observatory/observatory.sh --facts

# Compare with main
git stash
git checkout main
bash scripts/observatory/observatory.sh --facts

# Diff fact files
diff docs/v6/latest/facts/deps-conflicts.json <(git show origin/main:docs/v6/latest/facts/deps-conflicts.json)
```

### Team Synchronization

Keep the whole team using fresh facts:

```bash
# Add to team standup
bash .claude/scripts/read-observatory.sh facts

# Sample output:
# 📊 Observatory Facts Summary
# Key Facts:
# • Modules: 12
# • Build phases: 3
# • Tests: 1247 total (1200 JUnit5, 47 JUnit4)
# • Integration: MCP=true, A2A=true
# • Engine family: Stateful=6, Stateless=6
```

---

## Troubleshooting Observatory

### Observatory Generation Failure

**Symptom**: `bash scripts/observatory/observatory.sh` fails with error

**Step 1: Check Prerequisites**

```bash
# Verify jq is installed
which jq
jq --version

# Verify Python 3
python3 --version

# Verify git
git status

# Verify pom.xml is present
test -f pom.xml && echo "pom.xml found" || echo "ERROR: pom.xml missing"
```

**Step 2: Check Receipt for Refusals**

```bash
jq '.refusals[]' docs/v6/latest/receipts/observatory.json
```

Common refusals:
- `"Could not parse pom.xml"` → Check XML syntax
- `"Git repository not found"` → Check you're in YAWL root
- `"Source tree analysis failed"` → Check file permissions

**Step 3: Fix the Root Cause**

For XML parsing error:
```bash
xmllint pom.xml > /dev/null 2>&1 && echo "pom.xml valid" || echo "pom.xml invalid"
```

For git issues:
```bash
git log -1 --oneline  # Verify git repo
git status            # Check working tree
```

**Step 4: Regenerate**

```bash
rm -rf docs/v6/latest/
bash scripts/observatory/observatory.sh --facts
```

### Fact Staleness Diagnosis

**Symptom**: Facts seem out of date compared to code

**Step 1: Check Metadata Timestamps**

```bash
jq '.metadata.generated_at' docs/v6/latest/facts/modules.json
date  # Current time
```

If facts are > 1 hour old, regenerate:
```bash
bash scripts/observatory/observatory.sh --facts
```

**Step 2: Compare Input Hashes**

```bash
# Current pom.xml hash
sha256sum pom.xml | awk '{print $1}'

# Recorded in facts
jq '.metadata.inputs.pom_xml_sha256' docs/v6/latest/facts/modules.json

# If different, facts are stale
```

**Step 3: Regenerate If Stale**

```bash
bash scripts/observatory/observatory.sh --facts
```

### Corrupted Fact Files

**Symptom**: `jq` errors when reading facts (`parse error`)

**Step 1: Validate JSON**

```bash
jq empty docs/v6/latest/facts/modules.json
jq empty docs/v6/latest/facts/gates.json
# etc.
```

**Step 2: Verify SHA256 Hashes**

```bash
# Compare actual hash against receipt
sha256sum docs/v6/latest/facts/modules.json | awk '{print $1}' > /tmp/actual
jq '.outputs.modules_sha256' docs/v6/latest/receipts/observatory.json > /tmp/expected
diff /tmp/actual /tmp/expected
```

**Step 3: Recover**

If corrupted:
```bash
# Delete corrupted facts
rm -rf docs/v6/latest/facts/

# Regenerate
bash scripts/observatory/observatory.sh --facts

# Verify
jq '.modules | length' docs/v6/latest/facts/modules.json
```

### Cache Corruption Recovery

**Symptom**: Intermittent failures or inconsistent results

**Complete Cache Reset**:
```bash
# Remove all Observatory output
rm -rf docs/v6/latest/

# Remove Maven cache (if needed)
rm -rf ~/.m2/repository/org/yawl/

# Regenerate from scratch
bash scripts/observatory/observatory.sh --facts

# Verify
bash .claude/scripts/read-observatory.sh facts
```

### Manual Fact Generation

If the Observatory script is broken, manually generate key facts:

```bash
# modules.json: Extract from pom.xml
cat pom.xml | grep '<module>' | sed 's/.*<module>//;s/<\/module>.*//' | jq -R . | jq -s . > /tmp/modules.json

# reactor.json: Use Maven
mvn -q -DoutputFile=/tmp/reactor.txt dependency:tree

# tests.json: Count test files
find . -path '*/test/*' -name '*Test.java' -o -name '*IT.java' | jq -Rs 'split("\n") | length' > /tmp/tests.json
```

---

## Next Steps

1. **Run Observatory now**: `bash scripts/observatory/observatory.sh --facts`
2. **Bookmark the facts directory**: `docs/v6/latest/facts/`
3. **Use this guide** for your next debugging task
4. **Check diagrams**: `docs/v6/latest/diagrams/` for visual topology

For more information on extending Observatory, see [OBSERVATORY.md](../../.claude/OBSERVATORY.md).
