# Validation Scripts

This directory contains validation scripts for ensuring documentation quality and consistency.

## Scripts

| Script | Purpose | Runtime |
|--------|---------|---------|
| `validate-documentation.sh` | Package coverage, links, schemas | ~1 min |
| `validate-observatory.sh` | Fact freshness, SHA256 verification | ~30s |
| `validate-performance-baselines.sh` | Build/observatory time regression | ~3 min |
| `validate-release.sh` | Complete pre-release validation | ~5 min |

## Usage

### Quick Validation

```bash
# Validate documentation only
bash scripts/validation/validate-documentation.sh

# Validate observatory facts
bash scripts/validation/validate-observatory.sh
```

### Full Validation

```bash
# Complete pre-release validation
bash scripts/validation/validate-release.sh
```

### Performance Validation

```bash
# Check against baselines
bash scripts/validation/validate-performance-baselines.sh

# Update baselines
bash scripts/performance/measure-baseline.sh
```

## Output

All scripts generate reports in `docs/validation/`:

- `validation-report.json` - Machine-readable report
- `link-check-report.txt` - Link validation details
- `release-validation-report.md` - Human-readable release report

## CI/CD Integration

These scripts are integrated into GitHub Actions:

```yaml
# .github/workflows/documentation-validation.yml
- name: Validate documentation links
  run: bash scripts/validation/validate-documentation.sh
```

## Requirements

- Java 25
- Maven 3.9+
- Node.js + markdown-link-check (for link validation)
- jq (for JSON parsing)
- xmllint (for XSD validation)

### Installing markdown-link-check

```bash
npm install -g markdown-link-check
```

## Exit Codes

| Code | Meaning |
|------|---------|
| 0 | All validations passed |
| 1 | One or more errors detected |

## Extending

To add a new validation:

1. Create script in `scripts/validation/validate-<name>.sh`
2. Follow existing pattern (colors, error counting, report generation)
3. Add to `validate-release.sh` checklist
4. Update GitHub Actions workflow if needed
