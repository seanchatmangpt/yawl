# Archive Index

**Purpose**: Catalog of archived documentation, research, and historical records.

---

## Archive Structure

```
.claude/archive/
├── 2025-q4/
│   └── graalvm/          # GraalVM research (deferred to v7.0.0)
├── 2026-01/
│   └── VIOLATION_REPORT.md  # HYPER_STANDARDS audit (resolved)
└── INDEX.md              # This file
```

---

## 2025-Q4: GraalVM Research Archive

**Archive Date**: 2026-02-18
**Original Location**: `.claude/graalvm/`
**Retention**: Permanent (reference for v7.0.0 planning)

### Contents

| File | Description | Status |
|------|-------------|--------|
| `README.md` | GraalVM research overview and decision matrix | Reference |
| `NATIVE_IMAGE_ARCHITECTURE.md` | Native-image compilation design (800 lines) | Reference |
| `REFLECTION_ANALYSIS_REPORT.md` | 22 reflection points catalog (1200 lines) | Reference |
| `GRADLE_MIGRATION_ANALYSIS.md` | Gradle vs Maven decision framework | Reference |
| `ECOSYSTEM_MODERNIZATION_ROADMAP.md` | Long-term vision (Jakarta EE, Spring Boot) | Reference |
| `IMPLEMENTATION_GUIDE.md` | 8-week execution roadmap | Reference |

### Key Decisions

1. **Native-Image**: Deferred to v6.1.0 (approved, 320 hours effort)
2. **Gradle Migration**: Deferred to v7.0.0 (38% build speedup, moderate ROI)
3. **Virtual Threads**: Approved for v6.0.0 (82% throughput improvement)

### Restoration

To restore these documents to active development:
```bash
cp -r .claude/archive/2025-q4/graalvm/* .claude/graalvm/
```

---

## 2026-01: HYPER_STANDARDS Violation Report

**Archive Date**: 2026-02-18
**Original Location**: `.claude/VIOLATION_REPORT.md`
**Retention**: Permanent (historical record)

### Summary

- **Total Violations**: 61 (12 BLOCKER, 31 HIGH, 18 MEDIUM)
- **Resolution Status**: Coordination complete, remediation tracked separately
- **Key Issues**: Stub packages, demo code in production, silent exception handling

### Lessons Learned

1. Never commit `stub/` packages to production source
2. Demo/test classes must reside in `test/` directory
3. All exception catches must log at minimum before fallback
4. `System.out.println` forbidden in production code

---

## Archive Policy

### Retention Periods

| Category | Retention | Reason |
|----------|-----------|--------|
| ADR drafts | Permanent | Decision history |
| Research documents | Permanent | Future reference |
| Violation reports | 3 years | Compliance audit trail |
| Meeting notes | 1 year | Operational reference |

### Access

Archived documents are read-only reference materials. To update:

1. Copy from archive to working directory
2. Make changes
3. Update `Last Modified` date
4. Do not delete archive copy (maintain history)

---

## Related Documentation

- [CLAUDE.md](../CLAUDE.md) - Active project specification
- [docs/architecture/decisions/](../../docs/architecture/decisions/) - Active ADRs
- [.claude/INDEX.md](../INDEX.md) - Documentation map

---

**Last Updated**: 2026-02-18
**Maintainer**: YAWL Architecture Team
