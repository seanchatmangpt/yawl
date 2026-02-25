# Migration Guide: YAWL v6.0.0 to v6.0.0

**From**: YAWL v6.0.0 | **To**: YAWL v6.0.0 | **Effort**: Low (documentation-only)

---

## Overview

YAWL v6.0.0 introduces significant documentation improvements and operational tooling while maintaining full API backward compatibility with v5.2. This is a **documentation-focused release** with no breaking code changes.

### Key Changes

| Area | Change | Impact |
|------|--------|--------|
| Documentation | 89 package-info.java files | Better codebase understanding |
| Observatory | Codebase instrumentation | Automated facts/diagrams |
| Validation | CI/CD scripts | Quality gates |
| Performance | Baselines + SLAs | Regression detection |
| Configuration | Expanded .claude/ | Claude Code integration |

---

## Breaking Changes

**None.** YAWL v6.0.0 is fully backward compatible with v5.2.

All Interface A (design-time) and Interface B (client/runtime) methods are unchanged.
Database schema is unchanged (Hibernate mappings identical).

---

## Migration Steps

### Step 1: Update Dependencies

Update your pom.xml to reference v6.0.0:

```xml
<properties>
    <yawl.version>6.0.0</yawl.version>
</properties>

<dependencies>
    <dependency>
        <groupId>org.yawlfoundation.yawl</groupId>
        <artifactId>yawl-engine</artifactId>
        <version>${yawl.version}</version>
    </dependency>
    <dependency>
        <groupId>org.yawlfoundation.yawl</groupId>
        <artifactId>yawl-resourcing</artifactId>
        <version>${yawl.version}</version>
    </dependency>
    <!-- Add other YAWL modules as needed -->
</dependencies>
```

### Step 2: Update Documentation References

If you reference YAWL documentation, update paths:

| Old Path (v5.2) | New Path (v6.0) |
|-----------------|-----------------|
| `docs/README.md` | `docs/INDEX.md` |
| N/A | `docs/v6/latest/facts/` |
| N/A | `docs/v6/latest/diagrams/` |
| `.claude/CLAUDE.md` | `CLAUDE.md` (moved to root) |

### Step 3: Run Validation

```bash
# Validate documentation links
bash scripts/validation/validate-documentation.sh

# Validate observatory facts
bash scripts/validation/validate-observatory.sh

# Full pre-release validation
bash scripts/validation/validate-release.sh
```

### Step 4: Verify Build

```bash
# Standard build
mvn -T 1.5C clean compile

# With tests
mvn -T 1.5C clean test
```

---

## New Features

### Observatory System

The observatory generates facts and diagrams about the codebase:

```bash
# Generate all facts and diagrams
bash scripts/observatory/observatory.sh

# Output in docs/v6/latest/
```

**What you get:**
- 9 JSON fact files (modules, reactor, integration, etc.)
- 8 Mermaid diagrams (architecture, health, risks)
- 1 YAWL XML (build lifecycle)
- SHA256 receipt for verification

### Validation Scripts

Three validation scripts for CI/CD:

```bash
# Documentation validation (links, coverage, schemas)
bash scripts/validation/validate-documentation.sh

# Observatory validation (freshness, SHA256)
bash scripts/validation/validate-observatory.sh

# Performance validation (baselines, regression)
bash scripts/validation/validate-performance-baselines.sh
```

### Performance Baselines

Measure and track performance:

```bash
# Measure current baselines
bash scripts/performance/measure-baseline.sh

# Output in docs/v6/latest/performance/
```

### Agent DX Loop

Fast build-test loop for development:

```bash
# Compile + test CHANGED modules only (~5-15s)
bash scripts/dx.sh

# Compile only
bash scripts/dx.sh compile

# Test only
bash scripts/dx.sh test

# All modules
bash scripts/dx.sh all
```

---

## Configuration Changes

### Claude Code Integration

New files in `.claude/`:

