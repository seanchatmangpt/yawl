# CI/CD Quick Start Guide

## TL;DR

**Your code runs these gates when you push**:
1. **HYPER_STANDARDS** (1-2 min): No TODO, mock, or empty methods in production code
2. **dx.sh Validation** (30-35 min): Compile + test + guards + invariants checks
3. **Summary** (1 min): Final pass/fail status

**Run locally to test before pushing**:
```bash
bash scripts/dx.sh all
```

---

## Workflow Status

Check your validation status on GitHub:
- **Actions tab** → Click latest workflow run
- **See colored checks** for each phase
- **Download artifacts** for detailed reports

---

## Common Scenarios

### Push succeeds (all GREEN)
- Code merged automatically (if PR approved)
- No action needed

### HYPER_STANDARDS fails
**Issue**: TODO/FIXME comments or mock code in `src/`

**Fix**:
```bash
# Remove comments, delete mocks, implement real code
git diff src/  # Review your changes
bash scripts/dx.sh all  # Test locally
git add .
git commit -m "Fix HYPER_STANDARDS violations"
git push
```

### dx.sh Validation fails
**Issue**: Compilation, test, or validation error

**Fix**:
```bash
# Run locally to see error
bash scripts/dx.sh all 2>&1 | tail -50

# Check receipt for violations
bash scripts/validate-receipts.sh --check-all

# Fix the issue and verify
bash scripts/dx.sh all

# Push the fix
git push
```

### Build times out (>35 min)
**Issue**: Build taking too long

**Workaround**:
1. Check if you added large dependencies
2. Run `bash scripts/dx.sh` (incremental, faster)
3. Contact team if consistent timeout

---

## Local Testing

### Quick compile (changed modules only)
```bash
bash scripts/dx.sh
```

### Full validation (like CI)
```bash
bash scripts/dx.sh all
```

### Check specific receipt
```bash
bash scripts/validate-receipts.sh --summary .claude/receipts/guard-receipt.json
```

### View all violations
```bash
VERBOSE=1 bash scripts/validate-receipts.sh --check-all
```

---

## Receipt Files

Located in: `.claude/receipts/`

### guard-receipt.json
- Guards phase (H) violations
- Deferred work, mocks, stubs, lies
- Fix guidance included

### invariant-receipt.json
- Invariants phase (Q) violations
- Real implementation requirements
- Fix guidance included

Both in JSON format for machine processing.

---

## Exit Codes

| Exit | Meaning |
|------|---------|
| 0 | All checks passed, ready to merge |
| 1 | Error (file not found, parse error) |
| 2 | Violations found, fix code and retry |

---

## Getting Help

1. **Check the error message** in GitHub Actions
2. **Download artifacts** (validation-receipts, dx-build-logs)
3. **Run locally**: `bash scripts/dx.sh all`
4. **Review documentation**: `.claude/CI-CD-INTEGRATION.md`

---

## Key Files

| File | Purpose |
|------|---------|
| `.github/workflows/validate-gates.yml` | GitHub Actions workflow definition |
| `scripts/validate-receipts.sh` | Receipt parsing and formatting |
| `scripts/dx.sh` | Build and validation orchestration |
| `.claude/CI-CD-INTEGRATION.md` | Full documentation |

---

## Performance Tips

**Fast iteration**:
```bash
# 1. Make changes
# 2. Quick compile (2-3 min)
bash scripts/dx.sh

# 3. Fix issues if needed
# 4. Full validation before push (30-35 min)
bash scripts/dx.sh all

# 5. Push when everything is GREEN
git push
```

**Skip full build locally** (not recommended):
- CI will catch issues
- Faster local iteration only
- Always run `dx.sh all` before pushing

---

## Environment Variables

**To enable CI mode locally**:
```bash
export CLAUDE_CODE_REMOTE=true
bash scripts/dx.sh all
```

**To customize receipt parsing**:
```bash
export VERBOSE=1  # Show detailed output
export MAX_VIOLATIONS=20  # Show more violations
bash scripts/validate-receipts.sh --check-all
```

---

**Need more details?** See `.claude/CI-CD-INTEGRATION.md`
