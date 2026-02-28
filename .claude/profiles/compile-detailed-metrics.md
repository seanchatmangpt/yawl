# YAWL Compilation Metrics - Detailed Breakdown

**Date**: 2026-02-28  
**Purpose**: Per-module analysis for targeted optimization  

---

## Module Dependency Graph (Compilation Order)

```
Layer 0 (Foundation - parallel):
  ├─ yawl-utilities (no deps)
  ├─ yawl-security (no deps)
  ├─ yawl-benchmark (no deps)
  ├─ yawl-graalpy (no deps)
  └─ yawl-graaljs (no deps)

Layer 1 (First consumers - parallel after Layer 0):
  ├─ yawl-elements (depends: utilities, security)
  ├─ yawl-ggen (depends: utilities, security)
  ├─ yawl-graalwasm (no YAWL deps)
  ├─ yawl-dmn (depends: utilities, elements)
  └─ yawl-data-modelling (depends: utilities)

Layer 2 (Core engine - CRITICAL PATH):
  └─ yawl-engine (depends: utilities, security, elements, dmn, ggen)

Layer 3 (Engine extension - blocks Layer 4):
  └─ yawl-stateless (depends: engine, utilities)

Layer 4 (Services - parallel after Layer 3):
  ├─ yawl-authentication (depends: utilities, security, engine, stateless)
  ├─ yawl-scheduling (depends: utilities, engine, stateless)
  ├─ yawl-monitoring (depends: utilities, engine, stateless)
  ├─ yawl-worklet (depends: utilities, engine, stateless)
  ├─ yawl-control-panel (depends: utilities, engine, stateless)
  ├─ yawl-integration (depends: utilities, engine, stateless)  <-- HEAVY
  └─ yawl-webapps (depends: utilities, engine, stateless, ...)

Layer 5 (Advanced - parallel):
  ├─ yawl-pi (depends: utilities, engine)
  └─ yawl-resourcing (depends: utilities, engine, elements)

Layer 6 (Top-level):
  └─ yawl-mcp-a2a-app (depends: pi, resourcing, integration, ...)
```

---

## Per-Module Compilation Profile

### SLOWEST MODULES (Target for optimization)

#### 1. yawl-mcp-a2a-app (CRITICAL)
- **LOC**: 51,356
- **Classes**: 198
- **Generics**: 4,793 (HIGH)
- **Annotations**: 386
- **Est. Compile Time**: 30-45s
- **Bottleneck**: MCP protocol handling with heavy generics
- **Optimization**: Remove `-parameters` from implementation classes
- **Lint impact**: ~3-5s from `-Xlint:all`

#### 2. yawl-pi (HIGH)
- **LOC**: 8,704
- **Classes**: 63
- **Generics**: 832
- **Annotations**: 40
- **Est. Compile Time**: 8-12s
- **Bottleneck**: ML model integration (onnxruntime)
- **Optimization**: Split lint checking
- **Lint impact**: ~1-2s from `-Xlint:all`

#### 3. yawl-ggen (HIGH)
- **LOC**: 8,423
- **Classes**: 69
- **Generics**: 453
- **Annotations**: 59
- **Est. Compile Time**: 8-12s
- **Bottleneck**: Code generation with complex AST
- **Optimization**: Module-level parameter override
- **Lint impact**: ~1-2s from `-Xlint:all`

#### 4. yawl-dspy (MEDIUM)
- **LOC**: 4,841
- **Classes**: 23
- **Generics**: 570 (HIGH for size)
- **Annotations**: 42
- **Est. Compile Time**: 5-8s
- **Bottleneck**: DSPy framework integration
- **Optimization**: Remove `--enable-preview` if not used

#### 5. yawl-benchmark (MEDIUM)
- **LOC**: 1,832
- **Classes**: 7
- **Generics**: 269
- **Annotations**: 93 (HIGH for size!)
- **Est. Compile Time**: 2-4s
- **Bottleneck**: JMH annotation processing
- **Optimization**: Disable APT if possible

---

### CRITICAL PATH MODULES (Blocks downstream)

#### yawl-engine (CRITICAL PATH)
- **Dependencies**: All Layer 0, Layer 1
- **Est. Compile Time**: 20-30s
- **Critical**: Blocks yawl-stateless and Layer 4
- **Action**: Minimal optimization (must not break)
- **Settings**:
  - Keep `-parameters` (public API)
  - Keep `--enable-preview` (uses virtual threads)
  - Can reduce lint: `-Xlint:unchecked,deprecation`

#### yawl-stateless (CRITICAL PATH)
- **Dependencies**: yawl-engine
- **Est. Compile Time**: 10-15s
- **Critical**: Blocks Layer 4
- **Action**: Reduce lint checking
- **Settings**:
  - Keep `-parameters` (REST API)
  - Keep `--enable-preview` (virtual threads)
  - Reduce lint: `-Xlint:unchecked,deprecation`

---

## Optimization Matrix

### Parameter Flag Usage

