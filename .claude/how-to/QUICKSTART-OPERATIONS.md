# YAWL Operations Quick Start Guide

**Validation, monitoring, and operational excellence**

## Quality Gates and Validation

### Phase-Based Validation System

YAWL uses a 5-phase validation system:

1. **Ψ (Observatory)** - Codebase facts collection
2. **Λ (Build)** - Compile, test, package
3. **H (Guards)** - Anti-pattern detection
4. **Q (Invariants)** - Code honesty verification
5. **Ω (Consolidation)** - Final commit

### Running Validation Phases

```bash
# Observatory - collect codebase facts
bash scripts/observatory/observatory.sh --facts

# Build - compile and test
bash scripts/dx.sh all

# Guards - detect anti-patterns
bash .claude/hooks/hyper-validate.sh

# Q Phase - verify code invariants
yawl godspeed verify --verbose

# Consolidation - commit and push
git add . && git commit -m "feat: description"
```

## Observatory (Codebase Facts)

### Quick Commands

```bash
# Refresh all facts (modules, tests, coverage, hazards)
bash scripts/observatory/observatory.sh --facts

# Key fact files to read
cat docs/v6/latest/facts/modules.json       # Module structure
cat docs/v6/latest/facts/tests.json         # Test counts
cat docs/v6/latest/facts/maven-hazards.json # Build hazards
cat docs/v6/latest/facts/reactor.json       # Module dependencies
```

### What Facts Tell You

- **modules.json**: Which files belong to which module (prevents build confusion)
- **tests.json**: Test coverage gaps (find modules needing more tests)
- **maven-hazards.json**: Build conflicts (logging bridge issues, etc.)
- **deps-conflicts.json**: Version convergence problems
- **reactor.json**: Build order dependencies

### Fact Drift Detection

```bash
# Check if facts have changed
cat .claude/receipts/observatory-facts.sha256

# If drift detected, rebuild
bash scripts/dx.sh all
```

## Q Phase (Invariants) Validation

### The 4 Invariants

#### Q1: real_impl ∨ throw
Every method either implements real logic OR throws UnsupportedOperationException.

```java
// ❌ WRONG (empty stub)
public void initialize() { }

// ✅ RIGHT (real implementation)
public void initialize() {
    database.connect();
    cache.refresh();
}

// ✅ RIGHT (honest exception)
public void initialize() {
    throw new UnsupportedOperationException(
        "Requires database configuration. See DATABASE.md:42"
    );
}
```

#### Q2: ¬mock (No Mock Implementations)
No mock, stub, fake, test, demo, sample in production code.

```java
// ❌ WRONG
public String getMockData() { return "fake data"; }

// ✅ RIGHT
public String getData() {
    return repository.fetch(); // Real implementation
}
```

#### Q3: ¬silent_fallback (No Silent Exception Handling)
Exceptions are propagated, never silently caught and faked.

```java
// ❌ WRONG
public Data fetchFromApi() {
    try { return api.call(); }
    catch (Exception e) { return DEFAULT_DATA; }
}

// ✅ RIGHT
public Data fetchFromApi() {
    try { return api.call(); }
    catch (ApiException e) {
        throw new RuntimeException("API failed", e);
    }
}
```

#### Q4: ¬lie (Code = Documentation)
Method behavior matches its name, documentation, and return type.

```java
// ❌ WRONG
/** Validates input */ public boolean validate(String data) { 
    return true; // LIES! No validation!
}

// ✅ RIGHT
/** Validates input */ public boolean validate(String data) {
    return validator.validate(data); // Actually validates!
}
```

### Running Q Phase

```bash
# Full verification
yawl godspeed verify --verbose

# Save detailed report
yawl godspeed verify --report json > invariant-report.json
```

## PSI (Observatory RDF Integration)

### One-Command Execution

```bash
# Convert JSON facts to RDF/Turtle
bash scripts/observatory/emit-rdf-facts.sh

# Output locations:
# - docs/v6/latest/facts.ttl
# - .claude/receipts/observatory-facts.sha256
```

### Code Integration

```java
// Load facts in ggen
GgenObservationBridge bridge = new GgenObservationBridge();
bridge.loadFacts(Paths.get("docs/v6/latest/facts.ttl"));

// Query modules
List<String> modules = bridge.getModules();

// Custom SPARQL query
List<Map<String, String>> results = bridge.query(
    "SELECT ?moduleName ?testCount WHERE { ?m ex:moduleName ?moduleName }"
);
```

## Common Workflows

### Before Commit Checklist

```bash
# 1. Refresh codebase facts
bash scripts/observatory/observatory.sh --facts

# 2. Compile and test
bash scripts/dx.sh all

# 3. Check code quality
bash .claude/hooks/hyper-validate.sh

# 4. Verify invariants
yawl godspeed verify --verbose

# 5. Commit
git add <specific-files>
git commit -m "feat: description"
```

### Fact-Based Module Selection

```bash
# Find low coverage modules (need more tests)
jq '.[] | select(.lineCoverage < 65) | .moduleName' docs/v6/latest/facts/tests.json

# Find modules with build hazards
cat docs/v6/latest/facts/maven-hazards.json
```

## Quality Gate Thresholds

| Gate | Command | Threshold | Risk |
|------|---------|-----------|------|
| Enforcer | `mvn validate` | Java 25+ | RED |
| Compile | `mvn compile` | Must succeed | RED |
| Unit Tests | `mvn test` | 100% pass | RED |
| JaCoCo | `mvn verify` | 50% line/branch | YELLOW |
| SpotBugs | `mvn verify -P ci` | 0 high bugs | YELLOW |
| PMD | `mvn verify -P analysis` | 0 violations | YELLOW |
| Checkstyle | `mvn verify -P analysis` | 0 violations | YELLOW |
| OWASP Dep-Check | `mvn verify -P security-audit` | CVSS < 7 | YELLOW |

### Profile-Gated Gates

```bash
# Run analysis checks
mvn verify -P analysis

# Run security audit
mvn verify -P security-audit

# Run all checks
mvn verify -P analysis,security-audit,ci
```

## Validation Exit Codes

| Code | Meaning | Action |
|------|---------|--------|
| 0 | All checks GREEN | Proceed to commit |
| 1 | Error (retryable) | Fix and retry |
| 2 | Invariants RED | Fix code and retry |
| 3 | Guard violations | Fix anti-patterns |
| 4 | Build failed | Fix compilation |

## Production Checklist

### Pre-Deployment Validation

```bash
# Full validation pipeline
bash scripts/dx.sh all && \
yawl godspeed verify --verbose && \
bash scripts/observatory/observatory.sh --facts

# Verify fact drift
cat .claude/receipts/observatory-facts.sha256

# Check build receipt
jq '.status' .ggen/build-receipt.json
```

## Quick Links

- **Observatory**: `PSI_QUICKREF.md`
- **Q Phase**: `Q-PHASE-QUICK-REFERENCE.md`
- **Validation**: `QUICK_VALIDATION_REFERENCE.md`
- **Build System**: `GGEN-BUILD-QUICK-REFERENCE.md`

---

**Last Updated**: February 22, 2026  
**Status**: Production Ready
