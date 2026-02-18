# /yawl-java25 Skill

Java 25 adoption and migration tools for YAWL workflow engine.

---

## Overview

Automates Java 25 feature adoption, performance optimization, and migration from Java 21.

**Invoked by**: `yawl-engineer`
**Requires**: Java 25 environment
**Output**: Reports, migration recommendations, performance metrics

---

## Commands

### `/yawl-java25 --check-deprecated`

Scan project for deprecated Java APIs that will be removed in future versions.

```bash
/yawl-java25 --check-deprecated
/yawl-java25 --check-deprecated --module=yawl-engine
```

**Output**:
- List of deprecated APIs with removal dates
- Replacement recommendations
- Files requiring updates

### `/yawl-java25 --migrate-records`

Identify candidates for migration to Java 25 records.

```bash
/yawl-java25 --migrate-records
/yawl-java25 --migrate-records --module=yawl-stateless --report
```

**Output**:
- Candidate classes for record conversion
- Refactoring impact analysis
- Memory savings estimates

### `/yawl-java25 --parallel-build`

Enable and validate parallel Maven builds.

```bash
/yawl-java25 --parallel-build
/yawl-java25 --parallel-build --report
```

**Output**:
- Build time comparison (sequential vs parallel)
- Parallel execution enabled
- Performance improvements documented

**Expected Results**:
- -50% build time (180s → 90s)
- -50% test time (60s → 30s)

### `/yawl-java25 --performance-report`

Generate Java 25 performance optimization report.

```bash
/yawl-java25 --performance-report
/yawl-java25 --performance-report --module=yawl-engine
```

**Report Includes**:
- Compact object header savings (-5-10% throughput)
- Virtual thread memory savings (2GB → 1MB for 1000 agents)
- GC efficiency improvements
- Startup time optimizations
- Recommendations for Phase 2-4 adoption

### `/yawl-java25 --security-check`

Validate Java 25 security compliance.

```bash
/yawl-java25 --security-check
/yawl-java25 --security-check --level=strict
```

**Checks**:
- ✅ TLS 1.3 enforcement
- ✅ Deprecated cryptography removal
- ✅ SBOM generation
- ✅ No Security Manager usage
- ✅ 3072-bit RSA minimum
- ✅ AES-GCM only for symmetric crypto

### `/yawl-java25 --module-audit`

Audit Java 9+ module system readiness.

```bash
/yawl-java25 --module-audit
/yawl-java25 --module-audit --report
```

**Analyzes**:
- Module boundaries
- Dependency cycles
- Exported APIs
- Internal package references
- Missing module-info.java files

---

## Phase Reference

| Phase | Features | Timeline | Skill Usage |
|-------|----------|----------|------------|
| **Phase 1** | Records, sealed classes, virtual threads | Weeks 1-2 | `--check-deprecated`, `--migrate-records` |
| **Phase 2** | CQRS, modules, reactive patterns | Weeks 3-4 | `--module-audit` |
| **Phase 3** | AOT profiling, GC tuning | Weeks 5-6 | `--performance-report` |
| **Phase 4** | Security APIs, cryptography | Weeks 7-8 | `--security-check` |

---

## Integration with Agents

**yawl-engineer**:
- Use `--check-deprecated` before refactoring
- Use `--migrate-records` to find DTO candidates
- Use `--parallel-build` to enable fast feedback loop

**yawl-performance-benchmarker**:
- Use `--performance-report` to quantify improvements
- Use `--security-check` for production validation

**yawl-architect**:
- Use `--module-audit` for architecture planning

---

## Related Documentation

- **[JAVA-25-FEATURES.md](../../JAVA-25-FEATURES.md)** - Feature adoption roadmap
- **[ARCHITECTURE-PATTERNS-JAVA25.md](../../ARCHITECTURE-PATTERNS-JAVA25.md)** - 8 implementation patterns
- **[BUILD-PERFORMANCE.md](../../BUILD-PERFORMANCE.md)** - Build optimization
- **[INDEX.md](../../INDEX.md)** - Complete documentation

---

## Examples

### Adopt Phase 1 in Week 1
```bash
# 1. Check for deprecated APIs (5 min)
/yawl-java25 --check-deprecated --report

# 2. Find record candidates (10 min)
/yawl-java25 --migrate-records --module=yawl-stateless

# 3. Enable parallel builds (2 min)
/yawl-java25 --parallel-build

# 4. Generate baseline performance (15 min)
/yawl-java25 --performance-report
```

### Pre-Production Validation
```bash
# 1. Security compliance check
/yawl-java25 --security-check --level=strict

# 2. Module system audit
/yawl-java25 --module-audit --report

# 3. Final performance validation
/yawl-java25 --performance-report
```

---

**Last Updated**: 2026-02-17
**Java Version**: 25+
**Standards**: HYPER_STANDARDS + Java 25
