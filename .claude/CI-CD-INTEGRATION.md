# YAWL CI/CD Integration — GitHub Actions Validation Gates

**Status**: PRODUCTION READY  
**Version**: 1.0  
**Last Updated**: 2026-02-28

## Overview

This document describes the GitHub Actions CI/CD pipeline that enforces YAWL's validation gates through automated testing and code quality checks.

## Architecture

### Components

1. **GitHub Actions Workflow** (`.github/workflows/validate-gates.yml`)
   - 372 lines, fully featured GitHub Actions workflow
   - Three validation phases with fail-fast strategy
   - Artifact archiving for build logs and reports
   - Receipt parsing and GitHub job summary integration

2. **Receipt Parsing Script** (`scripts/validate-receipts.sh`)
   - 352 lines, executable bash script
   - Parses dx.sh validation receipt JSON files
   - Multiple modes: validation, formatting, summarization
   - GitHub Actions environment integration

## Validation Pipeline

### Phase 1: HYPER_STANDARDS (Forbidden Patterns)

**Trigger**: Runs before compilation  
**Purpose**: Fast detection of code quality violations  
**Exit Code**: 0 (pass) or 1 (fail)

Detects and blocks:
- TODO/FIXME/XXX comments in production code
- Mock/stub method and class names in production code
- Empty no-op method bodies
- Mock framework imports in production src/

**Time**: ~1-2 minutes

### Phase 2: dx.sh Validation (Compile + Test + Guards + Invariants)

**Trigger**: After HYPER_STANDARDS passes  
**Purpose**: Full build, test, and semantic validation  
**Exit Code**: 0 (pass) or 2 (fail)

Executes:
1. Full compilation of all modules
2. Unit tests with JUnit 5
3. Guards phase (H): Detects deferred work, mocks, stubs, lies
4. Invariants phase (Q): Ensures real impl ∨ throw UnsupportedOperationException

Produces receipt files:
- `.claude/receipts/guard-receipt.json`
- `.claude/receipts/invariant-receipt.json`

**Time**: ~30-35 minutes

### Phase 3: Validation Summary

**Trigger**: Always runs (after phases 1 & 2)  
**Purpose**: Aggregate results and determine final status  
**Exit Code**: 0 (all green) or 1 (any failure)

Reports:
- Summary table of all validation gates
- Commit hash, branch, author
- Remediation steps if failures detected

## Receipt File Format

### Guard Receipt Example

```json
{
  "phase": "guards",
  "timestamp": "2026-02-28T20:30:00Z",
  "emit_directory": "yawl/engine",
  "files_scanned": 45,
  "violations": [
    {
      "pattern": "H_TODO",
      "severity": "FAIL",
      "file": "yawl/engine/YNetRunner.java",
      "line": 427,
      "content": "// TODO: Add deadlock detection",
      "fix_guidance": "Implement real logic or throw UnsupportedOperationException"
    }
  ],
  "summary": {
    "h_todo_count": 1,
    "h_mock_count": 0,
    "total_violations": 1
  },
  "status": "GREEN",
  "error_message": null
}
```

### Invariant Receipt Example

```json
{
  "phase": "invariants",
  "timestamp": "2026-02-28T20:35:00Z",
  "code_directory": ".",
  "java_files_scanned": 2143,
  "violations_found": 0,
  "violations": [],
  "status": "GREEN",
  "passing_rate": "100%",
  "severity": "NONE",
  "next_action": "Proceed to next phase"
}
```

## Workflow Features

### Fast Failure

- HYPER_STANDARDS phase fails immediately if violations detected
- dx.sh validation stops remaining gates if violation found
- Summary phase propagates failures up the dependency chain

### CI Environment Support

Set via environment variable:
```yaml
CLAUDE_CODE_REMOTE: true
```

Enables:
- Maven proxy for remote dependency resolution
- Custom metrics collection
- Remote cache synchronization

### Artifact Archiving

Automatically uploads:
- `validation-receipts` (30-day retention)
  - `guard-receipt.json`
  - `invariant-receipt.json`
- `dx-build-logs` (7-day retention)
  - Complete dx.sh output log
- `surefire-reports` (7-day retention)
  - JUnit test reports from all modules

### GitHub Integration

- **Job Summary**: Structured markdown in GitHub UI
- **Concurrency Control**: Cancels duplicate runs on same branch
- **Pull Request Comments**: Receipt violations highlighted (future)
- **Status Checks**: Each phase reported separately

## Usage

### Local Testing

Run the same validation pipeline locally:

```bash
# Full validation (like CI)
bash scripts/dx.sh all

# Compile only (fast iteration)
bash scripts/dx.sh

# Check receipts
bash scripts/validate-receipts.sh --check-all

# Format for viewing
bash scripts/validate-receipts.sh --summary .claude/receipts/guard-receipt.json
```

### Triggering in CI

Validation runs automatically on:
1. **Push to main/master/develop**
2. **Pull request** to main/master/develop
3. **Manual workflow dispatch** (GitHub UI)

To skip validation on a specific commit (not recommended):
- Use GitHub UI to re-run with `--skip-validate` flag
- Or set environment variable in commit message (requires workflow update)

### Interpreting Results

**HYPER_STANDARDS PASSED**
- No forbidden patterns in production code
- Proceed to next phase

**HYPER_STANDARDS FAILED**
- Remove TODO/FIXME comments
- Delete mock/stub/fake code
- Implement real behavior or throw UnsupportedOperationException
- Push fixes to trigger re-validation

**dx.sh Validation PASSED**
- Code compiles cleanly
- All tests pass
- No guard violations (H phase)
- No invariant violations (Q phase)
- Ready to merge

**dx.sh Validation FAILED**
- Check receipt files for specific violations
- Fix violations in source code
- Run `bash scripts/dx.sh all` locally to verify fix
- Push updated code to trigger re-validation

## Receipt Parsing Script

### Functions

#### `parse_receipt_status(receipt_file)`
Extract status field from receipt JSON.

**Exit Code**: 0 (success), 1 (error)  
**Output**: Status string (GREEN, RED, or UNKNOWN)

```bash
status=$(parse_receipt_status ".claude/receipts/guard-receipt.json")
echo "Status: $status"
```

#### `check_receipt_status(receipt_file)`
Check if receipt status is GREEN or RED.

**Exit Code**: 0 (GREEN), 1 (error), 2 (RED)

```bash
if bash scripts/validate-receipts.sh .claude/receipts/guard-receipt.json; then
    echo "Receipt is GREEN"
fi
```

#### `extract_violations(receipt_file, limit)`
Extract top N violations from receipt.

**Default limit**: 10

```bash
bash scripts/validate-receipts.sh --summary .claude/receipts/guard-receipt.json
```

#### `format_for_github(receipt_file, phase_name)`
Format receipt as GitHub markdown for job summary.

```bash
format_for_github ".claude/receipts/guard-receipt.json" "GUARDS"
```

#### `check_all_receipts(receipts_dir)`
Validate all receipts in directory.

**Exit Code**: 0 (all GREEN), 1 (error), 2 (any RED)

```bash
RECEIPT_DIR=.claude/receipts bash scripts/validate-receipts.sh --check-all
```

#### `summarize_violations(receipt_file)`
Count violations by type.

**Output**: Pattern name and count (colored)

```bash
bash scripts/validate-receipts.sh --summary .claude/receipts/guard-receipt.json
```

### Usage Modes

**Validate specific receipts**:
```bash
bash scripts/validate-receipts.sh guard-receipt.json invariant-receipt.json
```

**Check all receipts in directory**:
```bash
bash scripts/validate-receipts.sh --check-all
```

**Get summary of violations**:
```bash
bash scripts/validate-receipts.sh --summary .claude/receipts/guard-receipt.json
```

**Format for GitHub output**:
```bash
bash scripts/validate-receipts.sh --github-output \
  .claude/receipts/guard-receipt.json \
  .claude/receipts/invariant-receipt.json
```

### Exit Codes

| Exit | Meaning | Action |
|------|---------|--------|
| 0 | All receipts GREEN | Proceed (no action needed) |
| 1 | File not found or parse error | Retry (transient) |
| 2 | Violation found (RED status) | Fix violations and re-run |

## Environment Variables

### Workflow

```yaml
env:
  JAVA_VERSION: 25
  JAVA_DISTRIBUTION: temurin
  MAVEN_OPTS: -Xmx4g -Xms1g -XX:+UseZGC
  CI_ENVIRONMENT: true
  CLAUDE_CODE_REMOTE: true
```

### Receipt Script

```bash
RECEIPT_DIR=.claude/receipts    # Directory with receipt files (default)
VERBOSE=1                        # Show detailed output
MAX_VIOLATIONS=10               # Max violations to display
```

## Troubleshooting

### Workflow Fails on HYPER_STANDARDS

**Issue**: TODO/FIXME comments or mock code in production src/

**Solution**:
1. Remove all TODO/FIXME/XXX/HACK comments from `src/` and `yawl-*/src/`
2. Delete all mock/stub/fake classes and methods
3. Implement real behavior or throw `UnsupportedOperationException`
4. Commit and push to re-trigger validation

