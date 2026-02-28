# YAWL CI/CD Validation Gates

This README describes the GitHub Actions integration for YAWL's validation gates (Phase Λ of CLAUDE.md architecture).

## Quick Start

### For Developers

Before pushing code:

```bash
# Run validation locally
bash scripts/dx.sh all

# Check if all gates passed
bash scripts/validate-receipts.sh check-all .yawl/.dx-state/

# View detailed violations (if any)
bash scripts/validate-receipts.sh format-github .yawl/.dx-state/guard-receipt.json
```

### For CI/CD

The workflow runs automatically on:
- Push to `main` or `develop` branches
- Pull requests targeting `main` or `develop` branches

Workflow file: `.github/workflows/validate-gates.yml`

## What Gets Validated

The validation pipeline (dx.sh all) enforces three phases:

### Phase Λ: Build (Compile)
- Java 25 compilation
- Maven module dependencies
- Classpath resolution

### Phase H: Guards (Hyper-Standards)
Detects and blocks patterns that violate Fortune 5 production standards:

| Pattern | Description | Severity |
|---------|-------------|----------|
| H_TODO | TODO/FIXME/XXX/HACK comments | FAIL |
| H_MOCK | Mock/stub/fake/demo identifiers or classes | FAIL |
| H_STUB | Empty or placeholder return values | FAIL |
| H_EMPTY | No-op method bodies | FAIL |
| H_FALLBACK | Silent catch-and-fake error handling | FAIL |
| H_LIE | Code doesn't match documentation | FAIL |
| H_SILENT | Logging errors instead of throwing exceptions | FAIL |

### Phase Q: Invariants (Real Implementation)
Ensures code meets these invariants:
- **Q1**: Real implementation OR throw UnsupportedOperationException
- **Q2**: No mock classes in production code
- **Q3**: No silent error fallbacks (catch blocks must propagate or throw)

## Receipt Files

Validation results are stored as JSON receipts in `.yawl/.dx-state/`:

### guard-receipt.json
```json
{
  "phase": "guards",
  "status": "GREEN",
  "files_scanned": 42,
  "violations": [],
  "summary": { "total_violations": 0 }
}
```

### invariant-receipt.json
```json
{
  "phase": "invariants",
  "status": "GREEN",
  "java_files_scanned": 2143,
  "violations_found": 0
}
```

Status values:
- **GREEN** (0): All gates passed
- **RED** (2): Violations found, requires fixes

## Receipt Validation Utility

Tool: `scripts/validate-receipts.sh`

### Check single receipt:
```bash
bash scripts/validate-receipts.sh check .yawl/.dx-state/guard-receipt.json
# Exit code: 0 (GREEN), 2 (RED), 1 (error)
```

### Check all receipts:
```bash
bash scripts/validate-receipts.sh check-all .yawl/.dx-state/
# Returns 0 if all GREEN, 2 if any RED
```

### View violations:
```bash
bash scripts/validate-receipts.sh format-github .yawl/.dx-state/guard-receipt.json
```

### Parse for scripts:
```bash
bash scripts/validate-receipts.sh parse .yawl/.dx-state/guard-receipt.json
# Sets: RECEIPT_STATUS, FILES_SCANNED, TOTAL_VIOLATIONS, PHASE
```

## GitHub Actions Workflow

Workflow: `.github/workflows/validate-gates.yml`

### Steps

1. **Checkout code** - Git clone with full history
2. **Set up Java 25** - Temurin distribution with Maven cache
3. **Run validation** - Execute `bash scripts/dx.sh all`
4. **Archive receipts** - Upload `.yawl/.dx-state/` as artifact (30-day retention)
5. **Parse results** - Run `validate-receipts.sh check-all`
6. **Generate summary** - Format violations as GitHub markdown in job summary
7. **Fail job** - Exit with code 2 if violations detected
8. **Success** - Show success message if all gates passed

### Features

- **Concurrency**: Cancels in-progress runs on new push (group: `validate-${{ github.ref }}`)
- **Timeout**: 30 minutes per workflow run
- **Artifacts**: Receipt files archived for 30 days
- **Job Summary**: Violations displayed in GitHub UI
- **Graceful fallback**: Handles missing receipts without failing