| File | Purpose |
|------|---------|
| `BEST-PRACTICES-2026.md` | 12-section best practices guide |
| `JAVA-25-FEATURES.md` | Java 25 adoption roadmap |
| `ARCHITECTURE-PATTERNS-JAVA25.md` | 8 architectural patterns |
| `BUILD-PERFORMANCE.md` | Maven optimization |
| `SECURITY-CHECKLIST-JAVA25.md` | Security compliance |
| `OBSERVATORY.md` | Observatory instrument protocol |

### GitHub Actions

New CI/CD workflow:

```yaml
# .github/workflows/documentation-validation.yml
# Runs on: push to docs/, .claude/, schema/
```

---

## API Compatibility Matrix

| Interface | v5.2 | v6.0 | Compatible |
|-----------|------|------|------------|
| Interface A (design) | Yes | Yes | 100% |
| Interface B (client) | Yes | Yes | 100% |
| Interface E (events) | Yes | Yes | 100% |
| Interface X (extended) | Yes | Yes | 100% |

### Database Schema

| Schema Element | v5.2 | v6.0 | Change |
|----------------|------|------|--------|
| Tables | Same | Same | None |
| Columns | Same | Same | None |
| Indexes | Same | Same | None |
| Hibernate Mappings | Same | Same | None |

---

## Performance Impact

### Build Time

| Operation | v5.2 | v6.0 | Change |
|-----------|------|------|--------|
| `mvn clean compile` | ~50s | ~45s | -10% (optimized) |
| `mvn clean test` | ~100s | ~90s | -10% (parallel) |
| `bash scripts/dx.sh` | N/A | ~10s | New feature |

### Runtime

No runtime performance changes. Same throughput and latency.

---

## Deprecations

### None in v6.0.0

All v5.2 APIs remain fully supported.

### Planned for v7.0.0

The following are candidates for deprecation in v7.0.0:

- Old-style event classes (migrate to sealed records)
- Platform threads in agent discovery (migrate to virtual threads)

---

## Testing Your Migration

### Unit Tests

```bash
# Run all tests
mvn -T 1.5C clean test

# Run specific module tests
mvn -T 1.5C test -pl yawl-engine
```

### Integration Tests

```bash
# Run integration tests
mvn -T 1.5C clean verify -P integration
```

### Validation

```bash
# Full validation suite
bash scripts/validation/validate-release.sh
```

Expected output:
```
========================================
  YAWL v6.0.0 Release Validation
========================================

[1/8] Checking package-info coverage...
  Packages: 89, package-info.java: 89, Coverage: 100%
  PASSED: 100% package-info coverage

[2/8] Validating documentation links...
  Broken links: 0
  PASSED: All markdown links valid

...

========================================
  PASSED: Ready for release
========================================
```

---

## Troubleshooting

### Issue: Validation fails with "Observatory facts stale"

**Solution**: Run the observatory to refresh facts:
```bash
bash scripts/observatory/observatory.sh
```

### Issue: Link validation fails

**Solution**: Check the link check report:
```bash
cat docs/validation/link-check-report.txt
```

Fix broken links in documentation files.

### Issue: Build slower than expected

**Solution**: Ensure parallel builds are enabled:
```bash
mvn -T 1.5C clean compile  # -T 1.5C is required
```

### Issue: Performance baseline mismatch

**Solution**: Re-measure baselines:
```bash
bash scripts/performance/measure-baseline.sh
```

---

## Rollback

If issues arise, rollback to v5.2:

```xml
<properties>
    <yawl.version>5.2.0</yawl.version>
</properties>
```

No code changes required. All APIs are compatible.

---

## Support

- **Documentation**: [docs/INDEX.md](docs/INDEX.md)
- **Issues**: https://github.com/yawlfoundation/yawl/issues
- **Mailing List**: yawl-users@lists.sourceforge.net

---

## Related Documents

- [Release Checklist](RELEASE-CHECKLIST.md)
- [Final Implementation Plan](FINAL-IMPLEMENTATION-PLAN.md)
- [Performance SLA](v6/latest/performance/SLA.md)
- [Observatory Index](v6/latest/INDEX.md)

---

**Document Version**: 1.0
**Last Updated**: 2026-02-18
