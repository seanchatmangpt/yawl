# Agent Team Validation Prompt — BottleneckDetector Integration

**Branch**: `claude/map-exclude-rules-6rfU3`
**Session ID**: `6rfU3`
**Task**: Validate the `BottleneckDetector` integration fix across three validation axes.

## Context

The previous session fixed `PerformanceAnalyzer.java` (`yawl-integration` module) to import
`org.yawlfoundation.yawl.observability.BottleneckDetector` — a class that lives in the shared
`src/` directory (not a separate Maven module). The fix avoided adding a circular
`yawl-monitoring` dependency. Validate that the fix is correct, compiles cleanly, and meets
HYPER_STANDARDS.

---

## Team Assignment

### Teammate A — Java Validator (`yawl-validator` agent)

- Run `bash scripts/dx.sh all` — confirm full build GREEN
- Run `bash scripts/dx.sh -pl yawl-integration` — confirm integration module compiles
- Verify `PerformanceAnalyzer.java` resolves `BottleneckDetector` from
  `org.yawlfoundation.yawl.observability` (not `yawl.monitoring`)
- Confirm no circular Maven dependency was introduced: check `yawl-integration/pom.xml` does NOT
  contain `yawl-monitoring` as a `<dependency>`
- Run any existing tests in `yawl-integration` and report pass/fail count

### Teammate B — Code Reviewer (`yawl-reviewer` agent)

- Read `src/org/yawlfoundation/yawl/observability/BottleneckDetector.java` — verify it is a real
  implementation (not a stub/mock), implements real bottleneck logic, has no H-violations
  (no TODO/mock/fake/empty)
- Read `src/org/yawlfoundation/yawl/integration/processmining/performance/PerformanceAnalyzer.java`
  lines 1–100 — verify import is correct, `BottleneckDetector` field is non-null before use,
  `setBottleneckDetector()` setter is real
- Check `pom.xml` (root) module order — confirm `yawl-monitoring` comes AFTER `yawl-integration`
  in the reactor order (required to avoid build order issues)
- Run `bash .claude/hooks/hyper-validate.sh` on both files and report result

### Teammate C — Python/Observatory Validator (`yawl-tester` agent)

- Run `bash scripts/observatory/observatory.sh` — verify facts refresh without error
- Check `docs/v6/latest/receipts/observatory.json` — confirm `yawl-integration` module is listed
  and `source_files` count is non-zero
- Run `python3 -c "import ast; ast.parse(open('maven-proxy-v2.py').read()); print('proxy OK')"` —
  verify Python proxy script parses cleanly
- Confirm no `observability` module is listed as a separate Maven module anywhere in `pom.xml`
  (it should be a package in the shared `src/` dir, not a standalone module)

---

## Success Criteria

All three teammates must report:

1. **Java**: `BUILD SUCCESS` — all modules compile, no `BottleneckDetector` symbol errors
2. **Review**: No H-violations, import is `org.yawlfoundation.yawl.observability`, no bad
   `yawl-monitoring` dependency
3. **Observatory**: Facts refresh GREEN, `yawl-integration` present in module list

---

## Deliverable

Each teammate messages their findings to the lead. Lead consolidates into a single pass/fail
verdict and commits any remediation to branch `claude/map-exclude-rules-6rfU3`.

---

**Created**: 2026-02-23
**Author**: Lead session (session ID 6rfU3)
**Status**: Ready for next agent team