### Workflow Fails on dx.sh Validation

**Issue**: Compilation error, test failure, or validation violation

**Solution**:
1. Run locally: `bash scripts/dx.sh all`
2. Check receipt files: `bash scripts/validate-receipts.sh --check-all`
3. For guards violations: See H-GUARDS documentation
4. For invariants violations: See Q-INVARIANTS documentation
5. Fix violations and re-run locally before pushing

### Receipt Files Not Generated

**Issue**: Workflow exits before generating receipt files

**Possible Causes**:
- Compilation failure (Java syntax error)
- OutOfMemory during build
- Maven dependency resolution failure

**Solution**:
1. Check dx-build-logs artifact for error details
2. Increase MAVEN_OPTS memory if needed
3. Run locally to reproduce issue
4. Fix root cause and re-push

### Artifact Upload Fails

**Issue**: Receipt files not uploaded to GitHub

**Possible Causes**:
- Workflow cancelled mid-run
- Storage quota exceeded
- File permissions issue

**Solution**:
1. Check GitHub Actions logs for storage warnings
2. Delete old artifacts (30+ days)
3. Ensure workflow completes all phases

## Performance

### Baseline Times

| Phase | Duration | Parallelization |
|-------|----------|-----------------|
| HYPER_STANDARDS | 1-2 min | N/A (pattern scan) |
| Compile | 8-10 min | Maven parallelization (default) |
| Test | 15-20 min | JUnit 5 parallel execution |
| Guards (H) | 2-3 min | Parallel file scanning |
| Invariants (Q) | 1-2 min | Parallel pattern checking |
| **Total** | **30-35 min** | ~3-4× parallel speedup |

### Optimization

- **Cache**: Maven dependencies cached between runs
- **Incremental**: dx.sh builds only changed modules locally
- **Parallel**: Maven, JUnit, SPARQL queries all parallel
- **Fail-Fast**: Stop on first violation to save time

## Integration with Other Systems

### Pull Request Comments

Future feature: Automatically post receipt violations as PR comments.

```
@developer
Guard violation detected in pull request #123

Pattern: H_TODO
File: yawl/engine/YNetRunner.java:427
Content: // TODO: Add deadlock detection

Fix: Implement real deadlock logic or throw UnsupportedOperationException
```

### Slack Notifications

Future feature: Post workflow results to Slack channel.

```
[YAWL CI] Validation failed on main
Commit: abc123 (author)
Phase: dx.sh Validation
Issue: 3 guard violations found
Link: https://github.com/...
```

### JIRA Integration

Receipt violations can be automatically created as JIRA tickets.

## Best Practices

1. **Run locally before pushing**
   ```bash
   bash scripts/dx.sh all
   ```

2. **Fix violations immediately**
   - Don't commit workarounds or commented-out fixes
   - Implement real behavior or throw exceptions

3. **Check artifacts after failures**
   - Download validation-receipts for detailed error info
   - Download dx-build-logs to see compiler output

4. **Use fast iteration loop**
   ```bash
   # Fast: compile changed modules only
   bash scripts/dx.sh
   
   # After fix: full validation like CI
   bash scripts/dx.sh all
   ```

5. **Understand receipt format**
   - JSON structure allows machine processing
   - Can be integrated with IDE plugins
   - Searchable for specific violation types

## Maintenance

### Updating Workflow

To modify validation gates:
1. Edit `.github/workflows/validate-gates.yml`
2. Test locally with `bash scripts/dx.sh all`
3. Create PR and wait for validation
4. Merge after all gates pass

### Updating Receipt Script

To enhance receipt parsing:
1. Edit `scripts/validate-receipts.sh`
2. Test with existing receipt files
3. Test with new receipt formats
4. Update documentation

### Monitoring

Check workflow health:
- GitHub Actions page: View recent runs
- Artifacts: Check retention and cleanup
- Logs: Review performance trends

## References

- `.github/workflows/validate-gates.yml` — Workflow definition
- `scripts/validate-receipts.sh` — Receipt parsing script
- `scripts/dx.sh` — Build and validation orchestration
- `.claude/HYPER_STANDARDS.md` — Forbidden patterns reference
- `.claude/rules/validation-phases/H-GUARDS-*.md` — Guards phase docs
- `.claude/rules/validation-phases/Q-INVARIANTS-*.md` — Invariants phase docs

## Support

For issues or questions:
1. Check this document for troubleshooting section
2. Review workflow logs in GitHub Actions UI
3. Run `bash scripts/dx.sh all` locally to reproduce
4. Consult HYPER_STANDARDS and validation phase documentation