## Exit Codes

Scripts use these exit codes for CI/CD integration:

| Code | Meaning | CI Action |
|------|---------|-----------|
| 0 | Success - All gates GREEN | Proceed to deployment |
| 1 | Transient error - File I/O or parse error | Retry job |
| 2 | Violations detected - RED status | Block PR/deployment |

## Fixing Violations

When gates fail:

1. **Read the violation**
   ```bash
   bash scripts/validate-receipts.sh format-github .yawl/.dx-state/guard-receipt.json
   ```

2. **Fix the code** according to guidance:
   - Remove TODO/FIXME comments
   - Rename mock classes to real names
   - Implement real logic or throw exception
   - Fix code-documentation mismatches
   - Propagate exceptions instead of catching silently

3. **Re-run validation locally**
   ```bash
   bash scripts/dx.sh all
   bash scripts/validate-receipts.sh check-all .yawl/.dx-state/
   ```

4. **Verify result**
   - Exit code 0 = ready to push
   - Exit code 2 = more fixes needed

## Integration with Branch Protection

For production branches (main/develop), configure GitHub branch protection rules:

1. **Require status checks to pass**
   - Status: "Validate YAWL Build Gates"

2. **Additional protections**
   - Require at least 1 approval
   - Dismiss stale pull request approvals
   - Require branches to be up to date

This ensures no code with violations can be merged.

## Performance Notes

- **Local validation** (~10-15 minutes on modern hardware):
  - Compile: ~5 minutes
  - Guards phase: ~2 minutes
  - Invariants phase: ~3 minutes
- **GitHub Actions** (~15-20 minutes):
  - Network latency + artifact upload adds ~5 minutes
  - Maven cache speeds up subsequent runs
- **Receipt generation/parsing**: <1 second per receipt

## Environment Variables

### Set in Workflow
- `CLAUDE_CODE_REMOTE=true` - Enables Maven proxy auto-configuration

### Auto-set by GitHub
- `GITHUB_STEP_SUMMARY` - Path to job summary file
- `GITHUB_OUTPUT` - Path to output variables

### For Local CI-like Testing
```bash
export CLAUDE_CODE_REMOTE=true
bash scripts/dx.sh all
```

## Troubleshooting

### Workflow fails but no receipts generated
**Cause**: `dx.sh` exited early (missing dependencies, compile errors)

**Solution**: Review `dx.sh` output in workflow logs before receipt generation

### Job summary not showing violations
**Cause**: `validate-receipts.sh format-github` failed or receipt missing

**Solution**: Check `.yawl/.dx-state/*.json` files exist and are valid JSON

### H_TODO violations keep appearing
**Cause**: Code has TODO/FIXME/XXX/HACK comments

**Solution**: Implement the deferred work or replace with throw UnsupportedOperationException

### H_MOCK violations for test fixtures
**Cause**: Test code with mock/stub/fake/demo in class/method names

**Solution**: Rename test utilities to real names or move to test-only modules

### Q1 violations for unimplemented methods
**Cause**: Empty method bodies without throw statement

**Solution**: Either:
- Implement real logic
- Add: `throw new UnsupportedOperationException("Method not yet implemented. See IMPLEMENTATION_GUIDE.md");`

### Q3 violations for silent fallbacks
**Cause**: Catch blocks returning fake data instead of propagating

**Solution**: Remove catch block or throw/rethrow the exception

## Documentation

- **Full integration guide**: `.github/CI-CD-INTEGRATION.md`
- **Validation phases**: `.claude/HYPER_STANDARDS.md`
- **Architecture**: `CLAUDE.md` (Phase Λ, H, Q sections)
- **Build system**: `scripts/dx.sh` (run `bash scripts/dx.sh --help`)

## Support

For issues or questions:
1. Check violation output: `bash scripts/validate-receipts.sh format-github <receipt>`
2. Review HYPER_STANDARDS.md for pattern details
3. Check GitHub workflow logs for execution details
4. See CI-CD-INTEGRATION.md for detailed documentation