| Module | Public API? | Keep `-parameters`? | Benefit |
|--------|------------|-------------------|---------|
| yawl-engine | YES | YES | Required |
| yawl-stateless | YES | YES | Required |
| yawl-elements | YES | YES | Required |
| yawl-integration | YES | YES | Required |
| yawl-resourcing | YES | YES | Required |
| yawl-utilities | NO (internal) | NO | 3-5% faster |
| yawl-security | NO (internal) | NO | 2-3% faster |
| yawl-ggen | PARTIAL | NO | 2-3% faster |
| yawl-dmn | NO | NO | 2-3% faster |
| yawl-data-modelling | NO | NO | 2% faster |
| yawl-benchmark | NO | NO | 2% faster |
| yawl-pi | PARTIAL | NO | 2-3% faster |
| yawl-dspy | NO | NO | 2% faster |
| yawl-graalpy | NO | NO | 1% faster |
| yawl-graaljs | NO | NO | 1% faster |
| yawl-graalwasm | NO | NO | 1% faster |

---

### Preview Feature Usage

| Module | Uses Preview? | Keep `--enable-preview`? |
|--------|-------------|------------------------|
| yawl-engine | YES (virtual threads) | YES |
| yawl-stateless | YES (virtual threads) | YES |
| yawl-benchmark | YES (JMH) | YES |
| All others | NO | NO |

---

## Lint Checking Impact

### Full `-Xlint:all` Categories

```
unchecked       (generics without type parameters)
deprecation     (using deprecated methods)
cast            (redundant casts)
classfile       (issues with class files)
dep-ann         (missing @Deprecated)
divzero         (division by zero)
empty           (empty statements)
fallthrough     (fall-through in switch)
finally         (finally clauses not completing)
nullness        (dereference of null)
path            (invalid path)
processing      (annotation processing)
rawtypes        (raw types)
removal         (removal warnings)
serial          (serialization issues)
static          (static access)
try             (try-with-resources)
unchecked       (unchecked operations)
varargs         (varargs methods)
```

### Recommended Split

**Keep in all builds** (catches real issues):
- `unchecked` (generics misuse)
- `deprecation` (API changes)
- `nullness` (potential NPE)

**Move to CI/release only**:
- `cast` (usually safe)
- `serial` (not all code serializes)
- `varargs` (usually intentional)
- All others

---

## Estimated Compile Time Reduction

### Current Baseline (Estimated)
```
Layer 0 (parallel):       5s  (max of 5 modules)
Layer 1 (parallel):      12s  (max of 5 modules)
Layer 2 (yawl-engine):   25s
Layer 3 (yawl-stateless):12s
Layer 4 (parallel):      35s  (max of yawl-mcp-a2a-app: 40s, others 10-15s)
Layer 5 (parallel):      10s
Layer 6 (app):           20s
---
TOTAL (clean):          ~2m 20s
```

### After Phase 1 Optimizations (-12% expected)
```
Reductions:
- Reduced lint: Layer 1-6 each saves 1-3s = ~15s total
- Parallel L4: MCP-A2A now runs 40s → 36s (reduced lint)
---
TOTAL: ~2m 05s (15s saved = 11% reduction)
```

### After Phase 2 Optimizations (-8% additional)
```
Reductions:
- JVM TieredStopAtLevel=3: Saves 5-8% across all layers = ~7s
- Increased parallelism: Marginal (already optimized)
---
TOTAL: ~1m 58s (additional 7s saved = 6% from baseline)
---
CUMULATIVE: ~2m 05s (25s saved = 18% reduction)
```

---

## Per-Module Optimization Commands

### Build ONLY slow modules (for testing)
```bash
# Critical path only
mvn compile -pl yawl-utilities,yawl-engine,yawl-stateless -P java25-fast -B

# Slow modules for lint testing
mvn compile -pl yawl-mcp-a2a-app,yawl-pi,yawl-ggen -P java25-fast -B

# With timing
DX_TIMINGS=1 mvn compile -pl yawl-mcp-a2a-app -P java25-fast -B
```

---

## Risk Assessment

### Low Risk Changes
- Reduced lint checking: `-Xlint:unchecked,deprecation` (catches 90% of issues)
- Parameter flag removal (internal modules only)
- Increased parallelism

### Medium Risk Changes
- JVM TieredStopAtLevel=3 (need to verify no perf regression)
- Preview feature removal per module

### High Risk Changes
- Annotation processor disabling
- Metaspace tuning
- Build cache disable

---

## Validation Checklist

- [ ] Baseline compile time measured (clean + incremental)
- [ ] Phase 1 optimizations applied
- [ ] Lint warnings still caught (spot-check)
- [ ] Tests pass (`mvn test -P java25-fast`)
- [ ] Static analysis still works (`mvn clean verify -P analysis`)
- [ ] No performance degradation (runtime unaffected)
- [ ] CI pipeline uses full checks (`-P java25`)
- [ ] Developer docs updated

